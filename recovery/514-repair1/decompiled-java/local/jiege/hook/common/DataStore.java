package local.jiege.hook.common;

import de.robv.android.xposed.XposedHelpers;
import defpackage.c3;
import defpackage.t2;
import java.util.Iterator;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class DataStore {
    private DataStore() {
    }

    public static Object get(Symbols symbols, ClassLoader classLoader, Object obj, Object obj2) {
        return symbols.call("DataManager.getOr", manager(classLoader), obj, obj2);
    }

    public static Object key(Symbols symbols, String str, String str2) {
        Iterator it = symbols.all(str).iterator();
        while (it.hasNext()) {
            try {
                return Reflection.findDataKey((Class) it.next(), str2);
            } catch (IllegalStateException unused) {
            }
        }
        t2.j(c3.u("DataKey not found: ", str2));
        return null;
    }

    public static Object manager(ClassLoader classLoader) {
        return XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.oplus.camera.data.DataManager", classLoader), "getInstance", new Object[0]);
    }

    public static void put(Symbols symbols, ClassLoader classLoader, Object obj, Object obj2) {
        symbols.call("DataManager.put", manager(classLoader), obj, obj2);
    }
}
