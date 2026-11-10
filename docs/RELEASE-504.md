# Muse 4.32 GT7Pro（versionCode 504）

相机 7.013.51 (60000) / 相册 17.10.41 / realme RMX5010 / Android 17 (SDK 37)。

本轮修三件事：POP 与大师下滤镜和调节全部无作用、哈苏分区出现黑卡、以及符号失配导致的注入告警。

## 一、POP / GR 下每个滤镜、每个调节都没有作用

这一条不是渲染器的问题，是一个 hook 每帧抛异常。

`FilterGroupInjector` 的滤镜条带 hook 挂在 `FilterPresenter.typesFor` 上，它在每次预览回调里跑一次，用来把大师模式隐藏起来的尾部滤镜从条带里去掉。它每次都通过 `Symbols.get("FeaturePresenter.modeName", …)` 现取符号，而这个符号在 7.013.51 上解析不出来：

```
local.jiege.hook.common.Symbols$MissingSymbol:
  symbol FeaturePresenter.modeName unavailable (needs [FeaturePresenter.getCameraMode])
    at local.jiege.hook.filters.FilterGroupInjector$4.afterHookedMethod
    at android.provider.Nier.l5
    at yf.s.w4(FilterPresenter.java)
    at yf.s.z3(FilterPresenter.java)
    at yf.s.q3(FilterPresenter.java)
    at yf.s.R(FilterPresenter.java)
    at eb.i1.e0(FeatureManager.java)
    at za.b1$d.onPreviewCaptureProgressed(OneCameraImpl.java)
```

一次开机日志里这条栈出现 **412 次**，即每帧一次。异常从 after 钩子抛回被钩方法，`FilterPresenter.z3()` 因此拿不到滤镜类型表，这一帧的滤镜就没有被应用——表现就是"滤镜和调节全都没作用"，档位、强度、色彩全都一样。

同一根因还有第二处：`SoftLight` 把 `FilterPresenter.apply` 和 `FilterPresenter.captureRequest` 也挂了 before 钩子读同一个符号，抛出去以后滤镜在"应用"这一步就断了。日志里 8 次。

修法两层：

1. `FeaturePresenter.getCameraMode` 的混淆名从 `q` 改为 `y`。7.013.51 里 `q` 已经不是方法而是字段了；`y()Ljava/lang/String;` 直接返回构造函数传进来的模式名（同一个字段 `q`），`i()` 会在 UI 存在时转调 `ModeUi`，`y()` 不会，所以取 `y()`。符号恢复后 `FeaturePresenter.modeName` 随之解析出来。
2. 两处 hook 都改成**安装时解析一次 `Field`**，且钩子里 `try/catch` 兜底。以后再遇到版本漂移，最多是"大师模式隐藏滤镜不裁剪"或"柔光按非大师处理"，不会再打断预览回调。`FilterGroupInjector` 的失败日志按 key 只打一次，不会每帧刷屏。

## 二、哈苏分区有黑卡

`WatermarkMasterVM.g0(styles, previewRes, type)` 按样式建卡片，缩略图取 `ArraysKt.getOrNull(previewRes, i)`：

- 下标越界返回 null，`item.o`（预览资源 id）保持 0
- 卡片没有缩略图，背景、照片、文字一样都画不出来 → 纯黑一块

本机这一版的样式列表比预览数组长：列表条数由品牌分支决定（`b.a` 里对哈苏 type 的品牌门禁 + `getBuiltInHasselStyles` 的两个布尔参数），预览数组是 APK 里写死的。多出来的尾部必然越界，所以"固定黑两块"。原厂 OPPO 上两者长度本来就相等，所以看不到这个现象。

修法：hook `g0`，结果比预览数组长时按预览数组长度截断，只保留有缩略图的那几张。日志会打一行 `样式 N 项、预览 M 张，截掉…`，下次抓日志就能核对真实数字。

## 三、注入告警

`FeaturePresenter.modeName` 修好后，相机侧未匹配符号从 14 项降到 13 项。剩下的都是**本机真的没有、或代码已经按"缺失即关闭该功能"处理**的：

| 符号 | 状况 |
|---|---|
| `Mode.effectEntry` | 25MP/200MP 入口按钮，本机决策树没有 25M/200M 节点，功能本就关闭 |
| `SoundPlayer.ready` | `silence`/`unsilence` 的判别锚点，本版 `lq.h` 里已没有 `ConditionVariable.block` 调用；受影响的是快门静音窗口，不影响成像 |
| `FilterPresenter.styleMode/styleTarget/storeStyle/syncPalette/clampStyle` | 只被长按滤镜面板（`LegacyFilterPanel`）使用，该面板 `open()` 整体在 try/catch 内，缺符号时只是长按不弹面板 |
| `Shutter.t4` / `Shutter.H4` | `PopShutterGeometry` 里按名字可选解析，缺了只是不重建渐变，不影响快门几何 |
| `RetroMode.previewDecision` | 代码无引用 |
| `BaseMode.startPreEvList` / `sendCaptureRequestCore` | 指纹在本版多命中，`PopCaptureGate` 用 `has()` 守卫，取不到就跳过 |

所以"相机注入异常"这一行还会在，但它现在表示的是上面这些已知项，而不是每帧抛异常。

## 验证情况

- `./gradlew :app:assembleRelease` → BUILD SUCCESSFUL
- 符号表离线重放（`tools/symbols`，对 7.013.51 全量 dex）：`FeaturePresenter.getCameraMode` 与 `FeaturePresenter.modeName` 由 0 命中变为唯一命中
- **未上真机**：改完时手机未连接。滤镜与黑卡两项都要装包实测才算数
