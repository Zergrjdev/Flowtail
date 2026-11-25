# 04 — Custom content (blocks, items, paintings) & plugin project format

All schemas below are **DRAFT v0** — refine in Phase 2/3, keep `"schema"` version numbers, validate with JSON Schema, and give errors as `file:line — what — how to fix`.
See `examples/example-plugin/` for valid files.

## Plugin project layout (from the user's description)
```
my-plugin/
  plugin.json                 id, name, version, authors, api version, dependencies
  src/                        "regular plugin structure": Java/Kotlin sources + datapack data/ (recipes, loot, tags, advancements, functions)
  blocks/<Name>/              e.g. blocks/Rock/{config.json, texture.png}  (+ model.json / .bbmodel for complex models)
  items/<Name>/               tools, weapons, armour, foods, generic items (config.json + textures/models)
  paintings/<Name>/           painting variants (config.json + texture.png)
  sounds/  lang/  particles/  optional assets
  bedrock/                    OPTIONAL manual overrides for generated Bedrock output (Phase 7)
```
Compiler output: `plugin.jar` (+ `java-pack` contributions; Geyser/Bedrock files in Phase 7). Every block automatically gets an item.
"Custom functions" = code behaviours in `src/` referenced by id from config (`"on_use": "myplugin:fire_burst"`); see `docs/08`.

## Block `config.json` (draft)
| Field | Meaning |
|---|---|
| `id`, `name` | registry id (namespace from plugin.json) and display name |
| `template` | `cube` · `slab` · `stairs` · `trapdoor` · `button` · `pressure_plate` · `carpet` · `plant` (cross) · `shelf` · `custom_model` (valid, P3-12). Templates choose the display model and saved state; every FlowTail block uses the add-only barrier + entity hitbox path (ADR-024/030). See `examples/shapes-plugin`. |
| `textures` | `{all}` or per-face (`top`,`bottom`,`side`,`north`…); `connected` rules for connected textures |
| `model` | optional Java model JSON / Blockbench file for `custom_model` |
| `hitbox` | `{type: template\|box\|boxes, boxes:[[x1,y1,z1,x2,y2,z2]…], collision, selection}` (16-px units) |
| `states` | extra properties: `boolean`, `enum`, `int range`, `direction`; templates add their own (facing/half/shape…) |
| `mining` | `hardness`, `blast_resistance`, `tool` (pickaxe/axe/…/custom), `tier`, `requires_tool` |
| `drops` | `self` · loot table id · custom function |
| `sounds`, `particles` | preset or custom ids (`place`,`break`,`step`,`hit`,`fall`) |
| `light` | 0–15 (may depend on state) |
| `ticking` | `{enabled:false, interval:20}` — opt-in |
| `explosion` | `{breakable, drops}` |
| `item` | `{auto:true, stack_size, base, creative_tab, name override}`; `base` is an explicit vanilla item id for the block item, default `minecraft:paper` |
| `recipes` | list; optional |
| `behaviors` | map of event -> function id (`on_place`,`on_break`,`on_interact`,`on_tick`,`on_neighbor_change`…) |
| `inventory` | **code-only**: declared in the behaviour class through a structured API (CC-04) |

Template notes: FlowTail ports placement orientation, stair corner joining and same-id slab merging. Shape displays currently use the full-cube barrier collision and do not inherit vanilla redstone, inventory, or waterlogging behavior. Those capabilities need FlowTail-owned block logic; no vanilla block is substituted as a carrier. Plant display geometry is cross-shaped, but its current hitbox also remains a full cube.

## Item `config.json` (draft)
`template`: `generic` · `sword` · `axe` · `pickaxe` · `shovel` · `hoe` · `armor` · `food` · `potion` · `custom`.
Fields: `texture`/`model`, `stats` (attack_damage, attack_speed, durability, enchantability, repair_with, mining speed/tier),
`armor` (`slot`, `defense`, `toughness`, `knockback_resistance`, `equipment`: `{type:"layers", textures:{…}}` flat default | `{type:"model3d"}` advanced opt-in using extra packet-only entities),
`food`, `components` (raw component overrides), `behaviors`, `recipes`, `loot`.
Storage: base vanilla item + `custom_data{id}` + prototype expansion (ADR-005). Component names/versions: **VERIFY** (`item_model` ~1.21.2, item model definitions ~1.21.4, `equippable`, `tool`, `weapon`, `consumable`, `attribute_modifiers`, `painting/variant`).

## Painting `config.json` (draft)
`id`, `name`, `author`, `texture`, `width`, `height` (blocks; texture = 16 px per block), `item:{auto}`, `recipes`.
Registered in bootstrap -> registry sync -> pack texture. Vanilla and larger-than-vanilla sizes allowed (client limits: VERIFY).
Cost note: paintings are real entities; use them for placeable wall art, and `item_model` for inventory/hand items (ADR-014).

## Behaviours & functions
Events are typed and structured (block, item, entity, world). Functions are discovered by annotation/registration, referenced by id from JSON. No-code app produces
JSON + generated Java for simple triggers/actions.

## Graceful degradation (hard invariant)
Missing plugin -> custom blocks persist as unknown ids and render as placeholders; items keep `custom_data`, painting entities keep a fallback variant.
Provide `/content missing` report and a migration tool.

## Client gating
Players below the minimum supported client version, or with the pack declined/failed, are handled by `client profile` rules (kick vs fallback).
Custom content needs features absent in very old clients (e.g. 1.8.9 can't do it) — define fallbacks per open question Q4.

## Why items don't need paintings (answer to user's Q8)
- Since Minecraft 1.21.2 any item stack can carry an `item_model` component: a resource id like `{name}_example:ruby`. The resource pack contains `assets/<ns>/items/ruby.json` pointing at a model + texture. The stack stays a cheap vanilla base item (e.g. paper) with `custom_data{id}`; the server adds the components on send (ADR-005). Result: fully retextured / 3D / animated-by-model items, no entity, no placement.
- Painting variants are a **different thing**: a registry of wall art entities (size + texture). A painting *item* in the inventory still needs `item_model` to look unique, so paintings cannot be the item system. They remain a MUST feature for wall-placed decor (CC-11).
- Authoring stays simple: `items/Ruby/{texture.png,config.json}` and `paintings/Sunset/{texture.png,config.json}`; the compiler generates both outputs; a block auto-creates its item.
- Clients older than 1.21.2 (via ViaVersion) see the base item; no custom visuals.
