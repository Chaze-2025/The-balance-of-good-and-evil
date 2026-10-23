#!/usr/bin/env python3
"""Publish portable test evidence without confusing unit reports with Minecraft runs.

This utility never launches Minecraft or changes an EULA file. Runtime results
require a completed launcher manifest, matching SHA-256, and fresh result-file
timestamps. A normal build can therefore publish an explicit NOT RUN report.
"""
from __future__ import annotations

import argparse
from datetime import datetime
import hashlib
import html
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
COUNTS = ("tests", "failures", "errors", "skipped")
NOT_RUN_REASON = "Minecraft GameTests were not run; explicit owner EULA authorization is required."


def local_path(root: Path, value: str | Path) -> Path:
    path = Path(value)
    path = (root / path).resolve() if not path.is_absolute() else path.resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError("Evidence and result paths must stay inside the repository")
    return path


def xml_counts(path: Path) -> dict:
    data = path.read_bytes()
    if b"\x00" in data or b"<!DOCTYPE" in data.upper() or b"<!ENTITY" in data.upper():
        raise ValueError("DTD and entity declarations are not permitted in test reports")
    document = ET.fromstring(data)
    tag = lambda node: node.tag.rsplit("}", 1)[-1]
    if tag(document) not in ("testsuite", "testsuites"):
        raise ValueError("Expected a JUnit testsuite or testsuites document")
    cases = [node for node in document.iter() if tag(node) == "testcase"]
    counts = dict.fromkeys(COUNTS, 0)
    counts["tests"] = len(cases)
    for case in cases:
        outcomes = [tag(node) for node in case if tag(node) in ("failure", "error", "skipped")]
        if len(outcomes) > 1:
            raise ValueError("A testcase has conflicting or duplicate outcomes")
        if outcomes:
            counts[{"failure": "failures", "error": "errors", "skipped": "skipped"}[outcomes[0]]] += 1
    # Derive evidence from real testcase nodes, never trust an aggregate claim alone.
    for key in COUNTS:
        if key in document.attrib and int(document.attrib[key]) != counts[key]:
            raise ValueError(f"JUnit aggregate {key} does not match testcase evidence")
    return counts


def java_results(root: Path) -> dict:
    files = sorted((root / "build/test-results/test").glob("TEST-*.xml"))
    result = {"status": "not_run", "files": [], "problems": [], **dict.fromkeys(COUNTS, 0)}
    for path in files:
        result["files"].append(str(path.relative_to(root)))
        try:
            counts = xml_counts(local_path(root, path))
            for key in COUNTS:
                result[key] += counts[key]
        except (OSError, ValueError, ET.ParseError) as exc:
            result["problems"].append(f"{path.name}: {exc}")
    if result["problems"]:
        result["status"] = "invalid_report"
    elif result["failures"] or result["errors"]:
        result["status"] = "failed"
    elif result["tests"] > result["skipped"]:
        result["status"] = "passed"
    return result


def python_results(root: Path, log_path: Path | None) -> dict:
    result = {"status": "not_collected", **dict.fromkeys(COUNTS, 0)}
    path = local_path(root, log_path or root / "build/reports/cloud-testing/python-tests.log")
    if not path.exists():
        return result
    text = path.read_text(encoding="utf-8", errors="replace")
    runs = list(re.finditer(r"^Ran (\d+) tests? in [^\n]+$", text, re.MULTILINE))
    if not runs:
        return {**result, "status": "invalid_report", "reason": "No unittest completion line found"}
    last = runs[-1]
    result["tests"] = int(last.group(1))
    ending = text[last.end():].strip()
    outcome = re.fullmatch(r"(OK|FAILED)(?: \(([^\n]*)\))?", ending)
    if not outcome:
        return {**result, "status": "invalid_report", "reason": "No unambiguous unittest result follows its count"}
    details = outcome.group(2) or ""
    for key in ("failures", "errors", "skipped"):
        match = re.search(rf"(?:^|, )\b{key}=(\d+)\b", details)
        result[key] = int(match.group(1)) if match else 0
    result["status"] = ("failed" if outcome.group(1) == "FAILED" else
                        "passed" if result["tests"] > result["skipped"] else "not_run")
    return result


