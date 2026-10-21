> 真我 GT7 Pro（RMX5010 / ColorOS 17.0.0.111 / 相机 17.006.112 / 相册 17.10.37）适配版，单包无 Lite。**不替换任何系统库、不替换任何 ODM 配置。**

## 这一版修的是 460 造成的问题

460 装完：相机打不开，打开就黑屏，进不到相机页面；模块设置页一直显示「正在检查」；导出日志卡死闪退。

**先说结论：460 里让相机黑屏的那段代码已经删了。** 470 的相机行为与 450 一致（450 是能正常开机的），只多了几个"等应用起来之后才回答"的开关。

### 1. 黑屏是怎么来的

460 为了回答模式表的开关，在 `ModeGates` 里做了这件事：

```java
// 460 的写法（已删除）
boolean before = (Boolean) getter.invoke(null, key);   // 在 handleLoadPackage 里！
```

`handleLoadPackage` 跑在 `handleBindApplication` 中途，**Application 还没创建**。这时候主动调用
`CameraConfig.getConfigBooleanValue` → `CameraUnitUtils.getVendorTagConfig`，等于把相机的配置引擎提前叫醒：
它在"什么都没有"的状态下把配置读了一遍并缓存，相机随后启动，拿到的是空配置 —— 于是预览出不来，就是黑屏。

顺带解释了另外两个现象：设置页查状态是 `contentResolver.call` 到相机的 provider，这个调用**会拉起相机进程并等它起来**；相机进程起来就崩，所以永远等不到，一直「正在检查」；导出日志走同一条路，于是卡死。

### 2. 470 的改法

`modes/ModeGates.java` 重写：

- **不再主动调用原方法**，一次都不调。`was true/false` 那种预读日志删掉了。
- 类用 `Class.forName(name, false, loader)` 加载（不触发静态初始化），按签名找
  `getConfigBooleanValue(String)Z`，找不到就只打一行日志退出。
- 开关**只在 `Application.onCreate` 之后**回答，并且额外挂 `Instrumentation.callApplicationOnCreate`
  兜底（有的 Application 重写 `onCreate` 不调 super）。
- 整个流程包在 try/catch 里，任何一步失败都只影响 GR / POP / 哈苏水印，不影响相机启动。
- **逃生口**：`touch /data/adb/omnicam_gates_off` 单独关掉这一段，相机照常能用。不用卸模块。

`CameraConfig.flag` 这个符号也从 `symbols.json` 里撤掉了（回到 450 的 **255** 个），启动时的符号解析不再碰这个类。相册 **30/30**。

### 3. 哈苏水印：在相机里，不在相册里

上一版我理解错了方向，去改相册的 feature 开关。你说的是**相机里**的哈苏水印。查本机配置（已解密）：

| key | 本机 |
|---|---|
| `com.oplus.camera.support.custom.hasselblad.watermark` | **0** |
| `com.oplus.camera.support.custom.hasselblad.watermark.sellmode.default.open` | **0** |
| `com.oplus.hasselblad.watermark.guide.support` | 0 |
| `com.oplus.use.hasselblad.style.support` | 0 |

这几条**都在配置里**，只是值是 0（和 GR / POP 那种"键根本不存在"不是一回事）。470 把前两条回答成 true，
相机设置里的哈苏水印项才有机会出现。后两条（引导弹窗、哈苏整体 UI 风格）**没有动**——引导会弹教程，
整体风格会改配色，先不碰，等确认水印能出来再说。

相册那边 460 加的 10 个 feature 开关**已全部撤回**，回到 450 的行为（450 相册是好的）。

### 4. 设置页不再卡死

`Diagnostics.kt` 里查相机 / 相册状态改成**独立线程 + 6 秒超时**。进程起不来时页面会直接说"相机未被注入"，
而不是永远转圈；导出日志走同一个入口，也不会再卡死。

## 装完先看这几行

```bash
adb shell su -c "logcat -d | grep -E 'ModeGates|ModeData|Install: |Symbols'"
```

期望：

```
ModeData: mode database dropped, the camera will re-seed it
ModeGates: flag getter hooked, 5 flags answered true
ModeGates: com.oplus.gr.mode.support answered true (camera passed default false)
ModeGates: com.oplus.camera.support.custom.hasselblad.watermark answered true (camera passed default false)
Symbols: camera: 255 resolved, 0 missing
```

**只要相机能正常打开，这次就算成功一半。** 再看两件事：

1. 模式栏里有没有理光 GR 和 POP；
2. 相机设置 → 水印 里有没有哈苏。

如果相机又黑屏了，先别卸模块，直接：

```bash
adb shell su -c "touch /data/adb/omnicam_gates_off"
```

重启。相机应该立刻恢复（这一段被跳过），那就说明问题仍在这几个开关上，把日志发我。

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
