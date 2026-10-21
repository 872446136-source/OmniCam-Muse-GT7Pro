> 真我 GT7 Pro（RMX5010 / ColorOS 17.0.0.111 / 相机 17.006.112 / 相册 17.10.37）适配版，单包无 Lite。**不替换任何系统库、不替换任何 ODM 配置。**

## 这一版修的是 450 实测报告里的两个问题

用户日志 `OmniCam-Muse-日志-20261021-154831`：相册注入正常了，相机里**仍然**没有理光 GR 和 POP，相册里**选不了哈苏水印**。

```
ConfigProfile: profile staged: 48748 bytes for RMX5010
ModeData: mode database dropped, the camera will re-seed it
Capabilities: data=true lmt=true retro=true profile=true profileBytes=48748 ... native=true
Install: 23/25 features on, off: 25MP 解锁, 200MP 人脸门禁
Symbols: gallery: 30 resolved, 0 missing
```

450 的判断"删一次 `mode_data.db` 让相机按当前开关重新播种"是对的，但**开关本身还是 false**，重播种只是把 false 又写了一遍。460 补的是这一步。

### 1. 开关不在配置里，在进程里

相机的模式表 `databases/mode_data.db` 由 `ModeDataSQLiteOpenHelper` 播种，每一行写之前都要过 `CameraConfig.getConfigBooleanValue(key)`：

| 模式行 | 开关 |
|---|---|
| `gr` | `com.oplus.gr.mode.support` |
| `retroCamera`（POP） | `com.oplus.feature.retro.camera.support` |
| `xpan` | `com.oplus.feature.xpan.mode.support` |

`getConfigBooleanValue` → `CameraUnitUtils.getVendorTagConfig(key)`，**直接读 `oplus_camera_config` 本身**。查过本机这个文件（已解密核对）：

- `com.oplus.feature.xpan.mode.support` = `0`（存在）
- `com.oplus.gr.mode.support` —— **不存在**
- `com.oplus.feature.retro.camera.support` —— **不存在**

缺键的时候这条路**永远回答 false**，模块写进 override SharedPreferences 的值在这条路径上根本不会被查。这就是 450 删库重播种后仍然没有 GR / POP 的原因：`hasMode()` 走的是 profile 里的 `mode_type_list`（所以装配页显示已启用），而写表走的是这个开关。

`oplus_camera_config` 是加密的（ColorOS 17：`01 01` + AES-128-ECB + 3 字节尾，头部 4 字节**不是**长度），重打包改不动，所以只能在进程内回答。

460 新增 `modes/ModeGates.java`：在相机主进程 hook `CameraConfig.getConfigBooleanValue(String)`，对上面三个 key 直接返回 `true`，并且**在 hook 装上之前先把原始值读一遍写进日志**：

```
ModeGates: com.oplus.gr.mode.support was false, answering true from now on
ModeGates: com.oplus.feature.retro.camera.support was false, answering true from now on
ModeGates: com.oplus.feature.xpan.mode.support was false, answering true from now on
```

`ModeDataReset` 的版本号同步推到 `460`，所以装完**再删一次**库，这次播种时开关已经是 true。

只影响相机进程。模块关掉，这三个 key 读到的就是固件原值。

### 2. 哈苏水印：挡在 feature 开关上，不在照片上

450 已经做了两件事：把照片的 `WatermarkInfo` 设备块标成"哈苏 / LUMO"、让 `isPhotoSupportWatermark` 恒 true。都生效了，面板里还是只有「大师」「文字」两个 tab。

原因在面板装配那一步。`WatermarkCameraSection.b1()` 逐个问 feature 开关，一个开关决定一行：

```java
feature_is_support_watermark_master   // 相机水印面板本身
feature_is_support_martin
feature_is_support_got_realme
feature_is_support_owl_realme
```

哈苏 / LUMO 的系列还额外要 `feature_is_support_hassel_device`、`feature_is_support_hassel_watermark`、`is_camera_support_lumo`。这些在本机**全是 false / 缺键**——面板在"这个功能不存在"的分支里，压根不会去问照片是不是哈苏拍的。

这些开关都走 `Config.getBoolean(String, boolean, boolean)`，所以 460 在 `WatermarkSeriesUnlock` 里一并回答 true，并对每一个 hassel / lumo / realme / watermark_master 的查询打一行日志（每个键一次）：

