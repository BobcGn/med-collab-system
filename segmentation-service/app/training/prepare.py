"""Prepare local BUSI data only; no downloads, archive extraction or training."""

from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path
from typing import Sequence

from app.training.data import CATEGORIES, SPLITS, build_manifest


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--data-root",
        type=Path,
        required=True,
        help="Already-extracted local directory containing benign/, malignant/, normal/ PNGs",
    )
    parser.add_argument("--output", type=Path, required=True, help="Output JSON manifest path")
    parser.add_argument("--source-url", required=True, help="Manually reviewed public source URL")
    parser.add_argument(
        "--license", dest="license_name", required=True, help="Reviewed license name"
    )
    parser.add_argument(
        "--source-verified",
        action="store_true",
        required=True,
        help="Explicitly attest that you manually verified the public source and license",
    )
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--train-fraction", type=float, default=0.7)
    parser.add_argument("--val-fraction", type=float, default=0.15)
    args = parser.parse_args(argv)
    try:
        manifest = build_manifest(
            args.data_root,
            args.output,
            source_url=args.source_url,
            license_name=args.license_name,
            source_verified=args.source_verified,
            seed=args.seed,
            train_fraction=args.train_fraction,
            val_fraction=args.val_fraction,
        )
    except (OSError, ValueError) as exc:
        parser.error(str(exc))
    print(f"Manifest: {args.output.resolve()}")
    print("Source/license: user-confirmed only; no automatic web verification.")
    print("Split unit: duplicate_group; patient independence is NOT verified.")
    for split in SPLITS:
        records = [r for r in manifest["records"] if r["split"] == split]
        counts = Counter(r["category"] for r in records)
        groups = len({r["group_id"] for r in records})
        classes = " ".join(f"{category}={counts[category]}" for category in CATEGORIES)
        print(f"{split}: images={len(records)} groups={groups} {classes}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
