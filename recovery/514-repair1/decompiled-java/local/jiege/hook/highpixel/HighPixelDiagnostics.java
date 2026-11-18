package local.jiege.hook.highpixel;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import java.util.Arrays;
import local.jiege.hook.common.Log;

/* JADX INFO: Access modifiers changed from: package-private */
/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class HighPixelDiagnostics {
    private static final String MODE = "high_pixel_mode";
    private static final String TAG = "HighPixelRequest";

    private HighPixelDiagnostics() {
    }

    public static void install(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.consumer.ApsProcessor", classLoader);
        Class clsFindClass2 = XposedHelpers.findClass("com.oplus.ocs.camera.common.util.CameraRequestTag", classLoader);
        final Object staticObjectField = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.oplus.ocs.camera.common.util.ParameterKeys", classLoader), "KEY_CAPTURE_ALGO_LIST");
        XposedHelpers.findAndHookMethod(clsFindClass, "createMetaItemInfo", new Object[]{clsFindClass2, new XC_MethodHook() { // from class: local.jiege.hook.highpixel.HighPixelDiagnostics.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    Object obj = methodHookParam.args[0];
                    if (HighPixelDiagnostics.MODE.equals(XposedHelpers.getObjectField(obj, "mCaptureMode"))) {
                        Object objectField = XposedHelpers.getObjectField(obj, "mApsRequestTag");
                        String string = "?";
                        if (!methodHookParam.hasThrowable() && methodHookParam.getResult() != null) {
                            Object objCallMethod = XposedHelpers.callMethod(methodHookParam.getResult(), "get", new Object[]{staticObjectField});
                            string = objCallMethod instanceof String[] ? Arrays.toString((String[]) objCallMethod) : String.valueOf(objCallMethod);
                        }
                        Log.i(HighPixelDiagnostics.TAG, "id=" + XposedHelpers.getIntField(obj, "mRequestId") + " camera=" + XposedHelpers.getIntField(obj, "mRearFrontCameraId") + " live=" + XposedHelpers.getBooleanField(obj, "mbIsLivePhotoOn") + " pictureSize=" + XposedHelpers.getObjectField(objectField, "mPictureSize") + " highPictureSize=" + XposedHelpers.getBooleanField(objectField, "mbHighPictureSizeEnable") + " aiHighPixel=" + XposedHelpers.getBooleanField(objectField, "mbAiHighPixelEnable") + " tenBit=" + XposedHelpers.getBooleanField(obj, "mbPhoto10BitsEnable") + " ultraHdr=" + XposedHelpers.getBooleanField(obj, "mbUltraHdrEnable") + " rawSR=" + XposedHelpers.getBooleanField(obj, "mbRawSREnable") + " sensorSizes=" + Arrays.toString((Object[]) XposedHelpers.getObjectField(obj, "mSensorSizes")) + " evList=" + Arrays.toString((int[]) XposedHelpers.getObjectField(obj, "mCaptureEvList")) + " palette=" + XposedHelpers.getBooleanField(obj, "mbPaletteEnable") + " filterOpen=" + XposedHelpers.getBooleanField(obj, "mbFilterOpen") + " filter=" + XposedHelpers.getObjectField(obj, "mFilterType") + " softLight=" + XposedHelpers.getBooleanField(obj, "mbSoftLightOpen") + "/" + XposedHelpers.getObjectField(obj, "mSoftLightType") + " algorithms=" + string);
                    }
                } catch (Throwable th) {
                    Log.e(HighPixelDiagnostics.TAG, "log", th);
                }
            }
        }});
        Log.i(TAG, "installed");
    }
}
