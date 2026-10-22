# Vigil Torch: Phase 1 prototype

The Vigil Torch is the first implemented gameplay feature for The Balance Between
Good and Evil. It provides a server-authoritative ward that temporarily slows
ordinary vanilla zombies. Slowness is an engineering prototype for future ward
reactions; the torch does not make enemies retreat or guarantee safety.

## Using the torch

Craft one regular torch, one gold nugget, and one amethyst shard in any arrangement
in a crafting grid. The output is one Vigil Torch (`balance:vigil_torch`). No
Nether ingredient is required. Obtaining an amethyst shard unlocks the recipe in
the recipe book. The item is also in Creative Mode's Functional Blocks category.

The item places a standing torch on a suitable top surface or a wall-mounted
variant (`balance:wall_vigil_torch`) on a suitable side. Vanilla torch support
rules apply. Breaking either variant, or removing its support, drops the Vigil
Torch item according to its block loot table.

Both variants emit vanilla block light at level 14. An original golden/amber
texture and low-count golden particles distinguish the torch. Minecraft's
standard light propagation is separate from the ward: the ward is a sphere and
is not limited by line of sight, walls, or block light levels. Interaction sends
the localized actionbar message “Vigil Ward Active: 14 blocks” with the server's
configured radius. There is no permanent overlay or visible protection sphere.

## Protection behavior

The default radius is **14 blocks**, measured from the torch's block center to
the target entity's position (feet). For a source block at `(x, y, z)`, a target
at `(tx, ty, tz)` is protected when:

```text
(tx - (x + 0.5))² + (ty - (y + 0.5))² + (tz - (z + 0.5))² <= radius²
```

The boundary is included. Vertical distance counts exactly like horizontal
distance. Both standing and wall torches use the block center, not their rendered
flame position. Each torch contributes its own sphere; overlapping protection is
the union of those spheres.

Every 20 server ticks, valid loaded wards search their nearby bounding boxes and
check the spherical distance. Only `EntityType.ZOMBIE` qualifies, including baby
ordinary zombies. Husks, drowned, zombie villagers, zombified piglins, other
hostile mobs, players, and passive mobs are unaffected.

Eligible zombies receive Slowness I for 60 ticks. Refreshes preserve protection
while a zombie remains within at least one active loaded source. A newly entering
zombie may wait up to the next scheduled check. Zombies can still move and attack;
this system does not repel them or make a sanctuary invulnerable.

Overlapping torches apply at most one refresh per zombie per scheduled world
check and do not raise the Slowness level. Stronger unrelated Slowness is never
downgraded. An existing Slowness I with a longer duration, including an infinite
duration, is preserved. The ward never clears potion effects when a target exits.
After leaving every loaded ward or removing the last relevant source, the last
ward-generated effect expires naturally within 60 ticking server ticks (normally
three seconds). Real time varies with server tick rate; unloaded entities resume
Minecraft's normal effect handling when loaded again.

## Server configuration

Configuration belongs to the server installation or the integrated single-player
server. The versioned file is in Fabric Loader's configuration directory:
`config/balance-wards.properties`.

```properties
version=1
radius=14.0
```

Defaults are created only when the file is absent. An existing file is never
rewritten automatically. Invalid values, an unsupported version, or an unreadable
file fail server startup with a clear error instead of silently changing the
configuration. The default radius is 14. Values must be finite, greater than zero,
and at most 64 blocks. Restart the server after changing the file. The limit bounds the
prototype's nearby-entity queries; it is not a balancing decision for future
Hearth Altars or Sanctuary Hearts. The server determines protection and the
interaction message. Clients do not send ward positions or eligibility results.

## Lifecycle and persistence

The torch block and its minimal marker block entity are saved by Minecraft.
There is no separately persisted list of wards or custom per-torch ticker.
Fabric block-entity load/unload events add or remove source coordinates in a
transient index for each `ServerWorld`. Source validity is checked again before
use, and world unload releases its index. A stale unload notification for an old
marker cannot remove its replacement's source at the same position.

Consequently, loading a saved chunk rebuilds its sources from Minecraft's own
block/block-entity data, and unloading it removes those sources from active
protection. A torch in an unloaded chunk does not protect a nearby loaded entity;
wards never force-load chunks. Dimensions have independent indexes and cannot
protect each other. Multiple players share the authoritative world system rather
than running duplicate per-player checks.

