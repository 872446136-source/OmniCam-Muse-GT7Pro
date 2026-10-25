# OmniCam Muse 4.17 GT7Pro (481)

对着 480 的日志做的。480 把三个模式的特效表补上之后，**快门没反应的问题变成了闪退**——
用户的原话是「切到 GR 理光和 PoP 模式都直接闪退，但不卡死」。

这一版把闪退解决了，而且过程中发现 480 的日志里其实是**两个完全不同的崩溃**，
不是一个问题的两种表现。

---

## 1. 两个崩溃，两个异常

480 的日志里有 5 次相机进程退出（PID 17249 / 19063 / 20775 / 21628 / 22586），
按模式分是两类，互不相干：

### 1.1 切 POP（`retro_camera_mode`）→ `main` 线程

```
01:23:26.616  updateConfigure, supported featureList: [... retro_camera, super_text, ...],
              modeName: retroCamera, modeNameForCameraUnit: retro_camera_mode
01:23:26.623  FATAL EXCEPTION: main
java.lang.UnsatisfiedLinkError: dlopen failed:
    library "libXDocProcessSDK-jni.qti.so" not found
	at java.lang.System.loadLibrary(System.java:1765)
	at com.common.Util.loadJNILibrary(Util.java:91)
	at com.youtu.ocr.docprocess.DocDetector.<clinit>(DocDetector.java:180)
	at com.oplus.ocs.camera.SuperTextHelper.<init>(SuperTextHelper.java:7)
	at com.oplus.camera.feature.supertext.e.<init>(SuperTextEngine.java:21)
	at com.oplus.camera.feature.supertext.SuperTextManager.<init>(SuperTextManager.java:34)
	at ak.h.k2(SuperTextPresenter.java:92)
	at lc.p.R(BaseFeaturePresenter.java:29)
	at com.oplus.camera.g7.accept(R8$$SyntheticClass:289)
	at hb.t0.e(FeatureManager.java:48)
	at fm.k4.q0(ModeManager.java:499)
	at im.j.d(ModeSwitcher.java:201)
```

**7 毫秒**就死了。`retro_camera_mode` 那张表里有 `com.oplus.camera.feature.super_text`，
FeatureManager 老老实实把它装配了，`SuperTextPresenter` 构造时去 `System.loadLibrary`
那个 OCR 库——本机根本没有这个 `.so`。

### 1.2 切 GR（`gr_mode`）→ `CameraUnitControl` 线程

```
01:23:37.489  updateConfigure, supported featureList: [... hdr_all_route, ...],
              modeName: gr, modeNameForCameraUnit: gr_mode
01:23:37.659  FATAL EXCEPTION: CameraUnitControl
java.lang.NullPointerException: Attempt to invoke interface method
    'boolean java.util.List.contains(java.lang.Object)' on a null object reference
	at od.b.D2(HdrAllRoutePresenter.java:17)
	at od.b.Y(HdrAllRoutePresenter.java:15)
	at hb.k0.accept(R8$$SyntheticClass:94)
	at java.util.stream.ForEachOps$ForEachOp$OfRef.accept
	at java.util.concurrent.ConcurrentHashMap$ValueSpliterator.forEachRemaining
	at cb.l0.run(R8$$SyntheticClass:241)
```

**170 毫秒**后死，而且是另一条线程、另一个异常。`hdr_all_route` 的 presenter 里
有一个 `List` 字段在本机是 null，它直接 `.contains()`。

### 1.3 共同点

两条路都死在同一件事上：**某个 presenter 构造/初始化抛异常，而
`FeatureManager` 那个 `forEach` 外面没有任何人接**。一次装配失败 = 进程退出。
所以在 480 上连快门都还没机会按。

## 2. 为什么这两个特性本机不能用

`camera_unit_feature_config.protobuf` 里 GT7 自己那 23 个模式，一共用到 **74 个
`com.oplus.*` 特性类**。逐个核对，480 从 X9U 搬过来的表里有这么几个是本机**任何模式
都不装配**的：

| 特性 | GT7 原表用到吗 | 480 搬进来之后的后果 |
| --- | --- | --- |
| `hdr_all_route` | 否（74 个里没有） | GR / POP 都崩：`HdrAllRoutePresenter` NPE |
| `super_text` | 只在 `super_text_mode` 里，而那个模式从未上架 | POP 崩：`libXDocProcessSDK-jni.qti.so` 不存在 |
| `scale.focus` | 否 | 未观察到崩溃，但同样没人验证过 |
| `video_live_photo` | 否 | 同上 |

`super_text` 这一条尤其能说明问题：它在本机表里**确实存在**（`super_text_mode` 引用它），
但那个模式从来没被下发过，所以从来没人发现它一构造就 `UnsatisfiedLinkError`。
480 整表照搬 X9U，等于把这些「本机有代码、但从未被使用、一用就炸」的特性全放了进来。

## 3. 481 的做法

### 3.1 重建那张表：只留本机验证过的

不再整表照搬。新的 `build.py` 先算出「本机 23 个模式 + 2 个单元级覆盖引用到的全部
`com.oplus.*`」，这是**这台机器已经证明能构造**的集合，然后：

- 三个新增模式里，凡是**不在这个集合里**的特性条目，一律丢弃；
- 例外：`gr_photo`、`retro_camera`、`xpan` 三个显式保留——它们就是这三个模式存在的理由；
- `super_text` **单独写死剔除**，哪怕它在集合里（因为它在的是那个从未上架的模式）；
- `xpan_mode` **整条不加**。它是已上架模式，风险最大，等 GR / POP 稳下来再单独处理。

结果：

