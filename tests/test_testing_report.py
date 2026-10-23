"""Test evidence classification with synthetic XML/files; never launch Minecraft."""
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock

from tools import testing_report


class TestingReportTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.unit = self.write("build/test-results/test/TEST-example.xml",
                               '<testsuite tests="2" failures="0" errors="0" skipped="1">'
                               '<testcase name="unit"/><testcase name="skipped"><skipped/></testcase></testsuite>')
        self.result = self.root / "build/reports/gametest/results.xml"
        self.manifest = self.root / "build/reports/gametest/run.json"
        self.now = datetime.now(timezone.utc)

    def write(self, name, contents):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(contents.encode() if isinstance(contents, str) else contents)
        return path

    def completed(self, xml='<testsuite><testsuite><testcase name="runtime"/></testsuite></testsuite>', exit_code=0):
        self.write("build/reports/gametest/results.xml", xml)
        data = {"status": "completed", "execution_started_at": (self.now - timedelta(seconds=1)).isoformat(),
                "execution_finished_at": (self.now + timedelta(seconds=1)).isoformat(),
                "java_exit_code": exit_code, "results_file": "build/reports/gametest/results.xml",
                "results_sha256": hashlib.sha256(self.result.read_bytes()).hexdigest(),
                "coverage": {"graphical": True, "multiplayer": True, "persistence": True}, "evidence": []}
        self.write("build/reports/gametest/run.json", json.dumps(data))
        return data

    def collect(self):
        return testing_report.collect_report(self.root)

    def test_unit_results_never_imply_minecraft_or_visual_execution(self):
        report = self.collect()
        self.assertEqual(report["java_unit"]["status"], "passed")
        self.assertEqual(report["java_unit"]["tests"], 2)
        self.assertEqual(report["java_unit"]["skipped"], 1)
        self.assertEqual(report["minecraft_gametest"]["status"], "not_run")
        self.assertFalse(any(report["coverage"].values()))
        self.assertIn("owner EULA authorization", report["minecraft_gametest"]["reason"])

    def test_blocked_or_aborted_manifests_cannot_reuse_stale_results(self):
        data = self.completed()
        for status in ("not_run", "blocked", "not_started", "aborted"):
            with self.subTest(status=status):
                data.update(status=status, reason="Owner authorization was not supplied")
                self.manifest.write_text(json.dumps(data))
                runtime = self.collect()["minecraft_gametest"]
                self.assertEqual(runtime["status"], status)
                self.assertFalse(runtime["executed"])
                self.assertIsNone(runtime["tests"])

    def test_completed_runtime_requires_current_hash_and_execution_window(self):
        original = self.completed()
        cases = (("results_sha256", "0" * 64),
                 ("execution_started_at", (self.now + timedelta(days=1)).isoformat()),
                 ("execution_finished_at", "2020-01-01T00:00:00+00:00"),
                 ("execution_started_at", "2026-01-01T00:00:00"),
                 ("java_exit_code", None), ("results_file", "../../outside.xml"))
        for key, value in cases:
            with self.subTest(key=key, value=value):
                self.manifest.write_text(json.dumps({**original, key: value}))
                runtime = self.collect()["minecraft_gametest"]
                self.assertEqual(runtime["status"], "invalid_report")
                self.assertFalse(runtime["executed"])
                self.assertIsNone(runtime["tests"])
        self.manifest.write_text(json.dumps(original))
        stale = (self.now - timedelta(days=1)).timestamp()
        os.utime(self.result, (stale, stale))
        self.assertEqual(self.collect()["minecraft_gametest"]["status"], "invalid_report")

    def test_nested_fabric_xml_without_totals_counts_real_testcases(self):
        self.completed()
        report = self.collect()
        self.assertEqual(report["minecraft_gametest"]["status"], "passed")
        self.assertEqual(report["minecraft_gametest"]["tests"], 1)
        self.assertTrue(report["coverage"]["minecraft_world_tests"])
        # Unsupported runner claims cannot promote real-client or disk coverage.
        self.assertFalse(report["coverage"]["graphical_client"])
        self.assertFalse(report["coverage"]["real_multiplayer"])
        self.assertFalse(report["coverage"]["disk_persistence"])

    def test_runtime_failure_or_nonzero_exit_is_never_green(self):
        for xml, code in (('<testsuite><testcase><failure message="failed"/></testcase></testsuite>', 0),
                          ('<testsuite><testcase/></testsuite>', 1)):
            with self.subTest(code=code):
                self.completed(xml, code)
                runtime = self.collect()["minecraft_gametest"]
                self.assertEqual(runtime["status"], "failed")
                self.assertTrue(runtime["executed"])

    def test_launcher_validation_errors_or_missing_expected_tests_fail_completed_run(self):
        original = self.completed()
        for update in ({"validation_errors": ["Expected case was absent"]},
                       {"expected_tests": ["first", "second"]}):
            with self.subTest(update=update):
                self.manifest.write_text(json.dumps({**original, **update}))
                runtime = self.collect()["minecraft_gametest"]
                self.assertEqual(runtime["status"], "failed")
                self.assertTrue(runtime["executed"])
                self.assertTrue(runtime["problems"])

    def test_missing_empty_malformed_or_forged_reports_are_not_passes(self):
        for xml in ('<testsuite/>', '<testsuite><testcase><skipped/></testcase></testsuite>',
                    '<testsuite tests="9"><testcase/></testsuite>', '<broken',
                    '<!DOCTYPE x [<!ENTITY x "unsafe">]><testsuite><testcase name="&x;"/></testsuite>'):
            with self.subTest(xml=xml):
                self.completed(xml)
                self.assertEqual(self.collect()["minecraft_gametest"]["status"], "invalid_report")
        self.completed()
        self.result.unlink()
        self.assertEqual(self.collect()["minecraft_gametest"]["status"], "invalid_report")
        self.unit.write_text('<testsuite tests="10"><testcase/></testsuite>')
        self.assertEqual(self.collect()["java_unit"]["status"], "invalid_report")
        self.unit.unlink()
        self.assertEqual(self.collect()["java_unit"]["status"], "not_run")

    def test_python_completion_and_failures_are_parsed_without_guessing(self):
        path = "build/reports/cloud-testing/python-tests.log"
        for ending, expected in (("OK", "passed"), ("FAILED (failures=1, errors=1)", "failed"),
                                 ("unexpected trailing output", "invalid_report")):
            with self.subTest(ending=ending):
                self.write(path, "test_one ... ok\n\nRan 3 tests in 0.02s\n\n" + ending + "\n")
                python = self.collect()["python"]
                self.assertEqual(python["status"], expected)
                self.assertEqual(python["tests"], 3)
        self.write(path, "FAILED (failures=1)\n")
        self.assertEqual(self.collect()["python"]["status"], "invalid_report")

    def test_portable_outputs_escape_untrusted_reason_and_include_honest_scope(self):
        self.write("build/reports/gametest/run.json",
                   json.dumps({"status": "blocked", "reason": '<script>alert("x")</script>'}))
        self.write("build/classes/java/gametest/Test.class", b"synthetic presence fixture")
        report = self.collect()
        output = self.root / "report"
        testing_report.write_report(report, output, self.root)
        page = (output / "report.html").read_text()
        self.assertNotIn("<script>", page)
        self.assertIn("&lt;script&gt;", page)
        self.assertIn("No verified screenshot or video", page)
        self.assertIn("Compilation is not runtime evidence", page)
        self.assertEqual(json.loads((output / "summary.json").read_text())["gametest_compilation"]["class_files"], 1)
        self.assertIn("Minecraft world GameTests", (output / "summary.md").read_text())
        self.assertIn("not verified", (output / "summary.md").read_text())

    def test_only_fresh_hash_verified_visual_files_are_copied(self):
        manifest = self.completed()
        path = self.write("build/screenshots/evidence.png", b"\x89PNG\r\n\x1a\nsynthetic fixture, not a rendered image")
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        manifest["evidence"] = [{"path": "build/screenshots/evidence.png", "sha256": digest, "kind": "screenshot"}]
        self.manifest.write_text(json.dumps(manifest))
        report = self.collect()
        output = self.root / "report"
        testing_report.write_report(report, output, self.root)
        self.assertEqual((output / "evidence" / (digest[:12] + ".png")).read_bytes(), path.read_bytes())
        self.assertFalse(report["coverage"]["graphical_client"])
        path.write_bytes(b"altered")
        self.assertEqual(self.collect()["minecraft_gametest"]["status"], "invalid_report")

    def test_cli_fails_missing_unit_evidence_but_allows_blocked_runtime(self):
        args = ["testing_report.py", "--root", str(self.root)]
        with mock.patch("sys.argv", args), mock.patch("builtins.print"):
            self.assertEqual(testing_report.main(), 0)
            self.unit.unlink()
            self.assertEqual(testing_report.main(), 1)


if __name__ == "__main__":
    unittest.main()
