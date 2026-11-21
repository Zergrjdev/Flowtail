#!/usr/bin/env bash
# Build the pinned Paper base, apply FlowTail's isolated source patches, and name the artifact.
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/setup --check
bash scripts/build-upstream-paper.sh
repo=upstream/Paper
patch=server/patches/0001-flowtail-structured-startup.patch
if ! git -C "$repo" diff --quiet || ! git -C "$repo" diff --cached --quiet; then
  echo "[ERROR] upstream/Paper has local source changes. Save or review them before building FlowTail." >&2; exit 1
fi
if git -C "$repo" apply --check "../../$patch"; then
  git -C "$repo" apply "../../$patch"
elif git -C "$repo" diff --quiet -- paper-server/src/main/java/org/bukkit/craftbukkit/Main.java; then
  echo "[ERROR] FlowTail patch no longer applies. See $patch and docs/phase-1-rebase.md." >&2; exit 1
fi
(cd "$repo" && ./gradlew createPaperclipJar)
mkdir -p build
source_jar=$(find "$repo/paper-server/build/libs" -maxdepth 1 -type f -name '*paperclip*.jar' -printf '%T@ %p\n' | sort -nr | head -1 | cut -d' ' -f2-)
[ -n "$source_jar" ] || { echo "[ERROR] Paper build produced no Paperclip jar." >&2; exit 1; }
cp "$source_jar" build/FlowTail-server.jar
git -C "$repo" checkout -- paper-server/src/main/java/org/bukkit/craftbukkit/Main.java

echo "== build bundled FlowTail content engine =="
"$repo/gradlew" -p content-engine clean test jar
content_jar=$(find content-engine/build/libs -maxdepth 1 -type f -name 'FlowTailContent-*.jar' | head -1)
[ -n "$content_jar" ] || { echo "[ERROR] Content engine build produced no plugin jar." >&2; exit 1; }
mkdir -p build/flowtail-embedded/META-INF/flowtail
cp "$content_jar" build/flowtail-embedded/META-INF/flowtail/FlowTailContent.jar
jar uf build/FlowTail-server.jar -C build/flowtail-embedded META-INF/flowtail/FlowTailContent.jar
echo "Built and embedded: build/FlowTail-server.jar (META-INF/flowtail/FlowTailContent.jar)"
