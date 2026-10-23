# Codex development instructions

## Working scope

Use the existing checkout at `/workspace/The-balance-of-good-and-evil`. Each cloud
task is already isolated. Do not create a Git worktree unless the user explicitly
requests one. Inspect `git status --short` before and after changes; preserve
unrelated edits and existing assets. Never reset or overwrite user work to make a
build pass.

The repository includes the Vigil Torch prototype and Phase 2 Nightfall clock
on the Fabric foundation. Read `docs/vigil-torch.md` and `docs/nightfall.md` before
changing either system. Do not add gameplay as part of environment maintenance
or expand into creatures, Nightfall encounters, world generation, villages,
factions, or stronger wards without a user-defined feature scope.

## Tool activation and checks

- Run `bash scripts/setup.sh` when the JDK or prepared dependencies are missing.
  Reuse installed tools and caches; do not repeat downloads without a reason.
- Run Gradle through `./scripts/gradle.sh`. For raw `./gradlew` or JDK tools,
  first run `source scripts/env.sh` in that shell.
- Required build check:
  `./scripts/gradle.sh --no-daemon --dependency-verification strict clean build`.
- Asset utility tests: `python3 -m unittest discover -s tests -v`.
- Resource checks: `python3 tools/asset_tool.py validate` and
  `python3 tools/validate_vigil_resources.py`.
- Packaged artifact check:
  `python3 tools/verify_jar.py build/libs/balance-0.1.0.jar`.
- Generate mapped Minecraft sources with `./scripts/gradle.sh genSources` when
  needed for API investigation. Consult actual 1.21.1 Yarn names rather than
  examples targeting other Minecraft versions.

Inspect the current manifests and scripts if the mod version or command names
change. Record the actual tests, counts, runtime behavior, and failures. Never
describe a zero-test Gradle task, compilation, a live process, or an open port as
proof that gameplay works.

## Architecture

Java 21, Minecraft 1.21.1, Fabric Loader/API, Loom, Yarn, and Gradle are pinned.
Do not upgrade one component without checking the entire compatibility set and
updating the evidence in `docs/decisions.md`.

Use `dev.chaze.balance` and resource namespace `balance`. Keep server-safe code in
`src/main` and client rendering/audio/UI code in `src/client`. Never reference
client-only Minecraft classes from common initializers or server logic. Keep the
dedicated server capable of loading the mod without client classes.

Follow `docs/architecture.md` for registries, entity synchronization, persistent
state, data-driven content, and configuration. Avoid speculative abstractions or
empty feature implementations. Add dependencies only when an actual feature
needs them; GeckoLib is optional and is not installed. Respect asset licenses.
Read `docs/GAME_DESIGN.md` for the agreed direction. Its future requirements do
not themselves authorize more gameplay or story content. Keep the implemented
Vigil Torch and Nightfall foundations distinct from those requirements. Ward
state and effects are server-authoritative: do not trust particles or client interaction for
protection, force-load chunks, clear unrelated potion effects, or persist the
derived ward index. Extend meaningful pure-Java tests alongside logic changes;
world lifecycle and visual claims require actual Minecraft runtime checks.

Nightfall must read authoritative `ServerWorld` time of day and normalize with
long-safe floor modulo over 24,000 ticks. Queries must remain fresh between
scheduled observations; queries must not consume or emit transitions. Keep
phase-transition notifications out of initialization, report only direct observed
phase changes, and isolate transient state per world. The Nether, End, and custom
dimensions remain inactive until explicitly supported. Nightfall must never change vanilla time, spawning,
stats, or Vigil Torch behavior. Its base intensity cannot override protection.
Keep settings restart-only and validated, preserve existing configuration files,
and avoid custom networking until a concrete client feature requires it.

## Runtime boundaries

Do not automatically accept the Minecraft EULA. `runServer` requires the user's
own agreement before running a world. An EULA-declined smoke check verifies only
startup to that boundary.

Do not claim graphical Minecraft, Blockbench, Blender UI, multiplayer behavior,
or gameplay has been tested without actually executing the appropriate checks.
No graphical client or streaming service is guaranteed in this environment.
Keep debugger ports on loopback and never expose credentials in logs or files.

Builds need no persistent services. Installed dependencies and caches may survive
publication; processes and debug connections do not. If a server is necessary for
a task, start it deliberately, verify its logs and behavior, and stop only the
processes started by that task.

## Downloads and reproducibility

Use official HTTPS sources, the existing cloud proxy and CA trust, and the pinned
Gradle Wrapper. Keep checksum and TLS validation enabled. Preserve inherited
authentication; do not dump environment values or credential files. Verify
GitHub read access with a read-only Git operation before requesting credentials.
Read access does not imply push/API authorization.

Consult `docs/network.md` when downloads fail. A policy denial requires supported
environment settings; do not bypass the proxy. Preserve existing allowed hosts
when adding a genuinely required destination. Do not install graphical/heavy
tools, paid services, or external infrastructure without a demonstrated need.
