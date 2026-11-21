# 08 — Native plugin API (principles; DRAFT)

**Audience:** public community authors (Q49) -> stability and clarity beat cleverness. Bukkit compatibility is low priority and not promised (Q18/Q44).

## Principles
1. **Registry-first:** content is registered during a **bootstrap phase** (before registries freeze and sync to clients). Late registration is an error with a clear message.
2. **Structured, typed, discoverable:** builders and typed events instead of strings and maps. Inventories, block entities, tick hooks, mining, drops all have dedicated APIs (CC-04).
3. **Data + code parity:** anything definable in JSON is definable in code and vice-versa; code overrides data per id.
4. **Semver + deprecation policy:** `api.version` in `plugin.json`; deprecations last at least 2 minor versions; javadocs on everything public; no internals leaked.
5. **Safe by default:** non-ticking blocks, bounded virtual entities per view, meters per plugin; misbehaving plugins show up in the profiler with their id.
6. **Bedrock-blind:** nothing in the API requires Bedrock knowledge; `player.isBedrock()` exists as an escape hatch only.
7. **Threading clarity:** document which calls are main-thread-only; design so a future regionised mode can be supported (avoid global mutable state, prefer location-scoped schedulers).
8. **Errors are first-class:** exceptions in plugin code are attributed (plugin id, content id, event) in structured logs; never crash the tick loop.

## Surface sketch
- `ContentRegistry` — `blocks()`, `items()`, `paintings()`, `recipes()`, `sounds()`.
- `CustomBlock` (template, shape, states, mining, drops, sounds, light, ticking, behaviours) · `BlockEntityHandle` (data, optional ticking, inventories).
- `CustomItem` (template, stats, components, behaviours, armour equipment, food, recipes) · `ItemPrototype`.
- `CustomPainting` (size, texture, author, item).
- Events: `BlockPlace/Break/Interact/NeighborChange/Tick`, `ItemUse/Hit/Equip`, `PaintingPlace/Break`, `ContentReload` (restart-only for now).
- Behaviours: annotation or builder registration; referenced from JSON by `namespace:id`.
- Services: pack builder hooks, translator hooks (advanced), debug tools extension points.

## Lifecycle
`bootstrap` (register) -> `load` -> `enable` -> `tick` … -> `disable`. Restart required to change content (Q47).

## Compat note
Because the base is a Paper fork, the Bukkit/Paper API exists underneath. Treat it as *unsupported legacy surface* until a deliberate compat decision (Phase 8).
