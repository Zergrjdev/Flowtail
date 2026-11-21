# FlowTail benchmark bots

The Java bot swarm uses MCProtocolLib for the target protocol. It connects offline-mode accounts, remains logged in, follows server teleports, and sends slow movement to exercise nearby chunk delivery (or stays idle).

The target must be an isolated server configured with `online-mode=false`. Bot names are unauthenticated and the server treats them as real connections. Do not use this tool on a public server or expose an offline-mode test server to the internet.

Build the local metrics probe and install it in the isolated server:

```sh
bash scripts/build-bench-plugin.sh
cp build/FlowTailBench.jar run/flowtail/plugins/
```

Restart that server. The probe writes rolling tick, per-thread CPU, process CPU/RSS, and GC measurements to `run/flowtail/plugins/FlowTailBench/metrics.json`. The launcher also records its PID in `run/flowtail/.flowtail.pid` while it runs.

Run the bot swarm and attach both metric sources:

```sh
./upstream/Paper/gradlew -p tools/bench run --args="--host 127.0.0.1 --port 25565 --bots 10 --duration-seconds 60 --server-metrics-file run/flowtail/plugins/FlowTailBench/metrics.json"
```

Results include login latency, first chunk packet latency, chunk packet count, client bytes sent/received per player, movement packet count, server MSPT p50/p95/p99/max, per-thread CPU sampling, GC count/total/max pause, and process CPU/RSS. An optional `--server-pid` adds a host-side process sample on Linux. These measurements need a fixed world, profile, bot scenario, and hardware before comparing builds. `--idle` disables movement. MCProtocolLib's protocol version is pinned in `build.gradle.kts`; update it together with `versions.toml` when changing the target Minecraft release.
