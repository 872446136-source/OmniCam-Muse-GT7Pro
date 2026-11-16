# 版本分支

2026-11-17 按用户要求分离正式版与测试版源码：

| 分支 | 版本 | 内容 |
| --- | --- | --- |
| `release/513` | Muse GT7Pro 513，versionCode 513 | 以已发布的 `17606b0` 修复提交为基础，移除版本名称中的测试标记，保留已知问题与验证范围 |
| `test/514` | Muse GT7Pro 514-local，versionCode 514 | 本地编译验证过的 514 源码，包含分引擎曝光、实时色彩切换、视频美颜/实况导出开关及 POP 色调实验 |

514 的 `local` 标记说明安装包是在本地编译，源码上传后仍保留该名称，以对应已验证的 APK。新胶片核心、COUI、新水印及其他未完成项见 [Opus 2.0.0 适配记录](https://github.com/872446136-source/OmniCam-Muse-GT7Pro/blob/test/514/docs/UPSTREAM-OPUS-200.md)。

本次只上传版本分支，不修改线上 Release 或现有其他分支。现有工作流只自动响应 `main` 和 `test/full`，这两个新分支不会自动触发 GitHub 编译；后续按需要在本地继续编译。
