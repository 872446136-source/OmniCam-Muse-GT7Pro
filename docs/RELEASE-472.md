> 真我 GT7 Pro（RMX5010 / ColorOS 17.0.0.111 / 相机 17.006.112 / 相册 17.10.37）适配版，单包无 Lite。**不替换任何系统库、不替换任何 ODM 配置。**

## 471 的好消息，和还没好的地方

471 的开关修复**确认生效**了。日志里三行都在：

```
ModeGates: com.oplus.feature.retro.camera.support answered true
ModeGates: com.oplus.gr.mode.support answered true
ModeGates: com.oplus.camera.support.custom.hasselblad.watermark answered true
```

`ArrayIndexOutOfBounds` 一次都没有了，模式表也重新播种成功 —— **POP 模式入口出来了，相机里的哈苏水印也回来了。**

还剩下两件事：

1. **GR / POP 切过去卡死**（GR 和上一版表现一样）
2. **大师模式没有入口**

---

## 一、GR / POP 为什么卡死

这次日志被截断了（1.5 MB，470 那次是 16.7 MB），相机进程 16:58:49 之后就不再输出任何日志 —— 主线程卡住了，所以没有可用的堆栈。我改用反编译索引把切换那条路径还原出来，先看代码是怎么判"支持"的。

`vm/l.i(...)`（ModeSwitcher）打那行 `checkToSwitchMode` 之后，只做三件事：

```java
int id = pq.h.f(modeName);                    // 1. 模式名 → 模式 id
AtomicReference r = dq.e.g().q(id);           // 2. 按 id 查 mode_info，阻塞等结果
if (!modeName.equals(((ja.a) r.get()).…)) {   // 3. 查回来的行名和请求的名字对不上
    Log.e("ModeSwitcher", "checkToSwitchMode, the device is not support the mode:" + modeName);
    return false;
}
```

`dq.e.q(int)` 本身是：`ConditionVariable.close()` → 把查询丢给工作线程 → `block(timeout)` → 返回结果。工作线程 `dq.d.run()` 执行的是

```sql
select * from mode_info where mode_id = ?
```

查不到行就 `ref` 一直是 null，`block` 等满超时才返回。

470 的日志里这一段是 **3 毫秒** 就返回的（18.437 → 18.440），所以不是等超时，是**查询结果为空**。而 `CameraControlUI` 在此之前已经打了 `Camera Mode Change Start, next mode: gr`，模式指示器已经动了 —— 切换被中途否掉，模式栏就停在半途，看起来就是"卡住滑不了"。**卡死和"不支持"是同一件事**，不是两个 bug。

所以现在的问题收窄成一句话：**`q(39)` 为什么查不到那一行**。可能是行没写进去，可能是 `pq.h.f("gr")` 返回的不是 39，也可能行在但字段对不上。这三种从外面看一模一样，我不想再猜了。

### 472 的做法：装个只读探针

新增 `modes/ModeProbe.java`，**不改任何东西**，只记录三件事：

| 日志 | 含义 |
|---|---|
| `ModeProbe: f(gr) -> 39` | 相机自己把模式名转成了哪个 id |
| `ModeProbe: q(39) -> ModeInfo: [mName: gr, …] (2 ms)` | 查回来的那一行，或 `null`，以及耗时 |
| `ModeProbe: row gr id=39 position_headline rank=4 …` | 播种完 2 秒后，整张 `mode_info` 表 |

第三项是自己用只读方式打开 `/data/user/0/com.oplus.camera/databases/mode_data.db` 读的，不经过相机代码，所以表里到底有什么一目了然。

下次切一次 GR、切一次 POP，把日志发我，这三个就能对上号：

- `f(gr)` 不是 39 → 名字映射就是错的，改映射
- `f(gr)=39` 但 `q(39)` 是 null → 行没写进去，改播种
- 两个都对但切换还是被否 → 行在，问题在 `vm/l` 后面的分支，继续往下追

探针不影响相机，真要关掉：`touch /data/adb/omnicam_probe_off`。

---

## 二、大师模式为什么没有入口

先说结论：**大师模式在相机里就是 `professional` 这个模式**（相机内部叫 `ProfessionalMode`，模块里做大师滤镜/大师色彩的那几处一直用的也是这个名字）。

查本机配置（已解密核对），它缺了两样东西：

