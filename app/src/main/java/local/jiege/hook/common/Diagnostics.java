package local.jiege.hook.common;

import android.app.Application;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import local.jiege.settings.DiagnosticsContract;

/**
 * Injection state and recent module log lines of this hooked process. The settings app asks the
 * camera and the gallery for them through an exported provider of each (DiagnosticsContract):
 * they cannot see the settings app (package visibility), the settings app can see them.
 */
public final class Diagnostics {
    /**
     * 488 report: the exported log held exactly 600 lines and started four seconds after the
     * process did. Everything the module logged while it was arming — which hooks installed, which
     * class was identified, which field was found — had already been pushed out by the camera's own
     * mirrored lines, so a report that said "POP still blocked, XPAN still wrong" could not say
     * whether the gate had even been installed. The ring is four times longer now, and the module's
     * own lines are moved to a second ring when they would be evicted instead of being dropped.
     */
    private static final int MAX_LINES = 2400;

    /** How many of the module's own lines are kept for the whole life of the process. */
    private static final int KEEP_LINES = 400;

    /** Lines the camera mirror writes; the first to go when the ring is full. */
    private static final String MIRRORED = "CameraLog: ";

    private static final Object lock = new Object();
    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static final ArrayDeque<String> kept = new ArrayDeque<>();
    private static final long startTime = System.currentTimeMillis();
    private static int errors;
    private static String apkPath;
    private static int symbolsResolved;
    private static int symbolsMissing;
    private static volatile String masterStock;
    private static volatile boolean filesMounted = true;

    private Diagnostics() {}

    /** Called by Symbols.install: this process is hooked, its app and the symbol result. */
    static void begin(String section, ClassLoader loader, String appApk, int resolved, int missing) {
        synchronized (lock) {
            apkPath = appApk;
            symbolsResolved = resolved;
            symbolsMissing = missing;
        }
        String provider = "camera".equals(section) ? DiagnosticsContract.CAMERA_PROVIDER_CLASS
            : "gallery".equals(section) ? DiagnosticsContract.GALLERY_PROVIDER_CLASS : null;
        if (provider == null) return;
        try {
            XposedBridge.hookMethod(callMethod(XposedHelpers.findClass(provider, loader)), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!DiagnosticsContract.METHOD_STATE.equals(param.args[0])) return;
                    param.setResult(fromModule() ? state() : Bundle.EMPTY);
                }
            });
        } catch (Throwable error) {
            Log.e("Diagnostics", "state request hook unavailable", error);
        }
    }

    /** call(String, String, Bundle) where the provider or one of its superclasses declares it. */
    private static Method callMethod(Class<?> provider) throws NoSuchMethodException {
        for (Class<?> type = provider; type != null; type = type.getSuperclass()) {
            try {
                return type.getDeclaredMethod("call", String.class, String.class, Bundle.class);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(provider.getName() + ".call");
    }

    /** Whether this process sees the module's /odm files. */
    public static void setFilesMounted(boolean mounted) {
        filesMounted = mounted;
    }

    /** The camera's master filter list as laid out (MasterFilterContract stock list text). */
    public static void setMasterStock(String text) {
        masterStock = text;
    }

    static void record(String level, String line) {
        String stamp = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date());
        String text = stamp + " " + Process.myPid() + " " + level + " " + line;
        synchronized (lock) {
            if ("E".equals(level)) errors++;
            lines.addLast(text);
            while (lines.size() > MAX_LINES) {
                String gone = lines.removeFirst();
                // The module's own lines are the ones a report is read for. The camera mirror writes
                // thousands of lines and would otherwise evict them; those are the ones to lose.
                if (!gone.contains(MIRRORED) && kept.size() < KEEP_LINES) kept.addLast(gone);
            }
        }
    }

    private static boolean fromModule() {
        Application app = currentApplication();
        if (app == null) return false;
        String[] packages = app.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        return packages != null && Arrays.asList(packages).contains(DiagnosticsContract.MODULE_PACKAGE);
    }

    private static Bundle state() {
        Bundle state = new Bundle();
        synchronized (lock) {
            state.putString(DiagnosticsContract.KEY_APK, apkPath);
            state.putInt(DiagnosticsContract.KEY_SYMBOLS_RESOLVED, symbolsResolved);
            state.putInt(DiagnosticsContract.KEY_SYMBOLS_MISSING, symbolsMissing);
            state.putInt(DiagnosticsContract.KEY_ERRORS, errors);
            state.putLong(DiagnosticsContract.KEY_START, startTime);
            state.putInt(DiagnosticsContract.KEY_PID, Process.myPid());
            StringBuilder log = new StringBuilder();
            for (String line : kept) log.append(line).append('\n');
            for (String line : lines) log.append(line).append('\n');
            state.putString(DiagnosticsContract.KEY_LOG, log.toString());
        }
        state.putString(DiagnosticsContract.KEY_MASTER_STOCK, masterStock);
        state.putBoolean(DiagnosticsContract.KEY_FILES_MOUNTED, filesMounted);
        state.putString(DiagnosticsContract.KEY_PROCESS, Application.getProcessName());
        state.putString(DiagnosticsContract.KEY_MODULE_APK, Symbols.modulePath());
        state.putString(DiagnosticsContract.KEY_FEATURE_STATUS, Install.report());
        return state;
    }

    private static Application currentApplication() {
        try {
            return (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
        } catch (Throwable error) {
            return null;
        }
    }
}
