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

/** Restore the cyan/magenta preview axis absent from this firmware's POP CWCM resources. */
final class PopTintPreview {
    static void install(ClassLoader loader) throws Exception {
        if (!"RMX5010".equals(Build.MODEL)) return;
        Class<?> helper = XposedHelpers.findClass("com.oplus.ocs.camera.CombineLutHelper", loader);
        Method isRetro = XposedHelpers.findClass("com.oplus.camera.common.utils.CommonUtil", loader)
                .getDeclaredMethod("isRetroMode");
        isRetro.setAccessible(true);
        Map<Object, Boolean> retro = Collections.synchronizedMap(new WeakHashMap<>());
        XposedBridge.hookAllMethods(helper, "init", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                retro.put(p.thisObject, p.args.length >= 3 && String.valueOf(p.args[2]).endsWith("setting_Retro"));
            }
        });
        Method process = helper.getDeclaredMethod("process", int.class, int.class, int.class, int.class,
                float.class, float.class, float.class, int[].class);
        XposedBridge.hookMethod(process, new XC_MethodHook() {
            private int previous = Integer.MIN_VALUE;
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                if (!Boolean.TRUE.equals(retro.get(p.thisObject)) || !Boolean.TRUE.equals(isRetro.invoke(null))
                        || !(p.getResult() instanceof float[])) return;
                int value = (Integer) p.args[0];
                p.setResult(PopTintMath.apply((float[]) p.getResult(), value));
                if (value != previous) {
                    previous = value;
                    Log.i("PopTint", "preview cyan/magenta=" + value);
                }
            }
        });
    }
}
