#!/system/bin/sh
# Stages the data the camera and gallery read from their own private files.
#
# The POP resources, the GR tone tables and the GR watermark styles are built by post-fs-data.sh
# into the merged basictone tree, because that tree needs no unlock and no wait. Since 484 this
# script also keeps a second copy of that layer under the camera's own files (see "Camera-side copy"
# below): the merged tree is a module mount, and KernelSU can unmount module mounts for the camera
# app alone, which is precisely what the 483 report shows.
#
# Everything here is additive: files are written into the apps' data directories, never over /odm.
# Each block is skipped when its payload is absent, so a trimmed build simply does less instead of
# failing halfway.
#
# No restorecon on anything under /data/user/0/<pkg>: running it there replaces the app's SELinux
# categories with the ones in file_contexts, which have none, and the app is then denied the files
# this script just wrote. New files inherit the parent directory's context (categories included), so
# chown and chmod are all that is needed. The one chcon below copies a directory's own context.
#
# Kill switch: touch /data/adb/omnicam_off, then reboot.

MODDIR=${0%/*}
APP=/data/user/0/com.oplus.camera
GALLERY=/data/user/0/com.coloros.gallery3d

case "${1:-}" in
--wait)
    # Detached pass, started below when the camera's data directory is not up yet.
    #
    # /data/user/0/<pkg> only appears once the user unlocks: this device is file-based encrypted
    # (ro.crypto.type=file) and KernelSU runs service.sh at late_start, which on a locked boot is
    # well before that. 481 and every version before it waited 120 seconds, gave up, and exited —
    # silently, because there was nothing to log to. The camera-side data no longer depends on this
    # script, but the master-colour tables, the gallery watermark styles and the gallery's feature
    # switch still do, so keep trying.
    N=0
    while [ ! -d "$APP" ] && [ "$N" -lt 720 ]; do
        sleep 10
        N=$((N + 1))
    done
    [ -d "$APP" ] || exit 0
    ;;
esac

for OFF in /data/adb/omnicam_off /data/adb/omnicam_nostack_off /data/adb/omnicam_fullport_off; do
    [ -f "$OFF" ] && exit 0
done

PAYLOAD=$MODDIR/payload
GR_AVAILABLE='0.6(14),1(23),2(47),3(70),6(139),10(230)'
GR_MARKED='1.2173913(28),1.7391304(40)'

# A bounded wait: a boot must not stall forever if the camera is not installed.
wait_for() {
    DIR=$1
    LIMIT=$2
    N=0
    while [ ! -d "$DIR" ] && [ "$N" -lt "$LIMIT" ]; do
        sleep 1
        N=$((N + 1))
    done
    [ -d "$DIR" ]
}

if ! wait_for "$APP" 60; then
    # Not installed at all, or still locked. Do not give up: hand the whole staging run to a detached
    # copy of this script that keeps waiting for the unlock. Nothing here blocks the boot.
    if command -v setsid > /dev/null 2>&1; then
        setsid sh "$MODDIR/service.sh" --wait > /dev/null 2>&1 &
    else
        (sh "$MODDIR/service.sh" --wait > /dev/null 2>&1) &
    fi
    exit 0
fi

APP_UID=$(stat -c %u "$APP")
PREF_DIR=$APP/shared_prefs
PREF_FILE=$PREF_DIR/override_config_data.xml

mkdir -p "$PREF_DIR"
chown "$APP_UID:$APP_UID" "$PREF_DIR"
chmod 0770 "$PREF_DIR"

if [ ! -f "$PREF_FILE" ]; then
    printf '%s\n' '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>' '<map>' '</map>' > "$PREF_FILE"
fi

set_config() {
    KEY=$1
    VALUE=$2
    if grep -q "name=\"$KEY\"" "$PREF_FILE"; then
        sed -i "s#<string name=\"$KEY\">[^<]*</string>#<string name=\"$KEY\">$VALUE</string>#" "$PREF_FILE"
    else
        sed -i "/<\/map>/i\    <string name=\"$KEY\">$VALUE</string>" "$PREF_FILE"
    fi
}

# GR mode is enabled through the app's own feature switch. The HDR capture list names only modes that
# have a decision tree on this device: retroCamera is left out (its HDR support is unverified), xpan is
# not listed because its HDR support is unverified even though the mode itself is now reachable.
set_config com.oplus.gr.mode.support 1
# POP: the mode row in mode_data.db is written only when this flag is on (ModeDataSQLiteOpenHelper
# i()/j()), and the module stages the setting_Retro basictone it renders with.
set_config com.oplus.feature.retro.camera.support 1
set_config com.oplus.camera.mode.data.db.version 102
set_config com.oplus.available.gr.mode.zoomvalues "$GR_AVAILABLE"
set_config com.oplus.gr.mode.marked.zoomvalues "$GR_MARKED"
set_config com.oplus.camera.capture.hdr.cap.mode.value 'common,portrait,professional,night,highPixel,underWater,telephoto,gr'
set_config com.oplus.camera.preview.hdr.cap.mode.value 'common,professional,night,highPixel,underWater,telephoto,gr'
# XPAN: the firmware ships the mode, the decision-tree nodes and the crop parameters, but the support
# flag in oplus_camera_config is 0. Overriding the VendorTag here leaves the config file untouched.
set_config com.oplus.feature.xpan.mode.support 1

chown "$APP_UID:$APP_UID" "$PREF_FILE"
chmod 0660 "$PREF_FILE"

# The config profile (this device's own camera_unit_config with the modes its firmware ships but
# never lists) is NOT staged here. It is copied out of the module APK by the camera process itself
# (ConfigProfile.ensure), because only the app can write its own files with the app's SELinux
# categories: a root shell writing into /data/user/0/<pkg>/files produces files the app is denied,
# and before the first unlock it cannot write there at all. Nothing is mounted either way — the
# native path hooks serve the copy to the camera process only, so /odm stays untouched.

# Master colour (大师色彩): the engine data, scene profiles and look tables. Baked preview tables
# (files/jiege/mastercolor/live) come from the camera and are rebuilt when the module changes; the
# selected look survives the refresh.
MASTER_COLOR=$APP/files/jiege/mastercolor
if [ -d "$PAYLOAD/master_color" ]; then
    MASTER_COLOR_STAMP=$(grep '^versionCode=' "$MODDIR/module.prop")
    if [ "$(cat "$MASTER_COLOR/stamp" 2>/dev/null)" != "$MASTER_COLOR_STAMP" ] \
        || ! cmp -s "$PAYLOAD/master_color/hncs_engine.bin" "$MASTER_COLOR/hncs_engine.bin" \
        || ! cmp -s "$PAYLOAD/master_color/scene.bin" "$MASTER_COLOR/scene.bin" \
        || ! diff -rq "$PAYLOAD/master_color/looks" "$MASTER_COLOR/looks" >/dev/null 2>&1; then
        LOOK=$(cat "$MASTER_COLOR/look" 2>/dev/null)
        rm -rf "$MASTER_COLOR"
        mkdir -p "$MASTER_COLOR"
        cp "$PAYLOAD/master_color/hncs_engine.bin" "$MASTER_COLOR/hncs_engine.bin"
        cp "$PAYLOAD/master_color/scene.bin" "$MASTER_COLOR/scene.bin"
        cp -r "$PAYLOAD/master_color/looks" "$MASTER_COLOR/looks"
        echo "$MASTER_COLOR_STAMP" > "$MASTER_COLOR/stamp"
        [ -n "$LOOK" ] && echo "$LOOK" > "$MASTER_COLOR/look"
    fi
    mkdir -p "$MASTER_COLOR/live"
    chmod 0771 "$MASTER_COLOR" "$MASTER_COLOR/live" 2>/dev/null || true
fi

# GR watermark fonts: the styles name their fonts by URL, which the camera cannot load. Fonts the
# gallery already downloaded are copied where the GR hook looks for them.
GALLERY_FONTS=$GALLERY/files/watermark_master/materia
mkdir -p "$APP/files/jiege/fonts"
for FONT in GilroySemiBold.otf FZLTZCHK.TTF; do
    if [ -f "$GALLERY_FONTS/$FONT" ] && ! cmp -s "$GALLERY_FONTS/$FONT" "$APP/files/jiege/fonts/$FONT"; then
        cp "$GALLERY_FONTS/$FONT" "$APP/files/jiege/fonts/$FONT"
    fi
done
chown -R "$APP_UID:$APP_UID" "$APP/files/jiege" 2>/dev/null || true
chmod 0771 "$APP/files/jiege" 2>/dev/null || true

# Camera-side copy of the basictone payload — the reason 484 exists.
#
# post-fs-data.sh stages setting_Retro / lmt_gr / pop_port / mode_limit_watermark into a bind mount
# over /odm/etc/camera/basictone. That is a *module* mount, and KernelSU's per-app 卸载模块 (or
# Zygisk Next's denylist, which the 10007 ad-block module turns on at each boot) removes module
# mounts from one app's process namespace. So the camera alone stops seeing the tree while the
# settings app still reads it: the 483 report is exactly that state, data=false / retro=false /
# grSc=-1 with every data-layer feature off, and POP went black while GR hung.
#
# App private files are not a module mount, so nothing can hide them from the camera. This copies
# the same four directories next to the profile, and both the native and the Java side resolve them
# from there when the merged tree is missing. ~42 MB, and only when the module version changed.
#
# Source order matters. merged/basictone_extra is the layer post-fs-data.sh already composed, so it
# is used when it exists. When it does not — that script did not run this boot, which is one of the
# two ways the 483 report can be explained — the same composition is done here from payload/ and the
# device's own setting/ tree, because a copy of nothing is nothing.
BASICTONE_DST=$APP/files/jiege/basictone
BASICTONE_SRC=$MODDIR/merged/basictone_extra
STAMP=$(grep '^versionCode=' "$MODDIR/module.prop")

compose_basictone() {
    OUT=$1
    SRC=$2
    rm -rf "$OUT"
    mkdir -p "$OUT"
    if [ -d "$SRC" ]; then
        for NAME in setting_Retro lmt_gr pop_port mode_limit_watermark; do
            [ -d "$SRC/$NAME" ] || continue
            # Not cp -a: that carries the merged tree's vendor_configs_file label into app data,
            # where the camera is then denied. New files must inherit files/jiege's context.
            cp -r "$SRC/$NAME" "$OUT/$NAME"
        done
        echo "basictone copy from $SRC" >> "$MODDIR/bind.log"
        return 0
    fi
    # setting_Retro: the device's own setting/ tree (SimTool.ini, Ditherout/, FromXml/), then the
    # payload on top. The retro engine opens exactly "<setting dir>/SimTool.ini".
    mkdir -p "$OUT/setting_Retro"
    [ -d /odm/etc/camera/basictone/setting ] && cp -r /odm/etc/camera/basictone/setting/. "$OUT/setting_Retro"/
    [ -d "$PAYLOAD/setting_Retro" ] && cp -r "$PAYLOAD/setting_Retro"/. "$OUT/setting_Retro"/
    # lmt_gr: the nine 64^3 SCLut tables, plus the two LMT tables from the stock lmt directory.
    mkdir -p "$OUT/lmt_gr"
    for NAME in LMTLut0 LMTLut1; do
        [ -f "/odm/etc/camera/basictone/lmt/$NAME" ] && cp -f "/odm/etc/camera/basictone/lmt/$NAME" "$OUT/lmt_gr/$NAME"
    done
    [ -d "$PAYLOAD/gr_lmt64" ] && cp -f "$PAYLOAD/gr_lmt64"/* "$OUT/lmt_gr"/
    # pop_port: the Polaroid watermark resources and the retro UI animations / guide images.
    mkdir -p "$OUT/pop_port"
    for NAME in pld_watermark apk_resources; do
        [ -d "$PAYLOAD/$NAME" ] && cp -r "$PAYLOAD/$NAME" "$OUT/pop_port/$NAME"
    done
    # mode_limit_watermark: the five GR watermark styles and their drawables.
    mkdir -p "$OUT/mode_limit_watermark"
    if [ -d "$PAYLOAD/watermark_master_styles" ]; then
        for I in 1 2 3 4 5; do
            ID=gr_style_$I
            JSON=$PAYLOAD/watermark_master_styles/$ID.json
            [ -f "$JSON" ] || continue
            mkdir -p "$OUT/mode_limit_watermark/$ID"
            cp -f "$JSON" "$OUT/mode_limit_watermark/$ID/$ID.json"
            cp -f "$JSON" "$OUT/mode_limit_watermark/$ID.json"
            [ -d "$PAYLOAD/watermark_drawables" ] && cp -f "$PAYLOAD/watermark_drawables/"*.webp "$OUT/mode_limit_watermark/$ID/" 2>/dev/null
        done
        [ -d "$PAYLOAD/watermark_drawables" ] && cp -f "$PAYLOAD/watermark_drawables/"*.webp "$OUT/mode_limit_watermark/" 2>/dev/null
    fi
    echo "basictone copy composed from payload" >> "$MODDIR/bind.log"
}

if [ "$(cat "$BASICTONE_DST/stamp" 2>/dev/null)" != "$STAMP" ]; then
    compose_basictone "$BASICTONE_DST" "$BASICTONE_SRC"
    echo "$STAMP" > "$BASICTONE_DST/stamp"
    echo "basictone copy: gr=$(ls "$BASICTONE_DST/lmt_gr" 2>/dev/null | wc -l) retro=$(ls "$BASICTONE_DST/setting_Retro" 2>/dev/null | wc -l) pop=$(ls "$BASICTONE_DST/pop_port" 2>/dev/null | wc -l) wm=$(ls "$BASICTONE_DST/mode_limit_watermark" 2>/dev/null | wc -l)" >> "$MODDIR/bind.log"
fi
chown -R "$APP_UID:$APP_UID" "$BASICTONE_DST" 2>/dev/null || true
find "$BASICTONE_DST" -type d -exec chmod 0771 {} \; 2>/dev/null || true
find "$BASICTONE_DST" -type f -exec chmod 0644 {} \; 2>/dev/null || true
APP_CONTEXT=$(ls -dZ "$APP/files/jiege" 2>/dev/null | awk '{print $1}')
if [ -n "$APP_CONTEXT" ]; then
    chcon -R "$APP_CONTEXT" "$BASICTONE_DST" 2>/dev/null || true
fi

wait_for "$GALLERY" 120 || exit 0
GALLERY_UID=$(stat -c %u "$GALLERY")
GALLERY_PREF=$GALLERY/shared_prefs/business_featureSwitch_config.xml
N=0
while [ ! -f "$GALLERY_PREF" ] && [ "$N" -lt 120 ]; do
    sleep 1
    N=$((N + 1))
done
if [ -f "$GALLERY_PREF" ]; then
    if grep -q 'name="business_featureSwitch_is_camera_gr_supported"' "$GALLERY_PREF"; then
        sed -i 's/name="business_featureSwitch_is_camera_gr_supported" value="false"/name="business_featureSwitch_is_camera_gr_supported" value="true"/' "$GALLERY_PREF"
    else
        sed -i '/<\/map>/i\    <boolean name="business_featureSwitch_is_camera_gr_supported" value="true" />' "$GALLERY_PREF"
    fi
    chown "$GALLERY_UID:$GALLERY_UID" "$GALLERY_PREF"
    chmod 0660 "$GALLERY_PREF"
fi

# Gallery-side GR watermark styles.
if [ -d "$PAYLOAD/watermark_master_styles" ]; then
    STYLE_OUT=$GALLERY/files/ricoh_gr_styles
    mkdir -p "$STYLE_OUT"
    cp -f "$PAYLOAD/watermark_master_styles/"*.json "$STYLE_OUT/" 2>/dev/null || true
    if [ -d "$PAYLOAD/watermark_drawables" ]; then
        cp -f "$PAYLOAD/watermark_drawables/"*.webp "$STYLE_OUT/" 2>/dev/null || true
    fi
    chown -R "$GALLERY_UID:$GALLERY_UID" "$STYLE_OUT"
    chmod 0644 "$STYLE_OUT"/* 2>/dev/null || true
fi

# Camera unit SDK with the palette classes (CombineLut, PaletteColorTable) for the gallery's palette
# editor; the gallery hook loads this copy in place of the system one. Dex must be read-only.
IPU_JAR=$PAYLOAD/ipu/com.oplus.camera.unit.sdk.jar
if [ -f "$IPU_JAR" ]; then
    IPU_OUT=$GALLERY/files/jiege_ipu
    mkdir -p "$IPU_OUT"
    cmp -s "$IPU_JAR" "$IPU_OUT/com.oplus.camera.unit.sdk.jar" || cp -f "$IPU_JAR" "$IPU_OUT/"
    chown "$GALLERY_UID:$GALLERY_UID" "$IPU_OUT"
    chmod 0711 "$IPU_OUT"
    chown 0:0 "$IPU_OUT/com.oplus.camera.unit.sdk.jar"
    chmod 0444 "$IPU_OUT/com.oplus.camera.unit.sdk.jar"
    chcon "$(ls -dZ "$IPU_OUT" | awk '{print $1}')" "$IPU_OUT/com.oplus.camera.unit.sdk.jar" 2>/dev/null || true
fi
