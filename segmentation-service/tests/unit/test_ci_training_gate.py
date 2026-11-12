from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import pytest

SCRIPT_PATH = Path(__file__).resolve().parents[3] / ".github/scripts/check_python_training_tests.py"
spec = importlib.util.spec_from_file_location("check_python_training_tests", SCRIPT_PATH)
assert spec is not None and spec.loader is not None
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def _case(classname: str, outcome: str | None = None) -> ET.Element:
    case = ET.Element("testcase", classname=classname, name="test_synthetic")
    if outcome is not None:
        ET.SubElement(case, outcome)
    return case


def _write_report(tmp_path: Path, cases: list[ET.Element], root_tag: str = "testsuites") -> Path:
    root = ET.Element(root_tag)
    suite = ET.SubElement(root, "testsuite") if root_tag == "testsuites" else root
    suite.extend(cases)
    path = tmp_path / "pytest.xml"
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)
    return path


@pytest.fixture
def core_cases() -> list[ET.Element]:
    return [_case(module) for module in gate.REQUIRED_MODULES]


def test_required_modules_contract() -> None:
    assert gate.REQUIRED_MODULES == (
        "tests.unit.test_training_smoke",
        "tests.unit.test_model_bundle",
        "tests.unit.test_training_metrics",
        "tests.integration.test_model_bundle_routes",
    )


@pytest.mark.parametrize("root_tag", ["testsuites", "testsuite"])
@pytest.mark.parametrize("suffix", ["", ".TestTraining"])
def test_passing_reports_count_each_core_module(tmp_path, root_tag, suffix) -> None:
    cases = [
        _case(module + suffix)
        for index, module in enumerate(gate.REQUIRED_MODULES, start=1)
        for _ in range(index)
    ]
    summary = gate.check_report(_write_report(tmp_path, cases, root_tag))
    for index, module in enumerate(gate.REQUIRED_MODULES, start=1):
        assert summary["modules"][module] == {
            "tests": index,
            "passed": index,
            "skipped": 0,
            "failures": 0,
            "errors": 0,
        }
    assert summary["total"] == {
        "tests": len(cases),
        "passed": len(cases),
        "skipped": 0,
        "failures": 0,
        "errors": 0,
    }


def test_core_cases_across_multiple_suites(tmp_path, core_cases) -> None:
    path = _write_report(tmp_path, [])
    root = ET.Element("testsuites")
    for case in core_cases:
        ET.SubElement(root, "testsuite").append(case)
    ET.ElementTree(root).write(path)
    assert gate.check_report(path)["total"]["passed"] == len(core_cases)


@pytest.mark.parametrize("missing_module", gate.REQUIRED_MODULES)
def test_missing_core_module_is_rejected(tmp_path, core_cases, missing_module) -> None:
    cases = [case for case in core_cases if case.get("classname") != missing_module]
    cases.append(_case("tests.unit.test_optional"))
    with pytest.raises(ValueError, match="no test cases") as exc:
        gate.check_report(_write_report(tmp_path, cases))
    assert missing_module in str(exc.value)


@pytest.mark.parametrize("classname", ["", "renamed", "suffix", "prefix"])
def test_import_skip_or_similarly_named_module_cannot_replace_core(
    tmp_path, core_cases, classname
) -> None:
    module = gate.REQUIRED_MODULES[0]
    replacement = {
        "": "",
        "renamed": module + "_renamed",
        "suffix": module + "Extra",
        "prefix": "other." + module,
    }[classname]
    core_cases[0].set("classname", replacement)
    if not replacement:
        core_cases[0].set("name", module.replace(".", "/") + ".py")
        ET.SubElement(core_cases[0], "skipped", message="could not import torch")
    with pytest.raises(ValueError, match="no test cases") as exc:
        gate.check_report(_write_report(tmp_path, core_cases))
    assert module in str(exc.value)


@pytest.mark.parametrize("module", gate.REQUIRED_MODULES)
@pytest.mark.parametrize("outcome", ["skipped", "failure", "error"])
def test_any_unsuccessful_core_case_is_rejected(tmp_path, core_cases, module, outcome) -> None:
    core_cases.append(_case(module + ".TestTraining", outcome))
    with pytest.raises(ValueError) as exc:
        gate.check_report(_write_report(tmp_path, core_cases))
    assert module in str(exc.value)
    assert outcome in str(exc.value)


