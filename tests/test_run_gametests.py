"""Launcher policy tests use a fake subprocess; no Minecraft is launched."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools import run_gametests as runner


class GameTestLauncherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.eula = self.root / "run/server/eula.txt"
        self.eula.parent.mkdir(parents=True)
        self.original = b"# Owner has not agreed\neula=false\n"
        self.eula.write_bytes(self.original)
        source = self.root / "src/gametest/java/dev/chaze/balance/gametest/ExampleGameTests.java"
        source.parent.mkdir(parents=True)
        source.write_text('@GameTest(templateName="fixture") public void example(TestContext context) {}')

    def fake_launch(self, xml=None, exit_code=0, change_eula=False):
        root = self.root

        class Process:
            pid = 123456789
            returncode = exit_code

            def wait(self, timeout):
                if xml is not None:
                    path = root / "build/reports/gametest/results.xml"
                    path.write_text(xml)
                if change_eula:
                    (root / "run/server/eula.txt").write_text("eula=true\n")
                return exit_code

        return Process()

    def test_absent_or_wrong_consent_never_starts_a_process_or_changes_eula(self):
        with patch.object(runner.subprocess, "Popen") as launch:
            for consent in (None, "true", "yes", runner.OWNER_CONSENT.lower()):
                with self.subTest(consent=consent):
                    code, manifest = runner.run(self.root, consent)
                    self.assertEqual(code, 2)
                    self.assertEqual(manifest["status"], "blocked")
                    self.assertNotIn("execution_started_at", manifest)
                    self.assertEqual(self.eula.read_bytes(), self.original)
            launch.assert_not_called()

    def test_source_inventory_is_not_runtime_discovery(self):
        self.assertEqual(runner.expected_cases(self.root), ["examplegametests.example"])
        self.assertFalse((self.root / "build/reports/gametest/results.xml").exists())

    def test_failed_preflight_invalidates_previous_manifest_without_launch(self):
        path = self.root / "build/reports/gametest/run.json"
        path.parent.mkdir(parents=True)
        path.write_text('{"status":"completed"}')
        with patch.object(runner.subprocess, "Popen") as launch:
            code, manifest = runner.run(self.root, runner.OWNER_CONSENT, timeout=1)
            launch.assert_not_called()
        self.assertEqual(code, 1)
        self.assertEqual(manifest["status"], "aborted")
        self.assertNotIn("execution_started_at", manifest)
        self.assertEqual(json.loads(path.read_text()), manifest)

    def test_fresh_complete_nested_xml_and_zero_exit_are_required(self):
        xml = '<testsuite><testsuite><testcase name="examplegametests.example"/></testsuite></testsuite>'
        with patch.object(runner.subprocess, "Popen", side_effect=lambda *a, **k: self.fake_launch(xml)) as launch:
            code, manifest = runner.run(self.root, runner.OWNER_CONSENT)
        self.assertEqual(code, 0)
        self.assertEqual(manifest["status"], "completed")
        self.assertEqual(len(manifest["results_sha256"]), 64)
        self.assertEqual(manifest["java_exit_code"], 0)
        self.assertEqual(manifest["validation_errors"], [])
        self.assertIn("--dependency-verification", launch.call_args.args[0])
        self.assertIn("strict", launch.call_args.args[0])
        self.assertEqual(self.eula.read_bytes(), self.original)
        self.assertFalse(any(manifest["coverage"].values()))

    def test_old_report_is_removed_and_cannot_prove_a_new_run(self):
        path = self.root / "build/reports/gametest/results.xml"
        path.parent.mkdir(parents=True)
        path.write_text('<testsuite><testcase name="examplegametests.example"/></testsuite>')
        with patch.object(runner.subprocess, "Popen", side_effect=lambda *a, **k: self.fake_launch()):
            code, manifest = runner.run(self.root, runner.OWNER_CONSENT)
        self.assertEqual(code, 1)
        self.assertEqual(manifest["status"], "aborted")
        self.assertFalse(path.exists())

    def test_timeout_stops_only_the_launched_process_and_never_reports_success(self):
        process = self.fake_launch()
        with patch.object(runner.subprocess, "Popen", return_value=process), \
                patch.object(process, "wait", side_effect=runner.subprocess.TimeoutExpired("fixture", 30)), \
                patch.object(runner, "_stop", side_effect=lambda child: setattr(child, "returncode", -15)) as stop:
            code, manifest = runner.run(self.root, runner.OWNER_CONSENT, timeout=30)
        stop.assert_called_once_with(process)
        self.assertEqual(code, 1)
        self.assertEqual(manifest["java_exit_code"], -15)
        self.assertEqual(manifest["status"], "aborted")
        self.assertTrue(any("timeout" in error for error in manifest["validation_errors"]))
        self.assertEqual(self.eula.read_bytes(), self.original)

    def test_missing_failed_skipped_or_duplicate_cases_and_nonzero_exit_fail(self):
        scenarios = (
            ('<testsuite/>', 0),
            ('<testsuite><testcase name="wrong"/></testsuite>', 0),
            ('<testsuite><testcase name="examplegametests.example"><failure/></testcase></testsuite>', 0),
            ('<testsuite><testcase name="examplegametests.example"><skipped/></testcase></testsuite>', 0),
            ('<testsuite><testcase name="examplegametests.example"/><testcase name="examplegametests.example"/></testsuite>', 0),
            ('<testsuite><testcase name="examplegametests.example"/></testsuite>', 1),
            ('not xml', 0),
        )
        for xml, exit_code in scenarios:
            with self.subTest(xml=xml, exit_code=exit_code):
                with patch.object(runner.subprocess, "Popen", side_effect=lambda *a, **k: self.fake_launch(xml, exit_code)):
                    code, manifest = runner.run(self.root, runner.OWNER_CONSENT)
                self.assertEqual(code, 1)

    def test_fresh_world_cleanup_preserves_eula_bytes_and_unrelated_files(self):
        directory = self.root / "build/run/gameTest"
        (directory / "world").mkdir(parents=True)
        (directory / "eula.txt").write_bytes(self.original)
        (directory / "world/old.dat").write_text("old fixture")
        unrelated = self.root / "owner-notes.txt"
        unrelated.write_text("preserve")
        runner._fresh_world_directory(self.root)
        self.assertEqual((directory / "eula.txt").read_bytes(), self.original)
        self.assertFalse((directory / "world").exists())
        self.assertEqual(unrelated.read_text(), "preserve")
        self.assertEqual(self.eula.read_bytes(), self.original)

    def test_unexpected_eula_change_is_reported_as_failure(self):
        xml = '<testsuite><testcase name="examplegametests.example"/></testsuite>'
        with patch.object(runner.subprocess, "Popen", side_effect=lambda *a, **k: self.fake_launch(xml, change_eula=True)):
            code, manifest = runner.run(self.root, runner.OWNER_CONSENT)
        self.assertEqual(code, 1)
        self.assertTrue(any("EULA file changed" in error for error in manifest["validation_errors"]))
        # Never silently rewrites even this fake process's unexpected result.
        self.assertEqual(self.eula.read_text(), "eula=true\n")
        self.assertEqual(json.loads((self.root / "build/reports/gametest/run.json").read_text()), manifest)


if __name__ == "__main__":
    unittest.main()
