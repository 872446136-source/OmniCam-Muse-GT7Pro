package local.jiege.hook.watermark;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;

/**
 * Gallery photo editor: the 哈苏 (HASSELBLAD) group of the master-watermark list.
 *
 * <p>486 report: the watermark page shows the HASSELBLAD heading and then nothing under it — "只显示
 * hasselblad 没有水印可以选择". The heading is drawn per series from whatever styles the list
 * carries, so an empty group means the list had no {@code hassel_style_*} entry in it.
 *
 * <p>That list is built by
 * {@code WatermarkMasterStyle$Companion.getBuiltInHasselStyles(boolean, boolean)}. The class is not
 * obfuscated, so it is reached by name and no symbol is needed. Its shape is:
 *
 * <pre>
 *   list = []
 *   if (masterMode) list += getHasselMasterModeStyles()      // hassel_style_master_mode_1
 *   if (xpan)       list += getHasselXpanStyles()            // hassel_style_xpan_1
 *   for i in 1..count: list += Pair("hassel_style_" + i, getStyleFileNameById(...))
 *   for i in 1..count: list += Pair("hassel_text_style_" + i, ...)
 * </pre>
 *
 * <p>{@code count} is not a constant: it is read through {@code k6q.a(int, String)}, a config /
 * resource lookup. On this realme build it answers 0, so the two loops add nothing and the group is
 * empty — while the artwork is right there in the APK. The realme gallery 17.10.41 ships every file
 * the group needs, checked by name against the installed APK:
 *
 * <pre>
 *   assets/watermark_master_styles/hassel_style_1.json … _4.json
 *   assets/watermark_master_styles/hassel_text_style_1.json, _2.json
 *   assets/watermark_master_styles/hassel_style_master_mode_1.json
 *   assets/watermark_master_styles/hassel_style_xpan_1.json
 * </pre>
 *
 * <p>So the hook does two things, both narrow: it serves the call with both flags true (so the
 * master-mode and XPAN styles are not dropped either), and when the result carries no
 * {@code hassel_style_*} entry it appends the seven ids above, each paired with the file name the
 * companion itself computes. Nothing is invented: the ids, the file names and the artwork all come
 * from this APK. A list that already has the group is passed through untouched.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_hasselwm_off}.
 *
 * <p><b>487: the hook above never ran.</b> The 486 report has {@code hassel style list hooked} and
 * nothing after it — the watermark page was open at 01:20:43 and {@code getBuiltInHasselStyles} was
 * not called once in that whole session, so this build's list is assembled somewhere else. Rather
 * than guess again, this version also logs what the section really builds: {@code getSeries(String)}
 * is the companion method the grouping calls once per style id, and it is not obfuscated, so a
 * single line list of the ids that pass through it says outright whether {@code hassel_style_*} is
 * in the list at all. The fill stays for the case where the method is on the path.
 */
public final class HasselWatermark {
    private static final String TAG = "HasselWatermark";
    private static final String OFF = "/data/adb/omnicam_hasselwm_off";
    private static final String COMPANION =
        "com.oplus.gallery.framework.abilities.watermark.masterstyle.WatermarkMasterStyle$Companion";

    /** The built-in group, in the order the companion would have produced it. */
    private static final String[] FRAMES = {
        "hassel_style_1", "hassel_style_2", "hassel_style_3", "hassel_style_4",
    };
    private static final String[] TEXTS = {
        "hassel_text_style_1", "hassel_text_style_2",
    };
    private static final String MASTER_MODE = "hassel_style_master_mode_1";
    private static final String XPAN = "hassel_style_xpan_1";

    /** One line per process: this method runs for every style list the editor rebuilds. */
    private static boolean reported;

    private HasselWatermark() {}

