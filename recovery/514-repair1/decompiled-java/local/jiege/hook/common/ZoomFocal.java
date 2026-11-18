package local.jiege.hook.common;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ZoomFocal {
    private static final TreeMap<Float, Float> STOPS = new TreeMap<>();

    private ZoomFocal() {
    }

    public static float millimetres(Symbols symbols, float f) {
        TreeMap<Float, Float> treeMap = STOPS;
        synchronized (treeMap) {
            try {
                if (treeMap.isEmpty()) {
                    for (String str : (List) symbols.call("CameraConfig.list", null, "com.oplus.available.sat.all.zoomvalues")) {
                        int iIndexOf = str.indexOf(40);
                        STOPS.put(Float.valueOf(Float.parseFloat(str.substring(0, iIndexOf))), Float.valueOf(Float.parseFloat(str.substring(iIndexOf + 1, str.length() - 1))));
                    }
                }
                TreeMap<Float, Float> treeMap2 = STOPS;
                Map.Entry<Float, Float> entryFloorEntry = treeMap2.floorEntry(Float.valueOf(f));
                Map.Entry<Float, Float> entryCeilingEntry = treeMap2.ceilingEntry(Float.valueOf(f));
                if (entryFloorEntry == null) {
                    return (entryCeilingEntry.getValue().floatValue() * f) / entryCeilingEntry.getKey().floatValue();
                }
                if (entryCeilingEntry == null) {
                    return (entryFloorEntry.getValue().floatValue() * f) / entryFloorEntry.getKey().floatValue();
                }
                if (entryFloorEntry.getKey().equals(entryCeilingEntry.getKey())) {
                    return entryFloorEntry.getValue().floatValue();
                }
                return (((f - entryFloorEntry.getKey().floatValue()) * (entryCeilingEntry.getValue().floatValue() - entryFloorEntry.getValue().floatValue())) / (entryCeilingEntry.getKey().floatValue() - entryFloorEntry.getKey().floatValue())) + entryFloorEntry.getValue().floatValue();
            } catch (Throwable th) {
                throw th;
            }
        }
    }
}
