package local.jiege.hook.masterfilter;

import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import defpackage.c3;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import local.jiege.hook.common.DataStore;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Reflection;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.MasterFilterSync;
import local.jiege.hook.filters.PresetSelection;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class MasterPresetListSync {
    private static final String POSITION_KEY = "key_master_mode_current_preset_index";
    private static final String PRESET_KEY = "key_master_mode_current_preset_key";
    private static final String SELECTED_KEY = "pref_jiege_master_selected_preset";
    private static final String SELECTED_SIGNATURE = "pref_jiege_master_selected_signature";
    private static final String TAG = "MasterFilter";
    private static String lastCheck;
    private static Object selectedKey;
    private static Object selectedSignature;
    private static final ThreadLocal<Boolean> ownWrite = new ThreadLocal<>();
    private static final Map<Object, String> entryCache = new IdentityHashMap();

    private MasterPresetListSync() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String caller() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (StackTraceElement stackTraceElement : new Throwable().getStackTrace()) {
            String className = stackTraceElement.getClassName();
            if (!className.startsWith("de.robv.android.xposed") && !className.startsWith("org.lsposed") && !className.startsWith("LSPHooker_") && !className.startsWith("local.jiege") && !className.startsWith("java.lang.reflect") && !className.startsWith("jdk.internal")) {
                sb.append(className);
                sb.append('.');
                sb.append(stackTraceElement.getMethodName());
                sb.append(':');
                sb.append(stackTraceElement.getLineNumber());
                sb.append(' ');
                i++;
                if (i == 6) {
                    break;
                }
            }
        }
        return sb.toString().trim();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void deletePresetsOfRemovedImports(final Symbols symbols, final Object obj, List<Object> list) {
        List<String> listTakeRemovedImports = MasterFilterSync.takeRemovedImports();
        if (listTakeRemovedImports.isEmpty()) {
            return;
        }
        final ArrayList arrayList = new ArrayList();
        for (Object obj2 : list) {
            String strKey = MasterPresets.key(obj2);
            if (strKey != null && strKey.startsWith("custom_") && listTakeRemovedImports.contains(MasterPresets.filterType(obj2))) {
                arrayList.add(strKey);
            }
        }
        if (arrayList.isEmpty()) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: local.jiege.hook.masterfilter.a
            @Override // java.lang.Runnable
            public final void run() {
                MasterPresetListSync.lambda$deletePresetsOfRemovedImports$0(arrayList, symbols, obj);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String entryOf(Object obj) {
        String str = null;
        if (obj == null) {
            return null;
        }
        Map<Object, String> map = entryCache;
        synchronized (map) {
            try {
                String str2 = map.get(obj);
                if (str2 != null) {
                    if (!str2.isEmpty()) {
                        str = str2;
                    }
                    return str;
                }
                String str3 = PresetSelection.STANDARD_KEY;
                try {
                    if (Reflection.hasStringField(obj, POSITION_KEY)) {
                        str3 = "position";
                    } else if (Reflection.hasStringField(obj, PRESET_KEY)) {
                        str3 = "preset key";
                    }
                } catch (Throwable unused) {
                }
                Map<Object, String> map2 = entryCache;
                synchronized (map2) {
                    map2.put(obj, str3);
                }
                if (str3.isEmpty()) {
                    return null;
                }
                return str3;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Object filterTypes(Symbols symbols, Object obj, Class<?> cls) throws IllegalAccessException {
        for (Class<?> superclass = obj.getClass(); superclass != null; superclass = superclass.getSuperclass()) {
            for (Field field : superclass.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    Object obj2 = field.get(obj);
                    if (cls.isInstance(obj2)) {
                        return symbols.call("FilterSource.types", obj2, new Object[0]);
                    }
                }
            }
        }
        return null;
    }

    public static void install(final ClassLoader classLoader) {
        final Symbols symbols = Symbols.get();
        final Class<?> cls = symbols.cls("FilterSource");
        traceStorageWrites(symbols);
        XposedBridge.hookMethod(symbols.method("EffectData.rebuild"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterPresetListSync.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                try {
                    MasterPresetListSync.settleEarly(symbols, classLoader, cls, methodHookParam.thisObject);
                } catch (Throwable th) {
                    Log.e(MasterPresetListSync.TAG, "early master preset selection check failed", th);
                }
            }
        });
        XposedBridge.hookMethod(symbols.method("EffectData.current"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterPresetListSync.2
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = methodHookParam.thisObject;
                MasterPresets.track(obj);
                LinkedList linkedList = (LinkedList) symbols.get("EffectData.presets", obj);
                List list = (List) MasterPresetListSync.filterTypes(symbols, obj, cls);
                if (linkedList == null || list == null || list.isEmpty()) {
                    return;
                }
                int iCustomCount = MasterPresets.customCount(linkedList);
                int size = list.size() + iCustomCount;
                if (linkedList.size() < size) {
                    while (linkedList.size() > iCustomCount) {
                        linkedList.removeLast();
                    }
                    symbols.call("EffectData.rebuild", obj, list, linkedList);
                    Log.i(MasterPresetListSync.TAG, "master preset list rebuilt: " + linkedList.size() + " (was short of " + size + ")");
                }
                try {
                    MasterPresetListSync.deletePresetsOfRemovedImports(symbols, obj, linkedList);
                } catch (Throwable th) {
                    Log.e(MasterPresetListSync.TAG, "preset cleanup failed", th);
                }
                try {
                    MasterPresetListSync.keepSelection(symbols, classLoader, linkedList, iCustomCount, list, false);
                } catch (Throwable th2) {
                    Log.e(MasterPresetListSync.TAG, "master preset selection check failed", th2);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void keepSelection(Symbols symbols, ClassLoader classLoader, List<Object> list, int i, List<String> list2, boolean z) {
        String str;
        String str2;
        String str3;
        int i2;
        int i3;
        PresetSelection.Result resultResolve;
        Object objKey = DataStore.key(symbols, "Keys.presetIndex", POSITION_KEY);
        if (selectedKey == null) {
            selectedKey = symbols.call("DataKeys.prefs", null, String.class, SELECTED_KEY);
            selectedSignature = symbols.call("DataKeys.prefs", null, String.class, SELECTED_SIGNATURE);
        }
        ArrayList arrayList = new ArrayList(list.size());
        Iterator<Object> it = list.iterator();
        while (true) {
            boolean zHasNext = it.hasNext();
            str = PresetSelection.STANDARD_KEY;
            if (!zHasNext) {
                break;
            }
            String strKey = MasterPresets.key(it.next());
            if (strKey != null) {
                str = strKey;
            }
            arrayList.add(str);
        }
        int iIntValue = ((Integer) DataStore.get(symbols, classLoader, objKey, 0)).intValue();
        String str4 = (String) DataStore.get(symbols, classLoader, selectedKey, "\u0000");
        String str5 = (String) DataStore.get(symbols, classLoader, selectedSignature, "\u0000");
        if ("\u0000".equals(str4)) {
            str4 = null;
        }
        if ("\u0000".equals(str5)) {
            str5 = null;
        }
        if (z && (str4 == null || str5 == null)) {
            return;
        }
        String strSignature = PresetSelection.signature(list2);
        if (z) {
            String str6 = str4;
            str3 = strSignature;
            i2 = iIntValue;
            i3 = i;
            resultResolve = PresetSelection.resolve(i2, arrayList, i3, str3, str6, str5);
            str2 = str6;
        } else {
            str2 = str4;
            str3 = strSignature;
            i2 = iIntValue;
            i3 = i;
            resultResolve = PresetSelection.resolve(i2, arrayList, i3, str3, null, null);
        }
        String str7 = i2 + "|" + str2 + "|" + str5 + "|" + str3 + "|" + arrayList.size() + "|" + i3;
        if (!str7.equals(lastCheck)) {
            lastCheck = str7;
            StringBuilder sb = new StringBuilder("selection check");
            sb.append(z ? " (early)" : PresetSelection.STANDARD_KEY);
            sb.append(": stored=");
            sb.append(i2);
            sb.append(" record=");
            c3.s(sb, str2, "@", str5, " list=");
            sb.append(str3);
            sb.append(" presets=");
            sb.append(arrayList.size());
            sb.append(" custom=");
            sb.append(i3);
            sb.append(" keys=");
            sb.append(arrayList.subList(0, Math.min(5, arrayList.size())));
            sb.append(" -> ");
            sb.append(resultResolve.index);
            Log.i(TAG, sb.toString());
        }
        if (resultResolve.relocated) {
            write(symbols, classLoader, objKey, Integer.valueOf(resultResolve.index));
            StringBuilder sb2 = new StringBuilder("master preset selection ");
            sb2.append(i2);
            sb2.append(" -> ");
            sb2.append(resultResolve.index);
            sb2.append(" (");
            sb2.append(str2);
            sb2.append(")");
            if (z) {
                str = " early";
            }
            sb2.append(str);
            Log.i(TAG, sb2.toString());
        }
        if (z) {
            return;
        }
        if (!resultResolve.key.equals(str2)) {
            write(symbols, classLoader, selectedKey, resultResolve.key);
        }
        if (resultResolve.signature.equals(str5)) {
            return;
        }
        write(symbols, classLoader, selectedSignature, resultResolve.signature);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$deletePresetsOfRemovedImports$0(List list, Symbols symbols, Object obj) {
        Iterator it = list.iterator();
        while (it.hasNext()) {
            String str = (String) it.next();
            try {
                symbols.call("EffectData.delete", obj, str);
                Log.i(TAG, "deleted preset " + str + " of a deleted imported filter");
            } catch (Throwable th) {
                Log.e(TAG, "preset delete failed " + str, th);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void settleEarly(Symbols symbols, ClassLoader classLoader, Class<?> cls, Object obj) {
        MasterPresets.track(obj);
        LinkedList linkedList = (LinkedList) symbols.get("EffectData.presets", obj);
        List list = (List) filterTypes(symbols, obj, cls);
        if (linkedList == null || list == null || list.isEmpty()) {
            return;
        }
        int iCustomCount = MasterPresets.customCount(linkedList);
        if (linkedList.size() < list.size() + iCustomCount) {
            return;
        }
        keepSelection(symbols, classLoader, linkedList, iCustomCount, list, true);
    }

    private static void traceStorageWrites(Symbols symbols) {
        try {
            XposedBridge.hookMethod(symbols.method("DataManager.put"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterPresetListSync.3
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    String str;
                    String strEntryOf = MasterPresetListSync.entryOf(methodHookParam.args[0]);
                    if (strEntryOf == null) {
                        return;
                    }
                    boolean z = MasterPresetListSync.ownWrite.get() != null;
                    StringBuilder sb = new StringBuilder(z ? "module" : "camera");
                    sb.append(" writes ");
                    sb.append(strEntryOf);
                    sb.append("=");
                    sb.append(methodHookParam.args[1]);
                    if (z) {
                        str = PresetSelection.STANDARD_KEY;
                    } else {
                        str = " from " + Thread.currentThread().getName() + " " + MasterPresetListSync.caller();
                    }
                    sb.append(str);
                    Log.i(MasterPresetListSync.TAG, sb.toString());
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "storage write trace unavailable", th);
        }
    }

    private static void write(Symbols symbols, ClassLoader classLoader, Object obj, Object obj2) {
        ownWrite.set(Boolean.TRUE);
        try {
            DataStore.put(symbols, classLoader, obj, obj2);
        } finally {
            ownWrite.remove();
        }
    }
}