@pytest.mark.parametrize("outcome", ["failure", "error"])
def test_noncore_failure_or_error_is_rejected(tmp_path, core_cases, outcome) -> None:
    core_cases.append(_case("tests.unit.test_other", outcome))
    path = _write_report(tmp_path, core_cases)
    root = ET.parse(path).getroot()
    root.set("failures", "0")
    root.set("errors", "0")
    ET.ElementTree(root).write(path)
    with pytest.raises(ValueError, match="report contains failures or errors"):
        gate.check_report(path)


@pytest.mark.parametrize("outcome", ["failure", "error"])
def test_suite_level_failure_or_error_is_rejected(tmp_path, core_cases, outcome) -> None:
    path = _write_report(tmp_path, core_cases, "testsuite")
    root = ET.parse(path).getroot()
    ET.SubElement(root, outcome)
    ET.ElementTree(root).write(path)
    with pytest.raises(ValueError, match="report contains failures or errors"):
        gate.check_report(path)


@pytest.mark.parametrize("root_tag", ["testsuites", "testsuite"])
@pytest.mark.parametrize("outcome", ["failures", "errors"])
def test_declared_global_failures_or_errors_are_rejected(
    tmp_path, core_cases, root_tag, outcome
) -> None:
    path = _write_report(tmp_path, core_cases, root_tag)
    root = ET.parse(path).getroot()
    root.set(outcome, "1")
    ET.ElementTree(root).write(path)
    with pytest.raises(ValueError, match=f"report declares 1 {outcome}"):
        gate.check_report(path)


@pytest.mark.parametrize("value", ["invalid", "-1"])
def test_invalid_failure_count_is_rejected(tmp_path, core_cases, value) -> None:
    path = _write_report(tmp_path, core_cases)
    root = ET.parse(path).getroot()
    root.set("failures", value)
    ET.ElementTree(root).write(path)
    with pytest.raises(ValueError, match="Invalid JUnit failures count"):
        gate.check_report(path)


@pytest.mark.parametrize(
    ("content", "message"),
    [
        ("", "Cannot read JUnit report"),
        ("<testsuites>", "Cannot read JUnit report"),
        ('<?xml version="1.0" encoding="unknown"?><testsuites />', "Cannot read JUnit report"),
        ("<report />", "Unexpected JUnit root"),
        ("<testsuites />", "no test cases"),
        ('<testsuite tests="42" />', "no test cases"),
    ],
)
def test_empty_malformed_or_unexpected_report_is_rejected(tmp_path, content, message) -> None:
    path = tmp_path / "pytest.xml"
    path.write_text(content)
    with pytest.raises(ValueError, match=message):
        gate.check_report(path)


@pytest.mark.parametrize("is_directory", [False, True])
def test_unreadable_report_is_rejected(tmp_path, is_directory) -> None:
    path = tmp_path / "pytest.xml"
    if is_directory:
        path.mkdir()
    with pytest.raises(ValueError, match="Cannot read JUnit report"):
        gate.check_report(path)


def test_optional_noncore_skips_are_allowed_and_counted(tmp_path, core_cases) -> None:
    cases = [
        *core_cases,
        _case("tests.integration.test_local_only", "skipped"),
        _case("", "skipped"),
    ]
    summary = gate.check_report(_write_report(tmp_path, cases))
    assert summary["total"]["tests"] == len(cases)
    assert summary["total"]["passed"] == len(core_cases)
    assert summary["total"]["skipped"] == 2
    assert all(counts["skipped"] == 0 for counts in summary["modules"].values())


def test_main_success_reports_counts(tmp_path, core_cases, capsys) -> None:
    path = _write_report(tmp_path, core_cases)
    assert gate.main([str(path)]) == 0
    captured = capsys.readouterr()
    assert json.loads(captured.out) == gate.check_report(path)
    assert captured.err == ""


def test_main_failure_returns_nonzero_and_explains_error(tmp_path, capsys) -> None:
    path = _write_report(tmp_path, [])
    assert gate.main([str(path)]) == 1
    captured = capsys.readouterr()
    assert captured.out == ""
    assert "Training test gate failed" in captured.err
    assert "no test cases" in captured.err


@pytest.mark.parametrize("passing", [False, True])
def test_cli_exit_status(tmp_path, core_cases, passing) -> None:
    path = _write_report(tmp_path, core_cases if passing else [])
    result = subprocess.run(
        [sys.executable, str(SCRIPT_PATH), str(path)],
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == (0 if passing else 1)
    if passing:
        assert json.loads(result.stdout)["total"]["passed"] == len(core_cases)
        assert result.stderr == ""
    else:
        assert result.stdout == ""
        assert "no test cases" in result.stderr
