package local.jiege.hook.pop;

import android.app.Activity;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class PopGuard {
    private static final int LOG_LIMIT = 10;
    private static final String OFF = "/data/adb/omnicam_popguard_off";
    private static final String REPAIR_OFF = "/data/adb/omnicam_poprepair_off";
    private static final String TAG = "PopPort";
    private static final String UI_FACADE = "com.oplus.camera.protocal.ui.a";
    private static volatile Object attempted;
    private static boolean installed;
    private static volatile Object latest;
    private static final AtomicInteger SWALLOWED = new AtomicInteger();
    private static final AtomicBoolean INIT_DONE = new AtomicBoolean();

    private PopGuard() {
    }

    private static Activity activityOf(Object obj) {
        for (Field field : obj.getClass().getDeclaredFields()) {
            if (Activity.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    Object obj2 = field.get(obj);
                    if (obj2 instanceof Activity) {
                        return (Activity) obj2;
                    }
                    continue;
                } catch (Throwable unused) {
                    continue;
                }
            }
        }
        return null;
    }

    private static Object containerOf(Object obj) {
        if (obj != null) {
            for (Field field : obj.getClass().getDeclaredFields()) {
                if (!field.getType().isPrimitive()) {
                    try {
                        field.setAccessible(true);
                        Object obj2 = field.get(obj);
                        if (obj2 != null) {
                            return obj2;
                        }
                    } catch (Throwable unused) {
                        continue;
                    }
                }
            }
        }
        return latest;
    }

    public static /* bridge */ /* synthetic */ boolean d(Object obj, Symbols symbols) {
        return repair(symbols, obj);
    }

    private static Object facadeOf(Object obj) {
        Method method;
        try {
            Class<?> cls = Class.forName(UI_FACADE, false, obj.getClass().getClassLoader());
            for (Field field : obj.getClass().getDeclaredFields()) {
                if (!field.getType().isPrimitive()) {
                    try {
                        field.setAccessible(true);
                        Object obj2 = field.get(obj);
                        if (obj2 == null) {
                            continue;
                        } else {
                            Method[] methods = obj2.getClass().getMethods();
                            int length = methods.length;
                            int i = 0;
                            while (true) {
                                if (i >= length) {
                                    method = null;
                                    break;
                                }
                                method = methods[i];
                                if (method.getParameterTypes().length == 0 && cls.isAssignableFrom(method.getReturnType())) {
                                    break;
                                }
                                i++;
                            }
                            if (method == null) {
                                continue;
                            } else {
                                try {
                                    Object objInvoke = method.invoke(obj2, null);
                                    if (objInvoke != null) {
                                        Log.i(TAG, "ui facade taken from " + field.getName() + " -> " + method.getName());
                                        return objInvoke;
                                    }
                                    continue;
                                } catch (Throwable th) {
                                    Log.i(TAG, "ui facade getter " + method.getName() + " threw: " + th);
                                }
                            }
                        }
                    } catch (Throwable unused) {
                        continue;
                    }
                }
            }
            return null;
        } catch (Throwable th2) {
            Log.e(TAG, "ui facade interface not found: com.oplus.camera.protocal.ui.a", th2);
            return null;
        }
    }

    private static void guardShow(final Symbols symbols) {
        if (!symbols.has("PopUI.show")) {
            Log.i(TAG, "retro show runnable not found, POP keeps the stock behaviour");
            return;
        }
        try {
            XposedBridge.hookMethod(symbols.method("PopUI.show"), new XC_MethodHook() { // from class: local.jiege.hook.pop.PopGuard.3
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    try {
                        XposedBridge.invokeOriginalMethod(methodHookParam.method, methodHookParam.thisObject, methodHookParam.args);
                    } catch (Throwable th) {
                        if (PopGuard.d(methodHookParam.thisObject, symbols)) {
                            try {
                                XposedBridge.invokeOriginalMethod(methodHookParam.method, methodHookParam.thisObject, methodHookParam.args);
                                Log.i(PopGuard.TAG, "retro show runnable replayed after the view was built");
                            } catch (Throwable th2) {
                                PopGuard.note(methodHookParam.thisObject, th2);
                            }
                        } else {
                            PopGuard.note(methodHookParam.thisObject, th);
                        }
                    }
                    methodHookParam.setResult((Object) null);
                }
            });
            Log.i(TAG, "retro show runnable guarded (" + symbols.name("PopUI.show") + ")");
        } catch (Throwable th) {
            Log.e(TAG, "cannot guard the retro show runnable", th);
        }
    }

    public static void install(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "pop guard off (/data/adb/omnicam_popguard_off)");
            return;
        }
        if (installed) {
            return;
        }
        installed = true;
        Symbols symbols = Symbols.get();
        watchInit(symbols);
        watchContainer(symbols);
        guardShow(symbols);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void note(Object obj, Throwable th) {
        int iIncrementAndGet = SWALLOWED.incrementAndGet();
        if (iIncrementAndGet > 10) {
            if (iIncrementAndGet == 11) {
                Log.i(TAG, "retro show runnable keeps throwing, log stops at 10");
                return;
            }
            return;
        }
        StringBuilder sb = new StringBuilder("retro show runnable dropped (");
        sb.append(obj == null ? "?" : obj.getClass().getName());
        sb.append(", #");
        sb.append(iIncrementAndGet);
        sb.append(", retro UI init done: ");
        sb.append(INIT_DONE.get());
        sb.append(")");
        Log.i(TAG, sb.toString(), th);
    }

    private static boolean repair(Symbols symbols, Object obj) {
        AtomicBoolean atomicBoolean = INIT_DONE;
        if (atomicBoolean.get() || new File(REPAIR_OFF).exists()) {
            return false;
        }
        Object objContainerOf = containerOf(obj);
        if (objContainerOf == null) {
            Log.i(TAG, "retro UI missing and no container to build it on");
            return false;
        }
        if (objContainerOf == attempted) {
            return false;
        }
        attempted = objContainerOf;
        Activity activityActivityOf = activityOf(objContainerOf);
        Object objFacadeOf = facadeOf(objContainerOf);
        if (activityActivityOf == null || objFacadeOf == null) {
            StringBuilder sb = new StringBuilder("retro UI missing, cannot build it: activity=");
            sb.append(activityActivityOf != null);
            sb.append(" uiFacade=");
            sb.append(objFacadeOf != null);
            Log.i(TAG, sb.toString());
            return false;
        }
        Log.i(TAG, "retro UI missing, building it on ".concat(objContainerOf.getClass().getName()));
        try {
            symbols.call("PopUI.init", objContainerOf, activityActivityOf, objFacadeOf);
            return atomicBoolean.get();
        } catch (Throwable th) {
            Log.e(TAG, "retro UI init threw when called directly", th);
            return false;
        }
    }

    private static void watchContainer(Symbols symbols) {
        if (symbols.has("PopUI.init")) {
            try {
                Class<?> declaringClass = symbols.method("PopUI.init").getDeclaringClass();
                int i = 0;
                for (Constructor<?> constructor : declaringClass.getDeclaredConstructors()) {
                    try {
                        XposedBridge.hookMethod(constructor, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopGuard.1
                            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                PopGuard.latest = methodHookParam.thisObject;
                                PopGuard.INIT_DONE.set(false);
                            }
                        });
                        i++;
                    } catch (Throwable th) {
                        Log.e(TAG, "cannot watch a retro container constructor", th);
                    }
                }
                Log.i(TAG, "retro container watched (" + declaringClass.getName() + ", " + i + " ctors)");
            } catch (Throwable th2) {
                Log.e(TAG, "cannot reach the retro container class", th2);
            }
        }
    }

    private static void watchInit(Symbols symbols) {
        if (!symbols.has("PopUI.init")) {
            Log.i(TAG, "retro UI init not found, the report will not say whether the view was built");
            return;
        }
        try {
            XposedBridge.hookMethod(symbols.method("PopUI.init"), new XC_MethodHook() { // from class: local.jiege.hook.pop.PopGuard.2
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    PopGuard.INIT_DONE.set(true);
                    Log.i(PopGuard.TAG, "retro UI init done");
                }

                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Log.i(PopGuard.TAG, "retro UI init start");
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "cannot watch the retro UI init", th);
        }
    }
}
