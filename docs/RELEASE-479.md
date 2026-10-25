# OmniCam Muse 4.15 GT7Pro (479)

对着 478 的日志做的。这份日志把两个 bug 都钉死在了配置和一条布尔判断上：

- **切到 POP 没卡死** —— 478 的补建真的生效了，日志里五次一行不落；
- **但退出 POP 之后切任何模式都卡死** —— 真因是 `usecase_info` 里缺 `retro_camera_case`；
- **GR 按快门没反应** —— 真因是 `GrMode.Fe()` 的两道闸在这台机器上都是 false。

---

## 1. 日志确认 478 装对了

```
Symbols: camera: 262 resolved, 0 missing
PopPort: ui facade taken from y -> X
PopPort: retro UI missing, building it on gq.t
PopPort: retro UI init start
PopPort: retro UI init done
PopPort: retro show runnable replayed after the view was built
```

两次进 POP（`14:32:34` / `14:32:45`）都是这一串，**视图是就地建出来的，然后 runnable 重放成功**。
「切到 pop 没卡死」属实，478 的补建可以定下来了。

`GlGuard` 也收敛了：

```
14:32:34.814 GlGuard: inverse-mask overlay disabled for this process: the shader program is
              absent, so every frame would throw
```

三次 `skipped` 之后关掉，之后再没有一次。诊断页里 477 的那 5 条错误没了。

---

## 2. 退出 POP 后卡死：`retro_camera_case` 不存在

切到录像之后，日志是这样的：

```
14:32:49.344 setCurrentMode, newMode: commonVideo
14:32:49.384 applyParameters failed, preview state is not started
14:32:49.387 afterModeChanged, end!
            （11 秒，一个字都没有）
14:33:00.281 checkCameraReadyState, camera opening is not ready
14:33:00.287 closeCamera
14:33:00.650 OCAM_OneCameraImpl some thing has error!
java.lang.RuntimeException: getUseCaseValues, retro_camera_case's usecase_info are not configured!
    at com.oplus.ocs.camera.producer.mode.BaseMode.getUseCaseValues(BaseMode.java:2958)
    at com.oplus.ocs.camera.producer.mode.BaseMode.buildTargetSurface(BaseMode.java:1970)
    at com.oplus.ocs.camera.producer.mode.BaseMode.createRequestTag(BaseMode.java:1698)
    at com.oplus.ocs.camera.producer.mode.PhotoMode.createRequestTag(PhotoMode.java:296)
    at com.oplus.ocs.camera.producer.mode.RetroCameraMode.createRequestTag(RetroCameraMode.java:69)
    at com.oplus.ocs.camera.producer.ProducerImpl.startPreview(ProducerImpl.java:511)
    at com.oplus.ocs.camera.CameraDeviceAdapterV2.startPreview(CameraDeviceAdapterV2.java:2)
```

同一个异常 `14:32:42.911` 和 `14:33:00.650` 各一次 —— 就是两次退出 POP。

栈说得很清楚：**新模式的 preview 是用旧模式（`RetroCameraMode`）的 request tag 建的**。
POP 退出时旧模式还没被换掉，`startPreview` 走它的 `createRequestTag`，它要
`retro_camera_case`，而 GT7 的 `usecase_info` 里根本没有这个键：

| 配置 | `retro_camera_case` | `gr_mode_case` | `xpan_case` |
|---|---|---|---|
| X9U（上游） | 有 | 有 | 有 |
| GT7 出厂 | **没有** | 没有 | 没有 |
| GT7 + 478 profile | **没有** | 没有 | 有（478 加的） |

一次 POP 会话之后，相机单元就再也起不来 preview：`setCurrentMode` 照常执行、`afterModeChanged`
照常打印，**但画面永远是上一帧**，每个模式都这样。这就是"切到其他模式就卡死"。

### 修法

往 profile 的 `usecase_info` 里加 `retro_camera_case`，**内容直接取本机自己的
`sat_photo_case`**：

```json
"retro_camera_case": [
  {"capture_meta": "rear_sat"}, {"preview": "rear_sat"},
  {"raw16_output": "rear_main"}, {"raw16_output": "rear_wide"}, {"raw16_output": "rear_tele"},
  {"raw_output": "rear_main"}, {"raw_output": "rear_wide"}, {"raw_output": "rear_tele"},
  {"capture_yuv": "rear_main"}, {"capture_yuv": "rear_wide"}, {"capture_yuv": "rear_tele"},
  {"preview_in_preview": "rear_sat"},
  {"capture_raw10_dol": "rear_main"}, {"capture_raw10_dol": "rear_tele"},
  {"capture_raw10_fullsize": "rear_main"}, {"capture_raw10_fullsize": "rear_tele"}
]
```

