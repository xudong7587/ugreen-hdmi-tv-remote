# HDMI 遥控

用电视原装遥控器控制 NAS 的 HDMI 桌面。画面仍走 HDMI，按键通过局域网传输。无需配置域名，只需电视与 NAS 在同一局域网。

电视 APK → 无障碍按键服务 → LAN 自动发现 / 握手 → NAS Docker → HDMI 桌面原生接口。

[下载 APK（三种选择）](https://github.com/xudong7587/ugreen-hdmi-tv-remote/releases/tag/v0.4.1) · [更新日志](CHANGELOG.md) · [电视权限配置](docs/TV-ADB.md) · [预编译 Compose](compose.release.yaml)

## 快速开始

1. 在 NAS 部署下方的 `compose.native.yaml` 接收服务。
2. 在电视安装 APK，首次打开，完成无障碍和自启动授权。
3. 首页选择 NAS 所接的 **HDMI 3**，切到该输入源即可使用。NAS 地址和配对码通常不用输入。

首页只有「选择 HDMI 通道」和「更多设置」两个入口。自动连接默认开启；权限、备用连接、通道校准、暂停与按键诊断均在更多设置中。

**当前为实验版。** 已在一台雷鸟 Android 11 电视和一台支持 HDMI 桌面的绿联 NAS 上验证 HDMI 3 方向键转发，以及电视重启后后台自启、再切 HDMI 3 自动连接。未承诺全型号兼容。确认、返回、菜单、长按和长期稳定性仍需各固件验证。

## 电视 APK 怎么选？

三种选择均要求 **Android 6.0（API 23）及以上**，功能一致，一台电视只需安装其中一个。不确定处理器架构时，优先下载通用版。

| 版本 | 下载 | 适用情况 |
| --- | --- | --- |
| ARMv7（32 位） | [armeabi-v7a APK](https://github.com/xudong7587/ugreen-hdmi-tv-remote/releases/download/v0.4.1/nas-hdmi-remote-0.4.1-armeabi-v7a.apk) | 32 位 ARM Android 电视，包括 64 位处理器运行 32 位 Android 的情况 |
| ARM64（64 位） | [arm64-v8a APK](https://github.com/xudong7587/ugreen-hdmi-tv-remote/releases/download/v0.4.1/nas-hdmi-remote-0.4.1-arm64-v8a.apk) | 运行 64 位 Android 的 ARM 电视 |
| 通用版（推荐） | [universal APK](https://github.com/xudong7587/ugreen-hdmi-tv-remote/releases/download/v0.4.1/nas-hdmi-remote-0.4.1-universal.apk) | 不清楚系统架构，或希望使用一个安装包覆盖不同 CPU 架构 |

**ARMv7** 在 Android 中对应 **armeabi-v7a**。应以电视 Android 系统支持的 ABI 为准，不要只看芯片是否为 64 位。有 ADB 时可执行 `adb shell getprop ro.product.cpu.abilist`：包含 `arm64-v8a` 可选 ARM64，仅有 `armeabi-v7a` 则选 ARMv7；也可直接选通用版。

**本次是下载选项补充：** 当前电视端使用纯 Java，没有 JNI/原生 `.so` 库，原 APK 已无 CPU 架构限制。为保留旧版签名与实测代码，v0.4.1 的三份附件使用同一个已发布 APK，字节内容和 SHA-256 相同，仅文件名区分推荐用途；内置版本名仍为 `0.4.1-recovery-test`。不需要为了 ARMv7 支持卸载重装旧版。

「通用版」指 **CPU 架构通用**，不代表所有电视品牌、系统或固件均能使用。当前输入源适配仍主要面向 TCL/雷鸟；其他品牌可能需要新增适配，无障碍与自启动授权仍需完成。详情见 [更新日志](CHANGELOG.md)。

## 电视需要 ADB 吗？

- 有标准无障碍入口的电视：先通过系统设置启用，未必需要 ADB。
- 本次验证的雷鸟固件：没有标准入口，**首次配置需要 ADB**，通过电脑启用本应用无障碍服务；还必须在电视权限管理中允许自启动与关联启动。
- 日常按键转发直接在电视与 NAS 间通信，**不依赖 ADB 或电脑常驻**。关闭 ADB 后的完整回归尚未单独测试。
- 仅装 APK、选择通道，不能绕过 Android 的首次权限授权。

详见 [电视安装与 ADB 配置](docs/TV-ADB.md)。

## NAS Docker Compose（推荐原生接口后端）

预编译镜像地址已包含在 `compose.release.yaml` 中，支持 **amd64、arm64、arm/v7**，Docker 自动选择架构。发布流程逐架构构建并运行接收端测试后才推送；ARM NAS 的实际 HDMI 固件接口仍需对应设备验证。

只需下载 Release 中的 `compose.release.yaml` 和 `default.env.example`（仓库内名为 `.env.example`），将后者另存为 `.env` 并填写 NAS 局域网 IP，再运行：

```sh
docker compose -f compose.release.yaml up -d
docker compose -f compose.release.yaml logs --tail=30
```

以下是源码构建方式，两种 Compose 二选一，不要同时启动。

需要支持 HDMI 桌面的绿联系统、Docker Compose，以及宿主机 socket：

```sh
test -S /run/ugreen/http/display_desktop.http.sock
```

该路径是厂商内部接口，不同固件可能变化。没有 socket 时本方案不能直接使用；不要创建空文件冒充 socket。

下载本仓库后，在项目目录创建 `.env`（把示例值替换为 NAS 的实际局域网 IPv4）：

```dotenv
BIND_ADDRESS=192.168.1.100
```

```sh
docker compose -f compose.native.yaml up -d --build
docker compose -f compose.native.yaml logs --tail=30
```

实际配置见 [compose.native.yaml](compose.native.yaml)。接收端在 NAS 上使用 host 网络：

| 端口 | 用途 |
| --- | --- |
| TCP 8765 | 握手、心跳、七键转发，绑定 `.env` 指定的 NAS 地址 |
| UDP 8766 | 局域网广播发现和自动配对，监听所有接口 |

电视与 NAS 需在允许相互访问、允许广播的同一局域网；如启用 NAS 防火墙，放行来自电视所在网段的上述端口。AP 隔离、访客 Wi-Fi 或不同 VLAN 可能阻止发现。不要将端口映射到公网。

自动配对有意允许受信局域网设备获取配对码，省去电视输入。协议使用 HMAC 校验但不加密，自动发现也不提供 NAS 身份保证；用于自己的可信 LAN。多个接收端同时存在时会连接最先应答的实例，当前没有 NAS 选择器。

容器挂载 `./data:/data` 保存配对码，和 `/run/ugreen/http:/native:ro` 访问原生接口。**必须挂目录，不能只挂单个 socket 文件**：桌面重建 socket 后，单文件挂载会保留旧 inode，造成“已连接但按键无效”。该目录内其他 socket 名称也可见；接收代码固定访问 HDMI socket，只接受七个键，不提供任意请求代理。容器不需要 privileged、Docker socket 或 uinput。

常用命令：

```sh
# 停止（保留配对数据）
docker compose -f compose.native.yaml down
# 更新源码后重建
docker compose -f compose.native.yaml up -d --build
# 仅在需要手动配置备用信息时查看配对码；不要公开输出
docker compose -f compose.native.yaml exec nas-remote python /app/server.py --show-code
```

`.env`、`data/` 与密钥不会提交。默认未配置 `BIND_ADDRESS` 时只监听 TCP 回环地址，电视无法连接。不要同时运行 `compose.yaml` 和 `compose.native.yaml`：前者是早期实验性 uinput 后端，在本次 NAS 上未验证焦点移动。

## 使用与通道选择

- HDMI 3 预置当前已验证的 TCL 输入源编号 `10`。
- HDMI 1、2、4 可选择，但没有猜测固件编号，需在「更多设置 → 连接与通道校准」中填写。先切入目标 HDMI，再回应用查看最近通知中 `Type=InputSourceChanged` 的 `arg1`。只选对应 HDMI 的通知，勿选首页通知。
- 未校准（编号 `-1`）不会转发。其他品牌若不发送 TCL 广播，仅填编号也不能适配。
- 已验证 TCL 固件支持实时查询当前输入源，后台服务重启或临时窗口事件后可重新判断；不支持查询的固件仍依赖切源通知。
- 回电视首页、切离所选输入源、关屏或打开本应用时停止转发。
- 长按返回约两秒暂停本次接管，切离再切回恢复；短按返回转发给 NAS。
- NAS 重启或连接丢失后，电视会重新发现并握手，不重放旧按键。

**开机行为边界：** 已验证电视开机到首页时 APK 后台自启，随后切 HDMI 3 可用。电视本身设置为“开机进入首页”，所以“开机自动恢复 HDMI 3、不切源直接控制”尚未验证；应用不会强行切换电视输入源。

## 排查

| 现象 | 检查 |
| --- | --- |
| 显示尚未授权 | 无障碍服务是否启用并绑定；参照 ADB 文档 |
| 重启后无效，打开应用才恢复 | 电视的应用自启动、关联启动及自动管理策略 |
| 后台就绪但没接管 | 通道、校准编号和新输入源通知；重新切入目标 HDMI |
| 自动发现失败 | 同网段、UDP 8766、防火墙/AP 隔离；确认 NAS 接收容器运行 |
| 已连接却不能移动 | 原生 socket 是否存在，挂载是否为目录；查看容器日志 |
| 其他 HDMI / 其他品牌无效 | 当前只有 TCL 广播适配；未验证通道需校准，其他品牌需新增适配 |

「更多设置 → 按键诊断」只在手动开始后采集 90 秒、最多 80 条本地按键记录。`SERVICE` 代表后台收到了按键，`ACTIVITY` 仅代表应用页面收到。服务不读取屏幕文字。完整验证边界见 [验证记录](docs/VALIDATION.md)。

## 构建与测试

Android：JDK 17、SDK Platform 35、Gradle Wrapper 8.9。

```sh
cd android
./gradlew assembleDebug lintDebug  # Windows 使用 gradlew.bat
```

一次构建生成三个可独立安装的 APK，位于 `android/app/build/outputs/apk/debug/`：

- `nas-hdmi-remote-0.4.1-recovery-test-armeabi-v7a-debug.apk`
- `nas-hdmi-remote-0.4.1-recovery-test-arm64-v8a-debug.apk`
- `nas-hdmi-remote-0.4.1-recovery-test-universal-debug.apk`

Gradle 使用 ABI splits 生成 ARMv7、ARM64 与 universal 三个产物，CI 也分别提供三个下载项。当前没有原生库，因此分架构包并不比通用包拥有更多功能或更小体积；将来若引入原生库，必须重新验证各架构与通用包的实际覆盖范围。

Release `v0.4.1` 的三份附件（内置版本名 `0.4.1-recovery-test`，保留实测二进制）使用原有开发签名，可覆盖此前同签名版本；本机构建和 CI 的 debug 签名不能保证与已安装版本相同。请使用 Release 附件覆盖更新，勿把本机或 CI 产物当作同签名更新包。私钥不提交，未来切换正式签名需另行说明迁移。

接收端：Python 3.11+，只有标准库依赖。

```sh
python -m unittest discover -s tests -v
```

Docker 使用 `python:3.12-slim-bookworm`；当前未锁定基础镜像 digest、未完成 arm64 实机验证。GitHub Actions 包含 Python、Android build/lint，以及 amd64/arm64/arm/v7 容器构建和测试。

## 设计与许可

界面参考 [Sunny UI](https://github.com/xudong7587/sunny-ui-design-system) 的「晴蓝 × 精致描边」，原生 Android 实现，主色 `#9dbbff`、深蓝灰背景、明确焦点轮廓。没有引入 React/WebView 或复制其组件源码。设计来源与版本见 [UI 说明](docs/UI.md)。本项目自有代码使用 MIT，见 LICENSE；工具遵循各自许可证。
