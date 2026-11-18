package local.jiege.hook.filterpanel;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.hd;
import defpackage.id;
import defpackage.kg0;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Reflection;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.FilterCatalog;
import local.jiege.hook.filters.FilterEntry;
import local.jiege.hook.filters.FilterScope;
import local.jiege.hook.masterfilter.MasterPresets;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class LegacyFilterPanel {
    private static final String CAMERA = "com.oplus.camera";
    private static final String CAPTURE_PARAMS = "com.oplus.camera.feature.capture_params";
    private static final String CONTAINER = "com.oplus.camera.common.view.BottomEffectContainer";
    private static final int CONTROLS_ANIMATION = 150;
    private static final int DRAWN_AROUND_CENTRE = 4;
    private static final String EFFECT_STYLE = "com.oplus.feature.effect.style.support";
    private static final String FILTER_BEAN = "com.oplus.camera.filter.FilterBean";
    private static final String FILTER_MENU = "pref_filter_menu";
    private static final String MASTER = "professional";
    private static final float SWIPE_UP_DP = 24.0f;
    private static final String TAG = "LegacyFilterPanel";
    private static HiddenControls hiddenControls;
    private static Object open;
    private static boolean openMaster;
    private static boolean opening;
    private static boolean savedPaletteList;
    private static boolean shown;
    private static boolean stripOnly;
    private static final Map<Object, WeakReference<Object>> presenters = Collections.synchronizedMap(new WeakHashMap());
    private static final Map<Object, WeakReference<Object>> beautyUis = Collections.synchronizedMap(new WeakHashMap());
    private static final Map<Object, WeakReference<Object>> effectPanels = Collections.synchronizedMap(new WeakHashMap());
    private static final Map<Object, WeakReference<Object>> controlUis = Collections.synchronizedMap(new WeakHashMap());
    private static WeakReference<Object> expandedPanel = new WeakReference<>(null);
    private static final Set<Object> ownItems = Collections.newSetFromMap(new IdentityHashMap());
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static int pendingPage = -1;
    private static final Map<String, Object> keys = new HashMap();

    /* JADX INFO: renamed from: local.jiege.hook.filterpanel.LegacyFilterPanel$16, reason: invalid class name */
    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public class AnonymousClass16 extends XC_MethodHook {
        final /* synthetic */ Symbols val$symbols;

        public AnonymousClass16(Symbols symbols) {
            this.val$symbols = symbols;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static /* synthetic */ void lambda$beforeHookedMethod$0(Object obj, Symbols symbols) {
            int i = LegacyFilterPanel.pendingPage;
            LegacyFilterPanel.pendingPage = -1;
            if (i < 0 || LegacyFilterPanel.open != obj) {
                return;
            }
            try {
                LegacyFilterPanel.selectPage(symbols, obj, i);
            } catch (Throwable th) {
                Log.e(LegacyFilterPanel.TAG, "page selection failed", th);
            }
        }

        public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            final Object obj = LegacyFilterPanel.open;
            if (obj == null) {
                return;
            }
            methodHookParam.setResult((Object) null);
            boolean z = LegacyFilterPanel.pendingPage >= 0;
            LegacyFilterPanel.pendingPage = ((Integer) methodHookParam.args[0]).intValue();
            if (z) {
                return;
            }
            Handler handler = LegacyFilterPanel.main;
            final Symbols symbols = this.val$symbols;
            handler.post(new Runnable() { // from class: local.jiege.hook.filterpanel.a
                @Override // java.lang.Runnable
                public final void run() {
                    LegacyFilterPanel.AnonymousClass16.lambda$beforeHookedMethod$0(obj, symbols);
                }
            });
        }
    }

    /* JADX INFO: renamed from: local.jiege.hook.filterpanel.LegacyFilterPanel$2, reason: invalid class name */
    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public class AnonymousClass2 extends XC_MethodHook {
        /* JADX INFO: Access modifiers changed from: private */
        public static /* synthetic */ boolean lambda$afterHookedMethod$0(View view) {
            return LegacyFilterPanel.trigger() == 2 && LegacyFilterPanel.open(view);
        }

        public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            View view = (View) methodHookParam.thisObject;
            if (view.getId() <= 0 || !"camera_menu_right_enter_button".equals(view.getResources().getResourceEntryName(view.getId()))) {
                return;
            }
            view.setOnLongClickListener(new b());
            view.setOnTouchListener(new SwipeUp(0));
        }
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class HiddenControls {
        final WeakReference<Object> control;
        final boolean mainShutter;
        final boolean sideShutter;
        final boolean sideShutterEnabled;
        final boolean switchCamera;
        final boolean thumbnail;

        public HiddenControls(Object obj, Activity activity, View view) {
            this.control = new WeakReference<>(obj);
            this.mainShutter = visible(activity, "shutter_button");
            this.thumbnail = visible(activity, "thumbnail");
            this.switchCamera = visible(activity, "switch_camera_button");
            this.sideShutter = view != null && view.getVisibility() == 0;
            this.sideShutterEnabled = view == null || view.isEnabled();
        }

        private static boolean visible(Activity activity, String str) {
            int identifier = activity.getResources().getIdentifier(str, "id", LegacyFilterPanel.CAMERA);
            View viewFindViewById = identifier == 0 ? null : activity.findViewById(identifier);
            return viewFindViewById != null && viewFindViewById.getVisibility() == 0;
        }
    }

    private LegacyFilterPanel() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void cardNames(List<Object> list, FilterScope filterScope, Resources resources, ClassLoader classLoader) {
        String string;
        int identifier = resources.getIdentifier("camera_filter_none", "string", CAMERA);
        int identifier2 = resources.getIdentifier("effect_style_standard", "string", CAMERA);
        String string2 = identifier == 0 ? null : resources.getString(identifier);
        for (int i = 0; i < list.size(); i++) {
            Object obj = list.get(i);
            if (!ownItems.contains(obj)) {
                String str = (String) XposedHelpers.getObjectField(obj, "mType");
                FilterEntry filterEntryFind = str == null ? null : FilterCatalog.get(filterScope).find(str);
                if (filterEntryFind == null || (string = filterEntryFind.title) == null) {
                    string = (identifier2 == 0 || string2 == null || !string2.equals(XposedHelpers.getObjectField(obj, "mName"))) ? null : resources.getString(identifier2);
                }
                if (string != null) {
                    list.set(i, item(classLoader, str, string));
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void close(Object obj) {
        Object obj2 = open;
        if (obj2 == null) {
            return;
        }
        try {
            if (menuOf(Symbols.get(), obj2) != obj) {
                return;
            } else {
                Symbols.get().set("FilterPresenter.paletteList", obj2, Boolean.valueOf(savedPaletteList));
            }
        } catch (Throwable th) {
            Log.e(TAG, "strip close failed", th);
        }
        open = null;
        pendingPage = -1;
        Object obj3 = expandedPanel.get();
        expandedPanel = new WeakReference<>(null);
        if (obj3 != null) {
            try {
                restoreControls(Symbols.get());
                Symbols symbols = Symbols.get();
                Boolean bool = Boolean.TRUE;
                symbols.call("EffectPanelUi.collapse", obj3, FILTER_MENU, bool, bool);
            } catch (Throwable th2) {
                Log.e(TAG, "effect panel collapse failed", th2);
            }
        }
        Log.i(TAG, "strip closed");
    }

    private static int currentPage(Symbols symbols, Object obj) {
        boolean z = stripOnly;
        stripOnly = false;
        try {
            return ((Integer) symbols.call("FilterPresenter.styleCurrent", obj, new Object[0])).intValue();
        } finally {
            stripOnly = z;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void forgetNames(Symbols symbols, Object obj) {
        Object objFindFieldByType = Reflection.findFieldByType(symbols.get("FilterMenu.strip", menuOf(symbols, obj)), "com.oplus.camera.filter.GLProducerRender2DHorizontal");
        Object objectField = objFindFieldByType == null ? null : XposedHelpers.getObjectField(objFindFieldByType, "mFilterNameMarquee");
        if (objectField != null) {
            XposedHelpers.setIntField(objectField, "mCurFilterIndex", -1);
        }
    }

    private static void hideControls(Symbols symbols, Activity activity) {
        Object objOf = of(controlUis, activity);
        if (objOf == null) {
            Log.i(TAG, "control UI unknown, controls stay");
        } else {
            hiddenControls = new HiddenControls(objOf, activity, (View) symbols.get("ControlUi.sideShutterView", objOf));
            symbols.call("ControlUi.hideControls", objOf, Boolean.TRUE);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookButton(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.oplus.camera.ui.effectcontainer.MenuRightButton", classLoader, "onAttachedToWindow", new Object[]{new AnonymousClass2()});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookDrawWindow(final Symbols symbols, ClassLoader classLoader) {
        final int[] iArr = new int[2];
        final ThreadLocal threadLocal = new ThreadLocal();
        XposedBridge.hookAllMethods(XposedHelpers.findClass("com.oplus.ocs.camera.ipusdk.processunit.filter.vendorsdk.meicam.MeisheFilterSdk", classLoader), "drawFiltersFrame", new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.7
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                threadLocal.remove();
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = LegacyFilterPanel.open;
                if (obj != null) {
                    Object[] objArr = methodHookParam.args;
                    if (objArr.length >= 5) {
                        Object obj2 = objArr[0];
                        if ((obj2 instanceof List) && (objArr[LegacyFilterPanel.DRAWN_AROUND_CENTRE] instanceof Float)) {
                            try {
                                int size = ((List) obj2).size();
                                if (size <= 9) {
                                    return;
                                }
                                Symbols symbols2 = symbols;
                                int i = size - 1;
                                int iMax = Math.max(0, Math.min(i, ((Integer) symbols.call("FilterStrip.indexAt", symbols2.get("FilterMenu.strip", LegacyFilterPanel.y(obj, symbols2)), methodHookParam.args[LegacyFilterPanel.DRAWN_AROUND_CENTRE])).intValue()));
                                iArr[0] = Math.max(0, iMax - 4);
                                iArr[1] = Math.min(i, iMax + LegacyFilterPanel.DRAWN_AROUND_CENTRE);
                                threadLocal.set(iArr);
                            } catch (Throwable th) {
                                Log.e(LegacyFilterPanel.TAG, "strip draw window failed", th);
                            }
                        }
                    }
                }
            }
        });
        for (Method method : XposedHelpers.findClass("com.meicam.effect.oppo.MeisheRender", classLoader).getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if ("drawFiltersFrame".equals(method.getName()) && parameterTypes.length == 9) {
                XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.8
                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        int[] iArr2 = (int[]) threadLocal.get();
                        if (iArr2 == null) {
                            return;
                        }
                        methodHookParam.args[6] = Integer.valueOf(iArr2[0]);
                        methodHookParam.args[7] = Integer.valueOf(iArr2[1]);
                    }
                });
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookHide(Symbols symbols, ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(CONTAINER, classLoader, "setVisibilityState", new Object[]{XposedHelpers.findClass("com.oplus.camera.common.view.BottomEffectContainer$VisibilityState", classLoader), new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.11
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = LegacyFilterPanel.open;
                if (obj == null || LegacyFilterPanel.opening) {
                    return;
                }
                String strValueOf = String.valueOf(methodHookParam.args[0]);
                if ("FADING_OUT".equals(strValueOf) || "HIDDEN".equals(strValueOf)) {
                    try {
                        Object objY = LegacyFilterPanel.y(obj, Symbols.get());
                        if (Reflection.findFieldByType(objY, LegacyFilterPanel.CONTAINER) == methodHookParam.thisObject) {
                            LegacyFilterPanel.close(objY);
                        }
                    } catch (Throwable th) {
                        Log.e(LegacyFilterPanel.TAG, "strip close check failed", th);
                    }
                }
            }
        }});
        XposedBridge.hookMethod(symbols.method("FilterMenu.show"), new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.12
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (LegacyFilterPanel.opening) {
                    return;
                }
                LegacyFilterPanel.close(methodHookParam.thisObject);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookItems(final Symbols symbols, final ClassLoader classLoader) {
        XposedBridge.hookAllMethods(XposedHelpers.findClass("com.oplus.camera.filter.FilterHelper", classLoader), "setImageResourceHorizontal", new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.13
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object[] objArr;
                ArrayList arrayList;
                Object obj = LegacyFilterPanel.open;
                if (obj == null) {
                    return;
                }
                try {
                    LegacyFilterPanel.forgetNames(symbols, obj);
                    int i = 0;
                    while (true) {
                        objArr = methodHookParam.args;
                        if (i >= objArr.length) {
                            i = -1;
                            break;
                        } else if (objArr[i] instanceof List) {
                            break;
                        } else {
                            i++;
                        }
                    }
                    if (i >= 0 && (objArr[2] instanceof Integer)) {
                        List list = (List) objArr[i];
                        if (LegacyFilterPanel.openMaster) {
                            arrayList = LegacyFilterPanel.presetItems(symbols, obj, list, classLoader);
                            if (Boolean.TRUE.equals(methodHookParam.args[i - 1])) {
                                Collections.reverse(arrayList);
                            }
                        } else {
                            arrayList = new ArrayList(list);
                        }
                        LegacyFilterPanel.cardNames(arrayList, LegacyFilterPanel.openMaster ? FilterScope.MASTER : FilterScope.NORMAL, (Resources) methodHookParam.args[0], classLoader);
                        Object[] objArr2 = methodHookParam.args;
                        objArr2[i] = arrayList;
                        objArr2[2] = Integer.valueOf(LegacyFilterPanel.w(obj, symbols));
                    }
                } catch (Throwable th) {
                    Log.e(LegacyFilterPanel.TAG, "strip items failed", th);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookPreviewTap(final Symbols symbols) {
        XposedHelpers.findAndHookMethod(symbols.cls("EffectPanelBase"), "isExpanded", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.9
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (LegacyFilterPanel.open == null || methodHookParam.thisObject != LegacyFilterPanel.expandedPanel.get()) {
                    return;
                }
                methodHookParam.setResult(Boolean.TRUE);
            }
        }});
        XposedBridge.hookMethod(symbols.method("EffectPanelBase.revoke"), new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.10
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = LegacyFilterPanel.open;
                if (obj == null || methodHookParam.thisObject != LegacyFilterPanel.expandedPanel.get()) {
                    return;
                }
                try {
                    Symbols symbols2 = symbols;
                    symbols2.call("FilterMenu.hideAnimated", LegacyFilterPanel.y(obj, symbols2), Boolean.TRUE);
                } catch (Throwable th) {
                    Log.e(LegacyFilterPanel.TAG, "strip revoke failed", th);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookStrip(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("FilterStripHost.current"), new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.14
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = LegacyFilterPanel.open;
                if (obj != null) {
                    methodHookParam.setResult(Integer.valueOf(LegacyFilterPanel.pendingPage >= 0 ? LegacyFilterPanel.pendingPage : LegacyFilterPanel.w(obj, symbols)));
                }
            }
        });
        XposedBridge.hookMethod(symbols.method("FilterStripHost.inRange"), new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.15
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = LegacyFilterPanel.open;
                if (obj == null) {
                    return;
                }
                boolean z = false;
                int iIntValue = ((Integer) methodHookParam.args[0]).intValue();
                if (iIntValue >= 0 && iIntValue < ((Integer) symbols.call("FilterPresenter.styleCount", obj, new Object[0])).intValue()) {
                    z = true;
                }
                methodHookParam.setResult(Boolean.valueOf(z));
            }
        });
        XposedBridge.hookMethod(symbols.method("FilterStripHost.select"), new AnonymousClass16(symbols));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookStripOnly(Symbols symbols, ClassLoader classLoader) {
        XposedBridge.hookMethod(symbols.method("FilterPresenter.showMenu"), new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.3
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (LegacyFilterPanel.stripOnly) {
                    LegacyFilterPanel.stripOnly = false;
                    LegacyFilterPanel.shown = !methodHookParam.hasThrowable();
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (LegacyFilterPanel.opening && methodHookParam.thisObject == LegacyFilterPanel.open) {
                    LegacyFilterPanel.stripOnly = true;
                }
            }
        });
        XposedBridge.hookMethod(symbols.method("FilterPresenter.palette"), new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.4
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (LegacyFilterPanel.stripOnly) {
                    methodHookParam.setResult(Boolean.FALSE);
                }
            }
        });
        XposedHelpers.findAndHookMethod("com.oplus.camera.configure.CameraConfig", classLoader, "getConfigBooleanValue", new Object[]{String.class, new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.5
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (LegacyFilterPanel.stripOnly && LegacyFilterPanel.EFFECT_STYLE.equals(methodHookParam.args[0])) {
                    methodHookParam.setResult(Boolean.FALSE);
                }
            }
        }});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookStripTone(ClassLoader classLoader) {
        XposedBridge.hookAllConstructors(XposedHelpers.findClass("com.oplus.camera.filter.GLModelParam", classLoader), new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.6
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (LegacyFilterPanel.open == null || !LegacyFilterPanel.openMaster) {
                    return;
                }
                XposedHelpers.callMethod(methodHookParam.thisObject, "setNeedBasicToneConvert", new Object[]{Boolean.TRUE});
            }
        });
    }

    public static void installCamera(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        Log.guard(TAG, "instances", new id(symbols, (byte) 10));
        Log.guard(TAG, "long press", new hd(classLoader, (byte) 10));
        Log.guard(TAG, "strip only", new kg0(symbols, classLoader, (byte) 0));
        Log.guard(TAG, "strip items", new kg0(symbols, classLoader, (byte) 1));
        Log.guard(TAG, "strip callbacks", new id(symbols, (byte) 11));
        Log.guard(TAG, "menu hide", new kg0(symbols, classLoader, (byte) 2));
        Log.guard(TAG, "preview tap", new id(symbols, (byte) 12));
        Log.guard(TAG, "strip input tone", new hd(classLoader, (byte) 11));
        Log.guard(TAG, "strip draw window", new kg0(symbols, classLoader, (byte) 3));
    }

    private static Object item(ClassLoader classLoader, String str, Object obj) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Object objNewInstance = XposedHelpers.findClass(FILTER_BEAN, classLoader).getDeclaredConstructor(null).newInstance(null);
        XposedHelpers.setObjectField(objNewInstance, "mType", str);
        XposedHelpers.setObjectField(objNewInstance, "mName", obj);
        return objNewInstance;
    }

    private static Object key(Symbols symbols, String str) {
        Object objFindFeatureKey;
        Map<String, Object> map = keys;
        synchronized (map) {
            try {
                objFindFeatureKey = map.get(str);
                if (objFindFeatureKey == null) {
                    objFindFeatureKey = Reflection.findFeatureKey(symbols.cls("FeatureKeys"), symbols.cls("FeatureKey"), str);
                    map.put(str, objFindFeatureKey);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return objFindFeatureKey;
    }

    private static Object menuOf(Symbols symbols, Object obj) {
        return symbols.call("FilterMenuHolder.menu", symbols.get("FilterPresenter.menus", obj), new Object[0]);
    }

    private static Object of(Map<Object, WeakReference<Object>> map, Activity activity) {
        WeakReference<Object> weakReference = activity == null ? null : map.get(activity);
        if (weakReference == null) {
            return null;
        }
        return weakReference.get();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code restructure failed: missing block: B:13:0x003b, code lost:
    
        if (r7 == null) goto L14;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static boolean open(android.view.View r11) {
        /*
            java.lang.String r1 = "LegacyFilterPanel"
            java.lang.String r0 = "long press ignored: "
            r2 = 0
            android.content.Context r3 = r11.getContext()     // Catch: java.lang.Throwable -> L47
            android.app.Activity r5 = activityOf(r3)     // Catch: java.lang.Throwable -> L47
            java.util.Map<java.lang.Object, java.lang.ref.WeakReference<java.lang.Object>> r3 = local.jiege.hook.filterpanel.LegacyFilterPanel.presenters     // Catch: java.lang.Throwable -> L47
            java.lang.Object r6 = of(r3, r5)     // Catch: java.lang.Throwable -> L47
            java.util.Map<java.lang.Object, java.lang.ref.WeakReference<java.lang.Object>> r3 = local.jiege.hook.filterpanel.LegacyFilterPanel.beautyUis     // Catch: java.lang.Throwable -> L47
            java.lang.Object r3 = of(r3, r5)     // Catch: java.lang.Throwable -> L47
            java.util.Map<java.lang.Object, java.lang.ref.WeakReference<java.lang.Object>> r4 = local.jiege.hook.filterpanel.LegacyFilterPanel.effectPanels     // Catch: java.lang.Throwable -> L47
            java.lang.Object r8 = of(r4, r5)     // Catch: java.lang.Throwable -> L47
            if (r6 != 0) goto L22
            return r2
        L22:
            local.jiege.hook.common.Symbols r4 = local.jiege.hook.common.Symbols.get()     // Catch: java.lang.Throwable -> L47
            java.lang.String r7 = "professional"
            java.lang.String r9 = "FeaturePresenter.modeName"
            java.lang.Object r9 = r4.get(r9, r6)     // Catch: java.lang.Throwable -> L47
            boolean r9 = r7.equals(r9)     // Catch: java.lang.Throwable -> L47
            if (r9 == 0) goto L35
            r3 = 0
        L35:
            r7 = r3
            if (r9 == 0) goto L3b
            if (r8 != 0) goto L5c
            goto L3d
        L3b:
            if (r7 != 0) goto L5c
        L3d:
            java.lang.StringBuilder r11 = new java.lang.StringBuilder     // Catch: java.lang.Throwable -> L47
            r11.<init>(r0)     // Catch: java.lang.Throwable -> L47
            if (r9 == 0) goto L4a
            java.lang.String r0 = "effect panel"
            goto L4c
        L47:
            r0 = move-exception
            r11 = r0
            goto L8c
        L4a:
            java.lang.String r0 = "beauty menu"
        L4c:
            r11.append(r0)     // Catch: java.lang.Throwable -> L47
            java.lang.String r0 = " unknown"
            r11.append(r0)     // Catch: java.lang.Throwable -> L47
            java.lang.String r11 = r11.toString()     // Catch: java.lang.Throwable -> L47
            local.jiege.hook.common.Log.i(r1, r11)     // Catch: java.lang.Throwable -> L47
            return r2
        L5c:
            if (r9 != 0) goto L6f
            java.lang.Boolean r0 = java.lang.Boolean.TRUE     // Catch: java.lang.Throwable -> L47
            java.lang.String r3 = "FilterPresenter.palette"
            java.lang.Object[] r10 = new java.lang.Object[r2]     // Catch: java.lang.Throwable -> L47
            java.lang.Object r3 = r4.call(r3, r6, r10)     // Catch: java.lang.Throwable -> L47
            boolean r0 = r0.equals(r3)     // Catch: java.lang.Throwable -> L47
            if (r0 != 0) goto L6f
            return r2
        L6f:
            java.lang.Boolean r0 = java.lang.Boolean.TRUE     // Catch: java.lang.Throwable -> L47
            java.lang.String r3 = "FilterPresenter.styleMode"
            java.lang.Object[] r10 = new java.lang.Object[r2]     // Catch: java.lang.Throwable -> L47
            java.lang.Object r3 = r4.call(r3, r6, r10)     // Catch: java.lang.Throwable -> L47
            boolean r0 = r0.equals(r3)     // Catch: java.lang.Throwable -> L47
            if (r0 != 0) goto L80
            return r2
        L80:
            boolean r0 = show(r4, r5, r6, r7, r8, r9)     // Catch: java.lang.Throwable -> L47
            if (r0 != 0) goto L87
            return r2
        L87:
            r11.performHapticFeedback(r2)     // Catch: java.lang.Throwable -> L47
            r11 = 1
            return r11
        L8c:
            java.lang.String r0 = "long press failed"
            local.jiege.hook.common.Log.e(r1, r0, r11)
            return r2
        */
        throw new UnsupportedOperationException("Method not decompiled: local.jiege.hook.filterpanel.LegacyFilterPanel.open(android.view.View):boolean");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static ArrayList<Object> presetItems(Symbols symbols, Object obj, List<?> list, ClassLoader classLoader) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        ownItems.clear();
        List<Object> list2 = MasterPresets.list();
        int iCustomCount = MasterPresets.customCount(list2);
        int i = 0;
        int iIntValue = ((Integer) symbols.call("FilterPresenter.styleCount", obj, new Object[0])).intValue();
        HashMap map = new HashMap();
        for (Object obj2 : list) {
            map.put((String) XposedHelpers.getObjectField(obj2, "mType"), obj2);
        }
        ArrayList<Object> arrayList = new ArrayList<>(iIntValue);
        while (i < iIntValue && i < list2.size()) {
            Object obj3 = list2.get(i);
            String strFilterType = MasterPresets.filterType(obj3);
            Object objItem = map.get(strFilterType);
            String strName = i < iCustomCount ? MasterPresets.name(obj3) : null;
            if (strName != null) {
                objItem = item(classLoader, strFilterType, strName);
                ownItems.add(objItem);
            } else if (objItem == null) {
                objItem = item(classLoader, strFilterType, strFilterType);
            }
            arrayList.add(objItem);
            i++;
        }
        return arrayList;
    }

    private static Object query(Symbols symbols, Object obj, String str, Object obj2) {
        return symbols.call("FeaturePresenter.feature", obj, CAPTURE_PARAMS, key(symbols, str), obj2);
    }

    private static void restoreControls(Symbols symbols) {
        Integer numValueOf = Integer.valueOf(CONTROLS_ANIMATION);
        HiddenControls hiddenControls2 = hiddenControls;
        hiddenControls = null;
        Object obj = hiddenControls2 != null ? hiddenControls2.control.get() : null;
        if (obj == null) {
            return;
        }
        if (hiddenControls2.mainShutter) {
            symbols.call("ControlUi.mainShutter", obj, 0, numValueOf);
        }
        if (hiddenControls2.thumbnail) {
            symbols.call("ControlUi.thumbnail", obj, 0, numValueOf);
        }
        if (hiddenControls2.switchCamera) {
            symbols.call("ControlUi.switchCamera", obj, 0, numValueOf);
        }
        if (hiddenControls2.sideShutter) {
            symbols.call("ControlUi.sideShutter", obj, 0);
        }
        symbols.call("ControlUi.sideShutterEnabled", obj, Boolean.valueOf(hiddenControls2.sideShutterEnabled));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void selectPage(Symbols symbols, Object obj, int i) {
        Boolean bool = Boolean.TRUE;
        if (bool.equals(symbols.call("FilterPresenter.styleMode", obj, new Object[0]))) {
            Boolean bool2 = Boolean.FALSE;
            if (bool.equals(query(symbols, obj, "isEffectMenuSeekBarOpen", bool2))) {
                Log.i(TAG, "page " + i + " not selected, adjustment bar open");
                return;
            }
            Integer num = (Integer) symbols.call("FilterPresenter.styleCount", obj, new Object[0]);
            num.intValue();
            if (bool.equals(symbols.call("FilterPresenter.clampStyle", obj, Integer.valueOf(i), num))) {
                Object objCall = symbols.call("FilterPresenter.previewRequest", obj, new Object[0]);
                XposedHelpers.callMethod(objCall, "beginPresetSwitch", new Object[0]);
                Integer num2 = (Integer) symbols.get("FilterPresenter.styleTarget", obj);
                num2.intValue();
                symbols.call("FilterPresenter.storeStyle", obj, num2);
                symbols.call("FilterPresenter.applyFilter", obj, symbols.call("FilterPresenter.styleFilter", obj, new Object[0]));
                symbols.call("FilterPresenter.syncPalette", obj, new Object[0]);
                query(symbols, obj, "onFilterScrolling", bool2);
                XposedHelpers.callMethod(objCall, "markPresetSwitchReady", new Object[0]);
                symbols.call("StylePager.sync", symbols.get("FilterPresenter.pager", obj), 1, num2, num, bool);
            }
        }
    }

    private static boolean show(Symbols symbols, Activity activity, Object obj, Object obj2, Object obj3, boolean z) {
        if (open == null) {
            savedPaletteList = ((Boolean) symbols.get("FilterPresenter.paletteList", obj)).booleanValue();
        }
        open = obj;
        openMaster = z;
        Boolean bool = Boolean.TRUE;
        symbols.set("FilterPresenter.paletteList", obj, bool);
        shown = false;
        opening = true;
        try {
            if (z) {
                Object objMenuOf = menuOf(symbols, obj);
                XposedHelpers.callMethod(objMenuOf, "setPaletteMode", new Object[]{Boolean.FALSE});
                symbols.call("FilterMenu.show", objMenuOf, "common", bool);
                symbols.call("FilterPresenter.reloadStrip", obj, new Object[0]);
                symbols.call("EffectPanelUi.expand", obj3, FILTER_MENU);
                expandedPanel = new WeakReference<>(obj3);
                hideControls(symbols, activity);
                shown = true;
            } else {
                symbols.call("BeautyUi.openMenu", obj2, 3, bool, bool, Boolean.FALSE);
            }
            opening = false;
            if (!shown) {
                close(menuOf(symbols, obj));
                Log.i(TAG, "strip not opened, the filter entry refused");
                return false;
            }
            StringBuilder sb = new StringBuilder("strip opened (");
            sb.append(z ? "master presets" : "palette filters");
            sb.append(", ");
            sb.append(symbols.call("FilterPresenter.styleCount", obj, new Object[0]));
            sb.append(" pages, current ");
            sb.append(currentPage(symbols, obj));
            sb.append(")");
            Log.i(TAG, sb.toString());
            return true;
        } catch (Throwable th) {
            opening = false;
            throw th;
        }
    }

    private static void trackByActivity(Class<?> cls, final Map<Object, WeakReference<Object>> map) {
        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() { // from class: local.jiege.hook.filterpanel.LegacyFilterPanel.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                for (Object obj : methodHookParam.args) {
                    Activity activityActivityOf = obj instanceof Context ? LegacyFilterPanel.activityOf((Context) obj) : null;
                    if (activityActivityOf == null && obj != null && !(obj instanceof Context)) {
                        try {
                            activityActivityOf = (Activity) Reflection.findFieldByType(obj, "android.app.Activity");
                        } catch (Throwable unused) {
                        }
                    }
                    if (activityActivityOf != null) {
                        map.put(activityActivityOf, new WeakReference(methodHookParam.thisObject));
                        return;
                    }
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void trackInstances(Symbols symbols) {
        trackByActivity(symbols.cls("FilterPresenter"), presenters);
        trackByActivity(symbols.cls("BeautyUi"), beautyUis);
        trackByActivity(symbols.cls("EffectPanelUi"), effectPanels);
        trackByActivity(symbols.cls("ControlUi"), controlUis);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int trigger() {
        return Settings.getInt("filter_panel_trigger");
    }

    public static /* bridge */ /* synthetic */ int w(Object obj, Symbols symbols) {
        return currentPage(symbols, obj);
    }

    public static /* bridge */ /* synthetic */ Object y(Object obj, Symbols symbols) {
        return menuOf(symbols, obj);
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class SwipeUp implements View.OnTouchListener {
        private float downX;
        private float downY;
        private boolean opened;

        public /* synthetic */ SwipeUp(int i) {
            this();
        }

        private static float towardTop(View view, float f, float f2) {
            Display display = view.getDisplay();
            int rotation = display == null ? 0 : display.getRotation();
            if (rotation == 1) {
                return -f;
            }
            if (rotation != 2) {
                return rotation != 3 ? -f2 : f;
            }
            return f2;
        }

        @Override // android.view.View.OnTouchListener
        public boolean onTouch(View view, MotionEvent motionEvent) {
            int actionMasked = motionEvent.getActionMasked();
            if (actionMasked == 0) {
                this.downX = motionEvent.getRawX();
                this.downY = motionEvent.getRawY();
                this.opened = false;
                return false;
            }
            boolean z = this.opened;
            if (actionMasked != 2) {
                return z;
            }
            if (z) {
                return true;
            }
            if (towardTop(view, motionEvent.getRawX() - this.downX, motionEvent.getRawY() - this.downY) < view.getResources().getDisplayMetrics().density * LegacyFilterPanel.SWIPE_UP_DP || LegacyFilterPanel.trigger() != 1 || !LegacyFilterPanel.open(view)) {
                return false;
            }
            this.opened = true;
            MotionEvent motionEventObtain = MotionEvent.obtain(motionEvent);
            motionEventObtain.setAction(3);
            view.onTouchEvent(motionEventObtain);
            motionEventObtain.recycle();
            return true;
        }

        private SwipeUp() {
        }
    }
}
