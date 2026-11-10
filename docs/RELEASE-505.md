# Muse 4.33 GT7Pro（versionCode 505）

相机 7.013.51 (60000) / 相册 17.10.41 / realme RMX5010 / Android 17 (SDK 37)。

504 的黑卡修好了（样式表按缩略图数量收敛），剩下的三件事在这一版：哈苏分区里那张明显过大的卡、注入状态页的「12 个符号未匹配」、以及 POP 调节里的色温与色调。

## 一、哈苏分区只剩一张超大卡

`WatermarkCameraSection.K0()` 里哈苏是**两个独立的列表**，不是一行：

```
camera_hassel_style_layout      普通哈苏的外框
camera_hassel_style_list        普通哈苏的 RecyclerView
camera_hassel_xpan_style_layout XPAN 的外框
camera_hassel_xpan_style_list   XPAN 的 RecyclerView
```

`K0` 先给普通列表建 adapter（`H0(this, R.string.…master_hasselblad, show, 2)`），再判 `T0().first` 决定要不要建 XPAN 那一半。两者都在时，界面上就是上下两组，而普通列表拿到的是**XPAN 用例**的样式表——每个条目按宽幅卡排版，一张就占满整行，看起来就是"有一张水印特别大"。

503 之所以能看出这个问题，是因为那时普通列表被品牌门禁清空，只剩 XPAN 的三张，两张卡尺寸一致；504 把普通列表填回去以后，宽幅卡混进普通列表才显出来。

两处改动：

1. 读样式表时固定用 `show = true`。这个布尔来自 `show = !T0().first && wideScreen`，它同时也是 `vm.L(show)` 的入参——传 false 拿到的是"XPAN 场景"的宽幅样式。这里要的是普通列表，就按普通用例读。
2. 普通列表一旦有内容，XPAN 那一半就返回空。原厂上两者互斥（`ShowHasselXpanStyle` 决定谁出现），本机这个开关答的是 "XPAN"，于是两边都会填。放 `/data/adb/omnicam_hassel_xpan_keep` 可以保留两边。

504 的缩略图收敛留着：普通列表仍是 8 项、缩略图仍是 6 张时，尾部两项照样截掉，不会再出现黑卡。

## 二、「12 个符号未匹配」不是注入失败

那 12 项在 7.013.51 上确实不存在，但也不是这一版才坏的——它们对应的入口本来就不在这台机器上：

| 符号 | 是什么 | 为什么没有 |
|---|---|---|
| `Mode.effectEntry` | 哈苏超清的右侧进入按钮 | 本机决策树没有 25M/200M 节点 |
| `SoundPlayer.ready` / `silence` / `unsilence` | 快门声静音的三个锚点 | 7.013 把三者合成一个 `(J)V`，`checkRingerModeReady` 在同一个方法里 |
| `Shutter.t4` / `Shutter.H4` | 快门按钮的两个渐变字段 | 换了绘制实现，字段类型不再有 |
| `RetroMode.previewDecision` | 理光模式接收预览决策的方法 | 参数类型 `eb.e` 在 7.013 改名 |
| `FilterPresenter.styleMode` / `styleTarget` / `clampStyle` / `storeStyle` / `syncPalette` | 长按滤镜键打开完整面板并翻页 | 这一组方法在 7.013 被重排，硬编码名全部落空 |

它们一直计入 `symbols.json` 的失败数，设置页就把整个相机注入标成"异常"。这一版给这类符号加了 `"optional": true`：解析失败仍然记录（依赖它的符号照旧判定为缺），但**不计入**诊断的缺失数，日志里单独一行 `not on this build`。

改动在 `SymbolSearch`（`optionalFailures`）与 `Symbols.install`（按 spec 分流统计）。真正的缺失仍然是红的，混进来的"本机没有"不再报警。

## 三、POP 调节里的色温与色调：查到了通道，没敢动配置

`basictone/setting_Retro/SimTool.ini` 里只有 SCLut（Sat/Cont）和 VigTable，没有 CMCW 段，`[switch]` 里也没有 `CWCM`；上游各机型的 `SimTool_Master.ini` 是带的：

```
[CMCWLut]
lut_size  = 32
CMCWLutNum  = 9
CoWa =-100/0/100/
CyMa =-100/0/100/

[switch]
CWCM = 1
```

`CoWa` 是冷-暖（色温），`CyMa` 是青-品红（色调）。这一段缺失时，这两个滑杆的值在 tuning 里没有对应通道。

**这一版没有改它。** 这份 `SimTool.ini` 是本机原厂的，`build-full.yml` 里一直照抄不改，原因写在旁边：grid size 与 switch 块必须和固件里的 `libBasicTonePhoto.so` 对齐，而"表找不到就当成全 0"在这条链上的表现是黑预览加绿屏。声明 `CMCWLutNum = 9` 之后库会不会自己生成这 9 张表，没有真机日志无法确认，赌错的代价是理光模式直接不能看。

下一步（需要一次连接设备的验证）：

1. 备份 `/odm/etc/camera/basictone/setting_Retro/SimTool.ini`，只在临时路径上加 `[CMCWLut]` 与 `CWCM = 1`；
2. 开理光，看预览是否正常、`logcat | grep -i cmcw` 有没有加载记录；
3. 正常就固化进 `tools/setting_retro/SimTool.ini`，不正常就撤。

**光感与去锐化另有其因。** 这两项不在这份 tuning 里（没有锐化段，`VigTable` 只覆盖暗角）。拖滑杆时 `IPU_FilterProcessUnitSDK.processImage` 的参数里 `image_adjust_intensity` 从头到尾都是 0、`filter_800t_custom_params` 只有 `lux_index`，说明调节值压根没送到滤镜单元。下一步要在这条调用上挂一次参数打印，用一份新日志确定是 UI 没写、还是写入后被丢弃。

## 四、版本与文件

- `versionCode` 504 → 505，`versionName` Muse 4.32 → 4.33 GT7Pro
- `module/module.prop` 同步（描述已更新）
- 改动文件：`SymbolSearch.java`、`Symbols.java`、`symbols.json`、`HasselCameraPage.java`、`README.md`、`build.gradle.kts`
- **数据层与配置文件一律未动**：`SimTool.ini`、`libmsnativefilter.so`、`basictone`、`meishe_lut` 全部保持 504 原样
