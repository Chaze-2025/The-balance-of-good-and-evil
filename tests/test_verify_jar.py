"""Packaging regressions using synthetic class headers, not executable mod builds."""
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

from tools import verify_jar


ROOT = Path(__file__).resolve().parents[1]
NIGHTFALL_CLASSES = (
    'dev/chaze/balance/nightfall/NightfallPhase.class',
    'dev/chaze/balance/nightfall/NightfallClock.class',
    'dev/chaze/balance/nightfall/NightfallState.class',
    'dev/chaze/balance/nightfall/NightfallTracker.class',
    'dev/chaze/balance/nightfall/NightfallService.class',
    'dev/chaze/balance/nightfall/NightfallCommands.class',
    'dev/chaze/balance/config/NightfallConfig.class',
)
VIGIL_CLASSES = (
    'dev/chaze/balance/ward/WardSource.class',
    'dev/chaze/balance/ward/WardIndex.class',
    'dev/chaze/balance/ward/WardEffectPolicy.class',
    'dev/chaze/balance/ward/VigilWards.class',
    'dev/chaze/balance/registry/ModBlocks.class',
    'dev/chaze/balance/registry/ModItems.class',
    'dev/chaze/balance/registry/ModBlockEntities.class',
    'dev/chaze/balance/block/entity/VigilTorchBlockEntity.class',
)
NIGHTFALL_TRANSLATIONS = {
    'command.balance.nightfall.status': 'Dimension: %s | Time: %s | Phase: %s | Intensity: %s | Enabled: %s',
    'nightfall.balance.phase.daylight': 'Daylight',
    'nightfall.balance.phase.dusk': 'Dusk',
    'nightfall.balance.phase.night': 'Night',
    'nightfall.balance.phase.deep_night': 'Deep Night',
    'nightfall.balance.phase.dawn': 'Dawn',
    'nightfall.balance.phase.inactive': 'Inactive',
    'nightfall.balance.enabled': 'Enabled',
    'nightfall.balance.disabled': 'Disabled',
}


class JarVerificationTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.jar_path = Path(self.temporary.name) / 'fixture.jar'
        resource_root = ROOT / 'src/main/resources'
        self.entries = {str(path.relative_to(resource_root)): path.read_bytes()
                        for path in resource_root.rglob('*') if path.is_file()}
        version = next(line.split('=', 1)[1].strip()
                       for line in (ROOT / 'gradle.properties').read_text().splitlines()
                       if line.startswith('mod_version='))
        metadata = json.loads(self.entries['fabric.mod.json'])
        metadata['version'] = version
        self.entries['fabric.mod.json'] = json.dumps(metadata).encode()
        for name in (*VIGIL_CLASSES, *NIGHTFALL_CLASSES,
                     'dev/chaze/balance/BalanceMod.class',
                     'dev/chaze/balance/client/BalanceClient.class'):
            # The utility inspects headers; these fixtures are intentionally not JVM classes.
            self.entries[name] = struct.pack('>IHH', 0xCAFEBABE, 0, 65)
        self.edit_lang(lambda lang: lang.update(NIGHTFALL_TRANSLATIONS))

    def edit_lang(self, change):
        lang = json.loads(self.entries['assets/balance/lang/en_us.json'])
        change(lang)
        self.entries['assets/balance/lang/en_us.json'] = json.dumps(lang).encode()

    def verify(self):
        with zipfile.ZipFile(self.jar_path, 'w') as jar:
            for name, content in self.entries.items():
                jar.writestr(name, content)
        return verify_jar.verify(self.jar_path)

    def test_complete_fixture_reports_both_features(self):
        report = self.verify()
        self.assertEqual(report['vigil_feature_classes'], 8)
        self.assertEqual(report['nightfall_feature_classes'], 7)
        self.assertEqual(report['nightfall_translation_keys'], 9)
        self.assertFalse(report['vigil_resources']['gameplay_tested'])

    def test_each_nightfall_class_must_be_packaged(self):
        for name in NIGHTFALL_CLASSES:
            with self.subTest(name=name):
                original = self.entries.pop(name)
                try:
                    with self.assertRaisesRegex(KeyError, name):
                        self.verify()
                finally:
                    self.entries[name] = original

    def test_nightfall_class_header_must_target_java_21(self):
        name = 'dev/chaze/balance/nightfall/NightfallService.class'
        for magic, major in ((0xCAFEBABE, 61), (0, 65)):
            with self.subTest(magic=magic, major=major):
                self.entries[name] = struct.pack('>IHH', magic, 0, major)
                with self.assertRaisesRegex(ValueError, 'NightfallService.class: expected Java 21'):
                    self.verify()

    def test_each_nightfall_translation_is_required_and_nonempty(self):
        for key in NIGHTFALL_TRANSLATIONS:
            for value in (None, '', '   ', 42):
                with self.subTest(key=key, value=value):
                    self.edit_lang(lambda lang: lang.update(NIGHTFALL_TRANSLATIONS))
                    if value is None:
                        self.edit_lang(lambda lang: lang.pop(key))
                    else:
                        self.edit_lang(lambda lang: lang.update({key: value}))
                    with self.assertRaisesRegex(ValueError, 'Missing or empty Nightfall translation'):
                        self.verify()

    def test_status_translation_requires_all_five_fields(self):
        for count in (0, 4, 6):
            with self.subTest(count=count):
                self.edit_lang(lambda lang: lang.update({'command.balance.nightfall.status': ' '.join(['%s'] * count)}))
                with self.assertRaises(ValueError):
                    self.verify()

    def test_development_files_cannot_leak_into_distributable(self):
        self.entries['tests/TestFixture.java'] = b'fixture'
        with self.assertRaisesRegex(ValueError, 'Development files leaked'):
            self.verify()

    def test_vigil_feature_remains_required(self):
        self.entries.pop('dev/chaze/balance/ward/VigilWards.class')
        with self.assertRaisesRegex(KeyError, 'VigilWards.class'):
            self.verify()

    def test_isolated_gametest_code_and_resources_cannot_ship_in_the_mod(self):
        for name in ('dev/chaze/balance/gametest/VigilTorchGameTests.class',
                     'data/balance-test/gametest/structure/ward_arena.snbt'):
            with self.subTest(name=name):
                self.entries[name] = b'isolated test fixture'
                try:
                    with self.assertRaisesRegex(ValueError, 'Development files leaked'):
                        self.verify()
                finally:
                    self.entries.pop(name)


if __name__ == '__main__':
    unittest.main()
