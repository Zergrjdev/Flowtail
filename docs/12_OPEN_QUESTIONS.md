# 12 — Open questions (awaiting user)

Format: `N QUESTION, A (...) B (...)`. User replies like `1a, 2b`. Agents: add new questions at the bottom, continue numbering, never renumber. Move answered items to the "Answered" section with the date and update affected docs/ADRs.

## Answered (2026-11-20, round 1 — user reply "1 B ... 20 does this mean compiling everything")
- Q1 NAME -> B (user wants help choosing; shortlist in Q21). Keep placeholder `{name}` until Q21 answered.
- Q2 LICENCE -> A (GPL-3 fine, Paper fork). Extra constraint: user does NOT want Paper's API or Paper optimisations that break parity -> see Q23 / ADR-012, ADR-015.
- Q3 "POPULAR VERSIONS LATER" -> C (both: old clients via ViaVersion/ViaBackwards AND server builds for older MC versions). Server-on-older-versions is a LATE, separate effort (Phase 8+); latest-only until then.
- Q4 OLD CLIENTS -> old clients cannot see custom content; no special work needed. A "1.8.9 mimic" (newer clients joining a 1.8-style server) is allowed but explicitly labelled unsupported.
- Q5 PACK MERGING -> A (automatic at server start; conflict report still logged).
- Q6 WORLD FORMAT -> B (design our own; very optimised, hardware-aware config). See Q25 and ADR-009. Vanilla-format converter stays mandatory.
- Q7 THREADING -> A (single main thread + offloading default; threading mode selectable in setup/properties; regionised later experiment).
- Q8 PAINTINGS vs ITEM MODELS -> user asked for explanation first; explanation delivered in chat + docs/04 section "Why items don't need paintings". Awaiting confirm (Q22).
- Q9 3D ARMOUR -> A (flat default, 3D advanced opt-in).
- Q10 APP TECH -> B (desktop-only app). Framework choice in Q24.
- Q11 PLUGIN OUTPUT -> B (custom wrapper type containing jar + pack + bedrock outputs, e.g. `.{name}plugin`; compiler can also export plain jar).
- Q12 NO-CODE LOGIC -> C (both visual blocks and trigger->action forms; forms first).
- Q13 HOME-HOSTING -> B (wizard for UPnP/port-forward + guides + generated relay setup for a cheap VPS).
- Q14 NETHERNET -> clarification: NetherNet is Bedrock's newer transport replacing RakNet. NOT a playit failure. Research task R-17: does Geyser/our tunnelling support NetherNet, and what does it mean for home hosting/relay.
- Q15 PACK ENFORCEMENT -> custom: if a player declines the required pack, tell them the server is unplayable, then put them into a cheap "penalty limbo" with artificially high latency (delayed keep-alive/packets) that costs the server LESS than a normal player. Honest limits: a modified client can lie, so this is best-effort; the "verification probe" is optional and off by default. Penalty mode is configurable (kick | limbo-lag).
- Q16 SUPER-SPEED SAFETY -> both (warning + typed confirmation AND a plain toggle in properties; app uses typed confirmation).
- Q17 WORLDGEN IN SUPER-SPEED -> a toggle inside the super-speed settings (`super-speed.worldgen-may-differ`, default false).
- Q18 REPO HOSTING -> A (GitHub).
- Q19 APP OS -> Windows + Linux first, macOS later. Primary dev machine: CachyOS (Arch Linux).
- Q20 CI -> yes, CI compiles and tests everything; GitHub Actions on GitHub-hosted runners is fine (benchmark numbers noisy; keep a local bench path).

## Answered (2026-11-20, round 2)
- Q21 NAME -> working name **FlowTail** (placeholder, may change; keep the single-place name mechanism, P0-07). Properties file = `flowtail.properties`.
- Q22 ITEMS -> A. Items AND blocks are possible without paintings: items via `item_model`; blocks via carrier block states (simple cubes) or carrier block + block entity + packet-only display entity + interaction entity (complex). Paintings remain only for wall-placed art. The painting-based fallback is NOT needed for items/blocks.
- Q23 PAPER PARITY -> A (parity-breaking Paper changes become off-by-default toggles). PLUS: the plugin system must be our own native design (different from Paper's), and we add MANY more optimisations than Paper (e.g. chunk pregeneration tooling, XP orb clumping, item merging, entity/hopper/redstone/mob-AI/tracker improvements, packet caching, async everything). Each gets a risk class and a profile placement (docs/06).
- Q24 DESKTOP SHELL -> A (Electron). Qt is an alternative; revisit only if Electron size/memory is a real problem (Phase 7 spike).
- Q25 WORLD FORMAT -> A (survey existing formats, then design ours, vanilla converter). User also wants a compression-algorithm comparison (R-20).

## Answered (2026-11-25)
- Q26 PARTIAL COLLISION UNDER ADD-ONLY -> C (research first). The server-side Shulker probe ran on a disposable localhost Paper 26.3 server. `getBoundingBox()` stayed 1×1×1 for scale 1.0/0.5 and peek 0/1; idle tick measurements were inconclusive. No live vanilla client was available. See R-05 in `docs/11_RESEARCH_TASKS.md`. This is not a final collision design choice; Q27 asks what P3-02 should implement.
- Q27 PARTIAL COLLISION IMPLEMENTATION -> C (user, 2026-11-25). Investigate FlowTail-owned collision in the server fork before changing template behavior; do not substitute a vanilla block. Recorded as ADR-031.

## Pending
