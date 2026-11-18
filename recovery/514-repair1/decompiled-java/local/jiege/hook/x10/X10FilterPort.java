package local.jiege.hook.x10;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.t2;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import local.jiege.hook.common.ModeName;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.FilterCatalog;
import local.jiege.hook.filters.FilterEntry;
import local.jiege.hook.filters.FilterScope;
import local.jiege.hook.filters.X10Filters;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class X10FilterPort {
    private static final String STANDARD = "default";
    private static final String TAG = "X10Filter";
    private static final String[] TYPES = {X10Filters.QING_TOU, X10Filters.HU_PO};
    private static final Map<String, int[]> PALETTE = buildPalette();
    private static final int[] WHITE_LABEL = {-1, -4605511};

    private X10FilterPort() {
    }

    private static Map<String, int[]> buildPalette() {
        HashMap map = new HashMap();
        put(map, colors("#312F2D", "#A2988F"), "default", "Radiance.cube.rgb.bin", "vivid-warm.cube.rgb.bin", "vivid-lut.cube.rgb.bin", "mono.cube.rgb.bin", "mountains.cube.rgb.bin", "island.cube.rgb.bin", "city.cube.rgb.bin");
        put(map, colors("#848484", "#6591A0", "#8FB5C6"), X10Filters.QING_TOU);
        put(map, colors("#746A64", "#967C7C", "#DC7B30"), X10Filters.HU_PO);
        put(map, colors("#848484", "#3B9B6C", "#279AC4"), "800t.bin", "800t_master.bin", "neon-2020.cube.rgb.bin", "oplus_video_filter_neon");
        put(map, colors("#848484", "#6EC5B5", "#FFD239"), "fuji-eterna-v2.cube.rgb.bin");
        put(map, colors("#848484", "#4CA7C2"), "fuji_cc.bin", "fuji_cc_master.bin");
        put(map, colors("#848484", "#6CBC8A", "#3B66FB"), "natural.cube.rgb.bin");
        put(map, colors("#848484", "#D9AD92", "#FFA27D"), "portra400_normal.bin", "portra400_master.bin", "portra400_sdr_gen_a_1.bin,portra400_sdr_gen_d_1.bin", "portra400_hdr_normal_a_1.bin,portra400_hdr_normal_d_1.bin", "portra400_hdr_master_a_1.bin,portra400_hdr_master_d_1.bin");
        put(map, colors("#848484", "#957D5D", "#D7BF74"), "gourmet.cube.rgb.bin");
        put(map, colors("#848484", "#C69586", "#6B9BFF"), "fuji-proNegHi.bin", "fuji-proNegHi_master.bin");
        put(map, colors("#848484", "#2784B0", "#1EADF0"), "vivid-cool.cube.rgb.bin");
        put(map, colors("#848484", "#6E8C83", "#D4775A"), "fuji-nc.bin", "fuji-nc_master.bin");
        put(map, colors("#848484", "#967C7C", "#E69D55"), "ccd_base_warm.bin");
        put(map, colors("#848484", "#A1EBDD", "#95D6F3"), "ccd_base_cold.bin");
        put(map, colors("#848484", "#7A8C8D", "#93A4C0", "#D0B6B4"), "positive_normal.bin", "positive_master.bin", "positive_sdr_gen_a_1.bin,positive_sdr_gen_d_1.bin", "positive_hdr_normal_a_1.bin,positive_hdr_normal_d_1.bin", "positive_hdr_master_a_1.bin,positive_hdr_master_d_1.bin");
        put(map, colors("#848484", "#D2C1BA", "#FFC0BB"), "meicam.child.bin");
        put(map, colors("#141A18", "#6A6A5A", "#8E8E7C", "#F8FF95"), "Serenity.cube.rgb.bin");
        put(map, colors("#000000", "#A7A7A7"), "blackandwhite.cube.rgb.bin", "blackandwhite_HC_normal.bin", "blackandwhite_HC_master.bin", "blackandwhite_HC_sdr.bin", "blackandwhite_HC_hdr_normal.bin", "blackandwhite_HC_hdr_master.bin");
        return map;
    }

    public static TextView[] cardTexts(Object obj) {
        Iterator it = Symbols.get().all("Card.texts").iterator();
        TextView textView = null;
        TextView textView2 = null;
        while (it.hasNext()) {
            TextView textView3 = (TextView) ((Field) it.next()).get(obj);
            if (textView3 != null) {
                try {
                    if (textView3.getId() <= 0 || !"effect_style_filter_hint".equals(textView3.getResources().getResourceEntryName(textView3.getId()))) {
                        textView2 = textView3;
                    } else {
                        textView = textView3;
                    }
                } catch (Resources.NotFoundException unused) {
                }
            }
        }
        return new TextView[]{textView, textView2};
    }

    private static String catalogLabel(Object obj, String str) {
        FilterEntry filterEntryFind = FilterCatalog.get(FilterScope.ofMode(ModeName.of(obj))).find(str);
        if (filterEntryFind == null) {
            return null;
        }
        return filterEntryFind.label;
    }

    private static int[] colors(String... strArr) {
        int[] iArr = new int[strArr.length];
        for (int i = 0; i < strArr.length; i++) {
            iArr[i] = Color.parseColor(strArr[i]);
        }
        return iArr;
    }

    private static String filterType(Object obj) {
        return (String) Symbols.get().call("ModeUi.filterType", obj, new Object[0]);
    }

    private static void fitCardTextWidths(TextView textView, TextView textView2, Object obj) {
        View view;
        ViewGroup.LayoutParams layoutParams;
        int i;
        int i2;
        int iMax = Math.max(textView.getPaddingRight() + textView.getPaddingLeft() + ((int) Math.ceil(textView.getPaint().measureText(textView.getText().toString()))), textView2.getPaddingRight() + textView2.getPaddingLeft() + ((int) Math.ceil(textView2.getPaint().measureText(textView2.getText().toString()))));
        ViewGroup.LayoutParams layoutParams2 = textView.getLayoutParams();
        ViewGroup.LayoutParams layoutParams3 = textView2.getLayoutParams();
        if (layoutParams2 != null && (i2 = layoutParams2.width) >= 0) {
            iMax = Math.max(iMax, i2);
        }
        if (layoutParams3 != null && (i = layoutParams3.width) >= 0) {
            iMax = Math.max(iMax, i);
        }
        if (layoutParams2 != null) {
            layoutParams2.width = iMax;
            textView.setLayoutParams(layoutParams2);
        }
        if (layoutParams3 != null) {
            layoutParams3.width = iMax;
            textView2.setLayoutParams(layoutParams3);
        }
        if (!(obj instanceof View) || (layoutParams = (view = (View) obj).getLayoutParams()) == null) {
            return;
        }
        layoutParams.width = iMax;
        view.setLayoutParams(layoutParams);
    }

    private static void hookDrawing(ClassLoader classLoader) {
        try {
            XposedBridge.hookAllMethods(XposedHelpers.findClass("com.oplus.camera.filter.FilterHelper", classLoader), "checkSpecialDrawingItem", new XC_MethodHook() { // from class: local.jiege.hook.x10.X10FilterPort.6
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    int i;
                    Object[] objArr = methodHookParam.args;
                    if (objArr.length >= 5) {
                        Object obj = objArr[3];
                        if (obj instanceof Integer) {
                            Object obj2 = objArr[4];
                            if (obj2 instanceof String) {
                                String str = (String) obj2;
                                Integer num = (Integer) obj;
                                num.intValue();
                                if (str.equals(X10FilterPort.TYPES[0])) {
                                    i = 123;
                                } else {
                                    i = str.equals(X10FilterPort.TYPES[1]) ? 124 : -1;
                                }
                                if (i < 0) {
                                    return;
                                }
                                Object obj3 = methodHookParam.args[1];
                                XposedHelpers.callMethod(obj3, "updateValue", new Object[]{num, "drawing_item_filter_type", str});
                                XposedHelpers.callMethod(obj3, "setFilterIndex", new Object[]{str, num});
                                XposedBridge.log("X10Filter: drawing mapped " + str + " id=" + i);
                            }
                        }
                    }
                }
            });
        } catch (Throwable th) {
            XposedBridge.log("X10Filter: drawing hook failed " + th);
        }
    }

    private static void hookFilterMap(ClassLoader classLoader) {
        try {
            Map map = (Map) XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.oplus.camera.filter.FilterHelper", classLoader), "sFilterPathConvertMap");
            putFilterId(map, X10Filters.QING_TOU, 123);
            putFilterId(map, X10Filters.HU_PO, 124);
            putFilterId(map, "qing_tou.bin,morning.dream.cube.rgb.bin", 1096);
            putFilterId(map, "hu_po.bin,morning.dream.cube.rgb.bin", 1097);
            putFilterId(map, "qing_tou.bin,nostalgic.scene.cube.rgb.bin", 2096);
            putFilterId(map, "hu_po.bin,nostalgic.scene.cube.rgb.bin", 2097);
            XposedBridge.log("X10Filter: capture filter enum map installed");
        } catch (Throwable th) {
            XposedBridge.log("X10Filter: capture filter enum map failed " + th);
        }
    }

    private static void hookMasterCard(ClassLoader classLoader) {
        try {
            Symbols symbols = Symbols.get();
            XposedBridge.hookMethod(symbols.method("Card.isFilmCard"), new XC_MethodHook() { // from class: local.jiege.hook.x10.X10FilterPort.8
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    X10FilterPort.releaseNativeMasterCardType(methodHookParam);
                }

                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (X10FilterPort.isX10MasterCard(methodHookParam.thisObject)) {
                        methodHookParam.setObjectExtra("x10.master.native.type", X10FilterPort.hookNativeMasterCardType(methodHookParam.thisObject));
                    }
                }
            });
            XposedBridge.hookMethod(symbols.method("Card.layout"), new XC_MethodHook() { // from class: local.jiege.hook.x10.X10FilterPort.9
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    X10FilterPort.releaseNativeMasterCardType(methodHookParam);
                    if (X10FilterPort.isX10MasterCard(methodHookParam.thisObject)) {
                        X10FilterPort.restoreMasterCardLabels(methodHookParam.thisObject);
                    }
                }

                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (X10FilterPort.isX10MasterCard(methodHookParam.thisObject)) {
                        methodHookParam.setObjectExtra("x10.master.native.type", X10FilterPort.hookNativeMasterCardType(methodHookParam.thisObject));
                    }
                }
            });
            XposedBridge.log("X10Filter: master card labels installed");
        } catch (Throwable th) {
            XposedBridge.log("X10Filter: master card labels failed " + th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code duplicated, block: B:19:0x0040 A[RETURN] */
    /* JADX WARN: Code duplicated, block: B:20:0x0041  */
    /* JADX WARN: Code duplicated, block: B:25:0x0057  */
    /* JADX WARN: Code duplicated, block: B:37:0x0088  */
    /* JADX WARN: Code duplicated, block: B:39:0x00a8  */
    /* JADX WARN: Instruction removed from duplicated block: B:39:0x00a8, please report this as an issue */
    public static XC_MethodHook.Unhook hookNativeMasterCardType(Object obj) {
        String str;
        final String str2;
        String strName;
        Class<?> superclass;
        int i;
        Symbols symbols = Symbols.get();
        Object obj2 = symbols.get("Card.mode", obj);
        Method method = null;
        if (obj2 == null) {
            return null;
        }
        final String strFilterType = filterType(obj2);
        String[] strArr = TYPES;
        if (!strArr[0].equals(strFilterType) && !"default".equals(strFilterType) && catalogLabel(obj2, strFilterType) == null) {
            if (strArr[1].equals(strFilterType)) {
                str = "ccd_base_warm.bin";
            } else {
                str2 = null;
            }
            if (str2 == null) {
                return null;
            }
            strName = symbols.name("ModeUi.filterType");
            for (superclass = obj2.getClass(); superclass != null && method == null; superclass = superclass.getSuperclass()) {
                for (Method method2 : superclass.getDeclaredMethods()) {
                    if (!strName.equals(method2.getName()) && method2.getParameterTypes().length == 0 && method2.getReturnType() == String.class && !Modifier.isAbstract(method2.getModifiers())) {
                        method = method2;
                        break;
                    }
                }
            }
            if (method != null) {
                final String name = symbols.cls("Card").getName();
                final String strName2 = symbols.name("Card.isFilmCard");
                final String strName3 = symbols.name("Card.layout");
                return XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.x10.X10FilterPort.7
                    public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        if (strFilterType.equals(methodHookParam.getResult())) {
                            for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
                                if (name.equals(stackTraceElement.getClassName()) && (strName2.equals(stackTraceElement.getMethodName()) || strName3.equals(stackTraceElement.getMethodName()))) {
                                    methodHookParam.setResult(str2);
                                    return;
                                }
                            }
                        }
                    }
                });
            }
            throw new IllegalStateException("Missing " + obj2.getClass().getName() + "." + strName + "()");
        }
        str = "fuji-proNegHi.bin";
        str2 = str;
        if (str2 == null) {
            return null;
        }
        strName = symbols.name("ModeUi.filterType");
        while (superclass != null) {
            while (i < r8) {
                if (!strName.equals(method2.getName())) {
                }
            }
        }
        if (method != null) {
            final String name2 = symbols.cls("Card").getName();
            final String strName4 = symbols.name("Card.isFilmCard");
            final String strName5 = symbols.name("Card.layout");
            return XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.x10.X10FilterPort.7
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (strFilterType.equals(methodHookParam.getResult())) {
                        for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
                            if (name2.equals(stackTraceElement.getClassName()) && (strName4.equals(stackTraceElement.getMethodName()) || strName5.equals(stackTraceElement.getMethodName()))) {
                                methodHookParam.setResult(str2);
                                return;
                            }
                        }
                    }
                }
            });
        }
        throw new IllegalStateException("Missing " + obj2.getClass().getName() + "." + strName + "()");
    }

    private static void hookPalette(ClassLoader classLoader) {
        try {
            Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.ipuapi.process.filter.palette.PaletteSeekbarColors", classLoader);
            final Class clsFindClass2 = XposedHelpers.findClass("com.oplus.ocs.camera.ipuapi.process.filter.palette.PaletteSeekbarColorRecipe", classLoader);
            XposedHelpers.findAndHookMethod(clsFindClass, "get", new Object[]{String.class, new XC_MethodHook() { // from class: local.jiege.hook.x10.X10FilterPort.2
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    int[] iArr = (int[]) X10FilterPort.PALETTE.get((String) methodHookParam.args[0]);
                    if (iArr == null) {
                        iArr = (int[]) X10FilterPort.PALETTE.get("default");
                    }
                    methodHookParam.setResult(XposedHelpers.newInstance(clsFindClass2, new Object[]{iArr.clone()}));
                }
            }});
            XposedHelpers.findAndHookMethod(clsFindClass, "hasSeekbarColors", new Object[]{String.class, new XC_MethodHook() { // from class: local.jiege.hook.x10.X10FilterPort.3
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    methodHookParam.setResult(Boolean.TRUE);
                }
            }});
            XposedBridge.log("X10Filter: palette compatibility installed");
        } catch (Throwable th) {
            XposedBridge.log("X10Filter: palette hook failed " + th);
        }
    }

    private static void hookPaletteCapture(ClassLoader classLoader) {
        try {
            Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.consumer.ApsProcessor", classLoader);
            Class clsFindClass2 = XposedHelpers.findClass("com.oplus.ocs.camera.common.util.CameraRequestTag", classLoader);
            Class clsFindClass3 = XposedHelpers.findClass("com.oplus.ocs.camera.common.util.ParameterKeys", classLoader);
            final Class clsFindClass4 = XposedHelpers.findClass("com.oplus.ocs.camera.consumer.ApsDataConvert", classLoader);
            final Class clsFindClass5 = XposedHelpers.findClass("com.oplus.ocs.camera.consumer.apsAdapter.config.AlgoSwitchConfig", classLoader);
            final Object staticObjectField = XposedHelpers.getStaticObjectField(clsFindClass3, "KEY_CAPTURE_ALGO_LIST");
            XposedHelpers.findAndHookMethod(clsFindClass, "createMetaItemInfo", new Object[]{clsFindClass2, new XC_MethodHook() { // from class: local.jiege.hook.x10.X10FilterPort.10
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (methodHookParam.hasThrowable()) {
                        return;
                    }
                    try {
                        Object obj = methodHookParam.args[0];
                        if (XposedHelpers.getBooleanField(obj, "mbPaletteEnable")) {
                            Object objectField = XposedHelpers.getObjectField(obj, "mApsRequestTag");
                            int intField = XposedHelpers.getIntField(obj, "mRearFrontCameraId");
                            String str = (String) XposedHelpers.callStaticMethod(clsFindClass4, "getApsModeName", new Object[]{XposedHelpers.getObjectField(objectField, "mModeName"), Integer.valueOf(intField)});
                            if (((Boolean) XposedHelpers.callStaticMethod(clsFindClass5, "getSupportCaptureAlgo", new Object[]{str, Integer.valueOf(intField), "aps_algo_color_palette"})).booleanValue()) {
                                Object result = methodHookParam.getResult();
                                String[] strArr = (String[]) XposedHelpers.callMethod(result, "get", new Object[]{staticObjectField});
                                String[] strArrInclude = PaletteCaptureAlgorithms.include(strArr);
                                if (strArrInclude == strArr) {
                                    return;
                                }
                                XposedHelpers.callMethod(result, "setParameter", new Object[]{staticObjectField, strArrInclude});
                                XposedBridge.log("X10Filter: palette capture algorithm added, request=" + XposedHelpers.getIntField(obj, "mRequestId") + ", mode=" + str + ", camera=" + intField);
                            }
                        }
                    } catch (Throwable th) {
                        XposedBridge.log("X10Filter: palette capture update failed " + th);
                    }
                }
            }});
            XposedBridge.log("X10Filter: palette capture hook installed");
        } catch (Throwable th) {
            XposedBridge.log("X10Filter: palette capture hook install failed " + th);
        }
    }

    private static void hookPaletteColors(ClassLoader classLoader) {
        try {
            Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.ipuapi.process.filter.palette.PaletteColors", classLoader);
            final Class clsFindClass2 = XposedHelpers.findClass("com.oplus.ocs.camera.ipuapi.process.filter.palette.PaletteColorRecipe", classLoader);
            XposedHelpers.findAndHookMethod(clsFindClass, "get", new Object[]{String.class, new XC_MethodHook() { // from class: local.jiege.hook.x10.X10FilterPort.4
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object objNewInstance;
                    String str = (String) methodHookParam.args[0];
                    if (X10FilterPort.TYPES[0].equals(str)) {
                        objNewInstance = XposedHelpers.newInstance(clsFindClass2, new Object[]{new int[]{-6898736, 16777215}, new int[]{-1279595777, -1171735566}});
                    } else {
                        boolean zEquals = X10FilterPort.TYPES[1].equals(str);
                        Class cls = clsFindClass2;
                        objNewInstance = zEquals ? XposedHelpers.newInstance(cls, new Object[]{new int[]{16728642, 1694450242}, null, new int[]{14449968, 1524399408, -2327248}, new float[]{0.0f, 0.43f, 1.0f}}) : XposedHelpers.newInstance(cls, new Object[]{new int[]{16777215, 16777215}, new int[]{1051431803, -1280601221}});
                    }
                    methodHookParam.setResult(objNewInstance);
                }
            }});
            XposedHelpers.findAndHookMethod(clsFindClass, "hasPalette", new Object[]{String.class, new XC_MethodHook() { // from class: local.jiege.hook.x10.X10FilterPort.5
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    methodHookParam.setResult(Boolean.TRUE);
                }
            }});
            XposedBridge.log("X10Filter: palette color compatibility installed");
        } catch (Throwable th) {
            XposedBridge.log("X10Filter: palette color hook failed " + th);
        }
    }

    private static void hookStyleSwitchCount(ClassLoader classLoader) {
        try {
            final Symbols symbols = Symbols.get();
            XposedBridge.hookMethod(symbols.method("FilterPresenter.styleCount"), new XC_MethodHook() { // from class: local.jiege.hook.x10.X10FilterPort.1
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    methodHookParam.setResult(Integer.valueOf(((List) symbols.call("FilterPresenter.types", methodHookParam.thisObject, new Object[0])).size()));
                }
            });
        } catch (Throwable th) {
            XposedBridge.log("X10Filter: style switch count hook failed " + th);
        }
    }

    public static void installCamera(ClassLoader classLoader) {
        ModeName.install(classLoader);
        hookFilterMap(classLoader);
        hookDrawing(classLoader);
        hookMasterCard(classLoader);
        hookPalette(classLoader);
        hookPaletteColors(classLoader);
        hookPaletteCapture(classLoader);
        hookStyleSwitchCount(classLoader);
        XposedBridge.log("X10Filter: loaded");
    }

    private static boolean isStandardCardMode(Object obj) {
        String strOf = ModeName.of(obj);
        if ("common".equals(strOf) || "portrait".equals(strOf)) {
            return true;
        }
        return "highPixel".equals(strOf) && Settings.get("high_pixel_effects");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isX10MasterCard(Object obj) {
        Object obj2 = Symbols.get().get("Card.mode", obj);
        if (obj2 == null) {
            return false;
        }
        String strFilterType = filterType(obj2);
        String[] strArr = TYPES;
        return strArr[0].equals(strFilterType) || strArr[1].equals(strFilterType) || catalogLabel(obj2, strFilterType) != null || ("default".equals(strFilterType) && isStandardCardMode(obj2));
    }

    private static void put(Map<String, int[]> map, int[] iArr, String... strArr) {
        for (String str : strArr) {
            map.put(str, iArr);
        }
    }

    private static void putFilterId(Map<String, Integer> map, String str, int i) {
        Integer num = map.get(str);
        if (num == null || num.intValue() == i) {
            map.put(str, Integer.valueOf(i));
            return;
        }
        throw new IllegalStateException(str + " already has enum " + num);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void releaseNativeMasterCardType(XC_MethodHook.MethodHookParam methodHookParam) {
        XC_MethodHook.Unhook unhook = (XC_MethodHook.Unhook) methodHookParam.getObjectExtra("x10.master.native.type");
        if (unhook != null) {
            unhook.unhook();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void restoreMasterCardLabels(Object obj) {
        TextView[] textViewArrCardTexts = cardTexts(obj);
        TextView textView = textViewArrCardTexts[0];
        TextView textView2 = textViewArrCardTexts[1];
        if (textView == null || textView2 == null) {
            return;
        }
        Resources resources = textView.getResources();
        Object obj2 = Symbols.get().get("Card.mode", obj);
        String strFilterType = filterType(obj2);
        String strCatalogLabel = catalogLabel(obj2, strFilterType);
        int identifier = resources.getIdentifier("camera_filter_oplus_hint", "string", "com.oplus.camera");
        if (identifier == 0) {
            t2.j("Missing native X10 filter label resources");
            return;
        }
        if (!"default".equals(strFilterType) && strCatalogLabel == null) {
            int identifier2 = resources.getIdentifier(TYPES[0].equals(strFilterType) ? "camera_filter_oplus_qing_tou" : "camera_filter_oplus_hu_po", "string", "com.oplus.camera");
            if (identifier2 == 0) {
                t2.j("Missing native X10 filter label resources");
                return;
            }
            textView.setText(identifier2);
        }
        if (strCatalogLabel != null) {
            textView2.setText(strCatalogLabel);
        } else {
            textView2.setText(identifier);
        }
        whiteLabel(textView2);
        Object obj3 = Symbols.get().get("Card.labelBox", obj);
        if (obj3 instanceof View) {
            whiteLabel((View) obj3);
        }
        fitCardTextWidths(textView, textView2, obj3);
    }

    private static void whiteLabel(View view) {
        float[] cornerRadii;
        GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, WHITE_LABEL);
        if ((view.getBackground() instanceof GradientDrawable) && (cornerRadii = ((GradientDrawable) view.getBackground()).getCornerRadii()) != null) {
            gradientDrawable.setCornerRadii(cornerRadii);
        }
        view.setBackground(gradientDrawable);
    }

    public static void fitCardTextWidths(Object obj) {
        TextView textView;
        TextView[] textViewArrCardTexts = cardTexts(obj);
        TextView textView2 = textViewArrCardTexts[0];
        if (textView2 == null || (textView = textViewArrCardTexts[1]) == null) {
            return;
        }
        fitCardTextWidths(textView2, textView, Symbols.get().get("Card.labelBox", obj));
    }
}
