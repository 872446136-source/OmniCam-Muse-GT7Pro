package local.jiege.hook.common;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class OptionalHook {
    private OptionalHook() {
    }

    public static void method(Class<?> cls, String str, XC_MethodHook xC_MethodHook, Class<?>... clsArr) {
        if (cls == null) {
            return;
        }
        Object[] objArr = new Object[clsArr.length + 1];
        System.arraycopy(clsArr, 0, objArr, 0, clsArr.length);
        objArr[clsArr.length] = xC_MethodHook;
        try {
            XposedHelpers.findAndHookMethod(cls, str, objArr);
        } catch (Throwable unused) {
        }
    }
}
