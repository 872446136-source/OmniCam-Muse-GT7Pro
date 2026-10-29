# GT7Pro-适配 目录说明

真我 GT7 Pro（ColorOS 17.0.0.111 / 相机 17.006.112 / 相册 17.10.37）适配 OmniCam-Muse 的产物。

**源码仓库**：https://github.com/872446136-source/OmniCam-Muse-GT7Pro
**当前版本（单版本重构，刷这个）**：https://github.com/872446136-source/OmniCam-Muse-GT7Pro/releases/tag/full

| 包 | 说明 | 状态 |
|---|---|---|
| **`full` / Muse 4.1 GT7Pro** | 单版本，没有 Lite；所有功能都装，按本机能力自动启用；不替换任何系统库或 ODM 配置 | **刷这个** |
| `nostack-test` | 上一版过渡产物（去掉算法栈） | 已被 `full` 取代 |
| `v4.0-gt7pro-lite` | 最早的轻量版，只装纯 Java 钩子 | 已被 `full` 取代 |
| `full-port-test` | 暴力移植原版，实测取景器全黑 | 仅留档 |

上一版黑屏的原因已经定位：拿 X9 Ultra 的 `libAlgoInterface.so` / `libBasicTonePhoto.so` 和整套
ODM 配置覆盖了本机同名文件（天玑 vs 骁龙，结构体与配置格式都不同，且本机决策配置是加密的）。
`full` 这一版一个系统文件都不换，只叠加 LUT / basictone / 水印数据。

## 先看哪个

0. **`重构说明.md`** —— 当前版本：改了什么、本机每个功能是可用 / 部分可用 / 自动跳过，为什么。**要刷机就看这份。**
1. **`适配评估报告.md`** —— 结论、实测证据、逐功能兼容矩阵、下一步。
2. **`构建说明.md`** —— 打补丁、编译、刷入、排错，以及「这一版有什么、没什么」。
3. **`采集设备信息.sh`** —— 已经用过（结果在报告第五节）；换机或重采时再用。

## 文件清单

| 文件 | 作用 |
|---|---|
| `重构说明.md` | 单版本重构说明：架构改动、能力探测、本机功能状态表、确认步骤、取舍 |
| `无算法栈版.md` | 上一版过渡产物说明（已被 `full` 取代） |
| `适配评估报告.md` | 主报告：钩子层验证结果 + 完整版为什么做不出来的实测证据 |
| `构建说明.md` | 出包流程、Lite 功能清单、完整版还缺什么 |
| `采集设备信息.sh` | 设备侧数据采集（需 root，输出 `/sdcard/omnicam_gt7pro_dump.tar.gz`） |
| `清理残留.sh` | 卸载模块后补一次清理（模块自带 `uninstall.sh`，这个是手动兜底） |
| `诊断.sh` | 一次性收集排查信息：模块状态、bind.log、挂载、崩溃日志、相机 provider 状态 |
| `patch/gt7pro-lite.diff` | **主补丁**：10 个文件，轻量版全部改动（含 symbols.json 的 4 处修正） |
| `symbols.json` | 按 17.006.112 修正后的完整符号表（覆盖到 `app/src/main/assets/symbols.json`） |
| `patch/symbols-gt7pro.diff` | 只含 symbols.json 那 4 处修正的 diff（想单独看改动时用） |
| `tools/replay_gt7pro.py` | 一条命令回放整套符号表，看每个符号在你的 APK 上落到哪 |
| `tools/index_from_dex.py` | 直接从 dex 二进制生成 `tools/symbols/` 用的索引（不依赖 dexdump / java / androguard） |
| `tools/spec_eval.py`、`tools/dx.py` | 上游的符号评估器 + 精简版 dx（让上面那个脚本能独立跑） |
| `tools/elfsym.py` | ELF64 动态符号导出（报告里 `libAlgoInterface.so` 没有 `25021` 家族就是它查的） |
| `tools/axml.py` | 二进制 AndroidManifest 解析（读版本号 / 类数量用） |

## 快速开始

```bash
cd OmniCam-Muse
git apply GT7Pro-适配/patch/gt7pro-lite.diff
# 然后按 构建说明.md 第 4 节编译
```

## 验证钩子层（不用手机）

```bash
python tools/replay_gt7pro.py \
  --camera  "真我GT7Pro相机_17.006.112.apk" \
  --gallery "真我GT7Pro相册_17.10.37.apk"
```

当前结果：

```
== camera: 真我GT7Pro相机_17.006.112.apk
255 symbols, problems: 0
== gallery: 真我GT7Pro相册_17.10.37.apk
Section.segmentSetup               !! 0 hits []
Section.cameraStyles               !! unresolved dependency
31 symbols, problems: 2
TOTAL problems: 2
```

相册那 2 个未解析符号只被 `GrGalleryWatermark`（GR 相册水印）使用，轻量版不装这个功能，不影响。

加 `-v` 会逐条打印每个符号解析到的类/方法（254 行，适合排查）。只依赖 Python 3.9+ 标准库。
