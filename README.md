# Handoff package — Minecraft server software project

This folder contains the roadmap, research notes and early Linux-first Phase 1 implementation for the FlowTail Paper fork.

## How to use
1. Create a new git repo and copy this folder's contents into its root (or open this folder as the repo).
2. Open it in Codex (or Claude Code) and say:  
   **"Read AGENTS.md, then continue the roadmap. Log progress in PROGRESS.md."**
3. Answer any questions the agent asks using the `1a, 2b, 3c` format.

## Map
| File | Purpose |
|---|---|
| `AGENTS.md` | Rules for the AI agent. Read first. (`CLAUDE.md` just points to it.) |
| `PROGRESS.md` | Append-only log + "Current status" block. |
| `ROADMAP.md` | Phased checklist with acceptance criteria. |
| `docs/00_CONTEXT.md` | The context file: vision, user, facts, glossary. |
| `docs/01_REQUIREMENTS.md` | Every user answer turned into a traceable requirement. |
| `docs/02_DECISIONS.md` | Architecture decision log (DECIDED / DEFAULT / OPEN). |
| `docs/03_ARCHITECTURE.md` | Components, repo layout, data flow. |
| `docs/04_CUSTOM_CONTENT.md` | Blocks, items, paintings, plugin project format, config schemas (draft). |
| `docs/05_BEDROCK_GEYSER.md` | Bedrock/Geyser strategy. |
| `docs/06_PERFORMANCE_PARITY.md` | Parity profiles, optimisation backlog, settings. |
| `docs/07_APP.md` | Companion app spec. |
| `docs/08_PLUGIN_API.md` | Native plugin API principles. |
| `docs/09_TESTING_BENCHMARKS.md` | Tests, parity checks, benchmarks, capacity estimator. |
| `docs/10_LICENSING_LEGAL.md` | Licence conflict, EULA, trademark. |
| `docs/11_RESEARCH_TASKS.md` | Facts to verify in Phase 0. |
| `docs/12_OPEN_QUESTIONS.md` | Questions waiting for the user. |
| `config/name.properties.example` | Draft of the master settings file. |
| `examples/example-plugin/` | Draft of the plugin folder format (Rock, stairs, sword, painting). |

Package written 2026-11-20 against Minecraft Java Edition 26.3 / Java 25. Re-verify versions at the start of work.
