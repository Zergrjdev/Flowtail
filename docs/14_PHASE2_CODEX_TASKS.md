# 14 — Phase 2: server-side tasks for Codex

Read first: `AGENTS.md`, `docs/13_PHASE2_DESIGN.md`, `docs/04_CUSTOM_CONTENT.md`. The data side is done (compiler, pack generator). Everything below must be **compiled and run against Paper 26.3 on Java 25**, so it is yours. Work top to bottom; tick roadmap items only when the check passes; log each session in `PROGRESS.md`.

**Do not change** the invariants in `docs/13 §2` or the file formats without logging an ADR. If Paper's real API differs from `docs/13 §4` (it was read from source, never compiled), fix the doc and say so in the log.

## Step 0 — data checks completed
Mojang's 26.3 jar was inspected; pack format `[97, 1]` and protocol `777` are recorded in `versions.toml`/research docs. Note-block state reports were only for the retired carrier experiment. Current pack generation uses custom namespace item models and does not override vanilla block states, models, or textures.

## Tasks (IDs from ROADMAP.md)
* **P2-01 bootstrap + registry load.** New module `content-engine/` (Gradle, Java 25, depends on Paper API) shipped inside the FlowTail jar. In `PluginBootstrap#bootstrap`, read `content-state/registry.json`; fail loudly (structured log, plugin id + content id) on bad data; late registration is an error. Done when: server logs `N blocks, N items, N paintings registered` from the example plugin.
* **P2-07 (server part).** (a) Java port of `compiler/ftc/pack.py` — add `compiler/tests/golden/` vectors first, then match them. (b) `com.sun.net.httpserver` host on `pack.host.port`, URL from `pack.host.public-url` (auto = detect LAN IP and print it), SHA-1 check at startup. (c) Send the pack in the configuration phase if Paper allows (`AsyncPlayerConnectionConfigureEvent`) and hold the player until `SUCCESSFULLY_LOADED`; otherwise fall back to sending on join and freezing the player. (d) `pack.decline-action`: `kick` or `limbo-lag` (message "this server is unplayable without the pack", then a cheap limbo with artificial latency that costs less than a normal player; see docs/12 Q15 and ADR-016). Done when: vanilla client joins, gets the pack, decline paths work, `pack.required=false` skips the hold.
* **P2-02 + P2-03 items.** `ItemPrototypes`: id `example:river_fish` → base item + components (`item_model`, `item_name`, `max_stack_size`, later stats). 2A: write full components into the stack at creation and store `flowtail:id` in its PDC. Keep the `expand(stack)` interface so 2B (id only, expand on send via a packet hook, ADR-005) can replace it. `/give @s example:river_fish [count]` with tab completion, and a creative tab/search entry if the API allows. Done when: River Fish shows its icon, survives drop/pickup/chest/relog, and a stack the client sends back (creative) is recognised.
* **P2-04 + P2-05 blocks (ADR-024).** Per-chunk map `x,y,z → namespaced id` in chunk PDC remains the source of truth. Place uses a barrier hitbox + saved id + persistent item-display entity and interaction entity; the display item uses the same `item_model` as the block item. Remove all vanilla blockstate/model overrides. On chunk load, migrate legacy note-block carriers only at saved FlowTail positions; preserve unknown ids and show placeholders. Keep player-placed barriers distinct by requiring saved IDs. Vanilla note blocks are always enabled. Survival mining is P3-04. Verify chunk restart/unknown-plugin behavior and use a vanilla client for the Bait Crate visual.
* **P2-06 visuals.** Folded into P2-05 by ADR-024; the cube MVP owns the persistent entity projection lifecycle. Packet-only reserved entity IDs are no longer the design.
* **P2-09 example plugin works.** `examples/example-plugin` (Bait Crate cube + River Fish/Bait/Fishing Rod items + Reedbank painting data) loads end to end. All fishing themed item and block visuals build without warnings. Done when: acceptance in docs/13 §6 passes.
* **P2-10 debug tools v0.** `/content list` (plugins, blocks, items, paintings, retired slots), `/content missing`, `/content inspect` (look at a block → id, carrier state, chunk record), `/give`. Done when each prints plain-English output and appears in structured logs with source `content-engine`.

## Rules for this phase
* Keep Paper-API-level code in `content-engine/`; only add a Paper patch (`server/patches/`) when the API truly cannot do it, and say why in the log.
* Every error from plugin content names plugin id + content id + file (structured log, `docs/13`).
* Add tests where they can run without a server (golden vectors, registry parsing); run `python3 -m unittest discover -s compiler/tests` before every commit.
* Benchmarks: run `tools/bench` before/after with the example plugin loaded and an empty world; custom content must cost ~0 when no custom block is in view.
* Do not start Phase 3 items (shapes, states, mining, sounds, Bedrock).

## Paste this to Codex
> Read AGENTS.md, then docs/13_PHASE2_DESIGN.md and docs/14_PHASE2_CODEX_TASKS.md. Do "Step 0" first and log the results in PROGRESS.md. Then implement Phase 2 server-side tasks in the order listed, verifying each on the real server with a vanilla client or the bot swarm, ticking ROADMAP.md only when verified. The compiler and pack generator in compiler/ already work and have tests; do not rewrite them, but fix them if Step 0 shows the real server data differs.
