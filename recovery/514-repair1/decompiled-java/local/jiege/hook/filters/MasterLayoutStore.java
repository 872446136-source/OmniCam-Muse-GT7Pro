package local.jiege.hook.filters;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class MasterLayoutStore {
    private static volatile MasterLayout.Saved saved;

    private MasterLayoutStore() {
    }

    public static MasterLayout.Saved load() {
        return saved;
    }

    public static void set(MasterLayout.Saved saved2) {
        saved = saved2;
    }
}
