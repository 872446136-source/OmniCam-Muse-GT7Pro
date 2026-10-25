# OmniCam Muse 4.14 GT7Pro (478)

对着 477 的日志做的。这份日志只测了一件事——切到 POP——所以它把 POP 的问题说得很干净：
**保护生效了（不再闪退），但视图还是没建起来**。

---

## 1. 日志确认 477 装对了

```
Symbols: camera: 262 resolved, 0 missing (search, 1590 ms)
PopPort: retro show runnable guarded (run)
ShutterDispatch: shutter dispatch hooked on pn.a
ShutterDispatch: shutter handler traced: hm.i.Nj / hm.m3.Nj / hm.r.Nj
GlGuard: preview frame guarded (nn.o.onDrawFrame)
GlGuard: inverse-mask overlay guarded (com.oplus.camera.common.gl.p.r0)
```

符号 262/262、探针三个都装上了。这一版不用再动符号。

---

## 2. POP 卡死：`retro UI init done: false`

切到 POP 之后，日志里是这两行：

```
13:53:19.493 I PopPort: retro show runnable dropped (…ui.newsetting.container.t1, #1, retro UI init done: false)
13:53:52.589 I PopPort: retro show runnable dropped (…ui.newsetting.container.t1, #2, retro UI init done: false)
```

`#1` / `#2` 是两次切进 POP。两行都带着 `retro UI init done: false` —— 477 加的那个标志位，
回答的正是它要回答的问题：**`RetroCameraUIContainer` 的 init（`gq.t.F9`）从来没跑过**
（不是"跑了一半抛了"，start 那一行一次都没有）。

离线索引把这条链补完了：

| 位置 | 是什么 | 依据 |
|---|---|---|
| `t1.run()` | 读 `((gq.t) b).f`（`RetroCameraUI`）然后 `f.l(a)` | `iget-object Lgq/t; f Lbj/z` → `bj.z.l(I)V` |
| `gq.t.f` | 只有 `gq.t.F9(Activity, ui/a)` 写它 | `iput-object Lgq/t; f Lbj/z`，`F9` 里唯一一处 |
| `gq.t` | 由 `hp.q0.<init>` 构造（**每个相机会话一次**），构造时就把 `t1` 建好了 | `Lgq/t;-><init>` 唯一调用点 `Lhp/q0;` |
| `F9` 的唯一调用者 | `aj.a.N1(Bundle)`（`P.F9(L, M)`，无 null 判断） | `Leo/a;->F9` 只有 `Laj/a;` 调 |
| `aj.a` | `hb.y.c(int, Activity, ui/a, String, String)` 里按 `com.oplus.camera.feature.retro_camera` 创建 | 工厂里确实有这个键 |

所以链条是：**retro 的 feature presenter（`aj.a`）没被创建 → `N1` 没跑 → `F9` 没跑 →
`f` 是 null → 延迟的显示 runnable 够到一个空视图**。容器在、视图没建，所以模式切进去了、
屏幕上什么都没有 —— 这就是"卡死"。

477 只是把 `t1.run()` 包住不让它打死主线程，**没有解决"视图为什么没建"**。478 补上这一半：

> 显示 runnable 抛异常、且 init 没完成时，就地把视图建出来，然后把 runnable 重放一次。

要建视图需要 `F9(Activity, ui/a)` 两个参数，而它们都躺在混淆成单字母的字段里。478 一个名字
都不写死：

- **Activity**：容器里唯一一个声明类型是 `android.app.Activity` 的字段；
- **`ui.a` 门面**：遍历容器的引用字段，在取到的对象上找"无参、返回类型实现
  `com.oplus.camera.protocal.ui.a`"的方法并调用。实际命中的是相机的 manager 字段 →
  UI manager getter（新包 `gq.t.y` → `X()`）。这个接口名跨版本稳定，字段名和方法名都不稳定。

日志会写成这样：

```
PopPort: retro UI missing, building it on com.oplus.camera.ui.newsetting.container.gq$t
PopPort: ui facade taken from y -> X
PopPort: retro UI init start
PopPort: retro UI init done
PopPort: retro show runnable replayed after the view was built
```

容器实例来自 runnable 自己的字段（`t1` 里唯一的引用字段），所以第二次相机会话不会去修第一次的
容器；每个容器只试一次。`touch /data/adb/omnicam_poprepair_off` 只关修复、保留保护。

---

## 3. 那 5 条"注入错误"就是反色遮罩

诊断页那句「相机注入：5 条错误」在日志里是这五行：

