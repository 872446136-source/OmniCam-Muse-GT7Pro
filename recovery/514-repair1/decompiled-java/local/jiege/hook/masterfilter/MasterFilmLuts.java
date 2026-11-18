package local.jiege.hook.masterfilter;

import android.content.res.Resources;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import local.jiege.hook.common.ModeName;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.CaptureIds;
import local.jiege.hook.filters.FilterCatalog;
import local.jiege.hook.filters.FilterEntry;
import local.jiege.hook.filters.FilterScope;
import local.jiege.hook.x10.X10FilterPort;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class MasterFilmLuts {
    private static final String TAG = "MasterFilmLuts";

    private MasterFilmLuts() {
    }

    private static void hookTitles(ClassLoader classLoader) {
        try {
            Iterator it = Symbols.get().all("FilterBeans.lists").iterator();
            while (it.hasNext()) {
                XposedBridge.hookMethod((Method) it.next(), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterFilmLuts.1
                    public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Object result = methodHookParam.getResult();
                        if (result instanceof List) {
                            for (Object obj : (List) result) {
                                try {
                                    String str = (String) XposedHelpers.getObjectField(obj, "mType");
                                    String strTitleFor = MasterFilmLuts.titleFor(FilterScope.MASTER, str);
                                    if (strTitleFor == null) {
                                        strTitleFor = MasterFilmLuts.titleFor(FilterScope.NORMAL, str);
                                    }
                                    if (strTitleFor != null) {
                                        XposedHelpers.setObjectField(obj, "mName", strTitleFor);
                                    }
                                } catch (Throwable th) {
                                    XposedBridge.log("MasterFilmLuts: filter bean label failed " + th);
                                }
                            }
                        }
                    }
                });
            }
        } catch (Throwable th) {
            XposedBridge.log("MasterFilmLuts: filter bean naming hook failed " + th);
        }
        try {
            final Symbols symbols = Symbols.get();
            XposedBridge.hookMethod(symbols.method("Card.layout"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterFilmLuts.2
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    try {
                        Object obj = symbols.get("Card.mode", methodHookParam.thisObject);
                        if (obj == null) {
                            return;
                        }
                        String strTitleFor = MasterFilmLuts.titleFor(FilterScope.ofMode(ModeName.of(obj)), (String) symbols.call("ModeUi.filterType", obj, new Object[0]));
                        TextView textView = X10FilterPort.cardTexts(methodHookParam.thisObject)[0];
                        if (strTitleFor == null || textView == null) {
                            return;
                        }
                        textView.setText(strTitleFor);
                        X10FilterPort.fitCardTextWidths(methodHookParam.thisObject);
                    } catch (Throwable th2) {
                        XposedBridge.log("MasterFilmLuts: card label failed " + th2);
                    }
                }
            });
        } catch (Throwable th2) {
            XposedBridge.log("MasterFilmLuts: card hook failed " + th2);
        }
    }

    private static void hookWatermarkNames(ClassLoader classLoader) {
        try {
            final Symbols symbols = Symbols.get();
            XposedBridge.hookMethod(symbols.method("FilterNames.nameAt"), new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterFilmLuts.3
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    String strTitleForType;
                    try {
                        List list = (List) symbols.call("FilterNames.types", methodHookParam.thisObject, new Object[0]);
                        int iIntValue = ((Integer) methodHookParam.args[0]).intValue();
                        if (list == null || iIntValue < 0 || iIntValue >= list.size() || (strTitleForType = MasterFilmLuts.titleForType((String) list.get(iIntValue))) == null) {
                            return;
                        }
                        methodHookParam.setResult(strTitleForType);
                    } catch (Throwable th) {
                        XposedBridge.log("MasterFilmLuts: filter name failed " + th);
                    }
                }
            });
        } catch (Throwable th) {
            XposedBridge.log("MasterFilmLuts: filter name hook failed " + th);
        }
        try {
            for (Method method : XposedHelpers.findClass("com.oplus.camera.common.utils.PresetDataUtil", classLoader).getDeclaredMethods()) {
                if ("applyPresetFilter".equals(method.getName()) && method.getParameterTypes().length == 5) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.masterfilter.MasterFilmLuts.4
                        public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                            try {
                                if (Boolean.TRUE.equals(methodHookParam.getResult())) {
                                    Object[] objArr = methodHookParam.args;
                                    List list = (List) objArr[2];
                                    List list2 = (List) objArr[3];
                                    int iIntValue = ((Integer) objArr[4]).intValue();
                                    String strTitleForType = MasterFilmLuts.titleForType((String) list.get(iIntValue));
                                    if (strTitleForType == null) {
                                        return;
                                    }
                                    String string = ((Resources) methodHookParam.args[1]).getString(((Integer) list2.get(iIntValue)).intValue());
                                    for (Field field : methodHookParam.args[0].getClass().getDeclaredFields()) {
                                        if (field.getType() == String.class) {
                                            field.setAccessible(true);
                                            if (string.equals(field.get(methodHookParam.args[0]))) {
                                                field.set(methodHookParam.args[0], strTitleForType);
                                            } else {
                                                continue;
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable th2) {
                                XposedBridge.log("MasterFilmLuts: preset filter name failed " + th2);
                            }
                        }
                    });
                }
            }
        } catch (Throwable th2) {
            XposedBridge.log("MasterFilmLuts: preset filter name hook failed " + th2);
        }
    }

    public static void install(ClassLoader classLoader) {
        CaptureIds.apply(classLoader);
        hookTitles(classLoader);
        hookWatermarkNames(classLoader);
        XposedBridge.log("MasterFilmLuts: loaded");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String titleFor(FilterScope filterScope, String str) {
        FilterEntry filterEntryFind = FilterCatalog.get(filterScope).find(str);
        if (filterEntryFind == null) {
            return null;
        }
        return filterEntryFind.title;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String titleForType(String str) {
        String strTitleFor = titleFor(FilterScope.MASTER, str);
        return strTitleFor != null ? strTitleFor : titleFor(FilterScope.NORMAL, str);
    }
}
