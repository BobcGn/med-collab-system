from __future__ import annotations

import copy
import importlib.util
import io
import json
import shutil
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch

import numpy as np
from PIL import Image

from app.training.data import CATEGORIES, SPLITS, SegmentationDataset, build_manifest, load_manifest
from app.training.prepare import main

_HAS_TORCH = importlib.util.find_spec("torch") is not None


class TrainingDataTestCase(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / "busi"
        self.output = Path(self.temporary.name) / "manifest.json"
        self._fixture()

    def _fixture(self, size: int = 3) -> None:
        if self.root.exists():
            shutil.rmtree(self.root)
        for category_index, category in enumerate(CATEGORIES):
            directory = self.root / category
            directory.mkdir(parents=True)
            for index in range(size):
                image = np.zeros((8, 10), dtype=np.uint8)
                image[0, 0] = category_index * 30 + index + 1
                mask = np.zeros_like(image)
                if category != "normal":
                    mask[1:4, 2:6] = 255
                    image[mask > 0] = 255
                    Image.fromarray(mask).save(directory / f"{category}_{index}_mask.png")
                Image.fromarray(image).save(directory / f"{category}_{index}.png", compress_level=9)

    def _build(self, **overrides) -> dict:
        options = {
            "source_url": "https://example.org/synthetic-busi",
            "license_name": "Synthetic test fixture only",
            "source_verified": True,
        }
        options.update(overrides)
        return build_manifest(self.root, self.output, **options)

    def _write_manifest(self, manifest: dict) -> None:
        self.output.write_text(json.dumps(manifest), encoding="utf-8")

    def _duplicate(self, category: str = "benign", source_category: str = "benign") -> None:
        source = self.root / source_category / f"{source_category}_0.png"
        with Image.open(source) as image:
            image.save(self.root / category / "duplicate.png", compress_level=0)
        if source_category != "normal":
            shutil.copyfile(
                self.root / source_category / f"{source_category}_0_mask.png",
                self.root / category / "duplicate_mask.png",
            )

    def test_manual_source_consent_and_metadata_are_required(self) -> None:
        for options in (
            {"source_verified": False},
            {"source_verified": 1},
            {"source_url": ""},
            {"source_url": "file:///tmp/source"},
            {"source_url": "https://user:password@example.org/source"},
            {"license_name": "  "},
        ):
            with self.subTest(options=options), self.assertRaises(ValueError):
                self._build(**options)
            self.assertFalse(self.output.exists())

    def test_manifest_union_normal_zero_and_schema_round_trip(self) -> None:
        second = np.zeros((8, 10), dtype=np.uint8)
        second[5:7, 7:9] = 1
        Image.fromarray(second).save(self.root / "benign/benign_0_mask_1.png")
        Image.fromarray(np.zeros_like(second)).save(self.root / "normal/normal_1_mask.png")
        manifest = self._build()
        self.assertEqual(load_manifest(self.output), manifest)
        self.assertEqual(manifest["schema_version"], 1)
        self.assertEqual(manifest["data_root"], str(self.root.resolve()))
        self.assertEqual(manifest["source"]["verification"], "user-confirmed")
        self.assertEqual(manifest["split_unit"], "duplicate_group")
        self.assertIs(manifest["patient_independence_verified"], False)
        self.assertEqual(manifest["seed"], 42)
        self.assertEqual(len(manifest["records"]), 9)
        self.assertFalse(any("_mask" in r["image"] for r in manifest["records"]))
        record = next(r for r in manifest["records"] if r["id"] == "benign/benign_0")
        self.assertEqual(len(record["masks"]), 2)
        dataset = SegmentationDataset(self.output, record["split"])
        index = next(i for i, r in enumerate(dataset.records) if r["id"] == record["id"])
        image, mask = dataset.load_original(index)
        self.assertEqual(image.dtype, np.float32)
        self.assertEqual(mask.dtype, np.uint8)
        self.assertEqual(image.shape, (8, 10))
        self.assertGreaterEqual(float(image.min()), 0.0)
        self.assertLessEqual(float(image.max()), 1.0)
        self.assertEqual(set(np.unique(mask)), {0, 1})
        self.assertEqual(int(mask.sum()), 16)
        for split in SPLITS:
            dataset = SegmentationDataset(self.output, split)
            normal_index = next(
                i for i, r in enumerate(dataset.records) if r["category"] == "normal"
            )
            normal_record = dataset.records[normal_index]
            self.assertEqual(normal_record["implicit_zero_mask"], not normal_record["masks"])
            self.assertEqual(int(dataset.load_original(normal_index)[1].sum()), 0)

    def test_deterministic_stratified_group_counts_and_explicit_ratios(self) -> None:
        self._fixture(size=10)
        options = {"seed": 19, "train_fraction": 0.5, "val_fraction": 0.25}
        first = self._build(**options)
        text = self.output.read_text()
        self.assertEqual(self._build(**options), first)
        self.assertEqual(self.output.read_text(), text)
        for category in CATEGORIES:
            counts = {
                s: len(
                    {
                        r["group_id"]
                        for r in first["records"]
                        if r["category"] == category and r["split"] == s
                    }
                )
                for s in SPLITS
            }
            self.assertEqual(counts, {"train": 5, "val": 3, "test": 2})
        self.assertNotEqual(self._build(**(options | {"seed": 20}))["records"], first["records"])
        default = self._build()
        for category in CATEGORIES:
            counts = [
                sum(r["category"] == category and r["split"] == s for r in default["records"])
                for s in SPLITS
            ]
            self.assertEqual(counts, [7, 2, 1])

    def test_tiny_fixture_has_each_category_in_each_split(self) -> None:
        manifest = self._build()
        for category in CATEGORIES:
            self.assertEqual(
                {r["split"] for r in manifest["records"] if r["category"] == category},
                set(SPLITS),
            )

    def test_too_few_distinct_groups_fails_without_writing_manifest(self) -> None:
        self._fixture(size=2)
        with self.assertRaisesRegex(ValueError, "at least 3 distinct"):
            self._build()
        self.assertFalse(self.output.exists())
        self._fixture()
        for suffix in (".png", "_mask.png"):
            shutil.copyfile(
                self.root / f"benign/benign_0{suffix}",
                self.root / f"benign/benign_2{suffix}",
            )
        with self.assertRaisesRegex(ValueError, "at least 3 distinct"):
            self._build()

    def test_duplicate_decoded_pixels_stay_together_despite_different_png_bytes(self) -> None:
        self._duplicate()
        self.assertNotEqual(
            (self.root / "benign/benign_0.png").read_bytes(),
            (self.root / "benign/duplicate.png").read_bytes(),
        )
        manifest = self._build()
        records = [
            r for r in manifest["records"] if r["id"] in {"benign/benign_0", "benign/duplicate"}
        ]
        self.assertEqual(len(records), 2)
        self.assertEqual(len({r["group_id"] for r in records}), 1)
        self.assertEqual(len({r["split"] for r in records}), 1)
        self.assertEqual(load_manifest(self.output), manifest)

    def test_duplicate_group_can_span_categories(self) -> None:
        self._duplicate(category="malignant")
        manifest = self._build()
        records = [
            r for r in manifest["records"] if r["id"] in {"benign/benign_0", "malignant/duplicate"}
        ]
        self.assertEqual(len({r["group_id"] for r in records}), 1)
        self.assertEqual(len({r["split"] for r in records}), 1)
        self.assertEqual(load_manifest(self.output), manifest)

    def test_duplicate_contradictory_union_masks_are_rejected(self) -> None:
        self._duplicate()
        Image.fromarray(np.zeros((8, 10), dtype=np.uint8)).save(
            self.root / "benign/duplicate_mask.png"
        )
        with self.assertRaisesRegex(ValueError, "Contradictory union masks"):
            self._build()

    def test_positive_images_require_masks(self) -> None:
        for category in ("benign", "malignant"):
            with self.subTest(category=category):
                self._fixture()
                (self.root / category / f"{category}_0_mask.png").unlink()
                with self.assertRaisesRegex(ValueError, "Positive image has no mask"):
                    self._build()

    def test_corrupt_images_and_masks_are_rejected(self) -> None:
        for name in ("normal/normal_0.png", "benign/benign_0_mask.png"):
            with self.subTest(name=name):
                self._fixture()
                (self.root / name).write_bytes(b"not a PNG")
                with self.assertRaisesRegex(ValueError, "Unreadable or invalid PNG"):
                    self._build()

    def test_mask_dimensions_and_binary_values_are_checked(self) -> None:
        for mask, error in (
            (np.zeros((7, 10), dtype=np.uint8), "dimensions"),
            (np.full((8, 10), 127, dtype=np.uint8), "binary"),
        ):
            with self.subTest(error=error):
                Image.fromarray(mask).save(self.root / "benign/benign_0_mask.png")
                with self.assertRaisesRegex(ValueError, error):
                    self._build()

    def test_orphan_masks_are_not_silently_ignored(self) -> None:
        Image.fromarray(np.zeros((8, 10), dtype=np.uint8)).save(
            self.root / "normal/missing_mask_1.png"
        )
        with self.assertRaisesRegex(ValueError, "no matching original"):
            self._build()

    def test_invalid_ratios_and_seed_fail(self) -> None:
        for options in (
            {"train_fraction": 0},
            {"val_fraction": -0.1},
            {"train_fraction": 0.9, "val_fraction": 0.2},
            {"train_fraction": float("nan")},
            {"val_fraction": float("inf")},
            {"train_fraction": True},
            {"seed": True},
            {"seed": 1.5},
        ):
            with self.subTest(options=options), self.assertRaises(ValueError):
                self._build(**options)

    def test_load_rejects_duplicate_ids_and_invalid_schema(self) -> None:
        valid = self._build()
        duplicate = copy.deepcopy(valid)
        duplicate["records"].append(copy.deepcopy(duplicate["records"][0]))
        self._write_manifest(duplicate)
        with self.assertRaisesRegex(ValueError, "Duplicate image ID"):
            load_manifest(self.output)
        for key, value in (
            ("schema_version", 2),
            ("schema_version", True),
            ("patient_independence_verified", True),
            ("split_unit", "patient"),
            ("seed", "42"),
            ("records", []),
            ("data_root", "relative/path"),
            ("source", {"verification": "automatically-verified"}),
        ):
            with self.subTest(key=key, value=value):
                manifest = copy.deepcopy(valid)
                manifest[key] = value
                self._write_manifest(manifest)
                with self.assertRaises(ValueError):
                    load_manifest(self.output)

    def test_load_rejects_path_traversal_for_images_and_masks(self) -> None:
        valid = self._build()
        for field in ("image", "masks"):
            for unsafe in (
                "../outside.png",
                "/tmp/outside.png",
                "benign/../../outside.png",
                "C:/outside.png",
                "benign\\outside.png",
                "benign/./benign_0.png",
            ):
                with self.subTest(field=field, path=unsafe):
                    manifest = copy.deepcopy(valid)
                    manifest["records"][0][field] = [unsafe] if field == "masks" else unsafe
                    self._write_manifest(manifest)
                    with self.assertRaises(ValueError):
                        load_manifest(self.output)

    def test_build_and_load_reject_symlinks_escaping_root(self) -> None:
        valid = self._build()
        outside = Path(self.temporary.name) / "outside.png"
        Image.fromarray(np.zeros((8, 10), dtype=np.uint8)).save(outside)
        link = self.root / "normal/escape.png"
        link.symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "escapes data_root"):
            self._build()
        manifest = copy.deepcopy(valid)
        manifest["records"][0]["image"] = "normal/escape.png"
        self._write_manifest(manifest)
        with self.assertRaisesRegex(ValueError, "escapes data_root"):
            load_manifest(self.output)

    def test_load_checks_group_membership_and_prevents_cross_split_duplicates(self) -> None:
        self._duplicate()
        valid = self._build()
        for field in ("split", "group_id"):
            with self.subTest(field=field):
                manifest = copy.deepcopy(valid)
                duplicate = next(r for r in manifest["records"] if r["id"] == "benign/duplicate")
                duplicate[field] = (
                    next(s for s in SPLITS if s != duplicate["split"])
                    if field == "split"
                    else "forged-other-group"
                )
                self._write_manifest(manifest)
                with self.assertRaisesRegex(ValueError, "cross splits|same group_id"):
                    load_manifest(self.output)
        manifest = copy.deepcopy(valid)
        manifest["records"][1]["group_id"] = manifest["records"][0]["group_id"]
        self._write_manifest(manifest)
        with self.assertRaisesRegex(ValueError, "different image pixels"):
            load_manifest(self.output)

    def test_load_rechecks_union_masks_for_duplicates(self) -> None:
        self._duplicate()
        self._build()
        Image.fromarray(np.zeros((8, 10), dtype=np.uint8)).save(
            self.root / "benign/duplicate_mask.png"
        )
        with self.assertRaisesRegex(ValueError, "Contradictory union masks"):
            load_manifest(self.output)

    def test_cli_requires_manual_consent_and_reports_class_split_counts(self) -> None:
        args = [
            "--data-root",
            str(self.root),
            "--output",
            str(self.output),
            "--source-url",
            "https://example.org/synthetic-busi",
            "--license",
            "Synthetic test fixture only",
            "--seed",
            "7",
            "--train-fraction",
            "0.7",
            "--val-fraction",
            "0.15",
        ]
        with redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as raised:
            main(args)
        self.assertEqual(raised.exception.code, 2)
        self.assertFalse(self.output.exists())
        output = io.StringIO()
        with redirect_stdout(output):
            self.assertEqual(main([*args, "--source-verified"]), 0)
        self.assertEqual(load_manifest(self.output)["seed"], 7)
        for split in SPLITS:
            self.assertIn(
                f"{split}: images=3 groups=3 benign=1 malignant=1 normal=1", output.getvalue()
            )
        self.assertIn("patient independence is NOT verified", output.getvalue())
        self.assertIn("no automatic web verification", output.getvalue())

    def test_cli_manifest_failures_exit_without_success_output(self) -> None:
        args = [
            "--data-root", str(self.root),
            "--output", str(self.output),
            "--source-url", "https://example.org/synthetic-busi",
            "--license", "Synthetic test fixture only",
            "--source-verified",
        ]
        for error in (OSError("cannot read fixture"), ValueError("invalid fixture")):
            with self.subTest(error=type(error).__name__):
                stdout, stderr = io.StringIO(), io.StringIO()
                with (
                    patch("app.training.prepare.build_manifest", side_effect=error) as builder,
                    redirect_stdout(stdout),
                    redirect_stderr(stderr),
                    self.assertRaises(SystemExit) as raised,
                ):
                    main(args)
                builder.assert_called_once()
                self.assertEqual(raised.exception.code, 2)
                self.assertEqual(stdout.getvalue(), "")
                self.assertIn(str(error), stderr.getvalue())
                self.assertIn("error:", stderr.getvalue())
                self.assertFalse(self.output.exists())

    def test_dataset_rejects_invalid_configuration(self) -> None:
        self._build()
        for options in ({"input_height": 0}, {"input_width": -1}, {"in_channels": 0}):
            with self.subTest(options=options), self.assertRaises(ValueError):
                SegmentationDataset(self.output, "train", **options)
        with self.assertRaises(ValueError):
            SegmentationDataset(self.output, "validation")
        with self.assertRaises(ValueError):
            SegmentationDataset(self.output, "train").set_epoch(-1)

    @unittest.skipUnless(_HAS_TORCH, "torch is not installed")
    def test_dataset_tensor_contract_and_binary_nearest_resizing(self) -> None:
        import torch

        self._build()
        dataset = SegmentationDataset(
            self.output, "train", input_height=16, input_width=20, in_channels=2
        )
        sample = dataset[0]
        self.assertEqual(len(dataset), 3)
        self.assertEqual(dataset.data_root, self.root.resolve())
        self.assertEqual(sample["id"], dataset.records[0]["id"])
        self.assertEqual(sample["image"].dtype, torch.float32)
        self.assertEqual(sample["mask"].dtype, torch.float32)
        self.assertEqual(tuple(sample["image"].shape), (2, 16, 20))
        self.assertEqual(tuple(sample["mask"].shape), (1, 16, 20))
        self.assertTrue(torch.equal(sample["image"][0], sample["image"][1]))
        self.assertTrue(set(sample["mask"].unique().tolist()).issubset({0.0, 1.0}))
        self.assertGreaterEqual(float(sample["image"].min()), -1.0)
        self.assertLessEqual(float(sample["image"].max()), 1.0)

    @unittest.skipUnless(_HAS_TORCH, "torch is not installed")
    def test_training_flips_are_paired_reproducible_and_vary_by_epoch(self) -> None:
        import torch

        self._build()
        options = {"input_height": 8, "input_width": 10, "augment": True, "seed": 91}
        dataset = SegmentationDataset(self.output, "train", **options)
        other = SegmentationDataset(self.output, "train", **options)
        original = dataset.load_original(0)
        variants = set()
        for epoch in range(16):
            dataset.set_epoch(epoch)
            other.set_epoch(epoch)
            for index in range(len(dataset)):
                sample = dataset[index]
                self.assertTrue(torch.equal(sample["image"], other[index]["image"]))
                self.assertTrue(torch.equal(sample["mask"], other[index]["mask"]))
                np.testing.assert_array_equal(
                    sample["image"][0].numpy() > 0, sample["mask"][0].numpy() > 0
                )
            variants.add(dataset[0]["image"].numpy().tobytes())
        self.assertGreater(len(variants), 1)
        np.testing.assert_array_equal(original[0], dataset.load_original(0)[0])
        np.testing.assert_array_equal(original[1], dataset.load_original(0)[1])

    @unittest.skipUnless(_HAS_TORCH, "torch is not installed")
    def test_validation_and_test_never_augment_even_when_requested(self) -> None:
        import torch

        self._build()
        for split in ("val", "test"):
            with self.subTest(split=split):
                dataset = SegmentationDataset(
                    self.output, split, input_height=8, input_width=10, augment=True
                )
                self.assertFalse(dataset.augment)
                initial = dataset[0]
                for epoch in range(4):
                    dataset.set_epoch(epoch)
                    self.assertTrue(torch.equal(initial["image"], dataset[0]["image"]))
                    self.assertTrue(torch.equal(initial["mask"], dataset[0]["mask"]))
