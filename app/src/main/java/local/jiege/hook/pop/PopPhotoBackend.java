package local.jiege.hook.pop;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import local.jiege.hook.common.Log;

/** RMX5010's retro SDK session never supplies a usable preview decision. */
final class PopPhotoBackend {
    static void install(ClassLoader loader) {
        XposedHelpers.findAndHookMethod("com.oplus.ocs.camera.producer.mode.ModeFactory", loader,
                "createMode", String.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if ("retro_camera_mode".equals(param.args[0])) {
                            param.args[0] = "photo_mode";
                            Log.i("PopBackend", "POP uses an independent stock photo session; capture checks remain enabled");
                        }
                    }
                });
    }
}
