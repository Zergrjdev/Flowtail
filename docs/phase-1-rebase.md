# FlowTail Paper update procedure

The FlowTail fork is maintained as a small patch series in `server/patches/`. The pinned Paper commit is the only upstream-version value in `versions.toml`.

1. Find the Paper commit selected for the target Minecraft release and review its build instructions and licence notices.
2. From this folder run `scripts/rebase-flowtail.sh <40-character-commit>`.
3. If the patch conflicts, edit the patch against the new Paper source and verify the affected startup path. Keep changes in `server/patches/`, not as untracked edits under `upstream/Paper`.
4. Run `scripts/build-flowtail-server.sh`. It applies Paper's patches, applies the FlowTail patch, builds the Paperclip jar, and writes `build/FlowTail-server.jar`.
5. Boot a disposable world, check the startup JSON banner and `Done`, then join with a vanilla client before updating the pin in a release.

The update is intentionally stopped when the FlowTail patch no longer applies. It does not silently drop a patch or accept Mojang's EULA. Upstream source and Mojang's server jar are fetched during build and are not committed.
