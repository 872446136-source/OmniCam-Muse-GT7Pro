# OmniCam Muse 4.19 GT7Pro (483)

482 装完之后用户报的还是同一句话：

> pop 模式相机可以切换滤镜了不会卡死闪退镜头是黑的点了拍照没用。GR 理光可以拍照切换其他滤镜
> 镜头是黑的拍照出来是绿色只有标准滤镜可以正常使用

但 482 加的探针把答案直接写在日志里了，所以这一版不用猜。

---

## 1. 482 的日志说了什么

```
Capabilities: data=true lmt=true retro=true retroIni=true grSc=-1 grScBytes=0
              profile=true profileModel=RMX5010 profileBytes=47473
              plainDecision=false tuningJson=true native=true
```

- `retroIni=true` —— `setting_Retro/SimTool.ini` **投放成功了**，§1 那条根因确实修掉了；
- `grSc=-1` —— `/data/user/0/com.oplus.camera/files/ricoh_gr/lmt` **这个目录根本不存在**
  （`listFiles()` 返回 null）；
- `grScBytes=0` —— `SCLut0` 读不到。

以及 PopPort 依旧三连：

```
PopPort: POP PLD resources missing: .../files/pop_port/pld_watermark/pld/pld.json
PopPort: POP tree: (.../files/pop_port is absent)
```

**结论：482 对根因的判断有一半是对的，但数据一个字节都没到位。**

## 2. 为什么没到位

因为这些东西**全在 `service.sh` 里**，而 `service.sh` 从 475 起就没跑完过。

`/data/user/0/<pkg>` 要等用户解锁才出现（这台机器 `ro.crypto.type=file`），
KernelSU 在 `late_start` 就跑脚本，那时目录还不存在。

482 加了个 `--wait` 自举 —— 等不到就 `setsid` 起一个脱离的副本。日志证明这条路也没活下来。
最可能的原因：本机没有 `setsid`，于是走到 `( sh ... & )` 那个分支，子进程挂在同一个进程组上，
父 shell 退出时被一起收走。

**所以这一版不再赌时序。**

## 3. 483 的做法：把相机侧的数据全部搬到 `post-fs-data.sh`

### 3.1 为什么 `post-fs-data.sh` 靠得住

它**确实在跑** —— `retroIni=true` 就是它干的（它把 `setting_Retro/SimTool.ini` 写进合并树，
相机进程真的读到了）。而且：

- `post-fs-data` 阶段 `/data` 已经可用，`/data/adb/modules` 可写；
- 合并树是 `/data` 目录的 **bind mount**，不是只读的 `/odm`；
- 相机进程**能读**这个树 —— 同一条 `retroIni` 探针已经证明了（`File.canRead()` 返回 true）；
- 不需要解锁，不需要任何 app 侧拷贝，不需要等。

### 3.2 搬到哪、搬了什么

全部进 `/odm/etc/camera/basictone/`（合并树）下的新目录：

| 目录 | 内容 | 原来在哪 | 谁读它 |
| --- | --- | --- | --- |
| `pop_port/` | `pld_watermark`（拍立得水印）+ `apk_resources`（复古 UI 动画 25 个 json、引导图 4 张 webp） | `files/pop_port` | `PopResources` |
| `lmt_gr/` | 9 张 64³ `SCLut0..8` + 本机 `LMTLut0/1` | `files/ricoh_gr/lmt` | `gr_basictone.cpp` |
| `mode_limit_watermark/` | 5 套 GR 水印样式 + 20 个 webp | `files/mode_limit_watermark` | `GrCameraWatermark` |

对应的 6 处常量跟着改：

| 文件 | 常量 |
| --- | --- |
| `cpp/gr_basictone.cpp` | `kGrLmtPath` → `/odm/etc/camera/basictone/lmt_gr` |
| `cpp/master_lut_slots.cpp` | `kRicohLmt` → 同上 |
| `gr/GrEffectTransport.java` | `GR_LMT_DIR` → 同上 |
| `common/Capabilities.java` | `GR_LMT` → 同上 |
| `gr/GrCameraWatermark.java` | `MODE_LIMIT_DIR` → `/odm/etc/camera/basictone/mode_limit_watermark` |
| `pop/PopResources.java` | `POP_ROOT` → `/odm/etc/camera/basictone/pop_port` |

`pop_path_remap.cpp` 里那条「把 `/odm/.../setting_Retro` 重定向到 `files/pop_port/setting_Retro`」
现在没意义了 —— 合并树里的 `setting_Retro` 就是真的那份，重定向目标不存在，自动落回原路径。
代码留着，注释改清楚了。

### 3.3 「拍照出来是绿色」这条线索

BasicTone 是在 **YUV 空间**查表的（`.so` 里那段 GLSL：`lookup3dLut(dstYuv.rgb, ...)`）。
YUV(0,0,0) 换算回 RGB：

