#!/system/bin/sh
# Mounts the module's data layer only.
#
# Rule for this build: nothing in /odm is replaced. No system library, no camera config, no ISP bin,
# no gamma table. Those were what took the camera down on a device that is not a Find X9 Ultra —
# libAlgoInterface.so from another SoC has different struct layouts, and the X9 config files mean
# nothing to a Qualcomm tuning stack. What is mounted here is additive data only: LUT trees that the
# camera reads by name, and one row-patched hardware config built from the device's own file.
#
# Kill switch: touch /data/adb/omnicam_off, then reboot.
MODDIR=${0%/*}
LOG=$MODDIR/bind.log
set -u

log() {
    echo "$1" >> "$LOG"
}

bind_file() {
    SOURCE="$1"
    TARGET="$2"
    [ -f "$SOURCE" ] || { log "no source $SOURCE"; return 0; }
    [ -e "$TARGET" ] || { log "no target $TARGET"; return 0; }
    chown root:root "$SOURCE"
    chmod 0644 "$SOURCE"
    chcon u:object_r:vendor_configs_file:s0 "$SOURCE" 2>/dev/null || true
    if mount --bind "$SOURCE" "$TARGET" >> "$LOG" 2>&1; then
        log "file bind ok $TARGET"
    else
        log "file bind FAIL $TARGET"
    fi
}

label_tree() {
    DIR=$1
    chown -R root:root "$DIR"
    find "$DIR" -type d -exec chmod 0755 {} \;
    find "$DIR" -type f -exec chmod 0644 {} \;
    find "$DIR" -exec chcon u:object_r:vendor_configs_file:s0 {} \; 2>>"$LOG" || true
}

# Stock tree first, then the payload on top, so the device keeps every LUT it shipped with.
bind_merged_dir() {
    LIVE=$1
    EXTRA=$2
    DEST=$3
    TARGET=$4
    if [ ! -d "$LIVE" ]; then
        log "missing live $LIVE, skip $TARGET"
        return 0
    fi
    rm -rf "$DEST"
    mkdir -p "$DEST"
    cp -a "$LIVE"/. "$DEST"/
    if [ -d "$EXTRA" ]; then
        # Hard links for the payload layer: it is on the same filesystem as DEST, and it is now
        # ~80 MB (X10 tone curves, the nine GR tables, the POP resources). Copying that every boot
        # only to throw it away on the next one is wasted time; a link costs an inode.
        cp -al "$EXTRA"/. "$DEST"/ 2>/dev/null || cp -a "$EXTRA"/. "$DEST"/
    fi
    label_tree "$DEST"
    log "merge $TARGET live=$(ls "$LIVE" | wc -l) merged=$(ls "$DEST" | wc -l)"
    if mount --bind "$DEST" "$TARGET" >> "$LOG" 2>&1; then
        log "dir bind ok $TARGET"
    else
        log "dir bind FAIL $TARGET"
    fi
}

: > "$LOG"
log "begin $(date)"

for OFF in /data/adb/omnicam_off /data/adb/omnicam_nostack_off /data/adb/omnicam_fullport_off; do
    if [ -f "$OFF" ]; then
        log "kill switch $OFF present, nothing mounted"
        exit 0
    fi
done

# Only mount into a tree this module understands. A device without the OPlus camera LUT directory is
# not one of them, and guessing there is how a boot ends up with no viewfinder.
if [ ! -d /odm/etc/camera/meishe_lut ] || [ ! -d /odm/etc/camera/basictone ]; then
    log "not an OPlus camera tree, nothing mounted"
    exit 0
fi

bind_merged_dir /odm/etc/camera/meishe_lut "$MODDIR/payload/meishe_lut" \
    "$MODDIR/merged/meishe_lut" /odm/etc/camera/meishe_lut

# Payload files live on the same filesystem as the merge destination, so link them instead of
# copying: the X10 tone curves (41 MB) and the POP resources (12 MB) would otherwise be rewritten on
# every boot only to be deleted on the next one. Falls back to a copy across filesystems.
stage() {
    SRC=$1
    DST=$2
    mkdir -p "$DST"
    cp -al "$SRC"/. "$DST"/ 2>/dev/null || cp -a "$SRC"/. "$DST"/
}

# basictone: the stock tree plus the X10 tone curves and the retro settings the GR port reads.
BT_EXTRA=$MODDIR/merged/basictone_extra
rm -rf "$BT_EXTRA"
mkdir -p "$BT_EXTRA"
[ -d "$MODDIR/payload/x10_basictone" ] && stage "$MODDIR/payload/x10_basictone" "$BT_EXTRA"
# setting_Retro: this firmware has no such directory at all — /odm/etc/camera/basictone holds only
# lmt / odt / setting / setting_dolby / vig — and the retro BasicTone opens exactly
# "<setting dir>/SimTool.ini" ("%s/SimTool.ini" is the only ini name in libBasicTonePhoto.so, and
# libAlgoInterface.so points that "%s" at ".../basictone/setting_Retro").
#
# 481 created the directory but filled it with upstream's SimTool_Master.ini and
# SimTool_Photo_Master.ini. Neither name exists as a string in this firmware's library — they belong
# to the X9 Ultra build, which composes "SimTool_%s.ini" — so the retro path found no lut_size and no
# switch block and rendered a black viewfinder.
#
# So give it the device's own setting/ tree (SimTool.ini, Ditherout/, FromXml/ — the library also
# has readLMTXML in it) and then let the payload override on top. Nothing here is invented: it is
# the same file the stock photo path runs.
mkdir -p "$BT_EXTRA/setting_Retro"
if [ -d /odm/etc/camera/basictone/setting ]; then
    cp -a /odm/etc/camera/basictone/setting/. "$BT_EXTRA/setting_Retro"/
fi
if [ -d "$MODDIR/payload/setting_Retro" ]; then
    stage "$MODDIR/payload/setting_Retro" "$BT_EXTRA/setting_Retro"
fi
if [ -f "$BT_EXTRA/setting_Retro/SimTool.ini" ]; then
    log "setting_Retro staged: $(ls "$BT_EXTRA/setting_Retro" | wc -l) entries, SimTool.ini $(wc -c < "$BT_EXTRA/setting_Retro/SimTool.ini") bytes"
else
    log "setting_Retro has no SimTool.ini, the retro viewfinder will stay black"
fi

# --- POP and GR data, staged into the merged tree rather than the camera's private files ---
#
# This used to live in service.sh, writing to /data/user/0/com.oplus.camera/files. That never ran:
# /data/user/0/<pkg> does not exist until the user unlocks (ro.crypto.type=file) and KernelSU runs
# service.sh at late_start, so every version since 475 gave up there and left files/pop_port,
# files/ricoh_gr/lmt and files/mode_limit_watermark uncreated. 482 tried to rescue it with a
# detached --wait copy; the 482 report still showed grSc=-1 and "pop_port is absent", so that did
# not survive either.
#
# post-fs-data.sh is the one script this device does run, and the merged basictone tree is a bind
# mount of a /data directory that the camera process can already read — the retroIni probe proves
# it (it opened setting_Retro/SimTool.ini through this very tree). So everything the camera reads by
# name goes here, and the app-side copies are gone: nothing to wait for, nothing to unlock.
POP_STAGE=$BT_EXTRA/pop_port
mkdir -p "$POP_STAGE"
for NAME in pld_watermark apk_resources; do
    if [ -d "$MODDIR/payload/$NAME" ]; then
        stage "$MODDIR/payload/$NAME" "$POP_STAGE/$NAME"
    else
        log "payload/$NAME is absent, POP will come up with nothing to draw"
    fi
done
log "pop_port staged: $(ls "$POP_STAGE" | wc -l) entries, pld.json=$(ls "$POP_STAGE/pld_watermark/pld/pld.json" 2>/dev/null | wc -l)"

# GR tone tables. The nine SCLut files are the ones BasicTone actually reads (this firmware's
# setting/SimTool.ini says lut_size = 64, SCLutNum = 9, SC = 1); LMTLut0/1 are copied too so the
# directory is complete for the LMT pass even though the switch block has LMT = 0.
#
# Hard links from the payload: same filesystem, so 28 MB costs nothing and no time. A read-only
# module directory is fine for that; the merged tree is rebuilt every boot anyway.
GR_STAGE=$BT_EXTRA/lmt_gr
mkdir -p "$GR_STAGE"
for NAME in LMTLut0 LMTLut1; do
    [ -f "/odm/etc/camera/basictone/lmt/$NAME" ] && cp -f "/odm/etc/camera/basictone/lmt/$NAME" "$GR_STAGE/$NAME"
done
if [ -d "$MODDIR/payload/gr_lmt64" ]; then
    cp -lf "$MODDIR/payload/gr_lmt64"/* "$GR_STAGE"/ 2>/dev/null \
        || cp -f "$MODDIR/payload/gr_lmt64"/* "$GR_STAGE"/
    log "lmt_gr staged: $(ls "$GR_STAGE" | wc -l) tables, SCLut0 $(wc -c < "$GR_STAGE/SCLut0" 2>/dev/null || echo 0) bytes"
else
    log "payload/gr_lmt64 is absent, every GR style past the first will render on a zero table (green)"
fi

# GR watermark styles, camera side: the IPU master-watermark renderer reads
# mode_limit_watermark/gr_style_N/gr_style_N.json and the drawables beside it.
WM_STAGE=$BT_EXTRA/mode_limit_watermark
mkdir -p "$WM_STAGE"
if [ -d "$MODDIR/payload/watermark_master_styles" ]; then
    for I in 1 2 3 4 5; do
        ID=gr_style_$I
        JSON=$MODDIR/payload/watermark_master_styles/$ID.json
        [ -f "$JSON" ] || continue
        mkdir -p "$WM_STAGE/$ID"
        cp -f "$JSON" "$WM_STAGE/$ID/$ID.json"
        cp -f "$JSON" "$WM_STAGE/$ID.json"
        if [ -d "$MODDIR/payload/watermark_drawables" ]; then
            cp -f "$MODDIR/payload/watermark_drawables/"*.webp "$WM_STAGE/$ID/" 2>/dev/null || true
        fi
    done
    if [ -d "$MODDIR/payload/watermark_drawables" ]; then
        cp -f "$MODDIR/payload/watermark_drawables/"*.webp "$WM_STAGE/" 2>/dev/null || true
    fi
    log "mode_limit_watermark staged: $(ls "$WM_STAGE" | wc -l) entries"
fi

bind_merged_dir /odm/etc/camera/basictone "$BT_EXTRA" \
    "$MODDIR/merged/basictone" /odm/etc/camera/basictone

log "check meishe=$(ls /odm/etc/camera/meishe_lut 2>/dev/null | wc -l) basictone=$(ls /odm/etc/camera/basictone 2>/dev/null | wc -l)"
log "check lmt=$(ls -d /odm/etc/camera/basictone/lmt 2>&1)"

# Pro video ISZ (2x main, 6x tele): stock zeroes the ISO gate of rows 25-40, which DoIzoomStable() can
# never pass; -1 means "no ISO check". Patched from the device's own file every boot and only mounted
# when exactly 16 rows matched, so a different config layout is left alone.
# Touch $MODDIR/disable_pro_video_isz to skip.
HWCFG=/odm/etc/camera/CameraHWConfiguration.config
HWOUT=$MODDIR/merged/CameraHWConfiguration.config
if [ -f "$MODDIR/disable_pro_video_isz" ]; then
    log "pro video isz disabled"
elif [ -f "$HWCFG" ]; then
    mkdir -p "$MODDIR/merged"
    awk -F';' -v OFS=';' '
        /^\[/ { section = $0 }
        section == "[InsensorZoom]" && match($0, /^[ \t]*iZoomSettins\[[0-9]+\]/) {
            row = $0; sub(/^[ \t]*iZoomSettins\[/, "", row); sub(/\].*/, "", row)
            if (row + 0 >= 25 && row + 0 <= 40 && NF == 14 && $12 + 0 == 0 && $13 + 0 == 0 && $12 ~ /^[ \t]*0[ \t]*$/ && $13 ~ /^[ \t]*0[ \t]*$/) {
                $12 = "     -1"; $13 = "     -1"; patched++
            }
        }
        { print }
        END { print patched + 0 > "/dev/stderr" }
    ' "$HWCFG" > "$HWOUT" 2> "$MODDIR/merged/isz_count"
    COUNT=$(cat "$MODDIR/merged/isz_count" 2>/dev/null)
    if [ "$COUNT" = "16" ]; then
        bind_file "$HWOUT" "$HWCFG"
    else
        log "pro video isz skipped, patched rows=$COUNT (expected 16)"
    fi
fi
log "end"
