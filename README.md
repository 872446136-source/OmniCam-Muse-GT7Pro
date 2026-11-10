# OmniCam Muse（GT7 Pro）

真我 GT7 Pro 的相机增强模块。给 `com.oplus.camera` 补回机型策略裁掉的模式与调色资产，另外加一些拍摄、相册和视频相关的功能。代码移植自 [OmniCam-Muse](https://github.com/benbaobaoshigemi/OmniCam-Muse)，原项目面向 OPPO Find X9 Ultra。

| 项目 | 值 |
|---|---|
| 适配机型 | 真我 GT7 Pro（RMX5010），ColorOS 17 |
| 相机 / 相册 | `com.oplus.camera` 7.013.51 / `com.coloros.gallery3d` 17.10.41 |
| 模块 ID | `ricoh_pop_x9u` |
| 设置应用 | `local.jiege.camera`（随模块一起安装） |
| 当前版本 | Muse 4.33 GT7Pro（versionCode 505） |
| 上游基线 | Muse 4.4.4（`benbaobaoshigemi/OmniCam-Muse`） |

本仓库是**完整版**：只有一个包，没有 Lite 分支，也没有 Lite 功能表。所有功能都编译进同一个 APK，装到手机上时再按本机实际能力逐项决定开不开——挂载缺了、符号对不上、模式表里没有的，那一项就不装。设置应用的「注入状态」页会列出每一项的结果和原因，不用看日志猜。

模块不改系统库，也不改 `/odm` 下的配置文件。数据层（LUT、水印素材、模式库、调色表）以模块文件系统的形式叠在 `/odm` 上，层里有的文件盖住同名系统文件，其余原样透出。

唯一的例外是 `/odm/lib64/libmsnativefilter.so`（Meishe GPU 滤镜渲染器）。POP 的四款胶片和 GR 的四款滤镜跑在它里面，而 GT7 Pro 固件自带的这份没有对应渲染器（`CNvPolaroid`、`CNvFilterCCD`、`CNvPortra400`、`CNvShapeMask` 全为 0），`libAlgoInterface.so` 又是按绝对路径 `dlopen` 的，取不到就黑屏。模块换成带这些渲染器的版本，`libAlgoInterface.so` 当前用到的 16 个符号它在全部仍然导出。`libAlgoInterface.so`、`libBasicTonePhoto*.so`、各 ODM 配置都保持固件原样。

## 安装

需要已 root（KernelSU 或同类的 magic mount 实现）并装好 LSPosed。

1. 从 [Releases](https://github.com/872446136-source/OmniCam-Muse-GT7Pro/releases/tag/full) 下载 `OmniCam-Muse-GT7Pro-KSU.zip`。
2. 在 KernelSU 里刷入模块并重启。模块自带设置应用，刷入时一并安装。
3. 在 LSPosed 中启用 OmniCam Muse，作用域勾选**系统框架、相机、相册**。
4. 强制停止相机，重新打开。

设置入口在桌面的 OmniCam Muse 图标，或 LSPosed 里的模块页。

还原：在 KernelSU 里停用模块并重启，所有挂载随之撤销，相机回到原厂状态。

## 功能

功能说明按上游 4.4.4 整理。GT7 Pro 上能不能用由本机决定，具体状态看设置应用的「注入状态」页。

### 画质

- **拍照模式自然化**：关掉 AI 细节增强和局部对比度增强（LCE），再做一层柔化，三项分别开关。柔化不作用于大师模式和理光模式。
- **25MP 直出**：放宽 25MP 自动切换的照度、ISO、温度和运动条件，配合相机设置里的「超清优先」提高触发率。
- **哈苏超清 200MP**：解除人脸门禁，没有大人脸的画面也走全像素读出，其余条件保持原厂。

### 大师色彩

八个色彩家族，可以按权重混进原厂大师色彩，也可以完全替换。

| 家族 | 内容 |
|---|---|
| OPPO | 原厂大师色彩，可选 Find X9 Ultra、Find X8 Ultra、Find X7 Ultra |
| 基础 | 场景色彩经 Adobe Standard 标定，只套一条传输曲线：标准、直通、Rec.709、Gamma |
| HNCS | 哈苏自然色彩，色彩映射与色调曲线分别可选标准或复制 |
| Lumix | 松下 S9 的 15 种照片风格 |
| 胶片 | Spektrafilm 的 20 种胶片，负片可搭配 8 种相纸或电影拷贝，CMY 密度可调 |
| Canon | EOS R5 的标准、人像、风光、中性、可靠设置、单色 |
| Darktable | Sigmoid、Filmic、AgX 色调映射，黑白点、趾部、肩部、对比度与色相可调 |
| Leica M9 | 徕卡 M9 的色彩矩阵与色调渲染 |

曝光可以按中灰、中间调、高光或白点对齐到原厂大师模式的曝光。

### 大师模式效果栏

- **颗粒**：强度与大小分别调节，所有滤镜可用，数值按滤镜保存。
- **对比度**：-100 到 100，用理光 GR 的对比度曲线，作用在原厂影调之前。

### 大师滤镜

- **扩展滤镜**：旧版经典、徕卡、富士、理光与胶片模拟。
- **列表管理**：长按拖动排序，可显示或隐藏；隐藏的滤镜预设照常可用。
- **跨模式同步**：拍照、人像与哈苏超清共用同一套顺序与显示状态。
- **特殊效果**：霓虹套 CineStill 800T 的光晕与颗粒，和光套 Portra 的高光溢光。
- **导入与备份**：批量导入 Rec.709 `.cube` LUT，配置可备份恢复。
- **快速滤镜面板**：大师卡片或调色盘入口上滑、长按打开原厂滤镜条，滑动时只绘制中心及左右各 4 项。

### 光学模拟

- **物理柔光**：三挡柔光分别换成实体滤镜的光学模拟——弱挡 HDF、中挡 1/4 黑柔、强挡白柔。散射按场景光计算，高光溢散与实体滤镜一致。
- **和光物理胶片**：按柯达实测锐度模拟 Portra 400 的乳剂散射、光晕与边缘效应，作用于成片。

### 模式

- **理光 GR**：移植自 realme GT8，含影调、焦距条、快照对焦、相机与相册水印，补齐 GR 快门声和长曝光提示音，水印可单独关闭，模式位置可拖动。
- **POP 复古**：移植自 Reno16，SDR 与 Ultra HDR 可切换；光线充足时可输出中像素，有人脸时保持原尺寸。
- **X10 系列**：调色盘，以及清透、琥珀两款滤镜。
- **哈苏超清**：补齐滤镜、调色盘与柔光入口。
- **XPAN**：照片可存为实况，播放负片显影动画并带原厂快门声；取景框遮罩与显影动画可分别关闭；XPAN 水印只在 XPAN 模式出现。
- **移轴模式**。

### 视频

- **4K / 60fps 滤镜**：普通视频在 4K、60fps 下保留滤镜，不再降到 1080p30。
- **专业视频 ISZ**：解禁传感器内变焦（主摄 2×、长焦 6×），O-Log2 同样可用。

### 相册

- **重新编辑**：相机拍的滤镜与调色盘可在相册中重新编辑，Muse 滤镜与大师滤镜进入相册滤镜列表；GR 照片可编辑 GR 水印。
- **水印**：哈苏、LUMO、理光水印对所有照片开放，截图等无机型信息的照片自动补上本机名称；Muse 滤镜与导入滤镜显示自己的名称。
- **批量编辑水印**：多选后从「更多」进入，第一张选样式，其余后台套用，可离开相册，通知显示进度；选「无」即批量去除。
- **地址跳转**：照片详情里点地址，在地图应用打开拍摄位置。

### 拍摄习惯

- **快门声**：静音、振动模式下照常播放，可分别开关，音量单独设置。
- **按模式记忆**：闪光灯与实况开关由每个模式各自记住。
- **快启键**：直接进入指定模式，包括大师、GR 这些原厂快启列表里没有的模式。
- **机型伪装**：水印与成片 EXIF 里的机型可换成自定义预设。

### 设置应用

- **注入状态**：逐项检查 LSPosed、挂载、相机与相册注入、符号匹配；全部正常时卡片变绿。
- **即时生效**：改完设置后台结束相机进程，回相机就是新设置。
- **维护**：一键重启相机、相册；持续记录系统日志，复现问题后连同相机日志一起导出。
- **成片转储**：可单独开启 RAW、ccmVec（原厂 WB/CCM 之后、gamma 之前的浮点 RGB）和 HDRTransform 输入输出转储，默认关闭，重启相机进程后生效。

## 和上游的关系

两台机器不是同一配置世代：决策配置格式不同，原生管线类族不同（本机只有 `24019`，没有 `25021`），模式库里没有 GR / POP，大师色彩与 XPAN 的开关是 0。所以不能整包照搬上游。

这个仓库的做法是：

- **代码**跟上游走。上游的功能改动、修 bug 的思路、钩子写法都直接拿过来，只为本机改了必要的地方。
- **数据层**在构建时从上游对应 commit 拉取，只取数据（LUT、basictone、水印素材），不取 `module/system` 里的库，也不取任何 ODM 配置。本地仓库不放这些数据，所以克隆下来只有几百 KB。
- **配置**（`module/profiles/RMX5010`、`assets/profile`）是本机自己的，上游那份不能用。

同步上游的步骤写在 [docs/DEV_NOTES.md](docs/DEV_NOTES.md)。

## 构建

CI：推送到 `main` 或 `test/full` 会触发 `.github/workflows/build-full.yml`，依次拉上游数据层、组装挂载层、编译、签名、打包 KSU 模块，产物传到 `full` 这个 release。数据层对应的上游 commit 在 workflow 的 `UPSTREAM_SHA` 里，重新同步上游时改这一个值。

本地：Windows、JDK 17 以上、Android SDK 37、NDK 29.0.14206865、CMake 3.22.1、Python（含 numpy、pycryptodome）、Git for Windows。`local.properties` 里写 `sdk.dir`（已忽略）。

```powershell
$env:OMNICAM_PYTHON = 'python'
.\build.ps1
```

`build.ps1` 依次跑色彩目录检查、离线测试、脚本语法检查和 `assembleRelease`。仓库里没有 `port.keystore`（已忽略）时只产出未签名 APK；放好密钥并设置 `OMNICAM_STORE_PASSWORD`、`OMNICAM_KEY_ALIAS`、`OMNICAM_KEY_PASSWORD` 后才会签名并打包模块 zip。不同证书签名的 APK 不能直接覆盖安装。

## 已知问题

按 505 的状态分三类：**还没解决的**、**改完但没上真机确认的**、**本机确实没有、只做降级的**。设置应用「注入状态」页会逐项给出结果，每一项的来龙去脉在对应的 `docs/RELEASE-*.md`。

### 还没解决的

**POP / GR 的五个调节滑杆全部无效（光感、去锐化、色温、色调、自然饱和度）**

拖到哪一档画面都一样。日志已经排除了渲染器：`IPU_FilterProcessUnitSDK.processImage` 一共 671 次调用，参数里的 `image_adjust_intensity` **全是 0**，`filter_800t_custom_params` 只带 `lux_index`。也就是说滑杆的值根本没有下发到滤镜链，问题在写入侧而不是渲染侧。

- 光感、去锐化、自然饱和度：下一步要在这条 `processImage` 调用上挂参数打印，抓一次拖滑杆的日志，才能分清是界面没把值写进参数表，还是写进去之后被 SDK 丢了。
- 色温、色调：这两个走 BasicTone 的 CMCW 通道（`[CMCWLut]` 里的 `CoWa` 冷-暖、`CyMa` 青-品红）。本机 `basictone/setting_Retro/SimTool.ini` 里**没有这一段**，`[switch]` 里也没有 `CWCM`，所以这两个值无路可走。**目前没有改**：grid size 与 switch 块必须和 `libBasicTonePhoto.so` 对齐，表找不到时按全 0 处理，而在这条链上全 0 等于黑预览加绿屏。声明 `CMCWLutNum = 9` 之后库会不会自己生成表，没有真机日志无法确认，猜错的代价是理光整个不能用。

**XPAN 画幅停在 2.2:1，还不到 65:24**

取景框是 65:24，成片不是。日志里的决定性几行：`appSurfaceSize: 8000x3636`、`halSurfaceSize: 4096x3072`、`mXpanCropSize: 1368x3696`、`mXpanCropCoordinate: null`。crop region `{64, 360, 7872, 2912}` 的坐标系是 8000×3636 的画布（64+7872+64 = 8000，360+2912+364 = 3636），HAL 只交 4096×3072，坐标全部越界，所以裁不出来。

试过的两条路：

- 改 `CameraHWConfiguration.config` 里的 `8000X3636X4096X3072`，被 HAL 忽略，表面尺寸没变。
- 改成 `8000X3636X8000X3636` 生效了（`get customSurfaceSize: [Pair{8000x3636 8000x3636}]`），成片从 3072×4096 变成 3636×8000，比例 0.427，接近 XPAN 竖画布 0.4545，但仍然不是 65:24。

真正要让 HAL 按 8000×3636 交付，得手工 bind mount 加正确的 SELinux 标签再 `ctl.restart vendor.camera-provider`，模块目前还没接管这一步。`XpanCropRegion`（答 `getXpanLandScapeCropRegion` / `getXpanPortraitCropRegion`）已经编译进去，**没有在真机上验证过**。

**哈苏水印的相机侧缺资源**

`BaseWatermarkManager.getWatermarkJson-aiMaster` 读 `/photo_watermark/<style>`，这台机器的 ODM 里整个目录不存在，返回 null 之后 `isSystemRegionOverScope(null)` 抛 NPE。498 之后不再崩（输入为 null 时直接回答 false，方法体不执行），但缺的样式文件本身补不上，要一台有哈苏样式的机器上的资源。

**POP 快门偶发不出片**

496 定位到相机单元 SDK 的 `ModeInterface.isAllowedToTakePicture` 返回 1，起因是 SDK 手里的预览帧数还是模式打开时的旧值。`PopSdkGate` 在按压窗口内把 app 已接受的帧数写回 SDK；498 让这个帧数改成可读地取（`mMultiFrameCount` → `f` → `getMultiFrameCount()`），读不到时日志会说明。到 505 为止没有新的复现日志，但也没能稳定复现并确认修好。

### 改完还没上真机确认的

| 问题 | 版本 | 做法 |
|---|---|---|
| 哈苏分区只剩 XPAN 三张 | 503 | `watermarkmaster.b.a` 处理哈苏 type 时先过品牌门禁 `is_realme_brand`，本机为 true 直接返回空列表。改成在门禁返回空时，用 ViewModel 里那个 `(Z)List` 方法（7.013 里叫 `L`）重算一遍填进去 |
| 两张水印卡全黑 | 504 | `WatermarkMasterVM.g0` 用 `getOrNull(previewRes, i)` 取预览图，样式 8 条而预览图只有 6 张，越界返回 null 就画成纯黑。改成按预览图数量截断结果 |
| 哈苏那行里有一张卡特别大 | 505 | 哈苏是两个独立列表（普通 / XPAN），读样式表时传的 `show` 是 false，拿到的正是 XPAN 用例的宽幅表，于是一条卡占满整行。改成固定按 `show = true` 读，普通列表有内容后 XPAN 那半让位（放 `/data/adb/omnicam_hassel_xpan_keep` 可保留两边） |
| POP 下滤镜与调节全部无作用 | 504 | `FeaturePresenter.getCameraMode` 的符号名字写错了（`q` → `y`），每帧预览回调里读它都抛 `MissingSymbol`（一次会话 412 次），把 `yf.s.z3()` 打断。符号表修正，两处 hook 改成安装时解析一次、钩子内兜底 |
| 正片胶片 | — | 是否恢复正常还等一次实机确认 |

### 本机确实没有的入口（降级，不算故障）

相机从 7.013.40 升到 7.013.51 之后 R8 重排，有 12 个入口在这份 APK 里**确实不存在**：哈苏超清入口（本机决策树没有 25M / 200M 节点）、快门静音的 3 个定位方法（7.013 已合并成一个 `r(J)V`）、`Shutter.t4` / `Shutter.H4`、`RetroMode.previewDecision`（参数类型 `eb.e` 改名）、`FilterPresenter` 的 5 个长按面板翻页方法。

505 起这些在 `symbols.json` 里标了 `"optional": true`：解析失败仍然记录，日志单独打 `not on this build`，不再计入注入状态的缺失数。对应功能降级：快门声、POP 快门按钮的渐变重绘、物理柔光的成片那一半、哈苏超清的入口按钮、快速滤镜面板。拍照、GR、POP、XPAN、大师色彩、柔光取景、成片转储不受影响。

## 目录

| 路径 | 内容 |
|---|---|
| `app/` | 设置应用与钩子：Java（`hook/*`）、Kotlin（`ui/*`）、C++（`cpp/*`，大师色彩引擎 `master_*`、柔光 `soft_light*`、成片转储 `*_dump*`） |
| `module/` | KSU 模块：脚本、`profiles/` 配置、`lut_extra/` 补的 LUT；`payload/` 由 CI 拉取，不入库 |
| `GT7Pro-适配/` | 本机适配的评估报告、构建说明、符号回放工具 |
| `tools/` | 资源生成与离线测试：`mastercolor/`、`softlight/`、`symbols/`、`mslut.py`、`offline_tests/` |
| `docs/` | `MODULE.md` 各功能原理与涉及文件，`DEV_NOTES.md` 设备操作与排查，`RELEASE-*.md` 逐版记录 |

## 许可证

以 [GPL-3.0](LICENSE) 发布。Lumix、胶片、Canon、Darktable 等色彩引擎基于 [PhotonCamera](https://github.com/bjzhou/PhotonCamera)，胶片模拟来自 [Spektrafilm](https://github.com/andreavolpato/spektrafilm)。第三方来源与许可见 [NOTICE.md](NOTICE.md)。
