package local.jiege.hook.modes;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import local.jiege.hook.common.Log;

/**
 * Read-only probe for the mode table: what the camera asks, what it gets back, what is in the
 * database.
 *
 * <p>Why this exists. Switching to GR or POP freezes the camera, and the freeze looks identical to
 * a mode the camera refuses to switch to: {@code CameraControlUI} logs "Camera Mode Change Start,
 * next mode: gr", the indicator moves, then {@code vm/l.i} logs
 * "checkToSwitchMode, the device is not support the mode:gr" and returns, so the UI is left
 * half-switched and the mode bar stops responding.
 *
 * <p>{@code vm/l.i} decides that by three steps: {@code pq.h.f(name)} turns the mode name into an
 * id, {@code dq.e.q(id)} runs {@code select * from mode_info where mode_id = ?} on a worker thread
 * and blocks on a {@code ConditionVariable} for the answer, and the row's name is compared with the
 * name that was asked for. Any of the three can be the one that fails, and from the outside they
 * look the same. This probe logs all three:
 *
 * <ul>
 *   <li>{@code f(gr) -> 39} — the id the camera actually uses.
 *   <li>{@code q(39) -> ModeInfo: [mName: gr, ...] (2 ms)} — the row it got, or {@code null}.
 *   <li>a dump of {@code mode_info} two seconds after seeding, so the table can be compared with
 *       what the mode bar shows.
 * </ul>
 *
 * <p>Nothing is modified. Escape hatch: {@code touch /data/adb/omnicam_probe_off}.
 */
public final class ModeProbe {
    private static final String TAG = "ModeProbe";
    private static final String OFF = "/data/adb/omnicam_probe_off";
    private static final String OPERATOR = "com.oplus.camera.dq.e";
    private static final String HELPER = "com.oplus.camera.pq.h";
    private static final String DB = "/data/user/0/com.oplus.camera/databases/mode_data.db";
    private static final int DUMP_DELAY_MS = 2500;
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
        ModeGates.afterApplication(classLoader, new Runnable() {
            @Override public void run() {
                install(classLoader);
            }
        });
    }

    private static synchronized void install(ClassLoader classLoader) {
        if (installed) return;
        installed = true;
        hookName(HELPER, "f", String.class, int.class, classLoader);
        hookQuery(classLoader);
        hookSeed(classLoader);
    }

    /** {@code pq.h.f(String)} — mode name to mode id. */
    private static void hookName(String owner, String name, Class<?> param, Class<?> returns,
        ClassLoader classLoader) {
        try {
            Method method = find(Class.forName(owner, false, classLoader), name, param);
            if (method == null) {
                Log.i(TAG, owner + "." + name + " not found, name lookups are not logged");
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
    private static void hookQuery(ClassLoader classLoader) {
        try {
            Method method = find(Class.forName(OPERATOR, false, classLoader), "q", int.class);
            if (method == null) {
                Log.i(TAG, OPERATOR + ".q not found, row lookups are not logged");
                return;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    param.setObjectExtra(TAG, System.currentTimeMillis());
                }

                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Object start = param.getObjectExtra(TAG);
                    long elapsed = start instanceof Long ? System.currentTimeMillis() - (Long) start : -1;
                    Object ref = param.getResult();
                    Object row = null;
                    try {
                        if (ref instanceof java.util.concurrent.atomic.AtomicReference) {
                            row = ((java.util.concurrent.atomic.AtomicReference<?>) ref).get();
                        }
                    } catch (Throwable ignored) {
                    }
                    log("q(" + param.args[0] + ") -> " + (row == null ? "null" : row)
                        + " (" + elapsed + " ms)");
                }
            });
            Log.i(TAG, "row lookup hooked");
        } catch (Throwable error) {
            Log.e(TAG, "row lookup hook failed", error);
        }
    }

    /** {@code dq.e.h()} — seeding. Dumped once, after it returns, on a thread of our own. */
    private static void hookSeed(ClassLoader classLoader) {
        try {
            Method method = find(Class.forName(OPERATOR, false, classLoader), "h");
            if (method == null) {
                Log.i(TAG, OPERATOR + ".h not found, the table is not dumped");
                return;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    dumpLater();
                }
            });
            Log.i(TAG, "seeding hooked");
        } catch (Throwable error) {
            Log.e(TAG, "seeding hook failed", error);
        }
    }

    private static void dumpLater() {
        if (!dumped.compareAndSet(false, true)) return;
        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Thread.sleep(DUMP_DELAY_MS);
                } catch (InterruptedException ignored) {
                    return;
                }
                dump();
            }
        }, "mode-probe");
        thread.setDaemon(true);
        thread.start();
    }

    /** The table as the camera left it: name, id, position, rank, and both support columns. */
    private static void dump() {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(DB, null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.rawQuery(
                "select mode_name, mode_id, position, rank, support_front, support_edit from mode_info"
                    + " order by position, rank", null);
            int rows = 0;
            while (cursor.moveToNext() && rows < DUMP_LIMIT) {
                rows++;
                Log.i(TAG, "row " + cursor.getString(0) + " id=" + cursor.getInt(1)
                    + " " + cursor.getString(2) + " rank=" + cursor.getInt(3)
                    + " front=" + cursor.getString(4) + " edit=" + cursor.getString(5));
            }
            Log.i(TAG, "table: " + rows + " rows");
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
