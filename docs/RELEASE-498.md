# OmniCam Muse 4.26 GT7Pro (498)

**两个"读不到的返回值"。** 这一版不改资源、不改挂载、不替换任何系统文件，只处理两处「读不到就把
null 或 1 往下用」的地方。

---

## 1. 哈苏水印的相机侧 NPE

493 抓到的堆栈，进 POP 时的一次 `BaseMode.dh`：

```
java.lang.NullPointerException: Attempt to invoke virtual method
  'boolean org.json.JSONObject.has(java.lang.String)' on a null object reference
  at com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkCreator
       .isSystemRegionOverScope(MasterWatermarkCreator.java:6)
  at cl.e.m(WatermarkModel.java:322)
  at com.oplus.camera.module.BaseMode.da(BaseMode.java:101)
  at com.oplus.camera.module.BaseMode.dh(BaseMode.java:52)
```

`isSystemRegionOverScope` 读的是水印样式的"地域白名单"（哈苏商标按地区授权，所以有这道开关），
它第一行就是 `json.has("countryCode")`。而这份 json 在这台机器上根本没有：
`/odm/etc/camera/photo_watermark/` 整个目录不存在，`basictone/mode_limit_watermark/` 里也只有
真我 GT 的 `gr_style_1..5`，一套哈苏样式都没有 —— 于是
`BaseWatermarkManager.getWatermarkJson-aiMaster` 返回 null，被原样交给上面那一行。

修法是新增 `WatermarkNullGuard`：只在**参数为 null** 时，把 `isSystemRegionOverScope` 的结果定成
`false`（"不在地域限制范围内"），方法体不再执行。有 json 的正常路径一个字都不改，也不往 ODM 里
补任何猜出来的样式。

需要说清楚的一点：**这一版只让水印链不断，不负责把样式变出来。** 哈苏的 5 个样式文件在参考机上
也没有 —— PLK110 的 Aura、Opus 和 X8 Ultra 三份包里，`watermark_master_styles/` 都只有
`gr_style_1..5`，跟本机 ODM 里那套一样。要真正有哈苏水印，得从一台原厂就带哈苏样式的机器上取
`photo_watermark/` 那几个 json 和 webp，那是另一件事。

装好后日志里会出现（每次进程一次）：

```
WatermarkNull: 水印样式的 json 是 null（本机 ODM 没有 photo_watermark 目录），
               isSystemRegionOverScope 按「不在地域限制范围内」回答，不再抛 NPE；调用方 <类.方法(文件:行)>
```

括号里那一行是**谁**把 null 交进来的，下一份报告不用再猜。关掉：
`touch /data/adb/omnicam_hasselwm_null_off`。

---

## 2. POP 的帧数改成可读地取

496 的 `PopSdkGate` 要把"app 自己已经接受的帧数"写回相机单元 SDK 的预览决策，那个帧数来自
`framesOf()` —— 而它只读 `cb.e` 上一个混淆字段 `f`，读不到就**静默当 1**。混淆字段每版都可能换名字，
换掉之后这一行不会报错，只会把 1 写进去，然后日志里一片"修过了但还是不出片"。

现在按 `mMultiFrameCount` → `f` → `getMultiFrameCount()` 依次取，并且每个进程打一行说清楚是从哪
读到的：

```
PopPort: POP capture: the app's decision carries 2 frame(s) in mMultiFrameCount, that is the number
         written into the SDK's preview gate
```

三个都没有时也会明说：

```
PopPort: POP capture: the app's decision carries no readable frame count
         (mMultiFrameCount / f / getMultiFrameCount() all absent), the SDK's preview gate is repaired with 1 frame
```

看到这行，说明这一版的 POP 修法在你这台机器上取不到帧数，那就不是"修复不够"，是"读错了字段"。

---

## 3. 参考机型里看过、这一版没有搬的东西

三份参考（PLK110 的移植笔记、X8 Ultra 的 V3 全功能包、Aura 1.1.2 稳定版）里能直接用的大致三类，
逐条说明为什么不在这版动：

- **XPAN 的三处崩溃**（`XpanPhysicalFilterView.k` 的 `List.get(0)`、`XPanPresenter.p` 只判上界、
  `onTouch` 里 `Math.clamp(v, 0, size-1)`）。做法很清楚，但那是 PLK110 的**7.006.125**：类名不混淆、
  字段叫 `e`、方法是 `k`。本机是 7.013.40，同一个类要么被 R8 重排、要么根本不在。符号表是按
  APK 指纹建的，手上没有这台机器的相机 APK，写出来的指纹只会在真机上"匹配不到"，guard 装不上
  却看起来像装上了。要动这一条，给我一份 `logcat -b crash`（或者 7.013.40 的相机 APK）就行。
- **`GrPopDisable` 藏理光入口**：本仓库没有这个类（`guard/` 下只有 `PresenterGuard` 和
  `UnmountedGuard`），理光入口不是被它藏的，这条不用处理。
- **Pro video ISZ 补丁让 HAL 崩**：PLK110 上崩是因为脚本命中 **5 行**就 bind 了。本仓库的
  `post-fs-data.sh` 写死了"必须恰好 16 行"，不是 16 行就整段跳过并在 `bind.log` 里写
  `pro video isz skipped, patched rows=N`。所以本机要么没绑、要么是完整的 16 行情形，这一版不动它。
  如果你刷完在 `bind.log` 里看到的是 `pro video isz ok`，那才需要回头看这条。

---

## 4. 装完怎么验

1. 进相机 → 进 POP → 出来 → 开关一次水印。日志搜 `WatermarkNull`：
   - 一条"已装：… 1 个签名" → guard 装上了；
   - 之后有"json 是 null … 调用方 …" → 保护生效了，这一次不会再 NPE；
   - 一条都搜不到 → `MasterWatermarkCreator` 不在相机进程的类路径里，把那行
     `WatermarkNull: com.oplus.ocs.ipu... 不在相机进程的类路径里` 发我。
2. POP 点一次快门，日志搜 `PopPort: POP capture: the app's decision carries`：
   - 有帧数 + 字段名 → 取到了，接着看后面 `the SDK's capture gate ...`；
   - 是 `no readable frame count` → 字段全换了，把那一行发我。

---

## 5. 没动的东西

算法栈（`libAlgoInterface` / `libBasicTonePhoto*`）、ODM 配置、模块挂载层全部原样；唯一替换的系统
文件还是 `/odm/lib64/libmsnativefilter.so`。这一版只多了一个钩子（`WatermarkNullGuard`）和
`framesOf` 的取数方式，其它模式、其它功能的行为一个字都没改。
