# 03 — Architecture

## Components
1. **server/** — Paper-fork patch set (build-time download of vanilla + apply patches; no Mojang code committed).
2. **api/** — native plugin API (see `docs/08`).
3. **content-engine/** — registries, extended block registry, item prototypes, painting registry, client-profile translators, virtual entity manager, pack generator/host.
4. **parity/** — settings registry, profiles, risk classes, hardware tuner, capacity estimator.
5. **compiler/** — CLI that compiles a content project into: plugin `.jar`, Java resource pack, (later) Geyser mappings + Bedrock packs.
6. **bedrock-exporter/** — library used by the compiler (Phase 7).
7. **manager/** — local daemon: process control, console stream, config read/write, compiler runner, connectivity checks.
8. **app/** — web UI + desktop shell (loading screen, wizard, settings editor, console, plugin maker, code editor, world converter, profiler, docs).
9. **tools/** — `world-convert`, `bench` (bot swarm + metrics), `diff-test` (later), `shape-scan` (carrier library generator).

## Repo layout (monorepo)
```
/server /api /content-engine /parity /compiler /bedrock-exporter
/manager /app /tools/{world-convert,bench,diff-test,shape-scan}
/docs /examples /scripts  versions.toml  THIRD_PARTY.md  LICENSE
```

## Data flow
```
content project (json + textures + src)
   -> compiler -> plugin.jar + java-pack.zip (+ geyser mappings + bedrock packs, Phase 7)
server boot -> plugins register content in BOOTSTRAP phase (before registries freeze)
   -> registry data (incl. painting variants) sent to clients in the CONFIGURATION phase
   -> pack host serves merged pack; client must load it before entering the world
world data (server state, unlimited custom states)
   -> translator(profile) -> carrier states + virtual entities -> packets (cached per profile)
client input -> reverse translation -> custom block/item events -> plugin behaviours
```

## Custom block pipeline (ADR-004)
- **Storage:** extended registry; chunk palettes saved as namespaced strings; unknown ids preserved (never turned into air).
- **Carriers:** (A) *state carriers* — vanilla states whose client model is overridden by the pack (note block / tripwire / mushroom blocks / etc.) for cube-like blocks.
  (B) *shape carriers* — vanilla blocks whose **collision/selection shape** matches the custom shape (stairs, slabs, trapdoors, carpets, layers, plants…).
  A scan tool (`shape-scan`) builds a **carrier shape library** from vanilla data; an allocator assigns carriers and reports conflicts. Admins may list
  "sacrificed" vanilla blocks whose models can be repurposed (per-world packs make this safe). (C) *barrier + virtual entities* for complex models.
- **Why shapes matter:** the client predicts movement/collision from the carrier. Interaction entities have **no collision**, and barrier is a full cube, so
  stairs/slabs need carriers whose real shape matches.
- **Visuals for complex blocks:** packet-only `item_display`/`block_display` + `interaction` hitbox, spawned when a player starts watching the chunk, removed
  when unwatched; positions stored in block-entity data; ids from a reserved range; never in the level entity list.
- **Mining:** the client predicts break time from the carrier; use matching-hardness carriers where possible, otherwise `tool`/attribute tricks + server-driven break animation. Server remains authoritative.
- **Block entities:** custom server-side block entity types; ticking is opt-in; data persisted with the chunk; sent to clients only when needed.
- **Everything is keyed by client profile** so Bedrock (Geyser) and a no-pack fallback get different translations from the same server state.

## Custom item pipeline (ADR-005)
Definition registry (id -> base item + default component patch + behaviours). Expand on send; reverse-map from client; creative-mode inventory support;
`item_model` + `equippable` + `tool`/`weapon`/`consumable`/`attribute_modifiers` etc. (names/versions: VERIFY). Auto-item for blocks.

## Painting variant pipeline
Register in bootstrap -> included in the painting-variant registry sync -> textures from the pack. Placement uses vanilla wall-attachment rules and fixed
width×height (user wants vanilla + larger sizes). Obtain via item (recipe/loot if defined). Bedrock: generated add-on entity (experimental, Phase 7).

## Settings & profiles
Typed settings registry; each setting declares: key, type, default **per profile**, risk class, description, restart-required. `{name}.properties` is generated with comments;
the app reads the same registry (exported as JSON) to build its UI. See `docs/06`.

## Observability
Structured log lines (level, plugin id, content id, source). Console filters (errors / warnings / both) work on levels, not text matching. Per-plugin
and per-content-type timers for ticks, packets and memory.
