package local.jiege.hook.pop;

import android.app.AndroidAppHelper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import local.jiege.hook.common.Log;
import local.jiege.hook.filters.MasterLayout;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class PopPhotoBackend {
    private static final String MARKER = "omnicam.pop.photo_backend";

    /* JADX INFO: Access modifiers changed from: private */
    public static void ensureTintStage(Object obj) throws IOException {
        Object objectField = XposedHelpers.getObjectField(obj, "mFilterType");
        if (XposedHelpers.getIntField(obj, "mCyanMagentaValue") != 0) {
            if (!XposedHelpers.getBooleanField(obj, "mbFilterOpen") || !(objectField instanceof String) || ((String) objectField).isEmpty() || MasterLayout.STANDARD.equals(objectField)) {
                File file = new File(AndroidAppHelper.currentApplication().getFilesDir(), "pop_port/meishe_lut/omnicam_tint_identity.bin");
                if (!file.isFile()) {
                    ByteBuffer byteBufferOrder = ByteBuffer.allocate(98480).order(ByteOrder.LITTLE_ENDIAN);
                    byteBufferOrder.put(".MS-LUT ".getBytes(StandardCharsets.US_ASCII));
                    int[] iArr = {1, 32, 0, 1, 1, 0, 48, 0, 176, 0};
                    for (int i = 0; i < 10; i++) {
                        byteBufferOrder.putInt(iArr[i]);
                    }
                    for (int i2 = 0; i2 < 32; i2++) {
                        byteBufferOrder.putFloat(i2 / 31.0f);
                    }
                    for (int i3 = 0; i3 < 32; i3++) {
                        for (int i4 = 0; i4 < 32; i4++) {
                            for (int i5 = 0; i5 < 32; i5++) {
                                byteBufferOrder.put((byte) Math.round((i5 * 255.0f) / 31.0f));
                                byteBufferOrder.put((byte) Math.round((i4 * 255.0f) / 31.0f));
                                byteBufferOrder.put((byte) Math.round((i3 * 255.0f) / 31.0f));
                            }
                        }
                    }
                    file.getParentFile().mkdirs();
                    Files.write(file.toPath(), byteBufferOrder.array(), new OpenOption[0]);
                }
                XposedHelpers.setObjectField(obj, "mFilterType", file.getName());
                XposedHelpers.setBooleanField(obj, "mbFilterOpen", true);
                XposedHelpers.setIntField(obj, "mFilterIntensity", 100);
                Log.i("PopBackend", "neutral capture filter enables tint=" + XposedHelpers.getIntField(obj, "mCyanMagentaValue"));
            }
        }
    }

    public static void install(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.oplus.ocs.camera.producer.mode.ModeFactory", classLoader, "createMode", new Object[]{String.class, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopPhotoBackend.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Boolean bool = Boolean.TRUE;
                if (!bool.equals(methodHookParam.getObjectExtra(PopPhotoBackend.MARKER)) || methodHookParam.getResult() == null) {
                    return;
                }
                XposedHelpers.setAdditionalInstanceField(methodHookParam.getResult(), PopPhotoBackend.MARKER, bool);
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if ("retro_camera_mode".equals(methodHookParam.args[0])) {
                    methodHookParam.setObjectExtra(PopPhotoBackend.MARKER, Boolean.TRUE);
                    methodHookParam.args[0] = "photo_mode";
                    Log.i("PopBackend", "POP uses an independent stock photo session; capture checks remain enabled");
                }
            }
        }});
        XposedBridge.hookAllMethods(XposedHelpers.findClass("com.oplus.ocs.camera.producer.mode.PhotoMode", classLoader), "createRequestTag", new XC_MethodHook() { // from class: local.jiege.hook.pop.PopPhotoBackend.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException, IOException {
                if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(methodHookParam.thisObject, PopPhotoBackend.MARKER))) {
                    Object[] objArr = methodHookParam.args;
                    if (objArr.length == 6 && "before_take_picture".equals(objArr[3]) && methodHookParam.getResult() != null) {
                        XposedHelpers.setObjectField(methodHookParam.getResult(), "mCaptureMode", "retro_camera_mode");
                        Object objectField = XposedHelpers.getObjectField(methodHookParam.getResult(), "mApsRequestTag");
                        Object objNewInstance = XposedHelpers.newInstance(objectField.getClass(), new Object[0]);
                        for (Field field : objectField.getClass().getFields()) {
                            if (!Modifier.isStatic(field.getModifiers())) {
                                field.set(objNewInstance, field.get(objectField));
                            }
                        }
                        XposedHelpers.setObjectField(objNewInstance, "mModeName", "retro_camera_mode");
                        XposedHelpers.setObjectField(methodHookParam.getResult(), "mApsRequestTag", objNewInstance);
                        PopPhotoBackend.ensureTintStage(methodHookParam.getResult());
                        Log.i("PopBackend", "capture retains POP processing identity; tint=" + XposedHelpers.getIntField(methodHookParam.getResult(), "mCyanMagentaValue") + " filter=" + XposedHelpers.getObjectField(methodHookParam.getResult(), "mFilterType"));
                    }
                }
            }
        });
        Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.consumer.ApsProcessor", classLoader);
        String[] strArr = {"createMetaItemInfo", "getAlgoFromApp", "addPictureMeta"};
        for (int i = 0; i < 3; i++) {
            XposedBridge.hookAllMethods(clsFindClass, strArr[i], new XC_MethodHook() { // from class: local.jiege.hook.pop.PopPhotoBackend.3
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IOException {
                    Object obj;
                    Object[] objArr = methodHookParam.args;
                    if (objArr.length == 0 || (obj = objArr[0]) == null || !obj.getClass().getName().endsWith(".CameraRequestTag") || !"retro_camera_mode".equals(XposedHelpers.getObjectField(methodHookParam.args[0], "mCaptureMode"))) {
                        return;
                    }
                    PopPhotoBackend.ensureTintStage(methodHookParam.args[0]);
                }
            });
        }
    }
}
