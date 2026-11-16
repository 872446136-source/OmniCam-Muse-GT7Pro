package local.jiege.hook.watermark;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import local.jiege.hook.common.DataStore;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import org.json.JSONArray;
import org.json.JSONObject;

/** Uses matching Hassel style assets already installed in the target gallery. */
public final class HasselResources {
    private static final String GALLERY = "com.coloros.gallery3d";
    private static final String TAG = "HasselResources";
    private static final Map<String, String> CACHE = new HashMap<>();
    private HasselResources() {}

    public static void installCamera(ClassLoader loader) {
        Log.guard(TAG, "gallery style resources", () -> {
            Symbols symbols = Symbols.get();
            symbols.method("DataManager.getOr");
            XposedBridge.hookMethod(symbols.method("WatermarkStyles.load"), new XC_MethodHook() {
                // Resolving this key during package binding deadlocks PrefKeyGlobal against
                // DataMigrateHelper. The first watermark request happens after camera startup.
                private volatile Object styleKey;
                private String lastFailure;

                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length < 4 || param.hasThrowable()
                            || Boolean.TRUE.equals(param.args[3]) || "gr".equals(param.args[0])
                            || "retroCamera".equals(param.args[0])) return;
                    Context camera = AndroidAppHelper.currentApplication();
                    if (camera == null) return;
                    try {
                        Object key = styleKey;
                        if (key == null) {
                            key = DataStore.key(symbols, "Keys.styleId", "pref_ai_master_watermark_photo_style_id");
                            styleKey = key;
                        }
                        Object value = DataStore.get(symbols, loader, key, "");
                        String style = value instanceof String ? (String) value : "";
                        if (!style.matches("hassel_[A-Za-z0-9_]+")) return;
                        Object original = param.getResult();
                        if (original instanceof JSONObject && "pld".equals(((JSONObject) original).optString("styleId"))) return;
                        Context gallery = camera.createPackageContext(GALLERY, 0);
                        long version = gallery.getPackageManager().getPackageInfo(GALLERY, 0).getLongVersionCode();
                        JSONObject json;
                        if (original instanceof JSONObject && style.equals(((JSONObject) original).optString("styleId"))) {
                            json = new JSONObject(original.toString());
                        } else {
                            String cacheKey = version + ":" + style;
                            synchronized (CACHE) {
                                String cached = CACHE.get(cacheKey);
                                if (cached == null) {
                                    try (java.io.InputStream input = gallery.getAssets().open("watermark_master_styles/" + style + ".json")) {
                                        cached = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                                    }
                                    CACHE.put(cacheKey, cached);
                                }
                                json = new JSONObject(cached);
                            }
                        }
                        if (!style.equals(json.optString("styleId"))) {
                            throw new IllegalStateException("Gallery style ID differs from requested " + style);
                        }
                        File directory = new File(camera.getFilesDir(), "jiege/hassel_resources/" + version);
                        synchronized (CACHE) { rewrite(json, camera, gallery, directory); }
                        param.setResult(json);
                        lastFailure = null;
                    } catch (Throwable error) {
                        String failure = error.toString();
                        if (!failure.equals(lastFailure)) {
                            lastFailure = failure;
                            Log.e(TAG, "style resources unavailable", error);
                        }
                    }
                }
            });
            Log.i(TAG, "installed target gallery asset fallback");
        });
    }

    private static void rewrite(Object value, Context camera, Context gallery, File directory) throws Exception {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) rewrite(array.get(i), camera, gallery, directory);
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.optInt("bitmapSourceType", -1) == 0) {
                String name = object.optString("bitmap");
                if (name.matches("[A-Za-z0-9_]+")
                    && camera.getResources().getIdentifier(name, "drawable", camera.getPackageName()) == 0) {
                    int id = gallery.getResources().getIdentifier(name, "drawable", GALLERY);
                    if (id == 0) throw new IllegalStateException("Gallery drawable missing: " + name);
                    File file = new File(directory, name + ".png");
                    if (!file.isFile()) {
                        if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cannot create " + directory);
                        Drawable drawable = gallery.getResources().getDrawable(id, gallery.getTheme());
                        int width = drawable.getIntrinsicWidth(), height = drawable.getIntrinsicHeight();
                        if (width <= 0 || height <= 0) throw new IllegalStateException("Invalid drawable dimensions: " + name);
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        try {
                            drawable.setBounds(0, 0, width, height);
                            drawable.draw(new Canvas(bitmap));
                            File temporary = new File(directory, name + ".tmp");
                            try (FileOutputStream output = new FileOutputStream(temporary)) {
                                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new java.io.IOException("Encode failed: " + name);
                            }
                            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } finally { bitmap.recycle(); }
                        Log.i(TAG, "copied missing camera drawable " + name);
                    }
                    object.put("bitmapSourceType", 1);
                    object.put("bitmap", file.getAbsolutePath());
                }
            }
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) rewrite(object.get(keys.next()), camera, gallery, directory);
        }
    }
}
