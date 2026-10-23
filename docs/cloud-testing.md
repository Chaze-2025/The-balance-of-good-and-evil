# Cloud testing from an iPhone

Development and evidence collection run in Codex Cloud and GitHub Actions. The
owner needs no local Java installation or desktop computer to review results.
Minecraft 1.21.1, Java 21, Fabric, Yarn, Loom, Gradle, and strict dependency
verification retain their existing versions.

The testing branch is based on the unmerged Phase 2 branch so it can test both
Vigil Torches and Nightfall. Its pull request targets `codex/phase-2-nightfall`
to show only testing changes. It does not merge PR #3 or add gameplay features.

## What each check proves

| Check | Evidence it can provide | What it cannot establish |
| --- | --- | --- |
| Java unit tests | Supplied-time math, transition policies, ward geometry, configuration | Actual world ticks, placement, sleeping, networking, rendering |
| Worldless command tests | Production Brigadier tree and permission checks | A connected player executing the command in a world |
| Compiled Fabric GameTests | Sources use the pinned APIs and test resources package | A test was discovered, started, or passed in Minecraft |
| Headless Fabric GameTest run | Real worlds, blocks, entities, scheduled ticks, commands, and executed assertions | Rendering, real client networking, or unexecuted assertions |
| Mock player | Server interaction logic exercised by that fixture | Real connected clients, authentication, prediction, synchronization |
| Block-entity NBT roundtrip | Serialization/reconstruction of the tested data | Disk save/restart, actual chunk unloading, whole-world persistence |
| Two real clients plus a server | Join, tracking, synchronization, dimension transfer when exercised | Correct visuals unless a client was rendered and inspected |
| Actual client screenshots/video | The particular rendered scene captured | Server correctness or all rendering conditions |

Prepared GameTests are not runtime passes until Minecraft starts, discovers
them, and produces a nonempty current result. Missing or stale results must not
be converted into success. [Verification](verification.md) records actual runs.

## Owner authorization comes before runtime tests

