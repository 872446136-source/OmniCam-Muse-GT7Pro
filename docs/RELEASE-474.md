> 真我 GT7 Pro（RMX5010 / ColorOS 17.0.0.111 / 相机 17.006.112 / 相册 17.10.37）适配版，单包无 Lite。**不替换任何系统库、不替换任何 ODM 配置。**

## POP 崩溃：找到了，是画遮罩的时候

473 的日志里终于有了崩溃栈 —— 这是三次改版以来第一次拿到相机进程的 `FATAL EXCEPTION`：

```
FATAL EXCEPTION: PreviewGLThread
Process: com.oplus.camera, PID: 23162
java.lang.NullPointerException: Attempt to invoke virtual method
    'void com.oplus.camera.common.gl.w0.s()' on a null object reference
  at com.oplus.camera.common.gl.o.p0(GLES20Canvas.java:120)
  at tq.b1.dh(CameraPreviewUI.java:626)
  at tq.b1.g1(CameraPreviewUI.java:195)
  at co.l.G0(PreviewRenderer.java:329)
  at co.l.onDrawFrame(PreviewRenderer.java:34)
  at bo.r.r(PreviewEffectProcessor.java:16)
  at com.oplus.camera.common.gl.j0.d(GLThread.java:729)
```

时间对得很死：`17:36:21.289 afterModeChanged, end!` → `17:36:21.309` 崩溃，**20 毫秒**。

从离线索引里把这四个方法还原出来：

- `tq.b1.dh` 的方法体里有这样两条字符串：`shouldKeepGLInverseMaskForRetroUI, wait Retro UI preview mask ready timeout, waitMs: ` 和 `drawInverseMaskOverlay, greenFlashStress, width: …` —— 它就是 `drawInverseMaskOverlay`，**给复古 UI 画的圆角反色遮罩**。GR 和 POP 都叫 retro，但只有 POP 走这条。
- 它最后一句是 `new RectF(...)` → `o.p0(RectF, float, float[], float)`。
- `o.p0` 里绑的程序是 `o.j0`，`j0` 是用 `texture_vertex_shader_inverse_mask` + `draw_inverse_mask_shader` 建出来的那个 `w0`（着色器程序包装）。`w0.s()` 就是 `glUseProgram`。
- **`o.j0` 在这个进程里是 null。**

为什么是 null，离线索引答不了：`o.<init>` 确实给它赋过值，而且是**最后一个**建的着色器程序。要么那条赋值挂在这个机型不满足的条件里，要么程序被释放掉了。但这不影响怎么修 —— 遮罩是装饰，**一帧少画一层遮罩，比整条预览线程死掉强得多**。

### 474 怎么修

`GlGuard`，两个钩子，都从 `Application.onCreate` 起（不能在 `handleLoadPackage` 里碰相机类，那是 470 黑屏的原因）：

1. `GLES20Canvas.p0(RectF, float, float[], float)` —— 用 `invokeOriginalMethod` 调原方法，**任何 Throwable 都吞掉**，然后 `setResult(null)`。着色器在就照画，不在就跳过这一层，帧继续画完。
2. `PreviewEffectProcessor.r(gl)` —— GL 线程每帧的入口，同样包一层。别处再冒出空着色器，也只丢一层，不丢整条线程。

两条都不逐帧打日志：前 5 次 + 之后每 200 次一条。逐帧打日志本身就会把相机拖死。

**「重进相机一直在 POP 黑屏、切不了模式」也是这个原因**：GL 线程死了，surface 再也不画；UI 在等一个不存在的预览，模式栏自然没反应。线程不再死，这一串症状就一起没了。

## POP 的资源，模块从来没放过

日志里这行出现了 **10 次**：

```
PopPort: POP PLD resources missing:
    /data/user/0/com.oplus.camera/files/pop_port/pld_watermark/pld/pld.json
```

`PopResources` 一进来就检查这个文件，**不在就直接 return，后面整套都装不上**（拍立得水印路径、复古 UI 动画、引导图）。

原因很直接：`service.sh` 里根本没有 `pop_port` 这一段。上游 X8U 的 `service.sh` 有，这个移植版当时没把 payload 带过来。而且 CI 拉上游数据层时，`for DIR in …` 那个列表里也**没有 `pld_watermark` 和 `apk_resources`** —— 所以就算脚本有这段，包里也没有东西可放。

474 两处都补：

- CI 从上游多拉 `pld_watermark`（拍立得水印，2.3 MB）和 `apk_resources`（复古 UI 动画 23 个 Lottie json + 4 张引导图，9.7 MB），并且加了硬检查：上游这两个目录不在，**直接让构建失败**，不许再出一个「声称修了 POP 但包里没资源」的版本。
- `service.sh` 新增 `pop_port` 段落，stage 到 `files/pop_port/{pld_watermark,apk_resources,setting_Retro}`，chown/chmod 就够（这里不 restorecon，理由和别处一样：会把应用自己的 SELinux category 冲掉）。
- `meishe_lut` **没有**重复复制 —— `post-fs-data.sh` 已经把整棵树 merge 进 `/odm`，`pop_path_remap.cpp` 的 pop_port 分支只是个 fallback，永远不会命中，白占 5 MB。

## GR 快门：这次能定位到是哪一半

你说的「能切 GR 了，但点拍照没反应」，日志是这样：

