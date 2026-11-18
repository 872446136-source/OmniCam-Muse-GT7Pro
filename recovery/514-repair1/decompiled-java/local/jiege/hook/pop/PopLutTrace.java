package local.jiege.hook.pop;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class PopLutTrace {
    public static void install(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.CombineLutHelper", classLoader);
        final Map mapSynchronizedMap = Collections.synchronizedMap(new WeakHashMap());
        XposedBridge.hookAllMethods(clsFindClass, "init", new XC_MethodHook() { // from class: local.jiege.hook.pop.PopLutTrace.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object[] objArr = methodHookParam.args;
                boolean z = objArr.length >= 3 && String.valueOf(objArr[2]).contains("setting_Retro");
                mapSynchronizedMap.put(methodHookParam.thisObject, Boolean.valueOf(z));
                if (z) {
                    Log.i("PopLut", "init " + Arrays.toString(methodHookParam.args));
                }
            }
        });
        XposedBridge.hookAllMethods(clsFindClass, "process", new XC_MethodHook() { // from class: local.jiege.hook.pop.PopLutTrace.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Boolean.TRUE.equals(mapSynchronizedMap.get(methodHookParam.thisObject)) && (methodHookParam.getResult() instanceof float[])) {
                    float[] fArr = (float[]) methodHookParam.getResult();
                    int iRound = (int) Math.round(Math.cbrt(((double) fArr.length) / 3.0d));
                    int i = iRound / 2;
                    int i2 = i * iRound;
                    int i3 = ((iRound * i2) + i2 + i) * 3;
                    StringBuilder sb = new StringBuilder("values=");
                    Object[] objArr = methodHookParam.args;
                    sb.append(Arrays.toString(Arrays.copyOf(objArr, Math.min(7, objArr.length))));
                    sb.append(" length=");
                    sb.append(fArr.length);
                    sb.append(" hash=");
                    sb.append(Arrays.hashCode(fArr));
                    sb.append(" gray=");
                    int i4 = i3 + 3;
                    sb.append(i4 <= fArr.length ? Arrays.toString(Arrays.copyOfRange(fArr, i3, i4)) : "unknown");
                    Log.i("PopLut", sb.toString());
                }
            }
        });
    }
}
