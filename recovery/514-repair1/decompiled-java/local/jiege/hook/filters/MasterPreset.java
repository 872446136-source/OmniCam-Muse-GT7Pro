package local.jiege.hook.filters;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class MasterPreset {
    public final Map<String, Object> effect;
    public final String filterId;
    public final String id;
    public final String name;
    public final ProParams pro;

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class ProParams {
        public final String exposureCompensation;
        public final Map<String, String> extra;
        public final String focus;
        public final String iso;
        public final String shutter;
        public final String whiteBalance;

        public ProParams(String str, String str2, String str3, String str4, String str5, Map<String, String> map) {
            this.iso = str;
            this.shutter = str2;
            this.whiteBalance = str3;
            this.exposureCompensation = str4;
            this.focus = str5;
            this.extra = Collections.unmodifiableMap(new LinkedHashMap(map));
        }
    }

    public MasterPreset(String str, String str2, String str3, Map<String, Object> map, ProParams proParams) {
        this.id = str;
        this.name = str2;
        this.filterId = str3;
        this.effect = Collections.unmodifiableMap(new LinkedHashMap(map));
        this.pro = proParams;
    }
}
