package local.jiege.hook.shutter;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class CameraSoundForced {
    private static final String TAG = "CameraSoundForced";

    private CameraSoundForced() {
    }

    public static void installSystem(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("com.android.server.audio.AudioService", classLoader, "readCameraSoundForced", new Object[]{XC_MethodReplacement.returnConstant(Boolean.TRUE)});
            Log.i(TAG, "installed");
        } catch (Throwable th) {
            Log.e(TAG, "camera sound forced hook unavailable", th);
        }
    }
}
