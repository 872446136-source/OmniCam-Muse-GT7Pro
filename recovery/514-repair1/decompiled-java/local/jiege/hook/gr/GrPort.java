package local.jiege.hook.gr;

import local.jiege.hook.common.Log;
import local.jiege.hook.gr.GrCameraWatermark;
import local.jiege.hook.gr.GrEffectTransport;
import local.jiege.hook.gr.GrFocalLength;
import local.jiege.hook.gr.GrRatioGuard;
import local.jiege.hook.gr.GrShutterSound;
import local.jiege.hook.gr.GrSnapFocus;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class GrPort {
    private static final String TAG = "RicohGrPort";

    private GrPort() {
    }

    private static void install(String str, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable th) {
            Log.e(TAG, "GR " + str + " hooks failed", th);
        }
    }

    public static void installCamera(final ClassLoader classLoader) {
        try {
            GrModeHooks.install(classLoader);
            final byte b = 0;
            install("effect transport", new Runnable() { // from class: o40
                @Override // java.lang.Runnable
                public final void run() {
                    byte b2 = b;
                    ClassLoader classLoader2 = classLoader;
                    switch (b2) {
                        case 0:
                            GrEffectTransport.install(classLoader2);
                            break;
                        case 1:
                            GrFocalLength.install(classLoader2);
                            break;
                        case 2:
                            GrSnapFocus.install(classLoader2);
                            break;
                        case 3:
                            GrRatioGuard.install(classLoader2);
                            break;
                        case 4:
                            GrCameraWatermark.install(classLoader2);
                            break;
                        default:
                            GrShutterSound.install(classLoader2);
                            break;
                    }
                }
            });
            final byte b2 = 1;
            install("focal length", new Runnable() { // from class: o40
                @Override // java.lang.Runnable
                public final void run() {
                    byte b3 = b2;
                    ClassLoader classLoader2 = classLoader;
                    switch (b3) {
                        case 0:
                            GrEffectTransport.install(classLoader2);
                            break;
                        case 1:
                            GrFocalLength.install(classLoader2);
                            break;
                        case 2:
                            GrSnapFocus.install(classLoader2);
                            break;
                        case 3:
                            GrRatioGuard.install(classLoader2);
                            break;
                        case 4:
                            GrCameraWatermark.install(classLoader2);
                            break;
                        default:
                            GrShutterSound.install(classLoader2);
                            break;
                    }
                }
            });
            final byte b3 = 2;
            install("snap focus", new Runnable() { // from class: o40
                @Override // java.lang.Runnable
                public final void run() {
                    byte b4 = b3;
                    ClassLoader classLoader2 = classLoader;
                    switch (b4) {
                        case 0:
                            GrEffectTransport.install(classLoader2);
                            break;
                        case 1:
                            GrFocalLength.install(classLoader2);
                            break;
                        case 2:
                            GrSnapFocus.install(classLoader2);
                            break;
                        case 3:
                            GrRatioGuard.install(classLoader2);
                            break;
                        case 4:
                            GrCameraWatermark.install(classLoader2);
                            break;
                        default:
                            GrShutterSound.install(classLoader2);
                            break;
                    }
                }
            });
            final byte b4 = 3;
            install("ratio guard", new Runnable() { // from class: o40
                @Override // java.lang.Runnable
                public final void run() {
                    byte b5 = b4;
                    ClassLoader classLoader2 = classLoader;
                    switch (b5) {
                        case 0:
                            GrEffectTransport.install(classLoader2);
                            break;
                        case 1:
                            GrFocalLength.install(classLoader2);
                            break;
                        case 2:
                            GrSnapFocus.install(classLoader2);
                            break;
                        case 3:
                            GrRatioGuard.install(classLoader2);
                            break;
                        case 4:
                            GrCameraWatermark.install(classLoader2);
                            break;
                        default:
                            GrShutterSound.install(classLoader2);
                            break;
                    }
                }
            });
            final byte b5 = 4;
            install("camera watermark", new Runnable() { // from class: o40
                @Override // java.lang.Runnable
                public final void run() {
                    byte b6 = b5;
                    ClassLoader classLoader2 = classLoader;
                    switch (b6) {
                        case 0:
                            GrEffectTransport.install(classLoader2);
                            break;
                        case 1:
                            GrFocalLength.install(classLoader2);
                            break;
                        case 2:
                            GrSnapFocus.install(classLoader2);
                            break;
                        case 3:
                            GrRatioGuard.install(classLoader2);
                            break;
                        case 4:
                            GrCameraWatermark.install(classLoader2);
                            break;
                        default:
                            GrShutterSound.install(classLoader2);
                            break;
                    }
                }
            });
            final byte b6 = 5;
            install("shutter sounds", new Runnable() { // from class: o40
                @Override // java.lang.Runnable
                public final void run() {
                    byte b7 = b6;
                    ClassLoader classLoader2 = classLoader;
                    switch (b7) {
                        case 0:
                            GrEffectTransport.install(classLoader2);
                            break;
                        case 1:
                            GrFocalLength.install(classLoader2);
                            break;
                        case 2:
                            GrSnapFocus.install(classLoader2);
                            break;
                        case 3:
                            GrRatioGuard.install(classLoader2);
                            break;
                        case 4:
                            GrCameraWatermark.install(classLoader2);
                            break;
                        default:
                            GrShutterSound.install(classLoader2);
                            break;
                    }
                }
            });
        } catch (Throwable th) {
            Log.e(TAG, "GR mode unavailable, GR port disabled", th);
        }
    }

    public static void installGallery(ClassLoader classLoader) {
        GrGalleryWatermark.install(classLoader);
    }
}
