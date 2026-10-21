package local.jiege.hook.modes;

import android.app.Application;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import local.jiege.hook.common.Log;

/**
 * Support flags the camera reads out of {@code oplus_camera_config}, answered in-process.
 *
 * <p>Two things need them and neither can be reached from the profile:
 *
 * <ul>
 *   <li>The mode bar is seeded into {@code databases/mode_data.db} once, and each row is written
 *       behind a flag — {@code gr} needs {@code com.oplus.gr.mode.support}, {@code retroCamera}
 *       needs {@code com.oplus.feature.retro.camera.support}. Neither key exists in this device's
 *       config, and an absent key answers false forever, so re-seeding the database writes the
 *       same missing rows.
 *   <li>The camera's own 哈苏 watermark is behind
 *       {@code com.oplus.camera.support.custom.hasselblad.watermark}, which this device ships as 0.
 * </ul>
 *
 * <p>Both are read through {@code CameraConfig.getConfigBooleanValue}, which goes to
 * {@code CameraUnitUtils.getVendorTagConfig(key)} — the config file itself. The answer is given
 * here, for this process only; nothing is written to disk and with the module off the flags read
 * exactly as the firmware ships them.
 *
 * <p>Timing is the whole point of this class. The flags are answered from
 * {@code Application.onCreate}, never from {@code handleLoadPackage}: resolving or calling into
 * CameraConfig while the process is still being bound (before the app exists) loads that class and
 * runs its config engine too early, and the camera then comes up to a black screen. For the same
 * reason the class is loaded with {@code initialize = false} and the original method is never
 * called by this class — the first call is the camera's own.
 *
 * <p>470 got the shape wrong: it read {@code param.args[1]} for a "default" that this method does
 * not take — {@code getConfigBooleanValue(String)} has exactly one argument — so every call threw
 * ArrayIndexOutOfBounds inside the hook, Xposed swallowed it, and the answer was never set. The
 * rows were then never written, and the camera froze for the full database query timeout whenever
 * a mode in the bar had no row. Hence: answer first, log second, and never index past
 * {@code args[0]}.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_gates_off} turns this part off without touching
 * anything else.
 */
public final class ModeGates {
    private static final String TAG = "ModeGates";
    private static final String CONFIG = "com.oplus.camera.configure.CameraConfig";
    private static final String GETTER = "getConfigBooleanValue";
    private static final String OFF = "/data/adb/omnicam_gates_off";

    /** Flags answered true. Keys that are absent from the config can only be answered here. */
    private static final String[] FORCED = {
        "com.oplus.gr.mode.support",
        "com.oplus.feature.retro.camera.support",
        "com.oplus.feature.xpan.mode.support",
        // 大师模式 (ProfessionalMode) has no row of its own without this: the key is not in this
        // device's config at all, so the seeding code skips the mode and only a move config puts
        // it in the table. See ModePlacement for where it ends up.
        "com.oplus.feature.professional.mode.support",
        "com.oplus.camera.support.custom.hasselblad.watermark",
        "com.oplus.camera.support.custom.hasselblad.watermark.sellmode.default.open",
    };

    /** Keys already logged, so one report carries each answer once. */
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    /**
     * Every distinct key the camera asks about, logged once, capped. POP is still missing after the
     * flags started answering, and the two possible reasons look identical from outside: the camera
     * never asks about {@code retroCamera}, or it asks and drops the row somewhere else. This list
     * tells them apart from a single report.
     */
    private static final Set<String> ASKED = ConcurrentHashMap.newKeySet();
    private static final int ASKED_LIMIT = 100;

    private static boolean installed;

    private ModeGates() {}

    /**
     * Arms the flags for the camera's main process: nothing is resolved here, only the two framework
     * hooks that fire once the app is up. Both are armed because an app that overrides
     * {@code onCreate} without calling super would never reach the first one.
     */
    public static void installCamera(final ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "gates off (" + OFF + "), flags left as shipped");
            return;
        }
        afterApplication(classLoader, new Runnable() {
            @Override public void run() {
                install(classLoader);
            }
        });
    }

    /**
     * Runs {@code task} once the camera's Application exists.
     *
     * <p>This is the fix 470 shipped and 471 kept: resolving a camera class from
     * {@code handleLoadPackage} — {@code handleBindApplication} is still running and the
     * Application has not been created — starts the camera's config engine early and the camera
     * then comes up to a black screen. Everything that touches those classes goes through here.
     */
    static void afterApplication(final ClassLoader classLoader, final Runnable task) {
        XC_MethodHook arm = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                task.run();
            }
        };
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", arm);
        } catch (Throwable error) {
            Log.e(TAG, "application onCreate hook unavailable", error);
        }
        try {
            XposedHelpers.findAndHookMethod(android.app.Instrumentation.class, "callApplicationOnCreate",
                Application.class, arm);
        } catch (Throwable error) {
            Log.e(TAG, "callApplicationOnCreate hook unavailable", error);
        }
    }

    /** Runs once, from Application.onCreate: the camera's own config engine is up by then. */
    private static synchronized void install(ClassLoader classLoader) {
        if (installed) return;
        installed = true;
        Method getter;
        try {
            Class<?> config = Class.forName(CONFIG, false, classLoader);
            getter = find(config);
        } catch (Throwable error) {
            Log.e(TAG, "flag getter unavailable", error);
            return;
        }
        try {
            XposedBridge.hookMethod(getter, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Object key = param.args[0];
                    if (!(key instanceof String)) return;
                    String name = (String) key;
                    if (forced(name)) {
                        // Answer first, log after: this runs on the camera's own thread while it
                        // seeds its mode database, so nothing below may take the answer back.
                        param.setResult(Boolean.TRUE);
                        if (SEEN.add(name)) log(name + " answered true");
                        return;
                    }
                    if (ASKED.size() < ASKED_LIMIT && ASKED.add(name)) log("asked: " + name);
                }
            });
            Log.i(TAG, "flag getter hooked, " + FORCED.length + " flags answered true");
        } catch (Throwable error) {
            Log.e(TAG, "flag hook failed", error);
        }
    }

    /** getConfigBooleanValue(String)Z, static or not — this build's shape is not assumed. */
    private static Method find(Class<?> config) throws NoSuchMethodException {
        for (Method method : config.getDeclaredMethods()) {
            if (!GETTER.equals(method.getName())) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 1 && types[0] == String.class && method.getReturnType() == boolean.class) {
                return method;
            }
        }
        throw new NoSuchMethodException(config.getName() + "." + GETTER);
    }

    /** Logging must never be able to undo an answer that was already set. */
    private static void log(String message) {
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
        }
    }

    private static boolean forced(String key) {
        for (String flag : FORCED) if (flag.equals(key)) return true;
        return false;
    }
}
