# Environment verification

The foundation was validated in Codex Cloud on 2026-10-22, Linux x86_64
(Debian 13). The original evidence below describes the foundation before
Phase 1; it does not describe the current Vigil Torch artifact. Current feature
verification is recorded in the Phase 1 section at the end of this document.

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

At initial foundation validation the Gradle Java `test` task had **NO-SOURCE**.
Its 20 executed tests validated Python development tools, not gameplay. Phase 1
adds real Java ward-logic tests; their results belong to the current feature
record rather than this historical foundation evidence.

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

For later features, define behavior and acceptance checks, add only the required
Java/resources in the documented common/client layout, run the strict clean
build, and perform the relevant server/client tests.
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

## Phase 0: missing published checksums

The failed PR workflow run `39730392822` stopped during dependency verification.
A source export of commit `bb2b0a6` with an entirely empty `GRADLE_USER_HOME`
reproduced the two reported classpath failures: `guava-parent-33.0.0-jre.pom` and
`junit-bom-5.10.1.module`. The existing JUnit POM checksum was already correct;
the missing JUnit entry was its Gradle module metadata, not its POM.

After verifying and adding those entries, the cold-cache build exposed two more
unrecorded published artifacts: the Fabric Intermediary 1.21.1 v2 JAR and POM.
The Minecraft libraries configuration then required JUnit BOM module metadata
for 5.10.0 and 5.9.3. These six failures were all missing entries, not changes to
known expected hashes.
Earlier successful builds reused dependency caches, which did not exercise all
of the published metadata and mapping downloads required by a cold build.

The following SHA-256 entries were added individually:

| Published artifact | Verified SHA-256 |
| --- | --- |
| `com.google.guava:guava-parent:33.0.0-jre` POM | `040cc88c680b413d70ba9fc8371b36093021b10996aa3598621de767d418229a` |
| `org.junit:junit-bom:5.10.1` module metadata | `21b0afcfffe2ecb3770f5eb00ae7a19feaee94e771fa3918173850dae78067b7` |
| `org.junit:junit-bom:5.10.0` module metadata | `eb3ee6127608010694a898056e7407d117296003aba5f5db801df430b9887fcf` |
| `org.junit:junit-bom:5.9.3` module metadata | `b401fd25901e582a524aa5343c4b39e28bc56e24961c1069bf2b4bbfcee46b93` |
| `net.fabricmc:intermediary:1.21.1` v2 JAR | `e8bd6a4c39e235ee2dc73063558610775c72a2572fa0c6b32915b149074012a6` |
| `net.fabricmc:intermediary:1.21.1` POM | `b6ec5f03e95bea232b6960ed077dd0a765a3aabfd74732ee600eb77d2651adf4` |

Verification provenance:

