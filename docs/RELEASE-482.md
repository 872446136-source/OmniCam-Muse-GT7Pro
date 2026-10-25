# OmniCam Muse 4.18 GT7Pro (482)

对着 481 的日志做的。481 把闪退治好了，相机能进 POP、能进 GR、镜头也有画面了，
但用户反馈变成了这样：

> pop 模式相机可以切换滤镜了不会卡死闪退镜头是黑的点了拍照没用。
> GR 理光可以拍照切换其他滤镜镜头是黑的拍照出来是绿色只有标准滤镜可以正常使用

这一版把这两条都修了，**Java 只加了一行诊断，真正的改动全在数据层**。
另外还挖出一个从 475 起就一直存在、之前所有版本都踩着的坑：`service.sh` 从来没跑完过。

---

## 1. POP 取景黑 + 快门没反应

### 1.1 库只认一个 ini 名字

把本机的 `libBasicTonePhoto.so` 拉出来扫字符串，ini 相关的只有一条：

```
%s/SimTool.ini
```

`libAlgoInterface.so` 里那条 `"/odm/etc/camera/basictone/setting_Retro"` 是**一个目录**。
所以 retro 那条路径打开的是 `setting_Retro/SimTool.ini`。

### 1.2 本机根本没有这个目录

`/odm/etc/camera/basictone` 下面只有：

```
lmt/  odt/  setting/  setting_dolby/  vig/
```

**没有 `setting_Retro/`**。模块给它建了一个（`post-fs-data.sh` 会把 payload 的
`setting_Retro` 叠到合并树里），但里面放的是上游那两个文件：

```
SimTool_Master.ini
SimTool_Photo_Master.ini
```

而这两个名字，在本机这个 `.so` 里**一个字符串都搜不到**。它们属于 X9 Ultra 那版库
（那版按 `SimTool_%s.ini` 拼名字）。

结果就是：retro 那条路径既读不到 `lut_size`，也读不到 `[switch]` 块，BasicTone
根本起不来。取景是黑的，快门按下去也没有后续 —— 这跟「点了拍照没用」完全对得上。

### 1.3 顺带确认了一件事

诊断行 `Capabilities: data=true lmt=true retro=true` 里的 `retro=true` 是个**假阳性**：
它只检查 `setting_Retro` 这个目录在不在，不检查里面有没有 `SimTool.ini`。
目录是模块建的，所以永远为 true，POP 永远是黑的也没人发现。

482 把它拆成了三个探针（见 §4.1）。

### 1.4 修法

`tools/setting_retro/SimTool.ini` —— 本机 `setting/SimTool.ini` 原样拷贝，
md5 `26b7e8aeca9e9d075db71aaf7c479b78`，448 字节：

```ini
[LMTLut]   lut_size = 64   LMTLutNum = 2
[SCLut]    lut_size = 64   SCLutNum  = 9
[ODTLut]   lut_size = 64
[switch]   LMT=0  SC=1  ODT=0  Vig=1  Merge=0
```

CI 里盖进 `payload/setting_Retro/SimTool.ini`，`post-fs-data.sh` 再把**本机整个 `setting/` 树**
（`SimTool.ini` + `Ditherout/` + `FromXml/`，库里还有个 `readLMTXML`，这些它都要）拷进合并后的
`setting_Retro/`，最后让 payload 覆盖上去。**没有一个字节是编的**，就是这台机器自己拍照在用的那一份。

## 2. GR 除「标准」外全黑 / 出绿

### 2.1 尺寸对不上

本机 `SimTool.ini` 写的是 `lut_size = 64`、`SCLutNum = 9`。库**按 ini 里的尺寸去读文件**。

上游 GR 那 9 张表叫 `SC32Lut0..8`，32³，每张 393216 B：

```
32^3 * 12 B = 393216
64^3 * 12 B = 3145728
```

按 64³ 去读一个 32³ 的文件，等于只读到四分之一，剩下的字节落到别的通道上。
尺寸不对，读出来的东西当然不对。

### 2.2 更直接的原因：481 一张 SC 表都没放

481 的 `service.sh` 里，GR 那段的循环是这样的：

```sh
case "$NAME" in
    LMTPhotoLut*|SCLut*) continue ;;
esac
```

也就是**主动跳过所有 `SCLut*`**，只拷 `SC16*` / `SC32*` / `CWCM*`。而这三个名字，
本机 `/odm/etc/camera/basictone/lmt` 里一个都没有 —— 它只有 `LMTLut0/1` 和 `SCLut0..8`。

所以 481 装完，`files/ricoh_gr/lmt` 里只有 `LMTLut0` 和 `LMTLut1`，**SC 表一张都没有**。

库读不到表就是零表，零表跑出来的就是黑取景、绿照片。
「标准」那个滤镜不吃 SC 调整，所以只有它能用 —— 用户看到的现象一字不差。

