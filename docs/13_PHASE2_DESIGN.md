# 13 — Phase 2 design: content engine MVP

Written 2026-11-21 by Claude. Read with `docs/03` (architecture), `docs/04` (formats), `docs/08` (API).
**Status of Phase 2:** the *data side* (compiler, validation, pack generator, carrier allocation) is built and tested. P2-01's Java 25 Paper bootstrap and painting registration are implemented and verified on Paper 26.3; remaining server-side tasks are in progress following `docs/14_PHASE2_CODEX_TASKS.md`.

## 1. What exists and is tested (Python compiler suite: `python3 -m unittest discover -s compiler/tests` → 15 tests)
| Piece | Where | Verified |
|---|---|---|
| Position-aware JSON parser (errors say file:line) | `compiler/ftc/jsonpos.py` | unit tests |
| Schema v0 validation for plugin.json / blocks / items / paintings, typo suggestions, recipe + id reference checks, painting size-vs-texture check | `compiler/ftc/validate.py` | unit tests (file, line, fix on every error) |
| `build`: plugin folder → `<id>.ftplugin` (zip: `manifest.json` with per-file SHA-256, `content.json`, `pack/assets/…`, optional `datapack/`, optional `plugin.jar`) | `compiler/ftc/build.py` | unit tests, example plugin |
| `pack`: N plugins → one resource pack + `registry.json` + persistent `allocations.json` | `compiler/ftc/pack.py` | unit tests: deterministic, slots never move, retired slots never reused, conflicts reported |
| Carrier pool `cube.note_block` (allowlist, fallback property list or exact list from a server report) | `compiler/ftc/carriers.py` | unit tests |
| `scripts/gen-reports.sh`, `scripts/mc-pack-format.py`, `scripts/build-content.sh` | scripts/ | pack-format script tested on a synthetic jar; the other two only syntax-checked (need the real server jar) |
| Paper bootstrap + registry load + painting registration | `content-engine/` P2-01 | Paper 26.3 boot, log count, painting compose event and bot login |
| Java pack generator, built-in host and join-time enforcement | `content-engine/` P2-07 | golden vectors; Paper server + bot fetch, SHA-1, loaded, decline and optional-pack checks |

**Step 0 data check (Codex, 2026-11-21):** the Mojang 26.3 jar reports resource pack `97.1`, data pack `121.0`, protocol `777`; `blocks.json` reports note-block properties `instrument`, `note` (0–24), and `powered` (true/false). The report has 27 instruments, including four trumpet variants missing from the original fallback. These mismatches were corrected before server-side implementation. `build-content.sh` now generates a pack declaring exact `min_format`/`max_format` `[97, 1]` and uses the full server-reported carrier states. **Still unverified with a vanilla GUI client:** visual Rock block/item models and client resource reload. No client app was available in the Codex session; static pack inspection is not treated as visual verification.

## 2. Formats (frozen for Phase 2; schema number 0)
**`<id>.ftplugin`** = zip, deterministic (sorted entries, fixed timestamps). `manifest.json` lists a SHA-256 for every other file; `pack` refuses a damaged file.
**`registry.json`** (written by `pack`, read by the server): plugins, blocks (each with `carrier:{pool,block,state}`), items (`base`, `item_model`), paintings (`asset_id`), `retired_blocks`, `pack_format`.
**`allocations.json`** (server state, persists, never hand-edited): `pools.<pool>.slots.<ns:block> = {state, retired}`. Rules: append-only; a removed block's slot is kept and marked `retired`; retired slots are rendered with a visible placeholder model, never reused.
**`pack.zip` + `pack.sha1` + `pack.json`**: SHA-1 of the exact bytes served (this is what the client checks). `versions.toml` stores `resource_pack_format = [major, minor]`; `pack.mcmeta` writes that exact tuple to both `min_format` and `max_format` (26.3: `[97, 1]`).

### Hard invariants (do not break; they protect worlds)
1. **Saved worlds store namespaced ids (`example:rock`), never carrier states.** Carriers are a client-display detail so the engine can later be replaced by the extended block registry (ADR-004) without touching saves.
2. Unknown ids (plugin removed) are **preserved**, never turned into air (`/content missing` reports them; P3-10).
3. Carrier slots are append-only (above).
4. The compiler never writes into the server world or accepts the EULA.

## 3. Carrier MVP and its known limits
Pool `cube.note_block`: all `note_block` states except the one vanilla places (`instrument=harp,note=0,powered=false`). Limits, all must be shown to admins:
* **Vanilla note blocks must be disabled** in worlds that use this pool (placing, tuning, playing), because their states are our carriers. Setting: `content.note-block.vanilla=disabled` (new key, add to the registry in `parity/flowtail.py`).
* Note blocks change state on neighbour updates (instrument follows the block below; powered follows redstone). The engine **must cancel physics for tracked custom blocks** (Paper: `BlockPhysicsEvent`, `NotePlayEvent`). If the real server later patches this properly, remove the event workaround.
* The client predicts mining speed from the note block, so break time on the client can differ from the configured hardness until P3-04.
* The exact state list comes from `blocks.json` (data generator). The built-in list is a fallback and the compiler warns `W-CARRIER-UNVERIFIED` when it is used.
* Mushroom/tripwire pools and shape carriers (stairs, slabs…) are P3-02. Per R-06, rarity is not proof a state is safe; pools stay explicit allowlists.

