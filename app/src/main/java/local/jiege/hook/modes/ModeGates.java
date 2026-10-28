package local.jiege.hook.modes;

import android.app.Application;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import local.jiege.hook.common.Log;

/**
 * Support flags the camera reads out of {@code oplus_camera_config}, answered in-process.
 *
 * <p>Two things need them and neither can be reached from the profile:
 *
 * <ul>
 *   <li>The mode bar is seeded into {@code databases/mode_data.db} once, and each row is written
 *       behind a flag — {@code gr} needs {@code com.oplus.gr.mode.support}, {@code retroCamera}
 *       needs {@code com.oplus.feature.retro.camera.support}. Neither key exists in this device's
 *       config, and an absent key answers false forever, so re-seeding the database writes the
 *       same missing rows.
 *   <li>The camera's own 哈苏 watermark is behind
 *       {@code com.oplus.camera.support.custom.hasselblad.watermark}, which this device ships as 0.
 * </ul>
 *
 * <p>Both are read through {@code CameraConfig.getConfigBooleanValue}, which goes to
 * {@code CameraUnitUtils.getVendorTagConfig(key)} — the config file itself. The answer is given
 * here, for this process only; nothing is written to disk and with the module off the flags read
 * exactly as the firmware ships them.
 *
 * <p>Timing is the whole point of this class. The flags are answered from
 * {@code Application.onCreate}, never from {@code handleLoadPackage}: resolving or calling into
 * CameraConfig while the process is still being bound (before the app exists) loads that class and
 * runs its config engine too early, and the camera then comes up to a black screen. For the same
 * reason the class is loaded with {@code initialize = false} and the original method is never
 * called by this class — the first call is the camera's own.
 *
 * <p>470 got the shape wrong: it read {@code param.args[1]} for a "default" that this method does
 * not take — {@code getConfigBooleanValue(String)} has exactly one argument — so every call threw
 * ArrayIndexOutOfBounds inside the hook, Xposed swallowed it, and the answer was never set. The
 * rows were then never written, and the camera froze for the full database query timeout whenever
 * a mode in the bar had no row. Hence: answer first, log second, and never index past
 * {@code args[0]}.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_gates_off} turns this part off without touching
 * anything else.
 */
public final class ModeGates {
    private static final String TAG = "ModeGates";
    private static final String CONFIG = "com.oplus.camera.configure.CameraConfig";
    private static final String GETTER = "getConfigBooleanValue";
    /** Same question, different door: (key, default, cache). */
    private static final String CACHED = "d";
    private static final String OFF = "/data/adb/omnicam_gates_off";

    /** Flags answered true. Keys that are absent from the config can only be answered here. */
    private static final String[] FORCED = {
        "com.oplus.gr.mode.support",
        "com.oplus.feature.retro.camera.support",
        "com.oplus.feature.xpan.mode.support",
        // 大师模式 (ProfessionalMode) has no row of its own without this: the key is not in this
        // device's config at all, so the seeding code skips the mode and only a move config puts
        // it in the table. See ModePlacement for where it ends up.
        "com.oplus.feature.professional.mode.support",
        // 大师模式 in the bar rather than the panel. The key is read while the headline rows are
        // written; this device ships it as 0, which is why the reference layout never happened here.
        "com.oplus.support.professional.mode.in.headline",
        // 哈苏超清 / 50 MP. This is also the flag that puts the highPixel row in the mode bar:
        // dq.e.i reads it while seeding the headline, so the page and its entry come together.
        "com.oplus.camera.hasselblad.super.definition.support",
        "com.oplus.use.hasselblad.style.support",
        "com.oplus.feature.high.pixel.support",
        "com.oplus.feature.highpixel.merge.support",
        "com.oplus.high_pixel.global.ev.support",
        "com.oplus.support.highpixel.gesture_shutter",
        // 4K live photo inside the high pixel page.
        "com.oplus.camera.high.pixel.mode.4k.live.support",
        // 电影模式 and the master video page it shares the log UI with.
        "com.oplus.feature.movie.mode.support",
        "com.oplus.feature.movie.mode.V2.support",
        "com.oplus.feature.movie.customize.video.size.support",
        "com.oplus.director.filter.support",
        "com.oplus.feature.master.film.filter.label.enable",
        "com.oplus.feature.master.hq.raw.support",
        "com.oplus.feature.master.video.focus.peaking.histogram.oplus.r.support",
        "com.oplus.master.mode.enable.zsl",
        "com.oplus.use.real.master.pipeline",
        "com.oplus.master.video.three.state.stabilization",
        // 大师视频 60 fps. Master video sits on 800b (the ordinary recorder op mode) so this is the
        // switch that actually reaches the HAL; the film pipeline keeps its own 30 fps limit.
        "com.oplus.feature.master.video.60fps.support",
        // XPAN without the develop animation. pq.h.n() reads exactly this key to choose between the
        // legacy panel and the animated one, and the animated one is what freezes this phone.
        "com.oplus.xpan.legacy.ui.style",
        "com.oplus.camera.support.custom.hasselblad.watermark",
        "com.oplus.camera.support.custom.hasselblad.watermark.sellmode.default.open",
    };

