#!/usr/bin/env python3
"""Build the RMX5010 (真我 GT7 Pro) config profile from that device's own stock files.

Two features are present in the firmware but not offered, because the mode list never names them.
Everything else they need is already there:

  tilt-shift   mode_operation_mode.tilt_shift_mode = 8001, aps_algo_tilt_shift = 1 in the algo switch,
               SINGLE_ALGO_TILT_SHIFT in the preview decision tree, and the crop parameters.
  xpan         capture_stream_number.xpan_mode, APS_CAPMODE_XPAN nodes in the decision tree, the
               APS XPAN tags, and the 8000x3636 picture size / crop regions in oplus_camera_config.

So the profile only has to add the mode entries and flip one support flag. Nothing is invented: the
xpan operation code and use case come from a sibling device's stock config that does ship XPAN
(Find X8 Ultra, PKU110), and the shape of every entry is copied from the GT7 Pro file itself.

    python make_profile.py <stock-odm>/etc/camera/config <libOplusSecurity.so> <out-dir>

The stock directory is what the module dumps from the phone: /odm/etc/camera/config.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import oplus_config  # noqa: E402

# Where each lens should offer the new modes, copied from the PKU110 layout.
TILT_SHIFT_LENSES = ("rear_wide", "rear_main", "rear_tele", "front_main", "rear_sat")
XPAN_LENSES = ("rear_main",)
# PKU110 stock: xpan_mode -> 80AC, and the use case is the generic var_camera trio.
XPAN_OPERATION_MODE = "80AC"
XPAN_USECASE = [
    {"preview": "var_camera"},
    {"capture_raw10_fullsize": "var_camera"},
    {"raw_output": "var_camera"},
]
XPAN_SUPPORT_TAG = "com.oplus.feature.xpan.mode.support"


def insert_after(items, mode, anchor):
    """Puts `mode` right after `anchor` so the list keeps the firmware's grouping."""
    if mode in items:
        return False
    items.insert(items.index(anchor) + 1 if anchor in items else len(items), mode)
    return True


def patch_unit_config(text):
    unit = json.loads(text)
    modes = unit["mode_type_list"]
    added = []
    for lens in TILT_SHIFT_LENSES:
        if lens in modes and insert_after(modes[lens], "tilt_shift_mode", "under_water_mode"):
            added.append(f"{lens}.tilt_shift_mode")
    for lens in XPAN_LENSES:
        if lens in modes and insert_after(modes[lens], "xpan_mode", "panorama_mode"):
            added.append(f"{lens}.xpan_mode")

    if "xpan_mode" not in unit["mode_operation_mode"]:
        unit["mode_operation_mode"]["xpan_mode"] = XPAN_OPERATION_MODE
        added.append("mode_operation_mode.xpan_mode")
    if "xpan_case" not in unit["usecase_info"]:
        unit["usecase_info"]["xpan_case"] = XPAN_USECASE
        added.append("usecase_info.xpan_case")

    if not added:
        return text, []
    return json.dumps(unit, ensure_ascii=False, indent=2) + "\n", added


def patch_camera_config(text):
    tags = json.loads(text)
    changed = []
    for tag in tags:
        if tag.get("VendorTag") == XPAN_SUPPORT_TAG and tag.get("Value") != "1":
            tag["Value"] = "1"
            changed.append(f"{XPAN_SUPPORT_TAG} {tag.get('Value')}")
    if not changed:
        return text, []
    return json.dumps(tags, ensure_ascii=False, indent=2) + "\n", changed


def main():
    stock = Path(sys.argv[1])
    security = Path(sys.argv[2])
    out = Path(sys.argv[3])
    key = oplus_config.config_key(security)

    unit_src = stock / "camera_unit_config"
    text, added = patch_unit_config(unit_src.read_text(encoding="utf-8"))
    if added:
        (out / "camera_unit_config").parent.mkdir(parents=True, exist_ok=True)
        (out / "camera_unit_config").write_text(text, encoding="utf-8")
    print(f"camera_unit_config: {len(added)} entries")
    for item in added:
        print("   +", item)

    # The xpan support flag is a VendorTag, and service.sh already overrides those through the app's
    # own override_config_data.xml (that is how GR mode is enabled). So the shipped profile does not
    # touch the encrypted oplus_camera_config at all; rewriting it stays available for a device where
    # the override does not take.
    if "--with-encrypted" in sys.argv:
        cam_src = stock / "oplus_camera_config"
        plain = oplus_config.decrypt(key, cam_src.read_bytes())
        patched, changed = patch_camera_config(plain.decode("utf-8"))
        print(f"oplus_camera_config: {len(changed)} tags")
        for item in changed:
            print("   ~", item)
        if changed:
            packed = oplus_config.encrypt(key, patched.encode("utf-8"), cam_src.read_bytes())
            assert oplus_config.decrypt(key, packed) == patched.encode("utf-8")
            (out / "oplus_camera_config").write_bytes(packed)

    for name in ("camera_unit_config", "oplus_camera_config"):
        path = out / name
        if path.exists():
            print(f"wrote {path}  {path.stat().st_size}B")


if __name__ == "__main__":
    main()
