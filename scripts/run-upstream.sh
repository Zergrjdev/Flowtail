#!/usr/bin/env bash
# Boots the upstream Paper jar in run/upstream/. The Mojang EULA is NOT accepted for you.
set -euo pipefail
cd "$(dirname "$0")/.."
jar=$(ls upstream/Paper/paper-server/build/libs/*paperclip*.jar 2>/dev/null | head -1 || true)
[ -n "$jar" ] || { echo "No jar found. Run scripts/build-upstream-paper.sh first."; exit 1; }
mkdir -p run/upstream && cd run/upstream
if ! grep -qs 'eula=true' eula.txt; then
  echo "Before the server can run you must read and accept the Minecraft EULA:"
  echo "  https://aka.ms/MinecraftEULA"
  echo "Then create run/upstream/eula.txt containing the line: eula=true"
  echo "(This script will not do it for you.)"
  java -jar "../../$jar" --nogui || true; exit 0
fi
exec java -Xms2G -Xmx4G -jar "../../$jar" --nogui
