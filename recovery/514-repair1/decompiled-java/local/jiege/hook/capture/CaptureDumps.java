package local.jiege.hook.capture;

import local.jiege.hook.common.Settings;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class CaptureDumps {
    private CaptureDumps() {
    }

    public static void installCamera() {
        nativeConfigure(Settings.get("dump_raw"), Settings.get("dump_ccm_vec"), Settings.get("dump_hdrtransform"));
    }

    private static native void nativeConfigure(boolean z, boolean z2, boolean z3);
}
