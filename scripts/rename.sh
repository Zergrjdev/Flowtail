#!/usr/bin/env bash
# Rename the product everywhere it is written as the working name. Usage: scripts/rename.sh NewName
set -euo pipefail
cd "$(dirname "$0")/.."
new="${1:-}"; [ -n "$new" ] || { echo "Usage: scripts/rename.sh NewName   (letters/digits only)"; exit 1; }
[[ "$new" =~ ^[A-Za-z][A-Za-z0-9]*$ ]] || { echo "Name must be letters/digits only, e.g. FlowTail"; exit 1; }
old=$(grep -E '^software_name' versions.toml | sed -E 's/.*"([^"]+)".*/\1/')
oldl=$(echo "$old" | tr 'A-Z' 'a-z'); newl=$(echo "$new" | tr 'A-Z' 'a-z')
echo "Renaming $old -> $new (lowercase $oldl -> $newl)"
grep -rlI --exclude-dir=.git --exclude-dir=.tools --exclude=LICENSE -e "$old" -e "$oldl" . | while read -r f; do
  sed -i "s/$old/$new/g; s/$oldl/$newl/g" "$f"; echo "  updated $f"; done
echo "Done. Review with: git diff"
