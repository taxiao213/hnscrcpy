# Changelog

## v1.0.0（2026-09-24）

首个正式版本，M0–M6 全部完成。

### 功能
- 设备自动发现与列表展示（2s 轮询，型号/系统版本）
- H.264 实时投屏：hosScrcpy gRPC 流 → avcodec 直解 → JavaFX PixelBuffer 渲染
- 远控：左键触摸、右/中键鼠标、滚轮、拖拽（移动节流 ≤60Hz）
- 快捷键（macOS ⌘ / Win·Linux Ctrl）：⌘T 置顶 / ⇧⌘S 截图 / ⌘R 旋转 / ⇧⌘= 音量加 / ⌘- 音量减 / ⌘⌫ 返回 / ⇧⌘H 主页 / ⇧⌘O 最近任务
- 右侧悬浮竖排工具条 + ☰ 下拉菜单（样式对标 DevEco Testing 远控面板）：窗口置顶、关于
- 画面圆角裁剪 + 投影，深色背景悬浮布局；音量± 走 uinput（16/17）
- 设备旋转自适应；截图保存 ~/Pictures/hnscrcpy/
- 断流自动重连：视频流异常断开时指数退避重连（1s→10s，最多 5 次），状态栏提示重连进度
- CLI：--serial / --max-size(预留) / --bit-rate / --fps / --no-control / --help / --version
- 配置持久化 ~/.hnscrcpy/config.properties

### 工程
- 三平台 hdc + libusb 内置，运行时释放到 ~/.hnscrcpy/tools/
- hosScrcpy jar 运行时发现（env / ~/.hnscrcpy/lib / JetBrains 插件目录），不进包
- jpackage 打包：macOS dmg、Windows exe；GitHub Actions 三平台 CI
- 82 个单元测试，行覆盖率 83%（jacoco，UI/会话装配层除外）
- golden 样本（sample1.h264，48 帧真机采集）解码回归测试

### 关键技术结论
- FFmpegFrameGrabber 在裸 h264 直播管道上 find_stream_info 阻塞到 EOF，
  改用 avcodec send_packet/receive_frame 直解（每 gRPC 消息即完整 AU）
- hosScrcpy jar 自包含 grpc/netty/protobuf，禁止外部 grpc 依赖（classpath 铁律）
- JavaFX classpath 模式下主类不得继承 Application，入口使用 Launcher
- 最近任务键 = uinput 2720（真机实测）
- 浸泡测试（2h 连续滑动/按键）暴露视频流 ~9 分钟被设备侧断开（UNAVAILABLE），
  客户端无感知画面冻结 —— 已补断流检测 + 自动重连，重连前等待 2s 防设备侧实例冲突