## 4. Server side: design grounded in Paper's API (read from source at commit 1eda666, not yet compiled against)
Paper already exposes what we need for the MVP, so **Phase 2 does not need deep NMS patches**; build it as a bundled system plugin/module (`content-engine/`) first, move hot paths into patches in Phase 4.

**Step 0 API correction (2026-11-21):** Paper 26.3's `RegistryEvents` exposes a compose event for `PAINTING_VARIANT`, but it does not expose custom `BLOCK` or `ITEM` registry events. Blocks and items in this MVP are FlowTail definitions stored in the internal content registry; their server representation is the vanilla carrier/base item. Only painting variants are added to a real Minecraft registry through Paper's registry compose API. Do not describe the virtual block/item definitions as Mojang registry entries.

| Need | Paper API found | Notes |
|---|---|---|
| Load FlowTail definitions early | `io.papermc.paper.plugin.bootstrap.PluginBootstrap#bootstrap(BootstrapContext)` | P2-01 loads and validates `registry.json` before normal plugin enable |
| Painting variants | `RegistryEvents.PAINTING_VARIANT.compose()` + `PaintingVariantRegistryEntry.Builder` (`width`, `height`, `assetId`, `title`, `author`; width/height 1–16) | Experimental real-registry API; use for P2-01 painting registration |
| Blocks/items | No Paper 26.3 custom BLOCK or ITEM registration event | Keep these as virtual FlowTail registry definitions, mapped to carriers/base items |
| Item model | `DataComponentTypes.ITEM_MODEL` (value is a `Key`) | `item_model` = `example:ruby` |
| Stored item id | no `custom_data` constant in `DataComponentTypes`; use the item's `PersistentDataContainer` (stored inside `custom_data`) | key `flowtail:id` |
| Send pack | `Player#setResourcePack(UUID, url, hash, prompt, required)`, `PlayerResourcePackStatusEvent.Status` (`SUCCESSFULLY_LOADED, DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED…`) | implemented in P2-07; declined required pack follows `pack.decline-action` |
| Configuration-phase hold | `AsyncPlayerConnectionConfigureEvent`, `PlayerConnectionInitialConfigureEvent` | These events do not expose a way to suspend configuration until pack status; P2-07 uses join-time request and freezes movement until `SUCCESSFULLY_LOADED` |
| Per-chunk storage | chunk `PersistentDataContainer` | VERIFY the exact interface; store `local x,y,z → namespaced id` |
| Block events | `BlockPlaceEvent`, `BlockBreakEvent`, `BlockPhysicsEvent`, `NotePlayEvent` | MVP carrier logic |
| Pack host | JDK `com.sun.net.httpserver.HttpServer` | no dependency; verify SHA-1 at startup |

Pipeline at server start (P2-01/P2-07): read `content-state/plugins/*.ftplugin` → run the Java pack step (port of `compiler/ftc/pack.py`; golden-tested by ZIP entry hashes) → write `registry.json`, `allocations.json`, `pack.zip` → verify and start host → register content → on join send pack and hold required-pack players until status. `pack.required=false` sends an optional pack without a movement hold. `limbo-lag` uses a hidden, invulnerable low-chunk limbo per ADR-016's implementation note; it does not delay keep-alive packets.

## 5. Why the compiler is Python now and what happens to it
Python is quick to build and test here, and matches `parity/flowtail.py`. But the server must merge packs at start (Q5a) and end users should not need Python. **Proposed ADR-018:** the Python compiler is the *reference implementation*; the server gets a Java port of `pack` (and later `build`) checked against **golden vectors**: for the same `.ftplugin` inputs both produce identical `registry.json`, `allocations.json` and identical per-entry contents of `pack.zip` (compare entry hashes, not zip bytes). Add `compiler/tests/golden/` when the Java port starts. If you prefer one implementation, port everything to Java and keep this Python code only as test oracle — tell Claude/Codex which.

## 6. Phase 2 acceptance test (manual, vanilla 26.3 client)
1. `bash scripts/gen-reports.sh && bash scripts/build-content.sh` → no errors; only the known stairs warning.
2. Start FlowTail with the example plugin installed. Join with a vanilla client: accept the pack. Decline it once: see the "unplayable" message (pack.decline-action).
3. `/give @s example:ruby` → inventory shows the ruby texture, not paper. Wear nothing special; place the Rock block → shows the Rock texture, collides, breaks, drops Rock.
4. Stop/start the server: Rock blocks still there. Remove the plugin: world loads, blocks preserved as placeholders, `/content missing` lists `example:rock`. Re-add: blocks return.
5. Bot swarm (tools/bench) still logs in and reports MSPT; no new errors in the structured log.
