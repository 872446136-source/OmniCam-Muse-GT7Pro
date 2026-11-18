package local.jiege.hook.shutter;

import android.app.Application;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import defpackage.hc1;
import defpackage.t2;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Settings;
import local.jiege.hook.common.Symbols;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ShutterSound {
    private static final long PENDING_MS = 1000;
    private static final int STREAM_SYSTEM_ENFORCED = 7;
    private static final String TAG = "ShutterSound";
    private static volatile boolean cameraSilenced;
    private static volatile Method playerInstance;
    private static volatile boolean redirecting;
    private static volatile Field ringerField;
    private static SoundPool shadowPool;
    private static final Object lock = new Object();
    private static final Map<Integer, Integer> samples = new HashMap();
    private static final Set<Integer> shadowStreams = new HashSet();
    private static final Set<Integer> loadedSamples = new HashSet();
    private static final Map<Integer, PendingPlay> pendingPlays = new HashMap();
    private static final Map<Integer, Integer> standIns = new HashMap();
    private static int nextStandIn = -1;

    /* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
    public static final class PendingPlay {
        final float gain;
        final int loop;
        final int priority;
        final float rate;
        final int sample;
        final long time = SystemClock.uptimeMillis();

        public PendingPlay(int i, float f, int i2, int i3, float f2) {
            this.sample = i;
            this.gain = f;
            this.priority = i2;
            this.loop = i3;
            this.rate = f2;
        }

        public int start() {
            SoundPool soundPool = ShutterSound.shadowPool;
            int i = this.sample;
            float f = this.gain;
            return soundPool.play(i, f, f, this.priority, this.loop, this.rate);
        }
    }

    private ShutterSound() {
    }

    private static void install(ClassLoader classLoader) throws NoSuchMethodException {
        Symbols symbols = Symbols.get();
        Method method = symbols.method("SoundPlayer.refresh");
        playerInstance = symbols.method("SoundPlayer.instance");
        final Class<?> cls = symbols.cls("SoundPlayer");
        if (symbols.has("SoundPlayer.silence", "SoundPlayer.unsilence")) {
            XposedBridge.hookMethod(symbols.method("SoundPlayer.silence"), new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterSound.1
                public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    ShutterSound.cameraSilenced = true;
                }
            });
            XposedBridge.hookMethod(symbols.method("SoundPlayer.unsilence"), new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterSound.2
                public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    ShutterSound.cameraSilenced = false;
                }
            });
        } else {
            if (!symbols.has("SoundPlayer.restoreReady")) {
                t2.o("SoundPlayer silence contract unresolved");
                return;
            }
            Class<?> cls2 = null;
            for (Field field : cls.getDeclaredFields()) {
                Class<?> type = field.getType();
                if (type != Handler.class && Handler.class.isAssignableFrom(type)) {
                    if (cls2 != null && cls2 != type) {
                        t2.o("Ambiguous sound Handler");
                        return;
                    }
                    cls2 = type;
                }
            }
            if (cls2 == null) {
                t2.o("SoundPlayer private Handler");
                return;
            } else {
                XposedBridge.hookMethod(cls2.getDeclaredMethod("handleMessage", Message.class), new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterSound.3
                    public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Message message = (Message) methodHookParam.args[0];
                        if (message == null || message.what != 2) {
                            return;
                        }
                        ShutterSound.cameraSilenced = message.arg1 == 0;
                    }
                });
                Log.i(TAG, "using merged ready/restore sound contract");
            }
        }
        XposedBridge.hookMethod(method, new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterSound.4
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException {
                Field fieldRingerField = ShutterSound.ringerField(cls, methodHookParam.thisObject);
                if (fieldRingerField == null) {
                    return;
                }
                boolean z = !ShutterSound.cameraSilenced && ShutterSound.shouldPlay(fieldRingerField.getInt(methodHookParam.thisObject));
                synchronized (ShutterSound.lock) {
                    if (!z) {
                        try {
                            ShutterSound.shadowStreams.clear();
                        } catch (Throwable th) {
                            throw th;
                        }
                    }
                    ShutterSound.redirecting = z;
                }
                if (z) {
                    fieldRingerField.setInt(methodHookParam.thisObject, 2);
                }
            }
        });
        XposedBridge.hookMethod(XposedHelpers.findClass("com.oplus.camera.Camera", classLoader).getMethod("onResume", null), new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterSound.5
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws IllegalAccessException {
                Method method2 = ShutterSound.playerInstance;
                Object objInvoke = method2 != null ? method2.invoke(null, null) : null;
                if (objInvoke == null) {
                    return;
                }
                int ringerMode = ((AudioManager) ((Context) methodHookParam.thisObject).getSystemService("audio")).getRingerMode();
                boolean z = !ShutterSound.cameraSilenced && ShutterSound.shouldPlay(ringerMode);
                Field field2 = ShutterSound.ringerField;
                if (field2 == null || z == ShutterSound.redirecting) {
                    return;
                }
                synchronized (ShutterSound.lock) {
                    if (!z) {
                        try {
                            ShutterSound.shadowStreams.clear();
                        } catch (Throwable th) {
                            throw th;
                        }
                    }
                    ShutterSound.redirecting = z;
                }
                if (z) {
                    ringerMode = 2;
                }
                field2.setInt(objInvoke, ringerMode);
            }
        });
        XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterSound.6
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (ShutterSound.isCameraPool(methodHookParam.thisObject) && (methodHookParam.getResult() instanceof Integer)) {
                    synchronized (ShutterSound.lock) {
                        SoundPool soundPoolShadowPool = ShutterSound.shadowPool();
                        Object[] objArr = methodHookParam.args;
                        ShutterSound.samples.put((Integer) methodHookParam.getResult(), Integer.valueOf(soundPoolShadowPool.load((Context) objArr[0], ((Integer) objArr[1]).intValue(), ((Integer) methodHookParam.args[2]).intValue())));
                    }
                }
            }
        };
        Class cls3 = Integer.TYPE;
        XposedHelpers.findAndHookMethod(SoundPool.class, "load", new Object[]{Context.class, cls3, cls3, xC_MethodHook});
        XposedHelpers.findAndHookMethod(SoundPool.class, "load", new Object[]{String.class, cls3, new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterSound.7
            public void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (ShutterSound.isCameraPool(methodHookParam.thisObject) && (methodHookParam.getResult() instanceof Integer)) {
                    synchronized (ShutterSound.lock) {
                        Map map = ShutterSound.samples;
                        Integer num = (Integer) methodHookParam.getResult();
                        SoundPool soundPoolShadowPool = ShutterSound.shadowPool();
                        Object[] objArr = methodHookParam.args;
                        map.put(num, Integer.valueOf(soundPoolShadowPool.load((String) objArr[0], ((Integer) objArr[1]).intValue())));
                    }
                }
            }
        }});
        XC_MethodHook xC_MethodHook2 = new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterSound.8
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (ShutterSound.isCameraPool(methodHookParam.thisObject)) {
                    if (ShutterSound.redirecting && !ShutterSound.stillWanted()) {
                        methodHookParam.setResult(0);
                        return;
                    }
                    if (!ShutterSound.redirecting) {
                        float fVolumeGain = ShutterSound.volumeGain();
                        Object[] objArr = methodHookParam.args;
                        objArr[1] = Float.valueOf(((Float) objArr[1]).floatValue() * fVolumeGain);
                        Object[] objArr2 = methodHookParam.args;
                        objArr2[2] = Float.valueOf(((Float) objArr2[2]).floatValue() * fVolumeGain);
                        return;
                    }
                    synchronized (ShutterSound.lock) {
                        try {
                            Integer num = (Integer) ShutterSound.samples.get((Integer) methodHookParam.args[0]);
                            if (num == null) {
                                return;
                            }
                            float fVolumeGain2 = ShutterSound.volumeGain();
                            PendingPlay pendingPlay = new PendingPlay(num.intValue(), fVolumeGain2, ((Integer) methodHookParam.args[3]).intValue(), ((Integer) methodHookParam.args[4]).intValue(), ((Float) methodHookParam.args[5]).floatValue());
                            if (!ShutterSound.loadedSamples.contains(num)) {
                                int i = ShutterSound.nextStandIn;
                                ShutterSound.nextStandIn = i - 1;
                                ShutterSound.pendingPlays.put(Integer.valueOf(i), pendingPlay);
                                ShutterSound.shadowStreams.add(Integer.valueOf(i));
                                Log.i(ShutterSound.TAG, "play sample=" + methodHookParam.args[0] + " waits for the shadow load, stream=" + i);
                                methodHookParam.setResult(Integer.valueOf(i));
                                return;
                            }
                            int iStart = pendingPlay.start();
                            if (iStart != 0) {
                                ShutterSound.shadowStreams.add(Integer.valueOf(iStart));
                            }
                            Log.i(ShutterSound.TAG, "play sample=" + methodHookParam.args[0] + " cameraVolume=" + methodHookParam.args[1] + " gain=" + fVolumeGain2 + " stream=" + iStart);
                            methodHookParam.setResult(Integer.valueOf(iStart));
                        } catch (Throwable th) {
                            throw th;
                        }
                    }
                }
            }
        };
        Class cls4 = Float.TYPE;
        XposedHelpers.findAndHookMethod(SoundPool.class, "play", new Object[]{cls3, cls4, cls4, cls3, cls3, cls4, xC_MethodHook2});
        XC_MethodHook xC_MethodHook3 = new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterSound.9
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (ShutterSound.isCameraPool(methodHookParam.thisObject)) {
                    synchronized (ShutterSound.lock) {
                        try {
                            Integer num = (Integer) methodHookParam.args[0];
                            int iIntValue = num.intValue();
                            if (ShutterSound.shadowPool != null && ShutterSound.pendingPlays.containsKey(num)) {
                                if ("stop".equals(methodHookParam.method.getName())) {
                                    ShutterSound.pendingPlays.remove(num);
                                    ShutterSound.shadowStreams.remove(num);
                                }
                                methodHookParam.setResult((Object) null);
                                return;
                            }
                            Integer num2 = (Integer) ShutterSound.standIns.get(num);
                            if (num2 != null) {
                                iIntValue = num2.intValue();
                            }
                            if (ShutterSound.shadowPool != null && ShutterSound.shadowStreams.contains(Integer.valueOf(iIntValue))) {
                                Object[] objArr = (Object[]) methodHookParam.args.clone();
                                objArr[0] = Integer.valueOf(iIntValue);
                                if ("setVolume".equals(methodHookParam.method.getName())) {
                                    float fVolumeGain = ShutterSound.volumeGain();
                                    objArr[1] = Float.valueOf(fVolumeGain);
                                    objArr[2] = Float.valueOf(fVolumeGain);
                                }
                                XposedHelpers.callMethod(ShutterSound.shadowPool, methodHookParam.method.getName(), ((Method) methodHookParam.method).getParameterTypes(), objArr);
                                if ("stop".equals(methodHookParam.method.getName())) {
                                    ShutterSound.shadowStreams.remove(Integer.valueOf(iIntValue));
                                    ShutterSound.standIns.values().remove(Integer.valueOf(iIntValue));
                                }
                                methodHookParam.setResult((Object) null);
                                return;
                            }
                            if ("setVolume".equals(methodHookParam.method.getName())) {
                                float fVolumeGain2 = ShutterSound.volumeGain();
                                Object[] objArr2 = methodHookParam.args;
                                objArr2[1] = Float.valueOf(((Float) objArr2[1]).floatValue() * fVolumeGain2);
                                Object[] objArr3 = methodHookParam.args;
                                objArr3[2] = Float.valueOf(fVolumeGain2 * ((Float) objArr3[2]).floatValue());
                            }
                        } catch (Throwable th) {
                            throw th;
                        }
                    }
                }
            }
        };
        XposedHelpers.findAndHookMethod(SoundPool.class, "stop", new Object[]{cls3, xC_MethodHook3});
        XposedHelpers.findAndHookMethod(SoundPool.class, "pause", new Object[]{cls3, xC_MethodHook3});
        XposedHelpers.findAndHookMethod(SoundPool.class, "resume", new Object[]{cls3, xC_MethodHook3});
        XposedHelpers.findAndHookMethod(SoundPool.class, "setVolume", new Object[]{cls3, cls4, cls4, xC_MethodHook3});
        XposedHelpers.findAndHookMethod(SoundPool.class, "setLoop", new Object[]{cls3, cls3, xC_MethodHook3});
        XposedHelpers.findAndHookMethod(SoundPool.class, "setRate", new Object[]{cls3, cls4, xC_MethodHook3});
        XposedHelpers.findAndHookMethod(SoundPool.class, "release", new Object[]{new XC_MethodHook() { // from class: local.jiege.hook.shutter.ShutterSound.10
            public void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                if (ShutterSound.isCameraPool(methodHookParam.thisObject)) {
                    synchronized (ShutterSound.lock) {
                        try {
                            if (ShutterSound.shadowPool != null) {
                                ShutterSound.shadowPool.release();
                            }
                            ShutterSound.shadowPool = null;
                            ShutterSound.samples.clear();
                            ShutterSound.shadowStreams.clear();
                            ShutterSound.loadedSamples.clear();
                            ShutterSound.pendingPlays.clear();
                            ShutterSound.standIns.clear();
                        } catch (Throwable th) {
                            throw th;
                        }
                    }
                }
            }
        }});
        Log.i(TAG, "installed");
    }

    public static void installCamera(ClassLoader classLoader) {
        try {
            install(classLoader);
        } catch (Throwable th) {
            Log.e(TAG, "shutter sound hooks unavailable", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isCameraPool(Object obj) {
        Method method = playerInstance;
        if (method != null && obj != null && obj != shadowPool) {
            try {
                Object objInvoke = method.invoke(null, null);
                if (objInvoke == null) {
                    return false;
                }
                Iterator it = Symbols.get().all("SoundPlayer.pools").iterator();
                while (it.hasNext()) {
                    if (((Field) it.next()).get(objInvoke) == obj) {
                        return true;
                    }
                }
            } catch (Throwable unused) {
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Field ringerField(Class<?> cls, Object obj) {
        Field field = ringerField;
        if (field != null) {
            return field;
        }
        Application application = (Application) XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", (ClassLoader) null), "currentApplication", new Object[0]);
        if (application == null) {
            return null;
        }
        int ringerMode = ((AudioManager) application.getSystemService("audio")).getRingerMode();
        Field field2 = null;
        for (Field field3 : Symbols.get().all("SoundPlayer.refreshedInts")) {
            if (field3.getInt(obj) == ringerMode) {
                if (field2 != null) {
                    return null;
                }
                field2 = field3;
            }
        }
        if (field2 != null) {
            ringerField = field2;
            Log.i(TAG, "ringer mode field " + field2.getName());
        }
        return field2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void shadowLoaded(SoundPool soundPool, int i, int i2) {
        int iStart;
        synchronized (lock) {
            try {
                if (soundPool == shadowPool && i2 == 0) {
                    loadedSamples.add(Integer.valueOf(i));
                    Iterator<Map.Entry<Integer, PendingPlay>> it = pendingPlays.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Integer, PendingPlay> next = it.next();
                        PendingPlay value = next.getValue();
                        if (value.sample == i) {
                            it.remove();
                            Set<Integer> set = shadowStreams;
                            set.remove(next.getKey());
                            if (SystemClock.uptimeMillis() - value.time <= PENDING_MS && (iStart = value.start()) != 0) {
                                set.add(Integer.valueOf(iStart));
                                standIns.put(next.getKey(), Integer.valueOf(iStart));
                                Log.i(TAG, "played after the shadow load: stream " + next.getKey() + " -> " + iStart);
                            }
                        }
                    }
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static SoundPool shadowPool() {
        if (shadowPool == null) {
            SoundPool soundPoolBuild = new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(new AudioAttributes.Builder().setLegacyStreamType(STREAM_SYSTEM_ENFORCED).build()).build();
            shadowPool = soundPoolBuild;
            soundPoolBuild.setOnLoadCompleteListener(new hc1());
        }
        return shadowPool;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean shouldPlay(int i) {
        if (i != 2 && Settings.get("shutter_always")) {
            if (i == 0) {
                return Settings.get("shutter_in_silent");
            }
            if (i == 1) {
                return Settings.get("shutter_in_vibrate");
            }
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean stillWanted() {
        Application application = (Application) XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", (ClassLoader) null), "currentApplication", new Object[0]);
        if (application == null) {
            return true;
        }
        return shouldPlay(((AudioManager) application.getSystemService("audio")).getRingerMode());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static float volumeGain() {
        if (Settings.get("shutter_volume_custom")) {
            return Math.max(0, Math.min(100, Settings.getInt("shutter_volume"))) / 100.0f;
        }
        return 1.0f;
    }
}
