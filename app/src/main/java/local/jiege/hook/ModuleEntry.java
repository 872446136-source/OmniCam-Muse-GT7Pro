package local.jiege.hook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import local.jiege.hook.common.CameraLogMirror;
import local.jiege.hook.common.Capabilities;
import local.jiege.hook.common.Diagnostics;
import local.jiege.hook.common.Install;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.FilterGroupInjector;
import local.jiege.hook.gallery.AddressNavigation;
import local.jiege.hook.gallery.BatchWatermark;
import local.jiege.hook.gallery.WatermarkSeriesUnlock;
import local.jiege.hook.gr.GrPort;
import local.jiege.hook.highpixel.HighPixel200mFace;
import local.jiege.hook.highpixel.HighPixelEffects;
import local.jiege.hook.launch.KeyLaunchMode;
import local.jiege.hook.mastercolor.MasterColor;
import local.jiege.hook.masterfilter.MasterFilterCompletion;
import local.jiege.hook.masterlut.MasterContrast;
import local.jiege.hook.masterlut.MasterLutSlots;
import local.jiege.hook.modestate.ModeStateSplit;
import local.jiege.hook.natural.NaturalMode;
import local.jiege.hook.palette.GalleryColorPalette;
import local.jiege.hook.pop.PopPort;
import local.jiege.hook.seamless.SeamlessBlurGuard;
import local.jiege.hook.shutter.CameraSoundForced;
import local.jiege.hook.shutter.ShutterSound;
import local.jiege.hook.softlight.SoftLight;
import local.jiege.hook.tilt.TiltShiftCapture;
import local.jiege.hook.unlock25mp.Unlock25mp;
import local.jiege.hook.video.VideoFilterHighSpec;
import local.jiege.hook.watermark.ModelSpoof;
import local.jiege.hook.watermark.VideoWatermarkGuard;
import local.jiege.hook.watermark.XpanWatermarkIsolation;
import local.jiege.hook.x10.X10FilterPort;
import local.jiege.hook.xpan.XpanLivePhoto;
import local.jiege.hook.xpan.XpanViewfinderClean;

/**
 * LSPosed entry (assets/xposed_init). The native part is libjiege_native.so, declared in
 * assets/native_init so LSPosed loads it into every scoped process before these hooks run.
 *
 * <p>One build, every feature, no Lite profile. Each feature declares what it needs from
 * {@link Capabilities} and is installed only when this device has it; {@link Install} records the
 * outcome so the settings app can report exactly what is running on this phone and why the rest is
 * not. Nothing here replaces a system library or an ODM config — that rule is what keeps the camera
 * alive on a device whose config the module cannot substitute.
 *
 * <p>Obfuscated camera and gallery code is reached through Symbols (fingerprints in
 * assets/symbols.json), resolved once per app build before any feature installs.
 */
