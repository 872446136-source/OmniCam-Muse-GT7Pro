package local.jiege.hook.gr;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.common.ZoomFocal;
import local.jiege.hook.filters.PresetSelection;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class GrFocalLength {
    private static final String TAG = "RicohGrPort";
    private static final String ZOOM_BAR = "com.oplus.camera.feature.zoom.view.ZoomSeekBar";

    private GrFocalLength() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String bareNumber(String str) {
        return str.replace("×", PresetSelection.STANDARD_KEY).replace("x", PresetSelection.STANDARD_KEY).replace("X", PresetSelection.STANDARD_KEY).replace("mm", PresetSelection.STANDARD_KEY).trim();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int chipIndex(Symbols symbols, Object obj, float f) {
        List list = null;
        try {
            Object obj2 = symbols.get("Zoom.stops", obj);
            if (obj2 instanceof List) {
                list = (List) obj2;
            }
        } catch (Throwable unused) {
        }
        int size = 10;
        try {
            Object obj3 = symbols.get("Zoom.chips", obj);
            if (obj3 instanceof Map) {
                size = ((Map) obj3).size();
            } else if (obj3 instanceof List) {
                size = ((List) obj3).size();
            }
        } catch (Throwable unused2) {
        }
        if (list == null) {
            return 0;
        }
        int i = 0;
        for (int i2 = 0; i2 < list.size() && i2 < size; i2++) {
            Object obj4 = list.get(i2);
            if (obj4 instanceof Float) {
                float fFloatValue = ((Float) obj4).floatValue();
                if ((Float.compare(fFloatValue, 0.6f) != 0 || f < 1.0f || f >= fFloatValue) && Float.compare(fFloatValue, f) > 0) {
                    break;
                }
                i = i2;
            }
        }
        return i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookZoomToMillimetres(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("FocalLength.millimetres"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrFocalLength.12
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrState.isGrMode()) {
                    methodHookParam.setResult(Float.valueOf(Math.round(ZoomFocal.millimetres(symbols, ((Float) methodHookParam.args[0]).floatValue()))));
                }
            }
        });
    }

    public static void install(ClassLoader classLoader) {
        final Symbols symbols = Symbols.get();
        final Class clsFindClass = XposedHelpers.findClass(ZOOM_BAR, classLoader);
        Log.guard(TAG, "GR zoom stops", new b(symbols, (byte) 2));
        XposedBridge.hookAllConstructors(clsFindClass, new XC_MethodHook() { // from class: local.jiege.hook.gr.GrFocalLength.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrState.isGrMode()) {
                    try {
                        GrFocalLength.labelInMillimetres(symbols, methodHookParam.thisObject);
                        symbols.call("Zoom.relabel", methodHookParam.thisObject, new Object[0]);
                    } catch (Throwable unused) {
                    }
                }
            }
        });
        final XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrFocalLength.3
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrFocalLength.o(methodHookParam.thisObject, symbols)) {
                    GrFocalLength.labelInMillimetres(symbols, methodHookParam.thisObject);
                }
            }
        };
        Log.guard(TAG, "zoom relabel", new Log.Installer() { // from class: local.jiege.hook.gr.c
            @Override // local.jiege.hook.common.Log.Installer
            public final void install() {
                GrFocalLength.lambda$install$1(clsFindClass, xC_MethodHook, symbols);
            }
        });
        final byte b = 0;
        Log.guard(TAG, "zoom format", new Log.Installer() { // from class: local.jiege.hook.gr.d
            @Override // local.jiege.hook.common.Log.Installer
            public final void install() {
                byte b2 = b;
                Object obj = xC_MethodHook;
                Symbols symbols2 = symbols;
                switch (b2) {
                    case 0:
                        GrFocalLength.lambda$install$2(symbols2, (XC_MethodHook) obj);
                        break;
                    case 1:
                        GrFocalLength.lambda$install$3(symbols2, (XC_MethodHook) obj);
                        break;
                    case 2:
                        GrFocalLength.lambda$install$7(symbols2, (XC_MethodHook) obj);
                        break;
                    default:
                        GrFocalLength.lambda$install$10(symbols2, (Class) obj);
                        break;
                }
            }
        });
        final byte b2 = 1;
        Log.guard(TAG, "zoom hide labels", new Log.Installer() { // from class: local.jiege.hook.gr.d
            @Override // local.jiege.hook.common.Log.Installer
            public final void install() {
                byte b3 = b2;
                Object obj = xC_MethodHook;
                Symbols symbols2 = symbols;
                switch (b3) {
                    case 0:
                        GrFocalLength.lambda$install$2(symbols2, (XC_MethodHook) obj);
                        break;
                    case 1:
                        GrFocalLength.lambda$install$3(symbols2, (XC_MethodHook) obj);
                        break;
                    case 2:
                        GrFocalLength.lambda$install$7(symbols2, (XC_MethodHook) obj);
                        break;
                    default:
                        GrFocalLength.lambda$install$10(symbols2, (Class) obj);
                        break;
                }
            }
        });
        Log.guard(TAG, "zoom chip index", new b(symbols, (byte) 6));
        Log.guard(TAG, "zoom label text", new b(symbols, (byte) 7));
        Log.guard(TAG, "zoom label drawing", new b(symbols, (byte) 8));
        final XC_MethodHook xC_MethodHook2 = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrFocalLength.8
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrFocalLength.o(methodHookParam.thisObject, symbols)) {
                    try {
                        symbols.set("Zoom.iconBitmap", methodHookParam.thisObject, null);
                        symbols.set("Zoom.labelBitmap", methodHookParam.thisObject, null);
                    } catch (Throwable unused) {
                    }
                }
            }
        };
        final byte b3 = 2;
        Log.guard(TAG, "zoom label cache", new Log.Installer() { // from class: local.jiege.hook.gr.d
            @Override // local.jiege.hook.common.Log.Installer
            public final void install() {
                byte b4 = b3;
                Object obj = xC_MethodHook2;
                Symbols symbols2 = symbols;
                switch (b4) {
                    case 0:
                        GrFocalLength.lambda$install$2(symbols2, (XC_MethodHook) obj);
                        break;
                    case 1:
                        GrFocalLength.lambda$install$3(symbols2, (XC_MethodHook) obj);
                        break;
                    case 2:
                        GrFocalLength.lambda$install$7(symbols2, (XC_MethodHook) obj);
                        break;
                    default:
                        GrFocalLength.lambda$install$10(symbols2, (Class) obj);
                        break;
                }
            }
        });
        Log.guard(TAG, "zoom label fade", new b(symbols, (byte) 3));
        Log.guard(TAG, "zoom label alpha", new b(symbols, (byte) 4));
        final byte b4 = 3;
        Log.guard(TAG, "zoom unit", new Log.Installer() { // from class: local.jiege.hook.gr.d
            @Override // local.jiege.hook.common.Log.Installer
            public final void install() {
                byte b5 = b4;
                Object obj = clsFindClass;
                Symbols symbols2 = symbols;
                switch (b5) {
                    case 0:
                        GrFocalLength.lambda$install$2(symbols2, (XC_MethodHook) obj);
                        break;
                    case 1:
                        GrFocalLength.lambda$install$3(symbols2, (XC_MethodHook) obj);
                        break;
                    case 2:
                        GrFocalLength.lambda$install$7(symbols2, (XC_MethodHook) obj);
                        break;
                    default:
                        GrFocalLength.lambda$install$10(symbols2, (Class) obj);
                        break;
                }
            }
        });
        Log.guard(TAG, "GR focal length", new b(symbols, (byte) 5));
    }

    private static boolean isMillimetreBar(Symbols symbols, Object obj) {
        if (GrState.isGrMode()) {
            labelInMillimetres(symbols, obj);
            return true;
        }
        try {
            return "mm".equals(symbols.get("Zoom.unit", obj));
        } catch (Throwable unused) {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void labelInMillimetres(Symbols symbols, Object obj) {
        try {
            symbols.set("Zoom.unit", obj, "mm");
        } catch (Throwable unused) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$0(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("ZoomValues.forMode"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrFocalLength.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if ("gr".equals(methodHookParam.args[0])) {
                    Symbols symbols2 = symbols;
                    methodHookParam.setResult(symbols2.call("ZoomValues.parseWide", null, symbols2.call("CameraConfig.list", null, "com.oplus.available.wide.zoomvalues")));
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$1(Class cls, XC_MethodHook xC_MethodHook, Symbols symbols) {
        XposedHelpers.findAndHookMethod(cls, "setCurrentDisplayText", new Object[]{Float.TYPE, xC_MethodHook});
        XposedBridge.hookMethod(symbols.method("Zoom.setZoom"), xC_MethodHook);
        XposedBridge.hookMethod(symbols.method("Zoom.relabel"), xC_MethodHook);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$10(final Symbols symbols, final Class cls) {
        XposedBridge.hookMethod(symbols.method("ZoomUi.setUnit"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrFocalLength.11
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrState.isGrMode() || "mm".equals(methodHookParam.args[0])) {
                    try {
                        Object obj = symbols.get("ZoomUi.bar", methodHookParam.thisObject);
                        if (obj == null || !cls.isInstance(obj)) {
                            return;
                        }
                        GrFocalLength.labelInMillimetres(symbols, obj);
                    } catch (Throwable unused) {
                    }
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrState.isGrMode() || "mm".equals(methodHookParam.args[0])) {
                    Object[] objArr = methodHookParam.args;
                    objArr[0] = "mm";
                    objArr[1] = Boolean.FALSE;
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$2(Symbols symbols, XC_MethodHook xC_MethodHook) {
        XposedBridge.hookMethod(symbols.method("Zoom.formatZoom"), xC_MethodHook);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$3(Symbols symbols, XC_MethodHook xC_MethodHook) {
        XposedBridge.hookMethod(symbols.method("Zoom.hideLabels"), xC_MethodHook);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$4(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("Zoom.chipIndex"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrFocalLength.4
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrFocalLength.o(methodHookParam.thisObject, symbols)) {
                    methodHookParam.setResult(Integer.valueOf(GrFocalLength.chipIndex(symbols, methodHookParam.thisObject, ((Float) methodHookParam.args[0]).floatValue())));
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$5(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("Zoom.labelText"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrFocalLength.5
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrFocalLength.o(methodHookParam.thisObject, symbols)) {
                    methodHookParam.setResult(methodHookParam.args[0]);
                }
            }
        });
        XposedBridge.hookMethod(symbols.method("Zoom.unitText"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrFocalLength.6
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                String str = (String) methodHookParam.args[0];
                if (GrState.isGrMode() && str != null && ((Boolean) methodHookParam.args[1]).booleanValue()) {
                    methodHookParam.setResult(GrFocalLength.bareNumber(str));
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$6(final Symbols symbols) {
        final Method method = symbols.method("Zoom.drawUnitText");
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrFocalLength.7
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrFocalLength.o(methodHookParam.thisObject, symbols)) {
                    try {
                        Object[] objArr = methodHookParam.args;
                        String str = (String) objArr[4];
                        if (str != null) {
                            method.invoke(methodHookParam.thisObject, objArr[0], objArr[1], objArr[2], objArr[3], GrFocalLength.bareNumber(str));
                        }
                    } catch (Throwable unused) {
                    }
                    methodHookParam.setResult((Object) null);
                }
            }
        };
        XposedBridge.hookMethod(symbols.method("Zoom.drawLabel"), xC_MethodHook);
        XposedBridge.hookMethod(symbols.method("Zoom.drawLabelChecked"), xC_MethodHook);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$7(Symbols symbols, XC_MethodHook xC_MethodHook) {
        XposedBridge.hookMethod(symbols.method("Zoom.drawText"), xC_MethodHook);
        XposedBridge.hookMethod(symbols.method("Zoom.drawIcon"), xC_MethodHook);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$8(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.has("ZoomBase.fadeLabels") ? symbols.method("Zoom.fadeLabels") : null, new XC_MethodHook() { // from class: local.jiege.hook.gr.GrFocalLength.9
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrFocalLength.o(methodHookParam.thisObject, symbols)) {
                    methodHookParam.setResult((Object) null);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$9(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("Zoom.drawChip"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrFocalLength.10
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (GrFocalLength.o(methodHookParam.thisObject, symbols)) {
                    try {
                        Iterator it = symbols.all("ZoomBase.labelAlpha").iterator();
                        while (it.hasNext()) {
                            ((Field) it.next()).setInt(methodHookParam.thisObject, 255);
                        }
                        symbols.field("ZoomBase.labelVisible").setBoolean(methodHookParam.thisObject, true);
                    } catch (Throwable unused) {
                    }
                }
            }
        });
    }

    public static /* bridge */ /* synthetic */ boolean o(Object obj, Symbols symbols) {
        return isMillimetreBar(symbols, obj);
    }
}
