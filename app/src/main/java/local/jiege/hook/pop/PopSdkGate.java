package local.jiege.hook.pop;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import local.jiege.hook.common.Log;

/**
 * The fourth wall of the POP shutter, and the first one that is not in the camera app at all.
 *
 * <p>With the app's own gates out of the way (493 to 495) the capture still died three milliseconds
 * after the request went out:
 *
 * <pre>
 *   E/CameraUnit, ProducerImpl: takePicture, not allow take picture, mbApsFinishAddFrame: true,
 *                               isApsCaptureAlgoInitializing: false, checkResult: 1, ...
 *   E/OCAM_ApsService: onCaptureFailed, requestId: 96641371
 * </pre>
 *
 * <p>{@code checkResult} is the return of {@code ModeInterface.isAllowedToTakePicture}, which lives
 * in {@code /product/framework/com.oplus.camera.unit.sdk.jar} — not in the app, which is why no app
 * symbol ever explained it. Its first statement is
 *
 * <pre>
 *   if (checkPreviewResult(tag) != 0) {
 *       log(V, "isAllowedToTakePicture, checkPreviewResult return false"); return 1;
 *   }
 * </pre>
 *
 * <p>and that is the only place in the whole SDK that can produce a 1: {@code checkZoomRatio} is a
 * constant {@code true}, and {@code PhotoMode} / {@code PortraitMode} / {@code ProfessionalMode}
 * only ever return 0, 2 or 3. That line is written at verbose level, which is why the report
 * carried no reason next to its {@code checkResult: 1}.
 *
 * <p>{@code checkPreviewResult} answers "is the preview decision I am holding unusable for a
 * capture", and it says yes when that decision is for this camera and this mode but carries
 * {@code com.oplus.preview.multi_frame_count <= 0} — no frame to hand over. POP's decision is
 * exactly that: the decision the app itself accepts at shutter time reports
 * {@code mMultiFrameCount: 1}, while the SDK's copy is still the one APS sent when the mode opened,
 * and that one reported 0.
 *
 * <p>So this gate does two things and says which one was needed:
 *
 * <ol>
 *   <li>before the check runs, it writes the frame count the app accepted into the SDK's preview
 *       result — the stale datum repaired, not the check removed;
 *   <li>if the check still refuses, it lets that one capture through and logs the number, so a
 *       report always names the remaining reason instead of a bare {@code checkResult: 1}.
 * </ol>
 *
 * <p>A memory refusal is never overridden: by the time {@code BaseMode} returns 2 it has already
 * delivered the failure to the app, and a second capture on top of that only muddies the report.
 *
 * <p>The gate is armed by {@link PopCaptureGate} for the few seconds around a POP shutter press, so
 * every other mode keeps the SDK's behaviour untouched.
 */
final class PopSdkGate {
    private static final String TAG = "PopPort";

    private static final String SDK_MODE = "com.oplus.ocs.camera.producer.mode.BaseMode";
    private static final String SDK_PHOTO_MODE = "com.oplus.ocs.camera.producer.mode.PhotoMode";
    private static final String SDK_REQUEST_TAG = "com.oplus.ocs.camera.common.util.CameraRequestTag";
    private static final String DECISION_PARAMETER =
        "com.oplus.ocs.camera.common.parameter.apsadapter.ApsDecisionParameter";
    private static final String PREVIEW_KEY =
        "com.oplus.ocs.camera.common.parameter.apsadapter.ApsBaseParameter$Key";
    private static final String PREVIEW_RESULT =
        "com.oplus.ocs.camera.appinterface.CameraPreviewCallbackAdapter$PreviewResult";

    /** How long after a POP shutter press the SDK gate still counts as that press. */
    private static final long WINDOW_MS = 4000L;

    /**
     * How many times the capture gate may be asked about one press. {@code takePicture} can be
     * called twice for a single press (the touch-down stage asks too), and both belong to the same
     * press — but a mode switch must not inherit the arming, so it ends here.
     */
    private static final int USES_PER_PRESS = 2;

    private static volatile long armedAt;
    private static volatile int armedUses;
    private static volatile int armedFrames = 1;
    private static volatile boolean reportedSeen;
    private static volatile boolean reportedWrite;
    private static volatile boolean reportedGate;

    private static volatile Object keyCameraId;
    private static volatile Object keyCaptureMode;
    private static volatile Object keyFrameCount;
    private static volatile Method previewGet;
    private static volatile Method previewSet;

    private PopSdkGate() {}