    /**
     * Flags answered false. {@code video.focus.track.30fps.only} ships as 1 and forces the whole
     * recorder path down to 30 fps whenever tracking focus is on, which is the default — so a 60 fps
     * selection is accepted by the menu and then silently recorded at 30.
     */
    private static final String[] FORCED_FALSE = {
        "com.oplus.video.focus.track.30fps.only",
        "com.oplus.feature.highpixel.merge.not.support.zoom",
    };

    /** Integer answers, keyed exactly. See {@link #hookScalarGetters}. */
    private static final String[] INT_KEYS = {
        "com.oplus.log.version.curve",
        "com.oplus.camera.master.captureparam.version",
        "com.oplus.camera.highpixel.livephoto.video.max.duration",
        "com.oplus.camera.hasslblad.livephoto.capture.interval",
        "com.oplus.xpan.mode.version",
    };
    private static final int[] INT_VALUES = {2, 1, 3000, 1000, 3};

    /**
     * String answers, keyed exactly. See {@link #hookScalarGetters}.
     */
    private static final String[] STRING_KEYS = {
        "com.oplus.feature.master.mode.version",
        "com.oplus.feature.movie.mode.video.size",
    };
    private static final String[] STRING_VALUES = {"2.0", "3840x2160"};

    /**
     * The three XPAN picture sizes, filled only when the camera's own answer is null.
     *
     * <p>488. {@code XpanMode.T9} asks {@code CameraConfig.getSizeConfigValue} for
     * {@code com.oplus.xpan.main.picturesize} (or the wide one, depending on the lens) and that is
     * the size the XPAN capture is requested at. With the key absent the call answers null, the mode
     * falls back to the ordinary 4:3 size, and the crop region below no longer fits the frame —
     * which is exactly the 487 report: XPAN opens, shoots, and saves a 3072x4096 photo instead of a
     * 7872x2912 frame. See {@link #hookSizeGetters}.
     */
    private static final String[] SIZE_KEYS = {
        "com.oplus.xpan.main.picturesize",
        "com.oplus.xpan.wide.picturesize",
        "com.oplus.xpan.tele.picturesize",
    };
    private static final String[] SIZE_VALUES = {"7872x2912", "7872x2912", "7872x2912"};

