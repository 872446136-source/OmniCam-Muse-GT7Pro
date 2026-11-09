#!/usr/bin/env python3
"""DO NOT SHIP THIS OUTPUT. Build 503 proved it bricks the camera on RMX5010 (2026-11-09).

The stock /odm/etc/camera/config/oplus_camera_config is an AES envelope
(01 01 ... FF, 61880 bytes, md5 990cc3f80f4463bbc6af9c6cc57cd1e5), keyed from this
device's libOplusSecurity.so. This script writes plaintext JSON (65863 bytes).
503 shipped that file as module/system/odm/etc/camera/config/oplus_camera_config;
right after installation the magic mount had not taken effect yet so the camera
still worked, but the next reboot mounted the plaintext for every process. The
camera could not parse it: startup NPE in MenuOptionScreen, then device 0 open
but UNCONFIGURED (Surface already has a stream created for it /
PREVIEW_CONFIG_FAILED) — black viewfinder, shutter unresponsive. Deleting the
file restores the stock encrypted table and the camera immediately, no reboot of
the HAL needed (only a camera process restart).

The switch board is answered in-process by ModeGates; see make_camera_config.py
for why this file must never be shipped. Everything below is an audit trail of
the failed approach only.

Original note: write the module's oplus_camera_config with XPAN's vendor tags corrected.

The stock table declares XPAN's *canvas* as its picture size:

    com.oplus.xpan.main.picturesize  = 8000x3636      (64+7872+64 by 360+2912+360)

With the canvas handed out as the output size there is nothing left to cut, and the mode falls
back to the ordinary 4:3 pipeline — a 3072x4096 photo with no XPAN frame. The 4.4.4 port for
PMA110, the Aura port for PLK110 and the earlier GT7 Pro port all answer the crop region instead:

    com.oplus.xpan.{main,wide,tele}.picturesize = 7872x2912
    com.oplus.xpan.all.camera.support           = 1        (absent on this firmware)
    com.oplus.xpan.mode.version                 = 3        (absent on this firmware)

This tool takes the *decrypted* stock table and writes it back with those six entries in place.
THIS IS THE MISTAKE: the 503 build put the result in a filesystem layer
(system/odm/etc/camera/config/oplus_camera_config), so KernelSU's magic mount
replaced the encrypted stock file with plaintext for every process, HAL included.
"Plaintext is accepted" is false for this firmware — see the warning at the top.
The in-process redirect in profile_config.cpp cannot reach the HAL, and 502's
experiment proved it: a staged copy with a deliberately wrong size changed nothing.

    python patch_xpan_config.py <plain.json> <out>

`plain.json` comes from `oplus_config.py dump <libOplusSecurity.so> <stock> <out>`. The output is
plaintext on purpose: the loader accepts both, and plaintext carries no key that could stop matching
after a firmware update. The trade-off is that the file pins every vendor tag to the build it was
dumped from, which is why this is regenerated when the system moves.
"""
import json
import re
import sys
from pathlib import Path

SIZES = [
    "com.oplus.xpan.main.picturesize",
    "com.oplus.xpan.wide.picturesize",
    "com.oplus.xpan.tele.picturesize",
]
SIZE_VALUE = "7872x2912"

ADDED = [
    ("com.oplus.xpan.all.camera.support", "Byte", "1", "1"),
    ("com.oplus.xpan.mode.version", "Int32", "1", "3"),
]
FORCED = {"com.oplus.feature.xpan.mode.support": "1"}


def entries(text: str):
    """(start, end) of every {...} entry, so values can be replaced without re-serialising."""
    return [(m.start(), m.end()) for m in re.finditer(r"\{[^{}]*\}", text)]


def set_value(text: str, key: str, value: str) -> tuple[str, bool]:
    for start, end in entries(text):
        block = text[start:end]
        if f'"VendorTag": "{key}"' not in block:
            continue
        new = re.sub(r'("Value":\s*")[^"]*(")', lambda m: m.group(1) + value + m.group(2), block)
        if new == block:
            return text, False
        return text[:start] + new + text[end:], True
    return text, False


def add_entry(text: str, key: str, kind: str, count: str, value: str) -> str:
    end = text.rindex("]")
    cut = end
    while cut > 0 and text[cut - 1].isspace():
        cut -= 1
    head, tail = text[:cut], text[cut:]
    lead = "" if head.endswith(",") else ","
    block = (
        f'{lead}\n\n{{\n  "VendorTag": "{key}",\n  "Type": "{kind}",\n'
        f'  "Count": "{count}",\n  "Value": "{value}"\n}}\n'
    )
    return head + block + tail


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    source, out = Path(sys.argv[1]), Path(sys.argv[2])
    text = source.read_text(encoding="utf-8")

    changed = []
    for key in SIZES:
        text, done = set_value(text, key, SIZE_VALUE)
        if done:
            changed.append(key)
        elif f'"VendorTag": "{key}"' not in text:
            text = add_entry(text, key, "Int32", "2", SIZE_VALUE)
            changed.append(key + " (added)")
    for key, value in FORCED.items():
        text, done = set_value(text, key, value)
        if done:
            changed.append(key)
    for key, kind, count, value in ADDED:
        if f'"VendorTag": "{key}"' in text:
            text, done = set_value(text, key, value)
            if done:
                changed.append(key)
        else:
            text = add_entry(text, key, kind, count, value)
            changed.append(key + " (added)")

    cleaned = re.sub(r",(\s*[}\]])", r"\1", text)
    table = json.loads(cleaned)
    got = {e["VendorTag"]: e["Value"] for e in table}
    for key in SIZES:
        assert got.get(key) == SIZE_VALUE, (key, got.get(key))
    for key, kind, count, value in ADDED:
        assert got.get(key) == value, (key, got.get(key))
    for key, value in FORCED.items():
        assert got.get(key) == value, (key, got.get(key))

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8")
    print(f"{out}  {len(table)} entries, {len(text)}B")
    for key in changed:
        print("  ", key)


if __name__ == "__main__":
    main()
