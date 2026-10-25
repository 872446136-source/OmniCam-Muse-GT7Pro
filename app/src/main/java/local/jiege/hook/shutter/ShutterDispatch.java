package local.jiege.hook.shutter;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Method;
import local.jiege.hook.common.Log;
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

    private static boolean installed;

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
                return;
            } catch (Throwable error) {
                Log.e(TAG, "dispatch hook failed on " + BUS[i], error);
                return;
            }
        }
        Log.i(TAG, "event bus not found, a shutter press is not traced");
    }
}