    /**
     * The four XPAN crop regions, filled only when the camera's own answer is empty.
     *
     * <p>{@code XpanModel.onCreate} reads all four through {@code CameraConfig.l(String, int[])}
     * and stores them as the viewfinder region, the mask and the coordinate the watermark
     * composite places the photo at. This phone's {@code oplus_camera_config} is encrypted, and the
     * keys never reach the app's config map, so {@code l} hands back the default — an empty array —
     * and every consumer sees "no crop". The values are the ones this firmware's own config carries
     * for an 8192x6144 sensor (the same numbers the X8U and the reference GT7 Pro port use):
     * portrait x,y,w,h = 360,64,2912,7872 and landscape 64,360,7872,2912.
     *
     * <p>Filled, never forced: if a future firmware answers these itself, that answer is kept.
     */
    private static final String[] REGION_KEYS = {
        "com.oplus.xpan.crop.region.main.portrait",
        "com.oplus.xpan.crop.region.main.landscape",
        "com.oplus.xpan.crop.region.wide.portrait",
        "com.oplus.xpan.crop.region.wide.landscape",
    };
    private static final int[][] REGION_VALUES = {
        {360, 64, 2912, 7872},
        {64, 360, 7872, 2912},
        {360, 64, 2912, 7872},
        {64, 360, 7872, 2912},
    };

    /** Keys already logged, so one report carries each answer once. */
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private static final String MASTER_VIDEO_LIST = "com.oplus.camera.master.video.feature.list";

    /**
     * The master video feature slots, in the order {@code r9.i} indexes them: 0 base, 1 mode gate,
     * 4 log session, 6 log menu, 7 three-state, 8 LUT monitor, 10 histogram, 11 log page. Twelve
     * on, the two trailing slots this phone ships as 0.
     */
    private static final int[] FEATURE_LIST = {1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 0, 0};

    /**
     * Every distinct key the camera asks about, logged once, capped. POP is still missing after the
     * flags started answering, and the two possible reasons look identical from outside: the camera
     * never asks about {@code retroCamera}, or it asks and drops the row somewhere else. This list
     * tells them apart from a single report.
     */
    private static final Set<String> ASKED = ConcurrentHashMap.newKeySet();
    private static final int ASKED_LIMIT = 100;

    private static boolean installed;

    private ModeGates() {}

    /**
     * Arms the flags for the camera's main process: nothing is resolved here, only the two framework
     * hooks that fire once the app is up. Both are armed because an app that overrides
     * {@code onCreate} without calling super would never reach the first one.
     */
    public static void installCamera(final ClassLoader classLoader) {
        if (new File(OFF).exists()) {
            Log.i(TAG, "gates off (" + OFF + "), flags left as shipped");
            return;
        }
        afterApplication(classLoader, new Runnable() {
            @Override public void run() {
                install(classLoader);
            }
        });
    }

