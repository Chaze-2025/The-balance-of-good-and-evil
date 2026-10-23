# The-balance-of-good-and-evil

A Minecraft Java Edition supernatural survival horror mod developed in Codex
Cloud. The **Vigil Torch** provides the first protective light source. Phase 2
adds **Nightfall**, a server-side clock and base supernatural danger state derived
from Minecraft time. The larger mythology, creatures, settlements, and encounters
remain planned; see [the game design](docs/GAME_DESIGN.md).

## Supported toolchain

| Component | Pinned version |
| --- | --- |
| Minecraft Java Edition | 1.21.1 |
| Java development kit | Eclipse Temurin 21.0.12.1+1 |
| Gradle Wrapper | 8.14.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.116.17+1.21.1 |
| Fabric Loom | 1.10.5 |
| Yarn mappings | 1.21.1+build.3 |

Minecraft 1.21.1 is a deliberate compatibility baseline with an established
animation and mod ecosystem, rather than the latest Minecraft release. Dependency
pins live in `gradle.properties` and the wrapper configuration. See
[the selection rationale](docs/decisions.md) before changing them together.

## Start development in Codex Cloud

Use the existing checkout; cloud tasks already have isolation. No personal
computer installation is required for compiling and maintaining the project.

```bash
cd /workspace/The-balance-of-good-and-evil
bash scripts/setup.sh
./scripts/gradle.sh --no-daemon --dependency-verification strict clean build
python3 tools/verify_jar.py build/libs/balance-0.1.0.jar
```

Setup installs the full JDK when needed, checks its official download checksum,
and prepares the pinned wrapper and dependencies. Later tasks reuse the installed
JDK and Gradle caches. `scripts/gradle.sh` activates the tools and cloud proxy
configuration for each invocation; it does not require an interactive shell.
Alternatively, source `scripts/env.sh` before invoking `./gradlew` directly.

```bash
./scripts/gradle.sh genSources
python3 -m unittest discover -s tests -v
python3 tools/asset_tool.py validate
```

The distributable is `build/libs/balance-0.1.0.jar`. A sources JAR is for
development and is not the file to place in a player's `mods` directory. The
build runs Java unit tests for ward logic, Nightfall phase calculations,
configuration, and transition policies, plus Python asset/resource and
declined-EULA smoke-tool tests.
Those checks do not replace testing in a running Minecraft world.

The GitHub Actions build workflow runs the same Gradle checks and Python tests.
CI needs no repository secrets. Consult the pull request's Actions checks for
the actual hosted result; local build results are recorded separately.

## Cloud testing and iPhone reports

[The cloud testing workflow](docs/cloud-testing.md) adds isolated Fabric 1.21.1
GameTest sources and reports that can be reviewed through GitHub Actions on an
iPhone. Regular checks compile GameTests without starting Minecraft worlds.
The job summary is the primary mobile view; `cloud-test-reports` contains HTML,
summaries, XML results, and available logs.

World-running tests require the owner's explicit Minecraft EULA agreement.
Fabric's GameTest runner bypasses the usual EULA-file check, so `eula=false`
alone is not a sufficient guard. The project requires explicit owner consent
before its isolated runtime launcher runs and preserves the existing EULA file.
The guide explains authorization, manual execution, report interpretation, and
investigated multiplayer/screenshot/video options. Compiled tests are not
reported as completed gameplay tests.

## Vigil Torch prototype

Craft one regular torch, one gold nugget, and one amethyst shard together to make
one Vigil Torch. Place it on a suitable floor or wall, or find it in the
Functional Blocks Creative inventory category. It emits normal level-14 block
light and a few golden particles. Interact with it to show its configured range.

The separate supernatural ward has a default **14-block spherical radius** from
the torch block center, including the boundary. On the server, ordinary vanilla
zombies inside that sphere receive Slowness I on a scheduled refresh. The effect
expires naturally shortly after leaving all wards or removing the torch. It
does not stack across overlapping torches or replace stronger Slowness. Other
mobs are unaffected. This is a weakening prototype, not enemy repulsion.

