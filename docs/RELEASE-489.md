# OmniCam Muse 4.24 GT7Pro (489)

**488 的四条报告里，三条这次找到了「上一版为什么没生效」，第四条是日志本身看不见 —— 一并修了。**

---

## 0. 488 修好了什么、没修好什么

| 项 | 488 状态 | 489 结论 |
|---|---|---|
| POP 快门 | ❌ 还是 `blocked during retro filter switching` | 看门狗从没被武装，这次换了武装条件 |
| XPAN 成片 | ❌ 内容挤在左上角，下面全白 | 裁切区 hook 挂错了方法，这次挂全 |
| POP「正片胶片浓郁」黑 | ❌ 没变 | 旧 LUT 副本压过层，这次层优先 |
| 哈苏水印 | ❌ 相机和相册都没有 | 这一版只让日志能看见，不改行为 |

---

## 1. XPAN：裁切区从来没有交给算法

488 日志里唯一相关的两行是：

```
ModeGates: com.oplus.xpan.main.picturesize came from the config: 8000x3636
ModeGates: com.oplus.xpan.wide.picturesize came from the config: 8000x3636
```

**四个裁切区一条日志都没有** —— 既不是「came from the config」，也不是「was empty」。这说明
挂的那个方法，`XpanModel.onCreate` 根本没调用。

`CameraConfig` 里声明了**多个** `(String, int[]) -> int[]` 的方法，488 只挂了找到的第一个。

第二个原因更隐蔽：488 的策略是「空才填」。而 `XpanModel.onCreate` 拿到空数组后会用
`Arrays.copyOf(x, 4)` 变成 `{0,0,0,0}`，算法侧读到 0 就当「不裁」，**编码器却仍然按裁切后的画布
排版**。你那张图正是这个组合：画布是竖长条，内容却是没裁的 4:3 挤在左上角，下面是白的。

489 改了两处：**所有**这种形状的方法都挂上，由 key 决定走哪条；四个键**直接答**。

> 顺带纠正 488 自己的一个误判：`8000x3636` **不是错的**。它是缓冲区尺寸 ——
> `360+2912+360 = 3632`、`64+7872+64 = 8000`，正好对上；成品是裁切区 `2912x7872`。
> 所以 `picturesize` 仍然保持「空才填」，问题一直在裁切区。

---

## 2. POP 点拍照没反应：看门狗从来没武装起来

488 加了个 1200 ms 看门狗，武装条件是 `onFilterScrolling(r6)`。报告里同样三行
`blocked during retro filter switching` 反复出现，**却一条 `stale filter switch cleared` 都没有**。

把 `um.o1` 整个类读完，写 `s3` 的只有 `Ek(String, boolean)`：

```
r6(int)  onFilterScrolling       -> Ek(name, true)
Ek(String, boolean)               唯一写 s3 的地方
T0()     onFilterSnapSettled     -> t3 = true       全 APK 无调用方
Jg(...)  onPreviewDecisionResult -> Ek(name, ...)   全 APK 无调用方
```

`T0` 和 `Jg` 一个调用方都没有，所以 `s3` 一旦置位就再也不会被清。而 488 只从 `r6` 武装 ——
如果这次会话里 `s3` 是延迟任务 `um.j1` 置的（它同样走 `Ek`），`r6` 没被调用过，时间戳一直是 0，
看门狗每次都直接返回。

489：
- 字段按名字取 `s3` / `t3`，取不到再退回形状判定；
- `Ek` 和 `r6` 谁先动谁开始计时；
- 触发时把 `s3` 清掉、`t3` 置真 —— 和 `T0` 自己写的一样；
- 安装 / 识别 / 清除各打一行。

---

## 3. POP「正片胶片浓郁」黑屏：旧 LUT 副本压过层

滤镜读 LUT 时**先看** `files/pop_port/meishe_lut/<name>`，那是 484 建的副本；485 起不再建但也没删，
于是升级机上它一直盖住层里的同名文件 —— 而层里的 LUT 是构建时做过格式修正的（4.5 节：清透/琥珀
黑屏就是同一个格式问题）。

改成**层优先**：`/odm` 有就直接用，没有才回落到私有副本、再回落到你自己导入的滤镜。这样这类问题
不再依赖开机脚本有没有跑起来。`customize.sh` 里也顺手删掉那个目录。

---

## 4. 日志：为什么 488 的报告里什么都看不见

- 相机进程环形缓冲只有 **600 行**，导出时开头是 `02:28:14`，而进程 `02:28:10` 启动 ——
  四秒前的安装日志已经被相机自己的镜像冲掉了。所以「看门狗装没装」「哈苏门禁装没装」根本没法判断。
  现在 2400 行，模块自己的行被挤出时转入第二个环（400 行）保留。
- **相册进程的日志从来没进过导出包。** 哈苏水印门禁、GR 相册水印都在相册进程里打日志。
  现在导出多一个 `相册.txt`，和 `相机.txt` 同格式。
- `CameraLogMirror` 的 `"Xpan"` 前缀匹配不到 `"XPanPresenter"`（大小写敏感），而它正是打印
  `onPrepareCaptureRequest, cropRegion:…, XPAN add height:…` 的那个类 —— XPAN 最关键的一行一直
  没被镜像。已补上，并加上 `IPU_WM` / `WatermarkYuvInfo`。

---

## 5. 装完请这样验

**XPAN**（进 XPAN 拍一张，看日志）：

```
ModeGates: int[] config readers hooked: N, 4 XPAN crop regions answered
ModeGates: com.oplus.xpan.crop.region.main.portrait came from the config: 360,64,2912,7872
CameraLog: XPanPresenter onPrepareCaptureRequest, cropRegion:[360, 64, 2912, 7872], XPAN add height:…
```

成片应该是**竖长条 2912x7872**（约 24:65），内容铺满，不再是左上角一小块。

**POP**（进 POP，滚一下滤镜条，再按快门）：

```
PopPort: POP filter gate installed on um.o1, switching flag s3, settled flag t3, 2 boolean(s)
PopPort: stale filter switch cleared after 12xx ms idle, flags s3=false, t3=true
```

看到第一行就说明装上了；看到第二行说明清除生效。两行都没有 = 还有别的原因，把 `相机.txt` 发我。

**POP 正片胶片浓郁**：取景应该不再是黑的。

**哈苏水印**：这一版不改行为。相册的 `相册.txt` 里会有

```
HasselWatermark: hassel section gate armed on N reader(s)
HasselWatermark: feature_is_support_hassel_watermark answered true
HasselWatermark: watermark switch feature_is_support_hassel_watermark reads true
```

有前两行、没有第三行 = 门禁装上了但没人问；三行都有还是看不到 = 卡在别处（下一版再挖）。

---

## 6. 没动的东西

算法栈（`libAlgoInterface` / `libBasicTonePhoto*`）和 ODM 配置仍然一个字节都不替换；唯一替换的系统
文件还是 `/odm/lib64/libmsnativefilter.so`（Meishe 渲染器）。
