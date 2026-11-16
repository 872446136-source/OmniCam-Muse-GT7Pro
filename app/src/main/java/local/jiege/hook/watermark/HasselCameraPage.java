package local.jiege.hook.watermark;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import local.jiege.hook.common.Log;

/**
 * Restore the gallery's ordinary Hasselblad cards on realme while preserving the camera's
 * mode/layout flags. L(true) prepends the XPAN panorama cards; it is not a brand unlock.
 * The two lists use different resource ids and the view model's V flag identifies an XPAN page.
 */
public final class HasselCameraPage {
    private static final String TAG = "HasselCamPage";
    private static final String OFF = "/data/adb/omnicam_hasselcam_off";

    private static final String SECTION =
        "com.oplus.gallery.photoeditor.editingvvm.watermarkCamera.WatermarkCameraSection";

    /** The {@code StyleViewData} handler: a single method, {@code a(Object[]) -> Object}. */
    private static final String QUERY = "com.oplus.gallery.photoeditor.editingvvm.watermarkmaster.b";
    private static final String VM =
        "com.oplus.gallery.photoeditor.editingvvm.watermarkmaster.WatermarkMasterVM";
    private static final String R_STRING = "com.oplus.gallery.photoeditor.R$string";
    private static final String R_ID = "com.oplus.gallery.photoeditor.R$id";
    private static final String HASSEL_TYPE = "picture_editor_text_watermark_master_hasselblad";
    private static final String XPAN_TYPE = "picture3d_editor_id_watermark_master_hassel_xpan";

    /** Keep the XPAN cards even when the ordinary list is filled (stock layout with both halves). */
    private static final File KEEP_XPAN = new File("/data/adb/omnicam_hassel_xpan_keep");

    /** One line per process for each outcome; the page is rebuilt on every tab switch. */
    private static boolean reportedFill;
    private static boolean reportedMissing;
    private static boolean reportedXpan;
    private static int adapters;

    private HasselCameraPage() {}

