package local.jiege.hook.xpan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Size;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.t2;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.SymbolSearch;
import org.json.JSONObject;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class XpanWatermarkBuffer {
    private static final String SOURCE_SIZE = "omnicam.xpan.source.size";

    public static void install(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkAssembler", classLoader);
        Method method = null;
        for (Method method2 : clsFindClass.getDeclaredMethods()) {
            if (method2.getName().equals("assemble") && method2.getParameterTypes().length == 5) {
                if (method != null) {
                    t2.o("Ambiguous watermark assemble");
                    return;
                }
                method = method2;
            }
        }
        if (method == null) {
            t2.o("watermark assemble");
            return;
        }
        final Field fieldFindField = XposedHelpers.findField(method.getParameterTypes()[0], "s");
        Class<?> type = fieldFindField.getType();
        final Field fieldFindField2 = XposedHelpers.findField(type, "e");
        final Field fieldFindField3 = XposedHelpers.findField(type, "y");
        final Field fieldFindField4 = XposedHelpers.findField(type, "k");
        final Field fieldFindField5 = XposedHelpers.findField(type, "p");
        final Field fieldFindField6 = XposedHelpers.findField(type, "l");
        XposedBridge.hookMethod(XposedHelpers.findClass("com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkCreator", classLoader).getDeclaredMethod("createWatermarkBitmapHold", Context.class, type), new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanWatermarkBuffer.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj;
                Object result = methodHookParam.getResult();
                if (result == null || (obj = fieldFindField.get(result)) == null || !"xpan".equals(fieldFindField2.get(obj))) {
                    return;
                }
                Object objectField = XposedHelpers.getObjectField(obj, "z0");
                if ((objectField instanceof JSONObject) && "omnicam_xpan_crop".equals(((JSONObject) objectField).optString("styleId"))) {
                    ((Bitmap[]) XposedHelpers.getObjectField(result, "v"))[0] = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
                    ((int[]) XposedHelpers.getObjectField(result, SymbolSearch.METHOD))[0] = 2;
                    ((int[]) XposedHelpers.getObjectField(result, "n"))[0] = 2;
                    ((byte[][]) XposedHelpers.getObjectField(result, "y"))[0] = new byte[16];
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException {
                Object objCallMethod;
                Object obj = methodHookParam.args[1];
                if (obj != null && "xpan".equals(fieldFindField2.get(obj)) && fieldFindField3.getInt(obj) == 3) {
                    Size size = (Size) fieldFindField4.get(obj);
                    Size size2 = (Size) fieldFindField5.get(obj);
                    if (size == null || size2 == null || size.equals(size2) || (objCallMethod = XposedHelpers.callMethod(obj, "clone", new Object[0])) == null) {
                        return;
                    }
                    fieldFindField5.set(objCallMethod, size);
                    XposedHelpers.setAdditionalInstanceField(objCallMethod, XpanWatermarkBuffer.SOURCE_SIZE, size2);
                    methodHookParam.args[1] = objCallMethod;
                }
            }
        });
        Class cls = Integer.TYPE;
        Class cls2 = Long.TYPE;
        final Method method3 = clsFindClass.getMethod("cropOriginBuffer", cls2, cls, cls);
        final Method method4 = clsFindClass.getMethod("releaseHardwareBuffer", cls2);
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanWatermarkBuffer.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException, InvocationTargetException {
                Object objectExtra = methodHookParam.getObjectExtra("omnicam.xpan.buffer");
                if (objectExtra instanceof Long) {
                    method4.invoke(null, objectExtra);
                }
                Object objectExtra2 = methodHookParam.getObjectExtra("omnicam.xpan.bitmap");
                if (objectExtra2 instanceof Bitmap) {
                    ((Bitmap) objectExtra2).recycle();
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException {
                Object obj = fieldFindField.get(methodHookParam.args[0]);
                if (obj != null && "xpan".equals(fieldFindField2.get(obj)) && fieldFindField3.getInt(obj) == 3) {
                    Size size = (Size) fieldFindField4.get(obj);
                    Size size2 = (Size) fieldFindField5.get(obj);
                    Object additionalInstanceField = XposedHelpers.getAdditionalInstanceField(obj, XpanWatermarkBuffer.SOURCE_SIZE);
                    if (additionalInstanceField instanceof Size) {
                        size2 = (Size) additionalInstanceField;
                    }
                    if (size == null || size2 == null || size.equals(size2) || size.getWidth() > size2.getWidth() || size.getHeight() > size2.getHeight()) {
                        return;
                    }
                    Object obj2 = methodHookParam.args[2];
                    if (obj2 instanceof Bitmap) {
                        Bitmap bitmap = (Bitmap) obj2;
                        if ((bitmap.getWidth() == size.getWidth() && bitmap.getHeight() == size.getHeight()) || bitmap.getWidth() < size.getWidth() || bitmap.getHeight() < size.getHeight()) {
                            return;
                        }
                        Point point = (Point) fieldFindField6.get(obj);
                        int width = point == null ? (bitmap.getWidth() - size.getWidth()) / 2 : point.x;
                        int height = point == null ? (bitmap.getHeight() - size.getHeight()) / 2 : point.y;
                        if (width < 0 || height < 0 || size.getWidth() + width > bitmap.getWidth() || size.getHeight() + height > bitmap.getHeight()) {
                            return;
                        }
                        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(bitmap, width, height, size.getWidth(), size.getHeight());
                        methodHookParam.args[2] = bitmapCreateBitmap;
                        if (bitmapCreateBitmap != bitmap) {
                            methodHookParam.setObjectExtra("omnicam.xpan.bitmap", bitmapCreateBitmap);
                        }
                    } else {
                        boolean z = obj2 instanceof Long;
                        if (!z && !(obj2 instanceof String)) {
                            return;
                        }
                        long jLongValue = z ? ((Long) obj2).longValue() : Long.parseLong((String) obj2);
                        Long l = (Long) method3.invoke(null, Long.valueOf(jLongValue), Integer.valueOf(size.getWidth()), Integer.valueOf(size.getHeight()));
                        long jLongValue2 = l.longValue();
                        if (jLongValue2 == 0 || jLongValue2 == jLongValue) {
                            return;
                        }
                        methodHookParam.args[2] = l;
                        methodHookParam.setObjectExtra("omnicam.xpan.buffer", l);
                    }
                    Log.i("XpanBuffer", "cropped source before watermark: " + size2 + " -> " + size);
                }
            }
        });
    }
}
