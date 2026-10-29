# OmniCam Muse 4.21 GT7Pro (485)

**数据层从「脚本拼树再 bind」改成「模块文件层」，交给 magic mount / meta-magic_mount-rs 挂。**

POP 黑屏、GR 卡死、KSU 模块异常，三个问题同一个根：`/odm/etc/camera` 下那些目录从来没被挂上过。
这一版把它们变成模块里真实存在的文件，在任何 app 启动之前就挂好。

---

## 1. 上一版的判断错在哪

484 的结论是「KernelSU 的卸载模块 / Zygisk Next 排除列表把挂载按 app 摘掉了」。日志不支持这个结论：

```
$ grep -c Unmounted LSPosed.txt
280
$ grep Unmounted LSPosed.txt | grep -c camera
0
```

280 行 `Unmounted (...)` 全是字体模块和温控模块的文件（`/system/fonts/*.ttf`、
`/odm/etc/temperature_profile/*.xml`），**没有一条是相机进程、也没有一条在 `/odm/etc/camera` 下**。
排除列表根本没碰相机。

真实情况简单得多：那些挂载压根不存在。设置页 stat 出来的「数据未挂载也没有副本」和相机的
`data=false root=none` 说的是同一件事 —— 不是被摘掉，是从来没挂上。

## 2. 为什么从来没挂上

**证据一：`service.sh` 和 `boot-completed.sh` 都在解锁之前跑的。**

内核日志（`LSPosed.txt` 前半段其实是 ksud 的日志）：

```
23:34:39 ksud::module: exec /data/adb/modules/ricoh_pop_x9u/service.sh
23:34:48 ksud::module: exec /data/adb/modules/ricoh_pop_x9u/boot-completed.sh
23:34:48 ksud::init_event: on_boot_completed triggered!
23:35:19 相机进程 19106 启动
```

这台机器 `ro.crypto.type=file`，`/data/user/0/com.oplus.camera` 要到解锁那一刻才出现。
`service.sh` 里 `wait_for "$APP" 60` 等 60 秒，等到的还是「没解锁」，于是把活交给一个
`( sh & )` 的脱离进程 —— 那个进程随父 shell 一起被 ksud 收走。所以 484 那 42 MB 私有目录副本
一次都没写出来，`root=none` 里的 `none` 就是它。

**证据二：元模块早就装好了，而且工作正常。**

```
23:34:39 ksud::metamodule: Found metamodule in modules directory: /data/adb/modules/magic_mount_rs
```

`magic_mount_rs` 在负责整台机器的挂载，字体模块、温控模块都挂上了。只有本项目什么都挂不上 ——
因为本项目模块里**根本没有 `system/` 目录**：数据全在 `payload/` 下，靠 `post-fs-data.sh` 自己
bind。元模块扫不到可挂的东西，就什么也没挂。

## 3. 这一版怎么改

模块 zip 里多了这棵树，由 magic mount 在任何 app 启动之前挂到 `/odm`：

```
system/odm/etc/camera/meishe_lut/                      滤镜 LUT
system/odm/etc/camera/basictone/lmt_gr/                LMTLut0/1 + 九个 64³ SCLut
system/odm/etc/camera/basictone/pop_port/              pld_watermark + apk_resources
system/odm/etc/camera/basictone/mode_limit_watermark/  五套 gr_style_N + webp
system/odm/etc/camera/basictone/setting_Retro/         本机 setting/ 树（SimTool.ini、Ditherout、FromXml）
system/odm/etc/camera/basictone/lmt, palette, setting  X10 色调曲线与调色板
```

语义是 **overlay，不是覆盖**：层里有的文件盖住同名系统文件，层里没有的系统文件原样透出来。
这正是原来那段「先 `cp -a` 一份系统树、再 `cp -al` 把 payload 盖上去」在做的事，只是不再需要在
`/data` 里拼一棵 80 MB 的树，也不用对几万个文件跑 `chcon`。

推荐配元模块 [meta-magic_mount-rs](https://github.com/Tools-cx-app/meta-magic_mount-rs)
（你机器上装的就是它）；KernelSU 自带的 magic mount 走同一种机制，一样能用。

## 4. 脚本退回自己该做的事

| 文件 | 现在只负责 |
|---|---|
| `post-fs-data.sh` | Pro 视频 ISZ 补丁（必须用本机自己的 `CameraHWConfiguration.config` 逐行改）+ 把挂载结果写进 `bind.log` |
| `service.sh` | 大师色彩表、导入的滤镜、相册水印样式、相册功能开关、profile；顺手清理 484 留下的副本 |
| `customize.sh` | 装 APK、清 profile 和 484 副本的残留 |
| `boot-completed.sh` | 开机广播后再跑一次 `service.sh`（相册那部分需要解锁） |

`bind.log` 现在是报 bug 第一个要看的东西：

```
layer lmt_gr: present, 11 entries
layer pop_port: present, 2 entries
lmt_gr SCLut0=3145728 bytes
metamodule magic_mount_rs: installed, config=mountsource = "KSU" ...
```

## 5. XPAN 为什么一直不显示

模式表里它一直在，从 477 到 484 每一份日志都是同一行：

```
ModeProbe: row +20000ms xpan id=28 position_mode_panel rank=13 front=false edit=false
```

面板里 0..12 是 night…tiltShift，`xpan` 是 13 —— **排在最后一格，面板画不到那里**。
所以「表里有、屏幕上没有」。

485 用大师模式同一个机制挪它：`com.oplus.feature.move.mode.to.panel` 返回的是
`(模式, 排名)` 的搬移表，把 `xpan` 改成 rank=3、原本 3..12 的各让一格。顺序变了，一个都不少。
`touch /data/adb/omnicam_placement_off` 可关。

## 6. 怎么验证

刷完重启，进相机后看设置页诊断，应该是：

```
Capabilities: data=true root=merged lmt=true retro=true retroIni=true
              grSc=9 grScBytes=3145728 profile=true native=true
RicohGrPort: GR tables: /odm/etc/camera/basictone/lmt_gr
```

- `data=true root=merged` → 层挂上了，这是正常状态。
- `root=none` → 层没挂上，`bind.log` 里四行 `layer ...: ABSENT` 会告诉你是哪几个。
- 模式面板里应该能看到 XPAN 了（在 tiltShift 前面几格）。

## 7. 没动的

特性表、profile、GR 卡死兜底（表目录找不到就保持本机 `lmt`）、`UnmountedGuard`、481 的
`PresenterGuard`、479 的 `retro_camera_case` / POP 视图补建 / GL 收敛、480 的快门探针，全部原样。
哈苏水印仍没碰。仍然不替换任何系统库、不替换任何 ODM 配置。

逃生口：`omnicam_grsc_off`、`omnicam_featcfg_off`、`omnicam_presenterguard_off`、
`omnicam_grshutter_off`、`omnicam_poprepair_off`、`omnicam_popguard_off`、
`omnicam_shutterprobe_off`、`omnicam_liveconfig_off`、`omnicam_glguard_off`、
`omnicam_logall_off`、`omnicam_probe_off`、`omnicam_placement_off`、`omnicam_gates_off`、
`omnicam_native_off`、`omnicam_off`。均需重启。
