"""Local BUSI preparation; source/license verification is a manual prerequisite.

Only an already-extracted directory containing benign/, malignant/ and normal/
PNGs is supported. Nothing is downloaded or extracted. Duplicate groups use the
exact decoded grayscale pixels consumed by training, not PNG file bytes. These
are NOT patient-level splits: BUSI patient identities have not been verified.
"""

from __future__ import annotations

import hashlib
import json
import math
import random
import re
import warnings
from pathlib import Path, PurePosixPath, PureWindowsPath
from typing import Any
from urllib.parse import urlsplit

import numpy as np
from PIL import Image

CATEGORIES = ("benign", "malignant", "normal")
SPLITS = ("train", "val", "test")
_MASK_NAME = re.compile(r"(.+)_mask(?:_\d+)?", re.IGNORECASE)


def _source_metadata(source_url: str, license_name: str, source_verified: bool) -> dict:
    if source_verified is not True:
        raise ValueError(
            "Explicit source_verified=True is required after manual source/license review"
        )
    if not isinstance(source_url, str) or not source_url.strip():
        raise ValueError("source_url must be a nonempty public HTTP(S) source URL")
    try:
        url = urlsplit(source_url.strip())
        valid_url = url.scheme in {"http", "https"} and bool(url.hostname)
        valid_url = valid_url and url.username is None and url.password is None
    except ValueError:
        valid_url = False
    if not valid_url:
        raise ValueError("source_url must be a public HTTP(S) URL without credentials")
    if not isinstance(license_name, str) or not license_name.strip():
        raise ValueError("license_name must record the manually reviewed license")
    return {
        "url": source_url.strip(),
        "license": license_name.strip(),
        "verification": "user-confirmed",
    }


def _integer(value: Any, name: str, *, minimum: int | None = None) -> None:
    if type(value) is not int or (minimum is not None and value < minimum):
        suffix = f" >= {minimum}" if minimum is not None else ""
        raise ValueError(f"{name} must be an integer{suffix}")


def _fractions(train: float, val: float) -> tuple[float, float, float]:
    for value in (train, val):
        if type(value) not in (int, float) or not math.isfinite(value) or not 0 < value < 1:
            raise ValueError("Split fractions must be finite and strictly between zero and one")
    test = 1.0 - train - val
    if test <= 0:
        raise ValueError("train_fraction + val_fraction must be less than one")
    return float(train), float(val), test


def _local_root(value: str | Path) -> Path:
    try:
        root = Path(value).expanduser().resolve(strict=True)
    except (OSError, RuntimeError) as exc:
        raise ValueError(f"Cannot read local data directory: {value}") from exc
    if not root.is_dir():
        raise ValueError("data_root must be an extracted local BUSI directory, not an archive")
    return root


def _relative_file(root: Path, value: Any) -> Path:
    if not isinstance(value, str) or not value or "\\" in value or "\x00" in value:
        raise ValueError("Image/mask paths must be relative POSIX PNG paths")
    relative = PurePosixPath(value)
    if (
        relative.is_absolute()
        or PureWindowsPath(value).drive
        or ".." in relative.parts
        or relative.as_posix() != value
        or relative.suffix.lower() != ".png"
    ):
        raise ValueError(f"Unsafe relative PNG path: {value}")
    try:
        resolved = (root / relative).resolve(strict=True)
    except (OSError, RuntimeError) as exc:
        raise ValueError(f"Cannot read image/mask path: {value}") from exc
    if not resolved.is_relative_to(root) or not resolved.is_file():
        raise ValueError(f"Image/mask path escapes data_root or is not a file: {value}")
    return resolved


def _read_png(path: Path) -> np.ndarray:
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(path) as image:
                if image.format != "PNG" or getattr(image, "n_frames", 1) != 1:
                    raise ValueError("Expected a single-frame PNG")
                image.load()
                return np.array(image.convert("L"), dtype=np.uint8)
    except (
        OSError,
        ValueError,
        Image.DecompressionBombError,
        Image.DecompressionBombWarning,
    ) as exc:
        raise ValueError(f"Unreadable or invalid PNG: {path.name}: {exc}") from exc


def _load_pixels(root: Path, record: dict) -> tuple[np.ndarray, np.ndarray]:
    image = _read_png(_relative_file(root, record["image"]))
    masks = record["masks"]
    if not masks and record["category"] != "normal":
        raise ValueError(f"Positive image has no mask: {record['id']}")
    union = np.zeros(image.shape, dtype=np.uint8)
    for name in masks:
        mask = _read_png(_relative_file(root, name))
        if mask.shape != image.shape:
            raise ValueError(f"Mask dimensions differ from image: {name}")
        if not np.isin(mask, (0, 1, 255)).all():
            raise ValueError(f"Mask must be binary (0/1 or 0/255): {name}")
        union |= (mask > 0).astype(np.uint8)
    return image, union


