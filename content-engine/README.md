# FlowTail content engine

Java 25 Paper plugin bundled into the FlowTail server jar. During Paper bootstrap it loads and validates the generated `content-state/registry.json`, registers painting variants through Paper's registry compose API, and exposes an immutable registry to the runtime plugin.

Build from the repository root with `bash scripts/build-flowtail-server.sh`. The server launcher extracts the embedded plugin into Paper's `plugins/` directory. See `docs/13_PHASE2_DESIGN.md` for the Phase 2 format and invariants.