| key | 本机取值 | 后果 |
|---|---|---|
| `com.oplus.feature.professional.mode.support` | **不存在** | 播种时直接跳过，不给大师模式写行 |
| `com.oplus.support.professional.mode.in.headline` | `0` | 不会主动进模式栏 |
| `com.oplus.feature.move.mode.to.headline` | `professional,0` | 想把它挪进模式栏 |
| `com.oplus.feature.move.mode.to.panel` | `night,0,professional,1,highDefinition,2` | 又把它挪回面板 |

470 的日志把这场拉锯完整记下来了：

```
updatePosition, modeId: 4, toPosition: position_headline     ← headline 那条生效，进模式栏
moveModePosition, modeName: professional, needToMoveMode: true
updatePosition, modeId: 5, toPosition: position_mode_panel   ← 把 night 挪出去腾位置
moveModePosition, modeName: night, needToMoveMode: true
updatePosition, modeId: 4, toPosition: position_mode_panel   ← panel 那条又把它挪回来
checkIfNeedToUpdateMode, moveToPanelList: [night, 0, professional, 1, highDefinition, 2]
```

两条配置互相打架，panel 那条后执行，**所以大师模式最后躺在"更多"面板里，模式栏上看不到**。

472 两手一起改：

1. `ModeGates` 增加回答 `com.oplus.feature.professional.mode.support` → true，播种时就有它自己的行（不再只靠 move 配置兜）；
2. 新增 `modes/ModePlacement.java`，只对 `com.oplus.feature.move.mode.to.panel` 这一个 key 生效：返回列表时把 `professional` 和它后面那个 rank 一起摘掉。这样 panel 那条不再把它挪走，headline 那条留下来 —— **大师模式回到模式栏**，night 被挤进面板（参考机型本来也是这个布局）。

只动这一个 key、只在本进程内、只在那次读取时。要关掉：`touch /data/adb/omnicam_placement_off`。

---

## 装完先做这几步

```bash
adb shell su -c "logcat -d | grep -E 'ModeGates|ModeData|ModeProbe|ModePlacement'"
```

期望：

```
ModeData: mode database dropped, the camera will re-seed it
ModeGates: flag getter hooked, 6 flags answered true
ModeGates: com.oplus.feature.professional.mode.support answered true
ModePlacement: panel move hooked, 大师模式 goes to the bar
ModeProbe: name lookup hooked
ModeProbe: row lookup hooked
ModeProbe: table: N rows
```

然后：

1. 看模式栏里有没有 **大师模式**；
2. 切一次 **GR**（会卡，我知道），退出来；
3. 切一次 **POP**（也会卡），退出来；
4. 导出日志发我。

**这次不指望 GR / POP 修好** —— 472 的主要目的是把那三行探针日志拿到手。有了它，下一版就能一次改对，不用再来回试。

如果相机又出现别的异常，先关掉整层开关：`touch /data/adb/omnicam_gates_off`，重启，相机照常能用。

## 回退

```bash
# 关探针（只影响日志）
adb shell su -c "touch /data/adb/omnicam_probe_off"
# 关大师模式搬移（大师模式回到“更多”面板）
adb shell su -c "touch /data/adb/omnicam_placement_off"
# 关开关层（GR / POP / 大师模式 / 哈苏水印没有，其它照常）
adb shell su -c "touch /data/adb/omnicam_gates_off"
# 关原生层（profile 重定向随之失效，相机回到原样，Java 功能照常）
adb shell su -c "touch /data/adb/omnicam_native_off"
# 整体关掉
adb shell su -c "touch /data/adb/omnicam_off"
# 全部恢复
adb shell su -c "rm -f /data/adb/omnicam_off /data/adb/omnicam_native_off /data/adb/omnicam_gates_off /data/adb/omnicam_probe_off /data/adb/omnicam_placement_off"
```

改完重启。因为不替换任何系统文件，关掉之后相机必然是出厂状态。

## 安装

1. KernelSU / APatch 刷 `OmniCam-Muse-GT7Pro-KSU.zip`
2. 装 `OmniCam-Muse-GT7Pro.apk`，LSPosed 里启用并勾选 `com.oplus.camera` 与 `com.coloros.gallery3d`
3. 重启

包体 226 MB，其中 `master_color/looks` 是大师色彩成片查找表（未压缩 253 MB）。不需要可删 `payload/master_color` 重打包，降到 ~30 MB。
