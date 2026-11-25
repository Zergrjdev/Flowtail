# ROADMAP

**How to use:** work top to bottom. Tick `[x]` only when acceptance criteria are verified. Log every session in `PROGRESS.md`. IDs are stable (never renumber).
OPEN questions block the tasks that say **(needs Qn)** — ask the user in the `1a, 2b` format (see `docs/12_OPEN_QUESTIONS.md`).

---
## Phase 0 — Foundations & research
Goal: a repo the user can build with one command, verified facts, and a baseline to beat.
- [x] P0-01 Repo skeleton per `docs/03` (monorepo dirs, `versions.toml`, `.editorconfig`, CI placeholder, `THIRD_PARTY.md`, `softwareName` in one place)
- [x] P0-02 Linux `scripts/setup` checks/installs JDK 25, git, Gradle wrapper; plain-English errors (automatic Temurin JDK download smoke-tested in an isolated temporary checkout; Windows/macOS out of scope per user)
- [x] P0-03 Research results and sources recorded for R-01…R-16 (`docs/11`); unresolved runtime, empirical, or legal work is explicitly marked partial/deferred; ADR implications recorded
- [x] P0-04 Add GPL-3 `LICENSE` + NOTICE + header policy (Q2 answered: GPL-3)
- [x] P0-05 Build upstream Paper for the target version from source; document exact commands; boot a vanilla-compatible server
- [x] P0-06 Capture upstream idle startup/tick/RSS baseline with the stub harness; record runs and limitations in `docs/benchmarks/baseline.md` (loaded-player comparison remains a later benchmark)
- [x] P0-07 Name placeholder mechanism + rename script **(Q1 optional)**
**Acceptance:** Linux setup prerequisites and private JDK download verified; `bash scripts/start-flowtail.sh` builds and launches after the operator has accepted the EULA; baseline recorded; R-01…R-16 research table filled with sourced conclusions or explicit deferred verification.

## Phase 1 — Fork baseline & settings system
Goal: our own jar, vanilla-compatible, with the settings/profile backbone.
- [x] P1-01 Patch/fork pipeline produces `{name}-server.jar`; rebase script + "new Mojang drop" procedure documented (target ≤ 1–2 weeks)
- [x] P1-02 Typed settings registry + `{name}.properties` generator/loader (per-profile defaults, risk class, restart-required, unknown-key warnings) — see `config/name.properties.example`
- [x] P1-03 Parity profile engine: `vanilla | balanced | performance | super-speed | custom`; super-speed requires confirm **(Q16)**
- [x] P1-04 Hardware tuner library + CLI (`{name}-tune`): RAM/CPU/upload -> JVM flags, GC, threads, distances
- [x] P1-05 Structured logging (level, source, plugin id, content id) + startup banner; console-filterable levels
- [x] P1-06 Bench harness v1 (bot swarm, MSPT percentiles, GC, bandwidth) per `docs/09`
**Acceptance:** boots, joins with vanilla client, settings file generated with comments, profile switch changes behaviour flags, bench runs and prints percentiles.

## Phase 2 — Content engine MVP (the core)
Goal: a vanilla client + generated pack sees a custom block and a custom item.
- [x] P2-01 Bootstrap registration API (blocks, items, paintings) before registry freeze — verified on Paper 26.3; virtual block/item definitions and painting registry entry loaded
- [ ] P2-02 (verified on Paper 26.3 in Entry 0015 except the creative-inventory round-trip; `/content selftest` (Entry 0027) now drives the real creative event server-side, tick after it passes in game) Item prototype system (`custom_data{id}` + expanded components; reverse mapping; creative inventory)
- [ ] P2-03 (`/give` and ruby icon verified in Entry 0015; the "creative tab" clause is replaced by the FlowTail pause-screen menu + Custom Items chest menu per ADR-021, written in Entry 0016 and not yet compiled against Paper or run) Custom item MVP (`item_model` + basic components) + `/give` + FlowTail menu
- [ ] P2-04 (code written in Entry 0015: chunk-PDC records, unknown ids preserved; not compiled against Paper or run) Extended block registry + persistence with **unknown-block preservation**
- [ ] P2-05 (Entry 0026: half-size render fixed, interaction box realigned, self-healing projection, interim survival punch-to-break written; compiled against Paper API source only, not run) Add-only cube blocks: barrier collision/selection, item-display visual, interaction entity, placement/saved ids/unknown-id placeholder, with no vanilla blockstate/model overrides. Survival mining is P3-04.
- [ ] P2-06 Folded into P2-05 by ADR-024; entity lifecycle is required for the cube MVP
- [x] P2-07 (verified on Paper 26.3 with the bot swarm; join-phase fallback because Paper exposes no pause-until-status configuration hook) Java pack generator + HTTP host + pack delivery/hold and decline policies
- [x] P2-08 (done 2026-11-21, Claude; output is `<id>.ftplugin`, plugin.jar embedded when supplied; 13 unit tests) Compiler CLI v0: `blocks/` + `items/` -> `plugin.jar` + pack contribution; schema validation with file:line errors
- [ ] P2-09 (compiles and packs: verified; loading in-game open) `examples/example-plugin` compiles, loads, and works on a vanilla client
- [ ] P2-10 (`/content list [..|retired]|inspect|missing|selftest` — Entry 0027 makes all but `inspect` work from the console and adds `selftest`; not run) Debug tools v0: `/give`, `/content list`, block inspector
**Acceptance:** example plugin: Rock block + Ruby item visible, placeable, breakable, saved/loaded; remove plugin -> world still loads safely.

