package local.jiege.hook.pop;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/**
 * POP (retro camera): the shutter does nothing, and the camera says so itself.
 *
 * <p>The 488/491 reports both end the same way — the click is delivered, nothing is written to
 * DCIM, and the only line that explains it is
 *
 * <pre>
 *   OCAM_t1_BaseMode isReadyToCapture, decisionResult is null or mCameraId not matched, so return
 * </pre>
 *
 * <p>That message is the tail of {@code BaseMode.Td} (this build {@code hm.t1.Td}), and the decision
 * is not made there: {@code Td} loads the last preview decision ({@code BaseMode.d1}, this build
 * {@code Lcb/e;}) and asks {@code BaseMode.n8(cb.e, boolean, boolean)} whether a capture may start.
 * {@code n8} has four exits and only three of them log:
 *
 * <pre>
 *   decision == null      -> "capture, decisionResult is null"                     logs
 *   !decision.a           -> "capture, decisionResult not allow to capture"        logs
 *   !Rc(decision)         -> "capture, checkPreviewResult not support capture"     logs
 *   decision.b != getCameraId()                                                    silent
 * </pre>
 *
 * <p>The 491 run had none of the three lines and one of the fourth, so the preview decision was
 * built, allowed the capture, and passed the preview check — it simply carried a different camera
 * id than the mode reports. POP runs on the {@code rear_sat} camera (the log's
 * {@code targetCameraId: 12}), while the APS preview decision keeps the logical id it was computed
 * for, and {@code n8} compares the two verbatim.
 *
 * <p>This gate aligns that one field before {@code n8} runs, and lets the method's own checks decide
 * everything else. It never forces a result: a null decision, a memory limit, a live-photo
 * incompatibility and every later check in {@code Td} still refuse the shutter exactly as before.
 *
 * <p>Scoped to the retro mode when {@code RetroMode} resolves (it does on this phone, see
 * {@code PopFilterGate}); other modes already agree on the id, so the write is a no-op for them.
 */
final class PopCaptureGate {
    private static final String TAG = "PopPort";
    private static final String BASE_MODE = "com.oplus.camera.module.BaseMode";

    /** The decision class and its camera id, once found. */
    private static volatile Field cameraId;

    /** The preview decision type, kept so the pre-EV-list step can scan the mode's fields. */
    private static volatile Class<?> decisionType;

    private static volatile boolean reportedFix;
    private static volatile boolean reportedScope;
    private static volatile boolean reportedStable;
    private static volatile boolean reportedPreEvList;
    private static volatile boolean reportedSource;

    private PopCaptureGate() {}

