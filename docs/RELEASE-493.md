# OmniCam Muse 4.24 GT7Pro (493)

**POP 快门为什么点了没反应，查到底了。** 不是我们的门禁拦的，是相机自己的一句静默判定：
`BaseMode.n8` 最后一行 `decision.b != getCameraId()` —— 预览决策记的相机 id 和 POP 模式报的
不是同一个。这一版在那一行之前把它对齐。

这一版同时包含 492 的 12 个 LUT，装 493 不用先装 492。

---

## 1. 结论先说

488 和 491 两份日志，POP 里点快门都停在同一条：

```
W/OCAM_t1_BaseMode: isReadyToCapture, decisionResult is null or mCameraId not matched, so return
```

这句话是 `BaseMode.Td`（`hm.t1.Td` 走 `super`）打的。真正的判定不在 `Td` 里，而在它调用
的 `BaseMode.n8(cb.e, boolean, boolean)`：

```
009b: iget-object v3, v8, BaseMode.o          // synchronized (o)
009e: iget-object v0, v8, BaseMode.d1:Lcb/e;  // 上一次预览决策
00a1: invoke-virtual {v8, v0, v11, v12}, BaseMode.n8:(Lcb/e;ZZ)Z
00a6: if-nez v12, 0164                        // n8 说不行 → 打上面那句，返回 false
```

`n8` 有四个出口，**前三个都会打日志，第四个一行都不打**：

```java
boolean n8(cb.e d, boolean a, boolean b) {
    if (d == null)  { log("capture, decisionResult is null");                      return false; }
    if (!d.a)       { log("capture, decisionResult not allow to capture, ...");     ...          }
    if (!Rc(d))     { log("capture, checkPreviewResult not support capture, ...");  return false; }
    return d.b == getCameraId();          // ← 静默
}
```

491 那份真机日志里，前三句出现次数：

```
capture, decisionResult is null               0
not allow to capture                          0
checkPreviewResult not support capture        0
```

**全部为 0**。也就是：决策对象在、允许拍、预览结果也支持 —— 只是 `d.b` 和 `getCameraId()`
不相等。POP 跑在 `rear_sat` 上（日志 `targetCameraId: 12`），预览决策留的是它自己算出来的那个
逻辑 id，`n8` 直接拿两个数比。

---

## 2. 这份代码是怎么拿到的

不再靠猜，也不再用离线索引（那是另一个构建的混淆名）。直接拉**这台手机自己的** APK：

```bash
adb pull /data/app/~~...==/com.oplus.camera-.../base.apk   # 171,133,672 B
dexdump -d classes7.dex > dump.txt                         # 151 MB
```

`com.oplus.camera.module.BaseMode` 没被混淆（堆栈里本来就有），`n8` 在 `0x1a1124`，全文如下：

```
1a1136: if-nez v5, 0024            → d == null
1a117c: iget-boolean v1, v5, Lcb/e;.a   → 不允许拍
1a1200: invoke-virtual {v4, v5}, BaseMode.Rc:(Lcb/e;)Z  → 预览结果检查
1a1256: iget v5, v5, Lcb/e;.b:I         → 决策里的相机 id
1a125a: invoke-virtual {v4}, BaseMode.getCameraId:()I
1a1262: if-ne v5, v4, 009a              → 相等才算过
```

`Lcb/e;.b` 就是决策对象上的相机 id 字段。

---

## 3. 修法：对齐一个 int，别的什么都不动

新加 `PopCaptureGate`，在 `n8` 执行**前**把 `decision.b` 写成当前模式的 `getCameraId()`：

```java
XposedBridge.hookMethod(decide, new XC_MethodHook() {
    @Override protected void beforeHookedMethod(MethodHookParam param) {
        align(param, current);   // 只改 d.b，不改返回值
    }
});
```

`n8` 自己的四个判定一个都没绕过：决策为空、内存/连拍上限不允许、LivePhoto 不支持、以及 `Td`
后面还有的闪光灯、预览就绪、微距稳定、曝光时间那一串，全都照原样执行。这一版只保证"相机 id 一致"
这一项成立。

