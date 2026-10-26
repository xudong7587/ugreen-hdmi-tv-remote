# 更新日志

## v0.4.1 下载选项补充 — 2026-10-26

本次补充电视端的三个 APK 下载选择，并更新源码构建与 CI 产物；不改变已发布 APK 的运行代码、签名、包名或内置版本号。

| APK 附件 | 版本说明 |
| --- | --- |
| `nas-hdmi-remote-0.4.1-armeabi-v7a.apk` | ARMv7 / 32 位 ARM 电视选项；包括 64 位芯片搭配 32 位 Android 的设备。Android ABI 名称为 `armeabi-v7a`。 |
| `nas-hdmi-remote-0.4.1-arm64-v8a.apk` | ARM64 / 64 位 ARM Android 电视选项，依据系统支持的 ABI 选择。 |
| `nas-hdmi-remote-0.4.1-universal.apk` | CPU 架构通用版，适合不确定系统架构或需要同一安装包用于多种架构的情况，推荐优先选择。 |

### APK 内容与升级

- 当前应用使用纯 Java，没有 JNI/原生 `.so` 库，原版 APK 已经不限制 ARMv7、ARM64 等 CPU 架构。本次新增明确的下载选项，不宣称修复了原版不存在的原生 ABI 限制。
- 三份 Release 附件复用原来已签名、已实测的同一个 APK，字节内容、大小及 SHA-256 相同，文件名仅表达推荐用途。原附件 `nas-hdmi-remote-0.4.1-recovery-test.apk` 改名为 `nas-hdmi-remote-0.4.1-universal.apk`。
- 三个选项功能一致，内置版本名仍为 `0.4.1-recovery-test`、versionCode 仍为 `7`。已安装原版无需重新安装；同签名包可覆盖安装，不需要为了架构选项卸载或重新配置权限。
- 新的源码构建通过 Gradle ABI splits 一次输出 `armeabi-v7a`、`arm64-v8a` 和 `universal` 三份独立 APK，文件名包含版本、架构和构建类型。CI 分别上传三个 artifact，缺失产物会报错。
- 本机或 CI 构建使用各自的 debug 签名，与原 Release 签名可能不同，不能保证覆盖安装；正式下载请使用本次保留原签名的 Release 附件。
- NAS 接收端与 Docker 镜像没有变化，继续使用 `0.4.1` 镜像，无需更新容器。

### 兼容范围

- 要求 Android 6.0 / API 23 及以上。普通非 Android 电视不能安装 APK。
- 「通用」仅描述 CPU 架构，不等同于所有电视品牌和固件的 HDMI 输入源、遥控按键与开机策略均已适配。当前仍以 TCL/雷鸟输入源适配为主，其他品牌需要单独适配与验证。
- 无障碍权限、自启动与关联启动要求保持不变；ARMv7 电视的实际 HDMI 按键转发仍需实机验证，不把已有雷鸟 Android 11 的实测视为所有架构、所有电视均已验证。

## v0.4.1 — 2026-10-18（实验版）

- Sunny UI 深色淡蓝描边，首页保留「选择 HDMI 通道」与「更多设置」。
- 增加 TCL 实时输入源查询，恢复后台重启后遗漏切源通知的状态。
- 修复临时窗口通知导致 NAS 影视页按键转发永久停止的问题。
- 支持 LAN 自动发现、自动握手与 NAS 原生 HDMI 接口后端。
- 提供 amd64、arm64、arm/v7 多架构 Docker 镜像，各架构构建和接收端测试后再推送；ARM NAS 的实际 HDMI 固件接口仍需实机验证。
