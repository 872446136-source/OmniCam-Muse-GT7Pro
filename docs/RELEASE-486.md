# OmniCam Muse 4.22 GT7Pro (486)

**POP 黑屏和 GR 滤镜黑屏是同一个原因：Meishe 渲染器缺失。** XPAN 从「更多」挪进模式栏，哈苏水印分组补齐。

485 的日志确认数据层已经通了（`dataLayer=true`、`filesMounted=true`、265 个符号、23 项功能启用）。
这一版处理剩下四件互不相干的事。

---

## 1. POP 黑屏 / GR 滤镜黑屏 / 成片纯绿

POP 的四款胶片和 GR 的四款滤镜**不是 BasicTone pass**，是 Meishe 渲染器，住在
`/odm/lib64/libmsnativefilter.so` 里。`libAlgoInterface.so` 把这个绝对路径硬编码在字符串表里，
用 `dlopen` + `dlsym` 取类 —— 取不到就没有任何回退。

所以快门事件其实是正常发出去的（485 日志里 `ShutterDispatch` 99 行、`onCameraShutterButtonClick`
都在），只是没有画面可拍。

本机这个文件 `7282176` 字节 / `2723` 个导出，缺的正好是这几样：

| 符号 | 本机 | 用途 |
|---|---|---|
| `CNvPolaroid` | **0** | POP 的 Polaroid 渲染器 |
| `CNvFilterCCD` | **0** | CCD |
| `CNvPortra400` | **0** | Portra 400 |
| `CNvShapeMask` | **0** | GR 那几款 look 用的遮罩层 |
| `CNvCompoundLutTiled` | **0** | tiled compound-LUT 通路 |
| `CNvInflation` | **0** | — |

还有一条独立证据：本机 GR 的 LUT 是 **`.MS-LUT` version 2**（`gr.posi` v2/N=20、`gr.nega` v2/32、
`gr.bw` v2/32、`gr.hi.bw` v2/33），普通滤镜是 v1/32 —— 只有新的 compound-LUT 渲染器读得懂 v2。
「标准能用、其余四款黑屏」和「POP 全黑」是同一个根。

### 做法

`service.sh` 在 late_start 用 `nsenter` 进 init 命名空间，把模块自带的
`10957976` 字节 / `4097` 导出版 bind 到 `/odm/lib64/libmsnativefilter.so`。四道保险：

- 目标文件已经带着 `CNvPolaroid` 就不动（未来的固件自带就不用换）
- 大小已经等于替换件就跳过（软重启后 bind 还在）
- bind 完再 `grep` 一次确认，结果写进 `bind.log`
- `touch /data/adb/omnicam_msfilter_off` 或 KernelSU 里点本模块的「执行」按钮即可撤回

### 为什么这不算「换系统库」

这是本项目第一次替换系统库，所以把边界写清楚：

- `libAlgoInterface.so`、`libBasicTonePhoto*.so`、每一个 ODM 配置 —— **全部保持固件原样**
- `libmsnativefilter.so` 是 `dlopen`/`dlsym` 拿到的 GPU 滤镜渲染器，缺符号只让**那一个滤镜**失败，
  不会让加载失败
- `libAlgoInterface.so` 现在用到的 **16 个 Meishe 符号，替换件全部仍然导出**（逐个核对过），
  另外多给 `MsRenderHardwareBuffer` / `MsSetFacePoints` / `MsSetStarFilterAngles`
- 本机有而替换件没有的符号：**0 个**

---

## 2. XPAN 在「更多」里，退出时面板不关

485 的 `promoteXpan()` **从头到尾没生效过**。

`com.oplus.feature.move.mode.to.panel` 的值是 `night,0,professional,1,highDefinition,2` ——
里面根本没有 `xpan`，所以它在那个列表里找不到，第一个循环就 `return false`。日志两次都是同一行：

```
ModeProbe: q(28) -> mName: xpan, mPosition: position_mode_panel, mRank: 11
```

