package local.jiege.hook.modes;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import local.jiege.hook.common.Log;

/**
 * Read-only probe for the mode table, plus one repair: a row for a mode the camera will not switch
 * to because it has none.
 *
 * <p>Why this exists. Switching to GR or POP freezes the camera, and the freeze looks identical to
 * a mode the camera refuses to switch to: {@code CameraControlUI} logs "Camera Mode Change Start,
 * next mode: gr", the indicator moves, then {@code vm/l.i} logs
 * "checkToSwitchMode, the device is not support the mode:gr" and returns, so the UI is left
 * half-switched and the mode bar stops responding.
 *
 * <p>{@code vm/l.i} decides that in three steps: {@code pq.h.f(name)} turns the mode name into an
 * id, {@code dq.e.q(id)} runs {@code select * from mode_info where mode_id = ?} on a worker thread
 * and blocks on a {@code ConditionVariable} for the answer, and the row's name is compared with the
 * name that was asked for. Any of the three can be the one that fails, and from the outside they
 * look the same. This probe logs all three.
 *
 * <p><b>Class names.</b> These classes are in the default package: the dex says {@code Ldq/e;}, not
 * {@code Lcom/oplus/camera/dq/e;}. 472 hardcoded the prefixed name and the probe never installed —
 * {@code ClassNotFoundException: com.oplus.camera.pq.h} three times, which the settings page then
 * reported as "相机注入异常". Both spellings are tried now.
 *
 * <p>The table dump does not depend on any hook installing: it is scheduled from
 * {@code Application.onCreate} and reads the database directly over a read-only connection, so the
 * table is logged even if every hook above fails.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_probe_off}.
 */
public final class ModeProbe {
    private static final String TAG = "ModeProbe";
    private static final String OFF = "/data/adb/omnicam_probe_off";
    /**
     * ModeDataSQLiteOpenHelper, HeadlineHelper and ModeInfo. Two-letter names, and they move with
     * every camera build: 7.013.40 renamed all three (dq.e → np.e, pq.h → zp.k, ja.a → ia.a). The
     * build this module targets is listed first, the previous one second, so a rollback still logs.
     */
    private static final String[] OPERATOR = {"np.e", "dq.e"};
    private static final String[] HELPER = {"zp.k", "pq.h"};
    private static final String[] MODE_INFO = {"ia.a", "ja.a"};
    private static final String PREFIX = "com.oplus.camera.";
    private static final String DB = "/data/user/0/com.oplus.camera/databases/mode_data.db";
    private static final String[] WANTED = {
        "gr", "retroCamera", "professional",
        // 475: the two pages merged from the B6 port, plus XPAN, which is now seeded into the panel.
        "movie", "highPixel", "xpan",
    };
    private static final long[] DUMP_AT_MS = {8000L, 20000L};
    private static final int DUMP_LIMIT = 48;
    private static final AtomicBoolean dumped = new AtomicBoolean();
    private static boolean installed;

    private ModeProbe() {}

