package local.jiege.hook.guard;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Method;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.modes.ModeGates;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class PresenterGuard {
    private static final String OFF = "/data/adb/omnicam_presenterguard_off";
    private static final String TAG = "PresenterGuard";
    private static boolean installed;

    private PresenterGuard() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        Symbols symbols = Symbols.get();
        if (!symbols.has("FeaturePresenter.stage")) {
            Log.i(TAG, "stage dispatcher not resolved, a presenter that throws can still take the process down");
            return;
        }
        try {
            final Method method = symbols.method("FeaturePresenter.stage");
            XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.guard.PresenterGuard.2
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    try {
                        XposedBridge.invokeOriginalMethod(method, methodHookParam.thisObject, methodHookParam.args);
                    } catch (Throwable th) {
                        Log.i(PresenterGuard.TAG, "presenter " + methodHookParam.thisObject.getClass().getName() + " threw on stage " + methodHookParam.args[0] + ", swallowed so the mode switch can finish", th);
                    }
                    methodHookParam.setResult((Object) null);
                }
            });
            Log.i(TAG, "presenter stage guarded: " + method);
        } catch (Throwable th) {
            Log.e(TAG, "cannot guard the presenter stage", th);
        }
    }

    public static void installCamera(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "off (/data/adb/omnicam_presenterguard_off)");
        } else {
            ModeGates.afterApplication(classLoader, new Runnable() { // from class: local.jiege.hook.guard.PresenterGuard.1
                @Override // java.lang.Runnable
                public void run() {
                    PresenterGuard.install();
                }
            });
        }
    }
}
