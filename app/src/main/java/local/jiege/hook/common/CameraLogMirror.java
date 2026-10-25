package local.jiege.hook.common;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The camera's own log never reaches logcat on this system (its lines are dropped below
 * android.util.Log, or go to the private channel CameraUnitUtils.printPrivateLog), so the exported
 * log has none of it. Lines of the camera classes the module works with are copied into the
 * module log, which the export carries.
 *
 * <p>The export's own logcat grab is {@code logcat -d -t 20000}: about thirty seconds of this
 * device's buffers. A camera that freezes stops logging, so by the time the log is exported the
 * lines that explain the freeze have been pushed out — 471 and 472 both came back with zero
 * {@code OCAM} lines, which is why the mode switch could not be diagnosed from them. The switch
 * tags are mirrored here so those lines survive inside the module log.
 *
 * <p>474 adds the capture path. 473 proved the shutter click is delivered in GR —
 * {@code onShutterButtonClick, ShutterButton.id: …} appears four times, and every guard inside that
 * listener logs before it returns, so none of them fired — and yet no capture followed. The lines
 * that would say where it stopped are written under these tags, which were not mirrored, so the
 * report could not tell a rejected request from a request that never left. Mirroring them is the
 * whole point: the next report answers it instead of guessing.
 *
 * <p>Volume is capped. These classes log per frame in some paths, and a mirror that writes a hundred
 * lines a second would slow the camera down and hide the very freeze it is meant to explain. The
 * first {@link #LIMIT} lines are kept and the cap is then reported once.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_logall_off} — only the capture tags stop, the mode
 * tags keep working.
 */
public final class CameraLogMirror {
    private static final String TAG = "CameraLog";
    private static final String CAPTURE_OFF = "/data/adb/omnicam_logall_off";
    private static final int LIMIT = 4000;

    /** Tags mirrored from the first line. The mode switch and the database behind it. */
    private static final String[] ALWAYS = {
        "PresetDataRepository", "EffectDataManager", "EffectPreset", "PresetDataUtil",
        // "Camera Mode Change Start, next mode: …", "checkToSwitchMode, the device is not support
        // the mode:…", and the database rows behind both.
        "ModeSwitcher", "DataBaseOperator", "CameraControlUI", "HeadlineHelper",
        // The pages 475 turns on, so a report says whether they were reached and where they stopped:
        // the log video session and its LUT monitor, the film mode, and the 50 MP page.
        "LogVideoPresenter", "LogLutMenuView", "LogVideoMenu", "MasterVideo", "HighPixel",
        "MovieMode", "Xpan",
        // 478: "updateConfigure, supported featureList: …" is the list the camera builds presenters
        // from, per mode. POP's presenter — the only thing that builds the retro view — is created
        // from that list, so this line says outright whether retro_camera was in it.
        "FeatureManager",
    };

    /** Tags mirrored up to {@link #LIMIT}: what happens to a capture request after the click. */
    private static final String[] CAPTURE = {
        "CaptureProcessor", "CameraCore", "OneCameraImpl", "CaptureParam", "OutCaptureManager",
        "CaptureFail", "CommonCapMode", "CaptureDefer", "ModeManager", "MainShutterButton",
        "RetroCameraUIContainer", "CameraUIManager", "CaptureRequest", "BaseMode",
        // 476: the two modes whose capture is still unaccounted for. GRCapMode is written by the
        // class that owns the GR shutter handler, so its absence from a report after a press means
        // the mode class was never the active one — a different failure from "the handler ran and
        // the capture was refused further down", and the one thing 473 could not tell apart.
        "GRCapMode", "GRViewManager", "GREffectMenu", "RetroCameraMode", "RetroCamFilterUIContainer",
        "HighPixelMode", "MoviePresenter",
    };

    private static final AtomicInteger LINES = new AtomicInteger();
    private static boolean capped;
    /** Read once: the check runs for every log line the process writes. */
    private static boolean captureOff;

    private CameraLogMirror() {}

    public static void install(ClassLoader classLoader) {
        captureOff = new File(CAPTURE_OFF).exists();
        Log.i(TAG, "capture mirror ready, " + ALWAYS.length + " mode tags + "
            + (captureOff ? "0 capture tags (off)" : CAPTURE.length + " capture tags, cap " + LIMIT));
        // Log.println_native(bufID, priority, tag, msg): every android.util.Log call ends here.
        XposedBridge.hookMethod(XposedHelpers.findMethodExact(android.util.Log.class, "println_native",
            int.class, int.class, String.class, String.class), mirror(2, 3));
        Class<?> utils = XposedHelpers.findClassIfExists("com.oplus.ocs.camera.appinterface.adapter.CameraUnitUtils", classLoader);
        if (utils == null) return;
        for (Method method : utils.getDeclaredMethods()) {
            if ("printPrivateLog".equals(method.getName())) XposedBridge.hookMethod(method, mirror(0, 1));
        }
    }

    private static XC_MethodHook mirror(final int tagArg, final int messageArg) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Object tag = param.args[tagArg];
                if (!(tag instanceof String)) return;
                int kind = kind((String) tag);
                if (kind == 0) return;
                if (kind == 2 && !take()) return;
                Log.i(TAG, tag + " " + param.args[messageArg]);
            }
        };
    }

    /** 0 = not mirrored, 1 = always, 2 = capture path (capped). */
    private static int kind(String tag) {
        String base = tag.startsWith("OCAM_") ? tag.substring(5) : tag;
        for (String prefix : ALWAYS) if (base.startsWith(prefix)) return 1;
        if (captureOff) return 0;
        for (String prefix : CAPTURE) if (base.startsWith(prefix)) return 2;
        return 0;
    }

    /** True while the capture budget lasts; reports the cap once, on the line that hits it. */
    private static synchronized boolean take() {
        if (capped) return false;
        if (LINES.incrementAndGet() <= LIMIT) return true;
        capped = true;
        Log.i(TAG, "capture mirror capped at " + LIMIT + " lines, the rest is dropped");
        return false;
    }
}
