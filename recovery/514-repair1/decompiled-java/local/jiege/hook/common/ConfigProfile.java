package local.jiege.hook.common;

import android.os.Build;
import defpackage.c3;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ConfigProfile {
    private static final String ASSET_ROOT = "assets/profile/";
    private static final String FEATURES_OFF = "/data/adb/omnicam_featcfg_off";
    private static final String LIVE = "/proc/self/root/odm/etc/camera/config/camera_unit_config";
    private static final String LIVE_OFF = "/data/adb/omnicam_liveconfig_off";
    private static final String MANAGED_OVERRIDE = "master_video_mode";
    private static final String TAG = "ConfigProfile";
    private static final String UNIT = "camera_unit_config";
    private static final String FEATURES = "camera_unit_feature_config.protobuf";
    private static final String[] NAMES = {UNIT, FEATURES, "oplus_camera_config"};
    private static final String[] MANAGED_MODES = {"xpan_mode", "gr_mode", "retro_camera_mode", "tilt_shift_mode", "movie_mode", "high_pixel_mode"};
    private static final String[] MANAGED_FEATURES = {"movie_mode", "high_pixel_mode"};
    private static final String[] MANAGED_USECASES = {"xpan_case", "retro_camera_case"};

    private ConfigProfile() {
    }

    private static Object agree(Object obj, Object obj2) {
        boolean z = obj instanceof JSONObject;
        if (z && (obj2 instanceof JSONObject)) {
            JSONObject jSONObject = (JSONObject) obj2;
            JSONObject jSONObject2 = (JSONObject) obj;
            JSONObject jSONObject3 = new JSONObject();
            try {
                Iterator<String> itKeys = jSONObject.keys();
                while (itKeys.hasNext()) {
                    String next = itKeys.next();
                    jSONObject3.put(next, jSONObject.get(next));
                }
                Iterator<String> itKeys2 = jSONObject2.keys();
                while (itKeys2.hasNext()) {
                    String next2 = itKeys2.next();
                    Object obj3 = jSONObject2.get(next2);
                    if (jSONObject3.has(next2)) {
                        Object objAgree = agree(obj3, jSONObject3.get(next2));
                        if (objAgree == null) {
                            return null;
                        }
                        jSONObject3.put(next2, objAgree);
                    } else {
                        jSONObject3.put(next2, obj3);
                    }
                }
                return jSONObject3;
            } catch (Throwable unused) {
                return null;
            }
        }
        boolean z2 = obj instanceof JSONArray;
        if (z2 && (obj2 instanceof JSONArray)) {
            return obj;
        }
        if (z || (obj2 instanceof JSONObject) || z2 || (obj2 instanceof JSONArray)) {
            return null;
        }
        if ((obj instanceof Number) && !(obj2 instanceof Number)) {
            return null;
        }
        if ((obj instanceof Boolean) && !(obj2 instanceof Boolean)) {
            return null;
        }
        if (!(obj instanceof String) || (obj2 instanceof String)) {
            return obj;
        }
        return null;
    }

    private static byte[] asset(ClassLoader classLoader, String str) {
        String[] strArr = {ASSET_ROOT + Build.MODEL + "/" + str, c3.u(ASSET_ROOT, str)};
        ClassLoader[] classLoaderArr = {ConfigProfile.class.getClassLoader(), classLoader};
        for (int i = 0; i < 2; i++) {
            ClassLoader classLoader2 = classLoaderArr[i];
            if (classLoader2 != null) {
                for (int i2 = 0; i2 < 2; i2++) {
                    String str2 = strArr[i2];
                    byte[] bArr = read(classLoader2, str2);
                    if (bArr != null) {
                        Log.i(TAG, "profile asset " + str2 + " from " + classLoader2);
                        return bArr;
                    }
                }
            }
        }
        return null;
    }

    private static boolean contains(JSONArray jSONArray, String str) {
        for (int i = 0; i < jSONArray.length(); i++) {
            if (str.equals(jSONArray.optString(i))) {
                return true;
            }
        }
        return false;
    }

    private static byte[] content(ClassLoader classLoader, String str) {
        byte[] bArrMerge;
        if (FEATURES.equals(str) && new File(FEATURES_OFF).exists()) {
            return null;
        }
        byte[] bArrAsset = asset(classLoader, str);
        return (bArrAsset == null || !UNIT.equals(str) || new File(LIVE_OFF).exists() || (bArrMerge = merge(bArrAsset, readFile(new File(LIVE)))) == null) ? bArrAsset : bArrMerge;
    }

    private static String currentProcess() {
        try {
            FileInputStream fileInputStream = new FileInputStream("/proc/self/cmdline");
            try {
                byte[] bArr = new byte[256];
                int i = 0;
                while (i < 256) {
                    int i2 = fileInputStream.read(bArr, i, 256 - i);
                    if (i2 <= 0) {
                        break;
                    }
                    i += i2;
                }
                int i3 = 0;
                while (i3 < i && bArr[i3] != 0) {
                    i3++;
                }
                String str = new String(bArr, 0, i3, "UTF-8");
                fileInputStream.close();
                return str;
            } catch (Throwable th) {
                try {
                    fileInputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (Throwable unused) {
            return null;
        }
    }

    /* JADX WARN: Code duplicated, block: B:39:0x00c5 A[Catch: all -> 0x009b, TryCatch #2 {all -> 0x009b, blocks: (B:30:0x0094, B:42:0x0107, B:35:0x009e, B:37:0x00bc, B:39:0x00c5, B:41:0x00df, B:52:0x0120, B:51:0x011d, B:55:0x0125, B:56:0x013b, B:58:0x014b, B:60:0x0150, B:36:0x00b9, B:48:0x0118), top: B:70:0x0094, inners: #1, #3 }] */
    /* JADX WARN: Code duplicated, block: B:41:0x00df A[Catch: all -> 0x009b, TryCatch #2 {all -> 0x009b, blocks: (B:30:0x0094, B:42:0x0107, B:35:0x009e, B:37:0x00bc, B:39:0x00c5, B:41:0x00df, B:52:0x0120, B:51:0x011d, B:55:0x0125, B:56:0x013b, B:58:0x014b, B:60:0x0150, B:36:0x00b9, B:48:0x0118), top: B:70:0x0094, inners: #1, #3 }] */
    /* JADX WARN: Code duplicated, block: B:77:0x010e A[SYNTHETIC] */
    /* JADX WARN: Instruction removed from duplicated block: B:39:0x00c5, please report this as an issue */
    /* JADX WARN: Instruction removed from duplicated block: B:41:0x00df, please report this as an issue */
    public static boolean ensure(ClassLoader classLoader) {
        boolean z;
        String[] strArr;
        File file;
        FileOutputStream fileOutputStream;
        String strCurrentProcess = currentProcess();
        boolean z2 = false;
        if (strCurrentProcess == null || !strCurrentProcess.startsWith("com.oplus.camera")) {
            return false;
        }
        try {
            File file2 = new File(Capabilities.PROFILE_DIR);
            String[] strArr2 = NAMES;
            int length = strArr2.length;
            int i = 0;
            boolean z3 = false;
            boolean z4 = true;
            while (i < length) {
                String str = strArr2[i];
                byte[] bArrContent = content(classLoader, str);
                if (bArrContent != null) {
                    if (!file2.isDirectory() && !file2.mkdirs()) {
                        Log.e(TAG, "cannot create " + file2);
                        return z2;
                    }
                    File file3 = new File(file2, str);
                    z = z2;
                    strArr = strArr2;
                    if (file3.length() == bArrContent.length) {
                        try {
                            if (!sameStart(file3, bArrContent)) {
                                file = new File(file2, str + ".tmp");
                                fileOutputStream = new FileOutputStream(file);
                                try {
                                    fileOutputStream.write(bArrContent);
                                    fileOutputStream.close();
                                    if (file.renameTo(file3)) {
                                        Log.i(TAG, "profile staged: " + str + ", " + bArrContent.length + " bytes for " + Build.MODEL);
                                        z3 = true;
                                        if (!file3.isFile()) {
                                        }
                                    } else {
                                        file.delete();
                                        Log.e(TAG, "cannot publish " + file3);
                                    }
                                    z4 = z;
                                } catch (Throwable th) {
                                    try {
                                        fileOutputStream.close();
                                        throw th;
                                    } catch (Throwable th2) {
                                        th.addSuppressed(th2);
                                        throw th;
                                    }
                                }
                            } else if (!file3.isFile()) {
                                z4 = z;
                            }
                        } catch (Throwable th3) {
                            th = th3;
                        }
                    } else {
                        file = new File(file2, str + ".tmp");
                        fileOutputStream = new FileOutputStream(file);
                        fileOutputStream.write(bArrContent);
                        fileOutputStream.close();
                        if (file.renameTo(file3)) {
                            file.delete();
                            Log.e(TAG, "cannot publish " + file3);
                        } else {
                            Log.i(TAG, "profile staged: " + str + ", " + bArrContent.length + " bytes for " + Build.MODEL);
                            z3 = true;
                            if (!file3.isFile()) {
                            }
                        }
                        z4 = z;
                    }
                    Log.e(TAG, "profile staging failed", th);
                    return z;
                }
                if (FEATURES.equals(str) && new File(FEATURES_OFF).exists() && new File(file2, str).delete()) {
                    Log.i(TAG, "feature table withdrawn, the stock one is served (/data/adb/omnicam_featcfg_off)");
                }
                z = z2;
                strArr = strArr2;
                i++;
                z2 = z;
                strArr2 = strArr;
            }
            boolean z5 = z2;
            if (!z3) {
                Log.i(TAG, "profile already current for " + Build.MODEL);
            }
            OplusConfig.stage(file2);
            File file4 = new File(file2, "enabled");
            if (!file4.isFile()) {
                file4.createNewFile();
            }
            if (z4 && file4.isFile()) {
                return true;
            }
            return z5;
        } catch (Throwable th4) {
            th = th4;
            z = z2;
        }
    }

    private static boolean managed(String str) {
        for (String str2 : MANAGED_MODES) {
            if (str2.equals(str)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] merge(byte[] bArr, byte[] bArr2) {
        if (bArr2 == null) {
            Log.i(TAG, "live config unreadable, the shipped copy is served as is");
            return null;
        }
        try {
            JSONObject jSONObject = new JSONObject(new String(bArr, "UTF-8"));
            JSONObject jSONObject2 = new JSONObject(new String(bArr2, "UTF-8"));
            JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("mode_operation_mode");
            JSONObject jSONObjectOptJSONObject2 = jSONObject2.optJSONObject("mode_operation_mode");
            JSONObject jSONObjectOptJSONObject3 = jSONObject.optJSONObject("feature_operation_mode");
            JSONObject jSONObjectOptJSONObject4 = jSONObject2.optJSONObject("feature_operation_mode");
            JSONObject jSONObjectOptJSONObject5 = jSONObject.optJSONObject("mode_type_list");
            JSONObject jSONObjectOptJSONObject6 = jSONObject2.optJSONObject("mode_type_list");
            try {
                if (jSONObjectOptJSONObject == null || jSONObjectOptJSONObject2 == null || jSONObjectOptJSONObject3 == null || jSONObjectOptJSONObject4 == null || jSONObjectOptJSONObject5 == null || jSONObjectOptJSONObject6 == null) {
                    Log.i(TAG, "live config has no mode tables, the shipped copy is served as is");
                    return null;
                }
                String[] strArr = MANAGED_MODES;
                int length = strArr.length;
                int i = 0;
                while (i < length) {
                    int i2 = i;
                    String str = strArr[i2];
                    if (jSONObjectOptJSONObject.has(str)) {
                        putTyped(jSONObjectOptJSONObject2, str, jSONObjectOptJSONObject.get(str), str);
                    }
                    i = i2 + 1;
                    strArr = strArr;
                }
                if (jSONObjectOptJSONObject.has(MANAGED_OVERRIDE)) {
                    putTyped(jSONObjectOptJSONObject2, MANAGED_OVERRIDE, jSONObjectOptJSONObject.get(MANAGED_OVERRIDE), MANAGED_OVERRIDE);
                }
                for (String str2 : MANAGED_FEATURES) {
                    if (jSONObjectOptJSONObject3.has(str2)) {
                        putTyped(jSONObjectOptJSONObject4, str2, jSONObjectOptJSONObject3.get(str2), str2);
                    }
                }
                JSONObject jSONObjectOptJSONObject7 = jSONObject.optJSONObject("usecase_info");
                JSONObject jSONObjectOptJSONObject8 = jSONObject2.optJSONObject("usecase_info");
                if (jSONObjectOptJSONObject7 != null && jSONObjectOptJSONObject8 != null) {
                    for (String str3 : MANAGED_USECASES) {
                        if (jSONObjectOptJSONObject7.has(str3)) {
                            putTyped(jSONObjectOptJSONObject8, str3, jSONObjectOptJSONObject7.get(str3), str3);
                        }
                    }
                }
                Iterator<String> itKeys = jSONObjectOptJSONObject5.keys();
                while (itKeys.hasNext()) {
                    String next = itKeys.next();
                    JSONArray jSONArrayOptJSONArray = jSONObjectOptJSONObject5.optJSONArray(next);
                    if (jSONArrayOptJSONArray != null) {
                        jSONObjectOptJSONObject6.put(next, mergeList(jSONArrayOptJSONArray, jSONObjectOptJSONObject6.optJSONArray(next)));
                    }
                }
                String str4 = jSONObject2.toString(2) + "\r\n";
                Log.i(TAG, "live config merged: " + bArr2.length + " -> " + str4.length() + " chars");
                return str4.getBytes("UTF-8");
            } catch (Throwable th) {
                th = th;
            }
        } catch (Throwable th2) {
            th = th2;
        }
        Log.e(TAG, "live config merge failed, the shipped copy is served as is", th);
        return null;
    }

    private static JSONArray mergeList(JSONArray jSONArray, JSONArray jSONArray2) {
        JSONArray jSONArray3 = new JSONArray();
        for (int i = 0; i < jSONArray.length(); i++) {
            String strOptString = jSONArray.optString(i);
            if (strOptString.length() != 0 && (jSONArray2 == null || contains(jSONArray2, strOptString) || managed(strOptString))) {
                jSONArray3.put(strOptString);
            }
        }
        if (jSONArray2 != null) {
            for (int i2 = 0; i2 < jSONArray2.length(); i2++) {
                String strOptString2 = jSONArray2.optString(i2);
                if (strOptString2.length() > 0 && !contains(jSONArray3, strOptString2)) {
                    jSONArray3.put(strOptString2);
                }
            }
        }
        return jSONArray3;
    }

    private static void putTyped(JSONObject jSONObject, String str, Object obj, String str2) {
        Object objOpt = jSONObject.opt(str);
        if (objOpt == null) {
            try {
                jSONObject.put(str, obj);
                return;
            } catch (Throwable th) {
                Log.e(TAG, "config: cannot add " + str2, th);
                return;
            }
        }
        Object objAgree = agree(obj, objOpt);
        if (objAgree == null) {
            Log.i(TAG, "config: " + str2 + " left as the device has it, the shapes disagree");
            return;
        }
        try {
            jSONObject.put(str, objAgree);
        } catch (Throwable th2) {
            Log.e(TAG, "config: cannot write " + str2, th2);
        }
    }

    private static byte[] read(ClassLoader classLoader, String str) {
        try {
            InputStream resourceAsStream = classLoader.getResourceAsStream(str);
            if (resourceAsStream == null) {
                if (resourceAsStream != null) {
                    resourceAsStream.close();
                }
                return null;
            }
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(65536);
                byte[] bArr = new byte[8192];
                while (true) {
                    int i = resourceAsStream.read(bArr);
                    if (i <= 0) {
                        break;
                    }
                    byteArrayOutputStream.write(bArr, 0, i);
                    return null;
                }
                byte[] byteArray = byteArrayOutputStream.size() > 0 ? byteArrayOutputStream.toByteArray() : null;
                resourceAsStream.close();
                return byteArray;
            } catch (Throwable th) {
                try {
                    resourceAsStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (Throwable unused) {
            return null;
        }
    }

    private static byte[] readFile(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(65536);
                byte[] bArr = new byte[8192];
                while (true) {
                    int i = fileInputStream.read(bArr);
                    if (i <= 0) {
                        break;
                    }
                    byteArrayOutputStream.write(bArr, 0, i);
                    return null;
                }
                byte[] byteArray = byteArrayOutputStream.size() > 0 ? byteArrayOutputStream.toByteArray() : null;
                fileInputStream.close();
                return byteArray;
            } catch (Throwable th) {
                try {
                    fileInputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (Throwable unused) {
            return null;
        }
    }

    private static boolean sameStart(File file, byte[] bArr) {
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            try {
                int iMin = Math.min(64, bArr.length);
                byte[] bArr2 = new byte[iMin];
                int i = 0;
                while (i < iMin) {
                    int i2 = fileInputStream.read(bArr2, i, iMin - i);
                    if (i2 <= 0) {
                        break;
                    }
                    i += i2;
                }
                for (int i3 = 0; i3 < i; i3++) {
                    if (bArr2[i3] != bArr[i3]) {
                        fileInputStream.close();
                        return false;
                    }
                }
                boolean z = i == iMin;
                fileInputStream.close();
                return z;
            } catch (Throwable th) {
                try {
                    fileInputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (Throwable unused) {
            return false;
        }
    }
}
