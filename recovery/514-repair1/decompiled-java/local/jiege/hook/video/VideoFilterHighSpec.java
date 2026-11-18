package local.jiege.hook.video;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Reflection;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class VideoFilterHighSpec {
    private static final String FILTER_KEY = "feature_filter_index";
    private static final String MODE = "commonVideo";
    private static final String NEON = "oplus_video_filter_neon";
    private static final String RETENTION = "oplus_video_filter_portrait_retention";
    private static final List<String> SPEC_KEYS = Arrays.asList("pref_video_fps_key", "pref_video_size_key");
    private static final String TAG = "VideoFilterHighSpec";
    private static Object aiKey;
    private static volatile String currentMode;
    private static Object fpsKey;
    private static Object sizeKey;
    private static Symbols symbols;

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Spec {
        boolean ai;
        int fps;
        String size;

        private Spec() {
        }

        public static Spec read(Object obj) {
            Object objCall = VideoFilterHighSpec.symbols.call("FeaturePresenter.feature", obj, "com.oplus.camera.feature.video_size", VideoFilterHighSpec.sizeKey, PresetSelection.STANDARD_KEY);
            Object objCall2 = VideoFilterHighSpec.symbols.call("FeaturePresenter.feature", obj, "com.oplus.configure.video.fps", VideoFilterHighSpec.fpsKey, 30);
            Object objCall3 = VideoFilterHighSpec.symbols.call("FeaturePresenter.feature", obj, "com.oplus.camera.feature.ai_enhancement_video", VideoFilterHighSpec.aiKey, Boolean.FALSE);
            if (!(objCall2 instanceof Integer) || !(objCall3 instanceof Boolean)) {
                return null;
            }
            Spec spec = new Spec();
            spec.size = String.valueOf(objCall);
            spec.fps = ((Integer) objCall2).intValue();
            spec.ai = ((Boolean) objCall3).booleanValue();
            return spec;
        }

        public boolean allowed() {
            if (this.ai || this.fps > 60) {
                return false;
            }
            return is4k() || "video_size_1080p".equals(this.size);
        }

        public boolean is4k() {
            return "video_size_4kuhd".equals(this.size);
        }
    }

    private VideoFilterHighSpec() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean enabled() {
        return Settings.get("video_filter_high_spec");
    }

    private static void install(ClassLoader classLoader) {
        Symbols symbols2 = Symbols.get();
        symbols = symbols2;
        Class<?> cls = symbols2.cls("FeatureKeys");
        Class<?> cls2 = symbols.cls("FeatureKey");
        sizeKey = Reflection.findFeatureKey(cls, cls2, "getVideoSizeFromSp");
        fpsKey = Reflection.findFeatureKey(cls, cls2, "getVideoFpsFromSp");
        aiKey = Reflection.findFeatureKey(cls, cls2, "isAiEnhancementVideoOpen");
        final int staticIntField = XposedHelpers.getStaticIntField(XposedHelpers.findClass("com.oplus.camera.filter.FilterUtil", classLoader), "sNoneIndex");
        XposedBridge.hookAllMethods(XposedHelpers.findClass("com.oplus.ocs.camera.configure.ProtobufFeatureInfoImpl", classLoader), "getConflictFeatureList", new XC_MethodHook() { // from class: local.jiege.hook.video.VideoFilterHighSpec.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if ((methodHookParam.getResult() instanceof List) && VideoFilterHighSpec.MODE.equals(VideoFilterHighSpec.currentMode) && VideoFilterHighSpec.enabled()) {
                    String strValueOf = String.valueOf(XposedHelpers.callMethod(methodHookParam.thisObject, "getFeatureKeyName", new Object[0]));
                    boolean zEquals = VideoFilterHighSpec.FILTER_KEY.equals(strValueOf);
                    if (zEquals || VideoFilterHighSpec.SPEC_KEYS.contains(strValueOf)) {
                        List list = (List) methodHookParam.getResult();
                        ArrayList arrayList = new ArrayList(list.size());
                        for (Object obj : list) {
                            String strValueOf2 = String.valueOf(XposedHelpers.callMethod(XposedHelpers.callMethod(obj, "getFeatureKey", new Object[0]), "getName", new Object[0]));
                            if (!(zEquals ? VideoFilterHighSpec.SPEC_KEYS.contains(strValueOf2) : VideoFilterHighSpec.FILTER_KEY.equals(strValueOf2))) {
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
        XposedBridge.hookMethod(symbols.method("FilterPresenter.rebuildVideo"), new XC_MethodHook() { // from class: local.jiege.hook.video.VideoFilterHighSpec.2
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException {
                Spec spec;
                int iIntValue;
                String strJ;
                Object obj = methodHookParam.thisObject;
                VideoFilterHighSpec.currentMode = VideoFilterHighSpec.mode(obj);
                if (!VideoFilterHighSpec.MODE.equals(VideoFilterHighSpec.currentMode) || !VideoFilterHighSpec.enabled() || (spec = Spec.read(obj)) == null || !spec.allowed() || (strJ = VideoFilterHighSpec.j((iIntValue = ((Integer) VideoFilterHighSpec.symbols.call("FilterPresenter.currentIndex", obj, new Object[0])).intValue()), obj)) == null || VideoFilterHighSpec.RETENTION.equals(strJ) || VideoFilterHighSpec.NEON.equals(strJ)) {
                    return;
                }
                Object objCall = VideoFilterHighSpec.symbols.call("FilterPresenter.previewRequest", obj, new Object[0]);
                Boolean bool = Boolean.FALSE;
                XposedHelpers.callMethod(objCall, "setVideoRetentionState", new Object[]{bool});
                XposedHelpers.callMethod(objCall, "setVideoNeonState", new Object[]{bool});
                VideoFilterHighSpec.symbols.field("FilterPresenter.videoIndex").setInt(obj, iIntValue);
                methodHookParam.setResult((Object) null);
            }
        });
        XposedBridge.hookMethod(symbols.method("FilterPresenter.select"), new XC_MethodHook() { // from class: local.jiege.hook.video.VideoFilterHighSpec.3
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Integer num;
                int iIntValue;
                String strJ;
                Spec spec;
                Object obj = methodHookParam.thisObject;
                VideoFilterHighSpec.currentMode = VideoFilterHighSpec.mode(obj);
                if (VideoFilterHighSpec.MODE.equals(VideoFilterHighSpec.currentMode) && VideoFilterHighSpec.enabled() && (iIntValue = (num = (Integer) methodHookParam.args[0]).intValue()) > 0 && iIntValue > staticIntField && ((Integer) VideoFilterHighSpec.symbols.call("FilterPresenter.currentIndex", obj, new Object[0])).intValue() == iIntValue && (strJ = VideoFilterHighSpec.j(iIntValue, obj)) != null && strJ.endsWith(".bin") && (spec = Spec.read(obj)) != null && spec.allowed()) {
                    if (spec.is4k() || spec.fps == 60) {
                        VideoFilterHighSpec.symbols.call("FilterPresenter.apply", obj, num, VideoFilterHighSpec.symbols.call("FilterPresenter.softLightIndex", obj, new Object[0]), VideoFilterHighSpec.symbols.call("FilterPresenter.styleIndex", obj, new Object[0]), VideoFilterHighSpec.symbols.call("FilterPresenter.groupIndex", obj, new Object[0]));
                        VideoFilterHighSpec.symbols.call("FeaturePresenter.resetState", obj, new Object[0]);
                    }
                }
            }
        });
    }

    public static void installCamera(ClassLoader classLoader) {
        try {
            install(classLoader);
        } catch (Throwable th) {
            Log.e(TAG, "video filter high spec unavailable", th);
        }
    }

    public static /* bridge */ /* synthetic */ String j(int i, Object obj) {
        return type(obj, i);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String mode(Object obj) {
        return String.valueOf(symbols.get("FeaturePresenter.modeName", obj));
    }

    private static String type(Object obj, int i) {
        Object objCall = symbols.call("FilterPresenter.types", obj, new Object[0]);
        if (!(objCall instanceof List) || i < 0) {
            return null;
        }
        List list = (List) objCall;
        if (i >= list.size()) {
            return null;
        }
        return String.valueOf(list.get(i));
    }
}
