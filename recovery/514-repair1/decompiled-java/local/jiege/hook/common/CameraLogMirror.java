package local.jiege.hook.common;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class CameraLogMirror {
    private static final String CAPTURE_OFF = "/data/adb/omnicam_logall_off";
    private static final int LIMIT = 4000;
    private static final String TAG = "CameraLog";
    private static boolean capped;
    private static boolean captureOff;
    private static final String[] ALWAYS = {"PresetDataRepository", "EffectDataManager", "EffectPreset", "PresetDataUtil", "ModeSwitcher", "DataBaseOperator", "CameraControlUI", "HeadlineHelper", "LogVideoPresenter", "LogLutMenuView", "LogVideoMenu", "MasterVideo", "HighPixel", "MovieMode", "Xpan", "XPan", "IPU_WM", "WatermarkYuvInfo", "FeatureManager"};
    private static final String[] CAPTURE = {"CaptureProcessor", "CameraCore", "OneCameraImpl", "CaptureParam", "OutCaptureManager", "CaptureFail", "CommonCapMode", "CaptureDefer", "ModeManager", "MainShutterButton", "RetroCameraUIContainer", "CameraUIManager", "CaptureRequest", "BaseMode", "GRCapMode", "GRViewManager", "GREffectMenu", "RetroCameraMode", "RetroCamFilterUIContainer", "HighPixelMode", "MoviePresenter"};
    private static final AtomicInteger LINES = new AtomicInteger();

    private CameraLogMirror() {
    }

    public static void install(ClassLoader classLoader) {
        captureOff = new File(CAPTURE_OFF).exists();
        StringBuilder sb = new StringBuilder("capture mirror ready, ");
        sb.append(ALWAYS.length);
        sb.append(" mode tags + ");
        sb.append(captureOff ? "0 capture tags (off)" : CAPTURE.length + " capture tags, cap 4000");
        Log.i(TAG, sb.toString());
        Class cls = Integer.TYPE;
        XposedBridge.hookMethod(XposedHelpers.findMethodExact(android.util.Log.class, "println_native", new Class[]{cls, cls, String.class, String.class}), mirror(2, 3));
        Class clsFindClassIfExists = XposedHelpers.findClassIfExists("com.oplus.ocs.camera.appinterface.adapter.CameraUnitUtils", classLoader);
        if (clsFindClassIfExists == null) {
            return;
        }
        for (Method method : clsFindClassIfExists.getDeclaredMethods()) {
            if ("printPrivateLog".equals(method.getName())) {
                XposedBridge.hookMethod(method, mirror(0, 1));
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int kind(String str) {
        if (str.startsWith("OCAM_")) {
            str = str.substring(5);
        }
        for (String str2 : ALWAYS) {
            if (str.startsWith(str2)) {
                return 1;
            }
        }
        if (captureOff) {
            return 0;
        }
        for (String str3 : CAPTURE) {
            if (str.startsWith(str3)) {
                return 2;
            }
        }
        return 0;
    }

    private static XC_MethodHook mirror(final int i, final int i2) {
        return new XC_MethodHook() { // from class: local.jiege.hook.common.CameraLogMirror.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                int iKind;
                Object obj = methodHookParam.args[i];
                if ((obj instanceof String) && (iKind = CameraLogMirror.kind((String) obj)) != 0) {
                    if (iKind != 2 || CameraLogMirror.take()) {
                        Log.i(CameraLogMirror.TAG, obj + " " + methodHookParam.args[i2]);
                    }
                }
            }
        };
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized boolean take() {
        if (capped) {
            return false;
        }
        if (LINES.incrementAndGet() <= LIMIT) {
            return true;
        }
        capped = true;
        Log.i(TAG, "capture mirror capped at 4000 lines, the rest is dropped");
        return false;
    }
}
