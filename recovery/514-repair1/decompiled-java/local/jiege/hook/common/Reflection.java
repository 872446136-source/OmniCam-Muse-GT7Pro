package local.jiege.hook.common;

import defpackage.c3;
import defpackage.t2;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class Reflection {
    private static final String DATA_KEY = "com.oplus.camera.data.DataKey";
    private static final Map<Class<?>, Field[]> stringFields = Collections.synchronizedMap(new IdentityHashMap());

    private Reflection() {
    }

    public static Object findDataKey(Class<?> cls, String str) throws IllegalAccessException {
        for (Field field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && DATA_KEY.equals(field.getType().getName())) {
                field.setAccessible(true);
                Object obj = field.get(null);
                if (obj != null && hasStringField(obj, str)) {
                    return obj;
                }
            }
        }
        t2.j(c3.u("DataKey not found: ", str));
        return null;
    }

    public static Object findFeatureKey(Class<?> cls, Class<?> cls2, String str) throws IllegalAccessException {
        for (Field field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == cls2) {
                field.setAccessible(true);
                Object obj = field.get(null);
                if (obj != null && hasStringField(obj, str)) {
                    return obj;
                }
            }
        }
        t2.j(c3.u("feature key not found: ", str));
        return null;
    }

    public static Object findFieldByType(Object obj, String str) {
        if (obj == null) {
            return null;
        }
        for (Class<?> superclass = obj.getClass(); superclass != null; superclass = superclass.getSuperclass()) {
            for (Field field : superclass.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && str.equals(field.getType().getName())) {
                    field.setAccessible(true);
                    Object obj2 = field.get(obj);
                    if (obj2 != null) {
                        return obj2;
                    }
                }
            }
        }
        return null;
    }

    public static boolean hasStringField(Object obj, String str) {
        for (Field field : stringFields(obj.getClass())) {
            if (str.equals(field.get(obj))) {
                return true;
            }
        }
        return false;
    }

    private static Field[] stringFields(Class<?> cls) {
        Field[] fieldArr = stringFields.get(cls);
        if (fieldArr != null) {
            return fieldArr;
        }
        ArrayList arrayList = new ArrayList();
        Class<?> superclass = cls;
        while (true) {
            if (superclass == null) {
                Field[] fieldArr2 = (Field[]) arrayList.toArray(new Field[0]);
                stringFields.put(cls, fieldArr2);
                return fieldArr2;
            }
            for (Field field : superclass.getDeclaredFields()) {
                if (field.getType() == String.class && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    arrayList.add(field);
                }
            }
            superclass = superclass.getSuperclass();
        }
    }
}
