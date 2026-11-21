# 00 — Context

## 1. Vision (user's words, condensed)
A Minecraft Java server software that is **heavily optimised yet keeps vanilla behaviour**, scales from 1 to 500+ players
on anything from a home PC to a big dedicated box, and makes it **easy for plugin developers to add new blocks, items and
painting variants** that look fully retextured, using block entities + invisible hitboxes for blocks and data components
("NBT") for tools/armour. **Custom content is the core of the product.** Bedrock players join through Geyser; plugin
authors should never have to think about it. A companion app handles setup, settings, console, no-code plugin making, a code
editor, and world conversion.

## 2. The user and how to work with them
- Not a programmer; has never touched server internals; **can self-host Minecraft servers**.
- Solo, with AI agents (Codex + Claude) writing most code. Open-source release is intended; public community plugin authors.
- Wants: clear errors, easy-to-find problems, a polished app (loading screen, docs, wizard), configurable *everything*.
- Pain point: **home hosting connectivity** — playit.gg tunnel is laggy, and Bedrock-side connection ("nether net" — assumed
  to mean Bedrock/UDP connectivity through the tunnel; confirm) was hard to set up through it. See `docs/07_APP.md` §Connectivity.
- Communication style: short answers in `1a, 2b` format to batched questions.

## 3. Priorities (ordered)
1. Custom content engine (blocks, items, painting variants) — "the core of the software".
2. No lag spikes (tail latency) and lowest bandwidth; then average tick time; then memory.
3. Vanilla behaviour, tunable with a parity slider; bit-exact worldgen always.
4. Companion app and tooling.
5. Bedrock/Geyser (built last, but architected in from the start).
6. Docs (written near the end), differential testing (nice, later), Bukkit/Paper plugin compatibility (later, low priority).

## 4. Environment facts as of 2026-11-20 (VERIFY before relying)
- Java Edition **26.3 "Wilderness Bound"** released 2026-09-15 and was still current in mid-Nov 2026. 2026 drops so far: 26.1 (2026-03-24),
  26.2 (2026-06-16), 26.3 (2026-09-15). Mojang uses **year-based version numbers** from 2026. **Java 25 required** since 26.1.
- Geyser: custom items **v2** mapping system based on `item_model` + item components (v1 deprecated); custom blocks via block-state
  overrides (JSON mappings or Geyser extension API); Bedrock resource pack must be supplied; Geyser does not auto-convert Java packs.
  Rainbow (Fabric mod) generates mappings + Bedrock packs and is a good reference. Geyser 2.11 reportedly added experimental
  custom-entity APIs; one third-party plugin reports `ItemDisplay` entities are not translated to Bedrock. See `docs/05`.
- References: https://geysermc.org/wiki/geyser/custom-items · https://wiki.geysermc.org/geyser/custom-blocks · https://github.com/GeyserMC/rainbow

## 5. Glossary
- **Carrier block:** a real vanilla block state the *client* sees in place of a custom block (gives vanilla-exact collision/shape/prediction).
- **Virtual (packet-only) entity:** an entity sent to clients by packets only; never exists in the server's entity list; no ticking, no saving.
- **Extended registry:** server-side registry that holds unlimited custom block states beyond what vanilla clients know.
- **Client profile:** how a connection is translated: `java-pack`, `java-fallback` (no pack / old client), `bedrock`.
- **Prototype item:** stack stores only a custom id (+ small `custom_data`); the server expands the full component set when sending to clients.
- **Content project:** plugin source folder (`plugin.json`, `src/`, `blocks/`, `items/`, `paintings/` …) compiled by the plugin compiler.
- **Parity profile:** `vanilla` (COMPLETE parity) · `balanced` (default) · `performance` · `super-speed` (needs explicit toggle) · `custom`.
- **Risk class R0–R3:** how much an optimisation can deviate from vanilla (see `docs/06`).
- **Items use data components, not NBT** (since 1.20.5). "NBT for armour/swords" in the user's words means components + `custom_data`.

## 6. Out of scope for now
Folia-style regionised ticking (experimental, later), older-server-version builds (1.21.2 / 1.21.11 / 1.8.9 — later, meaning to be clarified in
open question Q3), full Bukkit/Paper plugin compatibility guarantee, differential testing (later).
