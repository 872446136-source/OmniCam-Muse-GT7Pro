package local.jiege.hook.xpan;

import android.app.Application;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.cv1;
import defpackage.pp1;
import defpackage.q51;
import defpackage.t2;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;
import local.jiege.hook.xpan.XpanLivePhoto;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class XpanLivePhoto {
    private static final String ASSEMBLER = "com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkAssembler";
    private static final String EXTENDER = "com.oplus.ocs.camera.OplusFileExtenderHelper";
    private static final long KEEP_MS = 86400000;
    private static final int MATCH_EDGE = 512;
    private static final double MATCH_ERROR = 8.0d;
    private static final long MATCH_TIME_MS = 10000;
    private static final String MODE = "xpan";
    private static final String PLAIN = ".plain";
    private static final long PLAIN_AFTER_MS = 5000;
    private static final long REQUEST_MS = 15000;
    private static final String TAG = "XpanLive";
    private static final String WATERMARK_PARAMS = "watermark.master.params";
    private static final long WRITE_WAIT_MS = 20000;
    private static volatile long composedFor;
    private static long largestArea;
    private static long largestFor;
    private static volatile long requestedAt;
    private static volatile long requestedWall;
    private static final Pattern DATE = Pattern.compile("mDate: (\\d+)");
    private static final ThreadLocal<Probe> PROBE = new ThreadLocal<>();
    private static final Set<String> IN_PROGRESS = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(new cv1());

    /* JADX INFO: renamed from: local.jiege.hook.xpan.XpanLivePhoto$1, reason: invalid class name */
    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public class AnonymousClass1 extends XC_MethodHook {
        final /* synthetic */ ClassLoader val$loader;

        public AnonymousClass1(ClassLoader classLoader) {
            this.val$loader = classLoader;
        }

        public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            final long jElapsedRealtime = SystemClock.elapsedRealtime();
            final long jCurrentTimeMillis = System.currentTimeMillis();
            XpanLivePhoto.requestedAt = jElapsedRealtime;
            XpanLivePhoto.requestedWall = jCurrentTimeMillis;
            ScheduledExecutorService scheduledExecutorService = XpanLivePhoto.WORKER;
            final ClassLoader classLoader = this.val$loader;
            scheduledExecutorService.schedule(new Runnable() { // from class: local.jiege.hook.xpan.b
                @Override // java.lang.Runnable
                public final void run() {
                    XpanLivePhoto.p(classLoader, jElapsedRealtime, jCurrentTimeMillis);
                }
            }, XpanLivePhoto.PLAIN_AFTER_MS, TimeUnit.MILLISECONDS);
        }
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Clean {
        final Rect area;
        final byte[] jpeg;
        final byte[] source;

        public Clean(byte[] bArr, Rect rect, byte[] bArr2) {
            this.jpeg = bArr;
            this.area = rect;
            this.source = bArr2;
        }
    }

    private XpanLivePhoto() {
    }

    private static Application app(ClassLoader classLoader) {
        return (Application) XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", classLoader), "currentApplication", new Object[0]);
    }

    private static void attach(Application application, ClassLoader classLoader, File file, long j) throws JSONException, InterruptedException, IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        long jElapsedRealtime = SystemClock.elapsedRealtime() + WRITE_WAIT_MS;
        while (true) {
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (options.outWidth > 0 || SystemClock.elapsedRealtime() > jElapsedRealtime) {
                break;
            } else {
                Thread.sleep(250L);
            }
        }
        int i = options.outWidth;
        if (i <= 0) {
            q51.c(file, "unreadable ");
            return;
        }
        Clean cleanCleanPhoto = cleanPhoto(application, file, i, options.outHeight, j);
        if (cleanCleanPhoto == null) {
            return;
        }
        File file2 = new File(application.getCacheDir(), "xpan_live");
        if (!file2.isDirectory() && !file2.mkdirs()) {
            q51.c(file2, "no cache dir ");
            return;
        }
        File fileCreateTempFile = File.createTempFile("silent_", ".mp4", file2);
        File fileCreateTempFile2 = File.createTempFile("live_", ".mp4", file2);
        File file3 = new File(file.getParentFile(), ".live_" + file.getName());
        try {
            XpanDevelopingVideo.Clip clipEncode = XpanDevelopingVideo.encode(file, options.outWidth, options.outHeight, cleanCleanPhoto.jpeg, cleanCleanPhoto.area, XpanDevelopingVideo.orientation(file), fileCreateTempFile);
            XpanShutterAudio.mux(application, fileCreateTempFile, fileCreateTempFile2, clipEncode.rotation);
            XpanLiveExtension.write(fileCreateTempFile2, clipEncode);
            long jLastModified = file.lastModified();
            Path path = file.toPath();
            Path path2 = file3.toPath();
            StandardCopyOption standardCopyOption = StandardCopyOption.REPLACE_EXISTING;
            Files.copy(path, path2, standardCopyOption);
            byte[] bArr = cleanCleanPhoto.source;
            if (bArr != null) {
                writeSourceImage(application, classLoader, file3, bArr);
            }
            writeVideoDisplayRect(application, classLoader, file3, clipEncode.display);
            Object objCallStaticMethod = XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.oplus.gallery.olive_editor.OLiveCreator", classLoader), "create", new Object[]{application, file3.getAbsolutePath()});
            if (objCallStaticMethod == null) {
                throw new IllegalStateException("OLiveCreator rejected " + file3);
            }
            FileInputStream fileInputStream = new FileInputStream(fileCreateTempFile2);
            try {
                int iIntValue = ((Integer) XposedHelpers.callMethod(objCallStaticMethod, "setVideoData", new Object[]{fileInputStream, Long.valueOf(clipEncode.coverUs), "video/mp4", "oplus"})).intValue();
                fileInputStream.close();
                if (iIntValue == 6) {
                    Log.i(TAG, "already live: " + file);
                    Files.deleteIfExists(file3.toPath());
                    Files.deleteIfExists(fileCreateTempFile.toPath());
                    Files.deleteIfExists(fileCreateTempFile2.toPath());
                    return;
                }
                if (iIntValue != 0) {
                    throw new IllegalStateException("OLiveCreator.setVideoData returned " + iIntValue);
                }
                Files.move(file3.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, standardCopyOption);
                file.setLastModified(jLastModified);
                MediaScannerConnection.scanFile(application, new String[]{file.getAbsolutePath()}, new String[]{"image/jpeg"}, null);
                Log.i(TAG, "live photo saved: " + file + " (" + options.outWidth + "x" + options.outHeight + "), clip " + clipEncode.width + "x" + clipEncode.height + " " + clipEncode.mime + ", " + fileCreateTempFile2.length() + " bytes");
                Files.deleteIfExists(file3.toPath());
                Files.deleteIfExists(fileCreateTempFile.toPath());
                Files.deleteIfExists(fileCreateTempFile2.toPath());
            } catch (Throwable th) {
                try {
                    fileInputStream.close();
                    throw th;
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                    throw th;
                }
            }
        } catch (Throwable th3) {
            Files.deleteIfExists(file3.toPath());
            Files.deleteIfExists(fileCreateTempFile.toPath());
            Files.deleteIfExists(fileCreateTempFile2.toPath());
            throw th3;
        }
    }

    private static Clean cleanPhoto(Application application, File file, int i, int i2, long j) throws JSONException {
        File[] fileArrListFiles = store(application).listFiles();
        if (fileArrListFiles == null) {
            fileArrListFiles = new File[0];
        }
        long jCurrentTimeMillis = System.currentTimeMillis();
        File file2 = null;
        File file3 = null;
        for (File file4 : fileArrListFiles) {
            String name = file4.getName();
            boolean zEndsWith = name.endsWith(PLAIN);
            if (zEndsWith) {
                try {
                    try {
                        name = name.substring(0, name.length() - 6);
                    } catch (NumberFormatException unused) {
                        if (jCurrentTimeMillis - file4.lastModified() > KEEP_MS) {
                            deleteTree(file4);
                        }
                    }
                } catch (NumberFormatException unused2) {
                }
            }
            long j2 = Long.parseLong(name);
            if (jCurrentTimeMillis - j2 > KEEP_MS) {
                deleteTree(file4);
            } else {
                long jAbs = Math.abs(j2 - j);
                if (jAbs <= MATCH_TIME_MS) {
                    if (zEndsWith) {
                        if (file3 == null || jAbs < Math.abs(stamp(file3) - j)) {
                            file3 = file4;
                        }
                    } else if (file2 == null || jAbs < Math.abs(stamp(file2) - j)) {
                        file2 = file4;
                    }
                }
            }
        }
        if (file2 == null) {
            if (file3 != null) {
                Log.i(TAG, file.getName() + " without watermark, capture " + file3.getName());
                return new Clean(Files.readAllBytes(file.toPath()), new Rect(0, 0, i, i2), null);
            }
            Log.i(TAG, "no capture for " + file.getName() + " taken at " + j);
            return null;
        }
        JSONObject jSONObject = new JSONObject(new String(Files.readAllBytes(new File(file2, "meta.json").toPath()), StandardCharsets.UTF_8));
        int i3 = jSONObject.getInt("outputWidth");
        int i4 = jSONObject.getInt("outputHeight");
        float f = i;
        float f2 = f / i2;
        float f3 = i3;
        if (Math.abs(f2 - (f3 / i4)) > f2 * 0.01f) {
            Log.i(TAG, "capture " + file2.getName() + " is " + i3 + "x" + i4 + ", not " + i + "x" + i2);
            return null;
        }
        File file5 = new File(file2, "clean.jpg");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file5.getAbsolutePath(), options);
        float f4 = f / f3;
        int i5 = jSONObject.getInt("x");
        int i6 = jSONObject.getInt("y");
        Rect rect = new Rect(Math.round(i5 * f4), Math.round(i6 * f4), Math.round((i5 + options.outWidth) * f4), Math.round((i6 + options.outHeight) * f4));
        double dMatchError = matchError(file5, file, rect);
        Log.i(TAG, String.format(Locale.ROOT, "%s from capture %s, clean %dx%d, error %.2f", file.getName(), file2.getName(), Integer.valueOf(options.outWidth), Integer.valueOf(options.outHeight), Double.valueOf(dMatchError)));
        if (dMatchError <= MATCH_ERROR) {
            return new Clean(Files.readAllBytes(file5.toPath()), rect, Files.readAllBytes(new File(file2, "src.jpg").toPath()));
        }
        return null;
    }

    private static void deleteTree(File file) {
        File[] fileArrListFiles = file.listFiles();
        if (fileArrListFiles != null) {
            for (File file2 : fileArrListFiles) {
                deleteTree(file2);
            }
        }
        file.delete();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void install(final ClassLoader classLoader) throws NoSuchMethodException {
        if (Settings.get("xpan_developing_live")) {
            Symbols symbols = Symbols.get();
            XposedBridge.hookMethod(symbols.method("XpanPresenter.requestCaptureOrigin"), new AnonymousClass1(classLoader));
            final Class clsFindClass = XposedHelpers.findClass(ASSEMBLER, classLoader);
            Method method = null;
            for (Method method2 : clsFindClass.getDeclaredMethods()) {
                if ("assemble".equals(method2.getName()) && method2.getParameterTypes().length == 5) {
                    method = method2;
                }
            }
            if (method == null) {
                t2.o("com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkAssembler.assemble");
                return;
            }
            XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanLivePhoto.2
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Probe probe = (Probe) XpanLivePhoto.PROBE.get();
                    if (probe == null) {
                        return;
                    }
                    XpanLivePhoto.PROBE.remove();
                    if (methodHookParam.hasThrowable() || probe.outputWidth <= 0 || probe.photoX < 0) {
                        return;
                    }
                    XpanLivePhoto.save(classLoader, probe, XpanLivePhoto.requestedWall);
                }

                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    long j = XpanLivePhoto.requestedAt;
                    if (j == 0 || SystemClock.elapsedRealtime() - j > XpanLivePhoto.REQUEST_MS) {
                        return;
                    }
                    XpanLivePhoto.composedFor = j;
                    try {
                        Bitmap bitmapOrigin = XpanLivePhoto.origin(clsFindClass, methodHookParam.args[2]);
                        long width = ((long) bitmapOrigin.getWidth()) * ((long) bitmapOrigin.getHeight());
                        synchronized (XpanLivePhoto.class) {
                            try {
                                if (XpanLivePhoto.largestFor != j || XpanLivePhoto.largestArea < width) {
                                    XpanLivePhoto.largestFor = j;
                                    XpanLivePhoto.largestArea = width;
                                    Probe probe = new Probe(0);
                                    probe.clean = bitmapOrigin;
                                    XpanLivePhoto.PROBE.set(probe);
                                }
                            } catch (Throwable th) {
                                throw th;
                            }
                        }
                    } catch (Throwable th2) {
                        Log.e(XpanLivePhoto.TAG, "clean photo unreadable", th2);
                    }
                }
            });
            Class cls = Integer.TYPE;
            XposedBridge.hookMethod(XposedHelpers.findMethodExact(clsFindClass, "hardwareBufferAllocate", new Class[]{cls, cls, cls}), new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanLivePhoto.3
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Probe probe = (Probe) XpanLivePhoto.PROBE.get();
                    if (probe == null || probe.outputWidth > 0) {
                        return;
                    }
                    probe.outputWidth = ((Integer) methodHookParam.args[0]).intValue();
                    probe.outputHeight = ((Integer) methodHookParam.args[1]).intValue();
                }
            });
            Class cls2 = Long.TYPE;
            XposedBridge.hookMethod(XposedHelpers.findMethodExact(clsFindClass, "copyBufferToCoord", new Class[]{cls2, cls2, cls, cls}), new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanLivePhoto.4
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Probe probe = (Probe) XpanLivePhoto.PROBE.get();
                    if (probe == null) {
                        return;
                    }
                    probe.photoX = ((Integer) methodHookParam.args[2]).intValue();
                    probe.photoY = ((Integer) methodHookParam.args[3]).intValue();
                }
            });
            XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanLivePhoto.5
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (methodHookParam.hasThrowable()) {
                        return;
                    }
                    try {
                        XpanLivePhoto.schedule(classLoader, methodHookParam.args[0]);
                    } catch (Throwable th) {
                        Log.e(XpanLivePhoto.TAG, "save callback failed", th);
                    }
                }
            };
            XposedBridge.hookMethod(symbols.method("Storage.updateQuick"), xC_MethodHook);
            XposedBridge.hookMethod(symbols.method("Storage.updateImage"), xC_MethodHook);
            Log.i(TAG, "installed");
        }
    }

    public static void installCamera(ClassLoader classLoader) {
        Log.guard(TAG, "XPAN live photo", new pp1(classLoader, (byte) 3));
    }

    private static boolean isXpanJpeg(Object obj) throws IllegalAccessException {
        boolean z = false;
        boolean z2 = false;
        for (Field field : obj.getClass().getDeclaredFields()) {
            if (field.getType() == String.class && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                Object obj2 = field.get(obj);
                if (MODE.equals(obj2)) {
                    z = true;
                }
                if ("jpeg".equals(obj2)) {
                    z2 = true;
                }
            }
        }
        return z && z2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$save$2(Application application, long j, Probe probe) {
        File file = new File(store(application), Long.toString(j));
        File file2 = new File(store(application), j + ".tmp");
        try {
            deleteTree(file2);
            if (!file2.mkdirs()) {
                throw new IllegalStateException("cannot create " + file2);
            }
            FileOutputStream fileOutputStream = new FileOutputStream(new File(file2, "clean.jpg"));
            try {
                Bitmap bitmap = probe.clean;
                Bitmap.CompressFormat compressFormat = Bitmap.CompressFormat.JPEG;
                if (!bitmap.compress(compressFormat, 97, fileOutputStream)) {
                    throw new IllegalStateException("JPEG encode failed");
                }
                fileOutputStream.close();
                Matrix matrix = new Matrix();
                matrix.postRotate(270.0f);
                Bitmap bitmap2 = probe.clean;
                Bitmap bitmapCreateBitmap = Bitmap.createBitmap(bitmap2, 0, 0, bitmap2.getWidth(), probe.clean.getHeight(), matrix, false);
                File file3 = new File(file2, "src.jpg");
                try {
                    FileOutputStream fileOutputStream2 = new FileOutputStream(file3);
                    try {
                        if (!bitmapCreateBitmap.compress(compressFormat, 95, fileOutputStream2)) {
                            throw new IllegalStateException("JPEG encode failed");
                        }
                        fileOutputStream2.close();
                        bitmapCreateBitmap.recycle();
                        ExifInterface exifInterface = new ExifInterface(file3.getAbsolutePath());
                        exifInterface.setAttribute("Orientation", Integer.toString(6));
                        exifInterface.saveAttributes();
                        Files.write(new File(file2, "meta.json").toPath(), new JSONObject().put("x", probe.photoX).put("y", probe.photoY).put("outputWidth", probe.outputWidth).put("outputHeight", probe.outputHeight).toString().getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
                        deleteTree(file);
                        if (!file2.renameTo(file)) {
                            throw new IllegalStateException("cannot publish " + file);
                        }
                    } catch (Throwable th) {
                        try {
                            fileOutputStream2.close();
                            throw th;
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                            throw th;
                        }
                    }
                } catch (Throwable th3) {
                    bitmapCreateBitmap.recycle();
                    throw th3;
                }
                bitmapCreateBitmap.recycle();
                throw th3;
            } catch (Throwable th4) {
                try {
                    fileOutputStream.close();
                    throw th4;
                } catch (Throwable th5) {
                    th4.addSuppressed(th5);
                    throw th4;
                }
            }
            try {
                Log.e(TAG, "saving capture " + j + " failed", th);
            } finally {
                probe.clean.recycle();
            }
        } catch (Throwable th6) {
            Log.e(TAG, "saving capture " + j + " failed", th6);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$schedule$3(Application application, ClassLoader classLoader, String str, long j) {
        try {
            attach(application, classLoader, new File(str), j);
        } catch (Throwable th) {
            try {
                Log.e(TAG, "XPAN live photo failed: " + str, th);
            } finally {
                IN_PROGRESS.remove(str);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ Thread lambda$static$0(Runnable runnable) {
        Thread thread = new Thread(runnable, TAG);
        thread.setPriority(4);
        return thread;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void markPlain(ClassLoader classLoader, long j, long j2) {
        if (composedFor == j) {
            return;
        }
        try {
            File fileStore = store(app(classLoader));
            if (!fileStore.isDirectory() && !fileStore.mkdirs()) {
                throw new IllegalStateException("cannot create " + fileStore);
            }
            Files.write(new File(fileStore, j2 + PLAIN).toPath(), new byte[0], new OpenOption[0]);
            Log.i(TAG, "capture " + j2 + " without watermark");
        } catch (Throwable th) {
            Log.e(TAG, "plain capture mark failed", th);
        }
    }

    private static double matchError(File file, File file2, Rect rect) {
        Bitmap bitmapSmall = small(file, null);
        Bitmap bitmapCreateScaledBitmap = Bitmap.createScaledBitmap(small(file2, rect), bitmapSmall.getWidth(), bitmapSmall.getHeight(), true);
        int height = bitmapSmall.getHeight() * bitmapSmall.getWidth();
        int[] iArr = new int[height];
        int[] iArr2 = new int[height];
        bitmapSmall.getPixels(iArr, 0, bitmapSmall.getWidth(), 0, 0, bitmapSmall.getWidth(), bitmapSmall.getHeight());
        bitmapCreateScaledBitmap.getPixels(iArr2, 0, bitmapSmall.getWidth(), 0, 0, bitmapSmall.getWidth(), bitmapSmall.getHeight());
        long jAbs = 0;
        for (int i = 0; i < height; i++) {
            for (int i2 = 0; i2 <= 16; i2 += 8) {
                jAbs += (long) Math.abs(((iArr[i] >> i2) & 255) - ((iArr2[i] >> i2) & 255));
            }
        }
        return jAbs / (((double) height) * 3.0d);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Bitmap origin(Class<?> cls, Object obj) {
        Bitmap bitmapCopy;
        if (obj instanceof Bitmap) {
            bitmapCopy = ((Bitmap) obj).copy(Bitmap.Config.ARGB_8888, false);
        } else {
            Bitmap bitmap = (Bitmap) XposedHelpers.callStaticMethod(cls, "hardwareBufferToBitmap8Bit", new Object[]{Long.valueOf(obj instanceof Long ? ((Long) obj).longValue() : Long.parseLong((String) obj))});
            bitmapCopy = bitmap == null ? null : bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        if (bitmapCopy != null) {
            return bitmapCopy;
        }
        q51.c(obj, "origin unreadable: ");
        return null;
    }

    public static /* bridge */ /* synthetic */ void p(ClassLoader classLoader, long j, long j2) {
        markPlain(classLoader, j, j2);
    }

    private static String path(Application application, Object obj) {
        String str = (String) XposedHelpers.getObjectField(obj, "mPath");
        if (str != null) {
            return str;
        }
        for (Field field : obj.getClass().getDeclaredFields()) {
            if (field.getType() == Uri.class) {
                field.setAccessible(true);
                Uri uri = (Uri) field.get(obj);
                if (uri != null) {
                    Cursor cursorQuery = application.getContentResolver().query(uri, new String[]{"_data"}, null, null, null);
                    if (cursorQuery != null) {
                        try {
                            if (cursorQuery.moveToFirst()) {
                                String string = cursorQuery.getString(0);
                                cursorQuery.close();
                                return string;
                            }
                        } catch (Throwable th) {
                            try {
                                cursorQuery.close();
                                throw th;
                            } catch (Throwable th2) {
                                th.addSuppressed(th2);
                                throw th;
                            }
                        }
                    }
                    if (cursorQuery != null) {
                        cursorQuery.close();
                    }
                } else {
                    continue;
                }
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void save(ClassLoader classLoader, final Probe probe, final long j) {
        Log.i(TAG, "capture " + j + ": clean " + probe.clean.getWidth() + "x" + probe.clean.getHeight() + " at " + probe.photoX + "," + probe.photoY + " of " + probe.outputWidth + "x" + probe.outputHeight);
        final Application applicationApp = app(classLoader);
        WORKER.execute(new Runnable() { // from class: local.jiege.hook.xpan.a
            @Override // java.lang.Runnable
            public final void run() {
                XpanLivePhoto.lambda$save$2(applicationApp, j, probe);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void schedule(final ClassLoader classLoader, Object obj) {
        if (obj == null || !isXpanJpeg(obj)) {
            return;
        }
        Matcher matcher = DATE.matcher(obj.toString());
        if (!matcher.find()) {
            q51.c(obj, "no capture time in ");
            return;
        }
        final long j = Long.parseLong(matcher.group(1));
        final Application applicationApp = app(classLoader);
        final String strPath = path(applicationApp, obj);
        if (strPath == null) {
            q51.c(obj, "no file for ");
        } else if (IN_PROGRESS.add(strPath)) {
            WORKER.execute(new Runnable() { // from class: dv1
                @Override // java.lang.Runnable
                public final void run() {
                    XpanLivePhoto.lambda$schedule$3(applicationApp, classLoader, strPath, j);
                }
            });
        }
    }

    private static Bitmap small(File file, Rect rect) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        int i = 1;
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        while (true) {
            int i2 = i * 2;
            if (Math.max(options.outWidth, options.outHeight) / i2 < MATCH_EDGE) {
                break;
            }
            i = i2;
        }
        BitmapFactory.Options options2 = new BitmapFactory.Options();
        options2.inSampleSize = i;
        Bitmap bitmapDecodeFile = BitmapFactory.decodeFile(file.getAbsolutePath(), options2);
        if (bitmapDecodeFile == null) {
            q51.c(file, "cannot decode ");
            return null;
        }
        if (rect == null) {
            return bitmapDecodeFile;
        }
        Rect rect2 = new Rect(rect.left / i, rect.top / i, rect.right / i, rect.bottom / i);
        rect2.intersect(0, 0, bitmapDecodeFile.getWidth(), bitmapDecodeFile.getHeight());
        return Bitmap.createBitmap(bitmapDecodeFile, rect2.left, rect2.top, rect2.width(), rect2.height());
    }

    private static long stamp(File file) {
        return Long.parseLong(file.getName().replace(PLAIN, PresetSelection.STANDARD_KEY));
    }

    private static File store(Application application) {
        return new File(application.getFilesDir(), "jiege/xpan_live");
    }

    private static int videoDisplayRectOffset(byte[] bArr) {
        if (bArr == null) {
            return -1;
        }
        ByteBuffer byteBufferOrder = ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN);
        try {
            short s = byteBufferOrder.getShort(214);
            int iMax = 216;
            for (int i = 0; i < s; i++) {
                int iMax2 = (Math.max(byteBufferOrder.getInt(iMax), 0) * 1545) + 20 + iMax;
                int iMax3 = Math.max(byteBufferOrder.getInt(iMax2), 0) + 4 + iMax2;
                iMax = Math.max(byteBufferOrder.getInt(iMax3), 0) + 4 + iMax3 + 4;
            }
            int i2 = iMax + 16;
            if (iMax + 32 <= bArr.length) {
                return i2;
            }
            return -1;
        } catch (IndexOutOfBoundsException unused) {
        }
    }

    private static void writeSourceImage(Application application, ClassLoader classLoader, File file, byte[] bArr) {
        Object objCallStaticMethod = XposedHelpers.callStaticMethod(XposedHelpers.findClass(EXTENDER, classLoader), "creator", new Object[]{application.getContentResolver(), Uri.fromFile(file)});
        XposedHelpers.callMethod(objCallStaticMethod, "setExtensionData", new Object[]{"src.image", bArr});
        XposedHelpers.callMethod(objCallStaticMethod, "close", new Object[0]);
    }

    private static void writeVideoDisplayRect(Application application, ClassLoader classLoader, File file, Rect rect) {
        Object objCallStaticMethod = XposedHelpers.callStaticMethod(XposedHelpers.findClass(EXTENDER, classLoader), "creator", new Object[]{application.getContentResolver(), Uri.fromFile(file)});
        try {
            byte[] bArr = (byte[]) XposedHelpers.callMethod(objCallStaticMethod, "getExtensionDataByteArray", new Object[]{WATERMARK_PARAMS});
            int iVideoDisplayRectOffset = videoDisplayRectOffset(bArr);
            if (iVideoDisplayRectOffset < 0) {
                XposedHelpers.callMethod(objCallStaticMethod, "close", new Object[0]);
                return;
            }
            ByteBuffer byteBufferOrder = ByteBuffer.wrap(bArr).order(ByteOrder.LITTLE_ENDIAN);
            int i = iVideoDisplayRectOffset + 8;
            if (byteBufferOrder.getFloat(i) > byteBufferOrder.getFloat(iVideoDisplayRectOffset)) {
                int i2 = iVideoDisplayRectOffset + 12;
                int i3 = iVideoDisplayRectOffset + 4;
                if (byteBufferOrder.getFloat(i2) > byteBufferOrder.getFloat(i3)) {
                    byteBufferOrder.putFloat(iVideoDisplayRectOffset, rect.left).putFloat(i3, rect.top).putFloat(i, rect.right).putFloat(i2, rect.bottom);
                    XposedHelpers.callMethod(objCallStaticMethod, "setExtensionData", new Object[]{WATERMARK_PARAMS, bArr});
                    XposedHelpers.callMethod(objCallStaticMethod, "close", new Object[0]);
                    return;
                }
            }
            XposedHelpers.callMethod(objCallStaticMethod, "close", new Object[0]);
        } catch (Throwable th) {
            XposedHelpers.callMethod(objCallStaticMethod, "close", new Object[0]);
            throw th;
        }
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Probe {
        Bitmap clean;
        int outputHeight;
        int outputWidth;
        int photoX;
        int photoY;

        private Probe() {
            this.photoX = -1;
            this.photoY = -1;
        }

        public /* synthetic */ Probe(int i) {
            this();
        }
    }
}
