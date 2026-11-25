# 02 — Decisions (ADR log)

Status: **DECIDED** (user) · **DEFAULT** (assistant recommendation the user delegated; changeable via a new entry) · **OPEN** (ask user).
To change a decision: add a new dated entry below it ("Supersedes ADR-0xx"), never delete the old text, and log it in PROGRESS.md.

| ADR | Topic | Status |
|---|---|---|
| 001 | Base = Paper fork | DEFAULT |
| 002 | Java 25 / latest MC only at launch | DECIDED (Java is mandated by MC) |
| 003 | Threading: offload default, regionised experimental later | DEFAULT |
| 004 | Custom blocks: extended registry + carrier translation + packet-only visuals | DEFAULT (superseded by ADR-024) |
| 005 | Custom items: prototype (id + component patch) expanded on send | DECIDED (hybrid, Q33) |
| 006 | Content project format + compiler outputs | DECIDED (shape) / DEFAULT (details) |
| 007 | App architecture: manager daemon + web UI + desktop shell | DEFAULT |
| 008 | Bedrock via managed Geyser; compile-time generation | DECIDED |
| 009 | World format | DECIDED: own format (design via Q25) |
| 010 | Parity profiles + risk classes | DECIDED (shape) / DEFAULT (table) |
| 011 | GC auto-selection | DEFAULT |
| 012 | Licence | DECIDED (GPL-3) |
| 013 | Plugin languages | DEFAULT |
| 014 | Painting variants vs `item_model` | DEFAULT (explained; confirm Q22) |
| 015 | Paper parity stance | OPEN (Q23) |
| 016 | Pack enforcement penalty limbo | DECIDED |
| 017 | Version strategy (Via for old clients, older server builds later) | DECIDED |
| 024 | Custom block visuals and collision | DECIDED (user, 2026-11-22) |
| 030 | Shaped blocks remain add-only | DECIDED (user direction, 2026-11-22) |

## ADR-001 Base = Paper fork — DEFAULT
User asked for "the best for all". Paper gives vanilla parity (redstone, mob AI, bit-exact worldgen), a mature async chunk system,
Geyser-compatible plugin hosting, and a patch-based workflow suited to fast rebases. From scratch (Minestom-style Java, Rust) would
need years to reach parity. Consequence: GPL-3 obligations (see ADR-012). Revisit only if licence answer forces it.

## ADR-003 Threading — DEFAULT
Phase 1–4: one main tick thread + aggressive offloading (chunk IO/gen, compression, packet encoding, tracker work).
User wants threading "configurable automatically or manually": expose `threading.mode`. Regionised (Folia-style) is a **later experiment**
(Phase 8) because it changes the plugin threading model and is a very large patch set. Reserve the setting now.

## ADR-004 Custom blocks — DEFAULT
Server stores custom blocks as real states in an **extended registry** (unlimited). Chunk persistence uses namespaced string ids in the palette
and **preserves unknown ids**. At network-encode time a translator maps each custom state to a **carrier block state** (+ optional packet-only
entities) per **client profile**; translated sections are cached per profile. Details: `docs/03`, `docs/04`.

## ADR-005 Items — DECIDED (hybrid)
Stack = real vanilla base item + `custom_data{id}` + optional per-stack overrides. Server expands the definition's component set on send and
reverse-maps stacks coming back from the client. Definition changes apply to existing stacks without rewriting saves.

## ADR-007 App — DEFAULT
`manager` (local daemon with HTTP+WebSocket API, wraps the server process, reads/writes configs, runs the compiler) + **web UI** (TypeScript, Monaco
editor) + **desktop shell** (Electron preferred for fewer moving parts and consistent rendering; Tauri acceptable). Same UI can be opened in a
browser for remote hosts. Console, settings editor and plugin maker all use the manager API.

