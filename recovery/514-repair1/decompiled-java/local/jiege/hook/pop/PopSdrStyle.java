package local.jiege.hook.pop;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class PopSdrStyle {
    private static final String CAPTURE_HDR_MODES = "com.oplus.camera.capture.hdr.cap.mode.value";
    private static final String POP_MODE = "retroCamera";
    private static final String TAG = "PopPort";

    private PopSdrStyle() {
    }

    public static void install(ClassLoader classLoader) {
        XposedBridge.hookMethod(Symbols.get().method("CameraConfig.list"), new XC_MethodHook() { // from class: local.jiege.hook.pop.PopSdrStyle.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (PopSdrStyle.CAPTURE_HDR_MODES.equals(methodHookParam.args[0]) && (methodHookParam.getResult() instanceof List) && Settings.get("pop_sdr_style")) {
                    List list = (List) methodHookParam.getResult();
                    if (list.contains(PopSdrStyle.POP_MODE)) {
                        ArrayList arrayList = new ArrayList(list);
                        arrayList.remove(PopSdrStyle.POP_MODE);
                        methodHookParam.setResult(arrayList);
                    }
                }
            }
        });
        Log.i(TAG, "POP SDR style hook installed");
    }
}
