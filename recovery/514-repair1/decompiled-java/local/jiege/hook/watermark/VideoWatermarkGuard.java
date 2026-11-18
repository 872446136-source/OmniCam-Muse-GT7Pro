package local.jiege.hook.watermark;

import android.os.Bundle;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import defpackage.q51;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class VideoWatermarkGuard {
    private static final String TAG = "VideoWatermarkGuard";

    private VideoWatermarkGuard() {
    }

    public static void installCamera(ClassLoader classLoader) {
        Log.guard(TAG, "video watermark without a bitmap", new q51((byte) 11));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$0() {
        XposedBridge.hookMethod(Symbols.get().method("WatermarkYuv.bundle"), new XC_MethodHook() { // from class: local.jiege.hook.watermark.VideoWatermarkGuard.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object result = methodHookParam.getResult();
                if ((result instanceof Bundle) && ((Bundle) result).getByteArray("buffer") == null) {
                    Log.i(VideoWatermarkGuard.TAG, "orientation " + methodHookParam.args[0] + " has no watermark bitmap, recording without it");
                    methodHookParam.setResult((Object) null);
                }
            }
        });
    }
}
