# OmniCam Muse 4.11 GT7Pro (475)

这一版把 B6 移植包（`OmniCam-Muse-RMX5010-B6-widescreen`，`Muse 4.4.4-port`）验证过的功能合了进来，
并按这台机器的 sensor 重新校准。**B6 能让哈苏超清、4K live、log 视频、LUT 监看出来，代价是整套
X9 Ultra 的配置**——它的 `oplus_camera_config` 有 616 条、`camera_unit_config` 有 607 行，
连 `com.oplus.camera.mode.data.db.version` 都从 5 降到了 2。这版只拿开关，不拿尺寸。

---

## 一、B6 的三个 bug，各自的成因

### 1. 连续拍照，相册加载不出来

B6 改了这些：

| 键 | GT7 Pro 原值 | B6 |
|---|---|---|
| `com.oplus.high.mfnr.picturesize` | `4000x3008` | `4096x3072` |
| `com.oplus.high.mfnr.tele.picturesize` | `4624x3472` | `4096X3072` |
| `com.oplus.high.mfnr.wide.picturesize` | `4000x3000` | `3264x2448` |
| `com.oplus.sat.wide.support.imagesizes` | `4000x3008…` | `4096x3072…` |
| `com.oplus.sat.ultra.support.imagesizes` | `4000x3000…` | `3264x2448…` |
| `com.oplus.high.livephoto.picturesize` | 不存在 | `4096x3072` |
| `com.oplus.portrait.mode.picture.size.1x` | `4096x3072x2624x1968` | `4096x3072x2880x2160` |

三点能确认这是照抄而不是校准：

1. 长焦那行写的是 `4096X3072`，**大写 X**。同一份文件里其它尺寸都是小写，这个值大概率解析失败。
2. 同一组里 `sat.wide` 被**放大**（4000→4096），`sat.ultra` 被**缩小**（4000→3264）。
   照抄一台机器的尺寸表，不会在同组里出现两个相反方向。
3. `4000x3008` 是这台机器 sensor 的 4in1 输出。`4096x3072` 是 8192x6144 的一半，
   属于 X9 Ultra 的 5000 万主摄。GT7 Pro 的主摄走不到这个 mode。

后果：MFNR 的 buffer 池按错误的尺寸分配。第一张还能靠缩放糊过去，第二张起 JPEG 头写的是
4096x3072、实际数据是 4000x3008，相册解码失败——表现就是「拍完点开是黑的/转圈」。

**这版没有改任何 `picturesize` / `imagesizes` / `picture.size` / `previewsize`。**
`tools/make_camera_config.py` 里有一份 `FORBIDDEN` 正则，任何尺寸类键都会被拒绝写入。

### 2. log 视频和 LUT 监看只能 30 帧

两个原因叠在一起：

**(a) 大师视频挂错了 op mode。** 本机 `camera_unit_config` 把 `master_video_mode` 指向 `80A3`。
在 X8U 和 B6 里，`80A3` 是**电影模式**，`800b` 才是普通录像；`80A3` 在 HAL 侧是 30 fps 的电影
流水线。所以「大师视频」这条路天生到不了 60。

**(b) 60 fps 的开关本身是关的。** `com.oplus.feature.master.video.60fps.support = 0`，
`com.oplus.video.focus.track.30fps.only = 1`——后者更狠：只要开着对焦跟踪（默认开），
整条录像链路就被压到 30，UI 上选 60 会被接受，然后静默按 30 录。

**修法：**

- `master_video_mode: 80A3 → 800b`（和 X8U、B6 一致）
- 新增 `movie_mode: 80A3`，`feature_operation_mode/movie_mode` 走 `80a4` 防抖
- `feature.master.video.60fps.support` 回答 **true**
- `video.focus.track.30fps.only` 回答 **false**

`800b` 和 `video_mode` 共用一个 op mode，这在 OPPO 的配置里是常规做法
（`photo_mode` / `portrait_mode` / `high_definition_mode` / `under_water_mode` 全都是 `8001`）。

### 3. XPAN 卡死

`pq.h.n()` 整个方法体只做一件事：

```java
return CameraConfig.getConfigBooleanValue("com.oplus.xpan.legacy.ui.style");
```

`dq.e.j()` 在给 `xpan` 写面板行时同时读 `feature.xpan.mode.support` 和这个键。
**B6 没有这个键**，于是走新版 UI——就是那个「显影动画」。动画本身能播，播完之后预览
被拆掉，模式切不回去。

**修法：回答 `com.oplus.xpan.legacy.ui.style = true`，走旧版面板，不播显影动画。**

同时**没有**动 B6 改过的 `xpan.main.picturesize`（`8000x3636` 保留）、`xpan.mode.version`
（不新增）、`xpan.all.camera.support`（不新增——加它会让长焦也进 XPAN，而长焦没有对应的
crop region）。

