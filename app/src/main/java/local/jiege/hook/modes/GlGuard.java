package local.jiege.hook.modes;

import android.graphics.RectF;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * and that field is null in this process, so the bind throws, the exception leaves
 * {@code onDrawFrame}, and the GL thread dies. From then on the preview never draws again: the
 * camera comes up in POP, the surface is never painted, and the mode bar does not respond because
 * the UI is waiting on a preview that is gone. That is the black screen.
 *
 * <p>475 guarded the wrong method. It hooked {@code bo.r.r(gl)} on the assumption that this was
 * the frame entry, but {@code bo.r} is the renderer's holder object; the frame that dies is
 * {@code PreviewRenderer.onDrawFrame()} — {@code co.l} in that build, {@code nn.o} in 7.013.40 —
 * which is a plain no-argument method that calls the canvas under a lock. Guarding the holder's
 * per-frame helper caught nothing, which is why POP still died with the guard in place. 476 hooks
 * the real entry.
 *
 * <p>Why the field is null is not answered by the offline index. It does not matter for the fix:
 * the overlay is decoration, and a frame without it is worth more than a dead preview thread. So
 * the frame is called through {@code invokeOriginalMethod} and any throwable is swallowed, and the
 * mask draw is wrapped the same way at a narrower aperture. Nothing is logged per frame — the first
 * few and then one in two hundred — because a per-frame log would itself be a reason for the camera
 * to stall.
 *
 * <p>477 proved the swallow works and showed what it costs: the mask threw on every one of the
 * first five frames after POP finished switching in, and a caught throwable still fills in a stack
 * trace on the GL thread sixty times a second. It also turned the report red — those five lines are
 * the "相机注入：5 条错误" the settings app counted. 478 stops calling it: once the shader program
 * has been absent for {@link #BROKEN_AFTER} consecutive frames it is absent for the life of this
 * process, so the draw is skipped without an exception and without a log line.
 *
 * <p>Class names. {@code com.oplus.camera.common.gl.*} is a short-name package whose members get
 * reshuffled between camera builds (7.013.40 turned {@code gl.o} into {@code gl.p} and made
 * {@code gl.o} something else entirely), so the canvas is found by shape among a few candidates
 * rather than by one fixed name. The mask method itself is never named: {@code p0} became
 * {@code r0}, and the four-argument signature is unique on that class.
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

    /**
     * Candidates for GLES20Canvas. The package is not obfuscated as a whole, but its classes carry
     * one-letter names that move: the build this module targets first is listed first.
     */
    private static final String[] CANVAS = {
        "com.oplus.camera.common.gl.p",   // 7.013.40
        "com.oplus.camera.common.gl.o",   // 7.006.112
        "com.oplus.camera.common.gl.q",
        "com.oplus.camera.common.gl.n",
        "com.oplus.camera.common.gl.r",
    };

    /** Candidates for PreviewRenderer, the class whose onDrawFrame the GL thread calls. */
    private static final String[] RENDERER = {
        "nn.o",  // 7.013.40
        "co.l",  // 7.006.112
    };

    private static final String FRAME = "onDrawFrame";

    private static final int FIRST_LOGS = 5;
    private static final int EVERY = 200;
    /** Consecutive failures before the mask draw is written off for this process. */
    private static final int BROKEN_AFTER = 3;

    private static final AtomicInteger MASK_FAILURES = new AtomicInteger();
    private static final AtomicInteger FRAME_FAILURES = new AtomicInteger();
    private static final AtomicInteger MASK_STREAK = new AtomicInteger();
    private static final AtomicBoolean MASK_BROKEN = new AtomicBoolean();

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
        guardFrame(classLoader);
        guardMask(classLoader);
    }

    /**
     * {@code PreviewRenderer.onDrawFrame()} — the GL thread's frame entry, and the method the 473
     * stack trace died in. Wrapping it keeps one bad overlay from taking the thread with it.
     */
    private static void guardFrame(ClassLoader classLoader) {
        for (String name : RENDERER) {
            Class<?> renderer;
            try {
                renderer = Class.forName(name, false, classLoader);
            } catch (Throwable ignored) {
                continue;
            }
            Method frame = null;
            for (Method method : renderer.getDeclaredMethods()) {
                // onDrawFrame is a framework override, so the name survives obfuscation; the shape
                // only confirms it is the no-argument entry.
                if (FRAME.equals(method.getName()) && method.getParameterTypes().length == 0
                    && method.getReturnType() == void.class) {
                    frame = method;
                    break;
                }
            }
            if (frame == null) continue;
            try {
                XposedBridge.hookMethod(frame, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                        } catch (Throwable error) {
                            note(FRAME_FAILURES, "draw frame skipped", error);
                        }
                        // Always: the original has already run (or thrown), it must not run twice.
                        param.setResult(null);
                    }
                });
                Log.i(TAG, "preview frame guarded (" + name + "." + FRAME + ")");
                return;
            } catch (Throwable error) {
                Log.e(TAG, "frame hook failed on " + name, error);
                return;
            }
        }
        Log.i(TAG, "onDrawFrame not found, the preview thread is only guarded at the mask");
    }

    /**
     * {@code GLES20Canvas.<mask>(RectF, float, float[], float)} — the retro inverse-mask overlay.
     *
     * <p>Found by shape, not by name: the method is one obfuscated letter, and the four-argument
     * signature with a RectF, a float, a float array and a float is unique on that class.
     */
    private static void guardMask(ClassLoader classLoader) {
        for (String name : CANVAS) {
            Class<?> canvas;
            try {
                canvas = Class.forName(name, false, classLoader);
            } catch (Throwable ignored) {
                continue;
            }
            Method draw = null;
            for (Method method : canvas.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 4 && types[0] == RectF.class && types[1] == float.class
                    && types[2] == float[].class && types[3] == float.class
                    && method.getReturnType() == void.class) {
                    draw = method;
                    break;
                }
            }
            if (draw == null) continue;
            final String where = name + "." + draw.getName();
            try {
                XposedBridge.hookMethod(draw, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (MASK_BROKEN.get()) {
                            // The shader program this draws with is not created in this process.
                            // Retrying would throw on every frame; skip silently instead.
                            param.setResult(null);
                            return;
                        }
                        try {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                            MASK_STREAK.set(0);
                        } catch (Throwable error) {
                            note(MASK_FAILURES, "inverse-mask overlay skipped", error);
                            if (MASK_STREAK.incrementAndGet() >= BROKEN_AFTER) {
                                MASK_BROKEN.set(true);
                                Log.i(TAG, "inverse-mask overlay disabled for this process: the"
                                    + " shader program is absent, so every frame would throw");
                            }
                        }
                        param.setResult(null);
                    }
                });
                Log.i(TAG, "inverse-mask overlay guarded (" + where + ")");
                return;
            } catch (Throwable error) {
                Log.e(TAG, "inverse-mask hook failed on " + where, error);
                return;
            }
        }
        Log.i(TAG, "inverse-mask draw not found in any canvas candidate");
    }

    /**
     * Logs the first few failures and then one in {@link #EVERY}: a draw path that throws on every
     * frame would otherwise write sixty lines a second and stall the camera on its own log.
     *
     * <p>Info, not error: the missing shader program is normal in a POP session on this device, and
     * the settings app turns every {@code E} line into "相机注入：N 条错误". An expected condition
     * must not be counted as a failed injection.
     */
    private static void note(AtomicInteger counter, String message, Throwable error) {
        int count = counter.incrementAndGet();
        if (count > FIRST_LOGS && count % EVERY != 0) return;
        Log.i(TAG, message + " (" + count + ")", error);
    }
}
