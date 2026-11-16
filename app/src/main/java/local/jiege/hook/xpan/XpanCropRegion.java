package local.jiege.hook.xpan;

import android.util.Size;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import local.jiege.hook.common.Log;

/** Crop the delivered XPAN YUV, not an 8000x3636 canvas that the HAL never delivered. */
public final class XpanCropRegion {
    private static final String TAG = "XpanCrop";
    private static final String PREFIX = "com.oplus.ocs.camera.ipuapi.process.watermark.";
    private static final ThreadLocal<ArrayDeque<Size>> INPUTS = new ThreadLocal<>();

    private XpanCropRegion() {}

    public static void installCamera(ClassLoader loader) {
        Log.guard(TAG, "XPAN crop region", () -> install(loader));
    }

    private static void install(ClassLoader loader) throws Exception {
        if (new File("/data/adb/omnicam_xpancrop_off").exists()) return;
        Class<?> util = XposedHelpers.findClass(PREFIX + "util.WatermarkUtil", loader);
        Class<?> config = XposedHelpers.findClass(PREFIX + "config.VendorConfigure", loader);
        Class<?> process = XposedHelpers.findClass(PREFIX + "entity.WatermarkProcessParameter", loader);
        Method hold = null;
        for (Method method : util.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!method.getName().equals("buildHold") || types.length != 6
                    || types[3] != Size.class || types[4] != Size.class || types[5] != boolean.class) continue;
            if (hold != null) throw new NoSuchMethodException("Ambiguous WatermarkUtil.buildHold");
            hold = method;
        }
        if (hold == null) throw new NoSuchMethodException("WatermarkUtil.buildHold");
        // 7.013.51's input image holder carries its actual pixel dimensions here. Refuse a
        // different layout rather than cropping with an unrelated preview or requested size.
        final Field inputSize = XposedHelpers.findField(hold.getParameterTypes()[1], "p");
        if (inputSize.getType() != Size.class) throw new NoSuchFieldException("watermark input Size p");
        final Field modeName = XposedHelpers.findField(process, "mModeName");
        final Field watermarkMode = XposedHelpers.findField(process, "mWatermarkMode");
        if (modeName.getType() != String.class || watermarkMode.getType() != int.class)
            throw new NoSuchFieldException("WatermarkProcessParameter mode fields");
        Method convert = unique(process, "covertToWatermarkYuvInfo", null);
        Method landscape = unique(config, "getXpanLandScapeCropRegion", int[].class);
        Method portrait = unique(config, "getXpanPortraitCropRegion", int[].class);

        // Resolve the complete contract before installing any of the crop hooks.
        XpanSurfaceGeometry.install(loader);
        XpanWatermarkBuffer.install(loader);
        XpanBareCrop.install(loader);
        XposedBridge.hookMethod(convert, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if ("xpan".equals(modeName.get(param.thisObject))) {
                    // Without XPAN mode 3 the SDK ignores even a valid crop coordinate.
                    watermarkMode.setInt(param.thisObject, 3);
                }
            }
        });
        XposedBridge.hookMethod(hold, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Size size = null;
                try {
                    if (param.args[1] != null) size = (Size) inputSize.get(param.args[1]);
                } catch (IllegalAccessException ignored) { }
                ArrayDeque<Size> frames = INPUTS.get();
                if (frames == null) {
                    frames = new ArrayDeque<>();
                    INPUTS.set(frames);
                }
                // An unknown nested frame must hide the outer frame, not borrow its crop.
                frames.push(size == null ? new Size(0, 0) : size);
                param.setObjectExtra("omnicam.xpan.frame", Boolean.TRUE);
            }

            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!Boolean.TRUE.equals(param.getObjectExtra("omnicam.xpan.frame"))) return;
                ArrayDeque<Size> frames = INPUTS.get();
                if (frames == null) return;
                if (!frames.isEmpty()) frames.pop();
                if (frames.isEmpty()) INPUTS.remove();
            }
        });
        XposedBridge.hookMethod(landscape, region(false));
        XposedBridge.hookMethod(portrait, region(true));
        Log.i(TAG, "installed 65:24 crop using actual watermark input size");
    }

    private static Method unique(Class<?> type, String name, Class<?> result) throws NoSuchMethodException {
        Method found = null;
        for (Method method : type.getDeclaredMethods()) {
            if (!name.equals(method.getName()) || (result != null && method.getReturnType() != result)) continue;
            if (found != null) throw new NoSuchMethodException("Ambiguous " + name);
            found = method;
        }
        if (found == null) throw new NoSuchMethodException(name);
        return found;
    }

    private static XC_MethodHook region(final boolean portrait) {
        return new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                ArrayDeque<Size> frames = INPUTS.get();
                Size size = frames == null ? null : frames.peek();
                if (size == null || size.getWidth() < 2 || size.getHeight() < 2 || param.hasThrowable()) return;
                final int[] crop;
                try {
                    crop = XpanCropMath.region(size.getWidth(), size.getHeight(), portrait);
                } catch (IllegalArgumentException tooSmall) { return; }
                Object before = param.getResult();
                if (before instanceof int[] && Arrays.equals(crop, (int[]) before)) return;
                param.setResult(crop);
                Log.i(TAG, size + (portrait ? " portrait " : " landscape ") + Arrays.toString(crop));
            }
        };
    }
}
