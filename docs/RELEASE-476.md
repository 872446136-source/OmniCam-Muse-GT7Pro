# OmniCam Muse 4.12 GT7Pro (476)

适配系统更新后的 **相机 7.013.40**（`versionCode 60000`）与 **相册 17.10.41**
（`versionCode 170100410`）。475 是给 7.006.112 / 17.10.37 做的；这次两个包都换了混淆，
所有写死的类名和一半以上的符号指纹都失效，不重解析就会出现「模块装着但功能静默不装」的状态。

---

## 1. 全量符号重解析

`assets/symbols.json` 的每一个指纹都对着新包重新验过一遍。验证方式是离线重放：
把 34 个相机 dex、18 个相册 dex 全部 `dexdump -d`，解析成类/方法/字段/字符串/调用索引，
再用与运行时同一套语义（`tools/symbols/spec_eval.py`）跑一遍。

| | 符号数 | 新包命中 |
|---|---|---|
| 相机 7.013.40 | 257 | 257 |
| 相册 17.10.41 | 30 | 30 |

失效的 13 个相机符号（在 475 上装到新包会变成「解析不到 → 功能自动关闭」）：

| 符号 | 475 | 476 | 影响 |
|---|---|---|---|
| `Lut3dData.paletteMode` | `Lut3dDataManager.g` | `ka.l0.j0` | 哈苏超清的取景器调色板 |
| `SoftLight.typesForMode` | `fg.e.r` / `fg.e.t`（3 参） | `ag.e.s` / `ag.e.u`（2 参） | 物理柔光类型表 |
| `SoundPlayer.silence` | `cr.k.s` | `lq.h.t` | 快门声 |
| `SoundPlayer.unsilence` | `cr.k.r` | `lq.h.s` | 快门声 |
| `Zoom.fadeLabels` | `ZoomSeekBar.f4` | `ZoomSeekBar.O3` | GR 变焦标签淡出 |
| `ZoomBase.fadeLabels` | `wa.q.f4` | `ua.p.O3` | 同上 |
| `CaptureParams.refreshEffectEntry` | `kf.n.H2` | `ff.p.S3` | GR 效果按钮刷新 |
| `FeaturePresenter.getCameraMode` | `x.getCameraMode` | `lc.p.q` | 模式名判定（5 个功能依赖） |
| `Shutter.b3/c3/d3/e3/g3/h3/l3` | 同名 | 整体后移一位（`c3/d3/e3/f3/h3/i3/m3`） | POP 快门几何 |
| `Shutter.t4/H4/I4` | 同名 | `u4/I4/J4` | 同上 |
| `Shutter.s3/j1/k1` | 同名 | `t3/k1/l1` | 同上 |

几处值得记下来的：

- **`FeaturePresenter` 少了 `getCameraMode` 这个名字。** 新包只留三个无参 `String` 方法：`k`、
  `l1`、`q`。`q()` 的字节码只有一条 `iget-object` 读 `mModeName`，就是原来的 `getCameraMode`；
  `k()` 还要经过 listener（`getCurrentModeName`），`l1()` 调用 `T3`（`getNextModeName`）。
  `getPreCameraMode` 在新包里没有了。
- **`SoundPlayer` 的 ringer 等待被拆成了独立方法。** 475 靠「有没有调用
  `ConditionVariable.block(J)`」区分 silence / unsilence；新包把那段搬到 `lq.h.b()`
  （日志标签 `checkRingerModeReady`），`unsilence` 去调它，`silence` 不调。所以符号改成
  先定义 `SoundPlayer.ready`，再用 `invokes` / `notInvokes` 指向它——不依赖任何混淆名。
- **`Lut3dDataManager` 还在，但调色板闸门搬走了。** 新类里已经没有
  `boolean(String)` 方法。现在符号不再写类名，只按「静态、收一个 String、返回 boolean、
  带 `palette.support`/`common`/`portrait`/`night` 四个字符串」去找，在新包命中
  `ka.l0.j0`，在旧包仍然命中 `Lut3dDataManager.g`。
