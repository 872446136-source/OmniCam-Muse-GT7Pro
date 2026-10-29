# OmniCam Muse 4.24 GT7Pro (488)

**487 的三件事都生效了。这一版处理剩下的四个问题，三个按日志改到位。**

---

## 0. 先确认 487 修好了什么

| 项 | 487 的状态 | 证据 |
|---|---|---|
| Meishe 渲染器 | ✅ 已替换 | `/odm/lib64/libmsnativefilter.so` = `10957976` 字节，设置页绿 |
| XPAN 进模式栏 | ✅ | `xpan row position_mode_panel rank 13 -> position_headline rank 7` |
| XPAN 能进能拍、更多面板不再卡住 | ✅ | 反馈确认 |
| GR 四款滤镜全部有画面 | ✅ | 反馈确认 |
| POP 取景框有画面 | ✅ | 反馈确认 |

剩下的四个：XPAN 出片不是宽幅、POP 一款滤镜取景黑、POP 点拍照没反应、哈苏水印没选项。

---

## 1. POP 点拍照没反应：相机自己说了原因

487 的日志里，POP 模式下每一次按快门都是这一行：

```
OCAM_RetroCameraMode onCameraShutterButtonClick, blocked during retro filter switching
OCAM_RetroCameraMode onCameraShutterButtonDispatchTouch, blocked during retro filter switching
OCAM_RetroCameraMode isReadyToCapture, blocked during retro filter switching
```

三个快门入口（点击、按下、可拍判定）在 `RetroCameraMode` 里被同一个布尔拦住了。这个布尔在
`onFilterScrolling(int)` 里置位，在 `onFilterSnapSettled()` 里清除 —— 后者是滤镜条吸附动画播完之后
回调的。**移植过来的滤镜条从来不发这个回调**，于是标志位一直挂着，之后每一次按快门都被丢掉。
这和反馈完全对上：有画面、滤镜能切、就是拍不了。

新增 `PopFilterGate`：

| 做法 | 为什么 |
|---|---|
| 不硬编码混淆类名 | 挂 `BaseMode` 的构造函数，相机每建一个模式对象都从这里过；只接受形状对得上的那一个（同时有 `Gf(int)Z` / `Hf(int)V` / `Td(ZZZZ)Z` 三个快门重写，外加 `r6(int)V` / `T0()V` 两个回调，布尔实例字段 1~4 个） |
| 标志位不靠猜 | 第一次 `onFilterSnapSettled` 时把字段值在调用前后各读一遍：由 true 变 false 的是「切换中」，由 false 变 true 的是「已吸附」 |
| 看门狗 | 快门来了、「切换中」还挂着、且已 1200 ms 没有任何滚动或吸附回调 → 把对象放回相机自己写过的「已吸附」状态 |
| 不误伤 | 真的在滑滤镜的那 200 ms 不动它；一次都没见过吸附回调的机器退化为清掉当前为 true 的布尔，并把字段名和值打进日志 |

逃生口：`touch /data/adb/omnicam_popgate_off`

---

## 2. XPAN 拍出来是普通照片：裁切区域和出片尺寸都没读到

487 的成片 `IMG20261029014437.jpg` 是 **3072x4096（4:3）**，而 XPAN 该是 **7872x2912（65:24）**。
相机侧读这两个值的地方找到了，都在 `CameraConfig` 上，而且都读不到：

```
XpanModel.onCreate  → CameraConfig.l(String, int[])
                      com.oplus.xpan.crop.region.main.portrait / .main.landscape
                      com.oplus.xpan.crop.region.wide.portrait / .wide.landscape
XpanMode.T9         → CameraConfig.getSizeConfigValue(String, boolean)
                      com.oplus.xpan.main.picturesize / .wide / .tele
```

本机的 `oplus_camera_config` 是加密的（`01 01` 开头，密钥来自 `libOplusSecurity.so`），这几个键
没有进到 app 的配置表里，于是 `l()` 返回默认的空数组、`getSizeConfigValue()` 返回 `null`，
`XpanModel` 认为没有裁切区，出片就落回普通 4:3。

两个都在 `ModeGates` 里补，**空才填，不是一律覆盖**：

