#!/usr/bin/env python3
"""Resample the upstream GR LMT cubes onto the grid this firmware's BasicTone is built for.

Both sides are the same file format: planar float32 RGB, channel 0 fastest, r fastest inside a
plane, no header, 12 bytes per entry. Only the grid differs.

    GT7 Pro   /odm/etc/camera/basictone/lmt/SCLut0..8   64^3   3145728 B   (setting/SimTool.ini: lut_size = 64)
    upstream  payload/gr_adjustment_lmt/SC32Lut0..8     32^3    393216 B   (setting_Retro: lut_size = 32)

BasicTone takes the grid size from the ini and then reads a file of that size. Handing it a 32^3
file while the ini says 64^3 makes it read a quarter of the table and treat the rest as the next
channel, and handing it no file at all leaves the merge running on a zero table. Both look the same
on screen: a black viewfinder and a green frame once the same table is applied in the capture path.
481 shipped the 32^3 names into a profile directory this firmware never opens, and skipped SCLut*
outright, which is why only the first GR style worked.

    gr_lmt.py <payload/gr_adjustment_lmt> <out dir> [--size 64]

Writes SCLut0..8 into <out dir>; every other table in the payload is left alone because this
firmware's libBasicTonePhoto.so has no CMCWLut or CWCM string in it at all, so a colour-matrix
stage would be read by nobody.
"""
import argparse
import struct
import sys
from pathlib import Path

ENTRY = 12  # 3 channels * float32
FAMILIES = ("SC32Lut", "SC16Lut")
STYLES = 9


def load(path):
    data = Path(path).read_bytes()
    if len(data) % ENTRY:
        raise SystemExit(f"{path}: {len(data)} bytes is not a multiple of {ENTRY}")
    n = round((len(data) / ENTRY) ** (1 / 3))
    if n ** 3 * ENTRY != len(data):
        raise SystemExit(f"{path}: {len(data)} bytes is not a cube ({n}^3 would be {n ** 3 * ENTRY})")
    return n, struct.unpack(f"<{len(data) // 4}f", data)


def resample(src, n_src, n_dst):
    """Trilinear resample of a planar float32 RGB cube. Identity in, identity out."""
    plane_src, plane_dst = n_src ** 3, n_dst ** 3
    scale = (n_src - 1) / (n_dst - 1)
    out = [0.0] * (3 * plane_dst)
    for c in range(3):
        base_src, base_dst = c * plane_src, c * plane_dst
        for b in range(n_dst):
            fb = b * scale
            b0 = min(int(fb), n_src - 1)
            b1 = min(b0 + 1, n_src - 1)
            tb = fb - b0
            for g in range(n_dst):
                fg = g * scale
                g0 = min(int(fg), n_src - 1)
                g1 = min(g0 + 1, n_src - 1)
                tg = fg - g0
                row = base_dst + b * n_dst * n_dst + g * n_dst
                for r in range(n_dst):
                    fr = r * scale
                    r0 = min(int(fr), n_src - 1)
                    r1 = min(r0 + 1, n_src - 1)
                    tr = fr - r0
                    i000 = base_src + b0 * n_src * n_src + g0 * n_src + r0
                    i001 = base_src + b0 * n_src * n_src + g0 * n_src + r1
                    i010 = base_src + b0 * n_src * n_src + g1 * n_src + r0
                    i011 = base_src + b0 * n_src * n_src + g1 * n_src + r1
                    i100 = base_src + b1 * n_src * n_src + g0 * n_src + r0
                    i101 = base_src + b1 * n_src * n_src + g0 * n_src + r1
                    i110 = base_src + b1 * n_src * n_src + g1 * n_src + r0
                    i111 = base_src + b1 * n_src * n_src + g1 * n_src + r1
                    s, v = src, out
                    c00 = s[i000] + (s[i001] - s[i000]) * tr
                    c01 = s[i010] + (s[i011] - s[i010]) * tr
                    c10 = s[i100] + (s[i101] - s[i100]) * tr
                    c11 = s[i110] + (s[i111] - s[i110]) * tr
                    c0 = c00 + (c01 - c00) * tg
                    c1 = c10 + (c11 - c10) * tg
                    v[row + r] = c0 + (c1 - c0) * tb
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("dest")
    parser.add_argument("--size", type=int, default=64)
    args = parser.parse_args()
    src_dir, dst_dir = Path(args.source), Path(args.dest)
    if not src_dir.is_dir():
        raise SystemExit(f"{src_dir} is not a directory")
    dst_dir.mkdir(parents=True, exist_ok=True)
    written = 0
    for style in range(STYLES):
        path = None
        for family in FAMILIES:
            candidate = src_dir / f"{family}{style}"
            if candidate.exists():
                path = candidate
                break
        if path is None:
            raise SystemExit(f"no source table for style {style} in {src_dir}")
        n_src, values = load(path)
        cube = resample(values, n_src, args.size)
        blob = struct.pack(f"<{len(cube)}f", *cube)
        target = dst_dir / f"SCLut{style}"
        target.write_bytes(blob)
        written += 1
        lo, hi = min(cube), max(cube)
        print(f"SCLut{style} <- {path.name} {n_src}^3 -> {args.size}^3, {len(blob)} B, "
              f"range {lo:.4f}..{hi:.4f}")
    print(f"wrote {written} tables to {dst_dir}")


if __name__ == "__main__":
    main()
