package local.jiege.hook.filters;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public enum FilterScope {
    NORMAL("sFilterGroup", "sPortraitFilterGroup", "sStickerFilterGroup", "sNightFilterGroup"),
    MASTER("sProFilterGroup");

    public static final String MASTER_MODE = "professional";
    final String[] groups;

    FilterScope(String... strArr) {
        this.groups = strArr;
    }

    public static FilterScope ofMode(Object obj) {
        return MASTER_MODE.equals(obj) ? MASTER : NORMAL;
    }
}
