package local.jiege.hook.watermark;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;
import org.json.JSONObject;

/**
 * 哈苏水印：地域白名单读到 null 时，不要让它把整条水印链打断。
 *
 * <p>493 抓到的崩溃（进 POP 时的一次 {@code BaseMode.dh}）：
 *
 * <pre>
 *   java.lang.NullPointerException: Attempt to invoke virtual method
 *     'boolean org.json.JSONObject.has(java.lang.String)' on a null object reference
 *     at ...MasterWatermarkCreator.isSystemRegionOverScope(MasterWatermarkCreator.java:6)
 *     at cl.e.m(WatermarkModel.java:322)
 *     at com.oplus.camera.module.BaseMode.da(BaseMode.java:101)
 *     at com.oplus.camera.module.BaseMode.dh(BaseMode.java:52)
 * </pre>
 *
 * <p>原因是这台机器的 ODM 里没有水印样式的 json：{@code /odm/etc/camera/photo_watermark/} 整个目录
 * 不存在（{@code basictone/mode_limit_watermark/} 里只有真我 GT 的 {@code gr_style_*}，一套哈苏样式
 * 都没有），于是 {@code BaseWatermarkManager.getWatermarkJson-aiMaster} 返回 null，调用方把这个 null
 * 直接交给 {@code isSystemRegionOverScope}，而它第一行就是 {@code json.has("countryCode")}。
 *
 * <p>修法只碰这一处：白名单的输入为 null 时，这个方法回答 false（"不在地域限制范围内"），方法体
 * 不再执行，null 也就不会被解引用。有 json 的正常路径一个字都不改 —— 这里不做任何"补数据"，
 * 也不猜样式 id；缺的是这台机器本来就没有的资源，不是这次要造的东西。
 *
 * <p>{@code MasterWatermarkCreator} 在 {@code com.oplus.ocs.ipu} 包里（IPU SDK），类名没有被混淆，
 * 所以按名字找，不走符号表。找不到就自己关掉，日志里说明白，相机照旧（还是会在那一行 NPE）。
 *
 * <p>关掉的办法：{@code touch /data/adb/omnicam_hasselwm_null_off}。
 */
public final class WatermarkNullGuard {
    private static final String TAG = "WatermarkNull";
    private static final String OFF = "/data/adb/omnicam_hasselwm_null_off";
    private static final String CREATOR =
        "com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkCreator";
    private static final String CHECK = "isSystemRegionOverScope";

    /** One line per process: this runs on every mode switch, the log would be unreadable otherwise. */
    private static boolean reportedNull;
    private static boolean reportedInstalled;

    private WatermarkNullGuard() {}

    public static void installCamera(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "地域白名单空值保护已关闭（" + OFF + " 存在），水印链仍会在那一行 NPE");
            return;
        }
        Log.guard(TAG, "地域白名单空值保护", () -> install(classLoader));
    }

    private static void install(ClassLoader classLoader) throws Throwable {
        Class<?> creator;
        try {
            creator = XposedHelpers.findClass(CREATOR, classLoader);
        } catch (Throwable missing) {
            Log.i(TAG, CREATOR + " 不在相机进程的类路径里，空值保护装不上；"
                + "水印样式缺失时相机仍会在 isSystemRegionOverScope 抛 NPE");
            return;
        }
        List<Method> checks = new ArrayList<>();
        for (Method method : creator.getDeclaredMethods()) {
            if (!CHECK.equals(method.getName())) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length < 1) continue;
            if (!JSONObject.class.isAssignableFrom(params[0])) continue;
            checks.add(method);
        }
        if (checks.isEmpty()) {
            Log.i(TAG, CREATOR + " 上有 " + CHECK + "，但签名和 493 的堆栈对不上"
                + "（没有一个方法的第一个参数是 JSONObject），空值保护装不上");
            return;
        }
        for (Method method : checks) {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length < 1 || param.args[0] != null) return;
                    param.setResult(Boolean.FALSE);
                    if (reportedNull) return;
                    reportedNull = true;
                    Log.i(TAG, "水印样式的 json 是 null（本机 ODM 没有 photo_watermark 目录），"
                        + CHECK + " 按「不在地域限制范围内」回答，不再抛 NPE；调用方 "
                        + caller());
                }
            });
        }
        if (!reportedInstalled) {
            reportedInstalled = true;
            Log.i(TAG, "地域白名单空值保护已装：" + CREATOR + "." + CHECK + " " + checks.size()
                + " 个签名，参数为 null 时回答 false");
        }
    }

    /** The nearest frame outside this module, so the next report says who passed the null. */
    private static String caller() {
        StackTraceElement[] frames = new Throwable().getStackTrace();
        for (StackTraceElement frame : frames) {
            String name = frame.getClassName();
            if (name.startsWith(WatermarkNullGuard.class.getName())) continue;
            if (name.startsWith("de.robv.android.xposed")) continue;
            return frame.getClassName() + "." + frame.getMethodName() + "(" + frame.getFileName()
                + ":" + frame.getLineNumber() + ")";
        }
        return "(unknown)";
    }
}
