package local.jiege.hook.gr;

import android.app.Application;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Reflection;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class GrRatioGuard {
    private static final String RATIO_3_2 = "3_2";
    private static final String RATIO_KEY = "pref_camera_photo_ratio_key";
    private static final String TAG = "RicohGrPort";
    private static volatile boolean dataManagerHooked;

    private GrRatioGuard() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookStoredRatio(final Symbols symbols) {
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrRatioGuard.3
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = methodHookParam.args[0];
                if (obj != null && Reflection.hasStringField(obj, GrRatioGuard.RATIO_KEY) && GrRatioGuard.RATIO_3_2.equals(methodHookParam.getResult())) {
                    methodHookParam.setResult("standard");
                }
            }
        };
        XposedBridge.hookMethod(symbols.method("DataManager.get"), xC_MethodHook);
        XposedBridge.hookMethod(symbols.method("DataManager.getOr"), xC_MethodHook);
        XposedHelpers.findAndHookMethod(Application.class, "onCreate", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.gr.GrRatioGuard.4
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrRatioGuard.dataManagerHooked) {
                    return;
                }
                GrRatioGuard.dataManagerHooked = true;
                XC_MethodHook xC_MethodHook2 = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrRatioGuard.4.1
                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam2) {
                        Object obj = methodHookParam2.args[0];
                        if (obj != null && Reflection.hasStringField(obj, GrRatioGuard.RATIO_KEY) && GrRatioGuard.RATIO_3_2.equals(methodHookParam2.args[1])) {
                            methodHookParam2.args[1] = "standard";
                        }
                    }
                };
                try {
                    XposedBridge.hookMethod(symbols.method("DataManager.putSync"), xC_MethodHook2);
                    XposedBridge.hookMethod(symbols.method("DataManager.put"), xC_MethodHook2);
                } catch (Throwable th) {
                    Log.e(GrRatioGuard.TAG, "stored ratio write hook failed", th);
                }
            }
        }});
    }

    public static void install(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        Log.guard(TAG, "GR photo ratio", new b(symbols, (byte) 14));
        Log.guard(TAG, "ratio menu", new b(symbols, (byte) 15));
        Log.guard(TAG, "stored ratio", new b(symbols, (byte) 16));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$0(Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("GrMode.photoRatio"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrRatioGuard.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrRatioGuard.RATIO_3_2.equals(methodHookParam.getResult())) {
                    methodHookParam.setResult("standard");
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$1(Symbols symbols) {
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrRatioGuard.2
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrRatioGuard.RATIO_KEY.equals(methodHookParam.args[0]) && (methodHookParam.args[1] instanceof String[])) {
                    ArrayList arrayList = new ArrayList();
                    for (String str : (String[]) methodHookParam.args[1]) {
                        if (!GrRatioGuard.RATIO_3_2.equals(str)) {
                            arrayList.add(str);
                        }
                    }
                    if (arrayList.size() == ((String[]) methodHookParam.args[1]).length) {
                        return;
                    }
                    if (arrayList.isEmpty()) {
                        methodHookParam.setResult((Object) null);
                    } else {
                        methodHookParam.args[1] = arrayList.toArray(new String[0]);
                    }
                }
            }
        };
        Iterator it = symbols.all("SettingsUi.setOptionsImpl").iterator();
        while (it.hasNext()) {
            XposedBridge.hookMethod((Method) it.next(), xC_MethodHook);
        }
    }
}
