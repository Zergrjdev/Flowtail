#!/usr/bin/env bash
# Runs every FlowTail test in one go and writes a report you can paste back.
#
#   bash scripts/test-all.sh              everything: python tests, content build, server build (which runs the
#                                         Java unit tests), then boots the server and runs `content selftest`
#   bash scripts/test-all.sh --quick      python tests + content build only (seconds, no Java, no server)
#   bash scripts/test-all.sh --no-server  everything except the boot + selftest step
#   bash scripts/test-all.sh --bots       also connect the benchmark bot swarm for 20 s during the boot step
#                                         (needs online-mode=false in run/flowtail/server.properties)
#
# Output: build/test-all/REPORT.txt (the summary plus the failing parts), and one log per step next to it.
# Exit code: 0 when every step that ran passed, 1 otherwise. A step that could not run is SKIP and never counts as a pass.
set -uo pipefail
cd "$(dirname "$0")/.."

quick=0; server=1; bots=0
for arg in "$@"; do
  case "$arg" in
    --quick) quick=1; server=0 ;;
    --no-server) server=0 ;;
    --bots) bots=1 ;;
    -h|--help) sed -n 2,13p "$0"; exit 0 ;;
    *) echo "unknown option: $arg (see --help)"; exit 2 ;;
  esac
done

OUT=build/test-all
rm -rf "$OUT"; mkdir -p "$OUT"
REPORT="$OUT/REPORT.txt"
NAMES=(); STATUS=(); NOTES=()

java_runtime=$(sed -nE 's/^java_runtime[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' versions.toml | head -1)
if [ -n "$java_runtime" ] && [ -x ".tools/jdk-$java_runtime/bin/java" ]; then
  export JAVA_HOME="$(pwd)/.tools/jdk-$java_runtime"; export PATH="$JAVA_HOME/bin:$PATH"
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/flowtail-gradle}"

record() { NAMES+=("$1"); STATUS+=("$2"); NOTES+=("$3"); printf '%-5s %s%s\n' "$2" "$1" "${3:+  ($3)}"; }

# step "name" logfile command...   PASS when the command exits 0
step() {
  local name="$1" log="$OUT/$2.log"; shift 2
  local start=$SECONDS
  if "$@" >"$log" 2>&1; then record "$name" PASS "$((SECONDS - start))s"
  else record "$name" FAIL "$((SECONDS - start))s, see $log"; fi
}

echo "== FlowTail test run $(date '+%Y-%m-%d %H:%M:%S') =="
step "python: compiler tests" python-compiler python3 -m unittest discover -s compiler/tests
step "python: parity tests"   python-parity   python3 -m unittest discover -s parity
step "content: build example plugin and pack" build-content bash scripts/build-content.sh

if [ "$quick" = 1 ]; then
  record "server build, Java unit tests, boot, selftest" SKIP "--quick"
