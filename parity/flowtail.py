#!/usr/bin/env python3
"""FlowTail Phase 1 settings and hardware tuning CLI (Python 3.10+, stdlib only)."""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import subprocess
import time
import zipfile
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "config" / "flowtail.properties"
LOG_STRUCTURED = True


@dataclass(frozen=True)
class Setting:
    key: str
    kind: str
    default: Any
    risk: str
    restart: bool
    description: str
    choices: tuple[str, ...] = ()


SETTINGS = [
    Setting("parity-profile", "choice", "balanced", "R0", True, "Behaviour and optimisation profile", ("vanilla", "balanced", "performance", "super-speed", "custom")),
    Setting("super-speed-confirm", "bool", False, "R3", True, "Explicit opt-in safety switch"),
    Setting("threading.mode", "choice", "offload", "R0", True, "Threading strategy", ("offload", "auto", "regionised")),
    Setting("threading.worker-threads", "int-auto", "auto", "R0", True, "Background worker thread count"),
    Setting("hardware.ram-mb", "int-auto", "auto", "R0", True, "Memory available to the server"),
    Setting("hardware.cpu-threads", "int-auto", "auto", "R0", True, "Logical CPU thread count"),
    Setting("hardware.upload-mbps", "float-auto", "auto", "R0", True, "Internet upload speed"),
    Setting("hardware.expected-max-players", "int-auto", "auto", "R0", True, "Expected concurrent players"),
    Setting("hardware.gc", "choice", "auto", "R0", True, "Java garbage collector", ("auto", "g1", "zgc", "shenandoah")),
    Setting("world.format", "choice", "vanilla", "R0", True, "World storage format", ("vanilla",)),
    Setting("worldgen.parity", "choice", "exact", "R0", True, "World generation parity (locked)", ("exact",)),
    Setting("content.note-block.vanilla", "choice", "disabled", "R0", True, "Vanilla note blocks are reserved as custom block carriers", ("disabled", "enabled")),
    Setting("pack.host.enabled", "bool", True, "R0", True, "Serve the generated resource pack from the built-in HTTP host"),
    Setting("pack.host.port", "int", 8080, "R0", True, "HTTP port used for resource pack delivery"),
    Setting("pack.host.public-url", "string", "auto", "R0", True, "Public resource pack URL or auto-detected LAN address"),
    Setting("pack.required", "bool", True, "R0", True, "Require players to load the resource pack before playing"),
    Setting("pack.decline-action", "choice", "kick", "R0", True, "Action when a player declines or cannot load the pack", ("kick", "limbo-lag")),
    Setting("opt.chunk.packet-cache", "bool", True, "R0", True, "Cache identical chunk packets"),
    Setting("opt.io.async", "bool", True, "R0", True, "Use Paper asynchronous chunk IO"),
    Setting("opt.net.packet-batching", "bool", True, "R0", True, "Batch compatible network packets"),
    Setting("opt.tracker.spatial-index", "bool", True, "R0", True, "Use spatial entity tracking"),
    Setting("opt.hopper.event-driven", "bool", False, "R1", True, "Event-driven hopper checks"),
    Setting("opt.mob.activation-ranges", "choice", "off", "R2", True, "Reduce distant mob activity", ("off", "conservative", "on", "aggressive")),
    Setting("opt.mob.ai-throttle", "choice", "off", "R2", True, "Throttle distant mob AI", ("off", "on", "aggressive")),
    Setting("opt.view-distance.dynamic", "bool", False, "R2", True, "Reduce view distance under load"),
    Setting("opt.light.deferred", "bool", False, "R2", True, "Defer lighting work"),
    Setting("opt.random-tick.budget", "bool", False, "R3", True, "Budget random ticks (changes behaviour)"),
    Setting("opt.entity.far-tick-skip", "bool", False, "R3", True, "Skip distant entity ticks"),
    Setting("opt.physics.simplified", "bool", False, "R3", True, "Simplify physics (changes behaviour)"),
    Setting("log.structured", "bool", True, "R0", False, "Emit newline-delimited structured logs"),
    Setting("log.console.default-filter", "choice", "all", "R0", False, "Console message filter", ("all", "errors", "warnings", "errors+warnings")),
]
BY_KEY = {s.key: s for s in SETTINGS}