public final class ModuleEntry implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String CAMERA = "com.oplus.camera";
    private static final String GALLERY = "com.coloros.gallery3d";
    /** System framework (system_server) scope. */
    private static final String SYSTEM = "android";

    @Override public void initZygote(StartupParam param) {
        Symbols.setModulePath(param.modulePath);
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        ClassLoader classLoader = param.classLoader;
        if (SYSTEM.equals(param.packageName)) {
            CameraSoundForced.installSystem(classLoader);
            BatchWatermark.installSystem(classLoader);
        } else if (GALLERY.equals(param.packageName)) {
            installGallery(classLoader, param);
        } else if (CAMERA.equals(param.packageName)) {
            installCamera(classLoader, param);
        }
    }

    private void installGallery(ClassLoader classLoader, XC_LoadPackage.LoadPackageParam param) {
        Symbols.install("gallery", classLoader, param.appInfo.sourceDir, param.appInfo.dataDir);

        boolean data = Capabilities.dataLayer();
        String noData = "滤镜 / GR 数据未挂载，KSU 模块没生效";

        Install.run("相册地址跳转", true, "", () -> AddressNavigation.installGallery(classLoader));
        Install.run("水印系列解锁", true, "", () -> WatermarkSeriesUnlock.installGallery(classLoader));
        Install.run("批量水印", true, "", () -> BatchWatermark.installGallery(classLoader));
        Install.run("GR 相册水印", data, noData, () -> GrPort.installGallery(classLoader));
        Install.run("相册调色板", data, noData, () -> GalleryColorPalette.installGallery(classLoader));
        Log.i("Install", Install.summary());
    }

    private void installCamera(ClassLoader classLoader, XC_LoadPackage.LoadPackageParam param) {
        Symbols.install("camera", classLoader, param.appInfo.sourceDir, param.appInfo.dataDir);

        // The native half carries the in-process path redirection (master colour live tables, GR LMT,
        // imported master filters) and the library-side hooks (LMT params, soft light, LUT slots).
        // Without it the pure Java features still install; the ones that need it are skipped below.
        boolean nativeLibrary = loadNative();
        Capabilities.setNativeLibrary(nativeLibrary);
        Log.i("Capabilities", Capabilities.describe());

        boolean data = Capabilities.dataLayer();
        boolean lmt = Capabilities.lmt();
        Diagnostics.setFilesMounted(data);
        String noData = "滤镜 / GR 数据未挂载，KSU 模块没生效";
        String noNative = "原生层未加载";
        String noLmt = "本机没有 /odm/etc/camera/basictone/lmt";

        // --- pure app layer: independent of the module's payload, so always installed ---
        Install.run("快启键模式", true, "", () -> KeyLaunchMode.installCamera(classLoader));
        Install.run("快门声", true, "", () -> ShutterSound.installCamera(classLoader));
        Install.run("模式状态分离", true, "", () -> ModeStateSplit.installCamera(classLoader));
        Install.run("4K60 滤镜", true, "", () -> VideoFilterHighSpec.installCamera(classLoader));
        Install.run("机型伪装", true, "", () -> ModelSpoof.installCamera(classLoader));
        Install.run("视频水印保护", true, "", () -> VideoWatermarkGuard.installCamera(classLoader));
        Install.run("相机日志镜像", true, "", () -> CameraLogMirror.install(classLoader));

        // --- data layer: needs the payload mounted, not the native library ---
        Install.run("滤镜目录", data, noData, () -> FilterGroupInjector.installCamera(classLoader));
        Install.run("X10 滤镜", data, noData, () -> X10FilterPort.installCamera(classLoader));
        Install.run("哈苏超清滤镜", data, noData, () -> HighPixelEffects.installCamera(classLoader));
        Install.run("大师滤镜补全", data, noData, () -> MasterFilterCompletion.installCamera(classLoader));

        // --- tuning host: serve tables into the stock LMT directory through the path hooks ---
        Install.run("GR 移植", data && lmt && nativeLibrary, !data ? noData : !lmt ? noLmt : noNative,
            () -> GrPort.installCamera(classLoader));
        Install.run("大师色彩", lmt && nativeLibrary, !lmt ? noLmt : noNative,
            () -> MasterColor.installCamera(classLoader));
        Install.run("大师 LUT 槽", lmt && nativeLibrary, !lmt ? noLmt : noNative,
            () -> MasterLutSlots.installCamera(classLoader));
        Install.run("大师对比度", lmt && nativeLibrary, !lmt ? noLmt : noNative,
            () -> MasterContrast.installCamera(classLoader));
        Install.run("物理柔光", nativeLibrary, noNative, () -> SoftLight.installCamera(classLoader));

        // --- config shape: only where the decision tree is plain text (X9 Ultra and peers) ---
        boolean plain = Capabilities.plainDecision();
        String encrypted = "本机决策配置是加密的，无法改写";
        Install.run("25MP 解锁", plain && nativeLibrary, !plain ? encrypted : noNative,
            () -> Unlock25mp.installCamera(classLoader));
        Install.run("200MP 人脸门禁", plain && nativeLibrary, !plain ? encrypted : noNative,
            () -> HighPixel200mFace.installCamera(classLoader));

        // --- mode dependent: the mode has to exist in this device's mode_type_list ---
        boolean retroMode = Capabilities.retro() && Capabilities.hasMode("retro_mode");
        Install.run("POP 复古相机", retroMode && nativeLibrary,
            !Capabilities.retro() ? "本机没有 setting_Retro basictone" : "本机 mode_type_list 没有 retro_mode",
            () -> PopPort.installCamera(classLoader, CAMERA.equals(param.processName)));
        Install.run("无缝模糊保护", retroMode, "POP 未启用，无需保护",
            () -> SeamlessBlurGuard.installCamera(classLoader));
        Install.run("移轴拍摄", Capabilities.hasMode("tilt_shift_mode"),
            "本机 mode_type_list 没有 tilt_shift_mode",
            () -> TiltShiftCapture.installCamera(classLoader));
        boolean xpan = Capabilities.hasMode("xpan_mode");
        Install.run("XPAN 实况", xpan, "本机 mode_type_list 没有 xpan_mode",
            () -> XpanLivePhoto.installCamera(classLoader));
        Install.run("XPAN 取景净化", xpan, "本机 mode_type_list 没有 xpan_mode",
            () -> XpanViewfinderClean.installCamera(classLoader));
        Install.run("XPAN 水印隔离", xpan, "本机 mode_type_list 没有 xpan_mode",
            () -> XpanWatermarkIsolation.installCamera(classLoader));

        // --- MediaTek tuning json: natural mode has nothing to write on a Qualcomm device ---
        Install.run("自然色彩", Capabilities.tuningJson() && nativeLibrary,
            "本机没有 conf_tuning_params.json（非天玑平台）",
            () -> NaturalMode.installCamera(classLoader));

        Log.i("Install", Install.summary());
    }

    /**
     * Loads the native half. LSPosed also loads it from assets/native_init before these hooks run;
     * this call makes the Java side fail loudly if the library is missing instead of silently
     * skipping every feature that needs it.
     */
    private static boolean loadNative() {
        try {
            System.loadLibrary("jiege_native");
            return true;
        } catch (Throwable error) {
            Log.e("Install", "native library unavailable", error);
            return false;
        }
    }
}
