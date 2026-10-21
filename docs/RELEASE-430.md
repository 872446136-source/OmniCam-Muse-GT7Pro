> 真我 GT7 Pro（RMX5010 / ColorOS 17.0.0.111）适配版，单包无 Lite。**不替换任何系统库、不替换任何 ODM 配置。**

## 本版（Muse 4.2 GT7Pro / versionCode 430）

### 1. 修掉了黑屏

上一版把 X9 Ultra 的 `libAlgoInterface.so` / `libBasicTonePhoto.so` 覆盖到本机 `/odm/lib64/`，又覆盖了加密配置。
X9 是天玑 9500、GT7 Pro 是骁龙 8 Elite，`AlgoProcessData` 结构体布局不同，相机进程在开取景器前就死了。

本版**一个系统文件都不换**，CI 打包后硬性断言 zip 内没有 `.so`、没有 `system/`，出现即 fail。

### 2. 新开启：移轴拍摄、XPAN

上一版判定"本机不支持"，结论是错的。逐项核对固件后确认：操作码、APS 算法开关、决策树节点、
输出流数、8000×3636 分辨率与裁切区域**本机全都有**，只是 `mode_type_list` 没列。

本版通过**配置 profile** 把入口补上（`tilt_shift_mode` 进 5 个镜头，`xpan_mode` 进 `rear_main`，
补 `mode_operation_mode.xpan_mode = 80AC` 与 `usecase_info.xpan_case`）。

实现方式是**进程内路径重定向**，不是挂载：

```
/odm/etc/camera/config/<name>  →  /data/user/0/com.oplus.camera/files/jiege/profile/<name>
```

只在相机进程内生效，`/odm` 一个字节没动，卸载后不存在残留。

### 3. 配置加密已解开

封装格式 `01 01 <4B头> <AES-128-ECB 密文> <FF + 3B尾>`，key 取自本机 `libOplusSecurity.so` 偏移 `0x7AB`
（逐字节 XOR `0x3F`，隔一个取一个 → 16 字节 AES key）。头尾 4 字节原样拷贝，已验证不是任何校验和，
解密再加密能**逐字节复现**原文件。工具在 `tools/oplus_config.py`。

### 4. 诊断页可见

设置 → 诊断页 → 「功能装配」一行直接列出 `N 项已启用，M 项未启用：功能（原因）`，不用猜。

## 本机功能状态

| 状态 | 功能 |
|---|---|
| ✅ 可用 | 滤镜目录、X10 滤镜+调色板、哈苏超清滤镜、大师滤镜补全、GR 移植、GR 水印 5 套、大师色彩、**移轴拍摄**、**XPAN**、水印系列解锁、批量水印、地址跳转、快门声、快启键、模式状态分离、4K60 滤镜、机型伪装、视频水印保护、相机日志镜像、相册调色板、专业视频 ISZ |
| ⚠️ 部分可用 | 大师 LUT 槽 / 大师对比度（本机无 `MergeLutTet_resize`）；物理柔光（本机无 `MsRenderHardwareBuffer`） |
| ⛔ 本机做不到 | 25MP / 200MP（决策树里没有 25M/200M 节点，22 个 `SINGLE_ALGO_*` 只有 `TILT_SHIFT`）；POP 复古（四份配置解密后 `retro` 出现 0 次）；自然色彩（天玑专属，无 `conf_tuning_params.json`） |

> 判断标准：**入口没开的补入口，零件没有的不补。**

## 开关

```bash
# 只关配置 profile（移轴 / XPAN 回落到原样）
adb shell su -c "touch /data/adb/omnicam_no_profile"
# 只关原生层（Java 功能照常）
adb shell su -c "touch /data/adb/omnicam_native_off"
# 整体关掉
adb shell su -c "touch /data/adb/omnicam_off"
# 恢复
adb shell su -c "rm -f /data/adb/omnicam_off /data/adb/omnicam_native_off /data/adb/omnicam_no_profile"
```

改完重启。

## 安装

1. KernelSU / APatch 刷 `OmniCam-Muse-GT7Pro-KSU.zip`
2. 装 `OmniCam-Muse-GT7Pro.apk`，在 LSPosed 里启用并勾选 `com.oplus.camera` 与 `com.coloros.gallery3d`
3. 重启

## 校验

```bash
# 数据层：应该只有 2 行 "dir bind ok"
adb shell su -c "cat /data/adb/modules/ricoh_pop_x9u/bind.log"
# 没有替换库：应该是空
adb shell su -c "ls /data/adb/modules/ricoh_pop_x9u/system 2>/dev/null"
# profile 到位：应有 camera_unit_config 与 enabled
adb shell su -c "ls -l /data/user/0/com.oplus.camera/files/jiege/profile"
```

包体 227 MB，其中 `master_color/looks` 是大师色彩成片查找表（未压缩 253 MB）。不需要可删 `payload/master_color` 重打包，降到 ~30 MB。
