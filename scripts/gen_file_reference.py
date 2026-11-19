#!/usr/bin/env python3
"""Regenerates docs/FILE_REFERENCE.md from the KDoc headers in app/src/main/java.

Run from the project root:

    python scripts/gen_file_reference.py

Each Kotlin file is documented with its line count, the "What it is / What it does" summary taken
from its header KDoc, and its key declarations with their nearest KDoc description.
"""

from __future__ import annotations

import collections
import pathlib
import re

SOURCE_ROOT = pathlib.Path("app/src/main/java/com/jmd/superapp")
OUTPUT = pathlib.Path("docs/FILE_REFERENCE.md")

DECL_RE = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s+)*"
    r"(?:private |internal |public |protected )?"
    r"(?:abstract |open |data |sealed |inner |enum |annotation )*"
    r"(class|object|interface|fun|val|var|typealias)\s+([A-Za-z_]\w*)",
    re.M,
)

WHAT_IS_RE = re.compile(r"What it is:\s*\n((?:\s*\*\s*.+\n)+?)")
WHAT_DOES_RE = re.compile(r"What it does:\s*\n((?:\s*\*\s*.+\n)+?)")


def clean(block: str) -> str:
    """Collapses a KDoc block into one sentence, stopping at the Linked-with section."""
    lines = []
    for raw in block.splitlines():
        text = raw.strip().lstrip("*").strip()
        if not text:
            break
        if text.lower().startswith(("linked with", "- ")):
            break
        lines.append(text)
    return re.sub(r"\s+", " ", " ".join(lines)).strip().rstrip("/ ").rstrip(".")


def header(text: str) -> tuple[str, str]:
    what = WHAT_IS_RE.search(text)
    does = WHAT_DOES_RE.search(text)
    return (clean(what.group(1)) if what else ""), (clean(does.group(1)) if does else "")


def read(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8-sig")


def build() -> str:
    files = sorted(SOURCE_ROOT.rglob("*.kt"))
    grouped: dict[str, list[tuple[pathlib.Path, str]]] = collections.defaultdict(list)
    total_lines = 0
    for path in files:
        text = read(path)
        total_lines += len(text.splitlines())
        match = re.search(r"^package\s+([\w.]+)", text, re.M)
        grouped[match.group(1) if match else "(default)"].append((path, text))

    out = [
        "# Super — File & Code Reference",
        "",
        "Auto-generated map of every Kotlin source file: what it is, what it does, and the main",
        "declarations it contributes.",
        "",
        f"- Kotlin files: **{len(files)}**",
        f"- Kotlin lines: **{total_lines:,}**",
        "",
        "> No `src/test` or `src/androidTest` tree: the project ships without test code by request.",
        "",
    ]

    for package in sorted(grouped):
        out += [f"## `{package}` — {len(grouped[package])} file(s)", ""]
        for path, text in sorted(grouped[package], key=lambda item: item[0].name):
            what, does = header(text)
            out.append(f"### `{path.as_posix()}`")
            out.append(f"*{len(text.splitlines())} lines*")
            if what:
                out.append(f"**What it is:** {what}.")
            if does:
                out.append(f"**What it does:** {does}.")

            declared, seen = [], set()
            for match in DECL_RE.finditer(text):
                kind, name = match.group(1), match.group(2)
                if name in seen:
                    continue
                seen.add(name)
                docs = list(WHAT_DOES_RE.finditer(text[: match.start()][-1500:]))
                description = clean(docs[-1].group(1)) if docs else ""
                if len(description) > 140:
                    description = description[:137] + "…"
                declared.append(f"- `{kind} {name}`" + (f" — {description}" if description else ""))

            if declared:
                out.append("**Key blocks:**")
                out += declared[:12]
                if len(declared) > 12:
                    out.append(f"- …and {len(declared) - 12} more")
            out.append("")

    return "\n".join(out) + "\n"


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(build(), encoding="utf-8")
    print(f"wrote {OUTPUT}")


if __name__ == "__main__":
    main()
