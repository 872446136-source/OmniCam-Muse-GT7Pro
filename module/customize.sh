# Settings app and hooks: install the APK this module ships while the module is flashed, so LSPosed
# points at it before the reboot that activates the module. A failed install (e.g. a different
# signing key) is left as it is and logged to install.log; the installed app is never uninstalled.

# Lite 构建：payload / system 属于别的机型（见 app/src/main/java/local/jiege/hook/common/Profile.java），
# 刷入时就删掉，别占着几百 MB 空间，也免得被误当成可用配置。
if [ -f "$MODPATH/lite" ]; then
    rm -rf "$MODPATH/payload" "$MODPATH/system"
    ui_print "- Lite build: payload and patched libraries removed"
fi

PACKAGE=local.jiege.camera
APK=$(ls "$MODPATH"/omnicam-muse-*.apk 2>/dev/null | head -n 1)
STAGED=/data/local/tmp/omnicam-muse-module.apk
HAVE=$(dumpsys package "$PACKAGE" 2>/dev/null | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n 1)
WANT=$(sed -n 's/^versionCode=//p' "$MODPATH/module.prop")
if [ -n "$APK" ] && cp "$APK" "$STAGED" && chmod 0644 "$STAGED"; then
    RESULT=$(pm install -r -d "$STAGED" 2>&1)
    rm -f "$STAGED"
else
    RESULT="APK missing or not staged"
fi
printf 'installed %s -> %s: %s\n' "${HAVE:-none}" "$WANT" "$RESULT" > "$MODPATH/install.log"
ui_print "- Settings app: $RESULT"
