# 05 — Bedrock / Geyser

**Timing:** implemented last (Phase 7) but every earlier feature must be Geyser-aware (BE-01). **Rule:** plugin authors never write Bedrock code (BE-03).

## What Geyser provides (partially verified 2026-11-20, see docs/11 R-08 and R-17)
- **Items (v2):** mappings keyed on Java `item_model` / components; legacy custom-model-data still supported; multiple Bedrock definitions per Java item via predicates;
  `bedrock_identifier` must be unique and not in `minecraft:`. A Bedrock resource pack must accompany the mappings.
- **Blocks:** custom blocks via **block-state overrides** on vanilla blocks (JSON mappings or extension API); needs custom-content enabled in Geyser's config (key name has changed across versions).
- **Skulls:** custom skull mappings exist.
- **Entities:** experimental custom-entity APIs reportedly arrived in Geyser 2.11; display-entity translation is **unconfirmed** (one third-party plugin reports `ItemDisplay` is not translated).
- Geyser does **not** convert Java resource packs or auto-generate mappings. Reference tools/projects: Rainbow (Fabric mod), VoxelCore, Kalo (third-party Paper plugins that publish Geyser mappings + Bedrock packs).

## Our approach (DECIDED shape, DEFAULT details)
1. **One definition, three outputs:** from the same JSON the compiler emits (a) server registration, (b) Java resource pack, (c) Geyser mappings + Bedrock resource & behaviour packs.
2. **Manual overrides:** `bedrock/` folder in a content project can override generated values (Q42b).
3. **Managed Geyser:** our software can install/update Geyser (+ Floodgate if wanted), drop mappings in `custom_mappings/` and the pack in Geyser's `packs/`, and write a publication manifest (revision + hashes). Geyser-Spigot is expected to run on a Paper-based server (VERIFY on our fork).
4. **Translation strategy per content kind**
   | Content | Java client | Bedrock via Geyser |
   |---|---|---|
   | Cube/state-carrier blocks | carrier + pack override | Geyser block-state override + generated Bedrock block (geometry/material) |
   | Shaped blocks (stairs, slabs…) | shape carrier | Bedrock custom block with matching collision/selection boxes |
   | Complex blocks (display + interaction) | virtual entities | **Risky** — needs custom-entity projection or a fallback carrier look-alike |
   | Items | `item_model` + components | Geyser v2 item mapping + attachable/geometry for 3D |
   | Armour | equipment assets | Bedrock attachables (3D possible on Bedrock) |
   | Painting variants | painting entity + pack texture | generated custom entity (experimental) or vanilla fallback |
   | Sounds/particles | pack | Bedrock sound definitions / particle files |
5. **Fallbacks are first-class:** each content definition carries a computed Bedrock fallback (nearest vanilla look-alike) used when exact projection is impossible; the app reports which content is exact vs approximated.
6. **Behaviour stays on the Java server.** Bedrock behaviour packs only describe client-visible properties (collision boxes, geometry, materials, mining speed). No gameplay logic is duplicated.
7. **Bedrock detection API:** `player.isBedrock()` for the rare plugin that needs it; Floodgate-aware auth.
8. **Model conversion:** Java model JSON / Blockbench -> Bedrock geometry (Rainbow is prior art). Textures are shared; atlas/size limits to be researched.
9. **Pack delivery:** Bedrock packs are served by Geyser; pack-required settings differ from Java — document.

## Design-now checklist (so Phase 7 isn't a rewrite)
- Keep **client profile** as a first-class parameter in every translator and cache.
- Keep block shapes/hitboxes/hardness/light as *data* (not code) so exporters can read them.
- Keep unique, stable ids for every definition (used as `bedrock_identifier`).
- Avoid features with no Bedrock equivalent unless a fallback exists; list them in `docs/11`.

## Home-hosting note (UPDATED 2026-11-20, R-17)
**Bedrock now requires NetherNet.** RakNet connections stopped working on/after 27 Oct 2026 (Bedrock 26.60). NetherNet = signaling over **TCP (HTTP)** on the Geyser port + gameplay over **UDP (WebRTC)**. Consequences for FlowTail:
- Geyser build #1237+ is required and NetherNet must be enabled in Geyser's config; our managed-Geyser installer does this automatically.
- Home-hosting wizard must open/forward **both TCP and UDP** for the Bedrock port (default 19132), or use **external signaling (NXS, e.g. Warden)** which needs only UDP for WebRTC.
- `clone-remote-port` (Bedrock on the same port as Java) does not work with built-in signaling: Bedrock gets its own port.
- Tunnels (playit etc.) must carry TCP+UDP for that port; this probably explains the earlier playit failures. The connectivity helper (doc 07) must test TCP and UDP separately and explain results in plain English.
- Built-in signaling is plain HTTP by default (players get a trust-on-first-use prompt); offer HTTPS (certificate or reverse proxy) as an advanced option.
- HAProxy header forwarding is not yet supported for NetherNet gameplay; the relay recipe (Q13b) must not depend on it.
