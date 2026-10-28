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
 * one boolean: the mode sets it in {@code onFilterScrolling(int)} and clears it in
 * {@code onFilterSnapSettled()} — the callback the filter strip sends when its snap animation has
 * settled. On this port that second callback never arrives: the strip is drawn from the ported
 * {@code pop_port} resources, the snap animation never reports settled, and the flag stays set for
 * the rest of the session. Every later shutter press is therefore dropped, which is exactly the
 * reported "有画面但点拍照没反应".
 *
 * <p>The flag is not left alone and it is not guessed at. The class is found from the instance the
 * camera builds ({@code BaseMode}'s constructor is hooked, so every mode object passes through
 * here) and only accepted when it has the retro mode's shape — the three shutter overrides plus the
 * two callbacks. The settled state is then read off the object itself: {@code onFilterSnapSettled}
 * is called once with the fields read before and after, and the field that goes true→false is the
 * switching flag and the one that goes false→true is the settled flag. A shutter press that arrives
 * more than {@link #STALE_MS} after the last scroll or settle, with the switching flag still set, is
 * preceded by putting the object back into that settled state — the same values the camera itself
 * writes when the animation does report back.
 *
 * <p>Nothing is forced while the strip is actually moving: the watchdog only fires when no scroll
 * and no settle has been seen for {@link #STALE_MS}, so a genuine 200 ms switch window is untouched.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_popgate_off}.
 */
final class PopFilterGate {
    private static final String TAG = "PopPort";
    private static final String BASE_MODE = "com.oplus.camera.module.BaseMode";
    private static final String OFF = "/data/adb/omnicam_popgate_off";

    /** No scroll and no settle for this long means the settle callback is never coming. */
    private static final long STALE_MS = 1200;

    /** The last time the mode reported anything about the filter strip (scroll or settle). */
    private static volatile long activity;

    /** The class and the two callbacks, once a mode object with the retro shape was seen. */
    private static volatile Class<?> retro;
    private static volatile Method scroll;
    private static volatile Method settled;

    /** The switching flag (true→false on settle) and the settled flag (false→true on settle). */
    private static volatile Field switching;
    private static volatile Field settledFlag;
    private static volatile Field[] flags = new Field[0];

    /** Set once the field values have been reported, so one report carries them once. */
    private static boolean reportedFields;
    /** The settle callback has run at least once, so the flags are known to be the camera's own. */
    private static volatile boolean sawSettle;
    private static boolean reportedStale;

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
        if (booleans.length == 0 || booleans.length > 4) return;

        retro = type;
        scroll = scrolling;
        settled = snapSettled;
        flags = booleans;

        XposedBridge.hookMethod(scrolling, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                activity = android.os.SystemClock.elapsedRealtime();
            }
        });
        XposedBridge.hookMethod(snapSettled, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                param.setObjectExtra("before", read(param.thisObject));
            }

            @Override protected void afterHookedMethod(MethodHookParam param) {
                activity = android.os.SystemClock.elapsedRealtime();
                sawSettle = true;
                boolean[] before = (boolean[]) param.getObjectExtra("before");
                boolean[] after = read(param.thisObject);
                if (before == null || after == null || before.length != after.length) return;
                for (int i = 0; i < before.length; i++) {
                    if (before[i] && !after[i]) switching = flags[i];
                    if (!before[i] && after[i]) settledFlag = flags[i];
                }
                if (!reportedFields) {
                    reportedFields = true;
                    Log.i(TAG, "filter strip settled: " + describe(after)
                        + ", switching flag " + name(switching) + ", settled flag " + name(settledFlag));
                }
            }
        });
        XC_MethodHook unstick = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                unstick(param.thisObject, "shutter");
            }
        };
        XposedBridge.hookMethod(click, unstick);
        XposedBridge.hookMethod(touch, unstick);
        XposedBridge.hookMethod(ready, unstick);
        Log.i(TAG, "POP filter gate installed on " + type.getName() + ", " + flags.length + " flag(s)");
    }

    /**
     * Puts the mode back into the state its own settle callback writes, when that callback is
     * overdue. Called from all three shutter overrides, before the camera's guard reads the flag.
     */
    private static void unstick(Object mode, String door) {
        long now = android.os.SystemClock.elapsedRealtime();
        long last = activity;
        // Nothing has ever been reported about the strip: there is nothing that can be stuck, and
        // touching the flags now would change a state the camera has not set yet.
        if (last == 0 || now - last <= STALE_MS) return;
        try {
            boolean stuck = switching != null && switching.getBoolean(mode);
            boolean unsettled = settledFlag != null && !settledFlag.getBoolean(mode);
            if (!stuck && !unsettled) {
                // Never saw the settle callback: report what the object holds so the next report
                // says which field it is, and clear the ones that are set.
                boolean[] values = read(mode);
                if (!reportedFields && values != null) {
                    reportedFields = true;
                    Log.i(TAG, "no settle callback yet, flags are " + describe(values));
                }
                if (!reportedStale && values != null) {
                    reportedStale = true;
                    Log.i(TAG, "shutter reached with the filter strip idle for " + (now - last)
                        + " ms, flags " + describe(values));
                }
                if (switching == null && !sawSettle) {
                    // The settle callback has never run on this build, so the fields were never
                    // identified by their transition. Clearing the ones that are set is the only
                    // reading of "still switching" that can be acted on; a build where the callback
                    // does run is left alone because its flags are already correct.
                    for (Field flag : flags) if (flag.getBoolean(mode)) flag.setBoolean(mode, false);
                }
                return;
            }
            if (stuck) switching.setBoolean(mode, false);
            if (unsettled) settledFlag.setBoolean(mode, true);
            if (!reportedStale) {
                reportedStale = true;
                Log.i(TAG, "stale filter switch cleared on " + door + " after "
                    + (now - last) + " ms idle, flags " + describe(read(mode)));
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
        return field == null ? "(not seen)" : field.getName();
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
