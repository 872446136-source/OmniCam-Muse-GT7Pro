package local.jiege.hook.filters;

import defpackage.c3;
import defpackage.t2;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class FilterCatalog {
    private static final Map<FilterScope, FilterCatalog> catalogs = new EnumMap(FilterScope.class);
    private static MasterConfig config = MasterConfig.empty();
    private static boolean mirrorMaster;
    private final Map<String, FilterEntry> byId = new LinkedHashMap();
    private final List<FilterEntry> entries;
    public final FilterScope scope;

    private FilterCatalog(FilterScope filterScope, List<FilterEntry> list) {
        this.scope = filterScope;
        for (FilterEntry filterEntry : list) {
            if (this.byId.put(filterEntry.id, filterEntry) != null) {
                StringBuilder sb = new StringBuilder("Duplicate ");
                sb.append(filterScope);
                String str = filterEntry.id;
                sb.append(" filter ");
                sb.append(str);
                throw new IllegalStateException(sb.toString());
            }
        }
        this.entries = Collections.unmodifiableList(new ArrayList(list));
    }

    private static void checkShared() {
        Map<FilterScope, FilterCatalog> map = catalogs;
        FilterCatalog filterCatalog = map.get(FilterScope.MASTER);
        for (FilterEntry filterEntry : map.get(FilterScope.NORMAL).entries) {
            FilterEntry filterEntryFind = filterCatalog.find(filterEntry.id);
            if (filterEntryFind != null && (!equal(filterEntry.captureIdFrom, filterEntryFind.captureIdFrom) || !equal(filterEntry.title, filterEntryFind.title) || !equal(filterEntry.nameResource, filterEntryFind.nameResource))) {
                t2.j(c3.l(new StringBuilder(), filterEntry.id, ": normal and master entries disagree on shared state"));
                return;
            }
        }
    }

    public static synchronized MasterConfig config() {
        return config;
    }

    public static synchronized void configure(MasterConfig masterConfig, boolean z) {
        if (masterConfig == null) {
            try {
                masterConfig = MasterConfig.empty();
            } catch (Throwable th) {
                throw th;
            }
        }
        config = masterConfig;
        mirrorMaster = z;
        catalogs.clear();
    }

    private static boolean equal(Object obj, Object obj2) {
        if (obj == null) {
            return obj2 == null;
        }
        return obj.equals(obj2);
    }

    public static synchronized FilterCatalog get(FilterScope filterScope) {
        Map<FilterScope, FilterCatalog> map;
        try {
            map = catalogs;
            if (map.isEmpty()) {
                List<FilterEntry> listEntries = MasterFilterDefaults.entries(config);
                List<FilterEntry> listEntries2 = NormalFilterDefaults.entries();
                if (mirrorMaster) {
                    listEntries2.addAll(NormalMasterMirror.mirrored(listEntries, listEntries2, config.layout()));
                }
                FilterScope filterScope2 = FilterScope.NORMAL;
                map.put(filterScope2, new FilterCatalog(filterScope2, listEntries2));
                FilterScope filterScope3 = FilterScope.MASTER;
                map.put(filterScope3, new FilterCatalog(filterScope3, listEntries));
                checkShared();
            }
        } catch (Throwable th) {
            throw th;
        }
        return map.get(filterScope);
    }

    public static synchronized boolean mirrorsMaster() {
        return mirrorMaster;
    }

    public List<FilterEntry> enabled() {
        ArrayList arrayList = new ArrayList();
        for (FilterEntry filterEntry : this.entries) {
            if (filterEntry.enabled) {
                arrayList.add(filterEntry);
            }
        }
        return arrayList;
    }

    public List<FilterEntry> entries() {
        return this.entries;
    }

    public FilterEntry find(String str) {
        if (str == null) {
            return null;
        }
        return this.byId.get(str);
    }

    public static synchronized void configure(MasterConfig masterConfig) {
        configure(masterConfig, false);
    }
}
