# PROGRESS LOG

Append-only. Newest entries at the bottom. Refresh the **Current status** block every session. Never rewrite old entries.

## Current status
- **Phase:** 0 and Phase 1 complete; Phase 2 P2-01, P2-07, P2-08 done; P2-02/P2-03 code exists and is hardened but NOT yet verified on a real server
- **Last completed:** P2-07 Java pack generator, HTTP host, and pack enforcement verified with the Paper 26.3 bot swarm (Entry 0013)
- **Next task:** run the P2-02/P2-03 verification in Entry 0014 on a machine with Paper 26.3 + Java 25 (build, boot, read the self-check log lines), then tick P2-02/P2-03 if they pass; then P2-04/P2-05 (blocks)
- **Blockers / waiting on user:** vanilla GUI client is not available in this session; Step 0 rendering check remains unverified. Continue using the real server and bot swarm for protocol/server verification; do not tick visual acceptance items without client evidence. Windows/macOS remain out of scope. The Claude session that wrote Entry 0014 had JDK 21 only and no Paper jars/network route, so no Java that touches Bukkit/Paper could be compiled there.
- **Mojang target:** Java Edition 26.3 / Java 25 (as of 2026-11-20 — verify in R-01)
- **Base:** Paper fork (DEFAULT, ADR-001)

## Entry template
```
### Entry NNNN — YYYY-MM-DD — <agent> — <short title>
- Roadmap items: <ids touched / completed>
- Changes: <what changed, files/modules>
- Verified by: <commands run + results>
- Benchmarks/parity: <numbers if relevant>
- Problems / surprises: <...>
- Decisions made/changed: <ADR ids>
- Questions for user: <ids in docs/12 or "none">
- Next step: <one concrete item>
```

---
### Entry 0001 — 2026-11-20 — Claude — Planning package created
- Roadmap items: none (planning only)
- Changes: created this handoff package (AGENTS.md, ROADMAP.md, docs/00–12, config + example plugin drafts). No product code written.
- Verified by: web research on current Minecraft version (Java 26.3, Java 25, year-based versioning) and Geyser custom items v2 / custom blocks status; claims are listed as VERIFY in docs.
- Problems / surprises: (1) user prefers a non-GPL licence but a Paper fork implies GPL-3 -> ADR-012 OPEN; (2) user's "manual" pack-merging answer looks inconsistent with the "easy" goals -> Q5; (3) painting variants vs `item_model` -> ADR-014 / Q8; (4) regionised threading is a very large effort -> kept as later experiment (ADR-003); (5) Geyser display-entity translation unconfirmed -> complex-block Bedrock support is a risk (docs/05).
- Decisions made/changed: ADR-001…014 recorded (see docs/02).
- Questions for user: docs/12 Q1–Q20.
- Next step: P0-01 repo skeleton, then P0-03 research tasks.

### Entry 0002 — 2026-11-20 — Claude — User answers round 1 recorded
- Roadmap items: none (planning only); P0-04 unblocked.
- Changes: docs/12 (answers moved to Answered, new Q21–Q25), docs/02 (ADR-009/012 decided, ADR-015–017 added), docs/04 (items vs paintings explanation), docs/11 (R-17–R-19).
- Verified by: n/a (documentation only).
- Problems / surprises: user wants Paper base without Paper's parity-breaking optimisations (ADR-015 / Q23); user-chosen own world format is the riskiest long-term item (Q25); NetherNet is a Bedrock transport topic, not a playit issue (R-17).
- Questions for user: Q21–Q25.
- Next step: P0-01 repo skeleton (can start now).

### Entry 0003 — 2026-11-20 — Claude — User answers round 2 recorded
- Roadmap items: none (planning only).
- Changes: docs/12 (Q21–Q25 answered), docs/02 (name FlowTail working name, ADR-007/009/014/015 decided), docs/06 (optimisation backlog), docs/11 (R-20 compression benchmark).
- Questions for user: none.
- Next step: P0-01 repo skeleton using working name FlowTail.