    /**
     * Runs {@code task} once the camera's Application exists.
     *
     * <p>This is the fix 470 shipped and 471 kept: resolving a camera class from
     * {@code handleLoadPackage} — {@code handleBindApplication} is still running and the
     * Application has not been created — starts the camera's config engine early and the camera
     * then comes up to a black screen. Everything that touches those classes goes through here.
     *
     * <p>Public because the shutter probe needs the same timing and lives in its own package.
     */
    public static void afterApplication(final ClassLoader classLoader, final Runnable task) {
        XC_MethodHook arm = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                task.run();
            }
        };
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", arm);
        } catch (Throwable error) {
            Log.e(TAG, "application onCreate hook unavailable", error);
        }
        try {
            XposedHelpers.findAndHookMethod(android.app.Instrumentation.class, "callApplicationOnCreate",
                Application.class, arm);
        } catch (Throwable error) {
            Log.e(TAG, "callApplicationOnCreate hook unavailable", error);
        }
    }

    /** Runs once, from Application.onCreate: the camera's own config engine is up by then. */
    private static synchronized void install(ClassLoader classLoader) {
        if (installed) return;
        installed = true;
        Method getter;
        Class<?> config;
        try {
            config = Class.forName(CONFIG, false, classLoader);
            getter = find(config);
        } catch (Throwable error) {
            Log.e(TAG, "flag getter unavailable", error);
            return;
        }
        try {
            XposedBridge.hookMethod(getter, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Object key = param.args[0];
                    if (!(key instanceof String)) return;
                    String name = (String) key;
                    if (forced(name)) {
                        // Answer first, log after: this runs on the camera's own thread while it
                        // seeds its mode database, so nothing below may take the answer back.
                        param.setResult(Boolean.TRUE);
                        if (SEEN.add(name)) log(name + " answered true");
                        return;
                    }
                    if (forcedFalse(name)) {
                        param.setResult(Boolean.FALSE);
                        if (SEEN.add(name)) log(name + " answered false");
                        return;
                    }
                    if (ASKED.size() < ASKED_LIMIT && ASKED.add(name)) log("asked: " + name);
                }
            });
            Log.i(TAG, "flag getter hooked, " + FORCED.length + " true, "
                + FORCED_FALSE.length + " false");
        } catch (Throwable error) {
            Log.e(TAG, "flag hook failed", error);
        }
        hookCachedGetter(config);
        hookMasterVideoFeatureList(config);
        hookScalarGetters(config);
        hookSizeGetters(config);
    }

    /**
     * {@code CameraConfig.l(String, int[]) int[]} — the master video feature array.
     *
     * <p>{@code r9.i} reads it once, into a static field, and every "does master video have X"
     * question in the camera goes through that field: index 4 gates the log session, 6 the log menu,
     * 8 the LUT monitor and 11 the log page itself. This phone ships 14 slots with only 0 and 10 on,
     * so the whole page is dark. The values below are the ones the X8U and the B6 port both use for
     * the first twelve slots; the last two stay as this phone shipped them, because the array is
     * indexed up to 17 and a short array would read as zero anyway.
     *
     * <p>Answered here rather than only in the config profile so that a profile which failed to
     * stage still leaves the page reachable.
     */
    private static void hookMasterVideoFeatureList(Class<?> config) {
        Method reader = null;
        for (Method method : config.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 2 && types[0] == String.class && types[1] == int[].class
                && method.getReturnType() == int[].class) {
                reader = method;
                break;
            }
        }
        if (reader == null) {
            Log.i(TAG, "master video feature reader not found, profile value only");
            return;
        }
        try {
            XposedBridge.hookMethod(reader, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Object key = param.args[0];
                    if (!(key instanceof String)) return;
                    if (!MASTER_VIDEO_LIST.equals(key)) return;
                    param.setResult(FEATURE_LIST.clone());
                    if (SEEN.add(MASTER_VIDEO_LIST)) log("master video feature list answered, "
                        + FEATURE_LIST.length + " slots");
                }

                @Override protected void afterHookedMethod(MethodHookParam param) {
                    // The XPAN crop regions go through the same reader, but they are filled rather
                    // than forced: an answer the firmware supplies is kept, only an empty one is
                    // replaced. See REGION_KEYS.
                    if (param.hasThrowable()) return;
                    Object key = param.args[0];
                    if (!(key instanceof String)) return;
                    int index = indexOfRegion((String) key);
                    if (index < 0) return;
                    int[] answer = (int[]) param.getResult();
                    if (answer != null && answer.length == 4 && answer[2] > 0 && answer[3] > 0) {
                        if (SEEN.add("region:" + key)) log(key + " came from the config: "
                            + answer[0] + "," + answer[1] + "," + answer[2] + "," + answer[3]);
                        return;
                    }
                    param.setResult(REGION_VALUES[index].clone());
                    if (SEEN.add("region:" + key)) log(key + " was empty, answered "
                        + REGION_VALUES[index][0] + "," + REGION_VALUES[index][1] + ","
                        + REGION_VALUES[index][2] + "," + REGION_VALUES[index][3]);
                }
            });
            Log.i(TAG, "master video feature list hooked, " + REGION_KEYS.length
                + " XPAN crop regions filled when empty");
        } catch (Throwable error) {
            Log.e(TAG, "master video feature hook failed", error);
        }
    }

    /**
     * {@code CameraConfig.getSizeConfigValue(String, boolean) Size} — the XPAN capture size.
     *
     * <p>{@code XpanMode.T9} asks this for {@code com.oplus.xpan.main.picturesize} and hands the
     * result to the capture request. Filled only when the camera has nothing, so a firmware that
     * already answers it keeps its own value and this hook never overrides a real size.
     */
    private static void hookSizeGetters(Class<?> config) {
        int armed = 0;
        for (Method method : config.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (types.length != 2 || types[0] != String.class || types[1] != boolean.class) continue;
            if (method.getReturnType() != android.util.Size.class) continue;
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) return;
                        Object key = param.args[0];
                        if (!(key instanceof String)) return;
                        int index = indexOfSize((String) key);
                        if (index < 0) return;
                        Object answer = param.getResult();
                        if (answer != null) {
                            if (SEEN.add("size:" + key)) log(key + " came from the config: " + answer);
                            return;
                        }
                        android.util.Size size = sizeOf(SIZE_VALUES[index]);
                        if (size == null) return;
                        param.setResult(size);
                        if (SEEN.add("size:" + key)) log(key + " was null, answered " + size);
                    }
                });
                armed++;
            } catch (Throwable error) {
                Log.e(TAG, "size hook failed on " + method.getName(), error);
            }
        }
        if (armed == 0) Log.i(TAG, "no size config reader found, the XPAN size is left alone");
    }

    /** {@code "7872x2912"} as a Size, or null when the entry is not a size. */
    private static android.util.Size sizeOf(String value) {
        int x = value.indexOf('x');
        if (x <= 0) return null;
        try {
            return new android.util.Size(Integer.parseInt(value.substring(0, x)),
                Integer.parseInt(value.substring(x + 1)));
        } catch (NumberFormatException notASize) {
            return null;
        }
    }

    private static int indexOfRegion(String key) {
        for (int i = 0; i < REGION_KEYS.length; i++) if (REGION_KEYS[i].equals(key)) return i;
        return -1;
    }

    private static int indexOfSize(String key) {
        for (int i = 0; i < SIZE_KEYS.length; i++) if (SIZE_KEYS[i].equals(key)) return i;
        return -1;
    }

    /**
     * {@code CameraConfig.d(String, boolean, boolean)} — the same question through the other door.
     *
     * <p>The seeding code asks some of its flags with this one as well as with
     * {@code getConfigBooleanValue}: both appear in {@code dq.e.i} and {@code dq.e.j}. Answering
     * only the first leaves the rows that go through {@code d} reading the shipped value. The two
     * booleans are a default and a cache flag, so only the key is looked at.
     */
    private static void hookCachedGetter(Class<?> config) {
        Method cached = null;
        for (Method method : config.getDeclaredMethods()) {
            if (!CACHED.equals(method.getName())) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 3 && types[0] == String.class && types[1] == boolean.class
                && types[2] == boolean.class && method.getReturnType() == boolean.class) {
                cached = method;
                break;
            }
        }
        if (cached == null) {
            Log.i(TAG, CACHED + " not found, only the plain getter is answered");
            return;
        }
        try {
            XposedBridge.hookMethod(cached, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Object key = param.args[0];
                    if (!(key instanceof String)) return;
                    String name = (String) key;
                    if (forced(name)) {
                        param.setResult(Boolean.TRUE);
                        if (SEEN.add(name)) log(name + " answered true via " + CACHED);
                    } else if (forcedFalse(name)) {
                        param.setResult(Boolean.FALSE);
                        if (SEEN.add(name)) log(name + " answered false via " + CACHED);
                    }
                }
            });
            Log.i(TAG, CACHED + " hooked too");
        } catch (Throwable error) {
            Log.e(TAG, CACHED + " hook failed", error);
        }
    }

    /**
     * The scalar answers: integers and strings that are not switches.
     *
     * <p>Every one of these is a key this phone does not have at all, so the camera falls back to the
     * default its own call site passes. The defaults are all older than the feature they describe:
     *
     * <ul>
     *   <li>{@code log.version.curve} default 1 — the log page would only know the older curve and
     *       never offer O-Log2. {@code r9.i.a()} reads it through {@code m(int, String)}.
     *   <li>{@code feature.master.mode.version} — {@code la.l0} reads it four times to decide which
     *       master colour features exist; without it the page stays at the v1 set.
     *   <li>{@code feature.movie.mode.video.size} — {@code fn.g.lk()} builds the film mode size from
     *       it; absent, the mode records at whatever the fallback is.
     *   <li>{@code highpixel.livephoto.video.max.duration} and
     *       {@code hasslblad.livephoto.capture.interval} — the 4K live clip length and the shutter
     *       interval behind it, read by {@code oh.y1}.
     * </ul>
     *
     * <p>Hooked by shape, not by name: every {@code CameraConfig} reader that takes a key and
     * returns an int or a String is armed, and the key is found by scanning the arguments. A call
     * that asks about anything else returns immediately.
     */
    private static void hookScalarGetters(Class<?> config) {
        int armed = 0;
        for (Method method : config.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            Class<?> result = method.getReturnType();
            boolean takesKey = false;
            for (Class<?> type : types) if (type == String.class) takesKey = true;
            if (!takesKey || (result != int.class && result != String.class)) continue;
            if (method.getName().equals(GETTER) || method.getName().equals(CACHED)) continue;
            if (result == int.class && types.length == 2 && types[0] == String.class
                && types[1] == int[].class) {
                continue;  // the feature array, handled above
            }
            final boolean returnsInt = result == int.class;
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Object found = keyOf(param.args);
                        if (found == null) return;
                        String key = (String) found;
                        if (returnsInt) {
                            int index = indexOfInt(key);
                            if (index < 0) return;
                            param.setResult(INT_VALUES[index]);
                            if (SEEN.add(key)) log(key + " answered " + INT_VALUES[index]);
                        } else {
                            int index = indexOfString(key);
                            if (index < 0) return;
                            param.setResult(STRING_VALUES[index]);
                            if (SEEN.add(key)) log(key + " answered " + STRING_VALUES[index]);
                        }
                    }
                });
                armed++;
            } catch (Throwable error) {
                Log.e(TAG, "scalar hook failed on " + method.getName(), error);
            }
        }
        Log.i(TAG, "scalar readers hooked: " + armed);
    }

    /** The first argument that names one of the keys this class answers. */
    private static Object keyOf(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (!(arg instanceof String)) continue;
            String name = (String) arg;
            if (indexOfInt(name) >= 0 || indexOfString(name) >= 0) return name;
        }
        return null;
    }

    private static int indexOfInt(String key) {
        for (int i = 0; i < INT_KEYS.length; i++) if (INT_KEYS[i].equals(key)) return i;
        return -1;
    }

    private static int indexOfString(String key) {
        for (int i = 0; i < STRING_KEYS.length; i++) if (STRING_KEYS[i].equals(key)) return i;
        return -1;
    }

    /** getConfigBooleanValue(String)Z, static or not — this build's shape is not assumed. */
    private static Method find(Class<?> config) throws NoSuchMethodException {
        for (Method method : config.getDeclaredMethods()) {
            if (!GETTER.equals(method.getName())) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 1 && types[0] == String.class && method.getReturnType() == boolean.class) {
                return method;
            }
        }
        throw new NoSuchMethodException(config.getName() + "." + GETTER);
    }

    /** Logging must never be able to undo an answer that was already set. */
    private static void log(String message) {
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
        }
    }

    private static boolean forced(String key) {
        for (String flag : FORCED) if (flag.equals(key)) return true;
        return false;
    }

    private static boolean forcedFalse(String key) {
        for (String flag : FORCED_FALSE) if (flag.equals(key)) return true;
        return false;
    }
}
