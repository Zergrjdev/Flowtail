# 01 — Requirements (traceable)

`Src` = number of the original 60-question list the user answered (Q#). Priority: **MUST** / **SHOULD** / **LATER**.
Status: all items come from the user unless marked *(assistant default)*.

## Platform & process
| ID | Requirement | Pri | Src |
|---|---|---|---|
| PL-01 | Base on a fork of Paper (assistant default; user said "you decide") on Java 25, targeting the latest MC only at first | MUST | 1, 6 |
| PL-02 | Support popular older versions later (user named 1.21.2, 1.21.11, 1.8.9) — meaning (client vs server versions) is OPEN | LATER | 6 |
| PL-03 | Adopt each new Mojang drop within ~1–2 weeks: small isolated patch set + rebase script | MUST | 7 |
| PL-04 | Open source; community plugin authors; stable versioned API | MUST | 4, 49, 59 |
| PL-05 | Licence: user prefers non-GPL if possible — conflicts with a Paper fork (see doc 10) | OPEN | 60 |
| PL-06 | Solo dev + AI agents: automation-first (scripts, CI, one-command setup) | MUST | 3, 5 |
| PL-07 | Works from 1 to 500+ players on any hardware via auto-tuning | MUST | 14, 15 |

## Parity & performance
| ID | Requirement | Pri | Src |
|---|---|---|---|
| PF-01 | Four named profiles + custom: `vanilla` (COMPLETE parity), `balanced` (default, "mostly vanilla, speed wins on conflict"), `performance`, `super-speed` (way less parity, needs explicit toggle) | MUST | 8, 13 |
| PF-02 | Every optimisation individually settable in `{name}.properties` | MUST | 8, 9, 10, 13 |
| PF-03 | Redstone: faster algorithm allowed in `balanced` if contraptions still work; setting + exact-parity option | MUST | 9 |
| PF-04 | Mob farms/cramming: small differences OK in `balanced`; setting + exact-parity option | MUST | 10 |
| PF-05 | Worldgen identical to vanilla for every seed (golden tests) | MUST | 11 |
| PF-06 | Faster world format as default with lossless converter to/from vanilla; vanilla format selectable | MUST | 12, 24 |
| PF-07 | Priorities: no lag spikes + lowest bandwidth first, then avg tick, then memory | MUST | 19 |
| PF-08 | Threading configurable: set automatically during setup or manually; regionised mode is experimental/later | SHOULD | 17 |
| PF-09 | GC auto-selected from hardware (user asked "which is best"); see doc 06 | MUST | 20 |
| PF-10 | Capacity estimator: app shows *recommended max players* for the chosen hardware + settings (include upload bandwidth) | SHOULD | 57 |

## Custom content
| ID | Requirement | Pri | Src |
|---|---|---|---|
| CC-01 | Unlimited custom blocks per plugin ("as many as you want") | MUST | 21 |
| CC-02 | Shapes: full cubes, stairs, slabs, trapdoors, shelves, saplings/plants, plus complex models; hitbox types configurable | MUST | 22, 23 |
| CC-03 | Block ticking opt-in per block; block states: rotation + arbitrary properties + connected textures | MUST | 23, 24 |
| CC-04 | Block inventories via a very structured code API (not data-driven) | SHOULD | 25 |
| CC-05 | Fully custom hardness, tool types and tiers | MUST | 26 |
| CC-06 | Custom sounds and particles per block; light emission; breakable/explosion rules per block | MUST | 27, 28, 29 |
| CC-07 | Every block auto-creates its item | MUST | 23 |
| CC-08 | Items: tools/weapons fully custom behaviour in code; armour custom stats + models; foods and potions; recipes optional per definition | MUST | 30–34, 37 |
| CC-09 | Armour models: flat retexture default; full 3D available to developers (advanced opt-in) | SHOULD | 32 |
| CC-10 | Item definition storage: hybrid (id in `custom_data` + server-expanded components, overrides allowed) | MUST | 33 |
| CC-11 | Painting variants are a first-class registry: fully retextured, vanilla + larger sizes, obtainable if a recipe exists; "core of the software" | MUST | 35, 36, 37 |
| CC-12 | Plugin folder: `src/` (datapack/regular plugin structure), `blocks/<Name>/{texture.png,config.json}`, `items/`, custom functions | MUST | 23 |
| CC-13 | Plugin compiler turns the folder into a plugin artifact + packs (Java + Bedrock) | MUST | 23, 42, 46 |
| CC-14 | Content definitions in both code and data (JSON) | MUST | 45, 46 |

## Plugin API
| ID | Requirement | Pri | Src |
|---|---|---|---|
| API-01 | Native API first; Bukkit compat low priority (comes free-ish with a Paper base but is not promised) | MUST | 18, 44 |
| API-02 | Languages: Java + Kotlin (assistant default); no-code via the app; scripting later maybe | MUST | 48 |
| API-03 | Restart-to-reload is fine (hot reload not required) | — | 47 |
| API-04 | Extensive built-in debug tools (give, block inspector, content list…) | SHOULD | 50 |
| API-05 | Public-community-grade stability: semver, deprecation policy, javadocs | MUST | 49 |
| API-06 | Plugin artifact = `.jar` (assistant default; custom wrapper type is OPEN); source = JSON | MUST | 46 |

## Resource packs
| ID | Requirement | Pri | Src |
|---|---|---|---|
| RP-01 | Built-in HTTP pack host | MUST | 51 |
| RP-02 | Pack required; enforce as hard as the protocol allows (user wants to defeat "decline" mods — best-effort only, see doc 10/11) | SHOULD | 52 |
| RP-03 | Merging: user answered "manual" — treated as *explicit build/merge step with overrides*; confirm (Q5) | OPEN | 53 |
| RP-04 | Per-world packs | SHOULD | 54 |

## Bedrock / Geyser
| ID | Requirement | Pri | Src |
|---|---|---|---|
| BE-01 | Built last, but every content feature must be designed with Geyser in mind | MUST | 38 |
| BE-02 | Geyser managed by our software; compile-time generation of Geyser mappings + Bedrock resource/behaviour packs with manual override options | MUST | 39, 41, 42 |
| BE-03 | Plugin authors never think about Bedrock (fully automatic) | MUST | 43 |
| BE-04 | Paintings on Bedrock: not hand-handled; produced via generated add-on (custom entity projection, experimental) | LATER | 40 |

## Companion app (details in doc 07)
| ID | Requirement | Pri | Src |
|---|---|---|---|
| AP-01 | Loading screen, built-in docs, setup wizard with RAM/CPU menu **before** settings; auto-configure settings from hardware | MUST | 13, 15 |
| AP-02 | Parity slider (efficiency ↔ parity) + SUPER SPEED toggle; also normal `server.properties` | MUST | 13 |
| AP-03 | Settings editor: select a server folder and edit everything | MUST | 15 |
| AP-04 | "Jar maker" / server builder: produce a ready server folder from the wizard choices | MUST | 23 |
| AP-05 | Custom console (SquidServers-like) exposing EVERY config; filters: errors / warnings / both; errors extremely easy to find | MUST | 23, 55 |
| AP-06 | No-code plugin maker + custom code editor for specific logic | MUST | 23 |
| AP-07 | World converter (our format ⇄ vanilla) | MUST | 23 |
| AP-08 | Home-hosting connectivity helper (tunnel pain) | SHOULD | 16 |
| AP-09 | Per-plugin / per-content-type profiler UI plus Spark-style profiling | SHOULD | 55 |

## Quality
| ID | Requirement | Pri | Src |
|---|---|---|---|
| QA-01 | Automated benchmarks in CI; results feed the capacity estimator | MUST | 57 |
| QA-02 | Differential testing vs vanilla — later (assistant recommends a minimal version in Phase 4) | LATER | 56 |
| QA-03 | Full docs written near the end (Phase 9) | MUST | 58 |
