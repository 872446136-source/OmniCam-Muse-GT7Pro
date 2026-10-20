# Settings app and hooks: install the APK this module ships while the module is flashed, so LSPosed
# points at it before the reboot that activates the module. A failed install (e.g. a different
# signing key) is left as it is and logged to install.log; the installed app is never uninstalled.

# 测试分支：payload 与 system/ 都保留（这是上游 X9 Ultra 的原件，暴力移植就是要它）。
if [ ! -d "$MODPATH/payload" ]; then
    ui_print "- WARNING: payload missing, this zip was built without the upstream payload"
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
