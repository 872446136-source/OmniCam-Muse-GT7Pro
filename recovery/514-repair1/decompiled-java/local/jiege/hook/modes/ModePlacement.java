package local.jiege.hook.modes;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import defpackage.c3;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ModePlacement {
    private static final String CONFIG = "com.oplus.camera.configure.CameraConfig";
    private static final String DB = "/data/user/0/com.oplus.camera/databases/mode_data.db";
    private static final String HEADLINE = "com.oplus.feature.move.mode.to.headline";
    private static final String HEADLINE_POSITION = "position_headline";
    private static final String MASTER = "professional";
    private static final String MORE = "more";
    private static final String OFF = "/data/adb/omnicam_placement_off";
    private static final String[] OPERATOR = {"np.e", "dq.e"};
    private static final String PANEL = "com.oplus.feature.move.mode.to.panel";
    private static final String PANEL_POSITION = "position_mode_panel";
    private static final String PREFIX = "com.oplus.camera.";
    private static final String READER = "u";
    private static final String TAG = "ModePlacement";
    private static final String XPAN = "xpan";
    private static final int XPAN_HEADLINE_RANK = 6;
    private static final int XPAN_PANEL_RANK = 3;
    private static boolean installed;
    private static boolean tableDone;

    private ModePlacement() {
    }

    private static Method find(Class<?> cls) {
        for (Method method : cls.getDeclaredMethods()) {
            if (READER.equals(method.getName())) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1 && parameterTypes[0] == String.class && List.class.isAssignableFrom(method.getReturnType())) {
                    return method;
                }
            }
        }
        return null;
    }

    private static void hookConfig(ClassLoader classLoader) {
        try {
            Method methodFind = find(Class.forName(CONFIG, false, classLoader));
            if (methodFind == null) {
                Log.i(TAG, "com.oplus.camera.configure.CameraConfig.u not found, the config move is skipped");
                return;
            }
            try {
                XposedBridge.hookMethod(methodFind, new XC_MethodHook() { // from class: local.jiege.hook.modes.ModePlacement.2
                    public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        String str = (String) methodHookParam.args[0];
                        if (ModePlacement.HEADLINE.equals(str)) {
                            ModePlacement.serveHeadline(methodHookParam);
                            return;
                        }
                        if (ModePlacement.PANEL.equals(str)) {
                            Object result = methodHookParam.getResult();
                            if (result instanceof List) {
                                List list = (List) result;
                                ArrayList arrayList = new ArrayList(list.size());
                                int i = 0;
                                while (true) {
                                    if (i >= list.size()) {
                                        break;
                                    }
                                    Object obj = list.get(i);
                                    if ("professional".equals(obj)) {
                                        i++;
                                    } else {
                                        arrayList.add(obj);
                                    }
                                    i++;
                                }
                                if (ModePlacement.promoteXpanInPanel(arrayList) ? true : arrayList.size() != list.size()) {
                                    methodHookParam.setResult(arrayList);
                                    ModePlacement.log("panel move served: " + arrayList);
                                }
                            }
                        }
                    }
                });
                Log.i(TAG, "panel move hooked, 大师模式 and XPAN go to the bar");
            } catch (Throwable th) {
                Log.e(TAG, "panel move hook failed", th);
            }
        } catch (Throwable th2) {
            Log.e(TAG, "config class unavailable", th2);
        }
    }

    private static void hookSeeding(final ClassLoader classLoader) {
        Method method = null;
        Class<?> cls = null;
        for (String str : OPERATOR) {
            String[] strArr = {str, c3.u(PREFIX, str)};
            for (int i = 0; i < 2; i++) {
                try {
                    cls = Class.forName(strArr[i], false, classLoader);
                    break;
                } catch (Throwable unused) {
                }
            }
            if (cls != null) {
                break;
            }
        }
        if (cls == null) {
            Log.i(TAG, OPERATOR[0] + " not found, the row is only fixed at the next start");
            return;
        }
        for (Method method2 : cls.getDeclaredMethods()) {
            if ("h".equals(method2.getName()) && method2.getParameterTypes().length == 0) {
                method = method2;
                break;
            }
        }
        if (method == null) {
            Log.i(TAG, "seeding method not found, the row is only fixed at the next start");
            return;
        }
        try {
            XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.modes.ModePlacement.3
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    ModePlacement.placeXpan(classLoader, "after seeding");
                }
            });
            Log.i(TAG, "seeding hooked, XPAN is written into the table as a headline row");
        } catch (Throwable th) {
            Log.e(TAG, "seeding hook failed", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized void install(ClassLoader classLoader) {
        if (installed) {
            return;
        }
        installed = true;
        placeXpan(classLoader, "start");
        hookConfig(classLoader);
        hookSeeding(classLoader);
    }

    public static void installCamera(final ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "placement off (/data/adb/omnicam_placement_off)");
        } else {
            ModeGates.afterApplication(classLoader, new Runnable() { // from class: local.jiege.hook.modes.ModePlacement.1
                @Override // java.lang.Runnable
                public void run() {
                    ModePlacement.install(classLoader);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void log(String str) {
        try {
            Log.i(TAG, str);
        } catch (Throwable unused) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static synchronized void placeXpan(ClassLoader classLoader, String str) {
        Cursor cursorRawQuery;
        try {
            if (!new File(DB).isFile()) {
                return;
            }
            SQLiteDatabase sQLiteDatabase = null;
            try {
                try {
                    SQLiteDatabase sQLiteDatabaseOpenDatabase = SQLiteDatabase.openDatabase(DB, null, 0);
                    try {
                        cursorRawQuery = sQLiteDatabaseOpenDatabase.rawQuery("select position, rank from mode_info where mode_name = ?", new String[]{XPAN});
                        try {
                            if (!cursorRawQuery.moveToFirst()) {
                                Log.i(TAG, "no xpan row in the table (" + str + ")");
                                try {
                                    cursorRawQuery.close();
                                } catch (Throwable unused) {
                                }
                                try {
                                    sQLiteDatabaseOpenDatabase.close();
                                } catch (Throwable unused2) {
                                }
                                return;
                            }
                            String string = cursorRawQuery.getString(0);
                            int i = cursorRawQuery.getInt(1);
                            cursorRawQuery.close();
                            if (HEADLINE_POSITION.equals(string)) {
                                if (!tableDone) {
                                    Log.i(TAG, "xpan already in the bar at rank " + i + " (" + str + ")");
                                }
                                tableDone = true;
                                try {
                                    sQLiteDatabaseOpenDatabase.close();
                                } catch (Throwable unused3) {
                                }
                                return;
                            }
                            Cursor cursorRawQuery2 = sQLiteDatabaseOpenDatabase.rawQuery("select max(rank) from mode_info where position = ? and mode_name != ?", new String[]{HEADLINE_POSITION, MORE});
                            try {
                                int i2 = (!cursorRawQuery2.moveToFirst() || cursorRawQuery2.isNull(0)) ? 1 : cursorRawQuery2.getInt(0) + 1;
                                cursorRawQuery2.close();
                                ContentValues contentValues = new ContentValues();
                                contentValues.put("position", HEADLINE_POSITION);
                                contentValues.put("rank", Integer.valueOf(i2));
                                if (sQLiteDatabaseOpenDatabase.update("mode_info", contentValues, "mode_name = ?", new String[]{XPAN}) > 0) {
                                    tableDone = true;
                                    Log.i(TAG, "xpan row " + string + " rank " + i + " -> position_headline rank " + i2 + " (" + str + ")");
                                } else {
                                    Log.i(TAG, "xpan row not updated (" + str + ")");
                                }
                                sQLiteDatabaseOpenDatabase.close();
                            } catch (Throwable th) {
                                th = th;
                                cursorRawQuery = cursorRawQuery2;
                                sQLiteDatabase = sQLiteDatabaseOpenDatabase;
                                try {
                                    Log.e(TAG, "mode table not updated (" + str + ")", th);
                                    if (cursorRawQuery != null) {
                                        try {
                                            cursorRawQuery.close();
                                        } catch (Throwable unused4) {
                                        }
                                    }
                                    if (sQLiteDatabase != null) {
                                        sQLiteDatabase.close();
                                    }
                                } catch (Throwable th2) {
                                    if (cursorRawQuery != null) {
                                        try {
                                            cursorRawQuery.close();
                                        } catch (Throwable unused5) {
                                        }
                                    }
                                    if (sQLiteDatabase == null) {
                                        throw th2;
                                    }
                                    try {
                                        sQLiteDatabase.close();
                                        throw th2;
                                    } catch (Throwable unused6) {
                                        throw th2;
                                    }
                                }
                            }
                        } catch (Throwable th3) {
                            th = th3;
                        }
                    } catch (Throwable th4) {
                        th = th4;
                        cursorRawQuery = null;
                    }
                } catch (Throwable th5) {
                    th = th5;
                    cursorRawQuery = null;
                }
            } catch (Throwable unused7) {
            }
        } catch (Throwable th6) {
            throw th6;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean promoteXpanInPanel(ArrayList<Object> arrayList) {
        int iRank;
        int i = 0;
        int i2 = 0;
        while (true) {
            if (i2 + 1 >= arrayList.size()) {
                i2 = -1;
                break;
            }
            if (XPAN.equals(arrayList.get(i2))) {
                break;
            }
            i2 += 2;
        }
        if (i2 < 0) {
            return false;
        }
        int i3 = i2 + 1;
        int iRank2 = rank(arrayList.get(i3));
        if (iRank2 < 0 || iRank2 <= XPAN_PANEL_RANK) {
            return false;
        }
        while (true) {
            int i4 = i + 1;
            if (i4 >= arrayList.size()) {
                arrayList.set(i3, rankLike(arrayList.get(i3), XPAN_PANEL_RANK));
                return true;
            }
            if (i != i2 && (iRank = rank(arrayList.get(i4))) >= XPAN_PANEL_RANK && iRank < iRank2) {
                arrayList.set(i4, rankLike(arrayList.get(i4), iRank + 1));
            }
            i += 2;
        }
    }

    private static int rank(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        if (obj instanceof String) {
            try {
                return Integer.parseInt(((String) obj).trim());
            } catch (NumberFormatException unused) {
            }
        }
        return -1;
    }

    private static Object rankLike(Object obj, int i) {
        return obj instanceof Number ? Integer.valueOf(i) : String.valueOf(i);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void serveHeadline(XC_MethodHook.MethodHookParam methodHookParam) {
        Object result = methodHookParam.getResult();
        if (result instanceof List) {
            List list = (List) result;
            if (list.isEmpty() || list.size() % 2 != 0) {
                return;
            }
            for (int i = 0; i < list.size(); i += 2) {
                if (XPAN.equals(list.get(i))) {
                    return;
                }
            }
            ArrayList arrayList = new ArrayList(list.size() + 2);
            arrayList.addAll(list);
            arrayList.add(XPAN);
            arrayList.add(rankLike(list.get(1), XPAN_HEADLINE_RANK));
            methodHookParam.setResult(arrayList);
            log("headline move served: " + arrayList);
        }
    }
}
