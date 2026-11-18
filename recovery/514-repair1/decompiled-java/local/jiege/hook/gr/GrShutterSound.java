package local.jiege.hook.gr;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.SoundPool;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class GrShutterSound {
    private static final String[] SOUNDS = {"camera_gr_shutter", "camera_gr_shutter_start", "camera_gr_shutter_stop"};
    private static final String TAG = "RicohGrPort";

    private GrShutterSound() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String grSound(Context context, int i) {
        String[] strArr = SOUNDS;
        int length = strArr.length;
        int i2 = 0;
        while (true) {
            if (i2 >= length) {
                return null;
            }
            String str = strArr[i2];
            if (i == context.getResources().getIdentifier(str, "raw", context.getPackageName())) {
                try {
                    AssetFileDescriptor assetFileDescriptorOpenRawResourceFd = context.getResources().openRawResourceFd(i);
                    try {
                        String str2 = assetFileDescriptorOpenRawResourceFd.getLength() <= 0 ? str : null;
                        assetFileDescriptorOpenRawResourceFd.close();
                        return str2;
                    } catch (Throwable th) {
                        if (assetFileDescriptorOpenRawResourceFd != null) {
                            try {
                                assetFileDescriptorOpenRawResourceFd.close();
                            } catch (Throwable th2) {
                                th.addSuppressed(th2);
                            }
                        }
                        throw th;
                    }
                } catch (Throwable unused) {
                    return str;
                }
            }
            i2++;
        }
    }

    public static void install(ClassLoader classLoader) {
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrShutterSound.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object[] objArr = methodHookParam.args;
                Context context = (Context) objArr[0];
                String strGrSound = GrShutterSound.grSound(context, ((Integer) objArr[1]).intValue());
                if (strGrSound == null) {
                    return;
                }
                try {
                    methodHookParam.setResult(Integer.valueOf(((SoundPool) methodHookParam.thisObject).load(GrShutterSound.original(context, strGrSound).getPath(), ((Integer) methodHookParam.args[2]).intValue())));
                } catch (Throwable th) {
                    Log.e(GrShutterSound.TAG, "GR sound " + strGrSound + " unavailable", th);
                }
            }
        };
        Class cls = Integer.TYPE;
        XposedHelpers.findAndHookMethod(SoundPool.class, "load", new Object[]{Context.class, cls, cls, xC_MethodHook});
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code duplicated, block: B:59:0x00b8 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    public static synchronized File original(Context context, String str) {
        File file = new File(context.getCacheDir(), "jiege_" + str + ".ogg");
        if (file.length() > 0) {
            return file;
        }
        ZipFile zipFile = new ZipFile(Symbols.modulePath());
        try {
            ZipEntry entry = zipFile.getEntry("assets/gr_sounds/" + str + ".ogg");
            File file2 = new File(file.getPath() + ".tmp");
            InputStream inputStream = zipFile.getInputStream(entry);
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file2);
                try {
                    byte[] bArr = new byte[8192];
                    while (true) {
                        int i = inputStream.read(bArr);
                        if (i <= 0) {
                            break;
                        }
                        fileOutputStream.write(bArr, 0, i);
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (Throwable th) {
                                th.addSuppressed(th);
                            }
                        }
                        throw th;
                    }
                    fileOutputStream.close();
                    inputStream.close();
                    if (file2.renameTo(file)) {
                        zipFile.close();
                        return file;
                    }
                    throw new IllegalStateException("cache not written: " + file);
                } catch (Throwable th2) {
                    try {
                        fileOutputStream.close();
                    } catch (Throwable th3) {
                        th2.addSuppressed(th3);
                    }
                    throw th2;
                }
            } catch (Throwable th4) {
                if (inputStream != null) {
                    inputStream.close();
                }
                throw th4;
            }
        } catch (Throwable th5) {
            try {
                zipFile.close();
            } catch (Throwable th6) {
                th5.addSuppressed(th6);
            }
            throw th5;
        }
    }
}
