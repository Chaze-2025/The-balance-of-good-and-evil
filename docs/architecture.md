# Architecture and extension points

The implemented systems are the Vigil Torch ward prototype and Phase 2 Nightfall
clock/base danger state. The entity, animation, audio, and world-generation
sections below describe future extension points. See [Vigil Torch](vigil-torch.md)
and [Nightfall](nightfall.md) for behavior and runtime test limits.

## Sources and resources

| Location | Responsibility |
| --- | --- |
| `src/main/java/dev/chaze/balance/BalanceMod.java` | Common initialization; safe on a dedicated server |
| `src/client/java/dev/chaze/balance/client/BalanceClient.java` | Client initialization and Vigil Torch cutout rendering |
| `src/main/java/dev/chaze/balance/registry/` | Explicit block, block-entity, and item registration |
| `src/main/java/dev/chaze/balance/block/` | Standing/wall torches and marker block entity |
| `src/main/java/dev/chaze/balance/ward/` | Pure ward geometry, source index, effect policy, and server adapter |
| `src/main/java/dev/chaze/balance/nightfall/` | Pure phases/clock/state/transition tracking and server adapter |
| `src/main/java/dev/chaze/balance/config/` | Validated restart-only ward and Nightfall settings |
| `src/test/java/dev/chaze/balance/` | Java ward, Nightfall, and configuration unit tests independent of a running world |
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

The Vigil Torch uses a versioned properties file in Fabric Loader's config
directory: `config/balance-wards.properties`. Its radius defaults to 14 blocks;
values must be finite, positive, and at most 64. Configuration is server-side and
requires restart. Missing files receive defaults; existing files are preserved.
Invalid or unreadable settings fail startup rather than falling back silently.
The localized interaction message is sent by the server, so
no custom networking is needed to display the configured radius. Never embed
secrets or machine-specific absolute paths in packaged resources.

## Implemented ward system

`WardSource` holds block coordinates and a radius. It uses inclusive squared
3D Euclidean distance from the block center to the target position. `WardIndex`
contains the currently loaded source positions for one world and answers union
queries; `WardEffectPolicy` determines whether Slowness I should be refreshed.
These classes contain no Minecraft world access and have focused Java tests.
The Minecraft adapter owns scheduling, entity-type checks, and status application.

Both torch variants have a minimal marker block entity with no per-tick ticker
or custom saved ward state. Fabric block-entity load/unload events populate and
remove positions in transient per-`ServerWorld` indexes. World unload clears the
index, and source validity is checked again before querying entities. Minecraft
block/block-entity data remains authoritative across saves and reloads. No
Minecraft entity or chunk reference is retained in the source index.

Once per 20 server ticks, each valid loaded source performs a bounded nearby
zombie query, then filters by exact ordinary zombie type and spherical distance.
One per-world set deduplicates targets across overlapping source queries. The
adapter uses the pure effect policy to preserve stronger, longer, or infinite
unrelated Slowness. It never removes effects when a target leaves; short ward
effects expire naturally. All world access remains on the server thread.

Expected work per refresh is `O(W + sum(C_i))`, where `W` is loaded ward count
and `C_i` is the nearby-query candidate count for ward `i`; deduplication uses
space proportional to distinct eligible targets. Overlapping sources still
repeat bounding-box queries, even though effect application is deduplicated.
Dense torch farms and very high configured radii are the primary performance
risks. There are no whole-world block/entity scans, forced chunk loads, custom
packets, or per-torch tickers. Profile real settlement workloads before adding
a spatial batching scheme.

Future wards can reuse source geometry, loaded-world bookkeeping, and union
queries while selecting a different effect or eligibility policy. Register
actual sources through the same lifecycle boundary; do not make block light or
particles determine protection. Hearth Altars, Sanctuary Hearts, creature
resistance, factions, and director integration remain unimplemented.

## Implemented Nightfall foundation

`NightfallPhase` defines five daily phases. `NightfallClock` maps a supplied long
Minecraft time to a phase using floor modulo over 24,000 ticks. `NightfallConfig`
validates ordered boundaries, finite normalized intensities, the enabled flag,
and the bounded observation interval. `NightfallState` represents current state;
an inactive dimension has no phase and intensity zero. `NightfallTracker` retains
only the last observation needed to distinguish an initial state, unchanged
state, or direct phase transition. These contracts are independently testable.

`NightfallService` adapts them to Fabric server lifecycle and world tick events.
Queries read the supplied world's current `getTimeOfDay()` value, so a scheduled
observation cache cannot make the current phase stale. A separate, nonmutating
query compares that current phase with the last observed phase. World loading
establishes a baseline without a transition notification. Scheduled observations
emit one server-side notification for a direct phase change; sleep or time
commands do not generate fictional intermediate phases. World unload and server
shutdown discard derived observation state. No phase or danger value is saved.

Only the Overworld is supported in Phase 2. The Nether, End, and custom dimensions
are inactive; effective enabled state also respects the global configuration.
A command source uses its current world for `/balance nightfall status` and must
have permission level 2. No client tick, FPS value, player-owned clock, custom
packet, or permanent HUD participates in the service.

Each current-state query and scheduled observation takes `O(1)` work. Stored
observations require `O(D)` space for loaded worlds, and one observation cycle
performs `O(D)` work for `D` worlds. This work does not depend on player, entity,
chunk, or ward counts. The default interval is 20 server ticks and can be set
between 5 and 200. The server tick schedule determines when to observe, while
Minecraft world time determines every phase and intensity. A frozen world clock
therefore cannot artificially increase danger.

Base intensity is only a time-related input for future systems. A future
encounter policy must also consider protection, territory, player circumstances,
recent encounters, and difficulty; high nighttime intensity must never bypass
Light territory or ward rules. Phase 2 does not combine those inputs, search for
encounters, spawn creatures, or alter vanilla mobs. Vigil Torch code, resources,
configuration, indexing, and tests retain their Phase 1 behavior.

Nightfall configuration lives in `config/balance-nightfall.properties`, uses
`version=1`, and requires a restart. Missing files receive defaults; existing
files are preserved. Invalid or unreadable settings fail startup clearly. See
[Nightfall](nightfall.md) for the complete schema and validation constraints.

## Verification as features arrive

Keep pure logic independently testable where that produces useful coverage. Add
Minecraft GameTests or server integration checks when a feature needs actual
registries, ticks, world state, or entities. Test rendering/animation/sound with a
working client before reporting visual or audio correctness. Multiplayer claims
require a server and real client interaction, including late join/reconnect
state synchronization; a successful build cannot establish these properties.
