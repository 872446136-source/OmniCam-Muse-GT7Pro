package local.jiege.hook.modes;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import defpackage.c3;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.SymbolSearch;
import local.jiege.hook.filters.FilterScope;
import local.jiege.hook.filters.PresetSelection;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ModeProbe {
    private static final String DB = "/data/user/0/com.oplus.camera/databases/mode_data.db";
    private static final int DUMP_LIMIT = 48;
    private static final String OFF = "/data/adb/omnicam_probe_off";
    private static final String PREFIX = "com.oplus.camera.";
    private static final String TAG = "ModeProbe";
    private static boolean installed;
    private static final String[] OPERATOR = {"np.e", "dq.e"};
    private static final String[] HELPER = {"zp.k", "pq.h"};
    private static final String[] MODE_INFO = {"ia.a", "ja.a"};
    private static final String[] WANTED = {"gr", "retroCamera", FilterScope.MASTER_MODE, "movie", "highPixel", "xpan"};
    private static final long[] DUMP_AT_MS = {8000, 20000};
    private static final AtomicBoolean dumped = new AtomicBoolean();

    private ModeProbe() {
    }

    private static Constructor<?> constructor(Class<?> cls) {
        try {
            Class cls2 = Boolean.TYPE;
            Class cls3 = Integer.TYPE;
            return cls.getDeclaredConstructor(String.class, cls2, String.class, cls3, cls3, cls2);
        } catch (Throwable unused) {
            return null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void dump(String str) {
        SQLiteDatabase sQLiteDatabaseOpenDatabase;
        Cursor cursorRawQuery = null;
        try {
            sQLiteDatabaseOpenDatabase = SQLiteDatabase.openDatabase(DB, null, 1);
            try {
                cursorRawQuery = sQLiteDatabaseOpenDatabase.rawQuery("select mode_name, mode_id, position, rank, support_front, support_edit from mode_info order by position, rank", null);
                StringBuilder sb = new StringBuilder();
                int i = 0;
                while (cursorRawQuery.moveToNext() && i < DUMP_LIMIT) {
                    i++;
                    String string = cursorRawQuery.getString(0);
                    String string2 = cursorRawQuery.getString(2);
                    log("row " + str + " " + string + " id=" + cursorRawQuery.getInt(1) + " " + string2 + " rank=" + cursorRawQuery.getInt(3) + " front=" + cursorRawQuery.getString(4) + " edit=" + cursorRawQuery.getString(5));
                    if ("position_headline".equals(string2)) {
                        if (sb.length() > 0) {
                            sb.append(',');
                        }
                        sb.append(string);
                    }
                }
                Log.i(TAG, "table " + str + ": " + i + " rows, headline: " + ((Object) sb));
                try {
                    cursorRawQuery.close();
                } catch (Throwable unused) {
                }
            } catch (Throwable th) {
                th = th;
                try {
                    Log.e(TAG, "table dump failed", th);
                } finally {
                    if (cursorRawQuery != null) {
                        try {
                            cursorRawQuery.close();
                        } catch (Throwable unused2) {
                        }
                    }
                    if (sQLiteDatabaseOpenDatabase != null) {
                        try {
                            sQLiteDatabaseOpenDatabase.close();
                        } catch (Throwable unused3) {
                        }
                    }
                }
            }
        } catch (Throwable th2) {
            th = th2;
            sQLiteDatabaseOpenDatabase = null;
        }
    }

    private static void dumpLater() {
        if (dumped.compareAndSet(false, true)) {
            Thread thread = new Thread(new Runnable() { // from class: local.jiege.hook.modes.ModeProbe.5
                @Override // java.lang.Runnable
                public void run() {
                    long jCurrentTimeMillis = System.currentTimeMillis();
                    for (long j : ModeProbe.DUMP_AT_MS) {
                        long jCurrentTimeMillis2 = j - (System.currentTimeMillis() - jCurrentTimeMillis);
                        if (jCurrentTimeMillis2 > 0) {
                            try {
                                Thread.sleep(jCurrentTimeMillis2);
                            } catch (InterruptedException unused) {
                                return;
                            }
                        }
                        ModeProbe.dump("+" + j + "ms");
                    }
                }
            }, "mode-probe");
            thread.setDaemon(true);
            thread.start();
        }
    }

    private static Method find(Class<?> cls, String str, Class<?>... clsArr) {
        for (Method method : cls.getDeclaredMethods()) {
            if (str.equals(method.getName())) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == clsArr.length) {
                    for (int i = 0; i < parameterTypes.length; i++) {
                        if (parameterTypes[i].equals(clsArr[i])) {
                        }
                    }
                    return method;
                }
                continue;
            }
        }
        return null;
    }

    private static void hookName(Class<?> cls) {
        try {
            Method methodFind = find(cls, SymbolSearch.FIELD, String.class);
            if (methodFind == null) {
                Log.i(TAG, "name lookup not found");
            } else {
                XposedBridge.hookMethod(methodFind, new XC_MethodHook() { // from class: local.jiege.hook.modes.ModeProbe.2
                    public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        ModeProbe.log("f(" + methodHookParam.args[0] + ") -> " + methodHookParam.getResult());
                    }
                });
                Log.i(TAG, "name lookup hooked");
            }
        } catch (Throwable th) {
            Log.e(TAG, "name lookup hook failed", th);
        }
    }

    private static void hookQuery(Class<?> cls, Class<?> cls2, Class<?> cls3) {
        try {
            Class cls4 = Integer.TYPE;
            Method methodFind = find(cls, "q", cls4);
            if (methodFind == null) {
                Log.i(TAG, "row lookup not found");
                return;
            }
            final Method methodFind2 = find(cls2, "g", cls4);
            final Constructor<?> constructor = constructor(cls3);
            XposedBridge.hookMethod(methodFind, new XC_MethodHook() { // from class: local.jiege.hook.modes.ModeProbe.3
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Object objRow;
                    Object objectExtra = methodHookParam.getObjectExtra(ModeProbe.TAG);
                    long jCurrentTimeMillis = objectExtra instanceof Long ? System.currentTimeMillis() - ((Long) objectExtra).longValue() : -1L;
                    Object result = methodHookParam.getResult();
                    if (result instanceof AtomicReference) {
                        AtomicReference atomicReference = (AtomicReference) result;
                        Object obj = atomicReference.get();
                        int iIntValue = ((Integer) methodHookParam.args[0]).intValue();
                        String strNameOf = ModeProbe.nameOf(methodFind2, iIntValue);
                        StringBuilder sb = new StringBuilder("q(");
                        sb.append(iIntValue);
                        sb.append(") -> ");
                        sb.append(obj == null ? "null" : obj);
                        sb.append(" (");
                        sb.append(jCurrentTimeMillis);
                        sb.append(" ms)");
                        sb.append(strNameOf == null ? PresetSelection.STANDARD_KEY : " name=".concat(strNameOf));
                        ModeProbe.log(sb.toString());
                        if (obj != null || strNameOf == null || !ModeProbe.wanted(strNameOf) || (objRow = ModeProbe.row(constructor, strNameOf, iIntValue)) == null) {
                            return;
                        }
                        ModeProbe.set(atomicReference, objRow);
                        ModeProbe.log("q(" + iIntValue + ") was empty, answered a " + strNameOf + " row");
                    }
                }

                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    methodHookParam.setObjectExtra(ModeProbe.TAG, Long.valueOf(System.currentTimeMillis()));
                }
            });
            Log.i(TAG, "row lookup hooked");
        } catch (Throwable th) {
            Log.e(TAG, "row lookup hook failed", th);
        }
    }

    private static void hookSeed(Class<?> cls) {
        try {
            Method methodFind = find(cls, "h", new Class[0]);
            if (methodFind == null) {
                return;
            }
            XposedBridge.hookMethod(methodFind, new XC_MethodHook() { // from class: local.jiege.hook.modes.ModeProbe.4
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    ModeProbe.log("seeding returned");
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "seeding hook failed", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized void install(ClassLoader classLoader) {
        try {
            if (installed) {
                return;
            }
            installed = true;
            Class<?> clsLoad = load(OPERATOR, classLoader);
            Class<?> clsLoad2 = load(HELPER, classLoader);
            Class<?> clsLoad3 = load(MODE_INFO, classLoader);
            if (clsLoad2 != null) {
                hookName(clsLoad2);
            }
            if (clsLoad != null && clsLoad2 != null && clsLoad3 != null) {
                hookQuery(clsLoad, clsLoad2, clsLoad3);
            }
            if (clsLoad != null) {
                hookSeed(clsLoad);
            }
            if (clsLoad == null || clsLoad2 == null) {
                Log.i(TAG, "probe incomplete: the table dump is all there is");
            }
        } catch (Throwable th) {
            throw th;
        }
    }

    public static void installCamera(final ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "probe off (/data/adb/omnicam_probe_off)");
        } else {
            dumpLater();
            ModeGates.afterApplication(classLoader, new Runnable() { // from class: local.jiege.hook.modes.ModeProbe.1
                @Override // java.lang.Runnable
                public void run() {
                    ModeProbe.install(classLoader);
                }
            });
        }
    }

    private static Class<?> load(String[] strArr, ClassLoader classLoader) {
        for (String str : strArr) {
            String[] strArr2 = {str, c3.u(PREFIX, str)};
            for (int i = 0; i < 2; i++) {
                String str2 = strArr2[i];
                try {
                    Class<?> cls = Class.forName(str2, false, classLoader);
                    Log.i(TAG, str2 + " found");
                    return cls;
                } catch (Throwable unused) {
                }
            }
        }
        Log.i(TAG, strArr[0] + " not found, its lookups are not logged");
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String str) {
        try {
            Log.i(TAG, str);
        } catch (Throwable unused) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String nameOf(Method method, int i) {
        if (method == null) {
            return null;
        }
        try {
            Object objInvoke = method.invoke(null, Integer.valueOf(i));
            if (objInvoke instanceof String) {
                return (String) objInvoke;
            }
        } catch (Throwable unused) {
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Object row(Constructor<?> constructor, String str, int i) {
        String str2;
        if (constructor == null) {
            return null;
        }
        try {
            str2 = str;
            try {
                return constructor.newInstance(str2, Boolean.TRUE, "position_headline", Integer.valueOf(i), 0, Boolean.FALSE);
            } catch (Throwable th) {
                th = th;
                Log.e(TAG, "could not build a row for " + str2, th);
                return null;
            }
        } catch (Throwable th2) {
            th = th2;
            str2 = str;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void set(AtomicReference<?> atomicReference, Object obj) {
        try {
            atomicReference.set(obj);
        } catch (Throwable unused) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean wanted(String str) {
        for (String str2 : WANTED) {
            if (str2.equals(str)) {
                return true;
            }
        }
        return false;
    }
}
