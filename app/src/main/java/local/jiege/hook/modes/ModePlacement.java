package local.jiege.hook.modes;

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
 * <p><b>XPAN (486).</b> 485 tried to raise XPAN's rank inside the panel list, which never worked:
 * the panel list is {@code night,0,professional,1,highDefinition,2} and XPAN is not in it. Its rank
 * comes from the mode table instead, and the 485 report shows what that produced —
 * {@code q(28) -> mRank: 11, mPosition: position_mode_panel}: the mode is in the panel section, so
 * it only exists behind 更多, and picking it there leaves the 更多 sheet open because that sheet
 * belongs to the panel the mode is being taken out of. Serving the *headline* list with XPAN
 * appended puts it in the bar like every other mode and takes 更多 out of the path entirely.
 *
 * <p>Only those two keys are touched, only for this process, and only for the length of the call
 * that reads them. Escape hatch: {@code touch /data/adb/omnicam_placement_off}.
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
     * Serves {@code com.oplus.feature.move.mode.to.headline} with XPAN appended, which is what puts
     * it in the mode bar. The list is (mode, rank) pairs, so the entry is two elements.
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
