package local.jiege.hook.common;

import android.app.Application;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.t2;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class Diagnostics {
    private static final int KEEP_LINES = 400;
    private static final int MAX_LINES = 2400;
    private static final String MIRRORED = "CameraLog: ";
    private static String apkPath;
    private static int errors;
    private static volatile String masterStock;
    private static int symbolsMissing;
    private static int symbolsResolved;
    private static final Object lock = new Object();
    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static final ArrayDeque<String> kept = new ArrayDeque<>();
    private static final long startTime = System.currentTimeMillis();
    private static volatile boolean filesMounted = true;

    private Diagnostics() {
    }

    public static void begin(String str, ClassLoader classLoader, String str2, int i, int i2) {
        String str3;
        synchronized (lock) {
            apkPath = str2;
            symbolsResolved = i;
            symbolsMissing = i2;
        }
        if ("camera".equals(str)) {
            str3 = "com.oplus.camera.entry.EntryProvider";
        } else {
            str3 = "gallery".equals(str) ? "com.oplus.gallery.openabilitypage.GalleryOpenProvider" : null;
        }
        if (str3 == null) {
            return;
        }
        try {
            XposedBridge.hookMethod(callMethod(XposedHelpers.findClass(str3, classLoader)), new XC_MethodHook() { // from class: local.jiege.hook.common.Diagnostics.1
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if ("jiege.diagnostics".equals(methodHookParam.args[0])) {
                        methodHookParam.setResult(Diagnostics.fromModule() ? Diagnostics.state() : Bundle.EMPTY);
                    }
                }
            });
        } catch (Throwable th) {
            Log.e("Diagnostics", "state request hook unavailable", th);
        }
    }

    private static Method callMethod(Class<?> cls) throws NoSuchMethodException {
        for (Class<?> superclass = cls; superclass != null; superclass = superclass.getSuperclass()) {
            try {
                return superclass.getDeclaredMethod("call", String.class, String.class, Bundle.class);
            } catch (NoSuchMethodException unused) {
            }
        }
        t2.o(cls.getName().concat(".call"));
        return null;
    }

    private static Application currentApplication() {
        try {
            return (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication", null).invoke(null, null);
        } catch (Throwable unused) {
            return null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean fromModule() {
        String[] packagesForUid;
        Application applicationCurrentApplication = currentApplication();
        return (applicationCurrentApplication == null || (packagesForUid = applicationCurrentApplication.getPackageManager().getPackagesForUid(Binder.getCallingUid())) == null || !Arrays.asList(packagesForUid).contains("local.jiege.camera")) ? false : true;
    }

    public static void record(String str, String str2) {
        String str3 = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date()) + " " + Process.myPid() + " " + str + " " + str2;
        synchronized (lock) {
            try {
                if ("E".equals(str)) {
                    errors++;
                }
                lines.addLast(str3);
                while (true) {
                    ArrayDeque<String> arrayDeque = lines;
                    if (arrayDeque.size() > MAX_LINES) {
                        String strRemoveFirst = arrayDeque.removeFirst();
                        if (!strRemoveFirst.contains(MIRRORED)) {
                            ArrayDeque<String> arrayDeque2 = kept;
                            if (arrayDeque2.size() < KEEP_LINES) {
                                arrayDeque2.addLast(strRemoveFirst);
                            }
                        }
                    }
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public static void setFilesMounted(boolean z) {
        filesMounted = z;
    }

    public static void setMasterStock(String str) {
        masterStock = str;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Bundle state() {
        Bundle bundle = new Bundle();
        synchronized (lock) {
            try {
                bundle.putString("apk", apkPath);
                bundle.putInt("symbolsResolved", symbolsResolved);
                bundle.putInt("symbolsMissing", symbolsMissing);
                bundle.putInt("errors", errors);
                bundle.putLong("start", startTime);
                bundle.putInt("pid", Process.myPid());
                StringBuilder sb = new StringBuilder();
                Iterator<String> it = kept.iterator();
                while (it.hasNext()) {
                    sb.append(it.next());
                    sb.append('\n');
                }
                Iterator<String> it2 = lines.iterator();
                while (it2.hasNext()) {
                    sb.append(it2.next());
                    sb.append('\n');
                }
                bundle.putString("log", sb.toString());
            } catch (Throwable th) {
                throw th;
            }
        }
        bundle.putString("masterStock", masterStock);
        bundle.putBoolean("filesMounted", filesMounted);
        bundle.putString("process", Application.getProcessName());
        bundle.putString("moduleApk", Symbols.modulePath());
        bundle.putString("featureStatus", Install.report());
        return bundle;
    }
}
