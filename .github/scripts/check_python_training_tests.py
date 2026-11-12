from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REQUIRED_MODULES = (
    "tests.unit.test_training_smoke",
    "tests.unit.test_model_bundle",
    "tests.unit.test_training_metrics",
    "tests.integration.test_model_bundle_routes",
)


def _counts(cases: list[ET.Element]) -> dict[str, int]:
    counts = {"tests": len(cases), "passed": 0, "skipped": 0, "failures": 0, "errors": 0}
    for case in cases:
        outcomes = {
            "skipped": case.find("skipped") is not None,
            "failures": case.find("failure") is not None,
            "errors": case.find("error") is not None,
        }
        counts["passed"] += not any(outcomes.values())
        for outcome, present in outcomes.items():
            counts[outcome] += present
    return counts


def check_report(path: Path) -> dict:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError, LookupError) as exc:
        raise ValueError(f"Cannot read JUnit report {path}: {exc}") from exc
    if root.tag not in {"testsuites", "testsuite"}:
        raise ValueError(f"Unexpected JUnit root: {root.tag!r}")

    cases = list(root.iter("testcase"))
    if not cases:
        raise ValueError("JUnit report contains no test cases")

    modules = {}
    problems = []
    for module in REQUIRED_MODULES:
        module_cases = [
            case
            for case in cases
            if case.get("classname", "") == module
            or case.get("classname", "").startswith(module + ".")
        ]
        counts = modules[module] = _counts(module_cases)
        if not module_cases:
            problems.append(f"{module}: no test cases (missing or renamed module)")
        elif counts["skipped"] or counts["failures"] or counts["errors"]:
            problems.append(
                f"{module}: {counts['skipped']} skipped, "
                f"{counts['failures']} failures, {counts['errors']} errors"
            )

    if any(element.tag in {"failure", "error"} for element in root.iter()):
        problems.append("JUnit report contains failures or errors")
    for suite in root.iter():
        if suite.tag not in {"testsuite", "testsuites"}:
            continue
        for outcome in ("failures", "errors"):
            value = suite.get(outcome, "0")
            try:
                count = int(value)
            except ValueError as exc:
                raise ValueError(f"Invalid JUnit {outcome} count: {value!r}") from exc
            if count < 0:
                raise ValueError(f"Invalid JUnit {outcome} count: {value!r}")
            if count:
                problems.append(f"JUnit report declares {count} {outcome}")
    if problems:
        raise ValueError("; ".join(problems))
    return {"modules": modules, "total": _counts(cases)}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Require passing core Python training tests.")
    parser.add_argument("report", type=Path, help="pytest JUnit XML report")
    args = parser.parse_args(argv)
    try:
        summary = check_report(args.report)
    except ValueError as exc:
        print(f"Training test gate failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
