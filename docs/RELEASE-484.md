# OmniCam Muse 4.20 GT7Pro (484)

483 装完之后用户报的是：

> pop 模式还是黑屏，这次切换 GR 理光直接卡死。

而且这次 483 的日志把根因指到了一个完全不同的地方 —— 不是数据内容不对，是**相机进程里根本看不到
模块**。

---

## 1. 483 的日志说了什么

`概要.txt`：

```
注入状态  KSU 模块异常
  [正常] LSPosed：模块已激活
  [异常] KSU 模块：数据未挂载，确认 KSU 模块已启用并重启手机
  [异常] 相机注入：相机里的模块文件被卸载，功能已停用。…常见原因：10007 去广告等模块开启了 Zygisk Next 排除列表
  [正常] 相册注入：已注入，30 个符号
  [正常] 功能装配：16 项已启用，9 项未启用：滤镜目录 / X10 滤镜 / 哈苏超清滤镜 /
        大师滤镜补全 / GR 移植 / POP 复古相机 … 理由统一是「滤镜 / GR 数据未挂载，KSU 模块没生效」
```

`Capabilities` 那一行：

```
data=false lmt=true retro=false retroIni=false grSc=-1 grScBytes=0
profile=true profileModel=RMX5010 profileBytes=47473 plainDecision=false tuningJson=true native=true
```

对比 482 的同一行：

```
data=true  lmt=true retro=true  retroIni=true  grSc=-1 grScBytes=0
```

**数据层在 483 反而全没了。** `retroIni` 从 `true` 掉回 `false`，`data` 从 `true` 掉回 `false`。

还有两个 tag 一行都没打出来：

```
PopPort:     （0 行）
RicohGrPort: （0 行）
```

`PopResources.install()` 和 GR 的原生 hook 在**每一条**返回路径上都会打日志，一行都没有说明它们
根本没被调用。`相机.txt` 里 `filesMounted = false`，功能清单里六项数据层功能全是 `off`。

---

## 2. 为什么会这样

`post-fs-data.sh` 把 `pop_port` / `lmt_gr` / `mode_limit_watermark` 写进
`/odm/etc/camera/basictone` 的 **bind mount**。这个 bind mount 是**模块挂载**，而 KernelSU 的
「卸载模块」和 Zygisk Next 的排除列表（10007 去广告这类模块每次开机都会打开）**只对指定进程移除
模块挂载**。

结果就是：设置页读得到模块，相机读不到。

```
设置页：/odm/etc/camera/basictone/pop_port/...      ✓ 在
相机  ：/odm/etc/camera/basictone/pop_port/...      ✗ 不存在（挂载不在这个命名空间里）
```

这类挂载在相机进程的命名空间里**不存在**，模块脚本里再怎么改路径、再怎么改时序都救不回来。483
把数据从 `service.sh` 搬到 `post-fs-data.sh`、把 staging 改成硬链接，方向没错，但整条路线都建立在
「合并树在相机进程里可见」这个前提上，而这个前提在用户的机器上不成立。

### 2.1 GR 为什么是「卡死」而不是「黑屏」

`gr_basictone.cpp` 的 `loadLut` 无条件把 BasicTone 的 LUT 路径改写成
`/odm/etc/camera/basictone/lmt_gr`：

```cpp
const char *desired = activeProfile == kGr ? kGrLmtPath : ...;
std::strcpy(lmtPath, desired);
```

这个目录在 483 不存在 → BasicTone 加载不返回 → 模式切换永远等在那里 → **卡死**。这是 484 必须
单独修的一条：数据缺失的时候**不许**把路径指向不存在的目录。

### 2.2 `UnmountedGuard` 是死代码

`app/src/main/java/local/jiege/hook/guard/UnmountedGuard.java` 从 483 就写好了（GR / POP 的模式
读改写回 `common`），但**全项目没有任何一处调用它**。所以模式栏里 GR 和 POP 还在，点进去照样
是「stock 模式 + 没有表」→ POP 黑、GR 卡死。484 把它接上了。

---

## 3. 484 做了什么

### 3.1 双源：不再赌挂载

`post-fs-data.sh` 照旧把四个目录组装进 `merged/basictone_extra`；`boot-completed.sh`（开机完成
广播之后，此时已解锁）跑 `service.sh`，把这一层复制到：

