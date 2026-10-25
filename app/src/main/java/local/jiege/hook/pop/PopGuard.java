package local.jiege.hook.pop;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/**
 * Stops POP's delayed show-UI runnable from taking the process down.
 *
 * <p>The 476 report has three identical crashes, all on the main thread:
 *
 * <pre>
 *   NullPointerException: Attempt to invoke virtual method 'void bj.z.l(int)' on a null object
 *     at com.oplus.camera.ui.newsetting.container.t1.run(R8$$SyntheticClass:135)
 *     at m9.i$a.run(ScheduledRunnable.java:24)
 * </pre>
 *
 * <p>{@code t1} is a one-shot runnable holding {@code (Object target, int what)}. For the retro
 * case the target is the {@code RetroCameraUIContainer}, and {@code run()} reads that container's
 * own {@code RetroCameraUI} field and calls {@code l(int)} on it — the show animation. The field is
 * null, so the container exists but its view was never built, and the delayed run reaches into it
 * anyway. Nothing in the message says why the view is missing, and a report cannot be taken on a
 * device that reboots into a black preview, so this wraps the run: the original still runs first,
 * and a throw is logged and dropped instead of killing the main thread.
 *
 * <p>Two probes come with it, because the interesting question is the one the crash cannot answer:
 *
 * <ul>
 *   <li>{@code retro UI init start} / {@code retro UI init done} — {@code RetroCameraUIContainer}
 *       initialising. Start without done means it threw, which is where the missing view comes from.</li>
 *   <li>the swallowed line carries the same flag, so one report says whether the container was
 *       ever initialised at all.</li>
 * </ul>
 *
 * <p>Armed from {@code Application.onCreate} with the rest of the POP hooks. Escape hatch:
 * {@code touch /data/adb/omnicam_popguard_off}.
 */
public final class PopGuard {
    private static final String TAG = "PopPort";
    private static final String OFF = "/data/adb/omnicam_popguard_off";
    private static final int LOG_LIMIT = 10;

    private static final AtomicInteger SWALLOWED = new AtomicInteger();
    private static final AtomicBoolean INIT_DONE = new AtomicBoolean();

    private static boolean installed;

    private PopGuard() {}

    public static void install(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "pop guard off (" + OFF + ")");
            return;
        }
        if (installed) return;
        installed = true;
        Symbols symbols = Symbols.get();
        watchInit(symbols);
        guardShow(symbols);
    }

    /** {@code RetroCameraUIContainer} building the retro view. Answers "did it get that far". */
    private static void watchInit(Symbols symbols) {
        if (!symbols.has("PopUI.init")) {
            Log.i(TAG, "retro UI init not found, the report will not say whether the view was built");
            return;
        }
        try {
            XposedBridge.hookMethod(symbols.method("PopUI.init"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Log.i(TAG, "retro UI init start");
                }

                @Override protected void afterHookedMethod(MethodHookParam param) {
                    INIT_DONE.set(true);
                    Log.i(TAG, "retro UI init done");
                }
            });
        } catch (Throwable error) {
            Log.e(TAG, "cannot watch the retro UI init", error);
        }
    }

    /** The show-UI runnable: run it, but never let it kill the main thread. */
    private static void guardShow(Symbols symbols) {
        if (!symbols.has("PopUI.show")) {
            Log.i(TAG, "retro show runnable not found, POP keeps the stock behaviour");
            return;
        }
        try {
            XposedBridge.hookMethod(symbols.method("PopUI.show"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    } catch (Throwable error) {
                        note(param.thisObject, error);
                    }
                    // Always: the original has already run (or thrown), it must not run twice.
                    param.setResult(null);
                }
            });
            Log.i(TAG, "retro show runnable guarded (" + symbols.name("PopUI.show") + ")");
        } catch (Throwable error) {
            Log.e(TAG, "cannot guard the retro show runnable", error);
        }
    }

    private static void note(Object runnable, Throwable error) {
        int count = SWALLOWED.incrementAndGet();
        if (count > LOG_LIMIT) {
            if (count == LOG_LIMIT + 1) {
                Log.i(TAG, "retro show runnable keeps throwing, log stops at " + LOG_LIMIT);
            }
            return;
        }
        Log.i(TAG, "retro show runnable dropped ("
            + (runnable == null ? "?" : runnable.getClass().getName()) + ", #" + count
            + ", retro UI init done: " + INIT_DONE.get() + "): " + error);
    }
}
