"""FlowTail content compiler (reference implementation, Python stdlib only).

Pipeline:  plugin folder --build--> <id>.ftplugin --pack--> pack.zip + registry.json + allocations.json
See docs/13_PHASE2_DESIGN.md. The server will port the `pack` step to Java for pack.merge=auto (verified with golden vectors).
"""
COMPILER_VERSION = "0.1.0"
SCHEMA = 0
