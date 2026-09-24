# hnscrcpy — 架构设计

> M0 已完成（调研 + 真机验证），协议细节见 [HOS_SCRCPY_PROTOCOL.md](./HOS_SCRCPY_PROTOCOL.md)。
> 本文档已按 M0 实证结论修订。

## 1. 技术栈

| 组件 | 选型 | 理由 |
|------|------|------|
| 语言 | Java 17 (LTS) | 复用 hosScrcpy jar（Java API）；本地工具链上限 17.0.19，JavaFX 21 兼容 17+ |
| UI | JavaFX 21 (LTS) | 官方跨平台桌面框架，jpackage 原生打包成熟 |
| 视频解码 | JavaCPP FFmpeg 7.1（avcodec 直解） | grabber 的 find_stream_info 在裸 h264 直播管道阻塞到 EOF；每 gRPC 消息即完整 AU，直解最低延迟 |
| 设备流 | hosScrcpy-1.0.15-beta.jar（不随包分发，运行时自动发现） | 真机验证的全链路 H.264 流通道 |
| 设备通信 | hdc（内嵌三平台二进制 + libusb 动态库） | 与参考项目一致 |
| 构建 | Maven + javafx-maven-plugin + jpackage | 三平台 CI 标准配方 |
| CI | GitHub Actions matrix | windows-latest / macos-14 (arm64) / macos-13 (x64) |
| 测试 | JUnit 5 + AssertJ | 单元测试 80% 覆盖目标 |

## 2. 总体架构

```
┌────────────────────────────────────────────────────────────┐
│ JavaFX UI 层                                                │
│  MainView(设备列表)  MirrorWindow(视频+工具栏)  SettingsView │
├────────────────────────────────────────────────────────────┤
│ 会话层   MirrorSession                                      │
│         生命周期编排：connect → stream → decode → render    │
│         状态机：IDLE → CONNECTING → STREAMING → CLOSED      │
├───────────────────────┬────────────────────────────────────┤
│ 视频管道               │ 控制通道                            │
│  HosScrcpyProvider    │  DeviceController                   │
│   (H.264 Annex B 帧,  │   触摸/鼠标/滚轮 → uitest 通道 RPC  │
│    按屏幕变化推送)     │   按键 → hdc shell uinput -K        │
│       ↓ 有界队列       │       ↑                             │
│  H264Decoder          │  InputForwarder（move 节流 ≤60Hz）  │
│   (FFmpeg/avcodec)      │       ↑                             │
│       ↓ BGRA Frame    │  CoordinateMapper                   │
│  FrameRenderer        │   窗口坐标 → 设备坐标               │
│   (PixelBuffer 零拷贝)│                                     │
├───────────────────────┴────────────────────────────────────┤
│ 基础设施                                                    │
│  HdcClient  HdcLocator  HosScrcpyLocator  DeviceMonitor    │
│  ProcessRunner  Platform  Log                              │
└────────────────────────────────────────────────────────────┘
```

## 3. 模块划分（按 feature 组织）

