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
import local.jiege.settings.ModuleSettings;

/** Removes the ordinary video beauty size/fps gates, independently of the filter switch. */
public final class VideoBeautyHighSpec {
    private static final List<String> SPEC = Arrays.asList("pref_video_size_key", "pref_video_fps_key");
    private static final String BEAUTY = "feature_facebeauty_enable";
    private static volatile String mode;
    private VideoBeautyHighSpec() {}
    public static void installCamera(ClassLoader loader) {
        Symbols s = Symbols.get();
        Method blocked = s.method("VideoMode.beautyBlocked"), menu = s.method("VideoMode.beautyMenuAllowed");
        Method switchMode = s.method("ModeSwitch.switch");
        Class<?> info = XposedHelpers.findClass("com.oplus.ocs.camera.configure.ProtobufFeatureInfoImpl", loader);
        XposedBridge.hookMethod(switchMode, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length > 1 && p.args[1] instanceof String) mode = (String) p.args[1];
            }
        });
        XposedBridge.hookMethod(blocked, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if (enabled()) p.setResult(false); }
        });
        XposedBridge.hookMethod(menu, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if (enabled()) p.setResult(true); }
        });
        XposedBridge.hookAllMethods(info, "getConflictFeatureList", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!enabled() || !"commonVideo".equals(mode) || !(p.getResult() instanceof List)) return;
                String from = String.valueOf(XposedHelpers.callMethod(p.thisObject, "getFeatureKeyName"));
                if (!BEAUTY.equals(from) && !SPEC.contains(from)) return;
                List<?> original = (List<?>) p.getResult();
                List<Object> kept = new ArrayList<>(original.size());
                for (Object item : original) {
                    String to = String.valueOf(XposedHelpers.callMethod(XposedHelpers.callMethod(item, "getFeatureKey"), "getName"));
                    if (!(BEAUTY.equals(from) ? SPEC.contains(to) : BEAUTY.equals(to))) kept.add(item);
                }
                if (kept.size() != original.size()) p.setResult(kept);
            }
        });
    }
    private static boolean enabled() { return Settings.get(ModuleSettings.VIDEO_BEAUTY_HIGH_SPEC); }
}
