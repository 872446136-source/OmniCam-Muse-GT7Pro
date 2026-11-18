package local.jiege.hook.filters;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class FilterEntry {
    public final String captureIdFrom;
    public final Category category;
    public final boolean enabled;
    public final String id;
    public final String label;
    public final String nameResource;
    public final Placement placement;
    public final Source source;
    public final String title;

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public enum Category {
        PRESET("预置滤镜", null),
        CLASSIC("旧版经典", "旧版经典"),
        FUJI("富士滤镜", "富士滤镜"),
        LEICA("徕卡风格", "徕卡风格"),
        RICOH("理光滤镜", "理光滤镜"),
        FILM("胶片模拟", "胶片模拟"),
        IMPORTED("导入滤镜", "导入滤镜");

        public final String displayName;
        public final String label;

        Category(String str, String str2) {
            this.displayName = str;
            this.label = str2;
        }
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public enum Placement {
        HEAD,
        TAIL
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public enum Source {
        STOCK_RESTORED,
        MODULE_LUT,
        X10_PORT,
        IMPORTED
    }

    public FilterEntry(String str, String str2, String str3, Category category, Source source, Placement placement, String str4, boolean z) {
        this.id = str;
        this.nameResource = str2;
        this.title = str3;
        this.category = category;
        this.label = category.label;
        this.source = source;
        this.placement = placement;
        this.captureIdFrom = str4;
        this.enabled = z;
    }
}
