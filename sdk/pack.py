#!/usr/bin/env python3
"""Pack a local xNotes plugin; no network or dependencies."""
import argparse
import json
import re
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

def pack(source: Path, out: Path) -> Path:
    manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
    ident = manifest["id"]
    if not isinstance(ident, str) or not re.fullmatch(r"[a-z][a-z0-9.-]{0,63}", ident) or ident == "core":
        raise ValueError("Invalid plugin ID")
    typ = manifest["type"]
    if typ in ("javascript", "toolbar-javascript"):
        names = ["manifest.json", "main.js"]
    elif typ == "toolbar":
        names = ["manifest.json", "toolbar.json"]
    elif typ == "declarative":
        names = ["manifest.json"] if manifest.get("feature") else ["manifest.json", "profiles.json"]
    else:
        raise ValueError("Unknown type")
    files = {}
    for name in names:
        path = source / name
        if path.is_symlink() or not path.is_file():
            raise ValueError(f"Missing or unsafe file: {name}")
        data = path.read_bytes()
        if len(data) > 65536 or (name in ("main.js", "toolbar.json") and len(data.decode("utf-8")) > 32768):
            raise ValueError(f"File too large: {name}")
        files[name] = data
    out.mkdir(parents=True, exist_ok=True)
    target = out / (ident + ".xplugin")
    from io import BytesIO
    buffer = BytesIO()
    with ZipFile(buffer, "w", ZIP_DEFLATED) as z:
        for name, data in files.items():
            z.writestr(name, data)
    if len(buffer.getvalue()) > 131072:
        raise ValueError("Archive too large")
    target.write_bytes(buffer.getvalue())
    return target

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--out", type=Path, default=Path("dist"))
    args = parser.parse_args()
    print(pack(args.source, args.out))
