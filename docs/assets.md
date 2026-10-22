# Asset workflow

All tools in this workflow are free. Asset checking uses Python's standard library;
audio conversion and codec inspection use FFmpeg and ffprobe. No Python packages
are required. Keep editable originals outside `src/main/resources`, for example
in `asset-sources/`, and commit originals when their size and licensing permit it.
Only exported game assets belong in the mod JAR. Record each asset's author,
license, source, and export settings; do not redistribute Minecraft's own assets.

## Resource layout

| Asset | Export location under `src/main/resources` |
| --- | --- |
| Block/item texture | `assets/balance/textures/block/<name>.png` or `textures/item/<name>.png` |
| Blockstate | `assets/balance/blockstates/<name>.json` |
| Crafting recipe | `data/balance/recipe/<name>.json` |
| Block loot | `data/balance/loot_table/blocks/<name>.json` |
| Entity texture | `assets/balance/textures/entity/<name>.png` |
| Vanilla block/item model | `assets/balance/models/block/<name>.json` or `models/item/<name>.json` |
| GeckoLib geometry, if GeckoLib is later added | `assets/balance/geo/<name>.geo.json` |
| GeckoLib animation, if GeckoLib is later added | `assets/balance/animations/<name>.animation.json` |
| Sound | `assets/balance/sounds/<category>/<name>.ogg` |
| Sound event definitions | `assets/balance/sounds.json` |
| Translation | `assets/balance/lang/en_us.json` |
| Structure NBT | `data/balance/structure/<name>.nbt` |
| World generation data | `data/balance/worldgen/<registry>/<name>.json` |

Use lowercase `snake_case` paths and the namespace `balance`. Minecraft 1.21.1
uses singular data registry directories such as `structure`, `loot_table`, and
`recipe`. Use matching version documentation when adding data packs. Empty
folders are placeholders, not implemented content.

## Blockbench and Blender

Blockbench is the recommended free editor for Minecraft models. Its web edition
can be used remotely from a compatible browser; usability on iPhone depends on
the browser and screen. This setup does not claim the graphical editor was run
or tested. Use a vanilla Java entity export for Java model code, or a GeckoLib
project/export for `.geo.json` and `.animation.json` when a compatible GeckoLib
dependency has deliberately been added. These formats are different; vanilla
Minecraft does not load GeckoLib geometry or arbitrary `.bbmodel` files itself.

Blender is useful for source mesh editing and texture baking. Exported OBJ/glTF
or `.blend` files are not native Minecraft entity models. Convert/rebuild the
model for the selected renderer, preserving UVs and the intended coordinate
scale. A future Blender automation command can run with
`blender --background <source.blend> --python <export.py>`; availability of the
executable alone does not establish that an export pipeline or GUI works.
The current cloud image passed a Blender 4.3.2 background Python invocation;
no graphical editing or model export was tested.

Walking, running, crawling, climbing, attacking, and idle clips should be separate
named animation clips. Future client animation controllers should derive state
from server-authoritative entity data; do not make gameplay depend on a rendered
animation frame. No models, animation controllers, or gameplay clips are
implemented for entities in this phase. The Vigil Torch uses ordinary block/item
models rather than skeletal animation. Evaluate GeckoLib's exact Minecraft/Fabric
artifact before adding it; do not install heavy rendering dependencies by
default.

## Textures

Export PNG in the dimensions expected by the model UVs. Power-of-two dimensions
are conventional but are not universally required; the validator warns instead
of rejecting other dimensions. Animated textures also require matching
`<texture>.png.mcmeta`; the actual texture/animation must be checked in game.

```sh
python3 tools/asset_tool.py inspect-png path/to/texture.png
python3 tools/asset_tool.py validate
```

PNG validation checks signature, header fields, chunk lengths, and CRCs. It does
not decode pixels, inspect UV alignment, enforce renderer-specific dimensions,
or validate every Minecraft JSON schema. JSON validation rejects malformed
files, duplicate keys, and nonstandard NaN/Infinity values. Resource files are
checked for valid case-sensitive Minecraft paths.

## Vigil Torch assets

Phase 1 supplies an original 16×16 pixel-art golden/amber torch texture and
standing/wall models using vanilla torch model templates. The texture is authored
for this repository; no third-party artwork or copied Minecraft texture is
packaged. Vanilla model parents remain references resolved by Minecraft. The
item, both blockstates, English localization, one shapeless recipe, and both
block loot tables are shipped together. Both variants drop `balance:vigil_torch`.

The recipe uses Minecraft 1.21.1's singular `recipe` directory and result `id`
field. Local model, texture, recipe, loot, and translation relationships are
checked by the feature-specific validator:

```sh
python3 tools/validate_vigil_resources.py
```

The resource tests also mutate these relationships to ensure invalid content is
rejected. Neither JSON/PNG validation nor a valid model parent establishes UV
appearance, particle restraint, actual light propagation, or item presentation
in a rendered Minecraft client. Those remain in-game review tasks.

## Audio

Create/edit source audio with an open-source editor such as Audacity, or use
FFmpeg entirely in the cloud. Audacity's graphical application is optional and
has not been tested here. Minecraft uses OGG Vorbis; an `.ogg` extension alone
does not guarantee the right codec. Use mono for positional creature/world
sounds; stereo is appropriate for nonpositional music or ambient beds.

```sh
python3 tools/asset_tool.py convert-audio asset-sources/growl.wav src/main/resources/assets/balance/sounds/creature/growl.ogg
python3 tools/asset_tool.py convert-audio asset-sources/music.wav src/main/resources/assets/balance/sounds/music/theme.ogg --stereo
python3 tools/asset_tool.py validate
# Optional policy when every sound in a resource tree must be positional:
python3 tools/asset_tool.py validate --positional-audio
```

Conversion refuses to overwrite an existing output. It uses Vorbis quality 5,
preserves FFmpeg's input sample-rate selection, and selects the first audio
stream. Listen to and tune loudness/loop boundaries before shipping; the
validator does not judge perceived volume or sound quality.

When implementing a future sound, use an explicit namespaced file reference in
`sounds.json`:

```json
{
  "creature.growl": {
    "sounds": ["balance:creature/growl"]
  }
}
```

The checker verifies local sound file/event references and the Vorbis codec using
ffprobe. References to `minecraft` or other namespaces cannot be resolved from
this repository alone and are left for runtime verification. Unqualified sound
names default to `minecraft`, so always specify `balance:` for local assets.
Stereo files produce warnings unless `--positional-audio` is supplied.

## Validation

```sh
python3 -m unittest discover -s tests -v
python3 tools/asset_tool.py validate
python3 tools/validate_vigil_resources.py
```

The tests exercise malformed JSON, invalid identifiers, corrupt PNG chunks,
missing sound files/events, actual WAV-to-Vorbis conversion, channel inspection,
and overwrite protection. Audio tests are explicitly skipped if FFmpeg/ffprobe
are unavailable. Passing these checks does not establish that a model animates
or that Minecraft gameplay has been tested.