## ADR-011 GC — DEFAULT
Auto-select in the app/tuner: **Generational ZGC** when heap ≥ ~8 GB and ≥ 4 cores (best for "no lag spikes"); **G1** with tuned flags for small heaps / few
cores. Re-benchmark before shipping defaults. (VERIFY JDK 25 GC options in `docs/11`.)

## ADR-012 Licence — OPEN (conflict)
User wants open source and "prefer something other than GPL if possible". A Paper/Spigot-derived server must stay GPL-3-compatible.
Options in `docs/10`. Do not pick a licence until Q2 is answered.

## ADR-013 Plugin languages — DEFAULT
Java + Kotlin on the JVM for code hooks; JSON for definitions; the no-code builder emits Java source/JSON that the compiler builds. Optional embedded
scripting (JS/Lua) is deferred — revisit after Phase 6.

## ADR-014 Painting variants vs item_model — OPEN
User sees painting variants as the core mechanism for "new fully retextured items". Technically, inventory items use `item_model` (no entity cost), while
painting variants are a data-driven registry for **wall-placed art entities**. Plan: implement BOTH (CC-11 stays MUST); default recommendation is `item_model`
for inventory/hand items and painting variants for placeable decor. Confirm with user (Q8).

## Update 2026-11-20 (round 1 answers)
- **ADR-012 DECIDED:** GPL-3, Paper fork. User will not rely on Paper's API; Paper-API compatibility is NOT a goal (native API first). Add `LICENSE` (GPL-3) in P0-04; keep a clear NOTICE for upstream.
- **ADR-009 DECIDED:** own world format, very optimised, tuned by hardware profile; converter to/from vanilla Anvil mandatory; default format is ours (Q12 c). Approach in Q25.
- **ADR-015 OPEN:** user dislikes Paper optimisations that don't keep parity -> Q23 decides how Paper's behaviour patches are handled.
- **ADR-016 DECIDED:** declined required pack -> message "server unplayable" -> penalty limbo (high artificial latency, cheaper than a real player) or kick; configurable. Best effort only.
- **ADR-017 DECIDED:** old clients via ViaVersion/ViaBackwards on the latest server; server builds for older versions are a late Phase 8+ effort. 1.8.9 support is "mimic, unsupported".
- **ADR-007 narrowed:** app is desktop-only (no browser/remote UI initially). Keep manager daemon + UI split internally so a web UI remains possible later.
- **ADR-006 narrowed:** plugin output is a custom wrapper type (jar + pack + bedrock outputs); plain jar export optional.

## Update 2026-11-20 (round 2)
- **Name:** working name FlowTail (placeholder; ADR-018 DECIDED as working name only).
- **ADR-014 DECIDED:** items use `item_model`; blocks use carrier states / block entity + display + interaction; paintings only for wall art.
- **ADR-015 DECIDED:** Paper fork; parity-breaking Paper changes are off-by-default toggles; plugin system is our own native API, not Paper's; many additional optimisations are a core goal (see docs/06).
- **ADR-007 DECIDED:** Electron shell (Qt = fallback option).
- **ADR-009:** survey-then-design approach with compression benchmark (R-18, R-20).

## Update 2026-11-20 (Phase 0 research)
- **ADR-008 impact (R-17):** Bedrock now needs NetherNet (RakNet cut off 27 Oct 2026). Managed Geyser must enable NetherNet; home-hosting needs TCP+UDP or external signaling. See docs/05.
- **ADR-001 impact (R-01):** Paper has no `ver/26.3.x` branch; `main` is 26.3. Our fork pins a commit in versions.toml. Paper plans a config merge in 26.4, so keep our settings layer separate from Paper's config files.
- **ADR-011 refinement (R-10):** ZGC has no non-generational mode in JDK 25 (plain `-XX:+UseZGC` is generational). Generational Shenandoah is a product feature and a real alternative. Benchmark both in P1-04.

