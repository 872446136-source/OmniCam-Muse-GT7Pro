package local.jiege.hook.tilt;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import defpackage.mo0;
import java.util.ArrayList;
import java.util.Arrays;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class TiltShiftCapture {
    private static final String ROTATE_MIRROR = "aps_algo_rotate_mirror";
    private static final String SDK = "com.oplus.ocs.camera.";
    private static final String TAG = "TiltShift";
    private static final String TILT_SHIFT = "aps_algo_tilt_shift";

    private TiltShiftCapture() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookCaptureList(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.consumer.ApsProcessor", classLoader);
        Class clsFindClass2 = XposedHelpers.findClass("com.oplus.ocs.camera.common.util.CameraRequestTag", classLoader);
        final Object staticObjectField = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.oplus.ocs.camera.common.util.ParameterKeys", classLoader), "KEY_CAPTURE_ALGO_LIST");
        XposedHelpers.findAndHookMethod(clsFindClass, "createMetaItemInfo", new Object[]{clsFindClass2, new XC_MethodHook() { // from class: local.jiege.hook.tilt.TiltShiftCapture.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (methodHookParam.hasThrowable()) {
                    return;
                }
                try {
                    Object obj = methodHookParam.args[0];
                    if (XposedHelpers.getBooleanField(obj, "mbTiltShiftOpen") && XposedHelpers.getIntField(obj, "mTiltShiftBlurValue") > 0) {
                        Object result = methodHookParam.getResult();
                        String[] strArr = (String[]) XposedHelpers.callMethod(result, "get", new Object[]{staticObjectField});
                        if (strArr == null) {
                            return;
                        }
                        ArrayList arrayList = new ArrayList(Arrays.asList(strArr));
                        if (arrayList.contains(TiltShiftCapture.TILT_SHIFT)) {
                            return;
                        }
                        int iIndexOf = arrayList.indexOf(TiltShiftCapture.ROTATE_MIRROR);
                        if (iIndexOf < 0) {
                            iIndexOf = arrayList.size();
                        }
                        arrayList.add(iIndexOf, TiltShiftCapture.TILT_SHIFT);
                        XposedHelpers.callMethod(result, "setParameter", new Object[]{staticObjectField, arrayList.toArray(new String[0])});
                        Log.i(TiltShiftCapture.TAG, "tilt-shift capture algorithm added " + arrayList);
                    }
                } catch (Throwable th) {
                    Log.e(TiltShiftCapture.TAG, "capture algorithm update failed", th);
                }
            }
        }});
    }

    public static void installCamera(ClassLoader classLoader) {
        Log.guard(TAG, "capture algorithm", new mo0(classLoader, (byte) 29));
    }
}
