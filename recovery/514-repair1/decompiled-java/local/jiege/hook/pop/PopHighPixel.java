package local.jiege.hook.pop;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.t2;
import java.lang.reflect.Method;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class PopHighPixel {
    private static final String PHOTO_MODE = "photo_mode";
    private static final String SAVED_MODE = "pop.captureMode";
    private static final String SDK = "com.oplus.ocs.camera.";
    private static final String TAG = "PopPort";

    private PopHighPixel() {
    }

    public static void install(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.consumer.ApsProcessor", classLoader);
        Class<?> clsFindClass2 = XposedHelpers.findClass("com.oplus.ocs.camera.common.util.CameraRequestTag", classLoader);
        int i = 0;
        for (Method method : clsFindClass.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if ("updateUpscaleSize".equals(method.getName()) && parameterTypes.length == 3 && parameterTypes[2] == clsFindClass2) {
                XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopHighPixel.1
                    public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Object objectExtra = methodHookParam.getObjectExtra(PopHighPixel.SAVED_MODE);
                        if (objectExtra != null) {
                            XposedHelpers.setObjectField(methodHookParam.args[2], "mCaptureMode", objectExtra);
                        }
                    }

                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Object obj = methodHookParam.args[2];
                        if (obj == null) {
                            return;
                        }
                        Object objectField = XposedHelpers.getObjectField(obj, "mCaptureMode");
                        if ((objectField instanceof String) && ((String) objectField).toLowerCase().contains("retro") && Settings.get("pop_high_pixel")) {
                            boolean booleanField = XposedHelpers.getBooleanField(obj, "mbIsDetectFace");
                            StringBuilder sb = new StringBuilder("POP capture face=");
                            sb.append(booleanField);
                            sb.append(" feature=");
                            sb.append(XposedHelpers.getObjectField(obj, "mApsDecisionFeatureType"));
                            sb.append(booleanField ? " -> stock size" : " -> photo-mode output size");
                            Log.i(PopHighPixel.TAG, sb.toString());
                            if (booleanField) {
                                return;
                            }
                            methodHookParam.setObjectExtra(PopHighPixel.SAVED_MODE, objectField);
                            XposedHelpers.setObjectField(obj, "mCaptureMode", PopHighPixel.PHOTO_MODE);
                        }
                    }
                });
                i++;
            }
        }
        if (i != 0) {
            return;
        }
        t2.j("ApsProcessor.updateUpscaleSize(..., CameraRequestTag) not found");
    }
}