```
WatermarkSeriesUnlock: feature_is_support_hassel_device (default false) answered true
WatermarkSeriesUnlock: feature_is_support_watermark_master (default false) answered true
```

这行日志的作用是**下结论**：如果 460 之后哈苏还是不出来，日志里能看到面板到底问了哪些键、哪些我们没覆盖到。

### 3. 关于"要不要换个相机版本"

结论：**现在换没用**。理由是这次的两个问题都不在相机版本上：

- GR / POP 不出现，是因为 `oplus_camera_config` 里**没有这两个键**，而写模式表那条路径只认这个文件。换成别的 17.x 相机版本，只要它的 `oplus_camera_config` 同样不带这两个键，结果一模一样；而带这两个键的版本只存在于 X9 Ultra 那一档机型上。
- 哈苏水印选不了，是**相册**（17.10.37）的 feature 开关，跟相机版本无关。

真正该换的是**机型**，不是版本。460 是这条路走到底的最后一版：如果 `ModeGates` 那三行日志确认 `was false`、模式还是不出来，那就说明还有第二道过滤（`pq/h` 的 `position_headline` / `position_mode_panel` 查询，或 `fq/d` 按 rank 去重时把未知模式丢掉），需要再往上一层追——那时候再考虑换包。

## 功能状态

| 状态 | 功能 |
|---|---|
| ✅ 可用 | 滤镜目录、X10 滤镜（含**清透 / 琥珀**）、哈苏超清滤镜、大师滤镜补全、GR 水印 5 套、大师色彩、**移轴拍摄**、**XPAN**、水印系列解锁、批量水印、地址跳转、快门声、快启键、模式状态分离、4K60 滤镜、机型伪装、视频水印保护、相机日志镜像、相册调色板、专业视频 ISZ |
| 🔧 待验证 | **理光 GR 模式**、**POP 复古相机**（460 才补上开关）、**哈苏 / LUMO / 理光GR 水印系列**（460 才补上开关） |
| ⚠️ 部分可用 | 大师 LUT 槽 / 大师对比度（本机无 `MergeLutTet_resize`）；物理柔光（本机无 `MsRenderHardwareBuffer`） |
| ⛔ 本机做不到 | 25MP / 200MP（决策树没有 25M / 200M 节点）；自然色彩（天玑专属） |

## 装完先看这几行

```bash
adb shell su -c "logcat -d | grep -E 'ModeGates|ModeData|WatermarkSeriesUnlock|ConfigProfile|Symbols: gallery'"
```

期望：

```
ModeGates: com.oplus.gr.mode.support was false, answering true from now on
ModeGates: com.oplus.feature.retro.camera.support was false, answering true from now on
ModeData: mode database dropped, the camera will re-seed it
WatermarkSeriesUnlock: watermark feature flags: 10 answered true
WatermarkSeriesUnlock: feature_is_support_hassel_device (default false) answered true
ConfigProfile: profile staged: 48748 bytes for RMX5010
Symbols: gallery: 30 resolved, 0 missing
```

三件事要分开看：

1. `ModeGates ... was false` —— 证明开关确实是那道门。**如果这里打的是 `was true`，那 GR / POP 不出现就是另一回事**，把这段发回来。
2. `ModeData: mode database dropped` —— 每个模块版本只出现一次。
3. 进相册编辑器 → 水印，看 tab 里有没有多出哈苏系列。没有的话，`WatermarkSeriesUnlock` 那几行日志会告诉我们面板问了什么。

## 回退

```bash
# 只关原生层（profile 的重定向随之失效，相机回到原样，Java 功能照常）
adb shell su -c "touch /data/adb/omnicam_native_off"
# 整体关掉
adb shell su -c "touch /data/adb/omnicam_off"
# 恢复
adb shell su -c "rm -f /data/adb/omnicam_off /data/adb/omnicam_native_off"
```

改完重启。因为不替换任何系统文件，关掉之后相机必然是出厂状态。

## 安装

1. KernelSU / APatch 刷 `OmniCam-Muse-GT7Pro-KSU.zip`
2. 装 `OmniCam-Muse-GT7Pro.apk`，LSPosed 里启用并勾选 `com.oplus.camera` 与 `com.coloros.gallery3d`
3. 重启

包体 227 MB，其中 `master_color/looks` 是大师色彩成片查找表（未压缩 253 MB）。不需要可删 `payload/master_color` 重打包，降到 ~30 MB。
