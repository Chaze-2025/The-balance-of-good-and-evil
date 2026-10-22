#!/usr/bin/env python3
"""Boot the development server only as far as its declined-EULA boundary.

This checks loader startup and the common mod initializer, not gameplay. It
never writes an EULA agreement and refuses to launch with an accepted EULA.
"""

import argparse
import json
import os
import re
import signal
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def expected_versions(path: Path = ROOT / "gradle.properties") -> dict:
    """Read the checked-in project pins instead of maintaining a second copy."""
    properties = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        if "=" in line:
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    required = ("minecraft_version", "loader_version", "mod_version")
    missing = [key for key in required if not properties.get(key)]
    if missing:
        raise ValueError("Missing project version pins: " + ", ".join(missing))
    return {key: properties[key] for key in required}


def eula_is_accepted(path: Path) -> bool:
    """Read ordinary EULA properties; refuse ambiguous Java escape syntax."""
    if not path.exists():
        return False
    text = path.read_text(encoding="utf-8")
    accepted = False
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        match = re.fullmatch(r"eula(?:\s*[:=]\s*|\s+)(true|false)", line,
                             re.IGNORECASE)
        if match is None:
            # Java properties also support continuations and Unicode escapes.
            # Never guess their meaning and accidentally launch an agreed world.
            raise ValueError("Existing EULA uses unfamiliar property syntax")
        accepted |= match.group(1).lower() == "true"
    return accepted


def check_output(output: str, exit_code: int, versions: dict | None = None) -> dict:
    versions = expected_versions() if versions is None else versions
    minecraft = re.escape(versions["minecraft_version"])
    loader = re.escape(versions["loader_version"])
    mod = re.escape(versions["mod_version"])
    checks = {
        "minecraft_and_fabric": bool(re.search(
            rf"Loading Minecraft {minecraft} with Fabric Loader {loader}(?:\s|$)", output)),
        "mod_discovered": bool(re.search(rf"^\s*- balance {mod}\s*$", output,
                                          re.MULTILINE)),
        "mod_initialized": "Balance development foundation initialized" in output,
        "eula_refused": "need to agree to the eula" in output.lower(),
        "gradle_completed": exit_code == 0 and "BUILD SUCCESSFUL" in output,
        "no_runtime_exception": not any(marker in output for marker in (
            "Exception in thread", "Caused by:", "BUILD FAILED",
            "A problem occurred", "Crash report saved")),
    }
    return {"outcome": "eula_boundary_verified" if all(checks.values()) else "failed",
            "gradle_exit_code": exit_code, "checks": checks,
            "expected_versions": versions,
            "gameplay_tested": False, "mod_initializer_tested": checks["mod_initialized"]}


def stop_process_group(process: subprocess.Popen) -> None:
    """Stop only this check's Gradle process and its server descendants."""
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


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timeout", type=int, default=180,
                        help="Maximum server/bootstrap execution seconds (default: 180)")
    args = parser.parse_args()
    if args.timeout < 1:
        parser.error("--timeout must be positive")
    eula = ROOT / "run/server/eula.txt"
    try:
        if eula_is_accepted(eula):
            parser.exit(2, "Refusing smoke check: run/server/eula.txt already accepts "
                        "the EULA. Its value was preserved; use a separate runtime "
                        "workflow for an agreed world.\n")
    except (OSError, UnicodeError, ValueError) as exc:
        parser.exit(2, f"Cannot safely inspect existing EULA; no server launched: {exc}\n")
    try:
        versions = expected_versions(ROOT / "gradle.properties")
    except (OSError, UnicodeError, ValueError) as exc:
        parser.exit(2, f"Cannot read project version pins; no server launched: {exc}\n")

    reports = ROOT / "build/reports"
    reports.mkdir(parents=True, exist_ok=True)
    log_path = reports / "server-smoke.log"
    report_path = reports / "server-smoke.json"
    command = [str(ROOT / "scripts/gradle.sh"), "--no-daemon", "runServer"]
    timed_out = False
    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=ROOT, stdout=log,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        try:
            exit_code = process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            timed_out = True
            stop_process_group(process)
            exit_code = process.returncode
        except KeyboardInterrupt:
            stop_process_group(process)
            raise

    output = log_path.read_text(encoding="utf-8", errors="replace")
    result = check_output(output, exit_code, versions)
    result.update({"timed_out": timed_out, "log": str(log_path), "command": command})
    if timed_out:
        result["outcome"] = "timeout"
    # Minecraft may create eula=false itself, but this tool never changes it.
    try:
        if eula_is_accepted(eula):
            result["outcome"] = "unexpected_eula_agreement"
    except (OSError, UnicodeError, ValueError) as exc:
        result["outcome"] = "eula_verification_failed"
        result["eula_error"] = str(exc)
    report_path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0 if result["outcome"] == "eula_boundary_verified" else 1


if __name__ == "__main__":
    raise SystemExit(main())
