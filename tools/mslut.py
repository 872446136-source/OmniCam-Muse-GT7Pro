#!/usr/bin/env python3
"""Read, resample and rewrite `.MS-LUT` 3D LUT files.

Format (little-endian, all fields uint32 unless noted):

    0x00  8   magic ".MS-LUT "
    0x08  4   version
    0x0C  4   size N (samples per axis)
    0x10  4   unused
    0x14  4   channels (1)
    0x18  4   per-version constant: 1 for v1, 24 for v3, 1176 for v4
    0x1C  4   unused
    0x20  4   header size (48)
    0x24  4   unused
    0x28  4   data offset = 48 + N * 4
    0x2C  4   unused
    0x30  N*4 axis table, float32, axis[i] = i / (N - 1)
    data  N^3 * 3 uint8, R fastest, then G, then B

Why this exists: the X10 filters 清透 and 琥珀 ship as version 4 / 33^3 LUTs. Every LUT the
GT7 Pro firmware itself uses is version 1 / 32^3 (58 files) or version 3 / 17^3 (39 files); it has
no version 4 file at all, and the two version 4 LUTs render black in its preview. Rewriting them
as version 1 / 32^3 with a trilinear resample gives the engine a format it loads.

Usage:
    mslut.py info <path>...        header of each file
    mslut.py fix  <dir> [--size N] convert every version > 3 LUT in the directory in place
"""

import struct
import sys
from pathlib import Path

MAGIC = b".MS-LUT "
HEADER = 48
SUPPORTED_TARGET_VERSION = 1
SUPPORTED_TARGET_SIZE = 32
# The per-version constant at 0x18. Constant within a version across every file in the payload.
VERSION_CONSTANT = {1: 1, 3: 24, 4: 1176}


def read_header(data):
    if data[:8] != MAGIC:
        raise ValueError("not an .MS-LUT file")
    version, size, _, channels, constant, _, header, _, offset, _ = struct.unpack("<10I", data[8:HEADER])
    if header != HEADER:
        raise ValueError(f"unexpected header size {header}")
    if offset != HEADER + size * 4:
        raise ValueError(f"unexpected data offset {offset} for size {size}")
    expected = offset + size ** 3 * 3
    if len(data) != expected:
        raise ValueError(f"size {len(data)} does not match {size}^3 * 3 + {offset} = {expected}")
    return version, size, channels, constant, offset


def read_lut(path):
    data = Path(path).read_bytes()
    version, size, channels, constant, offset = read_header(data)
    return version, size, data[offset:]


def build_lut(version, size, cube, prefix=None):
    """Serialize a size^3 RGB uint8 cube as a version-1 .MS-LUT.

    The 48-byte header and the axis table are taken verbatim from prefix when one is given, so the
    result is byte-identical to the LUTs the firmware ships (the axis values differ from a computed
    i/(n-1) ramp by one float32 ULP, which is harmless but needless).
    """
    if prefix is not None and len(prefix) == HEADER + size * 4:
        header_and_axis = prefix
    else:
        axis = b"".join(struct.pack("<f", i / (size - 1)) for i in range(size))
        offset = HEADER + len(axis)
        header_and_axis = (MAGIC + struct.pack("<10I", version, size, 0, 1, VERSION_CONSTANT.get(version, 1), 0,
                                               HEADER, 0, offset, 0) + axis)
    return header_and_axis + cube


def reference_prefix(directory, size=SUPPORTED_TARGET_SIZE):
    """Header and axis table of an existing version-1 LUT of this size in the directory, or None."""
    for path in sorted(Path(directory).iterdir()):
        if not path.is_file():
            continue
        try:
            data = path.read_bytes()
            version, found, _, _, offset = read_header(data)
        except (ValueError, OSError):
            continue
        if version == SUPPORTED_TARGET_VERSION and found == size:
            return data[:offset]
    return None


def resample(cube, source_size, target_size):
    """Trilinear resample of a source_size^3 RGB cube to target_size^3."""
    out = bytearray(target_size ** 3 * 3)
    scale = (source_size - 1) / (target_size - 1)
    index = 0
    for b in range(target_size):
        bz = b * scale
        b0 = int(bz)
        b1 = min(b0 + 1, source_size - 1)
        bf = bz - b0
        for g in range(target_size):
            gy = g * scale
            g0 = int(gy)
            g1 = min(g0 + 1, source_size - 1)
            gf = gy - g0
            for r in range(target_size):
                rx = r * scale
                r0 = int(rx)
                r1 = min(r0 + 1, source_size - 1)
                rf = rx - r0
                for channel in range(3):
                    def sample(x, y, z):
                        return cube[((z * source_size + y) * source_size + x) * 3 + channel]
                    c00 = sample(r0, g0, b0) * (1 - rf) + sample(r1, g0, b0) * rf
                    c10 = sample(r0, g1, b0) * (1 - rf) + sample(r1, g1, b0) * rf
                    c01 = sample(r0, g0, b1) * (1 - rf) + sample(r1, g0, b1) * rf
                    c11 = sample(r0, g1, b1) * (1 - rf) + sample(r1, g1, b1) * rf
                    c0 = c00 * (1 - gf) + c10 * gf
                    c1 = c01 * (1 - gf) + c11 * gf
                    value = c0 * (1 - bf) + c1 * bf
                    out[index] = max(0, min(255, int(round(value))))
                    index += 1
    return bytes(out)


def info(path):
    data = Path(path).read_bytes()
    try:
        version, size, channels, constant, offset = read_header(data)
        print(f"{path}: v{version} {size}^3 channels={channels} constant={constant} "
              f"data@{offset} {len(data)} bytes")
    except ValueError as error:
        print(f"{path}: {error}")


def fix(directory, target_size=SUPPORTED_TARGET_SIZE):
    changed = 0
    prefix = reference_prefix(directory, target_size)
    for path in sorted(Path(directory).iterdir()):
        if not path.is_file():
            continue
        try:
            version, size, cube = read_lut(path)
        except (ValueError, OSError):
            continue
        if version <= 3 and size == target_size:
            continue
        if version <= 3:
            # A size the device uses but another version: only version 4 is known to fail, leave
            # version 1 and 3 alone whatever their size.
            continue
        converted = resample(cube, size, target_size)
        path.write_bytes(build_lut(SUPPORTED_TARGET_VERSION, target_size, converted, prefix))
        print(f"{path.name}: v{version} {size}^3 -> v{SUPPORTED_TARGET_VERSION} {target_size}^3 "
              f"({len(converted) + HEADER + target_size * 4} bytes)")
        changed += 1
    return changed


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    command = argv[1]
    if command == "info":
        for path in argv[2:]:
            info(path)
        return 0
    if command == "fix":
        size = SUPPORTED_TARGET_SIZE
        if "--size" in argv:
            size = int(argv[argv.index("--size") + 1])
        changed = fix(argv[2], size)
        print(f"{changed} LUT(s) converted")
        return 0
    print(f"unknown command {command}")
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
