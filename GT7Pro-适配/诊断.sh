#!/system/bin/sh
# 一次性收集排查信息（需要 root）。
#
# 用法：
#   adb push 诊断.sh /data/local/tmp/
#   adb shell su -c "sh /data/local/tmp/诊断.sh"
#   adb pull /sdcard/omnicam-diag-*.txt
#
# 重要：**在停用模块之前跑**。bind.log 在模块目录里，模块一删就没了。

OUT=/sdcard/omnicam-diag-$(date +%Y%m%d-%H%M%S).txt
MOD=/data/adb/modules/ricoh_pop_x9u
CAM=/data/user/0/com.oplus.camera
GAL=/data/user/0/com.coloros.gallery3d

say() { echo "$@" >> "$OUT"; }
sec() { echo "" >> "$OUT"; echo "===== $1 =====" >> "$OUT"; }

: > "$OUT"
say "omnicam diagnostic $(date)"

sec "设备"
getprop ro.product.model >> "$OUT" 2>&1
getprop ro.build.display.id >> "$OUT" 2>&1
getprop ro.build.version.release >> "$OUT" 2>&1
uname -a >> "$OUT" 2>&1

sec "模块目录"
ls -la "$MOD" >> "$OUT" 2>&1
say "--- module.prop"
cat "$MOD/module.prop" >> "$OUT" 2>&1
say "--- lite 标记: $([ -f "$MOD/lite" ] && echo 存在（Lite 构建） || echo 不存在（full-port 测试构建）)"
say "--- payload: $([ -d "$MOD/payload" ] && du -sh "$MOD/payload" 2>/dev/null || echo 不存在)"
say "--- system:  $([ -d "$MOD/system" ] && du -sh "$MOD/system" 2>/dev/null || echo 不存在)"
say "--- 开关: off=$([ -f /data/adb/omnicam_fullport_off ] && echo 有 || echo 无) nolib=$([ -f /data/adb/omnicam_fullport_nolib ] && echo 有 || echo 无)"

sec "bind.log（挂载结果，每个文件一行）"
if [ -f "$MOD/bind.log" ]; then
    wc -l "$MOD/bind.log" >> "$OUT" 2>&1
    say "--- ok 计数: $(grep -c 'ok ' "$MOD/bind.log" 2>/dev/null)"
    say "--- FAIL 计数: $(grep -c 'FAIL' "$MOD/bind.log" 2>/dev/null)"
    tail -200 "$MOD/bind.log" >> "$OUT" 2>&1
else
    say "没有 bind.log —— post-fs-data.sh 没跑，或者模块没启用"
fi

sec "当前挂载（/odm 相关）"
mount | grep -E " /odm" >> "$OUT" 2>&1
say "--- 挂载点计数: $(mount | grep -c ' /odm')"

sec "/odm 关键文件"
ls -l /odm/lib64/libAlgoInterface.so /odm/lib64/libBasicTonePhoto.so 2>> "$OUT" >> "$OUT"
ls /odm/lib64/camera/ 2>> "$OUT" | head -20 >> "$OUT"
ls /odm/etc/camera/config/ 2>> "$OUT" | head -20 >> "$OUT"
ls /odm/etc/camera/ 2>> "$OUT" | head -30 >> "$OUT"

sec "应用版本"
dumpsys package com.oplus.camera 2>/dev/null | grep -E "versionName|versionCode" | head -4 >> "$OUT"
dumpsys package com.coloros.gallery3d 2>/dev/null | grep -E "versionName|versionCode" | head -4 >> "$OUT"
dumpsys package local.jiege.camera 2>/dev/null | grep -E "versionName|versionCode" | head -4 >> "$OUT"

sec "camera provider 状态"
dumpsys media.camera 2>/dev/null | head -60 >> "$OUT"

sec "模块在相机数据目录留下的东西"
for D in "$CAM/files/jiege" "$CAM/files/ricoh_gr" "$CAM/files/pop_port" "$CAM/files/odm" \
         "$CAM/files/mode_limit_watermark" "$GAL/files/ricoh_gr_styles" "$GAL/files/jiege_ipu"; do
    if [ -e "$D" ]; then
        say "$(du -sh "$D" 2>/dev/null)"
    else
        say "缺失 $D"
    fi
done
ls -l "$CAM/databases/" 2>/dev/null | head -10 >> "$OUT"

sec "崩溃记录（crash buffer）"
logcat -b crash -d -v time 2>/dev/null | tail -200 >> "$OUT"

sec "相机相关日志（main buffer）"
logcat -d -v time 2>/dev/null | grep -iE "cameraserver|CameraService|camera_provider|libAlgoInterface|libBasicTone|F DEBUG|FATAL|OCAM_|oplus.camera" | tail -400 >> "$OUT"

sec "tombstones"
ls -lt /data/tombstones 2>/dev/null | head -10 >> "$OUT"

sec "LSPosed"
ls -la /data/adb/lspd/log/ 2>/dev/null | tail -5 >> "$OUT"
for F in /data/adb/lspd/log/*.log; do
    [ -f "$F" ] || continue
    say "--- $F"
    grep -iE "jiege|Symbols|OmniCam" "$F" 2>/dev/null | tail -60 >> "$OUT"
done

sec "结束"
say "文件大小: $(wc -c < "$OUT") 字节"

echo "已写入 $OUT"
echo "拉回来：adb pull $OUT"
