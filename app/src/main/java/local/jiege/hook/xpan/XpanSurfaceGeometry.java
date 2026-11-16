package local.jiege.hook.xpan;

import android.util.Pair;
import android.util.Size;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;

/** Use the HAL's delivered dimensions for the XPAN app surface too. */
final class XpanSurfaceGeometry {
    static void install(ClassLoader loader) throws NoSuchMethodException {
        Class<?> mode = XposedHelpers.findClass("com.oplus.ocs.camera.producer.mode.XpanMode", loader);
        Method target = null;
        for (Method method : mode.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!method.getName().equals("getSurfaceSize") || types.length < 3
                    || types[1] != String.class || !List.class.isAssignableFrom(method.getReturnType())) continue;
            if (target != null) throw new NoSuchMethodException("Ambiguous XpanMode.getSurfaceSize");
            target = method;
        }
        if (target == null) throw new NoSuchMethodException("XpanMode.getSurfaceSize");
        XposedBridge.hookMethod(target, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!("capture".equals(param.args[1]) || "capture_yuv".equals(param.args[1]))
                        || !(param.getResult() instanceof List)) return;
                List<Pair<Size, Size>> fixed = new ArrayList<>();
                boolean changed = false;
                for (Object item : (List<?>) param.getResult()) {
                    if (!(item instanceof Pair)) return;
                    Pair<?, ?> pair = (Pair<?, ?>) item;
                    if (!(pair.first instanceof Size) || !(pair.second instanceof Size)) return;
                    Size delivered = (Size) pair.second;
                    if (delivered.getWidth() < 2 || delivered.getHeight() < 2) return;
                    fixed.add(new Pair<>(delivered, delivered));
                    changed |= !delivered.equals(pair.first);
                }
                if (changed) {
                    param.setResult(fixed);
                    Log.i("XpanGeometry", "capture surfaces use delivered dimensions: " + fixed);
                }
            }
        });
    }
}
