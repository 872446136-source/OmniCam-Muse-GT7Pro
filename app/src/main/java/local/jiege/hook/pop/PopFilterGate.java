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
import local.jiege.hook.common.Symbols;

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
 * <p>All three overrides in the retro mode (this build: {@code um.o1}) return early behind one
 * boolean — {@code s3}:
 *
 * <pre>
 *   r6(int)  onFilterScrolling   -> t3 = false, Ek(name, true)   the strip started moving
 *   Ek(String, boolean)          the only writer of s3
 *   T0()     onFilterSnapSettled -> t3 = true                    the strip settled
 *   Jg(...)  onPreviewDecisionResult -> reads s3, t3, w3         the preview answered
 * </pre>
 *
 * <p><b>488 and 489 both hooked the constructors of {@code com.oplus.camera.module.BaseMode} and
 * both never installed.</b> The 488 log has {@code POP filter gate armed on
 * com.oplus.camera.module.BaseMode} and not one line after it, while every shape check in this
 * class passes for {@code um.o1} (it has exactly two boolean fields, {@code s3} and {@code t3}, and
 * all five methods). The reason is the trigger, not the test: the camera builds its mode objects
 * during startup, before the module's hooks are installed, so a constructor hook never sees the
 * instance that matters.
 *
 * <p>This version takes the class from {@code assets/symbols.json} ({@code RetroMode} and its six
 * members, fingerprinted by the strings they log) and hooks the methods <em>on the class</em>, which
 * covers instances created before the hook. The constructor path is kept as a fallback for a build
 * whose symbols do not resolve, and now says why it gave up.
 *
 * <p>Nothing is forced while the strip is actually moving: the clock restarts on every scroll, so a
 * genuine 200 ms switch window is untouched. {@code s3} set while {@code t3} is already true is a
 * contradiction — the strip cannot be switching and settled at once — and is cleared on sight.
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

    /** The class and the flags, once the retro mode class was found. */
    private static volatile Class<?> retro;
    private static volatile Field switching;
    private static volatile Field settled;
    private static volatile Field[] flags = new Field[0];

    private static volatile boolean reportedInstall;
    private static volatile boolean reportedClear;
    private static volatile boolean reportedFallback;
    private static volatile boolean reportedRefusal;

    private PopFilterGate() {}

    /** The retro mode class, for the other POP hooks that need to tell it from the other modes. */
    static Class<?> retroClass() {
        return retro;
    }

    static void install(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "POP filter gate off (" + OFF + "), the shutter is left as the camera has it");
            return;
        }
        if (bySymbols()) return;
        byConstructors(classLoader);
    }

    // --- primary path: the class itself ---------------------------------------------------------

    /** Hooks the retro mode class found by fingerprint; works for instances built before us. */
    private static boolean bySymbols() {
        try {
            Symbols symbols = Symbols.get();
            if (!symbols.has("RetroMode", "RetroMode.shutterClick", "RetroMode.shutterTouch",
                    "RetroMode.readyToCapture")) {
                return false;
            }
            Class<?> type = symbols.cls("RetroMode");
            arm(type,
                symbols.method("RetroMode.shutterClick"),
                symbols.method("RetroMode.shutterTouch"),
                symbols.method("RetroMode.readyToCapture"),
                symbols.has("RetroMode.filterScrolling") ? symbols.method("RetroMode.filterScrolling") : null,
                symbols.has("RetroMode.filterSettled") ? symbols.method("RetroMode.filterSettled") : null,
                symbols.has("RetroMode.setSwitching") ? symbols.method("RetroMode.setSwitching") : null,
                "symbols");
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "POP filter gate could not use its symbols", error);
            return false;
        }
    }

    // --- fallback: the mode object's constructor ------------------------------------------------

    /**
     * The 488/489 path, kept for a build whose symbols do not resolve. It only works when the mode
     * object is built after this runs, which is not what happens on this phone — hence the log line
     * that says so instead of a silent no-op.
     */
    private static void byConstructors(ClassLoader classLoader) {
        try {
            Class<?> base = XposedHelpers.findClass(BASE_MODE, classLoader);
            XposedBridge.hookAllConstructors(base, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject == null) return;
                    Class<?> type = param.thisObject.getClass();
                    if (retro != null) return;
                    try {
                        identify(type);
                    } catch (Throwable error) {
                        Log.e(TAG, "POP filter gate could not read " + type.getName(), error);
                    }
                }
            });
            Log.i(TAG, "POP filter gate waiting on " + BASE_MODE + " constructors (fallback: the retro "
                + "mode symbols did not resolve)");
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
        String missing = shapeProblem(type, click, touch, ready, scrolling, snapSettled);
        if (missing != null) {
            if (!reportedFallback) {
                reportedFallback = true;
                Log.i(TAG, "POP filter gate skipped " + type.getName() + ": " + missing);
            }
            return;
        }
        arm(type, click, touch, ready, scrolling, snapSettled,
            method(type, "Ek", String.class, boolean.class), "constructors");
    }

    private static String shapeProblem(Class<?> type, Method click, Method touch, Method ready,
                                       Method scrolling, Method snapSettled) {
        if (click == null) return "no Gf(int):boolean";
        if (touch == null) return "no Hf(int):void";
        if (ready == null) return "no Td(boolean,boolean,boolean,boolean):boolean";
        if (scrolling == null) return "no r6(int):void";
        if (snapSettled == null) return "no T0():void";
        if (click.getReturnType() != boolean.class) return "Gf does not return boolean";
        if (touch.getReturnType() != void.class) return "Hf does not return void";
        if (ready.getReturnType() != boolean.class) return "Td does not return boolean";
        if (scrolling.getReturnType() != void.class) return "r6 does not return void";
        if (snapSettled.getReturnType() != void.class) return "T0 does not return void";
        Field[] booleans = booleans(type);
        if (booleans.length == 0) return "no boolean fields";
        if (booleans.length > 6) return booleans.length + " boolean fields, too many";
        return null;
    }

    // --- the hooks ------------------------------------------------------------------------------

    private static void arm(Class<?> type, Method click, Method touch, Method ready,
                            Method scrolling, Method snapSettled, Method writer, String how) {
        retro = type;
        flags = booleans(type);
        switching = named(type, "s3");
        settled = named(type, "t3");

        if (scrolling != null) {
            XposedBridge.hookMethod(scrolling, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    armed = android.os.SystemClock.elapsedRealtime();
                }
            });
        }
        // The writer of the flag: watching it is what gives the clock a real start, even when the
        // strip was moved by a delayed runnable rather than by a scroll this session.
        if (writer != null) {
            XposedBridge.hookMethod(writer, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    boolean on = param.args.length > 1 && Boolean.TRUE.equals(param.args[1]);
                    armed = on ? android.os.SystemClock.elapsedRealtime() : 0;
                }
            });
        }
        if (snapSettled != null) {
            XposedBridge.hookMethod(snapSettled, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    armed = 0;
                    Log.i(TAG, "filter strip settled, flags " + describe(read(param.thisObject)));
                }
            });
        }
        XC_MethodHook unstick = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                unstick(param.thisObject);
            }
        };
        XposedBridge.hookMethod(click, unstick);
        XposedBridge.hookMethod(touch, unstick);
        XposedBridge.hookMethod(ready, unstick);
        // 492: the flag can be clear and the shutter still do nothing. The click override returns a
        // boolean and the camera acts on it, so say so once instead of leaving the report with only
        // "no photo".
        XposedBridge.hookMethod(click, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(param.getResult())) return;
                if (reportedRefusal) return;
                reportedRefusal = true;
                Log.i(TAG, "shutter click returned false, flags " + describe(read(param.thisObject)));
            }
        });
        if (!reportedInstall) {
            reportedInstall = true;
            Log.i(TAG, "POP filter gate installed on " + type.getName() + " via " + how + ", switching flag "
                + name(switching) + ", settled flag " + name(settled) + ", " + flags.length + " boolean(s)"
                + (writer == null ? ", no writer hook" : ""));
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
            boolean contradictory = settled != null && settled.getBoolean(mode);
            long since = armed;
            if (!contradictory) {
                if (since == 0) {
                    // First sight of the flag with no known start. The camera gets its full window
                    // before anything is touched: a real switch finishes in a couple of hundred ms.
                    armed = now;
                    return;
                }
                long idle = now - since;
                if (idle <= STALE_MS) return;
                clear(mode, idle + " ms idle");
                return;
            }
            clear(mode, "settled flag already set");
        } catch (Throwable error) {
            Log.e(TAG, "POP filter gate could not clear the switch flag", error);
        }
    }

    private static void clear(Object mode, String why) throws IllegalAccessException {
        if (switching != null) {
            switching.setBoolean(mode, false);
        } else {
            for (Field flag : flags) if (flag.getBoolean(mode)) flag.setBoolean(mode, false);
        }
        if (settled != null) settled.setBoolean(mode, true);
        armed = 0;
        if (!reportedClear) {
            reportedClear = true;
            Log.i(TAG, "stale filter switch cleared (" + why + "), flags " + describe(read(mode)));
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
