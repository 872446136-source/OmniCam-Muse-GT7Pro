package local.jiege.hook.natural;

import java.util.Iterator;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class NaturalConfig {
    private static final String TMC_SECTION = "TMCParamsSet";
    private static final String[] ENHANCE_SECTIONS = {"EnhanceNetParamsSet", "EnhanceNetJDDGANParamsSet"};
    private static final Pattern ENHANCE_WEIGHT = Pattern.compile(".*(BlendWeight|MaskWeight)$");
    private static final Pattern USM_STRENGTH = Pattern.compile("usmStrength(1X|3X)");
    private static final Pattern LCE_STRENGTH = Pattern.compile("lceStrength(Face)?");

    private NaturalConfig() {
    }

    public static String apply(String str, boolean z, boolean z2) throws JSONException {
        JSONObject jSONObject = new JSONObject(str);
        if (z) {
            for (String str2 : ENHANCE_SECTIONS) {
                zeroMatching(jSONObject.optJSONObject(str2), ENHANCE_WEIGHT);
            }
            zeroMatching(jSONObject.optJSONObject(TMC_SECTION), USM_STRENGTH);
        }
        if (z2) {
            zeroMatching(jSONObject.optJSONObject(TMC_SECTION), LCE_STRENGTH);
        }
        return jSONObject.toString();
    }

    private static void zeroMatching(Object obj, Pattern pattern) throws JSONException {
        if (!(obj instanceof JSONObject)) {
            if (obj instanceof JSONArray) {
                JSONArray jSONArray = (JSONArray) obj;
                for (int i = 0; i < jSONArray.length(); i++) {
                    zeroMatching(jSONArray.get(i), pattern);
                }
                return;
            }
            return;
        }
        JSONObject jSONObject = (JSONObject) obj;
        Iterator<String> itKeys = jSONObject.keys();
        while (itKeys.hasNext()) {
            String next = itKeys.next();
            Object obj2 = jSONObject.get(next);
            if (pattern.matcher(next).matches()) {
                jSONObject.put(next, zeroed(obj2));
            } else {
                zeroMatching(obj2, pattern);
            }
        }
    }

    private static Object zeroed(Object obj) {
        if (!(obj instanceof JSONArray)) {
            if ((obj instanceof Integer) || (obj instanceof Long)) {
                return 0;
            }
            return Double.valueOf(0.0d);
        }
        JSONArray jSONArray = (JSONArray) obj;
        JSONArray jSONArray2 = new JSONArray();
        for (int i = 0; i < jSONArray.length(); i++) {
            jSONArray2.put(zeroed(jSONArray.get(i)));
        }
        return jSONArray2;
    }
}
