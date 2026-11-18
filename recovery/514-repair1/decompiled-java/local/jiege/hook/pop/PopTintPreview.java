package local.jiege.hook.pop;

import android.os.Build;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: Access modifiers changed from: package-private */
/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class PopTintPreview {
    public static void install(ClassLoader classLoader) {
        if ("RMX5010".equals(Build.MODEL)) {
            Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.CombineLutHelper", classLoader);
            final Method method = Symbols.get().method("Util.isRetroMode");
            final Map mapSynchronizedMap = Collections.synchronizedMap(new WeakHashMap());
            XposedBridge.hookAllMethods(clsFindClass, "init", new XC_MethodHook() { // from class: local.jiege.hook.pop.PopTintPreview.1
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Map map = mapSynchronizedMap;
                    Object obj = methodHookParam.thisObject;
                    Object[] objArr = methodHookParam.args;
                    map.put(obj, Boolean.valueOf(objArr.length >= 3 && String.valueOf(objArr[2]).endsWith("setting_Retro")));
                }
            });
            Class cls = Float.TYPE;
            Class cls2 = Integer.TYPE;
            XposedBridge.hookMethod(clsFindClass.getDeclaredMethod("process", cls2, cls2, cls2, cls2, cls, cls, cls, int[].class), new XC_MethodHook() { // from class: local.jiege.hook.pop.PopTintPreview.2
                private int previous = Integer.MIN_VALUE;

                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Boolean bool = Boolean.TRUE;
                    if (bool.equals(mapSynchronizedMap.get(methodHookParam.thisObject)) && bool.equals(method.invoke(null, null)) && (methodHookParam.getResult() instanceof float[])) {
                        int iIntValue = ((Integer) methodHookParam.args[0]).intValue();
                        methodHookParam.setResult(PopTintMath.apply((float[]) methodHookParam.getResult(), iIntValue));
                        if (iIntValue != this.previous) {
                            this.previous = iIntValue;
                            Log.i("PopTint", "preview cyan/magenta=" + iIntValue);
                        }
                    }
                }
            });
        }
    }
}
