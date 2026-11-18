package local.jiege.hook.pop;

import android.net.Uri;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import local.jiege.hook.common.Capabilities;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class PopResources {
    private static final String TAG = "PopPort";

    private PopResources() {
    }

    private static String describe(File file) {
        if (!file.exists()) {
            return "(" + file + " is absent)";
        }
        if (!file.isDirectory()) {
            return "(" + file + " is not a directory)";
        }
        String[] list = file.list();
        if (list == null) {
            return "(" + file + " cannot be listed)";
        }
        if (list.length == 0) {
            return "(" + file + " is empty)";
        }
        StringBuilder sb = new StringBuilder("(");
        sb.append(file);
        sb.append(" holds ");
        sb.append(list.length);
        sb.append(": ");
        for (int i = 0; i < Math.min(list.length, 6); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list[i]);
        }
        sb.append(list.length > 6 ? ", …)" : ")");
        return sb.toString();
    }

    public static void install(ClassLoader classLoader) {
        String strPopRoot = Capabilities.popRoot();
        File file = new File(new File(strPopRoot, "pld_watermark"), "pld");
        File file2 = new File(file, "pld.json");
        if (file2.isFile()) {
            XposedHelpers.setStaticObjectField(XposedHelpers.findClass("com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkJsonReader$AiMasterWatermarkResPath", classLoader), "RETRO_LIMIT_RES_PATH", file.getAbsolutePath());
            Symbols symbols = Symbols.get();
            try {
                symbols.set("PldPath.dir", null, new File(strPopRoot, "pld_watermark").getAbsolutePath());
                final File file3 = new File(strPopRoot, "apk_resources");
                XposedBridge.hookMethod(symbols.method("ApkResources.uri"), new XC_MethodHook() { // from class: local.jiege.hook.pop.PopResources.1
                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Object[] objArr = methodHookParam.args;
                        boolean z = false;
                        String str = (String) objArr[0];
                        String str2 = (String) objArr[1];
                        if (str2 == null) {
                            return;
                        }
                        boolean z2 = str2.endsWith(".json") && (str2.startsWith("filter_retro_") || str2.equals("retro_mode_title_selected_anim.json"));
                        if (str2.startsWith("retro_") && str2.endsWith(".webp")) {
                            z = true;
                        }
                        if (z2 || z) {
                            if (str == null || (z && str.endsWith("/apk_resources/img/"))) {
                                File file4 = new File(new File(file3, z2 ? "json" : "img"), str2);
                                if (file4.isFile()) {
                                    methodHookParam.setResult(Uri.fromFile(file4));
                                }
                            }
                        }
                    }
                });
                return;
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        Log.i(TAG, "POP PLD resources missing: " + file2 + " " + describe(file));
        StringBuilder sb = new StringBuilder("POP tree: ");
        sb.append(describe(new File(strPopRoot)));
        Log.i(TAG, sb.toString());
        Log.i(TAG, "POP root in use: " + strPopRoot + " (" + Capabilities.dataLayerNote() + "); check bind.log in the module directory if this stays absent");
    }
}
