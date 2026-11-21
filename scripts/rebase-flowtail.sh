#!/usr/bin/env bash
# Rebase the isolated FlowTail patch onto a Paper commit and build it. Usage: scripts/rebase-flowtail.sh <40-char-commit>
set -euo pipefail
cd "$(dirname "$0")/.."
commit="${1:-}"
[[ "$commit" =~ ^[0-9a-fA-F]{40}$ ]] || { echo "Usage: scripts/rebase-flowtail.sh <40-character Paper commit>"; exit 2; }
repo=upstream/Paper
git -C "$repo" diff --quiet && git -C "$repo" diff --cached --quiet || { echo "[ERROR] upstream/Paper has local tracked changes. Review or save them before rebasing."; exit 1; }
current=$(git -C "$repo" rev-parse HEAD)
if [ "$current" != "$commit" ]; then
  git -C "$repo" fetch origin "$commit"
  git -C "$repo" checkout --detach "$commit"
fi
if ! git -C "$repo" apply --check ../../server/patches/0001-flowtail-structured-startup.patch; then
  echo "[ERROR] FlowTail patch conflicts with this Paper commit. Resolve the patch in server/patches, then retry."; exit 1
fi
sed -i -E "/^pinned_commit[[:space:]]*=/ s/[0-9a-f]{40}/$commit/" versions.toml
echo "Pinned Paper at $commit. Run scripts/build-flowtail-server.sh to compile and verify the rebase."
