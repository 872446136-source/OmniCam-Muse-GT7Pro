# 514-repair1 恢复备份

2026-11-18 从用户保留的 `OmniCam-Muse-GT7Pro-514-repair1-KSU.zip` 提取。原始包 SHA-256 和每个文件的校验值见 `manifest.json`。

## 文件来源

- 仓库根目录是 GitHub 已存在的 `test/514` 基础源码（提交 `d75884e1da7bdd7965c7af1b145899766bbf7fca`），本次保持原样。
- `omnicam-muse-gt7pro.apk` 是安装包内的原始 APK，未重编译、未重签名。
- `module/` 是包内的安装脚本、版本说明和额外 LUT；`apk/assets/` 是 APK 内的配置与符号表，均逐字节提取。
- `decompiled-java/`（如存在）是 JADX 1.5.6 从 APK 恢复的 `local.jiege` Java 参考代码。它经过编译器优化，不是丢失的原始工程，也没有加入 Android 构建。
- 完整 270 MB KernelSU 安装包应作为 GitHub Release 附件保存；其中的挂载数据、原生库和签名均完整保留。

## 恢复边界

未找到 repair1 的原始 Java/Kotlin/C++ 工程、R8 mapping 或该次构建记录。不能把基础源码或反编译文件标记为“可完全复现 repair1 的原始源码”。后续需要对照原始 APK 逐项恢复修复，再独立编译和实机验证。

包内 CHANGELOG 提到的历史验证和“不同步 GitHub”只作为历史文档保留；本次同步依据用户 2026-11-18 的明确请求。没有进行新的手机实拍或 Android 编译。

## 反编译状态

保留 `local.jiege` 类名的 Java 参考文件共 134 个。整个 APK 的反编译返回 87 个错误，参考文件中的失败方法清单见 `manifest.json`。这部分包含 R8 生成的混淆辅助类引用和优化代码，不直接作为可编译源码使用。
