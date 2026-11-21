#!/usr/bin/env bash
# P0-06 (stub harness): boots the UPSTREAM Paper jar with a chosen GC, idles, records startup time,
# RSS memory and the bundled Spark profiler's `tps` and `health` output, then stops cleanly.
# No bot players yet (tools/bench arrives in P1-06), so this measures startup + idle + memory only.
# Usage: bash scripts/baseline-upstream.sh [g1|zgc|shenandoah] [idle_seconds=60] [heap=4G]
# Needs run/upstream/eula.txt (you accepted the EULA there earlier); it is copied, never created.
set -euo pipefail
cd "$(dirname "$0")/.."
gc="${1:-g1}"; idle="${2:-60}"; heap="${3:-4G}"
jar=$(ls upstream/Paper/paper-server/build/libs/*paperclip*.jar 2>/dev/null | head -1 || true)
[ -n "$jar" ] || { echo "No jar. Run scripts/build-upstream-paper.sh first."; exit 1; }
grep -qs 'eula=true' run/upstream/eula.txt || { echo "Accept the EULA first (see scripts/run-upstream.sh)."; exit 1; }
case "$gc" in
  g1) flags="-XX:+UseG1GC";;
  zgc) flags="-XX:+UseZGC";;
  shenandoah) flags="-XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational";;
  *) echo "GC must be g1, zgc or shenandoah"; exit 1;;
esac
stamp=$(date +%Y%m%d-%H%M%S); dir="run/baseline-$gc"; out="docs/benchmarks/raw"
mkdir -p "$dir" "$out"; cp run/upstream/eula.txt "$dir/eula.txt"
jarabs="$(pwd)/$jar"; log="$(pwd)/$out/$stamp-$gc.log"; fifo="$dir/stdin.fifo"
rm -f "$fifo"; mkfifo "$fifo"
cd "$dir"
exec 3<>"stdin.fifo"
java -Xms$heap -Xmx$heap $flags -jar "$jarabs" --nogui <&3 >"$log" 2>&1 &
pid=$!
echo "Started pid $pid ($gc, heap $heap). Waiting for 'Done'..."
for i in $(seq 1 120); do grep -q 'Done (' "$log" && break; kill -0 $pid 2>/dev/null || { echo "Server exited early; see $log"; exit 1; }; sleep 1; done
startup=$(grep -o 'Done ([0-9.]*s)' "$log" | head -1)
echo "Idling $idle s..."; sleep "$idle"
rss_kb=$(awk '/VmRSS/ {print $2}' /proc/$pid/status 2>/dev/null || echo 0)
echo "spark tps" >&3; sleep 3; echo "spark health" >&3; sleep 5; echo "stop" >&3
wait $pid || true
{
  echo "=== SUMMARY $stamp ==="
  echo "gc=$gc heap=$heap idle=${idle}s startup=$startup rss_mb=$((rss_kb/1024))"
  echo "cpu=$(grep -m1 'model name' /proc/cpuinfo | cut -d: -f2) cores=$(nproc) java=$(java -version 2>&1 | head -1)"
} | tee -a "$log"
echo "Full log: $log"