- **相册 30 个符号全部还在。** 第一次跑出来 14 个失效，原因是我的索引漏了
  `classes.dex` 和 `classes10.dex` 两个文件（`editingvvm` 包看起来从 3293 个类掉到 145 个，
  差点误判成「水印编辑器被挪进插件」）。补齐后全中。

`PopShutterGeometry` 也跟着改了：它原来拿符号 ID 的后缀当字段名去 `getObjectField(button, "b3")`，
字段一改名就改到了别的字段上。现在直接用符号解析出来的 `Field` 对象，名字只在
`symbols.json` 里出现一次。

---

## 2. 类名重定位（写死在 Java 里的那几个）

| 用途 | 475 | 476 |
|---|---|---|
| `ModeDataSQLiteOpenHelper` | `dq.e` | `np.e` |
| `HeadlineHelper` | `pq.h` | `zp.k` |
| `ModeInfo` | `ja.a` | `ia.a` |
| 事件总线 | `do.a` | `pn.a` |
| 事件基类 | `do.c` | `pn.c` |
| `PreviewRenderer`（GL 线程入口） | `co.l` | `nn.o` |
| `GLES20Canvas` | `com.oplus.camera.common.gl.o` | `com.oplus.camera.common.gl.p` |
| `CameraConfig` | `com.oplus.camera.configure.CameraConfig` | **不变** |

`CameraConfig` 是最关键的一个，也是唯一没动的：新包 55 个方法、54 个 static，名字和签名
逐条比对完全一致，`ModeGates` 的四个 hook（`getConfigBooleanValue`、`d(String,ZZ)`、
`l(String,int[])`、按形状扫的标量读取器）全部原样可挂。

**`do.a` 换掉是个坑。** 新包里 `do.a` 还在，但已经变成动画/尺寸相关的类
（`onAnimationCancel`、`a(I)`、`b(Size)`），跟事件总线毫无关系。只按名字挂会钩到一个
完全无关的类，探针永远不打印、也不报错。新总线 `pn.a` 的 `d(pn.c, boolean)` 形状没变。

三个类都改成了候选列表（新名在前、旧名在后），装回旧相机包时也能工作。

---

## 3. POP 崩溃：475 挂错了方法

473 的堆栈：

```
FATAL EXCEPTION: PreviewGLThread
java.lang.NullPointerException: Attempt to invoke virtual method
    'void com.oplus.camera.common.gl.w0.s()' on a null object reference
  at com.oplus.camera.common.gl.o.p0(GLES20Canvas.java:120)
  at tq.b1.dh(CameraPreviewUI.java:626)
  at tq.b1.g1(CameraPreviewUI.java:195)
  at co.l.G0(PreviewRenderer.java:329)
  at co.l.onDrawFrame(PreviewRenderer.java:34)
```

475 的保护挂在 `bo.r.r(gl)` 上，理由是「这是 GL 线程每帧调用的入口」。**它不是。**
`bo.r` 是渲染器的持有对象（`F0()` 返回 `co.l`），真正被 GL 线程调用的、异常穿出去把线程打死的是
`co.l.onDrawFrame()` —— 无参方法，里面加锁后调 `G0()`，`G0()` 再进
`CameraPreviewUI.g1 → dh → GLES20Canvas.p0`。所以 475 那一层 try/catch 从来没接住过任何东西，
POP 该崩还是崩。

476 改成：

1. **`nn.o.onDrawFrame()`**（476 的 `co.l`）。`onDrawFrame` 是框架 override，名字不被混淆，
   只按类名找 + 无参 void 校验。
2. **反色遮罩**按形状找：`(RectF, float, float[], float) → void` 在 `GLES20Canvas` 上唯一。
   475 写死的是方法名 `p0`，新包叫 `r0`，写死就会「找不到 → 静默跳过」。
