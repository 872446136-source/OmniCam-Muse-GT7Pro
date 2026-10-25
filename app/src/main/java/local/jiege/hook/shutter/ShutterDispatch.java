package local.jiege.hook.shutter;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.modes.ModeGates;

/**
 * Says whether a shutter press leaves the UI thread at all.
 *
 * <p>473: in GR the press is delivered — {@code onShutterButtonClick, ShutterButton.id: …} appears
 * four times — and every guard inside that listener logs before it returns, so none of them fired.
 * Nothing else followed: no capture, no thumbnail, no error. Two very different failures look
 * exactly like that from outside, and the report cannot tell them apart:
 *
 * <ul>
 *   <li>the press never becomes an event — the delayed single-click runnable is cancelled, or the
 *       event bus has no listener for the current mode;</li>
 *   <li>the press becomes an event and the capture is refused further down.</li>
 * </ul>
 *
 * <p>The listener ends in {@code aq.u0.run()}, which builds a
 * {@code CameraShutterButtonEventMessage} and hands it to {@code do.a.d(message, boolean)} — the
 * event bus. That call is the boundary. One line here, plus the capture tags now mirrored in
 * {@link local.jiege.hook.common.CameraLogMirror}, splits the two cases in a single report:
 *
 * <pre>
 *   ShutterDispatch: shutter event dispatched
 *   … followed by CaptureProcessor lines  → the capture was refused
 *   … and nothing after it               → the event bus has no listener for this mode
 * </pre>
 *
 * <p>{@code do.a.d} carries every event in the process, so only messages whose class name mentions
 * the shutter button are logged. One line per press, not per event.
 *
 * <p>Armed from {@code Application.onCreate} like every other camera-class hook here: resolving
 * these classes while the process is still being bound brings the config engine up too early and
 * the camera starts black.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_shutterprobe_off}.
 */
public final class ShutterDispatch {
    private static final String TAG = "ShutterDispatch";
    private static final String OFF = "/data/adb/omnicam_shutterprobe_off";
    /**
     * The event bus and its base message type. Both moved in 7.013.40 — do.a is now an animation
     * helper with a completely different set of methods, and do.c is the filter renderer — so the
     * old names would have hooked something unrelated. The new pair is listed first, the previous
     * build second.
     */
    private static final String[] BUS = {"pn.a", "do.a"};
    private static final String[] MESSAGE = {"pn.c", "do.c"};
    private static final String DISPATCH = "d";
    private static final String MARKER = "ShutterButton";

    /**
     * 479's report: POP opens, the mode switch works, preview runs — and a press still does
     * nothing, in POP and in GR alike. Two facts from that report narrow it to one place:
     *
     * <pre>
     *   POP  16:18:03.995  CommonCapMode onCameraShutterButtonClick   ← the click handler ran
     *   GR   16:17:57.680  shutter handled by hm.i                    ← only the button-down event
     * </pre>
     *
     * <p>Both modes therefore get as far as the mode's own shutter code, and neither produces a
     * single {@code CaptureProcessor} line afterwards. The one call that turns a press into a
     * request is {@code BaseMode.capture(cb.t)} — the method that logs
     * {@code capture, isSpruceStartCapture but isCapturing} and
     * {@code capture, checkStateBeforeCapture true} and ends in the capture processor. Nothing in
     * any report says whether it is entered or what it answers, so it is traced here: one line on
     * entry, one with the answer.
     *
     * <p>That answer is what separates the two remaining possibilities. Entered and false means the
     * mode refused the request itself; not entered at all means the click handler returned before
     * it, which is what 480's feature table fixes.
     */
    private static boolean installed;

    /** Cached shape-found button-down flag of the shutter message. */
    private static volatile Field downFlag;

    private ShutterDispatch() {}

