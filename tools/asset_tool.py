#!/usr/bin/env python3
"""Validate Minecraft resources and convert source audio without Python packages."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import zlib


NAMESPACE = re.compile(r"[a-z0-9_.-]+\Z")
RESOURCE_PATH = re.compile(r"[a-z0-9/._-]+\Z")
SOURCE_EXTENSIONS = {".blend", ".bbmodel", ".wav", ".mp3", ".flac", ".psd", ".xcf"}


def resource_id(value: str, default_namespace: str = "minecraft") -> tuple[str, str]:
    """Parse an identifier using Minecraft's default namespace convention."""
    if not isinstance(value, str):
        raise ValueError("resource identifier must be a string")
    namespace, separator, path = value.partition(":")
    if not separator:
        namespace, path = default_namespace, namespace
    if (not NAMESPACE.fullmatch(namespace) or not RESOURCE_PATH.fullmatch(path)
            or any(part in {"", ".", ".."} for part in path.split("/"))):
        raise ValueError(f"invalid resource identifier: {value!r}")
    return namespace, path


def _unique_object(pairs: list[tuple[str, object]]) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key!r}")
        result[key] = value
    return result


def read_json(path: Path):
    def reject_constant(value):
        raise ValueError(f"nonstandard JSON constant: {value}")
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=_unique_object,
                      parse_constant=reject_constant)


def inspect_png(path: Path) -> dict:
    """Check PNG chunk structure and CRCs; return IHDR metadata, not pixel decoding."""
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("invalid PNG signature")
    offset = 8
    metadata = None
    image_data = False
    ended = False
    while offset < len(data):
        if offset + 12 > len(data):
            raise ValueError("truncated PNG chunk")
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        end = offset + 12 + length
        if end > len(data):
            raise ValueError("truncated PNG chunk payload")
        payload = data[offset + 8:end - 4]
        crc = struct.unpack_from(">I", data, end - 4)[0]
        if zlib.crc32(kind + payload) != crc:
            raise ValueError(f"PNG CRC mismatch in {kind!r}")
        if metadata is None and kind != b"IHDR":
            raise ValueError("PNG must begin with IHDR")
        if kind == b"IHDR":
            if metadata is not None or length != 13:
                raise ValueError("invalid or repeated PNG IHDR")
            width, height, depth, color, compression, filtering, interlace = struct.unpack(
                ">IIBBBBB", payload)
            depths = {0: {1, 2, 4, 8, 16}, 2: {8, 16}, 3: {1, 2, 4, 8},
                      4: {8, 16}, 6: {8, 16}}
            if (not width or not height or width > 2**31 - 1 or height > 2**31 - 1
                    or depth not in depths.get(color, set()) or compression != 0
                    or filtering != 0 or interlace not in {0, 1}):
                raise ValueError("invalid PNG IHDR fields")
            metadata = {"width": width, "height": height, "bit_depth": depth,
                        "color_type": color, "interlaced": bool(interlace)}
        elif kind == b"IDAT":
            image_data = image_data or bool(length)
        elif kind == b"IEND":
            if length != 0 or end != len(data):
                raise ValueError("invalid PNG IEND or trailing bytes")
            ended = True
            break
        offset = end
    if metadata is None or not image_data or not ended:
        raise ValueError("PNG needs IHDR, nonempty IDAT, and IEND chunks")
    return metadata


def inspect_audio(path: Path) -> dict:
    if not shutil.which("ffprobe"):
        raise ValueError("ffprobe is required to validate OGG audio")
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_streams", "-of", "json", str(path)],
        check=True, capture_output=True, text=True)
    streams = json.loads(result.stdout).get("streams", [])
    audio = [stream for stream in streams if stream.get("codec_type") == "audio"]
    if len(audio) != 1 or len(streams) != 1 or audio[0].get("codec_name") != "vorbis":
        raise ValueError("Minecraft sounds must contain a single Vorbis audio stream")
    return {"codec": "vorbis", "channels": audio[0]["channels"],
            "sample_rate": int(audio[0]["sample_rate"])}


