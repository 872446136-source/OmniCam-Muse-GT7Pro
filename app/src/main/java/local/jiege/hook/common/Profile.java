package local.jiege.hook.common;

/**
 * Which feature set this build installs.
 *
 * This is the "full port minus the Find X9 Ultra algorithm stack" build. NATIVE_STACK is false, so
 * no hook that drives libjiege_native.so is installed — and the library is not shipped at all
 * (assets/native_init is removed and app/build.gradle.kts has no externalNativeBuild).
 *
 * What stays: the hooks that only touch the camera's own Java classes plus the module's data layer
 * (filter LUTs under /odm/etc/camera/meishe_lut, X10 basictone under /odm/etc/camera/basictone, the
 * GR LMT tree and the watermark artwork staged into the app data directory).
 *
 * What goes, because it rides the X9 Ultra algorithm stack — the patched libAlgoInterface /
 * libBasicTonePhoto(X9/X10) / libCombineLut, the ISP tuned bins, the gamma tables, the upstream
 * camera config and the 25021 decision family:
 *   mastercolour  master mode colour (HNCS / Lumix / film look tables)
 *   natural       natural photo mode (AI detail off, LCE off, extra softening)
 *   softlight     soft light portrait lighting
 *   masterlut     master LUT slots, contrast and grain
 *   masterfilter  master filter completion (extra LUTs, neon glow, Portra halation)
 *   pop           POP retro camera (path remap + pld_watermark)
 *   seamless      seamless-UI blur guard (POP thumbnail)
 *   highpixel     25MP unlock and the 200MP big-face gate
 *   tilt          tilt-shift capture algorithm
 *   palette       gallery palette editor (needs libCombineLut through jiege_ipu)
 *   xpan          XPAN live photo, viewfinder clean and watermark isolation
 *
 * Flipping NATIVE_STACK to true restores the full hook list, but it needs three things back first:
 * app/src/main/cpp, assets/native_init, and module/system/odm/lib64/*.so. Without them the hooks
 * call native methods that do not exist.
 *
 * Kill switch, no need to uninstall the module (post-fs-data.sh and service.sh both honour it):
 *   adb shell su -c "touch /data/adb/omnicam_nostack_off"  then reboot
 */
public final class Profile {
    /** Hooks that drive libjiege_native.so — off, the library is not part of this build. */
    public static final boolean NATIVE_STACK = false;

    private Profile() {}
}
