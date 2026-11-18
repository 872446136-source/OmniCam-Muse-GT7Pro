package local.jiege.hook.masterlut;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import defpackage.id;
import defpackage.t2;
import java.lang.reflect.Constructor;
import java.util.List;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.FilterScope;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class MasterContrast {
    private static final String GLOBAL = "";
    private static final String KEY_NAME = "key_master_mode_effect_muse_contrast";
    private static final String PACKAGE = "com.oplus.camera";
    private static final String PREF_GROUP = "com.oplus.camera_preferences_";
    private static final String TAG = "MuseContrast";
    private static volatile Object itemKey;
    private static boolean resetting;
    private static final ThreadLocal<Object> building = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> restoring = new ThreadLocal<>();

    private MasterContrast() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void arrange(Symbols symbols, Object obj, Context context) {
        List list = (List) symbols.get("EffectMenuBase.items", obj);
        int iId = id(context, "drawable", "master_contrast_icon");
        int iId2 = id(context, "drawable", "gr_mode_effect_tone_icon");
        int iId3 = id(context, "string", "camera_master_contrast");
        if (iId == 0 || iId2 == 0 || iId3 == 0) {
            t2.j("contrast resources missing");
            return;
        }
        Constructor<?> constructor = symbols.cls("EffectItem").getDeclaredConstructors()[0];
        for (int i = 0; i < list.size(); i++) {
            Object obj2 = list.get(i);
            if (((Integer) symbols.get("EffectItem.icon", obj2)).intValue() == iId) {
                list.set(i, constructor.newInstance(symbols.get("EffectItem.label", obj2), Integer.valueOf(iId2), 0, symbols.get("EffectItem.key", obj2)));
                list.add(i + 1, constructor.newInstance(Integer.valueOf(iId3), Integer.valueOf(iId), 0, key(symbols)));
                return;
            }
        }
        Log.i(TAG, "影调 item not found");
    }

    private static int id(Context context, String str, String str2) {
        return context.getResources().getIdentifier(str2, str, PACKAGE);
    }

    public static void installCamera(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        Log.guard(TAG, "value on entering master", new id(symbols, (byte) 13));
        Log.guard(TAG, "menu construction", new id(symbols, (byte) 14));
        Log.guard(TAG, "item", new id(symbols, (byte) 15));
        Log.guard(TAG, "scale", new id(symbols, (byte) 16));
        Log.guard(TAG, "value", new id(symbols, (byte) 17));
        Log.guard(TAG, "reset", new id(symbols, (byte) 18));
        Log.guard(TAG, "restore", new id(symbols, (byte) 19));
    }

    private static boolean isContrast(Symbols symbols, Object obj) {
        return (obj == null || itemKey == null || !itemKey.equals(symbols.get("EffectItem.key", obj))) ? false : true;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Object key(Symbols symbols) {
        Object obj;
        Object obj2 = itemKey;
        if (obj2 != null) {
            return obj2;
        }
        synchronized (MasterContrast.class) {
            try {
                if (itemKey == null) {
                    itemKey = symbols.method("Prefs.get").getParameterTypes()[0].getConstructor(String.class, String.class, String.class, Class.class).newInstance(symbols.call("Prefs.storage", null, new Object[0]), PREF_GROUP, KEY_NAME, Integer.class);
                }
                obj = itemKey;
            } catch (Throwable th) {
                throw th;
            }
        }
        return obj;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$0(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("Lut3d.load"), new XC_MethodHook() { // from class: local.jiege.hook.masterlut.MasterContrast.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (FilterScope.MASTER_MODE.equals(methodHookParam.args[2])) {
                    MasterLutSlots.set(MasterLutSlots.Param.CONTRAST, MasterContrast.stored(symbols));
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$1(Symbols symbols) {
        XposedBridge.hookAllConstructors(symbols.cls("EffectMenu"), new XC_MethodHook() { // from class: local.jiege.hook.masterlut.MasterContrast.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                MasterContrast.building.remove();
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object[] objArr = methodHookParam.args;
                if ("masterVideo".equals(objArr[objArr.length - 1])) {
                    return;
                }
                MasterContrast.building.set(methodHookParam.thisObject);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$2(final Symbols symbols) {
        XposedBridge.hookAllConstructors(symbols.cls("EffectContainer"), new XC_MethodHook() { // from class: local.jiege.hook.masterlut.MasterContrast.3
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = MasterContrast.building.get();
                if (obj == null) {
                    return;
                }
                MasterContrast.arrange(symbols, obj, (Context) methodHookParam.thisObject.getClass().getMethod("getContext", null).invoke(methodHookParam.thisObject, null));
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$3(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("EffectMenu.bipolar"), new XC_MethodHook() { // from class: local.jiege.hook.masterlut.MasterContrast.4
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                List list = (List) symbols.get("EffectMenuBase.items", methodHookParam.thisObject);
                int iIntValue = ((Integer) methodHookParam.args[0]).intValue();
                if (iIntValue < 0 || iIntValue >= list.size()) {
                    return;
                }
                if (MasterContrast.n(list.get(iIntValue), symbols)) {
                    methodHookParam.setResult(Boolean.TRUE);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$4(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("EffectMenu.dispatch"), new XC_MethodHook() { // from class: local.jiege.hook.masterlut.MasterContrast.5
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (MasterContrast.itemKey == null || !MasterContrast.itemKey.equals(methodHookParam.args[1])) {
                    return;
                }
                methodHookParam.setResult((Object) null);
                if (Boolean.TRUE.equals(MasterContrast.restoring.get()) && !MasterContrast.resetting) {
                    MasterLutSlots.set(MasterLutSlots.Param.CONTRAST, MasterContrast.stored(symbols));
                    return;
                }
                Integer num = (Integer) methodHookParam.args[0];
                int iIntValue = num.intValue();
                Symbols symbols2 = symbols;
                symbols2.call("Prefs.put", null, MasterContrast.key(symbols2), "", Integer.class, num);
                MasterLutSlots.set(MasterLutSlots.Param.CONTRAST, iIntValue);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$5(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("EffectItemView.onClick"), new XC_MethodHook() { // from class: local.jiege.hook.masterlut.MasterContrast.6
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                MasterContrast.resetting = false;
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = symbols.get("EffectItemView.item", methodHookParam.thisObject);
                MasterContrast.resetting = obj != null && symbols.get("EffectItem.key", obj) == null;
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$6(final Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("EffectMenuBase.restore"), new XC_MethodHook() { // from class: local.jiege.hook.masterlut.MasterContrast.7
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                MasterContrast.restoring.remove();
                List list = (List) symbols.get("EffectMenuBase.items", methodHookParam.thisObject);
                for (int i = 0; i < list.size(); i++) {
                    if (MasterContrast.n(list.get(i), symbols)) {
                        symbols.set("EffectItem.value", list.get(i), Integer.valueOf(MasterContrast.stored(symbols)));
                        symbols.call("EffectMenuBase.refreshItem", methodHookParam.thisObject, Integer.valueOf(i));
                    }
                }
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                MasterContrast.restoring.set(Boolean.TRUE);
            }
        });
    }

    public static /* bridge */ /* synthetic */ boolean n(Object obj, Symbols symbols) {
        return isContrast(symbols, obj);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int stored(Symbols symbols) {
        Object objCall = symbols.call("Prefs.get", null, key(symbols), "", Integer.class, 0);
        if (objCall instanceof Integer) {
            return ((Integer) objCall).intValue();
        }
        return 0;
    }
}
