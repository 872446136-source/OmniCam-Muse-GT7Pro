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

/**
 * GR port: makes the realme GR mode (app GrMode, SDK GRCapMode) run on PMA110.
 *
 * PMA110's camera ships the GR mode classes but not a working GR pipeline. The GR mode is run as
 * the professional (master) mode underneath: app and SDK methods that the GR classes lack or get
 * wrong are delegated to the professional implementations, and the UI is adjusted for GR.
 * App classes and members are symbols (assets/symbols.json); the SDK keeps its names.
 */
final class GrModeHooks {
    private static final String TAG = "RicohGrPort";
    private static final String STYLE_FEATURE = "com.oplus.feature.effect.style.support";

    /**
     * What the GR shutter asks before it fires. 477's report: the press reaches
     * {@code ShutterHandler.gr}, the log shows {@code shutter handled by …}, and then nothing —
     * no capture, no thumbnail, no error.
     *
     * <p>The handler is a two-step: the button-down message arms the capture processor
     * ({@code CaptureProcessor.H.a(true)} and {@code BaseMode.si(null)}), and the button-up message
     * only fires it when {@code GrMode.Fe()} says touch-down capture is supported. That method is
     * two gates deep:
     *
     * <pre>
     *   super.Fe()   → BaseMode.Fe() → getSupportFunction("func_support_touch_down_capture")
     *                                → Util.o1(s(), U0, captureProcessor.H())
     *                                → CameraConfig "com.oplus.support.pre.capture.frames.in.hal"
     *   Fe()         → com.google.protobuf.a.b(24, …).orElse(-1) &gt; -1
     * </pre>
     *
     * <p>Both are per-device answers this firmware gives false, so the up event takes the branch
     * that registers a {@code GRCapMode} callback and never calls {@code BaseMode.ij(0)} — the
     * method that logs {@code touchDownCapture, onCameraShutterButtonClick} and starts the capture.
     * The mode is otherwise healthy: preview runs, the session opens on operation mode 0x8009, and
     * the watermark is drawn. Only this one predicate stands between the press and the picture, so
     * it is answered true for GR.
     *
     * <p>Escape hatch: {@code touch /data/adb/omnicam_grshutter_off}.
     */
    private static final String TOUCH_DOWN_CAPTURE = "func_support_touch_down_capture";
    private static final String SHUTTER_OFF = "/data/adb/omnicam_grshutter_off";

    private GrModeHooks() {}

    static void install(ClassLoader classLoader) throws Throwable {
        Symbols symbols = Symbols.get();
        SuperCall.verify();
        hookModeEntry(symbols);
        hookAppModeInheritance(symbols);
        hookSdkModeInheritance(classLoader);
        GrState.init(symbols);
        Log.guard(TAG, "GR shutter", () -> hookTouchDownCapture(symbols));
        Log.guard(TAG, "GR entrance buttons", () -> hookEntranceButtons(symbols, classLoader));
        Log.guard(TAG, "GR effect branch", () -> hookMasterEffectBranch(symbols));
        Log.guard(TAG, "GR effect panel lifecycle", () -> hookEffectPanelLifecycle(symbols));
        Log.guard(TAG, "master effect entry", () -> GrMasterEffectEntry.install(symbols));
        Log.guard(TAG, "GR style feature", () -> hookStyleFeature(classLoader));
        Log.guard(TAG, "GR mode arrangement", () -> GrModeArrange.install(symbols));
        Log.i(TAG, "GR mode hooks installed");
    }

