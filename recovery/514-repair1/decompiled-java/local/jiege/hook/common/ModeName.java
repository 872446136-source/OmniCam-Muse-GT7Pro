package local.jiege.hook.common;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ModeName {
    private static volatile boolean installed;
    private static final Map<Object, String> names = Collections.synchronizedMap(new WeakHashMap());

    private ModeName() {
    }

    public static synchronized void install(ClassLoader classLoader) {
        if (installed) {
            return;
        }
        installed = true;
        XposedBridge.hookAllConstructors(XposedHelpers.findClass("com.oplus.camera.module.BaseMode", classLoader), new XC_MethodHook() { // from class: local.jiege.hook.common.ModeName.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object[] objArr = methodHookParam.args;
                if (objArr.length <= 0 || !(objArr[0] instanceof String)) {
                    return;
                }
                ModeName.names.put(methodHookParam.thisObject, (String) methodHookParam.args[0]);
            }
        });
    }

    public static String of(Object obj) {
        if (obj == null) {
            return null;
        }
        return names.get(obj);
    }
}
