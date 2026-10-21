# Settings app and hooks: install the APK this module ships while the module is flashed, so LSPosed
# points at it before the reboot that activates the module. A failed install (e.g. a different
# signing key) is left as it is and logged to install.log; the installed app is never uninstalled.

# One build for every device: the payload carries only the data layer (LUTs, basictone, GR LMT,
# watermark artwork), never a system library. A device whose camera config the module cannot
# substitute keeps working, because the features that need it turn themselves off at install time.
if [ ! -d "$MODPATH/payload" ]; then
    ui_print "- WARNING: payload missing, only the app-layer features will run"
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

# Builds up to 430 staged the config profile from this script with root and then ran restorecon on
# it, which drops the app's SELinux categories: the camera could not read the file and every mode
# the profile adds stayed hidden. The profile is now written by the camera process itself, so the
# old copy has to go once — it has the right length, and ConfigProfile would otherwise keep it.
for DIR in /data/user/0/com.oplus.camera/files/jiege/profile /data/data/com.oplus.camera/files/jiege/profile; do
    [ -d "$DIR" ] && rm -rf "$DIR"
done