def _pixel_digest(array: np.ndarray) -> str:
    digest = hashlib.sha256(f"uint8:{array.shape}:".encode("ascii"))
    digest.update(array.tobytes())
    return digest.hexdigest()


def _split_counts(size: int, fractions: tuple[float, float, float]) -> dict[str, int]:
    """Nearest proportional integer allocation, reserving one group per split."""
    targets = [size * fraction for fraction in fractions]
    counts = [max(1, math.floor(target)) for target in targets]
    while sum(counts) > size:
        eligible = [i for i, count in enumerate(counts) if count > 1]
        counts[max(eligible, key=lambda i: round(counts[i] - targets[i], 12))] -= 1
    while sum(counts) < size:
        counts[max(range(3), key=lambda i: round(targets[i] - counts[i], 12))] += 1
    return dict(zip(SPLITS, counts, strict=True))


def _assign_splits(records: list[dict], seed: int, fractions: tuple[float, float, float]) -> None:
    categories: dict[str, set[str]] = {}
    for record in records:
        categories.setdefault(record["group_id"], set()).add(record["category"])
    per_category = {
        category: [group for group in sorted(categories) if category in categories[group]]
        for category in CATEGORIES
    }
    if any(len(groups) < 3 for groups in per_category.values()):
        raise ValueError(
            "Each category needs at least 3 distinct pixel-content groups for 3 splits"
        )
    rng = random.Random(seed)
    order = sorted(categories)
    rng.shuffle(order)
    assignments: dict[str, str] = {}
    covered: dict[str, set[str]] = {category: set() for category in CATEGORIES}

    def cover_splits() -> bool:
        # Groups can span categories. Cover all category/split pairs first;
        # groups with identical category membership are interchangeable here.
        missing = [(c, s) for c in CATEGORIES for s in SPLITS if s not in covered[c]]
        if not missing:
            return True
        remaining = {
            c: [g for g in order if c in categories[g] and g not in assignments] for c in CATEGORIES
        }
        if any(len(remaining[c]) < 3 - len(covered[c]) for c in CATEGORIES):
            return False
        category, split = min(missing, key=lambda pair: len(remaining[pair[0]]))
        candidates = sorted(
            remaining[category],
            key=lambda g: -sum(split not in covered[c] for c in categories[g]),
        )
        tried = set()
        for group in candidates:
            membership = frozenset(categories[group])
            if membership in tried:
                continue
            tried.add(membership)
            added = [c for c in categories[group] if split not in covered[c]]
            assignments[group] = split
            for c in added:
                covered[c].add(split)
            if cover_splits():
                return True
            del assignments[group]
            for c in added:
                covered[c].remove(split)
        return False

    if not cover_splits():
        raise ValueError(
            "Duplicate grouping makes nonempty stratified train/val/test splits impossible"
        )
    targets = {c: _split_counts(len(groups), fractions) for c, groups in per_category.items()}
    counts = {
        c: {s: sum(assignments.get(g) == s for g in groups) for s in SPLITS}
        for c, groups in per_category.items()
    }
    for group in order:
        if group in assignments:
            continue
        choices = list(SPLITS)
        rng.shuffle(choices)
        split = max(
            choices,
            key=lambda s: sum(
                (targets[c][s] - counts[c][s]) / len(per_category[c])
                for c in sorted(categories[group])
            ),
        )
        assignments[group] = split
        for category in categories[group]:
            counts[category][split] += 1
    for record in records:
        record["split"] = assignments[record["group_id"]]


