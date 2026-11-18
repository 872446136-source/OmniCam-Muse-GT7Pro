package local.jiege.hook.launch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.hd;
import local.jiege.hook.common.DataStore;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class KeyLaunchMode {
    private static final String CAMERA = "com.oplus.camera.Camera";
    private static final int CHOSEN = 9000;
    private static final String TAG = "KeyLaunchMode";
    private static Object modeKey;
    private static Object remembered;
    private static String sessionMode;

    private KeyLaunchMode() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean fromKey(Intent intent) {
        return intent.getBooleanExtra("android_spruce_key_launch_type", false) || "spruce_double_tap_launch_from_framework".equals(intent.getStringExtra("android_camera_launch_type"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void install(final ClassLoader classLoader) {
        final Symbols symbols = Symbols.get();
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.launch.KeyLaunchMode.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                String name = methodHookParam.thisObject.getClass().getName();
                if (KeyLaunchMode.CAMERA.equals(name) || "com.oplus.camera.component.CameraImageActivity".equals(name)) {
                    Object obj = methodHookParam.args[0];
                    Intent intent = obj instanceof Intent ? (Intent) obj : ((Activity) methodHookParam.thisObject).getIntent();
                    if (intent != null && KeyLaunchMode.fromKey(intent) && Settings.get("key_launch_mode_on")) {
                        intent.putExtra("mode", KeyLaunchMode.CHOSEN);
                        if (KeyLaunchMode.sessionMode != null) {
                            return;
                        }
                        try {
                            Symbols symbols2 = symbols;
                            KeyLaunchMode.remembered = DataStore.get(symbols2, classLoader, KeyLaunchMode.modeKey(symbols2), "common");
                            KeyLaunchMode.sessionMode = Settings.getString("key_launch_mode");
                        } catch (Throwable th) {
                            Log.e(KeyLaunchMode.TAG, "remembered mode unreadable", th);
                        }
                    }
                }
            }
        };
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", new Object[]{Bundle.class, xC_MethodHook});
        XposedHelpers.findAndHookMethod(Activity.class, "onNewIntent", new Object[]{Intent.class, xC_MethodHook});
        XposedHelpers.findAndHookMethod(Activity.class, "onStop", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.launch.KeyLaunchMode.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (KeyLaunchMode.sessionMode == null || !KeyLaunchMode.CAMERA.equals(methodHookParam.thisObject.getClass().getName())) {
                    return;
                }
                try {
                    String str = KeyLaunchMode.sessionMode;
                    Symbols symbols2 = symbols;
                    if (str.equals(DataStore.get(symbols2, classLoader, KeyLaunchMode.modeKey(symbols2), PresetSelection.STANDARD_KEY))) {
                        Symbols symbols3 = symbols;
                        DataStore.put(symbols3, classLoader, KeyLaunchMode.modeKey(symbols3), KeyLaunchMode.remembered);
                    }
                } catch (Throwable th) {
                    Log.e(KeyLaunchMode.TAG, "remembered mode not restored", th);
                }
                KeyLaunchMode.sessionMode = null;
                KeyLaunchMode.remembered = null;
            }
        }});
        XposedBridge.hookMethod(symbols.method("CameraInfoCursor.modeName"), new XC_MethodHook() { // from class: local.jiege.hook.launch.KeyLaunchMode.3
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (((Integer) methodHookParam.args[0]).intValue() != KeyLaunchMode.CHOSEN) {
                    return;
                }
                String string = Settings.getString("key_launch_mode");
                Log.i(KeyLaunchMode.TAG, "quick launch key opens " + string);
                methodHookParam.setResult(string);
            }
        });
        Log.i(TAG, "installed");
    }

    public static void installCamera(ClassLoader classLoader) {
        Log.guard(TAG, "quick launch key mode", new hd(classLoader, (byte) 9));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Object modeKey(Symbols symbols) {
        Object obj = modeKey;
        if (obj != null) {
            return obj;
        }
        Object objKey = DataStore.key(symbols, "Keys.cameraMode", "pref_camera_mode_key");
        modeKey = objKey;
        return objKey;
    }
}
