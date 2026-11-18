package local.jiege.hook.gr;

import java.lang.reflect.Method;
import local.jiege.hook.common.Install;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class GrState {
    static volatile Object currentMode;
    private static volatile Method grModeCheck;
    private static volatile boolean lastGrMode;
    private static final ThreadLocal<Boolean> checking = new ThreadLocal<>();
    static volatile String snapSetting = Install.OFF;
    static volatile String watermarkStyleId = "gr_style_1";

    private GrState() {
    }

    public static void init(Symbols symbols) {
        grModeCheck = symbols.method("GrSupport.isGrMode");
    }

    public static boolean isGrMode() {
        Method method = grModeCheck;
        if (method == null) {
            return false;
        }
        Boolean bool = Boolean.TRUE;
        ThreadLocal<Boolean> threadLocal = checking;
        if (bool.equals(threadLocal.get())) {
            return lastGrMode;
        }
        threadLocal.set(bool);
        try {
            boolean zEquals = bool.equals(method.invoke(null, null));
            lastGrMode = zEquals;
            threadLocal.remove();
            return zEquals;
        } catch (Throwable unused) {
            checking.remove();
            return false;
        }
    }

    public static boolean snapActive() {
        return (snapSetting == null || Install.OFF.equalsIgnoreCase(snapSetting)) ? false : true;
    }
}