### Entry 0004 — 2026-11-20 — Claude — Phase 0 first pass
- Roadmap items: P0-01, P0-04, P0-07 done; P0-02 partial; P0-03 partial (10 of 20 research items filled); P0-05/P0-06 prepared but NOT run.
- Changes: repo skeleton (dirs with READMEs), versions.toml + gradle.properties, .editorconfig, .gitignore, CI placeholder, THIRD_PARTY.md, LICENSE (GPL-3 text from SPDX), NOTICE (with non-affiliation disclaimer), scripts/{setup,setup.ps1,build-upstream-paper.sh,run-upstream.sh,rename.sh}, docs/benchmarks/baseline.md template, docs/11 results, docs/05 NetherNet update, docs/02 notes.
- Verified by: `bash scripts/setup --check` correctly reports missing Java 25 (sandbox has Java 21); `scripts/rename.sh Printtail` on a copy updated versions.toml and gradle.properties; `bash -n` syntax check on build/run scripts. NOT verified: building Paper (sandbox has no JDK 25 and cannot reach Mojang's servers), Windows script, JDK download.
- Benchmarks/parity: none yet (baseline.md is a template).
- Problems / surprises: (1) Bedrock RakNet was cut off 27 Oct 2026; Bedrock now needs NetherNet (TCP signaling + UDP), probably the cause of the playit trouble (R-17); (2) Paper has no 26.3 branch/tag, `main` is 26.3; (3) Paper config merge planned for 26.4.
- Decisions made/changed: notes under ADR-001 and ADR-008 in docs/02.
- Questions for user: none.
- Next step: run `bash scripts/setup` then `bash scripts/build-upstream-paper.sh` on CachyOS (install JDK first: `sudo pacman -S jdk25-openjdk`), paste any errors into the next session.

### Entry 0005 — 2026-11-20 — Claude — Upstream Paper build verified on user's machine
- Roadmap items: P0-05 (build part verified; boot pending EULA), P0-03 (R-04, R-10 added).
- Changes: docs/11 (R-04 partial, R-10 done), docs/02 (ADR-011 refinement), docs/benchmarks/baseline.md (reference machine + btrfs note), ROADMAP note on P0-05.
- Verified by: user ran the scripts on CachyOS (Ryzen 7 7800X3D, 30 GiB, Java 25.0.5): `scripts/setup` ok; `applyPatches` BUILD SUCCESSFUL in 1m12s (946 source patches, 105 mache patches); `createPaperclipJar` BUILD SUCCESSFUL in 57s -> paper-paperclip-26.3.local-SNAPSHOT.jar (~60 MB); `run-upstream.sh` downloaded mojang_26.3.jar, applied patches, started Paper 26.3-DEV-HEAD@1eda666 on Java 25 and stopped at the EULA prompt as designed. Also confirms the Linux path of scripts/setup, build and run scripts.
- Problems / surprises: none. Note: the pinned commit builds as version string 26.3.local-SNAPSHOT.
- Next step: user accepts EULA, boots to 'Done', then P0-06 baseline (install Spark, record numbers) and the remaining research items.

### Entry 0006 — 2026-11-20 — Claude — P0-05 verified; baseline harness stub
- Roadmap items: P0-05 DONE; P0-06 harness written, numbers pending; R-03 done.
- Changes: scripts/baseline-upstream.sh (GC-selectable idle baseline using bundled Spark), docs/11 R-03, ROADMAP.
- Verified by: user ran run-upstream.sh on CachyOS: server booted `Done (4.500s)`, player Zergrj joined with a Minecraft 26.3 client at 127.0.0.1, `/stop` saved and shut down cleanly. Baseline script: `bash -n` only (cannot run Java 25 here).
- Observations: (1) a fresh upstream server had the whitelist ON (join refused until `/whitelist off`) — check `white-list` in generated server.properties and decide our default (a first-run wizard should ask); (2) Paper bundles Spark and logs libdeflate compression + OpenSSL cipher + 4 worker threads / 1 IO thread by default on this 16-thread CPU, so threads are NOT auto-scaled to cores — material for the hardware tuner (P1-04); (3) world data lands under `world/dimensions/...` as noted in R-02.
- Questions for user: none.
- Next step: user runs the baseline script for g1/zgc/shenandoah and pastes the SUMMARY lines; then record in docs/benchmarks/baseline.md.

### Entry 0007 — 2026-11-20 — Codex — Resumed at P0-01; setup improvements
- Roadmap items: P0-01 audited as present; P0-02 improved but still partial.
- Changes: rewrote `scripts/setup` to parse the Java target from `versions.toml`, check Git and Paper's Gradle wrapper, recognize the macOS JDK bundle path, and offer a private JDK download; rewrote `scripts/setup.ps1` to check these prerequisites and offer optional winget installs; clarified P0-02's remaining verification in `ROADMAP.md`.
- Verified by: `bash -n scripts/setup scripts/build-upstream-paper.sh scripts/run-upstream.sh` passed; `bash scripts/setup --check` passed and reported Git 2.56.1, Java 25+, and the Gradle wrapper in `upstream/Paper`.
- Benchmarks/parity: none. Three raw baseline logs are present, but benchmark results have not yet been analysed or entered into `docs/benchmarks/baseline.md`.
- Problems / surprises: Windows PowerShell, macOS, and the JDK download path could not be exercised here; P0-02 therefore remains unchecked.
- Decisions made/changed: none.
- Questions for user: none.
- Next step: verify `scripts/setup.ps1` on Windows and `scripts/setup` on macOS, then continue the remaining Phase 0 research and baseline work.

### Entry 0008 — 2026-11-21 — Codex — Phase 1 implemented and verified
- Roadmap items: P1-01 through P1-06 completed. Adjusted P0-02 scope to Linux only per user instruction; automatic JDK installation remains unverified.
- Changes: added a FlowTail Paper source patch, artifact build/rebase scripts and procedure; typed settings registry, commented config generator/validator, profile resolver with dual super-speed confirmation, hardware tuner, structured launch logging and log filters; MCProtocolLib 26.3 bot swarm plus Paper tick/GC/thread CPU/process CPU/RSS probe. Added Linux Phase 1 usage docs and recorded the MCProtocolLib MIT licence.
- Verified by: `scripts/setup --check` passed (Git, Python 3.14, Java 25, Gradle wrapper); Bash syntax checks and Python compile passed; profile checks confirmed balanced and super-speed effective flags differ while worldgen stays exact; `scripts/build-flowtail-server.sh` completed BUILD SUCCESSFUL and produced `build/FlowTail-server.jar`; `java -jar build/FlowTail-server.jar --version` printed the structured FlowTail startup banner and `26.3-DEV-1eda666`; benchmark probe compiled and loaded on a disposable server. Final 3-bot run logged in 3/3 and disconnected 3/3, sent movement, received 249 chunk packets, reported first chunk p50 30 ms / p99 42 ms, login p50 696 ms / p99 810 ms, and emitted server MSPT percentiles, GC, per-thread CPU, process CPU/RSS and client byte counters. The wrapper launch also produced `.flowtail.pid` and JSON console lines.
- Benchmarks/parity: harness smoke run only on a disposable, fresh world; observed p99 MSPT 177.448 ms during the short run. These numbers are not a performance comparison or Phase 0 upstream baseline.
- Problems / surprises: Paper's whitelist defaults on in the disposable run; it had to be disabled there before offline bots could join. Shell execution could not see a server PID started in another isolated process namespace, so the metrics plugin now samples RSS/CPU from inside the server process.
- Decisions made/changed: Linux-only Phase 1 setup scope per user's instruction; no ADR changes.
- Questions for user: none.
- Next step: resume P0-02 by verifying the optional JDK download into a temporary `.tools` directory, then continue P0-03 and P0-06.

### Entry 0009 — 2026-11-21 — Codex — Phase 0 completed
- Roadmap items: P0-02, P0-03, P0-06 completed; Phase 1 remains complete.
- Changes: fixed `scripts/setup` so an unset `JAVA_HOME` no longer resolves incorrectly to `/bin/java`; verified the optional Temurin JDK 25.0.5.1 download/extraction in a temporary checkout without system Java. Added `scripts/start-flowtail.sh` for one-command build and launch and taught `scripts/flowtail` to use the private JDK when installed. Filled sourced results/deferred checks for R-04…R-06, R-09, R-13…R-15, R-19; recorded ADR implications. Replaced the baseline template with the three upstream idle runs and explicit scope limits. The pasted user log independently confirms a successful boot and vanilla client join after EULA acceptance and whitelist adjustment.
- Verified by: `bash -n` on setup/build/run/start scripts passed; `bash scripts/setup --check` passed; private JDK download smoke test installed Java 25.0.5.1 and reported prerequisites ready; `GRADLE_USER_HOME=/tmp/flowtail-gradle bash scripts/start-flowtail.sh` completed Paper patch/build tasks, built `build/FlowTail-server.jar`, launched the FlowTail wrapper, logged `Done (4.904s)`, and stopped cleanly with exit code 0. `java -jar build/FlowTail-server.jar --version` printed `26.3-DEV-1eda666`. Upstream checkout was clean after the build. The smoke-test world/cache were removed; the existing explicitly accepted EULA file remains.
- Benchmarks/parity: upstream idle runs: G1 4.314 s / 2795 MiB; ZGC 4.317 s / 2232 MiB; generational Shenandoah 4.493 s / 2923 MiB. TPS remained 20 in all idle windows. These are single idle runs; no loaded-player or GC-pause comparison is claimed. Full notes and raw logs are in `docs/benchmarks/baseline.md`.
- Problems / surprises: first JDK smoke-test attempt exposed the empty-`JAVA_HOME` bug; fixed and retested. Build needed a writable temporary Gradle cache because the default home cache was read-only. Paper's JGit emitted a read-only warning for `/home/zace/.config/jgit/config`, but patching and both builds succeeded.
- Decisions made/changed: no user decisions changed; Windows/macOS remain out of scope. `docs/02_DECISIONS.md` records the P0 findings without changing ADR-016 or GC recommendations.
- Questions for user: none.
- Next step: P2-01 registry/bootstrap design and implementation.

### Entry 0011 — 2026-11-21 — Codex — Phase 2 Step 0 data audit
- Roadmap items: Step 0 data/server checks run; no Phase 2 roadmap items ticked.
- Changes: generated the 26.3 vanilla block report, inspected the generated pack and carrier data, and booted/stopped the Paper 26.3 server. Logged the mismatches before implementation as required.
- Verified by: `bash scripts/run-upstream.sh` reached `Done (3.788s)` and shut down cleanly; `bash scripts/gen-reports.sh` produced `build/reports/reports/blocks.json`; `python3 scripts/mc-pack-format.py run/upstream/cache/mojang_26.3.jar` reported resource pack 97.1, data pack 121.0, protocol 777; `bash scripts/build-content.sh` completed with 0 errors and no `W-CARRIER-UNVERIFIED`.
- Findings: generated `minecraft:note_block` states use `instrument`, `note` 0–24, and `powered` true/false, but the hard-coded fallback omits four 26.3 instruments (`trumpet`, `trumpet_exposed`, `trumpet_oxidized`, `trumpet_weathered`). `versions.toml` records only resource major 97, and the current pack generator writes integer `min_format`/`max_format`; 26.3 uses 97.1 and modern `min_format` accepts `[97, 1]` (the upper bound may be major `97`). These are concrete data-side fixes before relying on the pack. Generated pack: 13 entries, 6799 bytes, SHA-1 `c830a3f6ce402cdd79f7d60f282b7b03cef6d445`.
- Problems / surprises: the server initially could not bind its port while local networking was restricted; after local-network permission it booted successfully. No Minecraft GUI client is attached in this session, so pack rendering with `/setblock` and `/give` has not been observed. No ROADMAP item is marked verified from static pack inspection alone.
- Decisions made/changed: none. Step 0 visual verification remains pending; per docs/14, continue server-side work with actual server/bot protocol tests and keep visual claims unverified.
- Questions for user: none.
- Next step: correct pack format representation and fallback instrument list, regenerate the pack, then implement P2-01.

### Entry 0010 — 2026-11-21 — Codex — Source-only ZIP helper
- Roadmap items: maintenance helper; no roadmap item changed.
- Changes: added executable `scripts/package-source.py`, documented it in `scripts/README.md`, and added a git-ignore rule for generated `FlowTail-source*.zip` files. The script includes source, docs, and example assets while excluding dependency/runtime/build directories, JARs, class files, Python bytecode, and existing ZIPs.
- Verified by: `./scripts/package-source.py` created `FlowTail-source.zip`; Python's ZIP integrity check passed; archive inspection found 78 entries and zero dependency/build/runtime artifacts.
- Benchmarks/parity: archive is 116 KB (about 0.1 MiB) versus roughly 1.9 GiB for the working folder.
- Problems / surprises: `zip` command is not installed, so the helper uses Python's built-in `zipfile` module and needs no added dependency.
- Decisions made/changed: none.
- Questions for user: none.
- Next step: P2-01 registry/bootstrap design and implementation.

### Entry 0011 — 2026-11-21 — Claude — Phase 2 data side (compiler + pack generator) and server-side design
- Roadmap items: P2-08 done; P2-07 data side done (server host/push/hold open); P2-09 compile+pack verified, in-game open. P2-01..06, 10 NOT started (need Paper + Java 25 build/run, unavailable to this author).
- Changes: `compiler/` (ftc package: position-aware JSON parser, schema v0 validation with file:line + fix hints, `.ftplugin` builder, pack merger with persistent append-only carrier allocation, CLI `flowtailc.py`), 13 unit tests, `scripts/{build-content.sh,gen-reports.sh,mc-pack-format.py}`, `docs/13_PHASE2_DESIGN.md`, `docs/14_PHASE2_CODEX_TASKS.md` (Codex task list + paste-in prompt), ADR-018..020 (docs/02), `resource_pack_format = 97` in versions.toml (UNVERIFIED), .gitignore `content-state/`.
- Verified by: `python3 -m unittest discover -s compiler/tests` -> 13 passed; `bash scripts/build-content.sh` -> example plugin builds, pack.zip with 13 entries, deterministic sha1 across two runs; `mc-pack-format.py` on a synthetic jar; `bash -n` on new scripts. Paper API names in docs/13 §4 were read from the Paper source at commit 1eda666 (grep), not compiled.
- Benchmarks/parity: none. No server code changed.
- Problems / surprises: (1) the user's uploaded source shows Codex had already done Phase 1 in Python (parity/flowtail.py); Claude's unfinished Java parity module was discarded to avoid two implementations. (2) Pack format for 26.3 comes from one third-party wiki (97); Mojang's jar is the authority, hence `mc-pack-format.py`. (3) Using note blocks as carriers means vanilla note blocks must be disabled in those worlds and physics cancelled for tracked blocks; documented limits in docs/13 §3. (4) `custom_data` is not a constant in Paper's `DataComponentTypes`; plan uses the item PDC.
- Decisions made/changed: ADR-018 (proposed), ADR-019, ADR-020.
- Questions for user: ADR-018 veto (Python reference + Java port vs all-Java compiler); otherwise none.
- Next step: Codex does docs/14 Step 0, then P2-01.

### Entry 0012 — 2026-11-21 — Codex — Step 0 corrections and P2-01 bootstrap
- Roadmap items: Step 0 completed; P2-01 verified and ticked. P2-07 remains next.
- Changes: Step 0 found the real 26.3 note-block has 27 instruments (four missing from fallback) and resource-pack version is `[97, 1]`. Updated the fallback, pack format parsing/metadata, tests, and Step 0 documentation. Added `content-engine/`, a Java 25 Paper plugin module that validates the compiler registry during bootstrap, registers painting variants through Paper's compose API, and logs content counts. `scripts/build-flowtail-server.sh` embeds it in the server jar; `scripts/flowtail launch` extracts it into the Paper plugin folder. `scripts/start-flowtail.sh` stages the current content registry.
- Verified by: `python3 -m unittest discover -s compiler/tests -v` -> 15 passed; `bash scripts/build-content.sh` -> example pack built with exact `[97, 1]` metadata and no carrier warning; `GRADLE_USER_HOME=/tmp/flowtail-gradle bash scripts/build-flowtail-server.sh` -> Paper and content-engine builds succeeded, embedded jar present; isolated Paper 26.3 / Java 25 server reached `Done`, loaded registry with `plugins=1 blocks=1 items=2 paintings=1`, registered `example:sunset`, and enabled plugin; `gradlew -p tools/bench run --args="--host 127.0.0.1 --port 25566 --bots 1 --duration-seconds 15 --idle"` -> bot logged in, received 83 chunk packets, 1,477 ms login p95.
- Benchmarks/parity: Bot stayed connected for the 15-second scenario; no movement packets (`--idle`).
- Problems / surprises: Paper 26.3 has no custom BLOCK or ITEM registry compose events; those definitions are virtual FlowTail entries backed by carrier/base items. The tested launcher doesn't expose server console stdin to its wrapper, so the isolated test server remains running on loopback port 25566; it is bound only to `127.0.0.1` and offline mode. No GUI client was available, so no visual acceptance is claimed.
- Decisions made/changed: corrected `docs/13` §4 to distinguish virtual block/item definitions from actual painting registry registration.
- Questions for user: none.
- Next step: P2-07, beginning with golden vectors for the Java pack generator.

### Entry 0013 — 2026-11-21 — Codex — P2-07 Java pack generator and delivery
- Roadmap items: P2-07 verified and ticked; next is P2-02.
- Changes: added checked-in Python-generated golden vectors (`compiler/tests/golden/`) and Java `PackGenerator` port with persistent allocations, ZIP asset merging, full note-block variant list, and deterministic JSON entry output. Added embedded Java HTTP host with SHA-1 verification, auto LAN URL, resource-pack delivery and required-pack movement hold. Added kick and low-chunk limbo decline actions, and `pack.required=false` optional delivery. Added FlowTail config keys and copied plugin archives/settings/state during `start-flowtail.sh`. Updated the bot swarm to download/verify a pack or decline it.
- Verified by: `./upstream/Paper/gradlew -p content-engine clean test jar` -> registry, allocations, and SHA-256 of every generated ZIP entry matched golden vectors; `./upstream/Paper/gradlew -p tools/bench classes` succeeded; `bash scripts/build-flowtail-server.sh` built and embedded the plugin; isolated Paper 26.3 / Java 25 server booted with generated content, `curl http://127.0.0.1:18081/pack.zip` matched server pack SHA-1 and ZIP integrity; bot swarm with accept loaded 1 pack; decline with `kick` logged the kick; decline with `limbo-lag` logged transition to low-chunk limbo; with `pack.required=false`, bot loaded the optional pack and server logged movement allowed (bot sent 80 movement packets).
- Benchmarks/parity: 1 bot, 8-second optional-pack case: 80 movement packets, 83 chunk packets, one resource pack loaded, no plugin errors. Pack declines produced the expected server log and configured response.
- Problems / surprises: Paper's configuration events don't expose a way to hold the connection until a resource-pack status arrives; used the specified join-time fallback and freeze. ADR-016's implementation note disallows intentionally delaying keep-alives, so `limbo-lag` is implemented as a low-chunk, invisible, invulnerable limbo. The offline test server was isolated to loopback and shut down cleanly after each run. Vanilla GUI visuals remain unverified.
- Decisions made/changed: corrected `docs/13`'s P2-07 transport description to say join-time fallback for the hold.
- Questions for user: none.
- Next step: P2-02 item prototypes and reverse mapping; keep testing against the real Paper server.

### Entry 0014 — 2026-11-21 — Claude — P2-02/P2-03 review and hardening (NOT verified on a server)
- Roadmap items: P2-02 and P2-03 touched; **neither ticked**. (Housekeeping: this log has two entries numbered 0011 — Codex's Step 0 audit and Claude's Phase 2 data side. Old entries are not rewritten; numbering continues at 0014.)
- State found: `ItemPrototypes`, `ItemCommandListener`, `CreativeItemListener`, `ContentRuntime` and the plugin wiring were already in the source (file times after Entry 0013) but were never logged, tested or ticked.
- Defects found by reading the code against `compiler/ftc/build.py` and the registry: (1) the compiler writes `stack_size: 64` for every item (`entry.setdefault`), and `ItemPrototypes.apply` forced that onto `example:ruby_sword` (base `iron_sword`, max stack 1, damageable) — swords would have stacked to 64, and I believe vanilla rejects "damageable and stackable" components (from memory, not checked); (2) `expand()` built a brand-new stack, so `CreativeItemListener` would have stripped enchantments, damage, lore and other plugins' PDC every time a creative player moved a custom item.
- Changes: new `StackSizePolicy` (pure logic, no Bukkit types: damageable base keeps its own max; compiler default 64 is silently ignored for such bases; explicit other values are ignored with a one-time warning naming plugin_id/content_id) + `StackSizePolicyTest`; `ItemPrototypes` now uses it, adds `maxStackSize(id)`, and `expand()` clones the given stack and re-applies only the prototype-owned fields (also clears a stale `max_stack_size` component left by the old behaviour); the plugin's boot self-check now also asserts the real stack size matches the policy and that `expand()` keeps lore and a foreign PDC key, failing enable loudly if not.
- Verified by: the policy class compiled with JDK 21's `jdk.compiler` module and ran through a 6-case harness mirroring the JUnit test — all passed (sword/compiler-default → max 1 no override no warning; explicit 16 on damageable → ignored + warning; paper 16 → override; paper 64 → no component; 0 and 100 rejected); `python3 -m unittest discover -s compiler/tests` → 15 passed; syntax-only javac pass over the changed Bukkit-dependent files showed only missing-dependency errors (no Paper jars here), no syntax errors. **NOT verified:** compiling against Paper (`ItemMeta#hasMaxStackSize`, `setMaxStackSize(null)`, `Material#getMaxDurability` are used from memory of the Bukkit API — compile first), the JUnit run, boot, `/give`, creative round-trip, or any client rendering.
- Not changed, but needs a decision/verification (found while reading): (a) `ItemCommandListener` replaces vanilla `/give` tab completions (player names, `minecraft:` items) with only `@s` + custom ids, and registers both `TabCompleteEvent` and `AsyncTabCompleteEvent`; whether each fires for players, and whether to merge instead of replace, must be checked on the server; (b) `/give` is handled only for players via `PlayerCommandPreprocessEvent` — not console, command blocks or `/execute run give`; (c) block items (`example:rock`) are not in `registry.items()`, so `/give @s example:rock` does not work until P2-04/P2-05; (d) no Paper API for a creative tab was found — P2-03's "creative tab" clause is unmet and unverified; (e) follow-up for the compiler: stop emitting the implicit `stack_size: 64` for damageable templates — this changes golden `registry.json` and the Java `PackGenerator` port together, so it was left alone.
- Decisions made/changed: none (no ADR changes).
- Questions for user: none.
- Next step: on the Paper machine run `GRADLE_USER_HOME=/tmp/flowtail-gradle ./upstream/Paper/gradlew -p content-engine clean test jar`, then `bash scripts/build-flowtail-server.sh` and boot. Expect log lines `Verified item prototype ... content_id=example:ruby` and `...example:ruby_sword` and no `expand() dropped data` / `wrong max stack size` error and no stack-size warning for the sword. Then in-game (or bot): `/give @s example:ruby_sword` must give one sword whose max stack size is 1, `/give @s example:ruby_sword 2` is currently *refused* with "count must be between 1 and 1" (vanilla `/give` would hand out two separate swords — known deviation, splitting a count across stacks is a small follow-up), `/give @s example:ruby 64` must work, and drop/pickup/chest/relog must log the `flowtail:id` lines. Tick P2-02 when those pass; tick P2-03 only when a client also shows the ruby icon.

