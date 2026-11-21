# src/

"Regular plugin structure" lives here:

- `main/java/...` or `main/kotlin/...` — behaviours referenced from JSON, e.g. `example:ruby_burn`
- `datapack/data/<namespace>/...` — recipes, loot tables, tags, advancements, functions

Example behaviour (sketch only — the real API is designed in Phase 2/3, see docs/08):

    @Behavior("example:ruby_burn")
    public void rubyBurn(ItemHitEvent e) { e.target().ignite(60); }
