package local.jiege.hook.filters;

import java.util.Iterator;
import java.util.List;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class PresetSelection {
    public static final String STANDARD_KEY = "";

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Result {
        public final int index;
        public final String key;
        public final boolean relocated;
        public final String signature;

        public Result(int i, String str, String str2, boolean z) {
            this.index = i;
            this.key = str;
            this.signature = str2;
            this.relocated = z;
        }
    }

    private PresetSelection() {
    }

    public static Result resolve(int i, List<String> list, int i2, String str, String str2, String str3) {
        int iStandardIndex;
        if (list.isEmpty()) {
            return new Result(i, str2, str3, false);
        }
        if (str2 == null || str3 == null || str3.equals(str)) {
            iStandardIndex = (i < 0 || i >= list.size()) ? standardIndex(list, i2) : i;
        } else {
            iStandardIndex = list.indexOf(str2);
            if (iStandardIndex < 0) {
                iStandardIndex = standardIndex(list, i2);
            }
        }
        return new Result(iStandardIndex, list.get(iStandardIndex), str, iStandardIndex != i);
    }

    public static String signature(List<String> list) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
            sb.append('\n');
        }
        return Integer.toHexString(sb.toString().hashCode()) + ":" + list.size();
    }

    private static int standardIndex(List<String> list, int i) {
        int iIndexOf = list.indexOf(STANDARD_KEY);
        return iIndexOf >= 0 ? iIndexOf : Math.max(0, Math.min(i, list.size() - 1));
    }
}
