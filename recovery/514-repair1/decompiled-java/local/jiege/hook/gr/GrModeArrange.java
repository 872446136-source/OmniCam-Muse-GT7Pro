package local.jiege.hook.gr;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class GrModeArrange {
    private static final int GR_MODE_ID = 39;

    private GrModeArrange() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int color(Context context, String str) {
        int identifier = context.getResources().getIdentifier(str, "color", context.getPackageName());
        if (identifier == 0) {
            return 0;
        }
        return context.getColor(identifier);
    }

    public static void install(Symbols symbols) {
        final Class<?> cls = symbols.cls("ModeItem");
        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() { // from class: local.jiege.hook.gr.GrModeArrange.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException {
                Object[] objArr = methodHookParam.args;
                if (objArr.length >= 2) {
                    if ((objArr[0] instanceof Context) && Integer.valueOf(GrModeArrange.GR_MODE_ID).equals(methodHookParam.args[1])) {
                        Context context = (Context) methodHookParam.args[0];
                        int iColor = GrModeArrange.color(context, "color_white_with_10_percent_transparency");
                        int iColor2 = GrModeArrange.color(context, "color_white_with_full_percent_transparency");
                        for (Field field : cls.getDeclaredFields()) {
                            if (!Modifier.isStatic(field.getModifiers())) {
                                field.setAccessible(true);
                                if (field.getType() == Boolean.TYPE && Modifier.isFinal(field.getModifiers()) && !field.getBoolean(methodHookParam.thisObject)) {
                                    field.setBoolean(methodHookParam.thisObject, true);
                                } else if (field.getType() == Integer.TYPE && !Modifier.isFinal(field.getModifiers()) && iColor != iColor2 && field.getInt(methodHookParam.thisObject) == iColor) {
                                    field.setInt(methodHookParam.thisObject, iColor2);
                                }
                            }
                        }
                    }
                }
            }
        });
    }
}
