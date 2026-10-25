package local.jiege.hook.pop;

import android.app.Activity;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/**
 * Stops POP's delayed show-UI runnable from taking the process down, and builds the view it wants
 * when the camera has not.
 *
 * <p>476 crashed here, three times, on the main thread:
 *
 * <pre>
 *   NullPointerException: Attempt to invoke virtual method 'void bj.z.l(int)' on a null object
 *     at com.oplus.camera.ui.newsetting.container.t1.run(R8$$SyntheticClass:135)
 * </pre>
 *
 * <p>{@code t1} is a one-shot runnable holding {@code (Object container, int what)}. For the retro
 * case the container is the {@code RetroCameraUIContainer} and {@code run()} reads that container's
 * own {@code RetroCameraUI} field and calls {@code l(int)} — the show animation. 477 wrapped the
 * run and the crash stopped, which left the report saying what the crash could not:
 *
 * <pre>
 *   retro show runnable dropped (…container.t1, #1, retro UI init done: false)
 * </pre>
 *
 * <p>So the field is null because the view was never built: {@code RetroCameraUIContainer.init} —
 * the method that constructs {@code RetroCameraUI} and stores it in that field — never ran. Nothing
 * else in the process builds it, so POP comes up with no UI at all: the mode switch succeeds, the
 * preview stops painting the retro overlay, and the screen sits there until the user picks another
 * mode. That is the "卡死".
 *
 * <p>478 therefore keeps the guard and adds the repair: when the show runnable throws and the init
 * has not completed, the container is asked to build its view, and the runnable is retried once.
 * The two arguments the init needs are read off the container rather than from a symbol table,
 * because both live in obfuscated one-letter fields that move every build:
 *
 * <ul>
 *   <li>the {@code Activity} — the container's only field of that type;</li>
 *   <li>the {@code com.oplus.camera.protocal.ui.a} facade — reached through the container's camera
 *       manager field, by calling the no-argument method on it whose return type implements that
 *       interface. The interface name is stable across builds; the field and method names are not.</li>
 * </ul>
 *
 * <p>Both the runnable's container and the container instance itself are taken from the object the
 * hook is attached to, so a second camera session cannot repair the first one's container.
 *
 * <p>Armed from {@code Application.onCreate} with the rest of the POP hooks. Escape hatches:
 * {@code touch /data/adb/omnicam_popguard_off} for the whole guard, and
 * {@code touch /data/adb/omnicam_poprepair_off} to keep the guard but leave the view alone.
 */
public final class PopGuard {
    private static final String TAG = "PopPort";
    private static final String OFF = "/data/adb/omnicam_popguard_off";
    /** Turns off only the view repair, keeping the crash guard. */
    private static final String REPAIR_OFF = "/data/adb/omnicam_poprepair_off";
    /** Stable across builds: the UI facade interface, misspelling and all. */
    private static final String UI_FACADE = "com.oplus.camera.protocal.ui.a";
    private static final int LOG_LIMIT = 10;

    private static final AtomicInteger SWALLOWED = new AtomicInteger();
    private static final AtomicBoolean INIT_DONE = new AtomicBoolean();

    /** The container a repair was already attempted on: one attempt per camera session, not per app. */
    private static volatile Object attempted;

