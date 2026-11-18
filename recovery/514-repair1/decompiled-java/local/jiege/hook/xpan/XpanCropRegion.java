package local.jiege.hook.xpan;

import android.util.Size;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.pp1;
import defpackage.t2;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class XpanCropRegion {
    private static final ThreadLocal<ArrayDeque<Size>> INPUTS = new ThreadLocal<>();
    private static final String PREFIX = "com.oplus.ocs.camera.ipuapi.process.watermark.";
    private static final String TAG = "XpanCrop";

    private XpanCropRegion() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void install(ClassLoader classLoader) throws NoSuchFieldException, NoSuchMethodException {
        if (new File("/data/adb/omnicam_xpancrop_off").exists()) {
            return;
        }
        Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.ipuapi.process.watermark.util.WatermarkUtil", classLoader);
        Class clsFindClass2 = XposedHelpers.findClass("com.oplus.ocs.camera.ipuapi.process.watermark.config.VendorConfigure", classLoader);
        Class clsFindClass3 = XposedHelpers.findClass("com.oplus.ocs.camera.ipuapi.process.watermark.entity.WatermarkProcessParameter", classLoader);
        Method method = null;
        for (Method method2 : clsFindClass.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method2.getParameterTypes();
            if (method2.getName().equals("buildHold") && parameterTypes.length == 6 && parameterTypes[3] == Size.class && parameterTypes[4] == Size.class && parameterTypes[5] == Boolean.TYPE) {
                if (method != null) {
                    t2.o("Ambiguous WatermarkUtil.buildHold");
                    return;
                }
                method = method2;
            }
        }
        if (method == null) {
            t2.o("WatermarkUtil.buildHold");
            return;
        }
        final Field fieldFindField = XposedHelpers.findField(method.getParameterTypes()[1], "p");
        if (fieldFindField.getType() != Size.class) {
            throw new NoSuchFieldException("watermark input Size p");
        }
        final Field fieldFindField2 = XposedHelpers.findField(clsFindClass3, "mModeName");
        final Field fieldFindField3 = XposedHelpers.findField(clsFindClass3, "mWatermarkMode");
        if (fieldFindField2.getType() != String.class || fieldFindField3.getType() != Integer.TYPE) {
            throw new NoSuchFieldException("WatermarkProcessParameter mode fields");
        }
        Method methodUnique = unique(clsFindClass3, "covertToWatermarkYuvInfo", null);
        Method methodUnique2 = unique(clsFindClass2, "getXpanLandScapeCropRegion", int[].class);
        Method methodUnique3 = unique(clsFindClass2, "getXpanPortraitCropRegion", int[].class);
        XpanSurfaceGeometry.install(classLoader);
        XpanWatermarkBuffer.install(classLoader);
        XpanBareCrop.install(classLoader);
        XposedBridge.hookMethod(methodUnique, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanCropRegion.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException {
                if ("xpan".equals(fieldFindField2.get(methodHookParam.thisObject))) {
                    fieldFindField3.setInt(methodHookParam.thisObject, 3);
                }
            }
        });
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanCropRegion.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                ArrayDeque arrayDeque;
                if (Boolean.TRUE.equals(methodHookParam.getObjectExtra("omnicam.xpan.frame")) && (arrayDeque = (ArrayDeque) XpanCropRegion.INPUTS.get()) != null) {
                    if (!arrayDeque.isEmpty()) {
                        arrayDeque.pop();
                    }
                    if (arrayDeque.isEmpty()) {
                        XpanCropRegion.INPUTS.remove();
                    }
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Size size = null;
                try {
                    Object obj = methodHookParam.args[1];
                    if (obj != null) {
                        size = (Size) fieldFindField.get(obj);
                    }
                } catch (IllegalAccessException unused) {
                }
                ArrayDeque arrayDeque = (ArrayDeque) XpanCropRegion.INPUTS.get();
                if (arrayDeque == null) {
                    arrayDeque = new ArrayDeque();
                    XpanCropRegion.INPUTS.set(arrayDeque);
                }
                if (size == null) {
                    size = new Size(0, 0);
                }
                arrayDeque.push(size);
                methodHookParam.setObjectExtra("omnicam.xpan.frame", Boolean.TRUE);
            }
        });
        XposedBridge.hookMethod(methodUnique2, region(false));
        XposedBridge.hookMethod(methodUnique3, region(true));
        Log.i(TAG, "installed 65:24 crop using actual watermark input size");
    }

    public static void installCamera(ClassLoader classLoader) {
        Log.guard(TAG, "XPAN crop region", new pp1(classLoader, (byte) 2));
    }

    private static XC_MethodHook region(final boolean z) {
        return new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanCropRegion.3
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                ArrayDeque arrayDeque = (ArrayDeque) XpanCropRegion.INPUTS.get();
                Size size = arrayDeque == null ? null : (Size) arrayDeque.peek();
                if (size == null || size.getWidth() < 2 || size.getHeight() < 2 || methodHookParam.hasThrowable()) {
                    return;
                }
                try {
                    int[] iArrRegion = XpanCropMath.region(size.getWidth(), size.getHeight(), z);
                    Object result = methodHookParam.getResult();
                    if ((result instanceof int[]) && Arrays.equals(iArrRegion, (int[]) result)) {
                        return;
                    }
                    methodHookParam.setResult(iArrRegion);
                    StringBuilder sb = new StringBuilder();
                    sb.append(size);
                    sb.append(z ? " portrait " : " landscape ");
                    sb.append(Arrays.toString(iArrRegion));
                    Log.i(XpanCropRegion.TAG, sb.toString());
                } catch (IllegalArgumentException unused) {
                }
            }
        };
    }

    private static Method unique(Class<?> cls, String str, Class<?> cls2) throws NoSuchMethodException {
        Method method = null;
        for (Method method2 : cls.getDeclaredMethods()) {
            if (str.equals(method2.getName()) && (cls2 == null || method2.getReturnType() == cls2)) {
                if (method != null) {
                    t2.o("Ambiguous ".concat(str));
                    return null;
                }
                method = method2;
            }
        }
        if (method != null) {
            return method;
        }
        t2.o(str);
        return null;
    }
}
