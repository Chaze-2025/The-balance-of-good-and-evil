# Environment verification

Validated in Codex Cloud on 2026-10-22, Linux x86_64 (Debian 13). This is a
development foundation with common/client initializers and no gameplay features.

## Tools and provenance

| Tool | Version/status | Source or preparation |
| --- | --- | --- |
| Full JDK, `javac`, `jar`, `jdb`, `jcmd`, `jstack`, `keytool` | Eclipse Temurin 21.0.12.1+1 | Official Adoptium GitHub archive, publisher SHA-256 checked |
| Gradle and Gradle Wrapper | 8.14.3 | Official distribution and wrapper JAR, SHA-256 checked |
| Fabric Loader | 0.19.5 | Official Fabric Maven; server discovery verified |
| Fabric API | 0.116.17+1.21.1 | Official Fabric Maven; compiled and bootstrapped |
| Fabric Loom | 1.10.5 | Official Fabric Maven; compile/remap/decompile tasks executed |
| Yarn mappings | 1.21.1+build.3 | Official Fabric Maven; mapped source archives generated |
| Git | 2.52.0 | Preinstalled; existing GitHub HTTPS read integration verified |
| Python | 3.12.14 | Preinstalled; asset/build utilities use the standard library |
| FFmpeg and ffprobe | 7.1.5 | Preinstalled; real WAV-to-Vorbis conversion and inspection passed |
| Blender | 4.3.2 | Preinstalled; factory-startup background Python check passed |
| curl, unzip, zip, jq, Bash | Preinstalled utilities | Download/archive/scripting utilities; no additional paid service |

The original machine provided a Java runtime without the full compiler/debugger
toolset. The full JDK is now at `/workspace/.tools/minecraft/jdk-21.0.12.1+1`.
`scripts/env.sh` activates it and configures Java's proxy and CA trust without
turning off certificate checks. Gradle caches are retained outside the checkout
at `/workspace/.cache/gradle-balance`; mapped sources also use ignored `.gradle/`.
No additional Python packages or optional animation libraries were installed.

## Completed checks

| Check | Actual result |
| --- | --- |
| Git checkout and GitHub repository read | Clean before scaffold; `git ls-remote origin HEAD` succeeded |
| JDK and Gradle versions | Java/javac 21.0.12.1; Gradle 8.14.3 executed |
| Reusable installer | Passed from an absent temporary JDK directory using the verified archive, then reused the normal dependency cache |
| Minecraft/Fabric dependency resolution | Passed against official repositories with TLS and checksums retained |
| `clean build genSources` | Passed; common and client compilation, remapped mod and source JARs produced |
| Mapped Minecraft sources | 4,064 common and 1,299 client-only Java source files present in generated source archives |
| Final `./scripts/gradle.sh --no-daemon --offline --warning-mode all clean build` | Passed in 11 seconds, 12 tasks executed; prepared cache supports an offline clean build |
| Python regression suite through `check` | 20 passed, 0 failed, 0 skipped: 8 asset tests and 12 smoke-tool reporting/EULA tests |
| Resource validation | 15 files checked, 0 errors, 0 warnings; includes 12 empty-directory placeholders excluded from the JAR |
| Mod JAR validation | Valid ZIP, expanded mod version, both entrypoint classes, Java 21 bytecode version 65, resource metadata present |
| `python3 tools/server_smoke.py` | Passed; Minecraft 1.21.1/Fabric 0.19.5 discovered `balance 0.1.0`, executed the common initializer, then refused to run a world without EULA agreement; Gradle exit 0 |
| Java debugger | `runServer --debug-jvm` bound to loopback; `jdb -attach 127.0.0.1:5005` attached, listed threads, and resumed the JVM to the same EULA boundary |
| Blender background Python | `bpy` initialized with Blender 4.3.2 and the three factory-startup scene objects; process exited 0 |
| Repository integrity | Original README title preserved; only the requested foundation and documentation were added/updated |

