# OmniCam Muse 4.16 GT7Pro (480)

对着 479 的日志做的。这一版把「POP 和 GR 按快门都没反应」从「猜在哪一步」推进到「知道是哪张表没写」：

- 479 的两个修复都成立：**POP 不再卡死闪退**，**退出 POP 之后切别的模式也不再卡死**，画面能出来；
- 但两个模式**按快门都没反应**，而且 479 里加的那道 `Fe()` 闸根本没人问 —— 说明失败点在更前面；
- 真因是 `camera_unit_feature_config.protobuf` 里**没有这三个模式的条目**，相机单元因此一条拍摄特性都没装配。

---

## 1. 479 的日志把范围缩到一处

两个模式各自的最后一行：

```
16:18:03.995  OCAM_CommonCapMode onCameraShutterButtonClick      ← POP
16:17:57.680  ShutterDispatch: shutter handled by hm.i           ← GR
```

两边都走到了模式自己的快门代码，之后**一条 `CaptureProcessor` 行都没有**。
把「按下」变成「请求」的是 `BaseMode.capture(cb.t)`（就是日志里打
`capture, isSpruceStartCapture but isCapturing` 和 `capture, checkStateBeforeCapture true`
的那个方法，`i8 → h8`），而 477 / 478 / 479 三份日志里**一次都没出现过它**。

顺带两条旁证，都指向同一件事：

```
16:17:57.684  OCAM_FeatureManager updateConfigure, supported featureList:
              [com.oplus.camera.feature.lens_dirty,
               com.oplus.camera.feature.temperature_notification,
               com.oplus.camera.feature.frame_ratio,
               com.oplus.camera.feature.dark_detect], modeName: retroCamera,
              cameraType: rear_sat, modeNameForCameraUnit: retro_camera_mode
16:17:56.707  RicohGrPort: GR watermark style missing, keeping stock result: gr_style_1
```

`retroCamera` 和 `gr` 的 featureList 都只有 `common` 那四条**和拍摄毫无关系的通知**，
水印样式也查不到 —— 这两件事在正常的 `photo` 模式上都不会发生。

## 2. 真因：那张表里没有这三个模式

`/odm/etc/camera/config/camera_unit_feature_config.protobuf` 是「相机单元模式 → 装配哪些特性」的开关表。
它是个 protobuf，三个顶层字段：

| 字段 | 内容 |
| --- | --- |
| 1（repeated string） | 字符串表，树里每一个数字都是它的下标 |
| 2（message） | 重复 `{ 1: 模式名, 2: 特性树 }` |
| 3（message） | 同形状，单元级覆盖（`video_mode` / `photo_mode`） |
| 4（message） | 一个 5 字节的小字段，本版原样保留 |

本机这份有 **23 个模式**，没有 `gr_mode`、`retro_camera_mode`、`xpan_mode`。
查不到就退回 `common`，而本机 `common` 只有：

```
lens_dirty, temperature_notification, frame_ratio, dark_detect
```

于是 POP / GR 的 `CaptureProcessor`、`capture_defer`、`gr_photo`、`retro_camera`、
`capture_params`、`focus_exposure` …… **一个都没装配**。快门事件有人接（那是 `ShutterHandler`
的事），但接完没有人往下做，所以「按了没反应」。

同一件事还解释了另外两个老症状：`GR watermark style missing`（水印样式特性没装配），
以及 POP 的视图从来没人建（478 只能靠补建兜住）。

### 修法

把这三个模式的特效表补进 profile 的 `camera_unit_feature_config.protobuf`。

内容是**同一版相机 APK（7.013.40）在 X9U 上的原表**，逐条按**名字**重映射进 GT7 自己的字符串表：

- 字符串表 294 → 311 条，原有下标一个不动，新名字追加在后面；
- `gr_mode` 64 条、`retro_camera_mode` 232 条、`xpan_mode` 11 条；
- 三个模式引用的 48 个 `com.oplus.*` 名称，在这台机器的相机 APK 里都存在
  （`exposure_time` / `iso` / `ae_af_lock` 三个在 dex 字节里搜不到，但本机自己的
  `professional_mode` 也引用它们，所以是同一类情况，不是新增风险）；