def build_manifest(
    data_root: Path,
    output_path: Path,
    *,
    source_url: str,
    license_name: str,
    source_verified: bool,
    seed: int = 42,
    train_fraction: float = 0.7,
    val_fraction: float = 0.15,
) -> dict:
    """Validate local BUSI PNGs and write reproducible, duplicate-grouped splits.

    The caller attests to source/license review; this function does not verify a
    website or establish patient independence. Missing normal masks explicitly
    mean zero foreground. Non-normal images must have at least one mask.
    """
    source = _source_metadata(source_url, license_name, source_verified)
    _integer(seed, "seed")
    fractions = _fractions(train_fraction, val_fraction)
    root = _local_root(data_root)
    records = []
    ids = set()
    group_masks: dict[str, str] = {}
    for category in CATEGORIES:
        directory = root / category
        if not directory.is_dir() or not directory.resolve().is_relative_to(root):
            raise ValueError(f"Missing or unsafe BUSI category directory: {category}")
        pngs = sorted(p for p in directory.iterdir() if p.suffix.lower() == ".png")
        originals = [p for p in pngs if "_mask" not in p.stem.lower()]
        mask_files = [p for p in pngs if "_mask" in p.stem.lower()]
        masks_by_stem: dict[str, list[Path]] = {}
        for path in mask_files:
            match = _MASK_NAME.fullmatch(path.stem)
            if match is None:
                raise ValueError(f"Unsupported BUSI mask filename: {path.name}")
            masks_by_stem.setdefault(match[1].casefold(), []).append(path)
        for path in originals:
            record_id = f"{category}/{path.stem}"
            if record_id.casefold() in ids:
                raise ValueError(f"Duplicate image ID: {record_id}")
            ids.add(record_id.casefold())
            masks = masks_by_stem.pop(path.stem.casefold(), [])
            record = {
                "id": record_id,
                "image": path.relative_to(root).as_posix(),
                "masks": [p.relative_to(root).as_posix() for p in masks],
                "category": category,
                "implicit_zero_mask": not masks,
            }
            image, union = _load_pixels(root, record)
            group_id = _pixel_digest(image)
            mask_digest = _pixel_digest(union)
            if group_id in group_masks and group_masks[group_id] != mask_digest:
                raise ValueError(f"Contradictory union masks for duplicate image: {record_id}")
            group_masks[group_id] = mask_digest
            record["group_id"] = group_id
            records.append(record)
        if masks_by_stem:
            raise ValueError(f"Mask has no matching original in {category}")
    _assign_splits(records, seed, fractions)
    manifest = {
        "schema_version": 1,
        "data_root": str(root),
        "source": source,
        "split_unit": "duplicate_group",
        "patient_independence_verified": False,
        "seed": seed,
        "split_fractions": dict(zip(SPLITS, fractions, strict=True)),
        "records": records,
    }
    output = Path(output_path)
    source_paths = {
        (root / name).resolve()
        for record in records
        for name in [record["image"], *record["masks"]]
    }
    if output.resolve() in source_paths:
        raise ValueError("output_path must not overwrite a source image or mask")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    return manifest


def load_manifest(path: Path | str) -> dict:
    """Load and validate schema, local paths, pixels, masks and split integrity."""
    try:
        manifest = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise ValueError(f"Cannot read JSON manifest: {path}") from exc
    if not isinstance(manifest, dict) or type(manifest.get("schema_version")) is not int:
        raise ValueError("Manifest must be an object with schema_version=1")
    if manifest["schema_version"] != 1:
        raise ValueError("Unsupported manifest schema_version")
    source = manifest.get("source")
    if not isinstance(source, dict) or source.get("verification") != "user-confirmed":
        raise ValueError("Manifest source must record user-confirmed manual verification")
    _source_metadata(source.get("url"), source.get("license"), True)
    if (
        manifest.get("split_unit") != "duplicate_group"
        or manifest.get("patient_independence_verified") is not False
    ):
        raise ValueError(
            "Only duplicate_group splits with unverified patient independence are supported"
        )
    _integer(manifest.get("seed"), "seed")
    root_value = manifest.get("data_root")
    if not isinstance(root_value, str) or not Path(root_value).is_absolute():
        raise ValueError("Manifest data_root must be an absolute directory")
    root = _local_root(root_value)
    if "split_fractions" in manifest:
        ratios = manifest["split_fractions"]
        if not isinstance(ratios, dict) or set(ratios) != set(SPLITS):
            raise ValueError("Invalid manifest split_fractions")
        expected = _fractions(ratios["train"], ratios["val"])
        if (
            type(ratios["test"]) not in (int, float)
            or not math.isfinite(ratios["test"])
            or not math.isclose(ratios["test"], expected[2], rel_tol=1e-9, abs_tol=1e-12)
        ):
            raise ValueError("Manifest split_fractions must sum to one")
    records = manifest.get("records")
    if not isinstance(records, list) or not records:
        raise ValueError("Manifest records must be a nonempty list")
    ids: set[str] = set()
    image_paths: set[str] = set()
    group_details: dict[str, tuple[str, str, str]] = {}
    pixel_groups: dict[str, str] = {}
    seen_splits: dict[str, set[str]] = {c: set() for c in CATEGORIES}
    for record in records:
        if not isinstance(record, dict):
            raise ValueError("Each manifest record must be an object")
        for field in ("id", "group_id", "category", "split"):
            if not isinstance(record.get(field), str) or not record[field].strip():
                raise ValueError(f"Record {field} must be a nonempty string")
        record_id = record["id"].casefold()
        if record_id in ids:
            raise ValueError(f"Duplicate image ID: {record['id']}")
        ids.add(record_id)
        if record["category"] not in CATEGORIES or record["split"] not in SPLITS:
            raise ValueError("Invalid record category or split")
        _relative_file(root, record.get("image"))
        image_path = record["image"].casefold()
        if image_path in image_paths:
            raise ValueError(f"Duplicate image path: {record['image']}")
        image_paths.add(image_path)
        masks = record.get("masks")
        if not isinstance(masks, list) or any(not isinstance(p, str) for p in masks):
            raise ValueError("Record masks must be a list of relative PNG paths")
        if len(set(masks)) != len(masks):
            raise ValueError("Duplicate mask path in record")
        if "implicit_zero_mask" in record and (
            type(record["implicit_zero_mask"]) is not bool
            or record["implicit_zero_mask"] != (not masks)
        ):
            raise ValueError("Invalid implicit_zero_mask flag")
        image, union = _load_pixels(root, record)
        pixels, mask_pixels = _pixel_digest(image), _pixel_digest(union)
        group_id, split = record["group_id"], record["split"]
        if pixels in pixel_groups and pixel_groups[pixels] != group_id:
            raise ValueError("Duplicate image pixels must share the same group_id")
        pixel_groups[pixels] = group_id
        if group_id in group_details:
            old_pixels, old_mask, old_split = group_details[group_id]
            if old_pixels != pixels:
                raise ValueError("A duplicate group contains different image pixels")
            if old_mask != mask_pixels:
                raise ValueError("Contradictory union masks for duplicate image")
            if old_split != split:
                raise ValueError("A duplicate group cannot cross splits")
        group_details[group_id] = pixels, mask_pixels, split
        seen_splits[record["category"]].add(split)
    if any(splits != set(SPLITS) for splits in seen_splits.values()):
        raise ValueError("Each category must be represented in train, val and test")
    return manifest


