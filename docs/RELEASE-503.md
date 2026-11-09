> 真我 GT7 Pro（RMX5010 / ColorOS 17.0.0.112 / 相机 7.013.51 / 相册 17.10.41）适配版，单包无 Lite。

这一版修的是两个「模式在、效果不在」的问题：相机水印页的哈苏分区是空的，XPAN 模式拍出来没有
XPAN 画幅。第一个已经修完并在真机上验过；第二个只走到了半路，下面把已证实的部分和还没打通的
部分分开写。

## 一、相机水印页的哈苏分区

### 那条空白不是相机画的

设置 → 水印 → 照片水印，`HASSELBLAD` 标题下面什么都没有。这个页面不是 `com.oplus.camera` 的
界面，是 `com.coloros.gallery3d` 把 `PhotoEditorActivity` 叠在相机上——控件 id 属于相册，从设备
上 `uiautomator dump` 出来的层级就是这样：

```
camera_rm_chip_group          通用 / 理光 GR      两个 chip，本来就两个
camera_hassel_style_layout    只有 camera_style_item_title_image——那张字标
camera_frame_style_layout     个性画框 / 灵感取色 / 大师印记 / 经典相机   各三项
```

### 谁在决定这一栏有没有内容

`WatermarkCameraSection`（相册 `classes10.dex`，类名未混淆）里，`K0()` 装配哈苏那一栏，通过
`H0(section, type, show, flags)` 建适配器。`show` 这个参数来自 `T0()`：

```
T0()  =  vvm("WatermarkMasterVM/ShowHasselXpanStyle")  ?:  Pair(FALSE, TRUE)
show  =  T0().first && pageIsWide && screenIsNotSmall
```

真机上这个 view model 从没答过值，缺省就是 `Pair(FALSE, TRUE)`，于是 `H0` 收到 `show = false`，
查询给回来的列表是空的——分区只剩字标。同一段逻辑也管着 XPAN 那一组
（`camera_hassel_xpan_style_layout`）。

`watermark/HasselCameraPage.java` 只做一件事：`T0()` 返回 `Pair(FALSE, *)` 时改答
`Pair(TRUE, TRUE)`。样式条目仍然由相册自己的 lambda 和它自己的资产渲染
（`assets/watermark_master_styles/hassel_style_*.json` 在已安装的 APK 里），一个都不伪造；数据
本来就是空的话，分区还是空的，`H0` 那侧的只读监听会把条目数打进日志，下一次报告就能分辨是
「开关没放开」还是「后面没有数据」。

### 实测

装 503、重启相册与相机，进设置 → 水印 → 照片水印：

```
camera_hassel_style_layout                       [0,784][1264,1967]
  camera_hassel_xpan_style_layout                [56,973][1208,1897]
    'XPAN·宽幕人生'                               [384,973][880,1025]
    camera_hassel_xpan_style_list                [56,1081][1208,1740]
    camera_hassel_xpan_desc_tv
      'XPAN专属宽画幅，记录生活如电影篇章'
```

点进去是编辑预览：XPAN 白框、HASSELBLAD 角标、影像参数 `67mm f/2.6 1/200s ISO500`，点「应用」
后卡片带「使用中」。在 XPAN 模式下拍的那张，白框和角标确实写进了成片：

```
IMG20261109183554.jpg   4012x10376   白框 + HASSELBLAD 角标 + 框里一张 4:3 的竖图
```

**水印这一项到此为止是好的。** 可它也把第二个问题照得很清楚：框装对了，框里的画面还是普通竖幅。

## 二、XPAN 画幅：走到了哪一步

### 一次拍摄要过三道尺寸

```
app 请求      8000x3636        ← oplus_camera_config: com.oplus.xpan.main.picturesize
HAL 交付      4096x3072        ← CameraHWConfiguration.config: 80ac,-1_35_3,8000X3636X4096X3072
裁剪区域      64,360,7872,2912 ← oplus_camera_config: crop.region.main.landscape
```

这三条在真机上依次确认过（`/odm/etc/camera/config/oplus_camera_config` 是 AES 封套，用
`libOplusSecurity.so` 里那段 key 解开后读的）：

- `add surface wrapper: appSurfaceSize: 8000x3636, halSurfaceSize: 4096x3072`
- `mXpanCropSize: 1368x3696, mXpanCropCoordinate: null`

