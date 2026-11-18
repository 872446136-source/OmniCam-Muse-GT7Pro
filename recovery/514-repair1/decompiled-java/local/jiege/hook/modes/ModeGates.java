package local.jiege.hook.modes;

import android.app.Application;
import android.app.Instrumentation;
import android.util.Size;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.t2;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ModeGates {
    private static final int ASKED_LIMIT = 100;
    private static final String CACHED = "d";
    private static final String CONFIG = "com.oplus.camera.configure.CameraConfig";
    private static final String GETTER = "getConfigBooleanValue";
    private static final String MASTER_VIDEO_LIST = "com.oplus.camera.master.video.feature.list";
    private static final String OFF = "/data/adb/omnicam_gates_off";
    private static final String TAG = "ModeGates";
    private static boolean installed;
    private static final String[] FORCED = {"com.oplus.gr.mode.support", "com.oplus.feature.retro.camera.support", "com.oplus.feature.xpan.mode.support", "com.oplus.feature.professional.mode.support", "com.oplus.support.professional.mode.in.headline", "com.oplus.camera.hasselblad.super.definition.support", "com.oplus.use.hasselblad.style.support", "com.oplus.feature.high.pixel.support", "com.oplus.feature.highpixel.merge.support", "com.oplus.high_pixel.global.ev.support", "com.oplus.support.highpixel.gesture_shutter", "com.oplus.camera.high.pixel.mode.4k.live.support", "com.oplus.feature.movie.mode.support", "com.oplus.feature.movie.mode.V2.support", "com.oplus.feature.movie.customize.video.size.support", "com.oplus.director.filter.support", "com.oplus.feature.master.film.filter.label.enable", "com.oplus.feature.master.hq.raw.support", "com.oplus.feature.master.video.focus.peaking.histogram.oplus.r.support", "com.oplus.master.mode.enable.zsl", "com.oplus.use.real.master.pipeline", "com.oplus.master.video.three.state.stabilization", "com.oplus.feature.master.video.60fps.support", "com.oplus.xpan.legacy.ui.style", "com.oplus.camera.support.custom.hasselblad.watermark", "com.oplus.camera.support.custom.hasselblad.watermark.sellmode.default.open"};
    private static final String[] FORCED_FALSE = {"com.oplus.video.focus.track.30fps.only", "com.oplus.feature.highpixel.merge.not.support.zoom"};
    private static final String[] INT_KEYS = {"com.oplus.log.version.curve", "com.oplus.camera.master.captureparam.version", "com.oplus.camera.highpixel.livephoto.video.max.duration", "com.oplus.camera.hasslblad.livephoto.capture.interval", "com.oplus.xpan.mode.version"};
    private static final int[] INT_VALUES = {2, 1, 3000, 1000, 3};
    private static final String[] STRING_KEYS = {"com.oplus.feature.master.mode.version", "com.oplus.feature.movie.mode.video.size", "com.oplus.xpan.main.picturesize", "com.oplus.xpan.wide.picturesize", "com.oplus.xpan.tele.picturesize"};
    private static final String[] STRING_VALUES = {"2.0", "3840x2160", "7872x2912", "7872x2912", "7872x2912"};
    private static final String[] SIZE_KEYS = {"com.oplus.xpan.main.picturesize", "com.oplus.xpan.wide.picturesize", "com.oplus.xpan.tele.picturesize"};
    private static final String[] SIZE_VALUES = {"7872x2912", "7872x2912", "7872x2912"};
    private static final String[] REGION_KEYS = {"com.oplus.xpan.crop.region.main.portrait", "com.oplus.xpan.crop.region.main.landscape", "com.oplus.xpan.crop.region.wide.portrait", "com.oplus.xpan.crop.region.wide.landscape"};
    private static final int[][] REGION_VALUES = {new int[]{360, 64, 2912, 7872}, new int[]{64, 360, 7872, 2912}, new int[]{360, 64, 2912, 7872}, new int[]{64, 360, 7872, 2912}};
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
    private static final int[] FEATURE_LIST = {1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 0, 0};
    private static final Set<String> ASKED = ConcurrentHashMap.newKeySet();

    private ModeGates() {
    }

    public static void afterApplication(ClassLoader classLoader, final Runnable runnable) {
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.modes.ModeGates.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                runnable.run();
            }
        };
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new Object[]{xC_MethodHook});
        } catch (Throwable th) {
            Log.e(TAG, "application onCreate hook unavailable", th);
        }
        try {
            XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", new Object[]{Application.class, xC_MethodHook});
        } catch (Throwable th2) {
            Log.e(TAG, "callApplicationOnCreate hook unavailable", th2);
        }
    }

    private static Method find(Class<?> cls) throws NoSuchMethodException {
        for (Method method : cls.getDeclaredMethods()) {
            if (GETTER.equals(method.getName())) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1 && parameterTypes[0] == String.class && method.getReturnType() == Boolean.TYPE) {
                    return method;
                }
            }
        }
        t2.o(cls.getName().concat(".getConfigBooleanValue"));
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean forced(String str) {
        for (String str2 : FORCED) {
            if (str2.equals(str)) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean forcedFalse(String str) {
        for (String str2 : FORCED_FALSE) {
            if (str2.equals(str)) {
                return true;
            }
        }
        return false;
    }

    private static void hookCachedGetter(Class<?> cls) {
        Method method;
        Method[] declaredMethods = cls.getDeclaredMethods();
        int length = declaredMethods.length;
        int i = 0;
        while (true) {
            if (i >= length) {
                method = null;
                break;
            }
            method = declaredMethods[i];
            if (CACHED.equals(method.getName())) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 3 && parameterTypes[0] == String.class) {
                    Class<?> cls2 = parameterTypes[1];
                    Class<?> cls3 = Boolean.TYPE;
                    if (cls2 == cls3 && parameterTypes[2] == cls3 && method.getReturnType() == cls3) {
                        break;
                    }
                }
            }
            i++;
        }
        if (method == null) {
            Log.i(TAG, "d not found, only the plain getter is answered");
            return;
        }
        try {
            XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.modes.ModeGates.6
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object obj = methodHookParam.args[0];
                    if (obj instanceof String) {
                        String str = (String) obj;
                        if (ModeGates.forced(str)) {
                            methodHookParam.setResult(Boolean.TRUE);
                            if (ModeGates.SEEN.add(str)) {
                                ModeGates.log(str.concat(" answered true via d"));
                                return;
                            }
                            return;
                        }
                        if (ModeGates.forcedFalse(str)) {
                            methodHookParam.setResult(Boolean.FALSE);
                            if (ModeGates.SEEN.add(str)) {
                                ModeGates.log(str.concat(" answered false via d"));
                            }
                        }
                    }
                }
            });
            Log.i(TAG, "d hooked too");
        } catch (Throwable th) {
            Log.e(TAG, "d hook failed", th);
        }
    }

    private static void hookMasterVideoFeatureList(Class<?> cls) {
        int i = 0;
        for (Method method : intArrayReaders(cls)) {
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.modes.ModeGates.4
                    public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        int iIndexOfRegion;
                        String str;
                        if (methodHookParam.hasThrowable()) {
                            return;
                        }
                        Object obj = methodHookParam.args[0];
                        if ((obj instanceof String) && (iIndexOfRegion = ModeGates.indexOfRegion((String) obj)) >= 0) {
                            int[] iArr = (int[]) methodHookParam.getResult();
                            boolean z = iArr != null && iArr.length == 4 && iArr[2] > 0 && iArr[3] > 0;
                            if (z && Arrays.equals(iArr, ModeGates.REGION_VALUES[iIndexOfRegion])) {
                                if (ModeGates.SEEN.add("region:" + obj)) {
                                    ModeGates.log(obj + " came from the config: " + iArr[0] + "," + iArr[1] + "," + iArr[2] + "," + iArr[3]);
                                    return;
                                }
                                return;
                            }
                            methodHookParam.setResult(ModeGates.REGION_VALUES[iIndexOfRegion].clone());
                            if (ModeGates.SEEN.add("region:" + obj)) {
                                StringBuilder sb = new StringBuilder();
                                sb.append(obj);
                                if (z) {
                                    str = " was " + iArr[0] + "," + iArr[1] + "," + iArr[2] + "," + iArr[3] + ", answered ";
                                } else {
                                    str = " was empty, answered ";
                                }
                                sb.append(str);
                                sb.append(ModeGates.REGION_VALUES[iIndexOfRegion][0]);
                                sb.append(",");
                                sb.append(ModeGates.REGION_VALUES[iIndexOfRegion][1]);
                                sb.append(",");
                                sb.append(ModeGates.REGION_VALUES[iIndexOfRegion][2]);
                                sb.append(",");
                                sb.append(ModeGates.REGION_VALUES[iIndexOfRegion][3]);
                                ModeGates.log(sb.toString());
                            }
                        }
                    }

                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Object obj = methodHookParam.args[0];
                        if ((obj instanceof String) && ModeGates.MASTER_VIDEO_LIST.equals(obj)) {
                            methodHookParam.setResult(ModeGates.FEATURE_LIST.clone());
                            if (ModeGates.SEEN.add(ModeGates.MASTER_VIDEO_LIST)) {
                                ModeGates.log("master video feature list answered, " + ModeGates.FEATURE_LIST.length + " slots");
                            }
                        }
                    }
                });
                i++;
            } catch (Throwable th) {
                Log.e(TAG, "feature reader hook failed on " + method.getName(), th);
            }
        }
        if (i == 0) {
            Log.i(TAG, "no int[] config reader found, the XPAN crop regions are left alone");
            return;
        }
        Log.i(TAG, "int[] config readers hooked: " + i + ", " + REGION_KEYS.length + " XPAN crop regions answered on request");
    }

    private static void hookScalarGetters(Class<?> cls) {
        Class<?> cls2;
        int i = 0;
        for (Method method : cls.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            Class<?> returnType = method.getReturnType();
            int length = parameterTypes.length;
            int i2 = 0;
            boolean z = false;
            while (true) {
                if (i2 >= length) {
                    break;
                }
                if (parameterTypes[i2] == String.class) {
                    z = true;
                }
                i2++;
            }
            if (z && ((returnType == (cls2 = Integer.TYPE) || returnType == String.class) && !method.getName().equals(GETTER) && !method.getName().equals(CACHED) && (returnType != cls2 || parameterTypes.length != 2 || parameterTypes[0] != String.class || parameterTypes[1] != int[].class))) {
                final boolean z2 = returnType == cls2;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.modes.ModeGates.7
                        public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                            Object objKeyOf = ModeGates.keyOf(methodHookParam.args);
                            if (objKeyOf == null) {
                                return;
                            }
                            String str = (String) objKeyOf;
                            if (z2) {
                                int iIndexOfInt = ModeGates.indexOfInt(str);
                                if (iIndexOfInt < 0) {
                                    return;
                                }
                                methodHookParam.setResult(Integer.valueOf(ModeGates.INT_VALUES[iIndexOfInt]));
                                if (ModeGates.SEEN.add(str)) {
                                    ModeGates.log(str + " answered " + ModeGates.INT_VALUES[iIndexOfInt]);
                                    return;
                                }
                                return;
                            }
                            int iIndexOfString = ModeGates.indexOfString(str);
                            if (iIndexOfString < 0) {
                                return;
                            }
                            methodHookParam.setResult(ModeGates.STRING_VALUES[iIndexOfString]);
                            if (ModeGates.SEEN.add(str)) {
                                ModeGates.log(str + " answered " + ModeGates.STRING_VALUES[iIndexOfString]);
                            }
                        }
                    });
                    i++;
                } catch (Throwable th) {
                    Log.e(TAG, "scalar hook failed on " + method.getName(), th);
                }
            }
        }
        Log.i(TAG, "scalar readers hooked: " + i);
    }

    private static void hookSizeGetters(Class<?> cls) {
        int i = 0;
        for (Method method : cls.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length >= 1 && parameterTypes.length <= 2 && parameterTypes[0] == String.class && ((parameterTypes.length != 2 || parameterTypes[1] == Boolean.TYPE) && method.getReturnType() == Size.class)) {
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.modes.ModeGates.5
                        public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                            int iIndexOfSize;
                            Size sizeSizeOf;
                            String str;
                            if (methodHookParam.hasThrowable()) {
                                return;
                            }
                            Object obj = methodHookParam.args[0];
                            if ((obj instanceof String) && (iIndexOfSize = ModeGates.indexOfSize((String) obj)) >= 0 && (sizeSizeOf = ModeGates.sizeOf(ModeGates.SIZE_VALUES[iIndexOfSize])) != null) {
                                Object result = methodHookParam.getResult();
                                if (sizeSizeOf.equals(result)) {
                                    if (ModeGates.SEEN.add("size:" + obj)) {
                                        ModeGates.log(obj + " came from the config: " + result);
                                        return;
                                    }
                                    return;
                                }
                                methodHookParam.setResult(sizeSizeOf);
                                if (ModeGates.SEEN.add("size:" + obj)) {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append(obj);
                                    if (result == null) {
                                        str = " was null, answered ";
                                    } else {
                                        str = " was " + result + ", answered ";
                                    }
                                    sb.append(str);
                                    sb.append(sizeSizeOf);
                                    ModeGates.log(sb.toString());
                                }
                            }
                        }
                    });
                    i++;
                } catch (Throwable th) {
                    Log.e(TAG, "size hook failed on " + method.getName(), th);
                }
            }
        }
        if (i == 0) {
            Log.i(TAG, "no size config reader found, the XPAN size is left alone");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int indexOfInt(String str) {
        int i = 0;
        while (true) {
            String[] strArr = INT_KEYS;
            if (i >= strArr.length) {
                return -1;
            }
            if (strArr[i].equals(str)) {
                return i;
            }
            i++;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int indexOfRegion(String str) {
        int i = 0;
        while (true) {
            String[] strArr = REGION_KEYS;
            if (i >= strArr.length) {
                return -1;
            }
            if (strArr[i].equals(str)) {
                return i;
            }
            i++;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int indexOfSize(String str) {
        int i = 0;
        while (true) {
            String[] strArr = SIZE_KEYS;
            if (i >= strArr.length) {
                return -1;
            }
            if (strArr[i].equals(str)) {
                return i;
            }
            i++;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int indexOfString(String str) {
        int i = 0;
        while (true) {
            String[] strArr = STRING_KEYS;
            if (i >= strArr.length) {
                return -1;
            }
            if (strArr[i].equals(str)) {
                return i;
            }
            i++;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized void install(ClassLoader classLoader) {
        if (installed) {
            return;
        }
        installed = true;
        try {
            Class<?> cls = Class.forName(CONFIG, false, classLoader);
            try {
                XposedBridge.hookMethod(find(cls), new XC_MethodHook() { // from class: local.jiege.hook.modes.ModeGates.3
                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Object obj = methodHookParam.args[0];
                        if (obj instanceof String) {
                            String str = (String) obj;
                            if (ModeGates.forced(str)) {
                                methodHookParam.setResult(Boolean.TRUE);
                                if (ModeGates.SEEN.add(str)) {
                                    ModeGates.log(str.concat(" answered true"));
                                    return;
                                }
                                return;
                            }
                            if (ModeGates.forcedFalse(str)) {
                                methodHookParam.setResult(Boolean.FALSE);
                                if (ModeGates.SEEN.add(str)) {
                                    ModeGates.log(str.concat(" answered false"));
                                    return;
                                }
                                return;
                            }
                            if (ModeGates.ASKED.size() >= ModeGates.ASKED_LIMIT || !ModeGates.ASKED.add(str)) {
                                return;
                            }
                            ModeGates.log("asked: ".concat(str));
                        }
                    }
                });
                Log.i(TAG, "flag getter hooked, " + FORCED.length + " true, " + FORCED_FALSE.length + " false");
            } catch (Throwable th) {
                Log.e(TAG, "flag hook failed", th);
            }
            hookCachedGetter(cls);
            hookMasterVideoFeatureList(cls);
            hookScalarGetters(cls);
            hookSizeGetters(cls);
        } catch (Throwable th2) {
            Log.e(TAG, "flag getter unavailable", th2);
        }
    }

    public static void installCamera(final ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "gates off (/data/adb/omnicam_gates_off), flags left as shipped");
        } else {
            afterApplication(classLoader, new Runnable() { // from class: local.jiege.hook.modes.ModeGates.1
                @Override // java.lang.Runnable
                public void run() {
                    ModeGates.install(classLoader);
                }
            });
        }
    }

    private static List<Method> intArrayReaders(Class<?> cls) {
        ArrayList arrayList = new ArrayList();
        for (Method method : cls.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getReturnType() == int[].class) {
                if (parameterTypes.length == 2 && parameterTypes[0] == String.class && parameterTypes[1] == int[].class) {
                    arrayList.add(method);
                } else if (parameterTypes.length == 1 && parameterTypes[0] == String.class) {
                    arrayList.add(method);
                }
            }
        }
        return arrayList;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Object keyOf(Object[] objArr) {
        if (objArr == null) {
            return null;
        }
        for (Object obj : objArr) {
            if (obj instanceof String) {
                String str = (String) obj;
                if (indexOfInt(str) >= 0 || indexOfString(str) >= 0) {
                    return str;
                }
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String str) {
        try {
            Log.i(TAG, str);
        } catch (Throwable unused) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Size sizeOf(String str) {
        int iIndexOf = str.indexOf(120);
        if (iIndexOf <= 0) {
            return null;
        }
        try {
            return new Size(Integer.parseInt(str.substring(0, iIndexOf)), Integer.parseInt(str.substring(iIndexOf + 1)));
        } catch (NumberFormatException unused) {
            return null;
        }
    }
}
