"""Carrier pools: which vanilla block states a custom block may borrow on the client (docs/03, ADR-004, R-06).

Phase 2 MVP pool: `cube.note_block`. KNOWN LIMITS (documented in docs/13):
 - vanilla note blocks must be disabled in worlds that use this pool (their states ARE our carriers);
 - the property lists below are a FALLBACK. Pass a data-generator report (`--blocks-report blocks.json`, produced by
   scripts/gen-reports.sh from the real 26.3 server) and the compiler uses the exact states instead.
Pools are allowlists: nothing is ever picked just because it looks rare (R-06).
"""
from __future__ import annotations
import itertools
import json
from pathlib import Path

FALLBACK_NOTE_BLOCK = {
    "instrument": ["harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone",
                   "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling", "trumpet",
                   "trumpet_exposed", "trumpet_oxidized", "trumpet_weathered", "zombie", "skeleton", "creeper",
                   "dragon", "wither_skeleton", "piglin", "custom_head"],
    "note": [str(i) for i in range(25)],
    "powered": ["true", "false"],
}
DEFAULT_STATE_KEY = "instrument=harp,note=0,powered=false"   # the state vanilla places; never allocated


def key_of(props: dict) -> str:
    return ",".join(f"{k}={props[k]}" for k in sorted(props))


def load_properties(block: str, report_path: Path | None) -> tuple[dict, bool]:
    """Returns (ordered property->values, verified_from_report)."""
    if report_path:
        data = json.loads(Path(report_path).read_text(encoding="utf-8"))
        entry = data.get(block)
        if entry is None:
            raise SystemExit(f"error: {block} is not in {report_path}. Is this the right blocks.json from scripts/gen-reports.sh?")
        return {k: [str(x) for x in v] for k, v in entry["properties"].items()}, True
    if block == "minecraft:note_block":
        return FALLBACK_NOTE_BLOCK, False
    raise SystemExit(f"error: no built-in property list for {block}; pass --blocks-report")


def all_states(props: dict) -> list[dict]:
    names = sorted(props)
    return [dict(zip(names, combo)) for combo in itertools.product(*(props[n] for n in names))]


def pool_slots(props: dict) -> list[dict]:
    """Deterministic allocation order: sorted property names, values in declared order, default state removed."""
    names = sorted(props)
    out = []
    for combo in itertools.product(*(props[n] for n in names)):
        st = dict(zip(names, combo))
        if key_of(st) == DEFAULT_STATE_KEY:
            continue
        out.append(st)
    return out