def timestamp(value) -> float:
    if not isinstance(value, str):
        raise ValueError("Runtime timestamps are required")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("Runtime timestamps must include a timezone")
    return parsed.timestamp()


def checked_file(root: Path, value, digest, started: float, finished: float) -> Path:
    if not isinstance(value, str) or not isinstance(digest, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", digest):
        raise ValueError("Result/evidence path and SHA-256 are required")
    path = local_path(root, value)
    modified = path.stat().st_mtime
    if not started - 2 <= modified <= finished + 2:
        raise ValueError("Result/evidence file is outside the recorded execution window")
    if hashlib.sha256(path.read_bytes()).hexdigest() != digest.lower():
        raise ValueError("Result/evidence SHA-256 does not match the execution manifest")
    return path


def runtime_results(root: Path, manifest_path: Path | None) -> dict:
    result = {"status": "not_run", "executed": False, "reason": NOT_RUN_REASON,
              "evidence": [], "problems": [], **{key: None for key in COUNTS}}
    path = manifest_path or root / "build/reports/gametest/run.json"
    try:
        path = local_path(root, path)
    except ValueError as exc:
        return {**result, "status": "invalid_report", "problems": [str(exc)]}
    if not path.exists():
        return result
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(manifest, dict):
            raise ValueError("Runtime manifest must be an object")
        status = manifest.get("status")
        result["reason"] = str(manifest.get("reason", ""))
        if status in ("not_run", "blocked", "not_started", "aborted"):
            return {**result, "status": status, "reason": result["reason"] or NOT_RUN_REASON}
        if status not in ("completed", "executed"):
            raise ValueError("Runtime manifest has no recognized execution status")
        started = timestamp(manifest.get("execution_started_at"))
        finished = timestamp(manifest.get("execution_finished_at"))
        if finished < started:
            raise ValueError("Runtime execution finished before it started")
        exit_code = manifest.get("java_exit_code")
        if not isinstance(exit_code, int) or isinstance(exit_code, bool):
            raise ValueError("A completed runtime manifest requires the Java exit code")
        result_file = checked_file(root, manifest.get("results_file"), manifest.get("results_sha256"), started, finished)
        counts = xml_counts(result_file)
        if counts["tests"] <= counts["skipped"]:
            raise ValueError("Runtime XML contains no executed testcases")
        result.update(counts)
        result.update(executed=True, java_exit_code=exit_code,
                      results_file=str(result_file.relative_to(root)),
                      status="failed" if exit_code or counts["failures"] or counts["errors"] else "passed")
        validation_errors = manifest.get("validation_errors", [])
        if not isinstance(validation_errors, list) or any(not isinstance(error, str) for error in validation_errors):
            raise ValueError("Runtime validation_errors must be a list of messages")
        if validation_errors:
            result["status"] = "failed"
            result["problems"].extend(validation_errors)
        expected = manifest.get("expected_tests", [])
        if not isinstance(expected, list) or any(not isinstance(name, str) for name in expected):
            raise ValueError("Runtime expected_tests must be a list of test names")
        if expected:
            result["expected_tests"] = expected
            if len(expected) != len(set(expected)) or counts["tests"] < len(expected):
                result["status"] = "failed"
                result["problems"].append("Runtime testcase count does not cover the unique expected test list")
        result["reason"] = ("Minecraft GameTest execution has matching launcher and XML evidence."
                            if result["status"] == "passed" else
                            "Minecraft GameTests ran, but their results or Java exit code indicate a failure.")
        evidence = manifest.get("evidence", [])
        if not isinstance(evidence, list):
            raise ValueError("Runtime evidence must be a list")
        for item in evidence:
            if not isinstance(item, dict) or item.get("kind") not in ("screenshot", "video"):
                raise ValueError("Unrecognized visual evidence record")
            file = checked_file(root, item.get("path"), item.get("sha256"), started, finished)
            data = file.read_bytes()
            if item["kind"] == "screenshot" and (file.suffix.lower() != ".png" or not data.startswith(b"\x89PNG\r\n\x1a\n")):
                raise ValueError("Screenshot evidence must be a PNG")
            if item["kind"] == "video" and (file.suffix.lower() != ".mp4" or data[4:8] != b"ftyp"):
                raise ValueError("Video evidence must be an MP4")
            result["evidence"].append({"path": str(file.relative_to(root)), "sha256": item["sha256"].lower(), "kind": item["kind"]})
    except (OSError, ValueError, TypeError, ET.ParseError) as exc:
        # Invalid provenance never becomes a passing run because old XML exists.
        result.update(status="invalid_report", executed=False)
        result["reason"] = "Runtime evidence was rejected; no gameplay execution claim is made."
        result["problems"].append(str(exc))
        for key in COUNTS:
            result[key] = None
        result["evidence"] = []
    return result


def collect_report(root: Path = ROOT, runtime_manifest: Path | None = None,
                   python_log: Path | None = None) -> dict:
    root = root.resolve()
    runtime = runtime_results(root, runtime_manifest)
    compiled = list((root / "build/classes/java/gametest").rglob("*.class"))
    return {"java_unit": java_results(root), "python": python_results(root, python_log),
            "minecraft_gametest": runtime,
            "gametest_compilation": {"status": "outputs_present" if compiled else "not_collected",
                                     "class_files": len(compiled)},
            "coverage": {"minecraft_world_tests": runtime["executed"],
                         "graphical_client": False, "real_multiplayer": False, "disk_persistence": False},
            "limits": ["Unit tests and synthetic fixtures do not establish Minecraft gameplay.",
                       "Headless GameTests do not establish rendered appearance or real multiplayer.",
                       "NBT round trips do not establish disk save/restart or actual chunk unloading.",
                       "This workflow has no verified graphical, real-client multiplayer, or disk-persistence runner."]}


def markdown_text(value) -> str:
    text = html.escape(str(value), quote=False).replace("\n", " ")
    return re.sub(r"([\\`*_{}\[\]()#+.!|<>-])", r"\\\1", text)


def render_markdown(report: dict) -> str:
    lines = ["## Cloud testing verification", "", "| Check | Status | Tests | Failures | Errors | Skipped |",
             "| --- | --- | ---: | ---: | ---: | ---: |"]
    for label, key in (("Java unit/command tests", "java_unit"), ("Python tooling tests", "python"),
                       ("Minecraft world GameTests", "minecraft_gametest")):
        value = report[key]
        row = [label, value["status"], *("—" if value[k] is None else value[k] for k in COUNTS)]
        lines.append("| " + " | ".join(markdown_text(v) for v in row) + " |")
    compilation = report["gametest_compilation"]
    lines.extend(["", markdown_text(report["minecraft_gametest"]["reason"]), "",
                  f"GameTest compiled class files present: **{compilation['class_files']}**. Compilation is not runtime evidence.", "",
                  "Graphical client: **not verified**. Real multiplayer: **not verified**. Disk persistence: **not verified**.", "",
                  "Download the cloud-test-reports artifact and open report.html for the portable report and available evidence."])
    for key in ("java_unit", "python", "minecraft_gametest"):
        for problem in report[key].get("problems", []):
            lines.append("\n- " + markdown_text(problem))
        if key == "python" and report[key].get("reason"):
            lines.append("\n- " + markdown_text(report[key]["reason"]))
    lines.extend(["", *("- " + markdown_text(limit) for limit in report["limits"])])
    return "\n".join(lines) + "\n"


def render_html(report: dict) -> str:
    escape = lambda value: html.escape(str(value), quote=True)
    rows = []
    for label, key in (("Java unit/command tests", "java_unit"), ("Python tooling tests", "python"),
                       ("Minecraft world GameTests", "minecraft_gametest")):
        value = report[key]
        cells = [label, value["status"], *("—" if value[k] is None else value[k] for k in COUNTS)]
        rows.append("<tr>" + "".join(f"<td>{escape(v)}</td>" for v in cells) + "</tr>")
    problems = [problem for key in ("java_unit", "python", "minecraft_gametest")
                for problem in report[key].get("problems", [])]
    if report["python"].get("reason"):
        problems.append(report["python"]["reason"])
    evidence = []
    for item in report["minecraft_gametest"]["evidence"]:
        path = escape(item.get("report_path", item["path"]))
        evidence.append(f'<figure><figcaption>{escape(item["path"])}</figcaption>'
                        + (f'<img src="{path}" alt="Recorded evidence">' if item["kind"] == "screenshot" else
                           f'<video src="{path}" controls preload="metadata"></video>') + "</figure>")
    return ("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\">"
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            "<title>Minecraft cloud testing verification</title>"
            "<style>body{font:16px system-ui,sans-serif;max-width:960px;margin:2em auto;padding:0 1em;line-height:1.5}"
            "table{border-collapse:collapse;width:100%;font-size:.9em}td,th{border:1px solid #bbb;padding:.5em;text-align:left}"
            "img,video{max-width:100%;height:auto}code{overflow-wrap:anywhere}</style>"
            "<h1>Cloud testing verification</h1><table><thead><tr>"
            "<th>Check</th><th>Status</th><th>Tests</th><th>Failures</th><th>Errors</th><th>Skipped</th>"
            "</tr></thead><tbody>" + "".join(rows) + "</tbody></table>"
            + "<p>" + escape(report["minecraft_gametest"]["reason"]) + "</p>"
            + "<p>GameTest compiled class files present: " + str(report["gametest_compilation"]["class_files"])
            + ". Compilation is not runtime evidence.</p>"
            + "<h2>Limits</h2><ul>" + "".join("<li>" + escape(v) + "</li>" for v in report["limits"]) + "</ul>"
            + "<h2>Problems</h2>" + ("<ul>" + "".join("<li>" + escape(v) + "</li>" for v in problems) + "</ul>" if problems else "<p>None reported.</p>")
            + "<h2>Available visual evidence</h2>"
            + ("".join(evidence) if evidence else "<p>No verified screenshot or video files were produced.</p>")
            + "<p>Evidence-file integrity alone does not prove graphical gameplay, real-client multiplayer, or persistence.</p></html>")


def write_report(report: dict, output: Path, root: Path = ROOT) -> None:
    output.mkdir(parents=True, exist_ok=True)
    for item in report["minecraft_gametest"]["evidence"]:
        source = local_path(root.resolve(), item["path"])
        destination = output / "evidence" / (item["sha256"][:12] + source.suffix.lower())
        destination.parent.mkdir(exist_ok=True)
        shutil.copyfile(source, destination)
        item["report_path"] = str(destination.relative_to(output))
    (output / "summary.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (output / "summary.md").write_text(render_markdown(report), encoding="utf-8")
    (output / "report.html").write_text(render_html(report), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--runtime-manifest", type=Path)
    parser.add_argument("--python-log", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    try:
        report = collect_report(root, args.runtime_manifest, args.python_log)
        output = args.output or root / "build/reports/cloud-testing"
        write_report(report, output, root)
    except (OSError, ValueError) as exc:
        parser.exit(1, f"Cannot produce test report: {exc}\n")
    print(render_markdown(report))
    bad = {"failed", "invalid_report", "aborted"}
    return int(report["java_unit"]["status"] != "passed"
               or any(report[key]["status"] in bad for key in ("python", "minecraft_gametest")))


if __name__ == "__main__":
    raise SystemExit(main())