The first-ever server bootstrap logged a missing `server.properties` and created
default settings and `eula=false`. The final repeated bootstrap completed without
that first-run warning. Neither the installer nor smoke tool accepts the EULA.
The final build also resolved an execution-time Gradle project-access warning in
the initial scaffold; the final clean build emitted no deprecation warning.

The Gradle Java `test` task has **NO-SOURCE**. The 20 executed tests validate the
Python development tools; they are not Java unit tests, GameTests, or gameplay
tests. No such behavior exists in the minimal mod yet.

## Produced artifact and logs

Distributable: `build/libs/balance-0.1.0.jar` (3,366 bytes).
SHA-256 at validation:
`0323470f24193427594be88babf3e8c2ecd8c71f347322f8a8298c48082b1d45`.

The source archive is `build/libs/balance-0.1.0-sources.jar`; do not install that
as the playable mod. Fabric Loader and the matching Fabric API are separate
runtime requirements for a normal Minecraft installation.

Current-instance evidence is under ignored `build/reports/`:

- `clean-build.log`, `setup.log`, and `mod-jar.json`.
- `server-smoke.log` and `server-smoke.json` from the final smoke invocation.
- `debugger.log` and `debug-server.log` from the actual debug attachment.

These files are generated evidence, not committed source, and `clean` removes
them. The checked-in automation and this report explain how to reproduce checks.

## Limits and reusable cloud configuration

No Minecraft world was started: the server stopped at the declined-EULA boundary.
Game ticks, gameplay, multiplayer synchronization, rendering, positional audio
playback, creature AI, structures, and animations are untested. The initializer
log is evidence only of successful bootstrap and common mod initialization.

Minecraft's graphical client, Blockbench, Blender's GUI, and Audacity's GUI were
not run. No remote desktop, GPU/display pipeline, account login, or streaming
service was installed. Compilation, source inspection, asset conversion, and
Java debugging work through cloud commands without a personal computer.

GeckoLib 4.9.3 has relevant official Fabric/1.21.1 source metadata but remains
optional and uninstalled; its Cloudsmith Maven host was denied by the current
network policy. Its actual artifact resolution and in-game behavior need testing
when animation work begins. No NeoForge-only requirement was identified.

A GitHub Actions workflow was created during initial onboarding, which validated
the foundation locally without pushing it or triggering a hosted run. Later
preservation work reports its verified branch, pull request, and Actions results
separately; this initial-validation record does not establish those outcomes.
Git read access alone does not establish push access.

The environment configuration draft has saved `install_script` and `start_skill`
contents pointing to the tested setup, activation, build, and optional bootstrap
commands. Existing repositories, credentials, and network settings were preserved.
No new secret is needed for the verified workflow. Review and save the draft in
environment settings, then publish to retain the prepared environment. Saving a
draft did not publish a snapshot, and restoration in a new task is not yet tested.

To begin the first mod feature, define its behavior and acceptance checks, add
only the required Java/resources in the documented common/client layout, run
`./scripts/gradle.sh clean build`, and perform the relevant server/client tests.
For a world run, the user must first review Minecraft's EULA and agree in
`run/server/eula.txt`; then `./scripts/gradle.sh runServer` can be validated beyond
bootstrap. See [architecture](architecture.md), [assets](assets.md), and
[network requirements](network.md).

## Preservation recheck

Before preserving the foundation on its feature branch, the clean build,
20 Python tests, and asset validation were rerun successfully. An isolated source
export with no project build/Loom cache exposed a portability issue in the
initial checksum metadata: regenerated local Minecraft and remapped Fabric API
JARs had different bytes from the original cache. No published upstream artifact
failed verification.

The metadata now scopes trust to exactly 74 local generated JAR coordinates
(group, name, version, and filename). Original published dependencies and the
generated POMs retain their checksums. The isolated source export then passed a
strict clean build: 20 tests passed, none skipped, with zero resource errors or
warnings. Its distributable has the same SHA-256 recorded above. Raw downloaded
dependency caches were reused for this check; it was not a new cloud instance.
Hosted Actions results and remote push/PR state require separate verification.
