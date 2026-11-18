package local.jiege.hook.pop;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.SymbolSearch;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class PopCaptureGate {
    private static final String BASE_MODE = "com.oplus.camera.module.BaseMode";
    private static final String TAG = "PopPort";
    private static volatile Field cameraId;
    private static volatile Class<?> decisionType;
    private static volatile boolean reportedDecision;
    private static volatile boolean reportedFix;
    private static volatile boolean reportedFrames;
    private static volatile boolean reportedPreEvList;
    private static volatile boolean reportedScope;
    private static volatile boolean reportedSource;
    private static volatile boolean reportedStable;
    private static volatile Method startPreEvList;

    private PopCaptureGate() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void align(XC_MethodHook.MethodHookParam methodHookParam, Method method) {
        Field field;
        Object[] objArr = methodHookParam.args;
        if (objArr.length < 1 || objArr[0] == null || !inRetroMode(methodHookParam.thisObject) || (field = cameraId) == null) {
            return;
        }
        try {
            int iIntValue = ((Integer) method.invoke(methodHookParam.thisObject, null)).intValue();
            int i = field.getInt(methodHookParam.args[0]);
            if (i == iIntValue) {
                return;
            }
            field.setInt(methodHookParam.args[0], iIntValue);
            if (reportedFix) {
                return;
            }
            reportedFix = true;
            Log.i(TAG, "POP capture: the preview decision was made for camera " + i + " and the mode is on " + iIntValue + ", aligned so the shutter can fire; decision " + describe(methodHookParam.args[0]));
        } catch (Throwable th) {
            Log.e(TAG, "POP capture gate could not align the decision camera id", th);
        }
    }

    private static Field cameraIdOf(Class<?> cls) {
        try {
            Field declaredField = cls.getDeclaredField("b");
            if (declaredField.getType() == Integer.TYPE && !Modifier.isStatic(declaredField.getModifiers())) {
                declaredField.setAccessible(true);
                return declaredField;
            }
        } catch (Throwable unused) {
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String currentDecision(Object obj) {
        List<Field> listDecisionFields = decisionFields(obj);
        if (listDecisionFields.isEmpty()) {
            return "(no decision field)";
        }
        StringBuilder sb = new StringBuilder();
        for (Field field : listDecisionFields) {
            try {
                Object obj2 = field.get(obj);
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(field.getName());
                sb.append('=');
                sb.append(obj2 == null ? "null" : describe(obj2));
            } catch (Throwable th) {
                sb.append(field.getName());
                sb.append("=(unreadable: ");
                sb.append(th);
                sb.append(')');
            }
        }
        return sb.toString();
    }

    private static Method decisionCheck(Class<?> cls) {
        ArrayList<Method> arrayList = new ArrayList();
        for (Method method : cls.getDeclaredMethods()) {
            if (!method.isSynthetic() && !Modifier.isStatic(method.getModifiers())) {
                Class<?> returnType = method.getReturnType();
                Class<?> cls2 = Boolean.TYPE;
                if (returnType == cls2) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length == 3 && !parameterTypes[0].isPrimitive() && parameterTypes[1] == cls2 && parameterTypes[2] == cls2 && cameraIdOf(parameterTypes[0]) != null) {
                        arrayList.add(method);
                    }
                }
            }
        }
        if (arrayList.size() == 1) {
            return (Method) arrayList.get(0);
        }
        if (arrayList.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Method method2 : arrayList) {
            sb.append(' ');
            sb.append(method2.getName());
            sb.append(signature(method2.getParameterTypes()));
        }
        Log.i(TAG, "POP capture gate: " + arrayList.size() + " candidates," + ((Object) sb));
        return null;
    }

    private static List<Field> decisionFields(Object obj) {
        Class<?> cls = decisionType;
        ArrayList arrayList = new ArrayList();
        if (obj != null && cls != null) {
            ArrayList arrayList2 = new ArrayList();
            for (Class<?> superclass = obj.getClass(); superclass != null && superclass != Object.class; superclass = superclass.getSuperclass()) {
                for (Field field : superclass.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && field.getType() == cls) {
                        try {
                            field.setAccessible(true);
                            if ("X1".equals(field.getName()) || "Y1".equals(field.getName())) {
                                arrayList.add(field);
                            } else {
                                arrayList2.add(field);
                            }
                        } catch (Throwable unused) {
                        }
                    }
                }
            }
            arrayList.addAll(arrayList2);
        }
        return arrayList;
    }

    private static String describe(Object obj) {
        try {
            String strValueOf = String.valueOf(obj);
            return strValueOf.indexOf(64) == 0 ? obj.getClass().getName() : strValueOf;
        } catch (Throwable unused) {
            return obj.getClass().getName();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int framesOf(Object obj) {
        if (obj == null) {
            reportFrames(null, 1);
            return 1;
        }
        String str = "mMultiFrameCount";
        Object field = readField(obj, "mMultiFrameCount");
        if (!(field instanceof Integer)) {
            str = SymbolSearch.FIELD;
            field = readField(obj, SymbolSearch.FIELD);
        }
        if (!(field instanceof Integer)) {
            field = readCall(obj, "getMultiFrameCount");
            str = "getMultiFrameCount()";
        }
        if (!(field instanceof Integer)) {
            reportFrames(null, 1);
            return 1;
        }
        int iIntValue = ((Integer) field).intValue();
        reportFrames(str, iIntValue);
        return iIntValue;
    }

    private static Method getCameraId(Class<?> cls) {
        while (cls != null) {
            try {
                Method declaredMethod = cls.getDeclaredMethod("getCameraId", null);
                if (declaredMethod.getReturnType() == Integer.TYPE && declaredMethod.getParameterTypes().length == 0) {
                    declaredMethod.setAccessible(true);
                    return declaredMethod;
                }
                return null;
            } catch (NoSuchMethodException unused) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean inRetroMode(Object obj) {
        Class<?> clsRetroClass = PopFilterGate.retroClass();
        if (clsRetroClass != null) {
            return clsRetroClass.isInstance(obj);
        }
        if (!reportedScope) {
            reportedScope = true;
            Log.i(TAG, "POP capture gate: RetroMode did not resolve, aligning on any mode");
        }
        return true;
    }

    public static void install(ClassLoader classLoader) {
        try {
            Class clsFindClass = XposedHelpers.findClass(BASE_MODE, classLoader);
            Method methodDecisionCheck = decisionCheck(clsFindClass);
            if (methodDecisionCheck == null) {
                Log.i(TAG, "POP capture gate: com.oplus.camera.module.BaseMode has no (decision, boolean, boolean):boolean, the shutter is left as the camera has it");
                return;
            }
            Class<?> cls = methodDecisionCheck.getParameterTypes()[0];
            Field fieldCameraIdOf = cameraIdOf(cls);
            if (fieldCameraIdOf == null) {
                Log.i(TAG, "POP capture gate: " + cls.getName() + " has no int camera id, the shutter is left as the camera has it");
                return;
            }
            final Method cameraId2 = getCameraId(clsFindClass);
            if (cameraId2 == null) {
                Log.i(TAG, "POP capture gate: no getCameraId() on com.oplus.camera.module.BaseMode");
                return;
            }
            cameraId = fieldCameraIdOf;
            decisionType = cls;
            XposedBridge.hookMethod(methodDecisionCheck, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopCaptureGate.1
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (PopCaptureGate.inRetroMode(methodHookParam.thisObject)) {
                        Object[] objArr = methodHookParam.args;
                        PopSdkGate.arm(PopCaptureGate.framesOf(objArr.length > 0 ? objArr[0] : null));
                        PopCaptureGate.align(methodHookParam, cameraId2);
                        PopCaptureGate.startPreEvList(methodHookParam);
                    }
                }
            });
            Log.i(TAG, "POP capture gate installed: com.oplus.camera.module.BaseMode." + methodDecisionCheck.getName() + "(" + cls.getName() + ", boolean, boolean), camera id field " + fieldCameraIdOf.getName());
            skipFallbackWait();
            reportDecisionSource();
        } catch (Throwable th) {
            Log.e(TAG, "POP capture gate unavailable", th);
        }
    }

    private static Object readCall(Object obj, String str) {
        try {
            return XposedHelpers.callMethod(obj, str, new Object[0]);
        } catch (Throwable unused) {
            return null;
        }
    }

    private static Object readField(Object obj, String str) {
        try {
            return XposedHelpers.getObjectField(obj, str);
        } catch (Throwable unused) {
            return null;
        }
    }

    private static void reportDecisionSource() {
        try {
            Symbols symbols = Symbols.get();
            if (!symbols.has("BaseMode.sendCaptureRequestCore")) {
                Log.i(TAG, "POP capture gate: no BaseMode.sendCaptureRequestCore symbol, the decision timestamp cannot be reported");
                return;
            }
            Method method = symbols.method("BaseMode.sendCaptureRequestCore");
            XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopCaptureGate.3
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (PopCaptureGate.inRetroMode(methodHookParam.thisObject) && !PopCaptureGate.reportedDecision) {
                        PopCaptureGate.reportedDecision = true;
                        Log.i(PopCaptureGate.TAG, "POP capture: the request is built with the mode's decision " + PopCaptureGate.currentDecision(methodHookParam.thisObject));
                    }
                }
            });
            Log.i(TAG, "POP capture gate: decision timestamp reporter armed (com.oplus.camera.module.BaseMode." + method.getName() + ")");
        } catch (Throwable th) {
            Log.e(TAG, "POP capture gate could not arm the decision reporter", th);
        }
    }

    private static void reportFrames(String str, int i) {
        if (reportedFrames) {
            return;
        }
        reportedFrames = true;
        if (str == null) {
            Log.i(TAG, "POP capture: the app's decision carries no readable frame count (mMultiFrameCount / f / getMultiFrameCount() all absent), the SDK's preview gate is repaired with 1 frame");
            return;
        }
        Log.i(TAG, "POP capture: the app's decision carries " + i + " frame(s) in " + str + ", that is the number written into the SDK's preview gate");
    }

    private static String signature(Class<?>[] clsArr) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < clsArr.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(clsArr[i].getSimpleName());
        }
        sb.append(')');
        return sb.toString();
    }

    private static void skipFallbackWait() {
        try {
            Symbols symbols = Symbols.get();
            if (!symbols.has("BaseMode.fallbackStable")) {
                Log.i(TAG, "POP capture gate: no BaseMode.fallbackStable symbol, POP keeps waiting on a fallback that never settles on this phone");
                return;
            }
            Method method = symbols.method("BaseMode.fallbackStable");
            XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.pop.PopCaptureGate.2
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (PopCaptureGate.inRetroMode(methodHookParam.thisObject)) {
                        methodHookParam.setResult(Boolean.TRUE);
                        if (PopCaptureGate.reportedStable) {
                            return;
                        }
                        PopCaptureGate.reportedStable = true;
                        Log.i(PopCaptureGate.TAG, "POP capture: the fallback-stable wait is skipped in POP, it never settles on this phone and it also disables the shutter");
                    }
                }
            });
            Log.i(TAG, "POP capture gate: fallback-stable wait skipped in POP (com.oplus.camera.module.BaseMode." + method.getName() + ")");
        } catch (Throwable th) {
            Log.e(TAG, "POP capture gate could not skip the fallback wait", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void startPreEvList(XC_MethodHook.MethodHookParam methodHookParam) {
        Object[] objArr = methodHookParam.args;
        if (objArr.length < 1 || objArr[0] == null || !inRetroMode(methodHookParam.thisObject)) {
            return;
        }
        Object obj = methodHookParam.args[0];
        Method methodStartPreEvListMethod = startPreEvListMethod();
        if (methodStartPreEvListMethod == null) {
            if (reportedPreEvList) {
                return;
            }
            reportedPreEvList = true;
            Log.i(TAG, "POP capture: no BaseMode.startPreEvList symbol, the capture request keeps the camera's own decision timestamp");
            return;
        }
        try {
            Object objInvoke = methodStartPreEvListMethod.invoke(methodHookParam.thisObject, obj);
            boolean zEquals = Boolean.TRUE.equals(objInvoke);
            if (!reportedPreEvList) {
                reportedPreEvList = true;
                StringBuilder sb = new StringBuilder("POP capture: startPreEvList called for the retro mode, the camera answered ");
                sb.append(objInvoke);
                sb.append(zEquals ? "; the request now carries the preview decision's timestamp" : "; storing the decision on the mode so the request still carries it");
                Log.i(TAG, sb.toString());
            }
            if (zEquals) {
                return;
            }
            storeDecision(methodHookParam.thisObject, obj);
        } catch (Throwable th) {
            Log.e(TAG, "POP capture: startPreEvList failed, storing the decision instead", th);
            storeDecision(methodHookParam.thisObject, obj);
        }
    }

    private static Method startPreEvListMethod() {
        Method method = startPreEvList;
        if (method != null) {
            return method;
        }
        try {
            Symbols symbols = Symbols.get();
            if (!symbols.has("BaseMode.startPreEvList")) {
                return null;
            }
            Method method2 = symbols.method("BaseMode.startPreEvList");
            startPreEvList = method2;
            return method2;
        } catch (Throwable th) {
            Log.e(TAG, "POP capture: startPreEvList symbol unusable", th);
            return null;
        }
    }

    private static void storeDecision(Object obj, Object obj2) {
        List<Field> listDecisionFields = decisionFields(obj);
        if (listDecisionFields.isEmpty()) {
            if (reportedSource) {
                return;
            }
            reportedSource = true;
            StringBuilder sb = new StringBuilder("POP capture: ");
            sb.append(obj.getClass().getSimpleName());
            sb.append(" has no field of type ");
            sb.append(decisionType == null ? "?" : decisionType.getName());
            sb.append(" anywhere in its hierarchy, the request keeps no decision");
            Log.i(TAG, sb.toString());
            return;
        }
        StringBuilder sb2 = new StringBuilder();
        try {
            for (Field field : listDecisionFields) {
                field.set(obj, obj2);
                if (sb2.length() > 0) {
                    sb2.append(',');
                }
                sb2.append(field.getName());
            }
            if (reportedSource) {
                return;
            }
            reportedSource = true;
            Log.i(TAG, "POP capture: decision stored on " + obj.getClass().getSimpleName() + " fields " + ((Object) sb2));
        } catch (Throwable th) {
            Log.e(TAG, "POP capture: the decision could not be stored on the mode", th);
        }
    }
}