486 改成把 `xpan` 追加进 `com.oplus.feature.move.mode.to.headline`（rank 6），**从模式栏直接进**。
「更多」那张面板不再参与路径，退出时自然也不会留在屏幕上。

`promoteXpanInPanel()` 保留为回退路径（万一将来固件把它种进 panel 列表）。

---

## 3. 哈苏水印只显示 HASSELBLAD 标题、下面没有可选的水印

相册大师水印列表的分组内容由
`WatermarkMasterStyle$Companion.getBuiltInHasselStyles(boolean, boolean)` 生成：

```text
list = []
if (masterMode) list += getHasselMasterModeStyles()   // hassel_style_master_mode_1
if (xpan)       list += getHasselXpanStyles()         // hassel_style_xpan_1
for i in 1..count: list += Pair("hassel_style_"      + i, getStyleFileNameById(...))
for i in 1..count: list += Pair("hassel_text_style_" + i, getStyleFileNameById(...))
```

`count` 不是常量，是经 `k6q.a(int, String)` 查出来的。**realme 这版相册答 0**，两条循环什么都不加，
于是 HASSELBLAD 标题画出来、分组是空的 —— 就是「只显示 hasselblad，没有水印可以选择」。

而这个相册自带全套素材（逐个对照过已安装的 APK）：

```text
assets/watermark_master_styles/hassel_style_1.json … _4.json
assets/watermark_master_styles/hassel_text_style_1.json, _2.json
assets/watermark_master_styles/hassel_style_master_mode_1.json
assets/watermark_master_styles/hassel_style_xpan_1.json
```

所以 `HasselWatermark` 只做两件事：

1. 把调用的两个 `boolean` 都置 `true` —— 它们分别门控 master-mode 与 XPAN 两个子集，都不是品牌判断；
2. 列表里**没有任何 `hassel_style_*` 时**，把这七个 id 补回去。

文件名仍由相册自己的 `getStyleFileNameById` 算，**不凭空造任何东西**；列表本来就有时原样放过。
`touch /data/adb/omnicam_hasselwm_off` 可关。

> 如果这一版补完仍然是空的，下一次的日志里会有一行 `HasselWatermark: hassel group filled: N styles added`
> 或者 `the companion knows no hassel style file names` —— 两种情况指向完全不同的下一步。

---

## 4. GR 相册水印素材改成优先读挂载层

485 的日志里有一行被淹了：

```
RicohGrPort: gallery GR style missing
  /data/user/0/com.coloros.gallery3d/files/ricoh_gr_styles/gr_style_4.json
```

那个目录属于相册自己，要等解锁、还要等相册被启动过，`service.sh` 才写得进去；一次没赶上就永远缺着，
而模块会静悄悄改用 APK 自带的 GR 样式。

`mode_limit_watermark` 从 485 起就在挂载层里，开机第一秒可读、不依赖解锁 —— 改成**先读它**，
私有目录留作回退。

---

## 5. 诊断与开关

设置页在「KSU 模块」之后新增一项 **「Meishe 渲染器」**：直接显示当前
`/odm/lib64/libmsnativefilter.so` 是原厂还是模块版本。原厂时给出
「在 KernelSU 里点本模块的执行按钮开启替换，然后重启手机」。

新增开关：

- `omnicam_msfilter_off` —— 撤下 Meishe 渲染器替换（KernelSU 卡片「执行」按钮同一个开关）
- `omnicam_hasselwm_off` —— 不补哈苏水印分组

---

## 6. 没动的东西

485 的数据层与 overlay 语义、`meta-magic_mount-rs` 的用法、GR 卡死兜底、`UnmountedGuard`、
`PresenterGuard`、`retro_camera_case` / POP 视图补建 / GL 收敛、快门探针、特性表、profile，全部原样。

---

`versionCode 486` / `versionName Muse 4.22 GT7Pro`
