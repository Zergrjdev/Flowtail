# 10 — Licensing & legal (NOT legal advice — verify with official sources / a lawyer before release)

## The licence conflict (OPEN — Q2)
- User wants **open source** and **prefers a non-GPL licence if possible**.
- A server derived from Paper/Spigot/CraftBukkit is built from GPL-3 code and patches; assume the combined work must be GPL-3-compatible. (VERIFY exactly which Paper parts are under which licences — see doc 11 R-07.)
- Options:
  | Option | Pros | Cons |
  |---|---|---|
  | A. Paper fork, GPL-3 | fastest, inherits parity + ecosystem | copyleft; cannot go permissive |
  | B. Permissive licence, non-Paper base (e.g. Minestom-style or a clean patch set on vanilla with a loader) | licence freedom | vanilla parity must be rebuilt — huge cost |
  | C. Split: GPL-3 server fork + **permissively-licensed** plugin API/compiler/app/tools (where legally clean) | plugin authors/app not forced GPL | needs careful boundaries; verify API licence situation |
- Assistant recommendation: **A or C**. Decide with the user before P0-04.
- Third-party code reuse (optimisation mods, etc.): copy only if the licence is compatible; otherwise reimplement from the idea. Log everything in `THIRD_PARTY.md`.

## Mojang / Microsoft
- **Do not redistribute Mojang's server jar, decompiled/deobfuscated sources, or assets.** Use build-time download + patch (Paper-style).
- **EULA:** the end user accepts it; the app/installer must show it and require explicit user action.
- **Branding:** do not use "Minecraft" in the product name/domain/logo; use a descriptive "for Minecraft"-style phrase only as allowed by Mojang's brand and commercial guidelines (VERIFY current text).
- **Resource packs/textures** shipped in examples must be original (placeholders only).

## Resource-pack enforcement (RP-02) — honest limits
The vanilla protocol trusts the client: a "required" pack makes a vanilla client disconnect if it declines, but modified clients can lie about pack status.
Best-effort plan: send the pack during the **configuration phase**, hold the player until a SUCCESSFULLY_LOADED status, kick on decline/failed/timeouts, add retry + clear messages.
An optional **verification probe** (checking the client actually resolves a pack-provided key) is possible but is client fingerprinting with privacy implications and must be an
explicit, documented, opt-in setting. Ask the user before building it (Q15). Never collect more than the pass/fail result.

## Plugin ecosystem
Public community plugins: define a plugin licence guideline, malware/abuse policy for any official listing, and a "content pack" permissions model (plugins can't touch the filesystem outside their folder by default — evaluate).
