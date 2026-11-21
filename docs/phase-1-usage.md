# Phase 1: run and configure the first patched server

This is a Linux-first development build. Java 25, Git, Python 3.10+, and network access for Paper's build are required.

## Build

```sh
bash scripts/build-flowtail-server.sh
```

The output is `build/FlowTail-server.jar`. The first build downloads upstream build inputs. It does not accept the Minecraft EULA.

To build and launch in one command after you have accepted the Minecraft EULA, use:

```sh
bash scripts/start-flowtail.sh
```

On the first run, if the EULA has not been accepted in `run/flowtail/eula.txt`, the server exits and prints the EULA instructions. Read the EULA, set `eula=true` yourself, then rerun the same command.

## Settings and profile

```sh
scripts/flowtail init
scripts/flowtail check
scripts/flowtail tune --ram-mb 16384 --cpu-threads 8 --upload-mbps 20 --players 30
scripts/flowtail set-profile balanced
```

Edit `config/flowtail.properties` as plain text. `check` prints effective typed values and warns about unknown keys. `worldgen.parity=exact` is fixed. A named profile owns its listed optimisation flags; use `custom` to set those flags individually. Some flags are profile recommendations and have no effect on Paper until their server patches are implemented.

Super-speed asks for an explicit typed confirmation and requires the config toggle:

```sh
scripts/flowtail set-profile super-speed --confirm SUPER-SPEED
scripts/flowtail check
```

## Launch and benchmark

After you have read and accepted the Minecraft EULA, place `eula=true` in `run/flowtail/eula.txt`, then:

```sh
scripts/flowtail launch build/FlowTail-server.jar
```

Startup and server console lines are emitted as JSON with timestamp, level, source, plugin id and content id fields. The launcher records its process id in `run/flowtail/.flowtail.pid`.

Build and install the metrics probe in the isolated test server, then restart it:

```sh
bash scripts/build-bench-plugin.sh
cp build/FlowTailBench.jar run/flowtail/plugins/
```

Run the bots only against an isolated server configured with `online-mode=false`:

```sh
./upstream/Paper/gradlew -p tools/bench run --args="--host 127.0.0.1 --bots 10 --duration-seconds 60 --server-metrics-file run/flowtail/plugins/FlowTailBench/metrics.json"
```

The output includes join latency, first chunk packet latency, bytes per player, movement/chunk counts, MSPT percentiles, per-thread CPU, GC pauses, and process CPU/RSS. Do not expose the offline-mode test server to the internet. Benchmark comparisons require a fixed world, profile, bot path and hardware.
