package local.jiege.hook.common;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ModeDataReset {
    private static final String DATABASES = "databases";
    private static final String[] FILES = {"mode_data.db", "mode_data.db-journal", "mode_data.db-wal", "mode_data.db-shm"};
    private static final String MARKER = "mode_db_reset";
    private static final String MARKER_SEPARATOR = ":";
    private static final String VERSION = "499";

    private ModeDataReset() {
    }

    public static boolean ensure(String str, String str2) {
        if (str == null) {
            return false;
        }
        File file = new File(Capabilities.PROFILE_DIR);
        File file2 = new File(file, MARKER);
        String str3 = "499:" + identity(str2);
        if (file2.isFile() && str3.equals(read(file2))) {
            return false;
        }
        File file3 = new File(str, DATABASES);
        boolean z = false;
        boolean z2 = false;
        for (String str4 : FILES) {
            File file4 = new File(file3, str4);
            if (file4.isFile()) {
                if (file4.delete()) {
                    z2 = true;
                } else {
                    Log.e("ModeData", "cannot delete " + file4);
                    z = true;
                }
            }
        }
        if (z) {
            Log.e("ModeData", "the mode database is still on disk, the next start will retry");
            return z2;
        }
        if (!file.isDirectory() && !file.mkdirs()) {
            Log.e("ModeData", "cannot create " + file);
            return z2;
        }
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            try {
                fileOutputStream.write(str3.getBytes("UTF-8"));
                fileOutputStream.close();
            } catch (Throwable th) {
                try {
                    fileOutputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (Throwable th3) {
            Log.e("ModeData", "cannot write " + file2, th3);
        }
        Log.i("ModeData", (z2 ? "mode database dropped, the camera will re-seed it" : "no mode database to drop") + " (marker " + str3 + ")");
        return z2;
    }

    private static String identity(String str) {
        if (str != null && str.length() != 0) {
            try {
                File file = new File(str);
                return file.length() + "-" + file.lastModified();
            } catch (Throwable unused) {
            }
        }
        return "?";
    }

    private static String read(File file) {
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] bArr = new byte[64];
                while (true) {
                    int i = fileInputStream.read(bArr);
                    if (i <= 0) {
                        String strTrim = new String(byteArrayOutputStream.toByteArray(), "UTF-8").trim();
                        fileInputStream.close();
                        return strTrim;
                    }
                    byteArrayOutputStream.write(bArr, 0, i);
                    return null;
                }
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
}