Read [Minecraft's EULA](https://aka.ms/MinecraftEULA) before authorizing a world
run. No permission is inferred from preparing these tests. The existing
`run/server/eula.txt` remains `eula=false`.

This Fabric version's GameTest
[`MainMixin`](https://github.com/FabricMC/fabric-api/blob/0.116.17+1.21.1/fabric-gametest-api-v1/src/main/java/net/fabricmc/fabric/mixin/gametest/server/MainMixin.java)
treats `fabric-api.gametest` as sufficient to pass the normal EULA check. It can
start a world while an EULA file still says false. Keeping that file false alone
is therefore not an authorization guard. The project launcher and Gradle runtime
task require explicit consent before enabling that mode; normal builds and PR
checks compile GameTests without running them.

The owner must explicitly confirm agreement and scope, for example:

> I have read and agree to the Minecraft EULA. I authorize Codex to run isolated
> Fabric 1.21.1 Minecraft test worlds for this repository in Codex Cloud and
> GitHub Actions. Preserve the normal development server's `eula=false`.

The manual Actions input uses this exact acknowledgement token:

```text
I_ACCEPT_MINECRAFT_EULA_FOR_ISOLATED_TESTS
```

The token records the owner's attestation. Codex may submit it only after the
owner explicitly agrees and authorizes these runs; preparing this workflow is
not that agreement. The manual runtime job also restricts dispatch to the repository owner.
Do not launch Minecraft's GameTest main class directly, set its enabling property
to evade the project guard, or use an old result as permission. This authorization
does not expose a public server, authorize paid infrastructure, or supply client
account credentials.

## Repeatable commands and evidence

The regular validation path does not start a world:

```sh
./scripts/gradle.sh --no-daemon --dependency-verification strict clean build
python3 -m unittest discover -s tests -v
python3 tools/asset_tool.py validate
python3 tools/validate_vigil_resources.py
python3 tools/verify_jar.py build/libs/balance-0.1.0.jar
```

`build` also compiles the isolated GameTest source set. Test-only classes and
structures do not belong in the distributable. The test mod exercises production
Vigil Torch and Nightfall code rather than copying their implementation.

Only after explicit owner authorization, the guarded runtime command is:

```sh
python3 tools/run_gametests.py \
  --owner-eula-consent I_ACCEPT_MINECRAFT_EULA_FOR_ISOLATED_TESTS \
  --timeout 300
```

It uses the isolated `build/run/gameTest` directory. The normal development
server's EULA, configuration, and saves must not be replaced. These are disposable
test worlds, not the owner's gameplay world. Generated evidence is under
`build/reports/gametest/`:

- `run.json`: launch outcome and the scope actually observed.
- `results.xml`: Minecraft's GameTest results, when the server produced them.
- `server.log`: launch, discovery, and server output.

`python3 tools/testing_report.py` combines evidence under
`build/reports/cloud-testing/` as `report.html`, `summary.md`, and `summary.json`.
It distinguishes unit results, compiled tests, runtime results, blocked/unexecuted
checks, and available visual evidence. Generate reports after the checks to
include. A clean build removes generated evidence; workflow artifacts retain
the evidence from an individual run.

## Reviewing GitHub Actions on an iPhone

1. Open the testing PR's **Checks**, or the repository's **Actions** tab. Select
   the workflow run for the intended branch and commit.
2. Read the job summary first. This is the primary mobile view and states
   whether runtime tests ran, were blocked, failed, or passed. A passing build
   does not turn an unrun runtime job into a pass.
3. Open individual step logs for failures. In **Artifacts**, download
   `cloud-test-reports` and uncompress the ZIP with the iPhone Files app.
4. The artifact contains standalone HTML and machine-readable summaries/results.
   GitHub does not render artifact HTML as a hosted website. If Files cannot
   preview the HTML, use the Actions summary; raw XML remains available.
5. `balance-mod` contains the built mod and sources. PNG screenshots and MP4
   recordings can also be downloaded and previewed in Files when a run actually
   produces them. The workflow does not invent visuals when no client ran.

The existing **Build foundation** workflow has an optional manual input named
`minecraft_eula_acknowledgement`. Leave it blank for compilation/unit checks.
Only a nonempty acknowledgement selects its separate owner-guarded runtime job;
an incorrect token or a non-owner is rejected before Minecraft starts.

GitHub's **Run workflow** UI may show only inputs from the default branch until
the updated workflow is merged. Before that, the owner can authorize Codex to
dispatch the existing `build.yml` workflow at `codex/cloud-runtime-testing` with
the acknowledgement, then review its Actions summary and artifacts on an iPhone.
Codex must receive explicit EULA authorization before submitting that input.
This does not require or authorize merging PR #3. Once the field is available in
the UI, select the intended branch/ref and enter the acknowledgement above.
Runtime execution never becomes automatic on push or PR events.

Artifacts/logs follow GitHub's retention limits. Download important evidence from
the specific run. Summaries and artifacts need no separate website, paid service,
or external infrastructure.

## Runtime coverage and remaining gaps

The prepared suite declares eight Vigil Torch tests and one serialized Nightfall
scenario. This inventory describes intended assertions, not completed runs:

| Prepared tests | Intended server-world assertions |
| --- | --- |
| Vigil Torch | Standing/wall item placement, support loss and drops, radius and mob eligibility, effect refresh/expiry, removal, overlaps, stronger/longer/infinite effect preservation, marker NBT/replacement, and an absent-chunk no-force-load query |
| Nightfall | Five phase boundaries, actual time commands and diagnostic feedback/permissions, direct transition events, frozen/resumed daylight cycle, wrapping, an in-memory player's vanilla bed/sleep path, and inactive Nether/End states |

GameTests can exercise actual item/block interaction, zombie status effects over
ticks, and Nightfall queries/events against a real `ServerWorld`. Time-changing
cases must be serialized because one test server shares time and gamerules.
Fixtures must restore changed state and isolate mobs/blocks from other tests.
The current compiled test inventory and eventual XML results are separate from
claims that all these scenarios passed.

Sleeping must exercise the server's real sleep-management path. Merely assigning
the next day's time verifies a time jump, not sleeping. A mock-player limitation
must be reported rather than replaced with a sleep claim.

An NBT roundtrip is not a world-restart test. Stronger persistence validation
needs a two-process fixture: save a known torch world, stop cleanly, reopen that
same save, and check that valid wards return and removed wards do not. A real
chunk-unload test must release tickets outside permanently loaded spawn/GameTest
areas, verify the chunk actually unloads, then reload it. GameTest structures can
retain chunks; manually invoking a callback or reconstructing a block entity
does not prove unloading. These cases need the same owner authorization and
must be reported individually when an actual harness performs them.

## Multiplayer investigation

Multiple mocked players do not equal two clients. Full multiplayer validation
needs a dedicated server and two independent Java clients, then assertions for
join/late join, block tracking, status effects, dimension transfer, reconnect,
and world reload.

That harness must decide how licensed accounts and authentication are supplied.
An isolated loopback-only development fixture may deliberately use test profiles,
but does not validate online authentication and must not be exposed publicly.
Never place account tokens in source, reports, or logs. This work adds no account
secret, public port, or real-client automation and makes no multiplayer-runtime
claim.

## Screenshots and video investigation

Pinned Fabric API `0.116.17+1.21.1` includes the server
[`fabric-gametest-api-v1`](https://github.com/FabricMC/fabric-api/tree/0.116.17+1.21.1/fabric-gametest-api-v1)
module. Its published dependency list and tagged source contain no
`fabric-client-gametest-api-v1`. Newer branches, including
[`1.21.5`](https://github.com/FabricMC/fabric-api/tree/1.21.5/fabric-client-gametest-api-v1),
have a separate client testing module; do not assume those APIs exist here or
upgrade this project's baseline to add them.

On the inspected cloud machine, FFmpeg/ffprobe 7.1.5 execute and FFmpeg advertises
an H.264 encoder. Mesa software-rendering driver files exist. `Xvfb`, `xvfb-run`,
`glxinfo`, `vulkaninfo`, and `xdotool` are absent, and `DISPLAY` is unset. No OpenGL
context, graphical Minecraft window, screenshot, or Minecraft video was tested.
Driver files or a listed encoder do not establish a working rendered client.

A future free workflow can investigate Xvfb plus Mesa software rendering, launch
the matching Minecraft client, position a camera, capture real frames, and encode
a short recording with FFmpeg. It needs validated client launch, licensed-account
or profile decisions, controlled input, render-readiness checks, adequate RAM/CPU,
and owner runtime authorization. Software rendering can be slow on hosted
runners. First prove one rendered frame; then add scene checks and recording.
Use ffprobe to verify recordings and upload only files produced by that run.

Screenshots can validate torch appearance; video can show interactions and effect
timing. Both still need server assertions. This work installs no heavy graphical
stack or paid GPU service and does not claim visual testing is operational.
