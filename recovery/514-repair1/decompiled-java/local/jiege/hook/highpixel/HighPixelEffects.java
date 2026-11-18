package local.jiege.hook.highpixel;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.c3;
import defpackage.hd;
import defpackage.id;
import defpackage.t2;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class HighPixelEffects {
    private static final String FILTER_MENU = "pref_filter_menu";
    private static final String FILTER_PROCESS = "pref_filter_process_key";
    private static final String MODE = "highPixel";
    private static final String PALETTE = "key_support_palette";
    private static final String TAG = "HighPixelEffects";
    private static final Set<String> answered = Collections.synchronizedSet(new HashSet());

    private HighPixelEffects() {
    }

    private static void hookBeautyModel(Class<?> cls) {
        int i = 0;
        for (Method method : cls.getDeclaredMethods()) {
            boolean z = false;
            for (Class<?> cls2 : method.getParameterTypes()) {
                z |= cls2 == String.class;
            }
            if (z && !method.isSynthetic()) {
                XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.highpixel.HighPixelEffects.6
                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        int i2 = 0;
                        while (true) {
                            Object[] objArr = methodHookParam.args;
                            if (i2 >= objArr.length) {
                                return;
                            }
                            if (HighPixelEffects.MODE.equals(objArr[i2])) {
                                methodHookParam.args[i2] = "common";
                            }
                            i2++;
                        }
                    }
                });
                i++;
            }
        }
        if (i == 0) {
            t2.j("beauty model: no methods");
            return;
        }
        Log.i(TAG, "beauty model: " + i + " methods answer highPixel as common");
    }

    private static void hookEntries(Class<?> cls, final Method method) {
        XposedHelpers.findAndHookMethod(cls, "getSupportFunction", new Object[]{String.class, new XC_MethodHook() { // from class: local.jiege.hook.highpixel.HighPixelEffects.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                String str = (String) methodHookParam.args[0];
                if ((HighPixelEffects.FILTER_PROCESS.equals(str) || HighPixelEffects.FILTER_MENU.equals(str) || HighPixelEffects.PALETTE.equals(str)) && Settings.get("high_pixel_effects")) {
                    Boolean boolValueOf = Boolean.valueOf(HighPixelEffects.PALETTE.equals(str) ? HighPixelEffects.paletteConfigured(method) : true);
                    methodHookParam.setResult(boolValueOf);
                    if (HighPixelEffects.answered.add(str + "=" + boolValueOf)) {
                        Log.i(HighPixelEffects.TAG, "mode answers " + str + " = " + boolValueOf);
                    }
                }
            }
        }});
        Log.i(TAG, "mode entries installed");
    }

    private static void hookEntryButton(Method method, final Class<?> cls) {
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.highpixel.HighPixelEffects.7
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (cls.isInstance(methodHookParam.thisObject) && Settings.get("high_pixel_effects")) {
                    Boolean bool = Boolean.TRUE;
                    if (bool.equals(methodHookParam.getResult())) {
                        return;
                    }
                    methodHookParam.setResult(bool);
                    if (HighPixelEffects.answered.add("entryButton")) {
                        Log.i(HighPixelEffects.TAG, "right-hand entry button enabled for the mode");
                    }
                }
            }
        });
        Log.i(TAG, "entry button installed");
    }

    private static void hookFilterModes(List<Method> list) {
        int i = 0;
        for (Method method : list) {
            if (!method.getDeclaringClass().getName().startsWith("com.oplus.camera.statistics.")) {
                XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.highpixel.HighPixelEffects.8
                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        if (HighPixelEffects.MODE.equals(methodHookParam.args[0]) && Settings.get("high_pixel_effects")) {
                            methodHookParam.setResult(Boolean.TRUE);
                        }
                    }
                });
                i++;
            }
        }
        if (i == 1) {
            Log.i(TAG, "filter modes installed");
        } else {
            t2.j(c3.g("filter mode list: ", i, " candidates"));
        }
    }

    private static void hookLutTexture(Method method, Class<?> cls) {
        try {
            Method declaredMethod = cls.getDeclaredMethod(method.getName(), null);
            XposedBridge.hookMethod(declaredMethod, new XC_MethodHook() { // from class: local.jiege.hook.highpixel.HighPixelEffects.3
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Boolean bool = Boolean.TRUE;
                    if (!bool.equals(methodHookParam.getResult()) && Settings.get("high_pixel_effects") && bool.equals(XposedHelpers.callMethod(methodHookParam.thisObject, "getSupportFunction", new Object[]{HighPixelEffects.PALETTE}))) {
                        methodHookParam.setResult(bool);
                        if (HighPixelEffects.answered.add("lutTexture")) {
                            Log.i(HighPixelEffects.TAG, "preview lut texture enabled for the mode");
                        }
                    }
                }
            });
            Log.i(TAG, "preview lut texture installed on " + declaredMethod.getName());
        } catch (NoSuchMethodException unused) {
            Log.i(TAG, "preview lut texture: mode inherits " + method.getName() + ", nothing to do");
        }
    }

    private static void hookPalettePreview(Class<?> cls, final Field field, final Field field2, final Method method) {
        field.setAccessible(true);
        field2.setAccessible(true);
        XposedBridge.hookAllMethods(cls, "createEngine", new XC_MethodHook() { // from class: local.jiege.hook.highpixel.HighPixelEffects.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException {
                Object obj = field2.get(methodHookParam.thisObject);
                if (obj != null && HighPixelEffects.MODE.equals(XposedHelpers.callMethod(obj, "getCapModeName", new Object[0])) && Settings.get("high_pixel_effects") && HighPixelEffects.paletteConfigured(method) && !field.getBoolean(methodHookParam.thisObject)) {
                    field.setBoolean(methodHookParam.thisObject, true);
                    if (HighPixelEffects.answered.add("previewEngine")) {
                        Log.i(HighPixelEffects.TAG, "preview palette engine switched on for the mode");
                    }
                }
            }
        });
        Log.i(TAG, "preview palette engine installed");
    }

    private static void hookPreviewPalette(final Method method) {
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.highpixel.HighPixelEffects.9
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (HighPixelEffects.MODE.equals(methodHookParam.args[0]) && Settings.get("high_pixel_effects")) {
                    Boolean boolValueOf = Boolean.valueOf(HighPixelEffects.paletteConfigured(method));
                    methodHookParam.setResult(boolValueOf);
                    if (HighPixelEffects.answered.add("previewPalette=" + boolValueOf)) {
                        Log.i(HighPixelEffects.TAG, "preview palette gate for highPixel = " + boolValueOf);
                    }
                }
            }
        });
        Log.i(TAG, "preview palette installed");
    }

    private static void hookSoftLightCapture(Method method, final Method method2, final Field field) {
        field.setAccessible(true);
        final Class clsFindClass = XposedHelpers.findClass("com.oplus.camera.filter.FilterUtil", method.getDeclaringClass().getClassLoader());
        final Object staticObjectField = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.oplus.ocs.camera.CameraParameter", method.getDeclaringClass().getClassLoader()), "SOFT_LIGHT_OPEN");
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.highpixel.HighPixelEffects.5
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException, InvocationTargetException {
                int iIntValue;
                if (methodHookParam.hasThrowable() || !HighPixelEffects.MODE.equals(field.get(methodHookParam.thisObject)) || !Settings.get("high_pixel_effects") || (iIntValue = ((Integer) method2.invoke(methodHookParam.thisObject, null)).intValue()) == XposedHelpers.getStaticIntField(clsFindClass, "sSoftLightNoneIndex")) {
                    return;
                }
                Object objectField = XposedHelpers.getObjectField(methodHookParam.args[0], "D");
                for (Method method3 : objectField.getClass().getMethods()) {
                    if (method3.getName().equals("e") && method3.getParameterTypes().length == 2 && method3.getParameterTypes()[0].isInstance(staticObjectField)) {
                        method3.invoke(objectField, staticObjectField, Boolean.TRUE);
                        if (HighPixelEffects.answered.add("softLightCapture")) {
                            Log.i(HighPixelEffects.TAG, "soft light opened for the mode's capture request, index=" + iIntValue);
                            return;
                        }
                        return;
                    }
                }
                t2.j("capture parameter builder has no e(key, value)");
            }
        });
        Log.i(TAG, "soft light capture installed");
    }

    private static void hookSoftLightTypes(List<Method> list) {
        Iterator<Method> it = list.iterator();
        while (it.hasNext()) {
            XposedBridge.hookMethod(it.next(), new XC_MethodHook() { // from class: local.jiege.hook.highpixel.HighPixelEffects.4
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (HighPixelEffects.MODE.equals(methodHookParam.args[1]) && Settings.get("high_pixel_effects")) {
                        methodHookParam.args[1] = "common";
                    }
                }
            });
        }
        Log.i(TAG, "soft light lists installed: " + list.size());
    }

    public static void installCamera(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        Log.guard(TAG, "mode entries", new id(symbols, (byte) 1));
        Log.guard(TAG, "preview palette", new id(symbols, (byte) 2));
        Log.guard(TAG, "filter modes", new id(symbols, (byte) 3));
        Log.guard(TAG, "preview palette engine", new id(symbols, (byte) 4));
        Log.guard(TAG, "preview lut texture", new id(symbols, (byte) 5));
        Log.guard(TAG, "soft light types", new id(symbols, (byte) 6));
        Log.guard(TAG, "soft light capture", new id(symbols, (byte) 7));
        Log.guard(TAG, "beauty model", new id(symbols, (byte) 8));
        Log.guard(TAG, "entry button", new id(symbols, (byte) 9));
        Log.guard(TAG, "request log", new hd(classLoader, (byte) 8));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$0(Symbols symbols) {
        hookEntries(symbols.cls("HighPixelMode"), symbols.method("Lut3dData.paletteMode"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$1(Symbols symbols) {
        hookPreviewPalette(symbols.method("Lut3dData.paletteMode"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$2(Symbols symbols) {
        hookFilterModes(symbols.all("FilterModes.isFilterMode"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$3(Symbols symbols) {
        hookPalettePreview(symbols.cls("PalettePreview"), symbols.field("PalettePreview.flag"), symbols.field("PalettePreview.request"), symbols.method("Lut3dData.paletteMode"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$4(Symbols symbols) {
        hookLutTexture(symbols.method("Mode.needBasicToneLut"), symbols.cls("HighPixelMode"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$5(Symbols symbols) {
        hookSoftLightTypes(symbols.all("SoftLight.typesForMode"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$6(Symbols symbols) {
        hookSoftLightCapture(symbols.method("FilterPresenter.captureRequest"), symbols.method("FilterPresenter.softLightIndex"), symbols.field("FeaturePresenter.modeName"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$7(Symbols symbols) {
        hookBeautyModel(symbols.cls("BeautyModel"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$8(Symbols symbols) {
        hookEntryButton(symbols.method("Mode.effectEntry"), symbols.cls("HighPixelMode"));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean paletteConfigured(Method method) {
        return ((Boolean) XposedBridge.invokeOriginalMethod(method, (Object) null, new Object[]{"common"})).booleanValue();
    }
}
