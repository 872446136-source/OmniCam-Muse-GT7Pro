package local.jiege.hook.masterfilter;

import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class MasterFilterCompletion {
    private static final String TAG = "MasterFilter";

    private MasterFilterCompletion() {
    }

    public static void installCamera(ClassLoader classLoader) {
        try {
            MasterFilmLuts.install(classLoader);
        } catch (Throwable th) {
            Log.e(TAG, "master LUT hooks failed", th);
        }
        try {
            MasterNeonGlow.install(classLoader);
        } catch (Throwable th2) {
            Log.e(TAG, "neon glow hooks failed", th2);
        }
        try {
            MasterPortraHalation.install(classLoader);
        } catch (Throwable th3) {
            Log.e(TAG, "Portra halation hook failed", th3);
        }
        try {
            MasterGrain.install(classLoader);
        } catch (Throwable th4) {
            Log.e(TAG, "grain hooks failed", th4);
        }
        try {
            MasterPresetListSync.install(classLoader);
        } catch (Throwable th5) {
            Log.e(TAG, "preset list sync hook failed", th5);
        }
    }
}
