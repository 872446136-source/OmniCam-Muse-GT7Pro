package local.jiege.hook.pop;

import android.net.Uri;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import local.jiege.hook.common.Capabilities;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/**
 * POP port: POP's Polaroid watermark (PLD) and its UI animations/guide images are not in this
 * camera APK. post-fs-data.sh stages them into the merged basictone tree, and service.sh keeps a
 * copy in the camera's private files for the case where KernelSU unmounts the module for the camera
 * process. {@link Capabilities#popRoot()} picks whichever is reachable and these hooks point there.
 *
 * <p>The staging used to be done by service.sh into the camera's private files only. That never ran
 * in time: the private directory does not exist until the user unlocks and service.sh runs before
 * that, so every report from 475 to 482 said "POP tree is absent".
 */
final class PopResources {
    private static final String TAG = "PopPort";

    private PopResources() {}

    static void install(ClassLoader classLoader) {
        // Resolved, not hardcoded: the merged tree is a module mount and KernelSU can unmount it for
        // the camera alone, in which case service.sh's copy under the camera's own files is used.
        String root = Capabilities.popRoot();
        File pld = new File(new File(root, "pld_watermark"), "pld");
        File pldJson = new File(pld, "pld.json");
        if (!pldJson.isFile()) {
            // Say what is actually there. "missing" alone cannot separate "post-fs-data.sh did not
            // reach the block" from "it ran and the payload was absent", and those two need
            // different fixes.
            Log.i(TAG, "POP PLD resources missing: " + pldJson + " " + describe(pld));
            Log.i(TAG, "POP tree: " + describe(new File(root)));
            Log.i(TAG, "POP root in use: " + root + " (" + Capabilities.dataLayerNote() + "); "
                + "check bind.log in the module directory if this stays absent");
            return;
        }
        XposedHelpers.setStaticObjectField(XposedHelpers.findClass(
            "com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkJsonReader$AiMasterWatermarkResPath", classLoader),
            "RETRO_LIMIT_RES_PATH", pld.getAbsolutePath());
        Symbols symbols = Symbols.get();
        try {
            symbols.set("PldPath.dir", null, new File(root, "pld_watermark").getAbsolutePath());
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }

        // ApkResources.uri(directory, name) resolves APK resource files: retro animations (json) and guide images (webp).
        final File apkResources = new File(root, "apk_resources");
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
                File file = new File(new File(apkResources, animation ? "json" : "img"), name);
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
