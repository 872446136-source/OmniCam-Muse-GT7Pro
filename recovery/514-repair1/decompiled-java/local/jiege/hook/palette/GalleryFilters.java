package local.jiege.hook.palette;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import defpackage.c3;
import java.io.File;
import java.util.List;
import local.jiege.hook.common.Log;
import local.jiege.hook.filters.FilterCatalog;
import local.jiege.hook.filters.FilterEntry;
import local.jiege.hook.filters.FilterScope;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class GalleryFilters {
    private static final String[] LUT_DIRS = {"/odm/etc/camera/meishe_lut/", "/odm/etc/camera/filters_lut/"};
    private static final String MANAGER = "com.oplus.ocs.camera.ipusdk.processunit.filter.list.FilterGroupManager";
    private static final String TAG = "GalleryFilters";

    private GalleryFilters() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int append(Object obj, FilterScope filterScope) {
        int i = 0;
        try {
            List list = (List) XposedHelpers.getObjectField(obj, "mBackTypeList");
            for (FilterEntry filterEntry : FilterCatalog.get(filterScope).entries()) {
                if (filterEntry.enabled && filterEntry.source != FilterEntry.Source.IMPORTED && !list.contains(filterEntry.id) && lutExists(filterEntry.id)) {
                    String str = filterEntry.title;
                    if (str == null) {
                        str = "R.string." + filterEntry.nameResource;
                    }
                    XposedHelpers.callMethod(obj, "addBack", new Object[]{filterEntry.id, str});
                    i++;
                }
            }
            return i;
        } catch (Throwable th) {
            Log.e(TAG, "gallery " + filterScope + " filters failed", th);
            return i;
        }
    }

    public static void install(ClassLoader classLoader) {
        try {
            final Class clsFindClass = XposedHelpers.findClass(MANAGER, classLoader);
            Class cls = Boolean.TYPE;
            XposedHelpers.findAndHookMethod(clsFindClass, "init", new Object[]{Context.class, cls, cls, new XC_MethodHook() { // from class: local.jiege.hook.palette.GalleryFilters.1
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Log.i(GalleryFilters.TAG, "gallery filters added: normal " + GalleryFilters.append(XposedHelpers.getStaticObjectField(clsFindClass, "sFilterGroup"), FilterScope.NORMAL) + ", master " + GalleryFilters.append(XposedHelpers.getStaticObjectField(clsFindClass, "sProFilterGroup"), FilterScope.MASTER));
                }
            }});
        } catch (Throwable th) {
            Log.e(TAG, "gallery filter hook failed", th);
        }
    }

    private static boolean lutExists(String str) {
        for (String str2 : LUT_DIRS) {
            if (new File(c3.j(str2, str)).isFile()) {
                return true;
            }
        }
        return false;
    }
}
