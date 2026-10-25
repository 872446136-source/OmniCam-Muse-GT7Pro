package local.jiege.hook.pop;

import android.graphics.RectF;
import android.graphics.SweepGradient;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/**
 * POP port: keeps the shutter's drawing geometry centred when the shutter changes size.
 *
 * MainShutterButton creates its ring and disc rectangles once (called from onDraw), centred on the
 * view size at that first draw, and onSizeChanged never updates them. Stock X9U modes all use the
 * same shutter size, so this never shows. The ported POP mode uses a larger shutter (288 px vs
 * 264 px); once the shutter has been drawn at one size, the other size draws the inner disc
 * 12 px off centre on both axes (measured +11.5, +11.5) until the camera restarts.
 *
 * On a real size change the centred rectangles are moved to the new centre, and the gradients
 * built from their coordinates are dropped or rebuilt, so the next draw matches the new size.
 *
 * <p>475 addressed the fields by their literal names, which was wrong twice over: the names shift
 * with every camera build (7.013.40 moved b3 to c3 and every other one along with it), and the
 * hook then edited a field the shutter never reads. The fields are now taken from the resolved
 * {@code Shutter.*} symbols and used as {@link Field} objects, so the name only has to be right in
 * assets/symbols.json — where the mapping is recorded — and not here as well.
 */
final class PopShutterGeometry {
    /** Rectangles built around the view centre with size-independent radii. */
    private static final String[] CENTRED_RECTS = {"b3", "c3", "d3", "e3", "g3", "h3", "l3"};
    /** Gradients created lazily (null-checked) from those rectangles. */
    private static final String[] LAZY_GRADIENTS = {"t4", "H4", "I4"};
    /** The ring's sweep-gradient paint, and the two colours that paint is rebuilt with. */
    private static final String PAINT = "s3";
    private static final String RING = "ring";
    private static final String COLOUR_FROM = "j1";
    private static final String COLOUR_TO = "k1";

    private PopShutterGeometry() {}

    static void install(ClassLoader classLoader) {
        final Symbols symbols = Symbols.get();
        final List<Field> rects = resolve(symbols, CENTRED_RECTS);
        if (rects.isEmpty()) {
            // Without the rectangles there is nothing to move, and the rest is meaningless.
            Log.i("PopPort", "shutter geometry fields not found, the recentre is left out");
            return;
        }
        final List<Field> gradients = resolve(symbols, LAZY_GRADIENTS);
        final List<Field> paint = resolve(symbols, new String[] {PAINT});
        final List<Field> ring = resolve(symbols, new String[] {RING});
        final List<Field> colours = resolve(symbols, new String[] {COLOUR_FROM, COLOUR_TO});
        XposedHelpers.findAndHookMethod("com.oplus.camera.control.MainShutterButton", classLoader,
            "onSizeChanged", Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE,
            new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    int width = (Integer) param.args[0], height = (Integer) param.args[1];
                    int oldWidth = (Integer) param.args[2], oldHeight = (Integer) param.args[3];
                    if (oldWidth == 0 || oldHeight == 0
                        || (width == oldWidth && height == oldHeight)) return;
                    try {
                        recentre(param.thisObject, (width - oldWidth) / 2f,
                            (height - oldHeight) / 2f, rects, gradients, paint, ring, colours);
                    } catch (Throwable error) {
                        Log.e("PopPort", "shutter recentre failed", error);
                    }
                }
            });
        Log.i("PopPort", "shutter recentre installed: " + rects.size() + " rects, "
            + gradients.size() + " gradients, paint " + (paint.isEmpty() ? "missing" : "found")
            + ", colours " + colours.size());
    }

    /** Resolves the symbols that exist; a build without one loses that part, not the whole hook. */
    private static List<Field> resolve(Symbols symbols, String[] names) {
        List<Field> found = new ArrayList<>(names.length);
        for (String name : names) {
            if (!symbols.has("Shutter." + name)) {
                Log.i("PopPort", "Shutter." + name + " not resolved on this build");
                continue;
            }
            found.add(symbols.field("Shutter." + name));
        }
        return found;
    }

    private static void recentre(Object button, float dx, float dy, List<Field> rects,
            List<Field> gradients, List<Field> paint, List<Field> ring, List<Field> colours)
            throws IllegalAccessException {
        for (Field rect : rects) {
            Object value = rect.get(button);
            if (value instanceof RectF) ((RectF) value).offset(dx, dy);
        }
        for (Field gradient : gradients) gradient.set(button, null);
        // The ring's sweep gradient is only built together with its rectangle; rebuild it at the
        // new centre so the next draw does not reuse the old one.
        if (paint.isEmpty() || ring.isEmpty() || colours.size() < 2) {
            ((android.view.View) button).invalidate();
            return;
        }
        Object disc = ring.get(0).get(button);
        Object brush = paint.get(0).get(button);
        if (disc instanceof RectF && brush != null) {
            XposedHelpers.callMethod(brush, "setShader", new SweepGradient(
                ((RectF) disc).centerX(), ((RectF) disc).centerY(),
                colours.get(0).getInt(button), colours.get(1).getInt(button)));
        }
        ((android.view.View) button).invalidate();
    }
}
