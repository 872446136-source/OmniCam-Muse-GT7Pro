#!/usr/bin/env python3
"""Build the RMX5010 camera_unit_config profile: this device's own table plus the modes its
firmware ships but never lists, plus the two the B6 port proved the HAL accepts.

Reads the profile already in the module, adds the entries, and writes both copies (the module
profile that service.sh stages and the APK asset that ConfigProfile.ensure copies out).

    python tools/make_mode_profile.py

What is added, and why:

* ``movie_mode`` — the operation mode behind the log video / LUT monitor page. GT7 Pro ships
  ``80A3`` wired to ``master_video_mode`` instead, so its master video is the 30 fps film pipeline
  and there is no log page at all. The B6 port and the X8U both put ``800b`` on master video and
  ``80A3`` on a separate movie mode; that is the arrangement that gives log at 60 fps, because
  ``800b`` is the ordinary recorder op mode.
* ``high_pixel_mode`` — 50 MP / Hasselblad super definition. The feature table for it
  (``high_pixel_mode_featureTable``) was already in this profile, unused.
* ``gr_mode`` / ``retro_camera_mode`` / ``xpan_mode`` / ``tilt_shift_mode`` — kept as they were.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROFILE = ROOT / "module/profiles/RMX5010/config/camera_unit_config"
ASSET = ROOT / "app/src/main/assets/profile/RMX5010/camera_unit_config"

# Modes that only make sense on the rear cameras. front_main deliberately keeps its list.
#
# rear_sat is included because 474 already put gr_mode and retro_camera_mode there and it worked:
# the list is what the mode picker may offer per camera group, not what the fusion path runs.
REAR_LISTS = ["rear_main", "rear_tele", "rear_wide", "rear_sat"]
ADD_TO_REAR = ["movie_mode", "high_pixel_mode"]


def add_mode(config, mode, operation_mode, feature=None):
    config["mode_operation_mode"][mode] = operation_mode
    if feature is not None:
        config["feature_operation_mode"][mode] = feature
    # Idempotent: a previous run may have put the mode in lists this run does not want.
    for name, modes in config["mode_type_list"].items():
        while mode in modes:
            modes.remove(mode)
    for name in REAR_LISTS:
        config["mode_type_list"][name].append(mode)


def main():
    config = json.loads(PROFILE.read_text(encoding="utf-8"))

    # Master video moves off the film op mode so 60 fps is reachable; the film pipeline keeps 80A3
    # under its own mode name.
    config["mode_operation_mode"]["master_video_mode"] = "800b"

    add_mode(config, "movie_mode", "80A3",
             {"stabilization": {"video_stabilization": "80a4"}})
    add_mode(config, "high_pixel_mode", "8001",
             {"aiHighPixel": {"on": "8001"}})

    # The feature tables the two new modes read. high_pixel_mode_featureTable already exists; the
    # movie mode reuses the master video table, which is why it needs no new entry.
    assert "high_pixel_mode_featureTable" in config, "high pixel table missing"
    assert "master_video_mode_featureTable" in config, "master video table missing"

    text = json.dumps(config, ensure_ascii=False, indent=2).replace("\n", "\r\n") + "\r\n"
    for target in (PROFILE, ASSET):
        target.write_bytes(text.encode("utf-8"))
        print(f"wrote {target.relative_to(ROOT)}  {len(text)}B")

    print("mode_operation_mode.master_video_mode =", config["mode_operation_mode"]["master_video_mode"])
    print("mode_operation_mode.movie_mode        =", config["mode_operation_mode"]["movie_mode"])
    print("mode_operation_mode.high_pixel_mode   =", config["mode_operation_mode"]["high_pixel_mode"])
    for name in REAR_LISTS:
        print(f"  {name}: {config['mode_type_list'][name]}")


if __name__ == "__main__":
    main()
