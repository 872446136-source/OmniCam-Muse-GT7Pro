package local.jiege.hook.common;

import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Stages the per-model config profile into the camera's own data directory.
 *
 * <p>The profile is this device's {@code camera_unit_config} with the modes its firmware ships but
 * never lists added (tilt-shift, XPAN, GR) plus the two the B6 port proved the HAL accepts (movie,
 * high pixel). {@code profile_config.cpp} serves it to the camera process by redirecting reads of
 * {@code /odm/etc/camera/config/<name>} — nothing is mounted and {@code /odm} is never written.
 *
 * <p><b>Where the content comes from (476).</b> 475 shipped a whole {@code camera_unit_config} built
 * off-device from one system build's copy. That is fine until the firmware moves: after an OTA the
 * staged file is still the old table, and because it replaces the file wholesale it silently reverts
 * whatever the update changed — new sizes, new modes, new tuning. The camera app and the system both
 * moved this time, so the file is now built at run time: the device's own copy is the base, and only
 * the keys this module owns are written over it.
 *
 * <p>The live copy is read through {@code /proc/self/root/odm/...}: the native redirect matches on
 * the literal prefix {@code /odm/etc/camera/config/}, so this spelling reaches the real file even
 * though the process is already redirected. If that read fails for any reason — no such file, an
 * unexpected shape, a JSON error — the copy shipped in the APK is served unchanged, which is exactly
 * 475's behaviour.
 *
 * <p>Why from the APK rather than from the module directory: the copy has to end up owned by the
 * camera uid with that app's SELinux context, or the camera cannot read it. A root shell writing
 * into {@code /data/user/0/com.oplus.camera/files} at boot gets neither reliably — {@code restorecon}
 * on an app's private directory strips the per-app categories and locks the app out — and at boot the
 * directory may not even be writable yet. The camera process writing its own assets into its own
 * files directory has none of those problems, and it runs after the device is unlocked by
 * definition.
 *
 * <p>Called before {@link Capabilities} is probed, so {@code hasMode()} sees the profile on the very
 * first camera start after an update. Writing to a temporary name and renaming keeps a camera that
 * starts while the previous copy is being written from reading a half file.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_liveconfig_off} pins the staged file to the copy
 * inside the APK.
 */
public final class ConfigProfile {
    private static final String TAG = "ConfigProfile";
    private static final String ASSET_ROOT = "assets/profile/";

    /**
     * The files a profile may carry. Only {@code camera_unit_config} is shipped: it is plain JSON, so
     * a profile can be built and reviewed off-device. {@code oplus_camera_config} is encrypted with a
     * key derived from the device's own {@code libOplusSecurity.so} and wrapped in a 4-byte envelope
     * whose tail does not survive a change in payload length, so the switch board is answered in
     * {@code ModeGates} instead of being shipped as a file. The name is still listed because
     * {@code profile_config.cpp} serves whatever is staged, and a future build may want it.
     */
    private static final String[] NAMES = {
        "camera_unit_config",
        "oplus_camera_config",
    };

    /** The one file this class knows how to merge; anything else is served as shipped. */
    private static final String UNIT = "camera_unit_config";

    /** The device's own copy, reached past the native redirect. */
    private static final String LIVE = "/proc/self/root/odm/etc/camera/config/camera_unit_config";
    private static final String LIVE_OFF = "/data/adb/omnicam_liveconfig_off";

    /**
     * What this module is responsible for. Everything else in the file — sizes, tuning tables,
     * feature graphs, every mode the firmware itself lists — is taken from the device, so a system
     * update that changes them is carried through instead of being reverted to this module's
     * vintage.
     */
    private static final String[] MANAGED_MODES = {
        "xpan_mode", "gr_mode", "retro_camera_mode", "tilt_shift_mode", "movie_mode",
        "high_pixel_mode",
    };

    /** The one operation code the module overrides on a mode the firmware already had. */
    private static final String MANAGED_OVERRIDE = "master_video_mode";

    /** The modes whose feature table the module adds. */
    private static final String[] MANAGED_FEATURES = {"movie_mode", "high_pixel_mode"};

    /**
     * The use cases the module adds, because the firmware names the mode but not its streams.
     *
     * <ul>
     *   <li>{@code xpan_case} — XPAN's preview and capture streams.</li>
     *   <li>{@code retro_camera_case} — POP. 478's report ends with
     *       {@code RuntimeException: getUseCaseValues, retro_camera_case's usecase_info are not
     *       configured!} raised from {@code RetroCameraMode.createRequestTag} while the next mode
     *       was opening its preview, so the switch after POP left the camera unit with no preview
     *       at all: the mode changes, nothing else does, and every later mode is stuck. The entry
     *       is this device's own {@code sat_photo_case}: POP opens the same {@code rear_sat} stream
     *       as the stock sat photo mode, so every stream name in it is one this HAL already
     *       accepts.</li>
     * </ul>
     */
    private static final String[] MANAGED_USECASES = {"xpan_case", "retro_camera_case"};