    /** The most recently constructed {@code RetroCameraUIContainer}, as a fallback lookup. */
    private static volatile Object latest;

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
        watchContainer(symbols);
        guardShow(symbols);
    }

    /**
     * The container's constructor: remembers the instance so a repair can find one even when the
     * runnable's own reference is the only thing left.
     */
    private static void watchContainer(Symbols symbols) {
        if (!symbols.has("PopUI.init")) return;
        Class<?> container;
        try {
            container = symbols.method("PopUI.init").getDeclaringClass();
        } catch (Throwable error) {
            Log.e(TAG, "cannot reach the retro container class", error);
            return;
        }
        int hooked = 0;
        for (Constructor<?> constructor : container.getDeclaredConstructors()) {
            try {
                XposedBridge.hookMethod(constructor, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        latest = param.thisObject;
                        // A new session builds a new container, so "the view was built" starts over.
                        INIT_DONE.set(false);
                    }
                });
                hooked++;
            } catch (Throwable error) {
                Log.e(TAG, "cannot watch a retro container constructor", error);
            }
        }
        Log.i(TAG, "retro container watched (" + container.getName() + ", " + hooked + " ctors)");
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

    /** The show-UI runnable: run it, and if the view is missing build it and run it again. */
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
                        if (repair(symbols, param.thisObject)) {
                            try {
                                XposedBridge.invokeOriginalMethod(
                                    param.method, param.thisObject, param.args);
                                Log.i(TAG, "retro show runnable replayed after the view was built");
                            } catch (Throwable again) {
                                note(param.thisObject, again);
                            }
                        } else {
                            note(param.thisObject, error);
                        }
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

    /**
     * Builds the retro view the container never built.
     *
     * @return whether the init ran without throwing, i.e. whether a retry is worth it
     */
    private static boolean repair(Symbols symbols, Object runnable) {
        if (INIT_DONE.get()) return false;
        if (new File(REPAIR_OFF).exists()) return false;
        Object container = containerOf(runnable);
        if (container == null) {
            Log.i(TAG, "retro UI missing and no container to build it on");
            return false;
        }
        if (container == attempted) return false;
        attempted = container;
        Activity activity = activityOf(container);
        Object facade = facadeOf(container);
        if (activity == null || facade == null) {
            Log.i(TAG, "retro UI missing, cannot build it: activity=" + (activity != null)
                + " uiFacade=" + (facade != null));
            return false;
        }
        Log.i(TAG, "retro UI missing, building it on " + container.getClass().getName());
        try {
            symbols.call("PopUI.init", container, activity, facade);
            return INIT_DONE.get();
        } catch (Throwable error) {
            Log.e(TAG, "retro UI init threw when called directly", error);
            return false;
        }
    }

    /** {@code t1}'s container reference: the runnable's only field that is not a primitive. */
    private static Object containerOf(Object runnable) {
        if (runnable != null) {
            for (Field field : runnable.getClass().getDeclaredFields()) {
                if (field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(runnable);
                    if (value != null) return value;
                } catch (Throwable ignored) {
                    // try the next one
                }
            }
        }
        return latest;
    }

    /** The container's {@code Activity}: the one field declared with that type. */
    private static Activity activityOf(Object container) {
        for (Field field : container.getClass().getDeclaredFields()) {
            if (!Activity.class.isAssignableFrom(field.getType())) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(container);
                if (value instanceof Activity) return (Activity) value;
            } catch (Throwable ignored) {
                // try the next one
            }
        }
        return null;
    }

    /**
     * The {@code ui.a} facade, without naming a single obfuscated member: walk the container's
     * object fields, and on each one look for a no-argument method whose return type implements the
     * facade interface. On the camera manager that is the UI-manager getter.
     */
    private static Object facadeOf(Object container) {
        Class<?> facade;
        try {
            facade = Class.forName(UI_FACADE, false, container.getClass().getClassLoader());
        } catch (Throwable error) {
            Log.e(TAG, "ui facade interface not found: " + UI_FACADE, error);
            return null;
        }
        for (Field field : container.getClass().getDeclaredFields()) {
            if (field.getType().isPrimitive()) continue;
            Object holder;
            try {
                field.setAccessible(true);
                holder = field.get(container);
            } catch (Throwable ignored) {
                continue;
            }
            if (holder == null) continue;
            Method getter = null;
            for (Method method : holder.getClass().getMethods()) {
                if (method.getParameterTypes().length != 0) continue;
                if (!facade.isAssignableFrom(method.getReturnType())) continue;
                getter = method;
                break;
            }
            if (getter == null) continue;
            try {
                Object value = getter.invoke(holder);
                if (value != null) {
                    Log.i(TAG, "ui facade taken from " + field.getName() + " -> " + getter.getName());
                    return value;
                }
            } catch (Throwable error) {
                Log.i(TAG, "ui facade getter " + getter.getName() + " threw: " + error);
            }
        }
        return null;
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
            + ", retro UI init done: " + INIT_DONE.get() + ")", error);
    }
}
