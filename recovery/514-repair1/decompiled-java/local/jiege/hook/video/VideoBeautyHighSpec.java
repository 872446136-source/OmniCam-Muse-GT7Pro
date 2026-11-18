package local.jiege.hook.video;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class VideoBeautyHighSpec {
    private static final String BEAUTY = "feature_facebeauty_enable";
    private static final List<String> SPEC = Arrays.asList("pref_video_size_key", "pref_video_fps_key");
    private static volatile String mode;

    private VideoBeautyHighSpec() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean enabled() {
        return Settings.get("video_beauty_high_spec");
    }

    public static void installCamera(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        Method method = symbols.method("VideoMode.beautyBlocked");
        Method method2 = symbols.method("VideoMode.beautyMenuAllowed");
        Method method3 = symbols.method("ModeSwitch.switch");
        Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.configure.ProtobufFeatureInfoImpl", classLoader);
        XposedBridge.hookMethod(method3, new XC_MethodHook() { // from class: local.jiege.hook.video.VideoBeautyHighSpec.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object[] objArr = methodHookParam.args;
                if (objArr.length > 1) {
                    Object obj = objArr[1];
                    if (obj instanceof String) {
                        VideoBeautyHighSpec.mode = (String) obj;
                    }
                }
            }
        });
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.video.VideoBeautyHighSpec.2
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (VideoBeautyHighSpec.enabled()) {
                    methodHookParam.setResult(Boolean.FALSE);
                }
            }
        });
        XposedBridge.hookMethod(method2, new XC_MethodHook() { // from class: local.jiege.hook.video.VideoBeautyHighSpec.3
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (VideoBeautyHighSpec.enabled()) {
                    methodHookParam.setResult(Boolean.TRUE);
                }
            }
        });
        XposedBridge.hookAllMethods(clsFindClass, "getConflictFeatureList", new XC_MethodHook() { // from class: local.jiege.hook.video.VideoBeautyHighSpec.4
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (VideoBeautyHighSpec.enabled() && "commonVideo".equals(VideoBeautyHighSpec.mode) && (methodHookParam.getResult() instanceof List)) {
                    String strValueOf = String.valueOf(XposedHelpers.callMethod(methodHookParam.thisObject, "getFeatureKeyName", new Object[0]));
                    if (VideoBeautyHighSpec.BEAUTY.equals(strValueOf) || VideoBeautyHighSpec.SPEC.contains(strValueOf)) {
                        List list = (List) methodHookParam.getResult();
                        ArrayList arrayList = new ArrayList(list.size());
                        for (Object obj : list) {
                            String strValueOf2 = String.valueOf(XposedHelpers.callMethod(XposedHelpers.callMethod(obj, "getFeatureKey", new Object[0]), "getName", new Object[0]));
                            if (VideoBeautyHighSpec.BEAUTY.equals(strValueOf)) {
                                if (!VideoBeautyHighSpec.SPEC.contains(strValueOf2)) {
                                    arrayList.add(obj);
                                }
                            } else if (!VideoBeautyHighSpec.BEAUTY.equals(strValueOf2)) {
                                arrayList.add(obj);
                            }
                        }
                        if (arrayList.size() != list.size()) {
                            methodHookParam.setResult(arrayList);
                        }
                    }
                }
            }
        });
    }
}
