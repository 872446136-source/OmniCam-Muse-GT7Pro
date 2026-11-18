package local.jiege.hook.common;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import defpackage.t2;
import java.lang.reflect.Method;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class SuperCall {

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class ProbeChild extends ProbeParent {
        @Override // local.jiege.hook.common.SuperCall.ProbeParent
        public String value() {
            return "child";
        }
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static class ProbeParent {
        public String value() {
            return "parent";
        }
    }

    private SuperCall() {
    }

    public static void inherit(Class<?> cls, Class<?> cls2, String str, Class<?>... clsArr) {
        final Method methodPin = pin(cls2, str, clsArr);
        XposedBridge.hookMethod(cls.getDeclaredMethod(str, clsArr), new XC_MethodReplacement() { // from class: local.jiege.hook.common.SuperCall.5
            public Object replaceHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                return XposedBridge.invokeOriginalMethod(methodPin, methodHookParam.thisObject, methodHookParam.args);
            }
        });
    }

    public static Method pin(Class<?> cls, String str, Class<?>... clsArr) {
        Method declaredMethod = cls.getDeclaredMethod(str, clsArr);
        declaredMethod.setAccessible(true);
        XposedBridge.hookMethod(declaredMethod, new XC_MethodHook() { // from class: local.jiege.hook.common.SuperCall.2
        });
        return declaredMethod;
    }

    public static void verify() {
        Method declaredMethod = ProbeParent.class.getDeclaredMethod("value", null);
        XC_MethodHook.Unhook unhookHookMethod = XposedBridge.hookMethod(declaredMethod, new XC_MethodHook() { // from class: local.jiege.hook.common.SuperCall.1
        });
        Object objInvokeOriginalMethod = XposedBridge.invokeOriginalMethod(declaredMethod, new ProbeChild(), new Object[0]);
        unhookHookMethod.unhook();
        if ("parent".equals(objInvokeOriginalMethod)) {
            return;
        }
        t2.j("Non-virtual superclass invocation unavailable");
    }

    public static void inherit(Method method, Method method2) {
        final Method methodPin = pin(method2);
        XposedBridge.hookMethod(method, new XC_MethodReplacement() { // from class: local.jiege.hook.common.SuperCall.4
            public Object replaceHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                return XposedBridge.invokeOriginalMethod(methodPin, methodHookParam.thisObject, methodHookParam.args);
            }
        });
    }

    public static Method pin(Method method) {
        method.setAccessible(true);
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.common.SuperCall.3
        });
        return method;
    }
}
