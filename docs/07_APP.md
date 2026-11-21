# 07 — Companion app

Architecture (ADR-007, DEFAULT): **manager daemon** (HTTP + WebSocket, wraps the server process, reads/writes configs, runs the compiler) + **web UI** (TypeScript, Monaco editor)
+ **desktop shell** (Electron preferred). The UI also works in a browser for remote hosts. Target OS: Windows + macOS + Linux (confirm Q19).

## Screens
1. **Splash / loading screen** — version, update check, environment checks (JDK, ports, disk).
2. **Home** — list servers (folders), start/stop, status, quick links.
3. **Setup wizard (new server)**
   1. **Hardware menu (comes BEFORE settings):** detect RAM/CPU; user can override RAM + CPU budget; expected max players; upload Mbps; Bedrock on/off.
      Tuner proposes JVM flags, GC, threads, distances, threading mode, and shows **recommended max players** (+ confidence, bottleneck: CPU / RAM / bandwidth).
   2. **Parity slider:** `Efficiency ◄──► Parity` with named stops (vanilla / balanced / performance), plus a separate **SUPER SPEED** toggle (warning + explicit confirm). "Custom" opens every toggle.
   3. **Normal `server.properties`** form (all vanilla options, with descriptions).
   4. **Connectivity helper** (below).
   5. **Plugins & Bedrock** (select plugins, enable Geyser/Floodgate).
   6. **Review -> Build server** ("jar maker"): creates the server folder, `{name}.properties`, start scripts with JVM flags, optional Geyser. The Mojang **EULA is shown and must be accepted by the user** — never auto-accepted.
4. **Settings editor** — pick any existing server folder; edit `server.properties`, `{name}.properties`, per-plugin configs; validation; diff view; "restart required" markers.
5. **Console** (SquidServers-like): live log, command box with autocomplete, **filter: All / Errors / Warnings / Errors + Warnings**, search, per-plugin colouring and filtering, click an error to open the **Problems panel**
   (plain-English cause, file/line, suggested fix, copy-report button). Every config reachable from here.
6. **Plugin maker (no code)** — forms to create blocks/items/paintings: drag textures in, pick template (cube/slab/stairs/trapdoor/shelf/plant/model), hitbox editor, mining/tool tiers,
   sounds/particles, states, recipes; live preview; simple triggers/actions ("when right-clicked -> run function"); produces a content project folder (doc 04).
7. **Code editor** — Monaco with Java/Kotlin/JSON + JSON-schema validation, API autocompletion, build button, error list linked to lines, template snippets.
8. **Build screen** — runs the compiler; shows jar + packs + (Phase 7) Bedrock export report (exact vs approximated content).
9. **World converter** — our format ⇄ vanilla; backup-first; progress; verification report.
10. **Profiler** — per-plugin and per-content-type ticks/packets/memory; Spark-style sampling; capacity estimate vs live load.
11. **Docs** — built-in searchable docs (written in Phase 9).

## Error visibility (user priority)
Every error/warning has: level, source (core / plugin id / content id / file:line), plain-English message, suggested fix. Console + Problems panel + a status badge on the server card.
Build errors from the compiler use the same format.

## Connectivity helper (pain point: laggy playit tunnel; Bedrock UDP hard to set up)
Goals: get a home-hosted server reachable with the least latency, for Java (TCP 25565) and Bedrock (UDP 19132).
- Detect CGNAT (router WAN IP vs public IP), UPnP/NAT-PMP availability, IPv6 availability.
- Ranked options: direct port-forward (try UPnP; else step-by-step guide) > IPv6 > cheap VPS + frp/WireGuard relay (generated scripts/config) > hosted tunnel services (warn about latency).
- Separate **UDP** reachability test for Bedrock; clear pass/fail with fixes.
- Optional latency estimate and bandwidth estimate (feeds the capacity estimator).
- External reachability checks need a service (project-hosted or third-party): decide in Phase 5 (OPEN, doc 12).

## Auto-tune details
See doc 06 & 09. The tuner is a library (`parity/`) so both the app and a CLI (`{name}-tune`) use identical logic.
