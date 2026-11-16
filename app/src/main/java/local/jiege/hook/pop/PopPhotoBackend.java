package local.jiege.hook.pop;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import android.app.AndroidAppHelper;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import local.jiege.hook.common.Log;

/** RMX5010's retro SDK session never supplies a usable preview decision. */
final class PopPhotoBackend {
    private static final String MARKER = "omnicam.pop.photo_backend";
    static void install(ClassLoader loader) {
        XposedHelpers.findAndHookMethod("com.oplus.ocs.camera.producer.mode.ModeFactory", loader,
                "createMode", String.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if ("retro_camera_mode".equals(param.args[0])) {
                            param.setObjectExtra(MARKER, Boolean.TRUE);
                            param.args[0] = "photo_mode";
                            Log.i("PopBackend", "POP uses an independent stock photo session; capture checks remain enabled");
                        }
                    }
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (Boolean.TRUE.equals(param.getObjectExtra(MARKER)) && param.getResult() != null)
                            XposedHelpers.setAdditionalInstanceField(param.getResult(), MARKER, Boolean.TRUE);
                    }
                });
        Class<?> photo = XposedHelpers.findClass("com.oplus.ocs.camera.producer.mode.PhotoMode", loader);
        XposedBridge.hookAllMethods(photo, "createRequestTag", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(param.thisObject, MARKER))
                        || param.args.length != 6 || !"before_take_picture".equals(param.args[3])
                        || param.getResult() == null) return;
                // Session/decision stay photo_mode. Only the already-built capture's processing
                // identity is POP, so APS consumes its retro adjustment and filter parameters.
                XposedHelpers.setObjectField(param.getResult(), "mCaptureMode", "retro_camera_mode");
                Object shared = XposedHelpers.getObjectField(param.getResult(), "mApsRequestTag");
                Object capture = XposedHelpers.newInstance(shared.getClass());
                for (Field field : shared.getClass().getFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) field.set(capture, field.get(shared));
                }
                XposedHelpers.setObjectField(capture, "mModeName", "retro_camera_mode");
                XposedHelpers.setObjectField(param.getResult(), "mApsRequestTag", capture);
                ensureTintStage(param.getResult());
                Log.i("PopBackend", "capture retains POP processing identity");
            }
        });
        Class<?> processor = XposedHelpers.findClass("com.oplus.ocs.camera.consumer.ApsProcessor", loader);
        for (String method : new String[] {"createMetaItemInfo", "getAlgoFromApp", "addPictureMeta"}) {
            XposedBridge.hookAllMethods(processor, method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    if (p.args.length == 0 || p.args[0] == null
                            || !p.args[0].getClass().getName().endsWith(".CameraRequestTag")) return;
                    if ("retro_camera_mode".equals(XposedHelpers.getObjectField(p.args[0], "mCaptureMode")))
                        ensureTintStage(p.args[0]);
                }
            });
        }
    }

    private static void ensureTintStage(Object tag) throws Exception {
        if (XposedHelpers.getIntField(tag, "mCyanMagentaValue") == 0
                || XposedHelpers.getBooleanField(tag, "mbFilterOpen")) return;
        // The natural look has no filter node. Supply a neutral LUT so the same native
        // output stage used by the other POP looks can apply the per-capture tint.
        File file = new File(AndroidAppHelper.currentApplication().getFilesDir(),
                "pop_port/meishe_lut/omnicam_tint_identity.bin");
        if (!file.isFile()) {
            int n = 32, offset = 48 + n * 4;
            ByteBuffer out = ByteBuffer.allocate(offset + n * n * n * 3).order(ByteOrder.LITTLE_ENDIAN);
            out.put(".MS-LUT ".getBytes(StandardCharsets.US_ASCII));
            for (int v : new int[] {1, n, 0, 1, 1, 0, 48, 0, offset, 0}) out.putInt(v);
            for (int i = 0; i < n; i++) out.putFloat(i / (float) (n - 1));
            for (int b = 0; b < n; b++) for (int g = 0; g < n; g++) for (int r = 0; r < n; r++) {
                out.put((byte) Math.round(r * 255f / (n - 1)));
                out.put((byte) Math.round(g * 255f / (n - 1)));
                out.put((byte) Math.round(b * 255f / (n - 1)));
            }
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), out.array());
        }
        XposedHelpers.setObjectField(tag, "mFilterType", file.getName());
        XposedHelpers.setBooleanField(tag, "mbFilterOpen", true);
        XposedHelpers.setIntField(tag, "mFilterIntensity", 100);
        Log.i("PopBackend", "neutral capture filter enables tint=" + XposedHelpers.getIntField(tag, "mCyanMagentaValue"));
    }
}