class SegmentationDataset:
    """Torch-compatible map dataset, with no torch requirement for preparation.

    Flips are paired, training-only, and deterministic for (seed, epoch, ID),
    independent of worker/access order. Call set_epoch before each train epoch.
    """

    def __init__(
        self,
        manifest_path: Path | str,
        split: str,
        *,
        input_height: int = 256,
        input_width: int = 256,
        in_channels: int = 1,
        augment: bool = False,
        seed: int = 42,
    ) -> None:
        if split not in SPLITS:
            raise ValueError("split must be train, val or test")
        for name, value in (
            ("input_height", input_height),
            ("input_width", input_width),
            ("in_channels", in_channels),
        ):
            _integer(value, name, minimum=1)
        _integer(seed, "seed")
        if type(augment) is not bool:
            raise ValueError("augment must be a boolean")
        self.manifest = load_manifest(manifest_path)
        self.data_root = Path(self.manifest["data_root"])
        self.records = [r for r in self.manifest["records"] if r["split"] == split]
        self.split = split
        self.input_height = input_height
        self.input_width = input_width
        self.in_channels = in_channels
        self.augment = augment and split == "train"
        self.seed = seed
        self._epoch = 0

    def __len__(self) -> int:
        return len(self.records)

    def set_epoch(self, epoch: int) -> None:
        _integer(epoch, "epoch", minimum=0)
        self._epoch = epoch

    def load_original(self, index: int) -> tuple[np.ndarray, np.ndarray]:
        image, mask = _load_pixels(self.data_root.resolve(), self.records[index])
        return image.astype(np.float32) / 255.0, mask

    def __getitem__(self, index: int) -> dict[str, Any]:
        import torch

        from app.preprocessing.transforms import prepare_tensor, resize_mask

        record = self.records[index]
        image, mask = self.load_original(index)
        if self.augment:
            rng = random.Random(f"{self.seed}:{self._epoch}:{record['id']}")
            for axis in (1, 0):
                if rng.random() < 0.5:
                    image, mask = np.flip(image, axis=axis), np.flip(mask, axis=axis)
        tensor = prepare_tensor(
            np.ascontiguousarray(image),
            self.input_height,
            self.input_width,
            channels=self.in_channels,
        )
        target = resize_mask(np.ascontiguousarray(mask), self.input_height, self.input_width)
        return {
            "image": torch.from_numpy(np.ascontiguousarray(tensor, dtype=np.float32)),
            "mask": torch.from_numpy(np.ascontiguousarray(target[None], dtype=np.float32)),
            "id": record["id"],
        }
