> 真我 GT7 Pro（RMX5010 / ColorOS 17.0.0.112 / 相机 7.013.51 / 相册 17.10.41）适配版，单包无 Lite。

## 这一版修的是相机打不开

498 刷上去之后，相机点开就退，设置应用里「相机注入」那一栏是红的。日志里两条线索，但只有一条是原因：

```
相机注入：14 个符号未匹配，相机版本可能不受支持
```

这条只是功能降级，不是闪退的原因——14 条 missing 全部被 `Log.guard` / `Install.run` 接住了，
相机进程照常把 26 项功能装完，日志里 `featureStatus` 是清一色的 `on`。

真正退出的地方在 `logcat` 的 crash buffer 里：

```
java.lang.RuntimeException: Unable to start activity ComponentInfo{com.oplus.camera/com.oplus.camera.Camera}
Caused by: java.lang.ClassCastException: java.lang.Integer cannot be cast to java.lang.String
	at mp.h.c(DataBaseOperator.java:75)
	at mp.h.h(DataBaseOperator.java:335)
	at mp.h.<init>(DataBaseOperator.java:84)
	at mp.h$a.create(DataBaseOperator.java:3)
	at com.oplus.camera.util.w0.get(CameraSingleton.java:22)
	at mp.h.g(DataBaseOperator.java:3)
	at com.oplus.camera.Camera.s0(Camera.java:485)
	at la.d0.onCreate(QuickActivity.java:113)
```

`DataBaseOperator` 是相机自己的**模式库** `databases/mode_data.db` 的封装（450 / 460 / 471 都写过它）。
它在构造函数里把表逐行读一遍，每一列都按字符串取。7.013.51 把其中一列改成了整数，
于是拿 7.013.40 建出来的那张表去读，第一行就炸在 `Camera.onCreate` 里——此时模块的钩子还没跑。

## 为什么旧表还在

`ModeDataReset` 负责删这张表让相机重建，它的标记文件写的是模块版本号，而这个值自 **488** 之后
就没动过。489 到 498 这十个版本、相机从 17.006.112 到 7.013.40 再到 7.013.51 的两三次升级，
一次都没删过。表结构没变的时候看不出问题，结构一变就是启动即崩。

## 499 改了什么

**1. 模式库跟着相机包一起重建**（`common/ModeDataReset.java`）

标记内容从 `488` 改成 `499:<APK 长度>-<APK 修改时间>`。模块更新重建一次（有新模式的版本需要），
相机更新也重建一次（表结构可能动了）。代价是一样的：用户自己排的模式顺序会回到默认，
所以还是每次变化只做一次，不是每次启动都做。

没用 `versionName` 是因为 `LoadPackageParam.appInfo` 是 `ApplicationInfo`，它没有这个字段，
而这时 application 对象还没建出来。APK 的大小和修改时间随每次安装变化，正是要捕捉的事件，
平时启动又不会动。

另外补了一条：**删不掉就不写标记**。原来即使 `delete()` 失败也照样把标记写下去，等于
"试过一次就再也不试"，相机就会一直崩在同一行上；现在留空，下次启动继续尝试，并在日志里说清楚。

**2. 配置合并只覆盖结构对得上的部分**（`common/ConfigProfile.java`）

profile 是按一个相机版本写的。新版本把某个字段从文本改成数字、或者从值改成对象之后，
旧结构整块盖上去，就是上面那个 `Integer cannot be cast to String` 的另一种来源。

`merge` 里四个覆盖点（六个模式、`master_video_mode`、两张 feature 表、两个 use case）
现在都走 `putTyped` → `agree`：设备已有的键，两边每一层类型都对得上才覆盖，
对不上就整块留设备的，并打一行 `config: <名字> left as the device has it, the shapes disagree`。
设备有的键保留、模块要加的键照加，所以正常情况（设备本来没有这些模式）行为不变。

## 14 个符号：相机从 7.013.40 换到了 7.013.51

R8 重排过，写死名字的那批全部落空。它们是逐个被接住的，只有对应功能降级：

| 符号 | 受影响的功能 | 现在的状态 |
|---|---|---|
| `SoundPlayer.ready` / `silence` / `unsilence` | 快门声（静音、振动模式下照常播放） | 不生效，用原厂快门声 |
| `Shutter.t4` / `H4` | POP 快门按钮的渐变重绘 | 不重绘，按钮用原厂配色 |
| `FeaturePresenter.getCameraMode` / `modeName` | 物理柔光的**成片**那一半 | 取景有效，成片不套柔光 |
| `Mode.effectEntry` | 哈苏超清的入口按钮 | 入口不注入 |
| `RetroMode.previewDecision` | POP 的预览决策挂载点 | 未挂 |
| `FilterPresenter.styleMode` / `styleTarget` / `clampStyle` / `storeStyle` / `syncPalette` | 快速滤镜面板（上滑/长按开原厂滤镜条） | 面板不开 |

其余 317 个符号都还在，拍照、GR、POP、XPAN、大师色彩、物理柔光的取景、成片转储、
哈苏水印的空值保护全部正常。

要补这 14 个需要 7.013.51 的相机 APK：`/data/app/~~.../com.oplus.camera-.../base.apk`。
拿到之后用 `GT7Pro-适配/tools/replay_gt7pro.py` 回放，按特征重新对一遍名字，不用猜。

## 刷之前

由于这一版会删掉模式库，**刷完第一次进相机，模式栏的顺序会回到默认**，包括自己拖过的位置。
只发生这一次，之后正常。

如果相机还是打不开，先看两行：

```
adb logcat -d | grep -E "ModeData|DataBaseOperator"
```

- 有 `mode database dropped, the camera will re-seed it (marker 499:...)`：库删掉了，
  还是崩说明问题不在库上，把新的 crash 栈发过来。
- 有 `the mode database is still on disk, the next start will retry`：文件被占用没删掉，
  重启一次让它在开机后第一时间再试。
- 一行都没有：模块没进到相机进程，先看「注入状态」页的前四项。

## 版本

Muse 4.27 GT7Pro（499）。