PROFILE_DEFAULTS: dict[str, dict[str, Any]] = {
    "vanilla": {"opt.hopper.event-driven": False, "opt.mob.activation-ranges": "off", "opt.mob.ai-throttle": "off", "opt.view-distance.dynamic": False, "opt.light.deferred": False, "opt.random-tick.budget": False, "opt.entity.far-tick-skip": False, "opt.physics.simplified": False},
    "balanced": {"opt.hopper.event-driven": True, "opt.mob.activation-ranges": "conservative", "opt.mob.ai-throttle": "off", "opt.view-distance.dynamic": True, "opt.light.deferred": False, "opt.random-tick.budget": False, "opt.entity.far-tick-skip": False, "opt.physics.simplified": False},
    "performance": {"opt.hopper.event-driven": True, "opt.mob.activation-ranges": "on", "opt.mob.ai-throttle": "on", "opt.view-distance.dynamic": True, "opt.light.deferred": True, "opt.random-tick.budget": False, "opt.entity.far-tick-skip": False, "opt.physics.simplified": False},
    "super-speed": {"opt.hopper.event-driven": True, "opt.mob.activation-ranges": "aggressive", "opt.mob.ai-throttle": "aggressive", "opt.view-distance.dynamic": True, "opt.light.deferred": True, "opt.random-tick.budget": True, "opt.entity.far-tick-skip": True, "opt.physics.simplified": True},
    "custom": {},
}


def parse_value(setting: Setting, raw: str) -> Any:
    value = raw.strip()
    if setting.kind == "bool":
        if value.lower() in ("true", "yes", "1"): return True
        if value.lower() in ("false", "no", "0"): return False
        raise ValueError("expected true or false")
    if setting.kind in ("int", "int-auto"):
        if setting.kind == "int-auto" and value.lower() == "auto": return "auto"
        number = int(value)
        if number <= 0: raise ValueError("must be greater than zero")
        return number
    if setting.kind == "float-auto":
        if value.lower() == "auto": return "auto"
        number = float(value)
        if not math.isfinite(number) or number <= 0: raise ValueError("must be a finite number greater than zero")
        return number
    if setting.kind == "choice":
        if value not in setting.choices: raise ValueError("expected one of: " + ", ".join(setting.choices))
        return value
    return value


def read_config(path: Path) -> tuple[dict[str, Any], list[str], list[str]]:
    values = {s.key: s.default for s in SETTINGS}
    errors: list[str] = []
    warnings: list[str] = []
    if path.exists():
        for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            stripped = line.strip()
            if not stripped or stripped.startswith("#"): continue
            if "=" not in line:
                errors.append(f"{path}:{line_no}: expected key=value"); continue
            key, raw = line.split("=", 1)
            key = key.strip()
            raw = raw.split("#", 1)[0].strip()
            if key not in BY_KEY:
                warnings.append(f"{path}:{line_no}: unknown setting '{key}' (ignored)"); continue
            try: values[key] = parse_value(BY_KEY[key], raw)
            except (ValueError, OverflowError) as exc: errors.append(f"{path}:{line_no}: {key}: {exc}")
    profile = values["parity-profile"]
    for key, value in PROFILE_DEFAULTS[profile].items():
        values[key] = value
    if profile == "super-speed" and not values["super-speed-confirm"]:
        errors.append("parity-profile=super-speed requires super-speed-confirm=true")
    if values["threading.mode"] == "regionised":
        warnings.append("threading.mode=regionised is reserved for a future experimental build; using offload")
        values["threading.mode"] = "offload"
    return values, errors, warnings


def write_example(path: Path) -> None:
    lines = ["# FlowTail settings. Generated from the typed registry.", "# Profiles: vanilla, balanced, performance, super-speed, custom.", "# R0 = behaviour-identical; R1 = tiny parity risk; R2 = small deviations; R3 = large deviations.", ""]
    for setting in SETTINGS:
        lines += [f"# {setting.description} | risk {setting.risk} | restart required: {'yes' if setting.restart else 'no'}"]
        lines += [f"{setting.key}={str(setting.default).lower() if isinstance(setting.default, bool) else setting.default}", ""]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def effective(values: dict[str, Any]) -> dict[str, Any]:
    result = dict(values)
    # Profile choices become explicit effective flags for the process wrapper.
    for key, setting in BY_KEY.items():
        if key.startswith("opt.") and key in result:
            result[f"effective.{key}"] = result[key]
    return result