为什么不抄 X9U 那份：X9U 的 `retro_camera_case` 里有 `rear_ultra_tele`，GT7 的 `mode_type_list`
里没有这个 camera type（只有 `rear_wide` / `rear_main` / `rear_tele` / `front_main` / `rear_sat` /
`rear_portrait` / `rear_main_front_main`）。抄过来就是把一个这台机器不认识的流名塞进配置。

POP 开的就是 `rear_sat` 那条流（`cameraType: rear_sat, targetCameraId: 12`），和出厂 sat photo
完全一样，所以直接用 `sat_photo_case`：**里面每个流名这台 HAL 本来就认**，一个都不用猜。

`ConfigProfile` 里 `MANAGED_USECASE` 改成 `MANAGED_USECASES` 数组，`xpan_case` 和
`retro_camera_case` 一起走同一条合并路径 —— 仍然只写 profile 里这几个键，其余全部来自本机。

---

## 3. GR 按快门没反应：`Fe()` 的两道闸

477/478 的日志里，GR 按下去是这样：

```
ShutterDispatch: shutter message built
ShutterDispatch: shutter handled by hm.i
            （之后什么都没有：没有 CaptureProcessor，没有缩略图，没有错误）
```

`hm.i` 就是 `GrMode`（符号表按 `gr.effect.params` 定位到它），它继承 `hm.k0` → `hm.r` → `BaseMode`。
`hm.i.Nj`（`ShutterHandler.gr`）的字节码是两段：

```
|0000: iget-boolean v5, v5, CameraShutterButtonEventMessage;.d:Z
|0003: if-eqz v5, 0017                     ← 不是 down 就跳到 up 分支
|0005: … CaptureProcessor.H.a(true)        ← down：把拍摄器武装起来
|0017: iput-boolean v0, v4, BaseMode;.i:Z
|0019: invoke-virtual {v4}, Lhm/r;.Fe:()Z  ← up：Fe() 决定到底拍不拍
|001f: if-eqz v5, 003b                     ← Fe()==false 就整段跳过
|0021: BaseMode.ij(0) → BaseMode.i = 结果  ← 真正拍照的那一下
```

`Fe()` 返回 false 时，up 分支只 `new fm/q0(this, 1)` 登记一个 `GRCapMode` 回调，**永远不调
`BaseMode.ij(0)`** —— 而 `ij(0)` 第一行就是
`touchDownCapture, onCameraShutterButtonClick`，日志里一次都没出现，正好对上。

`hm.r.Fe()` 是两道闸：

```java
if (!super.Fe()) return false;                 // ① BaseMode.Fe()
Optional o = j.d(activity);                    //   读一个设置项
long v = (Long) com.google.protobuf.a.b(24, o).orElse(-1L);
if (v <= -1) return false;                     // ② 设置项 24 必须 >= 0
if (!F1()) return false;
if (Lc()) return false;                        // BaseMode.Lc() 硬编码 false
return true;
```

闸①展开：

```java
// BaseMode.Fe()
if (!getSupportFunction("func_support_touch_down_capture")) return false;
if (vc()) return false;
if (g1.a == 2) return false;
… Fd() / nd() / Od() 组合 …
```

而 `func_support_touch_down_capture` 在 `hm.r.getSupportFunction` 的 44 个 case 里是第 5 个：

```
|04b7: invoke-virtual {v14}, BaseMode;.s:()Z          ← 前置还是后置
|04bb: iget-object v14, BaseMode;.Q1 → CaptureProcessor
|04bf: invoke-virtual CaptureProcessor.H() → cb/e
|04c3: invoke-static {v15, v12, v14}, Util.o1:(ZLjava/lang/String;Lcb/e;)Z
|04c7: return v14
```

`Util.o1` 读的是 `com.oplus.support.pre.capture.frames.in.hal`（还有 `CamCmdUtil.a(3)`、
`com.oplus.pre.evlist.not.support.mode`）。**这两道闸都是按机型给的答案**，这台机器给的都是
false，所以 GR 的「按一下快门」只登记回调、从不真拍。

### 修法

对 GR 把这两道闸都答成 true：

1. 已有的 `GrMode.getSupportFunction` 钩子里加一条：`func_support_touch_down_capture` → `true`；
2. 新增 `hookTouchDownCapture`，钩 `Fe()` 本身，**只覆盖 false 的那次**。

要钩哪个 `Fe` 需要小心：`GrMode` 是继承链的叶子（`hm.i`），**它自己没声明 `Fe`**，声明在
上一层 `hm.r` 里。所以不写死混淆名，而是从符号表解析出的 `BaseMode.Fe` 出发，沿 `GrMode`
往上找「同名同签名」的声明，找到谁就钩谁；一层都没覆盖时退回钩 `BaseMode.Fe`，再用
`grMode.isInstance(thisObject)` 把范围限死在 GR。

