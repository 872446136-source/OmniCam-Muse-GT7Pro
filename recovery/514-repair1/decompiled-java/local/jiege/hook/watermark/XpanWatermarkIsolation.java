package local.jiege.hook.watermark;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.text.TextUtils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import defpackage.c3;
import defpackage.pp1;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import local.jiege.hook.common.DataStore;
import local.jiege.hook.common.Install;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;
import org.json.JSONObject;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class XpanWatermarkIsolation {
    private static final String BACKUP = "pref_ai_master_watermark_photo_style_id_backup";
    private static final String BACKUP_DIR = "/data/user/0/com.oplus.camera/files/jiege/wm_backup";
    private static final String OPEN = "pref_ai_master_watermark_photo_open_state";
    private static final String PHOTO_DIR = "/data/user/0/com.oplus.camera/files/photo_watermark";
    private static final String POP_STYLE = "pld";
    private static final String SAVED_PREFIX = "pref_jiege_wm_";
    private static final String STYLE = "pref_ai_master_watermark_photo_style_id";
    private static final String SWITCH = "pref_watermark_capture_switch_key";
    private static final String TAG = "XpanWatermark";
    private static final String XPAN_MODE = "xpan";
    private static final String XPAN_STYLE_PREFIX = "hassel_style_xpan_";

    private XpanWatermarkIsolation() {
    }

    private static void backupFiles(String str) {
        if (str.isEmpty() || !styleJson(PHOTO_DIR, str).isFile()) {
            return;
        }
        try {
            copyTree(new File(PHOTO_DIR, str).toPath(), new File(BACKUP_DIR, str).toPath());
        } catch (IOException e) {
            Log.e(TAG, "style backup failed ".concat(str), e);
        }
    }

    private static void clear(Symbols symbols, ClassLoader classLoader, Keys keys) {
        DataStore.put(symbols, classLoader, keys.style, PresetSelection.STANDARD_KEY);
        DataStore.put(symbols, classLoader, keys.open, Boolean.FALSE);
        setCapture(symbols, classLoader, keys, Install.OFF);
        DataStore.put(symbols, classLoader, keys.backup, PresetSelection.STANDARD_KEY);
    }

    private static void copyTree(final Path path, final Path path2) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() { // from class: local.jiege.hook.watermark.XpanWatermarkIsolation.3
            @Override // java.nio.file.SimpleFileVisitor, java.nio.file.FileVisitor
            public FileVisitResult preVisitDirectory(Path path3, BasicFileAttributes basicFileAttributes) throws IOException {
                Files.createDirectories(path2.resolve(path.relativize(path3).toString()), new FileAttribute[0]);
                return FileVisitResult.CONTINUE;
            }

            @Override // java.nio.file.SimpleFileVisitor, java.nio.file.FileVisitor
            public FileVisitResult visitFile(Path path3, BasicFileAttributes basicFileAttributes) throws IOException {
                Files.copy(path3, path2.resolve(path.relativize(path3).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static /* bridge */ /* synthetic */ String e(Object obj, Symbols symbols, ClassLoader classLoader) {
        return read(symbols, classLoader, obj, PresetSelection.STANDARD_KEY);
    }

    private static boolean ensureFiles(String str) {
        Application applicationCurrentApplication;
        if (styleJson(PHOTO_DIR, str).isFile()) {
            return true;
        }
        if (styleJson(BACKUP_DIR, str).isFile()) {
            try {
                copyTree(new File(BACKUP_DIR, str).toPath(), new File(PHOTO_DIR, str).toPath());
                return styleJson(PHOTO_DIR, str).isFile();
            } catch (IOException e) {
                Log.e(TAG, "style restore failed " + str, e);
                return false;
            }
        }
        if (str.matches("hassel_(?:style_(?:[1-4]|xpan_1|master_mode_1)|text_style_[12])") && (applicationCurrentApplication = AndroidAppHelper.currentApplication()) != null) {
            try {
                InputStream inputStreamOpen = applicationCurrentApplication.createPackageContext("com.coloros.gallery3d", 0).getAssets().open("watermark_master_styles/" + str + ".json");
                try {
                    boolean z = inputStreamOpen.read() >= 0;
                    inputStreamOpen.close();
                    return z;
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
            } catch (Exception unused) {
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void enterXpan(Symbols symbols, ClassLoader classLoader, State state) {
        Object obj = state.live.style;
        String strConcat = PresetSelection.STANDARD_KEY;
        String str = read(symbols, classLoader, obj, PresetSelection.STANDARD_KEY);
        if (str.startsWith(XPAN_STYLE_PREFIX)) {
            return;
        }
        if (!str.isEmpty()) {
            save(symbols, classLoader, state.live, state.other);
        }
        boolean zRestore = restore(symbols, classLoader, state.xpan, state.live);
        if (!zRestore) {
            clear(symbols, classLoader, state.live);
        }
        StringBuilder sb = new StringBuilder("entered XPAN, ");
        sb.append(zRestore ? "restored XPAN watermark" : "no saved XPAN watermark");
        if (!str.isEmpty()) {
            strConcat = ", set aside ".concat(str);
        }
        sb.append(strConcat);
        Log.i(TAG, sb.toString());
    }

    public static /* bridge */ /* synthetic */ File f(String str) {
        return styleJson(PHOTO_DIR, str);
    }

    private static void hookStyleJson(final Symbols symbols, final State state, final ClassLoader classLoader) {
        XposedBridge.hookMethod(symbols.method("WatermarkStyles.load"), new XC_MethodHook() { // from class: local.jiege.hook.watermark.XpanWatermarkIsolation.2
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (Boolean.TRUE.equals(methodHookParam.args[3]) || "gr".equals(methodHookParam.args[0]) || !Settings.get("xpan_watermark_isolation")) {
                    return;
                }
                try {
                    synchronized (state) {
                        try {
                            State state2 = state;
                            if (state2.live == null) {
                                XpanWatermarkIsolation.init(symbols, state2);
                            }
                        } catch (Throwable th) {
                            throw th;
                        }
                    }
                    String strE = XpanWatermarkIsolation.e(state.live.style, symbols, classLoader);
                    Object result = methodHookParam.getResult();
                    String strOptString = result instanceof JSONObject ? ((JSONObject) result).optString("styleId") : PresetSelection.STANDARD_KEY;
                    if (strE.isEmpty()) {
                        if (strOptString.startsWith("hassel_")) {
                            methodHookParam.setResult((Object) null);
                            return;
                        }
                        return;
                    }
                    if (!strE.equals(strOptString) && !XpanWatermarkIsolation.POP_STYLE.equals(strOptString)) {
                        File fileF = XpanWatermarkIsolation.f(strE);
                        if (fileF.isFile()) {
                            methodHookParam.setResult(new JSONObject(new String(Files.readAllBytes(fileF.toPath()), StandardCharsets.UTF_8)));
                            StringBuilder sb = new StringBuilder("photo watermark JSON was ");
                            if (strOptString.isEmpty()) {
                                strOptString = "missing";
                            }
                            sb.append(strOptString);
                            sb.append(", using ");
                            sb.append(strE);
                            Log.i(XpanWatermarkIsolation.TAG, sb.toString());
                        }
                    }
                } catch (Throwable th2) {
                    Log.e(XpanWatermarkIsolation.TAG, "watermark JSON check failed", th2);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void init(Symbols symbols, State state) {
        Keys keys = new Keys(0);
        keys.style = DataStore.key(symbols, "Keys.styleId", STYLE);
        keys.backup = DataStore.key(symbols, "Keys.styleId", BACKUP);
        keys.open = DataStore.key(symbols, "Keys.styleId", OPEN);
        keys.capture = DataStore.key(symbols, "Keys.styleId", SWITCH);
        state.xpan = savedKeys(symbols, XPAN_MODE);
        state.other = savedKeys(symbols, "other");
        state.live = keys;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void install(final ClassLoader classLoader) {
        final Symbols symbols = Symbols.get();
        symbols.method("DataKeys.prefs");
        final State state = new State(0);
        XposedBridge.hookMethod(symbols.method("ModeSwitch.switch"), new XC_MethodHook() { // from class: local.jiege.hook.watermark.XpanWatermarkIsolation.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                Object[] objArr = methodHookParam.args;
                String str = (String) objArr[0];
                String str2 = (String) objArr[1];
                if (TextUtils.isEmpty(str2) || !Settings.get("xpan_watermark_isolation")) {
                    return;
                }
                try {
                    synchronized (state) {
                        try {
                            State state2 = state;
                            if (state2.live == null) {
                                XpanWatermarkIsolation.init(symbols, state2);
                            }
                        } catch (Throwable th) {
                            throw th;
                        }
                    }
                    if (XpanWatermarkIsolation.XPAN_MODE.equals(str2)) {
                        XpanWatermarkIsolation.enterXpan(symbols, classLoader, state);
                    } else if (XpanWatermarkIsolation.XPAN_MODE.equals(str) || TextUtils.isEmpty(str)) {
                        XpanWatermarkIsolation.leaveXpan(symbols, classLoader, state);
                    }
                } catch (Throwable th2) {
                    Log.e(XpanWatermarkIsolation.TAG, "slot swap failed " + str + " -> " + str2, th2);
                }
            }
        });
        hookStyleJson(symbols, state, classLoader);
        Log.i(TAG, "installed");
    }

    public static void installCamera(ClassLoader classLoader) {
        Log.guard(TAG, "XPAN watermark isolation", new pp1(classLoader, (byte) 5));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void leaveXpan(Symbols symbols, ClassLoader classLoader, State state) {
        String str = read(symbols, classLoader, state.live.style, PresetSelection.STANDARD_KEY);
        if (str.isEmpty() || str.startsWith(XPAN_STYLE_PREFIX)) {
            if (!str.isEmpty()) {
                save(symbols, classLoader, state.live, state.xpan);
            }
            boolean zRestore = restore(symbols, classLoader, state.other, state.live);
            if (!zRestore) {
                clear(symbols, classLoader, state.live);
            }
            StringBuilder sb = new StringBuilder("left XPAN, saved ");
            sb.append(str);
            sb.append(zRestore ? ", other watermark restored" : ", slot cleared");
            Log.i(TAG, sb.toString());
        }
    }

    private static String read(Symbols symbols, ClassLoader classLoader, Object obj, Object obj2) {
        Object obj3 = DataStore.get(symbols, classLoader, obj, obj2);
        if (obj3 != null) {
            obj2 = obj3;
        }
        return String.valueOf(obj2);
    }

    private static boolean restore(Symbols symbols, ClassLoader classLoader, Keys keys, Keys keys2) {
        String str = read(symbols, classLoader, keys.style, PresetSelection.STANDARD_KEY);
        if (str.isEmpty()) {
            return false;
        }
        if (!ensureFiles(str)) {
            Log.i(TAG, "style files are gone, not restored: ".concat(str));
            return false;
        }
        DataStore.put(symbols, classLoader, keys2.style, str);
        DataStore.put(symbols, classLoader, keys2.backup, read(symbols, classLoader, keys.backup, str));
        DataStore.put(symbols, classLoader, keys2.open, Boolean.valueOf(read(symbols, classLoader, keys.open, "false")));
        setCapture(symbols, classLoader, keys2, read(symbols, classLoader, keys.capture, Install.OFF));
        DataStore.put(symbols, classLoader, keys.style, PresetSelection.STANDARD_KEY);
        return true;
    }

    private static void save(Symbols symbols, ClassLoader classLoader, Keys keys, Keys keys2) {
        String str = read(symbols, classLoader, keys.style, PresetSelection.STANDARD_KEY);
        DataStore.put(symbols, classLoader, keys2.style, str);
        DataStore.put(symbols, classLoader, keys2.backup, read(symbols, classLoader, keys.backup, PresetSelection.STANDARD_KEY));
        DataStore.put(symbols, classLoader, keys2.open, read(symbols, classLoader, keys.open, Boolean.FALSE));
        DataStore.put(symbols, classLoader, keys2.capture, read(symbols, classLoader, keys.capture, Install.OFF));
        backupFiles(str);
    }

    private static Keys savedKeys(Symbols symbols, String str) {
        Keys keys = new Keys(0);
        keys.style = symbols.call("DataKeys.prefs", null, String.class, c3.k(SAVED_PREFIX, str, "_style"));
        keys.backup = symbols.call("DataKeys.prefs", null, String.class, c3.k(SAVED_PREFIX, str, "_backup"));
        keys.open = symbols.call("DataKeys.prefs", null, String.class, c3.k(SAVED_PREFIX, str, "_open"));
        keys.capture = symbols.call("DataKeys.prefs", null, String.class, c3.k(SAVED_PREFIX, str, "_switch"));
        return keys;
    }

    private static void setCapture(Symbols symbols, ClassLoader classLoader, Keys keys, String str) {
        symbols.call("DataManager.putSync", DataStore.manager(classLoader), keys.capture, str, Boolean.FALSE);
    }

    private static File styleJson(String str, String str2) {
        return new File(str + "/" + str2 + "/" + str2 + ".json");
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class Keys {
        Object backup;
        Object capture;
        Object open;
        Object style;

        public /* synthetic */ Keys(int i) {
            this();
        }

        private Keys() {
        }
    }

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class State {
        Keys live;
        Keys other;
        Keys xpan;

        public /* synthetic */ State(int i) {
            this();
        }

        private State() {
        }
    }
}