---

## 二、合进来的功能

### 哈苏超清 / 5000 万 / 4K live

关键发现：`com.oplus.camera.hasselblad.super.definition.support` 这一个键**同时**是
「哈苏超清开关」和「highPixel 进模式栏的条件」——`dq.e.i()` 在写 headline 时读它。
所以打开它，页面和入口一起出现，不需要 `ModePlacement` 那种搬移。

| 键 | 值 |
|---|---|
| `com.oplus.camera.hasselblad.super.definition.support` | true |
| `com.oplus.use.hasselblad.style.support` | true |
| `com.oplus.feature.high.pixel.support` | true |
| `com.oplus.feature.highpixel.merge.support` | true |
| `com.oplus.high_pixel.global.ev.support` | true |
| `com.oplus.support.highpixel.gesture_shutter` | true |
| `com.oplus.camera.high.pixel.mode.4k.live.support` | true |
| `com.oplus.camera.highpixel.livephoto.video.max.duration` | 3000 |
| `com.oplus.camera.hasslblad.livephoto.capture.interval` | 1000 |

`camera_unit_config` 里新增 `high_pixel_mode: 8001`，`feature_operation_mode/high_pixel_mode`
= `{aiHighPixel: {on: "8001"}}`。它要的能力表 `high_pixel_mode_featureTable` **本来就在这份
profile 里**，一直没用上。

### 电影模式 / log 页 / LUT 监看

`r9.i` 是这套功能的闸门。它的每个方法都只是 `c(idx, default)`，`idx` 指向
`com.oplus.camera.master.video.feature.list` 的一个槽位：

| 槽 | 方法 | 作用 |
|---|---|---|
| 1 | `k()` | 模式闸门（`CameraEntry.l`） |
| 4 | `m()` | log 会话（`qh.e.buildSession` + `LogVideoPresenter`） |
| 6 | `f()` | log 菜单（`LogLutMenuView`） |
| 7 | `g()` / `j()` | 三态 |
| 8 | `i()` | **LUT 监看** |
| 10 | `l()` | 直方图缓存 |
| 11 | `d()` | **log 页本身**（O-Log2 / SDR / HDR） |

本机是 `1,0,0,0,0,0,0,0,0,0,1,0,0,0`（14 槽，只有 0 和 10 是 1），所以整页是暗的。
这版回答 `1,1,1,1,1,1,1,2,1,1,1,1,0,0`——前十二槽用 X8U 和 B6 一致的值，后两槽保持本机原样
（`r9.i` 会索引到 17，短数组越界读会拿到默认值，不如给足）。

> 为什么不直接抄 B6 的十二项：这台机器的配置是 14 槽，B6/X8U 是 12 槽。直接塞 12 项会让
> 第 12、13 槽消失，而那两槽在这台机器上是给新版功能的。

其他开关：`feature.movie.mode.support`、`feature.movie.mode.V2.support`、
`director.filter.support`、`feature.master.film.filter.label.enable`、
`feature.master.hq.raw.support`、`use.real.master.pipeline`、`master.mode.enable.zsl`、
`master.video.three.state.stabilization`、`log.version.curve = 2`、
`feature.master.mode.version = 2.0`、`feature.movie.mode.video.size = 3840x2160`。

---

## 三、为什么开关走 hook，不走 profile 文件

`oplus_camera_config` 是加密的：`01 01 <2B> <AES-128-ECB> <4B>`，key 从设备自己的
`libOplusSecurity.so[0x7AB:0x7AB+32]` 派生。四个 stock 文件的头尾分别是：

```
oplus_camera_config                0101e506…  …ddc829ff
oplus_camera_aps_config            0101f96b…  …4ed0ddff
oplus_camera_algo_switch_config    01013cf7…  …396886ff
oplus_camera_preview_decision…     0101cdf5…  …a9fca6ff
```

头 4 字节的 `01 01` 是 magic，后 2 字节每个文件不同；尾 4 字节每个文件不同、末字节固定 `ff`，
且已确认**不是** crc32 / adler32 / 简单求和（四个文件都对不上）。

问题就在这里：**明文长度从 61749 变成 68 KB 之后，尾 4 字节该不该重算、按什么重算，不知道。**
赌错的话，相机读到的是一份解析不了的配置——那是黑屏，而且比现在的任何一个 bug 都难查。

所以这版把整张开关表做在 `ModeGates` 里，进程内回答，一个字节都不写盘：

