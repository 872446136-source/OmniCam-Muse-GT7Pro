package local.jiege.hook.xpan;

import android.util.Pair;
import android.util.Size;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.t2;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class XpanSurfaceGeometry {
    public static void install(ClassLoader classLoader) {
        Method method = null;
        for (Method method2 : XposedHelpers.findClass("com.oplus.ocs.camera.producer.mode.XpanMode", classLoader).getDeclaredMethods()) {
            Class<?>[] parameterTypes = method2.getParameterTypes();
            if (method2.getName().equals("getSurfaceSize") && parameterTypes.length >= 3 && parameterTypes[1] == String.class && List.class.isAssignableFrom(method2.getReturnType())) {
                if (method != null) {
                    t2.o("Ambiguous XpanMode.getSurfaceSize");
                    return;
                }
                method = method2;
            }
        }
        if (method != null) {
            XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanSurfaceGeometry.1
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (("capture".equals(methodHookParam.args[1]) || "capture_yuv".equals(methodHookParam.args[1])) && (methodHookParam.getResult() instanceof List)) {
                        ArrayList arrayList = new ArrayList();
                        boolean z = false;
                        for (Object obj : (List) methodHookParam.getResult()) {
                            if (!(obj instanceof Pair)) {
                                return;
                            }
                            Pair pair = (Pair) obj;
                            if (!(pair.first instanceof Size)) {
                                return;
                            }
                            Object obj2 = pair.second;
                            if (!(obj2 instanceof Size)) {
                                return;
                            }
                            Size size = (Size) obj2;
                            if (size.getWidth() < 2 || size.getHeight() < 2) {
                                return;
                            }
                            arrayList.add(new Pair(size, size));
                            z |= !size.equals(pair.first);
                        }
                        if (z) {
                            methodHookParam.setResult(arrayList);
                            Log.i("XpanGeometry", "capture surfaces use delivered dimensions: " + arrayList);
                        }
                    }
                }
            });
        } else {
            t2.o("XpanMode.getSurfaceSize");
        }
    }
}
