"""Regression tests for malformed imports and real audio conversion."""

import importlib.util
import json
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest
import zlib


SPEC = importlib.util.spec_from_file_location("asset_tool", Path(__file__).parents[1] / "tools/asset_tool.py")
asset_tool = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(asset_tool)


def png_bytes(width=1, height=1):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress((b"\0" + b"\0\0\0\xff" * width) * height))
            + chunk(b"IEND", b""))


class AssetValidationTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def write(self, path, data):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data if isinstance(data, bytes) else data.encode())
        return target

    def test_identifier_rejects_uppercase_colons_and_traversal(self):
        for value in ["Balance:test", "balance:../x", "balance:x:y", "balance:a//b", "balance:"]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                asset_tool.resource_id(value)
        self.assertEqual(asset_tool.resource_id("entity.zombie.ambient"), ("minecraft", "entity.zombie.ambient"))

    def test_invalid_json_and_duplicate_keys_fail(self):
        self.write("assets/balance/lang/en_us.json", '{"x": "first", "x": "second"}')
        self.write("data/balance/broken.json", '{"unterminated":')
        self.write("data/balance/nan.json", '{"value": NaN}')
        errors, _, count = asset_tool.validate_resources(self.root)
        self.assertEqual(count, 3)
        self.assertEqual(len(errors), 3)
        self.assertTrue(any("duplicate JSON key" in error for error in errors))

    def test_case_sensitive_resource_paths_fail(self):
        self.write("assets/balance/textures/Creature.png", png_bytes())
        errors, _, _ = asset_tool.validate_resources(self.root)
        self.assertEqual(len(errors), 1)
        self.assertIn("invalid resource identifier", errors[0])

    def test_png_crc_and_truncation_fail(self):
        valid = self.write("valid.png", png_bytes())
        self.assertEqual(asset_tool.inspect_png(valid)["width"], 1)
        broken = bytearray(valid.read_bytes())
        broken[29] ^= 1
        for index, contents in enumerate([bytes(broken), valid.read_bytes()[:-8], b"not a png"]):
            with self.subTest(index=index), self.assertRaises(ValueError):
                asset_tool.inspect_png(self.write(f"bad{index}.png", contents))

    def test_non_power_of_two_is_warning_not_failure(self):
        self.write("assets/balance/textures/test.png", png_bytes(3, 1))
        errors, warnings, _ = asset_tool.validate_resources(self.root)
        self.assertEqual(errors, [])
        self.assertEqual(len(warnings), 1)

    def test_missing_sound_file_and_event_fail(self):
        self.write("assets/balance/sounds.json", json.dumps({
            "missing_file": {"sounds": ["balance:creature/missing"]},
            "missing_event": {"sounds": [{"name": "balance:missing", "type": "event"}]},
            "vanilla_reference": {"sounds": [{"name": "minecraft:entity.zombie.ambient", "type": "event"}]},
        }))
        errors, _, _ = asset_tool.validate_resources(self.root)
        self.assertEqual(len(errors), 2)
        self.assertTrue(any("missing local sound file" in error for error in errors))
        self.assertTrue(any("missing local sound event" in error for error in errors))

    @unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "FFmpeg/ffprobe not installed")
    def test_real_conversion_validation_and_overwrite_protection(self):
        source = self.root / "source.wav"
        subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i",
                        "sine=frequency=440:duration=0.1", "-ac", "2", str(source)], check=True)
        mono = self.root / "assets/balance/sounds/example.ogg"
        asset_tool.convert_audio(source, mono)
        self.assertEqual(asset_tool.inspect_audio(mono)["channels"], 1)
        self.write("assets/balance/sounds.json", '{"example": {"sounds": ["balance:example"]}}')
        errors, _, _ = asset_tool.validate_resources(self.root)
        self.assertEqual(errors, [])
        original = mono.read_bytes()
        with self.assertRaises(ValueError):
            asset_tool.convert_audio(source, mono)
        self.assertEqual(mono.read_bytes(), original)
        stereo = self.root / "assets/balance/sounds/music.ogg"
        asset_tool.convert_audio(source, stereo, stereo=True)
        errors, warnings, _ = asset_tool.validate_resources(self.root, positional_audio=True)
        self.assertTrue(any("stereo audio" in error for error in errors))
        errors, warnings, _ = asset_tool.validate_resources(self.root)
        self.assertEqual(errors, [])
        self.assertTrue(any("stereo audio" in warning for warning in warnings))

    def test_missing_resource_directory_fails(self):
        errors, _, count = asset_tool.validate_resources(self.root / "missing")
        self.assertEqual(count, 0)
        self.assertEqual(len(errors), 1)


if __name__ == "__main__":
    unittest.main()
