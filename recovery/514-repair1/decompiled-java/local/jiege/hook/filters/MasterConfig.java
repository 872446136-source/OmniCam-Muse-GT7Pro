package local.jiege.hook.filters;

import defpackage.c3;
import defpackage.t2;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class MasterConfig {
    public static final String IMPORT_PREFIX = "JG_IMPORT_";
    public static final String LUT_SUFFIX = ".cube.rgb.bin";
    private static final String MAGIC = "jiege-master 1";
    public final List<Imported> imported;
    public final List<String> order;
    public final Map<String, Boolean> visible;

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Imported {
        public final String id;
        public final long size;
        public final String title;

        public Imported(String str, String str2, long j) {
            if (!MasterConfig.isImportId(str)) {
                t2.m(c3.u("Not an import ID: ", str));
                throw null;
            }
            this.id = str;
            this.title = MasterConfig.clean(str2);
            this.size = j;
        }
    }

    public MasterConfig(List<String> list, Map<String, Boolean> map, List<Imported> list2) {
        this.order = Collections.unmodifiableList(new ArrayList(list));
        this.visible = Collections.unmodifiableMap(new LinkedHashMap(map));
        this.imported = Collections.unmodifiableList(new ArrayList(list2));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String clean(String str) {
        return str == null ? PresetSelection.STANDARD_KEY : str.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static MasterConfig empty() {
        List list = Collections.EMPTY_LIST;
        return new MasterConfig(list, Collections.EMPTY_MAP, list);
    }

    public static boolean isImportId(String str) {
        return str != null && str.startsWith(IMPORT_PREFIX) && str.endsWith(LUT_SUFFIX) && str.indexOf(47) < 0 && str.indexOf(92) < 0 && !str.contains("..") && str.length() > 23;
    }

    public static MasterConfig parse(String str) {
        if (str == null) {
            return empty();
        }
        String[] strArrSplit = str.split("\n", -1);
        if (strArrSplit.length == 0 || !MAGIC.equals(strArrSplit[0].trim())) {
            return empty();
        }
        ArrayList arrayList = new ArrayList();
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        ArrayList arrayList2 = new ArrayList();
        for (int i = 1; i < strArrSplit.length; i++) {
            String[] strArrSplit2 = strArrSplit[i].replace("\r", PresetSelection.STANDARD_KEY).split("\t", -1);
            try {
                String str2 = strArrSplit2[0];
                int iHashCode = str2.hashCode();
                if (iHashCode != -1184795739) {
                    if (iHashCode != 106006350) {
                        if (iHashCode == 466743410 && str2.equals("visible") && strArrSplit2.length >= 3 && !strArrSplit2[1].isEmpty()) {
                            linkedHashMap.put(strArrSplit2[1], Boolean.valueOf("1".equals(strArrSplit2[2])));
                        }
                    } else if (str2.equals("order") && strArrSplit2.length >= 2 && !strArrSplit2[1].isEmpty() && !arrayList.contains(strArrSplit2[1])) {
                        arrayList.add(strArrSplit2[1]);
                    }
                } else if (str2.equals("import") && strArrSplit2.length >= 4) {
                    arrayList2.add(new Imported(strArrSplit2[1], strArrSplit2[2], Long.parseLong(strArrSplit2[3])));
                }
            } catch (IllegalArgumentException unused) {
            }
        }
        return new MasterConfig(arrayList, linkedHashMap, arrayList2);
    }

    public MasterLayout.Saved layout() {
        return new MasterLayout.Saved(this.order, this.visible);
    }

    public String write() {
        StringBuilder sb = new StringBuilder("jiege-master 1\n");
        for (String str : this.order) {
            sb.append("order\t");
            sb.append(clean(str));
            sb.append('\n');
        }
        for (Map.Entry<String, Boolean> entry : this.visible.entrySet()) {
            sb.append("visible\t");
            sb.append(clean(entry.getKey()));
            sb.append('\t');
            sb.append(entry.getValue().booleanValue() ? '1' : '0');
            sb.append('\n');
        }
        for (Imported imported : this.imported) {
            sb.append("import\t");
            sb.append(imported.id);
            sb.append('\t');
            sb.append(imported.title);
            sb.append('\t');
            sb.append(imported.size);
            sb.append('\n');
        }
        return sb.toString();
    }
}
