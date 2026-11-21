# 09 — Testing, benchmarks, capacity estimator

## Test layers
1. **Unit/integration:** registries, translators, settings/profile engine, compiler (schema validation + golden outputs), pack generator.
2. **Worldgen golden tests (hard invariant):** fixed seeds -> hash chunk data across many coordinates/dimensions; compare against vanilla reference; must pass in every profile.
3. **Parity corpus:** redstone contraptions, mob farms, hoppers, item flow, fluid behaviour, piston/slime machines — run under `vanilla` (must match exactly) and `balanced` (must function). Record expected outcomes.
4. **Content tests:** place/break/drop/mining speed/explosions for each template; save/load; remove-plugin graceful degradation; client-profile translation snapshots.
5. **Differential testing (LATER, user: "nice, later"):** run vanilla and our server on the same seed/inputs and diff chunk hashes, entity positions, block updates. Assistant recommends a *minimal* version by Phase 4 for worldgen + redstone.
6. **Soak tests:** 24h runs with bots; memory growth; GC pause maxima.

## Benchmark harness (`tools/bench`)
- Protocol-level **bot swarm** (movement, chunk exploration, block interaction, chat), scenarios: idle, exploration, farms, redstone, custom-content-heavy, Bedrock-via-Geyser (Phase 7).
- Metrics: MSPT p50/p95/p99/max, GC pause max/total, bytes/sec per player (in/out), CPU per thread, RSS, chunk load latency, join time.
- **CI (user: automated):** run on every PR with a fixed hardware class; store results; fail on regressions beyond a threshold (start at 5% p95, 10% p99); publish trend graphs.
- Always compare to the **upstream baseline** captured in Phase 0.

## Capacity estimator (shown in the app as "recommended max players")
1. **Calibration run** (optional, ~2–5 min at setup): spawn increasing numbers of bots; find the largest N where p95 MSPT < 40 ms and p99 < 50 ms for the chosen profile and distances.
2. **Bandwidth bound:** `players × measured_kbps_per_player` vs user's upload Mbps (home hosting is often limited here) — report whichever bound is lower and say which.
3. **Memory bound:** heap needed per player at chosen view distance.
4. **Safety factor:** report 80% of the measured limit; show a range and the bottleneck (CPU / RAM / bandwidth).
5. **No calibration?** Use a lookup model fitted from CI benchmark results by hardware class; mark confidence "estimated".

## Definition of done for any optimisation
Setting + risk class + test + before/after benchmark numbers recorded in PROGRESS.md + parity check for R0/R1.
