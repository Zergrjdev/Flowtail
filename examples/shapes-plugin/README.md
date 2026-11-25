# Shape Samples

One block of every Phase 3 shape template. Build and load it next to the River Fishing example:

    bash scripts/build-content.sh examples/example-plugin examples/shapes-plugin

Every texture has a small red "L" near its top-left corner, so a wrongly rotated face is easy to spot.
`weave_button` has no `mining` section on purpose: it shows the default hardness (1.5, stone-like).
Every template uses FlowTail's barrier hitbox and saved state. The display shows its shape, but collision is currently a full cube. Vanilla redstone, shelf inventory and waterlogging are not implemented yet.
The visual checklist is in docs/13_PHASE2_DESIGN.md, section 7.
