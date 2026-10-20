# 测试分支：全功能暴力移植（GT7 Pro）

这个分支把上游 OmniCam-Muse（OPPO Find X9 Ultra / PMA110）的**全部功能原样搬过来**：

- 全部相机钩子 + 全部相册钩子（`Profile.LITE = false`）
- 原生库 `libjiege_native.so`（`assets/native_init` 已恢复，LSPosed 会注入）
- 上游 `module/payload/`（405 MB：camera_unit_config、feature config、ISP 调校、meishe LUT、basictone、GR/POP/X10 资源、水印样式…）
- 上游 `module/system/odm/lib64/`（91 MB：打过补丁的 `libAlgoInterface.so`、`libBasicTonePhoto.so`、`libmsnativefilter.so`）
- 去掉了机型守卫，`/odm` 该盖的照盖，`post-mount.sh` 也从「缺一个就整体退出」改成「缺的跳过、有的照挂」

**这个分支不是为了能用，是为了在真机上收集崩溃点。** 静态分析已经证明它大概率不工作，见下。

## 先说清楚风险

| 风险 | 说明 |
|---|---|
| 相机可能完全打不开 | 换掉 `libAlgoInterface.so` 是最狠的一步：X9 Ultra 的 79 MB 库和 GT7 Pro 其它 HAL 库的 ABI 对不上，camera provider 可能一起崩 |
| 拍照链路可能崩 | `libBasicTonePhoto.so` 同理 |
| 模式表可能错乱 | `service.sh` 会写 GR / POP / 移轴的开关，相机去建本机配置里不存在的模式 |
| 系统本身不受影响 | 挂的都是相机相关路径，`/odm` 配置与 `/odm/lib64` 相机库，不影响开机 |

**刷之前先确认你能救回来**：手机能连 adb，或者能在 KernelSU 里停用模块（KSU 安全模式：开机时按音量键）。

## 回滚（三档，从轻到重）

```bash
# 1. 只撤挂载，保留钩子 —— 不用卸载模块，重启即生效
adb shell su -c "touch /data/adb/omnicam_fullport_off"

# 2. 只撤原生库替换，保留 /odm 配置覆盖
adb shell su -c "rm /data/adb/omnicam_fullport_off; touch /data/adb/omnicam_fullport_nolib"

# 3. 彻底停用
adb shell su -c "touch /data/adb/modules/ricoh_pop_x9u/disable"
```

改完都要重启。想回到能用的版本，刷 main 分支的 `OmniCam-Muse-GT7Pro-KSU.zip`（Lite 版）。

## 卸载后有没有残留

测试版比 Lite 版脏得多：`service.sh` 全跑，会在相机和相册的 data 目录里铺一堆文件。

模块带 `module/uninstall.sh`，KernelSU / Magisk 删模块时会自动执行，清掉：

- 相机 `files/{jiege,ricoh_gr,pop_port,odm,mode_limit_watermark}`、`databases/mode_data.db`、`shared_prefs/override_config_data.xml` 里模块写的 10 个键
- 相册 `files/{ricoh_gr_styles,jiege_ipu}`、`business_featureSwitch_config.xml` 里的 GR 开关
- 设置应用 `local.jiege.camera`
- `/data/adb/omnicam_fullport_off`、`/data/adb/omnicam_fullport_nolib`

日志在 `/data/local/tmp/omnicam-uninstall.log`。漏了（比如直接删了模块目录）就用 `GT7Pro-适配/清理残留.sh` 补一次，跑几次都安全。
只剩 LSPosed 作用域里那条要手动去掉。完整清单见 `GT7Pro-适配/构建说明.md` 第 9 节。

## 建议的测试顺序

一次全上，坏在哪都不知道。按这个顺序走，每步重启后打开相机看：

1. **先只挂配置**：`touch /data/adb/omnicam_fullport_nolib` → 重启 → 打开相机。
   - 相机能开：说明配置层兼容，问题在库。
   - 相机打不开：配置层就不兼容，库那步不用试了。
2. **再放开库**：`rm /data/adb/omnicam_fullport_nolib` → 重启 → 打开相机。
3. 记录每一步的现象，看第 4 节的日志。

## 出问题看哪里

```bash
# 挂载结果：每个文件一行 ok / FAIL
adb shell su -c "cat /data/adb/modules/ricoh_pop_x9u/bind.log"

# 相机崩没崩
adb shell su -c "logcat -b crash -d | tail -100"
adb shell su -c "ls -l /data/tombstones"

# 钩子是否注入了 / 符号匹配情况（LSPosed 日志）
adb shell su -c "logcat -d | grep -iE 'LSPosed|Symbols|jiege' | tail -200"
```

`bind.log` 里这几行是**预期会失败**的，不算问题：

```
file bind FAIL /odm/lib64/camera/com.qti.tuned.lighthousemain.bin      # 本机没这个文件
no stock library libBasicTonePhotoX9.so, skipped
no stock library libBasicTonePhotoX10.so, skipped
```

## 静态分析的预测（对照实测）

| 预测 | 依据 | 真机表现 |
|---|---|---|
| ISP 调校挂不上 | GT7 Pro 是 `com.qti.tuned.sunny_imx906_main.bin` 等，没有 `lighthouse*` | 应该只是 bind FAIL，不影响 |
| 25MP / 200MP 补丁无效 | 本机 `libAlgoInterface.so` 只有 `24019` 决策族，补丁地址是 `25021` 的 | 不崩就算赢 |
| 大师色彩没有素材 | 本机 `libBasicTonePhoto.so` 里没有 `MergeLutTet_resize`，且 `hasselblad.style.support = 0` | 大师色彩入口可能空 |
| GR / POP 模式建不出来 | 本机 `mode_data.db` 19 个模式里没有 `gr` / `retroCamera`，`camera_unit_feature_config` 是另一代格式 | 切到 GR 大概率黑屏或闪退 |
| 相机整体起不来 | `libAlgoInterface.so` 被换成 X9 的 | 最可能的结果 |

## 构建

`.github/workflows/build-test.yml`：push 到 `test/full-port` 触发。

- payload 与 `module/system/` **不在仓库里**（500 MB），构建时按固定上游提交 `8f373e10b17c95532a3ff2f41c4777ca797da947` 从 codeload 拉下来
- 真编原生库：NDK `29.0.14206865` + CMake `3.22.1`（Lite 版是把这部分剥掉的）
- 产物发到 pre-release `full-port-test`，文件名 `OmniCam-Muse-FullPort-KSU.zip`（约 450 MB）
- 签名用同一个 `signing/port.p12`，和 Lite 版证书一致，可以直接互相覆盖安装

## 测完把结果发我

最有用的三条：`bind.log` 全文、`logcat -b crash` 里相机那段、以及「第 1 步 / 第 2 步分别是什么现象」。有了这些才能判断是配置层还是库层的问题，再决定下一步是重做配置还是只重做库。
