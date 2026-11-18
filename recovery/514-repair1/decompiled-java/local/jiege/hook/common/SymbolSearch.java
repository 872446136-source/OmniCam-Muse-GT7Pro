package local.jiege.hook.common;

import defpackage.a;
import defpackage.an0;
import defpackage.ch;
import defpackage.cr;
import defpackage.dh;
import defpackage.eh;
import defpackage.gt0;
import defpackage.ia0;
import defpackage.iz;
import defpackage.jg1;
import defpackage.jz;
import defpackage.kz;
import defpackage.lz;
import defpackage.m10;
import defpackage.ol;
import defpackage.px;
import defpackage.px0;
import defpackage.qx;
import defpackage.rx;
import defpackage.rz;
import defpackage.sx;
import defpackage.t2;
import defpackage.tx;
import defpackage.ux;
import defpackage.vh;
import defpackage.xo1;
import defpackage.xp1;
import defpackage.yh;
import defpackage.ym0;
import defpackage.zm0;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.luckypray.dexkit.DexKitBridge;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class SymbolSearch {
    public static final String CLASS = "c";
    public static final String FIELD = "f";
    public static final String METHOD = "m";
    private final DexKitBridge bridge;
    public final Map<String, List<String>> resolved = new LinkedHashMap();
    public final Map<String, String> failures = new LinkedHashMap();
    public final Map<String, String> optionalFailures = new LinkedHashMap();

    private SymbolSearch(DexKitBridge dexKitBridge) {
        this.bridge = dexKitBridge;
    }

    private boolean assignable(String str, String str2) {
        ArrayList arrayList = new ArrayList();
        arrayList.add(str);
        ArrayList arrayList2 = new ArrayList();
        while (!arrayList.isEmpty()) {
            String str3 = (String) arrayList.remove(arrayList.size() - 1);
            if (str3.equals(str2)) {
                return true;
            }
            if (!arrayList2.contains(str3)) {
                arrayList2.add(str3);
                ch chVarH = this.bridge.h(str3);
                if (chVarH != null) {
                    ch chVar = (ch) chVarH.i.getValue();
                    if (chVar != null) {
                        arrayList.add(chVar.j());
                    }
                    Iterator<E> it = ((dh) chVarH.j.getValue()).iterator();
                    while (it.hasNext()) {
                        arrayList.add(((ch) it.next()).j());
                    }
                }
            }
        }
        return false;
    }

    private static String classEntry(String str) {
        return join(CLASS, str);
    }

    private static void collect(Object obj, List<String> list) {
        if (obj instanceof String) {
            String str = (String) obj;
            if (str.startsWith("@")) {
                list.add(str.substring(1));
                return;
            }
        }
        if (obj instanceof JSONArray) {
            JSONArray jSONArray = (JSONArray) obj;
            for (int i = 0; i < jSONArray.length(); i++) {
                collect(jSONArray.opt(i), list);
            }
        }
    }

    private boolean dependenciesSettled(JSONObject jSONObject, JSONObject jSONObject2) {
        for (String str : references(jSONObject)) {
            if (jSONObject2.has(str) && !this.resolved.containsKey(str) && !this.failures.containsKey(str)) {
                return false;
            }
        }
        return true;
    }

    public static String descriptor(String str) {
        if (str.endsWith("[]")) {
            return "[" + descriptor(str.substring(0, str.length() - 2));
        }
        switch (str) {
            case "double":
                return "D";
            case "int":
                return "I";
            case "byte":
                return "B";
            case "char":
                return "C";
            case "long":
                return "J";
            case "void":
                return "V";
            case "boolean":
                return "Z";
            case "float":
                return "F";
            case "short":
                return "S";
            default:
                return "L" + str.replace('.', '/') + ";";
        }
    }

    private String[] entry(String str) {
        return split(this.resolved.get(str.substring(1)).get(0));
    }

    private void fail(String str, JSONObject jSONObject, String str2) {
        this.failures.put(str, str2);
        if (jSONObject == null || !jSONObject.optBoolean("optional")) {
            return;
        }
        this.optionalFailures.put(str, str2);
    }

    private String fieldDescriptor(String str) {
        if (!str.startsWith("@")) {
            return str;
        }
        String[] strArrEntry = entry(str);
        return descriptor(strArrEntry[1]) + "->" + strArrEntry[2] + ":" + descriptor(strArrEntry[3]);
    }

    private List<String> find(JSONObject jSONObject) throws JSONException {
        String string = jSONObject.getString("kind");
        string.getClass();
        switch (string) {
            case "method":
                return findMethod(jSONObject);
            case "class":
                return findClass(jSONObject);
            case "field":
                return findField(jSONObject);
            default:
                t2.m(jSONObject.getString("kind"));
                return null;
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    private List<String> findClass(JSONObject jSONObject) throws JSONException {
        ch chVar;
        if (jSONObject.has("name")) {
            ch chVarH = this.bridge.h(jSONObject.getString("name"));
            return chVarH == null ? new ArrayList() : one(classEntry(chVarH.j()));
        }
        if (jSONObject.has("superOf")) {
            ch chVarH2 = this.bridge.h(typeName(jSONObject.getString("superOf")));
            ch chVar2 = chVarH2 == null ? null : (ch) chVarH2.i.getValue();
            return chVar2 == null ? new ArrayList() : one(classEntry(chVar2.j()));
        }
        if (jSONObject.has("interfaceOf")) {
            ArrayList arrayList = new ArrayList();
            ch chVarH3 = this.bridge.h(typeName(jSONObject.getString("interfaceOf")));
            if (chVarH3 != null) {
                Iterator<E> it = ((dh) chVarH3.j.getValue()).iterator();
                while (it.hasNext()) {
                    arrayList.add(classEntry(((ch) it.next()).j()));
                }
            }
            return arrayList;
        }
        if (jSONObject.has("typeOf")) {
            return one(classEntry(entry(jSONObject.getString("typeOf"))[3]));
        }
        if (jSONObject.has("returnOf")) {
            return one(classEntry(entry(jSONObject.getString("returnOf"))[3]));
        }
        byte b = 1;
        if (jSONObject.has("ownerOf")) {
            return one(classEntry(entry(jSONObject.getString("ownerOf"))[1]));
        }
        Object[] objArr = 0;
        if (jSONObject.has("paramOf")) {
            JSONArray jSONArray = jSONObject.getJSONArray("paramOf");
            return one(classEntry(entry(jSONArray.getString(0))[jSONArray.getInt(1) + 4]));
        }
        eh ehVar = new eh();
        List<String> listStrings = strings(jSONObject, "strings");
        if (!listStrings.isEmpty()) {
            ArrayList arrayList2 = new ArrayList(vh.O(listStrings));
            Iterator<T> it2 = listStrings.iterator();
            while (it2.hasNext()) {
                arrayList2.add(new jg1((String) it2.next(), 5, false));
            }
            ehVar.F = new ArrayList(arrayList2);
        }
        if (jSONObject.has("super")) {
            String strTypeName = typeName(jSONObject.getString("super"));
            strTypeName.getClass();
            eh ehVar2 = new eh();
            ehVar2.A = new jg1(strTypeName, 5, false);
            ehVar.B = ehVar2;
        }
        Iterator<String> it3 = strings(jSONObject, "ifaces").iterator();
        while (it3.hasNext()) {
            String strTypeName2 = typeName(it3.next());
            strTypeName2.getClass();
            lz lzVar = ehVar.C;
            if (lzVar == null) {
                lzVar = new lz(b);
            }
            ehVar.C = lzVar;
            eh ehVar3 = new eh();
            ehVar3.P(strTypeName2, 5, false);
            List arrayList3 = lzVar.B;
            if (arrayList3 == null) {
                arrayList3 = new ArrayList();
            }
            lzVar.B = arrayList3;
            arrayList3.add(ehVar3);
        }
        Iterator<String> it4 = strings(jSONObject, "fieldTypes").iterator();
        while (it4.hasNext()) {
            ehVar.O(typeName(it4.next()));
        }
        ArrayList arrayList4 = new ArrayList();
        Iterator<String> it5 = strings(jSONObject, "staticFieldTypes").iterator();
        while (it5.hasNext()) {
            arrayList4.add(typeName(it5.next()));
        }
        Iterator it6 = arrayList4.iterator();
        while (it6.hasNext()) {
            ehVar.O((String) it6.next());
        }
        List<String> listStrings2 = strings(jSONObject, "clinitStrings");
        if (!listStrings2.isEmpty()) {
            an0 an0Var = new an0();
            an0.P(an0Var, "<clinit>");
            an0Var.S(listStrings2);
            lz lzVar2 = ehVar.E;
            if (lzVar2 == null) {
                lzVar2 = new lz((byte) 2);
            }
            ehVar.E = lzVar2;
            lzVar2.O(an0Var);
        }
        ArrayList arrayList5 = new ArrayList();
        DexKitBridge dexKitBridge = this.bridge;
        rz rzVar = new rz(objArr == true ? 1 : 0);
        rzVar.B = ehVar;
        dexKitBridge.getClass();
        byte[] bArrO = rzVar.O();
        ReentrantReadWriteLock.ReadLock lock = dexKitBridge.f.readLock();
        lock.lock();
        try {
            long j = dexKitBridge.e;
            if (j == 0) {
                throw new IllegalStateException("DexKitBridge is not valid");
            }
            byte[] bArrNativeFindClass = DexKitBridge.nativeFindClass(j, bArrO);
            lock.unlock();
            ByteBuffer byteBufferWrap = ByteBuffer.wrap(bArrNativeFindClass);
            byteBufferWrap.getClass();
            a aVar = new a();
            byteBufferWrap.order(ByteOrder.LITTLE_ENDIAN);
            aVar.c(byteBufferWrap.position() + byteBufferWrap.getInt(byteBufferWrap.position()), byteBufferWrap);
            dh<ch> dhVar = new dh();
            int iB = aVar.b(4);
            int iF = iB != 0 ? aVar.f(iB) : 0;
            for (int i = 0; i < iF; i++) {
                a aVarG = aVar.g(i);
                aVarG.getClass();
                dhVar.add(xo1.z(dexKitBridge, aVarG));
            }
            if (dhVar.size() > 1) {
                yh.P(dhVar, new m10((byte) 8));
            }
            for (ch chVar3 : dhVar) {
                if (!jSONObject.has("super") || ((chVar = (ch) chVar3.i.getValue()) != null && chVar.j().equals(typeName(jSONObject.getString("super"))))) {
                    if (hasStaticFields(chVar3, arrayList4)) {
                        arrayList5.add(classEntry(chVar3.j()));
                    }
                }
            }
            return arrayList5;
        } catch (Throwable th) {
            lock.unlock();
            throw th;
        }
    }

    private List<String> findField(JSONObject jSONObject) throws JSONException {
        byte b;
        kz kzVar = new kz();
        if (jSONObject.has("in")) {
            String strTypeName = typeName(jSONObject.getString("in"));
            strTypeName.getClass();
            kz.O(kzVar, strTypeName);
        }
        if (jSONObject.has("name")) {
            String string = jSONObject.getString("name");
            string.getClass();
            kz.P(kzVar, string);
        }
        if (jSONObject.has("type")) {
            String strTypeName2 = typeName(jSONObject.getString("type"));
            strTypeName2.getClass();
            eh ehVar = new eh();
            ehVar.P(strTypeName2, 5, false);
            kzVar.C = ehVar;
        }
        Iterator<String> it = strings(jSONObject, "readBy").iterator();
        while (true) {
            b = 2;
            if (!it.hasNext()) {
                break;
            }
            String strMethodDescriptor = methodDescriptor(it.next());
            strMethodDescriptor.getClass();
            lz lzVar = kzVar.D;
            if (lzVar == null) {
                lzVar = new lz(b);
            }
            kzVar.D = lzVar;
            lzVar.O(new an0(strMethodDescriptor));
        }
        Iterator<String> it2 = strings(jSONObject, "writtenBy").iterator();
        while (it2.hasNext()) {
            String strMethodDescriptor2 = methodDescriptor(it2.next());
            strMethodDescriptor2.getClass();
            lz lzVar2 = kzVar.E;
            if (lzVar2 == null) {
                lzVar2 = new lz(b);
            }
            kzVar.E = lzVar2;
            lzVar2.O(new an0(strMethodDescriptor2));
        }
        ArrayList arrayList = new ArrayList();
        Iterator<String> it3 = strings(jSONObject, "notWrittenBy").iterator();
        while (it3.hasNext()) {
            arrayList.add(methodDescriptor(it3.next()));
        }
        ArrayList arrayList2 = new ArrayList();
        DexKitBridge dexKitBridge = this.bridge;
        rz rzVar = new rz((byte) 1);
        rzVar.B = kzVar;
        dexKitBridge.getClass();
        byte[] bArrO = rzVar.O();
        ReentrantReadWriteLock.ReadLock lock = dexKitBridge.f.readLock();
        lock.lock();
        try {
            long j = dexKitBridge.e;
            if (j == 0) {
                throw new IllegalStateException("DexKitBridge is not valid");
            }
            byte[] bArrNativeFindField = DexKitBridge.nativeFindField(j, bArrO);
            lock.unlock();
            ByteBuffer byteBufferWrap = ByteBuffer.wrap(bArrNativeFindField);
            byteBufferWrap.getClass();
            a aVar = new a();
            byteBufferWrap.order(ByteOrder.LITTLE_ENDIAN);
            aVar.c(byteBufferWrap.position() + byteBufferWrap.getInt(byteBufferWrap.position()), byteBufferWrap);
            jz<iz> jzVar = new jz();
            int iB = aVar.b(4);
            int iF = iB != 0 ? aVar.f(iB) : 0;
            for (int i = 0; i < iF; i++) {
                a aVarH = aVar.h(i);
                aVarH.getClass();
                jzVar.add(ol.l(dexKitBridge, aVarH));
            }
            if (jzVar.size() > 1) {
                yh.P(jzVar, new m10((byte) 9));
            }
            for (iz izVar : jzVar) {
                if (!jSONObject.has("static") || Modifier.isStatic(izVar.b) == jSONObject.getBoolean("static")) {
                    Iterator<E> it4 = ((zm0) izVar.e.getValue()).iterator();
                    boolean z = false;
                    while (it4.hasNext()) {
                        if (arrayList.contains(((ym0) it4.next()).c)) {
                            z = true;
                        }
                    }
                    if (!z) {
                        arrayList2.add(join(FIELD, izVar.j().a, izVar.j().b, izVar.j().c));
                    }
                }
            }
            return preferredNames(jSONObject, arrayList2);
        } catch (Throwable th) {
            lock.unlock();
            throw th;
        }
    }

    private List<String> findMethod(JSONObject jSONObject) throws JSONException {
        if (jSONObject.has("overrideOf")) {
            String[] strArrEntry = entry(jSONObject.getString("overrideOf"));
            ch chVarH = this.bridge.h(typeName(jSONObject.getString("in")));
            ArrayList arrayList = new ArrayList();
            if (chVarH != null) {
                for (ym0 ym0Var : (zm0) chVarH.k.getValue()) {
                    if (ym0Var.k() && sameSignature(ym0Var, strArrEntry)) {
                        arrayList.add(methodEntry(ym0Var));
                    }
                }
            }
            return arrayList;
        }
        byte b = 2;
        if (jSONObject.has("implOf")) {
            String[] strArrEntry2 = entry(jSONObject.getString("implOf"));
            an0 an0Var = new an0();
            String str = strArrEntry2[2];
            str.getClass();
            an0.P(an0Var, str);
            String str2 = strArrEntry2[3];
            str2.getClass();
            an0.R(an0Var, str2);
            an0Var.Q(params(strArrEntry2));
            ArrayList arrayList2 = new ArrayList();
            DexKitBridge dexKitBridge = this.bridge;
            rz rzVar = new rz(b);
            rzVar.B = an0Var;
            for (ym0 ym0Var2 : dexKitBridge.g(rzVar)) {
                if (ym0Var2.k() && !Modifier.isAbstract(ym0Var2.b) && sameSignature(ym0Var2, strArrEntry2)) {
                    String str3 = ym0Var2.j().a;
                    if (!str3.equals(strArrEntry2[1]) && assignable(str3, strArrEntry2[1])) {
                        arrayList2.add(methodEntry(ym0Var2));
                    }
                }
            }
            return arrayList2;
        }
        an0 an0Var2 = new an0();
        if (jSONObject.has("in")) {
            String strTypeName = typeName(jSONObject.getString("in"));
            strTypeName.getClass();
            an0.O(an0Var2, strTypeName);
        }
        if (jSONObject.has("name")) {
            String string = jSONObject.getString("name");
            string.getClass();
            an0.P(an0Var2, string);
        }
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("params");
        if (jSONArrayOptJSONArray != null) {
            int length = jSONArrayOptJSONArray.length();
            px0 px0Var = an0Var2.D;
            if (px0Var == null) {
                px0Var = new px0();
                an0Var2.D = px0Var;
            }
            ia0 ia0Var = new ia0();
            ia0Var.A = length;
            ia0Var.B = length;
            px0Var.B = ia0Var;
        }
        if (jSONObject.has("ret")) {
            String strTypeName2 = typeName(jSONObject.getString("ret"));
            strTypeName2.getClass();
            an0.R(an0Var2, strTypeName2);
        }
        List<String> listStrings = strings(jSONObject, "strings");
        if (!listStrings.isEmpty()) {
            an0Var2.S(listStrings);
        }
        List<String> listStrings2 = strings(jSONObject, "contains");
        if (!listStrings2.isEmpty()) {
            ArrayList arrayList3 = new ArrayList(vh.O(listStrings2));
            Iterator<T> it = listStrings2.iterator();
            while (it.hasNext()) {
                arrayList3.add(new jg1((String) it.next(), 1, false));
            }
            an0Var2.E = new ArrayList(arrayList3);
        }
        JSONArray jSONArrayOptJSONArray2 = jSONObject.optJSONArray("numbers");
        if (jSONArrayOptJSONArray2 != null) {
            ArrayList<Number> arrayList4 = new ArrayList();
            for (int i = 0; i < jSONArrayOptJSONArray2.length(); i++) {
                arrayList4.add(Integer.valueOf(jSONArrayOptJSONArray2.getInt(i)));
            }
            ArrayList arrayList5 = new ArrayList(vh.O(arrayList4));
            for (Number number : arrayList4) {
                gt0 gt0Var = new gt0();
                number.getClass();
                if (number instanceof Byte) {
                    gt0Var.a = new px(number.byteValue());
                    gt0Var.b = 1;
                } else if (number instanceof Short) {
                    gt0Var.a = new ux(number.shortValue());
                    gt0Var.b = 2;
                } else if (number instanceof Integer) {
                    gt0Var.a = new sx(number.intValue());
                    gt0Var.b = 3;
                } else if (number instanceof Long) {
                    gt0Var.a = new tx(number.longValue());
                    gt0Var.b = 4;
                } else if (number instanceof Float) {
                    gt0Var.a = new rx(number.floatValue());
                    gt0Var.b = 5;
                } else if (number instanceof Double) {
                    gt0Var.a = new qx(number.doubleValue());
                    gt0Var.b = 6;
                }
                arrayList5.add(gt0Var);
            }
            an0Var2.G = new ArrayList(arrayList5);
        }
        Iterator<String> it2 = strings(jSONObject, "invokes").iterator();
        while (it2.hasNext()) {
            String strMethodDescriptor = methodDescriptor(it2.next());
            strMethodDescriptor.getClass();
            lz lzVar = an0Var2.H;
            if (lzVar == null) {
                lzVar = new lz(b);
            }
            an0Var2.H = lzVar;
            lzVar.O(new an0(strMethodDescriptor));
        }
        Iterator<String> it3 = strings(jSONObject, "fields").iterator();
        while (it3.hasNext()) {
            String strFieldDescriptor = fieldDescriptor(it3.next());
            strFieldDescriptor.getClass();
            List arrayList6 = an0Var2.F;
            if (arrayList6 == null) {
                arrayList6 = new ArrayList();
            }
            an0Var2.F = arrayList6;
            xp1 xp1Var = new xp1();
            xp1Var.B = 1;
            kz kzVar = new kz();
            cr crVar = new cr(strFieldDescriptor);
            kz.P(kzVar, crVar.b);
            kz.O(kzVar, crVar.a);
            String str4 = crVar.c;
            str4.getClass();
            eh ehVar = new eh();
            ehVar.P(str4, 5, false);
            kzVar.C = ehVar;
            xp1Var.A = kzVar;
            xp1Var.B = 1;
            arrayList6.add(xp1Var);
        }
        Iterator<String> it4 = strings(jSONObject, "calledBy").iterator();
        while (it4.hasNext()) {
            String strMethodDescriptor2 = methodDescriptor(it4.next());
            strMethodDescriptor2.getClass();
            lz lzVar2 = an0Var2.I;
            if (lzVar2 == null) {
                lzVar2 = new lz(b);
            }
            an0Var2.I = lzVar2;
            lzVar2.O(new an0(strMethodDescriptor2));
        }
        ArrayList arrayList7 = new ArrayList();
        Iterator<String> it5 = strings(jSONObject, "notInvokes").iterator();
        while (it5.hasNext()) {
            arrayList7.add(methodDescriptor(it5.next()));
        }
        ArrayList arrayList8 = new ArrayList();
        DexKitBridge dexKitBridge2 = this.bridge;
        rz rzVar2 = new rz(b);
        rzVar2.B = an0Var2;
        for (ym0 ym0Var3 : dexKitBridge2.g(rzVar2)) {
            if (ym0Var3.k() && (jSONArrayOptJSONArray == null || paramsMatch(jSONArrayOptJSONArray, ym0Var3.j().c))) {
                int i2 = ym0Var3.b;
                if (!jSONObject.has("static") || Modifier.isStatic(i2) == jSONObject.getBoolean("static")) {
                    if (!jSONObject.has("abstract") || Modifier.isAbstract(i2) == jSONObject.getBoolean("abstract")) {
                        if (!arrayList7.isEmpty()) {
                            Iterator<E> it6 = ((zm0) ym0Var3.e.getValue()).iterator();
                            boolean z = false;
                            while (it6.hasNext()) {
                                if (arrayList7.contains(((ym0) it6.next()).c)) {
                                    z = true;
                                }
                            }
                            if (z) {
                            }
                        }
                        arrayList8.add(methodEntry(ym0Var3));
                    }
                }
            }
        }
        return preferredNames(jSONObject, arrayList8);
    }

    private static boolean hasStaticFields(ch chVar, List<String> list) {
        for (String str : list) {
            boolean z = false;
            for (iz izVar : (jz) chVar.l.getValue()) {
                if (Modifier.isStatic(izVar.b) && izVar.j().c.equals(str)) {
                    z = true;
                }
            }
            if (!z) {
                return false;
            }
        }
        return true;
    }

    private static String join(String... strArr) {
        StringBuilder sb = new StringBuilder();
        for (String str : strArr) {
            if (sb.length() > 0) {
                sb.append('\t');
            }
            sb.append(str);
        }
        return sb.toString();
    }

    private String methodDescriptor(String str) {
        if (!str.startsWith("@")) {
            return str;
        }
        String[] strArrEntry = entry(str);
        StringBuilder sb = new StringBuilder(descriptor(strArrEntry[1]));
        sb.append("->");
        sb.append(strArrEntry[2]);
        sb.append('(');
        for (int i = 4; i < strArrEntry.length; i++) {
            sb.append(descriptor(strArrEntry[i]));
        }
        sb.append(')');
        sb.append(descriptor(strArrEntry[3]));
        return sb.toString();
    }

    private static String methodEntry(ym0 ym0Var) {
        ArrayList arrayList = new ArrayList();
        arrayList.add(METHOD);
        arrayList.add(ym0Var.j().a);
        arrayList.add(ym0Var.j().b);
        arrayList.add(ym0Var.j().d);
        arrayList.addAll(ym0Var.j().c);
        return join((String[]) arrayList.toArray(new String[0]));
    }

    private List<String> missingDependencies(JSONObject jSONObject) {
        ArrayList arrayList = new ArrayList();
        for (String str : references(jSONObject)) {
            if (!this.resolved.containsKey(str)) {
                arrayList.add(str);
            }
        }
        return arrayList;
    }

    private static List<String> one(String str) {
        ArrayList arrayList = new ArrayList();
        arrayList.add(str);
        return arrayList;
    }

    private static List<String> params(String[] strArr) {
        ArrayList arrayList = new ArrayList();
        for (int i = 4; i < strArr.length; i++) {
            arrayList.add(strArr[i]);
        }
        return arrayList;
    }

    private boolean paramsMatch(JSONArray jSONArray, List<String> list) {
        if (jSONArray.length() != list.size()) {
            return false;
        }
        for (int i = 0; i < jSONArray.length(); i++) {
            if (!jSONArray.isNull(i) && !typeName(jSONArray.getString(i)).equals(list.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> preferredNames(JSONObject jSONObject, List<String> list) {
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("names");
        if (jSONArrayOptJSONArray == null) {
            return list;
        }
        for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
            ArrayList arrayList = new ArrayList();
            for (String str : list) {
                if (jSONArrayOptJSONArray.optString(i).equals(split(str)[2])) {
                    arrayList.add(str);
                }
            }
            if (!arrayList.isEmpty()) {
                return arrayList;
            }
        }
        return new ArrayList();
    }

    private static List<String> references(JSONObject jSONObject) {
        ArrayList arrayList = new ArrayList();
        Iterator<String> itKeys = jSONObject.keys();
        while (itKeys.hasNext()) {
            collect(jSONObject.opt(itKeys.next()), arrayList);
        }
        return arrayList;
    }

    public static SymbolSearch run(DexKitBridge dexKitBridge, JSONObject jSONObject) throws JSONException {
        boolean z;
        SymbolSearch symbolSearch = new SymbolSearch(dexKitBridge);
        ArrayList<String> arrayList = new ArrayList();
        Iterator<String> itKeys = jSONObject.keys();
        while (itKeys.hasNext()) {
            arrayList.add(itKeys.next());
        }
        for (boolean z2 = true; !arrayList.isEmpty() && z2; z2 = z) {
            Iterator it = arrayList.iterator();
            z = false;
            while (it.hasNext()) {
                String str = (String) it.next();
                JSONObject jSONObject2 = jSONObject.getJSONObject(str);
                if (symbolSearch.dependenciesSettled(jSONObject2, jSONObject)) {
                    it.remove();
                    List<String> listMissingDependencies = symbolSearch.missingDependencies(jSONObject2);
                    if (listMissingDependencies.isEmpty()) {
                        try {
                            List<String> listFind = symbolSearch.find(jSONObject2);
                            boolean zOptBoolean = jSONObject2.optBoolean("multi");
                            if (listFind.size() == 1 || (zOptBoolean && !listFind.isEmpty())) {
                                symbolSearch.resolved.put(str, listFind);
                            } else {
                                StringBuilder sb = new StringBuilder();
                                sb.append(listFind.size());
                                sb.append(" matches ");
                                if (listFind.size() > 4) {
                                    listFind = listFind.subList(0, 4);
                                }
                                sb.append(listFind);
                                symbolSearch.fail(str, jSONObject2, sb.toString());
                            }
                        } catch (Throwable th) {
                            symbolSearch.fail(str, jSONObject2, String.valueOf(th));
                        }
                    } else {
                        symbolSearch.fail(str, jSONObject2, "needs " + listMissingDependencies);
                    }
                    z = true;
                }
            }
        }
        for (String str2 : arrayList) {
            symbolSearch.fail(str2, jSONObject.optJSONObject(str2), "circular reference");
        }
        return symbolSearch;
    }

    private static boolean sameSignature(ym0 ym0Var, String[] strArr) {
        return ym0Var.j().b.equals(strArr[2]) && ym0Var.j().d.equals(strArr[3]) && ym0Var.j().c.equals(params(strArr));
    }

    public static String[] split(String str) {
        return str.split("\t", -1);
    }

    private static List<String> strings(JSONObject jSONObject, String str) {
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray(str);
        ArrayList arrayList = new ArrayList();
        if (jSONArrayOptJSONArray != null) {
            for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                arrayList.add(jSONArrayOptJSONArray.getString(i));
            }
        }
        return arrayList;
    }

    private String typeName(String str) {
        return !str.startsWith("@") ? str : entry(str)[1];
    }
}
