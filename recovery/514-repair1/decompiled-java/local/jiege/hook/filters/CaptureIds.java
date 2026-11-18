package local.jiege.hook.filters;

import de.robv.android.xposed.XposedHelpers;
import java.util.Map;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class CaptureIds {
    private static final String TAG = "CaptureIds";

    private CaptureIds() {
    }

    public static synchronized void apply(ClassLoader classLoader) {
        try {
            Map map = (Map) XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.oplus.camera.filter.FilterHelper", classLoader), "sFilterPathConvertMap");
            int i = 0;
            for (FilterScope filterScope : FilterScope.values()) {
                for (FilterEntry filterEntry : FilterCatalog.get(filterScope).entries()) {
                    String str = filterEntry.captureIdFrom;
                    if (str != null) {
                        Integer num = (Integer) map.get(str);
                        if (num == null) {
                            Log.i(TAG, filterEntry.captureIdFrom + " capture ID is absent, " + filterEntry.id + " not mapped");
                        } else {
                            Integer num2 = (Integer) map.get(filterEntry.id);
                            if (num2 == null || num2.equals(num)) {
                                map.put(filterEntry.id, num);
                                i++;
                            } else {
                                Log.i(TAG, filterEntry.id + " already maps to capture ID " + num2 + ", kept");
                            }
                        }
                    } else if (filterEntry.source != FilterEntry.Source.X10_PORT && map.get(filterEntry.id) == null) {
                        Log.i(TAG, filterEntry.id + " has no capture ID");
                    }
                }
            }
            Log.i(TAG, "capture IDs mapped: " + i);
        } catch (Throwable th) {
            Log.e(TAG, "capture ID map failed", th);
        }
    }
}
