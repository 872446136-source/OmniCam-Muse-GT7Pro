package local.jiege.hook.watermark;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.hd;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class HasselCameraPage {
    private static final String HASSEL_TYPE = "picture_editor_text_watermark_master_hasselblad";
    private static final File KEEP_XPAN = new File("/data/adb/omnicam_hassel_xpan_keep");
    private static final String OFF = "/data/adb/omnicam_hasselcam_off";
    private static final String QUERY = "com.oplus.gallery.photoeditor.editingvvm.watermarkmaster.b";
    private static final String R_ID = "com.oplus.gallery.photoeditor.R$id";
    private static final String R_STRING = "com.oplus.gallery.photoeditor.R$string";
    private static final String SECTION = "com.oplus.gallery.photoeditor.editingvvm.watermarkCamera.WatermarkCameraSection";
    private static final String TAG = "HasselCamPage";
    private static final String VM = "com.oplus.gallery.photoeditor.editingvvm.watermarkmaster.WatermarkMasterVM";
    private static final String XPAN_TYPE = "picture3d_editor_id_watermark_master_hassel_xpan";
    private static int adapters;
    private static boolean reportedFill;
    private static boolean reportedMissing;
    private static boolean reportedXpan;

    private HasselCameraPage() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookQuery(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass(QUERY, classLoader);
        final Class clsFindClass2 = XposedHelpers.findClass(VM, classLoader);
        final Method methodOnlyShowList = onlyShowList(clsFindClass2);
        if (methodOnlyShowList == null) {
            if (reportedMissing) {
                return;
            }
            reportedMissing = true;
            Log.i(TAG, "找不到唯一的 (boolean)->List 样式入口，普通哈苏保持原厂");
            return;
        }
        methodOnlyShowList.setAccessible(true);
        try {
            final int staticIntField = XposedHelpers.getStaticIntField(XposedHelpers.findClass(R_STRING, classLoader), HASSEL_TYPE);
            final int iXpanType = xpanType(classLoader);
            XposedBridge.hookAllMethods(clsFindClass, "a", new XC_MethodHook() { // from class: local.jiege.hook.watermark.HasselCameraPage.1
                /* JADX WARN: Code duplicated, block: B:7:0x0012  */
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object[] objArr;
                    Object[] objArr2 = methodHookParam.args;
                    if (objArr2.length == 1) {
                        Object obj = objArr2[0];
                        if (obj instanceof Object[]) {
                            objArr = (Object[]) obj;
                        } else {
                            objArr = null;
                        }
                    } else {
                        objArr = null;
                    }
                    if (objArr == null || objArr.length < 3) {
                        return;
                    }
                    Object obj2 = objArr[0];
                    if (obj2 instanceof Integer) {
                        int iIntValue = ((Integer) obj2).intValue();
                        Object result = methodHookParam.getResult();
                        if (result instanceof List) {
                            if (iIntValue == staticIntField) {
                                if (((List) result).isEmpty()) {
                                    try {
                                        Object objK = HasselCameraPage.k(clsFindClass2, methodHookParam.thisObject);
                                        if (objK == null) {
                                            return;
                                        }
                                        Object objInvoke = methodOnlyShowList.invoke(objK, Boolean.valueOf(Boolean.TRUE.equals(objArr[2])));
                                        if ((objInvoke instanceof List) && !((List) objInvoke).isEmpty()) {
                                            methodHookParam.setResult(objInvoke);
                                            if (HasselCameraPage.reportedFill) {
                                                return;
                                            }
                                            HasselCameraPage.reportedFill = true;
                                            Log.i(HasselCameraPage.TAG, "普通哈苏样式 " + ((List) objInvoke).size() + " 项（按页面模式读），替换掉品牌门禁给的空列表");
                                            return;
                                        }
                                        return;
                                    } catch (Throwable th) {
                                        Log.e(HasselCameraPage.TAG, "普通哈苏样式重算失败", th);
                                        return;
                                    }
                                }
                                return;
                            }
                            int i = iXpanType;
                            if (i == 0 || iIntValue != i) {
                                return;
                            }
                            if (HasselCameraPage.j(clsFindClass2, methodHookParam.thisObject) || HasselCameraPage.KEEP_XPAN.exists()) {
                                return;
                            }
                            List list = (List) result;
                            if (list.isEmpty()) {
                                return;
                            }
                            int size = list.size();
                            methodHookParam.setResult(new ArrayList());
                            if (HasselCameraPage.reportedXpan) {
                                return;
                            }
                            HasselCameraPage.reportedXpan = true;
                            Log.i(HasselCameraPage.TAG, "非 XPAN 页面，隐藏专用水印 " + size + " 张收起来（放 " + HasselCameraPage.KEEP_XPAN + " 可保留）");
                        }
                    }
                }
            });
            StringBuilder sb = new StringBuilder("哈苏样式查询已接管 com.oplus.gallery.photoeditor.editingvvm.watermarkmaster.b.a（列表入口 ");
            sb.append(methodOnlyShowList.getName());
            sb.append("，XPAN type ");
            sb.append(iXpanType == 0 ? "未找到" : String.valueOf(iXpanType));
            sb.append("）");
            Log.i(TAG, sb.toString());
        } catch (Throwable unused) {
            Log.i(TAG, "资源 id picture_editor_text_watermark_master_hasselblad 不在这一版里，普通哈苏保持原厂");
        }
    }

    public static void installGallery(ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "off (/data/adb/omnicam_hasselcam_off)");
        } else {
            Log.guard(TAG, "哈苏样式查询", new hd(classLoader, (byte) 3));
            Log.guard(TAG, "哈苏分区样式表", new hd(classLoader, (byte) 4));
        }
    }

    private static boolean isXpanPage(Object obj, Class<?> cls) {
        try {
            return Boolean.TRUE.equals(XposedHelpers.callMethod(XposedHelpers.getObjectField(viewModel(obj, cls), "V"), "getValue", new Object[0]));
        } catch (Throwable unused) {
            return true;
        }
    }

    public static /* bridge */ /* synthetic */ boolean j(Class cls, Object obj) {
        return isXpanPage(obj, cls);
    }

    public static /* bridge */ /* synthetic */ Object k(Class cls, Object obj) {
        return viewModel(obj, cls);
    }

    private static Method onlyShowList(Class<?> cls) {
        Method method = null;
        for (Method method2 : cls.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method2.getParameterTypes();
            if (method2.getReturnType() == List.class && parameterTypes.length == 1 && parameterTypes[0] == Boolean.TYPE) {
                if (method != null) {
                    return null;
                }
                method = method2;
            }
        }
        return method;
    }

    private static Object viewModel(Object obj, Class<?> cls) {
        if (obj == null) {
            return null;
        }
        for (Field field : obj.getClass().getDeclaredFields()) {
            if (cls.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return field.get(obj);
                } catch (Throwable unused) {
                    break;
                }
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void watchLists(ClassLoader classLoader) {
        Class clsFindClass = XposedHelpers.findClass(SECTION, classLoader);
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.watermark.HasselCameraPage.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                int iIntValue;
                Object result = methodHookParam.getResult();
                if (result != null) {
                    try {
                        iIntValue = ((Integer) XposedHelpers.callMethod(result, "getItemCount", new Object[0])).intValue();
                    } catch (Throwable unused) {
                        iIntValue = -1;
                    }
                } else {
                    iIntValue = -1;
                }
                int i = HasselCameraPage.adapters;
                HasselCameraPage.adapters = i + 1;
                if (i >= 24) {
                    return;
                }
                Log.i(HasselCameraPage.TAG, "样式表 type=" + methodHookParam.args[1] + " show=" + methodHookParam.args[2] + " 条目=" + iIntValue);
            }
        };
        Class cls = Integer.TYPE;
        XposedHelpers.findAndHookMethod(clsFindClass, "H0", new Object[]{clsFindClass, cls, Boolean.TYPE, cls, xC_MethodHook});
        Log.i(TAG, "哈苏分区样式表已挂监听");
    }

    private static int xpanType(ClassLoader classLoader) {
        try {
            int i = 0;
            int i2 = 0;
            for (Field field : XposedHelpers.findClass(R_ID, classLoader).getDeclaredFields()) {
                if (field.getName().equals(XPAN_TYPE)) {
                    i++;
                    i2 = field.getInt(null);
                }
            }
            if (i == 1) {
                return i2;
            }
            return 0;
        } catch (Throwable unused) {
        }
    }
}
