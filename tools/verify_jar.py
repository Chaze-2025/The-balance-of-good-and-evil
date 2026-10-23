#!/usr/bin/env python3
"""Verify the actual distributable, including entry points and Java 21 bytecode."""
import argparse
import json
import struct
import zipfile
from pathlib import Path

try:
    from .validate_vigil_resources import validate as validate_vigil_resources
except ImportError:  # Direct CLI invocation, rather than importing tools.verify_jar.
    from validate_vigil_resources import validate as validate_vigil_resources


def verify(path: Path) -> dict:
    properties = Path(__file__).resolve().parents[1] / 'gradle.properties'
    version = next(line.split('=', 1)[1].strip() for line in properties.read_text().splitlines()
                   if line.startswith('mod_version='))
    with zipfile.ZipFile(path) as jar:
        bad = jar.testzip()
        if bad:
            raise ValueError(f"Corrupt ZIP member: {bad}")
        metadata = json.loads(jar.read('fabric.mod.json'))
        if metadata.get('id') != 'balance' or metadata.get('version') != version:
            raise ValueError('Unexpected mod ID or unexpanded/wrong version')
        expected = {
            'main': 'dev.chaze.balance.BalanceMod',
            'client': 'dev.chaze.balance.client.BalanceClient',
        }
        for side, name in expected.items():
            if metadata.get('entrypoints', {}).get(side) != [name]:
                raise ValueError(f'Missing {side} entry point')
            data = jar.read(name.replace('.', '/') + '.class')
            magic, _, major = struct.unpack('>IHH', data[:8])
            if magic != 0xCAFEBABE or major != 65:
                raise ValueError(f'{name}: expected Java 21 class version 65')
        feature_classes = (
            'dev/chaze/balance/ward/WardSource.class',
            'dev/chaze/balance/ward/WardIndex.class',
            'dev/chaze/balance/ward/WardEffectPolicy.class',
            'dev/chaze/balance/ward/VigilWards.class',
            'dev/chaze/balance/registry/ModBlocks.class',
            'dev/chaze/balance/registry/ModItems.class',
            'dev/chaze/balance/registry/ModBlockEntities.class',
            'dev/chaze/balance/block/entity/VigilTorchBlockEntity.class',
        )
        for name in feature_classes:
            data = jar.read(name)
            magic, _, major = struct.unpack('>IHH', data[:8])
            if magic != 0xCAFEBABE or major != 65:
                raise ValueError(f'{name}: expected Java 21 class version 65')
        nightfall_classes = (
            'dev/chaze/balance/nightfall/NightfallPhase.class',
            'dev/chaze/balance/nightfall/NightfallClock.class',
            'dev/chaze/balance/nightfall/NightfallState.class',
            'dev/chaze/balance/nightfall/NightfallTracker.class',
            'dev/chaze/balance/nightfall/NightfallService.class',
            'dev/chaze/balance/nightfall/NightfallCommands.class',
            'dev/chaze/balance/config/NightfallConfig.class',
        )
        for name in nightfall_classes:
            data = jar.read(name)
            magic, _, major = struct.unpack('>IHH', data[:8])
            if magic != 0xCAFEBABE or major != 65:
                raise ValueError(f'{name}: expected Java 21 class version 65')
        names = jar.namelist()
        if any(n.startswith(('asset-sources/', 'tests/', 'tools/')) for n in names):
            raise ValueError('Development files leaked into the mod JAR')
        jar.read('assets/balance/sounds.json')
        vigil_resources = validate_vigil_resources(jar.read)
        lang = json.loads(jar.read('assets/balance/lang/en_us.json'))
        nightfall_translation_keys = (
            'command.balance.nightfall.status',
            'nightfall.balance.phase.daylight',
            'nightfall.balance.phase.dusk',
            'nightfall.balance.phase.night',
            'nightfall.balance.phase.deep_night',
            'nightfall.balance.phase.dawn',
            'nightfall.balance.phase.inactive',
            'nightfall.balance.enabled',
            'nightfall.balance.disabled',
        )
        for key in nightfall_translation_keys:
            if not isinstance(lang.get(key), str) or not lang[key].strip():
                raise ValueError(f'Missing or empty Nightfall translation: {key}')
        if lang['command.balance.nightfall.status'].count('%s') != 5:
            raise ValueError('Nightfall status translation must contain five %s placeholders')
        return {'jar': str(path), 'mod': metadata['id'], 'version': metadata['version'],
                'entrypoints': list(expected), 'java_class_version': 65, 'bytes': path.stat().st_size,
                'vigil_feature_classes': len(feature_classes), 'vigil_resources': vigil_resources,
                'nightfall_feature_classes': len(nightfall_classes),
                'nightfall_translation_keys': len(nightfall_translation_keys)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.jar), indent=2))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, struct.error) as exc:
        parser.exit(1, f'JAR validation failed: {exc}\n')
