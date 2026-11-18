package local.jiege.hook.pop;

import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.c3;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class PopSdkGate {
    private static final String DECISION_PARAMETER = "com.oplus.ocs.camera.common.parameter.apsadapter.ApsDecisionParameter";
    private static final String PREVIEW_KEY = "com.oplus.ocs.camera.common.parameter.apsadapter.ApsBaseParameter$Key";
    private static final String PREVIEW_RESULT = "com.oplus.ocs.camera.appinterface.CameraPreviewCallbackAdapter$PreviewResult";
    private static final String SDK_MODE = "com.oplus.ocs.camera.producer.mode.BaseMode";
    private static final String SDK_PHOTO_MODE = "com.oplus.ocs.camera.producer.mode.PhotoMode";
    private static final String SDK_REQUEST_TAG = "com.oplus.ocs.camera.common.util.CameraRequestTag";
    private static final String TAG = "PopPort";
    private static final int USES_PER_PRESS = 2;
    private static final long WINDOW_MS = 4000;
    private static volatile long armedAt = 0;
    private static volatile int armedFrames = 1;
    private static volatile int armedUses;
    private static volatile Object keyCameraId;
    private static volatile Object keyCaptureMode;
    private static volatile Object keyFrameCount;
    private static volatile Method previewGet;
    private static volatile Method previewSet;
    private static volatile boolean reportedGate;
    private static volatile boolean reportedSeen;
    private static volatile boolean reportedWrite;

    private PopSdkGate() {
    }

    private static boolean allow(Class<?> cls) {
        Method method;
        Method[] declaredMethods = cls.getDeclaredMethods();
        int length = declaredMethods.length;
        int i = 0;
        while (true) {
            if (i >= length) {
                method = null;
                break;
            }
            method = declaredMethods[i];
            if (method.getName().equals("isAllowedToTakePicture") && method.getReturnType() == Integer.TYPE && method.getParameterTypes().length == 3) {
                break;
            }
            i++;
        }
        if (method == null) {
            return false;
        }
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopSdkGate.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                PopSdkGate.release(methodHookParam);
            }
        });
        return true;
    }

    public static void arm(int i) {
        if (i <= 0 || i > 32) {
            i = 1;
        }
        armedFrames = i;
        armedUses = USES_PER_PRESS;
        armedAt = SystemClock.elapsedRealtime();
    }

    private static boolean armed() {
        long j = armedAt;
        return armedUses > 0 && j != 0 && SystemClock.elapsedRealtime() - j < WINDOW_MS;
    }

    private static void consume() {
        int i = armedUses - 1;
        if (i <= 0) {
            i = 0;
        }
        armedUses = i;
        if (armedUses == 0) {
            armedAt = 0L;
        }
    }

    private static Class<?> find(ClassLoader classLoader, String str) {
        try {
            return XposedHelpers.findClass(str, classLoader);
        } catch (Throwable unused) {
            return null;
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v2, types: [java.lang.StringBuilder] */
    /* JADX WARN: Type inference failed for: r2v0, types: [boolean, int] */
    /* JADX WARN: Type inference failed for: r2v1, types: [int] */
    /* JADX WARN: Type inference failed for: r2v4 */
    /* JADX WARN: Type inference failed for: r2v5 */
    /* JADX WARN: Type inference failed for: r2v6 */
    public static void install(ClassLoader classLoader) {
        ?? r2;
        Class<?> clsFind = find(classLoader, SDK_MODE);
        if (clsFind == null) {
            Log.i(TAG, "POP SDK gate: com.oplus.ocs.camera.producer.mode.BaseMode is not on this phone's class path, the capture stays with the camera unit's own preview check");
            return;
        }
        keys(classLoader);
        ?? Repair = repair(clsFind, classLoader);
        Class<?> clsFind2 = find(classLoader, SDK_PHOTO_MODE);
        if (clsFind2 != null && repair(clsFind2, classLoader)) {
            r2 = Repair;
            r2 = Repair;
            r2 = Repair + 1;
        }
        r2 = Repair;
        r2 = Repair;
        r2 = Repair;
        boolean zAllow = allow(clsFind);
        if (r2 == 0 && !zAllow) {
            Log.i(TAG, "POP SDK gate: nothing to hook on com.oplus.ocs.camera.producer.mode.BaseMode");
            return;
        }
        ?? sb = new StringBuilder("POP SDK gate installed: preview frame count repaired on ");
        sb.append(r2);
        sb.append(" mode class(es), capture gate ");
        sb.append(zAllow ? "releasable" : "not reachable");
        Log.i(TAG, sb.toString());
    }

    private static String integer(Object obj, String str) {
        try {
            return String.valueOf(XposedHelpers.getIntField(obj, str));
        } catch (Throwable unused) {
            return "?";
        }
    }

    private static void keys(ClassLoader classLoader) {
        Class<?> clsFind = find(classLoader, DECISION_PARAMETER);
        Class<?> clsFind2 = find(classLoader, PREVIEW_KEY);
        Class<?> clsFind3 = find(classLoader, PREVIEW_RESULT);
        if (clsFind == null || clsFind2 == null || clsFind3 == null) {
            Log.i(TAG, "POP SDK gate: the preview parameter keys did not resolve, the frame count stays as the camera unit left it");
            return;
        }
        keyCameraId = staticField(clsFind, "KEY_PREVIEW_CAMERA_ID");
        keyCaptureMode = staticField(clsFind, "KEY_PREVIEW_CAPTURE_MODE");
        keyFrameCount = staticField(clsFind, "KEY_PREVIEW_MULTI_FRAME_COUNT");
        try {
            previewGet = clsFind3.getMethod("get", clsFind2);
            previewSet = clsFind3.getMethod("set", clsFind2, Object.class);
        } catch (Throwable th) {
            Log.e(TAG, "POP SDK gate: the preview result has no get/set, the frame count stays as the camera unit left it", th);
        }
    }

    private static Object object(Object obj, String str) {
        try {
            return XposedHelpers.getObjectField(obj, str);
        } catch (Throwable unused) {
            return null;
        }
    }

    private static String preview(Object obj) {
        return "camera " + value(obj, keyCameraId) + ", capture mode " + value(obj, keyCaptureMode) + ", frames " + value(obj, keyFrameCount);
    }

    private static Object previewResult(Object obj) {
        if (obj == null) {
            return null;
        }
        for (Class<?> superclass = obj.getClass(); superclass != null && superclass != Object.class; superclass = superclass.getSuperclass()) {
            try {
                Field declaredField = superclass.getDeclaredField("mPreviewResult");
                if (!Modifier.isStatic(declaredField.getModifiers())) {
                    declaredField.setAccessible(true);
                    return declaredField.get(obj);
                }
            } catch (NoSuchFieldException unused) {
            } catch (Throwable unused2) {
                return null;
            }
        }
        return null;
    }

    private static String reason(int i) {
        if (i == 1) {
            return "its preview decision has no frame";
        }
        if (i == 3) {
            return "a sensor mode switch timed out";
        }
        return i < 0 ? "a check that sets no code" : "a check inside the camera unit";
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void release(XC_MethodHook.MethodHookParam methodHookParam) {
        if (armed()) {
            Object result = methodHookParam.getResult();
            if (result instanceof Integer) {
                int iIntValue = ((Integer) result).intValue();
                consume();
                if (iIntValue == 0) {
                    if (reportedGate) {
                        return;
                    }
                    reportedGate = true;
                    Log.i(TAG, "POP capture: the SDK's capture gate accepted the request, the preview decision now carries a frame and nothing was overridden");
                    return;
                }
                if (iIntValue == USES_PER_PRESS) {
                    if (reportedGate) {
                        return;
                    }
                    reportedGate = true;
                    Log.i(TAG, "POP capture: the SDK refused the capture for memory or a preview mismatch (2) and has already reported it as failed, leaving it refused");
                    return;
                }
                if (!reportedGate) {
                    reportedGate = true;
                    Log.i(TAG, "POP capture: the SDK's capture gate still answered " + iIntValue + " (" + reason(iIntValue) + "), letting this one capture through on " + methodHookParam.thisObject.getClass().getSimpleName());
                }
                methodHookParam.setResult(0);
            }
        }
    }

    private static boolean repair(Class<?> cls, ClassLoader classLoader) {
        try {
            XposedBridge.hookMethod(cls.getDeclaredMethod("checkPreviewResult", XposedHelpers.findClass(SDK_REQUEST_TAG, classLoader)), new XC_MethodHook() { // from class: local.jiege.hook.pop.PopSdkGate.1
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    PopSdkGate.repairFrameCount(methodHookParam);
                }
            });
            return true;
        } catch (Throwable unused) {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void repairFrameCount(XC_MethodHook.MethodHookParam methodHookParam) {
        Object objPreviewResult;
        if (!armed() || previewGet == null || previewSet == null || (objPreviewResult = previewResult(methodHookParam.thisObject)) == null) {
            return;
        }
        Object[] objArr = methodHookParam.args;
        Object obj = objArr.length > 0 ? objArr[0] : null;
        try {
            Object objInvoke = previewGet.invoke(objPreviewResult, keyFrameCount);
            int iIntValue = objInvoke instanceof Integer ? ((Integer) objInvoke).intValue() : -1;
            if (!reportedSeen) {
                reportedSeen = true;
                Log.i(TAG, "POP capture: the SDK's preview gate sees " + preview(objPreviewResult) + ", and the request is " + request(obj) + ", on " + methodHookParam.thisObject.getClass().getSimpleName());
            }
            if (iIntValue > 0) {
                return;
            }
            try {
                previewSet.invoke(objPreviewResult, keyFrameCount, Integer.valueOf(armedFrames));
                if (reportedWrite) {
                    return;
                }
                reportedWrite = true;
                Log.i(TAG, "POP capture: the SDK's preview decision carried " + iIntValue + " frame(s), the " + armedFrames + " the app's own decision accepted is written into it so the capture is not refused for having nothing to take");
            } catch (Throwable th) {
                Log.e(TAG, "POP capture: the frame count could not be written into the preview result", th);
            }
        } catch (Throwable th2) {
            Log.e(TAG, "POP capture: the SDK preview result's frame count is unreadable", th2);
        }
    }

    private static String request(Object obj) {
        if (obj == null) {
            return "(no request tag)";
        }
        String strString = string(obj, "mCameraType");
        Object objObject = object(obj, "mApsRequestTag");
        String strString2 = objObject == null ? "?" : string(objObject, "mModeName");
        StringBuilder sb = new StringBuilder("camera id ");
        c3.s(sb, integer(obj, "mRearFrontCameraId"), ", type ", strString, ", aps mode ");
        sb.append(strString2);
        return sb.toString();
    }

    private static Object staticField(Class<?> cls, String str) {
        try {
            return XposedHelpers.getStaticObjectField(cls, str);
        } catch (Throwable unused) {
            return null;
        }
    }

    private static String string(Object obj, String str) {
        Object objObject = object(obj, str);
        return objObject == null ? "?" : String.valueOf(objObject);
    }

    private static Object value(Object obj, Object obj2) {
        if (obj2 != null && previewGet != null) {
            try {
                return previewGet.invoke(obj, obj2);
            } catch (Throwable unused) {
            }
        }
        return "?";
    }
}
