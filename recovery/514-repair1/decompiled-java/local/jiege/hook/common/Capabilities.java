package local.jiege.hook.common;

import android.os.Build;
import defpackage.c3;
import java.io.File;
import java.io.FileInputStream;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class Capabilities {
    public static final String CAMERA_FILES = "/data/user/0/com.oplus.camera/files";
    public static final String DECISION = "/odm/etc/camera/config/oplus_camera_preview_decision_config.json";
    private static final String LAYER_MARKER = "/odm/etc/camera/meishe_lut/JG_FILM_KODAK_GOLD_200.cube.rgb.bin";
    public static final String LMT = "/odm/etc/camera/basictone/lmt";
    public static final String MEISHE_LUT = "/odm/etc/camera/meishe_lut";
    public static final String MSFILTER = "/odm/lib64/libmsnativefilter.so";
    public static final long MSFILTER_BYTES = 10957976;
    public static final String ODM_CAMERA = "/odm/etc/camera";
    public static final String ODM_CONFIG = "/odm/etc/camera/config";
    public static final String PROFILE_DIR = "/data/user/0/com.oplus.camera/files/jiege/profile";
    public static final String RETRO = "/odm/etc/camera/basictone/setting_Retro";
    public static final String TUNING_JSON = "/odm/etc/camera/conf_tuning_params.json";
    public static final String UNIT_CONFIG = "/odm/etc/camera/config/camera_unit_config";
    private static String UNIT_TEXT;
    private static Boolean dataLayer;
    private static volatile String dataRoot;
    private static volatile long dataRootProbeAt;
    private static volatile boolean dataRootProbed;
    private static volatile boolean nativeLibrary;
    private static Boolean plainDecision;
    private static Boolean unitConfig;
    public static final String BASICTONE = "/odm/etc/camera/basictone";
    public static final String CAMERA_BASICTONE = "/data/user/0/com.oplus.camera/files/jiege/basictone";
    private static final String[] BASICTONE_ROOTS = {BASICTONE, CAMERA_BASICTONE, "/data/data/com.oplus.camera/files/jiege/basictone"};

    private Capabilities() {
    }

    public static synchronized boolean dataLayer() {
        Boolean boolValueOf;
        try {
            boolValueOf = dataLayer;
            if (boolValueOf == null) {
                boolValueOf = Boolean.valueOf(readable(LAYER_MARKER));
                dataLayer = boolValueOf;
            }
        } catch (Throwable th) {
            throw th;
        }
        return boolValueOf.booleanValue();
    }

    public static String dataLayerNote() {
        String strDataRoot = dataRoot();
        if (strDataRoot == null) {
            return "相机进程看不到模块数据：/odm/etc/camera 下没有 lmt_gr / pop_port / setting_Retro，说明模块的 system/odm 层没被挂载。确认 KernelSU 里本模块已启用（没有 disable 标记）、magic_mount_rs 元模块已安装，然后重启手机";
        }
        return strDataRoot.equals(BASICTONE) ? "模块数据走 /odm 模块层（magic mount），这是正常状态" : "模块数据走 484 遗留的相机私有目录副本，重启一次就会切回 /odm 模块层";
    }

    public static String dataPath(String str) {
        String strDataRoot = dataRoot();
        if (strDataRoot == null) {
            strDataRoot = BASICTONE;
        }
        return c3.l(new StringBuilder(strDataRoot), "/", str);
    }

    public static synchronized String dataRoot() {
        int i;
        if (dataRoot != null) {
            return dataRoot;
        }
        long jNanoTime = System.nanoTime() / 1000000;
        if (dataRootProbed && jNanoTime - dataRootProbeAt < 2000) {
            return null;
        }
        dataRootProbed = true;
        dataRootProbeAt = jNanoTime;
        String[] strArr = BASICTONE_ROOTS;
        int length = strArr.length;
        while (i < length) {
            String str = strArr[i];
            i = (new File(str, "lmt_gr").isDirectory() || new File(str, "pop_port").isDirectory() || new File(str, "setting_Retro").isDirectory()) ? 0 : i + 1;
            dataRoot = str;
            break;
        }
        return dataRoot;
    }

    public static String describe() {
        String str;
        String strDataRoot = dataRoot();
        StringBuilder sb = new StringBuilder("data=");
        sb.append(dataLayer());
        sb.append(" root=");
        if (strDataRoot == null) {
            str = "none";
        } else {
            str = strDataRoot.equals(BASICTONE) ? "merged" : "files";
        }
        sb.append(str);
        sb.append(" lmt=");
        sb.append(lmt());
        sb.append(" retro=");
        sb.append(retro());
        sb.append(" retroIni=");
        sb.append(retroSettings());
        sb.append(" grSc=");
        sb.append(grTables());
        sb.append(" grScBytes=");
        sb.append(grTableBytes());
        sb.append(" profile=");
        sb.append(profileConfig());
        sb.append(" profileModel=");
        sb.append(Build.MODEL);
        sb.append(" profileBytes=");
        sb.append(profileSize());
        sb.append(" plainDecision=");
        sb.append(plainDecision());
        sb.append(" tuningJson=");
        sb.append(tuningJson());
        sb.append(" native=");
        sb.append(nativeLibrary());
        return sb.toString();
    }

    public static boolean grLmt() {
        return new File(grLmtDir()).isDirectory();
    }

    public static String grLmtDir() {
        return dataPath("lmt_gr");
    }

    public static long grTableBytes() {
        return new File(grLmtDir() + "/SCLut0").length();
    }

    public static int grTables() {
        File[] fileArrListFiles = new File(grLmtDir()).listFiles();
        if (fileArrListFiles == null) {
            return -1;
        }
        int i = 0;
        for (File file : fileArrListFiles) {
            if (file.isFile() && file.getName().startsWith("SCLut")) {
                i++;
            }
        }
        return i;
    }

    public static synchronized boolean hasMode(String str) {
        try {
            String strUnitConfigText = unitConfigText();
            if (strUnitConfigText == null) {
                return false;
            }
            int iIndexOf = strUnitConfigText.indexOf("\"mode_type_list\"");
            if (iIndexOf < 0) {
                return false;
            }
            int iIndexOf2 = strUnitConfigText.indexOf("\"device_info\"", iIndexOf);
            return (iIndexOf2 > iIndexOf ? strUnitConfigText.substring(iIndexOf, iIndexOf2) : strUnitConfigText.substring(iIndexOf)).contains("\"" + str + "\"");
        } catch (Throwable th) {
            throw th;
        }
    }

    public static boolean lmt() {
        return readable(LMT);
    }

    public static String modeLimitDir() {
        return dataPath("mode_limit_watermark");
    }

    public static boolean nativeLibrary() {
        return nativeLibrary;
    }

    public static synchronized boolean plainDecision() {
        Boolean boolValueOf;
        try {
            boolValueOf = plainDecision;
            if (boolValueOf == null) {
                boolValueOf = Boolean.valueOf(startsWithBrace(DECISION));
                plainDecision = boolValueOf;
            }
        } catch (Throwable th) {
            throw th;
        }
        return boolValueOf.booleanValue();
    }

    public static String popRoot() {
        return dataPath("pop_port");
    }

    public static boolean profileConfig() {
        return readable("/data/user/0/com.oplus.camera/files/jiege/profile/enabled");
    }

    public static long profileSize() {
        try {
            return new File("/data/user/0/com.oplus.camera/files/jiege/profile/camera_unit_config").length();
        } catch (Throwable unused) {
            return -1L;
        }
    }

    private static byte[] readAll(String str, int i) {
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(str);
            try {
                byte[] bArr = new byte[i];
                int i2 = 0;
                while (i2 < i) {
                    int i3 = fileInputStream.read(bArr, i2, i - i2);
                    if (i3 <= 0) {
                        break;
                    }
                    i2 += i3;
                }
                if (i2 == i) {
                    try {
                        fileInputStream.close();
                    } catch (Throwable unused) {
                    }
                    return bArr;
                }
                byte[] bArr2 = new byte[i2];
                System.arraycopy(bArr, 0, bArr2, 0, i2);
                try {
                    fileInputStream.close();
                } catch (Throwable unused2) {
                }
                return bArr2;
            } catch (Throwable unused3) {
                if (fileInputStream != null) {
                    try {
                        fileInputStream.close();
                    } catch (Throwable unused4) {
                    }
                }
                return null;
            }
        } catch (Throwable unused5) {
            fileInputStream = null;
        }
    }

    private static boolean readable(String str) {
        try {
            return new File(str).canRead();
        } catch (Throwable unused) {
            return false;
        }
    }

    public static String renderer() {
        File file = new File(MSFILTER);
        if (!file.isFile()) {
            return "missing (/odm/lib64/libmsnativefilter.so)";
        }
        long length = file.length();
        if (length == MSFILTER_BYTES) {
            return "module (" + length + " bytes, has CNvPolaroid)";
        }
        return "stock (" + length + " bytes, no Polaroid/CCD/Portra400 renderer)";
    }

    public static boolean rendererReplaced() {
        File file = new File(MSFILTER);
        return file.isFile() && file.length() == MSFILTER_BYTES;
    }

    public static boolean retro() {
        return readable(retroDir());
    }

    public static String retroDir() {
        return dataPath("setting_Retro");
    }

    public static boolean retroSettings() {
        return readable(retroDir() + "/SimTool.ini");
    }

    public static void setNativeLibrary(boolean z) {
        nativeLibrary = z;
    }

    private static boolean startsWithBrace(String str) {
        byte[] all = readAll(str, 1);
        return all != null && all.length == 1 && all[0] == 123;
    }

    public static boolean tuningJson() {
        return readable(TUNING_JSON);
    }

    private static synchronized String unitConfigText() {
        if (unitConfig == null) {
            unitConfig = Boolean.FALSE;
            String[] strArr = {"/data/user/0/com.oplus.camera/files/jiege/profile/camera_unit_config", UNIT_CONFIG};
            for (int i = 0; i < 2; i++) {
                try {
                    byte[] all = readAll(strArr[i], 98304);
                    if (all != null && all.length > 0 && all[0] == 123) {
                        UNIT_TEXT = new String(all, "UTF-8");
                        unitConfig = Boolean.TRUE;
                        break;
                    }
                } catch (Throwable unused) {
                }
            }
        }
        return unitConfig.booleanValue() ? UNIT_TEXT : null;
    }
}
