#!/usr/bin/env python
"""Replay OmniCam-Muse's hook symbol table (assets/symbols.json) against a target APK.

Prints, per symbol, the class/method it resolves to on that build, and a problem count.
A symbol that does not resolve means the matching hook silently does nothing on that build.

  python replay_gt7pro.py --camera <camera.apk> [--gallery <gallery.apk>] [-v]

Needs: python 3.9+ only (no androguard, no java, no dexdump).
"""
import argparse
import json
import os
import pickle
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))


def build_index(apk, out):
    subprocess.check_call([sys.executable, os.path.join(HERE, "index_from_dex.py"), apk, out])
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--camera")
    ap.add_argument("--gallery")
    ap.add_argument("--spec", default=os.path.join(HERE, "..", "symbols.json"))
    ap.add_argument("--work", default=tempfile.mkdtemp(prefix="muse_replay_"))
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    # dx.py reads SYMBOLS_WORK at import time, so set it before importing spec_eval.
    os.environ["SYMBOLS_WORK"] = args.work
    # Prefer the bundled spec_eval.py + dx.py shim; fall back to the upstream repo if absent.
    if HERE not in sys.path:
        sys.path.insert(0, HERE)
    if not os.path.exists(os.path.join(HERE, "spec_eval.py")):
        repo_tools = os.path.join(HERE, "..", "..", "repo", "tools")
        for p in (repo_tools, os.path.join(repo_tools, "symbols")):
            if os.path.isdir(p) and p not in sys.path:
                sys.path.insert(0, p)
    try:
        import spec_eval
    except ImportError:
        sys.exit("缺少 spec_eval.py：请把它和 dx.py 放在本脚本同目录，"
                 "或设置 PYTHONPATH 指向 <repo>/tools 和 <repo>/tools/symbols")

    spec = json.load(open(args.spec, encoding="utf-8"))
    total = 0
    for app, apk, tag in (("camera", args.camera, "target_cam"), ("gallery", args.gallery, "target_gal")):
        if not apk:
            continue
        pkl = os.path.join(args.work, "idx_%s.pkl" % tag)
        if not os.path.exists(pkl):
            build_index(apk, pkl)
        print("== %s: %s" % (app, os.path.basename(apk)))
        total += spec_eval.report(spec[app], (tag,), None, verbose=args.verbose)
    print("TOTAL problems:", total)
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
