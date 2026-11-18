package local.jiege.hook.filters;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ProParamsRecord {
    public static final String COLOR_TONE = "pref_professional_color_tone_key";
    public static final String EXPOSURE_COMPENSATION = "pref_professional_exposure_compensation_key";
    public static final String EXPOSURE_TIME = "pref_professional_exposure_time_key";
    public static final String FOCUS_MODE = "pref_professional_focus_mode_key";
    public static final String ISO = "pref_professional_iso_key";
    public static final String WHITE_BALANCE = "pref_professional_whitebalance_key";

    private ProParamsRecord() {
    }

    private static String clean(String str) {
        return str.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    public static Map<String, String> parse(String str) {
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        if (str != null) {
            for (String str2 : str.split("\n")) {
                int iIndexOf = str2.indexOf(9);
                if (iIndexOf > 0) {
                    linkedHashMap.put(str2.substring(0, iIndexOf), str2.substring(iIndexOf + 1).replace("\r", PresetSelection.STANDARD_KEY));
                }
            }
        }
        return linkedHashMap;
    }

    public static MasterPreset.ProParams toProParams(Map<String, String> map) {
        LinkedHashMap linkedHashMap = new LinkedHashMap(map);
        return new MasterPreset.ProParams((String) linkedHashMap.remove(ISO), (String) linkedHashMap.remove(EXPOSURE_TIME), (String) linkedHashMap.remove(WHITE_BALANCE), (String) linkedHashMap.remove(EXPOSURE_COMPENSATION), (String) linkedHashMap.remove(FOCUS_MODE), Collections.unmodifiableMap(linkedHashMap));
    }

    public static String write(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                sb.append(clean(entry.getKey()));
                sb.append('\t');
                sb.append(clean(entry.getValue()));
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