```
src/main/java/com/hnscrcpy/
├── App.java                     # JavaFX 入口，启动主窗口/CLI 直通投屏
├── cli/Arguments.java           # CLI 解析（无依赖手写）
├── device/
│   ├── HdcClient.java           # hdc 子进程封装：list targets / shell / fport / file send|recv
│   ├── HdcLocator.java          # 定位 hdc：HDC 环境变量 > PATH > 内嵌二进制提取到 ~/.hnscrcpy/tools/
│   ├── HosScrcpyLocator.java    # 定位 hosScrcpy jar：扫描 JetBrains DevecoTesting-Hypium 插件目录
│   │                            #   > 用户配置路径（jar 不随包分发，见协议文档 §9）
│   ├── DeviceInfo.java          # sn / 类型(真机|模拟器) / 型号 / 系统版本
│   └── DeviceMonitor.java       # 2s 轮询设备列表，变化事件通知 UI（isOnline 5s 超时偶发误判 → 重试）
├── stream/
│   ├── StreamProvider.java      # 接口：start(VideoConfig, FrameSink) / stop() / isRunning()
│   ├── HosScrcpyProvider.java   # 包装 HosRemoteDevice；onData(ByteBuffer)=H.264 Annex B 帧
│   ├── VideoConfig.java         # frameRate / bitRate(Mbps) / iFrameInterval（映射 HosRemoteConfig）
│   └── FrameSink.java           # 回调：onVideoSize(w,h) / onFrame(byte[] annexB) / onError(t)
├── decode/
│   ├── H264Decoder.java         # avcodec 直解（send_packet/receive_frame + sws→ARGB）；
│   │                            #   不用 FFmpegFrameGrabber：裸 h264 直播管道上 find_stream_info
│   │                            #   会阻塞到 EOF（fps 估算需读完整流），实测所有 demuxer 选项无效
│   ├── VideoFrame.java          # 解码输出：BGRA byte[] + 宽高 + pts
│   └── DecoderPump.java         # 独立线程：队列取 AnnexB → 解码 → 回调渲染；有界队列丢旧帧
├── render/
│   ├── FrameRenderer.java       # JavaFX PixelBuffer(BGRA→WritableImage) 零拷贝上屏
│   └── RenderScheduler.java     # AnimationTimer 驱动；渲染落后时跳过中间帧
├── control/
│   ├── DeviceController.java    # 触摸/鼠标/滚轮 → HosRemoteDevice.onTouch*/onMouse*（uitest 通道，
│   │                            #   同步 RPC）；按键 → executeShellCommand(uinput -K)
│   ├── CoordinateMapper.java    # 窗口内容区 ↔ 设备像素 双向换算（缩放/黑边/旋转）
│   └── InputForwarder.java      # JavaFX 事件 → DeviceController；手势判定(点击/滑动阈值)；move 节流
├── session/
│   ├── MirrorSession.java       # 组装 StreamProvider+Decoder+Controller，状态机，资源清理
│   └── SessionException.java
├── ui/
│   ├── MainView.java            # 设备列表 + 状态
│   ├── MirrorWindow.java        # 视频区 + 工具栏 + 状态栏 + 快捷键
│   └── SettingsView.java        # 画质/快捷键/路径设置（Properties 持久化 ~/.hnscrcpy/config.properties）
└── util/
    ├── Platform.java            # OS/arch 检测
    ├── ProcessRunner.java       # 子进程执行（stdout/stderr 消费、超时、销毁）
    └── Log.java                 # SLF4J + 文件滚动日志（禁止拼接 protobuf 消息对象）
```

**classpath 铁律（M0 实证）**：hosScrcpy jar 自包含 grpc/netty/protobuf/fastjson/guava（2023-10 版），
运行期**禁止**再引入外部 grpc/netty/protobuf 依赖（否则 `NoSuchMethodError: filterTransport`，
症状表现为 gRPC UNKNOWN）。hosScrcpy jar 单独放置、与应用 jar 同置 classpath 即可。

## 4. 关键数据流

### 4.1 投屏链路

```
设备侧 scrcpy server (hosScrcpy 推送 libscreen_casting.z.so 并以 uitest 扩展启动)
        │ gRPC ScrcpyService/onStart 流（经 fport → unix socket scrcpy_grpc_socket）
        ▼ 每消息一帧：Annex B（SPS/PPS 内嵌首包；IDR=5；P=1），按屏幕变化推送
HosRemoteDevice.onData(ByteBuffer)          [hosScrcpy 回调线程]
        ▼ 拷贝到 byte[]
ArrayBlockingQueue<Packet>(容量 8，满则丢最旧)  [背压：宁丢帧不积压]
        ▼
DecoderPump 线程 → FFmpeg 裸流解封装 → 解码 → BGRA VideoFrame
        ▼
最新帧槽位（volatile 引用，只保留最新一帧）
        ▼
AnimationTimer（JavaFX 线程）→ PixelBuffer 更新 WritableImage → ImageView
```

