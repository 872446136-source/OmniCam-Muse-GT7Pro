package local.jiege.hook.pop;

import android.os.Build;
import defpackage.mo0;
import local.jiege.hook.common.Log;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class PopPort {
    private static final String TAG = "PopPort";

    private PopPort() {
    }

    public static void installCamera(ClassLoader classLoader, boolean z) {
        boolean z2 = false;
        if (z) {
            if ("RMX5010".equals(Build.MODEL)) {
                try {
                    PopPhotoBackend.install(classLoader);
                    z2 = true;
                } catch (Throwable th) {
                    Log.e(TAG, "POP photo backend unavailable", th);
                }
            }
            try {
                PopResources.install(classLoader);
            } catch (Throwable th2) {
                Log.e(TAG, "POP resource hooks unavailable", th2);
            }
            try {
                PopGuard.install(classLoader);
            } catch (Throwable th3) {
                Log.e(TAG, "POP crash guard unavailable", th3);
            }
        }
        try {
            PopShutterGeometry.install(classLoader);
        } catch (Throwable th4) {
            Log.e(TAG, "POP shutter geometry fix unavailable", th4);
        }
        try {
            PopFaceRetouchGuard.install(classLoader);
        } catch (Throwable th5) {
            Log.e(TAG, "POP face retouch guard unavailable", th5);
        }
        try {
            PopHighPixel.install(classLoader);
        } catch (Throwable th6) {
            Log.e(TAG, "POP high pixel hook unavailable", th6);
        }
        Log.guard(TAG, "POP preview parameters", new mo0(classLoader, (byte) 26));
        Log.guard(TAG, "POP tint preview", new mo0(classLoader, (byte) 27));
        Log.guard(TAG, "POP LUT diagnostics", new mo0(classLoader, (byte) 28));
        try {
            PopSdrStyle.install(classLoader);
        } catch (Throwable th7) {
            Log.e(TAG, "POP SDR style hook unavailable", th7);
        }
        if (z) {
            try {
                PopFilterGate.install(classLoader);
            } catch (Throwable th8) {
                Log.e(TAG, "POP filter gate unavailable", th8);
            }
            if (!z2) {
                try {
                    PopCaptureGate.install(classLoader);
                } catch (Throwable th9) {
                    Log.e(TAG, "POP capture gate unavailable", th9);
                }
            }
            if (z2) {
                return;
            }
            try {
                PopSdkGate.install(classLoader);
            } catch (Throwable th10) {
                Log.e(TAG, "POP camera unit gate unavailable", th10);
            }
        }
    }
}
