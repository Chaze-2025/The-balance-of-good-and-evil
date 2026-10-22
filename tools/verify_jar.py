#!/usr/bin/env python3
"""Verify the actual distributable, including entry points and Java 21 bytecode."""
import argparse
import json
import struct
import zipfile
from pathlib import Path


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
        names = jar.namelist()
        if any(n.startswith(('asset-sources/', 'tests/', 'tools/')) for n in names):
            raise ValueError('Development files leaked into the mod JAR')
        jar.read('assets/balance/sounds.json')
        return {'jar': str(path), 'mod': metadata['id'], 'version': metadata['version'],
                'entrypoints': list(expected), 'java_class_version': 65, 'bytes': path.stat().st_size}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.jar), indent=2))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, struct.error) as exc:
        parser.exit(1, f'JAR validation failed: {exc}\n')
