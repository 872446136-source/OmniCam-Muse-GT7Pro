# OmniCam Muse 4.23 GT7Pro (487)

**486 的两个修复都没生效，日志说明了原因，这一版按日志改。**

---

## 1. Meishe 渲染器：486 的 bind 从来没落地

486 用 `service.sh` 里的 `mount --bind` 把模块自带的 `libmsnativefilter.so` 盖到
`/odm/lib64/libmsnativefilter.so`。486 的报告里设置页读到的仍然是原厂字节数：

```
Meishe 渲染器：原厂版本（stock (7282176 bytes, no Polaroid/CCD/Portra400 renderer)）
```

bind 能不能落进 app 看得见的 mnt ns，取决于 KernelSU 把 `service.sh` 放在哪个命名空间里；
而当时唯一的记录是模块目录下的 `bind.log`，**它不在导出包里** —— 所以「没挂上」和
「挂了但看不见」分不出来。这两件事一起改：

| 改动 | 说明 |
|---|---|
| 文件改走挂载层 | `system/odm/lib64/libmsnativefilter.so`，magic mount 在任何 app 启动之前挂好，和 485 起一直在用的 `system/odm/etc/camera` 是同一条路，那条路已被日志证明能挂上（`KSU 模块：模块数据可读`） |
| bind 保留为兜底 | 没有元模块的机器走这条；源路径改成层里那份，不再单独放一份 payload |
| 结果可查 | 写进 `/data/local/tmp/omnicam-bind.log`，导出日志里多一个 **「模块日志.txt」**：`bind.log`、`action.log`、`module.prop`、`/odm/lib64` 的实际大小、层里那份的大小 |
| 执行按钮 | 改成真的把文件**移出/移回挂载层**（写标记撤不掉 magic mount），并把当前状态打出来 |

为什么必须换这个文件，486 的论证仍然成立：POP 的四款胶片和 GR 的四款滤镜是 Meishe renderer，
`libAlgoInterface.so` 硬编码绝对路径 + `dlopen` + `dlsym`，取不到没有回退；本机那份缺
`CNvPolaroid` / `CNvFilterCCD` / `CNvPortra400` / `CNvShapeMask` / `CNvCompoundLutTiled` /
`CNvInflation`，而 GR 的 `.MS-LUT` 又是 version 2。替换件 `10957976` 字节 / `4097` 导出，
在用的 16 个 Meishe 符号一个不少，本机有而它没有的 = 0。

> 装完第一次开机，如果「Meishe 渲染器」还是红的，请直接导出日志 —— 这次「模块日志.txt」
> 会写明是层里没有文件、还是 bind 失败、还是别的原因。

---

## 2. XPAN：前两版改的东西一次都没被调用

485 改 `com.oplus.feature.move.mode.to.panel` 的 rank，486 往 `move.mode.to.headline` 追加，
两版都 hook 了 `com.oplus.camera.configure.CameraConfig.u(String)`。486 的日志：

```
ModePlacement: panel move hooked, 大师模式 and XPAN go to the bar
```

**之后一条 ModePlacement 都没有。** 那个方法在本机这个版本上根本没被调用过。

模式栏不看那个配置，它看模式表 `databases/mode_data.db`：

```
ModeProbe: row +8000ms xpan id=28 position_mode_panel rank=13
ModeProbe: table +8000ms: 22 rows, headline: professional,retroCamera,commonVideo,common,gr,portrait,highPixel,more
```

487 直接写表：表种完之后（以及每次启动时，覆盖表已经存在的情况）把 `xpan` 那行改成
`position_headline`，rank 取最后一个真实模式之后的那一格；`more` 的 rank 是
`Integer.MAX_VALUE`，所以永远在最后。模式栏就把它当普通模式画出来，**「更多」那张面板不再
参与路径，选完也不会留在屏幕上**。

`ModeDataReset` 的版本号从 `476` 提到 `487`，刷完这一版会重建一次模式表。

---

## 3. 哈苏水印：486 的 hook 装了，但没被调用

486 的日志里只有一行 `HasselWatermark: hassel style list hooked`，而水印页面在 `01:20:43`
就打开了，日志导出在 `01:24:32` —— 整个会话里 `getBuiltInHasselStyles` **一次都没被调用**。
所以这条列表不是它建的，补空列表的逻辑自然也没机会跑。

487 不再猜，加一个**只读探针**：`WatermarkMasterStyle$Companion.getSeries(String)` 是分组时
每个样式 id 都会走一遍的方法，不混淆。它打出什么 id，就说明列表里到底有什么：

```
HasselWatermark: styles seen (16): personalize_brand_1,personalize_film_1,...
```

- `hassel_style_*` 出现在里面 → 分组是存在的，空的是**渲染**；
- 一次都没出现 → 列表在别处建，下一步就去查那里。

列表为空时补七个内置 id 的逻辑保留着，方法一旦被调用就会生效。

---

## 4. GR 相册水印

486 改成优先读挂载层之后，理光GR 那四个样式在相册里已经能选了（这次截图里 理光GR 标签下
四张缩略图都在）。这一版不动。

---

## 5. 没动的东西

485 的数据层与 overlay 语义、`meta-magic_mount-rs` 的用法、GR 卡死兜底、`UnmountedGuard`、
`PresenterGuard`、`retro_camera_case` / POP 视图补建 / GL 收敛、快门探针、特性表、profile，
全部原样。

---

`versionCode 487` / `versionName Muse 4.23 GT7Pro`
