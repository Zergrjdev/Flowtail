#!/usr/bin/env bash
# P0-05: build UPSTREAM Paper (unmodified) from source into upstream/Paper. Needs internet + JDK 25.
# This downloads Mojang's server at build time (never committed). You are not accepting any EULA here.
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/setup --check || exit 1
repo=$(grep -E '^repo' versions.toml | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
commit=$(grep -E '^pinned_commit' versions.toml | sed -E 's/.*"([a-f0-9]+)".*/\1/')
mkdir -p upstream && cd upstream
[ -d Paper/.git ] || git clone "$repo" Paper
cd Paper
current=$(git rev-parse HEAD)
if [ "$current" != "$commit" ]; then
  git fetch origin "$commit"
  git checkout --detach "$commit"
fi
export JAVA_HOME="${JAVA_HOME:-}"; [ -d "../../.tools/jdk-25" ] && export JAVA_HOME="$(cd ../../.tools/jdk-25 && pwd)" && export PATH="$JAVA_HOME/bin:$PATH"
echo "== applyPatches (slow the first time: downloads Mojang's jar, decompiles) =="
./gradlew applyPatches
echo "== createPaperclipJar =="
./gradlew createPaperclipJar
echo; echo "Built jar(s):"; ls -la paper-server/build/libs/*.jar
echo "Next: bash scripts/run-upstream.sh"
