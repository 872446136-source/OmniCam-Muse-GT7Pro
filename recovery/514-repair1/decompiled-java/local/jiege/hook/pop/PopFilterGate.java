package local.jiege.hook.pop;

import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class PopFilterGate {
    private static final String BASE_MODE = "com.oplus.camera.module.BaseMode";
    private static final String OFF = "/data/adb/omnicam_popgate_off";
    private static final long STALE_MS = 1200;
    private static final String TAG = "PopPort";
    private static volatile long armed;
    private static volatile Field[] flags = new Field[0];
    private static volatile boolean reportedClear;
    private static volatile boolean reportedFallback;
    private static volatile boolean reportedInstall;
    private static volatile boolean reportedRefusal;
    private static volatile Class<?> retro;
    private static volatile Field settled;
    private static volatile Field switching;

    private PopFilterGate() {
    }

    private static void arm(Class<?> cls, Method method, Method method2, Method method3, Method method4, Method method5, Method method6, String str) {
        retro = cls;
        flags = booleans(cls);
        switching = named(cls, "s3");
        settled = named(cls, "t3");
        if (method4 != null) {
            XposedBridge.hookMethod(method4, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopFilterGate.2
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    PopFilterGate.armed = SystemClock.elapsedRealtime();
                }
            });
        }
        if (method6 != null) {
            XposedBridge.hookMethod(method6, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopFilterGate.3
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object[] objArr = methodHookParam.args;
                    PopFilterGate.armed = (objArr.length <= 1 || !Boolean.TRUE.equals(objArr[1])) ? 0L : SystemClock.elapsedRealtime();
                }
            });
        }
        if (method5 != null) {
            XposedBridge.hookMethod(method5, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopFilterGate.4
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    PopFilterGate.armed = 0L;
                    Log.i(PopFilterGate.TAG, "filter strip settled, flags " + PopFilterGate.describe(PopFilterGate.read(methodHookParam.thisObject)));
                }
            });
        }
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.pop.PopFilterGate.5
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                PopFilterGate.unstick(methodHookParam.thisObject);
            }
        };
        XposedBridge.hookMethod(method, xC_MethodHook);
        XposedBridge.hookMethod(method2, xC_MethodHook);
        XposedBridge.hookMethod(method3, xC_MethodHook);
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopFilterGate.6
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Boolean.TRUE.equals(methodHookParam.getResult()) || PopFilterGate.reportedRefusal) {
                    return;
                }
                PopFilterGate.reportedRefusal = true;
                Log.i(PopFilterGate.TAG, "shutter click returned false, flags " + PopFilterGate.describe(PopFilterGate.read(methodHookParam.thisObject)));
            }
        });
        if (reportedInstall) {
            return;
        }
        reportedInstall = true;
        StringBuilder sb = new StringBuilder("POP filter gate installed on ");
        sb.append(cls.getName());
        sb.append(" via ");
        sb.append(str);
        sb.append(", switching flag ");
        sb.append(name(switching));
        sb.append(", settled flag ");
        sb.append(name(settled));
        sb.append(", ");
        sb.append(flags.length);
        sb.append(" boolean(s)");
        sb.append(method6 == null ? ", no writer hook" : PresetSelection.STANDARD_KEY);
        Log.i(TAG, sb.toString());
    }

    private static Field[] booleans(Class<?> cls) {
        ArrayList arrayList = new ArrayList();
        for (Field field : cls.getDeclaredFields()) {
            if (field.getType() == Boolean.TYPE && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                arrayList.add(field);
            }
        }
        return (Field[]) arrayList.toArray(new Field[0]);
    }

    private static void byConstructors(ClassLoader classLoader) {
        try {
            XposedBridge.hookAllConstructors(XposedHelpers.findClass(BASE_MODE, classLoader), new XC_MethodHook() { // from class: local.jiege.hook.pop.PopFilterGate.1
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object obj = methodHookParam.thisObject;
                    if (obj == null) {
                        return;
                    }
                    Class<?> cls = obj.getClass();
                    if (PopFilterGate.retro != null) {
                        return;
                    }
                    try {
                        PopFilterGate.identify(cls);
                    } catch (Throwable th) {
                        Log.e(PopFilterGate.TAG, "POP filter gate could not read ".concat(cls.getName()), th);
                    }
                }
            });
            Log.i(TAG, "POP filter gate waiting on com.oplus.camera.module.BaseMode constructors (fallback: the retro mode symbols did not resolve)");
        } catch (Throwable th) {
            Log.e(TAG, "POP filter gate unavailable", th);
        }
    }

    private static boolean bySymbols() {
        try {
            Symbols symbols = Symbols.get();
            if (!symbols.has("RetroMode", "RetroMode.shutterClick", "RetroMode.shutterTouch", "RetroMode.readyToCapture")) {
                return false;
            }
            arm(symbols.cls("RetroMode"), symbols.method("RetroMode.shutterClick"), symbols.method("RetroMode.shutterTouch"), symbols.method("RetroMode.readyToCapture"), symbols.has("RetroMode.filterScrolling") ? symbols.method("RetroMode.filterScrolling") : null, symbols.has("RetroMode.filterSettled") ? symbols.method("RetroMode.filterSettled") : null, symbols.has("RetroMode.setSwitching") ? symbols.method("RetroMode.setSwitching") : null, "symbols");
            return true;
        } catch (Throwable th) {
            Log.e(TAG, "POP filter gate could not use its symbols", th);
            return false;
        }
    }

    private static void clear(Object obj, String str) throws IllegalAccessException {
        if (switching != null) {
            switching.setBoolean(obj, false);
        } else {
            for (Field field : flags) {
                if (field.getBoolean(obj)) {
                    field.setBoolean(obj, false);
                }
            }
        }
        if (settled != null) {
            settled.setBoolean(obj, true);
        }
        armed = 0L;
        if (reportedClear) {
            return;
        }
        reportedClear = true;
        Log.i(TAG, "stale filter switch cleared (" + str + "), flags " + describe(read(obj)));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String describe(boolean[] zArr) {
        if (zArr == null) {
            return "(unreadable)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < zArr.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(flags[i].getName());
            sb.append('=');
            sb.append(zArr[i]);
        }
        return sb.toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized void identify(Class<?> cls) {
        if (retro == null && cls != null) {
            Class cls2 = Integer.TYPE;
            Method method = method(cls, "Gf", cls2);
            Method method2 = method(cls, "Hf", cls2);
            Class cls3 = Boolean.TYPE;
            Method method3 = method(cls, "Td", cls3, cls3, cls3, cls3);
            Method method4 = method(cls, "r6", cls2);
            Method method5 = method(cls, "T0", new Class[0]);
            String strShapeProblem = shapeProblem(cls, method, method2, method3, method4, method5);
            if (strShapeProblem == null) {
                arm(cls, method, method2, method3, method4, method5, method(cls, "Ek", String.class, cls3), "constructors");
                return;
            }
            if (!reportedFallback) {
                reportedFallback = true;
                Log.i(TAG, "POP filter gate skipped " + cls.getName() + ": " + strShapeProblem);
            }
        }
    }

    public static void install(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "POP filter gate off (/data/adb/omnicam_popgate_off), the shutter is left as the camera has it");
        } else {
            if (bySymbols()) {
                return;
            }
            byConstructors(classLoader);
        }
    }

    private static Method method(Class<?> cls, String str, Class<?>... clsArr) {
        try {
            Method declaredMethod = cls.getDeclaredMethod(str, clsArr);
            if (Modifier.isStatic(declaredMethod.getModifiers())) {
                return null;
            }
            return declaredMethod;
        } catch (NoSuchMethodException unused) {
            return null;
        }
    }

    private static String name(Field field) {
        return field == null ? "(not by name)" : field.getName();
    }

    private static Field named(Class<?> cls, String str) {
        try {
            Field declaredField = cls.getDeclaredField(str);
            if (declaredField.getType() == Boolean.TYPE && !Modifier.isStatic(declaredField.getModifiers())) {
                declaredField.setAccessible(true);
                return declaredField;
            }
        } catch (Throwable unused) {
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean[] read(Object obj) {
        try {
            boolean[] zArr = new boolean[flags.length];
            for (int i = 0; i < flags.length; i++) {
                zArr[i] = flags[i].getBoolean(obj);
            }
            return zArr;
        } catch (Throwable unused) {
            return null;
        }
    }

    public static Class<?> retroClass() {
        return retro;
    }

    private static String shapeProblem(Class<?> cls, Method method, Method method2, Method method3, Method method4, Method method5) {
        if (method == null) {
            return "no Gf(int):boolean";
        }
        if (method2 == null) {
            return "no Hf(int):void";
        }
        if (method3 == null) {
            return "no Td(boolean,boolean,boolean,boolean):boolean";
        }
        if (method4 == null) {
            return "no r6(int):void";
        }
        if (method5 == null) {
            return "no T0():void";
        }
        Class<?> returnType = method.getReturnType();
        Class<?> cls2 = Boolean.TYPE;
        if (returnType != cls2) {
            return "Gf does not return boolean";
        }
        Class<?> returnType2 = method2.getReturnType();
        Class<?> cls3 = Void.TYPE;
        if (returnType2 != cls3) {
            return "Hf does not return void";
        }
        if (method3.getReturnType() != cls2) {
            return "Td does not return boolean";
        }
        if (method4.getReturnType() != cls3) {
            return "r6 does not return void";
        }
        if (method5.getReturnType() != cls3) {
            return "T0 does not return void";
        }
        Field[] fieldArrBooleans = booleans(cls);
        if (fieldArrBooleans.length == 0) {
            return "no boolean fields";
        }
        if (fieldArrBooleans.length <= 6) {
            return null;
        }
        return fieldArrBooleans.length + " boolean fields, too many";
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void unstick(Object obj) {
        boolean z;
        long jElapsedRealtime = SystemClock.elapsedRealtime();
        try {
            boolean z2 = true;
            if (switching != null) {
                z = switching.getBoolean(obj);
            } else {
                boolean z3 = false;
                for (Field field : flags) {
                    if (field.getBoolean(obj)) {
                        z3 = true;
                    }
                }
                z = z3;
            }
            if (!z) {
                armed = 0L;
                return;
            }
            if (settled == null || !settled.getBoolean(obj)) {
                z2 = false;
            }
            long j = armed;
            if (z2) {
                clear(obj, "settled flag already set");
                return;
            }
            if (j == 0) {
                armed = jElapsedRealtime;
                return;
            }
            long j2 = jElapsedRealtime - j;
            if (j2 <= STALE_MS) {
                return;
            }
            clear(obj, j2 + " ms idle");
        } catch (Throwable th) {
            Log.e(TAG, "POP filter gate could not clear the switch flag", th);
        }
    }
}
