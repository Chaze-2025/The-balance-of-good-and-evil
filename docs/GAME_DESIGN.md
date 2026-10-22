# The Balance Between Good and Evil: design requirements

This document records the agreed direction for a Minecraft Java survival horror
mod built on the existing Minecraft 1.21.1 Fabric foundation. These are design
requirements, not claims of completed systems. Phase 1 implements a limited
[Vigil Torch prototype](vigil-torch.md): a craftable light with a server-side ward
that temporarily slows ordinary vanilla zombies. Its Slowness test is not the
final supernatural behavior. The larger systems, creatures, locations, and story
below remain planned; their creative direction is unchanged.

## Experience and original mythology

The experience must combine Minecraft survival with an original supernatural
mythology. Exploration, uncertainty, investigation, preparation, and the search
for refuge must matter alongside combat. Creatures, locations, structures,
artifacts, supernatural rules, and investigations must be original and internally
consistent; names and detailed lore remain to be designed.

The world must feel vast and largely controlled by Darkness. A smaller region of
Light must remain permanently protected. That region is a dependable refuge and
must not be silently conquered by random events or director escalation. Its
boundaries, generation rules, and relationship to different dimensions remain
open design decisions. Permanence is a requirement for this region, not a promise
that every ward elsewhere provides absolute immunity.

## Settlements, sanctuary, and change

The world must include safe sanctuary and warded villages that give players places
to prepare, recover, and investigate. Clearly communicate a settlement's current
protection and any limits rather than presenting misleading safety guarantees.

Vulnerable villages outside permanent protection must be capable of resisting
Darkness, falling, becoming corrupted, and rebuilding. These outcomes must be
meaningful persistent states with readable causes and opportunities for player
intervention. Exact transition conditions, recovery costs, NPC consequences, and
timelines remain undecided; this document does not prescribe a quest or simulate
those transitions yet.

## Player allegiance

An optional player-allegiance system must support Good, Evil, and neutrality or
Balance. Players must be able to engage with survival and exploration without
being forced into an allegiance. Whether neutrality and Balance are distinct
paths, how allegiance changes, and which consequences apply are future design
decisions. Avoid treating an unchosen allegiance as automatic wrongdoing.

For multiplayer, different player allegiances must coexist coherently. Rules for
shared wards, mixed-allegiance groups, settlement access, and player conflict must
be explicit before those interactions are implemented. No allegiance mechanics
are present in the foundation.

## Night and the adaptive horror director

Supernatural danger must increase dramatically at night. Players should have
reasons to plan journeys, prepare shelter, and choose when to take risks. The
director must respect the permanently protected Light region and the documented
limits of other protection systems.

An adaptive horror director must balance stalking, scares, dangerous encounters,
and quiet periods. It must create tension through pacing, not constant attacks or
unbounded enemy spawning. Quiet periods are intentional parts of the experience.
Its event selection should respect current danger, recent encounters, recovery,
available escape/preparation, nearby players, and regional protection. Exact
signals, budgets, cooldowns, escalation rules, accessibility options, and player
feedback must be designed and tested later rather than assumed here.

The director must not claim omniscient powers that invalidate all player planning.
It must coordinate overlapping multiplayer encounters and avoid multiplying
pressure without limit when players gather. No director implementation or event
content is part of this phase.

## Protection hierarchy

| Future system | Required role | Protection boundary |
| --- | --- | --- |
| Vigil Torches | Player-buildable, localized protection | Small areas useful for preparation and travel; not a universal safe zone |
| Hearth Altars | Larger multiblock structures offering stronger protection | More substantial protected areas with clear structure validity and effect feedback |
| Sanctuary Hearts | Powerful protection for settlements | Settlement-scale refuge with explicit coverage and limits |

Wards must repel or weaken many evil entities without guaranteeing absolute
safety against every entity, threat, or circumstance. Different threats may
interact with wards differently, but exceptions must have understandable rules
and counterplay. A ward must not make a claim of absolute safety and then be
arbitrarily ignored by the director.

The Light region's permanent protection is a separate world-level requirement.
Ordinary player-built wards must not silently acquire that same guarantee.
Crafting, placement rules, protection shapes and ranges, overlapping effects,
upkeep, breakage, and any permitted failure conditions remain open decisions.
No recipes, ranges, multiblock layouts, or supernatural immunities are fixed by
this document.

## Exploration and supernatural content

Future content must include original supernatural creatures, locations,
structures, artifacts, and investigations. Design these together so that clues,
environmental evidence, preparation, and discovery can matter. Avoid reducing
every supernatural encounter to a combat encounter. Detailed creature abilities,
animations, sounds, structure layouts, investigation mechanics, and story content
require separate feature specifications and acceptance checks.

## Multiplayer and performance requirements

- Keep consequential director decisions, allegiance, settlement state, ward
  effects, and entity behavior authoritative on the server. Clients present
  rendering, animation, sound, and other appropriate effects.
- Synchronize relevant state for nearby players, including late joins and
  reconnects. Validate requests on the server rather than trusting client claims.
- Use performance-conscious regional simulation with bounded work and event
  budgets. Avoid scanning the entire world or every ward on every tick.
- Bound updates to active/loaded regions and nearby players. Do not force-load
  distant chunks just to simulate universal horror activity. Define unloaded
  settlement progression explicitly before implementing it.
- Persist meaningful regional, settlement, and player state with versioned data
  and a migration strategy. Do not infer persistence from static Java fields.
- Measure server tick cost, memory, networking, and multiplayer fairness as each
  system is introduced. Set numerical budgets from measured behavior later.

These are constraints for future development, not existing networking or
simulation capabilities. Preserve the pinned Fabric toolchain unless a separate,
evidence-based compatibility decision authorizes a change.

## Smallest practical first gameplay slice

The recommended first slice is a placeable Vigil Torch with one explicitly
bounded server-side protection query and clear player feedback. A practical
prototype effect is a short, locally refreshed Slowness I effect on ordinary
vanilla zombies, which can still attack. Let the effect expire after departure or
torch removal rather than clearing unrelated stronger effects. This is a proposed
test case, not an agreed final target list or tuning rule. Keep it separate
from a world-wide director, allegiance, village simulation, and multiblock systems.
Validate placement, removal, persistence across reload, overlapping torch queries,
effect expiry, preservation of stronger existing effects, and consistent results
for multiple players. Choose an exact effect and target
category before implementation; do not imply it repels creatures until that
behavior has actually been added and tested.

This is a recommendation only. Phase 0 adds documentation and verifies the
foundation; it does not implement the first slice or authorize an automatic PR
merge.
