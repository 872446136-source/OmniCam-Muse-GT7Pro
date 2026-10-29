package local.jiege.hook.pop;

import local.jiege.hook.common.Log;

/**
 * POP port: the retro camera (POP) mode. Its settings, LUTs and Polaroid assets are staged by the
 * KSU module; the native half (pop_path_remap.cpp) redirects ODM reads to them.
 */
public final class PopPort {
    private static final String TAG = "PopPort";

    private PopPort() {}

    /** @param mainProcess resources are only needed in the camera's main process */
    public static void installCamera(ClassLoader classLoader, boolean mainProcess) {
        if (mainProcess) {
        try {
            PopResources.install(classLoader);
        } catch (Throwable error) {
            Log.e(TAG, "POP resource hooks unavailable", error);
        }
        try {
            PopGuard.install(classLoader);
        } catch (Throwable error) {
            Log.e(TAG, "POP crash guard unavailable", error);
        }
        }
        try {
            PopShutterGeometry.install(classLoader);
        } catch (Throwable error) {
            Log.e(TAG, "POP shutter geometry fix unavailable", error);
        }
        try {
            PopFaceRetouchGuard.install(classLoader);
        } catch (Throwable error) {
            Log.e(TAG, "POP face retouch guard unavailable", error);
        }
        try {
            PopHighPixel.install(classLoader);
        } catch (Throwable error) {
            Log.e(TAG, "POP high pixel hook unavailable", error);
        }
        try {
            PopSdrStyle.install(classLoader);
        } catch (Throwable error) {
            Log.e(TAG, "POP SDR style hook unavailable", error);
        }
        if (mainProcess) {
        try {
            PopFilterGate.install(classLoader);
        } catch (Throwable error) {
            Log.e(TAG, "POP filter gate unavailable", error);
        }
        // After the filter gate: it is what resolves RetroMode, and this gate scopes itself to that
        // class so it never touches the camera ids of the other modes.
        try {
            PopCaptureGate.install(classLoader);
        } catch (Throwable error) {
            Log.e(TAG, "POP capture gate unavailable", error);
        }
        // The camera unit's own copy of the preview decision: the last gate before the APS, and the
        // only one that lives outside the app. Armed by the capture gate above, per press.
        try {
            PopSdkGate.install(classLoader);
        } catch (Throwable error) {
            Log.e(TAG, "POP camera unit gate unavailable", error);
        }
        }
    }
}
