# Third-party software and licences
Log every dependency/idea source here (AGENTS.md rule 6).
| Component | Use | Licence | Notes |
|---|---|---|---|
| PaperMC/Paper | upstream base, fetched at build time, patched | GPL-3.0 (some patches MIT/other per header) | Not committed. paperweight is MIT. |
| Mojang server jar | downloaded by the user's Paperclip on first run | Minecraft EULA | Never redistributed. User must accept the EULA. |
| Geyser / Floodgate | Bedrock support (Phase 7) | MIT (VERIFY) | Used as a dependency, not copied. |
| ViaVersion / ViaBackwards / ViaRewind | old-client support | GPL-3.0-or-later | Optional runtime plugins. |
| Linear region format (xymb-endcrystalme) | prior art for world format research only | MIT (tools/mod; VERIFY per repo) | Ideas only until a licence review is done. |
| Zstandard | candidate compression | BSD/GPLv2 dual (VERIFY JVM binding licence) | R-20. |
| GeyserMC/MCProtocolLib | Java 26.3 protocol client for isolated benchmark bots | MIT | Phase 1 `tools/bench`; dependency pinned to `26.3-4`, fetched at build time. |
