package local.jiege.hook.common;

import defpackage.c3;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class OplusConfig {
    private static final int KEY_BYTES = 32;
    private static final int KEY_OFFSET = 1963;
    private static final int KEY_XOR = 63;
    private static final String LIVE = "/proc/self/root/odm/etc/camera/config/oplus_camera_config";
    private static final String NAME = "oplus_camera_config";
    private static final String OFF = "/data/adb/omnicam_xpanconf_off";
    private static final String TAG = "OplusConfig";
    private static final String[] SECURITY = {"/proc/self/root/odm/lib64/libOplusSecurity.so", "/odm/lib64/libOplusSecurity.so", "/proc/self/root/system/lib64/libOplusSecurity.so"};
    private static final String[][] SET = {new String[]{"com.oplus.xpan.main.picturesize", "7872x2912"}, new String[]{"com.oplus.xpan.wide.picturesize", "7872x2912"}, new String[]{"com.oplus.xpan.tele.picturesize", "7872x2912"}, new String[]{"com.oplus.feature.xpan.mode.support", "1"}};
    private static final String[][] ADD = {new String[]{"com.oplus.xpan.all.camera.support", "Byte", "1", "1"}, new String[]{"com.oplus.xpan.mode.version", "Int32", "1", "3"}};

    private OplusConfig() {
    }

    private static String addEntry(String str, String str2, String str3, String str4, String str5) {
        int iLastIndexOf = str.lastIndexOf(93);
        if (iLastIndexOf < 0) {
            return null;
        }
        while (iLastIndexOf > 0 && Character.isWhitespace(str.charAt(iLastIndexOf - 1))) {
            iLastIndexOf--;
        }
        String strSubstring = str.substring(0, iLastIndexOf);
        String strSubstring2 = str.substring(iLastIndexOf);
        StringBuilder sb = new StringBuilder();
        if (!strSubstring.endsWith(",")) {
            sb.append(',');
        }
        c3.s(sb, "\n\n{\n  \"VendorTag\": \"", str2, "\",\n  \"Type\": \"", str3);
        c3.s(sb, "\",\n  \"Count\": \"", str4, "\",\n  \"Value\": \"", str5);
        sb.append("\"\n}\n");
        return strSubstring + ((Object) sb) + strSubstring2;
    }

    private static byte[] aes(byte[] bArr, byte[] bArr2, boolean z) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(z ? 1 : 2, new SecretKeySpec(bArr, "AES"));
            return cipher.doFinal(bArr2);
        } catch (Throwable th) {
            Log.e(TAG, "AES failed", th);
            return null;
        }
    }

    private static byte[] decrypt(byte[] bArr) {
        byte[] bArrKey;
        byte[] bArrAes;
        int i;
        int length = bArr.length - 8;
        if (length <= 0 || length % 16 != 0 || (bArrKey = key()) == null || (bArrAes = aes(bArrKey, Arrays.copyOfRange(bArr, 4, bArr.length - 4), false)) == null || bArrAes.length == 0 || (i = bArrAes[bArrAes.length - 1] & 255) < 1 || i > 16 || i > bArrAes.length) {
            return null;
        }
        return Arrays.copyOf(bArrAes, bArrAes.length - i);
    }

    private static byte[] encrypt(byte[] bArr, byte[] bArr2) {
        byte[] bArrKey = key();
        if (bArrKey == null) {
            return null;
        }
        int length = 16 - (bArr.length % 16);
        byte[] bArrCopyOf = Arrays.copyOf(bArr, bArr.length + length);
        for (int length2 = bArr.length; length2 < bArrCopyOf.length; length2++) {
            bArrCopyOf[length2] = (byte) length;
        }
        byte[] bArrAes = aes(bArrKey, bArrCopyOf, true);
        if (bArrAes == null) {
            return null;
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(bArrAes.length + 8);
        byteArrayOutputStream.write(bArr2, 0, 4);
        byteArrayOutputStream.write(bArrAes, 0, bArrAes.length);
        byteArrayOutputStream.write(bArr2, bArr2.length - 4, 4);
        return byteArrayOutputStream.toByteArray();
    }

    private static byte[] key() {
        for (String str : SECURITY) {
            byte[] bArr = read(new File(str));
            if (bArr != null && bArr.length >= 1995) {
                byte[] bArr2 = new byte[16];
                for (int i = 0; i < KEY_BYTES; i += 2) {
                    bArr2[i / 2] = (byte) (bArr[i + KEY_OFFSET] ^ 63);
                }
                return bArr2;
            }
        }
        Log.i(TAG, "libOplusSecurity.so not readable, the stock table is served");
        return null;
    }

    private static byte[] patch(byte[] bArr) throws UnsupportedEncodingException {
        String strAddEntry;
        boolean z = bArr.length > 12 && bArr[0] == 1 && bArr[1] == 1 && bArr[bArr.length - 1] == -1;
        byte[] bArrDecrypt = z ? decrypt(bArr) : bArr;
        if (bArrDecrypt == null) {
            Log.i(TAG, "config is an envelope this key does not open, the stock table is served");
            return null;
        }
        String str = new String(bArrDecrypt, "UTF-8");
        int i = 0;
        for (String[] strArr : SET) {
            String value = setValue(str, strArr[0], strArr[1]);
            if (value != null) {
                i++;
                str = value;
            }
        }
        for (String[] strArr2 : ADD) {
            if (!str.contains("\"" + strArr2[0] + "\"") && (strAddEntry = addEntry(str, strArr2[0], strArr2[1], strArr2[2], strArr2[3])) != null) {
                i++;
                str = strAddEntry;
            }
        }
        if (i == 0) {
            Log.i(TAG, "config has no XPAN keys to write, the stock table is served");
            return null;
        }
        byte[] bytes = str.getBytes("UTF-8");
        byte[] bArrEncrypt = z ? encrypt(bytes, bArr) : bytes;
        byte[] bArrDecrypt2 = z ? decrypt(bArrEncrypt) : bArrEncrypt;
        if (bArrDecrypt2 == null || !Arrays.equals(bArrDecrypt2, bytes)) {
            Log.e(TAG, "round trip mismatch, the stock table is served", null);
            return null;
        }
        Log.i(TAG, "XPAN config patched: " + i + " values, " + bArrEncrypt.length + " bytes");
        return bArrEncrypt;
    }

    private static byte[] read(File file) {
        if (file.isFile() && file.length() > 0) {
            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                try {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream((int) file.length());
                    byte[] bArr = new byte[8192];
                    while (true) {
                        int i = fileInputStream.read(bArr);
                        if (i <= 0) {
                            byte[] byteArray = byteArrayOutputStream.toByteArray();
                            fileInputStream.close();
                            return byteArray;
                        }
                        byteArrayOutputStream.write(bArr, 0, i);
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
            }
        }
        return null;
    }

    private static boolean sameStart(File file, byte[] bArr) {
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            try {
                int length = bArr.length;
                byte[] bArr2 = new byte[length];
                int i = 0;
                while (i < length) {
                    int i2 = fileInputStream.read(bArr2, i, length - i);
                    if (i2 <= 0) {
                        fileInputStream.close();
                        return false;
                    }
                    i += i2;
                }
                boolean zEquals = Arrays.equals(bArr2, bArr);
                fileInputStream.close();
                return zEquals;
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

    private static String setValue(String str, String str2, String str3) {
        int iIndexOf;
        int iIndexOf2;
        int iIndexOf3;
        int i;
        int iIndexOf4;
        int iIndexOf5 = str.indexOf("\"" + str2 + "\"");
        if (iIndexOf5 < 0 || (iIndexOf = str.indexOf("\"Value\"", iIndexOf5)) < 0 || iIndexOf - iIndexOf5 > 400 || (iIndexOf2 = str.indexOf(58, iIndexOf)) < 0 || (iIndexOf3 = str.indexOf(34, iIndexOf2)) < 0 || (iIndexOf4 = str.indexOf(34, (i = iIndexOf3 + 1))) < 0 || str.substring(i, iIndexOf4).equals(str3)) {
            return null;
        }
        return str.substring(0, i) + str3 + str.substring(iIndexOf4);
    }

    public static boolean stage(File file) {
        byte[] bArrPatch;
        File file2 = new File(file, NAME);
        if (new File(OFF).exists()) {
            if (file2.isFile() && file2.delete()) {
                Log.i(TAG, "staged config withdrawn (/data/adb/omnicam_xpanconf_off)");
            }
            return false;
        }
        byte[] bArr = read(new File(LIVE));
        if (bArr == null || bArr.length < 64) {
            Log.i(TAG, "live config unreadable, the stock table is served");
            return false;
        }
        try {
            bArrPatch = patch(bArr);
        } catch (Throwable th) {
            Log.e(TAG, "config patch failed, the stock table is served", th);
            bArrPatch = null;
        }
        if (bArrPatch == null) {
            if (file2.isFile() && file2.delete()) {
                Log.i(TAG, "staged config removed, stock served");
            }
            return false;
        }
        if (!file.isDirectory() && !file.mkdirs()) {
            Log.e(TAG, "cannot create " + file);
            return false;
        }
        if (file2.length() == bArrPatch.length && sameStart(file2, bArrPatch)) {
            Log.i(TAG, "staged config already current");
            return true;
        }
        File file3 = new File(file, "oplus_camera_config.tmp");
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file3);
            try {
                fileOutputStream.write(bArrPatch);
                fileOutputStream.close();
                if (file3.renameTo(file2)) {
                    Log.i(TAG, "staged config written: " + bArrPatch.length + " bytes");
                    return true;
                }
                Log.e(TAG, "cannot publish " + file2);
                file3.delete();
                return false;
            } catch (Throwable th2) {
                try {
                    fileOutputStream.close();
                } catch (Throwable th3) {
                    th2.addSuppressed(th3);
                }
                throw th2;
            }
        } catch (Throwable th4) {
            Log.e(TAG, "cannot write " + file3, th4);
            file3.delete();
            return false;
        }
    }
}
