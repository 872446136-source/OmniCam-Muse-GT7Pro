package local.jiege.hook.watermark;

import android.content.Context;
import android.hardware.HardwareBuffer;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.bb0;
import defpackage.c3;
import defpackage.hd;
import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ModelSpoof {
    private static final String EXIF = "com.oplus.exif.OplusExifInterface";
    private static final String EXIF_PARAMS = "com.oplus.camera.jni.ExifParams";
    private static final String ICC = "com.oplus.camera.jni.IccProfile";
    private static final String PARAMETER = "com.oplus.ocs.camera.ipuapi.process.watermark.entity.WatermarkProcessParameter";
    private static final String TAG = "ModelSpoof";
    private static volatile String deviceModel;
    private static final Map<Object, Boolean> jpegStreams = Collections.synchronizedMap(new WeakHashMap());
    private static final ThreadLocal<Boolean> patching = new ThreadLocal<>();

    private ModelSpoof() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String deviceModel() {
        String str;
        String str2 = PresetSelection.STANDARD_KEY;
        String str3 = deviceModel;
        if (str3 != null) {
            return str3;
        }
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            String[] strArr = {"ro.vendor.oplus.market.enname", "ro.vendor.oplus.market.name"};
            int i = 0;
            str = PresetSelection.STANDARD_KEY;
            while (i < 2) {
                try {
                    String str4 = (String) cls.getMethod("get", String.class, String.class).invoke(null, strArr[i], PresetSelection.STANDARD_KEY);
                    try {
                        if (!str4.isEmpty()) {
                            str = str4;
                            break;
                        }
                        i++;
                        str = str4;
                    } catch (Throwable unused) {
                        str2 = str4;
                        str = str2;
                    }
                } catch (Throwable unused2) {
                    str2 = str;
                }
            }
        } catch (Throwable unused3) {
        }
        deviceModel = str;
        return str;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookExif(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass(EXIF, classLoader);
        final int staticIntField = XposedHelpers.getStaticIntField(clsFindClass, "TAG_MODEL");
        XposedHelpers.findAndHookMethod(clsFindClass, "buildTag", new Object[]{Integer.TYPE, Object.class, new XC_MethodHook() { // from class: local.jiege.hook.watermark.ModelSpoof.3
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (((Integer) methodHookParam.args[0]).intValue() == staticIntField && Settings.get("model_spoof_watermark") && Settings.get("model_spoof_exif")) {
                    methodHookParam.args[1] = ModelSpoof.model();
                    Log.i(ModelSpoof.TAG, "EXIF Model tag " + methodHookParam.args[1]);
                }
            }
        }});
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.watermark.ModelSpoof.4
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Settings.get("model_spoof_watermark") && Settings.get("model_spoof_exif")) {
                    try {
                        Object objCallMethod = XposedHelpers.callMethod(methodHookParam.thisObject, "buildTag", new Object[]{Integer.valueOf(staticIntField), ModelSpoof.model()});
                        if (objCallMethod != null) {
                            XposedHelpers.callMethod(methodHookParam.thisObject, "setTag", new Object[]{objCallMethod});
                        }
                        StringBuilder sb = new StringBuilder("EXIF write ");
                        sb.append(methodHookParam.method.getName());
                        sb.append(", Model ");
                        sb.append(ModelSpoof.model());
                        sb.append(objCallMethod == null ? " (tag refused)" : PresetSelection.STANDARD_KEY);
                        Log.i(ModelSpoof.TAG, sb.toString());
                    } catch (Throwable th) {
                        Log.e(ModelSpoof.TAG, "EXIF Model before write failed", th);
                    }
                }
            }
        };
        XposedBridge.hookAllMethods(clsFindClass, "writeExif", xC_MethodHook);
        XposedBridge.hookAllMethods(clsFindClass, "rewriteExif", xC_MethodHook);
        XposedBridge.hookAllMethods(clsFindClass, "forceRewriteExif", xC_MethodHook);
        Log.i(TAG, "EXIF hook installed");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookJpegWrite(ClassLoader classLoader) {
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.watermark.ModelSpoof.5
            /* JADX WARN: Code duplicated, block: B:46:0x00a7 A[Catch: all -> 0x0091, TryCatch #0 {all -> 0x0091, blocks: (B:39:0x0087, B:42:0x0093, B:44:0x00a1, B:46:0x00a7, B:50:0x00bb, B:57:0x00d3, B:53:0x00ca), top: B:66:0x0087 }] */
            /* JADX WARN: Code duplicated, block: B:48:0x00b6  */
            /* JADX WARN: Code duplicated, block: B:49:0x00b9  */
            /* JADX WARN: Code duplicated, block: B:52:0x00c7  */
            /* JADX WARN: Code duplicated, block: B:53:0x00ca A[Catch: all -> 0x0091, TryCatch #0 {all -> 0x0091, blocks: (B:39:0x0087, B:42:0x0093, B:44:0x00a1, B:46:0x00a7, B:50:0x00bb, B:57:0x00d3, B:53:0x00ca), top: B:66:0x0087 }] */
            /* JADX WARN: Code duplicated, block: B:55:0x00ce  */
            /* JADX WARN: Code duplicated, block: B:56:0x00d1  */
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                JpegModelPatcher.Result resultPatch;
                String str;
                String str2;
                if (ModelSpoof.patching.get() == null) {
                    Object[] objArr = methodHookParam.args;
                    boolean z = false;
                    Object obj = objArr[0];
                    if (obj instanceof byte[]) {
                        byte[] bArr = (byte[]) obj;
                        int iIntValue = objArr.length == 1 ? 0 : ((Integer) objArr[1]).intValue();
                        Object[] objArr2 = methodHookParam.args;
                        int length = objArr2.length == 1 ? bArr.length : ((Integer) objArr2[2]).intValue();
                        if (length < 64 || iIntValue < 0 || iIntValue + length > bArr.length) {
                            return;
                        }
                        if (bArr[iIntValue] == -1 && bArr[iIntValue + 1] == -40 && bArr[iIntValue + 2] == -1) {
                            z = true;
                        }
                        if ((z || ModelSpoof.jpegStreams.containsKey(methodHookParam.thisObject)) && Settings.get("model_spoof_watermark") && Settings.get("model_spoof_exif")) {
                            ThreadLocal threadLocal = ModelSpoof.patching;
                            Boolean bool = Boolean.TRUE;
                            threadLocal.set(bool);
                            if (z) {
                                try {
                                    ModelSpoof.jpegStreams.put(methodHookParam.thisObject, bool);
                                    resultPatch = JpegModelPatcher.patch(bArr, iIntValue, length, ModelSpoof.deviceModel(), ModelSpoof.model());
                                    if (z || resultPatch.changed()) {
                                        StringBuilder sb = new StringBuilder("JPEG write ");
                                        sb.append(length);
                                        sb.append(" bytes");
                                        if (z) {
                                            str = " (start)";
                                        } else {
                                            str = PresetSelection.STANDARD_KEY;
                                        }
                                        sb.append(str);
                                        sb.append(": Model ");
                                        if (resultPatch.exif) {
                                            str2 = "replaced";
                                        } else if (resultPatch.tooLong) {
                                            str2 = "NOT replaced, name too long";
                                        } else {
                                            str2 = "unchanged";
                                        }
                                        sb.append(str2);
                                        sb.append(", appended copies ");
                                        sb.append(resultPatch.appended);
                                        sb.append(", was ");
                                        sb.append(resultPatch.original);
                                        Log.i(ModelSpoof.TAG, sb.toString());
                                    }
                                } catch (Throwable th) {
                                    try {
                                        Log.e(ModelSpoof.TAG, "JPEG patch failed", th);
                                    } finally {
                                        ModelSpoof.patching.remove();
                                    }
                                }
                            } else {
                                resultPatch = JpegModelPatcher.patch(bArr, iIntValue, length, ModelSpoof.deviceModel(), ModelSpoof.model());
                                if (z) {
                                    StringBuilder sb2 = new StringBuilder("JPEG write ");
                                    sb2.append(length);
                                    sb2.append(" bytes");
                                    if (z) {
                                        str = " (start)";
                                    } else {
                                        str = PresetSelection.STANDARD_KEY;
                                    }
                                    sb2.append(str);
                                    sb2.append(": Model ");
                                    if (resultPatch.exif) {
                                        str2 = "replaced";
                                    } else if (resultPatch.tooLong) {
                                        str2 = "NOT replaced, name too long";
                                    } else {
                                        str2 = "unchanged";
                                    }
                                    sb2.append(str2);
                                    sb2.append(", appended copies ");
                                    sb2.append(resultPatch.appended);
                                    sb2.append(", was ");
                                    sb2.append(resultPatch.original);
                                    Log.i(ModelSpoof.TAG, sb2.toString());
                                } else {
                                    StringBuilder sb3 = new StringBuilder("JPEG write ");
                                    sb3.append(length);
                                    sb3.append(" bytes");
                                    if (z) {
                                        str = " (start)";
                                    } else {
                                        str = PresetSelection.STANDARD_KEY;
                                    }
                                    sb3.append(str);
                                    sb3.append(": Model ");
                                    if (resultPatch.exif) {
                                        str2 = "replaced";
                                    } else if (resultPatch.tooLong) {
                                        str2 = "NOT replaced, name too long";
                                    } else {
                                        str2 = "unchanged";
                                    }
                                    sb3.append(str2);
                                    sb3.append(", appended copies ");
                                    sb3.append(resultPatch.appended);
                                    sb3.append(", was ");
                                    sb3.append(resultPatch.original);
                                    Log.i(ModelSpoof.TAG, sb3.toString());
                                }
                            }
                        }
                    }
                }
            }
        };
        XposedHelpers.findAndHookMethod(FileOutputStream.class, "write", new Object[]{byte[].class, xC_MethodHook});
        Class cls = Integer.TYPE;
        XposedHelpers.findAndHookMethod(FileOutputStream.class, "write", new Object[]{byte[].class, cls, cls, xC_MethodHook});
        XposedHelpers.findAndHookMethod("com.oplus.camera.jni.FormatConverter", classLoader, "writeHardwareBufferToFile", new Object[]{HardwareBuffer.class, cls, Long.TYPE, new XC_MethodHook() { // from class: local.jiege.hook.watermark.ModelSpoof.6
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                ParcelFileDescriptor parcelFileDescriptor = (ParcelFileDescriptor) methodHookParam.getObjectExtra("file");
                if (parcelFileDescriptor == null) {
                    return;
                }
                try {
                    try {
                        if (Boolean.TRUE.equals(methodHookParam.getResult())) {
                            Log.i(ModelSpoof.TAG, "hardware buffer wrote " + methodHookParam.args[2] + " bytes: " + ModelSpoof.patchFile(parcelFileDescriptor.getFileDescriptor()));
                        }
                        parcelFileDescriptor.close();
                    } catch (Throwable th) {
                        try {
                            parcelFileDescriptor.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                        throw th;
                    }
                } catch (Throwable th3) {
                    Log.e(ModelSpoof.TAG, "hardware buffer file patch failed", th3);
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Settings.get("model_spoof_watermark") && Settings.get("model_spoof_exif")) {
                    try {
                        methodHookParam.setObjectExtra("file", ParcelFileDescriptor.fromFd(((Integer) methodHookParam.args[1]).intValue()));
                    } catch (Throwable th) {
                        Log.e(ModelSpoof.TAG, "hardware buffer file unavailable", th);
                    }
                }
            }
        }});
        Log.i(TAG, "JPEG write hooks installed");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookNativeExif(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass(EXIF_PARAMS, classLoader);
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.watermark.ModelSpoof.7
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (methodHookParam.args[4] != null && Settings.get("model_spoof_watermark") && Settings.get("model_spoof_exif")) {
                    XposedHelpers.setObjectField(methodHookParam.args[4], "exifModel", ModelSpoof.model());
                    Log.i(ModelSpoof.TAG, "native JPEG exifModel " + ModelSpoof.model());
                }
            }
        };
        Class cls = Integer.TYPE;
        XposedHelpers.findAndHookMethod(ICC, classLoader, "convertYuv2JpegWithExifParams", new Object[]{byte[].class, cls, cls, cls, clsFindClass, xC_MethodHook});
        Log.i(TAG, "native EXIF hook installed");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookWatermark(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(PARAMETER, classLoader, "covertToWatermarkYuvInfo", new Object[]{Context.class, Boolean.TYPE, new XC_MethodHook() { // from class: local.jiege.hook.watermark.ModelSpoof.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Settings.get("model_spoof_watermark")) {
                    String strModel = ModelSpoof.model();
                    XposedHelpers.setObjectField(methodHookParam.thisObject, "mMarketName", strModel);
                    XposedHelpers.setObjectField(methodHookParam.thisObject, "mEnMarketName", strModel);
                    Log.i(ModelSpoof.TAG, "watermark market name " + strModel);
                }
            }
        }});
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.watermark.ModelSpoof.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Settings.get("model_spoof_watermark")) {
                    methodHookParam.setResult(ModelSpoof.model());
                }
            }
        };
        XposedBridge.hookMethod(Symbols.get().method("MarketName.name"), xC_MethodHook);
        XposedBridge.hookMethod(Symbols.get().method("MarketName.enName"), xC_MethodHook);
        Log.i(TAG, "watermark hook installed");
    }

    public static void installCamera(ClassLoader classLoader) {
        Log.guard(TAG, "watermark model", new hd(classLoader, (byte) 14));
        Log.guard(TAG, "EXIF model", new hd(classLoader, (byte) 15));
        Log.guard(TAG, "native EXIF model", new hd(classLoader, (byte) 16));
        Log.guard(TAG, "JPEG bytes", new hd(classLoader, (byte) 17));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String model() {
        return bb0.x(Settings.getString("model_preset_id"), Settings.getString("model_custom_presets"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String patchFile(FileDescriptor fileDescriptor) throws ErrnoException, InterruptedIOException, EOFException {
        long j = Os.fstat(fileDescriptor).st_size;
        if (j > 268435456) {
            return "too large";
        }
        int i = (int) j;
        byte[] bArr = new byte[i];
        int i2 = 0;
        int i3 = 0;
        while (i3 < i) {
            FileDescriptor fileDescriptor2 = fileDescriptor;
            int iPread = Os.pread(fileDescriptor2, bArr, i3, i - i3, i3);
            if (iPread <= 0) {
                throw new EOFException(c3.h("read ", i3, " of ", i));
            }
            i3 += iPread;
            fileDescriptor = fileDescriptor2;
        }
        FileDescriptor fileDescriptor3 = fileDescriptor;
        byte[] bArr2 = (byte[]) bArr.clone();
        JpegModelPatcher.Result resultPatch = JpegModelPatcher.patch(bArr, 0, i, deviceModel(), model());
        if (!resultPatch.changed()) {
            if (resultPatch.tooLong) {
                return "name too long, not replaced";
            }
            return "nothing to replace, was " + resultPatch.original;
        }
        while (i2 < i) {
            if (bArr[i2] == bArr2[i2]) {
                i2++;
            } else {
                int i4 = i2;
                while (i4 < i && bArr[i4] != bArr2[i4]) {
                    i4++;
                }
                for (int iPwrite = i2; iPwrite < i4; iPwrite += Os.pwrite(fileDescriptor3, bArr, iPwrite, i4 - iPwrite, iPwrite)) {
                }
                i2 = i4;
            }
        }
        StringBuilder sb = new StringBuilder("Model ");
        sb.append(resultPatch.exif ? "replaced" : "unchanged");
        sb.append(", appended copies ");
        sb.append(resultPatch.appended);
        sb.append(", was ");
        sb.append(resultPatch.original);
        return sb.toString();
    }
}