    public static void installCamera(final ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "off (" + OFF + ")");
            return;
        }
        ModeGates.afterApplication(classLoader, new Runnable() {
            @Override public void run() {
                install(classLoader);
            }
        });
    }

    private static synchronized void install(ClassLoader classLoader) {
        if (installed) return;
        installed = true;
        for (int i = 0; i < BUS.length; i++) {
            Class<?> bus;
            try {
                bus = Class.forName(BUS[i], false, classLoader);
            } catch (Throwable ignored) {
                continue;
            }
            Method dispatch = null;
            for (Method method : bus.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                // d(base message, sticky flag): the type every event extends, and a boolean.
                if (DISPATCH.equals(method.getName()) && types.length == 2
                    && MESSAGE[i].equals(types[0].getName()) && types[1] == boolean.class) {
                    dispatch = method;
                    break;
                }
            }
            if (dispatch == null) continue;
            try {
                XposedBridge.hookMethod(dispatch, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Object message = param.args[0];
                        if (message == null) return;
                        String type = message.getClass().getName();
                        if (type.indexOf(MARKER) < 0) return;
                        Log.i(TAG, "shutter event dispatched");
                    }
                });
                Log.i(TAG, "shutter dispatch hooked on " + BUS[i]);
                trace(classLoader);
                return;
            } catch (Throwable error) {
                Log.e(TAG, "dispatch hook failed on " + BUS[i], error);
                return;
            }
        }
        Log.i(TAG, "event bus not found, a shutter press is not traced");
    }

    /**
     * The two boundaries either side of the bus, so one press in a report says which half broke.
     *
     * <p>{@code shutter message built} is the message constructor — the click listener ends by
     * building one of these, so a press with no such line never became an event. {@code shutter
     * handled by …} is every method that takes the message: the photo path, the GR path and the
     * third one. All three are resolved by shape, not by name, so this keeps working when the
     * package is rebuilt.
     */
    private static void trace(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        if (symbols.has("ShutterMessage")) {
            try {
                for (Constructor<?> constructor : symbols.cls("ShutterMessage").getDeclaredConstructors()) {
                    XposedBridge.hookMethod(constructor, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            Log.i(TAG, "shutter message built");
                        }
                    });
                }
            } catch (Throwable error) {
                Log.e(TAG, "cannot watch the shutter message", error);
            }
        }
        traceCapture(symbols);
        if (!symbols.has("ShutterHandler.all")) {
            Log.i(TAG, "shutter handlers not found, only the bus is traced");
            return;
        }
        Method gr = symbols.has("ShutterHandler.gr") ? symbols.method("ShutterHandler.gr") : null;
        try {
            for (Method handler : symbols.<Method>all("ShutterHandler.all")) {
                final String owner = handler.getDeclaringClass().getName();
                final boolean isGr = gr != null && gr.equals(handler);
                XposedBridge.hookMethod(handler, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Object message = param.args.length > 0 ? param.args[0] : null;
                        Log.i(TAG, "shutter handled by " + owner + ", down: " + down(message));
                    }
                });
                Log.i(TAG, "shutter handler traced: " + owner + "." + handler.getName()
                    + (isGr ? " (GR)" : ""));
            }
        } catch (Throwable error) {
            Log.e(TAG, "cannot watch the shutter handlers", error);
        }
    }

    /**
     * Watches the call that turns a press into a request: {@code BaseMode.capture(cb.t)}, the one
     * that ends in the capture processor. Entry and answer, one line each, because the pair is what
     * separates "the mode refused it" from "the handler never got this far".
     */
    private static void traceCapture(Symbols symbols) {
        if (!symbols.has("BaseMode.capture")) {
            Log.i(TAG, "capture request symbol missing, a press cannot be followed past the mode");
            return;
        }
        try {
            Method capture = symbols.method("BaseMode.capture");
            XposedBridge.hookMethod(capture, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Log.i(TAG, "capture request entered on " + param.thisObject.getClass().getName());
                }

                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Log.i(TAG, "capture request returned " + param.getResult());
                }
            });
            Log.i(TAG, "capture request traced: " + capture);
        } catch (Throwable error) {
            Log.e(TAG, "cannot trace the capture request", error);
        }
    }

    /**
     * The message's button-down flag. It is the only non-final boolean on the class — the other two
     * are set once in the constructor — so the field is found by shape, not by name.
     */
    private static boolean down(Object message) {
        if (message == null) return false;
        Field flag = downFlag;
        if (flag == null) {
            Field found = null;
            for (Field field : message.getClass().getDeclaredFields()) {
                if (field.getType() != boolean.class) continue;
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (Modifier.isFinal(field.getModifiers())) continue;
                if (found != null) return false;
                found = field;
            }
            if (found == null) return false;
            found.setAccessible(true);
            downFlag = flag = found;
        }
        try {
            return flag.getBoolean(message);
        } catch (Throwable error) {
            return false;
        }
    }
}
