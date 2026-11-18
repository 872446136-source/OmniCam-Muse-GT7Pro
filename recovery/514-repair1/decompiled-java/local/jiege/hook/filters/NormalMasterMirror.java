package local.jiege.hook.filters;

import de.robv.android.xposed.XposedHelpers;
import defpackage.c3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class NormalMasterMirror {
    private static final List<String> GROUPS = Arrays.asList("sFilterGroup", "sPortraitFilterGroup");
    private static final Map<String, String> NORMAL_TYPE;

    static {
        HashMap map = new HashMap();
        NORMAL_TYPE = map;
        map.put("portra400_master.bin", "portra400_normal.bin");
        map.put("positive_master.bin", "positive_normal.bin");
        map.put("fuji_cc_master.bin", "fuji_cc.bin");
        map.put("fuji-nc_master.bin", "fuji-nc.bin");
        map.put("fuji-proNegHi_master.bin", "fuji-proNegHi.bin");
        map.put("800t_master.bin", "800t.bin");
        map.put("blackandwhite_HC_master.bin", "blackandwhite_HC_normal.bin");
    }

    private NormalMasterMirror() {
    }

    public static String apply(Class<?> cls) {
        Object staticObjectField = XposedHelpers.getStaticObjectField(cls, "sProFilterGroup");
        if (staticObjectField == null) {
            return PresetSelection.STANDARD_KEY;
        }
        List<String> listOrder = order(new ArrayList((List) XposedHelpers.getObjectField(staticObjectField, "mBackTypeList")), FilterGroupInjector.masterHiddenCount());
        Iterator<String> it = GROUPS.iterator();
        int i = 0;
        while (it.hasNext()) {
            Object staticObjectField2 = XposedHelpers.getStaticObjectField(cls, it.next());
            if (staticObjectField2 != null) {
                MasterLayout.reorder((List) XposedHelpers.getObjectField(staticObjectField2, "mBackTypeList"), (List) XposedHelpers.getObjectField(staticObjectField2, "mBackNameIdList"), listOrder);
                MasterLayout.reorder((List) XposedHelpers.getObjectField(staticObjectField2, "mFrontTypeList"), (List) XposedHelpers.getObjectField(staticObjectField2, "mFrontNameIdList"), listOrder);
                i++;
            }
        }
        return c3.g(", ", i, " groups in master order");
    }

    public static List<FilterEntry> mirrored(List<FilterEntry> list, List<FilterEntry> list2, MasterLayout.Saved saved) {
        ArrayList arrayList = new ArrayList();
        for (FilterEntry filterEntry : list) {
            if (filterEntry.source != FilterEntry.Source.X10_PORT && MasterLayout.isVisible(filterEntry.id, filterEntry, saved)) {
                Iterator<FilterEntry> it = list2.iterator();
                boolean zEquals = false;
                while (it.hasNext()) {
                    zEquals |= it.next().id.equals(filterEntry.id);
                }
                if (!zEquals) {
                    arrayList.add(filterEntry);
                }
            }
        }
        return arrayList;
    }

    public static List<String> order(List<String> list, int i) {
        ArrayList arrayList = new ArrayList();
        for (String str : list.subList(0, list.size() - i)) {
            String str2 = NORMAL_TYPE.get(str);
            if (str2 != null) {
                str = str2;
            }
            arrayList.add(str);
        }
        return arrayList;
    }

    public static boolean takes(String str, FilterEntry filterEntry) {
        return filterEntry.source == FilterEntry.Source.X10_PORT || GROUPS.contains(str);
    }
}
