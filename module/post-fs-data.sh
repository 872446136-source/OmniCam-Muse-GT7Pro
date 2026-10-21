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
        cp -a "$EXTRA"/. "$DEST"/
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

# basictone: the stock tree plus the X10 tone curves and the retro settings the GR port reads.
BT_EXTRA=$MODDIR/merged/basictone_extra
rm -rf "$BT_EXTRA"
mkdir -p "$BT_EXTRA"
[ -d "$MODDIR/payload/x10_basictone" ] && cp -a "$MODDIR/payload/x10_basictone"/. "$BT_EXTRA"/
if [ -d "$MODDIR/payload/setting_Retro" ]; then
    mkdir -p "$BT_EXTRA/setting_Retro"
    cp -a "$MODDIR/payload/setting_Retro"/. "$BT_EXTRA/setting_Retro"/
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
