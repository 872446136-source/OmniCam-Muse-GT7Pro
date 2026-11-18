package local.jiege.hook.xpan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.bv1;
import defpackage.t2;
import defpackage.zu1;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Reflection;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.MasterLayout;
import local.jiege.hook.xpan.XpanFilterButton;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class XpanFilterButton {
    private static AlertDialog dialog;
    private static volatile boolean xpan;
    private static final Map<Activity, WeakReference<Object>> presenters = Collections.synchronizedMap(new WeakHashMap());
    private static final ThreadLocal<Boolean> configuring = ThreadLocal.withInitial(new zu1());

    private XpanFilterButton() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Activity activity(Context context) {
        int i = 0;
        while (i < 12 && (context instanceof ContextWrapper)) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            Context baseContext = ((ContextWrapper) context).getBaseContext();
            if (baseContext == context) {
                return null;
            }
            i++;
            context = baseContext;
        }
        return null;
    }

    public static void installCamera(final ClassLoader classLoader) {
        Method[] methodArr;
        if (!"RMX5010".equals(Build.MODEL)) {
            return;
        }
        Symbols symbols = Symbols.get();
        final Class<?> cls = symbols.cls("XpanPresenter");
        Class<?>[] interfaces = cls.getInterfaces();
        int length = interfaces.length;
        int i = 0;
        final Method method = null;
        final Method method2 = null;
        while (true) {
            Class<?> cls2 = Void.TYPE;
            char c = 1;
            if (i < length) {
                for (Method method3 : interfaces[i].getDeclaredMethods()) {
                    Class<?>[] parameterTypes = method3.getParameterTypes();
                    if (method3.getReturnType() == cls2 && parameterTypes.length == 1 && parameterTypes[0] == String.class) {
                        if (method != null) {
                            t2.o("Ambiguous XPAN filter setter");
                            return;
                        }
                        method = method3;
                    }
                    if (method3.getReturnType() == String.class && parameterTypes.length == 0) {
                        if (method2 != null) {
                            t2.o("Ambiguous XPAN filter getter");
                            return;
                        }
                        method2 = method3;
                    }
                }
                i++;
            } else {
                if (method == null || method2 == null) {
                    t2.o("XPAN filter callbacks");
                    return;
                }
                method.setAccessible(true);
                method2.setAccessible(true);
                final Class clsFindClass = XposedHelpers.findClass("com.oplus.camera.filter.FilterGroupManager", classLoader);
                Method[] declaredMethods = symbols.cls("FeatureManager").getDeclaredMethods();
                int length2 = declaredMethods.length;
                int i2 = 0;
                Method method4 = null;
                while (true) {
                    Class<?> cls3 = Integer.TYPE;
                    if (i2 >= length2) {
                        if (method4 == null) {
                            t2.o("XPAN feature configuration");
                            return;
                        }
                        int i3 = 10000;
                        XposedBridge.hookMethod(method4, new XC_MethodHook(i3) { // from class: local.jiege.hook.xpan.XpanFilterButton.1
                            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                XpanFilterButton.configuring.set(Boolean.valueOf(Boolean.TRUE.equals(methodHookParam.getObjectExtra("xpanConfigPrevious"))));
                            }

                            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                methodHookParam.setObjectExtra("xpanConfigPrevious", XpanFilterButton.configuring.get());
                                XpanFilterButton.configuring.set(Boolean.valueOf("xpan".equals(methodHookParam.args[1])));
                            }
                        });
                        XposedHelpers.findAndHookMethod("com.oplus.ocs.camera.config.FeatureConfigureAdapter", classLoader, "getFeatureInfoList", new Object[]{cls3, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanFilterButton.2
                            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                if (((Boolean) XpanFilterButton.configuring.get()).booleanValue() && (methodHookParam.getResult() instanceof Set)) {
                                    HashSet hashSet = new HashSet((Set) methodHookParam.getResult());
                                    if (hashSet.add("com.oplus.camera.feature.xpan")) {
                                        methodHookParam.setResult(hashSet);
                                        Log.i("XpanFilter", "enabled stock XPAN presenter in feature list");
                                    }
                                }
                            }
                        }});
                        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanFilterButton.3
                            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                for (Object obj : methodHookParam.args) {
                                    if (obj instanceof Activity) {
                                        XpanFilterButton.presenters.put((Activity) obj, new WeakReference(methodHookParam.thisObject));
                                        Log.i("XpanFilter", "presenter attached to ".concat(obj.getClass().getName()));
                                        return;
                                    }
                                }
                            }

                            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                XpanFilterButton.restoreFilters(clsFindClass, classLoader);
                            }
                        });
                        XposedBridge.hookMethod(symbols.method("FeaturePresenter.stage"), new XC_MethodHook(i3) { // from class: local.jiege.hook.xpan.XpanFilterButton.4
                            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                Activity activity;
                                if (cls.isInstance(methodHookParam.thisObject) && (activity = (Activity) Reflection.findFieldByType(methodHookParam.thisObject, Activity.class.getName())) != null) {
                                    XpanFilterButton.presenters.put(activity, new WeakReference(methodHookParam.thisObject));
                                }
                            }
                        });
                        XposedBridge.hookMethod(symbols.method("ModeSwitch.switch"), new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanFilterButton.5
                            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                if (methodHookParam.hasThrowable()) {
                                    return;
                                }
                                XpanFilterButton.xpan = "xpan".equals(methodHookParam.args[1]);
                                if (XpanFilterButton.xpan || XpanFilterButton.dialog == null) {
                                    return;
                                }
                                XpanFilterButton.dialog.dismiss();
                                XpanFilterButton.dialog = null;
                            }
                        });
                        final Class clsFindClass2 = XposedHelpers.findClass("com.oplus.camera.control.ShutterButton", classLoader);
                        XposedBridge.hookMethod(XposedHelpers.findMethodBestMatch(clsFindClass2, "dispatchTouchEvent", new Class[]{MotionEvent.class}), new XC_MethodHook() { // from class: local.jiege.hook.xpan.XpanFilterButton.6
                            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                int identifier;
                                if (XpanFilterButton.xpan && clsFindClass2.isInstance(methodHookParam.thisObject)) {
                                    View view = (View) methodHookParam.thisObject;
                                    if (view.isEnabled() && view.getVisibility() == 0 && (identifier = view.getResources().getIdentifier("switch_camera_button", "id", "com.oplus.camera")) != 0 && view.getId() == identifier) {
                                        Activity activity = XpanFilterButton.activity(view.getContext());
                                        WeakReference weakReference = (WeakReference) XpanFilterButton.presenters.get(activity);
                                        Object obj = weakReference == null ? null : weakReference.get();
                                        if (activity == null || obj == null || activity.isFinishing()) {
                                            return;
                                        }
                                        MotionEvent motionEvent = (MotionEvent) methodHookParam.args[0];
                                        int actionMasked = motionEvent.getActionMasked();
                                        if (actionMasked == 0) {
                                            view.setPressed(true);
                                        }
                                        if (actionMasked == 1 || actionMasked == 3) {
                                            view.setPressed(false);
                                        }
                                        if (actionMasked == 1 && motionEvent.getX() >= 0.0f && motionEvent.getY() >= 0.0f && motionEvent.getX() < view.getWidth() && motionEvent.getY() < view.getHeight()) {
                                            try {
                                                XpanFilterButton.show(activity, obj, clsFindClass, method, method2);
                                            } catch (Throwable th) {
                                                Log.e("XpanFilter", "filter menu failed", th);
                                            }
                                        }
                                        methodHookParam.setResult(Boolean.TRUE);
                                    }
                                }
                            }
                        });
                        Log.i("XpanFilter", "film button connected to stock XPAN filter callbacks");
                        return;
                    }
                    Method method5 = declaredMethods[i2];
                    Class<?>[] parameterTypes2 = method5.getParameterTypes();
                    char c2 = c;
                    if (method5.getReturnType() == cls2) {
                        methodArr = declaredMethods;
                        if (parameterTypes2.length == 7 && parameterTypes2[0] == Activity.class && parameterTypes2[c2] == String.class && parameterTypes2[2] == cls3 && parameterTypes2[3] == cls3) {
                            if (method4 != null) {
                                t2.o("Ambiguous feature configuration");
                                return;
                            }
                            method4 = method5;
                        }
                    } else {
                        methodArr = declaredMethods;
                    }
                    i2++;
                    c = c2;
                    declaredMethods = methodArr;
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$show$1(Method method, Object obj, String[] strArr, DialogInterface dialogInterface, int i) {
        try {
            method.invoke(obj, strArr[i]);
            Log.i("XpanFilter", "selected " + strArr[i]);
            dialogInterface.dismiss();
        } catch (Throwable th) {
            Log.e("XpanFilter", "filter selection failed", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void restoreFilters(Class<?> cls, ClassLoader classLoader) {
        Object staticObjectField = XposedHelpers.getStaticObjectField(cls, "sHasselbladXpanFilterGroup");
        List list = (List) XposedHelpers.getObjectField(staticObjectField, "mBackTypeList");
        if (list.isEmpty()) {
            Map map = (Map) XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.oplus.camera.filter.FilterHelper", classLoader), "sFilterPathConvertMap");
            String[] strArr = {MasterLayout.STANDARD, "Delta400.3dl.rgb.bin", "fuji-proNegHi_master.bin", "fuji-proNegHi.bin", "portra400_master.bin", "portra400_normal.bin", "800t_master.bin", "800t.bin", "fuji-nc_master.bin", "fuji-nc.bin", "blackandwhite_HC_master.bin", "blackandwhite_HC_normal.bin", "positive_master.bin", "positive_normal.bin", "fuji_cc_master.bin", "fuji_cc.bin"};
            for (int i = 0; i < 16; i++) {
                String str = strArr[i];
                if (MasterLayout.STANDARD.equals(str) || map.containsKey(str)) {
                    String[] strArr2 = {"sProFilterGroup", "sFilterGroup"};
                    for (int i2 = 0; i2 < 2; i2++) {
                        Object staticObjectField2 = XposedHelpers.getStaticObjectField(cls, strArr2[i2]);
                        List list2 = (List) XposedHelpers.getObjectField(staticObjectField2, "mBackTypeList");
                        List list3 = (List) XposedHelpers.getObjectField(staticObjectField2, "mBackNameIdList");
                        int iIndexOf = list2.indexOf(str);
                        if (iIndexOf >= 0 && iIndexOf < list3.size()) {
                            XposedHelpers.callMethod(staticObjectField, "addBack", new Object[]{str, list3.get(iIndexOf)});
                            break;
                        }
                    }
                }
            }
            Log.i("XpanFilter", "restored " + list.size() + " available stock film entries");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void show(Activity activity, final Object obj, Class<?> cls, final Method method, Method method2) {
        AlertDialog alertDialog = dialog;
        if (alertDialog == null || !alertDialog.isShowing()) {
            Object staticObjectField = XposedHelpers.getStaticObjectField(cls, "sHasselbladXpanFilterGroup");
            List list = (List) XposedHelpers.getObjectField(staticObjectField, "mBackTypeList");
            List list2 = (List) XposedHelpers.getObjectField(staticObjectField, "mBackNameIdList");
            if (list.isEmpty() || list.size() != list2.size()) {
                t2.j("XPAN filter list");
                return;
            }
            String[] strArr = new String[list.size()];
            int size = list.size();
            final String[] strArr2 = new String[size];
            for (int i = 0; i < list.size(); i++) {
                strArr2[i] = (String) list.get(i);
                strArr[i] = activity.getString(((Integer) list2.get(i)).intValue());
            }
            AlertDialog alertDialogCreate = new AlertDialog.Builder(activity).setTitle("XPAN 滤镜").setSingleChoiceItems(strArr, list.indexOf(method2.invoke(obj, null)), new DialogInterface.OnClickListener() { // from class: av1
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i2) {
                    XpanFilterButton.lambda$show$1(method, obj, strArr2, dialogInterface, i2);
                }
            }).setNegativeButton("取消", (DialogInterface.OnClickListener) null).create();
            dialog = alertDialogCreate;
            alertDialogCreate.setOnDismissListener(new bv1());
            dialog.show();
            Log.i("XpanFilter", "opened " + size + " stock filters");
        }
    }
}
