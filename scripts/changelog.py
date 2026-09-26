#!/usr/bin/env python3
"""Writes CHANGELOG.md from docs/releases/v*.md, newest version first. The release notes are the
single source: each version's section is its notes without the install instructions, dated by the
commit that added the notes. Used by the Release workflow and by hand:

    python3 scripts/changelog.py [salida.md]
"""
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
NOTES = ROOT / "docs" / "releases"


def version_key(path):
    return tuple(int(n) for n in re.findall(r"\d+", path.stem))


def added_on(path):
    out = subprocess.run(
        ["git", "log", "--diff-filter=A", "--format=%ad", "--date=format:%Y-%m-%d", "--", str(path)],
        capture_output=True, text=True, cwd=ROOT,
    ).stdout.split()
    return out[-1] if out else ""


def body(path):
    lines = path.read_text(encoding="utf-8").splitlines()
    kept, skipping = [], False
    for line in lines:
        if line.startswith("### "):
            skipping = bool(re.match(r"###\s+(instalar|install)", line, re.IGNORECASE))
        if not skipping:
            kept.append(line)
    # Notes use "###" for their sections; under a "## version" heading they read one level down.
    return "\n".join(kept).strip()


def main():
    out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "CHANGELOG.md"
    files = sorted(NOTES.glob("v*.md"), key=version_key, reverse=True)
    parts = [
        "# Changelog: Player",
        "",
        "Todas las versiones estables, de la más reciente a la más antigua. Se genera solo desde "
        "`docs/releases/` con `scripts/changelog.py`; cada release lleva su APK en "
        "[GitHub Releases](https://github.com/danielzunigazb/player/releases).",
        "",
    ]
    for f in files:
        date = added_on(f)
        parts += [f"## {f.stem}" + (f" · {date}" if date else ""), "", body(f), ""]
    out.write_text("\n".join(parts), encoding="utf-8")
    print(f"{out}: {len(files)} versions")


if __name__ == "__main__":
    main()
