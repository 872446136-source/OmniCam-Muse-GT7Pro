package local.jiege.hook.pop;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;

/**
 * POP (retro camera): the shutter stays dead after a filter is picked.
 *
 * <p>487 report: POP shows the frame now and the filters all render, but pressing the shutter does
 * nothing. The camera says why itself, in its own log:
 *
 * <pre>
 *   OCAM_RetroCameraMode onCameraShutterButtonClick, blocked during retro filter switching
 *   OCAM_RetroCameraMode onCameraShutterButtonDispatchTouch, blocked during retro filter switching
 *   OCAM_RetroCameraMode isReadyToCapture, blocked during retro filter switching
 * </pre>
 *
 * <p>All three overrides in {@code RetroCameraMode} (this build: {@code um.o1}) return early behind
 * one boolean — {@code s3}. 488 read the class and found out who writes it:
 *
 * <pre>
 *   r6(int)  onFilterScrolling   -> Ek(name, true)     the strip started moving
 *   Ek(String, boolean)          the only writer of s3
 *   T0()     onFilterSnapSettled -> t3 = true          the strip settled  (NO CALLERS)
 *   Jg(...)  onPreviewDecisionResult -> Ek(name, ...)  the preview answered (NO CALLERS)
 * </pre>
 *
 * <p>{@code T0} and {@code Jg} have no callers at all in this APK, so nothing can ever clear
 * {@code s3} again: one scroll of the ported filter strip disables the shutter for the rest of the
 * session. 488's first attempt armed itself from {@code r6} only and did nothing on the phone —
 * the 488 report shows the same three blocked lines with no watchdog line at all, because {@code s3}
 * was set by the delayed runnable {@code um.j1}, not by a scroll this session, so the arming
 * timestamp was never written.
 *
 * <p>This version arms itself on observation instead: whichever of {@code Ek} / {@code r6} runs
 * first starts the clock, and if the flag is still set {@link #STALE_MS} later it is cleared right
 * before the camera's own guard reads it. The two fields are taken by name ({@code s3} / {@code t3},
 * both stable in this APK) and fall back to the shape — the three shutter overrides plus the two
 * callbacks — when a future build renames them.
 *
 * <p>Nothing is forced while the strip is actually moving: the clock restarts on every scroll, so a
 * genuine 200 ms switch window is untouched.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_popgate_off}.
 */
final class PopFilterGate {
    private static final String TAG = "PopPort";
    private static final String BASE_MODE = "com.oplus.camera.module.BaseMode";
    private static final String OFF = "/data/adb/omnicam_popgate_off";

    /** No scroll and no settle for this long means the settle callback is never coming. */
    private static final long STALE_MS = 1200;

    /** When the switching flag was first seen set (or last refreshed by a scroll). */
    private static volatile long armed;

    /** The class and the callbacks, once a mode object with the retro shape was seen. */
    private static volatile Class<?> retro;
    private static volatile Field switching;
    private static volatile Field settled;
    private static volatile Field[] flags = new Field[0];

    private static volatile boolean reportedInstall;
    private static volatile boolean reportedClear;

    private PopFilterGate() {}