## Phase 3 — Content engine complete
- [ ] P3-01 (server self-test covers eight model templates and FlowTail-saved display states; client appearance remains unchecked. Add-only hitboxes now use barriers for every template. Full-cube collision remains; native trapdoor/button redstone, shelf inventory, waterlogging and support-loss behavior are not complete.) Shape templates: slab, stairs, trapdoor, shelf, plant/sapling (vanilla-style placement logic)
- [ ] P3-02 (ADR-030 supersedes the vanilla carrier table/allocator design: implement a FlowTail-owned shape/collision runtime; never reserve or substitute a vanilla block. Q26's Shulker research spike found no server-authoritative partial box through the tested Bukkit API; awaiting Q27.) Shape collision library, placement and state logic for add-only templates
- [ ] P3-03 Block states: rotation, arbitrary properties, connected textures
- [ ] P3-04 (first slice in Entry 0029: timed survival mining, tiers, requires_tool; durability and modifiers still open, not run) Mining: hardness, tools, tiers, requires-tool, prediction handling, break animation (Entry 0031: blocks without a mining section default to hardness 1.5)
- [ ] P3-05 Sounds, particles, light, explosion/breakable rules
- [ ] P3-06 Block entity API: opt-in ticking, data, structured inventories API
- [ ] P3-07 Painting variants end-to-end (registry sync, sizes, placement, obtain via recipe/loot)
- [ ] P3-08 Tools/weapons behaviours, armour (stats + flat equipment assets), 3D armour advanced mode **(Q9)**, foods/potions, recipes/loot
- [ ] P3-09 Behaviour/function API + typed events + JSON `behaviors` binding
- [ ] P3-10 Content validation + friendly errors; `/content missing` + migration tool
- [ ] P3-11 Client gating + fallback profile **(Q3, Q4)**
- [ ] P3-12 Complex `custom_model` blocks (Blockbench/Java model) with hitbox types
**Acceptance:** every template in `docs/04` works in-game; save/load; Bedrock-relevant data (shapes, hardness, light, ids) is stored as data.

## Phase 4 — Performance & parity
- [ ] P4-01 R0 optimisations (packet cache, async IO, off-thread encode, tracker index, translated-section cache) with benchmarks
- [ ] P4-02 R1 optimisations (event-driven hoppers, item merge, etc.) with parity tests
- [ ] P4-03 R2/R3 optimisations behind profiles/toggles
- [ ] P4-04 World format + lossless converter both ways **(Q6; ADR-009)**
- [ ] P4-05 Threading modes: `offload` default + `auto`; regionised design spike only **(Q7)**
- [ ] P4-06 Capacity estimator + calibration run
- [ ] P4-07 CI benchmarks + regression gates
- [ ] P4-08 Parity corpus + worldgen golden tests; minimal differential test (worldgen + redstone)
- [ ] P4-09 Per-plugin / per-content-type profiler
**Acceptance:** documented gains vs baseline (p99 MSPT, bandwidth); `vanilla` profile passes parity corpus; worldgen golden tests green in all profiles.

## Phase 5 — Companion app v1
- [ ] P5-01 Manager daemon (process control, config IO, log stream, compiler runner) **(Q10)**
- [ ] P5-02 UI shell + loading screen + server list **(Q19)**
- [ ] P5-03 Setup wizard: hardware menu -> parity slider + super-speed -> server.properties -> build ("jar maker")
- [ ] P5-04 Settings editor (select folder, edit all configs, validation, diff)
- [ ] P5-05 Console with filters (All / Errors / Warnings / Errors+Warnings) + Problems panel
- [ ] P5-06 Connectivity helper **(Q13, Q14)**
- [ ] P5-07 World converter UI; profiler UI
**Acceptance:** a non-programmer creates, tunes and runs a server entirely from the app.

## Phase 6 — No-code plugin maker & code editor
- [ ] P6-01 Content forms for blocks/items/paintings with live preview **(Q12)**
- [ ] P6-02 Hitbox editor, state editor, recipe editor
- [ ] P6-03 Triggers/actions -> generated project/source
- [ ] P6-04 Monaco code editor (Java/Kotlin/JSON schema, API autocompletion, build button, error list)
- [ ] P6-05 Build screen + artifact output **(Q11)**
**Acceptance:** build the example plugin from scratch in the app without touching files.

## Phase 7 — Bedrock / Geyser
- [ ] P7-01 Re-verify Geyser capabilities (R-08/R-09) with a real Bedrock client
- [ ] P7-02 Exporter: Geyser v2 item mappings + block mappings + Bedrock resource/behaviour packs from content definitions
- [ ] P7-03 Managed Geyser (+Floodgate) install/update, publish manifest, auto-copy mappings/packs
- [ ] P7-04 `bedrock/` manual overrides; exact-vs-approximated report
- [ ] P7-05 Complex blocks + paintings via custom-entity projection (experimental) or fallbacks
- [ ] P7-06 Bedrock bench scenario; UDP connectivity tests
**Acceptance:** example plugin visible and usable by a Bedrock client with zero Bedrock-specific code from the author.

## Phase 8 — Hardening & extras
- [ ] P8-01 Full differential testing harness
- [ ] P8-02 Regionised threading experiment (Folia-style evaluation) **(Q7)**
- [ ] P8-03 Multi-version support via Via* / older server bases **(Q3)**
- [ ] P8-04 Bukkit/Paper compat decision and layer
- [ ] P8-05 Resource-pack verification probe if approved **(Q15)**
- [ ] P8-06 Security review (plugin sandboxing, packet limits, pack host abuse)

## Phase 9 — Docs & release
- [ ] P9-01 Full docs (user, plugin author, admin) built into the app
- [ ] P9-02 Release pipeline, installers, update checks, versioning
- [ ] P9-03 Public plugin authoring guide + example gallery
- [ ] P9-04 Licence/legal review pass
