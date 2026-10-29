package local.jiege.hook.modes;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;

/**
 * Puts 大师模式 and XPAN in the mode bar instead of the "more" panel.
 *
 * <p><b>大师模式</b> is mode {@code professional}. It reaches the bar through the config pair
 * {@code com.oplus.feature.move.mode.to.headline = professional,0} and
 * {@code com.oplus.feature.move.mode.to.panel = night,0,professional,1,highDefinition,2}; the panel
 * one used to win and pull it back out. The panel list is served without {@code professional} in it,
 * so the headline move stands.
 *
 * <p><b>XPAN — why the first two attempts did nothing (487).</b> 485 raised XPAN's rank inside the
 * panel list; 486 appended it to the headline list. Both hooked
 * {@code com.oplus.camera.configure.CameraConfig.u(String)}, and the 486 report shows that method is
 * never called at all: the hook installed ("panel move hooked"), and neither branch ever logged.
 * The mode bar is not built from that config either — it is built from the camera's mode table,
 * {@code databases/mode_data.db}, which the camera seeds once and then only queries. The 486 report
 * says so outright:
 *
 * <pre>
 *   ModeProbe: row +8000ms xpan id=28 position_mode_panel rank=13
 *   ModeProbe: table +8000ms: 22 rows, headline: professional,retroCamera,commonVideo,common,gr,portrait,highPixel,more
 * </pre>
 *
 * <p>So this version stops asking the config and writes the row: after the table is seeded (and
 * once at start, for the boots where it already exists) {@code xpan} is moved to
 * {@code position_headline} with the next free rank, i.e. right after the last real mode and before
 * the {@code more} entry, whose rank is {@code Integer.MAX_VALUE}. The bar then draws it like any
 * other mode, and the "more" sheet stops being part of the path — which is also why it no longer
 * stays open after picking XPAN.
 *
 * <p>The config hooks are kept: they cost nothing, and a future firmware that does read that key
 * gets the same answer from both ends.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_placement_off}.
 */
public final class ModePlacement {
    private static final String TAG = "ModePlacement";
    private static final String OFF = "/data/adb/omnicam_placement_off";
    private static final String CONFIG = "com.oplus.camera.configure.CameraConfig";
    private static final String READER = "u";
    private static final String PANEL = "com.oplus.feature.move.mode.to.panel";
    private static final String HEADLINE = "com.oplus.feature.move.mode.to.headline";
    private static final String MASTER = "professional";
    /** XPAN's mode name in this firmware's tables ({@code xpan id=28}). */
    private static final String XPAN = "xpan";
    /** Where XPAN lands in the bar: after 大师/POP/视频/照片/理光, i.e. the last slot. */
    private static final int XPAN_HEADLINE_RANK = 6;
    /** Where XPAN is moved to if it is found in the panel list after all. */
    private static final int XPAN_PANEL_RANK = 3;
    private static final String PREFIX = "com.oplus.camera.";
    /** ModeDataSQLiteOpenHelper / the class that seeds the table. Two-letter names, they move. */
    private static final String[] OPERATOR = {"np.e", "dq.e"};
    private static final String DB = "/data/user/0/com.oplus.camera/databases/mode_data.db";
    private static final String HEADLINE_POSITION = "position_headline";
    private static final String PANEL_POSITION = "position_mode_panel";
    private static final String MORE = "more";
    private static boolean installed;
    private static boolean tableDone;

    private ModePlacement() {}

