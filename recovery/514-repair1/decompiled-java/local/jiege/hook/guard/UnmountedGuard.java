package local.jiege.hook.guard;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import defpackage.pp1;
import local.jiege.hook.common.Capabilities;
import local.jiege.hook.common.DataStore;
import local.jiege.hook.common.Diagnostics;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class UnmountedGuard {
    private static final String TAG = "UnmountedGuard";
    private static volatile boolean logged;
    private static volatile Object modeKey;

    private UnmountedGuard() {
    }

    public static boolean filesMounted() {
        return Capabilities.dataRoot() != null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void install(ClassLoader classLoader) {
        final Symbols symbols = Symbols.get();
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.guard.UnmountedGuard.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object result = methodHookParam.getResult();
                if ("gr".equals(result) || "retroCamera".equals(result)) {
                    try {
                        if (UnmountedGuard.modeKey == null) {
                            UnmountedGuard.modeKey = DataStore.key(symbols, "Keys.cameraMode", "pref_camera_mode_key");
                        }
                        if (methodHookParam.args[0] != UnmountedGuard.modeKey) {
                            return;
                        }
                        methodHookParam.setResult("common");
                        if (!UnmountedGuard.logged) {
                            Log.i(UnmountedGuard.TAG, "camera mode " + result + " -> common");
                        }
                        UnmountedGuard.logged = true;
                    } catch (Throwable th) {
                        Log.e(UnmountedGuard.TAG, "camera mode key unavailable", th);
                    }
                }
            }
        };
        XposedBridge.hookMethod(symbols.method("DataManager.get"), xC_MethodHook);
        XposedBridge.hookMethod(symbols.method("DataManager.getOr"), xC_MethodHook);
    }

    public static void installCamera(ClassLoader classLoader) {
        Diagnostics.setFilesMounted(false);
        Log.i(TAG, "module files not mounted in the camera process, features off");
        Log.guard(TAG, "photo mode in place of GR / POP", new pp1(classLoader, (byte) 0));
    }

    public static void installGallery() {
        Diagnostics.setFilesMounted(false);
        Log.i(TAG, "module files not mounted in the gallery process, features off");
    }
}