def tune(ram_mb: int, cpu_threads: int, upload_mbps: float, players: int, bedrock: bool, gc: str) -> dict[str, Any]:
    if ram_mb < 1024 or cpu_threads < 1 or upload_mbps <= 0 or players < 1:
        raise ValueError("RAM must be at least 1024 MB, CPU and players positive, and upload above zero")
    heap_mb = min(int(ram_mb * 0.70), max(1024, 1024 + players * 160))
    chosen_gc = gc if gc != "auto" else ("zgc" if heap_mb >= 8192 and cpu_threads >= 4 else "g1")
    per_player_kbps = 80 if bedrock else 65
    network_limit = max(1, int(upload_mbps * 1000 * 0.8 / per_player_kbps))
    cpu_limit = max(1, cpu_threads * (3 if cpu_threads <= 4 else 5))
    recommended = max(1, int(min(players, network_limit, cpu_limit) * 0.8))
    return {"heap_mb": heap_mb, "heap_flag": f"-Xms{heap_mb}M -Xmx{heap_mb}M", "gc": chosen_gc,
            "gc_flags": {"g1": "-XX:+UseG1GC", "zgc": "-XX:+UseZGC", "shenandoah": "-XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational"}[chosen_gc],
            "worker_threads": max(2, min(cpu_threads - 1 if cpu_threads > 1 else 1, 8)),
            "view_distance": 8 if cpu_threads < 4 else 10, "simulation_distance": 5 if cpu_threads < 4 else 8,
            "recommended_max_players": recommended, "network_bound_players": network_limit, "cpu_bound_players": cpu_limit,
            "upload_mbps": upload_mbps, "estimated_kbps_per_player": per_player_kbps,
            "confidence": "rough starting estimate; calibrate with tools/bench"}


def log_event(level: str, source: str, message: str, **fields: Any) -> None:
    if LOG_STRUCTURED:
        print(json.dumps({"time": __import__("datetime").datetime.now().astimezone().isoformat(timespec="seconds"), "level": level, "source": source, "plugin_id": None, "content_id": None, "message": message, **fields}, separators=(",", ":")))
    else:
        print(f"{level}: [{source}] {message}")


def discover_plugin_ids(server_dir: Path) -> set[str]:
    names: set[str] = set()
    plugin_dir = server_dir / "plugins"
    if not plugin_dir.is_dir(): return names
    for path in plugin_dir.iterdir():
        try:
            if path.is_dir():
                descriptor = path / "plugin.yml"
                if descriptor.exists(): names.update(re.findall(r"(?m)^name:\s*['\"]?([^'\"\s]+)", descriptor.read_text(encoding="utf-8")))
            elif path.suffix.lower() == ".jar":
                with zipfile.ZipFile(path) as archive:
                    for entry in ("plugin.yml", "paper-plugin.yml"):
                        try:
                            descriptor = archive.read(entry).decode("utf-8")
                            names.update(re.findall(r"(?m)^name:\s*['\"]?([^'\"\s]+)", descriptor))
                        except KeyError: pass
        except (OSError, zipfile.BadZipFile):
            continue
    return names


