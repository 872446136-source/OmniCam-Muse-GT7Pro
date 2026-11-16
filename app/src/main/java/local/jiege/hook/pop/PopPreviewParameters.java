package local.jiege.hook.pop;

import android.graphics.Rect;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import local.jiege.hook.common.Log;

/** Forward POP adjustments already supplied by the UI but dropped by FilterIpuAdapter. */
final class PopPreviewParameters {
    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES = new ThreadLocal<>();
    private static final class Frame {
        final Object unit;
        final int[] values;
        Frame(Object unit, Object[] args, boolean retro) {
            this.unit = unit;
            values = retro ? new int[] {(Integer) args[18], (Integer) args[19],
                    (Integer) args[20], (Integer) args[21]} : null;
        }
    }

    static void install(ClassLoader loader) throws Exception {
        Class<?> adapter = XposedHelpers.findClass("com.oplus.camera.filter.ipuadapter.FilterIpuAdapter", loader);
        Class<?> unit = XposedHelpers.findClass("com.oplus.ocs.camera.ipuapi.process.filter.FilterProcessUnit", loader);
        Class<?> parameter = XposedHelpers.findClass("com.oplus.ocs.camera.ipuapi.process.filter.entity.FilterProcessParameter", loader);
        final Method isRetro = XposedHelpers.findClass("com.oplus.camera.common.utils.CommonUtil", loader)
                .getDeclaredMethod("isRetroMode");
        isRetro.setAccessible(true);
        final Field processUnit = XposedHelpers.findField(adapter, "mFilterProcessUnit");
        final Method[] setters = {
            parameter.getMethod("setVignetteIntensity", int.class),
            parameter.getMethod("setImageAdjustIntensity", int.class),
            parameter.getMethod("setGrParticleSizeValue", int.class),
            parameter.getMethod("setGrParticleAmountValue", int.class)
        };
        Method draw = adapter.getDeclaredMethod("drawFrame", int.class, int.class, int.class,
                int.class, int.class, int.class, String.class, int.class, String.class, String.class,
                boolean.class, String.class, float[].class, int.class, boolean.class, String.class,
                Rect[].class, byte[].class, int.class, int.class, int.class, int.class, int.class,
                float.class, int.class, boolean.class);
        Method process = null;
        for (Method method : unit.getMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!method.getName().equals("processImage") || types.length != 2 || types[0] != parameter) continue;
            if (process != null) throw new NoSuchMethodException("Ambiguous typed filter processImage");
            process = method;
        }
        if (process == null) throw new NoSuchMethodException("Typed filter processImage");
        XposedBridge.hookMethod(draw, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Object currentUnit = null;
                boolean retro = false;
                try {
                    currentUnit = processUnit.get(param.thisObject);
                    retro = Boolean.TRUE.equals(isRetro.invoke(null));
                } catch (ReflectiveOperationException ignored) { }
                ArrayDeque<Frame> frames = FRAMES.get();
                if (frames == null) {
                    frames = new ArrayDeque<>();
                    FRAMES.set(frames);
                }
                frames.push(new Frame(currentUnit, param.args, retro));
                param.setObjectExtra("omnicam.pop.frame", Boolean.TRUE);
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!Boolean.TRUE.equals(param.getObjectExtra("omnicam.pop.frame"))) return;
                ArrayDeque<Frame> frames = FRAMES.get();
                if (frames == null) return;
                if (!frames.isEmpty()) frames.pop();
                if (frames.isEmpty()) FRAMES.remove();
            }
        });
        XposedBridge.hookMethod(process, new XC_MethodHook() {
            private int[] previous;
            private boolean reportedFailure;
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                ArrayDeque<Frame> frames = FRAMES.get();
                Frame frame = frames == null ? null : frames.peek();
                if (frame == null || frame.values == null || frame.unit != param.thisObject || param.args[0] == null) return;
                try {
                    for (int i = 0; i < setters.length; i++) setters[i].invoke(param.args[0], frame.values[i]);
                    if (!Arrays.equals(previous, frame.values)) {
                        previous = frame.values.clone();
                        Log.i("PopPreview", "vignette/clarity/grainSize/grainAmount=" + Arrays.toString(previous));
                    }
                } catch (ReflectiveOperationException error) {
                    if (!reportedFailure) {
                        reportedFailure = true;
                        Log.e("PopPreview", "preview adjustment forwarding failed", error);
                    }
                }
            }
        });
        Log.i("PopPreview", "installed scoped POP preview forwarding");
    }
}
