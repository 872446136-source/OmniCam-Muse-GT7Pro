# 无算法栈版（nostack）

完整版（`test/full-port`）在真我 GT7 Pro 上出现取景器全黑、进相机卡死。这一版把**所有依赖
Find X9 Ultra 独有算法栈的功能**全部拿掉，只留下数据层和纯 Java 钩子，供实机验证。

- 应用版本：`versionCode 410` / `Muse 4.0 GT7Pro nostack`
- 模块 id 仍是 `ricoh_pop_x9u`，可直接覆盖安装原来的完整版测试包
- 一键关掉（不用卸载）：`adb shell su -c "touch /data/adb/omnicam_nostack_off"` 后重启

## 为什么这版不会再黑屏

完整版做了四件会动到成像管线的事，这一版一件都不做：

| 完整版动作 | 风险 | 本版 |
| --- | --- | --- |
| `libjiege_native.so` 在相机进程里 patch `libAlgoInterface` / `libBasicTonePhoto`（含 25021 决策族偏移） | 符号偏移按 X9 Ultra 的 so 写死，GT7 Pro 的 so 版本不同 → 决策链断裂，预览无帧 | **不做**，APK 里没有 `.so`，`assets/native_init` 也删了 |
| bind 上游 `libBasicTonePhotoX9.so` / `X10.so` / `libCombineLut.so` | 换掉本机的成像库 | **不做**，`module/system/` 整个不打包 |
| bind 上游 `camera_unit_config` / `oplus_camera_config` / 决策配置 / ISP 调校 bin / gamma 表 | 配置描述的是 X9 Ultra 的 200MP 主摄 + 三摄组合，GT7 Pro 对不上 | **不做** |
| 往 `/odm/etc/camera/basictone` 塞 `master_color` 引擎数据、`unlock25mp`、`face200m` 配置副本 | 引擎数据版本不匹配 | **不做** |

本版只 bind 两棵**数据树**（都是往本机现有目录里叠加，不替换任何文件）：

```
/odm/etc/camera/meishe_lut    本机原有 + payload/meishe_lut（42 个 LUT）
/odm/etc/camera/basictone     本机原有 + payload/x10_basictone（35 组）+ payload/setting_Retro（2 个）
```

外加一处纯配置改动：`CameraHWConfiguration.config` 的 `[InsensorZoom]` 第 25–40 行 ISO 门限
（`0/0` → `-1/-1`，让专业视频的 2x/6x 无损变焦能过 `DoIzoomStable()`）。脚本会数命中行数，不是
16 行就整段跳过，不会写坏配置。

## 保留的功能

| 分类 | 功能 | 实现 |
| --- | --- | --- |
| 滤镜 | 滤镜目录注入（普通 / 大师两套分开管） | `FilterGroupInjector` + meishe_lut |
| 滤镜 | X10 滤镜（卡片、标签、预览、出片） | `X10FilterPort` + x10_basictone |
| 滤镜 | 哈苏超清里的滤镜与调色板条目 | `HighPixelEffects` |
| 滤镜 | 4K / 60fps 下普通滤镜不再被降级 | `VideoFilterHighSpec` |
| GR | GR 模式、预览 LMT、焦距、比例、快门声 | `GrPort` + `files/ricoh_gr/lmt`（GR 调整 LMT） |
| GR | GR 水印（相机 + 相册）5 套样式 | `GrCameraWatermark` / `GrGalleryWatermark` + JSON/webp 素材 |
| 水印 | 水印系列解锁、批量加水印 / 撤销 | `WatermarkSeriesUnlock` / `BatchWatermark` |
| 水印 | 视频水印保护、机型伪装（EXIF + JPEG） | `VideoWatermarkGuard` / `ModelSpoof` |
| 相册 | 照片详情里的地址跳转地图 | `AddressNavigation` |
| 相机 | 快门声（静音/振动下也响、独立音量） | `ShutterSound` / `CameraSoundForced` |
| 相机 | 快启键指定模式、闪光灯与实况状态按模式分离 | `KeyLaunchMode` / `ModeStateSplit` |
| 安全网 | 模块文件没挂上时自动关掉 GR/POP 并退回普通模式 | `UnmountedGuard` |

