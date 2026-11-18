package local.jiege.hook.watermark;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import defpackage.hd;
import defpackage.t2;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import local.jiege.hook.common.DataStore;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class HasselResources {
    private static final Map<String, String> CACHE = new HashMap();
    private static final String GALLERY = "com.coloros.gallery3d";
    private static final String TAG = "HasselResources";

    private HasselResources() {
    }

    public static void installCamera(ClassLoader classLoader) {
        Log.guard(TAG, "gallery style resources", new hd(classLoader, (byte) 5));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$0(final ClassLoader classLoader) {
        final Symbols symbols = Symbols.get();
        symbols.method("DataManager.getOr");
        XposedBridge.hookMethod(symbols.method("WatermarkStyles.load"), new XC_MethodHook() { // from class: local.jiege.hook.watermark.HasselResources.1
            private String lastFailure;
            private volatile Object styleKey;

            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Application applicationCurrentApplication;
                JSONObject jSONObject;
                if (methodHookParam.args.length < 4 || methodHookParam.hasThrowable() || Boolean.TRUE.equals(methodHookParam.args[3]) || "gr".equals(methodHookParam.args[0]) || "retroCamera".equals(methodHookParam.args[0]) || (applicationCurrentApplication = AndroidAppHelper.currentApplication()) == null) {
                    return;
                }
                try {
                    Object objKey = this.styleKey;
                    if (objKey == null) {
                        objKey = DataStore.key(symbols, "Keys.styleId", "pref_ai_master_watermark_photo_style_id");
                        this.styleKey = objKey;
                    }
                    Object obj = DataStore.get(symbols, classLoader, objKey, PresetSelection.STANDARD_KEY);
                    String str = obj instanceof String ? (String) obj : PresetSelection.STANDARD_KEY;
                    if (str.matches("hassel_[A-Za-z0-9_]+")) {
                        Object result = methodHookParam.getResult();
                        if ((result instanceof JSONObject) && "pld".equals(((JSONObject) result).optString("styleId"))) {
                            return;
                        }
                        Context contextCreatePackageContext = applicationCurrentApplication.createPackageContext(HasselResources.GALLERY, 0);
                        long longVersionCode = contextCreatePackageContext.getPackageManager().getPackageInfo(HasselResources.GALLERY, 0).getLongVersionCode();
                        if ((result instanceof JSONObject) && str.equals(((JSONObject) result).optString("styleId"))) {
                            jSONObject = new JSONObject(result.toString());
                        } else {
                            String str2 = longVersionCode + ":" + str;
                            synchronized (HasselResources.CACHE) {
                                String str3 = (String) HasselResources.CACHE.get(str2);
                                if (str3 == null) {
                                    InputStream inputStreamOpen = contextCreatePackageContext.getAssets().open("watermark_master_styles/" + str + ".json");
                                    try {
                                        str3 = new String(inputStreamOpen.readAllBytes(), StandardCharsets.UTF_8);
                                        inputStreamOpen.close();
                                        HasselResources.CACHE.put(str2, str3);
                                    } catch (Throwable th) {
                                        if (inputStreamOpen != null) {
                                            try {
                                                inputStreamOpen.close();
                                            } catch (Throwable th2) {
                                                th.addSuppressed(th2);
                                            }
                                        }
                                        throw th;
                                    }
                                }
                                jSONObject = new JSONObject(str3);
                            }
                        }
                        if (!str.equals(jSONObject.optString("styleId"))) {
                            throw new IllegalStateException("Gallery style ID differs from requested ".concat(str));
                        }
                        File file = new File(applicationCurrentApplication.getFilesDir(), "jiege/hassel_resources/" + longVersionCode);
                        synchronized (HasselResources.CACHE) {
                            HasselResources.rewrite(jSONObject, applicationCurrentApplication, contextCreatePackageContext, file);
                        }
                        methodHookParam.setResult(jSONObject);
                        this.lastFailure = null;
                    }
                } catch (Throwable th3) {
                    String string = th3.toString();
                    if (string.equals(this.lastFailure)) {
                        return;
                    }
                    this.lastFailure = string;
                    Log.e(HasselResources.TAG, "style resources unavailable", th3);
                }
            }
        });
        Log.i(TAG, "installed target gallery asset fallback");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void rewrite(Object obj, Context context, Context context2, File file) throws JSONException, IOException {
        if (obj instanceof JSONArray) {
            JSONArray jSONArray = (JSONArray) obj;
            for (int i = 0; i < jSONArray.length(); i++) {
                rewrite(jSONArray.get(i), context, context2, file);
            }
            return;
        }
        if (obj instanceof JSONObject) {
            JSONObject jSONObject = (JSONObject) obj;
            if (jSONObject.optInt("bitmapSourceType", -1) == 0) {
                String strOptString = jSONObject.optString("bitmap");
                if (strOptString.matches("[A-Za-z0-9_]+") && context.getResources().getIdentifier(strOptString, "drawable", context.getPackageName()) == 0) {
                    int identifier = context2.getResources().getIdentifier(strOptString, "drawable", GALLERY);
                    if (identifier == 0) {
                        t2.j("Gallery drawable missing: ".concat(strOptString));
                        return;
                    }
                    File file2 = new File(file, strOptString.concat(".png"));
                    if (!file2.isFile()) {
                        if (!file.isDirectory() && !file.mkdirs()) {
                            t2.l(file, "Cannot create ");
                            return;
                        }
                        Drawable drawable = context2.getResources().getDrawable(identifier, context2.getTheme());
                        int intrinsicWidth = drawable.getIntrinsicWidth();
                        int intrinsicHeight = drawable.getIntrinsicHeight();
                        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
                            t2.j("Invalid drawable dimensions: ".concat(strOptString));
                            return;
                        }
                        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888);
                        try {
                            drawable.setBounds(0, 0, intrinsicWidth, intrinsicHeight);
                            drawable.draw(new Canvas(bitmapCreateBitmap));
                            File file3 = new File(file, strOptString.concat(".tmp"));
                            FileOutputStream fileOutputStream = new FileOutputStream(file3);
                            try {
                                if (!bitmapCreateBitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)) {
                                    throw new IOException("Encode failed: ".concat(strOptString));
                                }
                                fileOutputStream.close();
                                Files.move(file3.toPath(), file2.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                bitmapCreateBitmap.recycle();
                                Log.i(TAG, "copied missing camera drawable ".concat(strOptString));
                            } catch (Throwable th) {
                                try {
                                    fileOutputStream.close();
                                } catch (Throwable th2) {
                                    th.addSuppressed(th2);
                                }
                                throw th;
                            }
                        } catch (Throwable th3) {
                            bitmapCreateBitmap.recycle();
                            throw th3;
                        }
                        bitmapCreateBitmap.recycle();
                        throw th3;
                    }
                    jSONObject.put("bitmapSourceType", 1);
                    jSONObject.put("bitmap", file2.getAbsolutePath());
                }
            }
            Iterator<String> itKeys = jSONObject.keys();
            while (itKeys.hasNext()) {
                rewrite(jSONObject.get(itKeys.next()), context, context2, file);
            }
        }
    }
}
