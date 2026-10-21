package local.jiege.hook.modes;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/**
 * The support flags the camera's mode database is seeded with, answered in-process.
 *
 * <p>The camera does not build its mode bar from the config on every start: it writes the modes into
 * {@code databases/mode_data.db} once (ModeDataSQLiteOpenHelper) and only queries that table
 * afterwards. Each row is written behind a {@code CameraConfig} flag — {@code gr} needs
 * {@code com.oplus.gr.mode.support}, {@code retroCamera} needs
 * {@code com.oplus.feature.retro.camera.support}, {@code xpan} needs
 * {@code com.oplus.feature.xpan.mode.support}.
 *
 * <p>None of those three exist in this device's {@code oplus_camera_config} (the xpan one exists and
 * is 0). {@code CameraConfig.getConfigBooleanValue} reads
 * {@code CameraUnitUtils.getVendorTagConfig(key)} — the config file itself — so an absent key can
 * never answer true, and the override SharedPreferences this module writes are not consulted on that
 * path either. That is why adding the modes to {@code camera_unit_config} made
 * {@code hasMode()} pass while the camera still showed nothing: the row was never written.
 *
 * <p>So the answer is given here instead, for this process only. No file is replaced; with the module
 * switched off the flags read exactly as the firmware ships them.
 */
public final class ModeGates {
    private static final String TAG = "ModeGates";

    /** Flags that gate a mode row; forced on so the row exists after the database is re-seeded. */
    private static final String[] FORCED = {
        "com.oplus.gr.mode.support",
        "com.oplus.feature.retro.camera.support",
        "com.oplus.feature.xpan.mode.support",
    };

    private ModeGates() {}

    public static void installCamera(ClassLoader classLoader) {
        Method getter = null;
        try {
            getter = Symbols.get().method("CameraConfig.flag");
        } catch (Throwable error) {
            Log.e(TAG, "mode support flags unavailable", error);
            return;
        }
        // Logged before the hook goes in, so a report says whether the flag was the blocker at all.
        for (String key : FORCED) {
            boolean before = false;
            try {
                before = (Boolean) getter.invoke(null, key);
            } catch (Throwable error) {
                Log.e(TAG, key + " unreadable", error);
            }
            Log.i(TAG, key + " was " + before + ", answering true from now on");
        }
        try {
            XposedBridge.hookMethod(getter, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Object key = param.args[0];
                    if (!(key instanceof String)) return;
                    for (String forced : FORCED) {
                        if (forced.equals(key)) {
                            param.setResult(Boolean.TRUE);
                            return;
                        }
                    }
                }
            });
        } catch (Throwable error) {
            Log.e(TAG, "mode support flag hook failed", error);
        }
    }
}
