#!/usr/bin/env bash
# One-command build and launch for the Linux FlowTail development server.
# The operator must accept the Minecraft EULA manually before the first successful boot.
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/build-content.sh
bash scripts/build-flowtail-server.sh
mkdir -p run/flowtail/content-state/plugins run/flowtail/config
cp build/content/pack/registry.json run/flowtail/content-state/registry.json
cp content-state/allocations.json run/flowtail/content-state/allocations.json
cp build/content/plugins/*.ftplugin run/flowtail/content-state/plugins/
if [ -f config/flowtail.properties ]; then cp config/flowtail.properties run/flowtail/config/flowtail.properties; fi
exec scripts/flowtail launch build/FlowTail-server.jar
