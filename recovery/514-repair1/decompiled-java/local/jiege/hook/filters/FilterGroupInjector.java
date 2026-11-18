package local.jiege.hook.filters;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.t2;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class FilterGroupInjector {
    private static final String APP = "com.oplus.camera.filter.FilterGroupManager";
    private static final String CAMERA = "com.oplus.camera";
    private static final String CAMERA_ACTIVITY = "com.oplus.camera.Camera";
    private static final String NIGHT_GROUP = "sNightFilterGroup";
    private static final String SDK = "com.oplus.ocs.camera.ipusdk.processunit.filter.list.FilterGroupManager";
    private static final String TAG = "FilterGroups";
    private static volatile List<String> masterHidden = Collections.EMPTY_LIST;
    private static final Set<String> reported = ConcurrentHashMap.newKeySet();

    private FilterGroupInjector() {
    }

    private static List<FilterEntry> forGroup(FilterScope filterScope, String str, List<FilterEntry> list) {
        if (filterScope != FilterScope.NORMAL) {
            return list;
        }
        ArrayList arrayList = new ArrayList();
        for (FilterEntry filterEntry : list) {
            if (NormalMasterMirror.takes(str, filterEntry)) {
                arrayList.add(filterEntry);
            }
        }
        return arrayList;
    }

    private static void hookPresetMatch(ClassLoader classLoader) {
        try {
            XposedBridge.hookAllMethods(XposedHelpers.findClass("com.oplus.camera.common.utils.PresetDataUtil", classLoader), "matchPresetFilterOnDevice", new XC_MethodHook() { // from class: local.jiege.hook.filters.FilterGroupInjector.5
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object[] objArr = methodHookParam.args;
                    if (objArr.length == 4 && (objArr[2] instanceof List) && (objArr[3] instanceof List)) {
                        List list = FilterGroupInjector.masterHidden;
                        if (list.isEmpty()) {
                            return;
                        }
                        Object[] objArr2 = methodHookParam.args;
                        List list2 = (List) objArr2[2];
                        List list3 = (List) objArr2[3];
                        if (list3.size() - list2.size() != list.size()) {
                            return;
                        }
                        if (list.contains(FilterGroupInjector.presetFilterType(methodHookParam.args[0]))) {
                            methodHookParam.setResult(Boolean.TRUE);
                        } else {
                            methodHookParam.args[3] = new ArrayList(list3.subList(0, list2.size()));
                        }
                    }
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "preset filter match hook failed", th);
        }
    }

    private static void hookReload() {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.filters.FilterGroupInjector.3
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Intent intent;
                    Intent launchIntentForPackage;
                    Activity activity = (Activity) methodHookParam.thisObject;
                    if (FilterGroupInjector.CAMERA_ACTIVITY.equals(activity.getClass().getName()) && (intent = activity.getIntent()) != null && "android.intent.action.MAIN".equals(intent.getAction()) && MasterFilterSync.changedSinceLoad() && (launchIntentForPackage = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName())) != null) {
                        Log.i(FilterGroupInjector.TAG, "master filter configuration changed, restarting the camera");
                        launchIntentForPackage.addFlags(268468224);
                        activity.finishAffinity();
                        activity.startActivity(launchIntentForPackage);
                        Process.killProcess(Process.myPid());
                    }
                }
            }});
        } catch (Throwable th) {
            Log.e(TAG, "reload hook failed", th);
        }
    }

    private static void hookStrip() {
        try {
            Symbols symbols = Symbols.get();
            final Field field = symbols.has("FeaturePresenter.modeName") ? symbols.field("FeaturePresenter.modeName") : null;
            XposedBridge.hookMethod(symbols.method("FilterPresenter.typesFor"), new XC_MethodHook() { // from class: local.jiege.hook.filters.FilterGroupInjector.4
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    try {
                        List list = FilterGroupInjector.masterHidden;
                        if (!list.isEmpty() && (methodHookParam.getResult() instanceof List)) {
                            Field field2 = field;
                            if (field2 == null || FilterScope.ofMode(field2.get(methodHookParam.thisObject)) == FilterScope.MASTER) {
                                List list2 = (List) methodHookParam.getResult();
                                int size = list2.size() - list.size();
                                if (size >= 1 && list2.subList(size, list2.size()).equals(list)) {
                                    methodHookParam.setResult(new ArrayList(list2.subList(0, size)));
                                }
                            }
                        }
                    } catch (Throwable th) {
                        FilterGroupInjector.once("filter strip", th);
                    }
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "filter strip hook failed", th);
        }
    }

    private static int inject(Object obj, boolean z, List<FilterEntry> list, Context context) {
        Object objValueOf;
        String str = z ? "addBack" : "addFrontAndBack";
        int i = 1;
        int i2 = 0;
        for (FilterEntry filterEntry : list) {
            List list2 = (List) XposedHelpers.getObjectField(obj, "mBackTypeList");
            if (list2 == null || !list2.contains(filterEntry.id)) {
                if (context == null) {
                    objValueOf = "R.string." + filterEntry.nameResource;
                } else {
                    int identifier = context.getResources().getIdentifier(filterEntry.nameResource, "string", CAMERA);
                    if (identifier == 0) {
                        t2.i(filterEntry.nameResource, "Missing name resource ");
                        return 0;
                    }
                    objValueOf = Integer.valueOf(identifier);
                }
                FilterEntry.Placement placement = filterEntry.placement;
                FilterEntry.Placement placement2 = FilterEntry.Placement.HEAD;
                String str2 = filterEntry.id;
                if (placement == placement2) {
                    XposedHelpers.callMethod(obj, str, new Object[]{Integer.valueOf(i), str2, objValueOf});
                    i++;
                } else {
                    XposedHelpers.callMethod(obj, str, new Object[]{str2, objValueOf});
                }
                i2++;
            } else if (filterEntry.placement == FilterEntry.Placement.HEAD) {
                i++;
            }
        }
        return i2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void injectAll(Class<?> cls, Context context, ClassLoader classLoader) {
        FilterScope[] filterScopeArr;
        int i;
        MasterFilterSync.load(context);
        CaptureIds.apply(classLoader);
        FilterScope[] filterScopeArrValues = FilterScope.values();
        int length = filterScopeArrValues.length;
        for (int i2 = 0; i2 < length; i2++) {
            FilterScope filterScope = filterScopeArrValues[i2];
            FilterCatalog filterCatalog = FilterCatalog.get(filterScope);
            List<FilterEntry> listEntries = filterScope == FilterScope.MASTER ? filterCatalog.entries() : filterCatalog.enabled();
            String[] strArr = filterScope.groups;
            int length2 = strArr.length;
            int i3 = 0;
            while (i3 < length2) {
                String str = strArr[i3];
                try {
                    Object staticObjectField = XposedHelpers.getStaticObjectField(cls, str);
                    if (staticObjectField == null) {
                        filterScopeArr = filterScopeArrValues;
                        i = length;
                    } else {
                        filterScopeArr = filterScopeArrValues;
                        try {
                            int iInject = inject(staticObjectField, NIGHT_GROUP.equals(str), forGroup(filterScope, str, listEntries), context);
                            FilterScope filterScope2 = FilterScope.MASTER;
                            i = length;
                            String strLayOut = filterScope == filterScope2 ? layOut(staticObjectField, filterCatalog) : PresetSelection.STANDARD_KEY;
                            if (filterScope == filterScope2 && context != null) {
                                try {
                                    reportStock(staticObjectField, filterCatalog, context);
                                } catch (Throwable th) {
                                    th = th;
                                    Log.e(TAG, filterScope + " " + str + " injection failed", th);
                                }
                            }
                            StringBuilder sb = new StringBuilder();
                            sb.append(context == null ? "SDK " : "app ");
                            sb.append(filterScope);
                            sb.append(" ");
                            sb.append(str);
                            sb.append(" +");
                            sb.append(iInject);
                            sb.append(strLayOut);
                            Log.i(TAG, sb.toString());
                        } catch (Throwable th2) {
                            th = th2;
                            i = length;
                            Log.e(TAG, filterScope + " " + str + " injection failed", th);
                            i3++;
                            filterScopeArrValues = filterScopeArr;
                            length = i;
                        }
                    }
                } catch (Throwable th3) {
                    th = th3;
                    filterScopeArr = filterScopeArrValues;
                }
                i3++;
                filterScopeArrValues = filterScopeArr;
                length = i;
            }
        }
        if (FilterCatalog.mirrorsMaster()) {
            try {
                StringBuilder sb2 = new StringBuilder();
                sb2.append(context == null ? "SDK" : "app");
                sb2.append(" normal groups");
                sb2.append(NormalMasterMirror.apply(cls));
                Log.i(TAG, sb2.toString());
            } catch (Throwable th4) {
                Log.e(TAG, "normal groups in master order failed", th4);
            }
        }
    }

    public static void installCamera(final ClassLoader classLoader) {
        try {
            final Class clsFindClass = XposedHelpers.findClass(SDK, classLoader);
            XposedBridge.hookAllMethods(clsFindClass, "init", new XC_MethodHook() { // from class: local.jiege.hook.filters.FilterGroupInjector.1
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    FilterGroupInjector.injectAll(clsFindClass, null, classLoader);
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "SDK filter group hook failed", th);
        }
        try {
            final Class clsFindClass2 = XposedHelpers.findClass(APP, classLoader);
            XposedHelpers.findAndHookMethod(clsFindClass2, "initFromIpu", new Object[]{Context.class, HashMap.class, new XC_MethodHook() { // from class: local.jiege.hook.filters.FilterGroupInjector.2
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    FilterGroupInjector.injectAll(clsFindClass2, (Context) methodHookParam.args[0], classLoader);
                }
            }});
        } catch (Throwable th2) {
            Log.e(TAG, "app filter group hook failed", th2);
        }
        hookStrip();
        hookPresetMatch(classLoader);
        hookReload();
    }

    public static String layOut(Object obj, FilterCatalog filterCatalog) {
        List list = (List) XposedHelpers.getObjectField(obj, "mBackTypeList");
        MasterLayout.Result resultCompute = MasterLayout.compute(list, filterCatalog, MasterLayoutStore.load());
        MasterLayout.reorder(list, (List) XposedHelpers.getObjectField(obj, "mBackNameIdList"), resultCompute.order);
        MasterLayout.reorder((List) XposedHelpers.getObjectField(obj, "mFrontTypeList"), (List) XposedHelpers.getObjectField(obj, "mFrontNameIdList"), resultCompute.order);
        List<String> list2 = resultCompute.order;
        masterHidden = Collections.unmodifiableList(new ArrayList(list2.subList(list2.size() - resultCompute.hiddenCount, resultCompute.order.size())));
        return ", " + resultCompute.hiddenCount + " hidden";
    }

    public static int masterHiddenCount() {
        return masterHidden.size();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void once(String str, Throwable th) {
        if (reported.add(str)) {
            Log.e(TAG, str + " hook failed, left as it is", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String presetFilterType(Object obj) {
        int i;
        int iIndexOf;
        String strValueOf = String.valueOf(obj);
        int iIndexOf2 = strValueOf.indexOf("', mFilterType=");
        if (iIndexOf2 >= 0 && (iIndexOf = strValueOf.indexOf(", mFilterNameResEntry=", (i = iIndexOf2 + 15))) >= 0) {
            return strValueOf.substring(i, iIndexOf);
        }
        return null;
    }

    private static void reportStock(Object obj, FilterCatalog filterCatalog, Context context) {
        List list = (List) XposedHelpers.getObjectField(obj, "mBackTypeList");
        List list2 = (List) XposedHelpers.getObjectField(obj, "mBackNameIdList");
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        for (int i = 0; i < list.size(); i++) {
            String string = null;
            if (filterCatalog.find((String) list.get(i)) != null) {
                arrayList.add((String) list.get(i));
                arrayList2.add(null);
            } else {
                try {
                    if (list2.get(i) instanceof Integer) {
                        string = context.getString(((Integer) list2.get(i)).intValue());
                    }
                } catch (Throwable unused) {
                }
                arrayList.add((String) list.get(i));
                arrayList2.add(string);
            }
        }
        MasterFilterSync.reportStock(arrayList, arrayList2);
    }
}
