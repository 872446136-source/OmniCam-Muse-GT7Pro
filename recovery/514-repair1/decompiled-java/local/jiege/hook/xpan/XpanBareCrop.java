package local.jiege.hook.xpan;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import local.jiege.hook.common.DataStore;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;
import org.json.JSONObject;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class XpanBareCrop {
    private static volatile boolean xpan;

    public static void install(final ClassLoader classLoader) {
        final Symbols symbols = Symbols.get();
        Method method = symbols.method("WatermarkModel.isOpen");
        Method method2 = symbols.method("WatermarkModel.styleNormal");
        Method method3 = symbols.method("WatermarkStyles.load");
        XposedBridge.hookMethod(symbols.method("ModeSwitch.switch"), new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanBareCrop.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                XpanBareCrop.xpan = "xpan".equals(methodHookParam.args[1]);
            }
        });
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanBareCrop.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if ("xpan".equals(methodHookParam.args[2])) {
                    Boolean bool = Boolean.TRUE;
                    if (!bool.equals(methodHookParam.args[1]) || methodHookParam.hasThrowable()) {
                        return;
                    }
                    methodHookParam.setResult(bool);
                }
            }
        });
        XposedBridge.hookMethod(method2, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanBareCrop.3
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (!XpanBareCrop.xpan || methodHookParam.hasThrowable()) {
                    return;
                }
                methodHookParam.setResult(Boolean.TRUE);
            }
        });
        XposedBridge.hookMethod(method3, new XC_MethodHook(10000) { // from class: local.jiege.hook.xpan.XpanBareCrop.4
            private Object openKey;
            private Object styleKey;

            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if ("xpan".equals(methodHookParam.args[0])) {
                    Boolean bool = Boolean.TRUE;
                    if (bool.equals(methodHookParam.args[3]) || methodHookParam.hasThrowable()) {
                        return;
                    }
                    if (this.openKey == null) {
                        this.openKey = DataStore.key(symbols, "Keys.styleId", "pref_ai_master_watermark_photo_open_state");
                        this.styleKey = DataStore.key(symbols, "Keys.styleId", "pref_ai_master_watermark_photo_style_id");
                    }
                    Object obj = DataStore.get(symbols, classLoader, this.openKey, Boolean.FALSE);
                    Object obj2 = DataStore.get(symbols, classLoader, this.styleKey, PresetSelection.STANDARD_KEY);
                    if (bool.equals(obj) && (obj2 instanceof String) && !((String) obj2).isEmpty()) {
                        return;
                    }
                    methodHookParam.setResult(new JSONObject("{\"styleId\":\"omnicam_xpan_crop\",\"baseImageSize\":291,\"size\":{\"leftMargin\":0,\"rightMargin\":0,\"topMargin\":0,\"bottomMargin\":0},\"imageOffset\":{\"startX\":0,\"startY\":0},\"background\":{\"backgroundType\":0,\"color\":\"#000000\"},\"bitmaps\":[]}"));
                }
            }
        });
        Log.i("XpanCrop", "crop stage remains active with visible watermark off");
    }
}
