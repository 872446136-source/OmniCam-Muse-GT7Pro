# OmniCam Muse 4.13 GT7Pro (477)

对着 476 的第一份真机日志做的。这份日志里有三样有用的东西：三份一模一样的崩溃栈、
一份完整的 hook 安装清单、以及**没有**任何一次快门按压（所以 GR 那一半仍然只能装探针）。

---

## 1. 日志先确认了 476 装对了

```
ConfigProfile: live config merged: 28764 -> 46573 chars
Capabilities: data=true lmt=true retro=true profile=true profileModel=RMX5010 profileBytes=46573
ShutterDispatch: shutter dispatch hooked on pn.a
GlGuard: preview frame guarded (nn.o.onDrawFrame)
GlGuard: inverse-mask overlay guarded (com.oplus.camera.common.gl.p.r0)
CameraLog: capture mirror ready, 15 mode tags + 21 capture tags, cap 4000
Symbols: camera: 257 resolved, 0 missing
```

`live config merged` 那一行是合并路径真的走了：设备那份是紧凑 JSON（28764 字符），
输出是 `toString(2)` 的缩进版（46573 字符），所以字符数变大是格式不是内容。
`ModeProbe` 的表也全在：22 行，`gr` id=39 在 headline 第 4 位，`retroCamera` id=42 第 1 位。

---

## 2. POP 崩溃：容器在，视图没建

三次崩溃完全一样，都是 main 线程：

```
NullPointerException: Attempt to invoke virtual method 'void bj.z.l(int)' on a null object reference
  at com.oplus.camera.ui.newsetting.container.t1.run(R8$$SyntheticClass:135)
  at m9.i$a.run(ScheduledRunnable.java:24)
```

离线索引还原出来的链条：

| 位置 | 是什么 | 依据 |
|---|---|---|
| `t1` | 一次性 runnable，字段 `(int a, Object b)` | `<init>(Object,int)`，`run()` 读 `a`/`b` |
| `t1.run()` | 读 `((gq.t) b).f` 然后 `f.l(a)` | `iget-object Lgq/t; f Lbj/z` 紧跟 `bj.z.l(I)V` |
| `gq.t` | `RetroCameraUIContainer`，字段 `f` 是 `RetroCameraUI` | 日志标签 `RetroCameraUIContainer` / `RetroCameraUIAniHelper` |
| `gq.t.F9` | 建 `bj.z` 并赋给 `f` | `bj/z;-><init>` 的唯一调用点；`iput-object Lgq/t; f Lbj/z` |
| `t1.run()` 的字符串 | `showUIInternal` / `showUI, start outer background alpha in` | 就是"把复古 UI 显示出来"那一步 |

**所以不是 POP 的资源画不出来，是 UI 对象根本没建，延迟的显示动画照样去够它。**
`f` 为 null 只有两种来路：`F9` 没被调用，或者 `F9` 在赋值之前就抛了。两者外观一致，
而日志里 `OCAM_*` 全被过滤掉了（导出工具只留白名单标签），看不到 `F9` 里那些
`initRetroCamFilterScrollView, root view is null!` 之类的输出。

477 的处理：

1. **包住 `t1.run()`**（符号 `PopUI.show`，按 `run()V` + `showUIInternal` +
   `RetroCameraUIAniHelper` 两个字符串定位，不写死 `t1`）。原方法照跑，抛了就记一行丢掉，
   主线程不再被打死。日志：`retro show runnable dropped (…, #1, retro UI init done: false): …`
2. **`RetroCameraUIContainer` 初始化打两行**（符号 `PopUI.init`，按
   `(Activity, ui/a)V` + `RetroCameraUIContainer` + `initRetroCamFilterScrollView, root view is null!`
   定位）：`retro UI init start` / `retro UI init done`。
3. 被吞掉那一行带上 `retro UI init done: true|false`。**start 有、done 没有 → 初始化自己抛了，
   视图缺失就是从那里来的**；start 和 done 都没有 → `F9` 压根没被调用，那是容器注册的问题。

这两个符号都在旧包上验过：`PopUI.show` → 旧包 `cp.b.run`，`PopUI.init` → 旧包 `xq.f0.K9`，
所以不是只对新包有效的一次性写死。

---

## 3. GR 快门：三个边界，一次按压就能分开

476 的探针只装在总线（`pn.a.d`）上，而这份日志里**一次快门都没按**（窗口只有 20 秒，
`onShutterButtonClick` 一行都没有），所以 GR 那一半没有任何新信息。477 把边界补齐成三段：

| 日志行 | 位置 | 含义 |
|---|---|---|
| `shutter message built` | `CameraShutterButtonEventMessage.<init>` | 点击监听器跑到了构造事件这一步 |
| `shutter message dispatched` | `pn.a.d(message, boolean)` | 事件进了总线 |
| `shutter handled by <类>` | 所有收这个消息的方法 | 哪个模式接住了；`hm.i` 是 GR，`hm.r` / `hm.m3` 是另外两条 |

三个都按形状解析：消息类名本身不被混淆（`CameraShutterButtonEventSource$CameraShutterButtonEventMessage`），
收消息的方法按参数类型找，共三个（`hm.i.Nj` / `hm.r.Nj` / `hm.m3.Nj`，旧包是
`um.h.Jj` / `um.g3.Jj` / `um.n.Jj`）。GR 那个还能通过已有的 `GrMode` 类符号单独定位。

于是下一次按压的读法：

- 三行都没有 → 点击根本没进监听器（GR 用的是另一条 UI 路径，要查复古 UI 自己的快门）
- 有 built、没有 dispatched → 中间被拦
- 有 built + dispatched、没有 handled → **总线在 GR 模式下没有监听者**，模式类没订阅
- 有 handled（`hm.i`）、后面没有 `CaptureProcessor` → 处理器跑了、拍摄在后面被拒
- 有 handled 也有 `CaptureProcessor` → 拍摄发了，问题在更下游

---

## 4. POP 资源：别再留断链

`PopPort: POP PLD resources missing: …/files/pop_port/pld_watermark/pld/pld.json` 还在。
`service.sh` 确实会从 payload 铺这个目录，CI 也验过 `payload/pld_watermark/pld/pld.json` 在包里，
所以剩下的最可能就是**上游数据层用链接组织这些文件**，而 `cp -a` 复制的是链接本身 ——
落到 app 目录里就是一个断链，和"没复制"完全一样。

- `cp -a` → `cp -aL`（`pld_watermark` / `apk_resources` / `setting_Retro` 三处），链接跟着解开
- 仍然缺失时，日志会把目录里实际有什么列出来：
  `POP PLD resources missing: … (…/pld holds 3: a.json, b.webp, …)`，
  或者 `(…/pld is absent)` / `(… is a link to …)` 之类。下一次就能直接看出是哪一种。

---

## 5. 这版没动的东西

- **哈苏水印**：按你说的先放着。相机 APK 里没有哈苏水印样式条目，要做只能自造
  `files/mode_limit_watermark/<id>/<id>.json` + logo，没有参考样本。
- **配置与模式库**：`ModeDataReset.VERSION` 仍然是 `476`，这版没有新增模式，所以不会再
  重播种一次 `mode_data.db`，你自己排的模式顺序会保留。
- **相机/相册包**：还是 7.013.40 / 17.10.41，没有换包。
- 符号数 257 → 262（新增 `PopUI.show`、`PopUI.init`、`ShutterHandler.gr`、
  `ShutterHandler.all`、`ShutterMessage`），新包上 262/262 全中，相册 30/30。
