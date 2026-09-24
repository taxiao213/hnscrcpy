# hnscrcpy — 鸿蒙 NEXT 投屏远控工具 PRD

## 1. 产品概述

hnscrcpy 是一个类似 scrcpy 的桌面工具：在 Windows / macOS(arm64) / macOS(x86_64) 上执行一个二进制文件，
即可将 HarmonyOS NEXT 设备的屏幕实时镜像到桌面窗口，并支持鼠标键盘远控操作。

设备侧不安装任何常驻 App，仅通过 HDC(HarmonyOS Device Connector) 与设备通信。
视频流获取复用已验证的 hosScrcpy 方案（华为 Hypium 测试框架组件，H.264 流）。

### 与参考项目 harmony-screencap 的关系

| 维度 | harmony-screencap | hnscrcpy |
|------|-------------------|----------|
| 形态 | Spring Boot 服务 + 浏览器前端 | 单个原生二进制 + 桌面窗口 |
| 视频链路 | hosScrcpy H.264 → WebSocket → WebCodecs | hosScrcpy H.264 → FFmpeg/avcodec 解码 → JavaFX 渲染（全本地，无网络中转） |
| 视频源 | 双模式（H.264 / JPEG 截图回退） | 仅 H.264 流（hosScrcpy） |
| 远控 | REST API + uinput | 窗口事件直接映射 uinput |
| 分发 | jar + 脚本 | jpackage 三平台安装包 + CI |

## 2. 目标用户与场景

- 鸿蒙应用开发者：开发调试时在大屏上查看设备、用键鼠操作
- 测试工程师：设备兼容性测试、演示录屏
- 技术运营：真机远程演示

## 3. 功能需求

### 3.1 核心功能（P0）

| 编号 | 功能 | 说明 |
|------|------|------|
| F1 | 设备发现 | `hdc list targets` 枚举设备，自动识别真机/模拟器（序列号 127.0 开头为模拟器）；设备插拔自动刷新 |
| F2 | 一键投屏 | 单设备直连；多设备弹出选择列表；执行后打开镜像窗口 |
| F3 | 实时镜像 | H.264 流解码渲染，目标 ≥30fps（设备支持时 60fps），端到端延迟 <300ms |
| F4 | 鼠标远控 | 点击、滑动、拖拽（左键拖动=滑动，右键=返回） |
| F5 | 键盘远控 | Home / Back / 电源 / 最近任务 / 音量（如设备支持），快捷键可配置 |
| F6 | 坐标映射 | 窗口缩放、黑边、横竖屏旋转下的精确坐标换算 |
| F7 | 会话管理 | 断连检测、重连、异常退出清理（杀掉设备侧 scrcpy 进程、释放端口） |

### 3.2 增强功能（P1）

| 编号 | 功能 | 说明 |
|------|------|------|
| F8 | 画质设置 | 码率 / 最大边长 / 帧率上限设置（取决于 hosScrcpy 支持度，见调研项 R2） |
| F9 | 窗口工具栏 | 返回 / Home / 多任务 / 电源 / 全屏 / 置顶 / 截图保存 / 旋转 |
| F10 | 截图 | 将当前帧保存为 PNG/JPEG |
| F11 | 旋转跟随 | 设备旋转时流分辨率变化，解码器重建 + 窗口自适应 |
| F12 | 命令行参数 | scrcpy 风格 CLI（见 5.2），支持脚本化使用 |
| F13 | 多会话 | 同时镜像多台设备（受 hosScrcpy 端口限制，见风险 R-3） |

### 3.3 暂不做（YAGNI）

- 音频转发（hosScrcpy 不支持）
- 文件拖拽安装（APK/HAP install）
- 剪贴板同步
- 录屏（后续迭代考虑）
- Linux 平台

## 4. 非功能需求

| 类别 | 指标 |
|------|------|
| 性能 | 1080p 解码渲染 ≥30fps；帧渲染不阻塞 UI 线程；解码队列有界（丢帧不积压） |
| 延迟 | 端到端 <300ms（USB 连接） |
| 体积 | 安装包 ≤250MB（内嵌 JRE + JavaFX + FFmpeg natives + hdc 二进制） |
| 稳定性 | 设备拔线 100% 不崩溃，会话可恢复或干净退出 |
| 兼容 | Windows 10+ x64、macOS 12+ arm64 / x86_64；HarmonyOS NEXT 真机 + 官方模拟器 |
| 安全 | 不采集不上传任何设备数据；不硬编码密钥 |

## 5. 交互设计

### 5.1 主流程

```
启动二进制
  ├─ 无设备 → 主窗口显示"等待设备连接…"，自动轮询刷新
  ├─ 1 台设备 → 直接开始投屏
  └─ 多台设备 → 设备列表，双击/回车选择

投屏窗口
  ├─ 视频区：等比缩放，黑边填充
  ├─ 工具栏（悬浮底部，可隐藏）：Back Home Recent Power | 旋转 全屏 置顶 截图
  ├─ 状态栏：分辨率 / 实时帧率 / 码率 / 设备 SN / 连接状态
  └─ 快捷键（MOD = Ctrl，可配置）
       左键点击 → 设备点击
       左键拖动 → 设备滑动
       右键     → Back
       MOD+H  → Home   MOD+B → Back   MOD+P → 电源
       MOD+R  → 旋转   MOD+F → 全屏   MOD+S → 截图
       MOD+O  → 置顶
```

### 5.2 CLI（scrcpy 风格）

```
hnscrcpy [options]

-s, --serial <sn>        指定设备序列号
-m, --max-size <px>      视频最大边长（默认 1920，0 = 原始分辨率）
-b, --bit-rate <value>   视频码率（默认 8M；支持 8M / 8000K 写法）
    --fps <n>            帧率上限（默认跟随设备刷新率）
    --window-title <t>   窗口标题
    --always-on-top      窗口置顶
-f, --fullscreen         全屏启动
    --no-control         只看不控
    --stay-awake         投屏时保持设备唤醒（uinput/aa 命令，见调研 R4）
-v, --version            版本号
-h, --help               帮助
```

## 6. 边界与约束

1. 设备必须已开启 USB 调试并通过 HDC 连接（Windows 需安装华为 USB 驱动，文档说明）。
2. 视频流仅支持 hosScrcpy H.264 模式；hosScrcpy 不兼容的设备（"can not find scrcpy pid"）给出明确错误提示与排查指引，不做 JPEG 回退（本产品决策）。
3. hosScrcpy 为华为 DevEco/Hypium 组件，其再分发许存在不确定性 —— 发布前需评估（见风险 R-6）。
4. 仅支持 USB / HDC TCP 连接（模拟器为 127.0.0.1:5555），不做 WiFi 发现。
