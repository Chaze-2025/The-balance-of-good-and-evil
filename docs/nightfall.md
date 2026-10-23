# Nightfall: Phase 2 foundation

Nightfall supplies the time-related base supernatural danger state for future
horror systems. It reads Minecraft's authoritative server world time, identifies
one of five phases, and exposes a configurable intensity and transition API.
It does not advance its own clock or modify vanilla time, spawning, mob stats,
AI, weather, or existing Vigil Torch protection. No encounters or creatures are
implemented in this phase.

## Daily phases

The daily cycle contains exactly 24,000 ticks. Default intervals include both
ends shown below; internally, each phase starts at its threshold and ends before
the next threshold.

| Phase | Minecraft time of day | Base danger intensity |
| --- | --- | --- |
| Daylight | 0–11,499 | 0.15 |
| Dusk | 11,500–12,999 | 0.40 |
| Night | 13,000–16,999 | 0.75 |
| Deep Night | 17,000–22,499 | 1.00 |
| Dawn | 22,500–23,999 | 0.25 |

The clock uses `Math.floorMod(time, 24000L)` before classifying the supplied long
value. This handles negative values, many elapsed days, and long integer limits
without adding an offset that could overflow. The same valid time and settings
always produce the same result.

“Elevated nighttime danger” is an explicit phase classification: enabled Dusk,
Night, and Deep Night qualify. Daylight and Dawn do not. This flag is independent
of intensity tuning; assigning a lower Night intensity does not rename that
phase. Consumers can inspect both the flag and the actual intensity.

## Server configuration

The server creates `config/balance-nightfall.properties` in Fabric Loader's
configuration directory only when it is absent. Settings are read at server
startup and require a restart after changes.

```properties
version=1
enabled=true
dusk_start=11500
night_start=13000
deep_night_start=17000
dawn_start=22500
daylight_intensity=0.15
dusk_intensity=0.40
night_intensity=0.75
deep_night_intensity=1.00
dawn_intensity=0.25
update_interval_ticks=20
```

Validation requires a supported version, all required keys, and a valid boolean
for `enabled`. Thresholds must satisfy:

```text
0 < dusk_start < night_start < deep_night_start < dawn_start < 24000
```

Daylight starts at zero. These ordered thresholds partition the full daily cycle
without gaps or overlap. Every intensity must be finite and within `[0.0, 1.0]`.
The observation interval must be an integer from 5 through 200 server ticks.
Intensities need not be ordered; each phase can be tuned independently.

Invalid or unreadable configuration fails startup with an error identifying the
problem. Unknown keys are rejected so misspelled settings cannot be ignored.
Existing files, including invalid files, are never silently rewritten.
No external configuration library is required. The Vigil Torch's separate
`balance-wards.properties` file and settings are unchanged.

## Dimensions and disabled state

Only the Overworld supports Nightfall in Phase 2. The Nether, End, and custom
dimensions are inactive even when the global `enabled` setting is true. A global
`enabled=false` makes the Overworld inactive too.

An inactive state retains normalized Minecraft time for diagnostics, has no
Nightfall phase, reports base intensity `0.0`, and reports effective enabled state
`false`. It never pretends the Nether or End has an Overworld-style night.
Dimension-specific behavior can be added later through an explicit support
policy. Inactivity has no effect on Vigil Torches in any dimension.

## Architecture and server API

The implementation separates pure calculations from Minecraft access:

- `NightfallPhase` names the five phases and their nighttime classification.
- `NightfallClock` normalizes supplied time and selects a phase and intensity.
- `NightfallConfig` loads and validates restart-only settings.
- `NightfallState` is a snapshot of normalized time, optional phase, base
  intensity, and effective enabled state.
- `NightfallTracker` compares observations for one world and reports only direct
  phase changes.
- `NightfallService` reads `ServerWorld.getTimeOfDay()`, supplies fresh server
  queries, and connects observation tracking to Fabric lifecycle and tick events.
- `NightfallCommands` registers the read-only administrative status command.

Use these `NightfallService` entry points on the Minecraft server thread;
queries from another thread fail explicitly rather than reading world state
unsafely:

| API | Result |
| --- | --- |
| `state(world)` | Fresh `NightfallState` snapshot |
| `phase(world)` | Current optional phase, empty when inactive |
| `baseIntensity(world)` | Current configured intensity, zero when inactive |
| `isEnabled(world)` | Effective configuration and dimension support |
| `isElevatedNighttimeDanger(world)` | Active Dusk, Night, or Deep Night |
| `hasPhaseChangedSinceLastObservation(world)` | Nonmutating comparison with the observation baseline |
| `PHASE_CHANGED` | Server event carrying world, previous phase, and fresh current state |

Future systems can subscribe to server-authoritative transition notifications.
Current-state queries derive their answer from the supplied world's current time; a cached observation cannot return a stale phase between
scheduled updates. Queries do not advance the tracker, consume transitions, or
emit notifications.

World load establishes the first observation without a false transition. Every
configured observation interval, the server compares the actual phase with the
last observed phase. Repeated observations of the same phase produce no duplicate
notification. A jump from Daylight to Deep Night produces one direct transition;
it does not fabricate Dusk or Night events. Crossing into another phase and back
between two observations is not observable by this polling design. Consumers
that need current state should use the fresh query rather than infer it from
notification history.

