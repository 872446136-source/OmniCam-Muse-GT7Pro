package local.jiege.hook.modes;

import android.graphics.RectF;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class GlGuard {
    private static final int BROKEN_AFTER = 3;
    private static final int EVERY = 200;
    private static final int FIRST_LOGS = 5;
    private static final String FRAME = "onDrawFrame";
    private static final String OFF = "/data/adb/omnicam_glguard_off";
    private static final String TAG = "GlGuard";
    private static boolean installed;
    private static final String[] CANVAS = {"com.oplus.camera.common.gl.p", "com.oplus.camera.common.gl.o", "com.oplus.camera.common.gl.q", "com.oplus.camera.common.gl.n", "com.oplus.camera.common.gl.r"};
    private static final String[] RENDERER = {"nn.o", "co.l"};
    private static final AtomicInteger MASK_FAILURES = new AtomicInteger();
    private static final AtomicInteger FRAME_FAILURES = new AtomicInteger();
    private static final AtomicInteger MASK_STREAK = new AtomicInteger();
    private static final AtomicBoolean MASK_BROKEN = new AtomicBoolean();

    private GlGuard() {
    }

    private static void guardFrame(ClassLoader classLoader) {
        Method method;
        for (String str : RENDERER) {
            try {
                Method[] declaredMethods = Class.forName(str, false, classLoader).getDeclaredMethods();
                int length = declaredMethods.length;
                int i = 0;
                while (true) {
                    if (i >= length) {
                        method = null;
                        break;
                    }
                    method = declaredMethods[i];
                    if (FRAME.equals(method.getName()) && method.getParameterTypes().length == 0 && method.getReturnType() == Void.TYPE) {
                        break;
                    } else {
                        i++;
                    }
                }
                if (method != null) {
                    try {
                        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.modes.GlGuard.2
                            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                try {
                                    XposedBridge.invokeOriginalMethod(methodHookParam.method, methodHookParam.thisObject, methodHookParam.args);
                                } catch (Throwable th) {
                                    GlGuard.note(GlGuard.FRAME_FAILURES, "draw frame skipped", th);
                                }
                                methodHookParam.setResult((Object) null);
                            }
                        });
                        Log.i(TAG, "preview frame guarded (" + str + "." + FRAME + ")");
                        return;
                    } catch (Throwable th) {
                        Log.e(TAG, "frame hook failed on " + str, th);
                        return;
                    }
                }
            } catch (Throwable unused) {
            }
        }
        Log.i(TAG, "onDrawFrame not found, the preview thread is only guarded at the mask");
    }

    private static void guardMask(ClassLoader classLoader) {
        Method method;
        for (String str : CANVAS) {
            try {
                Method[] declaredMethods = Class.forName(str, false, classLoader).getDeclaredMethods();
                int length = declaredMethods.length;
                int i = 0;
                while (true) {
                    if (i >= length) {
                        method = null;
                        break;
                    }
                    method = declaredMethods[i];
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length == 4 && parameterTypes[0] == RectF.class) {
                        Class<?> cls = parameterTypes[1];
                        Class<?> cls2 = Float.TYPE;
                        if (cls == cls2 && parameterTypes[2] == float[].class && parameterTypes[BROKEN_AFTER] == cls2 && method.getReturnType() == Void.TYPE) {
                            break;
                        }
                    }
                    i++;
                }
                if (method != null) {
                    String str2 = str + "." + method.getName();
                    try {
                        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.modes.GlGuard.3
                            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                if (GlGuard.MASK_BROKEN.get()) {
                                    methodHookParam.setResult((Object) null);
                                    return;
                                }
                                try {
                                    XposedBridge.invokeOriginalMethod(methodHookParam.method, methodHookParam.thisObject, methodHookParam.args);
                                    GlGuard.MASK_STREAK.set(0);
                                } catch (Throwable th) {
                                    GlGuard.note(GlGuard.MASK_FAILURES, "inverse-mask overlay skipped", th);
                                    if (GlGuard.MASK_STREAK.incrementAndGet() >= GlGuard.BROKEN_AFTER) {
                                        GlGuard.MASK_BROKEN.set(true);
                                        Log.i(GlGuard.TAG, "inverse-mask overlay disabled for this process: the shader program is absent, so every frame would throw");
                                    }
                                }
                                methodHookParam.setResult((Object) null);
                            }
                        });
                        Log.i(TAG, "inverse-mask overlay guarded (" + str2 + ")");
                        return;
                    } catch (Throwable th) {
                        Log.e(TAG, "inverse-mask hook failed on ".concat(str2), th);
                        return;
                    }
                }
            } catch (Throwable unused) {
            }
        }
        Log.i(TAG, "inverse-mask draw not found in any canvas candidate");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized void install(ClassLoader classLoader) {
        if (installed) {
            return;
        }
        installed = true;
        guardFrame(classLoader);
        guardMask(classLoader);
    }

    public static void installCamera(final ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "off (/data/adb/omnicam_glguard_off), the preview thread is left unguarded");
        } else {
            ModeGates.afterApplication(classLoader, new Runnable() { // from class: local.jiege.hook.modes.GlGuard.1
                @Override // java.lang.Runnable
                public void run() {
                    GlGuard.install(classLoader);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void note(AtomicInteger atomicInteger, String str, Throwable th) {
        int iIncrementAndGet = atomicInteger.incrementAndGet();
        if (iIncrementAndGet <= FIRST_LOGS || iIncrementAndGet % EVERY == 0) {
            Log.i(TAG, str + " (" + iIncrementAndGet + ")", th);
        }
    }
}
