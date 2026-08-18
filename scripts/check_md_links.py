#!/usr/bin/env python3
"""Check that relative links in project Markdown files resolve.

Exits non-zero if any relative link points to a missing file.
Run from the repository root:

    python3 scripts/check_md_links.py
"""

import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXCLUDED_DIRS = {"node_modules", ".git", "dist", ".gradle", "build"}

LINK_PATTERNS = [
    re.compile(r"\[[^\]]*\]\(([^)]+)\)"),  # inline: [text](target)
    re.compile(r"^\s*\[[^\]]+\]:\s*(\S+)", re.M),  # reference: [label]: target
]
CODE_FENCE = re.compile(r"```.*?```", re.S)


def markdown_files(root: Path):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in EXCLUDED_DIRS]
        for name in filenames:
            if name.endswith(".md"):
                yield Path(dirpath) / name


def extract_links(text: str) -> list[str]:
    text = CODE_FENCE.sub("", text)  # ignore links inside code blocks
    links = []
    for pat in LINK_PATTERNS:
        links.extend(pat.findall(text))
    return links


def is_external(target: str) -> bool:
    return target.startswith(
        ("http://", "https://", "mailto:", "tel:", "data:", "www.", "#")
    )


def main() -> int:
    broken = []
    checked = 0
    for file in markdown_files(ROOT):
        rel = file.relative_to(ROOT)
        text = file.read_text(encoding="utf-8")
        targets = sorted(set(extract_links(text)))
        if not targets:
            continue
        checked += 1
        for target in targets:
            target = target.split(" ")[0].strip()  # strip optional title
            if is_external(target) or target.startswith("/"):
                continue
            resolved = (file.parent / target).resolve()
            if not resolved.exists():
                broken.append(f"{rel}: {target}")

    if broken:
        print(f"Broken relative links found in {checked} file(s) with links:")
        for b in sorted(broken):
            print(f"  {b}")
        return 1
    print(f"All relative links OK ({checked} file(s) checked)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