- Guava's [published POM](https://repo.maven.apache.org/maven2/com/google/guava/guava-parent/33.0.0-jre/guava-parent-33.0.0-jre.pom)
  was fetched over verified HTTPS from both `repo.maven.apache.org` and
  `repo1.maven.org`. Their bytes and computed SHA-256 matched. Both copies also
  matched the [published SHA-1](https://repo.maven.apache.org/maven2/com/google/guava/guava-parent/33.0.0-jre/guava-parent-33.0.0-jre.pom.sha1).
  This release does not publish SHA-256/SHA-512 sidecars for that POM; the recorded
  SHA-256 was computed from those corroborated bytes, not copied from a failed
  verification report. SHA-1 is supporting evidence, not the Gradle checksum used.
- JUnit's [module metadata](https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.10.1/junit-bom-5.10.1.module)
  matched byte-for-byte across both Central endpoints and matched their published
  [SHA-256](https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.10.1/junit-bom-5.10.1.module.sha256)
  and SHA-512 sidecars. The already-recorded JUnit POM was checked independently
  and retained unchanged.
- JUnit BOM [5.10.0 module metadata](https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.10.0/junit-bom-5.10.0.module)
  and [5.9.3 module metadata](https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.9.3/junit-bom-5.9.3.module)
  each matched both Central endpoints, both publishers' SHA-256/SHA-512 sidecars,
  and the cold-cache bytes. Their existing POM checksums were left unchanged.
- Fabric's [Intermediary v2 JAR](https://maven.fabricmc.net/net/fabricmc/intermediary/1.21.1/intermediary-1.21.1-v2.jar)
  and [POM](https://maven.fabricmc.net/net/fabricmc/intermediary/1.21.1/intermediary-1.21.1.pom)
  each matched the official repository's SHA-256 and SHA-512 sidecars.
- The independently verified artifacts also matched the actual bytes downloaded
  into the cold Gradle dependency cache. No published artifact was exempted from
  checksum verification, no existing checksum was replaced, and no new trust
  exception was added. The existing local-output rules and all other verification
  settings remain unchanged.

The validation command explicitly selects strict verification:
`./scripts/gradle.sh --no-daemon --dependency-verification strict clean build`.
For a genuine cold-cache check, use an isolated source export without its
`.gradle/` or `build/` directories and a new empty `GRADLE_USER_HOME`; changing
only the project directory while reusing dependency caches is insufficient.
Retain the regular development cache for routine builds rather than deleting it.

Final Phase 0 validation used another source export and a second, newly created
empty dependency cache after all six entries were added. The strict clean build
passed in 1 minute 43 seconds: 12 tasks executed, all 20 existing Python tests
passed with none skipped, and 15 resource files validated with zero errors or
warnings. Both mod entrypoints compiled and the Java 21 distributable passed its
JAR validation. Its SHA-256 matches the artifact recorded above. The wrapper,
plugin dependencies, mappings, and Minecraft build dependencies were downloaded
into that new cache; no previous dependency cache or project cache was copied.
CI now explicitly passes `--dependency-verification strict` as well. The later
hosted Actions result must be verified against the pushed PR head separately.

The 1.21.1 Fabric toolchain and gameplay source are unchanged. The agreed game
direction is recorded in [GAME_DESIGN.md](GAME_DESIGN.md) as future requirements,
not implemented content. This phase does not accept the Minecraft EULA or merge
the foundation PR.


## Phase 1: Vigil Torch verification

The implementation starts from foundation PR #1's verified merge commit
`72f4750660780a1857ab0fa0c333247c1ac72cf8`, whose required checks passed.
Minecraft 1.21.1, Java 21, Fabric Loader/API, Yarn, Loom, and Gradle pins are
preserved. JUnit is a test-only dependency; it is not bundled in the playable JAR.

Required repeatable checks:

```sh
./scripts/gradle.sh --no-daemon --dependency-verification strict clean build
python3 -m unittest discover -s tests -v
python3 tools/asset_tool.py validate
python3 tools/validate_vigil_resources.py
python3 tools/verify_jar.py build/libs/balance-0.1.0.jar
```

The build's `check` includes Java tests, Python tests, general asset validation,
feature-specific resource validation, and packaged JAR checks. Java tests cover
block-center spherical geometry, exact/vertical boundaries, source-index union
and removal/rebuild bookkeeping, and effect refresh eligibility. These are
pure-logic tests, not evidence that Minecraft placement or chunk events ran.
Python resource tests validate and deliberately break resource relationships.
The JAR check confirms expected code/resources are packaged, not rendered.

Local Phase 1 verification completed on 2026-10-22:

| Check | Result |
| --- | --- |
| Strict clean Gradle build | Passed in 14 seconds; all 15 tasks executed |
| Java JUnit tests | 30 passed: 7 geometry, 9 index/lifecycle contracts, 8 effect policy, 6 configuration; no failures/errors/skips |
| Python suite | 32 passed: 20 existing development-tool tests and 12 resource tests; no failures/skips; also rerun separately |
| General asset validation | 25 resource files, zero errors/warnings |
| Vigil resource validation | All 11 linked resources passed, including recipe-book discovery |
| Distributable validation | Passed: Java 21 entrypoints, eight required feature classes, and linked resources; 30,012-byte JAR |
| Dedicated-server bootstrap | Fabric discovered the mod, executed both common registration log messages, then declined to start a world because the EULA is not accepted; exit 0 |
| EULA | Existing `eula=false` preserved |

The resulting `build/libs/balance-0.1.0.jar` has SHA-256
`5b1bca4c365ef519725286d27344b3cb0cfbf91477326cf075c3d8aecf422099`.
JUnit's test-only published artifacts were independently verified against both
official Maven Central endpoints and their SHA-256/SHA-512 sidecars; see
[checksum provenance](junit-checksums.md). Strict verification settings and all
existing checksums/trust scopes remain intact. Hosted Actions must be checked
against the pushed PR head separately; this local record does not establish a
hosted result.

No EULA acceptance is authorized. A successful declined-EULA bootstrap can
confirm Fabric/common initialization and registration without running a world.
Placement, support removal, zombie movement/effect timing, real chunk/world
reloads, light/particle appearance, and multiple clients require a permitted
Minecraft world and are not established by compilation or pure-logic tests.
See the [manual runtime checklist](vigil-torch.md#runtime-validation-checklist).

## Phase 2: Nightfall verification

Phase 2 starts from Phase 1 PR #2's verified merge commit
`00de0f9670071111d486ed17b9edd520a499121d`, whose two build checks passed.
Minecraft 1.21.1, Java 21, Fabric Loader/API, Yarn, Loom, Gradle, and strict
dependency verification remain the compatibility baseline. Nightfall adds no
unrelated dependency and preserves Vigil-specific source, resources,
configuration, registration behavior, and existing tests. Shared initializer
and localization files may gain Nightfall entries without changing those wards.

Required checks:

```sh
./scripts/gradle.sh --no-daemon --dependency-verification strict clean build
python3 -m unittest discover -s tests -v
python3 tools/asset_tool.py validate
python3 tools/validate_vigil_resources.py
python3 tools/verify_jar.py build/libs/balance-0.1.0.jar
```

Nightfall Java tests exercise supplied-time phase calculations, exact boundaries,
long-safe wrapping, configuration validation, disabled/unsupported states, and
observation/transition policies. Simulated sleeping, frozen time, time jumps, and
independent trackers test pure contracts; they are not actual Minecraft world,
command, sleep, or multiplayer tests. Existing ward tests and Python resource/tool
tests remain required regressions. JAR checks establish packaging, not gameplay.

Local Phase 2 validation completed on 2026-10-23:

| Check | Actual result |
| --- | --- |
| Strict clean Gradle build | Passed in 16 seconds; 15 tasks executed |
| Java tests | 71 passed, no failures/errors/skips: all 30 existing ward/configuration tests plus 41 Nightfall tests |
| Nightfall test coverage | 9 clock, 12 configuration, 6 state, 12 transition, and 2 worldless command-tree/permission tests |
| Complete Python suite | 39 passed, no failures/skips: all 32 existing tests plus 7 packaging regressions; also rerun separately |
| General asset validation | 25 resource files, zero errors/warnings |
| Vigil resource validation | All 11 linked resources passed without changes |
| Distributable validation | Both Java 21 entrypoints, 8 existing ward classes, 7 Nightfall classes, 9 Nightfall localization keys, and Vigil resources verified |
| Server bootstrap | Minecraft 1.21.1/Fabric initialized the mod, including the Nightfall callback-registration log, then refused to start a world at the EULA boundary; exit 0 |
| Preservation review | Vigil-specific source/resources/configuration/tests, existing translation values, dependency pins, build configuration, wrapper, and verification metadata unchanged |

The 49,823-byte `build/libs/balance-0.1.0.jar` has SHA-256
`cbc5899b5f7d36d27f640e9a621836f47d09137d236155e0869f5a82df53b202`.
The two command tests use real Brigadier nodes and permission-only Minecraft
command sources, without executing the status handler or creating a world.
Their generated root logging directory is ignored. Packaging regression fixtures
contain deliberately synthetic class headers; the separate real build/JAR check
provides the actual artifact evidence. No dependency or checksum update was
needed. Hosted Actions must be verified against the pushed PR head separately;
this local record does not establish a hosted result.

No EULA acceptance is authorized. A successful declined-EULA smoke run proves
only Fabric/common initialization to that boundary. Actual world clock updates,
live command execution/feedback, sleeping, pause/resume, lifecycle notifications,
save/reload, dimension transfers, and multiple-client behavior require a permitted
Minecraft world. See the [runtime checklist](nightfall.md#automated-and-runtime-verification).

## Cloud testing workflow

This change is based on the unmerged Phase 2 branch so the focused PR can test
both systems without merging PR #3. It preserves existing gameplay, the pinned
compatibility baseline, and strict dependency verification. See
[cloud testing](cloud-testing.md) for commands and iPhone report review.

The normal build compiles isolated Fabric GameTests; compilation does not prove
discovery or runtime assertions. World-running tests need explicit owner
authorization because the pinned GameTest runner bypasses the ordinary EULA-file
check. No runtime permission is inferred from workflow preparation, and the normal
server's `eula=false` is preserved.

Local verification completed on 2026-10-23:

| Check | Actual result |
| --- | --- |
| Strict clean build | Passed in 16 seconds, 17 tasks executed; GameTest compilation included, no world launched |
| Java tests | 73 passed, no failures/errors/skips: all 71 existing tests plus 2 worldless Minecraft SNBT conversion tests |
| Python tests | 60 passed, no failures/skips: all 39 existing tests plus reporting, consent/launcher, and test-content packaging regressions |
| Prepared GameTests | 8 Vigil scenarios and 1 serialized Nightfall scenario compile; runtime discovery and assertions are **not run** |
| Consent guards | Python launcher refused without consent; direct Gradle runtime launch refused before task execution |
| Reports | HTML, Markdown, and JSON generated; runtime correctly marked blocked, with no graphical/multiplayer/disk-persistence claim |
| Resources and artifact | 25 resource files with zero errors/warnings, all 11 Vigil resources valid, distributable verified without GameTest code/resources |
| Normal server bootstrap | Common initialization succeeded, including Nightfall, then stopped at the declined-EULA boundary; no world ran |
| Preservation | Production source/resources, existing Java tests, pinned dependencies/wrapper/checksums unchanged; `run/server/eula.txt` bytes preserved |

The distributable remains byte-for-byte identical to Phase 2: 49,823 bytes,
SHA-256 `cbc5899b5f7d36d27f640e9a621836f47d09137d236155e0869f5a82df53b202`.
The structure tests use Minecraft's real provider-SNBT conversion to validate the
test fixture, without a server or world. Launcher/report tests use synthetic
processes and XML; those passing tests are not Minecraft execution evidence.
The owner has not authorized EULA acceptance, so no GameTest world, real sleep,
status-effect runtime assertion, disk restart, true chunk unload, real-client
multiplayer, screenshot, or Minecraft video is claimed. Hosted build/report
publication is verified separately against the pushed testing PR head.

Read-only inspection found FFmpeg/ffprobe 7.1.5 and Mesa driver files, but no Xvfb,
configured display, or tested OpenGL context. No Minecraft screenshot/video was
produced. The pinned API has server GameTests; newer client GameTest APIs are not
part of the 1.21.1 baseline. The guide documents a future free client-capture path
without claiming that it has been installed or validated.
