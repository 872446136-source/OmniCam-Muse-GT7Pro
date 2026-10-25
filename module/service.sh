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
APP=/data/user/0/com.oplus.camera
GALLERY=/data/user/0/com.coloros.gallery3d

case "${1:-}" in
--wait)
    # Detached pass, started below when the camera's data directory is not up yet.
    #
    # /data/user/0/<pkg> only appears once the user unlocks: this device is file-based encrypted
    # (ro.crypto.type=file) and KernelSU runs service.sh at late_start, which on a locked boot is
    # well before that. 481 and every version before it waited 120 seconds, gave up, and exited —
    # silently, because there was nothing to log to. That is why files/pop_port never existed and
    # every report said "service.sh did not reach the POP block", and it is why files/ricoh_gr/lmt
    # was never built either.
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

# GR profile: the device's own LMT tree, then the GR tone tables on top as SCLut0..8.
#
# 481 skipped every SCLut* here and copied nothing from the payload, because the loop is driven by
# what /odm holds and this device has no SC16/SC32/CWCM file at all — those names only ever existed
# in the payload. The profile therefore contained LMTLut0 and LMTLut1 and nothing else, and
# libBasicTonePhoto reads a table it cannot find as an empty one: the merge then runs on zeros, which
# is a black viewfinder and a green frame. GR's first style was the only one that needed no SCLut,
# which is exactly the "only the standard filter works" report.
#
# The tables have to be 64^3, not the payload's 32^3: libBasicTonePhoto.so takes the grid size from
# SimTool.ini (this firmware says lut_size = 64) and then reads a file of that size, so a 32^3 file
# is read as a quarter of a table and the rest of the bytes land in the next channel. tools/gr_lmt.py
# resamples at build time; the payload only ships the 32^3 originals.
if [ -d /odm/etc/camera/basictone/lmt ]; then
    OUT=$APP/files/ricoh_gr/lmt
    mkdir -p "$OUT"
    chown "$APP_UID:$APP_UID" "$APP/files"
    chmod 0770 "$APP/files"
    for ORIGINAL in /odm/etc/camera/basictone/lmt/*; do
        NAME=${ORIGINAL##*/}
        if [ -L "$OUT/$NAME" ]; then rm "$OUT/$NAME"; fi
        if [ -d "$ORIGINAL" ]; then
            mkdir -p "$OUT/$NAME"
            cp -R "$ORIGINAL/." "$OUT/$NAME/"
        else
            cp "$ORIGINAL" "$OUT/$NAME"
        fi
    done
    if [ -f /data/adb/omnicam_grsc_off ]; then
        # Escape hatch: keep the device's own SCLut0..8 instead of the GR tone curves. The profile
        # still holds a complete, correctly sized tree, so GR stays usable — it just looks like the
        # normal photo path instead of a film simulation.
        echo "GR tone tables skipped, /data/adb/omnicam_grsc_off is present"
    elif [ -d "$PAYLOAD/gr_lmt64" ]; then
        for SRC in "$PAYLOAD/gr_lmt64"/*; do
            [ -f "$SRC" ] || continue
            NAME=${SRC##*/}
            rm -f "$OUT/$NAME"
            cp "$SRC" "$OUT/$NAME"
        done
    fi
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
POP_STATUS=$POP_ASSET/status.txt
mkdir -p "$POP_ASSET"
: > "$POP_STATUS"

# One payload directory into pop_port, entry by entry, and a line in status.txt either way. The
# 477 report said only "pld is absent" for a payload that the build had verified was in the zip, so
# the copy is now also the thing that explains itself: which directories were visible, how many
# files landed, and whether pld.json survived.
copy_tree() {
    NAME=$1
    SRC=$PAYLOAD/$NAME
    DST=$POP_ASSET/$NAME
    if [ ! -d "$SRC" ]; then
        echo "$NAME: payload absent at $SRC" >> "$POP_STATUS"
        return
    fi
    mkdir -p "$DST"
    # Count what is there before and after: a pipeline's status is the last command's, so a failing
    # tar would still look like success. Every non-directory entry in the source must become a file
    # in the destination — with -h a link becomes a file, which is the point.
    WANT=$(find "$SRC" ! -type d 2>/dev/null | wc -l)
    # tar -h, not cp -aL. With -a (archive) cp puts the no-dereference flag back, so -L does not
    # win and a link into the upstream store is copied as a link — which is indistinguishable from
    # "never copied" once the camera looks for the file. tar -h dereferences unconditionally.
    tar -chf - -C "$SRC" . 2>/dev/null | tar -xf - -C "$DST" 2>/dev/null
    GOT=$(find "$DST" -type f 2>/dev/null | wc -l)
    if [ "$GOT" -lt "$WANT" ]; then
        cp -rfL "$SRC/." "$DST/" 2>>"$POP_STATUS"
        GOT=$(find "$DST" -type f 2>/dev/null | wc -l)
        echo "$NAME: tar left $GOT of $WANT, cp -rfL retried" >> "$POP_STATUS"
    else
        echo "$NAME: copied $GOT files (wanted $WANT)" >> "$POP_STATUS"
    fi
}

if [ -d "$PAYLOAD" ]; then
    copy_tree pld_watermark
    copy_tree apk_resources
    copy_tree setting_Retro
    mkdir -p "$POP_ASSET/apk_resources/json" "$POP_ASSET/apk_resources/img"
    if [ -f "$POP_ASSET/pld_watermark/pld/pld.json" ]; then
        echo "pld.json: present" >> "$POP_STATUS"
    else
        echo "pld.json: MISSING after the copy" >> "$POP_STATUS"
    fi
else
    echo "payload: $PAYLOAD is absent, nothing to stage" >> "$POP_STATUS"
fi

chown -R "$APP_UID:$APP_UID" "$POP_ASSET"
find "$POP_ASSET" -type d -exec chmod 0755 {} \;
find "$POP_ASSET" -type f -exec chmod 0644 {} \;

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
