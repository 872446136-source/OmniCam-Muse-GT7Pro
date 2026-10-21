package local.jiege.hook.common;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;

/**
 * The camera's own log never reaches logcat on this system (its lines are dropped below
 * android.util.Log, or go to the private channel CameraUnitUtils.printPrivateLog), so the exported
 * log has none of it. Lines of the camera classes the module works with are copied into the
 * module log, which the export carries.
 *
 * <p>The export's own logcat grab is {@code logcat -d -t 20000}: about thirty seconds of this
 * device's buffers. A camera that freezes stops logging, so by the time the log is exported the
 * lines that explain the freeze have been pushed out — 471 and 472 both came back with zero
 * {@code OCAM} lines, which is why the mode switch could not be diagnosed from them. The switch
 * tags are mirrored here so those lines survive inside the module log.
 */
public final class CameraLogMirror {
    private static final String TAG = "CameraLog";
    private static final String[] TAGS = {
        "OCAM_PresetDataRepository", "OCAM_EffectDataManager", "OCAM_EffectPreset", "OCAM_PresetDataUtil",
        // The mode switch: "Camera Mode Change Start, next mode: …", "checkToSwitchMode, the device
        // is not support the mode:…", and the database rows behind both.
        "OCAM_ModeSwitcher", "OCAM_DataBaseOperator", "OCAM_CameraControlUI", "OCAM_HeadlineHelper",
    };

    private CameraLogMirror() {}

    public static void install(ClassLoader classLoader) {
        // Log.println_native(bufID, priority, tag, msg): every android.util.Log call ends here.
        XposedBridge.hookMethod(XposedHelpers.findMethodExact(android.util.Log.class, "println_native",
            int.class, int.class, String.class, String.class), mirror(2, 3));
        Class<?> utils = XposedHelpers.findClassIfExists("com.oplus.ocs.camera.appinterface.adapter.CameraUnitUtils", classLoader);
        if (utils == null) return;
        for (Method method : utils.getDeclaredMethods()) {
            if ("printPrivateLog".equals(method.getName())) XposedBridge.hookMethod(method, mirror(0, 1));
        }
    }

    private static XC_MethodHook mirror(final int tagArg, final int messageArg) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Object tag = param.args[tagArg];
                if (!(tag instanceof String) || !mirrored((String) tag)) return;
                Log.i(TAG, tag + " " + param.args[messageArg]);
            }
        };
    }

    private static boolean mirrored(String tag) {
        for (String prefix : TAGS) if (tag.startsWith(prefix)) return true;
        return false;
    }
}
