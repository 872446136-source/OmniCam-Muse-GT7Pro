# GT7Pro-适配 目录说明

真我 GT7 Pro（ColorOS 17.0.0.111 / 相机 7.006.112 / 相册 17.10.37）适配 OmniCam-Muse 的产物。

**源码仓库**：https://github.com/872446136-source/OmniCam-Muse-GT7Pro
**编译好的产物**：
- 轻量版：https://github.com/872446136-source/OmniCam-Muse-GT7Pro/releases/tag/v4.0-gt7pro-lite
- 无算法栈版：https://github.com/872446136-source/OmniCam-Muse-GT7Pro/releases/tag/nostack-test
- 完整版测试包（会黑屏，仅留档）：https://github.com/872446136-source/OmniCam-Muse-GT7Pro/releases/tag/full-port-test

现在有三个包，都刷得进：

| 包 | 内容 | 状态 |
|---|---|---|
| 轻量版 `v4.0-gt7pro-lite` | 只装纯应用层钩子，不挂 `/odm`、不加载原生库 | 稳，但功能少，**没有滤镜** |
| **无算法栈版 `nostack-test`** | 轻量版 + 数据层（滤镜 LUT、X10 basictone、GR LMT、水印素材）+ GR 移植 | **推荐刷这个** |
| 完整版测试包 `full-port-test` | 上游全量 + 原生库 + 上游相机配置 | 实测取景器全黑、进相机卡死，仅留档 |

轻量版和无算法栈版都**不替换任何系统库**（无算法栈版只把 LUT / basictone 数据叠加到本机目录上）。
完整版为什么黑屏、哪些功能在本机做不出来，见 `适配评估报告.md` 第五节和 `无算法栈版.md`。

## 先看哪个

0. **`无算法栈版.md`** —— 最新一版（完整版去掉 X9 Ultra 算法栈）：装什么、去掉什么、怎么确认。**要刷机就看这份。**
1. **`适配评估报告.md`** —— 结论、实测证据、逐功能兼容矩阵、下一步。
2. **`构建说明.md`** —— 打补丁、编译、刷入、排错，以及「这一版有什么、没什么」。
3. **`采集设备信息.sh`** —— 已经用过（结果在报告第五节）；换机或重采时再用。

## 文件清单

| 文件 | 作用 |
|---|---|
| `无算法栈版.md` | 无算法栈版说明：产物、去掉的功能、保留的功能、确认步骤、取舍 |
| `适配评估报告.md` | 主报告：钩子层验证结果 + 完整版为什么做不出来的实测证据 |
| `构建说明.md` | 出包流程、Lite 功能清单、完整版还缺什么 |
| `采集设备信息.sh` | 设备侧数据采集（需 root，输出 `/sdcard/omnicam_gt7pro_dump.tar.gz`） |
| `清理残留.sh` | 卸载模块后补一次清理（模块自带 `uninstall.sh`，这个是手动兜底） |
| `诊断.sh` | 一次性收集排查信息：模块状态、bind.log、挂载、崩溃日志、相机 provider 状态 |
| `patch/gt7pro-lite.diff` | **主补丁**：10 个文件，轻量版全部改动（含 symbols.json 的 4 处修正） |
| `symbols.json` | 按 7.006.112 修正后的完整符号表（覆盖到 `app/src/main/assets/symbols.json`） |
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
  --camera  "真我GT7Pro相机_7.006.112.apk" \
  --gallery "真我GT7Pro相册_17.10.37.apk"
```

当前结果：

```
== camera: 真我GT7Pro相机_7.006.112.apk
255 symbols, problems: 0
== gallery: 真我GT7Pro相册_17.10.37.apk
Section.segmentSetup               !! 0 hits []
Section.cameraStyles               !! unresolved dependency
31 symbols, problems: 2
TOTAL problems: 2
```

相册那 2 个未解析符号只被 `GrGalleryWatermark`（GR 相册水印）使用，轻量版不装这个功能，不影响。

加 `-v` 会逐条打印每个符号解析到的类/方法（254 行，适合排查）。只依赖 Python 3.9+ 标准库。