def cmd_launch(args: argparse.Namespace) -> int:
    global LOG_STRUCTURED
    values, errors, warnings = read_config(args.config)
    LOG_STRUCTURED = values["log.structured"]
    for warning in warnings: log_event("WARN", "settings", warning)
    for error in errors: log_event("ERROR", "settings", error)
    if errors: return 2
    if not args.jar.is_file(): log_event("ERROR", "startup", f"Server jar not found: {args.jar}"); return 2
    install_bundled_plugins(args.jar, args.directory)
    ram = values["hardware.ram-mb"]
    cpu = values["hardware.cpu-threads"]
    players = values["hardware.expected-max-players"]
    upload = values["hardware.upload-mbps"]
    mem_total = int(os.sysconf("SC_PAGE_SIZE") * os.sysconf("SC_PHYS_PAGES") / (1024 * 1024))
    advice = tune(mem_total if ram == "auto" else ram, (os.cpu_count() or 2) if cpu == "auto" else cpu, 20 if upload == "auto" else upload,
                  20 if players == "auto" else players, False, values["hardware.gc"])
    gcflag = {"g1": "-XX:+UseG1GC", "zgc": "-XX:+UseZGC", "shenandoah": "-XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational"}[advice["gc"]]
    cmd = ["java", f"-Xms{advice['heap_mb']}M", f"-Xmx{advice['heap_mb']}M", *gcflag.split(), "-jar", str(args.jar.resolve()), "--nogui"]
    profile = values["parity-profile"]
    plugin_ids = discover_plugin_ids(args.directory)
    log_event("INFO", "startup", f"Starting FlowTail ({profile} profile)", profile=profile, heap_mb=advice["heap_mb"], gc=advice["gc"], worker_threads=advice["worker_threads"])
    for key, setting in BY_KEY.items():
        if key.startswith("opt."): log_event("INFO", "settings", f"{key}={values[key]}", setting=key, risk=setting.risk)
    log_event("INFO", "startup", "Complete world generation parity is locked to exact", setting="worldgen.parity", value="exact")
    try:
        proc = subprocess.Popen(cmd, cwd=args.directory, stdin=None, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    except OSError as exc:
        log_event("ERROR", "startup", f"Could not start Java: {exc}"); return 1
    pid_file = args.directory / ".flowtail.pid"
    pid_file.write_text(f"{proc.pid}\n", encoding="ascii")
    assert proc.stdout is not None
    for line in proc.stdout:
        message = line.rstrip()
        level_match = re.search(r"\b(SEVERE|ERROR|WARN|WARNING|INFO|DEBUG)\b", message)
        level = {"SEVERE": "ERROR", "ERROR": "ERROR", "WARN": "WARN", "WARNING": "WARN", "DEBUG": "DEBUG"}.get(level_match.group(1), "INFO") if level_match else ("ERROR" if "Exception" in message else "INFO")
        filt = values["log.console.default-filter"]
        if (filt == "errors" and level != "ERROR") or (filt == "warnings" and level != "WARN") or (filt == "errors+warnings" and level not in ("ERROR", "WARN")): continue
        match = re.search(r"\[([A-Za-z0-9_.-]+)\]\s+(.+)$", message)
        if match and match.group(1) in plugin_ids:
            log_event(level, "plugin", match.group(2), plugin_id=match.group(1), content_id=None)
        else:
            log_event(level, "server", message)
    code = proc.wait()
    pid_file.unlink(missing_ok=True)
    log_event("INFO" if code == 0 else "ERROR", "shutdown", "Server process stopped", exit_code=code)
    return code


def install_bundled_plugins(server_jar: Path, server_directory: Path) -> None:
    """Install FlowTail's embedded Paper plugin into the server's normal plugin discovery folder."""
    entry = "META-INF/flowtail/FlowTailContent.jar"
    try:
        with zipfile.ZipFile(server_jar) as archive:
            module = archive.read(entry) if entry in archive.namelist() else None
    except (OSError, zipfile.BadZipFile) as exc:
        raise RuntimeError(f"Could not read FlowTail modules from {server_jar}: {exc}") from exc
    if module is None:
        return
    plugin_dir = server_directory / "plugins"
    plugin_dir.mkdir(parents=True, exist_ok=True)
    destination = plugin_dir / "FlowTailContent.jar"
    if destination.is_file() and destination.read_bytes() == module:
        return
    temporary = destination.with_suffix(".jar.tmp")
    temporary.write_bytes(module)
    temporary.replace(destination)
    log_event("INFO", "startup", "Installed bundled content engine", plugin_jar=str(destination))


def cmd_bench(args: argparse.Namespace) -> int:
    samples: list[float] = []
    for _ in range(args.samples):
        try:
            import socket, struct, time
            host, port = args.host, args.port
            started = time.perf_counter()
            with socket.create_connection((host, port), timeout=args.timeout) as sock:
                sock.settimeout(args.timeout)
                def varint(n: int) -> bytes:
                    out = bytearray()
                    while True:
                        b = n & 0x7f; n >>= 7
                        out.append(b | (0x80 if n else 0))
                        if not n: return bytes(out)
                hostbytes = host.encode("utf-8")
                payload = b"\x00" + varint(args.protocol) + struct.pack(">H", len(hostbytes)) + hostbytes + struct.pack(">H", port) + b"\x01"
                sock.sendall(varint(len(payload)) + payload)
                request = b"\x01\x00"
                sock.sendall(varint(len(request)) + request)
                def read_varint() -> int:
                    result = 0
                    for shift in range(0, 35, 7):
                        byte = sock.recv(1)
                        if not byte: raise OSError("server closed before replying")
                        result |= (byte[0] & 0x7f) << shift
                        if not byte[0] & 0x80: return result
                    raise OSError("invalid status response length")
                size = read_varint()
                packet = bytearray()
                while len(packet) < size:
                    part = sock.recv(min(4096, size - len(packet)))
                    if not part: raise OSError("incomplete status response")
                    packet.extend(part)
                if not packet or packet[0] != 0: raise OSError("unexpected status response packet")
                samples.append((time.perf_counter() - started) * 1000.0)
        except (OSError, TimeoutError) as exc:
            print(f"sample failed: {exc}", file=sys.stderr)
    if not samples:
        print("No successful status-ping samples.", file=sys.stderr); return 1
    ordered = sorted(samples)
    def pct(p: float) -> float: return ordered[min(len(ordered)-1, math.ceil(p * len(ordered))-1)]
    print(json.dumps({"scenario": "status-ping", "host": args.host, "port": args.port, "samples": len(samples),
                      "latency_ms": {"p50": round(pct(.50), 2), "p95": round(pct(.95), 2), "p99": round(pct(.99), 2), "max": round(max(samples), 2)},
                      "note": "Measures TCP/Minecraft status response latency only; not player bots, MSPT, GC, or bandwidth under gameplay."}, indent=2))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="FlowTail Phase 1 configuration, tuner, and bench tools")
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("init", help="Generate a commented settings file"); p.add_argument("path", nargs="?", type=Path, default=CONFIG)
    p = sub.add_parser("check", help="Validate settings and show effective flags"); p.add_argument("path", nargs="?", type=Path, default=CONFIG)
    p = sub.add_parser("tune", help="Recommend JVM and server settings"); p.add_argument("--ram-mb", type=int, default=None); p.add_argument("--cpu-threads", type=int, default=None); p.add_argument("--upload-mbps", type=float, default=20); p.add_argument("--players", type=int, default=20); p.add_argument("--bedrock", action="store_true"); p.add_argument("--gc", choices=("auto", "g1", "zgc", "shenandoah"), default="auto")
    p = sub.add_parser("bench", help="Measure Minecraft status-ping percentiles"); p.add_argument("host", nargs="?", default="127.0.0.1"); p.add_argument("--port", type=int, default=25565); p.add_argument("--samples", type=int, default=100); p.add_argument("--timeout", type=float, default=2); p.add_argument("--protocol", type=int, default=0, help="Handshake protocol id; use current server protocol")
    p = sub.add_parser("launch", help="Start a server jar using validated settings"); p.add_argument("jar", type=Path); p.add_argument("--config", type=Path, default=CONFIG); p.add_argument("--directory", type=Path, default=ROOT / "run" / "flowtail")
    p = sub.add_parser("set-profile", help="Set a parity profile safely"); p.add_argument("profile", choices=("vanilla", "balanced", "performance", "super-speed", "custom")); p.add_argument("--config", type=Path, default=CONFIG); p.add_argument("--confirm", default="")
    args = parser.parse_args()
    if args.command == "init": write_example(args.path); print(f"Wrote {args.path}"); return 0
    if args.command == "check":
        values, errors, warnings = read_config(args.path)
        for warning in warnings: print("WARNING: " + warning, file=sys.stderr)
        for error in errors: print("ERROR: " + error, file=sys.stderr)
        if errors: return 2
        print(json.dumps(effective(values), indent=2)); return 0
    if args.command == "tune":
        cpus = args.cpu_threads or (os.cpu_count() or 2)
        ram = args.ram_mb or max(1024, int(os.sysconf("SC_PAGE_SIZE") * os.sysconf("SC_PHYS_PAGES") / (1024 * 1024)))
        try: print(json.dumps(tune(ram, cpus, args.upload_mbps, args.players, args.bedrock, args.gc), indent=2)); return 0
        except ValueError as exc: print(f"ERROR: {exc}", file=sys.stderr); return 2
    if args.command == "bench":
        if args.samples < 1 or args.samples > 10000: parser.error("--samples must be between 1 and 10000")
        return cmd_bench(args)
    if args.command == "launch":
        args.directory.mkdir(parents=True, exist_ok=True)
        return cmd_launch(args)
    if args.command == "set-profile":
        if args.profile == "super-speed" and args.confirm != "SUPER-SPEED":
            print("Super-speed changes gameplay. To continue, run again with --confirm SUPER-SPEED.", file=sys.stderr); return 2
        current = args.config.read_text(encoding="utf-8").splitlines() if args.config.exists() else []
        updates = {"parity-profile": args.profile, "super-speed-confirm": "true" if args.profile == "super-speed" else "false"}
        seen = set(); out = []
        for line in current:
            key = line.split("=", 1)[0].strip() if "=" in line and not line.lstrip().startswith("#") else None
            if key in updates:
                if key not in seen: out.append(f"{key}={updates[key]}"); seen.add(key)
            else: out.append(line)
        for key, value in updates.items():
            if key not in seen: out.append(f"{key}={value}")
        args.config.parent.mkdir(parents=True, exist_ok=True); args.config.write_text("\n".join(out) + "\n", encoding="utf-8")
        print(f"Set parity-profile={args.profile} in {args.config}"); return 0
    return 2


if __name__ == "__main__": raise SystemExit(main())
