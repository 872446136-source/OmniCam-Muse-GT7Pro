package local.jiege.hook.modes;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;

/**
 * Puts 大师模式 in the mode bar instead of the "more" panel.
 *
 * <p>大师模式 is mode {@code professional} (the camera calls it ProfessionalMode; the filters code
 * in this module has always used the same name). Its support flag
 * {@code com.oplus.feature.professional.mode.support} is not in this device's
 * {@code oplus_camera_config} at all, so the seeding code never adds a row for it on its own; what
 * puts it in the table is a pair of config keys that name a mode and a rank:
 *
 * <pre>
 *   com.oplus.feature.move.mode.to.headline = professional,0
 *   com.oplus.feature.move.mode.to.panel    = night,0,professional,1,highDefinition,2
 * </pre>
 *
 * <p>Both are present, and the panel one is applied last, so 大师模式 is moved into the headline and
 * then moved straight back out — it ends up inside the panel and the bar never shows it. The
 * headline key is the one that matches the reference device, so the panel list is served without
 * {@code professional} in it and the headline move stands. Night is then pushed into the panel to
 * make room, which is what the reference layout does anyway.
 *
 * <p>The same key also carries XPAN's rank, which is how 485 gets XPAN on screen at all: it is
 * seeded into the panel but lands in the last slot, past where the panel stops drawing. See
 * {@link #promoteXpan}.
 *
 * <p>Only that key is touched, only for this process, and only for the length of the call that
 * reads it. Escape hatch: {@code touch /data/adb/omnicam_placement_off}.
 */
public final class ModePlacement {
    private static final String TAG = "ModePlacement";
    private static final String OFF = "/data/adb/omnicam_placement_off";
    private static final String CONFIG = "com.oplus.camera.configure.CameraConfig";
    private static final String READER = "u";
    private static final String PANEL = "com.oplus.feature.move.mode.to.panel";
    private static final String MASTER = "professional";
    /** XPAN's mode name in this firmware's tables ({@code xpan id=28}). */
    private static final String XPAN = "xpan";
    /** Where XPAN is moved to: inside the part of the panel the UI actually draws. */
    private static final int XPAN_RANK = 3;
    private static boolean installed;

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
        Method reader;
        try {
            Class<?> config = Class.forName(CONFIG, false, classLoader);
            reader = find(config);
            if (reader == null) {
                Log.i(TAG, CONFIG + "." + READER + " not found, 大师模式 stays in the panel");
                return;
            }
        } catch (Throwable error) {
            Log.e(TAG, "config class unavailable", error);
            return;
        }
        try {
            XposedBridge.hookMethod(reader, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!PANEL.equals(param.args[0])) return;
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
                    if (promoteXpan(kept)) changed = true;
                    if (!changed) return;
                    param.setResult(kept);
                    log("panel move served: " + kept);
                }
            });
            Log.i(TAG, "panel move hooked, 大师模式 goes to the bar, XPAN moves up");
        } catch (Throwable error) {
            Log.e(TAG, "panel move hook failed", error);
        }
    }

    /**
     * XPAN is seeded into this device's panel by the config profile, but it lands in the last slot —
     * {@code xpan id=28 position_mode_panel rank=13} in every report since 477 — and the panel stops
     * drawing before that, so the mode exists in the table and never appears on screen.
     *
     * <p>Moving it is the same mechanism 大师模式 uses: the list this key returns is a list of
     * (mode, rank) moves, so giving XPAN a lower rank and pushing the modes it passes one slot down
     * puts it inside the visible part of the panel. Only the order changes; every entry stays.
     *
     * @return whether the list was modified
     */
    private static boolean promoteXpan(ArrayList<Object> pairs) {
        int xpan = -1;
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            if (XPAN.equals(pairs.get(i))) {
                xpan = i;
                break;
            }
        }
        if (xpan < 0) return false;
        int rank = rank(pairs.get(xpan + 1));
        if (rank < 0 || rank <= XPAN_RANK) return false;
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            if (i == xpan) continue;
            int other = rank(pairs.get(i + 1));
            if (other >= XPAN_RANK && other < rank) pairs.set(i + 1, other + 1);
        }
        pairs.set(xpan + 1, XPAN_RANK);
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