These lifecycle statements describe the implemented code paths. Pure index tests
simulate adding, removing, clearing, and rebuilding coordinates; they do not prove
real Minecraft chunk events or save/reload behavior. Those runtime cases remain
on the checklist below until tested in a permitted world.

## Architecture and performance

The implementation keeps reusable concepts small:

- `WardSource`: immutable block position, radius, and spherical containment.
- `WardIndex`: loaded source positions and protection union queries for one world.
- `WardEffectPolicy`: eligibility and preservation rules for the prototype effect.
- The Minecraft server adapter: source lifecycle, scheduled local queries, target
  type checks, and status application.

All Minecraft world access runs on the server thread. One refresh costs
`O(W + sum(C_i))` for `W` active loaded sources and each source's local query
candidates `C_i`. A set deduplicates effect applications across overlapping
sources. Overlap still repeats nearby bounding-box queries; dense torch farms
and large configured radii are the main performance risks. There are no global
world scans, full chunk scans, per-tick global entity scans, force-loaded chunks,
or new network messages. Real settlement-scale performance requires profiling.

A future real ward source can reuse the geometry, index, lifecycle boundary, and
server query concepts. Add an effect/eligibility policy for a concrete feature
rather than encoding all future creature/faction behavior now. Hearth Altars,
Sanctuary Hearts, wards for other allegiances, village protection, creature
resistance, and Nightfall Director integration remain design requirements only.

## Automated verification

Run the strict build and existing validation suite:

```sh
./scripts/gradle.sh --no-daemon --dependency-verification strict clean build
python3 -m unittest discover -s tests -v
python3 tools/asset_tool.py validate
python3 tools/validate_vigil_resources.py
python3 tools/verify_jar.py build/libs/balance-0.1.0.jar
```

Java tests exercise pure geometry, boundaries, overlap/removal/rebuild
bookkeeping, and effect refresh policy. Python tests check assets, resource
relationships, and development tools. Packaging checks require the implementation
and resource files in the distributable. Actual results and counts belong in
[verification](verification.md#phase-1-vigil-torch-verification).

No Minecraft world or graphical client is implied by these checks. A successful
`python3 tools/server_smoke.py` run confirms only startup through common
initialization to the declined-EULA boundary. Neither it nor this implementation
accepts the EULA.

## Runtime validation checklist

After the user has reviewed and explicitly accepted Minecraft's EULA for the
chosen server, use the built distributable with the pinned Minecraft/Fabric/API
versions in a test world. Do not infer completion from the checklist's presence.

1. Craft the recipe, find the Creative item, and place both standing and wall
   variants. Break them directly and by removing support; verify one correct drop.
2. Inspect item/block textures, level-14 lighting, low particle counts, wall
   orientation, and localized interaction range in a rendered client.
3. Spawn an ordinary zombie inside the sphere; confirm Slowness I while it can
   still move and attack. Check exact boundary positions, just outside positions,
   vertical separation, and protection through an obstruction.
4. Move the zombie outside all wards. Confirm refreshes stop and the short ward
   effect expires. Repeat after breaking the last torch.
5. Overlap two wards, remove one, then remove both. Confirm protection persists
   through the remaining source and never stacks into stronger Slowness.
6. Apply stronger Slowness and longer/infinite Slowness I from an unrelated
   source. Confirm the ward preserves them. Confirm unsupported mob types remain
   unaffected.
7. Save and reopen the world, unload and reload source chunks, and replace/remove
   torches around those transitions. Confirm valid wards return and removed wards
   leave no stale protection. Confirm unloaded source chunks are not force-loaded.
8. Repeat in a second dimension and with multiple real clients, including joining
   an already running server. Confirm dimension isolation, server authority, and
   normal status synchronization.
9. Profile a dense group of wards and nearby zombies before making any claim about
   large-settlement capacity. Record server tick rate and configured radius.

## Next small milestone

The next recommended technical step is a server-authoritative Nightfall clock
with tested day/night transitions, a bounded scheduled update, and configurable
state. Establish that timing foundation before adding adaptive encounter
selection, scares, or new creatures. Phase 1 does not implement any Nightfall
behavior, director, territory simulation, or further gameplay system.