## Update 2026-11-21 (Phase 0 closeout)
- **ADR-004 implementation constraint (R-06):** carrier selection must preserve both collision and selection behavior; only explicitly allowlisted states may be considered, and rarity is not evidence that a vanilla state is safe to repurpose. The shape inventory tool remains planned for P3.
- **ADR-011 evidence (P0-06):** single idle runs on the reference host showed 4.314/4.317/4.493 s startup and 2795/2232/2923 MiB RSS for G1/ZGC/generational Shenandoah. This is not workload evidence and does not change the default recommendation.
- **ADR-016 implementation constraint (R-19):** do not deliberately slow keepalives; test a bounded low-chunk limbo or kick-after-decline instead. This is an implementation note, not a change to the user's decision.

## Update 2026-11-21 (Phase 2 data side, Claude)
- **ADR-018 PROPOSED (default, veto welcome):** the content compiler is a Python reference implementation (`compiler/`). The server gets a Java port of the pack-merge step for `pack.merge=auto`, verified with golden vectors (docs/13 §5). End users must not need Python.
- **ADR-019 DECIDED:** Phase 2 content engine is built on Paper API (bootstrap, registry events, data components, PDC, block events) inside `content-engine/`; deep patches wait for Phase 4. Saved data stores namespaced ids, never carrier states.
- **ADR-020 DECIDED:** carrier slots are append-only and retired slots are never reused (`allocations.json`).
- **Carrier MVP:** pool `cube.note_block`; vanilla note blocks disabled in those worlds (docs/13 §3). Revisit in P3-02.

## Update 2026-11-21 (menu instead of creative tab, Claude)
- **ADR-021 DECIDED (user, 2026-11-21):** no creative-inventory tab. Players get custom items from a **FlowTail menu**: a pause-screen button (Minecraft dialog tagged `minecraft:pause_screen_additions`, labelled with a purple-to-purple-pink bold gradient) that opens a dialog with a **Custom Items** button, which opens a paged chest menu of every FlowTail item and block. The Custom Items button is meant for creative mode (setting `menu.custom-items.access=creative|op|everyone`). Everything is configurable through `menu.*` keys in `flowtail.properties` and the settings registry (so the future app edits them too). A server-links entry could not do this: server links only open URLs. Evidence: Paper 26.x dialog API (`DialogBase.Builder#externalTitle` is "used on buttons that open this dialog", `DialogTagKeys.PAUSE_SCREEN_ADDITIONS`, `PlayerCustomClickEvent`). Limits: dialogs are data-driven and fixed at startup, so all `menu.*` keys need a restart; the dialog is identical for every player, so access is enforced on click, not by hiding the button.

## Update 2026-11-22 (carrier isolation, Claude)
- **ADR-022 DECIDED (user, 2026-11-22):** a custom block, and the item that gives it, have **nothing to do with the vanilla block that carries them** unless the definition says so explicitly. Carriers are a client-display detail (ADR-004/019), so nothing of their vanilla behaviour may leak into content. Rules: (1) vanilla right-click behaviour of the carrier (note-block tuning and playing) never reaches a FlowTail block; right-clicks are delivered to plugins as `FlowTailBlockInteractEvent` instead (`dev.flowtail.content.api`). (2) Carrier behaviour is an explicit opt-in: `"carrier_inherit": ["note_sound"]` in the block's `config.json`, mirrored by the `CarrierInheritance` enum; unknown values are compile errors with a fix hint. New carrier behaviours are added as new enum constants, never as implicit defaults. (3) The auto-generated block item is made of an inert, non-block base item (`minecraft:paper`, `ItemPrototypes.DEFAULT_BLOCK_ITEM_BASE`), not of the carrier; `"item": {"base": "minecraft:note_block"}` (any vanilla item id) is the explicit opt-out. FlowTail then places such items itself (`BlockPlacer`: vanilla click rules, a normal `BlockPlaceEvent` so protection plugins still veto, consumption, swing, place sound). Consequence: with a non-block base the client cannot predict the placement, so the block appears one network round-trip after the click; a block that needs prediction sets `item.base` to a block item. Not covered yet: carrier-derived break/step sounds and client mining-speed prediction (P3-04/P3-05), and redstone/punch note sounds.

