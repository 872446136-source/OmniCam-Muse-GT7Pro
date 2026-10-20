#!/system/bin/sh
# Data-layer bind mounts only.
#
# This build keeps the Find X9 Ultra *data* (filter LUTs, X10 basictone, GR LMT) and drops its
# algorithm stack: no patched libAlgoInterface / libBasicTonePhoto(X9/X10) / libCombineLut, no ISP
# tuned bins, no gamma tables, no upstream camera config. What is mounted here is consumed by the
# camera's own meishe / basictone engines, so nothing in this file replaces code the phone needs.
#
# Kill switch: adb shell su -c "touch /data/adb/omnicam_nostack_off" then reboot.
MODDIR=${0%/*}
LOG=$MODDIR/bind.log
set -u

log() {
    echo "$1" >> "$LOG"
}

bind_merged_dir() {
    LIVE=$1
    EXTRA=$2
    DEST=$3
    TARGET=$4
    log "merge $TARGET from $LIVE"
    if [ ! -d "$LIVE" ]; then
        log "missing live $LIVE"
        return 0
    fi
    rm -rf "$DEST"
    mkdir -p "$DEST"
    cp -a "$LIVE"/. "$DEST"/
    if [ -d "$EXTRA" ]; then
        cp -a "$EXTRA"/. "$DEST"/
    fi
    chown -R root:root "$DEST"
    find "$DEST" -type d -exec chmod 0755 {} \;
    find "$DEST" -type f -exec chmod 0644 {} \;
    find "$DEST" -exec chcon u:object_r:vendor_configs_file:s0 {} \; 2>>"$LOG" || true
    log "live_count=$(ls "$LIVE" | wc -l) merged_count=$(ls "$DEST" | wc -l)"
    if mount --bind "$DEST" "$TARGET" >> "$LOG" 2>&1; then
        log "dir bind ok $TARGET"
    else
        log "dir bind FAIL $TARGET"
    fi
}

: > "$LOG"
log "begin"

if [ -f /data/adb/omnicam_nostack_off ]; then
    log "kill switch /data/adb/omnicam_nostack_off present, nothing mounted"
    exit 0
fi

# Filters: the camera's meishe engine enumerates /odm/etc/camera/meishe_lut, so the ported LUTs are
# merged on top of the live tree instead of replacing it.
bind_merged_dir /odm/etc/camera/meishe_lut "$MODDIR/payload/meishe_lut" "$MODDIR/merged/meishe_lut" /odm/etc/camera/meishe_lut

# Basictone: the live tree plus the X10 filter tables and the GR (setting_Retro) tables.
BT_EXTRA=$MODDIR/merged/basictone_extra
rm -rf "$BT_EXTRA"
mkdir -p "$BT_EXTRA/setting_Retro"
if [ -d "$MODDIR/payload/setting_Retro" ]; then
    cp -a "$MODDIR/payload/setting_Retro"/. "$BT_EXTRA/setting_Retro"/
fi
if [ -d "$MODDIR/payload/x10_basictone" ]; then
    cp -a "$MODDIR/payload/x10_basictone"/. "$BT_EXTRA"/
fi
bind_merged_dir /odm/etc/camera/basictone "$BT_EXTRA" "$MODDIR/merged/basictone" /odm/etc/camera/basictone

log "fuji_nc=$(ls -l /odm/etc/camera/meishe_lut/fuji-nc.bin 2>&1)"
log "retro_ini=$(ls -l /odm/etc/camera/basictone/setting_Retro/SimTool_Master.ini 2>&1)"
log "host_setting=$(ls -d /odm/etc/camera/basictone/setting 2>&1)"

# Pro video ISZ (2x main, 6x tele; O-Log2 too): the stock config sets the ISO gate of rows 25-40 to
# 0/0, which DoIzoomStable() can never pass; -1 means "no ISO check", like the normal video rows.
# Patched from the live file on every boot, skipped when the row count is not the expected 16.
# A plain edit of this phone's own file, not part of the X9 Ultra algorithm stack.
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
        chown root:root "$HWOUT"
        chmod 0644 "$HWOUT"
        chcon u:object_r:vendor_configs_file:s0 "$HWOUT" 2>/dev/null || true
        if mount --bind "$HWOUT" "$HWCFG" >> "$LOG" 2>&1; then
            log "file bind ok $HWCFG"
        else
            log "file bind FAIL $HWCFG"
        fi
    else
        log "pro video isz skipped, patched rows=$COUNT"
    fi
fi
log "end"
