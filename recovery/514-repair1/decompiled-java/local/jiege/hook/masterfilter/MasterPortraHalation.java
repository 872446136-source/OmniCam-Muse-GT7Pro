package local.jiege.hook.masterfilter;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import local.jiege.hook.common.Settings;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class MasterPortraHalation {
    private static final String SDK = "com.oplus.ocs.camera.";

    private MasterPortraHalation() {
    }

    public static void install(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.oplus.ocs.camera.producer.mode.BaseMode", classLoader, "setPortraFilterGrain", new Object[]{"com.oplus.ocs.camera.common.util.CameraRequestTag", new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterPortraHalation.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = methodHookParam.args[0];
                Object objectField = obj == null ? null : XposedHelpers.getObjectField(obj, "mFilterType");
                if (objectField instanceof String) {
                    String str = (String) objectField;
                    if (str.contains("portra400") && str.contains("master") && Settings.get("master_effect_portra")) {
                        XposedHelpers.setFloatField(obj, "mfFilterHighlightThresh", 1.0f);
                        XposedHelpers.setFloatField(obj, "mfFilterShadowThresh", 0.2f);
                        if (Settings.get("master_portra_physical")) {
                            XposedHelpers.setFloatField(obj, "mfFilterHighlightOpacity", 0.0f);
                        }
                    }
                }
            }
        }});
    }
}
