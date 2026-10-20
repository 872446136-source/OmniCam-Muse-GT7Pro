#!/usr/bin/env python3
"""Remove the native (CMake/NDK) part from app/build.gradle.kts before a Lite build.

The Lite build deletes assets/native_init, so LSPosed never loads libjiege_native.so and the
camera never runs the native half (ODM path remap, tuning-library patches). Building it anyway
would drag a 1 GB NDK download and CMake 3.22.1 into CI for a library nobody loads, so the
workflow strips the declarations instead.

Removed:
  ndkVersion = "..."
  externalNativeBuild { ... }      (both the android{} one and the defaultConfig{} one)
  ndk { abiFilters += ... }
"""

import re
import sys
from pathlib import Path


def drop_block(src: str, header: str) -> str:
    """Drop every `header { ... }` block, brace-matched, together with its whole line."""
    out, i = [], 0
    while True:
        j = src.find(header, i)
        if j < 0:
            out.append(src[i:])
            return "".join(out)
        line_start = src.rfind("\n", 0, j) + 1
        out.append(src[i:line_start])
        brace = src.find("{", j)
        depth, p = 0, brace
        while p < len(src):
            if src[p] == "{":
                depth += 1
            elif src[p] == "}":
                depth -= 1
                if depth == 0:
                    break
            p += 1
        else:
            raise SystemExit(f"unbalanced braces after {header!r}")
        newline = src.find("\n", p)
        i = len(src) if newline < 0 else newline + 1


def main() -> None:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build.gradle.kts")
    text = path.read_text(encoding="utf-8")

    text = drop_block(text, "externalNativeBuild {")
    text = drop_block(text, "ndk {")
    text = re.sub(r"^[ \t]*ndkVersion[ \t]*=.*\n", "", text, flags=re.MULTILINE)

    for gone in ("externalNativeBuild", "ndkVersion", "abiFilters"):
        if gone in text:
            raise SystemExit(f"{gone} still present after stripping")

    path.write_text(text, encoding="utf-8")
    print(f"native build stripped: {path}")


if __name__ == "__main__":
    main()
