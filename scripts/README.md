# Helper scripts

Linux-first helper commands for setup, upstream and FlowTail builds, server launch, tuning, and benchmarks live here. They should print plain-English errors and the next action. Windows and macOS support are out of scope for now.

## Make a small source ZIP

From the project folder, run:

```sh
./scripts/package-source.py
```

This creates `FlowTail-source.zip` in the project folder. It includes the source, docs, and example assets while skipping downloaded libraries, upstream checkouts, server worlds, caches, compiled JARs, and build output. It uses Python's standard library; no extra ZIP program is needed.

## Content scripts (Phase 2)
- `scripts/build-content.sh [plugin-dir ...]` compiles plugins and builds the merged resource pack into `build/content/`.
- `scripts/gen-reports.sh` runs Mojang's data generator from the cached server jar to get exact block states (`build/reports/reports/blocks.json`).
- `scripts/mc-pack-format.py <server.jar>` prints the pack format the jar declares.