### 2.3 顺带搞清了 CWCM 为什么没意义

`libBasicTonePhoto.so` 里内嵌的 GLSL 片段，整条链路只有四级：

```glsl
dstYuv = lookup3dLut(dstYuv.rgb, lmtLutTexture);
dstYuv = lookup3dLut(dstYuv.rgb, mergeLutTexture);
dstYuv = lookup3dLut(dstYuv.rgb, odtLutTexture);
dstYuv = lookup3dLut(dstYuv.rgb, scLutTexture);
```

**没有 CWCM 这一级**，整个 `.so` 里也没有 `CWCM` / `CMCWLut` 字符串。
上游的 `SimTool_Master.ini` 里写着 `CWCM = 1`、`CMCWLutNum = 9`，还配了 18 张
`CWCM16Lut*` / `CWCM32Lut*` —— 在本机上这些表**没有任何人会去读**。

这也解释了上游那 9 张 SC 表为什么是「纯曲线」：它们的三个平面完全一样，
沿 R/G/B 三个轴分别是一条 tone 曲线（三轴曲线相同，互差 0.00）。
颜色那部分本来在 CWCM 里，本机做不到，所以 GR 的滤镜在本机只能体现**影调差异**，
不能体现**色彩差异**。这是库的限制，不是配置能绕过去的。

### 2.4 修法

新增 `tools/gr_lmt.py`，在 CI 里把上游 9 张 32³ 三线性重采样成 64³：

```
SCLut0 <- SC32Lut0  32^3 -> 64^3, 3145728 B, range 0.0000..1.0000
...
SCLut8 <- SC32Lut8  32^3 -> 64^3, 3145728 B, range 0.0000..1.0000
```

格式和本机完全一致：**无头、float32、平面布局**（R / G / B 三块各 n³ 个值，块内 R 最快）。

这个布局不是猜的，是量出来的：本机 `SCLut0` 在 `n³` 和 `2n³` 两个位置上各有一个
**0.479 的跳变**（`0.6978 → 0.2184`），平面边界清清楚楚；块内三条轴都是平滑的。

正确性校验：

- 恒等表重采样，误差 `1.1e-16`；
- 实表重采样后，每个平面与「按同样比例对源曲线做线性插值」的结果误差 `2.9e-08`（float32 精度）；
- 9 张表互不相同，与第 0 张的最大差 `0.0587 / 0.1134 / 0.0336 / 0.0902 / 0.1437 / 0.0689 / 0.1216 / 0.1730`。

`service.sh` 的 GR 段整个重写：

1. 先把本机 `/odm/etc/camera/basictone/lmt` **整棵树**拷进 `files/ricoh_gr/lmt`
   （`LMTLut0/1` + 本机自己的 `SCLut0..8`）；
2. 再用 payload 里那 9 张 64³ 覆盖上去。

这样无论库读 LMT 还是读 SC，目录里都是齐的、尺寸都是对的。

## 3. 从 475 起 `service.sh` 就没跑完过

这条是这次才发现的，而且它一个人解释了三个「怎么修都不好」的现象。

### 3.1 现象

481 的日志里，每一份都是这三行：

```
PopPort: POP PLD resources missing: .../files/pop_port/pld_watermark/pld/pld.json
PopPort: POP tree: (/data/user/0/com.oplus.camera/files/pop_port is absent)
PopPort: POP staging report: (.../pop_port/status.txt is absent — service.sh did not reach the POP block)
```

以及 322 次：

```
RicohGrPort: GR watermark style missing, keeping stock result: gr_style_1
```

### 3.2 原因

`/data/user/0/<pkg>` 要**等用户解锁**才会出现（这台机器 `ro.crypto.type=file`），
而 KernelSU 在 `late_start` 就跑 `service.sh` 了 —— 锁屏装机的时候那个目录还不存在。

旧代码：

```sh
if ! wait_for "$APP" 120; then
    exit 0
fi
```

等 120 秒，等不到就**静默退出**。整个脚本后面所有东西（`pop_port`、`ricoh_gr/lmt`、
`mode_limit_watermark`）全都没跑。

所以：
- POP 的 PLD 资源、retro UI 动画、引导图从来没投放 → POP 没有东西可画；
- GR 的 9 张表从来没拷过；
- GR 水印样式表 `files/mode_limit_watermark/gr_style_*` 从来没建过 → 那 322 行。

### 3.3 修法：`--wait` 自举

