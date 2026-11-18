package local.jiege.hook.filters;

import defpackage.t2;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class MasterLayout {
    public static final String STANDARD = "default";

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Result {
        public final int hiddenCount;
        public final List<String> order;

        public Result(List<String> list, int i) {
            this.order = Collections.unmodifiableList(list);
            this.hiddenCount = i;
        }
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Saved {
        public final List<String> order;
        public final Map<String, Boolean> visible;

        public Saved(List<String> list, Map<String, Boolean> map) {
            this.order = Collections.unmodifiableList(new ArrayList(list));
            this.visible = map;
        }
    }

    private MasterLayout() {
    }

    public static Result compute(List<String> list, FilterCatalog filterCatalog, Saved saved) {
        LinkedHashSet linkedHashSet = new LinkedHashSet(list);
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        if (linkedHashSet.remove(STANDARD)) {
            arrayList.add(STANDARD);
        }
        ArrayList<String> arrayList3 = new ArrayList();
        if (saved != null) {
            for (String str : saved.order) {
                if (linkedHashSet.contains(str) && !arrayList3.contains(str)) {
                    arrayList3.add(str);
                }
            }
        }
        String str2 = null;
        for (String str3 : list) {
            if (!STANDARD.equals(str3)) {
                if (!arrayList3.contains(str3)) {
                    arrayList3.add(str2 == null ? 0 : arrayList3.indexOf(str2) + 1, str3);
                }
                str2 = str3;
            }
        }
        for (String str4 : arrayList3) {
            (isVisible(str4, filterCatalog, saved) ? arrayList : arrayList2).add(str4);
        }
        ArrayList arrayList4 = new ArrayList(arrayList);
        arrayList4.addAll(arrayList2);
        return new Result(arrayList4, arrayList2.size());
    }

    public static boolean isVisible(String str, FilterEntry filterEntry, Saved saved) {
        Boolean bool;
        if (STANDARD.equals(str)) {
            return true;
        }
        if (saved == null || (bool = saved.visible.get(str)) == null) {
            return filterEntry == null || filterEntry.enabled;
        }
        return bool.booleanValue();
    }

    public static <T> void reorder(List<String> list, List<T> list2, List<String> list3) {
        if (list2 != null && list2.size() != list.size()) {
            t2.j("Parallel lists differ in size");
            return;
        }
        ArrayList arrayList = new ArrayList(list.size());
        ArrayList arrayList2 = new ArrayList(list.size());
        boolean[] zArr = new boolean[list.size()];
        Iterator<String> it = list3.iterator();
        while (it.hasNext()) {
            int iIndexOf = list.indexOf(it.next());
            if (iIndexOf >= 0 && !zArr[iIndexOf]) {
                zArr[iIndexOf] = true;
                arrayList.add(list.get(iIndexOf));
                if (list2 != null) {
                    arrayList2.add(list2.get(iIndexOf));
                }
            }
        }
        for (int i = 0; i < list.size(); i++) {
            if (!zArr[i]) {
                arrayList.add(list.get(i));
                if (list2 != null) {
                    arrayList2.add(list2.get(i));
                }
            }
        }
        list.clear();
        list.addAll(arrayList);
        if (list2 != null) {
            list2.clear();
            list2.addAll(arrayList2);
        }
    }

    public static boolean isVisible(String str, FilterCatalog filterCatalog, Saved saved) {
        return isVisible(str, filterCatalog.find(str), saved);
    }
}
