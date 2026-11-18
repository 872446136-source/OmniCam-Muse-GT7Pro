package local.jiege.hook.natural;

import android.hardware.camera2.CaptureRequest;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import defpackage.t2;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.FilterScope;
import local.jiege.hook.filters.PresetSelection;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class NaturalMode {
    private static final long CHECK_INTERVAL_MS = 500;
    private static final double EPS_SOFTEST = 0.001d;
    private static final double EPS_STOCK = 1.0E-7d;
    private static final String STOCK_JSON = "/odm/etc/camera/conf_tuning_params.json";
    private static final String TAG = "NaturalMode";
    private static long checkedAt;
    private static final File ROOT = new File("/data/user/0/com.oplus.camera/files/jiege/natural");
    private static String appliedConfig = PresetSelection.STANDARD_KEY;
    private static String appliedSoften = PresetSelection.STANDARD_KEY;

    private NaturalMode() {
    }

    private static double epsFor(int i) {
        double dMin = ((double) Math.min(100, i)) / 100.0d;
        double dLog10 = Math.log10(EPS_STOCK);
        return Math.pow(10.0d, ((Math.log10(EPS_SOFTEST) - dLog10) * dMin) + dLog10);
    }

    public static void installCamera(ClassLoader classLoader) {
        ROOT.mkdirs();
        Log.guard(TAG, "soften mode isolation", new t2((byte) 28));
        Log.guard(TAG, "settings sync", new t2((byte) 29));
        Log.i(TAG, "installed");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$0() {
        XposedBridge.hookMethod(Symbols.get().method("ModeSwitch.switch"), new XC_MethodHook() { // from class: local.jiege.hook.natural.NaturalMode.1
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                String str = (String) methodHookParam.args[1];
                if (str == null || str.isEmpty()) {
                    return;
                }
                NaturalMode.setSoftenMode((FilterScope.MASTER_MODE.equals(str) || "gr".equals(str)) ? false : true);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$1() {
        XposedBridge.hookAllMethods(CaptureRequest.Builder.class, "build", new XC_MethodHook() { // from class: local.jiege.hook.natural.NaturalMode.2
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                NaturalMode.sync();
            }
        });
    }

    private static String read(File file) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        try {
            String str = new String(fileInputStream.readAllBytes(), StandardCharsets.UTF_8);
            fileInputStream.close();
            return str;
        } catch (Throwable th) {
            try {
                fileInputStream.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static native void setSoftenMode(boolean z);

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized void sync() {
        try {
            long jCurrentTimeMillis = System.currentTimeMillis();
            if (jCurrentTimeMillis - checkedAt < CHECK_INTERVAL_MS) {
                return;
            }
            checkedAt = jCurrentTimeMillis;
            boolean z = Settings.get("natural_mode");
            boolean z2 = true;
            boolean z3 = z && Settings.get("natural_no_ai_detail");
            if (!z || !Settings.get("natural_no_lce")) {
                z2 = false;
            }
            syncConfig(z3, z2);
            syncSoften(z ? Settings.getInt("natural_soften") : 0);
        } catch (Throwable th) {
            throw th;
        }
    }

    private static void syncConfig(boolean z, boolean z2) {
        String str;
        if (z || z2) {
            str = "ai=" + z + ",lce=" + z2;
        } else {
            str = PresetSelection.STANDARD_KEY;
        }
        if (str.equals(appliedConfig)) {
            return;
        }
        File file = new File(ROOT, "conf_tuning_params.json");
        try {
            if (str.isEmpty()) {
                file.delete();
            } else {
                write(file, NaturalConfig.apply(read(new File("/odm/etc/camera/conf_tuning_params.json")), z, z2));
            }
            appliedConfig = str;
            if (str.isEmpty()) {
                str = "stock";
            }
            Log.i(TAG, "tuning json: ".concat(str));
        } catch (Exception e) {
            Log.e(TAG, "tuning json", e);
            file.delete();
            appliedConfig = PresetSelection.STANDARD_KEY;
        }
    }

    private static void syncSoften(int i) {
        String str = i <= 0 ? PresetSelection.STANDARD_KEY : String.format(Locale.ROOT, "%.3e", Double.valueOf(epsFor(i)));
        if (str.equals(appliedSoften)) {
            return;
        }
        File file = new File(ROOT, "soften");
        try {
            if (str.isEmpty()) {
                file.delete();
            } else {
                write(file, str);
            }
            appliedSoften = str;
            StringBuilder sb = new StringBuilder("soften ");
            sb.append(i);
            sb.append("%, eps ");
            if (str.isEmpty()) {
                str = "stock";
            }
            sb.append(str);
            Log.i(TAG, sb.toString());
        } catch (IOException e) {
            Log.e(TAG, "soften", e);
            file.delete();
            appliedSoften = PresetSelection.STANDARD_KEY;
        }
    }

    private static void write(File file, String str) throws IOException {
        File file2 = new File(file.getPath() + ".tmp");
        FileOutputStream fileOutputStream = new FileOutputStream(file2);
        try {
            fileOutputStream.write(str.getBytes(StandardCharsets.UTF_8));
            fileOutputStream.close();
            if (file2.renameTo(file)) {
                return;
            }
            t2.l(file2, "rename ");
        } catch (Throwable th) {
            try {
                fileOutputStream.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }
}
