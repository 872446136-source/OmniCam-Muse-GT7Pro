# OmniCam Muse 4.24 GT7Pro (495)

**POP 快门的第三道墙。** 494 让快门被允许、请求也真的发出去了（`capture request returned true`），
但请求里带着 `decisionTimestamp: -1`，三毫秒后 APS 直接 `onCaptureFailed`。这一版补上相机在 POP 里
跳过的那一步。

---

## 1. 494 在真机上拆掉的第二道墙

`adb install` 494 后进 POP 点快门，日志：

```
PopPort: POP capture: the fallback-stable wait is skipped in POP, it never settles on this phone
         and it also disables the shutter
ShutterDispatch: capture request returned true          ← 488/491/492/493 都是 false
```

快门不再被拒，`CF_WAIT_FALLBACK` 和 `CF_SHUTTER_BUTTON_IS_NOT_CLICKABLE` 都消失了。但 DCIM 里没有新文件。

---

## 2. 第三道墙：请求里没有决策时间戳

紧接着：

```
OCAM_t1_BaseMode: onSendCaptureRequestCore, send, decisionTimestamp: -1, fastShot: false,
                  mbIsTouchDownCapture: false, mbDispatchCaptureType: 0
CameraUnit, ProducerImpl: takePicture, not allow take picture, mbApsFinishAddFrame: true,
                  isApsCaptureAlgoInitializing: false, checkResult: 1, isCShot: false
OCAM_ApsService: onCaptureFailed, requestId: 217445086
```

同一台机器切到「照片」模式拍一张，全部正常，日志里的对应行是：

```
OCAM_p1_BaseMode: startPreEvlist, finalMergeArray: [...], decisionResult.mTimestamp: 3876846970333
OplusCameraManagerGlobal: sendOplusExtCamCmd, cmd: CMD_PRE_EVLIST
OCAM_p1_BaseMode: onSendCaptureRequestCore, send, decisionTimestamp: 3876846970333,
                  mbIsTouchDownCapture: true
```

差别只有一个：**照片模式有 `startPreEvlist`，POP 一次都没有**（整份日志里
`startPreEvlist` 只出现 1 次，就是照片那次）。

`BaseMode.bh`（`onSendCaptureRequestCore`，`0x1a5150`）把时间戳这样算出来：

```
003a: iget-object v5, v4, BaseMode;.X1:Lcb/e;      // g.t = this.X1
0040: if-eqz v5, 0047
0044: iget-wide v1, v5, Lcb/e;.M0:J                // ts = X1.M0
0047: const-wide/16 v1, -1                         // 否则 ts = -1
```

`X1` 只有一个地方写：`BaseMode.Vi(cb.e)`，也就是 `startPreEvList`（`0x19cd90`）。它由
`onPreviewDecisionResult` 在 `needCapture` 置位后调用。POP 里这条链一次都没走通，`X1` 一直是 null，
于是请求带着 -1 出去，APS 没有可匹配的决策帧，拒绝。

---

## 3. 修法

`assets/symbols.json` 加两个符号。这两个方法的日志串都很多，逐个列全不现实，所以给符号加了一种
**包含匹配**（`"contains"`，DexKit 的 `usingStrings`；原来的 `"strings"` 是整串相等）：

```json
"BaseMode.startPreEvList": {
  "kind": "method", "in": "@BaseMode", "ret": "boolean",
  "contains": ["startPreEvlist, finalMergeArray: "]
},
"BaseMode.sendCaptureRequestCore": {
  "kind": "method", "in": "@BaseMode", "ret": "long",
  "contains": ["onSendCaptureRequestCore, send, decisionTimestamp: "]
}
```

两个串在全 APK 里各只出现 1 次，都在 `classes7.dex`。

`PopCaptureGate` 在 `n8` 执行前（`n8` 整场只在点快门时被调用一次，日志可证）把相机刚刚交给它的
决策对象送进 `startPreEvList`：

```java
Object answer = start.invoke(param.thisObject, decision);
if (!Boolean.TRUE.equals(answer)) storeDecision(param.thisObject, decision);
```

**`Vi` 不被强制**：它自己的每一道闸（pre-EV-list 支持、夜景/连拍上限、重复决策）全部照常判断，
返回值原样记进日志。只有它拒绝时，才由我们把决策写进模式上的决策字段 —— 也就是 `Vi` 本来会做的那
一件写入。

---

## 4. 装完请这样验

进 POP → 点快门 → 看 DCIM。日志里应该出现：

```
PopPort: POP capture: startPreEvList called for the retro mode, the camera answered ...
PopPort: POP capture: the request is built with the mode's decision X1=<...>
```

- `onSendCaptureRequestCore, send, decisionTimestamp:` 后面**不再是 -1** → 第三道墙过了。
- 出片 → POP 快门通了。
- 还是不出 → 新日志会给出下一条原因，直接发我。

---

## 5. 没动的东西

算法栈（`libAlgoInterface` / `libBasicTonePhoto*`）和 ODM 配置仍然一个字节都不替换；唯一替换的
系统文件还是 `/odm/lib64/libmsnativefilter.so`（Meishe 渲染器）。其它模式的快门路径一个字都没改。

## 6. 顺带

- 正片胶片（12 个 LUT）是否恢复，还等你确认一次。
- 哈苏水印的相机侧 NPE 已经定位到根：`BaseWatermarkManager.getWatermarkJson-aiMaster` 去读
  `/photo_watermark/<style>`，这台机器的 ODM 里**整个目录都不存在**，返回 null 之后
  `isSystemRegionOverScope(null)` 抛 NPE。下一版处理。
