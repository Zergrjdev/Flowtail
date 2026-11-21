# 06 — Performance & parity

## Priorities (user): 1) no lag spikes (tail latency) 2) lowest bandwidth 3) average tick time 4) memory.
Measure **MSPT p50/p95/p99/max**, GC pause max, bytes/sec per player, RSS. Optimise p99 and bandwidth first.

## Risk classes
- **R0** behaviour-identical (always on, even in `vanilla`).
- **R1** theoretically identical, subtle ordering risk (on from `balanced`; off in `vanilla`).
- **R2** small visible deviations (`performance`; a few "balanced-on" ones where noted).
- **R3** large deviations (`super-speed` only, requires `super-speed-confirm=true`).

## Profiles (DEFAULT table — tune with benchmarks)
| Setting key | Risk | vanilla | balanced | performance | super-speed |
|---|---|---|---|---|---|
| `opt.chunk.packet-cache` | R0 | on | on | on | on |
| `opt.io.async` (chunk IO/compress/batched writes/spread autosave) | R0 | on | on | on | on |
| `opt.net.packet-batching` + off-thread encode/compress | R0 | on | on | on | on |
| `opt.tracker.spatial-index` (+ dirty-flag diffs) | R0 | on | on | on | on |
| `content.virtual-entities.packet-only` | R0 | on | on | on | on |
| `opt.hopper.event-driven` | R1 | off | on | on | on |
| `opt.item-merge.radius` | R1 | vanilla | vanilla | larger | larger |
| `opt.redstone.algorithm` | R1/R2 | vanilla | fast | fast | fast |
| `opt.mob.cramming` | R1/R2 | vanilla | vanilla | optimised | simplified |
| `opt.mob.activation-ranges` | R2 | off | conservative | on | aggressive |
| `opt.mob.ai-throttle` | R2 | off | off | on | aggressive |
| `opt.view-distance.dynamic` (only when overloaded) | R2 | off | on | on | aggressive |
| `opt.light.deferred` | R2 | off | off | on | on |
| `opt.random-tick.budget` | R3 | off | off | off | on |
| `opt.entity.far-tick-skip` | R3 | off | off | off | on |
| `opt.physics.simplified` (fluids/falling blocks) | R3 | off | off | off | on |
Plus `parity-profile=custom` where each key is set manually. `vanilla` = "COMPLETE parity" (R0 only). Worldgen results never change (PF-05).

## Optimisation backlog (each: setting + test + benchmark + risk class)
**Network / bandwidth:** chunk packet cache shared across players · off-thread packet encode + native compression (VERIFY libs) · bundle/flush consolidation · per-player tracker with dirty flags and spatial index · send-rate limiting for chunk bursts · skip redundant entity/metadata updates · custom-content translated-section cache per client profile.
**Tick tails / GC:** allocation-free hot paths (profile-driven) · primitive collections · interned component maps · spread autosave/saving/lighting across ticks · tick-time budgeting with deferred work queues · avoid large pauses (see GC).
**Chunks / world:** async chunk gen + IO · batched region writes · optional faster world format (ADR-009) · structure/noise caching **without altering results**.
**Entities:** spatial grids · cheaper collision · goal-selector evaluation cost · activation ranges · pathfinding offload (results must be applied deterministically at tick boundary).
**Blocks:** event-driven hoppers · sparse random-tick/block-entity tick lists · custom blocks non-ticking by default · per-section index of custom blocks.
**Startup:** JDK 25 AOT/CDS cache features (VERIFY JEP availability/flags).
**Custom content cost:** cap and meter virtual entities per player view; batch spawn with the chunk packet bundle; view-range scaling for display entities.

## GC (ADR-011, VERIFY)
Default: Generational ZGC (heap ≥ ~8 GB, ≥ 4 cores) for minimal pauses; G1 + tuned flags otherwise; Shenandoah optional. Tuner writes JVM flags into the start script. Benchmark all three on the bench world before shipping.

## Hardware auto-tune (app "RAM/CPU" menu, before settings)
Inputs: RAM, CPU threads, expected max players, upload Mbps (home hosting), Bedrock on/off. Outputs: heap size, GC + flags, worker threads, view/simulation distance, entity caps,
`threading.mode`, profile suggestion, **recommended max players** (doc 09). Manual override always possible.

## World format (ADR-009, OPEN)
User wants a faster default format + lossless converter both ways. Research prior art (Linear-style region formats, zstd/LZ4 region files) for compression ratio, random-access cost, crash safety, tool compatibility
(WorldEdit offline, MCA tools), licence. Requirements: lossless, atomic writes, versioned header, converter tested on large worlds, vanilla format always selectable.

## Threading (ADR-003)
Default `offload`. `auto` picks by core count. `regionised` = experimental later (Phase 8) after evaluating Folia's approach (plugin model impact).

## Additional optimisation backlog (user wants "way more than Paper")
Each item: assign a risk class and profile; benchmark + parity test before enabling.
- Chunk pregeneration tool (built-in, resumable, hardware-aware, bit-identical worldgen)
- XP orb clumping/merging, item-entity merging (vanilla-radius default, larger in performance/super-speed)
- Event-driven hoppers, cheaper entity collision, spatial entity index, mob AI/goal throttling for far mobs
- Tracker dirty flags, packet caching/bundling, off-thread encoding and compression
- Async chunk IO/lighting/worldgen, lazy autosave spreading, own world format (ADR-009)
- Redstone wire algorithm options, sparse random ticks, cheaper block updates
- Per-content-type cost accounting for custom blocks/items