Observation state is isolated per server world and discarded on world unload or
server shutdown. Reloading or restarting establishes a fresh baseline from the
saved Minecraft clock. No redundant phase, intensity, or independently advancing
timer is persisted.

## Time changes, sleep, and pauses

Normal progression, `/time set`, `/time add`, and sleeping all affect the same
authoritative input. An immediate query reflects the resulting phase; a scheduled
notification arrives on the next observation if the observed phase changed.
Sleeping directly to daylight therefore skips intermediate phases correctly.

Disabling `doDaylightCycle` freezes natural progression, so the phase and
intensity remain fixed until time changes through another supported Minecraft
mechanism. Reenabling it resumes classification from the advancing world clock.
A paused integrated server does not create artificial Nightfall progression.
The server tick schedule controls when observations happen; it never supplies
the time of day or increases intensity by elapsed host time.

## Administrative diagnostics

Use this read-only server command with permission level 2:

```text
/balance nightfall status
```

It reports the command source's current dimension, normalized Minecraft time of
day, phase or inactive state, base intensity, and effective enabled state. A
console source normally uses the Overworld. To inspect a different dimension,
execute the command with that dimension as its context. Player-facing text uses
localization resources. There is no permanent HUD, repeated player notification,
time mutation, or entity spawning.

## Multiplayer and performance

All decisions and notifications run on the server. There is one observation
tracker per enabled loaded world, shared by players rather than duplicated per
player.
A joining player or a player changing dimensions obtains state from the current
command/world context. No client FPS, client-supplied time, or client danger
calculation is authoritative. This phase needs no custom networking because it
has no persistent client presentation.

A current-state query and one world's scheduled observation each take `O(1)`
work. One observation cycle takes `O(D)` work and stores `O(D)` tracker state for
`D` loaded worlds. It performs no entity searches, chunk scans, ward iteration,
or forced chunk loading. Snapshot creation is limited to explicit queries and
bounded observations. Real server performance and multiplayer behavior still
require runtime verification.

## Automated and runtime verification

Run the existing strict workflow:

```sh
./scripts/gradle.sh --no-daemon --dependency-verification strict clean build
python3 -m unittest discover -s tests -v
python3 tools/asset_tool.py validate
python3 tools/validate_vigil_resources.py
python3 tools/verify_jar.py build/libs/balance-0.1.0.jar
```

Java tests cover phase boundaries, wrapping and long values, configuration,
disabled/unsupported states, independent observation trackers, chronological and
skipped transitions, repeated/frozen observations, and initialization. They
exercise pure contracts by supplying time values. They do not prove that a
Minecraft sleep, status execution, save/reload, or player transfer executed correctly.
Two additional worldless tests use the production Brigadier tree and real
Minecraft command-source permission checks to verify the command path and
level-2 access restriction. They never execute the status handler or create a
server/world.
Existing Vigil Torch tests and resource validation remain part of the build.
Actual counts and execution results belong in
[verification](verification.md#phase-2-nightfall-verification).

A declined-EULA server smoke check, if run successfully, proves startup and
common initialization only. It cannot establish world ticks, live command execution,
sleeping, transitions, or multiplayer behavior. No workflow accepts Minecraft's
EULA on the user's behalf.

After the user explicitly permits a world run, use a test world to check the
following. Status is a current snapshot; inspecting transition counts also needs
a server-side test subscriber or Nightfall DEBUG logging.

1. Inspect status at each boundary and on both sides of it, including 23,999 to
   24,000. Check large time values through supported Minecraft commands.
2. Run `/time set` and `/time add` across several phases in either direction.
   Confirm immediate status accuracy and one direct scheduled transition.
3. Sleep through a night and confirm the resulting daylight state without
   intermediate notifications.
4. Disable `doDaylightCycle`, wait, then reenable it. Confirm frozen state and
   correct resumption. Repeat around a phase boundary and with an integrated
   server pause/resume.
5. Save/reopen the world and restart the server. Confirm state follows saved time
   and initialization produces no false transition.
6. Inspect Overworld, Nether, End, and a custom dimension if available. Confirm
   inactive dimensions and isolated observation state.
7. Check the command as an operator, a player without permission, and console.
   Join an existing server and change dimensions with multiple real clients.
8. Recheck Vigil Torch placement, crafting, ward queries, zombie weakening, and
   chunk reload behavior in multiple dimensions. Nightfall must not change them.
9. Edit a copied configuration, restart, and confirm valid custom settings and
   meaningful failures for invalid settings without file replacement.

## Future encounter and protection integration

Base intensity represents time-related threat potential, not a complete danger
calculation or permission to attack. Future encounters must also consider regional
Darkness, permanently protected Light territory, nearby wards and sanctuaries,
player circumstances, recent encounters, difficulty, and multiplayer conditions.
Deep Night at intensity `1.0` must not override the Light region's intended safety
or a ward's documented rules. Creature-specific reactions and quiet periods
remain future policy decisions.

The next small technical milestone is a bounded encounter eligibility policy
with cooldowns, ward-aware candidate checks, and deterministic tests. It should
produce no encounter in invalid or protected contexts and preserve quiet periods.
A separately scoped original creature can then consume that policy to form the
first complete encounter. Phase 2 implements neither that policy nor a creature,
adaptive director, spawn changes, rituals, faction mechanics, or territory system.
