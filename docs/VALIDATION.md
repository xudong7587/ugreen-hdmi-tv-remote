# 验证记录

已完成（0.3.x 实机）：
- 雷鸟 Android 11 HDMI 3 后台捕获方向键。
- NAS 原生 HDMI 接口移动图标焦点。
- 修复 socket 单文件挂载后，电视遥控器到 NAS 的端到端方向转发。
- 允许电视应用自启动/关联启动后，重启不打开 APK，直接切 HDMI 3 可以控制。
- 停在 HDMI 3 重启后，电视回到首页，故无切源的 HDMI 开机恢复尚未验证。

0.4.0-preview：新版原生 UI 与可配置输入源；Android assembleDebug/lintDebug 通过，接收端 18 项测试通过；浏览器交互稿已检查首页及焦点。尚未在电视上覆盖安装，不将旧版实测冒充新版实测。

待验证：确认/返回/菜单完整行为、长按、待机恢复、长时间连接、多 NAS、其他 HDMI 校准、其他品牌电视与 arm64。uinput 仅验证内核事件产生，未验证当前 NAS 桌面焦点响应。

0.4.1 用户实测确认可用后授权发布。0.4.0 正常 APK 中实时查询 HDMI 3=10、首页/权限设置=17 已读取验证；0.4.1 修正窗口事件造成的永久停止，按实时输入源纠正状态，过时异步结果不会覆盖更新的切源/窗口通知。后台查询测试不代表所有厂商均支持。

容器发布流程对 linux/amd64、linux/arm64、linux/arm/v7 运行构建、启动和接收端测试；后两者在 QEMU 下验证，未宣称 ARM NAS HDMI 实机已测。

## 2026-10-26：电视 APK 三个下载选项

本次在 Mac 上使用 JDK 17、Android SDK Platform 35、项目 Gradle Wrapper 8.9 完成：

- `cd android && bash gradlew --no-daemon assembleDebug lintDebug --console=plain`：构建成功，lint 为 0 errors、5 warnings（目标 SDK、矢量图尺寸、备份配置与文本拼接提示）。
- 检查 `output-metadata.json` 与实际文件：恰好生成 `armeabi-v7a`、`arm64-v8a`、`universal` 三个独立 APK，文件名包含版本、架构和 debug 类型。
- 对三个产物执行 `apksigner verify --print-certs` 和 `aapt dump badging`：签名有效且相同，包名、versionCode 7、内置版本名 `0.4.1-recovery-test` 与最低 API 23 保持原配置；ZIP 完整，无原生库。
- `python3 -m unittest discover -s tests -v`：18 项全部通过。
- `git diff --check`：通过。

Release 附件复用原来已发布的实测 APK，其 SHA-256 为 `46016b290a8e6842e11e3702f4e61d967f8b58738caa12eac245c3566d259f4c`，并确认不含原生库。三个下载名称对应相同内容与原签名；本机构建的签名与原 Release 不同，故不以本机构建包替换旧版附件。

上述为本地构建、静态检查与包内容验证，未进行 ARMv7/ARM64 电视实机安装及 HDMI 端到端测试，也未在本机覆盖 Windows 或容器多架构；这些结果不代表云端 CI 已通过。运行逻辑未更改，品牌与固件适配边界保持不变。