- 原文件 23 个模式**逐条校验过，解码结果与改前完全一致**；末尾那个 5 字节字段原样保留；
- 所有数字都是字符串表下标，重建后最大值 310 < 311，没有越界。

投放方式和 `camera_unit_config` 一样：放进 APK 的 `assets/profile/RMX5010/`，由相机进程自己
拷进 `files/jiege/profile/`，再由 `profile_config.cpp` 做进程内路径重定向。`/odm` 一个字节都不改。

## 3. 快门探针换成真正的那一步

479 加的 `BaseMode.shutterUp` 用 `com.oplus.vibe.mode.support` 定位，离线重放指到了
`fm.m3.Jf`（CommonCapMode 的点击处理），GR 走的是基类那份，**探针根本盖不到**。
这一版删掉它，只留一个探针：

```
BaseMode.capture  →  BaseMode.h8   （按 capture, checkStateBeforeCapture true 定位，全 dex 唯一）
```

按下之后日志会成对出现：

```
ShutterDispatch: capture request entered on com.oplus.camera.module.…
ShutterDispatch: capture request returned true|false
```

- **进了、返回 true** —— 拍了；
- **进了、返回 false** —— 模式自己拒了，下一步查 `h8` 里的闸；
- **压根没进** —— 点击处理在它之前就返回了，那还是这张表的事。

另外 `shutter handled by …` 那行现在带 `down: true|false`，可以直接看出上/下两个消息是不是都到了。

## 4. 这一版改了什么

| 文件 | 改动 |
| --- | --- |
| `module/profiles/RMX5010/config/camera_unit_feature_config.protobuf` | 新增（GT7 原表 + `gr_mode` / `retro_camera_mode` / `xpan_mode`） |
| `app/src/main/assets/profile/RMX5010/camera_unit_feature_config.protobuf` | 同上，投放用 |
| `ConfigProfile.java` | `NAMES` 加这张表；`omnicam_featcfg_off` 撤下已投放的副本 |
| `ShutterDispatch.java` | 删 `shutterUp` / `shutterClick` 探针和那个 GR 兜底调用；`capture` 探针改指 `h8`；`shutter handled by` 带上 `down:` |
| `symbols.json` | `BaseMode.capture` 重新定位到 `h8`；删两条定位不准的探针；263 → 264 |

## 5. 这一版要怎么验

1. 刷完重启，**先进 POP**：日志里 `updateConfigure, supported featureList:` 那一行
   **不应该再是那四条**，应该出现 `com.oplus.camera.feature.retro_camera`、
   `com.oplus.camera.feature.capture_defer` 之类。
2. POP 里按快门：应该出现 `capture request entered on …`，随后相册里有照片。
3. **再进 GR**，同样看 `supported featureList` 和 `capture request entered`。
   GR 的 `GR watermark style missing` 也应该消失。
4. 退出 POP 切别的模式，画面照常（479 已修，别回退）。

## 6. 逃生口

新增：

```
touch /data/adb/omnicam_featcfg_off
```

撤下这张特性表、改回出厂那份，重启生效。**如果刷完相机起不来或者变得很怪，先建这个文件再重启**，
就能把这一版的改动单独摘掉，其余修复不受影响。

原有：`omnicam_grshutter_off`、`omnicam_poprepair_off`、`omnicam_popguard_off`、
`omnicam_shutterprobe_off`、`omnicam_liveconfig_off`、`omnicam_glguard_off`、
`omnicam_logall_off`、`omnicam_probe_off`、`omnicam_placement_off`、`omnicam_gates_off`、
`omnicam_native_off`、`omnicam_off`。均需重启。

## 7. 这版没动的东西

- 哈苏水印：仍然没碰。
- 479 的 `retro_camera_case`、POP 视图补建、GL 收敛、GR 的 `Fe()` 闸，全部原样保留。
- `versionCode 480` / `versionName "Muse 4.16 GT7Pro"`。
