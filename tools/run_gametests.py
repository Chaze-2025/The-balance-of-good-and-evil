#!/usr/bin/env python3
"""Run isolated Minecraft worlds only after explicit owner EULA authorization.

Fabric's GameTest launcher bypasses the ordinary EULA refusal. Neither this
runner nor Gradle writes eula=true; that does not remove the consent requirement.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OWNER_CONSENT = "I_ACCEPT_MINECRAFT_EULA_FOR_ISOLATED_TESTS"


def now() -> datetime:
    return datetime.now(timezone.utc)


def expected_cases(root: Path) -> list[str]:
    """Inventory the explicitly annotated methods; no runtime-discovery claim."""
    cases = []
    for source in sorted((root / "src/gametest/java/dev/chaze/balance/gametest").glob("*GameTests.java")):
        methods = re.findall(r"@GameTest\s*\([^)]*\)\s+public\s+void\s+(\w+)\s*\(",
                             source.read_text(), re.DOTALL)
        cases.extend(source.stem.lower() + "." + method.lower() for method in methods)
    if not cases or len(cases) != len(set(cases)):
        raise ValueError("Missing or duplicate annotated GameTest inventory")
    return cases


def _write_manifest(root: Path, manifest: dict) -> None:
    path = root / "build/reports/gametest/run.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(manifest, indent=2) + "\n")


def _stop(process: subprocess.Popen) -> None:
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait()


def _fresh_world_directory(root: Path) -> Path:
    """Discard only generated test fixtures, retaining any EULA file byte-for-byte."""
    directory = root / "build/run/gameTest"
    if directory.is_symlink() or (root / "build").is_symlink() or (root / "build/run").is_symlink():
        raise ValueError("Refusing a symlinked generated test directory")
    directory.mkdir(parents=True, exist_ok=True)
    for entry in directory.iterdir():
        if entry.name == "eula.txt":
            continue
        if entry.is_dir() and not entry.is_symlink():
            shutil.rmtree(entry)
        else:
            entry.unlink()
    return directory


def run(root: Path = ROOT, owner_consent: str | None = None, timeout: int = 300) -> tuple[int, dict]:
    manifest = {
        "status": "blocked", "reason": "Explicit owner acceptance of Minecraft's EULA is required before GameTests.",
        "coverage": {"graphical": False, "multiplayer": False, "persistence": False},
        "evidence": [], "results_file": "build/reports/gametest/results.xml",
        "expected_tests": [], "validation_errors": [],
    }
    if owner_consent != OWNER_CONSENT:
        _write_manifest(root, manifest)
        return 2, manifest
    manifest["status"] = "aborted"
    manifest["reason"] = "Authorized launch has not produced a complete runtime report."
    _write_manifest(root, manifest)  # Invalidate any previous result before preflight.
    try:
        if not 30 <= timeout <= 1800:
            raise ValueError("Timeout must be between 30 and 1800 seconds")
        normal_eula = root / "run/server/eula.txt"
        normal_bytes = normal_eula.read_bytes() if normal_eula.exists() else None
        manifest["expected_tests"] = expected_cases(root)
        run_dir = _fresh_world_directory(root)
        isolated_eula = run_dir / "eula.txt"
        isolated_bytes = isolated_eula.read_bytes() if isolated_eula.exists() else None
    except (OSError, ValueError) as error:
        manifest["reason"] = "Runtime preflight failed; no Minecraft process launched."
        manifest["validation_errors"].append(str(error))
        _write_manifest(root, manifest)
        return 1, manifest
    reports = root / "build/reports/gametest"
    reports.mkdir(parents=True, exist_ok=True)
    results = root / manifest["results_file"]
    results.unlink(missing_ok=True)
    command = [str(root / "scripts/gradle.sh"), "--no-daemon", "--dependency-verification", "strict",
               "-PbalanceGameTestEulaConsent=" + owner_consent, "runGameTest"]
    manifest["command"] = command
    started = now()
    manifest["execution_started_at"] = started.isoformat()
    _write_manifest(root, manifest)
    with (reports / "server.log").open("w") as log:
        try:
            process = subprocess.Popen(command, cwd=root, stdout=log, stderr=subprocess.STDOUT,
                                       start_new_session=True)
        except OSError as error:
            manifest["reason"] = "Runtime process could not start."
            manifest["validation_errors"].append(str(error))
            manifest["execution_finished_at"] = now().isoformat()
            _write_manifest(root, manifest)
            return 1, manifest
        try:
            exit_code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            _stop(process)
            exit_code = process.returncode
            manifest["validation_errors"].append("GameTest process exceeded its timeout")
        except KeyboardInterrupt:
            _stop(process)
            manifest["reason"] = "Authorized attempt interrupted; no passing result claimed."
            manifest["execution_finished_at"] = now().isoformat()
            manifest["java_exit_code"] = process.returncode
            _write_manifest(root, manifest)
            raise
    finished = now()
    manifest["execution_finished_at"] = finished.isoformat()
    manifest["java_exit_code"] = exit_code
    if (normal_eula.read_bytes() if normal_eula.exists() else None) != normal_bytes:
        manifest["validation_errors"].append("Normal development EULA file changed unexpectedly")
    if isolated_bytes is not None and (not isolated_eula.exists() or isolated_eula.read_bytes() != isolated_bytes):
        manifest["validation_errors"].append("Existing isolated EULA file changed unexpectedly")
    if isolated_eula.exists() and re.search(rb"(?im)^\s*eula\s*=\s*true\s*$", isolated_eula.read_bytes()):
        manifest["validation_errors"].append("Isolated run unexpectedly contains eula=true; this runner never writes agreement")
    if results.exists():
        data = results.read_bytes()
        manifest["results_sha256"] = hashlib.sha256(data).hexdigest()
        try:
            if b"<!DOCTYPE" in data.upper():
                raise ValueError("DOCTYPE is not permitted in test reports")
            parsed = ET.fromstring(data)
            cases = parsed.findall(".//testcase")
            names = [case.get("name", "") for case in cases]
            missing = set(manifest["expected_tests"]) - set(names)
            if not cases or missing:
                manifest["validation_errors"].append("Incomplete GameTest discovery/results; missing: " + ", ".join(sorted(missing)))
            if len(names) != len(set(names)):
                manifest["validation_errors"].append("Duplicate runtime test results")
            if any(case.find("failure") is not None or case.find("error") is not None
                   or case.find("skipped") is not None for case in cases):
                manifest["validation_errors"].append("GameTest failures/errors/skips were reported")
            if not started.timestamp() - 2 <= results.stat().st_mtime <= finished.timestamp() + 2:
                manifest["validation_errors"].append("Runtime XML is stale or outside the current launch window")
            manifest["status"] = "completed"
            manifest["reason"] = "Runtime process completed; evaluate XML and exit status, not this status label alone."
        except (ET.ParseError, ValueError) as error:
            manifest["validation_errors"].append("Invalid GameTest XML: " + str(error))
    else:
        manifest["validation_errors"].append("No GameTest XML was produced; no runtime pass can be claimed")
    _write_manifest(root, manifest)
    return (0 if manifest["status"] == "completed" and exit_code == 0
            and not manifest["validation_errors"] else 1), manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--owner-eula-consent", help="Exact acknowledgement, only after the owner explicitly authorizes these tests")
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()
    code, manifest = run(owner_consent=args.owner_eula_consent, timeout=args.timeout)
    print(json.dumps(manifest, indent=2))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
