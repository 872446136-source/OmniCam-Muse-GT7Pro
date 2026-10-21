> 真我 GT7 Pro（RMX5010 / ColorOS 17.0.0.111 / 相机 17.006.112 / 相册 17.10.37）适配版，单包无 Lite。**不替换任何系统库、不替换任何 ODM 配置。**

## 这一版修的是 430 实测报告里的四个问题

用户日志 `OmniCam-Muse-日志-20261021-142701` 里，`Capabilities` 那行写得很清楚：

```
data=true lmt=true retro=true profile=false plainDecision=false tuningJson=true native=true
```

`profile=false` —— 配置 profile 根本没生效，移轴 / XPAN 全被跳过，GR 也没有模式入口。原因不是逻辑写错，是**写入方式**错了：

`service.sh` 用 root 把 profile 拷进 `/data/user/0/com.oplus.camera/files` 之后跑了 `restorecon`。而 `restorecon` 在 app 私有目录上会把 per-app SELinux category 抹掉（file_contexts 里没有 category），app 随即读不到自己目录里刚写进去的文件；加上开机时 CE 存储可能还没解锁，root 连写都写不进去。

### 1. profile 改由相机进程自己写

现在由相机进程从 APK 的 `assets/profile/<model>/camera_unit_config` 写进自己的 files 目录（`ConfigProfile.ensure`）。进程写自己的目录，owner 和 SELinux context 天然正确，且一定发生在解锁之后。`service.sh` 不再拷 profile，并删掉全部 6 处 `restorecon`（app 私有目录只留 `chown` / `chmod`，新文件本来就继承父目录的 category）。`customize.sh` 在刷入时清一次旧 profile，避免 430 留下的坏文件被当成"已就位"。

### 2. GR 模式进相机

`mode_type_list` 里没有 `gr_mode`，所以 GR 钩子装了也看不到模式。参考 X8 Ultra（PKU110）现货配置：**`gr_mode` 的 operation code 就是 `professional_mode` 的 `8009`** —— 正好和 GR 移植"跑在专业模式之上"的实现对上。profile 现在给 `rear_wide` / `rear_main` / `rear_tele` / `rear_sat` 加 `gr_mode`，并补 `mode_operation_mode.gr_mode = 8009`。

### 3. 清透 / 琥珀 不再黑屏

这两个滤镜的 LUT 是 **v4 / 33³**。本机固件自带的 97 个 LUT 全是 **v1 / 32³**（58 个）或 **v3 / 17³**（39 个），**一个 v4 都没有**；而 payload 里恰好只有这两个是 v4 —— 也恰好就是黑屏的那两个。

新增 `tools/mslut.py`：CI 取到上游数据层后，把 v4 LUT 用三线性重采样转成 v1 / 32³。头部 176 字节直接取自同目录的现货 v1 LUT，**逐字节一致**；采样误差最大 6/255（网格量化，渲染时还有插值）。

### 4. 相册注入不再异常

两个符号未匹配：

| 符号 | 原因 | 改法 |
|---|---|---|
| `Section.segmentSetup` | 靠字符串 `"null cannot be cast to non-null type ...COUISegmentButtonLayout"` 定位，17.10.37 里没这句了 | 删掉该条目 |
| `Section.cameraStyles` | `calledBy` 里带着上面那个，跟着一起失败 | 改用 `backButtonSetup` + `onCreate` 两个调用者定位 |

→ gallery **30/30**。

另外 `FileStyleSource` 在 17.10.37 解析到混淆类 `com.oplus.aiunit.vision.e9i`，它没有单 `String` 构造器（`NoSuchMethodError`）。改成按 `(String)` / `(String,boolean)` / `(String,int)` / `(File)` / `(String,String)` 依次尝试，安装时把该类所有构造器签名打进日志；都对不上就保留内置资源，不再报错。

### POP 仍然不启用，但理由改准了

模式名是 `retro_camera_mode`（不是之前代码里写的 `retro_mode`）。但本机决策树没有 retro 分支、决策配置又加密补不出节点，只加模式入口会得到一个**空模式** —— 比没有更糟。所以只把判断和文案改对，不打开。

## 功能状态

| 状态 | 功能 |
|---|---|
| ✅ 可用 | 滤镜目录、X10 滤镜（含**清透 / 琥珀**）、哈苏超清滤镜、大师滤镜补全、**理光 GR**、GR 水印 5 套、大师色彩、**移轴拍摄**、**XPAN**、水印系列解锁、批量水印、地址跳转、快门声、快启键、模式状态分离、4K60 滤镜、机型伪装、视频水印保护、相机日志镜像、相册调色板、专业视频 ISZ |
| ⚠️ 部分可用 | 大师 LUT 槽 / 大师对比度（本机无 `MergeLutTet_resize`）；物理柔光（本机无 `MsRenderHardwareBuffer`） |
| ⛔ 本机做不到 | 25MP / 200MP（决策树没有 25M / 200M 节点）；POP 复古（无 retro 分支，决策配置加密）；自然色彩（天玑专属） |

## 装完先看这三行

```bash
adb shell su -c "logcat -d | grep -E 'ConfigProfile|Capabilities|Install: '"
```

期望：

```
ConfigProfile: profile asset assets/profile/RMX5010/camera_unit_config from ...
ConfigProfile: profile staged: 48602 bytes for RMX5010
Capabilities: data=true lmt=true retro=true profile=true profileModel=RMX5010 profileBytes=48602 ... native=true
Install: 21/25 features on, off: 25MP 解锁, 200MP 人脸门禁, POP 复古相机, 无缝模糊保护
```

`profile=true` 是关键。如果还是 false，日志里的 `ConfigProfile:` 行会说清是哪一步断的（取不到 asset / 建不了目录 / 改名失败）。

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