    static void install(ClassLoader classLoader) {
        try {
            Class<?> base = XposedHelpers.findClass(BASE_MODE, classLoader);
            Method decide = decisionCheck(base);
            if (decide == null) {
                Log.i(TAG, "POP capture gate: " + BASE_MODE
                    + " has no (decision, boolean, boolean):boolean, the shutter is left as the camera has it");
                return;
            }
            Class<?> decision = decide.getParameterTypes()[0];
            Field id = cameraIdOf(decision);
            if (id == null) {
                Log.i(TAG, "POP capture gate: " + decision.getName()
                    + " has no int camera id, the shutter is left as the camera has it");
                return;
            }
            Method current = getCameraId(base);
            if (current == null) {
                Log.i(TAG, "POP capture gate: no getCameraId() on " + BASE_MODE);
                return;
            }
            cameraId = id;
            decisionType = decision;

            XposedBridge.hookMethod(decide, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    align(param, current);
                    startPreEvList(param);
                }
            });
            Log.i(TAG, "POP capture gate installed: " + BASE_MODE + "." + decide.getName()
                + "(" + decision.getName() + ", boolean, boolean), camera id field " + id.getName());
        } catch (Throwable error) {
            Log.e(TAG, "POP capture gate unavailable", error);
        }
        skipFallbackWait();
        reportDecisionSource();
    }

    /**
     * The second wall, found on the device right after the first one fell: {@code Td} then asks
     * {@code BaseMode.jc(boolean, boolean)} whether the pipeline fallback has settled, and on this
     * phone it never does.
     *
     * <pre>
     *   OCAM_t1_BaseMode: isFallBackStable, wait fallback be stable to capture,
     *                     isFallbackMatched: true, isMasterPipelineMatched: false
     *   OCAM_AutoLogHelper: tryCatchCaptureFailedLog, reason: CF_WAIT_FALLBACK
     * </pre>
     *
     * <p>{@code jc} returns true when {@code kc()} says the device does not want this check, or when
     * {@code nd()} says the fallback is stable. {@code kc()} reads three {@code CameraConfig}
     * booleans ({@code com.oplus.capture.wait.fallback.stable.support} among them) and this firmware
     * wants the check; {@code nd()} asks the UI layer for a flag that POP never sets.
     *
     * <p>Worse than a plain refusal, the wait branch also writes {@code P/Q/R/X} on the mode and
     * disables the shutter, so the next press is refused earlier with
     * {@code CF_SHUTTER_BUTTON_IS_NOT_CLICKABLE} — the "press it and nothing happens" in the report.
     *
     * <p>So the wait is skipped for the retro mode only, before the body runs: the shutter goes
     * straight on to the capture with whatever pipeline the preview is already showing. Every other
     * mode keeps the wait exactly as the camera has it.
     */
    private static void skipFallbackWait() {
        try {
            Symbols symbols = Symbols.get();
            if (!symbols.has("BaseMode.fallbackStable")) {
                Log.i(TAG, "POP capture gate: no BaseMode.fallbackStable symbol, POP keeps waiting on a "
                    + "fallback that never settles on this phone");
                return;
            }
            Method stable = symbols.method("BaseMode.fallbackStable");
            XposedBridge.hookMethod(stable, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!inRetroMode(param.thisObject)) return;
                    param.setResult(Boolean.TRUE);
                    if (!reportedStable) {
                        reportedStable = true;
                        Log.i(TAG, "POP capture: the fallback-stable wait is skipped in POP, it never "
                            + "settles on this phone and it also disables the shutter");
                    }
                }
            });
            Log.i(TAG, "POP capture gate: fallback-stable wait skipped in POP (" + BASE_MODE + "."
                + stable.getName() + ")");
        } catch (Throwable error) {
            Log.e(TAG, "POP capture gate could not skip the fallback wait", error);
        }
    }

    /** Puts the decision on the mode's camera id, so the camera's own check can pass. */
    private static void align(XC_MethodHook.MethodHookParam param, Method current) {
        if (param.args.length < 1 || param.args[0] == null) {
            return;
        }
        if (!inRetroMode(param.thisObject)) {
            return;
        }
        Field id = cameraId;
        if (id == null) {
            return;
        }
        try {
            int mode = (Integer) current.invoke(param.thisObject);
            int decided = id.getInt(param.args[0]);
            if (decided == mode) {
                return;
            }
            id.setInt(param.args[0], mode);
            if (!reportedFix) {
                reportedFix = true;
                Log.i(TAG, "POP capture: the preview decision was made for camera " + decided
                    + " and the mode is on " + mode + ", aligned so the shutter can fire; decision "
                    + describe(param.args[0]));
            }
        } catch (Throwable error) {
            Log.e(TAG, "POP capture gate could not align the decision camera id", error);
        }
    }

    /**
     * The third wall, found on the device after the second one fell: with the shutter finally
     * allowed, the capture request goes out with no decision timestamp and the APS refuses it three
     * milliseconds later.
     *
     * <pre>
     *   OCAM_t1_BaseMode: onSendCaptureRequestCore, send, decisionTimestamp: -1, ...
     *   CameraUnit, ProducerImpl: takePicture, not allow take picture, ... checkResult: 1
     *   OCAM_ApsService: onCaptureFailed, requestId: ...
     * </pre>
     *
     * <p>{@code BaseMode.bh} builds the request's decision timestamp as
     * {@code g.t = this.X1; long ts = (g.t != null) ? g.t.M0 : -1}. {@code X1} is the decision the
     * mode accepted for this capture, and it is written in exactly one place:
     * {@code BaseMode.Vi(cb.e)} — {@code startPreEvList}. Stock calls it from
     * {@code onPreviewDecisionResult} once {@code needCapture} is set; the 494 run shows it never ran
     * in POP at all ({@code startPreEvlist} appears once in the whole session, in the photo mode
     * capture that followed), so {@code X1} stayed null.
     *
     * <p>So POP gets the step it skipped: {@code Vi} is called with the decision {@code n8} was just
     * handed, before the request is built. {@code Vi} is not forced — it keeps every one of its own
     * guards (pre-EV-list support, night/burst limits, a duplicate decision) and its answer is
     * logged. Only when it declines does this write the decision into the mode's decision fields
     * itself, which is the single thing {@code Vi} would have done.
     */
    private static void startPreEvList(XC_MethodHook.MethodHookParam param) {
        if (param.args.length < 1 || param.args[0] == null) return;
        if (!inRetroMode(param.thisObject)) return;
        Object decision = param.args[0];
        Method start = startPreEvListMethod();
        if (start == null) {
            if (!reportedPreEvList) {
                reportedPreEvList = true;
                Log.i(TAG, "POP capture: no BaseMode.startPreEvList symbol, the capture request keeps "
                    + "the camera's own decision timestamp");
            }
            return;
        }
        try {
            Object answer = start.invoke(param.thisObject, decision);
            boolean accepted = Boolean.TRUE.equals(answer);
            if (!reportedPreEvList) {
                reportedPreEvList = true;
                Log.i(TAG, "POP capture: startPreEvList called for the retro mode, the camera answered "
                    + answer + (accepted
                        ? "; the request now carries the preview decision's timestamp"
                        : "; storing the decision on the mode so the request still carries it"));
            }
            if (!accepted) {
                storeDecision(param.thisObject, decision);
            }
        } catch (Throwable error) {
            Log.e(TAG, "POP capture: startPreEvList failed, storing the decision instead", error);
            storeDecision(param.thisObject, decision);
        }
    }

    private static volatile Method startPreEvList;

    private static Method startPreEvListMethod() {
        Method cached = startPreEvList;
        if (cached != null) return cached;
        try {
            Symbols symbols = Symbols.get();
            if (!symbols.has("BaseMode.startPreEvList")) return null;
            cached = symbols.method("BaseMode.startPreEvList");
            startPreEvList = cached;
            return cached;
        } catch (Throwable error) {
            Log.e(TAG, "POP capture: startPreEvList symbol unusable", error);
            return null;
        }
    }

    /**
     * Writes the decision into every field of the decision type the mode declares — that is
     * {@code X1} and {@code Y1} (both set together by {@code Vi}) and nothing else on this build.
     * Named in the log so a build with a different field set is obvious in the report.
     */
    private static void storeDecision(Object mode, Object decision) {
        Class<?> type = decisionType;
        if (type == null) return;
        StringBuilder names = new StringBuilder();
        try {
            for (Field field : mode.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() != type) continue;
                field.setAccessible(true);
                field.set(mode, decision);
                if (names.length() > 0) names.append(',');
                names.append(field.getName());
            }
        } catch (Throwable error) {
            Log.e(TAG, "POP capture: the decision could not be stored on the mode", error);
            return;
        }
        if (!reportedSource) {
            reportedSource = true;
            Log.i(TAG, "POP capture: decision stored on " + mode.getClass().getSimpleName()
                + " fields " + (names.length() == 0 ? "(none found)" : names));
        }
    }

    /**
     * Read-only: says whether the capture request really found a decision, by looking at the mode's
     * decision field from inside {@code onSendCaptureRequestCore} — the method that turns it into
     * {@code decisionTimestamp}. One line per process, on the first capture.
     */
    private static void reportDecisionSource() {
        try {
            Symbols symbols = Symbols.get();
            if (!symbols.has("BaseMode.sendCaptureRequestCore")) {
                Log.i(TAG, "POP capture gate: no BaseMode.sendCaptureRequestCore symbol, the decision "
                    + "timestamp cannot be reported");
                return;
            }
            Method build = symbols.method("BaseMode.sendCaptureRequestCore");
            XposedBridge.hookMethod(build, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!inRetroMode(param.thisObject)) return;
                    if (reportedDecision) return;
                    reportedDecision = true;
                    Log.i(TAG, "POP capture: the request is built with the mode's decision " + currentDecision(param.thisObject));
                }
            });
            Log.i(TAG, "POP capture gate: decision timestamp reporter armed (" + BASE_MODE + "."
                + build.getName() + ")");
        } catch (Throwable error) {
            Log.e(TAG, "POP capture gate could not arm the decision reporter", error);
        }
    }

    private static volatile boolean reportedDecision;

    private static String currentDecision(Object mode) {
        Class<?> type = decisionType;
        if (type == null) return "(decision type unknown)";
        try {
            for (Field field : mode.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() != type) continue;
                field.setAccessible(true);
                Object value = field.get(mode);
                return field.getName() + "=" + (value == null ? "null" : describe(value));
            }
        } catch (Throwable error) {
            return "(unreadable: " + error + ")";
        }
        return "(no decision field)";
    }

    /**
     * True for the retro mode object. When {@code RetroMode} does not resolve there is no way to
     * tell one mode instance from another, so the write is allowed but said out loud — the id only
     * ever disagrees on this phone in POP.
     */
    private static boolean inRetroMode(Object mode) {
        Class<?> retro = PopFilterGate.retroClass();
        if (retro == null) {
            if (!reportedScope) {
                reportedScope = true;
                Log.i(TAG, "POP capture gate: RetroMode did not resolve, aligning on any mode");
            }
            return true;
        }
        return retro.isInstance(mode);
    }

    /**
     * {@code BaseMode.n8(cb.e, boolean, boolean)} is the only method on {@code BaseMode} with a
     * (reference, boolean, boolean) to boolean shape in this build, so the signature is the whole
     * fingerprint and no obfuscated name is needed.
     */
    private static Method decisionCheck(Class<?> base) {
        List<Method> found = new ArrayList<>();
        for (Method method : base.getDeclaredMethods()) {
            if (method.isSynthetic() || Modifier.isStatic(method.getModifiers())) continue;
            if (method.getReturnType() != boolean.class) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 3) continue;
            if (params[0].isPrimitive()) continue;
            if (params[1] != boolean.class || params[2] != boolean.class) continue;
            if (cameraIdOf(params[0]) == null) continue;
            found.add(method);
        }
        if (found.size() == 1) {
            return found.get(0);
        }
        if (found.isEmpty()) {
            return null;
        }
        StringBuilder names = new StringBuilder();
        for (Method method : found) {
            names.append(' ').append(method.getName()).append(signature(method.getParameterTypes()));
        }
        Log.i(TAG, "POP capture gate: " + found.size() + " candidates," + names);
        return null;
    }

    private static String signature(Class<?>[] types) {
        StringBuilder text = new StringBuilder("(");
        for (int i = 0; i < types.length; i++) {
            if (i > 0) text.append(',');
            text.append(types[i].getSimpleName());
        }
        return text.append(')').toString();
    }

    /** The decision's camera id. {@code b} is the field {@code n8} compares against. */
    private static Field cameraIdOf(Class<?> decision) {
        try {
            Field field = decision.getDeclaredField("b");
            if (field.getType() != int.class || Modifier.isStatic(field.getModifiers())) return null;
            field.setAccessible(true);
            return field;
        } catch (Throwable missing) {
            return null;
        }
    }

    private static Method getCameraId(Class<?> base) {
        for (Class<?> type = base; type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod("getCameraId");
                if (method.getReturnType() != int.class || method.getParameterTypes().length != 0) {
                    return null;
                }
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException next) {
                // keep walking up
            }
        }
        return null;
    }

    /** One line about the decision object, for the report that follows a build with no fix. */
    private static String describe(Object decision) {
        try {
            String text = String.valueOf(decision);
            if (text == null || text.indexOf('@') == 0) return decision.getClass().getName();
            return text;
        } catch (Throwable error) {
            return decision.getClass().getName();
        }
    }
}
