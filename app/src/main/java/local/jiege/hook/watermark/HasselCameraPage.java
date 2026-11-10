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
 * Camera watermark page: the 哈苏 (HASSELBLAD) section, which on this realme build carries only the
 * XPAN styles and none of the ordinary ones.
 *
 * <p>That page is not the camera's UI — it is {@code com.coloros.gallery3d} rendering
 * {@code PhotoEditorActivity} over the camera. {@code WatermarkCameraSection.K0()} fills the two
 * lists under the HASSELBLAD wordmark:
 *
 * <pre>
 *   camera_hassel_style_list       H0(this, R.string.…_master_hasselblad, show, 2)
 *   camera_hassel_xpan_style_list  H0(this, R.id.…_master_hassel_xpan, false, 6)
 * </pre>
 *
 * <p>{@code show} for the ordinary list is {@code !T0().first && wideScreen}, so the two lists are
 * mutually exclusive: {@code ShowHasselXpanStyle} true means "show XPAN instead". Both go through
 * {@code H0}, which turns its type into a query on the {@code WatermarkMasterVM/StyleViewData}
 * handler — {@code watermarkmaster.b.a([type, flag, show])}.
 *
 * <p><b>Why the ordinary list is empty here.</b> That handler starts with a brand check on the
 * HASSELBLAD type:
 *
 * <pre>
 *   if (type == R.string.picture_editor_text_watermark_master_hasselblad) {
 *       if (WatermarkMasterVM.n0()) return new ArrayList();   // n0() = z9b.c(6, "is_realme_brand")
 *       return vm.L(show);
 *   }
 * </pre>
 *
 * <p>{@code n0()} answers {@code is_realme_brand}, true on this device, so the list is emptied
 * before anything is read — while the XPAN type has no such branch and comes back with its styles.
 * That is exactly the 503 report: three XPAN cards under HASSELBLAD and nothing else.
 *
 * <p><b>Why {@code n0()} itself is not answered.</b> The same flag decides whether the HASSELBLAD
 * type button is added at all ({@code WatermarkMasterVM.x}, {@code if (!n0()) skip}), and it is read
 * from ten places in this package. Answering it false would bring the styles back but drop the
 * button — the section would vanish rather than fill. So the flag is left alone: when the
 * HASSELBLAD query comes back empty, {@code vm.L(show)} is called here instead, which is the same
 * call the handler would have made one line earlier. The list it returns is the one this APK builds
 * ({@code WatermarkMasterStyle.Companion.getBuiltInHasselStyles} plus the resource arrays), so
 * nothing is invented and no count is faked. A non-empty answer is passed through untouched.
 *
 * <p>503 also answered {@code T0()} with {@code Pair(TRUE, TRUE)}. That was the wrong lever: it
 * sets {@code show} false for the ordinary list and true for the XPAN one, which is what produced
 * "only the XPAN styles". It is removed — {@code T0()} keeps whatever the view model says and the
 * XPAN group keeps its stock behaviour.
 *
 * <p><b>Why some cards are black.</b> {@code WatermarkMasterVM.g0(styles, previewRes, type)} builds
 * one card per style and takes its thumbnail with {@code ArraysKt.getOrNull(previewRes, i)}: an
 * index past the end of the preview array yields null, the card's preview field keeps its zero, and
 * nothing is drawn — a black tile. This build's style list is longer than the preview array for the
 * HASSELBLAD type (the brand branch above decides how many styles there are, the array is fixed in
 * the APK), so the tail always lands past the end. {@code alignPreviews} drops that tail, leaving
 * exactly the styles that have a thumbnail — which is what a stock OPPO build shows, because there
 * the two lengths already agree.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_hasselcam_off}.
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
    private static final String XPAN_TYPE = "picture_editor_text_watermark_master_hassel_xpan";

    /** Set once the ordinary 哈苏 list has content; the XPAN half steps aside from then on. */
    private static volatile boolean ordinaryFilled;

    /** Keep the XPAN cards even when the ordinary list is filled (stock layout with both halves). */
    private static final File KEEP_XPAN = new File("/data/adb/omnicam_hassel_xpan_keep");

    /** One line per process for each outcome; the page is rebuilt on every tab switch. */
    private static boolean reportedFill;
    private static boolean reportedMissing;
    private static boolean reportedTrim;
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
        Log.guard(TAG, "哈苏预览对齐", () -> alignPreviews(classLoader));
    }

    /**
     * Drops the cards that have no thumbnail. {@code g0} builds one card per style but takes the
     * thumbnail with {@code getOrNull(previewRes, i)}, so every style past the end of the preview
     * array is built with a zero preview and renders as a black tile. The style list is longer than
     * that array on this brand, so the tail is always black.
     */
    private static void alignPreviews(ClassLoader classLoader) throws Throwable {
        Class<?> vm = XposedHelpers.findClass(VM, classLoader);
        XposedHelpers.findAndHookMethod(vm, "g0", java.util.ArrayList.class, int[].class, int.class,
            new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Object built = param.getResult();
                    int[] previews = param.args.length > 1 && param.args[1] instanceof int[]
                        ? (int[]) param.args[1] : null;
                    if (!(built instanceof List) || previews == null || previews.length == 0) return;
                    List<?> items = (List<?>) built;
                    if (items.size() <= previews.length) return;
                    param.setResult(new java.util.ArrayList<Object>(items.subList(0, previews.length)));
                    if (!reportedTrim) {
                        reportedTrim = true;
                        Log.i(TAG, "样式 " + items.size() + " 项、预览 " + previews.length
                            + " 张，截掉没有缩略图的 " + (items.size() - previews.length) + " 项");
                    }
                }
            });
        Log.i(TAG, "哈苏预览对齐已挂上");
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
                    if (!((List<?>) result).isEmpty()) {
                        // Stock on a phone that has the styles: nothing to add, but the fact matters
                        // to the XPAN branch below, which only steps aside when this list is real.
                        ordinaryFilled = true;
                        return;
                    }
                    try {
                        Object owner = viewModel(param.thisObject, vm);
                        if (owner == null) return;
                        // Read as the wide-screen, non-XPAN case even when this phone answers
                        // "XPAN" for the pair: that flag is what the ordinary list is built for,
                        // and asking for the XPAN case returns the wide cards that render as one
                        // oversized tile. The list is dropped to the thumbnails below either way.
                        Object filled = read.invoke(owner, true);
                        if (!(filled instanceof List) || ((List<?>) filled).isEmpty()) return;
                        param.setResult(filled);
                        ordinaryFilled = true;
                        if (!reportedFill) {
                            reportedFill = true;
                            Log.i(TAG, "普通哈苏样式 " + ((List<?>) filled).size()
                                + " 项（按 show=true 读），替换掉品牌门禁给的空列表");
                        }
                    } catch (Throwable error) {
                        Log.e(TAG, "普通哈苏样式重算失败", error);
                    }
                    return;
                }
                // The XPAN list and the ordinary one are two halves of one row — stock builds only
                // ever fill one, because ShowHasselXpanStyle decides which. This brand answers
                // "XPAN" for a phone that has no ordinary styles, so with the ordinary list filled
                // both come back and the row mixes a full-width XPAN card in with the normal ones,
                // which reads on screen as one card that is far too large. Once the ordinary list
                // is there, the XPAN half is emptied.
                if (xpan != 0 && asked == xpan && ordinaryFilled && !KEEP_XPAN.exists()
                    && !((List<?>) result).isEmpty()) {
                    int dropped = ((List<?>) result).size();
                    param.setResult(new java.util.ArrayList<Object>());
                    if (!reportedXpan) {
                        reportedXpan = true;
                        Log.i(TAG, "普通哈苏已填充，XPAN 那 " + dropped
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