## Update 2026-11-22 (carrier self-healing and picking, Claude)
- **ADR-023 DEFAULT:** a FlowTail block's carrier state is re-asserted by the server whenever vanilla logic may have changed it (note-block instrument re-derivation from the blocks above and below, which cannot be cancelled through Bukkit): `CarrierGuard` checks notified blocks one tick later, and every chunk load checks its records. A block with an unknown id (plugin removed) has no known carrier state and is not touched. Middle-click ("pick block") on a FlowTail block gives the FlowTail item with vanilla hotbar rules (`PickPlan`), never the carrier's vanilla item. Revisit when P2-06/P3-02 replace the carrier mechanism; a carrier whose state does not depend on neighbours (and is not client-interactable) would make the guard unnecessary.

## Update 2026-11-22 (add-only custom blocks, user)
- **ADR-024 DECIDED:** custom blocks are add-only. The default cube implementation uses an item-display entity for visuals, using the existing `item_model` resource-pack path; a barrier block provides collision/selection and a matching interaction entity provides click handling. The note-block carrier implementation is retired as the default and vanilla note blocks stay enabled. Keep the existing namespaced per-chunk id records as the persistence source of truth; unknown ids remain preserved and get a visible placeholder projection. Player-placed barriers must remain distinguishable from FlowTail collision barriers through saved id records. Do not override vanilla block models or blockstates in generated packs. This supersedes the carrier choices in ADR-004/020 and makes the visual/hitbox work planned as P2-06 part of the P2-05 cube MVP. The optional "fast mode" carrier design was rejected. Costs accepted: entity overhead, custom survival mining work (P3-04), uncertain Geyser projection, different block-lighting appearance, and a substantial compiler/pack-generator/server rewrite. The required ItemDisplay, Interaction, and persistent-data APIs were verified in the checked-out Paper 26.3 source.
- **ADR-025 DEFAULT (2026-11-22, Claude):** projection geometry and the interim survival break for ADR-024. (1) Display context `NONE` at scale 1.002 instead of `FIXED`: `FIXED` applies the vanilla block model's item-frame transform (scale 0.5) and made every cube block half size. (2) The interaction entity sits at the block base centre and is 0.98 wide/high, so the barrier, not the entity, is what the client targets (vanilla placement against a FlowTail block works like against stone; the entity still catches clicks the barrier cannot). Alternative if testing shows clicks are lost: 1.0 or larger, at the cost of blocks no longer being placeable against a FlowTail block by vanilla rules. (3) `ProjectionSpec.LAYOUT` versions the geometry; chunk load rebuilds projections with a different stored state so existing worlds heal. (4) Until P3-04, survival left-click breaks a FlowTail block instantly (no hardness) through a normal `BlockBreakEvent`. (5) The compiler always writes `items/<id>.json` for a block, because the display needs that model even when `item.auto=false`. Revisit (1)-(2) after the first vanilla-client check; (4) is replaced by P3-04.
- **ADR-026 DEFAULT (2026-11-23, Claude):** chunk-load projection check is free for ordinary chunks and never runs inside the load event. (1) `ChunkLoadEvent` only looks at the chunk's saved records (`BlockManager.chunkNeedsCheck`); a chunk with none returns at once, with no entity access. Entry 0026's first version scanned `Chunk#getEntities()` on every loaded chunk, which forces a synchronous entity load per chunk and breaks the Phase 2 rule that custom content costs about nothing when no custom block is around. (2) A chunk with records is queued and checked on the next tick, because Paper loads a chunk's entities separately from its blocks and the load event is the wrong place to spawn or remove entities. (3) `BlockManager.repair` still sweeps orphans on a record-less chunk, but only when its entities are already loaded. Trade-off: a projection entity left behind in a chunk that has no records at all (records lost by something outside FlowTail) is not found until `/content` tooling grows a sweep; every normal path (break, WorldEdit removal, plugin removal) keeps the record, so the normal path is covered.
- **ADR-027 DEFAULT (2026-11-23, Claude):** survival mining of custom blocks is timed by the server (P3-04 first slice). A barrier cannot be dug by the client, so `MiningSessions` computes the break time from the block's `mining` section and the held tool with the vanilla formula (speed / hardness / 30, or /100 when the tool may not harvest; tier speeds wood 2, stone 4, copper 5, iron 6, diamond 8, netherite 9, gold 12; efficiency adds level squared plus one), keeps the break going while the player keeps swinging at the block (PlayerAnimationEvent, 7 idle ticks allowed), and finishes through the normal `BlockBreakEvent` path so protection plugins still veto. `requires_tool` plus a wrong tool or tier means the break drops nothing. `hardness: -1` is unbreakable in survival; **a block with no `mining` section keeps the Phase 2 instant break** (chosen so the golden example output does not change; add a `mining` section to opt in). Mining effect (Entry 0030): the vanilla crack overlay is drawn on a block's model and a barrier has none, so `sendBlockDamage` shows nothing; instead every 4 ticks the block plays its preset's vanilla `hit` sound and a few item-texture particles appear on the face toward the player. Tool wear (Entry 0030): one finished break costs the held tool 1 durability (swords 2; nothing for instant blocks, hands or non-tools) through Paper's `damageItemStack`, so Unbreaking and the break sound apply. Not done yet, on purpose: haste/fatigue/underwater/airborne modifiers, a real crack overlay (would need a model-based overlay entity), custom tool kinds. Assumption to verify in game: a client holding the attack button at a barrier keeps sending arm swings about every 3 ticks; if it does not, mining cancels after 7 ticks and the fix is to key progress on a different signal.


