package local.jiege.hook.xpan;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Size;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.json.JSONObject;
import local.jiege.hook.common.Log;

/** The AI watermark assembler copies a whole buffer; XPAN crop metadata alone cannot crop it. */
final class XpanWatermarkBuffer {
    private static final String SOURCE_SIZE = "omnicam.xpan.source.size";
    static void install(ClassLoader loader) throws Exception {
        Class<?> assembler = XposedHelpers.findClass(
                "com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkAssembler", loader);
        Method assemble = null;
        for (Method m : assembler.getDeclaredMethods()) {
            if (m.getName().equals("assemble") && m.getParameterTypes().length == 5) {
                if (assemble != null) throw new NoSuchMethodException("Ambiguous watermark assemble");
                assemble = m;
            }
        }
        if (assemble == null) throw new NoSuchMethodException("watermark assemble");
        Field info = XposedHelpers.findField(assemble.getParameterTypes()[0], "s");
        Class<?> yuv = info.getType();
        Field mode = XposedHelpers.findField(yuv, "e");
        Field watermarkMode = XposedHelpers.findField(yuv, "y");
        Field cropSize = XposedHelpers.findField(yuv, "k");
        Field inputSize = XposedHelpers.findField(yuv, "p");
        Field cropPoint = XposedHelpers.findField(yuv, "l");
        // The JSON reader sizes text and margins from p, even when image geometry uses k.
        // Give it a per-hold copy describing the cropped pixels, and retain the source size
        // separately until assemble performs the physical crop.
        Class<?> creator = XposedHelpers.findClass(
                "com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkCreator", loader);
        Method create = creator.getDeclaredMethod("createWatermarkBitmapHold", android.content.Context.class, yuv);
        XposedBridge.hookMethod(create, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                Object data = p.args[1];
                if (data == null || !"xpan".equals(mode.get(data)) || watermarkMode.getInt(data) != 3) return;
                Size target = (Size) cropSize.get(data);
                Size source = (Size) inputSize.get(data);
                if (target == null || source == null || target.equals(source)) return;
                Object copy = XposedHelpers.callMethod(data, "clone");
                if (copy == null) return;
                inputSize.set(copy, target);
                XposedHelpers.setAdditionalInstanceField(copy, SOURCE_SIZE, source);
                p.args[1] = copy;
            }
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                Object hold = p.getResult();
                if (hold == null) return;
                Object data = info.get(hold);
                if (data == null || !"xpan".equals(mode.get(data))) return;
                Object json = XposedHelpers.getObjectField(data, "z0");
                if (!(json instanceof JSONObject)
                        || !"omnicam_xpan_crop".equals(((JSONObject) json).optString("styleId"))) return;
                // Both capture bundle builders discard a hold whose six artwork slots are empty,
                // even when q contains the cropped image. A transparent element keeps that image
                // in the existing encode path without introducing a visible border or text.
                Bitmap[] bitmaps = (Bitmap[]) XposedHelpers.getObjectField(hold, "v");
                bitmaps[0] = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
                ((int[]) XposedHelpers.getObjectField(hold, "m"))[0] = 2;
                ((int[]) XposedHelpers.getObjectField(hold, "n"))[0] = 2;
                ((byte[][]) XposedHelpers.getObjectField(hold, "y"))[0] = new byte[16];
            }
        });
        Method crop = assembler.getMethod("cropOriginBuffer", long.class, int.class, int.class);
        Method release = assembler.getMethod("releaseHardwareBuffer", long.class);
        XposedBridge.hookMethod(assemble, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                Object data = info.get(p.args[0]);
                if (data == null || !"xpan".equals(mode.get(data)) || watermarkMode.getInt(data) != 3) return;
                Size target = (Size) cropSize.get(data);
                Size source = (Size) inputSize.get(data);
                Object uncropped = XposedHelpers.getAdditionalInstanceField(data, SOURCE_SIZE);
                if (uncropped instanceof Size) source = (Size) uncropped;
                if (target == null || source == null || target.equals(source)
                        || target.getWidth() > source.getWidth() || target.getHeight() > source.getHeight()) return;
                Object buffer = p.args[2];
                if (buffer instanceof Bitmap) {
                    Bitmap original = (Bitmap) buffer;
                    if (original.getWidth() == target.getWidth() && original.getHeight() == target.getHeight()) return;
                    if (original.getWidth() < target.getWidth() || original.getHeight() < target.getHeight()) return;
                    Point at = (Point) cropPoint.get(data);
                    int x = at == null ? (original.getWidth() - target.getWidth()) / 2 : at.x;
                    int y = at == null ? (original.getHeight() - target.getHeight()) / 2 : at.y;
                    if (x < 0 || y < 0 || x + target.getWidth() > original.getWidth() || y + target.getHeight() > original.getHeight()) return;
                    Bitmap cropped = Bitmap.createBitmap(original, x, y, target.getWidth(), target.getHeight());
                    p.args[2] = cropped;
                    if (cropped != original) p.setObjectExtra("omnicam.xpan.bitmap", cropped);
                } else if (buffer instanceof Long || buffer instanceof String) {
                    long original = buffer instanceof Long ? (Long) buffer : Long.parseLong((String) buffer);
                    long cropped = (Long) crop.invoke(null, original, target.getWidth(), target.getHeight());
                    if (cropped == 0 || cropped == original) return;
                    p.args[2] = cropped;
                    p.setObjectExtra("omnicam.xpan.buffer", cropped);
                } else return;
                Log.i("XpanBuffer", "cropped source before watermark: " + source + " -> " + target);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                Object buffer = p.getObjectExtra("omnicam.xpan.buffer");
                if (buffer instanceof Long) release.invoke(null, buffer);
                Object bitmap = p.getObjectExtra("omnicam.xpan.bitmap");
                if (bitmap instanceof Bitmap) ((Bitmap) bitmap).recycle();
            }
        });
    }
}