See [Vigil Torch behavior, configuration, and testing](docs/vigil-torch.md) for
placement rules, exact distance calculation, server configuration, and the
remaining in-game checks. No creatures, director, factions, altars, village
simulation, or custom world generation are implemented.

## Nightfall foundation

Nightfall reads the server world's existing time without changing the vanilla
clock, mob spawning, damage, health, or AI. Its default Overworld phases are:

| Phase | Minecraft time of day | Base danger intensity |
| --- | --- | --- |
| Daylight | 0–11,499 | 0.15 |
| Dusk | 11,500–12,999 | 0.40 |
| Night | 13,000–16,999 | 0.75 |
| Deep Night | 17,000–22,499 | 1.00 |
| Dawn | 22,500–23,999 | 0.25 |

Server APIs return current state from authoritative world time, including between
scheduled transition observations. Sleep and time commands can jump directly to
a new phase; polling does not invent intermediate transitions. The Nether, End,
and custom dimensions are inactive in this phase. Vigil Torches remain independent.

Operators with permission level 2 can inspect their command's current dimension:

```text
/balance nightfall status
```

The command reports dimension, normalized time, phase, base intensity, and whether
Nightfall is active. Settings live in `config/balance-nightfall.properties` and
require a server restart. See [Nightfall behavior and testing](docs/nightfall.md)
for exact intervals, configuration validation, API contracts, and runtime limits.
Base intensity is a future decision input; it does not override wards or the
planned permanent safety of Light territory. There are no Nightfall encounters,
client HUD, or custom network messages yet.

## Runtime and debugging

The cloud machine can compile both sides without a graphical desktop. Server
startup and world simulation are separate checks from compilation:

```bash
./scripts/gradle.sh runServer
```

For a repeatable check that stops at the declined-EULA boundary, run
`python3 tools/server_smoke.py`. It refuses to run if an existing EULA file
already records agreement and retains the process log and result under
`build/reports/`.

Minecraft creates `run/server/eula.txt` on first server launch. Read the linked Minecraft
EULA and accept it yourself only if you agree, then rerun the command. Setup and
automation never accept it on your behalf. The EULA-declined smoke check only
verifies the startup boundary; it does not establish a running game world or
tested gameplay.

For a server that is permitted to run, start a suspended Java debug session with
`./scripts/gradle.sh runServer --debug-jvm`, then use `jdb -attach 127.0.0.1:5005`
from a second shell after sourcing `scripts/env.sh`. Keep the debugger on loopback;
do not expose it publicly. Inspect `run/server/logs/latest.log` for server diagnostics.

`runClient` needs a working display and OpenGL environment. Graphical Minecraft,
Blockbench, and Blender interfaces are not established by this setup. No remote
desktop or game streaming service is included. See [validation evidence and
limitations](docs/verification.md) for checks actually executed.

## Continue development

- [Game design](docs/GAME_DESIGN.md): agreed survival horror direction and future
  requirements; the Vigil Torch prototype implements only the first ward test.
- [Nightfall](docs/nightfall.md): phases, base intensity, server configuration,
  diagnostics, transition behavior, and remaining runtime checks.
- [Architecture](docs/architecture.md): common/client boundaries, authoritative
  server logic, persistence, synchronization, entities, and world generation.
- [Assets](docs/assets.md): Blockbench/Blender interchange, textures, animations,
  and command-line audio validation and conversion.
- [Network access](docs/network.md): required download hosts, optional hosts, and
  the platform's existing GitHub integration.
- [Agent instructions](AGENTS.md): safe, repeatable work in future Codex tasks.

Keep future changes small and independently testable. Reuse the ward source,
index, and effect policy instead of building a second protection system. The
recommended next technical milestone is a bounded, cooldown-based encounter
eligibility policy that combines Nightfall with ward checks and deterministic
tests. A separately scoped original creature can then consume that policy.
Neither encounter selection nor new creatures are implemented in Phase 2.
GeckoLib remains an evaluated optional dependency; add it only when skeletal
entity animation is needed.
