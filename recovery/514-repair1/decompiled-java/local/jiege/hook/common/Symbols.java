package local.jiege.hook.common;

import android.os.Process;
import defpackage.c3;
import defpackage.t2;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import local.jiege.hook.filters.PresetSelection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.luckypray.dexkit.DexKitBridge;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class Symbols {
    private static final int CACHE_VERSION = 2;
    private static final String SPEC = "assets/symbols.json";
    private static final String TAG = "Symbols";
    private static volatile Symbols current;
    private static volatile String modulePath;
    private final ClassLoader loader;
    private final Map<String, List<Object>> resolved = new LinkedHashMap();
    private final Map<String, String> failures = new LinkedHashMap();

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class MissingSymbol extends RuntimeException {
        public MissingSymbol(String str, String str2) {
            StringBuilder sb = new StringBuilder("symbol ");
            sb.append(str);
            sb.append(" unavailable");
            sb.append(str2 == null ? PresetSelection.STANDARD_KEY : c3.k(" (", str2, ")"));
            super(sb.toString());
        }
    }

    private Symbols(ClassLoader classLoader) {
        this.loader = classLoader;
    }

    /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue */
    private Object decode(String str) throws NoSuchFieldException, NoSuchMethodException {
        String[] strArrSplit = SymbolSearch.split(str);
        String str2 = strArrSplit[0];
        str2.getClass();
        byte b = -1;
        switch (str2.hashCode()) {
            case 99:
                if (str2.equals(SymbolSearch.CLASS)) {
                    b = 0;
                }
                break;
            case 102:
                if (str2.equals(SymbolSearch.FIELD)) {
                    b = 1;
                }
                break;
            case 109:
                if (str2.equals(SymbolSearch.METHOD)) {
                    b = CACHE_VERSION;
                }
                break;
        }
        switch (b) {
            case 0:
                return type(strArrSplit[1]);
            case 1:
                Field declaredField = type(strArrSplit[1]).getDeclaredField(strArrSplit[CACHE_VERSION]);
                declaredField.setAccessible(true);
                return declaredField;
            case CACHE_VERSION /* 2 */:
                int length = strArrSplit.length - 4;
                Class<?>[] clsArr = new Class[length];
                for (int i = 0; i < length; i++) {
                    clsArr[i] = type(strArrSplit[i + 4]);
                }
                Method declaredMethod = type(strArrSplit[1]).getDeclaredMethod(strArrSplit[CACHE_VERSION], clsArr);
                declaredMethod.setAccessible(true);
                return declaredMethod;
            default:
                t2.m(str);
                return null;
        }
    }

    private boolean decodeAll(JSONObject jSONObject, JSONObject jSONObject2) {
        try {
            JSONObject jSONObject3 = jSONObject.getJSONObject("resolved");
            JSONObject jSONObject4 = jSONObject.getJSONObject("failures");
            Iterator<String> itKeys = jSONObject2.keys();
            while (itKeys.hasNext()) {
                String next = itKeys.next();
                if (jSONObject3.has(next)) {
                    JSONArray jSONArray = jSONObject3.getJSONArray(next);
                    ArrayList arrayList = new ArrayList();
                    for (int i = 0; i < jSONArray.length(); i++) {
                        arrayList.add(decode(jSONArray.getString(i)));
                    }
                    this.resolved.put(next, arrayList);
                } else {
                    if (!jSONObject4.has(next)) {
                        return false;
                    }
                    this.failures.put(next, jSONObject4.getString(next));
                }
            }
            return true;
        } catch (Throwable th) {
            Log.e(TAG, "symbol entries unusable", th);
            this.resolved.clear();
            this.failures.clear();
            return false;
        }
    }

    public static Symbols get() {
        Symbols symbols = current;
        if (symbols != null) {
            return symbols;
        }
        t2.j("symbols not installed");
        return null;
    }

    public static Symbols install(String str, ClassLoader classLoader, String str2, String str3) {
        JSONObject jSONObject;
        Symbols symbols = new Symbols(classLoader);
        current = symbols;
        long jCurrentTimeMillis = System.currentTimeMillis();
        String str4 = "error";
        try {
            jSONObject = new JSONObject(readSpec()).getJSONObject(str);
            try {
                File file = new File(str2);
                StringBuilder sb = new StringBuilder("2|");
                sb.append(sha1(jSONObject.toString()));
                sb.append("|");
                sb.append(file.getAbsolutePath());
                sb.append("|");
                try {
                    sb.append(file.length());
                    sb.append("|");
                    sb.append(file.lastModified());
                    String string = sb.toString();
                    File file2 = new File(str3, "cache/jiege_symbols_" + str + ".json");
                    JSONObject cache = readCache(file2, string);
                    str4 = "cache";
                    if (cache == null || !symbols.decodeAll(cache, jSONObject)) {
                        JSONObject jSONObjectSearch = search(jSONObject, str2);
                        str4 = "search";
                        symbols.resolved.clear();
                        symbols.failures.clear();
                        symbols.decodeAll(jSONObjectSearch, jSONObject);
                        writeCache(file2, string, jSONObjectSearch);
                    }
                    jSONObject = jSONObject;
                } catch (Throwable th) {
                    th = th;
                    jSONObject = jSONObject;
                    Log.e(TAG, "symbol resolution failed", th);
                }
            } catch (Throwable th2) {
                th = th2;
            }
        } catch (Throwable th3) {
            th = th3;
            jSONObject = null;
        }
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        for (Map.Entry<String, String> entry : symbols.failures.entrySet()) {
            JSONObject jSONObjectOptJSONObject = jSONObject == null ? null : jSONObject.optJSONObject(entry.getKey());
            if (jSONObjectOptJSONObject == null || !jSONObjectOptJSONObject.optBoolean("optional")) {
                arrayList.add(entry.getKey() + ": " + entry.getValue());
            } else {
                arrayList2.add(entry.getKey() + ": " + entry.getValue());
            }
        }
        Log.i(TAG, str + ": " + symbols.resolved.size() + " resolved, " + arrayList.size() + " missing (" + str4 + ", " + (System.currentTimeMillis() - jCurrentTimeMillis) + " ms)");
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            Log.i(TAG, "  missing " + ((String) it.next()));
        }
        Iterator it2 = arrayList2.iterator();
        while (it2.hasNext()) {
            Log.i(TAG, "  not on this build " + ((String) it2.next()));
        }
        Diagnostics.begin(str, classLoader, str2, symbols.resolved.size(), arrayList.size());
        return symbols;
    }

    public static String modulePath() {
        return modulePath;
    }

    private Object one(String str) {
        List<Object> list = this.resolved.get(str);
        if (list != null) {
            return list.get(0);
        }
        throw new MissingSymbol(str, this.failures.get(str));
    }

    private static byte[] readAll(InputStream inputStream) throws IOException {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[8192];
            while (true) {
                int i = inputStream.read(bArr);
                if (i <= 0) {
                    byte[] byteArray = byteArrayOutputStream.toByteArray();
                    inputStream.close();
                    return byteArray;
                }
                byteArrayOutputStream.write(bArr, 0, i);
            }
        } catch (Throwable th) {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    private static JSONObject readCache(File file, String str) {
        if (!file.isFile()) {
            return null;
        }
        try {
            JSONObject jSONObject = new JSONObject(new String(readAll(new FileInputStream(file)), "UTF-8"));
            if (str.equals(jSONObject.optString("key"))) {
                return jSONObject.getJSONObject("entries");
            }
        } catch (Throwable unused) {
        }
        return null;
    }

    private static String readSpec() throws IOException {
        InputStream resourceAsStream = Symbols.class.getClassLoader().getResourceAsStream(SPEC);
        if (resourceAsStream != null) {
            return new String(readAll(resourceAsStream), "UTF-8");
        }
        String str = modulePath;
        if (str == null) {
            t2.j("assets/symbols.json not readable (no module path)");
            return null;
        }
        ZipFile zipFile = new ZipFile(str);
        try {
            ZipEntry entry = zipFile.getEntry(SPEC);
            if (entry == null) {
                throw new IllegalStateException("assets/symbols.json missing from ".concat(str));
            }
            String str2 = new String(readAll(zipFile.getInputStream(entry)), "UTF-8");
            zipFile.close();
            return str2;
        } catch (Throwable th) {
            try {
                zipFile.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    private static JSONObject search(JSONObject jSONObject, String str) {
        System.loadLibrary("dexkit");
        str.getClass();
        DexKitBridge dexKitBridge = new DexKitBridge(str);
        try {
            SymbolSearch symbolSearchRun = SymbolSearch.run(dexKitBridge, jSONObject);
            JSONObject jSONObject2 = new JSONObject();
            for (Map.Entry<String, List<String>> entry : symbolSearchRun.resolved.entrySet()) {
                jSONObject2.put(entry.getKey(), new JSONArray((Collection) entry.getValue()));
            }
            JSONObject jSONObjectPut = new JSONObject().put("resolved", jSONObject2).put("failures", new JSONObject(symbolSearchRun.failures));
            dexKitBridge.close();
            return jSONObjectPut;
        } catch (Throwable th) {
            try {
                dexKitBridge.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    public static void setModulePath(String str) {
        modulePath = str;
    }

    private static String sha1(String str) {
        byte[] bArrDigest = MessageDigest.getInstance("SHA-1").digest(str.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : bArrDigest) {
            sb.append(String.format("%02x", Byte.valueOf(b)));
        }
        return sb.toString();
    }

    private Class<?> type(String str) {
        str.getClass();
        switch (str) {
            case "double":
                return Double.TYPE;
            case "int":
                return Integer.TYPE;
            case "byte":
                return Byte.TYPE;
            case "char":
                return Character.TYPE;
            case "long":
                return Long.TYPE;
            case "void":
                return Void.TYPE;
            case "boolean":
                return Boolean.TYPE;
            case "float":
                return Float.TYPE;
            case "short":
                return Short.TYPE;
            default:
                return str.endsWith("[]") ? Class.forName(SymbolSearch.descriptor(str).replace('/', '.'), false, this.loader) : Class.forName(str, false, this.loader);
        }
    }

    private static void writeCache(File file, String str, JSONObject jSONObject) {
        try {
            File parentFile = file.getParentFile();
            if (parentFile == null || parentFile.isDirectory() || parentFile.mkdirs()) {
                File file2 = new File(file.getPath() + "." + Process.myPid() + ".tmp");
                FileOutputStream fileOutputStream = new FileOutputStream(file2);
                try {
                    fileOutputStream.write(new JSONObject().put("key", str).put("entries", jSONObject).toString().getBytes("UTF-8"));
                    fileOutputStream.close();
                    if (file2.renameTo(file)) {
                        return;
                    }
                    file2.delete();
                } catch (Throwable th) {
                    try {
                        fileOutputStream.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                    throw th;
                }
            }
        } catch (Throwable th3) {
            Log.e(TAG, "symbol cache not written", th3);
        }
    }

    public <T> List<T> all(String str) {
        List<Object> list = this.resolved.get(str);
        if (list != null) {
            return Collections.unmodifiableList(list);
        }
        throw new MissingSymbol(str, this.failures.get(str));
    }

    public Object call(String str, Object obj, Object... objArr) {
        try {
            return method(str).invoke(obj, objArr);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    public Class<?> cls(String str) {
        return (Class) one(str);
    }

    public Field field(String str) {
        return (Field) one(str);
    }

    public boolean has(String... strArr) {
        for (String str : strArr) {
            if (!this.resolved.containsKey(str)) {
                return false;
            }
        }
        return true;
    }

    public Method method(String str) {
        return (Method) one(str);
    }

    public String name(String str) {
        Object objOne = one(str);
        return objOne instanceof Method ? ((Method) objOne).getName() : ((Field) objOne).getName();
    }

    public void set(String str, Object obj, Object obj2) {
        field(str).set(obj, obj2);
    }

    public Object get(String str, Object obj) {
        return field(str).get(obj);
    }
}
