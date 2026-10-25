#!/usr/bin/env python3
"""Reference for the oplus_camera_config switch board — NOT shipped as a file.

The keys below are the ones the B6 port turned on to get Hasselblad super definition, 4K live, the
film mode, the log page, the LUT monitor, 60 fps and XPAN. They are answered in-process by
``ModeGates`` instead of being written into a config profile, for one reason: this file is encrypted
with a key derived from the device's own ``libOplusSecurity.so`` and wrapped in a 4-byte envelope
whose tail does not survive a change in payload length. Shipping a re-encrypted copy would mean
betting the whole config engine on that tail being inert, and a config the engine cannot parse is a
black viewfinder.

The list is kept here because it is the audit trail for what ModeGates answers and why, and because
the envelope may be understood well enough one day to ship the file.

Why this is a profile and not a replacement, if it ever is shipped: profile_config.cpp serves this
file to the camera process only, from the app's private directory. /odm is never written and no
other process sees it.

    python tools/make_camera_config.py [--security path/to/libOplusSecurity.so]

Inputs (both must be the RMX5010 stock files):
    dump/omnicam_dump/odm_camera_config/oplus_camera_config      (encrypted, used as the envelope)
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOOLS = ROOT / "tools"
STOCK = ROOT.parent / "dump/omnicam_dump/odm_camera_config/oplus_camera_config"
SECURITY = ROOT.parent / "dump/omnicam_dump/odm_lib64/libOplusSecurity.so"
# Written outside the module tree on purpose: see the header for why this is not shipped.
OUT_MODULE = ROOT.parent / "out/oplus_camera_config.candidate"
OUT_ASSET = OUT_MODULE

# absent -> added. Every one of these is a switch the camera asks about; none changes a buffer
# geometry or a version stamp.
ADD = {
    # Hasselblad super definition (哈苏超清) and the 50 MP page it lives on.
    "com.oplus.camera.hasselblad.super.definition.support": "1",
    "com.oplus.high_pixel.global.ev.support": "1",
    "com.oplus.support.highpixel.gesture_shutter": "1",
    "com.oplus.feature.highpixel.merge.not.support.zoom": "0",
    # 4K live photo inside the high pixel page.
    "com.oplus.camera.high.pixel.mode.4k.live.support": "1",
    "com.oplus.camera.highpixel.livephoto.video.max.duration": "3000",
    "com.oplus.camera.hasslblad.livephoto.capture.interval": "1000",
    # Master video / film: the log page, the LUT monitor and the master filters.
    "com.oplus.log.version.curve": "2",
    "com.oplus.director.filter.rus": "1",
    "com.oplus.feature.master.film.filter.label.enable": "1",
    "com.oplus.feature.master.mode.version": "2.0",
    "com.oplus.camera.master.video.type": "1",
    "com.oplus.use.real.master.pipeline": "1",
    "com.oplus.master.mode.enable.zsl": "1",
    "com.oplus.camera.master.captureparam.version": "1",
    "com.oplus.master.video.three.state.stabilization": "1",
    "com.oplus.feature.master.video.focus.peaking.histogram.oplus.r.support": "1",
    # Film mode itself.
    "com.oplus.feature.movie.mode.video.size": "3840x2160",
    "com.oplus.feature.movie.customize.video.size.support": "1",
    # XPAN: the develop animation is the new UI. pq.h.n() reads exactly this key to decide between
    # the legacy panel and the animated one, and on this phone the animated one tears down the
    # preview more often than it survives. Legacy keeps the mode usable.
    "com.oplus.xpan.legacy.ui.style": "1",
}

# key -> value to force. These exist on this phone with a different value.
SET = {
    "com.oplus.use.hasselblad.style.support": "1",
    "com.oplus.feature.high.pixel.support": "1",
    "com.oplus.feature.highpixel.merge.support": "1",
    "com.oplus.feature.movie.mode.support": "1",
    "com.oplus.feature.movie.mode.V2.support": "1",
    "com.oplus.director.filter.support": "1",
    "com.oplus.feature.master.hq.raw.support": "1",
    "com.oplus.feature.xpan.mode.support": "1",
    # 60 fps in master video. The film pipeline (80A3) is 30 fps by design; master video now sits on
    # 800b, which is the ordinary recorder op mode, so this switch is what actually reaches 60.
    "com.oplus.feature.master.video.60fps.support": "1",
    # Tracking focus used to force the whole video path down to 30 fps.
    "com.oplus.video.focus.track.30fps.only": "0",
}

# 14 slots on this build (r9.i reads up to index 17, out of range returns the default). The first
# twelve are the X8U/B6 values: 1 = on for the log page, the LUT monitor, the log session, the
# master video HDR toggle and the guide. Slots 12-13 stay 0, which is what this phone shipped.
FEATURE_LIST = "1,1,1,1,1,1,1,2,1,1,1,1,0,0"

# Keys that must never be written from this profile, whatever a source package says. Listed so the
# script fails loudly instead of quietly reintroducing the B6 capture bug.
FORBIDDEN = re.compile(
    r"picturesize|picture\.size|imagesizes|\.sizes$|previewsize|preview\.size|"
    r"mode\.data\.db\.version|xpan\.mode\.version|available\..*zoomvalues|"
    r"high\.livephoto|livephoto\.size|feature\.capture\.mode\.livephoto"
)


def decrypt(key, data):
    if data[:2] != b"\x01\x01" or data[-1] != 0xFF:
        return data
    out = subprocess.run(["openssl", "enc", "-aes-128-ecb", "-nopad", "-K", key.hex(), "-d"],
                         input=data[4:-4], capture_output=True)
    if out.returncode:
        sys.exit(f"openssl failed: {out.stderr.decode()[:200]}")
    plain = out.stdout
    return plain[:-plain[-1]]


def encrypt(key, plain, frame):
    pad = 16 - len(plain) % 16
    out = subprocess.run(["openssl", "enc", "-aes-128-ecb", "-nopad", "-K", key.hex(), "-e"],
                         input=plain + bytes([pad]) * pad, capture_output=True)
    if out.returncode:
        sys.exit(f"openssl failed: {out.stderr.decode()[:200]}")
    return frame[:4] + out.stdout + frame[-4:]


def config_key(security: Path) -> bytes:
    raw = security.read_bytes()
    return bytes(v ^ 0x3F for v in raw[0x7AB:0x7AB + 32])[::2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--security", default=str(SECURITY))
    args = parser.parse_args()

    key = config_key(Path(args.security))
    frame = STOCK.read_bytes()
    plain = decrypt(key, frame)
    entries = json.loads(plain.decode("utf-8"))
    before = {e["VendorTag"]: e["Value"] for e in entries}

    for name in list(ADD) + list(SET) + ["com.oplus.camera.master.video.feature.list"]:
        if FORBIDDEN.search(name):
            sys.exit(f"refusing to write {name}: it describes a buffer geometry")

    added = 0
    changed = 0
    for name, value in ADD.items():
        if name in before:
            continue
        entries.append({"VendorTag": name, "Value": value})
        added += 1
    for name, value in SET.items():
        hit = next((e for e in entries if e["VendorTag"] == name), None)
        if hit is None:
            entries.append({"VendorTag": name, "Value": value})
            added += 1
        elif hit["Value"] != value:
            hit["Value"] = value
            changed += 1
    for entry in entries:
        if entry["VendorTag"] == "com.oplus.camera.master.video.feature.list":
            if entry["Value"] != FEATURE_LIST:
                entry["Value"] = FEATURE_LIST
                changed += 1
            break
    else:
        sys.exit("feature list key not found in the stock config")

    text = json.dumps(entries, ensure_ascii=False, indent=2)
    packed = encrypt(key, text.encode("utf-8"), frame)
    if decrypt(key, packed) != text.encode("utf-8"):
        sys.exit("round trip mismatch, refusing to write")

    OUT_MODULE.parent.mkdir(parents=True, exist_ok=True)
    OUT_MODULE.write_bytes(packed)
    print(f"wrote {OUT_MODULE}  {len(packed)}B  (reference only, not packaged)")

    print(f"entries {len(entries)} (was {len(before)}), added {added}, changed {changed}")
    final = {e["VendorTag"]: e["Value"] for e in entries}
    for name in list(ADD) + list(SET):
        print(f"  {name} = {final[name]}")
    print(f"  com.oplus.camera.master.video.feature.list = {final['com.oplus.camera.master.video.feature.list']}")


if __name__ == "__main__":
    main()