    /**
     * Marks the next few seconds as a POP capture and says how many frames the app's own decision
     * carries. Called from {@link PopCaptureGate} for the retro mode only.
     */
    static void arm(int frames) {
        // A single capture asks for a handful of frames; anything outside that is a field this
        // build does not lay out the way the dump says, and one frame is the safe reading.
        armedFrames = frames > 0 && frames <= 32 ? frames : 1;
        armedUses = USES_PER_PRESS;
        armedAt = android.os.SystemClock.elapsedRealtime();
    }

    private static boolean armed() {
        long at = armedAt;
        return armedUses > 0 && at != 0
            && android.os.SystemClock.elapsedRealtime() - at < WINDOW_MS;
    }

    /** One press has now been judged; the arming ends when its uses are gone. */
    private static void consume() {
        int left = armedUses - 1;
        armedUses = left > 0 ? left : 0;
        if (armedUses == 0) armedAt = 0;
    }

    static void install(ClassLoader classLoader) {
        Class<?> base = find(classLoader, SDK_MODE);
        if (base == null) {
            Log.i(TAG, "POP SDK gate: " + SDK_MODE + " is not on this phone's class path, the "
                + "capture stays with the camera unit's own preview check");
            return;
        }
        keys(classLoader);
        int repaired = 0;
        if (repair(base, classLoader)) repaired++;
        Class<?> photo = find(classLoader, SDK_PHOTO_MODE);
        if (photo != null && repair(photo, classLoader)) repaired++;
        boolean gate = allow(base);
        if (repaired == 0 && !gate) {
            Log.i(TAG, "POP SDK gate: nothing to hook on " + SDK_MODE);
            return;
        }
        Log.i(TAG, "POP SDK gate installed: preview frame count repaired on " + repaired
            + " mode class(es), capture gate " + (gate ? "releasable" : "not reachable"));
    }

    private static Class<?> find(ClassLoader classLoader, String name) {
        try {
            return XposedHelpers.findClass(name, classLoader);
        } catch (Throwable missing) {
            return null;
        }
    }

    /** The three preview keys, read once. An absent key leaves the repair switched off. */
    private static void keys(ClassLoader classLoader) {
        Class<?> parameter = find(classLoader, DECISION_PARAMETER);
        Class<?> key = find(classLoader, PREVIEW_KEY);
        Class<?> result = find(classLoader, PREVIEW_RESULT);
        if (parameter == null || key == null || result == null) {
            Log.i(TAG, "POP SDK gate: the preview parameter keys did not resolve, the frame count "
                + "stays as the camera unit left it");
            return;
        }
        keyCameraId = staticField(parameter, "KEY_PREVIEW_CAMERA_ID");
        keyCaptureMode = staticField(parameter, "KEY_PREVIEW_CAPTURE_MODE");
        keyFrameCount = staticField(parameter, "KEY_PREVIEW_MULTI_FRAME_COUNT");
        try {
            previewGet = result.getMethod("get", key);
            previewSet = result.getMethod("set", key, Object.class);
        } catch (Throwable error) {
            Log.e(TAG, "POP SDK gate: the preview result has no get/set, the frame count stays as "
                + "the camera unit left it", error);
        }
    }

    private static Object staticField(Class<?> owner, String name) {
        try {
            return XposedHelpers.getStaticObjectField(owner, name);
        } catch (Throwable missing) {
            return null;
        }
    }

