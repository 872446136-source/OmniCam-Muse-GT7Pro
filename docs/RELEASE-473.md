> 真我 GT7 Pro（RMX5010 / ColorOS 17.0.0.111 / 相机 17.006.112 / 相册 17.10.37）适配版，单包无 Lite。**不替换任何系统库、不替换任何 ODM 配置。**

## 472 的探针一个都没装上，这是我的错

诊断页那行「相机注入：3 条错误」就是它。日志：

```
ModeProbe: name lookup hook failed java.lang.ClassNotFoundException: com.oplus.camera.pq.h
ModeProbe: row lookup hook failed java.lang.ClassNotFoundException: com.oplus.camera.dq.e
ModeProbe: seeding hook failed java.lang.ClassNotFoundException: com.oplus.camera.dq.e
```

我从反编译索引里看到的是 `Ldq/e;`、`Lpq/h;` —— **这就是默认包的类**，dex 里没有 `com/oplus/camera/` 前缀。我在代码里按带包名的写法去取，当然找不到。10045 个类是这么命名的，`CameraConfig` 那种带包名的反而是少数。

473 两个写法都试（先 `dq.e`，再 `com.oplus.camera.dq.e`），并且**整表 dump 不再依赖任何钩子**：改成从 `Application.onCreate` 起 8 秒、20 秒各读一次，直接用只读连接打开
`/data/user/0/com.oplus.camera/databases/mode_data.db`。钩子全挂也能拿到表。

---

## 为什么 471 / 472 的日志里一行相机日志都没有

导出脚本抓的是 `logcat -d -t 20000`，这台机器两个缓冲区加起来大约三十秒。**相机卡死后就不再输出日志**，等你导出的时候，解释卡死的那几行早被挤掉了。所以 471（1.5 MB）和 472（1.9 MB）都只剩系统日志，`OCAM` 一行没有 —— 我一直在盲猜。

473 把切换这条链上的四个 tag 加进日志镜像（镜像会把行抄进模块日志，而模块日志一定会被导出）：

```
OCAM_ModeSwitcher       checkToSwitchMode, the device is not support the mode:…
OCAM_DataBaseOperator   insertHeadline / insertModePanel / updatePosition / query, queryModeId
OCAM_CameraControlUI    Camera Mode Change Start, next mode: …
OCAM_HeadlineHelper     模式名与资源的对应
```

下次导出的 `LSPosed.txt` 里会直接有 `CameraLog OCAM_ModeSwitcher …` 这样的行。

---

## 开关层多回答了一个入口

`dq.e.i` / `dq.e.j`（播种模式表的两个方法）读开关有两条路：

- `CameraConfig.getConfigBooleanValue(String)` —— 之前只回答了这条
- `CameraConfig.d(String, 默认值, 缓存)` —— 之前**没管**

473 两条都回答。如果某个模式的行是走第二条门读出来的，之前就一直是固件原值。

---

## 大师模式

472 已经确认 `professional.mode.support` 被回答成 true、面板那条 move 也确实被摘掉了：

```
ModeGates: com.oplus.feature.professional.mode.support answered true
ModePlacement: panel move served without professional: [night, 0, highDefinition, 2]
```

但你说还是没看到入口。那就还有一道门：`com.oplus.support.professional.mode.in.headline`（本机是 0），写在**模式栏那一段**的播种逻辑里。473 把它也回答成 true。

---

## 哈苏水印

开关确认回答了：

```
ModeGates: com.oplus.camera.support.custom.hasselblad.watermark answered true
```

但 472 的日志里，**第二个相机进程（pid 17412）压根没有问过这个键** —— 只有第一个进程（11000）问过一次。也就是说水印那一栏是按别的数据源搭出来的，不是靠这个开关。

这条我先不改，等 473 的相机日志。如果 473 的日志里仍然没有 `hasselblad` 相关的查询，那基本可以确定：**GT7 Pro 这个机型的相机 APK 里没有哈苏水印这一项**（真我没有哈苏联名），光靠开关造不出一个不存在的条目，得往水印列表里注入条目 —— 那是另一件事，我单独做。

---

## 装完请这样做

1. 打开相机，**先不要切模式**，等 10 秒（探针要读表）
2. 切一次 **GR**，卡住了就退出来
3. 切一次 **POP**，同样
4. 看模式栏有没有 **大师模式**
5. 相机设置 → 水印，看有没有哈苏
6. 导出日志

日志里这次应该出现：

```
ModeProbe: dq.e found
ModeProbe: pq.h found
ModeProbe: ja.a found
ModeProbe: name lookup hooked
ModeProbe: row lookup hooked
ModeProbe: table +8000ms: N rows, headline: …
ModeProbe: row +8000ms gr id=39 position_headline rank=4 …
ModeProbe: f(gr) -> 39
ModeProbe: q(39) -> ModeInfo: [...] (2 ms)
CameraLog OCAM_ModeSwitcher checkToSwitchMode, …
```

有这几行，GR / POP 卡在哪就能一次定死：是行没写进去（`q` 返回 `null`，探针会顺手补一行）、还是 id 对不上（`f` 的返回值）、还是行在但相机切到一半卡在别处（`CameraLog` 那几行会指出卡在哪个阶段）。

如果相机又出别的异常：`touch /data/adb/omnicam_gates_off`，重启。

## 回退

```bash
adb shell su -c "touch /data/adb/omnicam_probe_off"       # 关探针
adb shell su -c "touch /data/adb/omnicam_placement_off"   # 大师模式回面板
adb shell su -c "touch /data/adb/omnicam_gates_off"       # 关开关层
adb shell su -c "touch /data/adb/omnicam_native_off"      # 关原生层
adb shell su -c "touch /data/adb/omnicam_off"             # 全关
adb shell su -c "rm -f /data/adb/omnicam_off /data/adb/omnicam_native_off /data/adb/omnicam_gates_off /data/adb/omnicam_probe_off /data/adb/omnicam_placement_off"
```

改完重启。不替换任何系统文件，关掉之后相机必然是出厂状态。

## 安装

1. KernelSU / APatch 刷 `OmniCam-Muse-GT7Pro-KSU.zip`
2. 装 `OmniCam-Muse-GT7Pro.apk`，LSPosed 里启用并勾选 `com.oplus.camera` 与 `com.coloros.gallery3d`
3. 重启

包体 226 MB，其中 `master_color/looks` 是大师色彩成片查找表（未压缩 253 MB）。不需要可删 `payload/master_color` 重打包，降到 ~30 MB。
