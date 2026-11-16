package local.jiege.hook.gallery;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.settings.ModuleSettings;

/** Opus 2.0's 24-hour bound, limited to exports from the video editor. */
public final class VideoOliveDuration {
    private static final ThreadLocal<Boolean> EDITOR = new ThreadLocal<>();
    private VideoOliveDuration() {}
    public static void installGallery(ClassLoader loader) {
        Symbols s = Symbols.get();
        Method config = s.method("Config.getInt");
        XposedBridge.hookMethod(config, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (enabled() && "video_editor_olive_save_max_duration".equals(p.args[0])) p.setResult(86400);
            }
        });
        // Gallery 17.10.41 exports the selected start/end directly in CutVideoTask.
        // It uses the config gate and has no extra maxMs helper or description getter.
        if (s.has("OliveExportLimit.maxMs", "ExportOliveTask.run", "ExportOliveTask.isMenuExport")) {
        Method run = s.method("ExportOliveTask.run"), max = s.method("OliveExportLimit.maxMs");
        Field menu = s.field("ExportOliveTask.isMenuExport");
        XposedBridge.hookMethod(run, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                p.setObjectExtra("omnicam.previousEditor", EDITOR.get());
                EDITOR.set(enabled() && !menu.getBoolean(p.thisObject));
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Boolean previous = (Boolean) p.getObjectExtra("omnicam.previousEditor");
                if (previous == null) EDITOR.remove(); else EDITOR.set(previous);
            }
        });
        XposedBridge.hookMethod(max, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (Boolean.TRUE.equals(EDITOR.get())) p.setResult(86400000L);
            }
        });
        }
        if (s.has("TopActionbarSection.oliveDescription")) XposedBridge.hookMethod(s.method("TopActionbarSection.oliveDescription"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (enabled()) p.setResult("支持导出最长 24 小时的实况照片");
            }
        });
    }
    private static boolean enabled() { return Settings.get(ModuleSettings.VIDEO_OLIVE_UNLIMITED); }
}
