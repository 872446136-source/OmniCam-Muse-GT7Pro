package local.jiege.hook.masterfilter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class MasterPresets {
    private static volatile WeakReference<Object> manager = new WeakReference<>(null);

    private MasterPresets() {
    }

    public static int customCount(List<Object> list) {
        String strKey;
        Iterator<Object> it = list.iterator();
        int i = 0;
        while (it.hasNext() && (strKey = key(it.next())) != null && strKey.startsWith("custom_")) {
            i++;
        }
        return i;
    }

    public static String filterType(Object obj) {
        return value(text(obj, "', mFilterType=", ", mFilterNameResEntry="));
    }

    public static String key(Object obj) {
        return value(text(obj, "{mPresetKey=", ", mPresetName='"));
    }

    public static List<Object> list() {
        Object obj = manager.get();
        if (obj == null) {
            return new ArrayList();
        }
        try {
            List list = (List) Symbols.get().get("EffectData.presets", obj);
            return list == null ? new ArrayList() : new ArrayList(list);
        } catch (Throwable unused) {
            return new ArrayList();
        }
    }

    public static String name(Object obj) {
        return value(text(obj, ", mPresetName='", "', mFilterType="));
    }

    private static String text(Object obj, String str, String str2) {
        int length;
        int iIndexOf;
        String strValueOf = String.valueOf(obj);
        int iIndexOf2 = strValueOf.indexOf(str);
        if (iIndexOf2 >= 0 && (iIndexOf = strValueOf.indexOf(str2, (length = str.length() + iIndexOf2))) >= 0) {
            return strValueOf.substring(length, iIndexOf);
        }
        return null;
    }

    public static void track(Object obj) {
        if (obj == null || manager.get() == obj) {
            return;
        }
        manager = new WeakReference<>(obj);
    }

    private static String value(String str) {
        if (str == null || "null".equals(str)) {
            return null;
        }
        return str;
    }
}