裁剪坐标算不出来，是因为它按 8000×3636 的画布算，而 HAL 只交 4096×3072——区域整个越界，于是
`getXpanCropRegion` 这一支直接给了 null。没有区域就没有裁剪，成片就是传感器那张 4:3。

`8000x3636` 也不是随手写的数：`64 + 7872 + 64 = 8000`，`360 + 2912 + 360 = 3632`（表中写
3636，两侧各 362）。它是**裁剪区的画布**，不是输出尺寸。表里把画布当成 picture size 交出去，
等于告诉流水线「整张就是成片」，自然没有东西可裁。

### 两次失败，一次成功

改 `CameraHWConfiguration.config` 做对照实验（`/odm` 只读，用 bind mount 覆盖，并先把挂载源的
SELinux 标签改成 `u:object_r:vendor_configs_file:s0`——不改标签 HAL 读不到，相机直接 NPE 闪退、
XPAN 取景黑屏）。HAL 跑在独立进程 `vendor.qti.camera.provider-service_64` 里，改完要
`setprop ctl.restart vendor.camera-provider` 再 `ctl.restart cameraserver`，只重启 cameraserver
不会让它重读表。

| 改法 | 结果 |
|---|---|
| 9 处 `8000X3636X4096X3072` → `8000X3636X8192X3724` | 被忽略，仍 3072×4096 |
| 只改 `80ac,-1_35_3` 一条 → `8192X3724` | 被忽略 |
| 9 处 → `8000X3636X8000X3636`（交付尺寸与画布对齐） | **生效** |

第三行之后：

```
XpanMode: getSurfaceSize, get customSurfaceSize: [Pair{8000x3636 8000x3636}]
SurfacePool: createImageReader ... appSurfaceSize: 8000x3636, halSurfaceSize: 8000x3636
```

关掉水印再拍，`IMG20261109184434.jpg` 出来 **3636×8000**，画面是宽幅，不再是 4:3。方向对了，但
还没到 XPAN：标准输出是 **7872×2912**（65:24），现在这张是 2.2:1，两侧那 64 / 362 的边距还在画面
里。这一步要的是裁剪，而裁剪区域仍然是 null。

### 所以 503 里 XPAN 这一半是什么状态

- `common/OplusConfig.java`：在相机进程里把 `/odm/etc/camera/config/oplus_camera_config` 读出来、
  用本机 `libOplusSecurity.so` 的 key 解开、把 XPAN 的四条 vendor tag 写进去
  （`main/wide/tele.picturesize` → `7872x2912`、`feature.xpan.mode.support` → `1`，另补两条本机
  表里没有的 `xpan.all.camera.support` / `xpan.mode.version`），再按原封套加密封回去，交给
  `ConfigProfile` 的 staged 目录由 native 重定向供相机读取。**不往 `/odm` 写文件、不 bind mount**，
  这也是 503 之前试过直接投放明文配置后相机变砖的教训（见 `tools/patch_xpan_config.py` 抬头）。
- `modes/ModeGates.java`：把三条 XPAN 尺寸键也按字符串答一遍，并把 `int[]` 读取器和 `Size` 读取器
  的单参数形式一起挂上——7.013.51 的裁剪路径走的是 `(String) -> int[]`，之前只挂了
  `(String, int[])`，等于没挂到。
- `xpan/XpanCropRegion.java`（新增）：直接答 `getXpanLandScapeCropRegion` /
  `getXpanPortraitCropRegion`，主摄给 `64,360,7872,2912`，竖拍给 `360,64,2912,7872`。

**这三处都编译通过，但 `XpanCropRegion` 还没有在真机上验过**，HAL 那张表也还是手工 bind mount 的
实验状态——模块本身没有去改它。把 HAL 交付尺寸这件事做进模块（以及确认裁剪坐标在画布对齐后能自
己算出来，就不用再 hook）是下一步。

## 三、还没有解决的

- **XPAN 成片仍不是 7872×2912**。见上：HAL 表未模块化，裁剪区域未实测。
- 7.013.51 上 14 个符号仍然失配（快门声、POP 快门重绘、物理柔光成片、哈苏超清入口、滤镜面板等），
  逐个被接住、只降级不崩，清单见 `docs/RELEASE-499.md`。

## 版本

Muse 4.31 GT7Pro（503）。
