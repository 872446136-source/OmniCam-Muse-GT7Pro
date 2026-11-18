package local.jiege.hook.masterlut;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.hd;
import defpackage.id;
import defpackage.t2;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.FilterScope;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class MasterLutSlots {
    private static final String COMBINER = "com.oplus.ocs.camera.CombineLutHelper";
    private static final String TAG = "MuseLutSlots";
    private static volatile Object lut3d;
    private static volatile String previewMode;
    private static final int[] values = new int[Param.values().length];

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public enum Param {
        CONTRAST,
        FX_BLUE
    }

    private MasterLutSlots() {
    }

    public static int get(Param param) {
        int i;
        int[] iArr = values;
        synchronized (iArr) {
            i = iArr[param.ordinal()];
        }
        return i;
    }

    public static void installCamera(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        Log.guard(TAG, "parameters", new t2((byte) 23));
        Log.guard(TAG, "preview mode", new id(symbols, (byte) 20));
        Log.guard(TAG, "preview table", new hd(classLoader, (byte) 13));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$0() {
        nativeSetParams(snapshot());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$1(Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("Lut3d.load"), new XC_MethodHook() { // from class: local.jiege.hook.masterlut.MasterLutSlots.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                MasterLutSlots.lut3d = methodHookParam.thisObject;
                MasterLutSlots.previewMode = (String) methodHookParam.args[2];
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$2(ClassLoader classLoader) {
        XposedBridge.hookAllMethods(XposedHelpers.findClass(COMBINER, classLoader), "process", new XC_MethodHook() { // from class: local.jiege.hook.masterlut.MasterLutSlots.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                int i = 0;
                int[] iArr = null;
                for (Object obj : methodHookParam.args) {
                    if (obj instanceof int[]) {
                        iArr = (int[]) obj;
                    }
                }
                Object result = methodHookParam.getResult();
                float[] fArr = result instanceof float[] ? (float[]) result : null;
                if (iArr != null && iArr.length > 0) {
                    i = iArr[0];
                }
                MasterLutSlots.nativePreviewEnd(fArr, i);
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                MasterLutSlots.nativePreviewBegin(FilterScope.MASTER_MODE.equals(MasterLutSlots.previewMode));
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static native void nativePreviewBegin(boolean z);

    /* JADX INFO: Access modifiers changed from: private */
    public static native void nativePreviewEnd(float[] fArr, int i);

    private static native void nativeSetParams(int[] iArr);

    public static void set(Param param, int i) {
        int iMax = Math.max(-100, Math.min(100, i));
        int[] iArr = values;
        synchronized (iArr) {
            try {
                if (iArr[param.ordinal()] == iMax) {
                    return;
                }
                iArr[param.ordinal()] = iMax;
                nativeSetParams(snapshot());
                Object obj = lut3d;
                if (obj == null || !FilterScope.MASTER_MODE.equals(previewMode)) {
                    return;
                }
                try {
                    Symbols.get().call("Lut3d.update", obj, new Object[0]);
                } catch (Throwable th) {
                    Log.e(TAG, "preview recombination failed", th);
                }
            } catch (Throwable th2) {
                throw th2;
            }
        }
    }

    private static int[] snapshot() {
        int[] iArr;
        int[] iArr2 = values;
        synchronized (iArr2) {
            iArr = (int[]) iArr2.clone();
        }
        return iArr;
    }
}