- **ADR-028 DEFAULT (2026-11-24, Claude; slab idea and the 1.5 default are the user's):** shaped blocks (P3-01: slab, stairs, trapdoor, button, pressure plate, carpet, plant) are carried by a REAL vanilla block with the right collision and selection shape, covered by an item display. This replaces the barrier only where the shape is not a cube; cubes are unchanged (ADR-024). Rules: (1) The carrier is chosen by sound preset (`ShapeCarriers`: oak/stone/waxed-copper slabs and stairs, oak trapdoor always, oak/stone button and plate, white carpet, `light[level=0]` for plants). Waxed copper so it never oxidises; nothing flammable-by-design matters because `BlockBurnEvent` is cancelled for FlowTail blocks. (2) "Unbreakable": blocks have no components; the carrier is protected by events. A survival left click is cancelled at `PlayerInteractEvent` (Paper then returns before it starts vanilla digging, read from `ServerPlayerGameMode` at commit 9ee1fac), so the carrier's own hardness never applies and `MiningSessions` times the break. A cancelled client prediction is rolled back by Paper's block-change ack. Creative breaks stay vanilla. (3) Water: carriers that can be waterlogged (slab, stairs, trapdoor, plant) behave as in vanilla and a waterlogged block leaves water when mined; `BlockFromToEvent` is cancelled for buttons, plates and carpets so water never washes them away. (4) A carrier that vanilla wants to destroy by itself (lost support) arrives as Paper's `BlockDestroyEvent`; FlowTail forgets the block, sets `willDrop(false)` and drops its own item. (5) The display is drawn "0.001 bigger" so it hides the vanilla texture without z-fighting: the compiler grows every box of every model by 0.016 px on all sides and keeps the texture coordinates of the un-grown box. A uniform display scale cannot do this for L-shaped stairs (internal faces would still coincide), so shaped blocks are drawn at scale 1.0; cubes keep scale 1.002. (6) Facing is baked into the models (no runtime rotation): 40 stair models, 24 button models, etc. Variant names are a contract between `compiler/ftc/shapes.py` and `BlockShapes.java`, checked by `compiler/tests/golden/shape-variants.json` in both test suites and again at runtime by `/content selftest` against the registry's `variants` list. (7) Vanilla logic is part of the template: trapdoors open and react to redstone, buttons press, plates trigger, stair corners join, slabs merge into double slabs (same block only), all by the carrier's own logic; `ShapeSync` reads the carrier back into the saved state and display after clicks, redstone, physics events and neighbour changes. Interaction with these carriers is therefore NOT denied (`InteractRules` unchanged for other blocks). (8) State is stored per block in the chunk record as `x,y,z=ns:id[k=v,...]` (display-relevant keys only; waterlogging lives on the carrier). Old records without brackets still load, and a build that does not know the bracket suffix keeps the line untouched. The carrier wins when it disagrees with the record. (9) The interaction entity exists only where the client cannot target the carrier (cubes, plants). (10) This supersedes the barrier-slot allocator idea of P3-02 ("sacrificed vanilla block"): there is no allocator and no vanilla block is sacrificed; P3-02 is reduced to the carrier table. Known limits: walking and break sounds are the carrier's (P3-05 will let a preset choose); presets with no vanilla slab fall back to stone; every trapdoor is oak; the shelf template waits for the verified 26.3 shape; sapling growth and plant support-loss events are not built. Amends ADR-027: **a block with no `mining` section (or a section without `hardness`) now has hardness 1.5, pickaxe-preferred, no tool required** (it is no longer instant). Only the placeholder of a block whose plugin is gone still breaks instantly.

- **ADR-029 DEFAULT (2026-11-24, Codex):** P3-01's `shelf` template uses vanilla `oak_shelf` as its carrier, retaining vanilla shelf storage, redstone switching, side chaining, placement and waterlogging. The display model covers the wood body and slot panel; it has 32 state variants (`facing × side_chain × powered`) and follows the carrier when those properties change. Its dimensions were inspected in the locally installed Minecraft 26.3 client assets and independently represented as original project model geometry; no Mojang asset, model, or texture is copied into the repository. The shelf support face is opposite its outward `facing`. This adds the shelf to ADR-028's shaped-carrier system; custom model files remain P3-12.
- **ADR-030 DECIDED (user direction reaffirmed in implementation, 2026-11-24):** ADR-024's add-only rule applies to every custom block template. New FlowTail placements use the FlowTail barrier hitbox, saved namespaced id/state records, item-display visuals and FlowTail interaction entities. Never use a vanilla slab, stair, trapdoor, button, pressure plate, carpet, light, or shelf as the runtime block at a FlowTail position; never alter an unrecorded vanilla block. This supersedes ADR-028/029 wherever they require a real vanilla block as a shape carrier, and supersedes the P3-02 vanilla carrier allocator. Old vanilla carrier states may be converted only at positions already owned by a saved FlowTail id record, preserving the saved id and shape state. The current barrier hitbox is a full cube; custom partial collision and vanilla redstone, inventory, waterlogging, and support-loss behavior require FlowTail-owned implementation and are not provided by the display entity itself. Do not claim those behaviors are complete until implemented and verified.
- **ADR-031 DECIDED (user, 2026-11-25):** investigate and implement FlowTail-owned per-position collision/selection shapes in the server fork for P3-02. Keep the barrier as the actual stored hitbox block and keep the namespaced FlowTail records as persistence source of truth. Do not replace vanilla blocks or introduce a vanilla carrier. Every custom shape registration is owned by the content plugin and removed when the owner is disabled; the plugin restores shapes from saved records after chunk load. Vanilla-client movement/selection must be checked separately because the client still receives the barrier block state; server collision results alone do not satisfy in-game acceptance.
