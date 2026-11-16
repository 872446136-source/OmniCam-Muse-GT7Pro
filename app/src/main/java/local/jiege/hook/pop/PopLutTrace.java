package local.jiege.hook.pop;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import local.jiege.hook.common.Log;

/** Candidate diagnostics for the firmware's POP CombineLut path. */
final class PopLutTrace {
    static void install(ClassLoader loader) {
        Class<?> helper = XposedHelpers.findClass("com.oplus.ocs.camera.CombineLutHelper", loader);
        Map<Object, Boolean> retro = Collections.synchronizedMap(new WeakHashMap<>());
        XposedBridge.hookAllMethods(helper, "init", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                boolean active = p.args.length >= 3 && String.valueOf(p.args[2]).contains("setting_Retro");
                retro.put(p.thisObject, active);
                if (active) Log.i("PopLut", "init " + Arrays.toString(p.args));
            }
        });
        XposedBridge.hookAllMethods(helper, "process", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!Boolean.TRUE.equals(retro.get(p.thisObject)) || !(p.getResult() instanceof float[])) return;
                float[] lut = (float[]) p.getResult();
                int n = (int) Math.round(Math.cbrt(lut.length / 3.0));
                int at = 3 * ((n / 2) * n * n + (n / 2) * n + n / 2);
                Log.i("PopLut", "values=" + Arrays.toString(Arrays.copyOf(p.args, Math.min(7, p.args.length)))
                        + " length=" + lut.length + " hash=" + Arrays.hashCode(lut)
                        + " gray=" + (at + 3 <= lut.length ? Arrays.toString(Arrays.copyOfRange(lut, at, at + 3)) : "unknown"));
            }
        });
    }
}