def _validate_sounds(root: Path, namespace: str, events, errors: list[str]) -> None:
    label = f"assets/{namespace}/sounds.json"
    if not isinstance(events, dict):
        errors.append(f"{label}: expected a JSON object")
        return
    for event, definition in events.items():
        try:
            resource_id(f"{namespace}:{event}")
            if not isinstance(definition, dict) or not isinstance(definition.get("sounds", []), list):
                raise ValueError("event definition must be an object with a sounds array")
            for sound in definition.get("sounds", []):
                if isinstance(sound, str):
                    name, sound_type = sound, "file"
                elif isinstance(sound, dict):
                    name, sound_type = sound.get("name"), sound.get("type", "file")
                else:
                    raise ValueError("sound entry must be a string or an object")
                sound_namespace, sound_path = resource_id(name)
                if sound_type not in {"file", "event"}:
                    raise ValueError(f"unknown sound type {sound_type!r}")
                if sound_namespace != namespace:
                    # Other mods/vanilla may supply this resource at runtime.
                    continue
                if sound_type == "event":
                    if sound_path not in events:
                        raise ValueError(f"missing local sound event {name!r}")
                elif not (root / "assets" / namespace / "sounds" / (sound_path + ".ogg")).is_file():
                    raise ValueError(f"missing local sound file {name!r}")
        except ValueError as error:
            errors.append(f"{label}, event {event!r}: {error}")


def validate_resources(root: Path, positional_audio: bool = False) -> tuple[list[str], list[str], int]:
    errors: list[str] = []
    warnings: list[str] = []
    count = 0
    if not root.is_dir():
        return [f"resource directory does not exist: {root}"], [], 0
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        count += 1
        relative = path.relative_to(root)
        parts = relative.parts
        try:
            if len(parts) >= 2 and parts[0] in {"assets", "data"}:
                if len(parts) < 3:
                    raise ValueError("resources must be inside a namespace directory")
                resource_id(f"{parts[1]}:{'/'.join(parts[2:])}")
            if path.suffix in SOURCE_EXTENSIONS:
                warnings.append(f"{relative}: keep editable source assets outside packaged resources")
            parsed = None
            if path.suffix in {".json", ".mcmeta"}:
                parsed = read_json(path)
            if len(parts) == 3 and parts[0] == "assets" and parts[2] == "sounds.json":
                _validate_sounds(root, parts[1], parsed, errors)
            if path.suffix == ".png":
                metadata = inspect_png(path)
                for dimension in ("width", "height"):
                    value = metadata[dimension]
                    if value & (value - 1):
                        warnings.append(f"{relative}: {dimension}={value} is not a power of two; verify UVs/animation metadata")
            if path.suffix == ".ogg":
                metadata = inspect_audio(path)
                if metadata["channels"] != 1:
                    message = f"{relative}: stereo audio does not provide positional spatialization; use mono for creature/world sounds"
                    (errors if positional_audio else warnings).append(message)
        except (OSError, ValueError, subprocess.CalledProcessError) as error:
            errors.append(f"{relative}: {error}")
    return errors, warnings, count


def convert_audio(source: Path, destination: Path, stereo: bool = False) -> None:
    if not source.is_file():
        raise ValueError(f"source does not exist: {source}")
    if destination.suffix != ".ogg":
        raise ValueError("destination must have the .ogg extension")
    if destination.exists():
        raise ValueError(f"refusing to overwrite existing file: {destination}")
    if not shutil.which("ffmpeg"):
        raise ValueError("ffmpeg is required for audio conversion")
    destination.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-nostdin", "-n", "-v", "error", "-i", str(source),
                    "-vn", "-map", "0:a:0", "-c:a", "libvorbis", "-q:a", "5",
                    "-ac", "2" if stereo else "1", str(destination)], check=True)
    inspect_audio(destination)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    validate = commands.add_parser("validate", help="validate JSON, names, PNGs, and sound references/codecs")
    validate.add_argument("--resources", type=Path, default=Path("src/main/resources"))
    validate.add_argument("--positional-audio", action="store_true", help="require all OGG files to be mono")
    png = commands.add_parser("inspect-png", help="inspect PNG headers and verify chunk CRCs")
    png.add_argument("path", type=Path)
    audio = commands.add_parser("convert-audio", help="convert source audio to Minecraft Vorbis OGG (mono by default)")
    audio.add_argument("source", type=Path)
    audio.add_argument("destination", type=Path)
    audio.add_argument("--stereo", action="store_true", help="for nonpositional music/ambience")
    args = parser.parse_args()
    try:
        if args.command == "validate":
            errors, warnings, count = validate_resources(args.resources, args.positional_audio)
            for warning in warnings:
                print(f"WARNING: {warning}", file=sys.stderr)
            for error in errors:
                print(f"ERROR: {error}", file=sys.stderr)
            print(f"Validated {count} resource files: {len(errors)} errors, {len(warnings)} warnings.")
            return int(bool(errors))
        if args.command == "inspect-png":
            print(json.dumps(inspect_png(args.path), indent=2))
        else:
            convert_audio(args.source, args.destination, args.stereo)
            print(f"Created {args.destination}")
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
