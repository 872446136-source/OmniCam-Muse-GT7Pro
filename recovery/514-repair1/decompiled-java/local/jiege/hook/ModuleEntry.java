package local.jiege.hook;

import android.content.pm.ApplicationInfo;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import defpackage.hd;
import defpackage.mo0;
import defpackage.no0;
import defpackage.t2;
import local.jiege.hook.common.Capabilities;
import local.jiege.hook.common.ConfigProfile;
import local.jiege.hook.common.Diagnostics;
import local.jiege.hook.common.Install;
import local.jiege.hook.common.Log;
import local.jiege.hook.common.ModeDataReset;
import local.jiege.hook.common.Symbols;
import local.jiege.hook.filters.PresetSelection;
import local.jiege.hook.gallery.BatchWatermark;
import local.jiege.hook.guard.PresenterGuard;
import local.jiege.hook.guard.UnmountedGuard;
import local.jiege.hook.modes.GlGuard;
import local.jiege.hook.modes.ModeGates;
import local.jiege.hook.modes.ModePlacement;
import local.jiege.hook.modes.ModeProbe;
import local.jiege.hook.pop.PopPort;
import local.jiege.hook.shutter.CameraSoundForced;
import local.jiege.hook.shutter.ShutterDispatch;

/* JADX INFO: compiled from: r8-map-id-54d8ba168e4b5b5c25fa03338d1af60be7e1bb3fb2909943d25d21784fb2e874 */
/* JADX INFO: loaded from: classes.dex */
public final class ModuleEntry implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String CAMERA = "com.oplus.camera";
    private static final String GALLERY = "com.coloros.gallery3d";
    private static final String SYSTEM = "android";

    private void installCamera(ClassLoader classLoader, XC_LoadPackage.LoadPackageParam loadPackageParam) {
        String str;
        String str2;
        ApplicationInfo applicationInfo = loadPackageParam.appInfo;
        Symbols.install("camera", classLoader, applicationInfo.sourceDir, applicationInfo.dataDir);
        ConfigProfile.ensure(classLoader);
        if (CAMERA.equals(loadPackageParam.processName)) {
            ModeGates.installCamera(classLoader);
            ModePlacement.installCamera(classLoader);
            ModeProbe.installCamera(classLoader);
            GlGuard.installCamera(classLoader);
            ShutterDispatch.installCamera(classLoader);
            PresenterGuard.installCamera(classLoader);
            ApplicationInfo applicationInfo2 = loadPackageParam.appInfo;
            if (applicationInfo2 == null || (str2 = applicationInfo2.dataDir) == null) {
                str2 = "/data/user/0/com.oplus.camera";
            }
            ModeDataReset.ensure(str2, applicationInfo2 != null ? applicationInfo2.sourceDir : null);
        }
        boolean zLoadNative = loadNative();
        Capabilities.setNativeLibrary(zLoadNative);
        Log.i("Capabilities", Capabilities.describe());
        boolean zDataLayer = Capabilities.dataLayer();
        boolean zGrLmt = Capabilities.grLmt();
        boolean zLmt = Capabilities.lmt();
        boolean z = false;
        z = false;
        byte b = 1;
        Diagnostics.setFilesMounted(Capabilities.dataRoot() != null);
        Log.i("Capabilities", Capabilities.dataLayerNote());
        if (Capabilities.dataRoot() == null) {
            UnmountedGuard.installCamera(classLoader);
        }
        byte b2 = 24;
        Install.run("快启键模式", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, b2));
        Install.run("快门声", true, PresetSelection.STANDARD_KEY, new hd(classLoader, (byte) 28));
        Install.run("模式状态分离", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, (byte) 9));
        Install.run("4K60 滤镜", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, (byte) 12));
        Install.run("视频美颜规格", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, (byte) 13));
        Install.run("机型伪装", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, (byte) 14));
        Install.run("视频水印保护", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, (byte) 15));
        Install.run("哈苏相机水印资源", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, (byte) 16));
        Install.run("哈苏水印空值保护", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, (byte) 17));
        Install.run("相机日志镜像", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, (byte) 18));
        byte b3 = 25;
        Install.run("滤镜目录", zDataLayer, "模块的 /odm 数据层没挂载：确认模块已启用、magic_mount_rs 已安装，然后重启", new mo0(classLoader, b3));
        Install.run("X10 滤镜", zDataLayer, "模块的 /odm 数据层没挂载：确认模块已启用、magic_mount_rs 已安装，然后重启", new hd(classLoader, (byte) 19));
        Install.run("哈苏超清滤镜", zDataLayer, "模块的 /odm 数据层没挂载：确认模块已启用、magic_mount_rs 已安装，然后重启", new hd(classLoader, (byte) 20));
        Install.run("大师滤镜补全", zDataLayer, "模块的 /odm 数据层没挂载：确认模块已启用、magic_mount_rs 已安装，然后重启", new hd(classLoader, (byte) 21));
        Install.run("快速滤镜面板", true, PresetSelection.STANDARD_KEY, new hd(classLoader, (byte) 22));
        boolean z2 = zGrLmt && zLmt && zLoadNative;
        if (zGrLmt) {
            str = !zLmt ? "本机没有 /odm/etc/camera/basictone/lmt" : "原生层未加载";
        } else {
            str = "/odm/etc/camera/basictone/lmt_gr 不存在，GR 色调表没挂载";
        }
        Install.run("GR 移植", z2, str, new hd(classLoader, (byte) 23));
        if (!Capabilities.hasMode("gr_mode")) {
            Log.i("Install", "GR 模式入口不在 mode_type_list 里（profile 未生效），相机里看不到 GR");
        }
        Install.run("大师色彩", zLmt && zLoadNative, !zLmt ? "本机没有 /odm/etc/camera/basictone/lmt" : "原生层未加载", new hd(classLoader, b2));
        Install.run("大师 LUT 槽", zLmt && zLoadNative, !zLmt ? "本机没有 /odm/etc/camera/basictone/lmt" : "原生层未加载", new hd(classLoader, b3));
        byte b4 = 26;
        Install.run("大师对比度", zLmt && zLoadNative, zLmt ? "原生层未加载" : "本机没有 /odm/etc/camera/basictone/lmt", new hd(classLoader, b4));
        Install.run("物理柔光", zLoadNative, "原生层未加载", new hd(classLoader, (byte) 27));
        Install.run("成片转储", zLoadNative, "原生层未加载", new t2(b4));
        boolean zPlainDecision = Capabilities.plainDecision();
        Install.run("25MP 解锁", zPlainDecision && zLoadNative, !zPlainDecision ? "本机决策树没有 25M / 200M 节点（机型无此算法）" : "原生层未加载", new mo0(classLoader, z ? (byte) 1 : (byte) 0));
        Install.run("200MP 人脸门禁", zPlainDecision && zLoadNative, zPlainDecision ? "原生层未加载" : "本机决策树没有 25M / 200M 节点（机型无此算法）", new mo0(classLoader, b));
        boolean zRetro = Capabilities.retro();
        boolean z3 = zRetro && Capabilities.hasMode("retro_camera_mode");
        Install.run("POP 复古相机", z3 && zLoadNative, !zRetro ? "/odm/etc/camera/basictone/setting_Retro 不存在，retro 数据没挂载" : "本机 mode_type_list 没有 retro_camera_mode（配置 profile 未生效）", new no0(classLoader, loadPackageParam));
        Install.run("无缝模糊保护", z3, "POP 未启用，无需保护", new mo0(classLoader, (byte) 2));
        Install.run("移轴拍摄", Capabilities.hasMode("tilt_shift_mode"), "本机 mode_type_list 没有 tilt_shift_mode（配置 profile 未生效）", new mo0(classLoader, (byte) 3));
        boolean zHasMode = Capabilities.hasMode("xpan_mode");
        Install.run("XPAN 滤镜按钮", zHasMode, "本机没有 xpan_mode", new mo0(classLoader, (byte) 4));
        Install.run("XPAN 实况", zHasMode, "本机 mode_type_list 没有 xpan_mode（配置 profile 未生效）", new mo0(classLoader, (byte) 5));
        Install.run("XPAN 取景净化", zHasMode, "本机 mode_type_list 没有 xpan_mode（配置 profile 未生效）", new mo0(classLoader, (byte) 6));
        Install.run("XPAN 水印隔离", zHasMode, "本机 mode_type_list 没有 xpan_mode（配置 profile 未生效）", new mo0(classLoader, (byte) 7));
        Install.run("XPAN 画幅裁剪", zHasMode, "本机 mode_type_list 没有 xpan_mode（配置 profile 未生效）", new mo0(classLoader, (byte) 10));
        if (Capabilities.tuningJson() && zLoadNative) {
            z = true;
        }
        Install.run("自然色彩", z, "本机没有 conf_tuning_params.json（非天玑平台）", new mo0(classLoader, (byte) 11));
        Log.i("Install", Install.summary());
    }

    private void installGallery(ClassLoader classLoader, XC_LoadPackage.LoadPackageParam loadPackageParam) {
        ApplicationInfo applicationInfo = loadPackageParam.appInfo;
        Symbols.install("gallery", classLoader, applicationInfo.sourceDir, applicationInfo.dataDir);
        boolean zDataLayer = Capabilities.dataLayer();
        Install.run("相册地址跳转", true, PresetSelection.STANDARD_KEY, new hd(classLoader, (byte) 18));
        Install.run("视频编辑实况时长", true, PresetSelection.STANDARD_KEY, new hd(classLoader, (byte) 29));
        Install.run("水印系列解锁", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, (byte) 8));
        Install.run("哈苏水印分组", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, (byte) 19));
        Install.run("相机水印哈苏分区", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, (byte) 20));
        Install.run("批量水印", true, PresetSelection.STANDARD_KEY, new mo0(classLoader, (byte) 21));
        Install.run("GR 相册水印", zDataLayer, "模块的 /odm 数据层没挂载：确认模块已启用、magic_mount_rs 已安装，然后重启", new mo0(classLoader, (byte) 22));
        Install.run("相册调色板", zDataLayer, "模块的 /odm 数据层没挂载：确认模块已启用、magic_mount_rs 已安装，然后重启", new mo0(classLoader, (byte) 23));
        Log.i("Install", Install.summary());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static /* synthetic */ void lambda$installCamera$31(ClassLoader classLoader, XC_LoadPackage.LoadPackageParam loadPackageParam) {
        PopPort.installCamera(classLoader, CAMERA.equals(loadPackageParam.processName));
    }

    private static boolean loadNative() {
        try {
            System.loadLibrary("jiege_native");
            return true;
        } catch (Throwable th) {
            Log.e("Install", "native library unavailable", th);
            return false;
        }
    }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        ClassLoader classLoader = loadPackageParam.classLoader;
        if (SYSTEM.equals(loadPackageParam.packageName)) {
            CameraSoundForced.installSystem(classLoader);
            BatchWatermark.installSystem(classLoader);
        } else if (GALLERY.equals(loadPackageParam.packageName)) {
            installGallery(classLoader, loadPackageParam);
        } else if (CAMERA.equals(loadPackageParam.packageName)) {
            installCamera(classLoader, loadPackageParam);
        }
    }

    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) {
        Symbols.setModulePath(startupParam.modulePath);
    }
}
