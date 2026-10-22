# Architecture and extension points

The foundation deliberately contains only mod initialization and build/asset
tooling. The sections below describe where future features belong; they do not
claim those features already exist.

## Sources and resources

| Location | Responsibility |
| --- | --- |
| `src/main/java/dev/chaze/balance/BalanceMod.java` | Common initialization; safe on a dedicated server |
| `src/client/java/dev/chaze/balance/client/BalanceClient.java` | Client initialization and future rendering integration |
| `src/main/resources/fabric.mod.json` | Mod identity, entrypoints, environment, and runtime dependencies |
| `src/main/resources/assets/balance/` | Textures, sound declarations/audio, models, localization, and optional animation exports |
| `src/main/resources/data/balance/` | Data-driven gameplay content and world generation |
| `tools/` and `tests/` | Asset automation and its regression checks |
| `scripts/` | Tool activation, setup, and repeatable Gradle invocation |

Loom's split source sets ensure the common and client entrypoints are compiled
and packaged together while retaining explicit side boundaries. Add Java
packages only when they contain a concrete feature. Suitable future package
names include `registry`, `entity`, `entity.ai`, `network`, `world`, `config`, and
`client.render`; none requires an empty implementation now.

## Entities and animation

Register entity types and attributes in common initialization. Keep AI goals,
navigation, spawn rules, despawning, damage, and decisions authoritative on the
server. Perform expensive searches on bounded schedules; avoid unbounded scans
on every entity tick. Use vanilla entity tracking for ordinary fields and
Fabric payload networking for explicit interactions that need it. Validate
client requests on the server and synchronize only the state clients need.

Renderers, model layers, particles, and animation presentation belong to the
client source set. Vanilla model animation is available immediately. GeckoLib
4.9.3 has a compatible Fabric/Minecraft 1.21.1 branch and can be added for skeletal
animations later. A GeckoLib feature would include controller state, exported
geometry, animation JSON, and textures together. Walking, running, crawling,
climbing, attacks, and idle states remain future design work. Drive visual events
from authoritative synchronized state; a visual animation callback must not
become the authority for multiplayer damage.

## Audio, atmosphere, and interactions

Future sound events are registered in common code; sound assets and
`sounds.json` live under `assets/balance`. Positional audio uses mono Ogg Vorbis;
stereo is for deliberate non-positional music or ambience. Trigger important
sounds from validated gameplay events, and keep local ambience/render effects
client-side. See [the asset workflow](assets.md) for import and validation rules.

Blocks, items, and interactions use explicit registries and small feature-focused
classes. Prefer Fabric lifecycle and interaction events before introducing
mixins. When a mixin becomes necessary, document the target invariant and test
against this project's pinned Minecraft version.

## World generation and persistence

Prefer data-driven definitions under `data/balance/worldgen/`, with structure
templates under `data/balance/structure/`. Minecraft 1.21.1 uses singular names
for several other data directories, including `recipe`, `loot_table`, and
`advancement`; verify the target version's schema when importing older packs.
Use registry keys and codecs for world generation integration. Keep biome and
structure changes deterministic and compatible with server reload/world
generation rules.

Persist entity state using the target version's entity NBT hooks and synchronize
the visible subset through tracked fields or explicit payloads. World-level data
belongs in server `PersistentState` with a clear schema/version and migration
strategy. Do not treat static Java fields as saved world data. Limit writes and
avoid synchronous network or disk work on the server tick thread.

## Configuration and dependencies

Build versions are centralized in `gradle.properties`; wrapper distribution and
checksum are under `gradle/wrapper`. Use exact versions instead of snapshots or
dynamic selectors. Keep release artifacts reproducible and avoid bundling
Minecraft, Fabric Loader, Fabric API, or development dependencies in the mod JAR.
Archive timestamps and file order are normalized. Gradle's
`gradle/verification-metadata.xml` records SHA-256 values for published artifacts
and generated POMs; regular builds verify them without rewriting the file.
Exact local Loom-generated Minecraft and remapped Fabric API JAR coordinates
have scoped trust entries because their bytes change across regeneration and
source-line remapping. These are build outputs; their original Maven inputs
remain checksum-verified, and Loom verifies official Minecraft downloads using
the version manifest. Do not extend these exceptions to published dependencies.
When deliberately
upgrading dependencies, investigate official artifacts and review new checksums
before regenerating that metadata. Never regenerate it to silence an unexplained
checksum mismatch. Minecraft downloads also use Loom's manifest checksums.

There is no gameplay configuration file because there are no configurable
features yet. When one is needed, use Fabric Loader's config directory, explicit
defaults and validation, and a versioned format. Keep server-authoritative
settings on the server; synchronize any values needed by client presentation.
Never embed secrets or machine-specific absolute paths in packaged resources.

## Verification as features arrive

Keep pure logic independently testable where that produces useful coverage. Add
Minecraft GameTests or server integration checks when a feature needs actual
registries, ticks, world state, or entities. Test rendering/animation/sound with a
working client before reporting visual or audio correctness. Multiplayer claims
require a server and real client interaction, including late join/reconnect
state synchronization; a successful build cannot establish these properties.
