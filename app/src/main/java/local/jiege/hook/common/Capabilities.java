package local.jiege.hook.common;

import android.system.Os;
import java.io.File;
import java.io.FileInputStream;

/**
 * What this device can actually do, probed once per process before any feature installs.
 *
 * <p>This module never replaces a system library or an ODM config. That is the whole design rule, and
 * it is what the previous full port got wrong: it bind-mounted Find X9 Ultra's libAlgoInterface.so,
 * libBasicTonePhoto.so and camera configs over the device's own, and on a phone whose SoC, struct
 * layouts and config encryption differ that takes the camera down before the viewfinder ever opens.
 *
 * <p>So every feature is rebuilt on top of the device's own files. Where a feature genuinely needs
 * something this device does not have, the probe below says so and the feature is skipped at install
 * time instead of half-installing.
 *
 * <p>Three groups:
 * <ul>
 *   <li>data layer — the module's LUT / basictone / watermark payload, bind-mounted additively by
 *       post-fs-data.sh. {@link #dataLayer()}.</li>
 *   <li>tuning host — the stock LMT directory the master colour and GR engines serve tables from.
 *       {@link #lmt()}.</li>
 *   <li>config shape — whether the camera's decision config is plain text (patchable, as on X9 Ultra)
 *       or encrypted (as on GT7 Pro / ColorOS 17). {@link #plainDecision()}.</li>
 * </ul>
 */
public final class Capabilities {
    public static final String ODM_CAMERA = "/odm/etc/camera";
    public static final String ODM_CONFIG = ODM_CAMERA + "/config";
    public static final String LMT = ODM_CAMERA + "/basictone/lmt";
    public static final String MEISHE_LUT = ODM_CAMERA + "/meishe_lut";
    public static final String BASICTONE = ODM_CAMERA + "/basictone";
    public static final String RETRO = BASICTONE + "/setting_Retro";
    public static final String DECISION = ODM_CONFIG + "/oplus_camera_preview_decision_config.json";
    public static final String TUNING_JSON = ODM_CAMERA + "/conf_tuning_params.json";
    public static final String UNIT_CONFIG = ODM_CONFIG + "/camera_unit_config";
    /** Config profile staged by service.sh; the native path hooks serve it over the ODM configs. */
    public static final String PROFILE_DIR = "/data/user/0/com.oplus.camera/files/jiege/profile";

    private static Boolean dataLayer;
    private static Boolean plainDecision;
    private static Boolean unitConfig;
    private static volatile boolean nativeLibrary;

    private Capabilities() {}

    /** True once the module's payload is mounted: the LUT tree sits on another device than its parent. */
    public static synchronized boolean dataLayer() {
        if (dataLayer == null) dataLayer = mounted(MEISHE_LUT, ODM_CAMERA);
        return dataLayer;
    }

    /** The stock LMT directory both colour engines read; present on every OPlus camera. */
    public static boolean lmt() {
        return readable(LMT);
    }

    /** POP and GR need the retro basictone the module stages; absent on GT7 Pro's stock config. */
    public static boolean retro() {
        return readable(RETRO);
    }

    /**
     * Whether the decision tree can be patched by rewriting it, which needs plain JSON. X9 Ultra ships
     * plain text; GT7 Pro (ColorOS 17) ships an encrypted blob, so 25MP and the 200MP gate cannot be
     * re-targeted there without the platform key.
     */
    public static synchronized boolean plainDecision() {
        if (plainDecision == null) plainDecision = startsWithBrace(DECISION);
        return plainDecision;
    }

    /** The tuning json natural mode rewrites; MediaTek-only, so absent on a Qualcomm device. */
    public static boolean tuningJson() {
        return readable(TUNING_JSON);
    }

    /**
     * Whether a per-model config profile is staged. The native path hooks serve it to the camera
     * process only, which is how a mode the firmware ships but never lists becomes reachable.
     */
    public static boolean profileConfig() {
        return readable(PROFILE_DIR + "/enabled");
    }

    /** Whether {@code libjiege_native.so} loaded and its hooks installed. */
    public static boolean nativeLibrary() {
        return nativeLibrary;
    }

    public static void setNativeLibrary(boolean loaded) {
        nativeLibrary = loaded;
    }

    /**
     * Whether a mode is declared in this device's {@code mode_type_list}. XPAN and tilt-shift exist as
     * vendor tags on GT7 Pro but are not listed as modes, so their hooks would find nothing to bind to.
     */
    public static synchronized boolean hasMode(String mode) {
        String text = unitConfigText();
        if (text == null) return false;
        int start = text.indexOf("\"mode_type_list\"");
        if (start < 0) return false;
        int end = text.indexOf("\"device_info\"", start);
        String block = end > start ? text.substring(start, end) : text.substring(start);
        return block.contains("\"" + mode + "\"");
    }

    /** One line for the settings app's diagnostics page. */
    public static String describe() {
        return "data=" + dataLayer() + " lmt=" + lmt() + " retro=" + retro()
            + " profile=" + profileConfig() + " profileModel=" + android.os.Build.MODEL
            + " profileBytes=" + profileSize() + " plainDecision=" + plainDecision()
            + " tuningJson=" + tuningJson() + " native=" + nativeLibrary();
    }

    /** Size of the staged profile, so a report shows whether it is the file that was built. */
    public static long profileSize() {
        try {
            return new File(PROFILE_DIR + "/camera_unit_config").length();
        } catch (Throwable error) {
            return -1;
        }
    }

    private static synchronized String unitConfigText() {
        if (unitConfig == null) {
            unitConfig = Boolean.FALSE;
            // The staged profile first: that is the copy the camera process is served through the
            // native path hooks, so it is what the modes will actually be read from.
            for (String path : new String[] {PROFILE_DIR + "/camera_unit_config", UNIT_CONFIG}) {
                try {
                    byte[] raw = readAll(path, 96 * 1024);
                    if (raw != null && raw.length > 0 && raw[0] == '{') {
                        UNIT_TEXT = new String(raw, "UTF-8");
                        unitConfig = Boolean.TRUE;
                        break;
                    }
                } catch (Throwable ignored) {
                    // try the next candidate
                }
            }
        }
        return unitConfig ? UNIT_TEXT : null;
    }

    private static String UNIT_TEXT;

    private static boolean mounted(String path, String parent) {
        try {
            return Os.stat(path).st_dev != Os.stat(parent).st_dev;
        } catch (Throwable error) {
            return false;
        }
    }

    private static boolean readable(String path) {
        try {
            return new File(path).canRead();
        } catch (Throwable error) {
            return false;
        }
    }

    private static boolean startsWithBrace(String path) {
        byte[] raw = readAll(path, 1);
        return raw != null && raw.length == 1 && raw[0] == '{';
    }

    private static byte[] readAll(String path, int limit) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(path);
            byte[] buffer = new byte[limit];
            int total = 0;
            while (total < limit) {
                int read = in.read(buffer, total, limit - total);
                if (read <= 0) break;
                total += read;
            }
            if (total == limit) return buffer;
            byte[] exact = new byte[total];
            System.arraycopy(buffer, 0, exact, 0, total);
            return exact;
        } catch (Throwable error) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
    }
}
