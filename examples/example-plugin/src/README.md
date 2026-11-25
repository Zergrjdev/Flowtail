# River Fishing example plugin

This Phase 2 example demonstrates generated fishing-themed content: a Bait Crate cube block, River Fish and Bait items, a River Fishing Rod icon using the vanilla fishing-rod item as its base, and the Reedbank painting.

The pack currently adds content only. Fishing gameplay such as casting, custom bite timing, loot tables, and awarding these fish is not implemented yet; the content behavior/recipe APIs arrive in later roadmap phases. The rod uses a vanilla fishing rod base, so vanilla fishing still applies.

When compiled with `scripts/build-content.sh`, use `/give @s example:river_fish`, `/give @s example:bait`, `/give @s example:fishing_rod`, or `/give @s example:bait_crate`.

## What each piece does today (plain English)

| Content | What it is | What it does right now |
|---|---|---|
| **Bait Crate** (`example:bait_crate`) | A block, one full cube | Place it, walk into it (solid), left-click to break it (survival: instantly, drops itself), look at it with `/content inspect`. A plugin can also listen for `FlowTailBlockInteractEvent` to react when a player right-clicks it. **It has no gameplay of its own yet** — it is the demo that proves custom blocks work. |
| **Bait** (`example:bait`) | An item (a paper underneath, fish-bait icon) | Nothing beyond existing: giveable, stackable, saved with the player. It does **not** change vanilla fishing yet. |
| **River Fish** (`example:river_fish`) | An item | Same: icon + `/give`; fishing does not award it yet. |
| **River Fishing Rod** (`example:fishing_rod`) | An item on a real fishing-rod base | Works as a vanilla fishing rod with its own icon. |
| **Reedbank** (`example:reedbank`) | A painting variant | Registered with the server as a painting variant. There is no way to obtain or place it in survival yet (roadmap P3-07). |

Making Bait attract fish, Bait Crates hold bait, or River Fish come out of the water needs the behaviour, recipe and loot APIs (roadmap P3-08/P3-09). Until then these are art + registry demos.
