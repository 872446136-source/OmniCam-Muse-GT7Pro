package local.jiege.hook.pop;

import android.graphics.Rect;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.t2;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: Access modifiers changed from: package-private */
/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class PopPreviewParameters {
    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES = new ThreadLocal<>();

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Frame {
        final Object unit;
        final int[] values;

        public Frame(Object obj, Object[] objArr, boolean z) {
            this.unit = obj;
            this.values = z ? new int[]{((Integer) objArr[18]).intValue(), ((Integer) objArr[19]).intValue(), ((Integer) objArr[20]).intValue(), ((Integer) objArr[21]).intValue()} : null;
        }
    }

    public static void install(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass("com.oplus.camera.filter.ipuadapter.FilterIpuAdapter", classLoader);
        Class clsFindClass2 = XposedHelpers.findClass("com.oplus.ocs.camera.ipuapi.process.filter.FilterProcessUnit", classLoader);
        Class<?> clsFindClass3 = XposedHelpers.findClass("com.oplus.ocs.camera.ipuapi.process.filter.entity.FilterProcessParameter", classLoader);
        final Method method = Symbols.get().method("Util.isRetroMode");
        final Field fieldFindField = XposedHelpers.findField(clsFindClass, "mFilterProcessUnit");
        Class cls = Integer.TYPE;
        final Method[] methodArr = {clsFindClass3.getMethod("setVignetteIntensity", cls), clsFindClass3.getMethod("setImageAdjustIntensity", cls), clsFindClass3.getMethod("setGrParticleSizeValue", cls), clsFindClass3.getMethod("setGrParticleAmountValue", cls)};
        Class cls2 = Float.TYPE;
        Class cls3 = Boolean.TYPE;
        Method declaredMethod = clsFindClass.getDeclaredMethod("drawFrame", cls, cls, cls, cls, cls, cls, String.class, cls, String.class, String.class, cls3, String.class, float[].class, cls, cls3, String.class, Rect[].class, byte[].class, cls, cls, cls, cls, cls, cls2, cls, cls3);
        Method method2 = null;
        for (Method method3 : clsFindClass2.getMethods()) {
            Class<?>[] parameterTypes = method3.getParameterTypes();
            if (method3.getName().equals("processImage") && parameterTypes.length == 2 && parameterTypes[0] == clsFindClass3) {
                if (method2 != null) {
                    t2.o("Ambiguous typed filter processImage");
                    return;
                }
                method2 = method3;
            }
        }
        if (method2 == null) {
            t2.o("Typed filter processImage");
            return;
        }
        XposedBridge.hookMethod(declaredMethod, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopPreviewParameters.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                ArrayDeque arrayDeque;
                if (Boolean.TRUE.equals(methodHookParam.getObjectExtra("omnicam.pop.frame")) && (arrayDeque = (ArrayDeque) PopPreviewParameters.FRAMES.get()) != null) {
                    if (!arrayDeque.isEmpty()) {
                        arrayDeque.pop();
                    }
                    if (arrayDeque.isEmpty()) {
                        PopPreviewParameters.FRAMES.remove();
                    }
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                boolean zEquals;
                Object obj;
                Object obj2 = null;
                try {
                    obj = fieldFindField.get(methodHookParam.thisObject);
                    try {
                        zEquals = Boolean.TRUE.equals(method.invoke(null, null));
                    } catch (ReflectiveOperationException unused) {
                        obj2 = obj;
                        zEquals = false;
                        obj = obj2;
                    }
                } catch (ReflectiveOperationException unused2) {
                }
                ArrayDeque arrayDeque = (ArrayDeque) PopPreviewParameters.FRAMES.get();
                if (arrayDeque == null) {
                    arrayDeque = new ArrayDeque();
                    PopPreviewParameters.FRAMES.set(arrayDeque);
                }
                arrayDeque.push(new Frame(obj, methodHookParam.args, zEquals));
                methodHookParam.setObjectExtra("omnicam.pop.frame", Boolean.TRUE);
            }
        });
        XposedBridge.hookMethod(method2, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopPreviewParameters.2
            private int[] previous;
            private boolean reportedFailure;

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                ArrayDeque arrayDeque = (ArrayDeque) PopPreviewParameters.FRAMES.get();
                Frame frame = arrayDeque == null ? null : (Frame) arrayDeque.peek();
                if (frame == null || frame.values == null || frame.unit != methodHookParam.thisObject || methodHookParam.args[0] == null) {
                    return;
                }
                int i = 0;
                while (true) {
                    try {
                        Method[] methodArr2 = methodArr;
                        if (i >= methodArr2.length) {
                            break;
                        }
                        methodArr2[i].invoke(methodHookParam.args[0], Integer.valueOf(frame.values[i]));
                        i++;
                    } catch (ReflectiveOperationException e) {
                        if (this.reportedFailure) {
                            return;
                        }
                        this.reportedFailure = true;
                        Log.e("PopPreview", "preview adjustment forwarding failed", e);
                        return;
                    }
                }
                if (Arrays.equals(this.previous, frame.values)) {
                    return;
                }
                this.previous = (int[]) frame.values.clone();
                Log.i("PopPreview", "vignette/clarity/grainSize/grainAmount=" + Arrays.toString(this.previous));
            }
        });
        Log.i("PopPreview", "installed scoped POP preview forwarding");
    }
}