    private ConfigProfile() {}

    /** Copies the profile for this model into the camera's files directory; true when it is in place. */
    public static boolean ensure(ClassLoader appClassLoader) {
        String process = currentProcess();
        if (process == null || !process.startsWith("com.oplus.camera")) return false;
        try {
            File dir = new File(Capabilities.PROFILE_DIR);
            boolean staged = false;
            boolean complete = true;
            for (String name : NAMES) {
                byte[] profile = content(appClassLoader, name);
                if (profile == null) {
                    // A profile only ships the files it patches; a missing one falls through to stock.
                    continue;
                }
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    Log.e(TAG, "cannot create " + dir);
                    return false;
                }
                File target = new File(dir, name);
                if (target.length() != profile.length || !sameStart(target, profile)) {
                    File temporary = new File(dir, name + ".tmp");
                    try (OutputStream out = new FileOutputStream(temporary)) {
                        out.write(profile);
                    }
                    if (!temporary.renameTo(target)) {
                        temporary.delete();
                        Log.e(TAG, "cannot publish " + target);
                        complete = false;
                        continue;
                    }
                    staged = true;
                    Log.i(TAG, "profile staged: " + name + ", " + profile.length + " bytes for "
                        + Build.MODEL);
                }
                if (!target.isFile()) complete = false;
            }
            if (!staged) Log.i(TAG, "profile already current for " + Build.MODEL);
            File marker = new File(dir, "enabled");
            if (!marker.isFile()) marker.createNewFile();
            return complete && marker.isFile();
        } catch (Throwable error) {
            Log.e(TAG, "profile staging failed", error);
            return false;
        }
    }

    /**
     * The bytes to serve for one config name: the copy in the APK, with the device's own file merged
     * underneath it when that can be read.
     */
    private static byte[] content(ClassLoader appClassLoader, String name) {
        byte[] shipped = asset(appClassLoader, name);
        if (shipped == null || !UNIT.equals(name) || new File(LIVE_OFF).exists()) return shipped;
        byte[] merged = merge(shipped, readFile(new File(LIVE)));
        return merged != null ? merged : shipped;
    }

    /**
     * The profile the module ships, rebuilt on top of the config the device actually has.
     *
     * <p>The shipped file is this device's own table as it looked when the profile was written, so
     * it carries two kinds of content: the entries this module adds, and a snapshot of everything
     * else. Only the first kind may come from the module after an update — the second has to come
     * from the device, or an OTA's new sizes, new tables and new modes are reverted the moment the
     * camera starts. So the live file is the base, and the module writes over exactly the keys it
     * owns: five added modes, one overridden operation code, two feature tables, the use cases the
     * firmware has no entry for, and the mode lists (in the module's order, but only for modes the
     * device still offers).
     *
     * <p>Null when the live file cannot be read or does not have the shape this needs; the caller
     * then serves the shipped file unchanged.
     */
    private static byte[] merge(byte[] shippedBytes, byte[] liveBytes) {
        if (liveBytes == null) {
            Log.i(TAG, "live config unreadable, the shipped copy is served as is");
            return null;
        }
        try {
            JSONObject shipped = new JSONObject(new String(shippedBytes, "UTF-8"));
            JSONObject live = new JSONObject(new String(liveBytes, "UTF-8"));
            JSONObject shippedOps = shipped.optJSONObject("mode_operation_mode");
            JSONObject liveOps = live.optJSONObject("mode_operation_mode");
            JSONObject shippedFeatures = shipped.optJSONObject("feature_operation_mode");
            JSONObject liveFeatures = live.optJSONObject("feature_operation_mode");
            JSONObject shippedLists = shipped.optJSONObject("mode_type_list");
            JSONObject liveLists = live.optJSONObject("mode_type_list");
            if (shippedOps == null || liveOps == null || shippedFeatures == null
                || liveFeatures == null || shippedLists == null || liveLists == null) {
                Log.i(TAG, "live config has no mode tables, the shipped copy is served as is");
                return null;
            }
            for (String mode : MANAGED_MODES) {
                if (shippedOps.has(mode)) liveOps.put(mode, shippedOps.get(mode));
            }
            if (shippedOps.has(MANAGED_OVERRIDE)) {
                liveOps.put(MANAGED_OVERRIDE, shippedOps.get(MANAGED_OVERRIDE));
            }
            for (String mode : MANAGED_FEATURES) {
                if (shippedFeatures.has(mode)) liveFeatures.put(mode, shippedFeatures.get(mode));
            }
            JSONObject shippedCases = shipped.optJSONObject("usecase_info");
            JSONObject liveCases = live.optJSONObject("usecase_info");
            if (shippedCases != null && liveCases != null) {
                for (String useCase : MANAGED_USECASES) {
                    if (shippedCases.has(useCase)) liveCases.put(useCase, shippedCases.get(useCase));
                }
            }
            for (java.util.Iterator<String> it = shippedLists.keys(); it.hasNext(); ) {
                String group = it.next();
                JSONArray curated = shippedLists.optJSONArray(group);
                if (curated == null) continue;
                liveLists.put(group, mergeList(curated, liveLists.optJSONArray(group)));
            }
            String text = live.toString(2) + "\r\n";
            Log.i(TAG, "live config merged: " + liveBytes.length + " -> " + text.length() + " chars");
            return text.getBytes("UTF-8");
        } catch (Throwable error) {
            Log.e(TAG, "live config merge failed, the shipped copy is served as is", error);
            return null;
        }
    }

    /**
     * The module's order for one camera group: every mode the module lists that the device still
     * offers, in the module's order, then anything the device added since — so an update that drops
     * a mode does not have it resurrected here, and one that adds a mode does not have it hidden.
     */
    private static JSONArray mergeList(JSONArray curated, JSONArray live) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < curated.length(); i++) {
            String mode = curated.optString(i);
            if (mode.length() == 0) continue;
            if (live == null || contains(live, mode) || managed(mode)) out.put(mode);
        }
        if (live != null) {
            for (int i = 0; i < live.length(); i++) {
                String mode = live.optString(i);
                if (mode.length() > 0 && !contains(out, mode)) out.put(mode);
            }
        }
        return out;
    }

    private static boolean contains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i))) return true;
        }
        return false;
    }

    private static boolean managed(String mode) {
        for (String name : MANAGED_MODES) if (name.equals(mode)) return true;
        return false;
    }

    private static byte[] readFile(File file) {
        if (!file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            return out.size() > 0 ? out.toByteArray() : null;
        } catch (Throwable error) {
            return null;
        }
    }

    /**
     * The profile asset for this model, falling back to the model-less one; null when neither exists.
     *
     * Read through the module's own class loader: the camera's loader would look inside the camera
     * APK, where this asset does not exist. LSPosed loads the module from its APK with a
     * PathClassLoader, so assets/ is reachable as a resource; the app's loader is tried as well in
     * case that ever changes.
     */
    private static byte[] asset(ClassLoader appClassLoader, String file) {
        String[] names = {
            ASSET_ROOT + Build.MODEL + "/" + file,
            ASSET_ROOT + file,
        };
        ClassLoader[] loaders = {ConfigProfile.class.getClassLoader(), appClassLoader};
        for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            for (String name : names) {
                byte[] data = read(loader, name);
                if (data != null) {
                    Log.i(TAG, "profile asset " + name + " from " + loader);
                    return data;
                }
            }
        }
        return null;
    }

    private static byte[] read(ClassLoader classLoader, String name) {
        try (InputStream in = classLoader.getResourceAsStream(name)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.size() > 0 ? out.toByteArray() : null;
        } catch (Throwable error) {
            return null;
        }
    }

    /** Cheap identity check: the profile starts with a fixed header, so a length and prefix match will do. */
    private static boolean sameStart(File target, byte[] profile) {
        try (InputStream in = new FileInputStream(target)) {
            byte[] head = new byte[Math.min(64, profile.length)];
            int total = 0;
            while (total < head.length) {
                int read = in.read(head, total, head.length - total);
                if (read <= 0) break;
                total += read;
            }
            for (int i = 0; i < total; i++) if (head[i] != profile[i]) return false;
            return total == head.length;
        } catch (Throwable error) {
            return false;
        }
    }

    /** "/proc/self/cmdline", so this does not depend on hidden framework methods. */
    private static String currentProcess() {
        try (InputStream in = new FileInputStream("/proc/self/cmdline")) {
            byte[] buffer = new byte[256];
            int total = 0;
            int read;
            while (total < buffer.length && (read = in.read(buffer, total, buffer.length - total)) > 0) total += read;
            int end = 0;
            while (end < total && buffer[end] != 0) end++;
            return new String(buffer, 0, end, "UTF-8");
        } catch (Throwable error) {
            return null;
        }
    }
}