    /** Armed from Application.onCreate, same as ModeGates: never before the app exists. */
    public static void installCamera(final ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "probe off (" + OFF + ")");
            return;
        }
        // The dump needs no camera class at all, so it is armed before anything can fail.
        dumpLater();
        ModeGates.afterApplication(classLoader, new Runnable() {
            @Override public void run() {
                install(classLoader);
            }
        });
    }

    private static synchronized void install(ClassLoader classLoader) {
        if (installed) return;
        installed = true;
        Class<?> operator = load(OPERATOR, classLoader);
        Class<?> helper = load(HELPER, classLoader);
        Class<?> modeInfo = load(MODE_INFO, classLoader);        if (helper != null) hookName(helper);
        if (operator != null && helper != null && modeInfo != null) {
            hookQuery(operator, helper, modeInfo);
        }
        if (operator != null) hookSeed(operator);
        if (operator == null || helper == null) {
            Log.i(TAG, "probe incomplete: the table dump is all there is");
        }
    }

    /** These classes live in the default package; the prefixed spelling is tried second. */
    private static Class<?> load(String[] names, ClassLoader classLoader) {
        for (String name : names) {
            for (String candidate : new String[] {name, PREFIX + name}) {
                try {
                    Class<?> found = Class.forName(candidate, false, classLoader);
                    Log.i(TAG, candidate + " found");
                    return found;
                } catch (Throwable ignored) {
                }
            }
        }
        Log.i(TAG, names[0] + " not found, its lookups are not logged");
        return null;
    }

    /** {@code pq.h.f(String)} — mode name to mode id. Static. */
    private static void hookName(Class<?> helper) {
        try {
            Method method = find(helper, "f", String.class);
            if (method == null) {
                Log.i(TAG, "name lookup not found");
                return;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    log("f(" + param.args[0] + ") -> " + param.getResult());
                }
            });
            Log.i(TAG, "name lookup hooked");
        } catch (Throwable error) {
            Log.e(TAG, "name lookup hook failed", error);
        }
    }

    /** {@code dq.e.q(int)} — the blocking row lookup behind "the device is not support the mode". */
    private static void hookQuery(final Class<?> operator, final Class<?> helper, final Class<?> modeInfo) {
        try {
            Method method = find(operator, "q", int.class);
            if (method == null) {
                Log.i(TAG, "row lookup not found");
                return;
            }
            final Method nameOf = find(helper, "g", int.class);
            final Constructor<?> rowOf = constructor(modeInfo);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    param.setObjectExtra(TAG, System.currentTimeMillis());
                }

                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Object start = param.getObjectExtra(TAG);
                    long elapsed = start instanceof Long ? System.currentTimeMillis() - (Long) start : -1;
                    Object ref = param.getResult();
                    if (!(ref instanceof AtomicReference)) return;
                    AtomicReference<?> reference = (AtomicReference<?>) ref;
                    Object row = reference.get();
                    int id = (int) param.args[0];
                    String name = nameOf(nameOf, id);
                    log("q(" + id + ") -> " + (row == null ? "null" : row)
                        + " (" + elapsed + " ms)" + (name == null ? "" : " name=" + name));
                    if (row != null || name == null || !wanted(name)) return;
                    Object synthetic = row(rowOf, name, id);
                    if (synthetic == null) return;
                    set(reference, synthetic);
                    log("q(" + id + ") was empty, answered a " + name + " row");
                }
            });
            Log.i(TAG, "row lookup hooked");
        } catch (Throwable error) {
            Log.e(TAG, "row lookup hook failed", error);
        }
    }

    /** {@code dq.e.h()} — seeding. Only noted; the dump runs on its own timer. */
    private static void hookSeed(Class<?> operator) {
        try {
            Method method = find(operator, "h");
            if (method == null) return;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    log("seeding returned");
                }
            });
        } catch (Throwable error) {
            Log.e(TAG, "seeding hook failed", error);
        }
    }

    private static void dumpLater() {
        if (!dumped.compareAndSet(false, true)) return;
        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                long start = System.currentTimeMillis();
                for (long at : DUMP_AT_MS) {
                    long wait = at - (System.currentTimeMillis() - start);
                    if (wait > 0) {
                        try {
                            Thread.sleep(wait);
                        } catch (InterruptedException ignored) {
                            return;
                        }
                    }
                    dump("+" + at + "ms");
                }
            }
        }, "mode-probe");
        thread.setDaemon(true);
        thread.start();
    }

    /** The table as the camera left it: name, id, position, rank, and both support columns. */
    private static void dump(String when) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(DB, null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.rawQuery(
                "select mode_name, mode_id, position, rank, support_front, support_edit from mode_info"
                    + " order by position, rank", null);
            int rows = 0;
            StringBuilder headline = new StringBuilder();
            while (cursor.moveToNext() && rows < DUMP_LIMIT) {
                rows++;
                String name = cursor.getString(0);
                String position = cursor.getString(2);
                log("row " + when + " " + name + " id=" + cursor.getInt(1)
                    + " " + position + " rank=" + cursor.getInt(3)
                    + " front=" + cursor.getString(4) + " edit=" + cursor.getString(5));
                if ("position_headline".equals(position)) {
                    if (headline.length() > 0) headline.append(',');
                    headline.append(name);
                }
            }
            Log.i(TAG, "table " + when + ": " + rows + " rows, headline: " + headline);
        } catch (Throwable error) {
            Log.e(TAG, "table dump failed", error);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignored) {
                }
            }
            if (db != null) {
                try {
                    db.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** id to mode name through the camera's own map, so no id is hardcoded here. */
    private static String nameOf(Method nameOf, int id) {
        if (nameOf == null) return null;
        try {
            Object name = nameOf.invoke(null, id);
            return name instanceof String ? (String) name : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean wanted(String name) {
        for (String mode : WANTED) if (mode.equals(name)) return true;
        return false;
    }

    private static Constructor<?> constructor(Class<?> modeInfo) {
        try {
            return modeInfo.getDeclaredConstructor(
                String.class, boolean.class, String.class, int.class, int.class, boolean.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Same shape dq.d builds: (name, supportEdit, position, id, rank, supportFront). */
    private static Object row(Constructor<?> rowOf, String name, int id) {
        if (rowOf == null) return null;
        try {
            return rowOf.newInstance(name, Boolean.TRUE, "position_headline", id, 0, Boolean.FALSE);
        } catch (Throwable error) {
            Log.e(TAG, "could not build a row for " + name, error);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void set(AtomicReference<?> reference, Object value) {
        try {
            ((AtomicReference<Object>) reference).set(value);
        } catch (Throwable ignored) {
        }
    }

    private static Method find(Class<?> owner, String name, Class<?>... params) {
        for (Method method : owner.getDeclaredMethods()) {
            if (!name.equals(method.getName())) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length != params.length) continue;
            boolean same = true;
            for (int i = 0; i < types.length; i++) {
                if (!types[i].equals(params[i])) {
                    same = false;
                    break;
                }
            }
            if (same) return method;
        }
        return null;
    }

    private static void log(String message) {
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
        }
    }
}
