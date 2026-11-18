package local.jiege.hook.watermark;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.pp1;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import local.jiege.hook.common.Log;
import org.json.JSONObject;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class WatermarkNullGuard {
    private static final String CHECK = "isSystemRegionOverScope";
    private static final String CREATOR = "com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkCreator";
    private static final String OFF = "/data/adb/omnicam_hasselwm_null_off";
    private static final String TAG = "WatermarkNull";
    private static boolean reportedInstalled;
    private static boolean reportedNull;

    private WatermarkNullGuard() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String caller() {
        for (StackTraceElement stackTraceElement : new Throwable().getStackTrace()) {
            String className = stackTraceElement.getClassName();
            if (!className.startsWith(WatermarkNullGuard.class.getName()) && !className.startsWith("de.robv.android.xposed")) {
                return stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName() + "(" + stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber() + ")";
            }
        }
        return "(unknown)";
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void install(ClassLoader classLoader) {
        try {
            Class clsFindClass = XposedHelpers.findClass(CREATOR, classLoader);
            ArrayList arrayList = new ArrayList();
            for (Method method : clsFindClass.getDeclaredMethods()) {
                if (CHECK.equals(method.getName())) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length >= 1 && JSONObject.class.isAssignableFrom(parameterTypes[0])) {
                        arrayList.add(method);
                    }
                }
            }
            if (arrayList.isEmpty()) {
                Log.i(TAG, "com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkCreator 上有 isSystemRegionOverScope，但签名和 493 的堆栈对不上（没有一个方法的第一个参数是 JSONObject），空值保护装不上");
                return;
            }
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                XposedBridge.hookMethod((Method) it.next(), new XC_MethodHook() { // from class: local.jiege.hook.watermark.WatermarkNullGuard.1
                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Object[] objArr = methodHookParam.args;
                        if (objArr.length < 1 || objArr[0] != null) {
                            return;
                        }
                        methodHookParam.setResult(Boolean.FALSE);
                        if (WatermarkNullGuard.reportedNull) {
                            return;
                        }
                        WatermarkNullGuard.reportedNull = true;
                        Log.i(WatermarkNullGuard.TAG, "水印样式的 json 是 null（本机 ODM 没有 photo_watermark 目录），isSystemRegionOverScope 按「不在地域限制范围内」回答，不再抛 NPE；调用方 " + WatermarkNullGuard.caller());
                    }
                });
            }
            if (reportedInstalled) {
                return;
            }
            reportedInstalled = true;
            Log.i(TAG, "地域白名单空值保护已装：com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkCreator.isSystemRegionOverScope " + arrayList.size() + " 个签名，参数为 null 时回答 false");
        } catch (Throwable unused) {
            Log.i(TAG, "com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkCreator 不在相机进程的类路径里，空值保护装不上；水印样式缺失时相机仍会在 isSystemRegionOverScope 抛 NPE");
        }
    }

    public static void installCamera(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "地域白名单空值保护已关闭（/data/adb/omnicam_hasselwm_null_off 存在），水印链仍会在那一行 NPE");
        } else {
            Log.guard(TAG, "地域白名单空值保护", new pp1(classLoader, (byte) 1));
        }
    }
}
