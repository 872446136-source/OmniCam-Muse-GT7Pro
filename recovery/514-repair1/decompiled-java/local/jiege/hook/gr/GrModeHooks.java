package local.jiege.hook.gr;

import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Method;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Reflection;
import local.jiege.hook.common.SuperCall;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.FilterScope;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class GrModeHooks {
    private static final String SHUTTER_OFF = "/data/adb/omnicam_grshutter_off";
    private static final String STYLE_FEATURE = "com.oplus.feature.effect.style.support";
    private static final String TAG = "RicohGrPort";
    private static final String TOUCH_DOWN_CAPTURE = "func_support_touch_down_capture";

    private GrModeHooks() {
    }

    public static Object captureParams(Symbols symbols) {
        Object objCall;
        Object objModeActivity = modeActivity(GrState.currentMode);
        if (objModeActivity == null || (objCall = symbols.call("FeatureManager.of", null, objModeActivity)) == null) {
            return null;
        }
        return symbols.call("FeatureManager.captureParams", objCall, new Object[0]);
    }

    private static void hookAppModeInheritance(Symbols symbols) {
        SuperCall.inherit(symbols.cls("GrMode").getDeclaredMethod("onStart", null), symbols.cls("BaseMode").getDeclaredMethod("onStart", null));
        SuperCall.inherit(symbols.method("GrMode.zoomConfig"), symbols.method("ProMode.zoomConfig"));
        SuperCall.inherit(symbols.method("GrMode.modeId"), symbols.method("PhotoMode.modeId"));
        SuperCall.inherit(symbols.method("GrMode.zoomDefault"), symbols.method("Mode.zoomDefault"));
        SuperCall.inherit(symbols.method("GrMode.rawMenu"), symbols.method("ProMode.rawMenu"));
        SuperCall.inherit(symbols.method("GrMode.baseRawMenu"), symbols.method("Mode.rawMenu"));
        final Method methodPin = SuperCall.pin(symbols.method("ProMode.menuSwitch"));
        XposedBridge.hookMethod(symbols.method("GrMode.menuSwitch"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrModeHooks.2
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                String str = (String) methodHookParam.args[0];
                if ("pref_watermark_capture_switch_key".equals(str) || "pref_watermark_gr_capture_switch_key".equals(str)) {
                    methodHookParam.setResult(XposedBridge.invokeOriginalMethod(methodPin, methodHookParam.thisObject, new Object[]{"pref_watermark_capture_switch_key"}));
                }
            }
        });
        Class<?> cls = symbols.cls("GrMode");
        final Method methodPin2 = SuperCall.pin(symbols.cls("ProMode"), "getSupportFunction", String.class);
        XposedHelpers.findAndHookMethod(cls, "getSupportFunction", new Object[]{String.class, new XC_MethodHook() { // from class: local.jiege.hook.gr.GrModeHooks.3
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = GrState.currentMode;
                Object obj2 = methodHookParam.thisObject;
                if (obj != obj2) {
                    GrState.currentMode = obj2;
                    GrSnapFocus.onModeAttached(methodHookParam.thisObject);
                }
                String str = (String) methodHookParam.args[0];
                if ("pref_none_sat_tele_angle_key".equals(str) || "pref_support_switch_camera".equals(str)) {
                    methodHookParam.setResult(XposedBridge.invokeOriginalMethod(methodPin2, methodHookParam.thisObject, methodHookParam.args));
                    return;
                }
                if ("pref_filter_process_key".equals(str) || "pref_support_switch_camera_button".equals(str)) {
                    methodHookParam.setResult(Boolean.TRUE);
                } else {
                    if (!GrModeHooks.TOUCH_DOWN_CAPTURE.equals(str) || new File(GrModeHooks.SHUTTER_OFF).exists()) {
                        return;
                    }
                    methodHookParam.setResult(Boolean.TRUE);
                }
            }
        }});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookEffectPanelLifecycle(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("CaptureParams.onModeSwitch"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrModeHooks.10
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    Object obj = methodHookParam.args[0];
                    if (obj != null && ((Integer) symbols.get("ModeSwitchStage.stage", obj)).intValue() == 2) {
                        Object obj2 = symbols.get("ModeSwitchStage.toMode", obj);
                        if (("gr".equals(obj2) || FilterScope.MASTER_MODE.equals(obj2)) && symbols.get("CaptureParams.panel", methodHookParam.thisObject) != null) {
                            symbols.call("CaptureParams.releasePanel", methodHookParam.thisObject, new Object[0]);
                        }
                    }
                } catch (Throwable th) {
                    Log.e(GrModeHooks.TAG, "effect panel release failed", th);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookEntranceButtons(final Symbols symbols, ClassLoader classLoader) {
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrModeHooks.5
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                View view = (View) methodHookParam.thisObject;
                if (view.getId() <= 0 || !"switch_camera_button".equals(view.getResources().getResourceEntryName(view.getId()))) {
                    return;
                }
                int iIntValue = ((Integer) methodHookParam.args[0]).intValue();
                if ((iIntValue == 4 || iIntValue == 8) && GrState.isGrMode() && !GrModeHooks.styleChipsOpen(symbols)) {
                    methodHookParam.args[0] = 0;
                }
            }
        };
        Class cls = Integer.TYPE;
        XposedHelpers.findAndHookMethod("com.oplus.camera.control.ShutterButton", classLoader, "setVisibility", new Object[]{cls, xC_MethodHook});
        XposedBridge.hookMethod(symbols.method("ControlUi.switchVisibility"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrModeHooks.6
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                int iIntValue = ((Integer) methodHookParam.args[0]).intValue();
                if ((iIntValue == 4 || iIntValue == 8) && GrState.isGrMode() && !GrModeHooks.styleChipsOpen(symbols)) {
                    methodHookParam.args[0] = 0;
                }
            }
        });
        XposedHelpers.findAndHookMethod("com.oplus.camera.ui.effectcontainer.MenuRightButton", classLoader, "setVisibility", new Object[]{cls, new XC_MethodHook() { // from class: local.jiege.hook.gr.GrModeHooks.7
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                View view = (View) methodHookParam.thisObject;
                if (((Integer) methodHookParam.args[0]).intValue() == 0 && GrState.isGrMode() && view.getId() > 0) {
                    String resourceEntryName = view.getResources().getResourceEntryName(view.getId());
                    if ("camera_menu_right_enter_button".equals(resourceEntryName) || "camera_menu_right_enter_button_filter".equals(resourceEntryName)) {
                        methodHookParam.args[0] = 8;
                    }
                }
            }
        }});
        XposedBridge.hookMethod(symbols.method("GrViewManager.immersiveButton"), XC_MethodReplacement.returnConstant((Object) null));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookMasterEffectBranch(final Symbols symbols) {
        final ThreadLocal threadLocal = new ThreadLocal();
        XposedBridge.hookMethod(symbols.method("GrSupport.supported"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrModeHooks.8
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Boolean.TRUE.equals(threadLocal.get())) {
                    methodHookParam.setResult(Boolean.FALSE);
                }
            }
        });
        XposedBridge.hookMethod(symbols.method("CaptureParams.restoreEffects"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrModeHooks.9
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                threadLocal.remove();
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if ("gr".equals(symbols.get("FeaturePresenter.modeName", methodHookParam.thisObject))) {
                    return;
                }
                threadLocal.set(Boolean.TRUE);
            }
        });
    }

    private static void hookModeEntry(Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("CameraEntry.enter"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrModeHooks.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Boolean.TRUE.equals(methodHookParam.getObjectExtra("grEntry"))) {
                    methodHookParam.args[0] = "gr";
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if ("gr".equals(methodHookParam.args[0])) {
                    methodHookParam.setObjectExtra("grEntry", Boolean.TRUE);
                    methodHookParam.args[0] = FilterScope.MASTER_MODE;
                }
            }
        });
    }

    private static void hookSdkModeInheritance(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.producer.mode.GRCapMode", classLoader);
        Class clsFindClass2 = XposedHelpers.findClass("com.oplus.ocs.camera.producer.mode.ProfessionalMode", classLoader);
        SuperCall.inherit(clsFindClass, clsFindClass2, "getModeName", new Class[0]);
        SuperCall.inherit(clsFindClass, clsFindClass2, "onConfigure", XposedHelpers.findClass("com.oplus.ocs.camera.producer.device.CameraSessionEntity", classLoader), XposedHelpers.findClass("com.oplus.ocs.camera.common.parameter.SdkCameraDeviceConfig", classLoader), String.class, XposedHelpers.findClass("com.oplus.ocs.camera.common.util.ApsRequestTag", classLoader));
        SuperCall.inherit(clsFindClass, clsFindClass2, "getSurfaceUseCase", String.class, Boolean.TYPE, Float.class);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookStyleFeature(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.oplus.camera.configure.CameraConfig", classLoader, "getConfigBooleanValue", new Object[]{String.class, new XC_MethodHook() { // from class: local.jiege.hook.gr.GrModeHooks.11
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrModeHooks.STYLE_FEATURE.equals(methodHookParam.args[0]) && GrState.isGrMode()) {
                    methodHookParam.setResult(Boolean.FALSE);
                }
            }
        }});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookTouchDownCapture(Symbols symbols) {
        final Class<?> cls = symbols.cls("GrMode");
        Method methodOverrideTowards = overrideTowards(cls, symbols.method("BaseMode.touchDownCapture"));
        XposedBridge.hookMethod(methodOverrideTowards, new XC_MethodHook() { // from class: local.jiege.hook.gr.GrModeHooks.4
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (cls.isInstance(methodHookParam.thisObject)) {
                    Boolean bool = Boolean.TRUE;
                    if (bool.equals(methodHookParam.getResult())) {
                        Log.i(GrModeHooks.TAG, "touch-down capture asked, answered true");
                    } else if (new File(GrModeHooks.SHUTTER_OFF).exists()) {
                        Log.i(GrModeHooks.TAG, "touch-down capture asked, answered false, left alone (/data/adb/omnicam_grshutter_off)");
                    } else {
                        methodHookParam.setResult(bool);
                        Log.i(GrModeHooks.TAG, "touch-down capture answered true: stock said false, so the shutter only registered a callback and never called BaseMode.ij(0)");
                    }
                }
            }
        });
        Log.i(TAG, "GR touch-down capture hooked on " + methodOverrideTowards);
    }

    public static void install(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        SuperCall.verify();
        hookModeEntry(symbols);
        hookAppModeInheritance(symbols);
        hookSdkModeInheritance(classLoader);
        GrState.init(symbols);
        Log.guard(TAG, "GR shutter", new b(symbols, (byte) 9));
        Log.guard(TAG, "GR entrance buttons", new a(symbols, classLoader, (byte) 5));
        Log.guard(TAG, "GR effect branch", new b(symbols, (byte) 10));
        Log.guard(TAG, "GR effect panel lifecycle", new b(symbols, (byte) 11));
        Log.guard(TAG, "master effect entry", new b(symbols, (byte) 12));
        Log.guard(TAG, "GR style feature", new b(classLoader, (byte) 19));
        Log.guard(TAG, "GR mode arrangement", new b(symbols, (byte) 13));
        Log.i(TAG, "GR mode hooks installed");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$1(Symbols symbols, ClassLoader classLoader) {
        hookEntranceButtons(symbols, classLoader);
    }

    public static Object modeActivity(Object obj) {
        if (obj == null) {
            return null;
        }
        return Reflection.findFieldByType(obj, "android.app.Activity");
    }

    private static Method overrideTowards(Class<?> cls, Method method) {
        while (cls != null && cls != method.getDeclaringClass()) {
            try {
                Method declaredMethod = cls.getDeclaredMethod(method.getName(), method.getParameterTypes());
                if (declaredMethod.getReturnType() == method.getReturnType()) {
                    return declaredMethod;
                }
                cls = cls.getSuperclass();
            } catch (NoSuchMethodException unused) {
            }
        }
        return method;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean styleChipsOpen(Symbols symbols) {
        View view;
        try {
            Object objFindFieldByType = Reflection.findFieldByType(captureParams(symbols), symbols.cls("GrStylePanel").getName());
            return objFindFieldByType != null && symbols.cls("GrChips").isInstance(objFindFieldByType) && (view = (View) symbols.get("GrStylePanel.view", objFindFieldByType)) != null && view.isShown() && Boolean.TRUE.equals(XposedHelpers.callMethod(objFindFieldByType, "isExpanded", new Object[0]));
        } catch (Throwable unused) {
        }
    }
}
