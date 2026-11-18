package local.jiege.hook.gr;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class GrMasterEffectEntry {
    private static final String TAG = "RicohGrPort";

    private GrMasterEffectEntry() {
    }

    public static void install(final Symbols symbols) {
        final ThreadLocal threadLocal = new ThreadLocal();
        XposedBridge.hookMethod(symbols.method("GrSupport.supported"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrMasterEffectEntry.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Boolean.TRUE.equals(threadLocal.get())) {
                    methodHookParam.setResult(Boolean.FALSE);
                }
            }
        });
        XposedBridge.hookMethod(symbols.method("CaptureParams.refreshEffectEntry"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrMasterEffectEntry.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                threadLocal.remove();
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if ("gr".equals(symbols.get("FeaturePresenter.modeName", methodHookParam.thisObject))) {
                    return;
                }
                threadLocal.set(Boolean.TRUE);
            }
        });
        Log.i(TAG, "master effect entry refresh installed");
    }
}
