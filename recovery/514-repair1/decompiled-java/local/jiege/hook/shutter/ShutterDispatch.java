package local.jiege.hook.shutter;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;
import local.jiege.hook.modes.ModeGates;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ShutterDispatch {
    private static final String DISPATCH = "d";
    private static final String MARKER = "ShutterButton";
    private static final String OFF = "/data/adb/omnicam_shutterprobe_off";
    private static final String TAG = "ShutterDispatch";
    private static volatile Field downFlag;
    private static boolean installed;
    private static final String[] BUS = {"pn.a", "do.a"};
    private static final String[] MESSAGE = {"pn.c", "do.c"};

    private ShutterDispatch() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean down(Object obj) {
        if (obj == null) {
            return false;
        }
        Field field = downFlag;
        if (field == null) {
            Field field2 = null;
            for (Field field3 : obj.getClass().getDeclaredFields()) {
                if (field3.getType() == Boolean.TYPE && !Modifier.isStatic(field3.getModifiers()) && !Modifier.isFinal(field3.getModifiers())) {
                    if (field2 != null) {
                        return false;
                    }
                    field2 = field3;
                }
            }
            if (field2 == null) {
                return false;
            }
            field2.setAccessible(true);
            downFlag = field2;
            field = field2;
        }
        try {
            return field.getBoolean(obj);
        } catch (Throwable unused) {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized void install(ClassLoader classLoader) {
        Method method;
        try {
            if (installed) {
                return;
            }
            installed = true;
            int i = 0;
            while (true) {
                String[] strArr = BUS;
                if (i >= strArr.length) {
                    Log.i(TAG, "event bus not found, a shutter press is not traced");
                    return;
                }
                try {
                    Method[] declaredMethods = Class.forName(strArr[i], false, classLoader).getDeclaredMethods();
                    int length = declaredMethods.length;
                    int i2 = 0;
                    while (true) {
                        if (i2 >= length) {
                            method = null;
                            break;
                        }
                        method = declaredMethods[i2];
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        if (DISPATCH.equals(method.getName()) && parameterTypes.length == 2 && MESSAGE[i].equals(parameterTypes[0].getName()) && parameterTypes[1] == Boolean.TYPE) {
                            break;
                        } else {
                            i2++;
                        }
                    }
                    if (method != null) {
                        try {
                            XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterDispatch.2
                                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                    Object obj = methodHookParam.args[0];
                                    if (obj != null && obj.getClass().getName().indexOf(ShutterDispatch.MARKER) >= 0) {
                                        Log.i(ShutterDispatch.TAG, "shutter event dispatched");
                                    }
                                }
                            });
                            Log.i(TAG, "shutter dispatch hooked on " + BUS[i]);
                            trace(classLoader);
                            return;
                        } catch (Throwable th) {
                            Log.e(TAG, "dispatch hook failed on " + BUS[i], th);
                            return;
                        }
                    }
                    i++;
                } catch (Throwable unused) {
                }
            }
        } catch (Throwable th2) {
            throw th2;
        }
    }

    public static void installCamera(final ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "off (/data/adb/omnicam_shutterprobe_off)");
        } else {
            ModeGates.afterApplication(classLoader, new Runnable() { // from class: local.jiege.hook.shutter.ShutterDispatch.1
                @Override // java.lang.Runnable
                public void run() {
                    ShutterDispatch.install(classLoader);
                }
            });
        }
    }

    private static void trace(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        if (symbols.has("ShutterMessage")) {
            try {
                for (Constructor<?> constructor : symbols.cls("ShutterMessage").getDeclaredConstructors()) {
                    XposedBridge.hookMethod(constructor, new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterDispatch.3
                        public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                            Log.i(ShutterDispatch.TAG, "shutter message built");
                        }
                    });
                }
            } catch (Throwable th) {
                Log.e(TAG, "cannot watch the shutter message", th);
            }
        }
        traceCapture(symbols);
        if (!symbols.has("ShutterHandler.all")) {
            Log.i(TAG, "shutter handlers not found, only the bus is traced");
            return;
        }
        Method method = symbols.has("ShutterHandler.gr") ? symbols.method("ShutterHandler.gr") : null;
        try {
            for (Method method2 : symbols.all("ShutterHandler.all")) {
                final String name = method2.getDeclaringClass().getName();
                boolean z = method != null && method.equals(method2);
                XposedBridge.hookMethod(method2, new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterDispatch.4
                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Object[] objArr = methodHookParam.args;
                        Log.i(ShutterDispatch.TAG, "shutter handled by " + name + ", down: " + ShutterDispatch.down(objArr.length > 0 ? objArr[0] : null));
                    }
                });
                StringBuilder sb = new StringBuilder();
                sb.append("shutter handler traced: ");
                sb.append(name);
                sb.append(".");
                sb.append(method2.getName());
                sb.append(z ? " (GR)" : PresetSelection.STANDARD_KEY);
                Log.i(TAG, sb.toString());
            }
        } catch (Throwable th2) {
            Log.e(TAG, "cannot watch the shutter handlers", th2);
        }
    }

    private static void traceCapture(Symbols symbols) {
        if (!symbols.has("BaseMode.capture")) {
            Log.i(TAG, "capture request symbol missing, a press cannot be followed past the mode");
            return;
        }
        try {
            Method method = symbols.method("BaseMode.capture");
            XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterDispatch.5
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Log.i(ShutterDispatch.TAG, "capture request returned " + methodHookParam.getResult());
                }

                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Log.i(ShutterDispatch.TAG, "capture request entered on ".concat(methodHookParam.thisObject.getClass().getName()));
                }
            });
            Log.i(TAG, "capture request traced: " + method);
        } catch (Throwable th) {
            Log.e(TAG, "cannot trace the capture request", th);
        }
    }
}
