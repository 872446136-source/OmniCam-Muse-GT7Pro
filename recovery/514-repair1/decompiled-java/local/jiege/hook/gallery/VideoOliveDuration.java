package local.jiege.hook.gallery;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class VideoOliveDuration {
    private static final ThreadLocal<Boolean> EDITOR = new ThreadLocal<>();

    private VideoOliveDuration() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean enabled() {
        return Settings.get("video_olive_unlimited");
    }

    public static void installGallery(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        XposedBridge.hookMethod(symbols.method("Config.getInt"), new XC_MethodHook() { // from class: local.jiege.hook.gallery.VideoOliveDuration.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (VideoOliveDuration.enabled() && "video_editor_olive_save_max_duration".equals(methodHookParam.args[0])) {
                    methodHookParam.setResult(86400);
                }
            }
        });
        if (symbols.has("OliveExportLimit.maxMs", "ExportOliveTask.run", "ExportOliveTask.isMenuExport")) {
            Method method = symbols.method("ExportOliveTask.run");
            Method method2 = symbols.method("OliveExportLimit.maxMs");
            final Field field = symbols.field("ExportOliveTask.isMenuExport");
            XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.gallery.VideoOliveDuration.2
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Boolean bool = (Boolean) methodHookParam.getObjectExtra("omnicam.previousEditor");
                    if (bool == null) {
                        VideoOliveDuration.EDITOR.remove();
                    } else {
                        VideoOliveDuration.EDITOR.set(bool);
                    }
                }

                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    methodHookParam.setObjectExtra("omnicam.previousEditor", VideoOliveDuration.EDITOR.get());
                    VideoOliveDuration.EDITOR.set(Boolean.valueOf(VideoOliveDuration.enabled() && !field.getBoolean(methodHookParam.thisObject)));
                }
            });
            XposedBridge.hookMethod(method2, new XC_MethodHook() { // from class: local.jiege.hook.gallery.VideoOliveDuration.3
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (Boolean.TRUE.equals(VideoOliveDuration.EDITOR.get())) {
                        methodHookParam.setResult(86400000L);
                    }
                }
            });
        }
        if (symbols.has("TopActionbarSection.oliveDescription")) {
            XposedBridge.hookMethod(symbols.method("TopActionbarSection.oliveDescription"), new XC_MethodHook() { // from class: local.jiege.hook.gallery.VideoOliveDuration.4
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (VideoOliveDuration.enabled()) {
                        methodHookParam.setResult("支持导出最长 24 小时的实况照片");
                    }
                }
            });
        }
    }
}
