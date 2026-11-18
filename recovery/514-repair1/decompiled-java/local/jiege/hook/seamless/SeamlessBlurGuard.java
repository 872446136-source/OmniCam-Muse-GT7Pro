package local.jiege.hook.seamless;

import android.graphics.Bitmap;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import defpackage.id;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class SeamlessBlurGuard {
    private static final String TAG = "SeamlessBlur";

    private SeamlessBlurGuard() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookCrop(Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("BitmapUtil.cropCenterByScale"), new XC_MethodHook() { // from class: local.jiege.hook.seamless.SeamlessBlurGuard.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (methodHookParam.hasThrowable() || methodHookParam.getResult() != null) {
                    return;
                }
                Object obj = methodHookParam.args[0];
                if (obj instanceof Bitmap) {
                    methodHookParam.setResult(obj);
                    Log.i(SeamlessBlurGuard.TAG, "crop failed, keeping the uncropped bitmap");
                }
            }
        });
    }

    public static void installCamera(ClassLoader classLoader) {
        Log.guard(TAG, "center crop fallback", new id(Symbols.get(), (byte) 21));
    }
}
