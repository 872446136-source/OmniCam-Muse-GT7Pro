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
    private static final String BUS = "do.a";
    private static final String DISPATCH = "d";
    private static final String MESSAGE = "do.c";
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
        Class<?> bus;
        try {
            bus = Class.forName(BUS, false, classLoader);
        } catch (Throwable error) {
            Log.e(TAG, "event bus unavailable", error);
            return;
        }
        Method dispatch = null;
        for (Method method : bus.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            // d(do.c, boolean): the base message type every event extends, and a sticky flag.
            if (DISPATCH.equals(method.getName()) && types.length == 2
                && MESSAGE.equals(types[0].getName()) && types[1] == boolean.class) {
                dispatch = method;
                break;
            }
        }
        if (dispatch == null) {
            Log.i(TAG, "dispatch method not found on " + BUS);
            return;
        }
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
            Log.i(TAG, "shutter dispatch hooked");
        } catch (Throwable error) {
            Log.e(TAG, "dispatch hook failed", error);
        }
    }
}
