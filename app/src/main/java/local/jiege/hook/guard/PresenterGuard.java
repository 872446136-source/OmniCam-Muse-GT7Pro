package local.jiege.hook.guard;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Method;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.modes.ModeGates;

/**
 * Keeps one broken feature presenter from taking the whole camera process with it.
 *
 * <p>480 shipped the GR and POP feature tables and the camera died on every switch into either mode.
 * The report is precise about where:
 *
 * <pre>
 *   java.lang.UnsatisfiedLinkError: dlopen failed:
 *       library "libXDocProcessSDK-jni.qti.so" not found
 *     at com.youtu.ocr.docprocess.DocDetector.&lt;clinit&gt;
 *     at com.oplus.ocs.camera.SuperTextHelper.&lt;init&gt;
 *     at com.oplus.camera.feature.supertext.e.&lt;init&gt;        SuperTextEngine
 *     at com.oplus.camera.feature.supertext.SuperTextManager.&lt;init&gt;
 *     at ak.h.k2(SuperTextPresenter.java:92)
 *     at lc.p.R(BaseFeaturePresenter.java:29)
 *     at com.oplus.camera.g7.accept
 *     at hb.t0.e(FeatureManager.java:48)
 *     at fm.k4.q0(ModeManager.java:499)
 * </pre>
 *
 * <p>One feature's presenter failed to construct and the exception went straight up through
 * {@code FeatureManager}'s loop and out of the main looper. Nothing in that path catches it, so any
 * single unusable feature — a missing library, a null the device does not provide — is fatal.
 *
 * <p>{@code lc.p.R(int, Bundle, boolean)} is the per-presenter dispatcher every mode-switch stage
 * goes through: it is a chain of {@code if (stage == 30000) C1(); else if (stage == 30001) N1(b); …}
 * ending in {@code k2(boolean)}, the per-mode init the stack above dies in. Replacing it with a
 * guarded call means a presenter that throws loses its own init and nothing else: the switch
 * finishes, the camera stays up, and the log says which class and which stage.
 *
 * <p>Only the throw is swallowed — when the original returns normally the behaviour is identical,
 * because it is the original that ran.
 *
 * <p>Armed from {@code Application.onCreate} like every other camera-class hook here.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_presenterguard_off}.
 */
public final class PresenterGuard {
    private static final String TAG = "PresenterGuard";
    private static final String OFF = "/data/adb/omnicam_presenterguard_off";

    private static boolean installed;

    private PresenterGuard() {}

    public static void installCamera(final ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "off (" + OFF + ")");
            return;
        }
        ModeGates.afterApplication(classLoader, new Runnable() {
            @Override public void run() {
                install();
            }
        });
    }

    private static synchronized void install() {
        if (installed) return;
        installed = true;
        Symbols symbols = Symbols.get();
        if (!symbols.has("FeaturePresenter.stage")) {
            Log.i(TAG, "stage dispatcher not resolved, a presenter that throws can still take the process down");
            return;
        }
        try {
            final Method stage = symbols.method("FeaturePresenter.stage");
            XposedBridge.hookMethod(stage, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        XposedBridge.invokeOriginalMethod(stage, param.thisObject, param.args);
                    } catch (Throwable error) {
                        Log.i(TAG, "presenter " + param.thisObject.getClass().getName()
                            + " threw on stage " + param.args[0]
                            + ", swallowed so the mode switch can finish", error);
                    }
                    param.setResult(null);
                }
            });
            Log.i(TAG, "presenter stage guarded: " + stage);
        } catch (Throwable error) {
            Log.e(TAG, "cannot guard the presenter stage", error);
        }
    }
}
