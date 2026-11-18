package local.jiege.hook.masterfilter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.c3;
import defpackage.t2;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.MasterLayout;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class MasterGrain {
    private static final String AMOUNT_KEY = "key_muse_grain_amount_";
    private static final long FADE = 150;
    private static final String FILTER_NODE = "aps_algo_filter";
    private static final String GLOBAL = "";
    private static final String KEY_NAME = "key_master_mode_effect_muse_grain";
    private static final int LEVELS = 101;
    private static final String MASTER_MODE = "professional_mode";
    private static final String PACKAGE = "com.oplus.camera";
    private static final String PREF_GROUP = "com.oplus.camera_preferences_";
    private static final String SCALE_BAR = "com.oplus.camera.coui.seekbar.HSScaleBar";
    private static final String SDK = "com.oplus.ocs.camera.";
    private static final String SIZE_KEY = "key_muse_grain_size_";
    private static final String TAG = "MasterGrain";
    private static final int UNSET = -1;
    private static volatile Object filterNames;
    private static boolean hidingStock;
    private static volatile Object itemKey;
    private static volatile Object leftBar;
    private static volatile Object menu;
    private static Panel panel;
    private static boolean resetting;
    private static volatile Object rightBar;
    private static final List<String> AFTER_FILTER = Arrays.asList("aps_algo_eye_enhance", "aps_algo_super_text", "aps_algo_starburst", "aps_algo_fish_eye", "aps_algo_rotate_mirror", "aps_algo_tilt_shift", "aps_algo_watermark");
    private static final List<String> FILM_MASTERS = Arrays.asList("800t_master.bin", "fuji_cc_master.bin", "fuji-nc_master.bin", "fuji-proNegHi_master.bin");
    private static final String[][] FILM_GRAIN = {new String[]{"KEY_FILTER_GRAIN_TYPE", "2"}, new String[]{"KEY_FILTER_GRAIN_HIGHTLIGHT", "0.85"}, new String[]{"KEY_FILTER_GRAIN_ROUGHNESS", "0.33"}, new String[]{"KEY_FILTER_GRAIN_COLOR_ENABLE", "1"}, new String[]{"KEY_FILTER_GRAIN_COLOR_RATIO", "0.7"}, new String[]{"KEY_FILTER_GRAIN_MIDTONES", "0.9"}, new String[]{"KEY_FILTER_GRAIN_SHADOWS", "0.89"}, new String[]{"KEY_FILTER_GRAIN_FINALLY_ENABLE", "true"}};
    private static final Map<String, Object> keys = new HashMap();
    private static final ThreadLocal<Object> building = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> constructing = new ThreadLocal<>();

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Panel {
        final int expandedLeft;
        final int expandedTy;
        final int halfCenter;
        final Object owner;
        final ViewGroup parent;
        boolean shown;
        final View size;
        final TextView sizeTitle;
        final View strength;
        final TextView strengthTitle;
        final Symbols symbols;

        public Panel(Symbols symbols, Object obj, ViewGroup viewGroup) throws NoSuchMethodException {
            this.symbols = symbols;
            this.owner = obj;
            this.parent = viewGroup;
            Context context = viewGroup.getContext();
            this.halfCenter = (dimen(context, "hs_professional_mode_scale_bar_collapsed_width") / 2) + dimen(context, "hs_professional_mode_scale_bar_collapsed_margin");
            this.expandedLeft = dimen(context, "hs_professional_mode_scale_bar_expanded_margin_left");
            this.expandedTy = dimen(context, "hs_professional_mode_scale_bar_color_tone_title_expanded_ty");
            View viewBar = bar(context);
            this.strength = viewBar;
            View viewBar2 = bar(context);
            this.size = viewBar2;
            MasterGrain.leftBar = viewBar;
            MasterGrain.rightBar = viewBar2;
            TextView textViewTitle = title(context, "强度");
            this.strengthTitle = textViewTitle;
            TextView textViewTitle2 = title(context, "大小");
            this.sizeTitle = textViewTitle2;
            listen(viewBar, MasterGrain.AMOUNT_KEY, textViewTitle, viewBar2, textViewTitle2, MasterGrain.UNSET);
            listen(viewBar2, MasterGrain.SIZE_KEY, textViewTitle2, viewBar, textViewTitle, 1);
            View[] viewArr = {viewBar, viewBar2, textViewTitle, textViewTitle2};
            for (int i = 0; i < 4; i++) {
                View view = viewArr[i];
                view.setVisibility(8);
                viewGroup.addView(view);
            }
        }

        private View bar(Context context) throws NoSuchMethodException {
            ArrayList arrayList = new ArrayList();
            for (int i = 0; i < MasterGrain.LEVELS; i++) {
                arrayList.add(String.valueOf(i));
            }
            Constructor declaredConstructor = XposedHelpers.findClass(MasterGrain.SCALE_BAR, context.getClassLoader()).getDeclaredConstructor(Context.class, Integer.TYPE, Handler.class, List.class, String[].class, HashMap.class);
            MasterGrain.constructing.set(Boolean.TRUE);
            try {
                View view = (View) declaredConstructor.newInstance(context, Integer.valueOf(MasterGrain.LEVELS), new Handler(Looper.getMainLooper()), arrayList, null, null);
                MasterGrain.constructing.remove();
                XposedHelpers.callMethod(view, "setCountBetweenBigScale", new Object[]{10});
                XposedHelpers.callMethod(view, "setScaleInterval", new Object[]{2});
                return view;
            } catch (Throwable th) {
                MasterGrain.constructing.remove();
                throw th;
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void collapse(TextView textView, View view, TextView textView2, int i) {
            if (this.shown) {
                view.animate().alpha(1.0f).setDuration(MasterGrain.FADE).setStartDelay(120L).start();
                textView2.animate().alpha(1.0f).setDuration(MasterGrain.FADE).setStartDelay(120L).start();
                textView.animate().translationX(collapsedX(i)).translationY(0.0f).setDuration(320L).start();
            }
        }

        private float collapsedX(int i) {
            if (this.parent.getLayoutDirection() == 1) {
                i = -i;
            }
            return i * this.halfCenter;
        }

        private static int dimen(Context context, String str) {
            int iX = MasterGrain.x(context, str);
            if (iX == 0) {
                return 0;
            }
            return context.getResources().getDimensionPixelSize(iX);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void expand(TextView textView, View view, TextView textView2) {
            view.animate().alpha(0.0f).setStartDelay(0L).setDuration(MasterGrain.FADE).start();
            textView2.animate().alpha(0.0f).setStartDelay(0L).setDuration(MasterGrain.FADE).start();
            boolean z = this.parent.getLayoutDirection() == 1;
            float width = ((textView.getWidth() / 2.0f) - this.expandedLeft) - (textView.getPaint().measureText(textView.getText().toString()) / 2.0f);
            ViewPropertyAnimator viewPropertyAnimatorAnimate = textView.animate();
            if (!z) {
                width = -width;
            }
            viewPropertyAnimatorAnimate.translationX(width).translationY(this.expandedTy).setStartDelay(0L).setDuration(320L).start();
        }

        private static void fade(final View view, boolean z) {
            view.animate().cancel();
            if (z) {
                view.setAlpha(0.0f);
                view.setVisibility(0);
                view.animate().alpha(1.0f).setStartDelay(0L).setDuration(MasterGrain.FADE).withEndAction(null).start();
            } else if (view.getVisibility() == 0) {
                view.animate().alpha(0.0f).setStartDelay(0L).setDuration(MasterGrain.FADE).withEndAction(new Runnable() { // from class: local.jiege.hook.masterfilter.MasterGrain.Panel.2
                    @Override // java.lang.Runnable
                    public void run() {
                        view.setVisibility(8);
                    }
                }).start();
            }
        }

        private void filterUi(String str) {
            try {
                Symbols symbols = this.symbols;
                symbols.call(str, symbols.get("EffectMenuBase.host", this.owner), new Object[0]);
            } catch (Throwable th) {
                Log.e(MasterGrain.TAG, str + " failed", th);
            }
        }

        private void listen(View view, final String str, final TextView textView, final View view2, final TextView textView2, final int i) {
            final int[] iArr = {MasterGrain.UNSET};
            Class<?> cls = this.symbols.cls("ScaleBarListener");
            this.symbols.call("ScaleBar.setListener", view, Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, new InvocationHandler() { // from class: local.jiege.hook.masterfilter.MasterGrain.Panel.1
                @Override // java.lang.reflect.InvocationHandler
                public Object invoke(Object obj, Method method, Object[] objArr) {
                    String name = method.getName();
                    name.getClass();
                    switch (name) {
                        case "toString":
                            return "MasterGrain bar";
                        case "equals":
                            return Boolean.valueOf(obj == objArr[0]);
                        case "g":
                            Panel.this.tick(iArr, ((Integer) objArr[0]).intValue());
                            Panel panel = Panel.this;
                            MasterGrain.changed(panel.symbols, panel.owner, str, ((Integer) objArr[0]).intValue());
                            return null;
                        case "h":
                            Panel.this.expand(textView, view2, textView2);
                            return null;
                        case "j":
                            Panel.this.collapse(textView, view2, textView2, i);
                            return null;
                        case "hashCode":
                            return Integer.valueOf(System.identityHashCode(obj));
                        default:
                            return null;
                    }
                }
            }));
        }

        private void place(View view, View view2, String str, String str2, int i) {
            view.setLayoutParams(new RelativeLayout.LayoutParams((RelativeLayout.LayoutParams) view2.getLayoutParams()));
            XposedHelpers.callMethod(view, "setShowDots", new Object[]{new ArrayList(Collections.singletonList(Integer.valueOf(i)))});
            int iValue = MasterGrain.value(this.symbols, str, str2);
            this.symbols.call("ScaleBar.select", view, Integer.valueOf(iValue), Boolean.FALSE);
            this.symbols.call("ScaleBar.scrollTo", view, Integer.valueOf(iValue));
        }

        private void placeTitle(TextView textView, View view, int i) {
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams((RelativeLayout.LayoutParams) view.getLayoutParams());
            Context context = textView.getContext();
            int iDimen = dimen(context, "hs_professional_mode_scale_bar_title_margin_top");
            textView.setPadding(0, iDimen, 0, Math.max(0, (layoutParams.height - iDimen) - dimen(context, "hs_professional_mode_scale_bar_title_height")));
            textView.setLayoutParams(layoutParams);
            textView.setTranslationX(collapsedX(i));
            textView.setTranslationY(0.0f);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public void tick(int[] iArr, int i) {
            int i2 = iArr[0];
            iArr[0] = i;
            if (!this.shown || i2 == MasterGrain.UNSET || i2 == i) {
                return;
            }
            int iMin = Math.min(i2, i);
            if (i % 2 == 0 || (2 - (iMin % 2)) + iMin < Math.max(i2, i)) {
                try {
                    boolean zBooleanValue = ((Boolean) XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.oplus.camera.configure.CameraConfig", this.parent.getContext().getClassLoader()), "getConfigBooleanValue", new Object[]{"com.oplus.strong.vibrate.support"})).booleanValue();
                    Symbols symbols = this.symbols;
                    symbols.call("Vibrate.play", symbols.get("Vibrate.instance", null), Integer.valueOf(zBooleanValue ? 1 : 0), 0);
                } catch (Throwable th) {
                    Log.e(MasterGrain.TAG, "tick failed", th);
                }
            }
        }

        private TextView title(Context context, String str) {
            TextView textView = new TextView(context);
            textView.setText(str);
            textView.setTextColor(MasterGrain.UNSET);
            textView.setTextSize(0, dimen(context, "hs_professional_mode_scale_bar_title_text_size"));
            textView.setShadowLayer(2.0f, 0.0f, 0.0f, 1291845632);
            textView.setMaxLines(1);
            textView.setGravity(17);
            return textView;
        }

        public void hide() {
            View[] viewArr = {this.strength, this.size, this.strengthTitle, this.sizeTitle};
            for (int i = 0; i < 4; i++) {
                fade(viewArr[i], false);
            }
            this.shown = false;
            filterUi("FilterSource.showFilterUi");
        }

        public void show(View view) {
            String strCurrentType = MasterGrain.currentType(this.symbols);
            int[] iArrDefaults = MasterGrain.defaults(strCurrentType);
            place(this.strength, view, MasterGrain.AMOUNT_KEY, strCurrentType, iArrDefaults[0]);
            place(this.size, view, MasterGrain.SIZE_KEY, strCurrentType, iArrDefaults[1]);
            placeTitle(this.strengthTitle, view, MasterGrain.UNSET);
            placeTitle(this.sizeTitle, view, 1);
            View[] viewArr = {this.strength, this.size, this.strengthTitle, this.sizeTitle};
            for (int i = 0; i < 4; i++) {
                fade(viewArr[i], true);
            }
            this.shown = true;
            filterUi("FilterSource.hideFilterUi");
        }
    }

    private MasterGrain() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void addItem(Symbols symbols, Object obj, Context context) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        List list = (List) symbols.get("EffectMenuBase.items", obj);
        if (list.size() < 2) {
            return;
        }
        int iId = id(context, "string", "camera_gr_particle_amount");
        int iId2 = id(context, "drawable", "gr_mode_effect_particle_amount_icon");
        if (iId == 0 || iId2 == 0) {
            t2.j("grain resources missing");
            return;
        }
        if (itemKey == null) {
            itemKey = key(symbols, KEY_NAME);
        }
        list.add(list.size() + UNSET, symbols.cls("EffectItem").getDeclaredConstructors()[0].newInstance(Integer.valueOf(iId), Integer.valueOf(iId2), 0, itemKey));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void applyCapture(Symbols symbols, Object obj) {
        String strCurrentType = currentType(symbols);
        int iStored = stored(symbols, AMOUNT_KEY, strCurrentType);
        int iStored2 = stored(symbols, SIZE_KEY, strCurrentType);
        Log.i(TAG, "capture " + XposedHelpers.getObjectField(obj, "mFilterType") + " as " + strCurrentType + " " + iStored + "/" + iStored2);
        if (iStored == UNSET && iStored2 == UNSET) {
            return;
        }
        int[] iArrDefaults = defaults(strCurrentType);
        if (iStored == UNSET) {
            iStored = iArrDefaults[0];
        }
        if (iStored2 == UNSET) {
            iStored2 = iArrDefaults[1];
        }
        if (XposedHelpers.getFloatField(obj, "mbFilterGainAmount") > 0.0f) {
            XposedHelpers.setFloatField(obj, "mbFilterGainAmount", iStored / 100.0f);
            XposedHelpers.setFloatField(obj, "mbFilterGainSize", iStored2 / 100.0f);
        } else if (iStored > 0) {
            applyFilmGrain(obj, iStored, iStored2);
        }
    }

    private static void applyFilmGrain(Object obj, int i, int i2) {
        XposedHelpers.setFloatField(obj, "mbFilterGainAmount", (-i) / 100.0f);
        XposedHelpers.setIntField(obj, "mnFilterGrainType", 2);
        XposedHelpers.setIntField(obj, "mFilterColorGrain", 1);
        XposedHelpers.setFloatField(obj, "mbFilterGainColorRatio", 0.7f);
        XposedHelpers.setFloatField(obj, "mbFilterGainHighlight", 0.85f);
        XposedHelpers.setFloatField(obj, "mbFilterGainMidtones", 0.9f);
        XposedHelpers.setFloatField(obj, "mbFilterGainShadows", 0.89f);
        XposedHelpers.setFloatField(obj, "mbFilterGainSize", i2 / 100.0f);
        XposedHelpers.setFloatField(obj, "mbFilterGainRoughness", 0.33f);
        XposedHelpers.setBooleanField(obj, "mFilterGrainEnable", true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void changed(Symbols symbols, Object obj, String str, int i) {
        try {
            store(symbols, str, currentType(symbols), i);
            int iIndexOf = ((List) symbols.get("EffectMenuBase.items", obj)).indexOf(grainItem(symbols, obj));
            if (iIndexOf >= 0) {
                symbols.call("EffectMenuBase.refreshItem", obj, Integer.valueOf(iIndexOf));
            }
            symbols.call("EffectMenuBase.refreshReset", obj, Boolean.TRUE);
        } catch (Throwable th) {
            Log.e(TAG, "grain value not stored", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String currentType(Symbols symbols) {
        Object obj;
        Object obj2 = filterNames;
        if (obj2 == null) {
            return MasterLayout.STANDARD;
        }
        Object obj3 = symbols.get("FilterNames.presenter", obj2);
        List list = (List) symbols.call("FilterNames.types", obj2, new Object[0]);
        int iIntValue = ((Integer) symbols.call("CaptureParams.filterIndex", obj3, new Object[0])).intValue();
        return (list == null || iIntValue < 0 || iIntValue >= list.size() || (obj = list.get(iIntValue)) == null || "".equals(obj)) ? MasterLayout.STANDARD : obj.toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int[] defaults(String str) {
        boolean z = false;
        if (!str.contains("portra400") && !FILM_MASTERS.contains(str)) {
            if (str.contains("blackandwhite_HC")) {
                return new int[]{40, 47};
            }
            return str.contains("positive") ? new int[]{25, 47} : new int[]{0, 70};
        }
        if ("800t_master.bin".equals(str) && Settings.get("master_effect_neon")) {
            z = true;
        }
        return new int[]{36, z ? 42 : 70};
    }

    private static Object grainItem(Symbols symbols, Object obj) {
        for (Object obj2 : (List) symbols.get("EffectMenuBase.items", obj)) {
            if (isGrain(symbols, obj2)) {
                return obj2;
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hidePanel() {
        Panel panel2 = panel;
        if (panel2 == null || !panel2.shown) {
            return;
        }
        panel2.hide();
    }

    private static void hookHalfBars(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass(SCALE_BAR, classLoader);
        XposedHelpers.findAndHookMethod(clsFindClass, "getDefaultUIState", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.11
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (MasterGrain.constructing.get() != null || methodHookParam.thisObject == MasterGrain.leftBar || methodHookParam.thisObject == MasterGrain.rightBar) {
                    methodHookParam.setResult(0);
                }
            }
        }});
        String[] strArr = {"getCollapsedScaleStartX", "getCollapsedScaleEndX", "getCollapsedTouchStartX", "getCollapsedTouchEndX"};
        for (int i = 0; i < 4; i++) {
            final String str = strArr[i];
            XposedHelpers.findAndHookMethod(clsFindClass, str, new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.12
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (methodHookParam.thisObject != MasterGrain.rightBar) {
                    }
                    View view = (View) methodHookParam.thisObject;
                    int measuredWidth = view.getMeasuredWidth() / 2;
                    byte b = 0;
                    int iIntValue = ((Integer) XposedHelpers.callMethod(view, "getCollapsedContainerMargin", new Object[0])).intValue();
                    int iIntValue2 = ((Integer) XposedHelpers.callMethod(view, "getCollapsedContainerWidth", new Object[0])).intValue();
                    int iIntValue3 = ((Integer) XposedHelpers.callMethod(view, "getCollapsedScaleMarginStart", new Object[0])).intValue();
                    int iIntValue4 = ((Integer) XposedHelpers.callMethod(view, "getScaleWidth", new Object[0])).intValue();
                    String str2 = str;
                    str2.getClass();
                    switch (str2.hashCode()) {
                        case -2056589836:
                            if (!str2.equals("getCollapsedTouchStartX")) {
                                b = MasterGrain.UNSET;
                            }
                            break;
                        case -1189528410:
                            b = !str2.equals("getCollapsedScaleEndX") ? MasterGrain.UNSET : (byte) 1;
                            break;
                        case -269212737:
                            b = !str2.equals("getCollapsedScaleStartX") ? MasterGrain.UNSET : (byte) 2;
                            break;
                        default:
                            b = MasterGrain.UNSET;
                            break;
                    }
                    switch (b) {
                        case 0:
                            methodHookParam.setResult(Integer.valueOf(measuredWidth));
                            break;
                        case 1:
                            methodHookParam.setResult(Integer.valueOf((((iIntValue2 + iIntValue) + measuredWidth) - iIntValue3) - (iIntValue4 / 2)));
                            break;
                        case 2:
                            methodHookParam.setResult(Integer.valueOf((iIntValue4 / 2) + iIntValue3 + iIntValue + measuredWidth));
                            break;
                        default:
                            methodHookParam.setResult(Integer.valueOf((iIntValue * 2) + iIntValue2 + measuredWidth));
                            break;
                    }
                }
            }});
        }
    }

    private static void hookStandardCapture(final Symbols symbols, ClassLoader classLoader) {
        final Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.common.util.ParameterKeys", classLoader);
        final Object staticObjectField = XposedHelpers.getStaticObjectField(clsFindClass, "KEY_CAPTURE_ALGO_LIST");
        final Object staticObjectField2 = XposedHelpers.getStaticObjectField(clsFindClass, "KEY_FILTER_TYPE");
        XposedHelpers.findAndHookMethod("com.oplus.ocs.camera.consumer.ApsProcessor", classLoader, "createMetaItemInfo", new Object[]{"com.oplus.ocs.camera.common.util.CameraRequestTag", new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.13
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                String strCurrentType;
                int iStored;
                if (methodHookParam.hasThrowable()) {
                    return;
                }
                try {
                    Object obj = methodHookParam.args[0];
                    Object objectField = XposedHelpers.getObjectField(obj, "mApsRequestTag");
                    if (objectField != null && MasterGrain.MASTER_MODE.equals(XposedHelpers.getObjectField(objectField, "mModeName"))) {
                        Object objectField2 = XposedHelpers.getObjectField(obj, "mFilterType");
                        if ((!XposedHelpers.getBooleanField(obj, "mbFilterOpen") || MasterLayout.STANDARD.equals(objectField2)) && (iStored = MasterGrain.stored(symbols, MasterGrain.AMOUNT_KEY, (strCurrentType = MasterGrain.currentType(symbols)))) > 0) {
                            int iStored2 = MasterGrain.stored(symbols, MasterGrain.SIZE_KEY, strCurrentType);
                            if (iStored2 == MasterGrain.UNSET) {
                                iStored2 = MasterGrain.defaults(strCurrentType)[1];
                            }
                            Object result = methodHookParam.getResult();
                            String[] strArr = (String[]) XposedHelpers.callMethod(result, "get", new Object[]{staticObjectField});
                            if (strArr == null) {
                                return;
                            }
                            ArrayList arrayList = new ArrayList(Arrays.asList(strArr));
                            if (!arrayList.contains(MasterGrain.FILTER_NODE)) {
                                int size = arrayList.size();
                                for (int i = 0; i < arrayList.size(); i++) {
                                    if (MasterGrain.AFTER_FILTER.contains(arrayList.get(i))) {
                                        size = i;
                                        break;
                                    }
                                }
                                arrayList.add(size, MasterGrain.FILTER_NODE);
                                XposedHelpers.callMethod(result, "setParameter", new Object[]{staticObjectField, arrayList.toArray(new String[0])});
                            }
                            XposedHelpers.callMethod(result, "setParameter", new Object[]{staticObjectField2, ""});
                            for (String[] strArr2 : MasterGrain.FILM_GRAIN) {
                                XposedHelpers.callMethod(result, "setParameter", new Object[]{XposedHelpers.getStaticObjectField(clsFindClass, strArr2[0]), strArr2[1]});
                            }
                            XposedHelpers.callMethod(result, "setParameter", new Object[]{XposedHelpers.getStaticObjectField(clsFindClass, "KEY_FILTER_GRAIN_AMOUNT"), String.valueOf((-iStored) / 100.0f)});
                            XposedHelpers.callMethod(result, "setParameter", new Object[]{XposedHelpers.getStaticObjectField(clsFindClass, "KEY_FILTER_GRAIN_SIZE"), String.valueOf(iStored2 / 100.0f)});
                            Log.i(MasterGrain.TAG, "standard capture grain " + iStored + "/" + iStored2 + " " + arrayList);
                        }
                    }
                } catch (Throwable th) {
                    Log.e(MasterGrain.TAG, "standard capture grain failed", th);
                }
            }
        }});
    }

    private static int id(Context context, String str, String str2) {
        return context.getResources().getIdentifier(str2, str, PACKAGE);
    }

    public static void install(ClassLoader classLoader) {
        final Symbols symbols = Symbols.get();
        XposedBridge.hookAllConstructors(symbols.cls("FilterNames"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                MasterGrain.filterNames = methodHookParam.thisObject;
            }
        });
        XposedBridge.hookAllConstructors(symbols.cls("EffectMenu"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = MasterGrain.building.get();
                Object obj2 = methodHookParam.thisObject;
                if (obj == obj2) {
                    MasterGrain.menu = obj2;
                    MasterGrain.panel = null;
                }
                MasterGrain.building.remove();
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object[] objArr = methodHookParam.args;
                if ("masterVideo".equals(objArr[objArr.length + MasterGrain.UNSET])) {
                    return;
                }
                MasterGrain.building.set(methodHookParam.thisObject);
            }
        });
        XposedBridge.hookAllConstructors(symbols.cls("EffectContainer"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.3
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = MasterGrain.building.get();
                if (obj == null) {
                    return;
                }
                try {
                    MasterGrain.addItem(symbols, obj, (Context) XposedHelpers.callMethod(methodHookParam.thisObject, "getContext", new Object[0]));
                } catch (Throwable th) {
                    Log.e(MasterGrain.TAG, "grain item not added", th);
                }
            }
        });
        Class<?> cls = symbols.cls("EffectContainer");
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.4
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                View view = (View) methodHookParam.thisObject;
                if (view.getTranslationX() == 0.0f || !(view.getParent() instanceof View)) {
                    return;
                }
                if (view.getWidth() <= ((View) view.getParent()).getWidth() - (((Integer) symbols.get("EffectContainer.sideMargin", view)).intValue() * 2)) {
                    return;
                }
                view.setTranslationX(0.0f);
                symbols.call("EffectContainer.updateBounds", view, new Object[0]);
            }
        };
        Class cls2 = Boolean.TYPE;
        Class cls3 = Integer.TYPE;
        XposedHelpers.findAndHookMethod(cls, "onLayout", new Object[]{cls2, cls3, cls3, cls3, cls3, xC_MethodHook});
        XposedBridge.hookMethod(symbols.method("EffectItemView.onClick"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.5
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                MasterGrain.resetting = false;
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = symbols.get("EffectItemView.item", methodHookParam.thisObject);
                Object obj2 = MasterGrain.menu;
                if (obj2 == null) {
                    return;
                }
                if (MasterGrain.y(obj, symbols)) {
                    MasterGrain.togglePanel(symbols, obj2);
                    methodHookParam.setResult((Object) null);
                } else {
                    MasterGrain.hidePanel();
                    MasterGrain.resetting = obj != null && symbols.get("EffectItem.key", obj) == null;
                }
            }
        });
        XposedBridge.hookMethod(symbols.method("EffectMenu.dispatch"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.6
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (MasterGrain.itemKey == null || !MasterGrain.itemKey.equals(methodHookParam.args[1])) {
                    return;
                }
                methodHookParam.setResult((Object) null);
                if (MasterGrain.resetting) {
                    try {
                        String strCurrentType = MasterGrain.currentType(symbols);
                        MasterGrain.store(symbols, MasterGrain.AMOUNT_KEY, strCurrentType, MasterGrain.UNSET);
                        MasterGrain.store(symbols, MasterGrain.SIZE_KEY, strCurrentType, MasterGrain.UNSET);
                    } catch (Throwable th) {
                        Log.e(MasterGrain.TAG, "grain reset failed", th);
                    }
                }
            }
        });
        XposedBridge.hookMethod(symbols.method("EffectMenu.modified"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.7
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    Object objV = MasterGrain.v(methodHookParam.thisObject, symbols);
                    if (objV == null) {
                        return;
                    }
                    String strCurrentType = MasterGrain.currentType(symbols);
                    symbols.set("EffectItem.value", objV, Integer.valueOf((MasterGrain.stored(symbols, MasterGrain.AMOUNT_KEY, strCurrentType) == MasterGrain.UNSET && MasterGrain.stored(symbols, MasterGrain.SIZE_KEY, strCurrentType) == MasterGrain.UNSET) ? 0 : 1));
                } catch (Throwable th) {
                    Log.e(MasterGrain.TAG, "grain state not read", th);
                }
            }
        });
        XposedBridge.hookMethod(symbols.method("EffectMenu.hideSeekBar"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.8
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (MasterGrain.hidingStock) {
                    return;
                }
                MasterGrain.hidePanel();
            }
        });
        XposedBridge.hookMethod(symbols.method("EffectValueView.bind"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.9
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (MasterGrain.y(symbols.get("EffectItemView.item", methodHookParam.thisObject), symbols)) {
                    MasterGrain.show(symbols, methodHookParam.thisObject);
                    methodHookParam.setResult((Object) null);
                }
            }
        });
        final Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.camera.producer.mode.ProfessionalMode", classLoader);
        XposedHelpers.findAndHookMethod("com.oplus.ocs.camera.producer.mode.BaseMode", classLoader, "setPositiveFilterGrain", new Object[]{"com.oplus.ocs.camera.common.util.CameraRequestTag", new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterGrain.10
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = methodHookParam.args[0];
                if (obj == null || !clsFindClass.isInstance(methodHookParam.thisObject)) {
                    return;
                }
                try {
                    MasterGrain.applyCapture(symbols, obj);
                } catch (Throwable th) {
                    Log.e(MasterGrain.TAG, "capture grain failed", th);
                }
            }
        }});
        hookHalfBars(classLoader);
        hookStandardCapture(symbols, classLoader);
        Log.i(TAG, "master grain installed");
    }

    private static boolean isGrain(Symbols symbols, Object obj) {
        return (obj == null || itemKey == null || !itemKey.equals(symbols.get("EffectItem.key", obj))) ? false : true;
    }

    private static Object key(Symbols symbols, String str) {
        Object objNewInstance;
        Map<String, Object> map = keys;
        synchronized (map) {
            try {
                objNewInstance = map.get(str);
                if (objNewInstance == null) {
                    objNewInstance = symbols.method("Prefs.get").getParameterTypes()[0].getConstructor(String.class, String.class, String.class, Class.class).newInstance(symbols.call("Prefs.storage", null, new Object[0]), PREF_GROUP, str, Integer.class);
                    map.put(str, objNewInstance);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return objNewInstance;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void show(Symbols symbols, Object obj) {
        TextView textView = (TextView) symbols.get("EffectItemView.name", obj);
        Context context = textView.getContext();
        textView.setText("颗粒");
        ((ImageView) symbols.get("EffectItemView.icon", obj)).setImageResource(id(context, "drawable", "gr_mode_effect_particle_amount_icon"));
        ((TextView) symbols.get("EffectValueView.value", obj)).setText(String.valueOf(value(symbols, AMOUNT_KEY, currentType(symbols))));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void store(Symbols symbols, String str, String str2, int i) {
        symbols.call("Prefs.put", null, key(symbols, c3.j(str, str2)), "", Integer.class, Integer.valueOf(i));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int stored(Symbols symbols, String str, String str2) {
        Object objCall = symbols.call("Prefs.get", null, key(symbols, c3.j(str, str2)), "", Integer.class, Integer.valueOf(UNSET));
        return objCall instanceof Integer ? ((Integer) objCall).intValue() : UNSET;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void togglePanel(Symbols symbols, Object obj) {
        Panel panel2 = panel;
        if (panel2 != null && panel2.shown) {
            panel2.hide();
            return;
        }
        hidingStock = true;
        try {
            symbols.call("EffectMenuBase.hideSeekBar", obj, Boolean.TRUE);
            hidingStock = false;
            View view = (View) symbols.get("EffectMenuBase.seekBar", obj);
            ViewGroup viewGroup = (ViewGroup) view.getParent();
            View viewFindViewById = viewGroup.getRootView().findViewById(id(viewGroup.getContext(), "id", "softLightLayout"));
            if (viewFindViewById != null && viewFindViewById.isShown()) {
                viewFindViewById.setVisibility(8);
            }
            Panel panel3 = panel;
            if (panel3 == null || panel3.parent != viewGroup) {
                panel3 = new Panel(symbols, obj, viewGroup);
                panel = panel3;
            }
            panel3.show(view);
            Log.i(TAG, "panel shown for " + currentType(symbols));
        } catch (Throwable th) {
            hidingStock = false;
            throw th;
        }
    }

    public static /* bridge */ /* synthetic */ Object v(Object obj, Symbols symbols) {
        return grainItem(symbols, obj);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int value(Symbols symbols, String str, String str2) {
        int iStored = stored(symbols, str, str2);
        return iStored != UNSET ? iStored : defaults(str2)[!AMOUNT_KEY.equals(str) ? 1 : 0];
    }

    public static /* bridge */ /* synthetic */ int x(Context context, String str) {
        return id(context, "dimen", str);
    }

    public static /* bridge */ /* synthetic */ boolean y(Object obj, Symbols symbols) {
        return isGrain(symbols, obj);
    }
}