    static void install(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "POP filter gate off (" + OFF + "), the shutter is left as the camera has it");
            return;
        }
        try {
            Class<?> base = XposedHelpers.findClass(BASE_MODE, classLoader);
            XposedBridge.hookAllConstructors(base, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject == null) return;
                    try {
                        identify(param.thisObject.getClass());
                    } catch (Throwable error) {
                        Log.e(TAG, "POP filter gate could not read " + param.thisObject.getClass(), error);
                    }
                }
            });
            Log.i(TAG, "POP filter gate armed on " + BASE_MODE);
        } catch (Throwable error) {
            Log.e(TAG, "POP filter gate unavailable", error);
        }
    }

    /** Accepts the one mode class that has the retro shape, and arms it once. */
    private static synchronized void identify(Class<?> type) {
        if (retro != null || type == null) return;
        Method click = method(type, "Gf", int.class);
        Method touch = method(type, "Hf", int.class);
        Method ready = method(type, "Td", boolean.class, boolean.class, boolean.class, boolean.class);
        Method scrolling = method(type, "r6", int.class);
        Method snapSettled = method(type, "T0");
        if (click == null || touch == null || ready == null || scrolling == null || snapSettled == null) return;
        if (click.getReturnType() != boolean.class || touch.getReturnType() != void.class) return;
        if (ready.getReturnType() != boolean.class || scrolling.getReturnType() != void.class) return;
        if (snapSettled.getReturnType() != void.class) return;
        Field[] booleans = booleans(type);
        if (booleans.length == 0 || booleans.length > 6) return;

        retro = type;
        flags = booleans;
        switching = named(type, "s3");
        settled = named(type, "t3");

        XposedBridge.hookMethod(scrolling, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                armed = android.os.SystemClock.elapsedRealtime();
            }
        });
        // The writer of the flag. Watching it is what makes the clock start even when the strip was
        // moved by a delayed runnable rather than by a scroll this session.
        Method writer = method(type, "Ek", String.class, boolean.class);
        if (writer != null) {
            XposedBridge.hookMethod(writer, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    boolean on = param.args.length > 1 && Boolean.TRUE.equals(param.args[1]);
                    armed = on ? android.os.SystemClock.elapsedRealtime() : 0;
                }
            });
        }
        XposedBridge.hookMethod(snapSettled, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                armed = 0;
                Log.i(TAG, "filter strip settled, flags " + describe(read(param.thisObject)));
            }
        });
        XC_MethodHook unstick = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                unstick(param.thisObject);
            }
        };
        XposedBridge.hookMethod(click, unstick);
        XposedBridge.hookMethod(touch, unstick);
        XposedBridge.hookMethod(ready, unstick);
        if (!reportedInstall) {
            reportedInstall = true;
            Log.i(TAG, "POP filter gate installed on " + type.getName() + ", switching flag "
                + name(switching) + ", settled flag " + name(settled) + ", " + flags.length + " boolean(s)");
        }
    }

    /**
     * Puts the mode back into the state its own settle callback would have written, when that
     * callback is overdue. Called from all three shutter overrides, before the camera reads the flag.
     */
    private static void unstick(Object mode) {
        long now = android.os.SystemClock.elapsedRealtime();
        try {
            boolean stuck;
            if (switching != null) {
                stuck = switching.getBoolean(mode);
            } else {
                stuck = false;
                for (Field flag : flags) if (flag.getBoolean(mode)) stuck = true;
            }
            if (!stuck) {
                armed = 0;
                return;
            }
            long since = armed;
            if (since == 0) {
                // First sight of the flag. The camera gets its full window before anything is
                // touched: a real switch finishes in a couple of hundred milliseconds.
                armed = now;
                return;
            }
            long idle = now - since;
            if (idle <= STALE_MS) return;

            if (switching != null) {
                switching.setBoolean(mode, false);
            } else {
                for (Field flag : flags) if (flag.getBoolean(mode)) flag.setBoolean(mode, false);
            }
            if (settled != null) settled.setBoolean(mode, true);
            armed = 0;
            if (!reportedClear) {
                reportedClear = true;
                Log.i(TAG, "stale filter switch cleared after " + idle + " ms idle, flags "
                    + describe(read(mode)));
            }
        } catch (Throwable error) {
            Log.e(TAG, "POP filter gate could not clear the switch flag", error);
        }
    }

    private static boolean[] read(Object mode) {
        try {
            boolean[] values = new boolean[flags.length];
            for (int i = 0; i < flags.length; i++) values[i] = flags[i].getBoolean(mode);
            return values;
        } catch (Throwable error) {
            return null;
        }
    }

    private static String describe(boolean[] values) {
        if (values == null) return "(unreadable)";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) text.append(", ");
            text.append(flags[i].getName()).append('=').append(values[i]);
        }
        return text.toString();
    }

    private static String name(Field field) {
        return field == null ? "(not by name)" : field.getName();
    }

    /** A boolean instance field by name; null when this build renamed it. */
    private static Field named(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            if (field.getType() != boolean.class || Modifier.isStatic(field.getModifiers())) return null;
            field.setAccessible(true);
            return field;
        } catch (Throwable missing) {
            return null;
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... params) {
        try {
            Method method = type.getDeclaredMethod(name, params);
            return Modifier.isStatic(method.getModifiers()) ? null : method;
        } catch (NoSuchMethodException missing) {
            return null;
        }
    }

    private static Field[] booleans(Class<?> type) {
        List<Field> found = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (field.getType() != boolean.class || Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            found.add(field);
        }
        return found.toArray(new Field[0]);
    }
}
