# Baseline benchmarks (upstream Paper, unmodified) — P0-06

STATUS: **IDLE STARTUP BASELINE CAPTURED 2026-11-20.** Three runs were made on the reference CachyOS host using the upstream Paper checkout. This establishes startup/idle memory and tick references for later comparisons. It is not a loaded-player or performance-profile comparison.

## Machine and run conditions

- AMD Ryzen 7 7800X3D, 8 cores / 16 threads; 30.5 GiB RAM; 927 GiB btrfs SSD
- CachyOS, Linux 7.3.4-1-cachyos, x86_64
- OpenJDK 25.0.5.0.3 (Arch); Paper 26.3-DEV-HEAD at `1eda666`
- Fresh generated world per GC run; no players, plugins, or pre-generation; 4 GiB fixed heap; 60-second idle
- Raw logs: [`g1`](raw/20261120-233257-g1.log), [`ZGC`](raw/20261120-233413-zgc.log), [`generational Shenandoah`](raw/20261120-233529-shenandoah.log)

## Results

| GC | Startup to `Done` | RSS after idle | Spark TPS (5s–15m) | Tick p95 / max (last 1m) |
|---|---:|---:|---|---:|
| G1 | 4.314 s | 2795 MiB | 20 / 20 / 20 / 20 / 20 | 0.2 / 4.6 ms |
| ZGC | 4.317 s | 2232 MiB | 20 / 20 / 20 / 20 / 20 | 0.2 / 6.0 ms |
| Generational Shenandoah | 4.493 s | 2923 MiB | 20 / 20 / 20 / 20 / 20 | 0.2 / 3.4 ms |

Spark's 1-minute tick median was 0.1 ms (G1/ZGC) and 0.0 ms (Shenandoah). One-minute CPU displayed 0% for each process at the profiler's displayed precision. Idle tick samples are not representative of gameplay. At zero players, per-player bandwidth and join latency are not applicable. These logs do not contain GC pause totals/max, tick p50/p99 under load, per-thread CPU, or chunk-load latency; do not infer them from startup or idle RSS. The Spark health reports are linked in each raw log.

## Interpretation and next comparison

All three configurations sustained 20 TPS while idle. ZGC's observed idle RSS was 563 MiB below G1; Shenandoah's was 128 MiB above G1. These are single-run observations, not a GC recommendation. The next benchmark should use repeat runs, a fixed seeded/pregenerated world, and the Phase 1 bot harness under a defined workload; collect loaded MSPT percentiles, GC pauses, CPU/thread data, chunk latency, join time, and network bytes before comparing GC or FlowTail changes.

## Reproduce

The original command was `bash scripts/baseline-upstream.sh <g1|zgc|shenandoah> 60 4G`. It copies, but never creates, `run/upstream/eula.txt`; the operator must accept Mojang's EULA explicitly before running it.
