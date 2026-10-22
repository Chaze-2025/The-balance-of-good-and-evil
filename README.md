# The-balance-of-good-and-evil

A Minecraft Java Edition mod development foundation for Codex Cloud. The minimal
`balance` mod supplies common and client initialization only. It adds no creatures,
items, blocks, sounds, structures, or gameplay behavior.

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
./scripts/gradle.sh clean build
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
build validates the project resources; Python tests exercise asset validation and
conversion independently of Minecraft.
The suite also checks the declined-EULA smoke tool's refusal rules and reporting;
it contains no Java gameplay tests yet.

The GitHub Actions build workflow runs the same Gradle checks and Python tests.
CI needs no repository secrets. Consult the pull request's Actions checks for
the actual hosted result; local build results are recorded separately.

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

## Extend the foundation

- [Game design](docs/GAME_DESIGN.md): agreed survival horror direction and future
  requirements; the described gameplay systems are not implemented.
- [Architecture](docs/architecture.md): common/client boundaries, authoritative
  server logic, persistence, synchronization, entities, and world generation.
- [Assets](docs/assets.md): Blockbench/Blender interchange, textures, animations,
  and command-line audio validation and conversion.
- [Network access](docs/network.md): required download hosts, optional hosts, and
  the platform's existing GitHub integration.
- [Agent instructions](AGENTS.md): safe, repeatable work in future Codex tasks.

To create the first feature, choose its behavior and acceptance checks, implement
the smallest common/server component under `dev.chaze.balance`, add client-only
rendering and resources separately, then run the full build and relevant server
or client checks. GeckoLib is an evaluated optional dependency, not installed in
this foundation; add it only when a feature needs skeletal entity animation.
