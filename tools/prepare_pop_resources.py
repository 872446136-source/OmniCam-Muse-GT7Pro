#!/usr/bin/env python3
"""Stage CombineLut's POP inputs alongside the separate, stock BasicTone inputs.

BasicTonePhoto reads SimTool.ini and SCLut*. CombineLut reads SimTool_Master.ini /
SimTool_Photo_Master.ini, SC32Lut* and CWCM32Lut*. Keeping only the former silently
disables POP adjustments. This tool validates the latter before writing any files.
"""
import argparse
from array import array
import configparser
import math
from pathlib import Path
import shutil
import sys

SETTINGS = ("SimTool_Master.ini", "SimTool_Photo_Master.ini")


def read_cube(path, size):
    data = path.read_bytes()
    if len(data) != 12 * size ** 3:
        raise ValueError(f"{path}: expected {12 * size ** 3} bytes, got {len(data)}")
    values = array("f")
    values.frombytes(data)
    if sys.byteorder != "little":
        values.byteswap()
    if not all(math.isfinite(v) for v in values) or not any(values):
        raise ValueError(f"{path}: non-finite or empty LUT")
    return values


def identity(size):
    # Planar RGB; r varies fastest, then g, then b. Matches upstream SC32Lut4.
    values = array("f", (coordinate / (size - 1)
                         for channel in range(3)
                         for b in range(size)
                         for g in range(size)
                         for r in range(size)
                         for coordinate in ((r, g, b)[channel],)))
    if sys.byteorder != "little":
        values.byteswap()
    return values.tobytes()


def prepare(tables, settings, destination):
    tables, settings, destination = map(Path, (tables, settings, destination))
    required = {}
    for name in SETTINGS:
        config = configparser.ConfigParser()
        with (settings / name).open(encoding="utf-8-sig") as stream:
            config.read_file(stream)
        for section, key, prefix in (("SCLut", "SCLutNum", "SC"),
                                     ("CMCWLut", "CMCWLutNum", "CWCM")):
            size = config.getint(section, "lut_size")
            if size not in (16, 32) or config.getint(section, key) != 9:
                raise ValueError(f"{name}: unsupported {section} grid/count")
            for i in range(9):
                required[f"{prefix}{size}Lut{i}"] = size
        for section in ("switch", "forAppSwitch"):
            if section == "switch" and (config.getint(section, "LMT") != 0 or config.getint(section, "ODT") != 0):
                raise ValueError(f"{name}: this port only supplies SC/CWCM, not LMT/ODT")
            if config.getint(section, "SC") != 1 or config.getint(section, "CWCM") != 1:
                raise ValueError(f"{name}: SC/CWCM adjustments must be enabled")
    for name, size in required.items():
        read_cube(tables / name, size)
    # Detect interleaved/axis-swapped input before a generated BaseLut reaches a camera.
    for size in set(required.values()):
        neutral = read_cube(tables / f"SC{size}Lut4", size)
        expected = array("f")
        expected.frombytes(identity(size))
        if sys.byteorder != "little":
            expected.byteswap()
        if max(abs(a - b) for a, b in zip(neutral, expected)) > 1e-4:
            raise ValueError(f"SC{size}Lut4 does not use the expected neutral RGB layout")
    lmt, retro = destination / "lmt", destination / "setting_Retro"
    lmt.mkdir(parents=True, exist_ok=True)
    retro.mkdir(parents=True, exist_ok=True)
    for name in required:
        shutil.copyfile(tables / name, lmt / name)
    (lmt / "BaseLut").write_bytes(identity(64))
    for name in SETTINGS:
        shutil.copyfile(settings / name, retro / name)
    print(f"POP: staged {len(required)} SC/CWCM tables, BaseLut and both CombineLut settings")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tables", type=Path)
    parser.add_argument("settings", type=Path)
    parser.add_argument("destination", type=Path, help="mounted basictone directory")
    args = parser.parse_args()
    prepare(args.tables, args.settings, args.destination)