要点：
- **解码不在 FX 线程**：DecoderPump 独立线程，渲染只取最新帧，天然丢帧。
- **PixelBuffer 零拷贝**：BGRA 缓冲直接包 WritableImage，避免每帧像素复制。
- **按变化推流 ≠ 断流**：静态画面无新帧是正常行为（M0 实测静置 7s 无帧）。
  断流判定用 gRPC 状态 + isOnline 轮询，不用"帧超时"。
- **断流自动重连**：视频 gRPC 流异常（实测约 9 分钟会被设备侧断开，UNAVAILABLE）时，
  MirrorSession 经 pump errorHandler 感知 → ERROR 态 → 指数退避重连（1s→10s，≤5 次），
  重连前 stop 并等待 2s（设备侧 scrcpy 进程退出），pump.restart() 换全新解码器。
- **静默断流看门狗**：设备侧退出（screen exit / 被另一客户端抢占）时 gRPC 可能无任何
  异常回调，流只是静默无帧。看门狗每 10s 请求一次 IDR（静态画面也会被强制推一帧），
  连续两轮无新帧即判死（StreamStalledException）→ 走重连。
- **丢帧恢复**：解码器出错/花屏时调 `requestIDRFrame()` 强制关键帧。
- **分辨率变化**（旋转）：流宽高变化 → 重置解码器 + 通知窗口自适应。

### 4.2 控制链路

```
JavaFX 鼠标/键盘事件
  → InputForwarder（手势判定：位移<阈值+短按=点击；位移≥阈值=滑动；mouseMove 节流 ≤60Hz）
  → CoordinateMapper（窗口内容区坐标 → 设备分辨率坐标，黑边取负裁剪）
  → DeviceController
       ├─ 触摸/滑动/拖拽/滚轮 → HosRemoteDevice.onTouch*/onMouse*（uitest JSON-RPC 通道）
       └─ Home/Back/电源/最近任务/音量 → executeShellCommand("uinput -K -d N -u N")
```

- 触摸通道是**同步 RPC**（发一条等一条响应），延迟低但高频频发会排队 → InputForwarder 必须节流。
- 按键注入没有 uitest API，走 uinput 子进程（低频操作，进程开销可忽略）。
- 服务端的控制通道在 `startCaptureScreen` 时自动初始化（agent.so 推送 + 3 条转发）。

### 4.3 生命周期与清理

```
MirrorSession.close() 必须完成：
  1. HosScrcpyProvider.stopRecord()（fport rm + kill 设备侧 singleness 进程；
     参考实现 stop 后 sleep 2s 等设备侧退出，防新旧实例冲突）
  2. DecoderPump 中断并 join
  3. 关闭窗口，DeviceMonitor 继续运行
```

## 5. HDC 命令集（M0 真机验证）

| 操作 | 命令 |
|------|------|
| 设备列表 | `hdc list targets` |
| 屏幕信息 | `hdc -t <sn> shell SP_daemon -screen` → `activeMode: 1272x2860, refreshRate=60`（大小写两种字段名需兼容） |
| Home | `hdc -t <sn> shell uinput -K -d 1 -u 1` |
| Back | `hdc -t <sn> shell uinput -K -d 2 -u 2` |
| 电源 | `hdc -t <sn> shell uinput -K -d 18 -u 18` |
| **最近任务** | `hdc -t <sn> shell uinput -K -d 2720 -u 2720`（KEYCODE_APPSELECT，实测） |
| 音量+/- | `uinput -K -d 16/17 -u 16/17` |
| 亮屏/解锁 | `hdc -t <sn> shell aa start -a com.ohos.sceneboard -b com.ohos.sceneboard` |
| 唤醒 | `power-shell wakeup`（hosScrcpy 建流时自动执行） |
| 端口转发 | `hdc -t <sn> fport tcp:<local> <remote>`（remote = tcp:5000 或 localabstract:scrcpy_grpc_socket，按 uitest 版本） |
| 设备信息 | `hdc -t <sn> shell param get const.product.{name,manufacturer,software.version}` |

触摸/滑动/拖拽**不走 uinput**（有更好的 uitest 通道），见 §4.2。

