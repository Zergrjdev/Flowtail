#!/usr/bin/env python3
"""Print the resource/data pack format a Minecraft server jar declares (reads version.json inside the jar).

  python3 scripts/mc-pack-format.py run/upstream/cache/mojang_26.3.jar

Use the printed `resource_major` and `resource_minor` values for the `[major, minor]` value of
`resource_pack_format` in versions.toml. Mojang's own jar is the authority.
"""
import json
import sys
import zipfile

if len(sys.argv) != 2:
    sys.exit(__doc__)
try:
    with zipfile.ZipFile(sys.argv[1]) as z:
        names = z.namelist()
        # bundler jars keep the real server jar under META-INF/versions/<v>/server-<v>.jar
        data = None
        if "version.json" in names:
            data = json.loads(z.read("version.json"))
        else:
            inner = [n for n in names if n.startswith("META-INF/versions/") and n.endswith(".jar")]
            if inner:
                import io
                with zipfile.ZipFile(io.BytesIO(z.read(inner[0]))) as z2:
                    if "version.json" in z2.namelist():
                        data = json.loads(z2.read("version.json"))
        if data is None:
            sys.exit("error: no version.json found in that jar. Is it a Minecraft server jar?")
except (OSError, zipfile.BadZipFile) as e:
    sys.exit(f"error: cannot read {sys.argv[1]}: {e}")
print("name:", data.get("name"))
print("pack_version:", json.dumps(data.get("pack_version")))
for k in ("resource_pack_version", "data_pack_version", "world_version", "protocol_version"):
    if k in data:
        print(f"{k}:", data[k])
