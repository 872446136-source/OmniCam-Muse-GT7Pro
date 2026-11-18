package local.jiege.hook.gr;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CaptureRequest;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.List;
import local.jiege.hook.common.DataStore;
import local.jiege.hook.common.Install;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Reflection;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class GrSnapFocus {
    private static final String MODULE_PACKAGE = "local.jiege.camera";
    private static final String SNAP_KEY = "pref_snap_switch_key";
    private static final String TAG = "RicohGrPort";
    private static volatile boolean dataManagerHooked;
    private static final ThreadLocal<Boolean> loadingModuleDrawable = new ThreadLocal<>();
    private static volatile Context moduleContext;

    private GrSnapFocus() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void addSnapBeforeHdr(List<String> list) {
        if (list == null || list.contains(SNAP_KEY)) {
            return;
        }
        int iIndexOf = list.indexOf("pref_camera_hdr_mode_key");
        if (iIndexOf >= 0) {
            list.add(iIndexOf, SNAP_KEY);
        } else {
            list.add(SNAP_KEY);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void applyToMode(Object obj, String str) {
        if (obj == null) {
            return;
        }
        try {
            Symbols symbols = Symbols.get();
            Object staticObjectField = XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.oplus.ocs.camera.CameraParameter", obj.getClass().getClassLoader()), "FOCUS_MODE");
            float fDiopters = diopters(str);
            if (fDiopters >= 0.0f) {
                symbols.call("Mode.setPreviewParameter", obj, staticObjectField, 0);
                symbols.call("Mode.setRequestKey", obj, CaptureRequest.LENS_FOCUS_DISTANCE, Float.valueOf(fDiopters));
            } else {
                symbols.call("Mode.setPreviewParameter", obj, staticObjectField, 4);
                symbols.call("Mode.setRequestKey", obj, CaptureRequest.CONTROL_AF_MODE, 4);
                symbols.call("Mode.setRequestKey", obj, CaptureRequest.CONTROL_AF_TRIGGER, 2);
            }
        } catch (Throwable th) {
            Log.e(TAG, "snap focus apply failed", th);
        }
    }

    private static float diopters(String str) {
        if (str == null || Install.OFF.equalsIgnoreCase(str)) {
            return -1.0f;
        }
        if ("1m".equalsIgnoreCase(str)) {
            return 1.0f;
        }
        if ("2.5m".equalsIgnoreCase(str)) {
            return 0.4f;
        }
        if ("5m".equalsIgnoreCase(str)) {
            return 0.2f;
        }
        return ("∞".equals(str) || "inf".equalsIgnoreCase(str) || "100".equals(str)) ? 0.0f : -1.0f;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookAutofocusMode(Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("Mode.afMode"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrSnapFocus.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrState.isGrMode() && GrState.snapActive()) {
                    methodHookParam.setResult(0);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookCaptureStage(Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("GrMode.captureStage"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrSnapFocus.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrState.isGrMode() && GrState.snapActive()) {
                    GrSnapFocus.applyToMode(methodHookParam.thisObject, GrState.snapSetting);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookMenuClick(final Symbols symbols, final ClassLoader classLoader) {
        XposedBridge.hookMethod(symbols.method("CaptureParams.menuClick"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrSnapFocus.3
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrSnapFocus.SNAP_KEY.equals(methodHookParam.args[0])) {
                    String str = (String) methodHookParam.args[1];
                    GrState.snapSetting = str;
                    try {
                        Symbols symbols2 = symbols;
                        DataStore.put(symbols2, classLoader, DataStore.key(symbols2, "Keys.snap", GrSnapFocus.SNAP_KEY), str);
                    } catch (Throwable th) {
                        Log.e(GrSnapFocus.TAG, "snap setting store failed", th);
                    }
                    GrSnapFocus.applyToMode(GrState.currentMode, str);
                }
            }
        });
    }

    private static void hookMenuIcons(ClassLoader classLoader) {
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrSnapFocus.7
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Drawable drawableModuleIcon = GrSnapFocus.moduleIcon((Resources) methodHookParam.thisObject, ((Integer) methodHookParam.args[0]).intValue());
                if (drawableModuleIcon != null) {
                    methodHookParam.setResult(drawableModuleIcon);
                }
            }
        };
        try {
            Class cls = Integer.TYPE;
            XposedHelpers.findAndHookMethod(Resources.class, "getDrawable", new Object[]{cls, xC_MethodHook});
            XposedHelpers.findAndHookMethod(Resources.class, "getDrawable", new Object[]{cls, Resources.Theme.class, xC_MethodHook});
            XposedHelpers.findAndHookMethod(Resources.class, "getDrawableForDensity", new Object[]{cls, cls, xC_MethodHook});
            XposedHelpers.findAndHookMethod(Resources.class, "getDrawableForDensity", new Object[]{cls, cls, Resources.Theme.class, xC_MethodHook});
            XposedBridge.hookAllMethods(XposedHelpers.findClass("android.content.res.ResourcesImpl", classLoader), "loadDrawable", new XC_MethodHook() { // from class: local.jiege.hook.gr.GrSnapFocus.8
                /* JADX WARN: Code duplicated, block: B:11:0x0023  */
                /* JADX WARN: Code duplicated, block: B:20:0x0026 A[SYNTHETIC] */
                /* JADX WARN: Code duplicated, block: B:9:0x001f  */
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Resources resources = null;
                    int iIntValue = 0;
                    for (Object obj : methodHookParam.args) {
                        if (obj instanceof Integer) {
                            Integer num = (Integer) obj;
                            if (num.intValue() > 2130706432) {
                                iIntValue = num.intValue();
                            } else if (obj instanceof Resources) {
                                resources = (Resources) obj;
                            }
                        } else if (obj instanceof Resources) {
                            resources = (Resources) obj;
                        }
                    }
                    Drawable drawableModuleIcon = GrSnapFocus.moduleIcon(resources, iIntValue);
                    if (drawableModuleIcon != null) {
                        methodHookParam.setResult(drawableModuleIcon);
                    }
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "snap icon hooks failed", th);
        }
    }

    private static void hookStoredSetting(final Symbols symbols) {
        XposedHelpers.findAndHookMethod(Application.class, "onCreate", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.gr.GrSnapFocus.4
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrSnapFocus.dataManagerHooked) {
                    return;
                }
                GrSnapFocus.dataManagerHooked = true;
                try {
                    XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrSnapFocus.4.1
                        public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam2) {
                            Object obj = methodHookParam2.args[0];
                            if (obj == null || !Reflection.hasStringField(obj, GrSnapFocus.SNAP_KEY)) {
                                return;
                            }
                            GrSnapFocus.onSnapChanged(String.valueOf(methodHookParam2.args[1]));
                        }
                    };
                    XposedBridge.hookMethod(symbols.method("DataManager.putSync"), xC_MethodHook);
                    XposedBridge.hookMethod(symbols.method("DataManager.put"), xC_MethodHook);
                } catch (Throwable th) {
                    Log.e(GrSnapFocus.TAG, "snap setting tracking skipped", th);
                }
            }
        }});
    }

    private static void hookTopMenu(ClassLoader classLoader) {
        List list;
        try {
            Class clsFindClass = XposedHelpers.findClass("com.oplus.camera.common.config.CameraSettingsConfig", classLoader);
            XposedHelpers.findAndHookMethod(clsFindClass, "parseMenuTopSetting", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.gr.GrSnapFocus.5
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    GrSnapFocus.addSnapBeforeHdr((List) XposedHelpers.getObjectField(methodHookParam.thisObject, "mMenuTopList"));
                }
            }});
            XposedHelpers.findAndHookMethod(clsFindClass, "getMenuTopOptionList", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.gr.GrSnapFocus.6
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    GrSnapFocus.addSnapBeforeHdr((List) methodHookParam.getResult());
                }
            }});
            Object staticObjectField = XposedHelpers.getStaticObjectField(clsFindClass, "sCameraSettingConfig");
            if (staticObjectField == null || (list = (List) XposedHelpers.getObjectField(staticObjectField, "mMenuTopList")) == null || list.contains(SNAP_KEY)) {
                return;
            }
            list.add(SNAP_KEY);
        } catch (Throwable th) {
            Log.e(TAG, "snap top menu hook failed", th);
        }
    }

    public static void install(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        Log.guard(TAG, "snap capture stage", new b(symbols, (byte) 17));
        Log.guard(TAG, "snap AF mode", new b(symbols, (byte) 18));
        Log.guard(TAG, "snap menu click", new a(symbols, classLoader, (byte) 6));
        hookStoredSetting(symbols);
        hookTopMenu(classLoader);
        hookMenuIcons(classLoader);
    }

    private static Context moduleContext() {
        if (moduleContext == null) {
            synchronized (GrSnapFocus.class) {
                try {
                    if (moduleContext == null) {
                        try {
                            Application application = (Application) XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", (ClassLoader) null), "currentApplication", new Object[0]);
                            if (application != null) {
                                moduleContext = application.createPackageContext(MODULE_PACKAGE, 2);
                            }
                        } catch (Throwable th) {
                            Log.e(TAG, "module context unavailable", th);
                        }
                    }
                } catch (Throwable th2) {
                    throw th2;
                }
            }
        }
        return moduleContext;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Drawable moduleIcon(Resources resources, int i) {
        Context contextModuleContext;
        int identifier;
        if (resources != null && i != 0) {
            Boolean bool = Boolean.TRUE;
            ThreadLocal<Boolean> threadLocal = loadingModuleDrawable;
            if (!bool.equals(threadLocal.get())) {
                try {
                    String resourceEntryName = resources.getResourceEntryName(i);
                    if (!resourceEntryName.startsWith("snap_switch_") || (contextModuleContext = moduleContext()) == null || resources == contextModuleContext.getResources() || (identifier = contextModuleContext.getResources().getIdentifier(resourceEntryName, "drawable", MODULE_PACKAGE)) == 0) {
                        return null;
                    }
                    threadLocal.set(bool);
                    try {
                        Drawable drawable = contextModuleContext.getDrawable(identifier);
                        return drawable == null ? null : drawable.mutate();
                    } finally {
                        loadingModuleDrawable.remove();
                    }
                } catch (Throwable unused) {
                }
            }
        }
        return null;
    }

    public static void onModeAttached(Object obj) {
        if (GrState.snapActive()) {
            applyToMode(obj, GrState.snapSetting);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void onSnapChanged(String str) {
        GrState.snapSetting = str;
        applyToMode(GrState.currentMode, str);
    }
}
