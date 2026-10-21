package local.jiege.hook.common;

import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Stages the per-model config profile into the camera's own data directory, from the APK.
 *
 * <p>The profile is this device's {@code camera_unit_config} with the modes its firmware ships but
 * never lists added (tilt-shift, XPAN, GR). {@code profile_config.cpp} serves it to the camera
 * process by redirecting reads of {@code /odm/etc/camera/config/<name>} — nothing is mounted and
 * {@code /odm} is never written.
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
 */
public final class ConfigProfile {
    private static final String TAG = "ConfigProfile";
    private static final String ASSET_ROOT = "assets/profile/";
    private static final String NAME = "camera_unit_config";

    private ConfigProfile() {}

    /** Copies the profile for this model into the camera's files directory; true when it is in place. */
    public static boolean ensure(ClassLoader appClassLoader) {
        String process = currentProcess();
        if (process == null || !process.startsWith("com.oplus.camera")) return false;
        try {
            byte[] profile = asset(appClassLoader);
            if (profile == null) {
                Log.i(TAG, "no profile asset for model " + Build.MODEL + ", stock config used");
                return false;
            }
            File dir = new File(Capabilities.PROFILE_DIR);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                Log.e(TAG, "cannot create " + dir);
                return false;
            }
            File target = new File(dir, NAME);
            if (target.length() != profile.length || !sameStart(target, profile)) {
                File temporary = new File(dir, NAME + ".tmp");
                try (OutputStream out = new FileOutputStream(temporary)) {
                    out.write(profile);
                }
                if (!temporary.renameTo(target)) {
                    temporary.delete();
                    Log.e(TAG, "cannot publish " + target);
                    return false;
                }
                Log.i(TAG, "profile staged: " + profile.length + " bytes for " + Build.MODEL);
            }
            File marker = new File(dir, "enabled");
            if (!marker.isFile()) marker.createNewFile();
            return marker.isFile() && target.isFile();
        } catch (Throwable error) {
            Log.e(TAG, "profile staging failed", error);
            return false;
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
    private static byte[] asset(ClassLoader appClassLoader) {
        String[] names = {
            ASSET_ROOT + Build.MODEL + "/" + NAME,
            ASSET_ROOT + NAME,
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
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(64 * 1024);
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
        try (InputStream in = new java.io.FileInputStream(target)) {
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
        try (InputStream in = new java.io.FileInputStream("/proc/self/cmdline")) {
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
