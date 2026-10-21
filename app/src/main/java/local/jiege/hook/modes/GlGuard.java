package local.jiege.hook.modes;

import android.graphics.RectF;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import local.jiege.hook.common.Log;

/**
 * Keeps the preview GL thread alive when the retro overlay is asked for something this build did not
 * build.
 *
 * <p>473 crashed the whole camera eight milliseconds after POP (retroCamera) finished switching in:
 *
 * <pre>
 * FATAL EXCEPTION: PreviewGLThread
 * java.lang.NullPointerException: Attempt to invoke virtual method 'void
 *     com.oplus.camera.common.gl.w0.s()' on a null object reference
 *   at com.oplus.camera.common.gl.o.p0(GLES20Canvas.java:120)
 *   at tq.b1.dh(CameraPreviewUI.java:626)
 *   at tq.b1.g1(CameraPreviewUI.java:195)
 *   at co.l.G0(PreviewRenderer.java:329)
 *   at co.l.onDrawFrame(PreviewRenderer.java:34)
 * </pre>
 *
 * <p>{@code p0(RectF, float, float[], float)} is {@code drawInverseMaskOverlay} — the rounded-rect
 * mask the retro UI (POP, and only POP) paints over the viewfinder. It binds a shader program field
 * ({@code j0}, the one built from {@code texture_vertex_shader_inverse_mask} +
 * {@code draw_inverse_mask_shader}) and that field is null in this process, so the bind throws, the
 * exception leaves {@code onDrawFrame}, and the GL thread dies. From then on the preview never draws
 * again: re-entering the camera comes up in POP, the surface is never painted, and the mode bar does
 * not respond because the UI is waiting on a preview that is gone. That is the black screen.
 *
 * <p>Why the field is null is not answered by the offline index — {@code o.<init>} does assign it,
 * last of all its shader programs, so either the assignment is behind a condition this device does
 * not meet or the program was released again. It does not matter for the fix: the overlay is
 * decoration, and a frame without it is worth more than a dead preview thread. So the draw is called
 * through {@code invokeOriginalMethod} and any throwable is swallowed.
 *
 * <p>Second hook, same reason at a wider aperture: {@code bo.r.r(gl)} is the
 * {@code PreviewEffectProcessor.onDrawFrame} the GL thread calls once per frame. It is wrapped the
 * same way, so a null shader anywhere else in the draw path costs one overlay instead of the whole
 * preview. Nothing is logged per frame — the first few and then one in two hundred — because a
 * per-frame log would itself be a reason for the camera to stall.
 *
 * <p>Both hooks are armed from {@code Application.onCreate} (see {@link ModeGates#afterApplication}):
 * resolving a camera class while the process is still being bound loads the config engine too early
 * and the camera comes up black.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_glguard_off}.
 */
public final class GlGuard {
    private static final String TAG = "GlGuard";
    private static final String OFF = "/data/adb/omnicam_glguard_off";
    private static final String CANVAS = "com.oplus.camera.common.gl.o";
    private static final String MASK = "p0";
    private static final String EFFECT_PROCESSOR = "bo.r";
    private static final String FRAME = "r";
    private static final String GL_CALLBACK = "com.oplus.camera.common.gl.g";

    private static final int FIRST_LOGS = 5;
    private static final int EVERY = 200;

    private static final AtomicInteger MASK_FAILURES = new AtomicInteger();
    private static final AtomicInteger FRAME_FAILURES = new AtomicInteger();

    private static boolean installed;

    private GlGuard() {}

    public static void installCamera(final ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "off (" + OFF + "), the preview thread is left unguarded");
            return;
        }
        ModeGates.afterApplication(classLoader, new Runnable() {
            @Override public void run() {
                install(classLoader);
            }
        });
    }

    private static synchronized void install(ClassLoader classLoader) {
        if (installed) return;
        installed = true;
        guardMask(classLoader);
        guardFrame(classLoader);
    }

    /** {@code GLES20Canvas.p0(RectF, float, float[], float)} — the retro inverse-mask overlay. */
    private static void guardMask(ClassLoader classLoader) {
        Class<?> canvas;
        try {
            canvas = Class.forName(CANVAS, false, classLoader);
        } catch (Throwable error) {
            Log.e(TAG, "canvas class unavailable", error);
            return;
        }
        Method draw = null;
        for (Method method : canvas.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            // Name and shape: p0 is one obfuscated letter, so the shape decides, and the name only
            // says which of the matching overloads is meant.
            if (MASK.equals(method.getName()) && types.length == 4 && types[0] == RectF.class
                && types[1] == float.class && types[2] == float[].class && types[3] == float.class
                && method.getReturnType() == void.class) {
                draw = method;
                break;
            }
        }
        if (draw == null) {
            Log.i(TAG, "inverse-mask draw not found on " + CANVAS);
            return;
        }
        final String name = draw.getName();
        try {
            XposedBridge.hookMethod(draw, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    } catch (Throwable error) {
                        note(MASK_FAILURES, "inverse-mask overlay skipped", error);
                    }
                    // Always: the original has already run (or thrown), it must not run twice.
                    param.setResult(null);
                }
            });
            Log.i(TAG, "inverse-mask overlay guarded (" + name + ")");
        } catch (Throwable error) {
            Log.e(TAG, "inverse-mask hook failed", error);
        }
    }

    /** {@code PreviewEffectProcessor.r(gl)} — the per-frame entry the GL thread calls. */
    private static void guardFrame(ClassLoader classLoader) {
        Class<?> processor;
        try {
            processor = Class.forName(EFFECT_PROCESSOR, false, classLoader);
        } catch (Throwable error) {
            Log.e(TAG, "effect processor unavailable", error);
            return;
        }
        Method frame = null;
        for (Method method : processor.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            // The class has four one-argument methods taking the same GL callback; only r() is the
            // draw. e(g)V is one of the others, so the name has to be part of the match.
            if (FRAME.equals(method.getName()) && types.length == 1
                && GL_CALLBACK.equals(types[0].getName())
                && method.getReturnType() == void.class) {
                frame = method;
                break;
            }
        }
        if (frame == null) {
            Log.i(TAG, "onDrawFrame not found on " + EFFECT_PROCESSOR);
            return;
        }
        final String name = frame.getName();
        try {
            XposedBridge.hookMethod(frame, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                    } catch (Throwable error) {
                        note(FRAME_FAILURES, "draw frame skipped", error);
                    }
                    param.setResult(null);
                }
            });
            Log.i(TAG, "preview frame guarded (" + name + ")");
        } catch (Throwable error) {
            Log.e(TAG, "frame hook failed", error);
        }
    }

    /**
     * Logs the first few failures and then one in {@link #EVERY}: a draw path that throws on every
     * frame would otherwise write sixty lines a second and stall the camera on its own log.
     */
    private static void note(AtomicInteger counter, String message, Throwable error) {
        int count = counter.incrementAndGet();
        if (count > FIRST_LOGS && count % EVERY != 0) return;
        Log.e(TAG, message + " (" + count + ")", error);
    }
}