## 6. 打包架构

```
CI (GitHub Actions matrix，见 .github/workflows/build.yml)
  windows-latest  → jpackage --type exe   → hnscrcpy-<ver>.exe
  macos-14        → jpackage --type dmg   → hnscrcpy-<ver>.dmg
  macos-13        → jpackage --type dmg   → hnscrcpy-<ver>.dmg

每个 job 内（scripts/build-package.sh / .ps1）：
  1. mvn package；依赖拷贝到 target/libs
     （javacpp/ffmpeg/openblas 仅本平台 classifier；slf4j 等；不含 grpc/netty/protobuf！）
  2. jpackage --input target/libs --main-jar hnscrcpy.jar
     --main-class com.hnscrcpy.Launcher（默认运行时，不 jlink）
  3. 上传 artifact；tag 时发 Release
```

- **hosScrcpy jar 不进包**（华为组件无再分发授权）：首次启动自动扫描
  `~/Library/Application Support/JetBrains/*/plugins/DevecoTesting-Hypium/lib/`（macOS）、
  Windows/Linux 对应插件目录；找不到则引导用户安装 DevEco Testing 插件或手动放置到
  `~/.hnscrcpy/lib/hosScrcpy.jar`（或设 `HOS_SCRCPY_JAR` 环境变量）。
- JavaCPP natives 按平台裁剪（classifier），产物只含本平台 ffmpeg/openblas。
- 应用为非模块化 classpath 应用，JavaFX 走 classpath；入口 `Launcher`（主类继承 Application
  直接启动会报"缺少 JavaFX 运行时组件"）。
- 三平台 hdc 二进制 + libusb 已随 resources 打进应用 jar，运行时释放到 `~/.hnscrcpy/tools/`。
- macOS 签名/公证：v1 自签名 + README 说明；预留证书签名接入。

## 7. 调研项（M0 已全部关闭）

| 编号 | 问题 | 结论 |
|------|------|------|
| R1 | 最近任务键 | **2720**（APPSELECT），实测；2210/187 无效 |
| R2 | HosRemoteConfig 参数 | scale/frameRate/bitRate(Mbps<<20)/port/iFrameInterval/repeatInterval/imageScaleSize |
| R3 | 设备侧机制 | 见 HOS_SCRCPY_PROTOCOL.md：推送 .so → uitest 扩展启动 → gRPC over unix socket |
| R4 | 保持唤醒 | 无需额外命令：建流时 power-shell wakeup；server 端 disable power off render control |
| R5 | 帧格式 | Annex B，4 字节起始码，SPS/PPS 内嵌首包，每消息一帧，按变化推流 |
| R6 | 再分发合规 | 不随包分发；运行时自动发现 JetBrains 插件目录或用户手动放置 |

## 8. 关键风险与缓解（M0 后更新）

| 编号 | 风险 | 缓解 |
|------|------|------|
| R-1 | ~~JavaCV 裸流兼容性~~ | ✅ 已解决：grabber 直播管道阻塞，改 avcodec 直解（send_packet/receive_frame），golden 样本 47 帧全解出并目检通过 |
| R-2 | gRPC UNKNOWN（版本错配） | ✅ M0 已定位：hosScrcpy 自包含 grpc，禁止外部 grpc 依赖；写入 classpath 铁律 |
| R-3 | 按变化推流被误判为断流 | 断流判定基于 gRPC 状态+isOnline，不基于帧间隔；UI 状态栏体现"画面静止" |
| R-4 | isOnline 5s 超时偶发误判 | DeviceMonitor/连接前重试 ≥2 次 |
| R-5 | macOS Gatekeeper 拦截未签名包 | dmg 附安装说明；优先接入开发者证书签名+公证 |
| R-6 | hosScrcpy 再分发合规 | ✅ 结论：不内置，运行时自动发现（协议文档 §9） |
| R-7 | 多设备并发会话 | hosScrcpy 视频端口可配（-p）、本地转发端口随机，但设备侧多实例稳定性未知 → v1 单会话 |
