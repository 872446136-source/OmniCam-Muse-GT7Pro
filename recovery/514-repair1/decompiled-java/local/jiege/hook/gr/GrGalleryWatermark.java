package local.jiege.hook.gr;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.TypedValue;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import local.jiege.hook.common.Capabilities;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.OptionalHook;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class GrGalleryWatermark {
    private static final ThreadLocal<Boolean> PARSING_GR_PHOTO = new ThreadLocal<>();
    private static final String STYLE_ASSET_PREFIX = "watermark_master_styles/";
    private static final String STYLE_DIR = "/data/user/0/com.coloros.gallery3d/files/ricoh_gr_styles";
    private static final String TAG = "RicohGrPort";

    private GrGalleryWatermark() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static File artworkFile(Resources resources, int i) {
        if (resources != null && i != 0) {
            try {
                String resourceEntryName = resources.getResourceEntryName(i);
                if (resourceEntryName != null && (resourceEntryName.startsWith("realme_gr") || resourceEntryName.startsWith("sketch_realme_gr") || resourceEntryName.startsWith("sketch_camera_realme_gr") || resourceEntryName.startsWith("watermark_camera_realme_gr"))) {
                    return staged(resourceEntryName.concat(".webp"));
                }
            } catch (Throwable unused) {
            }
        }
        return null;
    }

    public static /* bridge */ /* synthetic */ boolean c(Object obj, Symbols symbols) {
        return openedFromGr(symbols, obj);
    }

    private static void hookArtwork(ClassLoader classLoader) {
        Class cls = Integer.TYPE;
        try {
            XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.5
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    File fileArtworkFile = GrGalleryWatermark.artworkFile((Resources) methodHookParam.thisObject, ((Integer) methodHookParam.args[0]).intValue());
                    if (fileArtworkFile != null) {
                        methodHookParam.setResult(new FileInputStream(fileArtworkFile));
                    }
                }
            };
            OptionalHook.method(Resources.class, "openRawResource", xC_MethodHook, cls);
            OptionalHook.method(Resources.class, "openRawResource", xC_MethodHook, cls, TypedValue.class);
            XC_MethodHook xC_MethodHook2 = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.6
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    GrGalleryWatermark.setDrawable(methodHookParam, (Resources) methodHookParam.thisObject, ((Integer) methodHookParam.args[0]).intValue());
                }
            };
            OptionalHook.method(Resources.class, "getDrawable", xC_MethodHook2, cls, Resources.Theme.class);
            OptionalHook.method(Resources.class, "getDrawableForDensity", xC_MethodHook2, cls, cls, Resources.Theme.class);
            OptionalHook.method(XposedHelpers.findClassIfExists("androidx.core.content.res.ResourcesCompat", classLoader), "getDrawable", new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.7
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object[] objArr = methodHookParam.args;
                    GrGalleryWatermark.setDrawable(methodHookParam, (Resources) objArr[0], ((Integer) objArr[1]).intValue());
                }
            }, Resources.class, cls, Resources.Theme.class);
            for (Method method : XposedHelpers.findClass("android.content.res.ResourcesImpl", (ClassLoader) null).getDeclaredMethods()) {
                if ("loadDrawable".equals(method.getName())) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.8
                        public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                            Resources resources = null;
                            Integer num = null;
                            for (Object obj : methodHookParam.args) {
                                if (obj instanceof Resources) {
                                    resources = (Resources) obj;
                                } else if (obj instanceof Integer) {
                                    Integer num2 = (Integer) obj;
                                    if (num2.intValue() > 2130706432) {
                                        num = num2;
                                    }
                                }
                            }
                            if (resources == null || num == null) {
                                return;
                            }
                            GrGalleryWatermark.setDrawable(methodHookParam, resources, num.intValue());
                        }
                    });
                }
            }
        } catch (Throwable th) {
            Log.e(TAG, "gallery GR drawable hooks failed", th);
        }
        try {
            XC_MethodHook xC_MethodHook3 = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.9
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object[] objArr = methodHookParam.args;
                    File fileArtworkFile = GrGalleryWatermark.artworkFile((Resources) objArr[0], ((Integer) objArr[1]).intValue());
                    if (fileArtworkFile == null) {
                        return;
                    }
                    Object[] objArr2 = methodHookParam.args;
                    Bitmap bitmapDecodeFile = BitmapFactory.decodeFile(fileArtworkFile.getAbsolutePath(), objArr2.length > 2 ? (BitmapFactory.Options) objArr2[2] : null);
                    if (bitmapDecodeFile != null) {
                        methodHookParam.setResult(bitmapDecodeFile);
                    }
                }
            };
            XposedHelpers.findAndHookMethod(BitmapFactory.class, "decodeResource", new Object[]{Resources.class, cls, BitmapFactory.Options.class, xC_MethodHook3});
            XposedHelpers.findAndHookMethod(BitmapFactory.class, "decodeResource", new Object[]{Resources.class, cls, xC_MethodHook3});
        } catch (Throwable th2) {
            Log.e(TAG, "gallery GR decodeResource hook failed", th2);
        }
    }

    private static void hookEditorSection(final Symbols symbols) {
        try {
            XposedBridge.hookMethod(symbols.method("Section.cameraStyles"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.10
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (GrGalleryWatermark.c(methodHookParam.thisObject, symbols)) {
                        methodHookParam.setResult(Boolean.TRUE);
                    }
                }
            });
            XposedBridge.hookMethod(symbols.method("Section.onCreate"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.11
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    try {
                        Object obj = methodHookParam.thisObject;
                        if (GrGalleryWatermark.c(obj, symbols)) {
                            symbols.call("Section.refreshStyles", obj, new Object[0]);
                            Object obj2 = symbols.get("Section.grChip", obj);
                            if (obj2 != null) {
                                XposedHelpers.callMethod(obj2, "setChecked", new Object[]{Boolean.TRUE});
                            }
                            Object obj3 = symbols.get("Section.otherChip", obj);
                            if (obj3 != null) {
                                XposedHelpers.callMethod(obj3, "setChecked", new Object[]{Boolean.FALSE});
                            }
                            View view = (View) symbols.get("Section.root", obj);
                            GrGalleryWatermark.setChildVisibility(view, "camera_rm_chip_group", 0);
                            GrGalleryWatermark.setChildVisibility(view, "camera_realme_gr_style_layout", 0);
                        }
                    } catch (Throwable th) {
                        Log.e(GrGalleryWatermark.TAG, "WatermarkCameraSection GR setup failed", th);
                    }
                }
            });
            XposedBridge.hookMethod(symbols.method("Section.refreshStyles"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.12
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    try {
                        View view = (View) symbols.get("Section.root", methodHookParam.thisObject);
                        GrGalleryWatermark.setChildVisibility(view, "camera_lumo_style_layout", 8);
                        GrGalleryWatermark.setChildVisibility(view, "camera_rm_chip_group", 0);
                        GrGalleryWatermark.setChildVisibility(view, "camera_realme_gr_style_layout", 0);
                    } catch (Throwable unused) {
                    }
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "WatermarkCameraSection hooks failed", th);
        }
    }

    private static void hookFeatureGates(Symbols symbols) {
        try {
            XposedBridge.hookMethod(symbols.method("Config.getBoolean"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.1
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object obj = methodHookParam.args[0];
                    if ("feature_is_camera_gr_supported".equals(obj) || "feature_is_support_hassel_watermark".equals(obj) || "feature_is_support_hassel_device".equals(obj)) {
                        methodHookParam.setResult(Boolean.TRUE);
                    }
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "gallery GR feature gate hook failed", th);
        }
        try {
            XposedBridge.hookMethod(symbols.method("CameraInfo.doubleSelect"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.2
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    methodHookParam.setResult(Boolean.TRUE);
                }
            });
        } catch (Throwable th2) {
            Log.e(TAG, "gallery double-select hook failed", th2);
        }
        XposedHelpers.findAndHookMethod(XposedHelpers.findClass("android.app.SharedPreferencesImpl", (ClassLoader) null), "getBoolean", new Object[]{String.class, Boolean.TYPE, new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.3
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = methodHookParam.args[0];
                if ((obj instanceof String) && ((String) obj).contains("is_camera_gr_supported")) {
                    methodHookParam.setResult(Boolean.TRUE);
                }
            }
        }});
    }

    private static void hookGrPhotoInfo(Symbols symbols) {
        try {
            XposedBridge.hookMethod(symbols.method("Extend.parse"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.13
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    GrGalleryWatermark.PARSING_GR_PHOTO.remove();
                }

                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object obj = methodHookParam.args[0];
                    if (!(obj instanceof Map) || ((Map) obj).get("gr.effect.info") == null) {
                        return;
                    }
                    GrGalleryWatermark.PARSING_GR_PHOTO.set(Boolean.TRUE);
                }
            });
            XposedBridge.hookMethod(symbols.method("Brand.isRealme"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.14
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (GrGalleryWatermark.PARSING_GR_PHOTO.get() != null) {
                        methodHookParam.setResult(Boolean.TRUE);
                    }
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "gallery GR photo info hooks failed", th);
        }
    }

    private static void hookMasterFilterName(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("com.oplus.gallery.foundation.codec.extend.mastermode.MasterModeInfo", classLoader, "getRealFilterName", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.15
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object objectField = XposedHelpers.getObjectField(methodHookParam.thisObject, "filterName");
                    if (!(objectField instanceof String) || ((String) objectField).isEmpty()) {
                        return;
                    }
                    methodHookParam.setResult(objectField);
                }
            }});
        } catch (Throwable th) {
            Log.e(TAG, "gallery master filter name hook failed", th);
        }
    }

    private static void hookModelTextLabel(final Symbols symbols) {
        try {
            XposedBridge.hookMethod(symbols.method("Personalized.setup"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.16
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    int identifier;
                    try {
                        String str = (String) symbols.get("Personalized.style", methodHookParam.thisObject);
                        if (str != null && str.startsWith("gr_style_")) {
                            List list = (List) symbols.get("Personalized.labels", methodHookParam.thisObject);
                            Context context = (Context) symbols.get("Editing.context", methodHookParam.thisObject);
                            if (list == null || context == null || (identifier = context.getResources().getIdentifier("picture_editor_text_watermark_model", "string", context.getPackageName())) == 0 || list.contains(Integer.valueOf(identifier))) {
                                return;
                            }
                            list.add(0, Integer.valueOf(identifier));
                        }
                    } catch (Throwable th) {
                        Log.e(GrGalleryWatermark.TAG, "GR model text label failed", th);
                    }
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "GR model text label setup failed", th);
        }
    }

    private static void hookStyleSource(final Symbols symbols) {
        String str = "AssetStyleSource.stream";
        try {
            if (!symbols.has("AssetStyleSource.stream")) {
                str = "AssetStyleSource.open";
            }
            XposedBridge.hookMethod(symbols.method(str), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrGalleryWatermark.4
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    try {
                        String strStyleFileName = GrGalleryWatermark.styleFileName((String) symbols.get("AssetStyleSource.name", methodHookParam.thisObject));
                        if (strStyleFileName == null) {
                            return;
                        }
                        File fileStaged = GrGalleryWatermark.staged(strStyleFileName);
                        if (fileStaged != null) {
                            methodHookParam.setResult(new FileInputStream(fileStaged));
                            return;
                        }
                        Log.i(GrGalleryWatermark.TAG, "gallery GR style missing, both " + new File(Capabilities.modeLimitDir(), strStyleFileName) + " and " + new File(GrGalleryWatermark.STYLE_DIR, strStyleFileName));
                    } catch (Throwable th) {
                        Log.e(GrGalleryWatermark.TAG, "gallery GR style routing failed", th);
                    }
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "gallery GR style routing setup failed", th);
        }
    }

    public static void install(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        hookFeatureGates(symbols);
        hookStyleSource(symbols);
        hookArtwork(classLoader);
        hookEditorSection(symbols);
        hookModelTextLabel(symbols);
        hookGrPhotoInfo(symbols);
        hookMasterFilterName(classLoader);
        Log.i(TAG, "gallery GR watermark hooks installed");
    }

    private static boolean openedFromGr(Symbols symbols, Object obj) {
        if (obj == null) {
            return false;
        }
        try {
            Context baseContext = (Context) symbols.get("Editing.context", obj);
            while ((baseContext instanceof ContextWrapper) && !(baseContext instanceof Activity)) {
                baseContext = ((ContextWrapper) baseContext).getBaseContext();
            }
            Intent intent = baseContext instanceof Activity ? ((Activity) baseContext).getIntent() : null;
            return intent != null && "gr".equals(intent.getStringExtra("ai_master_watermark_mode_name"));
        } catch (Throwable unused) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void setChildVisibility(View view, String str, int i) {
        if (view == null) {
            return;
        }
        int identifier = view.getResources().getIdentifier(str, "id", view.getContext().getPackageName());
        View viewFindViewById = identifier > 0 ? view.findViewById(identifier) : null;
        if (viewFindViewById != null) {
            viewFindViewById.setVisibility(i);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void setDrawable(XC_MethodHook.MethodHookParam methodHookParam, Resources resources, int i) {
        Bitmap bitmapDecodeFile;
        File fileArtworkFile = artworkFile(resources, i);
        if (fileArtworkFile == null || (bitmapDecodeFile = BitmapFactory.decodeFile(fileArtworkFile.getAbsolutePath())) == null) {
            return;
        }
        methodHookParam.setResult(new BitmapDrawable(resources, bitmapDecodeFile));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static File staged(String str) {
        File file = new File(Capabilities.modeLimitDir(), str);
        if (file.isFile()) {
            return file;
        }
        File file2 = new File(STYLE_DIR, str);
        if (file2.isFile()) {
            return file2;
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String styleFileName(String str) {
        if (str != null && str.startsWith(STYLE_ASSET_PREFIX)) {
            String strSubstring = str.substring(24);
            if (strSubstring.startsWith("gr_style_") && strSubstring.endsWith(".json") && strSubstring.indexOf(47) < 0 && strSubstring.indexOf(92) < 0) {
                return strSubstring;
            }
        }
        return null;
    }
}
