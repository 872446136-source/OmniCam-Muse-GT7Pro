# OmniCam Muse 4.24 GT7Pro (491)

**POP 的快门门禁从 487 起就没装上过 —— 488/489 挂的是构造器，而这台机器的模式对象比钩子先出生。**
这一版换成按指纹找类、直接钩方法；顺带让 POP 的 LUT 来源在日志里可见。

---

## 1. POP 快门：门禁一次都没安装成功

488 的日志里只有这一行，**后面什么都没有**：

```
PopPort: POP filter gate armed on com.oplus.camera.module.BaseMode
```

少了本该紧跟的 `POP filter gate installed on …`。也就是说 `identify()` 从头到尾没有接受过任何
一个类 —— 门禁根本没装上，所以 488/489 的看门狗、超时清零、`s3`/`t3` 全都没跑过。用户看到的
`blocked during retro filter switching` 自然一模一样。

而形状检查本身是过得去的。把 `um.o1` 读出来：

```
字段：r3, s3(Z), t3(Z), u3(I), v3(I), w3(Lum/j1;)   → 布尔字段只有 2 个
方法：Gf(I)Z  Hf(I)V  Td(ZZZZ)Z  r6(I)V  T0()V  Ek(String,Z)V  Jg(Leb/e;)V
```

488 要求「布尔字段 1~4 个」、489 放宽到 1~6 个，**都满足**；五个方法一个不缺。

所以问题不在判断，在**触发方式**：两版都挂在 `BaseMode` 的构造器上，等一个 `um.o1` 实例被
`new` 出来。但相机在启动时就把所有模式对象建好了（488 日志里 `armed on BaseMode` 在
`02:27:51.004`，而进程 `02:27:50.85` 就起来了），构造器钩子永远等不到那个实例。

### 491 的做法

`assets/symbols.json` 新增 8 个符号，按**它们自己打印的日志字符串**做指纹，不认类名：

| 符号 | 指纹 | 本机命中 |
|---|---|---|
| `RetroMode` | `onCameraShutterButtonClick, blocked during retro filter switching` + `onFilterSnapSettled, snapSettled=true` | `um.o1` |
| `RetroMode.shutterClick` | 同上第一条 + `(int)boolean` | `um.o1.Gf` |
| `RetroMode.shutterTouch` | `onCameraShutterButtonDispatchTouch, …` + `(int)void` | `um.o1.Hf` |
| `RetroMode.readyToCapture` | `isReadyToCapture, …` + `(ZZZZ)boolean` | `um.o1.Td` |
| `RetroMode.filterScrolling` | `onFilterScrolling` + `(int)void` | `um.o1.r6` |
| `RetroMode.filterSettled` | `onFilterSnapSettled, snapSettled=true` + `()void` | `um.o1.T0` |
| `RetroMode.setSwitching` | `(String,boolean)void` | `um.o1.Ek` |
| `RetroMode.previewDecision` | `(eb.e)void` | `um.o1.Jg` |

用项目自带的离线回放（`tools/symbols/spec_eval.py`）对 `idx_gt7_cam.pkl` 跑过：**8 个全部唯一命中**，
整表 273 个符号的问题数仍是 18（全是改动前就有的老符号），没有新增。

然后**在类上钩方法**，不碰构造器 —— 实例早就存在也无所谓，类级钩子对所有实例生效。

另外补一条判断：`s3`（切换中）为真而 `t3`（已落定）也为真，是**自相矛盾**的状态 —— 不可能既在
切换又已落定。这种情况立刻清，不再等 1200 ms。

构造器那条老路保留成兜底（符号解析不出来时才走），并且现在会**说明为什么放弃**：
`POP filter gate skipped <class>: no Gf(int):boolean` 之类，不再静默。

---

## 2. POP 黑屏：这一版让 LUT 的来源可见

「正片 / 胶片 / 浓郁」三个滤镜黑屏、其余正常。Meishe 渲染器拿不到 LUT 就是黑帧，而这里能出错
的地方有三个：层里没有这个文件、只剩私有副本、或者文件在但格式读不了。前两个由
`pop_path_remap.cpp` 决定，之前**完全看不见**。

现在每个 LUT 名字第一次被读时打一行：

```
PopPort: lut positive_normal.bin from the layer
PopPort: lut positive_normal.bin from the private copy (the layer has no such file)
PopPort: lut positive_normal.bin from the imported masters
PopPort: lut positive_normal.bin from nowhere, the open is about to fail
```

第三种（`nowhere`）就是黑屏的直接解释。第四种里如果名字是 `positive_*.bin` / `Polaroid_*.bin`
之类，那就是格式问题 —— 顺带说一句：上游 X8U 的 `positive_*.bin` 是 **v4 / 17³**（和 `qing_tou`
的 v4 同版本号、不同尺寸），CI 的 `tools/mslut.py fix` 会把目录里**所有 v>3** 的 LUT 重采样成
v1 / 32³，所以层里那份应该是可读的。

---

## 3. 装完请这样验

进 POP，随便切一个滤镜，然后点快门：

```
PopPort: POP filter gate installed on um.o1 via symbols, switching flag s3, settled flag t3, 2 boolean(s)
```

有这一行 = 门禁这次真装上了。之后点快门如果还拦，应该紧跟一行：

```
PopPort: stale filter switch cleared (settled flag already set), flags s3=false, t3=true
```

再看 `相机.txt` 里的 `PopPort: lut …`，把黑屏那三个滤镜对应的行发我。

---

## 4. 没动的东西

算法栈（`libAlgoInterface` / `libBasicTonePhoto*`）和 ODM 配置仍然一个字节都不替换；唯一替换的
系统文件还是 `/odm/lib64/libmsnativefilter.so`（Meishe 渲染器）。
