package local.jiege.hook.watermark;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import local.jiege.hook.common.Log;

/**
 * Camera watermark page: the empty 哈苏 (HASSELBLAD) section.
 *
 * <p>503 report: 设置 → 水印 → 照片水印 shows the HASSELBLAD wordmark and then nothing under it, so
 * there is no Hasselblad style to pick. That page is not the camera's own UI — it is
 * {@code com.coloros.gallery3d} rendering {@code PhotoEditorActivity} over the camera, and the
 * widget tree (dumped from the device) says exactly which part is empty:
 *
 * <pre>
 *   camera_rm_chip_group           通用 / 理光 GR          (two chips, by design)
 *   camera_hassel_style_layout     only camera_style_item_title_image — the wordmark
 *   camera_frame_style_layout      个性画框 / 灵感取色 / 大师印记 / 经典相机   (three items each)
 * </pre>
 *
 * <p>{@code camera_hassel_style_layout} is filled by
 * {@code WatermarkCameraSection.K0()}, which builds its list through
 * {@code H0(section, type, show, flags)}: the type goes through the {@code yjs} lambda in
 * {@code I0}, which answers the list of style items. Two of the arguments {@code K0} passes come
 * out of {@code T0()}:
 *
 * <pre>
 *   T0() = vvm("WatermarkMasterVM/ShowHasselXpanStyle") ?: Pair(FALSE, TRUE)
 *   show = T0().first &amp;&amp; pageIsWide &amp;&amp; screenIsNotSmall
 * </pre>
 *
 * <p>So on this realme build the page asks the view model for the flag, gets nothing, and the
 * default is FALSE: {@code H0} is called with {@code show = false}, and the {@code show} flag is
 * one of the arguments the item query receives — the section ends up with the wordmark only.
 * The flag decides the XPAN part of the same section too ({@code camera_hassel_xpan_style_layout}).
 *
 * <p>This hook answers {@code T0()} with {@code Pair(TRUE, TRUE)}. Nothing else is touched: the
 * style items still come from the gallery's own lambda and its own assets
 * ({@code assets/watermark_master_styles/hassel_style_*.json}, which the installed APK carries),
 * and if that list is empty anyway the section stays as it is. Every answer is logged with the
 * item count of the adapter {@code H0} built, so one report says whether the flag was the gate or
 * the data behind it is.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_hasselcam_off}.
 */
public final class HasselCameraPage {
    private static final String TAG = "HasselCamPage";
    private static final String OFF = "/data/adb/omnicam_hasselcam_off";
    private static final String SECTION =
        "com.oplus.gallery.photoeditor.editingvvm.watermarkCamera.WatermarkCameraSection";

    /** One line per process: the page is rebuilt on every tab switch. */
    private static boolean reportedFlag;
    private static int adapters;

    private HasselCameraPage() {}

    public static void installGallery(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "off (" + OFF + ")");
            return;
        }
        Log.guard(TAG, "哈苏分区开关", () -> hookFlag(classLoader));
        Log.guard(TAG, "哈苏分区列表", () -> watchLists(classLoader));
    }

    /**
     * {@code T0()} is the page's own reader of {@code ShowHasselXpanStyle}; the class is not
     * obfuscated, so it is reached by name.
     */
    private static void hookFlag(final ClassLoader classLoader) throws Throwable {
        Class<?> section = XposedHelpers.findClass(SECTION, classLoader);
        XposedHelpers.findAndHookMethod(section, "T0", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Object before = param.getResult();
                Object show = first(before);
                if (Boolean.TRUE.equals(show)) return;
                try {
                    Object pair = XposedHelpers.newInstance(
                        XposedHelpers.findClass("kotlin.Pair", classLoader),
                        Boolean.TRUE, Boolean.TRUE);
                    param.setResult(pair);
                } catch (Throwable error) {
                    Log.e(TAG, "哈苏分区开关回答失败", error);
                    return;
                }
                if (!reportedFlag) {
                    reportedFlag = true;
                    Log.i(TAG, "ShowHasselXpanStyle 原为 " + before + "，已回答 true");
                }
            }
        });
        Log.i(TAG, "哈苏分区开关已接管 " + SECTION + ".T0");
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

    /** {@code Pair.getFirst()} without linking against kotlin at compile time. */
    private static Object first(Object item) {
        if (item == null) return null;
        try {
            return XposedHelpers.callMethod(item, "getFirst");
        } catch (Throwable ignored) {
            return null;
        }
    }
}