```
R = 0 + 1.402 × (0 − 0.5) = −0.70 → 0
G = 0 − 0.344 × (0 − 0.5) − 0.714 × (0 − 0.5) = 0.53
B = 0 + 1.772 × (0 − 0.5) = −0.89 → 0
```

也就是 **RGB(0, 0.53, 0) = 纯绿**。

零表查出来正好是 YUV(0,0,0)，所以「拍出来是绿色」就是「SC 表一张都没读到」的确切特征。
这条和 `grSc=-1` 互相印证。

### 3.4 顺带：staging 改成硬链接

payload 和合并目标在同一个文件系统上，`cp -al` 只加 inode：

```
X10 色调表   41 MB
POP 资源     12 MB
GR 色调表    28 MB
```

以前每个开机都整份重写一遍，下一个开机再 `rm -rf` 掉。现在链接一下就完事，开机更快，
磁盘也不再翻倍。跨文件系统时自动退回 `cp -a`。

### 3.5 新增 `module/boot-completed.sh`

KernelSU 在**开机完成广播之后**再跑一次 `service.sh`，那时已经解锁，所以剩下那些只能放在
app 私有目录里的东西终于有机会到位：

- 大师色彩（`payload/master_color`，269 MB）；
- 相册端的 GR 水印样式（`files/ricoh_gr_styles`）；
- 相册的功能开关（`business_featureSwitch_is_camera_gr_supported`）；
- 相机 IPU SDK 那份 jar（调色板编辑器用）。

如果装的 KSU 版本不支持 `boot-completed` 这个阶段，什么都不坏 —— `service.sh` 里的 `--wait`
兜底还在，而相机侧的数据已经完全不依赖它了。

`service.sh` 里对应的三段（GR 色调表、`pop_port`、相机侧水印样式）删掉了，不再往没人读的
路径写 40 MB。

## 4. 这一版要怎么验

刷完重启，进相机，切 POP。

**日志里先看这一行**（LSPosed 日志，`Capabilities:` 开头）：

```
grSc=9 grScBytes=3145728
```

- `grSc=9` + `grScBytes=3145728` = 9 张 64³ 表都到位了 → GR 滤镜应该都有画面了；
- `grSc=0` = 目录在但没有 SCLut，post-fs-data 的 `payload/gr_lmt64` 缺失；
- `grSc=-1` = 目录还是不存在，post-fs-data 没跑到那一段（看模块目录下的 `bind.log`）；
- `grScBytes=393216` = 又变回 32³ 了。

POP 那边应该看到 `PopPort: POP PLD resources missing` **消失**，也就是：
`pld.json` 在 `/odm/etc/camera/basictone/pop_port/pld_watermark/pld/` 下被找到了。

然后：

1. POP 取景应该是有画面的；
2. POP 按快门，应该有照片进相册；
3. GR 各档滤镜应该都是正常影调，不再全黑、不再出绿；
4. GR 水印：`RicohGrPort: GR watermark style missing` 应该消失（482 里是 322 次）。

## 5. 改动清单

| 文件 | 改动 |
| --- | --- |
| `module/post-fs-data.sh` | 新增 `pop_port/`、`lmt_gr/`、`mode_limit_watermark/` 三段；`stage()` 用硬链接；合并时 extras 也走 `cp -al` |
| `module/boot-completed.sh` | 新增：开机完成后再跑一次 `service.sh` |
| `module/service.sh` | 删掉 GR 色调表 / `pop_port` / 相机侧水印三段（已迁移）；注释更新 |
| `cpp/gr_basictone.cpp` | `kGrLmtPath` → `/odm/etc/camera/basictone/lmt_gr` |
| `cpp/master_lut_slots.cpp` | `kRicohLmt` → 同上 |
| `cpp/pop_path_remap.cpp` | 注释更新（setting_Retro 不再重定向） |
| `gr/GrEffectTransport.java` | `GR_LMT_DIR` → 同上 |
| `gr/GrCameraWatermark.java` | `MODE_LIMIT_DIR` → `/odm/etc/camera/basictone/mode_limit_watermark` |
| `pop/PopResources.java` | `POP_ROOT` → `/odm/etc/camera/basictone/pop_port`；删掉 `staging()`（status.txt 不再存在） |
| `common/Capabilities.java` | `GR_LMT` → 同上（探针读数随之变成 /odm 那份） |
| `module.prop` / `build.gradle.kts` | 483 / `Muse 4.19 GT7Pro` |

## 6. 这版没动的东西

- **特性表**：一个字没改（没有重新生成 protobuf）。
- **哈苏水印**：仍然没碰。
- **GR 的颜色**：本机库没有 CWCM 级，只能做影调，做不到色彩 —— 硬限制。
- 481 的 `PresenterGuard`、479 的 `retro_camera_case` / POP 视图补建 / GL 收敛 / GR 的 `Fe()` 闸、
  480 的快门探针 —— 全部原样保留。
- `xpan_mode`：仍然不加。
