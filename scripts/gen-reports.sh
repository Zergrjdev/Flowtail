#!/usr/bin/env bash
# Runs Mojang's data generator from the cached vanilla server jar and writes blocks.json (every block state, exactly).
# The content compiler uses it for carrier pools (flowtailc pack --blocks-report build/reports/reports/blocks.json).
# Needs: scripts/run-upstream.sh run once (it downloads the vanilla jar into run/upstream/cache/).
set -euo pipefail
cd "$(dirname "$0")/.."
jar=$(ls run/upstream/cache/mojang_*.jar 2>/dev/null | head -1 || true)
[ -n "$jar" ] || { echo "No vanilla jar in run/upstream/cache/. Run scripts/run-upstream.sh once first."; exit 1; }
mkdir -p build/reports
echo "Using $jar"
java -DbundlerMainClass=net.minecraft.data.Main -jar "$jar" --reports --output build/reports
echo "Wrote build/reports/reports/blocks.json"
echo "Pack format declared by this jar:"; python3 scripts/mc-pack-format.py "$jar" || true
