package local.jiege.hook.pop;

import android.net.Uri;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/**
 * POP port: POP's Polaroid watermark (PLD) and its UI animations/guide images are not in this
 * camera APK. service.sh stages them under files/pop_port; these hooks point the camera there.
 */
final class PopResources {
    private static final String TAG = "PopPort";
    private static final String PLD_DIR = "/data/user/0/com.oplus.camera/files/pop_port/pld_watermark";
    private static final String APK_RESOURCES_DIR = "/data/user/0/com.oplus.camera/files/pop_port/apk_resources";

    private PopResources() {}

    static void install(ClassLoader classLoader) {
        File pld = new File(PLD_DIR, "pld");
        File pldJson = new File(pld, "pld.json");
        if (!pldJson.isFile()) {
            // Say what is actually there: service.sh stages this from the module payload, and the
            // 476 report could only say "missing" — which does not separate "the copy never ran"
            // from "it ran and left a dangling link" (cp -a keeps links, cp -aL follows them).
            Log.i(TAG, "POP PLD resources missing: " + pldJson + " " + describe(pld));
            return;
        }
        XposedHelpers.setStaticObjectField(XposedHelpers.findClass(
            "com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkJsonReader$AiMasterWatermarkResPath", classLoader),
            "RETRO_LIMIT_RES_PATH", pld.getAbsolutePath());
        Symbols symbols = Symbols.get();
        try {
            symbols.set("PldPath.dir", null, PLD_DIR);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }

        // ApkResources.uri(directory, name) resolves APK resource files: retro animations (json) and guide images (webp).
        XposedBridge.hookMethod(symbols.method("ApkResources.uri"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                String directory = (String) param.args[0];
                String name = (String) param.args[1];
                if (name == null) return;
                boolean animation = name.endsWith(".json")
                    && (name.startsWith("filter_retro_") || name.equals("retro_mode_title_selected_anim.json"));
                boolean guide = name.startsWith("retro_") && name.endsWith(".webp");
                if (!animation && !guide) return;
                if (directory != null && (!guide || !directory.endsWith("/apk_resources/img/"))) return;
                File file = new File(new File(APK_RESOURCES_DIR, animation ? "json" : "img"), name);
                if (file.isFile()) param.setResult(Uri.fromFile(file));
            }
        });
    }

    /** {@code absent} / {@code a link to …} / the first few entries — enough to tell the cases apart. */
    private static String describe(File dir) {
        if (!dir.exists()) return "(" + dir + " is absent)";
        if (!dir.isDirectory()) return "(" + dir + " is not a directory)";
        String[] names = dir.list();
        if (names == null) return "(" + dir + " cannot be listed)";
        if (names.length == 0) return "(" + dir + " is empty)";
        StringBuilder out = new StringBuilder("(").append(dir).append(" holds ")
            .append(names.length).append(": ");
        for (int i = 0; i < Math.min(names.length, 6); i++) {
            if (i > 0) out.append(", ");
            out.append(names[i]);
        }
        return out.append(names.length > 6 ? ", …)" : ")").toString();
    }
}