几个实现上的取舍：

- **怎么找到 `n8`**：不用名字。`BaseMode` 上 `(引用类型, boolean, boolean) → boolean` 这个方法
  在这个构建里只有它一个，签名本身就是指纹；而且额外要求第一个参数的类型上有 `int b` 字段，
  双重收窄。找到几个候选就一个都不 hook，只把候选名打进日志。
- **作用域**：只在 `PopFilterGate` 解析出的 `RetroMode`（`hm.t1`）实例上生效，其它模式一个字节
  都不碰 —— 它们本来就一致，这个写入对它们是空操作。
- **写一次就够**：每帧预览决策都是新对象，写入不会跨帧残留。

装好后日志里会看到：

```
PopPort: POP capture gate installed: com.oplus.camera.module.BaseMode.n8(cb.e, boolean, boolean), camera id field b
PopPort: POP capture: the preview decision was made for camera 0 and the mode is on 12, aligned so the shutter can fire; decision ...
```

第二条里的两个数字就是这次的答案 —— 请把它一起发回来。

---

## 4. 492 还没在真机上验过

492 补的 12 个 LUT（8 个 `positive_*` + `800t_master` / `ccd_base_*` / `fuji-nc_master`）还没装机
验证。493 里原样带着，装上直接看「正片胶片」：

- 有画面 → `PopPort: lut positive_sdr_gen_a_1.bin from the layer`
- 还是黑 → 看 `from the private copy` 还是 `from nowhere`

---

## 5. 顺带查到的：哈苏水印的 NPE

「相机和相册里哈苏水印不显示、选不了」这条，这次抓到了硬证据 —— 进 POP 时的一次
`BaseMode.dh` → `WatermarkModel` → `MasterWatermarkCreator`：

```
java.lang.NullPointerException: Attempt to invoke virtual method 'boolean org.json.JSONObject.has(java.lang.String)' on a null object reference
  at com.oplus.ocs.ipu.watermark.masterwatermark.MasterWatermarkCreator.isSystemRegionOverScope(MasterWatermarkCreator.java:6)
  at cl.e.m(WatermarkModel.java:322)
  at com.oplus.camera.module.BaseMode.da(BaseMode.java:101)
  at com.oplus.camera.module.BaseMode.dh(BaseMode.java:52)
```

`isSystemRegionOverScope(JSONObject)` 里第一步就是 `json.has("countryCode")`，传进来的是 `null`。
这个方法读的是水印样式的"地域白名单"（哈苏商标按地区授权，所以有这个开关），JSON 为 null 就
直接抛，整条水印流程断在这里。

同时看一眼这台机器的 `/odm/etc/camera/basictone/mode_limit_watermark/`：

```
gr_style_1..5.json  gr_style_1..5/  realme_gr_logo*.webp  sketch_*.webp
```

**只有真我 GT 的 GR 样式，一套哈苏样式都没有。** X8U 参考包里对应目录叫 `watermark_master_styles`，
是另外五个 json。这条下一版单独处理 —— 先要把 `WatermarkModel` 那个 JSON 到底从哪个路径读出来
定位清楚，再决定是补文件还是补 hook，不想在半懂的状态下改水印。

---

## 6. 装完请这样验

1. 进 POP，随便选一个滤镜，点快门 → 看 DCIM 有没有新文件。
2. 抓一下日志（或直接用设置页的导出），搜 `PopPort: POP capture`：
   - **有 `aligned so the shutter can fire` 且有照片** → 这条修好了。
   - **有那行但还是没照片** → 说明 id 对齐过了，卡在 `Td` 后面某一项，日志会接着给下一层。
   - **没有那行**，只有 `POP capture gate: ... has no ...` → 结构定位没命中，把那行发我。

---

## 7. 没动的东西

算法栈（`libAlgoInterface` / `libBasicTonePhoto*`）和 ODM 配置仍然一个字节都不替换；唯一替换的
系统文件还是 `/odm/lib64/libmsnativefilter.so`（Meishe 渲染器）。
