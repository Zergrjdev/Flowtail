#!/usr/bin/env bash
# Compile plugin folders and build the merged resource pack.  Usage: scripts/build-content.sh [plugin-dir ...]
# Default plugin: examples/example-plugin.  Output: build/content/{plugins/*.ftplugin, pack/*}; state in content-state/.
set -euo pipefail
cd "$(dirname "$0")/.."
dirs=("$@"); [ ${#dirs[@]} -gt 0 ] || dirs=(examples/example-plugin)
rm -rf build/content/plugins; mkdir -p build/content/plugins
files=()
for d in "${dirs[@]}"; do
  python3 compiler/flowtailc.py build "$d" -o build/content/plugins
  files+=("build/content/plugins/$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]+'/plugin.json'))['id'])" "$d").ftplugin")
done
extra=()
[ -f build/reports/reports/blocks.json ] && extra+=(--blocks-report build/reports/reports/blocks.json)
python3 compiler/flowtailc.py pack "${files[@]}" --state content-state -o build/content/pack "${extra[@]}"
