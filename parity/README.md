# Parity tools

Phase 1 CLI: `python3 parity/flowtail.py` (or `scripts/flowtail`). It uses only Python's standard library.

```sh
scripts/flowtail init
scripts/flowtail check
scripts/flowtail tune --ram-mb 16384 --cpu-threads 8 --upload-mbps 20 --players 30
scripts/flowtail set-profile performance
scripts/flowtail bench 127.0.0.1 --samples 100
```

The typed registry and profile resolver live in `flowtail.py`; unrecognized config keys produce warnings. `worldgen.parity` is locked to `exact`. `super-speed` requires both explicit command confirmation and `super-speed-confirm=true` in the config. The current benchmark measures Minecraft status response latency only; it is not a gameplay bot swarm or MSPT profiler.
