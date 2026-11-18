package local.jiege.hook.xpan;

import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.fv1;
import defpackage.gv1;
import defpackage.pp1;
import defpackage.q51;
import defpackage.t2;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;
import local.jiege.hook.xpan.XpanViewfinderClean;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class XpanViewfinderClean {
    private static final String COVER = "com.oplus.camera.feature.xpan.view.widget.CoverDrawable";
    private static final float LARGE_FRAME = 0.8f;
    private static final String TAG = "XpanClean";
    private static final ThreadLocal<int[]> READING = new ThreadLocal<>();
    private static final Map<Object, int[]> PADDING = Collections.synchronizedMap(new WeakHashMap());
    private static final Map<Object, Float> RADIUS = Collections.synchronizedMap(new WeakHashMap());
    private static final Map<ViewPropertyAnimator, WeakReference<View>> ANIMATORS = Collections.synchronizedMap(new WeakHashMap());

    private XpanViewfinderClean() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void apply(Method method, Object obj) {
        try {
            method.invoke(obj, null);
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "mask apply", e);
        }
    }

    private static Field[] fields(Class<?> cls, final Class<?> cls2) {
        return (Field[]) Arrays.stream(cls.getDeclaredFields()).filter(new Predicate() { // from class: ev1
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return XpanViewfinderClean.lambda$fields$2(cls2, (Field) obj);
            }
        }).peek(new fv1()).toArray(new gv1());
    }

    public static void installCamera(ClassLoader classLoader) {
        final boolean z = Settings.get("xpan_clean_small_mask");
        final boolean z2 = Settings.get("xpan_clean_large_mask");
        if (z || z2) {
            Log.guard(TAG, "XPAN mask", new Log.Installer() { // from class: hv1
                @Override // local.jiege.hook.common.Log.Installer
                public final void install() {
                    XpanViewfinderClean.installMask(z, z2);
                }
            });
        }
        if (Settings.get("xpan_clean_animation")) {
            Log.guard(TAG, "XPAN developing animation", new pp1(classLoader, (byte) 4));
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void installCapture(ClassLoader classLoader) {
        final Class clsFindClass = XposedHelpers.findClass(COVER, classLoader);
        Class cls = Long.TYPE;
        final Field[] fieldArrFields = fields(clsFindClass, cls);
        if (fieldArrFields.length != 2) {
            throw new IllegalStateException("CoverDrawable has " + fieldArrFields.length + " long fields");
        }
        Method method = null;
        for (Method method2 : clsFindClass.getDeclaredMethods()) {
            if (method2.getParameterCount() == 0 && method2.getReturnType() == Void.TYPE && !Modifier.isStatic(method2.getModifiers())) {
                if (method != null) {
                    t2.j("CoverDrawable has several frame methods");
                    return;
                }
                method = method2;
            }
        }
        if (method == null) {
            t2.j("CoverDrawable frame method not found");
            return;
        }
        XposedBridge.hookAllConstructors(clsFindClass, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanViewfinderClean.4
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                for (Field field : XpanViewfinderClean.k(clsFindClass)) {
                    Paint paint = (Paint) field.get(methodHookParam.thisObject);
                    if (paint != null && paint.getColorFilter() != null) {
                        paint.setColorFilter(null);
                    }
                }
            }
        });
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanViewfinderClean.5
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException {
                long j = fieldArrFields[0].getLong(methodHookParam.thisObject);
                long j2 = fieldArrFields[1].getLong(methodHookParam.thisObject);
                if (j <= 0 || j2 <= 0) {
                    return;
                }
                Field[] fieldArr = fieldArrFields;
                Field field = j > j2 ? fieldArr[0] : fieldArr[1];
                if (SystemClock.elapsedRealtime() - 1 >= Math.min(j, j2)) {
                    field.setLong(methodHookParam.thisObject, 1L);
                }
            }
        });
        XposedHelpers.findAndHookMethod(View.class, "animate", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanViewfinderClean.6
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = methodHookParam.thisObject;
                if (obj instanceof ImageView) {
                    if (clsFindClass.isInstance(((ImageView) obj).getDrawable())) {
                        XpanViewfinderClean.ANIMATORS.put((ViewPropertyAnimator) methodHookParam.getResult(), new WeakReference((View) methodHookParam.thisObject));
                    } else {
                        XpanViewfinderClean.ANIMATORS.remove(methodHookParam.getResult());
                    }
                }
            }
        }});
        XposedHelpers.findAndHookMethod(ViewPropertyAnimator.class, "setDuration", new Object[]{cls, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanViewfinderClean.7
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                WeakReference weakReference = (WeakReference) XpanViewfinderClean.ANIMATORS.remove(methodHookParam.thisObject);
                View view = weakReference != null ? (View) weakReference.get() : null;
                if (view == null) {
                    return;
                }
                view.setVisibility(4);
                methodHookParam.args[0] = 0L;
            }
        }});
        Log.i(TAG, "developing animation off");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void installMask(final boolean z, final boolean z2) {
        Class<?> cls = Symbols.get().cls("XpanMaskDrawer");
        final Field fieldOnly = only(cls, Float.TYPE, true);
        final Field fieldOnly2 = only(cls, Rect.class, false);
        Method[] declaredMethods = cls.getDeclaredMethods();
        int length = declaredMethods.length;
        Method method = null;
        int i = 0;
        Method method2 = null;
        while (true) {
            Class<?> cls2 = Void.TYPE;
            if (i >= length) {
                if (method2 == null) {
                    t2.j("mask padding reader not found");
                    return;
                }
                Class<?> cls3 = method2.getParameterTypes()[1];
                Method method3 = null;
                for (Method method4 : cls.getDeclaredMethods()) {
                    if (method4.getParameterCount() == 1 && method4.getParameterTypes()[0] == cls3 && method4.getReturnType() == cls2) {
                        if (method3 != null) {
                            t2.j("mask has several frame layouts");
                            return;
                        }
                        method3 = method4;
                    }
                }
                if (method3 == null) {
                    t2.j("mask frame layout not found");
                    return;
                }
                Class cls4 = Integer.TYPE;
                final Field[] fieldArrFields = fields(cls, cls4);
                Method method5 = Symbols.get().method("XpanMaskDrawer.releaseTexture");
                for (Method method6 : cls.getDeclaredMethods()) {
                    if (method6.getParameterCount() == 0 && method6.getReturnType() == cls2 && !method6.equals(method5) && !Modifier.isStatic(method6.getModifiers())) {
                        if (method != null) {
                            t2.j("mask has several apply methods");
                            return;
                        }
                        method = method6;
                    }
                }
                if (method == null) {
                    t2.j("mask apply method not found");
                    return;
                }
                method.setAccessible(true);
                XposedBridge.hookMethod(method2, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanViewfinderClean.1
                    public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        int[] iArr = (int[]) XpanViewfinderClean.READING.get();
                        XpanViewfinderClean.READING.remove();
                        if (iArr == null || iArr[0] < 0 || iArr[1] < 0) {
                            return;
                        }
                        XpanViewfinderClean.PADDING.put(methodHookParam.thisObject, iArr);
                    }

                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        XpanViewfinderClean.READING.set(new int[]{-1, -1});
                    }
                });
                XposedHelpers.findAndHookMethod(Resources.class, "getDimensionPixelSize", new Object[]{cls4, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanViewfinderClean.2
                    public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        int[] iArr = (int[]) XpanViewfinderClean.READING.get();
                        if (iArr == null) {
                            return;
                        }
                        try {
                            String resourceEntryName = ((Resources) methodHookParam.thisObject).getResourceEntryName(((Integer) methodHookParam.args[0]).intValue());
                            if (resourceEntryName.startsWith("xpan_mask_view_horizontal_inner_padding")) {
                                iArr[0] = ((Integer) methodHookParam.getResult()).intValue();
                            } else if (resourceEntryName.startsWith("xpan_mask_view_vertical_inner_padding")) {
                                iArr[1] = ((Integer) methodHookParam.getResult()).intValue();
                            }
                        } catch (Resources.NotFoundException unused) {
                        }
                    }
                }});
                final Method method7 = method;
                XposedBridge.hookMethod(method3, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanViewfinderClean.3
                    public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException {
                        Object obj = methodHookParam.thisObject;
                        Rect rect = (Rect) fieldOnly2.get(obj);
                        int[] iArr = (int[]) XpanViewfinderClean.PADDING.get(obj);
                        if (rect == null || rect.isEmpty() || iArr == null) {
                            return;
                        }
                        Float f = (Float) XpanViewfinderClean.RADIUS.get(obj);
                        if (f == null) {
                            Map map = XpanViewfinderClean.RADIUS;
                            Float fValueOf = Float.valueOf(fieldOnly.getFloat(obj));
                            map.put(obj, fValueOf);
                            f = fValueOf;
                        }
                        DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
                        boolean z3 = ((float) Math.min(rect.width(), rect.height())) >= ((float) Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels)) * XpanViewfinderClean.LARGE_FRAME;
                        float f2 = fieldOnly.getFloat(obj);
                        if (!z3 ? z : z2) {
                            fieldOnly.setFloat(obj, f.floatValue());
                            if (f2 != f.floatValue()) {
                                XpanViewfinderClean.apply(method7, obj);
                                return;
                            }
                            return;
                        }
                        int[] iArr2 = {rect.left + iArr[0], rect.top + iArr[1], rect.width() - (iArr[0] * 2), rect.height() - (iArr[1] * 2)};
                        int[] iArr3 = {rect.left, rect.top, rect.width(), rect.height()};
                        Field[] fieldArr = new Field[4];
                        for (int i2 = 0; i2 < 4; i2++) {
                            for (Field field : fieldArrFields) {
                                if (field.getInt(obj) == iArr2[i2]) {
                                    if (fieldArr[i2] != null) {
                                        return;
                                    } else {
                                        fieldArr[i2] = field;
                                    }
                                }
                            }
                            if (fieldArr[i2] == null) {
                                return;
                            }
                        }
                        for (int i3 = 0; i3 < 4; i3++) {
                            fieldArr[i3].setInt(obj, iArr3[i3]);
                        }
                        fieldOnly.setFloat(obj, 0.0f);
                        if (f2 != 0.0f) {
                            XpanViewfinderClean.apply(method7, obj);
                        }
                    }
                });
                StringBuilder sb = new StringBuilder("mask off for");
                String str = PresetSelection.STANDARD_KEY;
                sb.append(z ? " small" : PresetSelection.STANDARD_KEY);
                if (z2) {
                    str = " large";
                }
                sb.append(str);
                sb.append(" viewfinder");
                Log.i(TAG, sb.toString());
                return;
            }
            Method method8 = declaredMethods[i];
            Class<?>[] parameterTypes = method8.getParameterTypes();
            if (parameterTypes.length == 2 && parameterTypes[0] == Resources.class && !parameterTypes[1].isPrimitive() && method8.getReturnType() == cls2) {
                if (method2 != null) {
                    t2.j("mask has several padding readers");
                    return;
                }
                method2 = method8;
            }
            i++;
        }
    }

    public static /* bridge */ /* synthetic */ Field[] k(Class cls) {
        return fields(cls, Paint.class);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ boolean lambda$fields$2(Class cls, Field field) {
        return field.getType() == cls && !Modifier.isStatic(field.getModifiers());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ Field[] lambda$fields$4(int i) {
        return new Field[i];
    }

    private static Field only(Class<?> cls, Class<?> cls2, boolean z) {
        Field field = null;
        for (Field field2 : fields(cls, cls2)) {
            if (Modifier.isFinal(field2.getModifiers()) == z) {
                if (field != null) {
                    q51.f(cls.getName(), " has several ", cls2.getSimpleName(), " fields");
                    return null;
                }
                field = field2;
            }
        }
        if (field != null) {
            return field;
        }
        q51.f(cls.getName(), " has no ", cls2.getSimpleName(), " field");
        return null;
    }
}