```
17:35:35.843  Camera Mode Change Start, next mode: gr
17:35:35.949  afterModeChanged, start! isChangeModeFail: false
17:35:43.656  onShutterButtonClick, ShutterButton.id: 2131297925
17:35:45.834  onShutterButtonClick, ShutterButton.id: 2131297925
17:35:48.062  onShutterButtonClick, ShutterButton.id: 2131297925
17:36:05.822  onShutterButtonClick, ShutterButton.id: 2131297925
```

四次点击都进了监听器 `aq.k0$g.i()`。这个方法的每个提前返回分支**都会先打一行日志**（`third app capture is pending.` / `moreTab is showing….` / `thumbnail view is waiting for animation end.` / `ModePickerView follow finger can't response`），一行都没有 —— 所以**不是被 UI 的守卫拦下的**。

它的结尾是 `aq.u0.run()` → 构造 `CameraShutterButtonEventMessage` → `do.a.d(message, boolean)`（事件总线）。

于是只有两种可能，从外面看一模一样：

- 事件**没发出去** —— 延迟单击的 runnable 被取消了，或者当前模式没人监听这个事件；
- 事件**发了，拍摄被拒** —— 拒绝发生在拍摄链路更深处，那里的日志我一条都没镜像。

474 加两样东西把这两种情况分开：

1. `ShutterDispatch` —— 在 `do.a.d` 上挂一个钩子，只对类名里带 `ShutterButton` 的消息打一行 `shutter event dispatched`。一次点击一行。
2. 日志镜像从 8 个 tag 扩到 22 个，把拍摄链路整段接上：

```
CaptureProcessor  CameraCore  OneCameraImpl  CaptureParam  OutCaptureManager
CaptureFail  CommonCapMode  CaptureDefer  ModeManager  MainShutterButton
RetroCameraUIContainer  CameraUIManager  CaptureRequest  BaseMode
```

上限 4000 行（这些类有些路径是逐帧打的，不封顶反而会拖慢相机）。超过就停，并报一行 `capture mirror capped`。

下一份日志里，**`shutter event dispatched` 后面有没有 `CaptureProcessor` 的行**，就是答案。

## 哈苏水印：还是那句话，别抱开关的期望

473 的日志确认开关被问了、被回答成 true 了（8 次）：

```
ModeGates: com.oplus.camera.support.custom.hasselblad.watermark answered true
```

但水印那一栏是**按另一套数据搭的**，不是这个开关。真我没有哈苏联名，这个机型的相机 APK 里很可能**根本没有哈苏水印这一项**。光回答开关造不出一个不存在的条目 —— 要做得往水印列表里注入条目，那是独立的一件事，我另开一版做，不和 POP / GR 混在一起。

## 装完请这样做

1. 打开相机，先等 10 秒（模式探针要读表）
2. **切一次 POP**，等 15 秒 —— 这次应该**不再闪退、不再黑屏**，能看到取景画面（可能没有拍立得边框，那就是遮罩被跳过了，正常）
3. 在 POP 里试着切回别的模式，确认能切
4. 切到 **GR**，点一次快门，等 3 秒
5. 导出日志

日志里这次应该出现：

```
GlGuard: inverse-mask overlay guarded (p0)
GlGuard: preview frame guarded (r)
ShutterDispatch: shutter dispatch hooked
CameraLog: capture mirror ready
PopPort: (不再出现 PLD resources missing)
GlGuard: inverse-mask overlay skipped (1) java.lang.NullPointerException …
ShutterDispatch: shutter event dispatched
CameraLog OCAM_CaptureProcessor …
```

- 有 `inverse-mask overlay skipped` 但相机不崩了 → 修对了，遮罩就是画不出来，后面再单独补。
- 有 `shutter event dispatched` 但**没有** `OCAM_CaptureProcessor` → 事件发了、拍摄在更深处被拒，我按那条链路继续查。
- **连** `shutter event dispatched` 都没有 → 事件没发出去，问题在 `aq.u0` 那个延迟 runnable 或事件总线的监听注册上。

## 回退

```bash
adb shell su -c "touch /data/adb/omnicam_glguard_off"       # 关 GL 保护（会恢复 473 的闪退）
adb shell su -c "touch /data/adb/omnicam_shutterprobe_off"  # 关快门探针
adb shell su -c "touch /data/adb/omnicam_logall_off"        # 关拍摄链路日志镜像
adb shell su -c "touch /data/adb/omnicam_probe_off"         # 关模式探针
adb shell su -c "touch /data/adb/omnicam_placement_off"     # 大师模式回面板
adb shell su -c "touch /data/adb/omnicam_gates_off"         # 关开关层
adb shell su -c "touch /data/adb/omnicam_native_off"        # 关原生层
adb shell su -c "touch /data/adb/omnicam_off"               # 全关
adb shell su -c "rm -f /data/adb/omnicam_off /data/adb/omnicam_native_off /data/adb/omnicam_gates_off /data/adb/omnicam_probe_off /data/adb/omnicam_placement_off /data/adb/omnicam_glguard_off /data/adb/omnicam_shutterprobe_off /data/adb/omnicam_logall_off"
```

改完重启。不替换任何系统文件，关掉之后相机必然是出厂状态。

## 安装

1. KernelSU / APatch 刷 `OmniCam-Muse-GT7Pro-KSU.zip`
2. 装 `OmniCam-Muse-GT7Pro.apk`，LSPosed 里启用并勾选 `com.oplus.camera` 与 `com.coloros.gallery3d`
3. 重启

包体比 473 大约多 12 MB（POP 的水印和复古 UI 资源）。不需要 POP 的话删 `payload/pld_watermark` 和 `payload/apk_resources` 重打包即可，只是 POP 会退回「资源缺失」那个状态。
