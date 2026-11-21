# AGENTS.md — read this first

**Project:** a heavily optimised, vanilla-behaviour Minecraft Java server software with first-class custom
blocks / items / painting variants for plugin developers, Geyser (Bedrock) support designed in from day one,
and a companion app (setup wizard, console, no-code plugin maker, code editor, world converter).
The product name is **undecided**. In docs it is written `{name}`. Keep the name in ONE place
(`versions.toml` / `gradle.properties` -> `softwareName`) so renaming is trivial.

## Read order (every session, no exceptions)
1. `docs/00_CONTEXT.md` — vision, user profile, facts, glossary.
2. `PROGRESS.md` — the "Current status" block at the top + the last 3 log entries.
3. `ROADMAP.md` — find the first unchecked item in the current phase.
4. `docs/02_DECISIONS.md` — before touching architecture.
5. Other `docs/` files only as needed for the task at hand.

## Operating rules
1. **One roadmap item at a time.** Tick `[x]` in `ROADMAP.md` only when its acceptance criteria are met AND verified
   (run it, don't assume).
2. **Log every session.** Append an entry to `PROGRESS.md` (template inside) and refresh its "Current status" block.
   Include: what changed, how it was verified (commands + result), problems, next step. Never rewrite old entries.
3. **Decisions have a status** (see `docs/02_DECISIONS.md`): `DECIDED` (user said so — never change silently),
   `DEFAULT` (assistant recommendation the user delegated — may change with a new ADR entry + progress note),
   `OPEN` (do not guess — add to `docs/12_OPEN_QUESTIONS.md` and ask).
4. **Asking the user:** batch questions in this exact format and nothing fancier:
   `1 QUESTION, A (answer) B (answer) C (answer)`. The user replies like `1a, 2b, 3c`.
5. **The user is not a programmer** (but can self-host Minecraft servers). Provide copy-paste commands, helper scripts
   in `scripts/`, plain-English explanations, and a way to *see* that each feature works. Check that tools (JDK, git,
   Gradle) exist before assuming; tell the user how to install them.
6. **Legal:** never commit or redistribute Mojang code, decompiled sources, or assets. Use build-time download + patching
   (see `docs/10_LICENSING_LEGAL.md`). Do not put "Minecraft" in the product name. Track every third-party licence in
   `THIRD_PARTY.md`. Never auto-accept the Mojang EULA on the user's behalf — the user must do it explicitly.
7. **VERIFY items** in the docs are claims not yet confirmed. Phase 0 must confirm them from official/primary sources and
   record results in `docs/11_RESEARCH_TASKS.md` before building on them. Minecraft changes several times a year.
8. **Single source of truth for versions:** target Minecraft version + Java version live in one file only.
9. **Every optimisation sits behind a setting and a risk class (R0–R3)** per `docs/06_PERFORMANCE_PARITY.md`, with a test
   or benchmark proving the gain and (for R0/R1) proving parity.
10. **Keep patches small and isolated** so rebasing onto each new Mojang drop takes days, not weeks (user wants updates
    within ~1–2 weeks of a release).
11. **Do not invent upstream APIs.** Read the upstream source/docs. When unsure, write a research note, don't guess.
12. **Quality gate before ticking a phase:** builds from clean checkout, tests pass, benchmark has no unexplained regression,
    docs for the feature exist in-code (full user docs come in Phase 9).

## Hard invariants (never break)
- World generation is identical to vanilla for every seed (golden tests). *(Super-speed exception pending open question Q17.)*
- No data loss: world-format changes are lossless, crash-safe, and always have a converter + backup path.
- Removing a plugin must never corrupt or crash a world: unknown custom content is preserved or replaced by placeholders.
- Plugin authors never write Bedrock-specific code; Bedrock output is generated.
- Players on a vanilla client + the generated resource pack get full custom content (no client mod required).

## Starter prompt for the user
> Read AGENTS.md, then continue the roadmap. Log progress in PROGRESS.md.
