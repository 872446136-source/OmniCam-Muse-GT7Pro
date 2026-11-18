package local.jiege.hook.mastercolor;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.util.SizeF;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.bp;
import defpackage.c3;
import defpackage.hd;
import defpackage.hm0;
import defpackage.kv1;
import defpackage.s3;
import defpackage.t2;
import defpackage.xk0;
import defpackage.yk;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class MasterColor {
    private static final int CAMERA_SWITCH_MS = 300;
    private static final int CCT_HYSTERESIS = 40;
    private static final long LENS_RETRY_MS = 2000;
    private static final String LIVE = "/data/user/0/com.oplus.camera/files/jiege/mastercolor/live/";
    private static final String MASTER_MODE = "professional";
    private static final int PREVIEW_INTERVAL_MS = 200;
    private static final float PREVIEW_MATRIX_CHANGE = 0.02f;
    private static final String ROOT = "/data/user/0/com.oplus.camera/files/jiege/mastercolor";
    private static final long SETTING_INTERVAL_MS = 200;
    private static final String TAG = "MasterColor";
    private static String candidateCameraId;
    private static long candidateSince;
    private static volatile float exposureEv;
    private static volatile int generation;
    private static volatile boolean inMaster;
    private static String keyIdentity;
    private static volatile Object[] lastLoadArgs;
    private static volatile String loadedDir;
    private static volatile String look;
    private static volatile Object manager;
    private static Request pending;
    private static float[] previewCcm;
    private static int previewCct;
    private static volatile String previewDir;
    private static long previewResultAt;
    private static volatile long settingCheckedAt;
    private static Thread worker;
    private static final CaptureResult.Key<Integer> AWB_CCT = key("org.quic.camera2.statsconfigs.AWBFrameControlCCT", Integer.class);
    private static volatile boolean enabled = true;
    private static final int CCT_STEP = 100;
    private static volatile int tonePercent = CCT_STEP;
    private static volatile int colourPercent = CCT_STEP;
    private static volatile int[] densityPercent = {CCT_STEP, CCT_STEP, CCT_STEP};
    private static volatile int[] paramValues = new int[0];
    private static final Map<String, String> lenses = new HashMap();
    private static final Request CLEAR = new Request(0, 0, null, null, 0.0f, 0, 0, null, null, null);
    private static final Object QUEUE = new Object();
    private static final Map<String, Long> lensFailedAt = new HashMap();
    private static final String[] LENS_NAMES = {"uw14", "main23", "tele70", "tele230"};
    private static final double[] LENS_FOCALS = {14.0d, 23.0d, 70.0d, 230.0d};

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Request {
        final float[] ccm;
        final int cct;
        final int colour;
        final int[] density;
        final float ev;
        final int generation;
        final String lens;
        final String look;
        final int tone;
        final int[] values;

        public Request(int i, int i2, float[] fArr, String str, float f, int i3, int i4, int[] iArr, String str2, int[] iArr2) {
            this.generation = i;
            this.cct = i2;
            this.ccm = fArr;
            this.look = str;
            this.ev = f;
            this.tone = i3;
            this.colour = i4;
            this.density = iArr;
            this.lens = str2;
            this.values = iArr2;
        }
    }

    private MasterColor() {
    }

    private static int clampPercent(int i) {
        return Math.max(0, Math.min(CCT_STEP, i));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized void clearPreview(String str) {
        if (keyIdentity == null && previewDir == null) {
            return;
        }
        Log.i(TAG, str);
        keyIdentity = null;
        if (previewDir != null) {
            previewDir = null;
            submit(CLEAR);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookModeSwitch() {
        XposedBridge.hookMethod(Symbols.get().method("ModeSwitch.switch"), new XC_MethodHook() { // from class: local.jiege.hook.mastercolor.MasterColor.3
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                boolean zEquals;
                String str = (String) methodHookParam.args[1];
                if (str == null || str.isEmpty() || (zEquals = "professional".equals(str)) == MasterColor.inMaster) {
                    return;
                }
                MasterColor.inMaster = zEquals;
                if (zEquals) {
                    return;
                }
                MasterColor.leaveMaster();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookPreviewLut(ClassLoader classLoader) {
        final Symbols symbols = Symbols.get();
        final ThreadLocal threadLocal = new ThreadLocal();
        XposedBridge.hookMethod(symbols.method("Lut3d.load"), new XC_MethodHook() { // from class: local.jiege.hook.mastercolor.MasterColor.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                threadLocal.remove();
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                MasterColor.syncSetting();
                MasterColor.manager = methodHookParam.thisObject;
                boolean zEquals = "professional".equals(methodHookParam.args[2]);
                boolean zEquals2 = "professional".equals(symbols.get("Lut3d.mode", methodHookParam.thisObject));
                MasterColor.inMaster = zEquals;
                if (zEquals) {
                    MasterColor.lastLoadArgs = (Object[]) methodHookParam.args.clone();
                }
                String str = (zEquals && MasterColor.enabled) ? MasterColor.previewDir : null;
                if (zEquals != zEquals2 || (zEquals && !String.valueOf(str).equals(String.valueOf(MasterColor.loadedDir)))) {
                    symbols.set("Lut3d.index", methodHookParam.thisObject, -1);
                }
                if (zEquals) {
                    MasterColor.loadedDir = str;
                }
                threadLocal.set(str);
            }
        });
        XposedBridge.hookAllMethods(XposedHelpers.findClass("com.oplus.ocs.camera.CombineLutHelper", classLoader), "init", new XC_MethodHook() { // from class: local.jiege.hook.mastercolor.MasterColor.2
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                String str = (String) threadLocal.get();
                if (str != null) {
                    methodHookParam.args[0] = str;
                    Log.i(MasterColor.TAG, "preview LMT ".concat(str));
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookResults() {
        XposedBridge.hookAllConstructors(TotalCaptureResult.class, new XC_MethodHook() { // from class: local.jiege.hook.mastercolor.MasterColor.4
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (!MasterColor.inMaster || methodHookParam.hasThrowable()) {
                    return;
                }
                Object obj = methodHookParam.thisObject;
                try {
                    MasterColor.syncSetting();
                    MasterColor.onPreviewResult(obj);
                } catch (Throwable th) {
                    MasterColor.clearPreview("preview: source metadata read failed");
                    Log.e(MasterColor.TAG, "preview result", th);
                }
            }
        });
    }

    public static void installCamera(ClassLoader classLoader) {
        new File(LIVE).mkdirs();
        Log.guard(TAG, "master colour preview LUT", new hd(classLoader, (byte) 12));
        Log.guard(TAG, "master colour preview results", new t2((byte) 21));
        Log.guard(TAG, "master colour mode", new t2((byte) 22));
        Log.i(TAG, "installed");
    }

    private static <T> CaptureResult.Key<T> key(String str, Class<T> cls) {
        return (CaptureResult.Key) XposedHelpers.newInstance(CaptureResult.Key.class, new Class[]{String.class, Class.class}, new Object[]{str, cls});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void leaveMaster() {
        synchronized (QUEUE) {
            pending = null;
        }
        synchronized (MasterColor.class) {
            keyIdentity = null;
            candidateCameraId = null;
            previewResultAt = 0L;
        }
    }

    private static String lens(String str) {
        String str2 = lenses.get(str);
        if (str2 != null) {
            return str2;
        }
        Map<String, Long> map = lensFailedAt;
        Long l = map.get(str);
        if (l != null && System.currentTimeMillis() - l.longValue() < LENS_RETRY_MS) {
            return null;
        }
        String strLookupLens = lookupLens(str);
        if (strLookupLens == null) {
            map.put(str, Long.valueOf(System.currentTimeMillis()));
        }
        return strLookupLens;
    }

    private static String lookupLens(String str) {
        Application applicationCurrentApplication = AndroidAppHelper.currentApplication();
        if (applicationCurrentApplication == null) {
            return null;
        }
        try {
            CameraCharacteristics cameraCharacteristics = ((CameraManager) applicationCurrentApplication.getSystemService("camera")).getCameraCharacteristics(str);
            Integer num = (Integer) cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            float[] fArr = (float[]) cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            SizeF sizeF = (SizeF) cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if (cameraCharacteristics.getPhysicalCameraIds().isEmpty() && num != null && num.intValue() == 1 && fArr != null && fArr.length != 0 && sizeF != null) {
                int i = 0;
                float f = fArr[0];
                if (f > 0.0f) {
                    double dHypot = (((double) f) * 43.2666d) / Math.hypot(sizeF.getWidth(), sizeF.getHeight());
                    double dLog = Math.log(1.25d);
                    int i2 = -1;
                    while (true) {
                        double[] dArr = LENS_FOCALS;
                        if (i >= dArr.length) {
                            break;
                        }
                        double dAbs = Math.abs(Math.log(dHypot / dArr[i]));
                        if (dAbs <= dLog) {
                            i2 = i;
                            dLog = dAbs;
                        }
                        i++;
                    }
                    if (i2 < 0) {
                        return null;
                    }
                    Map<String, String> map = lenses;
                    String[] strArr = LENS_NAMES;
                    map.put(str, strArr[i2]);
                    return strArr[i2];
                }
            }
            return null;
        } catch (Throwable th) {
            Log.e(TAG, "lens " + str, th);
            return null;
        }
    }

    private static float[] matrix(ColorSpaceTransform colorSpaceTransform) {
        if (colorSpaceTransform == null) {
            return null;
        }
        float[] fArr = new float[9];
        for (int i = 0; i < 9; i++) {
            fArr[i] = colorSpaceTransform.getElement(i % 3, i / 3).floatValue();
        }
        return fArr;
    }

    private static float maxDifference(float[] fArr, float[] fArr2) {
        float fMax = 0.0f;
        for (int i = 0; i < fArr.length; i++) {
            fMax = Math.max(fMax, Math.abs(fArr[i] - fArr2[i]));
        }
        return fMax;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized void onPreviewResult(Object obj) {
        try {
            if (enabled && look != null) {
                String str = (String) read(obj, CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                if (str == null) {
                    str = (String) XposedHelpers.callMethod(obj, "getCameraId", new Object[0]);
                }
                if (str == null) {
                    clearPreview("preview: physical camera identity unavailable");
                    return;
                }
                long jCurrentTimeMillis = System.currentTimeMillis();
                String str2 = keyIdentity;
                if (str2 == null || str.equals(str2)) {
                    candidateCameraId = null;
                    if (jCurrentTimeMillis - previewResultAt < SETTING_INTERVAL_MS) {
                        return;
                    }
                } else if (!str.equals(candidateCameraId)) {
                    candidateCameraId = str;
                    candidateSince = jCurrentTimeMillis;
                    return;
                } else if (jCurrentTimeMillis - candidateSince < 300) {
                    return;
                }
                previewResultAt = jCurrentTimeMillis;
                String strLens = lens(str);
                Integer num = (Integer) read(obj, AWB_CCT);
                float[] fArrMatrix = matrix((ColorSpaceTransform) read(obj, CaptureResult.COLOR_CORRECTION_TRANSFORM));
                if (strLens != null && num != null && num.intValue() >= 1500 && num.intValue() <= 20000 && fArrMatrix != null) {
                    for (int i = 0; i < fArrMatrix.length; i++) {
                        fArrMatrix[i] = Math.round(fArrMatrix[i] * 256.0f) / 256.0f;
                    }
                    int iMax = previewCct;
                    if (!str.equals(keyIdentity) || Math.abs(num.intValue() - previewCct) > 90) {
                        iMax = Math.max(1700, Math.min(15000, ((num.intValue() + 50) / CCT_STEP) * CCT_STEP));
                    }
                    int i2 = iMax;
                    if (str.equals(keyIdentity) && i2 == previewCct && maxDifference(fArrMatrix, previewCcm) < PREVIEW_MATRIX_CHANGE) {
                        return;
                    }
                    keyIdentity = str;
                    previewCct = i2;
                    previewCcm = fArrMatrix;
                    Log.i(TAG, "preview key camera=" + str + " look=" + look + " cct=" + i2);
                    submit(new Request(generation, i2, fArrMatrix, look, exposureEv, tonePercent, colourPercent, densityPercent, strLens, paramValues));
                    return;
                }
                clearPreview("preview: incomplete colour metadata for camera ".concat(str));
            }
        } catch (Throwable th) {
            throw th;
        }
    }

    private static float[] parameterFloats(hm0[] hm0VarArr, int[] iArr) {
        float[] fArr = new float[4];
        fArr[0] = Float.NaN;
        fArr[1] = Float.NaN;
        fArr[2] = Float.NaN;
        fArr[3] = Float.NaN;
        for (int i = 0; i < Math.min(4, Math.min(hm0VarArr.length, iArr.length)); i++) {
            fArr[i] = iArr[i] / hm0VarArr[i].d;
        }
        return fArr;
    }

    private static String parameterText(hm0[] hm0VarArr, int[] iArr) {
        StringBuilder sb = new StringBuilder();
        for (float f : parameterFloats(hm0VarArr, iArr)) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Float.isNaN(f) ? "nan" : String.format(Locale.ROOT, "%.2f", Float.valueOf(f)));
        }
        return sb.toString();
    }

    private static void process(Request request) {
        String strWriteKey;
        if (request != CLEAR) {
            strWriteKey = writeKey(request.cct, request.ccm, request.look, request.ev, request.tone, request.colour, request.density, request.lens, request.values);
            if (strWriteKey == null) {
                clearPreview("preview: key write failed");
                return;
            }
            if (superseded()) {
                return;
            }
            new File(strWriteKey, "LMTLut0").exists();
            synchronized (MasterColor.class) {
                try {
                    if (request.generation != generation) {
                        return;
                    }
                    previewDir = strWriteKey;
                    Log.i(TAG, "preview table ".concat(strWriteKey));
                } catch (Throwable th) {
                    throw th;
                }
            }
        } else {
            strWriteKey = null;
        }
        reload(strWriteKey);
    }

    private static <T> T read(Object obj, CaptureResult.Key<T> key) {
        return (T) ((CaptureResult) obj).get(key);
    }

    private static void reload(String str) {
        Object obj = manager;
        Object[] objArr = lastLoadArgs;
        if (obj == null || objArr == null || !inMaster) {
            return;
        }
        if (!enabled) {
            str = null;
        }
        if (String.valueOf(str).equals(String.valueOf(loadedDir))) {
            return;
        }
        Symbols symbols = Symbols.get();
        symbols.set("Lut3d.index", obj, -1);
        symbols.call("Lut3d.load", obj, objArr);
    }

    private static void submit(Request request) {
        Object obj = QUEUE;
        synchronized (obj) {
            try {
                pending = request;
                if (worker == null) {
                    Thread thread = new Thread(new s3((byte) 2), "MasterColorWorker");
                    worker = thread;
                    thread.setDaemon(true);
                    worker.start();
                }
                obj.notifyAll();
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    private static boolean superseded() {
        boolean z;
        synchronized (QUEUE) {
            z = pending != null;
        }
        return z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void syncSetting() {
        long jCurrentTimeMillis = System.currentTimeMillis();
        if (jCurrentTimeMillis - settingCheckedAt < SETTING_INTERVAL_MS) {
            return;
        }
        settingCheckedAt = jCurrentTimeMillis;
        boolean z = Settings.getInt("master_look_mode") == 1;
        int i = Settings.getInt("master_look");
        int i2 = (z && i == 0) ? 1 : i;
        String strW = bp.w(i2, Settings.getInt("master_look_oppo"), Settings.getInt("master_look_lumix"), Settings.getInt("master_look_film"), Settings.getInt("master_look_canon"), Settings.getInt("master_look_darktable"), Settings.getInt("master_look_basic"), Settings.getInt("master_look_print"), Settings.getInt("master_hncs_intent"), Settings.getInt("master_hncs_curve"));
        int iClampPercent = z ? clampPercent(Settings.getInt("master_blend_tone")) : CCT_STEP;
        int iClampPercent2 = z ? clampPercent(Settings.getInt("master_blend_colour")) : CCT_STEP;
        if (iClampPercent == 0 && iClampPercent2 == 0) {
            strW = "oppo";
        }
        String str = strW;
        int i3 = Settings.getInt(yk.F(i2));
        int intOr = Settings.getIntOr(yk.I(i2), Settings.getInt("master_look_exposure"));
        int intOr2 = Settings.getIntOr(yk.i(i2), Settings.getInt("master_align"));
        float fMax = 0.0f;
        if (yk.T(str)) {
            float fMax2 = Math.max(-400, Math.min(400, intOr)) / 100.0f;
            if (kv1.q(intOr2, str) && intOr2 != 0) {
                fMax = ((float[]) xk0.a.get(str))[intOr2 - 1];
            }
            if (i3 == 1) {
                fMax2 = fMax;
            } else if (i3 != 0) {
                fMax2 += fMax;
            }
            fMax = Math.max(-4.0f, Math.min(4.0f, Math.round(fMax2 * 100.0f) / 100.0f));
        }
        int[] iArr = {Math.max(50, Math.min(150, Settings.getInt("master_film_density_c"))), Math.max(50, Math.min(150, Settings.getInt("master_film_density_m"))), Math.max(50, Math.min(150, Settings.getInt("master_film_density_y")))};
        if (i2 != 3 || bp.u(Settings.getInt("master_look_film"))) {
            iArr = new int[]{CCT_STEP, CCT_STEP, CCT_STEP};
        }
        hm0[] hm0VarArrD = bp.D(str);
        int length = hm0VarArrD.length;
        int[] iArr2 = new int[length];
        for (int i4 = 0; i4 < length; i4++) {
            hm0 hm0Var = hm0VarArrD[i4];
            iArr2[i4] = Math.max(hm0Var.e, Math.min(hm0Var.f, Settings.getInt(hm0Var.a)));
        }
        if (str.equals(look) && fMax == exposureEv && iClampPercent == tonePercent && iClampPercent2 == colourPercent && Arrays.equals(iArr, densityPercent) && Arrays.equals(iArr2, paramValues)) {
            return;
        }
        try {
            File file = new File(ROOT, "look.tmp");
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            try {
                fileOutputStream.write((str + "\n" + String.format(Locale.ROOT, "%.2f", Float.valueOf(fMax)) + "\n" + iClampPercent + "\n" + iClampPercent2 + "\n" + iArr[0] + " " + iArr[1] + " " + iArr[2] + "\n" + parameterText(hm0VarArrD, iArr2)).getBytes(StandardCharsets.US_ASCII));
                fileOutputStream.close();
                if (!file.renameTo(new File(ROOT, "look"))) {
                    Log.i(TAG, "look file rename failed");
                }
            } catch (Throwable th) {
                try {
                    fileOutputStream.close();
                    throw th;
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                    throw th;
                }
            }
        } catch (Throwable th3) {
            Log.e(TAG, "look file", th3);
        }
        synchronized (MasterColor.class) {
            look = str;
            exposureEv = fMax;
            tonePercent = iClampPercent;
            colourPercent = iClampPercent2;
            densityPercent = iArr;
            paramValues = iArr2;
            enabled = !"oppo".equals(str);
            keyIdentity = null;
            previewResultAt = 0L;
            previewDir = null;
            generation++;
        }
        StringBuilder sb = new StringBuilder("master colour ");
        sb.append(str);
        sb.append(z ? c3.i(" blend tone ", iClampPercent, "% colour ", iClampPercent2, "%") : PresetSelection.STANDARD_KEY);
        Log.i(TAG, sb.toString());
        submit(CLEAR);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void workerLoop() {
        Request request;
        while (true) {
            synchronized (QUEUE) {
                while (true) {
                    try {
                        request = pending;
                        if (request != null) {
                            break;
                        }
                        try {
                            QUEUE.wait();
                        } catch (InterruptedException unused) {
                            return;
                        }
                    } catch (Throwable th) {
                        throw th;
                    }
                }
                pending = null;
            }
            try {
                process(request);
            } catch (Throwable th2) {
                Log.e(TAG, "preview table", th2);
            }
        }
    }

    private static String writeKey(int i, float[] fArr, String str, float f, int i2, int i3, int[] iArr, String str2, int[] iArr2) {
        float[] fArrParameterFloats = parameterFloats(bp.D(str), iArr2);
        boolean z = i2 < CCT_STEP || i3 < CCT_STEP;
        float f2 = 100.0f;
        float f3 = i2 / 100.0f;
        float f4 = i3 / 100.0f;
        byte[] bytes = str.getBytes(StandardCharsets.US_ASCII);
        int length = fArr.length;
        long jFloatToIntBits = 1469598103934665603L;
        int i4 = 0;
        while (i4 < length) {
            jFloatToIntBits = (((long) Float.floatToIntBits(fArr[i4])) ^ jFloatToIntBits) * 1099511628211L;
            i4++;
            f2 = f2;
        }
        float f5 = f2;
        int i5 = 0;
        for (int length2 = bytes.length; i5 < length2; length2 = length2) {
            jFloatToIntBits = (((long) (bytes[i5] & 255)) ^ jFloatToIntBits) * 1099511628211L;
            i5++;
        }
        if (f != 0.0f) {
            jFloatToIntBits = (((long) Float.floatToIntBits(f)) ^ jFloatToIntBits) * 1099511628211L;
        }
        if (z) {
            jFloatToIntBits = (((((long) Float.floatToIntBits(f3)) ^ jFloatToIntBits) * 1099511628211L) ^ ((long) Float.floatToIntBits(f4))) * 1099511628211L;
        }
        if (!Arrays.equals(iArr, new int[]{CCT_STEP, CCT_STEP, CCT_STEP})) {
            for (int i6 : iArr) {
                jFloatToIntBits = (((long) Float.floatToIntBits(i6 / f5)) ^ jFloatToIntBits) * 1099511628211L;
            }
        }
        byte[] bytes2 = str2.getBytes(StandardCharsets.US_ASCII);
        int i7 = 0;
        for (int length3 = bytes2.length; i7 < length3; length3 = length3) {
            jFloatToIntBits = (((long) (bytes2[i7] & 255)) ^ jFloatToIntBits) * 1099511628211L;
            i7++;
        }
        int i8 = 0;
        for (int length4 = fArrParameterFloats.length; i8 < length4; length4 = length4) {
            float f6 = fArrParameterFloats[i8];
            if (!Float.isNaN(f6)) {
                jFloatToIntBits = (((long) Float.floatToIntBits(f6)) ^ jFloatToIntBits) * 1099511628211L;
            }
            i8++;
        }
        String strConcat = LIVE.concat(String.format(Locale.ROOT, "p%05d_%016x", Integer.valueOf(i), Long.valueOf(jFloatToIntBits)));
        File file = new File(strConcat);
        File file2 = new File(file, "key.bin");
        if (file2.exists()) {
            file2.setLastModified(System.currentTimeMillis());
            return strConcat;
        }
        ByteBuffer byteBufferOrder = ByteBuffer.allocate(bytes.length + 92 + bytes2.length + 16).order(ByteOrder.LITTLE_ENDIAN);
        byteBufferOrder.put("MCOLKEY8".getBytes(StandardCharsets.US_ASCII));
        byteBufferOrder.putInt(2).putInt(i);
        for (float f7 : fArr) {
            byteBufferOrder.putFloat(f7);
        }
        byteBufferOrder.putInt(0).putInt(0);
        byteBufferOrder.putInt(bytes.length).put(bytes);
        byteBufferOrder.putFloat(f);
        byteBufferOrder.putFloat(f3).putFloat(f4);
        for (int i9 : iArr) {
            byteBufferOrder.putFloat(i9 / f5);
        }
        byteBufferOrder.putInt(bytes2.length).put(bytes2);
        for (float f8 : fArrParameterFloats) {
            byteBufferOrder.putFloat(f8);
        }
        try {
            if (!file.isDirectory() && !file.mkdirs()) {
                return null;
            }
            File file3 = new File(file, "key.bin.tmp");
            FileOutputStream fileOutputStream = new FileOutputStream(file3);
            try {
                fileOutputStream.write(byteBufferOrder.array());
                fileOutputStream.close();
                if (file3.renameTo(file2)) {
                    return strConcat;
                }
                return null;
            } catch (Throwable th) {
                try {
                    fileOutputStream.close();
                    throw th;
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                    throw th;
                }
            }
        } catch (Throwable th3) {
            Log.e(TAG, "preview key write", th3);
            return null;
        }
    }
}