```
E GlGuard: inverse-mask overlay skipped (1..5) java.lang.reflect.InvocationTargetException
Caused by: NullPointerException: Attempt to invoke virtual method
    'void com.oplus.camera.common.gl.y0.s()' on a null object reference
    at com.oplus.camera.common.gl.p.r0(GLES20Canvas.java:120)
    ← dq.l1.ah(CameraPreviewUI.java:635) ← dq.l1.g1(193) ← nn.o.D0(329) ← nn.o.onDrawFrame(34)
```

`gl.p.r0` 是 `drawInverseMaskOverlay`（四个参数 `RectF,float,float[],float`，POP 专用），
它读的着色器程序字段（新包 `gl.p.k0`，类型 `gl.y0`；旧包是 `gl.w0`）在这个进程里是 null ——
因为 retro 视图没建，那条 overlay 程序也就没编译。477 把它吞掉了，所以预览线程不再死，但：

- 每一帧都在抛一个被吞掉的异常（GL 线程上每秒 60 次填栈）；
- 五行 `Log.e` 让诊断页变红。

478 改成**连调用都不再发生**：连续 3 次失败就认定这个进程里那条程序不存在，之后直接
`setResult(null)` 跳过，不调用、不记日志、不计数。日志降到一行：

```
I GlGuard: inverse-mask overlay disabled for this process: the shader program is absent, so every frame would throw
```

同时把这条日志从 `E` 降到 `I` —— 这是本机的**已知正常状态**，不该被算成注入失败。

---

## 4. POP 资源：`pld` 目录整个不在

```
I PopPort: POP PLD resources missing: …/files/pop_port/pld_watermark/pld/pld.json (…/pld is absent)
```

CI 验过 zip 里有 `payload/pld_watermark/pld/pld.json`（`pld_watermark 10` 个条目，2.3 MB），
所以问题在**设备上的那一次复制**。477 把 `cp -a` 改成 `cp -aL` 没解决，原因很可能是：
`cp -a` 是 archive，它会把 `no-dereference` 标志重新加回来，`-L` 赢不了，链接还是按链接复制。

478 三件事：

1. **改用 `tar -ch`**：`-h` 无条件解引用，不吃 `-a` 那套标志；失败再退回 `cp -rfL`。
2. **逐目录铺、逐目录记账**：`pld_watermark` / `apk_resources` / `setting_Retro` 各一行，
   写进 `files/pop_port/status.txt` —— 哪个 payload 目录可见、落了多少个文件、`pld.json` 在不在。
3. **相机端把这张账单读出来**：资源还缺的时候，日志会同时打
   `POP tree: (…pop_port holds …)` 和 `POP staging report: pld_watermark: copied 10 files | … | pld.json: present`。
   下一次不用再猜是"没复制"还是"复制了但落成断链"。

`status.txt` 本身缺失时，日志会直接说 `service.sh did not reach the POP block` ——
那是另一种故障，一眼分开。

---

## 5. 日志镜像加了 `FeatureManager`

POP 的 presenter 是从特性清单里装配的，而清单本来就有一行日志：

```
FeatureManager: updateConfigure, supported featureList: …, modeName: retroCamera
```

477 的镜像白名单里没有 `FeatureManager`，所以这行一直被导出工具滤掉了。478 加进 `ALWAYS`。
下一份日志里如果 `retroCamera` 那一行的清单里**没有** `com.oplus.camera.feature.retro_camera`，
那 478 的补建就是在替一个本来不存在的 presenter 兜底，根因要往模式配置那边查；如果有，
说明 presenter 是别的原因没建。这一行把"要不要继续往下查"变成一次读日志。

---

## 6. GR 快门：这一版仍然只有探针

这份日志里**一次 GR 都没进**（`modeName: gr` 一次都没有，只有 `retroCamera` / `commonVideo`），
所以 GR 那一半没有任何新信息。三个探针（`shutter message built` / `shutter event dispatched` /
`shutter handled by <类>`）已经在 477 装好、这份日志确认都装上了，缺的只是**一次真按压**。

下次进 GR 按一下快门再导日志，三行有没有就能直接定位。

---

## 7. 这版没动的东西

- **哈苏水印**：还是按你说的先放着。
- **配置与模式库**：`ModeDataReset.VERSION` 仍然是 `476`，没有新增模式，不会再重播种
  `mode_data.db`，你排好的模式顺序保留。
- **相机/相册包**：还是 7.013.40 / 17.10.41。
- **符号**：262 条不变，这一版没有新增符号 —— POP 的补建完全靠运行时类型找，不吃符号表。
