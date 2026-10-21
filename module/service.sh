#!/system/bin/sh
# Stages the data the camera and gallery read from their own private files.
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

for OFF in /data/adb/omnicam_off /data/adb/omnicam_nostack_off /data/adb/omnicam_fullport_off; do
    [ -f "$OFF" ] && exit 0
done

PAYLOAD=$MODDIR/payload
APP=/data/user/0/com.oplus.camera
GALLERY=/data/user/0/com.coloros.gallery3d
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

wait_for "$APP" 120 || exit 0

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

# GR profile: the device's own LMT tree with the GR adjustment tables (SC16/SC32/CWCM) swapped in.
# Every other entry is copied from /odm as-is, so the profile stays valid for this sensor set.
if [ -d "$PAYLOAD/gr_adjustment_lmt" ] && [ -d /odm/etc/camera/basictone/lmt ]; then
    OUT=$APP/files/ricoh_gr/lmt
    mkdir -p "$OUT"
    chown "$APP_UID:$APP_UID" "$APP/files"
    chmod 0770 "$APP/files"
    for ORIGINAL in /odm/etc/camera/basictone/lmt/*; do
        NAME=${ORIGINAL##*/}
        case "$NAME" in
            LMTPhotoLut*|SCLut*)
                # Palette LUTs stay in the shared ODM tree; GR uses its own profile.
                continue
                ;;
            SC16*|SC32*|CWCM*)
                [ -f "$PAYLOAD/gr_adjustment_lmt/$NAME" ] && cp "$PAYLOAD/gr_adjustment_lmt/$NAME" "$OUT/$NAME"
                ;;
            *)
                if [ -L "$OUT/$NAME" ]; then rm "$OUT/$NAME"; fi
                if [ -d "$ORIGINAL" ]; then
                    mkdir -p "$OUT/$NAME"
                    cp -R "$ORIGINAL/." "$OUT/$NAME/"
                else
                    cp "$ORIGINAL" "$OUT/$NAME"
                fi
                ;;
        esac
    done
    chown -R "$APP_UID:$APP_UID" "$APP/files/ricoh_gr"
fi

# POP resources. The mode itself is reached through the config profile, but three things it draws
# with are not in this camera APK: the Polaroid watermark (PLD), the retro UI animations and the
# guide images. They are staged here and PopResources points the camera at them; without them the
# hook bails out with "POP PLD resources missing" and the retro UI comes up with nothing to paint.
#
# setting_Retro is staged too because pop_path_remap.cpp serves /odm/etc/camera/basictone/setting_Retro
# from here when the merged /odm tree has no copy; meishe_lut is not duplicated — post-fs-data.sh
# already merges the whole tree into /odm, so a second 5 MB copy would only be served by a fallback
# that never runs.
POP_ASSET=$APP/files/pop_port
if [ -d "$PAYLOAD/pld_watermark" ] || [ -d "$PAYLOAD/apk_resources" ]; then
    mkdir -p "$POP_ASSET/pld_watermark" "$POP_ASSET/apk_resources/json" \
        "$POP_ASSET/apk_resources/img" "$POP_ASSET/setting_Retro"
    [ -d "$PAYLOAD/pld_watermark" ] && cp -a "$PAYLOAD/pld_watermark/." "$POP_ASSET/pld_watermark/"
    [ -d "$PAYLOAD/apk_resources" ] && cp -a "$PAYLOAD/apk_resources/." "$POP_ASSET/apk_resources/"
    if [ -d "$PAYLOAD/setting_Retro" ]; then
        cp -f "$PAYLOAD/setting_Retro"/* "$POP_ASSET/setting_Retro/" 2>/dev/null || true
    fi
    chown -R "$APP_UID:$APP_UID" "$POP_ASSET"
    find "$POP_ASSET" -type d -exec chmod 0755 {} \;
    find "$POP_ASSET" -type f -exec chmod 0644 {} \;
fi

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

# Camera-side GR watermark styles.
CAM_ML=$APP/files/mode_limit_watermark
if [ -d "$PAYLOAD/watermark_master_styles" ]; then
    mkdir -p "$CAM_ML"
    for i in 1 2 3 4 5; do
        ID=gr_style_$i
        NAME=$ID.json
        JSON=$PAYLOAD/watermark_master_styles/$NAME
        if [ -f "$JSON" ]; then
            mkdir -p "$CAM_ML/$ID"
            cp -f "$JSON" "$CAM_ML/$ID/$NAME"
            cp -f "$JSON" "$CAM_ML/$NAME"
            if [ -d "$PAYLOAD/watermark_drawables" ]; then
                cp -f "$PAYLOAD/watermark_drawables/"*.webp "$CAM_ML/$ID/" 2>/dev/null || true
            fi
        fi
    done
    if [ -d "$PAYLOAD/watermark_drawables" ]; then
        cp -f "$PAYLOAD/watermark_drawables/"*.webp "$CAM_ML/" 2>/dev/null || true
    fi
    chown -R "$APP_UID:$APP_UID" "$CAM_ML"
    find "$CAM_ML" -type f -exec chmod 0644 {} \;
    find "$CAM_ML" -type d -exec chmod 0755 {} \;
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
