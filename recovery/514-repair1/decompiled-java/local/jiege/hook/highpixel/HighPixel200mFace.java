package local.jiege.hook.highpixel;

import java.io.File;
import local.jiege.hook.common.MarkerSwitch;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class HighPixel200mFace {
    private HighPixel200mFace() {
    }

    public static void installCamera(ClassLoader classLoader) {
        MarkerSwitch.install("HighPixel200mFace", "high_pixel_200m_no_face", new File("/data/user/0/com.oplus.camera/files/jiege/face200m"));
    }
}