| 键 | 值 | 说明 |
|---|---|---|
| `...crop.region.main.portrait` | `360,64,2912,7872` | 8192x6144 主摄的 65:24 裁切 |
| `...crop.region.main.landscape` | `64,360,7872,2912` | 同上 |
| `...crop.region.wide.portrait` | `360,64,2912,7872` | 同上 |
| `...crop.region.wide.landscape` | `64,360,7872,2912` | 同上 |
| `...xpan.main.picturesize` | `7872x2912` | 出片尺寸 |
| `...xpan.wide.picturesize` | `7872x2912` | 出片尺寸 |
| `...xpan.tele.picturesize` | `7872x2912` | 出片尺寸 |
| `com.oplus.xpan.mode.version` | `3` | 版本 |

这组数和 X8U、和参考 GT7Pro 移植包里那份配置是同一组。固件将来自己答得出来就保留固件的答案，
日志会写 `came from the config`。

---

## 3. POP 只有「正片胶片浓郁」取景黑：484 留下的旧 LUT 副本还在挡路

`pop_path_remap.cpp` 读 `/odm/etc/camera/meishe_lut/<name>` 时，先看
`files/pop_port/meishe_lut/<name>` 在不在，在就用它，不在才落回 `/odm`。484 建过这份副本，
**485 不再建但也没删** —— 从 484 升上来的机器上，这份几个月前的残缺 LUT 会盖住挂载层里那份，
于是「在副本里的那几款」用旧的、「不在的」用层里的。反馈里只有一款黑，正是这个形状。

`service.sh` 现在把 `files/pop_port` 整个删掉（和 484 的 basictone 副本一样处理）。删掉之后
remap 找不到目标，落回 `/odm`，所有 POP LUT 只有一个来源。

---

## 4. 哈苏水印：门禁找到了，是 `feature_is_support_hassel_watermark`

487 的探针已经证明内置分组不是问题：

```
HasselWatermark: built-in hassel group present (8 styles), left alone
```

八个 `hassel_style_*` 本来就在列表里。真正决定「哈苏这一类要不要出现在水印面板里」的是
`WatermarkMasterVM.x`：它按 `type_button_id` 查样式类型表，只有对应的特性开关为 true 才留下那一行，
哈苏那一行的开关是 `feature_is_support_hassel_watermark`。这类开关全部走
`com.oplus.aiunit.vision.y9b` 读，所以这一版在那个类上把这一键答成 true。

只答这一键。同一个方法还会问 `is_oppo_brand` / `is_oneplus_brand` / `is_region_cn` /
`feature_is_support_watermark_master`，这几个**只记日志不改** —— 这是 realme 机器，强改品牌位
影响面远不止水印页。

> 另外说明：相机包里没有任何哈苏水印位图（`hasselblad_watermark_dark` 等只存在于相册的
> `resources.arsc`），所以哈苏水印是**相册侧**的能力，相机里本来就不该有选项。这一版的目标是让
> 相册的水印面板把那一类显示出来。

---

## 5. 没动的东西

485 的数据层与 overlay 语义、`meta-magic_mount-rs` 的用法、487 的渲染器挂载层方案、XPAN 写模式表、
GR 卡死兜底、`UnmountedGuard`、`PresenterGuard`、`retro_camera_case`、快门探针、特性表、profile，
全部原样。

---

## 6. 装完请这样验

1. **POP**：进 POP，切到任意滤镜，等一秒，按快门 → 应该能拍。日志里应出现
   `PopPort: stale filter switch cleared on shutter after ... ms idle`。
2. **XPAN**：进 XPAN 拍一张 → 成片比例应是 **65:24 宽幅**。日志里应出现
   `ModeGates: com.oplus.xpan.main.picturesize was null, answered 7872x2912` 和
   `com.oplus.xpan.crop.region.main.portrait was empty, answered 360,64,2912,7872`。
   如果日志写的是 `came from the config`，说明相机自己读到了，那问题就在别处，请把日志发回来。
3. **POP 正片胶片浓郁**：切到这一款，看取景是否还黑。
4. **哈苏水印**：相册 → 编辑 → 水印，看是否有「哈苏」这一类可选。日志里应出现
   `HasselWatermark: feature_is_support_hassel_watermark answered true`。

`versionCode 488` / `versionName Muse 4.24 GT7Pro`。
