#!/system/bin/sh
# OmniCam Muse — 真我 GT7 Pro 适配用设备信息采集脚本（需要 root）
#
# 用法（Windows）：
#   adb push 采集设备信息.sh /data/local/tmp/collect.sh
#   adb shell su -c "sh /data/local/tmp/collect.sh"
#   产物：/sdcard/omnicam_gt7pro_dump.tar.gz
#   adb pull /sdcard/omnicam_gt7pro_dump.tar.gz
#
# 采集内容 = 把模块 payload 从「Find X9 Ultra 的」换成「你这台机器的」所需的全部原始文件：
#   /odm 相机配置、/odm 原生库与 ISP 调校、相机/相册 APK、相机与相册的开关状态。

OUT=/data/local/tmp/omnicam_dump
TAR=/sdcard/omnicam_gt7pro_dump.tar.gz
CAM_APP=/data/user/0/com.oplus.camera
GAL_APP=/data/user/0/com.coloros.gallery3d

rm -rf "$OUT"
mkdir -p "$OUT/odm_camera_config" "$OUT/odm_camera" "$OUT/odm_lib64" "$OUT/odm_tuning" \
         "$OUT/apps" "$OUT/prefs" "$OUT/props"

say() { echo "[dump] $1"; }

# --- 1. 机型与版本 -------------------------------------------------------------------------
say "props"
getprop > "$OUT/props/getprop.txt" 2>&1
{
    echo "model=$(getprop ro.product.model)"
    echo "device=$(getprop ro.product.device)"
    echo "build=$(getprop ro.build.display.id)"
    echo "android=$(getprop ro.build.version.release) sdk=$(getprop ro.build.version.sdk)"
    echo "first_api_level=$(getprop ro.product.first_api_level)"
    echo "rom=$(getprop ro.build.version.oplusrom) / $(getprop ro.oplus.version)"
} > "$OUT/props/summary.txt" 2>&1

# --- 2. /odm 相机配置（模块要 bind 的那几个文件的「原件」）--------------------------------
say "odm camera config"
for f in camera_unit_config camera_unit_feature_config.protobuf oplus_camera_config \
         oplus_camera_algo_switch_config oplus_camera_aps_config \
         oplus_camera_preview_decision_config.json; do
    [ -f "/odm/etc/camera/config/$f" ] && cp -a "/odm/etc/camera/config/$f" "$OUT/odm_camera_config/" 2>>"$OUT/errors.txt"
done
ls -l /odm/etc/camera/config/ > "$OUT/odm_camera_config/_listing.txt" 2>&1

# --- 3. /odm/etc/camera 下的资源目录 -------------------------------------------------------
say "odm camera resources"
ls -lR /odm/etc/camera > "$OUT/odm_camera/_listing.txt" 2>&1
for d in meishe_lut basictone gamma filters_lut pld_watermark; do
    [ -d "/odm/etc/camera/$d" ] && cp -a "/odm/etc/camera/$d" "$OUT/odm_camera/" 2>>"$OUT/errors.txt"
done
for f in CameraHWConfiguration.config; do
    [ -f "/odm/etc/camera/$f" ] && cp -a "/odm/etc/camera/$f" "$OUT/odm_camera/" 2>>"$OUT/errors.txt"
done
[ -f /odm/etc/audio/audio_policy_volumes.xml ] && cp -a /odm/etc/audio/audio_policy_volumes.xml "$OUT/odm_camera/" 2>>"$OUT/errors.txt"

# --- 4. 被模块替换的原生库 + ISP 调校 ------------------------------------------------------
say "odm libs"
for f in libAlgoInterface.so libBasicTonePhoto.so libBasicTonePhotoX9.so libBasicTonePhotoX10.so \
         libmsnativefilter.so libCombineLut.so libCombineLutJni.so libOplusSecurity.so; do
    [ -f "/odm/lib64/$f" ] && cp -a "/odm/lib64/$f" "$OUT/odm_lib64/" 2>>"$OUT/errors.txt"
done
ls -l /odm/lib64/ | grep -iE "algo|basictone|msnative|combinelut|security" > "$OUT/odm_lib64/_listing.txt" 2>&1
ls -l /odm/lib64/camera/ > "$OUT/odm_tuning/_listing.txt" 2>&1
for f in /odm/lib64/camera/com.qti.tuned.*.bin; do
    [ -f "$f" ] && cp -a "$f" "$OUT/odm_tuning/" 2>>"$OUT/errors.txt"
done

# --- 5. 相机 / 相册 APK --------------------------------------------------------------------
say "apps"
for pkg in com.oplus.camera com.coloros.gallery3d; do
    p=$(pm path "$pkg" 2>/dev/null | sed -n 's/^package://p' | head -n 1)
    [ -n "$p" ] && cp -a "$p" "$OUT/apps/$(basename "$p")" 2>>"$OUT/errors.txt"
    dumpsys package "$pkg" 2>/dev/null | grep -E "versionName|versionCode|firstInstallTime" > "$OUT/apps/$pkg.txt"
done

# --- 6. 相机 / 相册的开关状态 --------------------------------------------------------------
say "prefs"
[ -f "$CAM_APP/shared_prefs/override_config_data.xml" ] && cp -a "$CAM_APP/shared_prefs/override_config_data.xml" "$OUT/prefs/" 2>>"$OUT/errors.txt"
[ -f "$GAL_APP/shared_prefs/business_featureSwitch_config.xml" ] && cp -a "$GAL_APP/shared_prefs/business_featureSwitch_config.xml" "$OUT/prefs/" 2>>"$OUT/errors.txt"
# 模式列表数据库：只读一份副本，用来确认这台机器有哪些模式（GR / tiltShift / xpan ...）
[ -f "$CAM_APP/databases/mode_data.db" ] && cp -a "$CAM_APP/databases/mode_data.db" "$OUT/prefs/" 2>>"$OUT/errors.txt"
ls -l "$CAM_APP/shared_prefs/" > "$OUT/prefs/_cam_prefs_listing.txt" 2>&1
ls -l "$CAM_APP/files/" > "$OUT/prefs/_cam_files_listing.txt" 2>&1
ls -l "$CAM_APP/databases/" > "$OUT/prefs/_cam_db_listing.txt" 2>&1

# --- 7. 环境（root 方案）-------------------------------------------------------------------
say "env"
{
    echo "--- ksu";       [ -d /data/adb/ksu ] && echo yes || echo no
    echo "--- magisk";    [ -d /data/adb/magisk ] && echo yes || echo no
    echo "--- apatch";    [ -d /data/adb/ap ] && echo yes || echo no
    echo "--- lsposed";   ls -d /data/adb/lspd 2>/dev/null || echo no
    echo "--- modules";   ls /data/adb/modules 2>/dev/null
    echo "--- busybox";   ls -l /data/adb/ksu/bin/busybox 2>/dev/null || echo no
} > "$OUT/env.txt" 2>&1

# --- 8. 打包 -------------------------------------------------------------------------------
say "packing"
rm -f "$TAR"
tar -czf "$TAR" -C /data/local/tmp omnicam_dump 2>>"$OUT/errors.txt"
chmod 0644 "$TAR"
echo
echo "[dump] 完成：$TAR"
echo "[dump] 大小：$(ls -l "$TAR" | awk '{print $5}') 字节"
echo "[dump] 电脑上执行： adb pull $TAR"