    /** CameraEntry's mode entry has no GR case; enter as professional and restore the argument. */
    private static void hookModeEntry(Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("CameraEntry.enter"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if ("gr".equals(param.args[0])) {
                    param.setObjectExtra("grEntry", Boolean.TRUE);
                    param.args[0] = "professional";
                }
            }

            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(param.getObjectExtra("grEntry"))) param.args[0] = "gr";
            }
        });
    }

    /** App side: GrMode borrows professional, photo-mode and BaseMode implementations. */
    private static void hookAppModeInheritance(Symbols symbols) throws Throwable {
        // Enter GR through BaseMode without changing either watermark switch.
        SuperCall.inherit(symbols.cls("GrMode").getDeclaredMethod("onStart"),
            symbols.cls("BaseMode").getDeclaredMethod("onStart"));
        SuperCall.inherit(symbols.method("GrMode.zoomConfig"), symbols.method("ProMode.zoomConfig"));
        SuperCall.inherit(symbols.method("GrMode.modeId"), symbols.method("PhotoMode.modeId"));
        SuperCall.inherit(symbols.method("GrMode.zoomDefault"), symbols.method("Mode.zoomDefault"));
        SuperCall.inherit(symbols.method("GrMode.rawMenu"), symbols.method("ProMode.rawMenu"));
        SuperCall.inherit(symbols.method("GrMode.baseRawMenu"), symbols.method("Mode.rawMenu"));

        // Both watermark controls use the professional mode's menu availability.
        final Method professionalSwitch = SuperCall.pin(symbols.method("ProMode.menuSwitch"));
        XposedBridge.hookMethod(symbols.method("GrMode.menuSwitch"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String key = (String) param.args[0];
                if ("pref_watermark_capture_switch_key".equals(key) || "pref_watermark_gr_capture_switch_key".equals(key)) {
                    param.setResult(XposedBridge.invokeOriginalMethod(professionalSwitch, param.thisObject,
                        new Object[] {"pref_watermark_capture_switch_key"}));
                }
            }
        });

        Class<?> grMode = symbols.cls("GrMode");
        final Method professionalSupport = SuperCall.pin(symbols.cls("ProMode"), "getSupportFunction", String.class);
        XposedHelpers.findAndHookMethod(grMode, "getSupportFunction", String.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (GrState.currentMode != param.thisObject) {
                    GrState.currentMode = param.thisObject;
                    GrSnapFocus.onModeAttached(param.thisObject);
                }
                String key = (String) param.args[0];
                if ("pref_none_sat_tele_angle_key".equals(key) || "pref_support_switch_camera".equals(key)) {
                    param.setResult(XposedBridge.invokeOriginalMethod(professionalSupport, param.thisObject, param.args));
                } else if ("pref_filter_process_key".equals(key) || "pref_support_switch_camera_button".equals(key)) {
                    param.setResult(Boolean.TRUE);
                } else if (TOUCH_DOWN_CAPTURE.equals(key) && !new File(SHUTTER_OFF).exists()) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });
    }

    /**
     * Answers the two gates described on {@link #TOUCH_DOWN_CAPTURE} so a GR press actually fires.
     *
     * <p>The key is handled in the existing {@code getSupportFunction} hook above; what is left is
     * {@code Fe()} itself, which adds a setting lookup on top of it. Both are hooked rather than
     * only {@code Fe()}, because {@code Fe()} is also asked by the UI when it decides whether to
     * show the touch-down hint — that answer should agree with the shutter's.
     *
     * <p>Which method to hook: {@code GrMode} is the leaf of the chain, and the override that the
     * shutter actually reaches sits one or two classes up it, so the override is looked up by
     * walking from {@code GrMode} towards {@code BaseMode} for a declaration with the resolved
     * base method's name and signature. Nothing here names an obfuscated member: the anchor is the
     * symbol-resolved {@code BaseMode.Fe}, and the walk stops at its declaring class. If no class in
     * between overrides it, the base method is hooked and the instance check below keeps it to GR.
     *
     * <p>Only the false case is overridden: on a device where the stock answer is already true
     * nothing changes, and the one line per process says so.
     */
    private static void hookTouchDownCapture(final Symbols symbols) throws Throwable {
        final Class<?> grMode = symbols.cls("GrMode");
        Method base = symbols.method("BaseMode.touchDownCapture");
        Method target = overrideTowards(grMode, base);
        XposedBridge.hookMethod(target, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!grMode.isInstance(param.thisObject)) return;
                if (Boolean.TRUE.equals(param.getResult())) {
                    Log.i(TAG, "touch-down capture asked, answered true");
                    return;
                }
                if (new File(SHUTTER_OFF).exists()) {
                    Log.i(TAG, "touch-down capture asked, answered false, left alone (" + SHUTTER_OFF + ")");
                    return;
                }
                param.setResult(Boolean.TRUE);
                Log.i(TAG, "touch-down capture answered true: stock said false, so the shutter only "
                    + "registered a callback and never called BaseMode.ij(0)");
            }
        });
        Log.i(TAG, "GR touch-down capture hooked on " + target);
    }

    /**
     * The declaration of {@code base} that a call on {@code type} reaches: the nearest class between
     * {@code type} and {@code base}'s own class that declares the same method, or {@code base} itself.
     */
    private static Method overrideTowards(Class<?> type, Method base) {
        for (Class<?> c = type; c != null && c != base.getDeclaringClass(); c = c.getSuperclass()) {
            try {
                Method declared = c.getDeclaredMethod(base.getName(), base.getParameterTypes());
                if (declared.getReturnType() == base.getReturnType()) return declared;
            } catch (NoSuchMethodException ignored) {
                // Not here; keep walking up.
            }
        }
        return base;
    }

    /** SDK side: GRCapMode configures sessions like ProfessionalMode. */
    private static void hookSdkModeInheritance(ClassLoader classLoader) throws Throwable {
        Class<?> grCapMode = XposedHelpers.findClass("com.oplus.ocs.camera.producer.mode.GRCapMode", classLoader);
        Class<?> professional = XposedHelpers.findClass("com.oplus.ocs.camera.producer.mode.ProfessionalMode", classLoader);
        SuperCall.inherit(grCapMode, professional, "getModeName");
        SuperCall.inherit(grCapMode, professional, "onConfigure",
            XposedHelpers.findClass("com.oplus.ocs.camera.producer.device.CameraSessionEntity", classLoader),
            XposedHelpers.findClass("com.oplus.ocs.camera.common.parameter.SdkCameraDeviceConfig", classLoader),
            String.class,
            XposedHelpers.findClass("com.oplus.ocs.camera.common.util.ApsRequestTag", classLoader));
        SuperCall.inherit(grCapMode, professional, "getSurfaceUseCase", String.class, Boolean.TYPE, Float.class);
    }

    /**
     * Keeps the "风格" (style) and camera-switch buttons visible in GR unless the style chips are
     * open, hides the right-side menu entrances, and drops the GR immersive preview button, whose
     * layout does not fit PMA110.
     */
    private static void hookEntranceButtons(final Symbols symbols, ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.oplus.camera.control.ShutterButton", classLoader, "setVisibility", Integer.TYPE,
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    if (view.getId() <= 0 || !"switch_camera_button".equals(view.getResources().getResourceEntryName(view.getId()))) return;
                    int visibility = (Integer) param.args[0];
                    if ((visibility == View.INVISIBLE || visibility == View.GONE) && GrState.isGrMode() && !styleChipsOpen(symbols)) {
                        param.args[0] = View.VISIBLE;
                    }
                }
            });
        XposedBridge.hookMethod(symbols.method("ControlUi.switchVisibility"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                int visibility = (Integer) param.args[0];
                if ((visibility == View.INVISIBLE || visibility == View.GONE) && GrState.isGrMode() && !styleChipsOpen(symbols)) {
                    param.args[0] = View.VISIBLE;
                }
            }
        });
        XposedHelpers.findAndHookMethod("com.oplus.camera.ui.effectcontainer.MenuRightButton", classLoader, "setVisibility", Integer.TYPE,
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    if ((Integer) param.args[0] != View.VISIBLE || !GrState.isGrMode() || view.getId() <= 0) return;
                    String name = view.getResources().getResourceEntryName(view.getId());
                    if ("camera_menu_right_enter_button".equals(name) || "camera_menu_right_enter_button_filter".equals(name)) {
                        param.args[0] = View.GONE;
                    }
                }
            });
        XposedBridge.hookMethod(symbols.method("GrViewManager.immersiveButton"), XC_MethodReplacement.returnConstant(null));
    }

    /** The capture-params presenter of the activity hosting the current GR mode, or null. */
    static Object captureParams(Symbols symbols) throws Throwable {
        Object activity = modeActivity(GrState.currentMode);
        if (activity == null) return null;
        Object features = symbols.call("FeatureManager.of", null, activity);
        return features == null ? null : symbols.call("FeatureManager.captureParams", features);
    }

    /** True while the GR style chip panel is shown and expanded. */
    private static boolean styleChipsOpen(Symbols symbols) {
        try {
            Object presenter = captureParams(symbols);
            Object chips = Reflection.findFieldByType(presenter, symbols.cls("GrStylePanel").getName());
            if (chips == null || !symbols.cls("GrChips").isInstance(chips)) return false;
            View view = (View) symbols.get("GrStylePanel.view", chips);
            return view != null && view.isShown() && Boolean.TRUE.equals(XposedHelpers.callMethod(chips, "isExpanded"));
        } catch (Throwable error) {
            return false;
        }
    }

    /** The activity hosting a camera mode instance (BaseMode keeps it in an Activity field). */
    static Object modeActivity(Object mode) throws Throwable {
        if (mode == null) return null;
        return Reflection.findFieldByType(mode, "android.app.Activity");
    }

    /**
     * CaptureParams.restoreEffects() restores effect axes and picks the GR branch from
     * GrSupport.supported(), not from the current mode. With GR enabled, master mode would
     * otherwise restore the GR effect values into its BasicTone axes, so outside GR, supported()
     * answers false for the duration of restoreEffects().
     */
    private static void hookMasterEffectBranch(final Symbols symbols) {
        final ThreadLocal<Boolean> restoringOutsideGr = new ThreadLocal<>();
        XposedBridge.hookMethod(symbols.method("GrSupport.supported"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(restoringOutsideGr.get())) param.setResult(Boolean.FALSE);
            }
        });
        XposedBridge.hookMethod(symbols.method("CaptureParams.restoreEffects"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!"gr".equals(symbols.get("FeaturePresenter.modeName", param.thisObject))) restoringOutsideGr.set(Boolean.TRUE);
            }

            @Override protected void afterHookedMethod(MethodHookParam param) {
                restoringOutsideGr.remove();
            }
        });
    }

    /** GR and professional share the presenter's cached effect panel; release it when switching into either. */
    private static void hookEffectPanelLifecycle(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("CaptureParams.onModeSwitch"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object stage = param.args[0];
                    if (stage == null || (Integer) symbols.get("ModeSwitchStage.stage", stage) != 2) return;
                    Object target = symbols.get("ModeSwitchStage.toMode", stage);
                    if (!"gr".equals(target) && !"professional".equals(target)) return;
                    if (symbols.get("CaptureParams.panel", param.thisObject) != null) {
                        symbols.call("CaptureParams.releasePanel", param.thisObject);
                    }
                } catch (Throwable error) {
                    Log.e(TAG, "effect panel release failed", error);
                }
            }
        });
    }

    /** GR has its own style panel; the generic effect-style feature is off while in GR. */
    private static void hookStyleFeature(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.oplus.camera.configure.CameraConfig", classLoader, "getConfigBooleanValue", String.class,
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (STYLE_FEATURE.equals(param.args[0]) && GrState.isGrMode()) param.setResult(Boolean.FALSE);
                }
            });
    }
}
