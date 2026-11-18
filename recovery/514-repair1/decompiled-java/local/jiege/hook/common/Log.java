package local.jiege.hook.common;

import de.robv.android.xposed.XposedBridge;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class Log {

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public interface Installer {
        void install();
    }

    private Log() {
    }

    public static void e(String str, String str2, Throwable th) {
        String str3 = str + ": " + str2 + " " + th;
        XposedBridge.log(str3);
        Diagnostics.record("E", str3 + "\n" + android.util.Log.getStackTraceString(th));
    }

    public static void guard(String str, String str2, Installer installer) {
        try {
            installer.install();
        } catch (Throwable th) {
            e(str, str2 + " unavailable", th);
        }
    }

    public static void i(String str, String str2, Throwable th) {
        String str3 = str + ": " + str2 + " " + th;
        XposedBridge.log(str3);
        Diagnostics.record("I", str3 + "\n" + android.util.Log.getStackTraceString(th));
    }

    public static void e(String str, String str2) {
        String str3 = str + ": " + str2;
        XposedBridge.log(str3);
        Diagnostics.record("E", str3);
    }

    public static void i(String str, String str2) {
        String str3 = str + ": " + str2;
        XposedBridge.log(str3);
        Diagnostics.record("I", str3);
    }
}