```
/data/user/0/com.oplus.camera/files/jiege/basictone/{setting_Retro,lmt_gr,pop_port,mode_limit_watermark}
```

**app 私有目录不是模块挂载**，卸载模块和 Zygisk Next 排除列表都藏不掉它。

如果 `merged/basictone_extra` 不存在（也就是 `post-fs-data.sh` 这次开机没跑到 —— 483 报告的另一
种可能解释），`service.sh` 就**现场从 `payload/` + 本机 `setting/` 树重新组装一遍**，因为「复制一个
不存在的东西」等于什么都没做。

体积约 42 MB，只在 `versionCode` 变化时复制一次。

复制细节：

- **不能用 `cp -a`**：`-a` 会连 SELinux 上下文一起带过去，把合并树的 `vendor_configs_file` 标签
  搬进 app 数据目录，相机反而被拒绝。用 `cp -r` 让新文件继承 `files/jiege` 的上下文。
- `chown -R` 到相机 uid，目录 `0771`、文件 `0644`。
- `chcon -R` 到 `files/jiege` 自己的上下文（带 app 的 SELinux category）。
- 写 `stamp`（模块 `versionCode`），版本没变就不重复复制。

### 3.2 解析逻辑统一到一处

原生：

```cpp
constexpr const char *kGrLmtCandidates[] = {
    "/odm/etc/camera/basictone/lmt_gr",
    "/data/user/0/com.oplus.camera/files/jiege/basictone/lmt_gr",
    "/data/data/com.oplus.camera/files/jiege/basictone/lmt_gr",
};
const char *grLmtDirectory();   // native_hooks.h 导出，命中哪个用哪个
```

`gr_basictone.cpp` 和 `master_lut_slots.cpp`（对比度用的 SC16Lut3/4/5）共用它。

「没找到」不永久缓存：`service.sh` 是开机广播之后才写副本的，相机完全可能比它先起来，所以未命中
时限速重探（最多每 2 秒一次），避免一帧一次 stat。

Java：

```java
Capabilities.grLmtDir()      // lmt_gr
Capabilities.popRoot()       // pop_port
Capabilities.modeLimitDir()  // mode_limit_watermark
Capabilities.retroDir()      // setting_Retro
Capabilities.dataPath(name)  // 以上四个的统一入口
```

`PopResources` / `GrEffectTransport` / `GrCameraWatermark` 全部改成调用它们，不再各自硬编码
`/odm/...`。

`pop_path_remap.cpp` 的 retro 重映射目标也指到新副本路径 —— 那是 retro 引擎真正 `open()` 的那条
路径，光靠合并树的话，挂载一被摘 POP 的 `SimTool.ini` 就读不到。

### 3.3 GR 卡死的兜底

```cpp
const char *desired = activeProfile == kGr ? grLmtDir() : ...;
// No GR tables anywhere: keep the host LUT.
if (desired == nullptr || desired[0] == '\0') desired = kHostLmtPath;
```

表目录找不到就**保持本机 `lmt`**：宁可色彩是普通照片，也不挂住相机。`CombineLutHelper.init` 的
预览重定向同理，目标不存在就不改参数。

### 3.4 功能门槛拆开

以前 `滤镜目录 / X10 滤镜 / 哈苏超清滤镜 / 大师滤镜补全 / GR 移植` 五项共用 `data`（= `meishe_lut`
是否被挂载）一个布尔，模块一被摘就整片关掉 —— 这也是 483 里 GR 和 POP 一起消失的原因。

484：

| 功能 | 判据 | 含义 |
| --- | --- | --- |
| 滤镜目录 / X10 / 哈苏超清 / 大师滤镜补全 | `data` | `meishe_lut` 是否被挂载（这些确实只走合并树） |
| GR 移植 | `grData` = `Capabilities.grLmt()` | 有 `lmt_gr` 目录就行，合并树或副本都算 |
| POP 复古相机 | `retro()` | 走 `dataPath()`，合并树或副本都算 |

`Diagnostics.setFilesMounted()` 改成 `Capabilities.dataRoot() != null`，含义变成「数据可读」而
不是「模块已挂载」。

### 3.5 诊断说人话

`Capabilities.describe()` 多一个字段：

