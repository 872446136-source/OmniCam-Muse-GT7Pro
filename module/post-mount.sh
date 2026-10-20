#!/system/bin/sh
# 测试分支：本机没有 libBasicTonePhotoX9.so / X10.so，原来的 set -eu 会在第一个 test -f 失败时
# 直接退出，一个库都不挂。这里改成逐个尝试，缺的记日志跳过。
MODDIR=${0%/*}
# 两档开关：
#   /data/adb/omnicam_fullport_off   —— 全部不挂
#   /data/adb/omnicam_fullport_nolib —— 只挂 /odm 配置，不换原生库（第一阶段测试用这个）
[ -f /data/adb/omnicam_fullport_off ] && exit 0
[ -f /data/adb/omnicam_fullport_nolib ] && exit 0

bind_library() {
    NAME=$1
    SOURCE="$MODDIR/system/odm/lib64/$NAME"
    TARGET="/odm/lib64/$NAME"
    if [ ! -f "$SOURCE" ]; then
        echo "no payload library $NAME" >> "$MODDIR/bind.log"
        return 0
    fi
    if [ ! -f "$TARGET" ]; then
        echo "no stock library $NAME, skipped" >> "$MODDIR/bind.log"
        return 0
    fi
    chown root:root "$SOURCE"
    chmod 0644 "$SOURCE"
    chcon u:object_r:same_process_hal_file:s0 "$SOURCE" 2>/dev/null || true
    if mount --bind "$SOURCE" "$TARGET"; then
        echo "library bind ok $TARGET" >> "$MODDIR/bind.log"
    else
        echo "library bind FAIL $TARGET" >> "$MODDIR/bind.log"
    fi
}

# Hybrid Mount creates the two new backend paths before this phase.
bind_library libBasicTonePhotoX9.so
bind_library libBasicTonePhotoX10.so
bind_library libBasicTonePhoto.so
bind_library libAlgoInterface.so
