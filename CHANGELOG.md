# Changelog

## v1.0.0（2026-09-24）

首个正式版本，M0–M6 全部完成。

### 功能
- 设备自动发现与列表展示（2s 轮询，型号/系统版本）
- H.264 实时投屏：hosScrcpy gRPC 流 → avcodec 直解 → JavaFX PixelBuffer 渲染
- 远控：左键触摸、右/中键鼠标、滚轮、拖拽（移动节流 ≤60Hz）
- 快捷键：H 主页 / B 返回 / R 最近任务 / P 电源 / Ctrl+S 截图
- 工具栏：主页 / 返回 / 最近任务 / 电源 / 截图 / 旋转
- 设备旋转自适应；截图保存 ~/Pictures/hnscrcpy/
- CLI：--serial / --max-size(预留) / --bit-rate / --fps / --no-control / --help / --version
- 配置持久化 ~/.hnscrcpy/config.properties

### 工程
- 三平台 hdc + libusb 内置，运行时释放到 ~/.hnscrcpy/tools/
- hosScrcpy jar 运行时发现（env / ~/.hnscrcpy/lib / JetBrains 插件目录），不进包
- jpackage 打包：macOS dmg、Windows exe；GitHub Actions 三平台 CI
- 68 个单元测试，行覆盖率 83%（jacoco，UI/会话装配层除外）
- golden 样本（sample1.h264，48 帧真机采集）解码回归测试

### 关键技术结论
- FFmpegFrameGrabber 在裸 h264 直播管道上 find_stream_info 阻塞到 EOF，
  改用 avcodec send_packet/receive_frame 直解（每 gRPC 消息即完整 AU）
- hosScrcpy jar 自包含 grpc/netty/protobuf，禁止外部 grpc 依赖（classpath 铁律）
- JavaFX classpath 模式下主类不得继承 Application，入口使用 Launcher
- 最近任务键 = uinput 2720（真机实测）
