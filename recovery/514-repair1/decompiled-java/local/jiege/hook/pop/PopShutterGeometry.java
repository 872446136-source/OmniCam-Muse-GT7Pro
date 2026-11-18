package local.jiege.hook.pop;

import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import defpackage.c3;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class PopShutterGeometry {
    private static final String COLOUR_FROM = "j1";
    private static final String COLOUR_TO = "k1";
    private static final String PAINT = "s3";
    private static final String RING = "ring";
    private static final String[] CENTRED_RECTS = {"b3", "c3", "d3", "e3", "g3", "h3", "l3"};
    private static final String[] LAZY_GRADIENTS = {"t4", "H4", "I4"};

    private PopShutterGeometry() {
    }

    public static void install(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        final List<Field> listResolve = resolve(symbols, CENTRED_RECTS);
        if (listResolve.isEmpty()) {
            Log.i("PopPort", "shutter geometry fields not found, the recentre is left out");
            return;
        }
        final List<Field> listResolve2 = resolve(symbols, LAZY_GRADIENTS);
        final List<Field> listResolve3 = resolve(symbols, new String[]{PAINT});
        final List<Field> listResolve4 = resolve(symbols, new String[]{RING});
        final List<Field> listResolve5 = resolve(symbols, new String[]{COLOUR_FROM, COLOUR_TO});
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.pop.PopShutterGeometry.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                int iIntValue = ((Integer) methodHookParam.args[0]).intValue();
                int iIntValue2 = ((Integer) methodHookParam.args[1]).intValue();
                int iIntValue3 = ((Integer) methodHookParam.args[2]).intValue();
                int iIntValue4 = ((Integer) methodHookParam.args[3]).intValue();
                if (iIntValue3 == 0 || iIntValue4 == 0) {
                    return;
                }
                if (iIntValue == iIntValue3 && iIntValue2 == iIntValue4) {
                    return;
                }
                try {
                    PopShutterGeometry.recentre(methodHookParam.thisObject, (iIntValue - iIntValue3) / 2.0f, (iIntValue2 - iIntValue4) / 2.0f, listResolve, listResolve2, listResolve3, listResolve4, listResolve5);
                } catch (Throwable th) {
                    Log.e("PopPort", "shutter recentre failed", th);
                }
            }
        };
        Class cls = Integer.TYPE;
        XposedHelpers.findAndHookMethod("com.oplus.camera.control.MainShutterButton", classLoader, "onSizeChanged", new Object[]{cls, cls, cls, cls, xC_MethodHook});
        StringBuilder sb = new StringBuilder("shutter recentre installed: ");
        sb.append(listResolve.size());
        sb.append(" rects, ");
        sb.append(listResolve2.size());
        sb.append(" gradients, paint ");
        sb.append(listResolve3.isEmpty() ? "missing" : "found");
        sb.append(", colours ");
        sb.append(listResolve5.size());
        Log.i("PopPort", sb.toString());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void recentre(Object obj, float f, float f2, List<Field> list, List<Field> list2, List<Field> list3, List<Field> list4, List<Field> list5) throws IllegalAccessException {
        Iterator<Field> it = list.iterator();
        while (it.hasNext()) {
            Object obj2 = it.next().get(obj);
            if (obj2 instanceof RectF) {
                ((RectF) obj2).offset(f, f2);
            }
        }
        Iterator<Field> it2 = list2.iterator();
        while (it2.hasNext()) {
            it2.next().set(obj, null);
        }
        if (list3.isEmpty() || list4.isEmpty() || list5.size() < 2) {
            ((View) obj).invalidate();
            return;
        }
        Object obj3 = list4.get(0).get(obj);
        Object obj4 = list3.get(0).get(obj);
        if ((obj3 instanceof RectF) && obj4 != null) {
            RectF rectF = (RectF) obj3;
            XposedHelpers.callMethod(obj4, "setShader", new Object[]{new SweepGradient(rectF.centerX(), rectF.centerY(), list5.get(0).getInt(obj), list5.get(1).getInt(obj))});
        }
        ((View) obj).invalidate();
    }

    private static List<Field> resolve(Symbols symbols, String[] strArr) {
        ArrayList arrayList = new ArrayList(strArr.length);
        for (String str : strArr) {
            if (symbols.has(c3.u("Shutter.", str))) {
                arrayList.add(symbols.field("Shutter." + str));
            } else {
                Log.i("PopPort", "Shutter." + str + " not resolved on this build");
            }
        }
        return arrayList;
    }
}
