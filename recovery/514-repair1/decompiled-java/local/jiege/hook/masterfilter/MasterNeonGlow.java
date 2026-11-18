package local.jiege.hook.masterfilter;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import local.jiege.hook.common.Settings;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class MasterNeonGlow {
    private static final String MASTER_800T = "800t_master.bin";
    private static final String NORMAL_800T = "800t.bin";
    private static final String SDK = "com.oplus.ocs.camera.";

    private MasterNeonGlow() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void applyNormal800t(Object obj, Class<?> cls, Class<?> cls2) {
        float[] fArr = (float[]) XposedHelpers.getStaticObjectField(cls2, "GRAIN_PARAM_800T");
        float[] fArr2 = (float[]) XposedHelpers.getStaticObjectField(cls, "FILTER_800T_GLOW_THRESH_ARRAY");
        XposedHelpers.setFloatField(obj, "mbFilterGainAmount", fArr[0]);
        XposedHelpers.setFloatField(obj, "mbFilterGainHighlight", 1.0f);
        XposedHelpers.setFloatField(obj, "mbFilterGainSize", 0.42f);
        XposedHelpers.setFloatField(obj, "mbFilterGainRoughness", 0.29f);
        XposedHelpers.setIntField(obj, "mFilterColorGrain", 0);
        XposedHelpers.setFloatField(obj, "mbFilterGainColorRatio", 0.7f);
        XposedHelpers.setFloatField(obj, "mbFilterGainMidtones", fArr[1]);
        XposedHelpers.setFloatField(obj, "mbFilterGainShadows", fArr[2]);
        XposedHelpers.setFloatField(obj, "mbFilterGlowRadius", 20.0f);
        XposedHelpers.setFloatField(obj, "mbFilterGlowIntensity", 0.4f);
        XposedHelpers.setFloatField(obj, "mbFilterGlowThresh", fArr2[0]);
        XposedHelpers.setFloatField(obj, "mbFilterGlowQuickImgThresh", fArr2[1]);
        XposedHelpers.setFloatField(obj, "mbFilterGlowPhotoThresh", fArr2[2]);
        XposedHelpers.setFloatField(obj, "mbFilterGlowPreviewBgThresh", fArr2[3]);
        XposedHelpers.setFloatField(obj, "mbFilterGlowQuickImgBgThresh", fArr2[4]);
        XposedHelpers.setFloatField(obj, "mbFilterGlowPhotoBgThresh", fArr2[5]);
        XposedHelpers.setObjectField(obj, "mbFilterGlowColor", "1,0.15,0.01,1");
        XposedHelpers.setIntField(obj, "mnFilterGrainType", 0);
        XposedHelpers.setBooleanField(obj, "mFilterGrainEnable", true);
        XposedHelpers.setFloatField(obj, "mfFilterBlurRadius", 0.0f);
        XposedHelpers.setFloatField(obj, "mfFilterHighlightOpacity", 0.0f);
        XposedHelpers.setFloatField(obj, "mfFilterrShadowOpacity", 0.0f);
        XposedHelpers.setFloatField(obj, "mfFilterHighlightThresh", 0.0f);
        XposedHelpers.setFloatField(obj, "mfFilterShadowThresh", 0.0f);
    }

    public static void install(ClassLoader classLoader) {
        final Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.ipusdk.processunit.filter.list.FilterConstant", classLoader);
        final Class clsFindClass2 = XposedHelpers.findClass("com.oplus.ocs.camera.ipusdk.processunit.filter.FilterProcessUnitSDK", classLoader);
        XposedHelpers.findAndHookMethod("com.oplus.ocs.camera.producer.mode.BaseMode", classLoader, "setPortraFilterGrain", new Object[]{"com.oplus.ocs.camera.common.util.CameraRequestTag", new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterNeonGlow.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = methodHookParam.args[0];
                if (obj != null && MasterNeonGlow.MASTER_800T.equals(XposedHelpers.getObjectField(obj, "mFilterType")) && Settings.get("master_effect_neon")) {
                    MasterNeonGlow.applyNormal800t(obj, clsFindClass, clsFindClass2);
                }
            }
        }});
        final Object staticObjectField = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.oplus.ocs.camera.common.util.ParameterKeys", classLoader), "KEY_FILTER_TYPE");
        XposedHelpers.findAndHookMethod("com.oplus.ocs.camera.consumer.apsAdapter.adapter.ApsParameters$ParameterModel", classLoader, "setParameter", new Object[]{"com.oplus.ocs.camera.consumer.apsAdapter.adapter.ApsParameters$Key", Object.class, new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterNeonGlow.2
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object[] objArr = methodHookParam.args;
                if (objArr[0] == staticObjectField && MasterNeonGlow.MASTER_800T.equals(objArr[1]) && Settings.get("master_effect_neon")) {
                    methodHookParam.args[1] = MasterNeonGlow.NORMAL_800T;
                }
            }
        }});
    }
}