符号表因此只加了一条：

```json
"BaseMode.touchDownCapture": {
  "kind": "method", "in": "@BaseMode", "params": [], "ret": "boolean",
  "strings": ["func_support_touch_down_capture"]
}
```

`BaseMode` 里引用这个字符串的方法有三个（`Fe` / `Wf` / `zf`），只有 `Fe` 是 `()Z`，所以
`params: []` + `ret: boolean` 就把范围收成一个。离线重放确认：

```
BaseMode.touchDownCapture          com.oplus.camera.module.BaseMode.Fe
```

`GrMode.touchDownCapture`（`overrideOf` 写法）试过，解析 0 命中 —— 原因就是上面那条：叶子类
不声明这个方法。所以改成运行期沿继承链找，符号表里不留这一条。

---

## 4. 还没修的：POP / GR 的 featureList 只有 4 条

478 日志里，POP 和 GR 的装配清单都是这四条：

```
OCAM_FeatureManager updateConfigure, supported featureList:
  [com.oplus.camera.feature.lens_dirty, com.oplus.camera.feature.temperature_notification,
   com.oplus.camera.feature.frame_ratio, com.oplus.camera.feature.dark_detect],
  modeName: retroCamera, targetCameraId: 12, cameraType: rear_sat, modeNameForCameraUnit: retro_camera_mode
```

这四条正好是 `common` 那一段的默认清单 —— 说明 **`retro_camera_mode` 在
`camera_unit_feature_config.protobuf` 里查不到，回落到 `common` 了**。GR 一样
（`modeNameForCameraUnit: gr_mode`，同样四条，没有 `com.oplus.camera.feature.gr_photo`）。

这份 protobuf 的结构已经解出来了（`field1` 是字符串表，`field2` 是「模式 → 特性表」，
所有数字都是字符串表下标）：

| 文件 | 字符串表 | `field2` 里的模式 |
|---|---|---|
| GT7 出厂 | 294 条 | 23 个，**没有** `gr_mode` / `retro_camera_mode` / `xpan_mode` |
| X9U | 319 条 | 23 个，含 `gr_mode` / `retro_camera_mode` / `xpan_mode` |

这是 POP 需要 478 那套「补建视图」、GR 拿不到自己 presenter 的共同根因。479 不动它：
它决定每个模式装哪些特性，改错会影响**所有**模式，得单独一版、单独验。这一版只修用户报的
两个 bug。

---

## 5. 这一版改了什么

| 文件 | 改动 |
|---|---|
| `module/profiles/RMX5010/config/camera_unit_config` | 加 `retro_camera_case`（内容 = 本机 `sat_photo_case`） |
| `app/src/main/assets/profile/RMX5010/camera_unit_config` | 同上（相机进程从 APK 里取） |
| `common/ConfigProfile.java` | `MANAGED_USECASE` → `MANAGED_USECASES`，两个 case 一起合并 |
| `gr/GrModeHooks.java` | `func_support_touch_down_capture` → true；新增 `hookTouchDownCapture` + `overrideTowards` |
| `assets/symbols.json` | 加 `BaseMode.touchDownCapture`（262 → 263 条） |
| `module/module.prop` / `app/build.gradle.kts` | 479 / `Muse 4.15 GT7Pro` |

---

## 6. 这一版要怎么验

装完重启，按这个顺序：

1. **先进 POP，再切到录像** —— 478 卡死的就是这一步。看是否还有
   `retro_camera_case's usecase_info are not configured`，以及切过去之后画面是否真的换了。
   成功的话日志里应该有 `ConfigProfile: live config merged` 和正常的 `startPreview`。
2. **再切到 GR，按快门** —— 日志里应该出现：

   ```
   RicohGrPort: GR touch-down capture hooked on com.oplus.camera.module.<…>.Fe
   RicohGrPort: touch-down capture answered true: stock said false, …
   ```

   然后按压之后应该跟出 `touchDownCapture, onCameraShutterButtonClick` 和 `CaptureProcessor` 行，
   相册里出现照片。
3. 如果 GR 还是不拍，但上面两行都在 —— 那就是 `ij(0)` 之后的拍摄链路（featureList 那件事），
   下一版处理。

---

## 7. 逃生口

| 文件 | 作用 |
|---|---|
| `/data/adb/omnicam_grshutter_off` | 关 GR 快门修正（两道闸都回到出厂答案） |
| `/data/adb/omnicam_liveconfig_off` | profile 固定用 APK 里的副本，不与本机配置合并 |
| `/data/adb/omnicam_poprepair_off` | 只关 POP 视图补建，保留崩溃保护 |
| `/data/adb/omnicam_popguard_off` | 关 POP 崩溃保护 |
| 其余 | 见 `module.prop` 的 description |

全部需要重启相机进程（或重启手机）。