    public static void installGallery(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "off (" + OFF + ")");
            return;
        }
        Log.guard(TAG, "hassel style list", () -> hook(classLoader));
    }

    private static void hook(ClassLoader classLoader) {
        final Class<?> companion = XposedHelpers.findClass(COMPANION, classLoader);
        XposedHelpers.findAndHookMethod(companion, "getBuiltInHasselStyles", boolean.class, boolean.class,
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    // Both flags gate a subset (master mode, XPAN); neither is a brand check.
                    param.args[0] = Boolean.TRUE;
                    param.args[1] = Boolean.TRUE;
                }

                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        fill(companion, classLoader, param);
                    } catch (Throwable error) {
                        Log.e(TAG, "hassel style list not filled", error);
                    }
                }
            });
        Log.i(TAG, "hassel style list hooked");
        probeSeries(companion);
    }

    /**
     * Read-only: the style ids the grouping actually walks, logged once per sixteen ids. If
     * {@code hassel_style_*} never shows up here the list is built elsewhere and the next report
     * says where to look; if it does show up, the group exists and the empty section is a rendering
     * problem instead. A hassel id is logged the moment it appears, whenever that is.
     */
    private static void probeSeries(Class<?> companion) {
        try {
            XposedHelpers.findAndHookMethod(companion, "getSeries", String.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Object id = param.args[0];
                    if (!(id instanceof String)) return;
                    String style = (String) id;
                    boolean hassel = style.startsWith("hassel");
                    int count;
                    synchronized (seen) {
                        seen.add(style);
                        count = seen.size();
                    }
                    if (hassel || count % 16 == 0) Log.i(TAG, "styles seen (" + count + "): " + join());
                }
            });
            Log.i(TAG, "style series probe armed");
        } catch (Throwable error) {
            Log.i(TAG, "style series probe unavailable: " + error);
        }
    }

    /** The ids the grouping has walked, in the order they first appeared. */
    private static final java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();

    private static String join() {
        synchronized (seen) {
            StringBuilder text = new StringBuilder();
            for (String id : seen) {
                if (text.length() > 0) text.append(',');
                text.append(id);
            }
            return text.toString();
        }
    }

    /**
     * Appends the built-in group when the companion produced none. A non-empty group, or a result
     * that is not a list at all, is left exactly as it came out.
     */
    private static void fill(Class<?> companion, ClassLoader classLoader, XC_MethodHook.MethodHookParam param) throws Throwable {
        Object value = param.getResult();
        if (!(value instanceof List)) {
            if (!reported) {
                reported = true;
                Log.i(TAG, "no style list returned (" + value + "), nothing to fill");
            }
            return;
        }
        List<?> original = (List<?>) value;
        for (Object item : original) {
            Object first = first(item);
            if (first instanceof String && ((String) first).startsWith("hassel_style_")) {
                if (!reported) {
                    reported = true;
                    Log.i(TAG, "built-in hassel group present (" + original.size() + " styles), left alone");
                }
                return;
            }
        }
        ArrayList<Object> filled = new ArrayList<>(original.size() + FRAMES.length + TEXTS.length + 2);
        filled.addAll(original);
        int added = 0;
        added += add(companion, classLoader, filled, MASTER_MODE);
        added += add(companion, classLoader, filled, XPAN);
        for (String id : FRAMES) added += add(companion, classLoader, filled, id);
        for (String id : TEXTS) added += add(companion, classLoader, filled, id);
        if (added == 0) {
            Log.i(TAG, "the companion knows no hassel style file names, group stays empty");
            return;
        }
        param.setResult(filled);
        if (!reported) {
            reported = true;
            Log.i(TAG, "hassel group filled: " + added + " styles added to " + original.size());
        }
    }

    /** Pair(styleId, fileName) the way the companion builds it; skipped when it has no file name. */
    private static int add(Class<?> companion, ClassLoader classLoader, List<Object> into, String id) {
        try {
            Object fileName = XposedHelpers.callStaticMethod(companion, "getStyleFileNameById", id);
            if (!(fileName instanceof String) || ((String) fileName).isEmpty()) return 0;
            into.add(XposedHelpers.newInstance(XposedHelpers.findClass("kotlin.Pair", classLoader), id, fileName));
            return 1;
        } catch (Throwable error) {
            Log.e(TAG, "style " + id + " not added", error);
            return 0;
        }
    }

    /** {@code Pair.getFirst()} without linking against kotlin.Pair at compile time. */
    private static Object first(Object item) {
        if (item == null) return null;
        try {
            return XposedHelpers.callMethod(item, "getFirst");
        } catch (Throwable ignored) {
            return null;
        }
    }
}