    /** Armed from Application.onCreate: the list is read while the mode table is seeded. */
    public static void installCamera(final ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "placement off (" + OFF + ")");
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
        // The table already exists on every boot after the first: fix it before the bar is built.
        placeXpan(classLoader, "start");
        hookConfig(classLoader);
        hookSeeding(classLoader);
    }

    private static void hookConfig(ClassLoader classLoader) {
        Method reader;
        try {
            Class<?> config = Class.forName(CONFIG, false, classLoader);
            reader = find(config);
            if (reader == null) {
                Log.i(TAG, CONFIG + "." + READER + " not found, the config move is skipped");
                return;
            }
        } catch (Throwable error) {
            Log.e(TAG, "config class unavailable", error);
            return;
        }
        try {
            XposedBridge.hookMethod(reader, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    String key = (String) param.args[0];
                    if (HEADLINE.equals(key)) {
                        serveHeadline(param);
                        return;
                    }
                    if (!PANEL.equals(key)) return;
                    Object value = param.getResult();
                    if (!(value instanceof List)) return;
                    List<?> original = (List<?>) value;
                    ArrayList<Object> kept = new ArrayList<>(original.size());
                    for (int i = 0; i < original.size(); i++) {
                        Object item = original.get(i);
                        if (MASTER.equals(item)) {
                            i++; // skip the rank that belongs to it
                            continue;
                        }
                        kept.add(item);
                    }
                    boolean changed = kept.size() != original.size();
                    if (promoteXpanInPanel(kept)) changed = true;
                    if (!changed) return;
                    param.setResult(kept);
                    log("panel move served: " + kept);
                }
            });
            Log.i(TAG, "panel move hooked, 大师模式 and XPAN go to the bar");
        } catch (Throwable error) {
            Log.e(TAG, "panel move hook failed", error);
        }
    }

    /**
     * The seeding method of the mode table. Rewriting the row once it has been written is the only
     * place that is guaranteed to be both after the insert and before the bar is built.
     */
    private static void hookSeeding(final ClassLoader classLoader) {
        Class<?> operator = null;
        for (String name : OPERATOR) {
            for (String candidate : new String[] {name, PREFIX + name}) {
                try {
                    operator = Class.forName(candidate, false, classLoader);
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (operator != null) break;
        }
        if (operator == null) {
            Log.i(TAG, OPERATOR[0] + " not found, the row is only fixed at the next start");
            return;
        }
        Method seed = null;
        for (Method method : operator.getDeclaredMethods()) {
            if ("h".equals(method.getName()) && method.getParameterTypes().length == 0) {
                seed = method;
                break;
            }
        }
        if (seed == null) {
            Log.i(TAG, "seeding method not found, the row is only fixed at the next start");
            return;
        }
        try {
            XposedBridge.hookMethod(seed, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    placeXpan(classLoader, "after seeding");
                }
            });
            Log.i(TAG, "seeding hooked, XPAN is written into the table as a headline row");
        } catch (Throwable error) {
            Log.e(TAG, "seeding hook failed", error);
        }
    }

    /**
     * Moves the {@code xpan} row into the headline section of the mode table.
     *
     * <p>The rank is the next free one after the last real headline mode. {@code more} is left out of
     * that calculation: its rank is {@code Integer.MAX_VALUE}, it is the "everything else" slot and it
     * has to stay last.
     */
    private static synchronized void placeXpan(ClassLoader classLoader, String when) {
        if (!new File(DB).isFile()) return;
        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = SQLiteDatabase.openDatabase(DB, null, SQLiteDatabase.OPEN_READWRITE);
            cursor = database.rawQuery(
                "select position, rank from mode_info where mode_name = ?", new String[] {XPAN});
            if (!cursor.moveToFirst()) {
                Log.i(TAG, "no " + XPAN + " row in the table (" + when + ")");
                return;
            }
            String position = cursor.getString(0);
            int rank = cursor.getInt(1);
            cursor.close();
            cursor = null;
            if (HEADLINE_POSITION.equals(position)) {
                if (!tableDone) Log.i(TAG, "xpan already in the bar at rank " + rank + " (" + when + ")");
                tableDone = true;
                return;
            }
            cursor = database.rawQuery(
                "select max(rank) from mode_info where position = ? and mode_name != ?",
                new String[] {HEADLINE_POSITION, MORE});
            int next = cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getInt(0) + 1 : 1;
            cursor.close();
            cursor = null;
            ContentValues values = new ContentValues();
            values.put("position", HEADLINE_POSITION);
            values.put("rank", next);
            int changed = database.update("mode_info", values, "mode_name = ?", new String[] {XPAN});
            if (changed > 0) {
                tableDone = true;
                Log.i(TAG, "xpan row " + position + " rank " + rank + " -> " + HEADLINE_POSITION
                    + " rank " + next + " (" + when + ")");
            } else {
                Log.i(TAG, "xpan row not updated (" + when + ")");
            }
        } catch (Throwable error) {
            Log.e(TAG, "mode table not updated (" + when + ")", error);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignored) {
                }
            }
            if (database != null) {
                try {
                    database.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Serves {@code com.oplus.feature.move.mode.to.headline} with XPAN appended, for a firmware that
     * reads that key. The list is (mode, rank) pairs, so the entry is two elements.
     *
     * <p>Left alone when the key is not present (nothing to append to), when XPAN is already in it,
     * and when the camera has already turned it into a table of another shape.
     */
    private static void serveHeadline(XC_MethodHook.MethodHookParam param) {
        Object value = param.getResult();
        if (!(value instanceof List)) return;
        List<?> original = (List<?>) value;
        if (original.isEmpty() || original.size() % 2 != 0) return;
        for (int i = 0; i < original.size(); i += 2) {
            if (XPAN.equals(original.get(i))) return;
        }
        ArrayList<Object> moved = new ArrayList<>(original.size() + 2);
        moved.addAll(original);
        moved.add(XPAN);
        moved.add(Integer.valueOf(XPAN_HEADLINE_RANK));
        param.setResult(moved);
        log("headline move served: " + moved);
    }

    /**
     * XPAN is seeded into this device's panel by the config profile. If it shows up in the panel
     * move list, give it a lower rank and push the modes it passes one slot down. 486 also puts it
     * in the headline list, which is the path that actually works; this is the fallback for the
     * case where a future build seeds it here instead.
     *
     * @return whether the list was modified
     */
    private static boolean promoteXpanInPanel(ArrayList<Object> pairs) {
        int xpan = -1;
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            if (XPAN.equals(pairs.get(i))) {
                xpan = i;
                break;
            }
        }
        if (xpan < 0) return false;
        int rank = rank(pairs.get(xpan + 1));
        if (rank < 0 || rank <= XPAN_PANEL_RANK) return false;
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            if (i == xpan) continue;
            int other = rank(pairs.get(i + 1));
            if (other >= XPAN_PANEL_RANK && other < rank) pairs.set(i + 1, other + 1);
        }
        pairs.set(xpan + 1, XPAN_PANEL_RANK);
        return true;
    }

    /** Ranks come out of the config as Integer, but a String would silently break the compare. */
    private static int rank(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    /** {@code u(String)} returning a List: this build's shape is not assumed. */
    private static Method find(Class<?> config) {
        for (Method method : config.getDeclaredMethods()) {
            if (!READER.equals(method.getName())) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 1 && types[0] == String.class && List.class.isAssignableFrom(method.getReturnType())) {
                return method;
            }
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
