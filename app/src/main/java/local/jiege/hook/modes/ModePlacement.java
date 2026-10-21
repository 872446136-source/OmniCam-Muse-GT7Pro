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
 * <p>Only that one key is touched, only for this process, and only for the length of the call that
 * reads it. Escape hatch: {@code touch /data/adb/omnicam_placement_off}.
 */
public final class ModePlacement {
    private static final String TAG = "ModePlacement";
    private static final String OFF = "/data/adb/omnicam_placement_off";
    private static final String CONFIG = "com.oplus.camera.configure.CameraConfig";
    private static final String READER = "u";
    private static final String PANEL = "com.oplus.feature.move.mode.to.panel";
    private static final String MASTER = "professional";
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
                    if (kept.size() == original.size()) return;
                    param.setResult(kept);
                    log("panel move served without " + MASTER + ": " + kept);
                }
            });
            Log.i(TAG, "panel move hooked, 大师模式 goes to the bar");
        } catch (Throwable error) {
            Log.e(TAG, "panel move hook failed", error);
        }
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
