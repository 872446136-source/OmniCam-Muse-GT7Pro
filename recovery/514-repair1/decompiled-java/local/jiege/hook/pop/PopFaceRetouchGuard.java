package local.jiege.hook.pop;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import java.util.ArrayList;
import java.util.Arrays;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class PopFaceRetouchGuard {
    private static final String FACE_RETOUCH = "aps_algo_facebase_retouch";
    private static final String SDK = "com.oplus.ocs.camera.";
    private static final String TAG = "PopPort";

    private PopFaceRetouchGuard() {
    }

    public static void install(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.consumer.ApsProcessor", classLoader);
        Class clsFindClass2 = XposedHelpers.findClass("com.oplus.ocs.camera.common.util.CameraRequestTag", classLoader);
        final Object staticObjectField = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.oplus.ocs.camera.common.util.ParameterKeys", classLoader), "KEY_CAPTURE_ALGO_LIST");
        XposedHelpers.findAndHookMethod(clsFindClass, "createMetaItemInfo", new Object[]{clsFindClass2, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopFaceRetouchGuard.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (methodHookParam.hasThrowable()) {
                    return;
                }
                Object obj = methodHookParam.args[0];
                Object objectField = XposedHelpers.getObjectField(obj, "mCaptureMode");
                if ((objectField instanceof String) && ((String) objectField).toLowerCase().contains("retro") && XposedHelpers.getIntField(obj, "mRearFrontCameraId") == 0) {
                    Object result = methodHookParam.getResult();
                    String[] strArr = (String[]) XposedHelpers.callMethod(result, "get", new Object[]{staticObjectField});
                    Log.i(PopFaceRetouchGuard.TAG, "rear POP algorithms " + Arrays.toString(strArr));
                    if (strArr == null) {
                        return;
                    }
                    ArrayList arrayList = new ArrayList();
                    for (String str : strArr) {
                        if (!PopFaceRetouchGuard.FACE_RETOUCH.equals(str)) {
                            arrayList.add(str);
                        }
                    }
                    if (arrayList.size() == strArr.length) {
                        return;
                    }
                    XposedHelpers.callMethod(result, "setParameter", new Object[]{staticObjectField, arrayList.toArray(new String[0])});
                    Log.i(PopFaceRetouchGuard.TAG, "rear POP face retouch removed");
                }
            }
        }});
    }
}