```
root=merged   模块数据走合并树（post-fs-data.sh 挂载）
root=files    模块数据走相机私有目录副本（模块挂载在相机进程里被卸载，service.sh 已补一份）
root=none     相机进程看不到模块数据：合并树和私有目录副本都没有
```

新增 `Capabilities.dataLayerNote()` 把上面三种情况写成中文一行，同时进日志和诊断页。设置页的
「KSU 模块」检查也改成「合并树或副本任一存在即通过」，不再把「副本可用」误报成「KSU 模块异常」。

---

## 4. 验证

刷完重启，进相机后 LSPosed 日志里应该看到：

```
Capabilities: data=… root=files retro=true retroIni=true grSc=9 grScBytes=3145728 … native=true
Capabilities: 模块数据走相机私有目录副本（模块挂载在相机进程里被卸载，service.sh 已补一份）
RicohGrPort: GR tables: /data/user/0/com.oplus.camera/files/jiege/basictone/lmt_gr
PopPort: POP PLD resources missing …   应该消失
RicohGrPort: GR watermark style missing …   应该消失
```

读数对照：

| 读数 | 含义 |
| --- | --- |
| `root=merged` | 合并树可见，走挂载（正常情况） |
| `root=files` | 合并树被摘，走私有目录副本（483 那台机器应该是这个） |
| `root=none` | 两边都没有，数据层功能停用，GR/POP 模式读会被改写成 `common` |
| `grSc=9` `grScBytes=3145728` | 9 张 64³ 表到位 |
| `grSc=0` | 目录在但没 SCLut，`payload/gr_lmt64` 缺失 |
| `grSc=-1` | 目录还是不存在 |

`bind.log`（模块目录下）新增几行：

```
basictone copy from /data/adb/modules/<id>/merged/basictone_extra
basictone copy composed from payload
basictone copy: gr=11 retro=N pop=2 wm=11
```

---

## 5. 没动

- 特性表一个字没改（没重新生成 `camera_unit_feature_config.protobuf`）；
- 哈苏水印仍然没碰（用户说过先不管）；
- GR 的颜色仍然做不到（本机 `libBasicTonePhoto.so` 里没有 CWCM 级）；
- 481 的 `PresenterGuard`、479 的 `retro_camera_case` / POP 视图补建 / GL 收敛 / GR 的 `Fe()`
  闸、480 的快门探针全部原样保留；
- `xpan_mode` 仍然不加；
- 不替换任何系统库、不替换任何 ODM 配置 —— 484 新增的只有一个 app 私有目录里的数据副本。

---

## 6. 关于 `适配GT7Pro功能都可用.zip`

用户同时问「把这个所有功能都移植过来不行吗」。那个包是**另一条技术路线**，和本项目的前提直接
冲突，不能照搬：

| | 本项目 | `适配GT7Pro功能都可用.zip` |
| --- | --- | --- |
| 挂载方式 | 只 bind-mount **数据层**（LUT / basictone / 水印素材） | `mountify` standalone **overlayfs 覆盖整个分区** |
| ODM 配置 | 一个字节都不动，改动走 profile + 进程内路径重定向 | `system/odm/etc/camera/` **328 个文件 76 MB 整包覆盖**，含 `camera_unit_config`、`camera_unit_feature_config.protobuf`、`filters_lut/*`、`cfr_bin/*` |
| 系统库 | 不替换 | 覆盖 `system/product/framework/com.oplus.camera.unit.sdk.jar` 与 `…unit.sdk.adapter.jar` |
| 应用 | 自己的 `local.jiege.camera`（LSPosed） | 同名包 `local.jiege.camera` 的 **`Muse 4.4.4`**（versionCode 444），会和本项目**互相覆盖** |
| 依赖 | KernelSU 的 `post-fs-data.sh` / `service.sh` / `boot-completed.sh` | `busybox`、`getfattr`、`susfs`、`trusted.overlay.opaque`，与 KernelSU 版本强绑定 |
| 风险 | 挂载没了 = 功能关掉，相机还能用 | 覆盖错了 = 相机起不来（SoC 不同、结构体布局不同、配置加密不同） |

它覆盖的 `filters_lut` 和 `cfr_bin` 确实比本项目多（本项目一直没动这两块）。如果要用，只能把它当
**素材来源**，把里面 GT7Pro 用得到的 LUT 文件按本项目的方式放进 `payload/`，而不是整包 overlayfs
覆盖上去。
