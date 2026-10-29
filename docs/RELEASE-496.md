# OmniCam Muse 4.24 GT7Pro (496)

**POP 快门第三道墙的真正位置。** 495 补的那一步在真机上被调用到了，但真正拦住拍摄的不是
`decisionTimestamp`，而是相机单元 SDK 里的 `checkResult: 1`。这一版把这道闸拆开，同时修掉 495
自己那个「一个字段都没找到」的 bug。

---

## 1. 495 在真机上到底发生了什么

```
PopPort: POP capture: startPreEvList called for the retro mode, the camera answered false;
         storing the decision on the mode so the request still carries it
PopPort: POP capture: decision stored on t1 fields (none found)
PopPort: POP capture: the request is built with the mode's decision (no decision field)
CameraUnit, ProducerImpl: takePicture, not allow take picture, mbApsFinishAddFrame: true,
         isApsCaptureAlgoInitializing: false, checkResult: 1, isCShot: false
OCAM_ApsService: onCaptureFailed, requestId: 96641371
```

`startPreEvList` 确实被调到了，也如实返回了 false（它的闸不绕过）。但两件事都错了：

1. `storeDecision` 一个字段都没匹配到，决策没写进 `X1`；
2. 就算写进去，真正拦住拍摄的也不是 `decisionTimestamp`。

---

## 2. 为什么是 `(none found)`

`X1` / `Y1` / `d1` 这三个 `cb/e` 类型字段都声明在 **`com.oplus.camera.module.BaseMode`** 上
（`field@8685` / `@8688`），而进到 `n8` 的实例是 RetroMode 子类（`hm.t1`）。

`getClass().getDeclaredFields()` **只返回该类自己声明的字段，不向上找父类**，所以一个都匹配不到。
495 的写法从一开始就看不到它们。

修法：沿继承链走，并把 `X1` / `Y1` 排在最前 —— 这两个正是 `Vi` 写、`bh` 读的那两个。

---

## 3. `checkResult: 1` 到底是谁给的

`ProducerImpl.takePicture` 住在 **`/product/framework/com.oplus.camera.unit.sdk.jar`**，不在
`base.apk` 里 —— 这就是为什么之前把 app 侧符号翻遍也解释不了它。

```
004c: invoke-interface {v2, v8, v9, v7}, ModeInterface;.isAllowedToTakePicture:(...)I
004f: move-result v2
0051: if-nez v2, 01cf          // 非 0 → 失败分支，日志里打 checkResult: v2
```

整个 SDK 里能返回 **1** 的只有一处：`BaseMode.isAllowedToTakePicture` 的 `0x0011: return 1`
（`const/4 v2, #int 1` 在 `0x0006`），触发条件是 `checkPreviewResult() != 0`。三条旁证：

- `checkZoomRatio` 是常量 true（`0000: const/4 v0,#1` / `0001: return v0`）；
- `PhotoMode` / `PortraitMode` / `ProfessionalMode` 只可能返回 0、2、3；
- 它那句日志是 `CameraUnitLog.v`（verbose），被 logcat 级别吃掉 —— 所以整份日志里一条内部原因都没有。

`checkPreviewResult`（`BaseMode` `0x2d2fd0`）返回 1 的条件是三条同时成立：

```
previewCameraId == requestTag.mRearFrontCameraId
且 捕获模式名一致（或无 tag 条目）
且 mPreviewResult.get("com.oplus.preview.multi_frame_count") <= 0
```

前两条在 POP 里是成立的（不成立会直接放行），所以差的就是**帧数**：SDK 自己那份 `mPreviewResult`
里帧数是 0。而 app 自己接受的那份决策，在点快门那一刻打出来是 `mMultiFrameCount: 1`。

也就是说：SDK 手里是模式刚打开时 APS 送来的旧值，app 手里是当前值。两边不同步。

---

## 4. 修法：新增 `PopSdkGate`

由 `PopCaptureGate` 在 RetroMode 的 `n8` 执行前 arm 一次（4 秒窗口，一次按压一次判定），然后：

| 位置 | 做什么 |
|---|---|
| SDK `BaseMode` / `PhotoMode.checkPreviewResult` | 执行前把 app 已接受的帧数写进 SDK 的 `mPreviewResult`。**修的是那个陈旧的数据，不是把检查删掉。** |
| SDK `BaseMode.isAllowedToTakePicture` | 返回后先原样记录答案，再决定是否放行 |

放行规则：

- 返回 **0** → 上面的修复已经够了，**什么都不覆盖**；
- 返回 **1** 或 **3** → 记录原因，放行这一次拍摄；
- 返回 **2**（内存 / 预览不匹配，`BaseMode` 已经自己把这次拍摄报成失败）→ **不动**，保持拒绝。
  在已经报过失败之后再拍一次只会把报告搅浑。

日志会把 SDK 看到的三个值（camera id / capture mode / frames）和请求侧的（camera id / camera type /
aps mode）一起打出来，下一份报告不用再猜。

---

## 5. 装完请这样验

进 POP → 点快门 → 看 DCIM。日志里应该出现：

```
PopPort: POP SDK gate installed: preview frame count repaired on 2 mode class(es), capture gate releasable
PopPort: POP capture: the SDK's preview gate sees camera ..., capture mode ..., frames 0,
         and the request is camera id ..., type ..., aps mode ...
PopPort: POP capture: the SDK's preview decision carried 0 frame(s), the 1 the app's own decision
         accepted is written into it ...
PopPort: POP capture: decision stored on t1 fields X1,Y1,d1
PopPort: POP capture: the request is built with the mode's decision X1=<...>
```

判定：

- `onSendCaptureRequestCore, send, decisionTimestamp:` 后面**不再是 -1** → 决策真的进到请求里了；
- `the SDK's capture gate accepted the request ... nothing was overridden` → 只靠修数据就通了，
  没有覆盖任何检查；
- `still answered 1 (...) letting this one capture through` → 修数据还不够，但这次拍摄被放过去了，
  括号里就是剩下的原因；
- 出片 → POP 快门通了。还是不出 → 新日志给出下一条原因，直接发我。

---

## 6. 没动的东西

算法栈（`libAlgoInterface` / `libBasicTonePhoto*`）和 ODM 配置仍然一个字节都不替换；唯一替换的系统
文件还是 `/odm/lib64/libmsnativefilter.so`（Meishe 渲染器）。其它模式的快门路径一个字都没改 ——
`PopSdkGate` 只在 POP 按压后的 4 秒窗口内生效，窗口外一次判断都不做。

## 7. 顺带

- 正片胶片（12 个 LUT）是否恢复，还等你确认一次。
- 哈苏水印的相机侧 NPE 已经定位到根：`BaseWatermarkManager.getWatermarkJson-aiMaster` 去读
  `/photo_watermark/<style>`，这台机器的 ODM 里**整个目录都不存在**，返回 null 之后
  `isSystemRegionOverScope(null)` 抛 NPE。下一版处理。