3. **画布类按候选名找**：`com.oplus.camera.common.gl.*` 这一包的短名每次构建都会重排
   （新包里 `gl.o` 变成了一个 `Supplier`，`gl.p` 才是画布），所以列
   `p / o / q / n / r` 依次试，命中形状即用。

日志里会打 `preview frame guarded (nn.o.onDrawFrame)` 和
`inverse-mask overlay guarded (com.oplus.camera.common.gl.p.r0)`，装没装上看得见。

---

## 4. 配置 profile 改成合并

475 的 `camera_unit_config` 是**整份**从某个系统版本抄出来、离线打好补丁再打进 APK 的。
这在固件不动的时候没问题；系统一 OTA，这份文件就变成「旧快照 + 本模块的增项」，
而它是以**替换**的方式被相机读到的——更新改过的尺寸、表、模式会被悄悄回退成旧值。

这次系统和相机包一起动了，所以 476 改成运行时合并：

1. 经 `/proc/self/root/odm/etc/camera/config/camera_unit_config` 读设备**当前**那份。
   原生重定向是按字面前缀 `/odm/etc/camera/config/` 匹配的，换个写法就能绕过它读到真文件。
2. 以它为准，只覆盖本模块负责的键：
   - `mode_operation_mode` 里五个新增模式（`xpan_mode` / `gr_mode` / `retro_camera_mode` /
     `tilt_shift_mode` / `movie_mode` / `high_pixel_mode`）
   - `mode_operation_mode.master_video_mode` → `800b`
   - `feature_operation_mode` 里 `movie_mode` / `high_pixel_mode`
   - `usecase_info.xpan_case`
   - `mode_type_list` 各摄像头组：按模块的顺序排，但只保留设备仍提供的模式，
     设备新增的追加在后面
3. 其它一切（尺寸、tuning、feature graph、设备自己列的模式）**保持设备的原样**。
4. 读不到、结构不对、JSON 报错 → 原样使用 APK 里那份，行为与 475 完全一致。

这一步没有设备无法验证，所以留了逃生口：`touch /data/adb/omnicam_liveconfig_off` 让 profile
固定用 APK 副本。日志里会打 `live config merged: N -> M chars` 或
`live config unreadable, the shipped copy is served as is`，一看就知道走了哪条路。

---

## 5. 已知未修

- **相机侧哈苏水印**：真我没有哈苏联名，相机 APK 里没有任何哈苏水印样式条目。现有机制是
  `files/mode_limit_watermark/<id>/<id>.json` + 图片（GR 就是这么做的，`service.sh` 会铺
  `gr_style_1..5`），要做哈苏版需要一份样式 JSON 和 logo，没有参考样本，这次没动。
  开关层（`support.custom.hasselblad.watermark`）仍然是回答 true 的。
- **GR 快门点了没反应**：476 只保证探针能装上（`pn.a.d` 已修）。`ShutterDispatch` 会把
  「按下 → 事件发出」这一段打出来：后面跟 `CaptureProcessor` 行 = 事件到了、拍摄被拒；
  什么都没有 = 事件没发出去。需要一份带这个标记的日志才能定下一步。
  这版同时把 GR 自己的日志标签（`GRCapMode` / `GRViewManager` / `GREffectMenu` /
  `RetroCameraMode`）加进了镜像 —— `GRCapMode` 是 GR 快门处理器的持有类写的，
  **按一次快门后日志里没有这一行，就说明 GR 的模式类根本没被激活**（模式库里的
  capture mode 指错了），和「处理器跑了、拍摄在后面被拒」是两种完全不同的修法。
  473 分不开这两者，就是因为这几个标签没镜像。

---

## 6. 其它

- `ModeDataReset.VERSION` → `476`，刷入后相机首次启动会重新播种一次 `mode_data.db`
  （用户自己的模式排序和 headline/panel 划分会被重置，这是每次加模式的代价）。
- `versionCode 476` / `versionName "Muse 4.12 GT7Pro"`。
- 逃生口新增 `/data/adb/omnicam_liveconfig_off`，其余不变。
