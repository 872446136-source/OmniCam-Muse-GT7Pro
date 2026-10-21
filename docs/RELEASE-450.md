> 真我 GT7 Pro（RMX5010 / ColorOS 17.0.0.111 / 相机 17.006.112 / 相册 17.10.37）适配版，单包无 Lite。**不替换任何系统库、不替换任何 ODM 配置。**

## 这一版修的是 440 实测报告里的两个问题

用户日志 `OmniCam-Muse-日志-20261021-150608`：profile 已经生效了，但相机里还是看不到 GR / POP，相册还是报注入异常。

```
ConfigProfile: profile staged: 48602 bytes for RMX5010
Capabilities: data=true lmt=true retro=true profile=true profileBytes=48602 ... native=true
Symbols: gallery: 29 resolved, 1 missing
Symbols:   missing Section.cameraStyles: 3 matches [S0 boolean, U0 boolean, V0 boolean]
RicohGrPort: WatermarkCameraSection hooks failed ... symbol Section.cameraStyles unavailable
```

### 1. 模式栏不在配置里，在数据库里

440 之后 `profile=true`、`hasMode("gr_mode")=true`、装配页也显示 GR 已启用，相机里却**还是没有 GR**。原因不在配置：

相机**不**每次启动都从配置重建模式栏，而是把模式写进 `databases/mode_data.db`（`ModeDataSQLiteOpenHelper`），之后只查这张表。写表时每一行都要过开关 —— `gr` 要 `com.oplus.gr.mode.support`，`retroCamera` 要 `com.oplus.feature.retro.camera.support`。这台机器的表是在装模块**之前**建的，那时开关还是关的，所以这两行从来没写进去；之后配置怎么改，表里没有就是没有。

450 新增 `common/ModeDataReset.java`：在相机主进程里、任何模式代码跑起来之前，把这个 db 文件删一次（按模块版本号记标记，一个版本只删一次）。相机的"db 不存在"分支会按**当前**开关重新写一遍，`gr` / `retroCamera` 这时才进表。

由相机进程自己删，是因为 root shell 往 app 私有目录写会丢掉 SELinux 的 per-app category（440 就是栽在 `restorecon` 上），开机未解锁前更是写不进去。

代价：模式顺序会回到默认一次（只一次）。

### 2. POP 复古相机一起开了

上一版判断"本机决策树没有 retro 分支，加模式入口只会得到空模式"。重新核对后这个判断不成立：POP 的 operation code 在 X8 Ultra 现货里就是 `8001`，也就是**拍照模式**的码 —— 它复用的是本机已经有的 8001 决策树，不需要 retro 分支。渲染用的 `basictone/setting_Retro` 本来就已经由模块挂上（`retro=true` 就是这么来的）。

所以 profile 现在也给 `rear_wide` / `rear_main` / `rear_tele` / `rear_sat` 加 `retro_camera_mode`，`mode_operation_mode.retro_camera_mode = 8001`，并在相机 override 配置里打开 `com.oplus.feature.retro.camera.support`。

### 3. 相册注入异常：符号不唯一

440 删掉 `Section.segmentSetup` 之后，`Section.cameraStyles` 仍然没解析上 —— 它靠"被 `backButtonSetup` + `onCreate` 调用、无参返回 `boolean`"定位，17.10.37 里这种形状的**有三个**（`S0` / `U0` / `V0`），不唯一就等于没匹配。

X8 Ultra 参考包把同一个符号钉在 `V0`，语义是"读当前选中的 tab / segment"。450 改成按行为定位：

- `invokes`：`COUISegmentButtonLayout.getSelectedSegmentPosition()` + `COUITabLayout.getSelectedTabPosition()` → 收到 `Q0` / `V0`
- `notInvokes`：`CompoundButton.isChecked()` → 排掉多读一个 chip 选中态的 `Q0`
- 剩下唯一一个就是 `V0`

离线重放（本机 17.10.37 的 dexdump 索引）：相册 **30/30**，相机 **255/255**。

### 4. 相册 GR 水印样式：换流不换对象

440 想用反射造一个 `FileStyleSource`，但 17.10.37 里那个混淆类 `com.oplus.aiunit.vision.e9i` **在 dex 里根本没有构造器**（不是签名不对，是没有），任何反射都造不出来，日志里只剩一行空的 `constructors`。

450 直接挂 `AssetStyleSource.getInputStream()`（混淆类 `py4`，内部就是 `context.getAssets().open(name)`）：名字对上 `gr_style_*.json` 就返回 `FileInputStream(files/ricoh_gr_styles/...)`。同一个位置，少一层依赖。

## 功能状态

| 状态 | 功能 |
|---|---|
| ✅ 可用 | 滤镜目录、X10 滤镜（含**清透 / 琥珀**）、哈苏超清滤镜、大师滤镜补全、**理光 GR**、GR 水印 5 套、大师色彩、**移轴拍摄**、**XPAN**、**POP 复古相机**、水印系列解锁、批量水印、地址跳转、快门声、快启键、模式状态分离、4K60 滤镜、机型伪装、视频水印保护、相机日志镜像、相册调色板、专业视频 ISZ |
| ⚠️ 部分可用 | 大师 LUT 槽 / 大师对比度（本机无 `MergeLutTet_resize`）；物理柔光（本机无 `MsRenderHardwareBuffer`） |
| ⛔ 本机做不到 | 25MP / 200MP（决策树没有 25M / 200M 节点）；自然色彩（天玑专属） |

## 装完先看这几行

```bash
adb shell su -c "logcat -d | grep -E 'ModeData|ConfigProfile|Capabilities|Install: |Symbols: gallery'"
```

期望：

```
ModeData: mode database dropped, the camera will re-seed it
ConfigProfile: profile staged: 48748 bytes for RMX5010
Capabilities: data=true lmt=true retro=true profile=true profileModel=RMX5010 profileBytes=48748 ... native=true
Install: 23/25 features on, off: 25MP 解锁, 200MP 人脸门禁
Symbols: gallery: 30 resolved, 0 missing
```

`ModeData:` 那行只在**每个模块版本的第一次启动**出现。出现后相机模式栏里应该有理光 GR 和 POP 两个模式。

如果模式还是没出来，把 `logcat -d | grep -iE "ModeData|mode_data|DataBaseOperator"` 整段发出来 —— 是"删不掉"还是"写表时被别的开关挡住"，这两行能直接区分。

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
