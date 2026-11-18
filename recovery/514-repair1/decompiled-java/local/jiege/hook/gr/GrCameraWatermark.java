package local.jiege.hook.gr;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.c3;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import local.jiege.hook.common.Capabilities;
import local.jiege.hook.common.DataStore;
import local.jiege.hook.common.Install;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Reflection;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
final class GrCameraWatermark {
    private static final String DEFAULT_STYLE = "gr_style_1";
    private static final String GALLERY_STYLE_DIR = "/data/user/0/com.coloros.gallery3d/files/ricoh_gr_styles";
    private static final String STYLE_ACTION = "com.oplus.camera.ai.master.watermark.resource";
    private static final String TAG = "RicohGrPort";
    private static volatile Object grSwitchKey;
    private static volatile boolean styleReceiverRegistered;
    private static final String[] FONT_DIRS = {"/data/user/0/com.oplus.camera/files/video_watermark", "/data/user/0/com.oplus.camera/files/jiege/fonts"};
    private static final ThreadLocal<Boolean> readingCaptureWatermark = new ThreadLocal<>();
    private static final String[] NORMAL_WATERMARK_PREFS = {"pref_watermark_capture_switch_key", "pref_ai_master_watermark_photo_open_state", "pref_ai_master_watermark_photo_style_id", "pref_ai_master_watermark_photo_style_id_backup"};
    private static final String GR_CAPTURE_SWITCH = "pref_watermark_gr_capture_switch_key";
    private static final String[] GR_WATERMARK_PREFS = {GR_CAPTURE_SWITCH, "pref_ai_master_watermark_mode_limit_open_state", "pref_ai_master_watermark_mode_limit_style_id", "pref_ai_master_watermark_mode_limit_style_id_backup"};