```sh
case "${1:-}" in
--wait)
    N=0
    while [ ! -d "$APP" ] && [ "$N" -lt 720 ]; do
        sleep 10
        N=$((N + 1))
    done
    [ -d "$APP" ] || exit 0
    ;;
esac

if ! wait_for "$APP" 60; then
    if command -v setsid > /dev/null 2>&1; then
        setsid sh "$MODDIR/service.sh" --wait > /dev/null 2>&1 &
    else
        (sh "$MODDIR/service.sh" --wait > /dev/null 2>&1) &
    fi
    exit 0
fi
```

- 等不到就把整段工作交给一个**脱离的副本**，最多等 2 小时；
- 主进程立刻 `exit 0`，不拖住开机；
- 解锁之后副本接着把整段跑完。

## 4. 482 顺手加的东西

### 4.1 诊断探针（唯一一行 Java 改动）

`Capabilities` 原来那个 `retro` 只看目录在不在，是假阳性。现在拆成三个：

```
retroIni    = setting_Retro/SimTool.ini 可读          （§1 的根因）
grSc        = files/ricoh_gr/lmt 里 SCLut* 的个数      （§2 的根因）
grScBytes   = files/ricoh_gr/lmt/SCLut0 的字节数
```

诊断行变成：

```
Capabilities: data=true lmt=true retro=true retroIni=true grSc=9 grScBytes=3145728
              profile=true profileModel=RMX5010 profileBytes=47473
              plainDecision=false tuningJson=true native=true
```

下次的日志里这三个数就是答案：
`retroIni=false` = SimTool.ini 没投放；`grSc=0` = service.sh 又没跑完；
`grScBytes=393216` = 又回到 32³ 了。

### 4.2 逃生口

```
touch /data/adb/omnicam_grsc_off
```

不覆盖那 9 张 GR 表，GR 继续用本机自己的 `SCLut0..8`。画面会变成普通拍照的样子，
但模式不会坏 —— 万一某档 GR 滤镜发白或者过曝，先用这个换回能用，再报。

## 5. 这一版要怎么验

刷完重启，**等解锁之后再打开相机**（第一次开机不要锁着屏插相机，`service.sh` 那个
副本要等解锁才开始干活；解锁后几秒内就装好了）。

1. **切 POP**：取景应该是有画面的（不再是黑的）。
   日志里 `Capabilities:` 那一行应该是 `retroIni=true grSc=9 grScBytes=3145728`。
2. **POP 按快门**：应该出现 `capture request entered`，相册里有照片。
3. **POP 切滤镜**：每档都过一遍，确认都能看到画面。
4. **切 GR**：标准之外随便挑几档，取景和成片都应该是正常影调，
   不再全黑、不再出绿。
5. **GR 水印**：日志里那 322 行 `GR watermark style missing` 应该消失。
6. 两个模式来回切几次，再切回普通拍照，确认画面正常。

如果还有问题，日志里这几种行直接指出位置：

```
PopPort: POP tree: (... is absent)                        -> service.sh 还是没跑
PopPort: POP staging report: (... status.txt is absent)   -> 同上
PopPort: POP PLD resources missing: ...                   -> payload 里缺 pld
RicohGrPort: GR watermark style missing: gr_style_N       -> mode_limit_watermark 没建
Capabilities: ... retroIni=false ...                      -> SimTool.ini 没投放
Capabilities: ... grSc=0 ...                              -> GR 表没拷
Capabilities: ... grScBytes=393216 ...                    -> 尺寸又变回 32³ 了
```

## 6. 改动清单

| 文件 | 改动 |
| --- | --- |
| `tools/setting_retro/SimTool.ini` | 新增：本机 `setting/SimTool.ini` 原样拷贝，448 B |
| `tools/gr_lmt.py` | 新增：32³ → 64³ 三线性重采样，平面 float32 RGB |
| `.github/workflows/build-full.yml` | 投放 `SimTool.ini`；生成 `payload/gr_lmt64/`；打包断言 |
| `module/service.sh` | `--wait` 自举；GR 段重写（先拷整树，再覆盖 9 张）；`omnicam_grsc_off` |
| `module/post-fs-data.sh` | 投放 `setting_Retro/SimTool.ini`；清掉 4 份重复注释 |
| `hook/common/Capabilities.java` | 新增 `retroSettings()` / `grTables()` / `grTableBytes()`，`describe()` 带上 |
| `module.prop` / `build.gradle.kts` | 482 / `Muse 4.18 GT7Pro` |

## 7. 这版没动的东西

- **哈苏水印**：仍然没碰，用户说过先不管。
- **GR 的颜色**：本机库没有 CWCM 级，做不到色彩差异，只能做影调。这是硬限制。
- 481 的 `PresenterGuard`、特性表重建、`prune()`，479 的 `retro_camera_case`、
  POP 视图补建、GL 收敛、GR 的 `Fe()` 闸，480 的快门探针 —— 全部原样保留。
- `xpan_mode`：仍然不加。
- 特性表：一个字都没改（482 没有重新生成 protobuf）。
