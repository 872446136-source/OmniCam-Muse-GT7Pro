package local.jiege.hook.softlight;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.c3;
import defpackage.id;
import defpackage.kg0;
import java.lang.reflect.Field;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.common.ZoomFocal;
import local.jiege.hook.filters.FilterScope;
import local.jiege.hook.softlight.SoftLight;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class SoftLight {
    private static final String TAG = "MuseSoftLight";
    private static final String ZOOM_BAR = "com.oplus.camera.feature.zoom.view.ZoomSeekBar";

    private SoftLight() {
    }

    public static void installCamera(ClassLoader classLoader) {
        final Symbols symbols = Symbols.get();
        Log.guard(TAG, "soft light focal length at 1x", new kg0(classLoader, symbols));
        final Field field = symbols.has("FeaturePresenter.modeName") ? symbols.field("FeaturePresenter.modeName") : null;
        String[] strArr = {"FilterPresenter.apply", "FilterPresenter.captureRequest"};
        for (int i = 0; i < 2; i++) {
            final String str = strArr[i];
            Log.guard(TAG, c3.u("soft light mode at ", str), new Log.Installer() { // from class: je1
                @Override // local.jiege.hook.common.Log.Installer
                public final void install() {
                    SoftLight.lambda$installCamera$1(symbols, str, field);
                }
            });
        }
        Log.guard(TAG, "soft light focal length", new id(symbols, (byte) 22));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$0(ClassLoader classLoader, final Symbols symbols) {
        XposedBridge.hookAllConstructors(XposedHelpers.findClass(ZOOM_BAR, classLoader), new XC_MethodHook() { // from class: local.jiege.hook.softlight.SoftLight.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                SoftLight.nativeSetLevels(Settings.get("soft_light_weak_hdf"), Settings.get("soft_light_medium_black_mist"), Settings.get("soft_light_strong_white_mist"));
                SoftLight.nativeSetFocal(ZoomFocal.millimetres(symbols, 1.0f));
                SoftLight.nativeSetNoLocalContrast(Settings.get("soft_light_no_local_contrast"));
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$1(Symbols symbols, String str, final Field field) {
        XposedBridge.hookMethod(symbols.method(str), new XC_MethodHook() { // from class: local.jiege.hook.softlight.SoftLight.2
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                boolean zEquals;
                Field field2 = field;
                if (field2 != null) {
                    try {
                        zEquals = FilterScope.MASTER_MODE.equals(field2.get(methodHookParam.thisObject));
                    } catch (Throwable unused) {
                        zEquals = false;
                    }
                } else {
                    zEquals = false;
                }
                SoftLight.nativeSetMasterMode(zEquals);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$2(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("Zoom.setZoom"), new XC_MethodHook() { // from class: local.jiege.hook.softlight.SoftLight.3
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                SoftLight.nativeSetFocal(ZoomFocal.millimetres(symbols, ((Float) methodHookParam.args[0]).floatValue()));
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static native void nativeSetFocal(float f);

    /* JADX INFO: Access modifiers changed from: private */
    public static native void nativeSetLevels(boolean z, boolean z2, boolean z3);

    /* JADX INFO: Access modifiers changed from: private */
    public static native void nativeSetMasterMode(boolean z);

    /* JADX INFO: Access modifiers changed from: private */
    public static native void nativeSetNoLocalContrast(boolean z);
}
