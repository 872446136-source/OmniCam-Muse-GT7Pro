# OmniCam Muse 4.24 GT7Pro (494)

**POP 快门在真机上拆到第二道墙了。** 493 装上后第一道（`BaseMode.n8` 的相机 id 静默比较）当场
通过，快门往下走了一层，露出真正的拦路者：`BaseMode.jc(ZZ)Z` 在等一个永远等不到的「回退稳定」，
而且这段等待还会把快门自己锁死。这一版在 POP 里跳过它。

---

## 1. 493 在真机上的结果

把 493 的 APK 直接 `adb install` 到手机上（修复在 APK 里，不用等刷模块），重启相机：

```
PopPort: POP filter gate installed on hm.t1 via symbols, switching flag s3, settled flag t3, 2 boolean(s)
PopPort: POP capture gate installed: com.oplus.camera.module.BaseMode.n8(cb.e, boolean, boolean), camera id field b
```

进 POP → 点快门，**488/491/492 一直跟着的那句消失了**：

```
PopPort: POP capture: the preview decision was made for camera -1 and the mode is on 0,
         aligned so the shutter can fire; decision mCameraId: 0, ... mbAllowToCapture: true ...
```

`isReadyToCapture, decisionResult is null or mCameraId not matched` **不再出现** —— 第一道墙倒了。
决策里真的是 `-1`，模式报的是 `0`，对齐后 `n8` 的四个判定全部通过。

---

## 2. 第二道墙：等一个永远不会稳定的回退

紧接着日志换成：

```
E/OCAM_t1_BaseMode: isFallBackStable, wait fallback be stable to capture,
                    isFallbackMatched: true, isMasterPipelineMatched: false
E/OCAM_AutoLogHelper: tryCatchCaptureFailedLog, reason: CF_WAIT_FALLBACK, reasonIndex: 26
ShutterDispatch: capture request returned false
```

`BaseMode.jc(boolean, boolean)` 的字节码（设备 APK `classes7.dex`，`0x1a0a44`）：

```
0005: if-eqz v0, 009a     → kc() 为假 → 直接 return true
000b: if-nez v0, 009a     → nd() 为真 → 直接 return true
000d..：                 → 否则进入等待分支
0068: const-string "CF_WAIT_FALLBACK"
007b: iput-boolean P = true / Q = v7 / R = false / X = true
0085: invoke-virtual Uh(Z) / U8().v6(ZZ)        // 快门置为等待态
0099: return false
```

`kc()` 读三个 `CameraConfig` 布尔值（`com.oplus.not.check.fallback.support`、
`com.oplus.capture.surface.decision.support`、`com.oplus.capture.wait.fallback.stable.support`），
这台机器的固件要求**必须等**；`nd()` 去 UI 层问一个 POP 永远不会置位的标志。两边一夹，
`kc() == true && nd() == false` → 每次都进等待分支。

**更糟的是它不止是拒绝**：等待分支把 `P/Q/R/X` 写成等待态、把快门置灰。所以第二次点击更早被拒：

```
E/OCAM_AutoLogHelper: tryCatchCaptureFailedLog, reason: CF_SHUTTER_BUTTON_IS_NOT_CLICKABLE, reasonIndex: 24
```

这就是用户报的「点了没反应」—— 点第一次是等回退，点第二次连按钮都点不动。

---

## 3. 修法

`assets/symbols.json` 新增一个符号，指纹用它自己打的日志串（全 APK 唯一，只在 `classes7.dex`）：

```json
"BaseMode.fallbackStable": {
  "kind": "method", "in": "@BaseMode",
  "params": ["boolean", "boolean"], "ret": "boolean",
  "strings": ["isFallBackStable, wait fallback be stable to capture, isFallbackMatched: "]
}
```

`PopCaptureGate` 在它**执行前**返回 true（`beforeHookedMethod` 里 `setResult(true)` 会跳过整个方法体）：

```java
XposedBridge.hookMethod(stable, new XC_MethodHook() {
    @Override protected void beforeHookedMethod(MethodHookParam param) {
        if (!inRetroMode(param.thisObject)) return;   // 只动 POP
        param.setResult(Boolean.TRUE);
    }
});
```

跳过整个方法体是关键：不在 `after` 里改结果，等待分支的 `P/Q/R/X` 写入和快门置灰就一次都不会
发生。其它模式一个字都不改。

---

## 4. 装完请这样验

进 POP → 点快门 → 看 DCIM。日志里应该出现：

```
PopPort: POP capture gate: fallback-stable wait skipped in POP (com.oplus.camera.module.BaseMode.jc)
PopPort: POP capture: the fallback-stable wait is skipped in POP, it never settles on this phone and it also disables the shutter
```

- 出片 → POP 快门通了。
- 还是不出 → 日志会给出下一条 `CF_*` 原因，直接发我，接着往下拆。

---

## 5. 顺带

- 493 的 12 个 LUT（8 个 `positive_*` 等）原样带着，正片胶片是否恢复也请一并看一下。
- 哈苏水印的 NPE 证据见 493 的说明，还在下一版排队。

## 6. 没动的东西

算法栈（`libAlgoInterface` / `libBasicTonePhoto*`）和 ODM 配置仍然一个字节都不替换；唯一替换的
系统文件还是 `/odm/lib64/libmsnativefilter.so`（Meishe 渲染器）。
