# 02 — Decisions (ADR log)

Status: **DECIDED** (user) · **DEFAULT** (assistant recommendation the user delegated; changeable via a new entry) · **OPEN** (ask user).
To change a decision: add a new dated entry below it ("Supersedes ADR-0xx"), never delete the old text, and log it in PROGRESS.md.

| ADR | Topic | Status |
|---|---|---|
| 001 | Base = Paper fork | DEFAULT |
| 002 | Java 25 / latest MC only at launch | DECIDED (Java is mandated by MC) |
| 003 | Threading: offload default, regionised experimental later | DEFAULT |
| 004 | Custom blocks: extended registry + carrier translation + packet-only visuals | DEFAULT |
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
