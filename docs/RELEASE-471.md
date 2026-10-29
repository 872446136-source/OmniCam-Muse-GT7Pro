> 真我 GT7 Pro（RMX5010 / ColorOS 17.0.0.111 / 相机 17.006.112 / 相册 17.10.37）适配版，单包无 Lite。**不替换任何系统库、不替换任何 ODM 配置。**

## 这一版修的是 470 留下的错

470 装完：GR 理光模式出现了，但切过去相机就卡住、滑不动；拍完照点进相册再退回来，相机黑屏卡死。POP 模式没有。

**根因是我在 470 的钩子里读了一个不存在的参数。** 470 的 `ModeGates` 里这么写：

```java
// 470 的写法（错的）
String key = (String) param.args[0];
Object fallback = param.args[1];   // getConfigBooleanValue(String) 只有一个参数
```

`getConfigBooleanValue(String)` **只有一个参数**，`args[1]` 不存在，于是每次调用都在钩子里抛
`ArrayIndexOutOfBoundsException: length=1; index=1`。框架把钩子里的异常吞掉、按原方法继续执行，所以：

- `param.setResult(TRUE)` 那一行**从来没执行过**；
- 模式表播种时每个开关问到的都是原值 false；
- `gr` / `retroCamera` 这两行**根本没写进 `mode_data.db`**。

日志里能直接看到（470 的日志）：

```
ModeGates: flag getter hooked, 5 flags answered true
java.lang.ArrayIndexOutOfBoundsException: length=1; index=1
    at local.jiege.hook.modes.ModeGates$2.beforeHookedMethod(...:47)
    at android.provider.ToothGraeserkGreet.getConfigBooleanValue(...)
    at dq.e.i(DataBaseOperator.java:139)
    at dq.e.h(DataBaseOperator.java:273)
```

`dq.e.i` / `dq.e.j` 就是播种模式表的那两个方法——异常正好落在写行的地方。

### 卡住和黑屏是同一个原因

切模式走 `vm/l.i(...)`，它打一行 `checkToSwitchMode, the device is not support the mode:gr`，
然后去 `dq/e.q(int)` 查那一行，内部 `ConditionVariable.block(timeout)` 等结果：

```
OCAM_ModeSwitcher: CameraTest checkToSwitchMode, start! lastModeName: null, modeName: gr, curModeName: common
OCAM_ModeSwitcher: checkToSwitchMode, the device is not support the mode:gr
```

行不存在 → 等满超时 → 判"不支持" → 界面卡在那里动不了。从相册退回相机也走同一条查询路径，
所以表现是黑屏卡死，其实是同一个超时。

## 471 的改法

`modes/ModeGates.java`：

```java
Object key = param.args[0];
if (!(key instanceof String)) return;
String name = (String) key;
if (forced(name)) {
    param.setResult(Boolean.TRUE);          // 先给答案
    if (SEEN.add(name)) log(name + " answered true");   // 日志在后面，且包在 try/catch 里
    return;
}
```

- **只读 `args[0]`**，不再碰 `args[1]`；
- **先 `setResult` 再打日志**，日志本身出问题也影响不到已经给出的答案；
- `ModeDataReset.VERSION` 推到 `"471"`，装完会**重新播种一次模式表**，把缺的行补上；
- 另外把相机**问过的开关名**记进日志（每个只记一次，最多 100 条，前缀 `asked:`）。POP 到底卡在哪一环，
  下次看日志就能分辨：是相机压根没问 `retroCamera`，还是问了但行被别处丢掉。

上一版的黑屏修复保持不变：开关仍然只在 `Application.onCreate` 之后回答，绝不主动调用原方法，
`CameraConfig` 用 `initialize = false` 加载。

## 装完先看这几行

```bash
adb shell su -c "logcat -d | grep -E 'ModeGates|ModeData|Install: |Symbols'"
```

期望（重点是**不能**再有 `ArrayIndexOutOfBounds` / `Exception in hooker`）：

```
ModeData: mode database dropped, the camera will re-seed it
ModeGates: flag getter hooked, 5 flags answered true
ModeGates: com.oplus.gr.mode.support answered true
ModeGates: com.oplus.feature.retro.camera.support answered true
ModeGates: com.oplus.camera.support.custom.hasselblad.watermark answered true
ModeGates: asked: com.oplus.feature.retro.camera.support      ← 说明 POP 的开关确实被问到了
Symbols: camera: 255 resolved, 0 missing
```

然后依次试：

1. 切 GR 理光模式 —— 应该不再卡住、能滑动、能拍照；
2. 拍一张，进相册，再退回来 —— 相机应该正常显示，不黑屏；
3. 相机设置 → 水印 —— 看有没有哈苏；
4. POP 模式 —— 如果还是没有，把日志发我，`asked:` 那几行会告诉我下一步该查哪里。

如果相机又出问题，先别卸模块：

```bash
adb shell su -c "touch /data/adb/omnicam_gates_off"
```

重启。相机应该立刻恢复（这一段被跳过），那就说明问题仍在这几个开关上。

## 回退

```bash
# 只关开关这一段（GR / POP / 哈苏水印没有，其它照常）
adb shell su -c "touch /data/adb/omnicam_gates_off"
# 只关原生层（profile 的重定向随之失效，相机回到原样，Java 功能照常）
adb shell su -c "touch /data/adb/omnicam_native_off"
# 整体关掉
adb shell su -c "touch /data/adb/omnicam_off"
# 全部恢复
adb shell su -c "rm -f /data/adb/omnicam_off /data/adb/omnicam_native_off /data/adb/omnicam_gates_off"
```

改完重启。因为不替换任何系统文件，关掉之后相机必然是出厂状态。

## 安装

1. KernelSU / APatch 刷 `OmniCam-Muse-GT7Pro-KSU.zip`
2. 装 `OmniCam-Muse-GT7Pro.apk`，LSPosed 里启用并勾选 `com.oplus.camera` 与 `com.coloros.gallery3d`
3. 重启

包体 227 MB，其中 `master_color/looks` 是大师色彩成片查找表（未压缩 253 MB）。不需要可删 `payload/master_color` 重打包，降到 ~30 MB。
