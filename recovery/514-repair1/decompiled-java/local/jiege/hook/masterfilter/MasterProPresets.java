package local.jiege.hook.masterfilter;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.SymbolSearch;
import local.jiege.hook.filters.PresetSelection;
import local.jiege.hook.filters.ProParamsRecord;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class MasterProPresets {
    private static final String RECORD_PREFIX = "pref_jiege_pro_";
    private static final String TAG = "MasterPro";
    private static boolean appliedByPreset;
    private static volatile Object gearListener;
    private static ClassLoader loader;
    private static final String[][] PARAMS = {new String[]{"w0", ProParamsRecord.WHITE_BALANCE}, new String[]{"x0", ProParamsRecord.COLOR_TONE}, new String[]{"y0", ProParamsRecord.ISO}, new String[]{"z0", ProParamsRecord.EXPOSURE_COMPENSATION}, new String[]{"A0", ProParamsRecord.EXPOSURE_TIME}, new String[]{"B0", ProParamsRecord.FOCUS_MODE}};
    private static final Map<String, Object> recordKeys = new HashMap();

    private MasterProPresets() {
    }

    private static void clearParams() {
        for (String[] strArr : PARAMS) {
            remove(param(strArr[0]));
        }
    }

    private static void clickGear() {
        ((View.OnClickListener) gearListener).onClick(null);
    }

    public static /* bridge */ /* synthetic */ Object d(Class cls, Object obj) {
        return presenterOf(obj, cls);
    }

    private static Object data() {
        return XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.oplus.camera.data.DataManager", loader), "getInstance", new Object[0]);
    }

    private static Object get(Object obj, Object obj2) {
        return XposedHelpers.callMethod(data(), "b", new Object[]{obj, obj2});
    }

    private static boolean inProGear(Object obj) {
        return XposedHelpers.getBooleanField(XposedHelpers.getObjectField(obj, "P"), "k");
    }

    public static void install(ClassLoader classLoader) {
        loader = classLoader;
        try {
            final Class clsFindClass = XposedHelpers.findClass("df.r", classLoader);
            XposedBridge.hookAllConstructors(XposedHelpers.findClass("df.b", classLoader), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterProPresets.1
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object obj = methodHookParam.thisObject;
                    if (!(obj instanceof View.OnClickListener) || MasterProPresets.d(clsFindClass, obj) == null) {
                        return;
                    }
                    if (MasterProPresets.gearListener == null) {
                        Log.i(MasterProPresets.TAG, "gear button handler captured");
                    }
                    MasterProPresets.gearListener = methodHookParam.thisObject;
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "gear button hook failed; presets cannot switch the gear", th);
        }
        XposedHelpers.findAndHookMethod("af.b", classLoader, "b", new Object[]{XposedHelpers.findClass("l9.a", classLoader), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterProPresets.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                String str;
                try {
                    Object result = methodHookParam.getResult();
                    if (result == null || XposedHelpers.getIntField(result, "a") != 0 || (str = (String) XposedHelpers.getObjectField(methodHookParam.args[0], "a")) == null || !str.startsWith("custom_")) {
                        return;
                    }
                    MasterProPresets.saveFor(str);
                } catch (Throwable th2) {
                    Log.e(MasterProPresets.TAG, "saving Pro parameters failed", th2);
                }
            }
        }});
        XposedHelpers.findAndHookMethod("af.b", classLoader, "e", new Object[]{String.class, new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterProPresets.3
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    String str = (String) methodHookParam.args[0];
                    if (str == null || !str.startsWith("custom_")) {
                        return;
                    }
                    MasterProPresets.put(MasterProPresets.record(str), PresetSelection.STANDARD_KEY);
                } catch (Throwable th2) {
                    Log.e(MasterProPresets.TAG, "clearing Pro parameters failed", th2);
                }
            }
        }});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$onSelected$0(String str) {
        try {
            select(str);
        } catch (Throwable th) {
            Log.e(TAG, "applying preset gear failed for " + str, th);
        }
    }

    public static void onSelected(final String str) {
        if (loader == null || str == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: local.jiege.hook.masterfilter.b
            @Override // java.lang.Runnable
            public final void run() {
                MasterProPresets.lambda$onSelected$0(str);
            }
        });
    }

    private static Object param(String str) {
        return XposedHelpers.getStaticObjectField(XposedHelpers.findClass("m9.f", loader), str);
    }

    private static Object presenter() {
        Object obj = gearListener;
        if (obj == null) {
            return null;
        }
        return presenterOf(obj, XposedHelpers.findClass("df.r", loader));
    }

    private static Object presenterOf(Object obj, Class<?> cls) {
        for (Field field : obj.getClass().getDeclaredFields()) {
            if (cls.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return field.get(obj);
                } catch (IllegalAccessException unused) {
                    break;
                }
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void put(Object obj, Object obj2) {
        XposedHelpers.callMethod(data(), "g", new Object[]{obj, obj2});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized Object record(String str) {
        Object objCallStaticMethod;
        Map<String, Object> map = recordKeys;
        objCallStaticMethod = map.get(str);
        if (objCallStaticMethod == null) {
            objCallStaticMethod = XposedHelpers.callStaticMethod(XposedHelpers.findClass("androidx.preference.e", loader), "H0", new Object[]{String.class, RECORD_PREFIX + str});
            map.put(str, objCallStaticMethod);
        }
        return objCallStaticMethod;
    }

    private static void remove(Object obj) {
        XposedHelpers.callMethod(data(), SymbolSearch.FIELD, new Object[]{obj});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void saveFor(String str) {
        Object objPresenter = presenter();
        boolean zInProGear = objPresenter != null ? inProGear(objPresenter) : Boolean.FALSE.equals(get(param("J0"), Boolean.TRUE));
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        if (zInProGear) {
            for (String[] strArr : PARAMS) {
                Object obj = get(param(strArr[0]), PresetSelection.STANDARD_KEY);
                if (obj != null && !PresetSelection.STANDARD_KEY.equals(obj)) {
                    linkedHashMap.put(strArr[1], obj.toString());
                }
            }
        }
        put(record(str), ProParamsRecord.write(linkedHashMap));
        StringBuilder sb = new StringBuilder("preset ");
        sb.append(str);
        sb.append(zInProGear ? " saved with Pro parameters " + linkedHashMap : " saved in the auto gear");
        Log.i(TAG, sb.toString());
    }

    private static void select(String str) {
        Map linkedHashMap = str.startsWith("custom_") ? ProParamsRecord.parse((String) get(record(str), PresetSelection.STANDARD_KEY)) : new LinkedHashMap();
        Object objPresenter = presenter();
        if (linkedHashMap.isEmpty()) {
            if (appliedByPreset) {
                appliedByPreset = false;
                clearParams();
                if (objPresenter != null && inProGear(objPresenter)) {
                    clickGear();
                }
                Log.i(TAG, str.concat(": left a Pro preset, parameters cleared, auto gear on"));
                return;
            }
            return;
        }
        writeParams(linkedHashMap);
        appliedByPreset = true;
        if (objPresenter == null) {
            Log.i(TAG, str.concat(": parameters written; gear button not captured yet"));
            return;
        }
        if (inProGear(objPresenter)) {
            XposedHelpers.callMethod(objPresenter, "C2", new Object[]{Boolean.TRUE});
            Log.i(TAG, str + ": Pro parameters reloaded " + linkedHashMap);
            return;
        }
        clickGear();
        Log.i(TAG, str + ": Pro gear on with " + linkedHashMap);
    }

    private static void writeParams(Map<String, String> map) {
        for (String[] strArr : PARAMS) {
            String str = map.get(strArr[1]);
            if (str != null) {
                put(param(strArr[0]), str);
            } else {
                remove(param(strArr[0]));
            }
        }
    }
}
