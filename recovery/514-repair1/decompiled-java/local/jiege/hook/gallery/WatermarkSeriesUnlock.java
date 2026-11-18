package local.jiege.hook.gallery;

import android.os.Build;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.id;
import defpackage.kg0;
import defpackage.q51;
import defpackage.t2;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class WatermarkSeriesUnlock {
    private static final String GR_INFO = "com.oplus.gallery.foundation.codec.extend.GRInfo";
    private static final String TAG = "WatermarkSeriesUnlock";

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Device {
        final Constructor<?> constructor;
        final Field name;
        final Field subType;

        private Device(Constructor<?> constructor, Field field, Field field2) {
            this.constructor = constructor;
            this.name = field;
            this.subType = field2;
        }

        public static Device of(Class<?> cls) {
            try {
                Class cls2 = Integer.TYPE;
                Class cls3 = Boolean.TYPE;
                Constructor<?> declaredConstructor = cls.getDeclaredConstructor(String.class, cls2, cls3, cls3);
                declaredConstructor.setAccessible(true);
                return new Device(declaredConstructor, WatermarkSeriesUnlock.fieldOfType(cls, String.class), WatermarkSeriesUnlock.fieldOfType(cls, cls2));
            } catch (Throwable unused) {
                return null;
            }
        }

        public Object marked(Object obj) {
            String strMarketName = obj != null ? (String) this.name.get(obj) : null;
            if (WatermarkSeriesUnlock.blank(strMarketName)) {
                strMarketName = WatermarkSeriesUnlock.marketName();
            }
            int i = obj != null ? this.subType.getInt(obj) : 0;
            Constructor<?> constructor = this.constructor;
            Integer numValueOf = Integer.valueOf(i);
            Boolean bool = Boolean.TRUE;
            return constructor.newInstance(strMarketName, numValueOf, bool, bool);
        }
    }

    private WatermarkSeriesUnlock() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean blank(Object obj) {
        return !(obj instanceof String) || ((String) obj).trim().isEmpty();
    }

    private static Constructor<?> constructor(Class<?> cls, Class<?> cls2) {
        for (Constructor<?> constructor : cls.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length > 0 && parameterTypes[parameterTypes.length - 1] == cls2 && parameterTypes.length == instanceFields(cls)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        t2.j("no full constructor in ".concat(cls.getName()));
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Field fieldOfType(Class<?> cls, Class<?> cls2) {
        for (Field field : cls.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == cls2) {
                field.setAccessible(true);
                return field;
            }
        }
        q51.f("no ", cls2.getName(), " field in ", cls.getName());
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookWatermarkContent(Symbols symbols) {
        int i = 0;
        for (Constructor<?> constructor : symbols.cls("WatermarkContent").getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length == 7 && parameterTypes[0] == String.class && parameterTypes[1] == String.class) {
                XposedBridge.hookMethod(constructor, new XC_MethodHook() { // from class: local.jiege.hook.gallery.WatermarkSeriesUnlock.2
                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        if (WatermarkSeriesUnlock.blank(methodHookParam.args[0])) {
                            methodHookParam.args[0] = Build.MANUFACTURER;
                        }
                        if (WatermarkSeriesUnlock.blank(methodHookParam.args[1])) {
                            methodHookParam.args[1] = WatermarkSeriesUnlock.marketName();
                        }
                    }
                });
                i++;
            }
        }
        if (i != 0) {
            return;
        }
        t2.j("no WatermarkContent constructor");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookWatermarkInfo(Symbols symbols, ClassLoader classLoader) {
        final Device deviceOf;
        final Class<?> cls = symbols.cls("RealmeWatermarkInfo");
        final Class clsFindClass = XposedHelpers.findClass(GR_INFO, classLoader);
        final Constructor<?> constructor = constructor(cls, clsFindClass);
        int i = 0;
        for (Constructor<?> constructor2 : symbols.cls("WatermarkInfo").getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor2.getParameterTypes();
            final int iIndexOf = indexOf(parameterTypes, cls);
            if (parameterTypes.length != 0 && iIndexOf >= 0 && (deviceOf = Device.of(parameterTypes[0])) != null) {
                XposedBridge.hookMethod(constructor2, new XC_MethodHook() { // from class: local.jiege.hook.gallery.WatermarkSeriesUnlock.1
                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Object[] objArr = methodHookParam.args;
                        objArr[0] = deviceOf.marked(objArr[0]);
                        Object[] objArr2 = methodHookParam.args;
                        int i2 = iIndexOf;
                        objArr2[i2] = WatermarkSeriesUnlock.withGr(objArr2[i2], cls, constructor, clsFindClass);
                    }
                });
                i++;
            }
        }
        if (i != 0) {
            return;
        }
        t2.j("no WatermarkInfo constructor with device and realme blocks");
    }

    private static int indexOf(Class<?>[] clsArr, Class<?> cls) {
        for (int i = 0; i < clsArr.length; i++) {
            if (clsArr[i] == cls) {
                return i;
            }
        }
        return -1;
    }

    public static void installGallery(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        Log.guard(TAG, "watermark info marks", new kg0(symbols, classLoader, (byte) 5));
        Log.guard(TAG, "watermark model text", new id(symbols, (byte) 23));
        Log.guard(TAG, "watermark tabs selectable", new id(symbols, (byte) 24));
    }

    private static int instanceFields(Class<?> cls) {
        int i = 0;
        for (Field field : cls.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                i++;
            }
        }
        return i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installGallery$2(Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("MasterVM.photoSupports"), XC_MethodReplacement.returnConstant(Boolean.TRUE));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String marketName() {
        try {
            Object objCallStaticMethod = XposedHelpers.callStaticMethod(Class.forName("android.os.SystemProperties"), "get", new Object[]{"ro.vendor.oplus.market.name", PresetSelection.STANDARD_KEY});
            if ((objCallStaticMethod instanceof String) && !((String) objCallStaticMethod).isEmpty()) {
                return (String) objCallStaticMethod;
            }
        } catch (Throwable unused) {
        }
        return Build.MODEL;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Object withGr(Object obj, Class<?> cls, Constructor<?> constructor, Class<?> cls2) {
        int length = constructor.getParameterTypes().length;
        Object[] objArr = new Object[length];
        if (obj != null) {
            for (int i = 0; i < length; i++) {
                objArr[i] = fieldOfType(cls, constructor.getParameterTypes()[i]).get(obj);
            }
        }
        int i2 = length - 1;
        if (objArr[i2] != null) {
            return obj;
        }
        objArr[i2] = cls2.getDeclaredConstructor(null).newInstance(null);
        return constructor.newInstance(objArr);
    }
}
