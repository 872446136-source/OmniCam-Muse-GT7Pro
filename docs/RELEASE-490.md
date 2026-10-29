# OmniCam Muse 4.24 GT7Pro (490)

**新增两份「全部正常没任何 bug」的参考包，第一件事就是把 489 的一个判断推翻了。**

---

## 1. XPAN 出片尺寸：本机的 `8000x3636` 是 realme 的占位值

原作者本人的模块（`OmniCam-Muse-4.4.4-KSU PMA110.zip`）和 PLK110 的稳定版（`OmniCam-Aura-PLK110`）
都把 `oplus_camera_config` 以**明文 JSON** 整份替换，两份的三个尺寸键**完全一致**：

```
com.oplus.xpan.main.picturesize = 7872x2912
com.oplus.xpan.wide.picturesize = 7872x2912
com.oplus.xpan.tele.picturesize = 7872x2912
com.oplus.xpan.crop.region.main.portrait  = 360,64,2912,7872
com.oplus.xpan.crop.region.main.landscape = 64,360,7872,2912
com.oplus.xpan.mode.version = 3
```

而本机（`oplus_camera_config` 解密后）三个尺寸键都是 **`8000x3636`**。

489 看到 `360+2912+360 = 3632`、`64+7872+64 = 8000`，判断 `8000x3636` 是「缓冲区尺寸」，
于是把尺寸键保留成「空才填」——**这是错的**。

裁切区 `2912x7872` 才是成品尺寸，`picturesize` 就是**输出**尺寸。输出 `8000x3636` 装不下
`2912x7872` 的裁切区，这正是那张「竖长条画布 + 未裁的 4:3 挤在左上角、下面全白」的成因。

490 把三个尺寸键改成**直接答 `7872x2912`**，和裁切区一样处理。

> 一句话总结三版：487 修了「XPAN 进得去」，488 修了「裁切区有值」，489 修了「裁切区真的挂上」，
> 490 修了「输出尺寸和裁切区对得上」。四个值必须同时正确，缺一个就是上面那张图。

---

## 2. 从这两份包里确认的其他事实（这次不改）

**① 原作者是整包替换。** `system/odm/lib64/` 下 5 个 so（`libAlgoInterface` 79 MB、
`libBasicTonePhoto`、`X9`、`X10`、`libmsnativefilter`），加 `post-fs-data.sh` 里
`mount --bind` 六个 config，加 `meishe_lut` / `basictone` 目录合并。

本模块仍然不替换算法栈和 ODM 配置，只走 profile + hook —— 这条不变。

> `libmsnativefilter.so` 两份都是 10,957,976 B，和我们从 X8U 拿的那份同尺寸，说明这一层已经对齐。

**② 原件用的是 `override_config_data.xml`**（相机 shared_prefs）写「覆盖键」，而不是改 ODM 配置：

```
com.oplus.gr.mode.support = 1
com.oplus.camera.mode.data.db.version = 102
com.oplus.available.gr.mode.zoomvalues = 0.6(14),1(23),2(47),3(70),6(139),10(230)
com.oplus.gr.mode.marked.zoomvalues   = 1.2173913(28),1.7391304(40)
com.oplus.camera.capture.hdr.cap.mode.value = common,portrait,professional,night,highPixel,xpan,underWater,telephoto,gr,retroCamera
com.oplus.camera.preview.hdr.cap.mode.value = common,professional,night,highPixel,xpan,underWater,telephoto,gr
```

本机解密配置里这几个键**都不存在**（`camera.mode.data.db.version` 是 5，不是 102），说明它们本来
就是「覆盖键」。我们用 `ModeGates` 在进程内按 key 回答，效果等价。

**③ metamodule 的 takeover 会静默禁用模块脚本。** Aura 的 README 明写
「meta-nomount(NoMount) 的 takeover 对模块挂载脚本的静默禁用」，作者为此把 `mount` 改成变量形式规避。
这和 489 的发现完全对上：本机 488/489 的 `模块日志.txt` 里没有 `bind.log`，
`post-fs-data.sh` / `service.sh` 并没有跑。所以**凡必须生效的东西不能只放脚本里**。

**④ 哈苏水印：两份包都没有任何哈苏素材。** `watermark_master_styles`（gr_style_1..5）、
`watermark_drawables`（realme_gr_logo 等）、`pld_watermark`、`setting_Retro` 和我们**完全一致**。
再次说明哈苏水印不是加素材能解决的，是品牌/区域门禁。

**⑤ `master.video.feature.list`。** Aura 把它从 5 项扩到 15 项、index 14 = 1 来开「片门全开」。
我们这份是 14 项、后两位 0；本机没有证据需要这一项，这次不动。

---

## 3. 装完请这样验

进 XPAN 拍一张，日志里应该看到：

```
ModeGates: com.oplus.xpan.main.picturesize was 8000x3636, answered 7872x2912
ModeGates: com.oplus.xpan.crop.region.main.portrait came from the config: 360,64,2912,7872
CameraLog: XPanPresenter onPrepareCaptureRequest, cropRegion:[360, 64, 2912, 7872], XPAN add height:…
```

成片应该是 **2912x7872**（竖，约 24:65），内容铺满整条，不再是左上角一小块 + 大片白。

如果尺寸那行显示的是 `came from the config: 7872x2912`，也是对的 —— 说明覆盖生效了。

---

## 4. 没动的东西

算法栈（`libAlgoInterface` / `libBasicTonePhoto*`）和 ODM 配置仍然一个字节都不替换；唯一替换的系统
文件还是 `/odm/lib64/libmsnativefilter.so`（Meishe 渲染器）。
