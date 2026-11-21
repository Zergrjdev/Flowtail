#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
api=$(find upstream/Paper/paper-api/build/libs -maxdepth 1 -type f -name 'paper-api-*.jar' -printf '%T@ %p\n' | sort -nr | head -1 | cut -d' ' -f2-)
[ -n "$api" ] || { echo "Build the Paper base first: bash scripts/build-flowtail-server.sh"; exit 1; }
gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
deps=$(find "$gradle_home/caches/modules-2/files-2.1" -type f -name '*.jar' -printf '%p:' 2>/dev/null | tr -d '\n')
[ -n "$deps" ] || { echo "Gradle dependency cache is empty. Build Paper first, then retry."; exit 1; }
out=tools/bench/build/server-plugin
rm -rf "$out"; mkdir -p "$out/classes"
javac --release 25 -cp "$api:$deps" -d "$out/classes" tools/bench/plugin/src/main/java/dev/flowtail/benchplugin/BenchMetricsPlugin.java
cp tools/bench/plugin/src/main/resources/plugin.yml "$out/classes/plugin.yml"
jar --create --file build/FlowTailBench.jar -C "$out/classes" .
echo "Built build/FlowTailBench.jar. Copy it to the isolated test server's plugins/ folder, then restart that server."
