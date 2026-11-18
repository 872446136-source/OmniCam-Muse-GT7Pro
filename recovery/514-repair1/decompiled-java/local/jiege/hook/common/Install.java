package local.jiege.hook.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import local.jiege.hook.filters.PresetSelection;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class Install {
    public static final String FAILED = "failed";
    public static final String OFF = "off";
    public static final String ON = "on";
    private static final Map<String, String[]> LINES = new LinkedHashMap();
    private static final List<String> ORDER = new ArrayList();

    private Install() {
    }

    public static void on(String str) {
        put(str, ON, PresetSelection.STANDARD_KEY);
        Log.i("Install", str + " on");
    }

    private static synchronized void put(String str, String str2, String str3) {
        try {
            Map<String, String[]> map = LINES;
            if (!map.containsKey(str)) {
                ORDER.add(str);
            }
            map.put(str, new String[]{str2, str3});
        } catch (Throwable th) {
            throw th;
        }
    }

    public static synchronized String report() {
        StringBuilder sb;
        try {
            sb = new StringBuilder();
            for (String str : ORDER) {
                String[] strArr = LINES.get(str);
                sb.append(strArr[0]);
                sb.append('\t');
                sb.append(str);
                sb.append('\t');
                sb.append(strArr[1]);
                sb.append('\n');
            }
        } catch (Throwable th) {
            throw th;
        }
        return sb.toString();
    }

    public static void run(String str, boolean z, String str2, Log.Installer installer) {
        if (!z) {
            skip(str, str2);
            return;
        }
        try {
            installer.install();
            put(str, ON, PresetSelection.STANDARD_KEY);
        } catch (Throwable th) {
            put(str, FAILED, String.valueOf(th));
            Log.e("Install", str + " failed", th);
        }
    }

    public static void skip(String str, String str2) {
        put(str, OFF, str2);
        Log.i("Install", str + " off: " + str2);
    }

    public static synchronized Map<String, String> status() {
        LinkedHashMap linkedHashMap;
        String string;
        try {
            linkedHashMap = new LinkedHashMap();
            for (String str : ORDER) {
                String[] strArr = LINES.get(str);
                if (ON.equals(strArr[0])) {
                    string = ON;
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(strArr[0]);
                    sb.append(strArr[1].isEmpty() ? PresetSelection.STANDARD_KEY : ": " + strArr[1]);
                    string = sb.toString();
                }
                linkedHashMap.put(str, string);
            }
        } catch (Throwable th) {
            throw th;
        }
        return linkedHashMap;
    }

    public static synchronized String summary() {
        StringBuilder sb;
        try {
            StringBuilder sb2 = new StringBuilder();
            int i = 0;
            for (String str : ORDER) {
                String[] strArr = LINES.get(str);
                if (ON.equals(strArr[0])) {
                    i++;
                } else {
                    sb2.append("\n  ");
                    sb2.append(str);
                    sb2.append(" -> ");
                    sb2.append(strArr[0]);
                    if (!strArr[1].isEmpty()) {
                        sb2.append(" (");
                        sb2.append(strArr[1]);
                        sb2.append(')');
                    }
                }
            }
            sb = new StringBuilder();
            sb.append(i);
            sb.append("/");
            sb.append(ORDER.size());
            sb.append(" features on");
            sb.append(sb2.length() == 0 ? PresetSelection.STANDARD_KEY : ", off:" + ((Object) sb2));
        } catch (Throwable th) {
            throw th;
        }
        return sb.toString();
    }
}
