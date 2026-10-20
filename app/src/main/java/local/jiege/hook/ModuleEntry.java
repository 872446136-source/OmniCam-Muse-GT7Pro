package local.jiege.hook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import local.jiege.hook.common.CameraLogMirror;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.Profile;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.FilterGroupInjector;
import local.jiege.hook.gr.GrPort;
import local.jiege.hook.gallery.AddressNavigation;
import local.jiege.hook.gallery.BatchWatermark;
import local.jiege.hook.gallery.WatermarkSeriesUnlock;
import local.jiege.hook.guard.UnmountedGuard;
import local.jiege.hook.highpixel.HighPixel200mFace;
import local.jiege.hook.highpixel.HighPixelEffects;
import local.jiege.hook.launch.KeyLaunchMode;
import local.jiege.hook.masterfilter.MasterFilterCompletion;
import local.jiege.hook.mastercolor.MasterColor;
import local.jiege.hook.masterlut.MasterContrast;
import local.jiege.hook.masterlut.MasterLutSlots;
import local.jiege.hook.modestate.ModeStateSplit;
import local.jiege.hook.natural.NaturalMode;
import local.jiege.hook.pop.PopPort;
import local.jiege.hook.seamless.SeamlessBlurGuard;
import local.jiege.hook.shutter.CameraSoundForced;
import local.jiege.hook.shutter.ShutterSound;
import local.jiege.hook.softlight.SoftLight;
import local.jiege.hook.tilt.TiltShiftCapture;
import local.jiege.hook.unlock25mp.Unlock25mp;
import local.jiege.hook.watermark.ModelSpoof;
import local.jiege.hook.watermark.VideoWatermarkGuard;
import local.jiege.hook.watermark.XpanWatermarkIsolation;
import local.jiege.hook.xpan.XpanLivePhoto;
import local.jiege.hook.xpan.XpanViewfinderClean;
import local.jiege.hook.video.VideoFilterHighSpec;
import local.jiege.hook.x10.X10FilterPort;

/**
 * LSPosed entry (assets/xposed_init).
 *
 * This build ships no libjiege_native.so and no assets/native_init: the native half is the Find X9
 * Ultra algorithm layer, which is what this build drops. Everything installed below works through
 * the camera's own Java classes and the module's data files alone.
 *
 * Obfuscated camera and gallery code is reached through Symbols (fingerprints in
 * assets/symbols.json), resolved once per app build before any feature installs.
 *
 * Installed here:
 *   filters       filter catalog injection, normal and master scope managed apart (data layer:
 *                 /odm/etc/camera/meishe_lut and /odm/etc/camera/basictone, bind-mounted)
 *   x10           X10 filter port (same data layer, no library patch)
 *   highpixel     filter list and palette entries inside 哈苏超清
 *   gr            Ricoh GR port: mode, preview LUT, watermark (camera + gallery), shutter sound
 *   gallery       photo-detail address opens a map app; watermark series unlock; batch watermark
 *   shutter       shutter sound in silent / vibrate mode, own shutter volume (camera + system)
 *   modestate     flash and live photo state per mode
 *   video         normal filters at 4K / 60fps
 *   launch        quick launch key opens a chosen mode
 *   guard         camera / gallery without the module's files: features off, GR / POP not reopened
 *
 * Gated off by Profile.NATIVE_STACK (the algorithm stack, not in this build): mastercolour,
 * natural, softlight, masterlut, masterfilter, pop, seamless, 25MP, 200MP face gate, tilt-shift,
 * gallery palette, XPAN.
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
            Symbols.install("gallery", classLoader, param.appInfo.sourceDir, param.appInfo.dataDir);
            AddressNavigation.installGallery(classLoader);
            // The GR styles read the module's staged files; without them they would fall back to
            // stock artwork, so the guard decides first.
            if (!UnmountedGuard.filesMounted()) {
                UnmountedGuard.installGallery();
                return;
            }
            GrPort.installGallery(classLoader);
            WatermarkSeriesUnlock.installGallery(classLoader);
            BatchWatermark.installGallery(classLoader);
        } else if (CAMERA.equals(param.packageName)) {
            // Without the module's /odm data the ported LUTs are missing and the GR / POP UI would
            // be built against stock files, so the guard decides first.
            if (!UnmountedGuard.filesMounted()) {
                Symbols.install("camera", classLoader, param.appInfo.sourceDir, param.appInfo.dataDir);
                UnmountedGuard.installCamera(classLoader);
                return;
            }
            Symbols.install("camera", classLoader, param.appInfo.sourceDir, param.appInfo.dataDir);
            FilterGroupInjector.installCamera(classLoader);
            X10FilterPort.installCamera(classLoader);
            HighPixelEffects.installCamera(classLoader);
            KeyLaunchMode.installCamera(classLoader);
            Log.guard("CameraLog", "camera log mirror", () -> CameraLogMirror.install(classLoader));
            ShutterSound.installCamera(classLoader);
            ModeStateSplit.installCamera(classLoader);
            VideoFilterHighSpec.installCamera(classLoader);
            GrPort.installCamera(classLoader);
            ModelSpoof.installCamera(classLoader);
            VideoWatermarkGuard.installCamera(classLoader);
            if (Profile.NATIVE_STACK) {
                installNativeStack(classLoader, CAMERA.equals(param.processName));
            }
        }
    }

    /**
     * The Find X9 Ultra algorithm stack. Needs libjiege_native.so (assets/native_init), the patched
     * libAlgoInterface / libBasicTonePhoto(X9/X10) / libCombineLut under module/system/odm/lib64, and
     * the upstream camera config, ISP tuned bins and gamma tables. None of that is in this build, so
     * Profile.NATIVE_STACK is false and this never runs.
     */
    private static void installNativeStack(ClassLoader classLoader, boolean mainProcess) {
        // LSPosed calls native_init when this library is loaded; it must be registered before the
        // camera loads libAlgoInterface / libBasicTone*, so it goes before the features.
        System.loadLibrary("jiege_native");
        PopPort.installCamera(classLoader, mainProcess);
        TiltShiftCapture.installCamera(classLoader);
        SeamlessBlurGuard.installCamera(classLoader);
        MasterFilterCompletion.installCamera(classLoader);
        MasterColor.installCamera(classLoader);
        NaturalMode.installCamera(classLoader);
        Unlock25mp.installCamera(classLoader);
        HighPixel200mFace.installCamera(classLoader);
        XpanWatermarkIsolation.installCamera(classLoader);
        XpanLivePhoto.installCamera(classLoader);
        XpanViewfinderClean.installCamera(classLoader);
        SoftLight.installCamera(classLoader);
        MasterLutSlots.installCamera(classLoader);
        MasterContrast.installCamera(classLoader);
    }
}
