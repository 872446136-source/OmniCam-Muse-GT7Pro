package local.jiege.hook.watermark;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.hd;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class HasselWatermark {
    private static final String COMPANION = "com.oplus.gallery.framework.abilities.watermark.masterstyle.WatermarkMasterStyle$Companion";
    private static final String FORCED = "feature_is_support_hassel_watermark";
    private static final String MASTER_MODE = "hassel_style_master_mode_1";
    private static final String OFF = "/data/adb/omnicam_hasselwm_off";
    private static final String TAG = "HasselWatermark";
    private static final String XPAN = "hassel_style_xpan_1";
    private static boolean reported;
    private static final String[] FRAMES = {"hassel_style_1", "hassel_style_2", "hassel_style_3", "hassel_style_4"};
    private static final String[] TEXTS = {"hassel_text_style_1", "hassel_text_style_2"};
    private static final Set<String> SWITCHES = ConcurrentHashMap.newKeySet();
    private static final LinkedHashSet<String> seen = new LinkedHashSet<>();

    private HasselWatermark() {
    }

    private static int add(Class<?> cls, ClassLoader classLoader, List<Object> list, String str) {
        try {
            Object objCallStaticMethod = XposedHelpers.callStaticMethod(cls, "getStyleFileNameById", new Object[]{str});
            if ((objCallStaticMethod instanceof String) && !((String) objCallStaticMethod).isEmpty()) {
                list.add(XposedHelpers.newInstance(XposedHelpers.findClass("kotlin.Pair", classLoader), new Object[]{str, objCallStaticMethod}));
                return 1;
            }
            return 0;
        } catch (Throwable th) {
            Log.e(TAG, "style " + str + " not added", th);
            return 0;
        }
    }

    private static int addIfMissing(Class<?> cls, ClassLoader classLoader, List<Object> list, String str) {
        Iterator<Object> it = list.iterator();
        while (it.hasNext()) {
            if (str.equals(first(it.next()))) {
                return 0;
            }
        }
        return add(cls, classLoader, list, str);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void fill(Class<?> cls, ClassLoader classLoader, XC_MethodHook.MethodHookParam methodHookParam) {
        Object result = methodHookParam.getResult();
        if (!(result instanceof List)) {
            if (reported) {
                return;
            }
            reported = true;
            Log.i(TAG, "no style list returned (" + result + "), nothing to fill");
            return;
        }
        List list = (List) result;
        Iterator it = list.iterator();
        while (it.hasNext()) {
            Object objFirst = first(it.next());
            if ((objFirst instanceof String) && ((String) objFirst).matches("hassel_style_[1-4]")) {
                if (reported) {
                    return;
                }
                reported = true;
                Log.i(TAG, "built-in hassel group present (" + list.size() + " styles), left alone");
                return;
            }
        }
        int size = list.size();
        String[] strArr = FRAMES;
        ArrayList arrayList = new ArrayList(size + strArr.length + TEXTS.length + 2);
        arrayList.addAll(list);
        Boolean bool = Boolean.TRUE;
        int iAddIfMissing = bool.equals(methodHookParam.args[0]) ? addIfMissing(cls, classLoader, arrayList, MASTER_MODE) : 0;
        if (bool.equals(methodHookParam.args[1])) {
            iAddIfMissing += addIfMissing(cls, classLoader, arrayList, XPAN);
        }
        for (String str : strArr) {
            iAddIfMissing += add(cls, classLoader, arrayList, str);
        }
        for (String str2 : TEXTS) {
            iAddIfMissing += add(cls, classLoader, arrayList, str2);
        }
        if (iAddIfMissing == 0) {
            Log.i(TAG, "the companion knows no hassel style file names, group stays empty");
            return;
        }
        methodHookParam.setResult(arrayList);
        if (reported) {
            return;
        }
        reported = true;
        Log.i(TAG, "hassel group filled: " + iAddIfMissing + " styles added to " + list.size());
    }

    private static Object first(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return XposedHelpers.callMethod(obj, "getFirst", new Object[0]);
        } catch (Throwable unused) {
            return null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void gate(ClassLoader classLoader) {
        try {
            Class clsFindClass = XposedHelpers.findClass("com.oplus.aiunit.vision.y9b", classLoader);
            XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.watermark.HasselWatermark.1
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object objectExtra = methodHookParam.getObjectExtra("hassel.key");
                    if (objectExtra instanceof String) {
                        Log.i(HasselWatermark.TAG, "watermark switch " + objectExtra + " reads " + methodHookParam.getResult());
                    }
                }

                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    String strKeyOf = HasselWatermark.keyOf(methodHookParam.args);
                    if (strKeyOf == null) {
                        return;
                    }
                    if (!HasselWatermark.FORCED.equals(strKeyOf)) {
                        if (HasselWatermark.SWITCHES.add("asked:".concat(strKeyOf))) {
                            methodHookParam.setObjectExtra("hassel.key", strKeyOf);
                        }
                    } else {
                        methodHookParam.setResult(Boolean.TRUE);
                        if (HasselWatermark.SWITCHES.add("forced:".concat(strKeyOf))) {
                            Log.i(HasselWatermark.TAG, strKeyOf.concat(" answered true"));
                        }
                    }
                }
            };
            int i = 0;
            for (Method method : clsFindClass.getDeclaredMethods()) {
                if (method.getReturnType() == Boolean.TYPE) {
                    boolean z = false;
                    for (Class<?> cls : method.getParameterTypes()) {
                        if (cls == String.class) {
                            z = true;
                        }
                    }
                    if (z) {
                        try {
                            XposedBridge.hookMethod(method, xC_MethodHook);
                            i++;
                        } catch (Throwable th) {
                            Log.e(TAG, "watermark switch hook failed on " + method.getName(), th);
                        }
                    }
                }
            }
            Log.i(TAG, "hassel section gate armed on " + i + " reader(s)");
        } catch (Throwable unused) {
            Log.i(TAG, "feature switch reader not in this build, the 哈苏 row is left as shipped");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hook(final ClassLoader classLoader) {
        final Class clsFindClass = XposedHelpers.findClass(COMPANION, classLoader);
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.watermark.HasselWatermark.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    HasselWatermark.fill(clsFindClass, classLoader, methodHookParam);
                } catch (Throwable th) {
                    Log.e(HasselWatermark.TAG, "hassel style list not filled", th);
                }
            }
        };
        Class cls = Boolean.TYPE;
        XposedHelpers.findAndHookMethod(clsFindClass, "getBuiltInHasselStyles", new Object[]{cls, cls, xC_MethodHook});
        Log.i(TAG, "hassel style list hooked");
        probeSeries(clsFindClass);
    }

    public static void installGallery(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "off (/data/adb/omnicam_hasselwm_off)");
        } else {
            Log.guard(TAG, "hassel style list", new hd(classLoader, (byte) 6));
            Log.guard(TAG, "hassel section gate", new hd(classLoader, (byte) 7));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String join() {
        String string;
        LinkedHashSet<String> linkedHashSet = seen;
        synchronized (linkedHashSet) {
            try {
                StringBuilder sb = new StringBuilder();
                for (String str : linkedHashSet) {
                    if (sb.length() > 0) {
                        sb.append(',');
                    }
                    sb.append(str);
                }
                string = sb.toString();
            } catch (Throwable th) {
                throw th;
            }
        }
        return string;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String keyOf(Object[] objArr) {
        if (objArr == null) {
            return null;
        }
        for (Object obj : objArr) {
            if (obj instanceof String) {
                String str = (String) obj;
                if (str.startsWith("feature_is_support_") || str.startsWith("is_") || str.startsWith("is_oppo") || str.startsWith("is_oneplus")) {
                    return str;
                }
            }
        }
        return null;
    }

    private static void probeSeries(Class<?> cls) {
        try {
            XposedHelpers.findAndHookMethod(cls, "getSeries", new Object[]{String.class, new XC_MethodHook() { // from class: local.jiege.hook.watermark.HasselWatermark.3
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    int size;
                    Object obj = methodHookParam.args[0];
                    if (obj instanceof String) {
                        String str = (String) obj;
                        boolean zStartsWith = str.startsWith("hassel");
                        synchronized (HasselWatermark.seen) {
                            HasselWatermark.seen.add(str);
                            size = HasselWatermark.seen.size();
                        }
                        if (zStartsWith || size % 16 == 0) {
                            Log.i(HasselWatermark.TAG, "styles seen (" + size + "): " + HasselWatermark.join());
                        }
                    }
                }
            }});
            Log.i(TAG, "style series probe armed");
        } catch (Throwable th) {
            Log.i(TAG, "style series probe unavailable: " + th);
        }
    }
}
