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
    /** The merged-tree retro directory. Feature code wants {@link #retroDir()}: this path is only
     *  reachable when the module's mount survived in the camera process. */
    public static final String RETRO = BASICTONE + "/setting_Retro";
    public static final String DECISION = ODM_CONFIG + "/oplus_camera_preview_decision_config.json";
    public static final String TUNING_JSON = ODM_CAMERA + "/conf_tuning_params.json";
    public static final String UNIT_CONFIG = ODM_CONFIG + "/camera_unit_config";
    /** Config profile staged by service.sh; the native path hooks serve it over the ODM configs. */
    public static final String PROFILE_DIR = "/data/user/0/com.oplus.camera/files/jiege/profile";
    /** The camera's own private directory; the second copy of the payload lands under it. */
    public static final String CAMERA_FILES = "/data/user/0/com.oplus.camera/files";
    /** Where the camera-side copy of the basictone payload lives. */
    public static final String CAMERA_BASICTONE = CAMERA_FILES + "/jiege/basictone";

    /**
     * The four directories the POP / GR engines read, best source first.
     *
     * <p>The first entry is the merged tree {@code post-fs-data.sh} bind-mounts, and it is the one
     * every version up to 483 relied on. It is also the one that keeps disappearing: it is a
     * <em>module</em> mount, so KernelSU's per-app 卸载模块 (or Zygisk Next's denylist, which the
     * 10007 ad-block module switches on at each boot) removes it from the camera process alone. The
     * 483 report is exactly that state — the settings app sees the module fine while the camera
     * reports {@code data=false retro=false grSc=-1} — and no amount of work in the module script can
     * survive a mount that is not in the process's namespace.
     *
     * <p>The second entry is the same four directories copied by {@code service.sh} into the
     * camera's own private files. App data is not a module mount, so nothing can hide it from the
     * camera. Same bytes, same module, one extra copy — and it makes the port independent of a
     * toggle the user should not have to find.
     */
    private static final String[] BASICTONE_ROOTS = {
        BASICTONE,
        CAMERA_BASICTONE,
        "/data/data/com.oplus.camera/files/jiege/basictone",
    };

    private static Boolean dataLayer;
    private static Boolean plainDecision;
    private static Boolean unitConfig;
    private static volatile boolean nativeLibrary;
    private static volatile String dataRoot;
    private static volatile boolean dataRootProbed;
    private static volatile long dataRootProbeAt;

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
        return readable(retroDir());
    }

    /**
     * The basictone root actually in use: the merged mount if it is in this process's namespace,
     * otherwise the copy in the camera's private files, otherwise null.
     *
     * <p>Probed before any feature installs and logged with the choice, so a report says which
     * source answered instead of only "data missing". A miss is not cached for the life of the
     * process: service.sh copies the payload after the boot broadcast, and the camera can be started
     * before that finishes, so this re-probes (at most every two seconds) until something answers.
     */
    public static synchronized String dataRoot() {
        if (dataRoot != null) return dataRoot;
        long now = System.nanoTime() / 1000000L;
        if (dataRootProbed && now - dataRootProbeAt < 2000) return null;
        dataRootProbed = true;
        dataRootProbeAt = now;
        for (String root : BASICTONE_ROOTS) {
            if (new File(root, "lmt_gr").isDirectory()
                || new File(root, "pop_port").isDirectory()
                || new File(root, "setting_Retro").isDirectory()) {
                dataRoot = root;
                break;
            }
        }
        return dataRoot;
    }

    /** A named directory under the basictone root in use, or the merged path when none is usable. */
    public static String dataPath(String name) {
        String root = dataRoot();
        return (root == null ? BASICTONE : root) + "/" + name;
    }

    /** {@code setting_Retro} — the retro BasicTone's setting directory. */
    public static String retroDir() {
        return dataPath("setting_Retro");
    }

    /** {@code lmt_gr} — the nine GR tone tables and the two LMT tables. */
    public static String grLmtDir() {
        return dataPath("lmt_gr");
    }

    /** {@code pop_port} — POP's Polaroid watermark resources and UI animations. */
    public static String popRoot() {
        return dataPath("pop_port");
    }

    /** {@code mode_limit_watermark} — the IPU master-watermark styles GR draws. */
    public static String modeLimitDir() {
        return dataPath("mode_limit_watermark");
    }

    /** Whether GR has a table directory at all. */
    public static boolean grLmt() {
        return new File(grLmtDir()).isDirectory();
    }

    /**
     * Whether the retro directory actually carries the settings file the engine opens.
     *
     * <p>{@link #retro()} only says the directory exists, and that was the trap in 481: the module
     * created {@code setting_Retro/} and filled it with upstream's {@code SimTool_Master.ini} and
     * {@code SimTool_Photo_Master.ini}, so the probe reported {@code retro=true} while POP rendered a
     * black viewfinder. This firmware's {@code libBasicTonePhoto.so} has exactly one ini name in it,
     * {@code "%s/SimTool.ini"}, so a directory without that file is a directory with no
     * {@code lut_size} and no {@code switch} block at all.
     */
    public static boolean retroSettings() {
        return readable(retroDir() + "/SimTool.ini");
    }

    /**
     * How many {@code SCLut*} tables GR has staged. BasicTone reads a table it cannot find as a zero
     * table, and a zero table is a black preview and a green frame, so this number is the difference
     * between "the filter works" and "the filter is black" for every style past the first.
     */
    public static int grTables() {
        File[] entries = new File(grLmtDir()).listFiles();
        if (entries == null) return -1;
        int count = 0;
        for (File entry : entries) {
            if (entry.isFile() && entry.getName().startsWith("SCLut")) count++;
        }
        return count;
    }

    /**
     * Bytes in the first GR tone table. The engine takes the grid size from {@code SimTool.ini}
     * ({@code lut_size = 64} here) and then reads a file of that size, so a 32^3 table in a 64^3 slot
     * is a quarter of a table and the rest of the bytes land in the next channel. 64^3 float32 RGB
     * is 3145728; 32^3 is 393216.
     */
    public static long grTableBytes() {
        return new File(grLmtDir() + "/SCLut0").length();
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
        String root = dataRoot();
        return "data=" + dataLayer() + " root=" + (root == null ? "none"
                : root.equals(BASICTONE) ? "merged" : "files")
            + " lmt=" + lmt() + " retro=" + retro()
            + " retroIni=" + retroSettings() + " grSc=" + grTables() + " grScBytes=" + grTableBytes()
            + " profile=" + profileConfig() + " profileModel=" + android.os.Build.MODEL
            + " profileBytes=" + profileSize() + " plainDecision=" + plainDecision()
            + " tuningJson=" + tuningJson() + " native=" + nativeLibrary();
    }

    /**
     * Why the data layer is or is not usable, in words a report can act on. The old wording said
     * "KSU module not mounted" for both a module that never mounted and a module that mounted but is
     * unmounted inside the camera process, and those two need different fixes.
     */
    public static String dataLayerNote() {
        String root = dataRoot();
        if (root == null) {
            return "相机进程看不到模块数据：合并树和私有目录副本都没有。检查 KernelSU 卸载模块 / Zygisk Next 排除列表，"
                + "并确认 /data/adb/modules 下的模块目录里有 merged/basictone_extra";
        }
        if (root.equals(BASICTONE)) {
            return "模块数据走合并树（post-fs-data.sh 挂载）";
        }
        return "模块数据走相机私有目录副本（模块挂载在相机进程里被卸载，service.sh 已补一份）";
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