else
  step "server: build jar (runs the Java unit tests)" build-server bash scripts/build-flowtail-server.sh
  if [ "${STATUS[${#STATUS[@]}-1]}" != PASS ]; then
    record "server: boot and /content selftest" SKIP "the build failed"
  elif [ "$server" = 0 ]; then
    record "server: boot and /content selftest" SKIP "--no-server"
  else
    boot_test() {
      local dir=run/flowtail log="$OUT/server.log" fifo="$OUT/console.fifo"
      mkdir -p "$dir/content-state/plugins" "$dir/config"
      cp build/content/pack/registry.json "$dir/content-state/registry.json"
      cp content-state/allocations.json "$dir/content-state/allocations.json"
      rm -f "$dir"/content-state/plugins/*.ftplugin; cp build/content/plugins/*.ftplugin "$dir/content-state/plugins/"
      if [ -f config/flowtail.properties ]; then cp config/flowtail.properties "$dir/config/flowtail.properties"; fi
      if ! grep -qs '^eula=true' "$dir/eula.txt"; then
        echo "The Minecraft EULA is not accepted in $dir/eula.txt (set eula=true after reading it), then run again." >"$OUT/boot.log"; return 3
      fi
      mkfifo "$fifo"; exec 9<>"$fifo"
      # The test waits for the server's Done/self-test lines while it is running. Without
      # unbuffered Python stdout, parity/flowtail.py only flushes those lines at shutdown.
      PYTHONUNBUFFERED=1 scripts/flowtail launch build/FlowTail-server.jar <"$fifo" >"$log" 2>&1 &
      local pid=$!
      local waited=0
      until grep -q 'Done (' "$log" 2>/dev/null; do
        if ! kill -0 "$pid" 2>/dev/null; then echo "server exited before it finished starting" >"$OUT/boot.log"; exec 9>&-; return 1; fi
        if grep -q 'Address already in use' "$log"; then echo "port in use: stop the other server first" >"$OUT/boot.log"; kill "$pid" 2>/dev/null; exec 9>&-; return 1; fi
        if [ "$waited" -ge 300 ]; then echo "server did not finish starting in 300 s" >"$OUT/boot.log"; kill "$pid" 2>/dev/null; exec 9>&-; return 1; fi
        sleep 2; waited=$((waited + 2))
      done
      echo "content selftest" >&9
      waited=0
      until grep -qE 'SELFTEST (PASSED|FAILED|NOTHING RAN)' "$log"; do
        if [ "$waited" -ge 90 ]; then echo "no selftest verdict within 90 s" >"$OUT/boot.log"; break; fi
        sleep 1; waited=$((waited + 1))
      done
      echo "content missing" >&9; echo "content list" >&9
      if [ "$bots" = 1 ]; then
        ./upstream/Paper/gradlew -p tools/bench run --args="--host 127.0.0.1 --port 25565 --bots 3 --duration-seconds 20 --idle" >"$OUT/bots.log" 2>&1 \
          && record "bots: 3 idle bots for 20 s" PASS "$OUT/bots.log" || record "bots: 3 idle bots for 20 s" FAIL "see $OUT/bots.log (online-mode must be false)"
      fi
      sleep 2; echo "stop" >&9
      waited=0
      while kill -0 "$pid" 2>/dev/null && [ "$waited" -lt 90 ]; do sleep 1; waited=$((waited + 1)); done
      kill -0 "$pid" 2>/dev/null && kill "$pid" 2>/dev/null
      exec 9>&-; rm -f "$fifo"
      grep -E 'selftest |SELFTEST|FlowTail self-test|Blocks whose plugin|No missing content' "$log" >"$OUT/selftest.txt"
      grep -q 'SELFTEST PASSED' "$log"
    }
    boot_test; rc=$?
    if [ "$rc" = 0 ]; then record "server: boot and /content selftest" PASS "$(grep -o 'SELFTEST PASSED.*' "$OUT/selftest.txt" | tail -1)"
    elif [ "$rc" = 3 ]; then record "server: boot and /content selftest" SKIP "$(cat "$OUT/boot.log")"
    else record "server: boot and /content selftest" FAIL "$(cat "$OUT/boot.log" 2>/dev/null || grep -o 'SELFTEST FAILED.*' "$OUT/selftest.txt" | tail -1), see $OUT/selftest.txt"; fi
    bad=$(grep -ciE 'exception|could not load|error occurred|\[ERROR\]' "$OUT/server.log" 2>/dev/null || true)
    if [ -f "$OUT/server.log" ]; then
      if [ "${bad:-0}" = 0 ]; then record "server: log has no errors or exceptions" PASS ""
      else record "server: log has no errors or exceptions" FAIL "$bad lines, see $OUT/server.log"; fi
    fi
  fi
fi

fails=0; passes=0; skips=0
for s in "${STATUS[@]}"; do case "$s" in PASS) passes=$((passes+1));; FAIL) fails=$((fails+1));; SKIP) skips=$((skips+1));; esac; done
{
  echo "FlowTail test report $(date '+%Y-%m-%d %H:%M:%S')  (git: $(git rev-parse --short HEAD 2>/dev/null || echo none))"
  for i in "${!NAMES[@]}"; do printf '%-5s %s%s\n' "${STATUS[$i]}" "${NAMES[$i]}" "${NOTES[$i]:+  (${NOTES[$i]})}"; done
  echo "RESULT: $passes passed, $fails failed, $skips skipped"
  for log in "$OUT"/*.log; do
    name=$(basename "$log" .log)
    case "$name" in server) continue ;; esac
    if grep -qE "$name" <(printf '%s\n' "${NOTES[@]}") 2>/dev/null; then echo; echo "---- tail of $log ----"; tail -n 40 "$log"; fi
  done
  if [ -s "$OUT/selftest.txt" ]; then echo; echo "---- selftest ----"; cat "$OUT/selftest.txt"; fi
} >"$REPORT"
echo; echo "RESULT: $passes passed, $fails failed, $skips skipped"
echo "Report to paste back: $REPORT"
[ "$fails" = 0 ] && [ "$passes" -gt 0 ]