- `getConfigBooleanValue(String)` / `d(String,ZZ)` — 所有开关
- `CameraConfig.l(String, int[])` — `master.video.feature.list` 十四槽
- 其余「收 key、返回 int 或 String」的读取方法 — 按 key 匹配返回
  （`log.version.curve`、`master.mode.version`、`movie.mode.video.size`、
  `highpixel.livephoto.video.max.duration`、`hasslblad.livephoto.capture.interval`、
  `master.captureparam.version`）

`tools/make_camera_config.py` 保留着，是这张表的审计记录：它列出了每一个键、值、以及为什么。
它现在只写到 `out/oplus_camera_config.candidate`，不进模块。

---

## 四、模式表的变化

`camera_unit_config`（`assets/profile/RMX5010/`，由相机进程自己从 APK 写出）：

```
mode_operation_mode.master_video_mode   80A3  →  800b
mode_operation_mode.movie_mode          新增  80A3
mode_operation_mode.high_pixel_mode     新增  8001
feature_operation_mode.movie_mode       新增  {stabilization: {video_stabilization: 80a4}}
feature_operation_mode.high_pixel_mode  新增  {aiHighPixel: {on: 8001}}
mode_type_list.rear_main                加 movie_mode, high_pixel_mode（共 21）
mode_type_list.rear_tele / rear_wide / rear_sat  同上
```

`gr_mode` / `retro_camera_mode` / `xpan_mode` / `tilt_shift_mode` 一个没动。

---

## 五、装完这样验

装好重启，开相机等 10 秒，按顺序做：

1. **模式栏**：应该看到 **哈苏超清（highPixel）** 和 **大师模式（professional）**。
   「更多」面板里应该有 **电影模式（movie）** 和 **XPAN**。
2. **连拍**：普通拍照，连按 5 下快门 → 打开相册，5 张都要能加载。
   **这是这版最重要的一条**——如果还有黑的，说明尺寸类键里还有漏网的。
3. **log 视频**：进大师视频（或电影模式）→ 找 log / LUT 入口 → 选 60 fps → 录 5 秒 →
   相册里看视频详情，帧率要是 60。
4. **XPAN**：进 XPAN，**不应该有显影动画**，直接是取景框。拍一张，能存。

导出日志后，看这几行：

```
ModeGates: flag getter hooked, 24 true, 2 false
ModeGates: master video feature list answered, 14 slots
ModeGates: scalar readers hooked: N
ModeGates: com.oplus.camera.hasselblad.super.definition.support answered true
ModeGates: com.oplus.feature.master.video.60fps.support answered true
ModeGates: com.oplus.video.focus.track.30fps.only answered false
ModeGates: com.oplus.xpan.legacy.ui.style answered true
ModeProbe: row +8000ms movie id=… / highPixel id=… / xpan id=…
```

`ModeProbe` 的整表 dump 会打出所有行。如果 `movie` / `highPixel` 有行但 `position_mode_panel`
之外没有 `position_headline`，说明模式表生效了但界面没画——那是另一层的问题，看
`OCAM_HeadlineHelper` 那几行。

---

## 六、没做的

- **哈苏水印**：真我没有哈苏联名，相机 APK 里没有这个条目。相册侧的
  `WatermarkSeriesUnlock` 早就解锁了哈苏/LUMO/GR，但相机侧的水印列表
  （`files/mode_limit_watermark/<id>/<id>.json`）里没有哈苏样式，要自己造样式 JSON 和 logo。
  单独一版。
- **POP 的 PLD 资源**：474 已经补了 `pop_port` 的 stage，这版没动。
- **GR 快门**：474 加了 `ShutterDispatch` 探针，这版保留。等日志。

---

## 七、逃生口

```bash
# 只关这一版新增的开关层（哈苏超清/4K live/电影/log/60帧/XPAN 全回到出厂状态，相机照常能用）
adb shell su -c "touch /data/adb/omnicam_gates_off"

# 只关 GL 保护（POP 遮罩会退回 473 的闪退）
adb shell su -c "touch /data/adb/omnicam_glguard_off"

# 只关快门探针 / 拍摄链路日志
adb shell su -c "touch /data/adb/omnicam_shutterprobe_off"
adb shell su -c "touch /data/adb/omnicam_logall_off"

# 只关模式探针 / 大师模式搬移 / 原生层
adb shell su -c "touch /data/adb/omnicam_probe_off"
adb shell su -c "touch /data/adb/omnicam_placement_off"
adb shell su -c "touch /data/adb/omnicam_native_off"

# 全关
adb shell su -c "touch /data/adb/omnicam_off"

# 恢复
adb shell su -c "rm -f /data/adb/omnicam_*"
```

全部需要重启相机（或重启设备）。

**回退到 474**：直接刷 474 的包即可。这版没有改任何持久化数据的位置，唯一被重写的是
`mode_data.db`（`ModeDataReset` 的版本号从 474 推到 475，会重删一次），刷回 474 会再删一次。
