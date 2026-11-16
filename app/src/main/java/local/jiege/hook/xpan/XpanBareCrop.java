package local.jiege.hook.xpan;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import local.jiege.hook.common.DataStore;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import org.json.JSONObject;

/** Keep the XPAN crop stage running when the user has disabled visible watermark artwork. */
final class XpanBareCrop {
    private static volatile boolean xpan;
    static void install(ClassLoader loader) throws Exception {
        Symbols symbols = Symbols.get();
        XposedBridge.hookMethod(symbols.method("ModeSwitch.switch"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                xpan = "xpan".equals(p.args[1]);
            }
        });
        Class<?> model = XposedHelpers.findClass("zk.c", loader);
        XposedHelpers.findAndHookMethod(model, "m", boolean.class, boolean.class, String.class,
                long.class, String.class, boolean[].class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if ("xpan".equals(p.args[2]) && Boolean.TRUE.equals(p.args[1]) && !p.hasThrowable())
                    p.setResult(Boolean.TRUE);
            }
        });
        XposedHelpers.findAndHookMethod(model, "e", String.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (xpan && !p.hasThrowable()) p.setResult(Boolean.TRUE);
            }
        });
        XposedBridge.hookMethod(symbols.method("WatermarkStyles.load"), new XC_MethodHook(10000) {
            private Object openKey;
            private Object styleKey;
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                if (!"xpan".equals(p.args[0]) || Boolean.TRUE.equals(p.args[3]) || p.hasThrowable()) return;
                if (openKey == null) {
                    openKey = DataStore.key(symbols, "Keys.styleId", "pref_ai_master_watermark_photo_open_state");
                    styleKey = DataStore.key(symbols, "Keys.styleId", "pref_ai_master_watermark_photo_style_id");
                }
                Object enabled = DataStore.get(symbols, loader, openKey, Boolean.FALSE);
                Object style = DataStore.get(symbols, loader, styleKey, "");
                if (Boolean.TRUE.equals(enabled) && style instanceof String && !((String) style).isEmpty()) return;
                // No text, logo or padding. This transient style is never saved to camera settings.
                p.setResult(new JSONObject("{\"styleId\":\"omnicam_xpan_crop\",\"baseImageSize\":291,"
                        + "\"size\":{\"leftMargin\":0,\"rightMargin\":0,\"topMargin\":0,\"bottomMargin\":0},"
                        + "\"imageOffset\":{\"startX\":0,\"startY\":0},"
                        + "\"background\":{\"backgroundType\":0,\"color\":\"#000000\"},\"bitmaps\":[]}"));
            }
        });
        Log.i("XpanCrop", "crop stage remains active with visible watermark off");
    }
}