    public static void installGallery(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "off (" + OFF + ")");
            return;
        }
        Log.guard(TAG, "哈苏样式查询", () -> hookQuery(classLoader));
        Log.guard(TAG, "哈苏分区样式表", () -> watchLists(classLoader));
    }

    /**
     * Fills the ordinary HASSELBLAD list: the handler answers an empty list for this brand, so when
     * that happens the same list is read through the view model's own {@code (boolean) -> List}.
     */
    private static void hookQuery(final ClassLoader classLoader) throws Throwable {
        final Class<?> query = XposedHelpers.findClass(QUERY, classLoader);
        final Class<?> vm = XposedHelpers.findClass(VM, classLoader);
        final Method read = onlyShowList(vm);
        if (read == null) {
            if (!reportedMissing) {
                reportedMissing = true;
                Log.i(TAG, "找不到唯一的 (boolean)->List 样式入口，普通哈苏保持原厂");
            }
            return;
        }
        read.setAccessible(true);
        final int type;
        final int xpan;
        try {
            Class<?> strings = XposedHelpers.findClass(R_STRING, classLoader);
            type = XposedHelpers.getStaticIntField(strings, HASSEL_TYPE);
        } catch (Throwable absent) {
            Log.i(TAG, "资源 id " + HASSEL_TYPE + " 不在这一版里，普通哈苏保持原厂");
            return;
        }
        xpan = xpanType(classLoader);
        XposedBridge.hookAllMethods(query, "a", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Object[] args = param.args.length == 1 && param.args[0] instanceof Object[]
                    ? (Object[]) param.args[0] : null;
                if (args == null || args.length < 3) return;
                if (!(args[0] instanceof Integer)) return;
                int asked = (Integer) args[0];
                Object result = param.getResult();
                if (!(result instanceof List)) return;
                if (asked == type) {
                    if (!((List<?>) result).isEmpty()) return;
                    try {
                        Object owner = viewModel(param.thisObject, vm);
                        if (owner == null) return;
                        // L(true) prepends M(false), the XPAN panorama cards. Preserve the
                        // section's actual mode/layout flag instead of treating true as ordinary.
                        Object filled = read.invoke(owner, Boolean.TRUE.equals(args[2]));
                        if (!(filled instanceof List) || ((List<?>) filled).isEmpty()) return;
                        param.setResult(filled);
                        if (!reportedFill) {
                            reportedFill = true;
                            Log.i(TAG, "普通哈苏样式 " + ((List<?>) filled).size()
                                + " 项（按页面模式读），替换掉品牌门禁给的空列表");
                        }
                    } catch (Throwable error) {
                        Log.e(TAG, "普通哈苏样式重算失败", error);
                    }
                    return;
                }
                // An XPAN-only row must not be offered from ordinary camera settings.
                if (xpan != 0 && asked == xpan && !isXpanPage(param.thisObject, vm) && !KEEP_XPAN.exists()
                    && !((List<?>) result).isEmpty()) {
                    int dropped = ((List<?>) result).size();
                    param.setResult(new java.util.ArrayList<Object>());
                    if (!reportedXpan) {
                        reportedXpan = true;
                        Log.i(TAG, "非 XPAN 页面，隐藏专用水印 " + dropped
                            + " 张收起来（放 " + KEEP_XPAN + " 可保留）");
                    }
                }
            }
        });
        Log.i(TAG, "哈苏样式查询已接管 " + QUERY + ".a（列表入口 " + read.getName()
            + "，XPAN type " + (xpan == 0 ? "未找到" : String.valueOf(xpan)) + "）");
    }

    /**
     * The XPAN half of the 哈苏 row. {@code WatermarkCameraSection.K0} passes
     * {@code R.id.…master_hassel_xpan} where the ordinary list gets an {@code R.string}; the id is
     * looked up over every {@code R$id} field and accepted only when exactly one exists.
     */
    private static int xpanType(ClassLoader classLoader) {
        try {
            Class<?> ids = XposedHelpers.findClass(R_ID, classLoader);
            java.lang.reflect.Field[] found = ids.getDeclaredFields();
            int value = 0;
            int count = 0;
            for (java.lang.reflect.Field field : found) {
                if (!field.getName().equals(XPAN_TYPE)) continue;
                count++;
                value = field.getInt(null);
            }
            return count == 1 ? value : 0;
        } catch (Throwable absent) {
            return 0;
        }
    }

    /** The one {@code (boolean) -> List} reader the view model declares; null unless it is unique. */
    private static Method onlyShowList(Class<?> vm) {
        Method found = null;
        for (Method method : vm.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (method.getReturnType() != List.class) continue;
            if (types.length != 1 || types[0] != boolean.class) continue;
            if (found != null) return null;
            found = method;
        }
        return found;
    }

    /** The view model the handler was built with: the class has one field and it is this one. */
    private static Object viewModel(Object handler, Class<?> vm) {
        if (handler == null) return null;
        for (Field field : handler.getClass().getDeclaredFields()) {
            if (!vm.isAssignableFrom(field.getType())) continue;
            try {
                field.setAccessible(true);
                return field.get(handler);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    /** 17.10.41 V reads CameraWatermarkSetting.ai_master_watermark_mode_name. */
    private static boolean isXpanPage(Object handler, Class<?> vm) {
        try {
            Object owner = viewModel(handler, vm);
            Object lazy = XposedHelpers.getObjectField(owner, "V");
            return Boolean.TRUE.equals(XposedHelpers.callMethod(lazy, "getValue"));
        } catch (Throwable unavailable) {
            // Keep the original group when a future gallery changes its mode contract.
            return true;
        }
    }

    /** Read-only: how many items the 哈苏 adapters actually get, so the next report says where to look. */
    private static void watchLists(ClassLoader classLoader) throws Throwable {
        Class<?> section = XposedHelpers.findClass(SECTION, classLoader);
        XposedHelpers.findAndHookMethod(section, "H0", section, int.class, boolean.class, int.class,
            new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Object adapter = param.getResult();
                    int count = -1;
                    if (adapter != null) {
                        try {
                            count = (Integer) XposedHelpers.callMethod(adapter, "getItemCount");
                        } catch (Throwable ignored) {
                        }
                    }
                    if (adapters++ >= 24) return;
                    Log.i(TAG, "样式表 type=" + param.args[1] + " show=" + param.args[2]
                        + " 条目=" + count);
                }
            });
        Log.i(TAG, "哈苏分区样式表已挂监听");
    }
}
