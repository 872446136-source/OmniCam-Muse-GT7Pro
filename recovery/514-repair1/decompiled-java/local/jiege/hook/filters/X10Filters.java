package local.jiege.hook.filters;

import java.util.List;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class X10Filters {
    public static final String HU_PO = "hu_po.bin";
    public static final String QING_TOU = "qing_tou.bin";

    private X10Filters() {
    }

    public static void add(List<FilterEntry> list) {
        list.add(entry(QING_TOU, "camera_filter_oplus_qing_tou"));
        list.add(entry(HU_PO, "camera_filter_oplus_hu_po"));
    }

    private static FilterEntry entry(String str, String str2) {
        return new FilterEntry(str, str2, null, FilterEntry.Category.PRESET, FilterEntry.Source.X10_PORT, FilterEntry.Placement.HEAD, null, true);
    }
}