    private GrCameraWatermark() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String currentStyle() {
        String str = GrState.watermarkStyleId;
        return str != null ? str : DEFAULT_STYLE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Bitmap decodeLogo(String str) {
        if (!str.endsWith(".webp")) {
            str = str.concat(".webp");
        }
        String strModeLimitDir = Capabilities.modeLimitDir();
        String[] strArr = {strModeLimitDir, c3.j(strModeLimitDir, "/gr_style_1"), GALLERY_STYLE_DIR};
        for (int i = 0; i < 3; i++) {
            File file = new File(strArr[i], str);
            if (file.isFile()) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        }
        return null;
    }

    private static Object grSwitchKey(Symbols symbols) {
        Object objKey = grSwitchKey;
        if (objKey != null) {
            return objKey;
        }
        try {
            objKey = DataStore.key(symbols, "Keys.grWatermark", GR_CAPTURE_SWITCH);
            grSwitchKey = objKey;
            return objKey;
        } catch (Throwable th) {
            Log.e(TAG, "GR watermark switch key missing", th);
            return objKey;
        }
    }

    private static boolean grWatermarkOn(Symbols symbols, ClassLoader classLoader) {
        Object objGrSwitchKey = grSwitchKey(symbols);
        if (objGrSwitchKey == null) {
            return true;
        }
        try {
            return !Install.OFF.equals(DataStore.get(symbols, classLoader, objGrSwitchKey, Install.ON));
        } catch (Throwable th) {
            Log.e(TAG, "GR watermark switch read failed", th);
            return true;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean holdsAny(Object obj, String[] strArr) {
        for (String str : strArr) {
            if (Reflection.hasStringField(obj, str)) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookCaptureState(Symbols symbols) {
        XposedBridge.hookMethod(symbols.method("WatermarkModel.isOpen"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrCameraWatermark.3
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                GrCameraWatermark.readingCaptureWatermark.remove();
            }

            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (((Boolean) methodHookParam.args[0]).booleanValue() || !((Boolean) methodHookParam.args[1]).booleanValue()) {
                    return;
                }
                if ("gr".equals(methodHookParam.args[2]) || GrState.isGrMode()) {
                    GrCameraWatermark.readingCaptureWatermark.set(Boolean.TRUE);
                }
            }
        });
    }

    private static void hookEditorLaunch(Symbols symbols) {
        try {
            XposedBridge.hookMethod(symbols.method("Util.openEditor"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrCameraWatermark.5
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (GrState.isGrMode()) {
                        methodHookParam.args[1] = "gr";
                    }
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "gallery editor mode hook failed", th);
        }
        XposedBridge.hookAllMethods(Activity.class, "startActivityForResult", new XC_MethodHook() { // from class: local.jiege.hook.gr.GrCameraWatermark.6
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object[] objArr = methodHookParam.args;
                if (objArr.length != 0 && (objArr[0] instanceof Intent) && GrState.isGrMode()) {
                    Intent intent = (Intent) methodHookParam.args[0];
                    if (intent.getComponent() == null || !"com.oplus.gallery.pictureeditorpage.PhotoEditorActivity".equals(intent.getComponent().getClassName())) {
                        return;
                    }
                    intent.putExtra("ai_master_watermark_mode_name", "gr");
                    intent.putExtra("is_camera_gr_supported", true);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookSettingsSummary(final Symbols symbols, final ClassLoader classLoader) {
        XposedBridge.hookMethod(symbols.method("Preference.assignment"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrCameraWatermark.4
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Reflection.hasStringField(methodHookParam.thisObject, "pref_watermark_setting_key")) {
                    Context context = (Context) Reflection.findFieldByType(methodHookParam.thisObject, "android.content.Context");
                    boolean zIsGrMode = GrState.isGrMode();
                    Symbols symbols2 = symbols;
                    ClassLoader classLoader2 = classLoader;
                    methodHookParam.args[0] = context.getString(context.getResources().getIdentifier(zIsGrMode ? GrCameraWatermark.l(classLoader2, symbols2) : GrCameraWatermark.r(classLoader2, symbols2) ? "camera_setting_slogan_on" : "camera_setting_switch_off", "string", context.getPackageName()));
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookStoredValues(final Symbols symbols, final ClassLoader classLoader) {
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrCameraWatermark.1
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                boolean z = false;
                Object obj = methodHookParam.args[0];
                if (obj == null) {
                    return;
                }
                if (Boolean.TRUE.equals(GrCameraWatermark.readingCaptureWatermark.get()) && Reflection.hasStringField(obj, "pref_ai_master_watermark_photo_open_state")) {
                    methodHookParam.setResult(Boolean.valueOf(GrCameraWatermark.l(classLoader, symbols)));
                    return;
                }
                boolean zHasStringField = Reflection.hasStringField(obj, "pref_ai_master_watermark_mode_limit_style_id");
                Object objCurrentStyle = PresetSelection.STANDARD_KEY;
                if (zHasStringField) {
                    Object result = methodHookParam.getResult();
                    if (result != null && String.valueOf(result).startsWith("gr_")) {
                        z = true;
                    }
                    if (GrState.isGrMode()) {
                        if (GrCameraWatermark.l(classLoader, symbols)) {
                            objCurrentStyle = z ? result : GrCameraWatermark.currentStyle();
                        }
                        methodHookParam.setResult(objCurrentStyle);
                        return;
                    } else {
                        if (z) {
                            methodHookParam.setResult(GrCameraWatermark.o(classLoader, symbols));
                            return;
                        }
                        return;
                    }
                }
                if (Reflection.hasStringField(obj, "pref_watermark_mode_limit_name")) {
                    if (GrState.isGrMode()) {
                        methodHookParam.setResult("gr");
                        return;
                    } else {
                        if ("gr".equals(methodHookParam.getResult())) {
                            methodHookParam.setResult(PresetSelection.STANDARD_KEY);
                            return;
                        }
                        return;
                    }
                }
                if (Reflection.hasStringField(obj, "pref_ai_master_watermark_mode_limit_open_state")) {
                    if (GrState.isGrMode()) {
                        methodHookParam.setResult(Boolean.valueOf(GrCameraWatermark.l(classLoader, symbols)));
                    }
                } else if (Reflection.hasStringField(obj, GrCameraWatermark.GR_CAPTURE_SWITCH)) {
                    Object result2 = methodHookParam.getResult();
                    if ((result2 == null || PresetSelection.STANDARD_KEY.equals(result2)) && GrState.isGrMode()) {
                        methodHookParam.setResult(Install.ON);
                    }
                }
            }
        };
        XposedBridge.hookMethod(symbols.method("DataManager.get"), xC_MethodHook);
        XposedBridge.hookMethod(symbols.method("DataManager.getOr"), xC_MethodHook);
    }

    private static void hookStyleJson(final Symbols symbols, final ClassLoader classLoader) {
        try {
            XposedBridge.hookMethod(symbols.method("WatermarkStyles.load"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrCameraWatermark.7
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (GrState.isGrMode() || "gr".equals(methodHookParam.args[0])) {
                        String strStyleFor = GrCameraWatermark.styleFor(methodHookParam.getResult(), symbols, classLoader);
                        String strModeLimitDir = Capabilities.modeLimitDir();
                        File file = new File(strModeLimitDir + "/" + strStyleFor + "/" + strStyleFor + ".json");
                        if (!file.isFile()) {
                            file = new File(strModeLimitDir + "/" + strStyleFor + ".json");
                        }
                        if (!file.isFile()) {
                            Log.i(GrCameraWatermark.TAG, "GR watermark style missing under " + strModeLimitDir + ", keeping stock result: " + strStyleFor);
                            return;
                        }
                        try {
                            JSONObject jSONObject = new JSONObject(GrCameraWatermark.readUtf8(file));
                            GrCameraWatermark.localizeFonts(jSONObject);
                            methodHookParam.setResult(jSONObject);
                        } catch (Throwable th) {
                            Log.e(GrCameraWatermark.TAG, "GR watermark style read failed", th);
                        }
                    }
                }

                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (GrState.isGrMode()) {
                        methodHookParam.args[0] = "gr";
                    }
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "GR watermark style hook failed", th);
        }
    }

    private static void hookStyleReceiver(final Symbols symbols, final ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(Application.class, "attach", new Object[]{Context.class, new XC_MethodHook() { // from class: local.jiege.hook.gr.GrCameraWatermark.12
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Application application = (Application) methodHookParam.thisObject;
                if (GrCameraWatermark.styleReceiverRegistered || !"com.oplus.camera".equals(application.getPackageName())) {
                    return;
                }
                application.registerReceiver(new BroadcastReceiver() { // from class: local.jiege.hook.gr.GrCameraWatermark.12.1
                    @Override // android.content.BroadcastReceiver
                    public void onReceive(Context context, Intent intent) {
                        String stringExtra = intent.getStringExtra("camera_watermark_rm_gr_photo_style_id");
                        if (stringExtra == null) {
                            stringExtra = intent.getStringExtra("styleId");
                        }
                        if (stringExtra == null || stringExtra.isEmpty()) {
                            return;
                        }
                        GrState.watermarkStyleId = stringExtra;
                        if (GrState.isGrMode()) {
                            try {
                                AnonymousClass12 anonymousClass12 = AnonymousClass12.this;
                                Symbols symbols2 = symbols;
                                DataStore.put(symbols2, classLoader, DataStore.key(symbols2, "Keys.styleId", "pref_ai_master_watermark_mode_limit_style_id"), stringExtra);
                            } catch (Throwable th) {
                                Log.e(GrCameraWatermark.TAG, "GR watermark style store failed", th);
                            }
                        }
                    }
                }, new IntentFilter(GrCameraWatermark.STYLE_ACTION), 2);
                GrCameraWatermark.styleReceiverRegistered = true;
            }
        }});
    }

    private static void hookStyleRendering(Symbols symbols, ClassLoader classLoader) {
        try {
            XposedBridge.hookMethod(symbols.method("WatermarkModel.styleNormal"), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrCameraWatermark.8
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (GrState.isGrMode()) {
                        methodHookParam.setResult(Boolean.TRUE);
                    }
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "watermark style check hook failed", th);
        }
        try {
            XposedHelpers.findAndHookMethod("com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkJsonReader", classLoader, "getTargetDir", new Object[]{XposedHelpers.findClass("com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkCustomParams", classLoader), new XC_MethodHook() { // from class: local.jiege.hook.gr.GrCameraWatermark.9
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object obj = methodHookParam.args[0];
                    String str = obj == null ? null : (String) XposedHelpers.callMethod(obj, "getStyleId", new Object[0]);
                    if (str == null || !str.contains("gr")) {
                        return;
                    }
                    methodHookParam.setResult("/mode_limit_watermark");
                }
            }});
        } catch (Throwable th2) {
            Log.e(TAG, "getTargetDir hook failed", th2);
        }
        try {
            XposedHelpers.findAndHookMethod("com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkElementProcessor", classLoader, "isNotNeedDrawGrElement", new Object[]{XposedHelpers.findClass("com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkCustomParams", classLoader), XC_MethodReplacement.returnConstant(Boolean.FALSE)});
        } catch (Throwable th3) {
            Log.e(TAG, "isNotNeedDrawGrElement hook failed", th3);
        }
        try {
            Class clsFindClass = XposedHelpers.findClass("com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkElementProcessor", classLoader);
            XposedBridge.hookAllMethods(clsFindClass, "processImage", new XC_MethodHook() { // from class: local.jiege.hook.gr.GrCameraWatermark.10
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Bitmap bitmapDecodeLogo;
                    try {
                        Object[] objArr = methodHookParam.args;
                        if (objArr.length < 4 || !(objArr[3] instanceof ViewGroup)) {
                            return;
                        }
                        Object obj = objArr[1];
                        if (obj instanceof JSONObject) {
                            JSONObject jSONObjectOptJSONObject = ((JSONObject) obj).optJSONObject("content");
                            String strOptString = jSONObjectOptJSONObject == null ? null : jSONObjectOptJSONObject.optString("bitmap");
                            if (strOptString != null) {
                                if (strOptString.startsWith("realme_gr") || strOptString.startsWith("sketch")) {
                                    ViewGroup viewGroup = (ViewGroup) methodHookParam.args[3];
                                    for (int i = 0; i < viewGroup.getChildCount(); i++) {
                                        View childAt = viewGroup.getChildAt(i);
                                        if ((childAt instanceof ImageView) && ((ImageView) childAt).getDrawable() == null && (bitmapDecodeLogo = GrCameraWatermark.decodeLogo(strOptString)) != null) {
                                            ((ImageView) childAt).setImageBitmap(bitmapDecodeLogo);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable th4) {
                        Log.e(GrCameraWatermark.TAG, "GR watermark logo failed", th4);
                    }
                }
            });
            XposedBridge.hookAllMethods(clsFindClass, "readImageFromProvider", new XC_MethodHook() { // from class: local.jiege.hook.gr.GrCameraWatermark.11
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object[] objArr = methodHookParam.args;
                    if (objArr.length >= 2) {
                        Object obj = objArr[1];
                        if (obj instanceof String) {
                            String strSubstring = (String) obj;
                            if (strSubstring.contains("realme_gr") || strSubstring.contains("sketch")) {
                                if (strSubstring.contains("/")) {
                                    strSubstring = strSubstring.substring(strSubstring.lastIndexOf(47) + 1);
                                }
                                Bitmap bitmapDecodeLogo = GrCameraWatermark.decodeLogo(strSubstring);
                                if (bitmapDecodeLogo != null) {
                                    methodHookParam.setResult(bitmapDecodeLogo);
                                }
                            }
                        }
                    }
                }
            });
        } catch (Throwable th4) {
            Log.e(TAG, "GR watermark renderer hooks failed", th4);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void hookWatermarkPartition(Symbols symbols) {
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.gr.GrCameraWatermark.2
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object obj = methodHookParam.args[0];
                if (obj == null) {
                    return;
                }
                if (GrState.isGrMode()) {
                    if (!GrCameraWatermark.holdsAny(obj, GrCameraWatermark.NORMAL_WATERMARK_PREFS)) {
                        return;
                    }
                } else if (!GrCameraWatermark.holdsAny(obj, GrCameraWatermark.GR_WATERMARK_PREFS)) {
                    return;
                }
                methodHookParam.setResult((Object) null);
            }
        };
        XposedBridge.hookMethod(symbols.method("DataManager.put"), xC_MethodHook);
        XposedBridge.hookMethod(symbols.method("DataManager.putSync"), xC_MethodHook);
    }

    public static void install(ClassLoader classLoader) {
        Symbols symbols = Symbols.get();
        Log.guard(TAG, "GR watermark stored values", new a(symbols, classLoader, (byte) 0));
        Log.guard(TAG, "GR watermark capture state", new b(symbols, (byte) 0));
        Log.guard(TAG, "GR watermark partition", new b(symbols, (byte) 1));
        Log.guard(TAG, "GR watermark settings summary", new a(symbols, classLoader, (byte) 1));
        hookEditorLaunch(symbols);
        hookStyleJson(symbols, classLoader);
        hookStyleRendering(symbols, classLoader);
        hookStyleReceiver(symbols, classLoader);
    }

    public static /* bridge */ /* synthetic */ boolean l(ClassLoader classLoader, Symbols symbols) {
        return grWatermarkOn(symbols, classLoader);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$1(Symbols symbols) {
        hookCaptureState(symbols);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$install$2(Symbols symbols) {
        hookWatermarkPartition(symbols);
    }

    private static File localFont(String str) {
        if (str.endsWith(".zip")) {
            str = str.substring(0, str.length() - 4);
        }
        for (String str2 : FONT_DIRS) {
            String[] strArr = {".TTF", ".ttf", ".OTF", ".otf"};
            for (int i = 0; i < 4; i++) {
                File file = new File(str2, c3.u(str, strArr[i]));
                if (file.isFile()) {
                    return file;
                }
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void localizeFonts(Object obj) throws JSONException {
        File fileLocalFont;
        if (!(obj instanceof JSONObject)) {
            if (obj instanceof JSONArray) {
                JSONArray jSONArray = (JSONArray) obj;
                for (int i = 0; i < jSONArray.length(); i++) {
                    localizeFonts(jSONArray.opt(i));
                }
                return;
            }
            return;
        }
        JSONObject jSONObject = (JSONObject) obj;
        if (jSONObject.optInt("fontType", -1) == 2 && jSONObject.has("fontName") && (fileLocalFont = localFont(jSONObject.optString("fontName"))) != null) {
            jSONObject.put("font", fileLocalFont.getAbsolutePath());
            jSONObject.put("fontType", 0);
        }
        Iterator<String> itKeys = jSONObject.keys();
        while (itKeys.hasNext()) {
            localizeFonts(jSONObject.opt(itKeys.next()));
        }
    }

    private static Object nonGrBackupStyle(Symbols symbols, ClassLoader classLoader) {
        try {
            Object obj = DataStore.get(symbols, classLoader, DataStore.key(symbols, "Keys.styleId", "pref_ai_master_watermark_mode_limit_style_id_backup"), PresetSelection.STANDARD_KEY);
            return (obj == null || String.valueOf(obj).startsWith("gr_")) ? PresetSelection.STANDARD_KEY : obj;
        } catch (Throwable unused) {
        }
    }

    public static /* bridge */ /* synthetic */ Object o(ClassLoader classLoader, Symbols symbols) {
        return nonGrBackupStyle(symbols, classLoader);
    }

    public static /* bridge */ /* synthetic */ boolean r(ClassLoader classLoader, Symbols symbols) {
        return watermarkOn(symbols, classLoader, "pref_watermark_capture_switch_key", "pref_ai_master_watermark_photo_style_id");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String readUtf8(File file) throws IOException {
        int length = (int) file.length();
        byte[] bArr = new byte[length];
        FileInputStream fileInputStream = new FileInputStream(file);
        int i = 0;
        while (i < length) {
            try {
                int i2 = fileInputStream.read(bArr, i, length - i);
                if (i2 < 0) {
                    break;
                }
                i += i2;
            } catch (Throwable th) {
                try {
                    fileInputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        }
        fileInputStream.close();
        return new String(bArr, 0, i, "UTF-8");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String styleFor(Object obj, Symbols symbols, ClassLoader classLoader) {
        if (obj instanceof JSONObject) {
            JSONObject jSONObject = (JSONObject) obj;
            if (jSONObject.optString("styleId").startsWith("gr_")) {
                return jSONObject.optString("styleId");
            }
        }
        String strCurrentStyle = currentStyle();
        try {
            Object obj2 = DataStore.get(symbols, classLoader, DataStore.key(symbols, "Keys.styleId", "pref_ai_master_watermark_mode_limit_style_id"), strCurrentStyle);
            if ((obj2 instanceof String) && ((String) obj2).startsWith("gr_")) {
                return (String) obj2;
            }
        } catch (Throwable unused) {
        }
        return strCurrentStyle;
    }

    private static boolean watermarkOn(Symbols symbols, ClassLoader classLoader, String str, String str2) {
        return Install.ON.equals(DataStore.get(symbols, classLoader, DataStore.key(symbols, "Keys.styleId", str), Install.OFF)) && !PresetSelection.STANDARD_KEY.equals(DataStore.get(symbols, classLoader, DataStore.key(symbols, "Keys.styleId", str2), PresetSelection.STANDARD_KEY));
    }
}