## 去掉的功能（X9 Ultra 独有算法栈）

| 功能 | 依赖 |
| --- | --- |
| POP 复古相机 + 无缝模糊保护 | `pop_path_remap.cpp`、`pld_watermark`、`apk_resources` |
| 25MP 解锁 | `unlock25mp.cpp` + 放宽的配置副本 |
| 哈苏超清 200MP 去人脸门禁 | `face200m.cpp` + 决策副本 |
| 大师色彩 / HNCS / Lumix / 胶片 look | `master_color.cpp`、`master_core.cpp`、`hncs_engine.bin`（338 个文件） |
| 自然色彩 | `natural_mode.cpp` + 调音 json |
| 柔光（梦幻 / 强） | `soft_light.cpp`（物理散射点扩散） |
| 大师 LUT 槽 / 对比度 / 颗粒 | `master_lut_slots.cpp` |
| 大师滤镜补全（额外 LUT、霓虹辉光、Portra 光晕） | `MasterFilterCompletion` + 大师 LUT 数据 |
| 移轴拍摄 | `TiltShiftCapture` + 移轴算法 |
| 相册调色板编辑 | `libCombineLut` / `jiege_ipu` jar |
| XPAN 实况 / 取景器净化 / 水印隔离 | X9 Ultra 的 XPAN 模式与决策 |

## 已知取舍

1. **GR 出片的成像参数不完整。** GR 的预览 LUT 由 Java 层接管（`Lut3d.load` 重定向到
   `files/ricoh_gr/lmt`），所以取景和成片都会套 GR 的 LMT；但饱和度 / 对比度 / 清晰度这些参数是
   `gr_basictone.cpp` 写进 APS 请求的，本版没有原生层，这几项不生效。
2. **快门音量自定只生效一半。** `audio_policy_volumes.xml` 是上游按 X9 Ultra 的音频策略改的，
   不 bind（怕影响整机音量曲线），所以「静音/振动下仍响快门声」正常，「独立快门音量档位」按本机
   默认走。
3. **GR 水印字体**从相册已下载的 `files/watermark_master/materia` 复制；相册没下载过字体时，
   GR 水印会退回默认字体。

## 装法

```
# 1. 刷模块（KernelSU / Magisk 均可）
#    OmniCam-Muse-GT7Pro-NoStack-KSU.zip
# 2. 重启
# 3. 确认挂载（应该是 2 行 dir bind ok）
adb shell su -c "cat /data/adb/modules/ricoh_pop_x9u/bind.log"
```

`bind.log` 里出现 `dir bind FAIL` 或 `missing live /odm/etc/camera/meishe_lut` 时，把整份
`/data/adb/modules/ricoh_pop_x9u/` 和 `logcat -b crash -d` 发回来。

## 卸载

模块自带 `uninstall.sh`，卸载时自动清理：`files/jiege`、`files/ricoh_gr`、
`files/mode_limit_watermark`、相册 `files/ricoh_gr_styles`、`mode_data.db`、写入的 7 个相机
设置键、相册 GR 开关，最后卸载 `local.jiege.camera`。日志在 `/data/local/tmp/omnicam-uninstall.log`。

## 想恢复完整版

把这三样放回去，再把 `Profile.NATIVE_STACK` 改成 `true`：

1. `app/src/main/cpp/`（`git checkout test/full-port -- app/src/main/cpp`）
2. `app/src/main/assets/native_init`（内容 `libjiege_native.so`，LF 结尾）
3. `module/system/odm/lib64/*.so` 与 `module/post-mount.sh`
4. `app/build.gradle.kts` 的 `ndkVersion` / `ndk {}` / `externalNativeBuild` 段