```
gr_mode            entries  64 ->  61, dropped camera.feature.hdr_all_route, camera.feature.scale.focus
retro_camera_mode  entries 232 -> 224, dropped camera.feature.hdr_all_route,
                                             camera.feature.super_text,
                                             camera.feature.video_live_photo
strings 294 -> 307, modes 23 -> 25, bytes 40091 -> 49621
```

剩下的特性都是本机别的模式正在用的：

- `gr_mode` 17 个类：`assist_view`、`capture_defer`、`capture_params`、`exposure_time`、
  `face_detect`、`filter`、`focus_exposure`、`frame_ratio`、`gr_photo`、`iso`、
  `temperature_notification`、`ten_bit`、`time_shutter`、`zoom` + 3 个 preview 项；
- `retro_camera_mode` 39 个类：`retro_camera`、`capture_defer`、`ai_scene`、`beauty`、
  `high_resolution`、`live_photo`、`macro`、`qr_code_scanner`、`super_text_two` …… 

注意 `super_text_two` 留着：本机 `photo_mode` 正在用它，能构造，不是崩溃那一个。

校验：

- 原文件 23 个模式的解码结果**逐条与改前完全一致**；
- 末尾那个 5 字节字段原样保留；
- 所有数字都是字符串表下标，重建后最大值 **306 < 307**，没有越界；
- 输出可复现（同一份输入跑两次，md5 都是 `18ca1a7dda9a5832ecff162f6b225d3a`）。

### 3.2 新增 `PresenterGuard`

表修好了，但「一个 presenter 抛异常就能带走整个进程」这个结构性问题是**相机的**，
不是表的。以后再加特性，只要有一个构造失败，还是同样的死法。

所以这一版加了一道兜底：hook `lc.p.R` —— `BaseFeaturePresenter` 的 stage 分派器，
`(int stage, Bundle, boolean)`，就是一串 `if (stage == 30000) C1(); else if (30001) N1(b); …
else O1(z);`，POP 崩溃栈里那个 `k2(Z)` 就是它的最后一个分支。

用 `invokeOriginalMethod` 在外面包一层 `try/catch`：

```java
XposedBridge.hookMethod(stage, new XC_MethodHook() {
    @Override protected void beforeHookedMethod(MethodHookParam param) {
        try {
            XposedBridge.invokeOriginalMethod(stage, param.thisObject, param.args);
        } catch (Throwable error) {
            Log.i(TAG, "presenter " + param.thisObject.getClass().getName()
                + " threw on stage " + param.args[0]
                + ", swallowed so the mode switch can finish", error);
        }
        param.setResult(null);
    }
});
```

- 正常返回时**行为完全不变**（跑的就是原方法）；
- 只有抛异常时被吞掉，那个 presenter 丢掉自己的 init，模式切换继续走完；
- 日志会写清**哪个类、哪个 stage**，下一次定位不用再猜。

这也是为什么 481 之后最坏的情况是「某个特性不工作」，而不是「相机整个退出」。

### 3.3 逃生口

新增：

```
touch /data/adb/omnicam_presenterguard_off
```

关掉这道保护，恢复「谁抛异常谁带走进程」的原样行为 —— 万一发现某个模式是
「勉强活着但状态全乱」，用这个把它换回崩溃，至少能立刻判断问题在哪。

`omnicam_featcfg_off` 仍然在，撤下这张特性表、改回出厂那份。

## 4. 这一版要怎么验

1. 刷完重启，**先切 POP**：应该不再闪退。日志里
   `updateConfigure, supported featureList:` 那一行应该出现
   `com.oplus.camera.feature.retro_camera`、`capture_defer`、`capture_params`，
   **不应该再有 `super_text`**。
2. POP 里按快门：应该出现 `capture request entered on …`，随后相册里有照片。
3. **再切 GR**：应该不再闪退。`featureList` 里应该有 `gr_photo`，
   **不应该再有 `hdr_all_route` / `scale.focus`**。
4. GR 里按快门：同样看 `capture request entered`。
5. 两个模式互相来回切几次，再切回普通拍照，确认画面正常（479 已修，别回退）。

如果还有闪退，日志里会有这两种新行，直接告诉我们死在哪：

```
PresenterGuard: presenter <类名> threw on stage <n>, swallowed so the mode switch can finish
```

有这一行 = 保护生效了，那个特性废了但相机活着；没有这一行 = 走的不是这条路，看新的栈。

## 5. 改动清单

| 文件 | 改动 |
| --- | --- |
| `module/profiles/RMX5010/config/camera_unit_feature_config.protobuf` | 重建（只留本机验证过的特性；`xpan_mode` 撤掉） |
| `app/src/main/assets/profile/RMX5010/camera_unit_feature_config.protobuf` | 同上，投放用 |
| `hook/guard/PresenterGuard.java` | 新增：`lc.p.R` 加 try/catch，presenter 抛异常不再带走进程 |
| `ModuleEntry.java` | 装 `PresenterGuard` |
| `symbols.json` | 新增 `FeaturePresenter.stage`（`lc.p.R`）；264 → 265 |
| `module.prop` / `build.gradle.kts` | 481 / `Muse 4.17 GT7Pro` |

## 6. 这版没动的东西

- 哈苏水印：仍然没碰。
- 479 的 `retro_camera_case`、POP 视图补建、GL 收敛、GR 的 `Fe()` 闸，480 的快门探针
  （`BaseMode.capture`），全部原样保留。
- `xpan_mode`：这一版**故意不加**。等 GR / POP 拍出照片之后单独处理。
- 480 那三个「本机也引用」的名字（`exposure_time` / `iso` / `ae_af_lock`）照旧保留。
