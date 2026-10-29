# OmniCam Muse 4.24 GT7Pro (492)

**正片胶片黑屏找到真凶了：`positive_*.bin` 这台机器上根本不存在。** 这一版把缺的 12 个 LUT 补进
挂载层。另外，491 在真机上验证过了：快门门禁这次真装上了，`blocked` 从"每点一次一行"变成 0。

---

## 1. 正片胶片：Meishe 每帧都在报错

用 adb 连上手机抓的实时日志，切到「正片胶片」后：

```
I/PopPort: lut positive_sdr_gen_a_1.bin from nowhere, the open is about to fail
I/PopPort: lut positive_sdr_gen_d_1.bin from nowhere, the open is about to fail
E/meicam : Failed to open lut file /odm/etc/camera/meishe_lut/positive_sdr_gen_a_1.bin
E/meicam : Failed to open lut file /odm/etc/camera/meishe_lut/positive_sdr_gen_d_1.bin
```

两条各出现 **546 次**（每帧两次），预览全黑 —— 和截图完全对上。

设备上 `/odm/etc/camera/meishe_lut` 共 149 个文件（本机 107 + 模块 42），把相机 dex 里引用到的
124 个 LUT 名逐个比对，缺的里面有 **8 个 `positive_*`**：

```
positive_normal.bin                  positive_master.bin
positive_sdr_gen_a_1.bin             positive_sdr_gen_d_1.bin
positive_hdr_normal_a_1.bin          positive_hdr_normal_d_1.bin
positive_hdr_master_a_1.bin          positive_hdr_master_d_1.bin
```

`com.oplus.camera.filter.FilterHelper` 的表写得很清楚：胶片条上的 `Portra胶片 → portra400_*`、
`正片胶片 → positive_*`、`NC/CC → fuji-*`。本机固件自带 `portra400_*`、`fuji-nc.bin`、
`fuji_cc.bin`、`800t_*`、`glow_sdr_*`、`Polaroid_*`，**唯独没有 `positive_*`**；上游 payload 也
没有（上游只有 42 个 LUT，X8U 那份参考包才有 50 个含 `positive_*`）。

### 修法

B6 参考包的 `/odm` 树里有这 8 个，而且是 **v3 / 17³** —— 本机自己就在用这个形状
（`Polaroid_normal.bin`、`ccd_normal_cold.bin` 都是 v3 / 17³ / 14855 B）。另外顺带补 4 个同样
"被引用但不存在"的：`800t_master.bin`、`ccd_base_cold.bin`、`ccd_base_warm.bin`、`fuji-nc_master.bin`。

放 `module/lut_extra/`，CI 在 `mslut.py fix` **之后**覆盖进 payload（这样它们不会被重采样）：

```yaml
cp -a module/lut_extra/. module/payload/meishe_lut/
for f in module/lut_extra/*.bin; do python3 tools/mslut.py info "$f"; done
```

12 个文件，364 KB。

---

## 2. 491 在真机上的验证结果

```
PopPort: POP filter gate installed on hm.t1 via symbols, switching flag s3, settled flag t3, 2 boolean(s)
```

**门禁第一次真正装上了**（488/489 从来没有这一行）。整段测试里
`blocked during retro filter switching` 出现 **0 次** —— 快门不再被那个标志拦死。

> 顺带纠正 488 的一个结论：`T0`（`onFilterSnapSettled`）**有调用方**，日志里
> `filter strip settled, flags s3=true, t3=true` 反复出现。也就是说 `s3` 与 `t3` 同时为真这种
> 自相矛盾的状态是常态，491 里"看到就清"的判断是对的。

> 还有一处要说明：符号在设备上命中的是 **`hm.t1`**，不是离线索引里那份 `um.o1`。索引是从另一个
> 构建 dump 的，混淆名不同，但指纹（日志字符串 + 签名）两边都能唯一命中，这正是符号系统的意义。

---

## 3. 快门：不再被拦，但还没出片

清掉标志之后点快门，日志里只有：

```
CameraLog: OCAM_CameraControlUI onShutterButtonClick, ShutterButton.id: 2131297915
CameraLog: OCAM_CommonCapMode onCameraShutterButtonClick
```

没有 `blocked`，但也没有拍下文件。所以"点了没反应"在 POP 里是**两个原因叠加**：标志拦截（491 修好）
+ 后面还有一层在拒绝。这一版加一行，把第三层的返回值打出来：

```
PopPort: shutter click returned false, flags s3=false, t3=true
```

有这行说明是相机自己否掉的（下一版顺着它查）；没有这行说明点击通过了、卡在更后面的拍照流程。

---

## 4. 装完请这样验

进 POP → 切到「正片胶片」→ 预览应该**立刻有画面**（不再是黑屏）。日志里不该再出现
`Failed to open lut file ... positive_*`；对应会看到：

```
PopPort: lut positive_sdr_gen_a_1.bin from the layer
```

---

## 5. 没动的东西

算法栈（`libAlgoInterface` / `libBasicTonePhoto*`）和 ODM 配置仍然一个字节都不替换；唯一替换的
系统文件还是 `/odm/lib64/libmsnativefilter.so`（Meishe 渲染器）。