    /**
     * Hooks {@code checkPreviewResult(CameraRequestTag):boolean} on one mode class. Returns true
     * when that class really declares it — the subclasses that only inherit it are covered by the
     * hook on the class that does.
     */
    private static boolean repair(Class<?> mode, ClassLoader classLoader) {
        Class<?> tag;
        try {
            tag = XposedHelpers.findClass(SDK_REQUEST_TAG, classLoader);
        } catch (Throwable missing) {
            return false;
        }
        Method check;
        try {
            check = mode.getDeclaredMethod("checkPreviewResult", tag);
        } catch (Throwable missing) {
            return false;
        }
        XposedBridge.hookMethod(check, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                repairFrameCount(param);
            }
        });
        return true;
    }

    /**
     * Puts the frame count the app accepted into the preview result the SDK is about to check, and
     * writes one line about what it found. Only for a capture that was just armed.
     */
    private static void repairFrameCount(XC_MethodHook.MethodHookParam param) {
        if (!armed() || previewGet == null || previewSet == null) return;
        Object preview = previewResult(param.thisObject);
        if (preview == null) return;
        Object tag = param.args.length > 0 ? param.args[0] : null;
        int frames;
        try {
            Object value = previewGet.invoke(preview, keyFrameCount);
            frames = value instanceof Integer ? (Integer) value : -1;
        } catch (Throwable error) {
            Log.e(TAG, "POP capture: the SDK preview result's frame count is unreadable", error);
            return;
        }
        if (!reportedSeen) {
            reportedSeen = true;
            Log.i(TAG, "POP capture: the SDK's preview gate sees " + preview(preview)
                + ", and the request is " + request(tag) + ", on "
                + param.thisObject.getClass().getSimpleName());
        }
        if (frames > 0) return;
        try {
            previewSet.invoke(preview, keyFrameCount, Integer.valueOf(armedFrames));
            if (!reportedWrite) {
                reportedWrite = true;
                Log.i(TAG, "POP capture: the SDK's preview decision carried " + frames
                    + " frame(s), the " + armedFrames + " the app's own decision accepted is "
                    + "written into it so the capture is not refused for having nothing to take");
            }
        } catch (Throwable error) {
            Log.e(TAG, "POP capture: the frame count could not be written into the preview result",
                error);
        }
    }

    /**
     * Hooks the one method every SDK mode funnels through. Its answer is logged before it is
     * released, so the next report says whether the repair above was enough on its own.
     */
    private static boolean allow(Class<?> mode) {
        Method allowed = null;
        for (Method method : mode.getDeclaredMethods()) {
            if (!method.getName().equals("isAllowedToTakePicture")) continue;
            if (method.getReturnType() != int.class) continue;
            if (method.getParameterTypes().length != 3) continue;
            allowed = method;
            break;
        }
        if (allowed == null) return false;
        XposedBridge.hookMethod(allowed, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                release(param);
            }
        });
        return true;
    }

    private static void release(XC_MethodHook.MethodHookParam param) {
        if (!armed()) return;
        Object answer = param.getResult();
        if (!(answer instanceof Integer)) return;
        int code = (Integer) answer;
        // One press is one judgement: the arming is spent whether or not it was released.
        consume();
        if (code == 0) {
            if (!reportedGate) {
                reportedGate = true;
                Log.i(TAG, "POP capture: the SDK's capture gate accepted the request, the preview "
                    + "decision now carries a frame and nothing was overridden");
            }
            return;
        }
        if (code == 2) {
            if (!reportedGate) {
                reportedGate = true;
                Log.i(TAG, "POP capture: the SDK refused the capture for memory or a preview "
                    + "mismatch (2) and has already reported it as failed, leaving it refused");
            }
            return;
        }
        if (!reportedGate) {
            reportedGate = true;
            Log.i(TAG, "POP capture: the SDK's capture gate still answered " + code + " ("
                + reason(code) + "), letting this one capture through on "
                + param.thisObject.getClass().getSimpleName());
        }
        param.setResult(Integer.valueOf(0));
    }

    private static String reason(int code) {
        if (code == 1) return "its preview decision has no frame";
        if (code == 3) return "a sensor mode switch timed out";
        if (code < 0) return "a check that sets no code";
        return "a check inside the camera unit";
    }

    /** The preview result on the mode object, whichever class in the hierarchy declares it. */
    private static Object previewResult(Object mode) {
        if (mode == null) return null;
        for (Class<?> type = mode.getClass(); type != null && type != Object.class;
                type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("mPreviewResult");
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                return field.get(mode);
            } catch (NoSuchFieldException next) {
                // keep walking up
            } catch (Throwable error) {
                return null;
            }
        }
        return null;
    }

    private static String preview(Object preview) {
        return "camera " + value(preview, keyCameraId)
            + ", capture mode " + value(preview, keyCaptureMode)
            + ", frames " + value(preview, keyFrameCount);
    }

    private static Object value(Object preview, Object key) {
        if (key == null || previewGet == null) return "?";
        try {
            return previewGet.invoke(preview, key);
        } catch (Throwable error) {
            return "?";
        }
    }

    private static String request(Object tag) {
        if (tag == null) return "(no request tag)";
        String type = string(tag, "mCameraType");
        Object aps = object(tag, "mApsRequestTag");
        String name = aps == null ? "?" : string(aps, "mModeName");
        return "camera id " + integer(tag, "mRearFrontCameraId") + ", type " + type
            + ", aps mode " + name;
    }

    private static String string(Object owner, String name) {
        Object value = object(owner, name);
        return value == null ? "?" : String.valueOf(value);
    }

    private static Object object(Object owner, String name) {
        try {
            return XposedHelpers.getObjectField(owner, name);
        } catch (Throwable missing) {
            return null;
        }
    }

    private static String integer(Object owner, String name) {
        try {
            return String.valueOf(XposedHelpers.getIntField(owner, name));
        } catch (Throwable missing) {
            return "?";
        }
    }
}
