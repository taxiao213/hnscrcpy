# Changelog

## v1.0.3（2026-10-25）

### 新增
- 投屏窗口「窗口置顶」菜单项增加 ✓ 勾选态显示（此前无任何状态反馈，状态栏提示又被每秒 FPS 统计覆盖，置顶是否生效不可见）
- ASCII 打字快通道：全部 95 个可打印 ASCII 字符（含大写与移位符号，Shift 组合键实测可用）逐键直发 uinput 键盘事件，单字符延迟 ~900ms → ~130ms；与文本攒批共用控制线程保证「先文后键」顺序；中文与未映射字符仍走 inputText 批量注入。键码表已经 `scripts/spike-keymap.sh` 真机标定（修正：空格=2050 非文档值 2052，方向键=2012-2015 非 2071-2074）；异常设备可用 `-Dhnscrcpy.keyFastPath=false` 整体回退

### 修复
- 看门狗误杀静态画面流（hosScrcpy 1.0.20 起 IDR 请求不再强制推帧）：改为桥内探活 + 输入预言机 + 5 分钟纯静默兜底，画面静止时不再周期性黑屏重连
- use-after-free 闪退：解码器环形缓冲在重连/关窗/截图竞态下被显式释放，渲染线程读到已 unmap 内存致 JVM 段错误；改为永不显式释放（GC 统一管理），restart 清空旧帧槽位
- 打开投屏黑屏 ~10s：会话建立（hdc 初始化 + startCaptureScreen 握手）从 FX 线程移到后台，窗口秒开并显示连接进度；流就绪即主动催 IDR 关键帧缩短首帧等待
- 软键盘输入延迟：打字攒批 150ms→80ms；按键/文本注入改走桥内 executeShellCommand（省本地 hdc 进程开销）；flushSync 上限 5s→2.5s
- 「关于」页版本号硬编码漂移（v1.0.2 显示 1.0.1）：改为构建期从 pom.xml 注入 app.properties，关于页与 --version 自动跟随 tag

### 稳定性
- 全局未捕获异常落盘 + 打包产物加 -XX:ErrorFile 崩溃尸检文件
- start/close/reconnect 同步互斥，调度器关闭后迟来回调安全丢弃
- 重连后解码/丢帧计数器按代清零（状态栏与看门狗判定口径一致）

### 文档
- README 已知限制补充：锁屏后投屏黑屏需先手动解锁（系统安全窗口保护 + 锁屏下触摸/键盘注入被拦截，已实测三条注入通道与截图链路）


## v1.0.2（2026-10-20）

### 新增
- 电脑键盘输入：投屏窗口聚焦后可直接打字（英文/数字实时注入），字符攒批经 `uitest uiInput inputText` 注入设备输入框；Enter/退格/Tab/方向键走 uinput
- 中文输入：菜单「输入文本…」（⌘I）呼出文本框，完整支持中文输入法组词/选字与 ⌘V 粘贴，回车整段注入（投屏画面控件的 IME 直打受 JavaFX 非文本控件管线限制，改用对话框方案）
- 剪贴板粘贴：工具条「粘贴到输入框」按钮 + ⌘V（Win/Linux Ctrl+V），电脑剪贴板文本整段注入设备最后点击的输入框
- 新增 `control/TextInjector`（攒批注入）、`control/KeyInputForwarder`（键盘转发，含 IME 组词压住）；诊断日志新增 TEXT_INJECT_FAILED 事件
- ⚠️ 注入通道与 ESCAPE/方向键键值待真机标定：连接设备后运行 `scripts/spike-text-inject.sh`

### 兼容性
- 设备固件启用 XPM 代码签名管控后，hosScrcpy 旧变体（含模拟器误判路径）无法加载导致投屏零帧：排查结论与 6.8-20260801 变体解法记录于 `docs/HOS_SCRCPY_PROTOCOL.md` §10

## v1.0.1（2026-09-29）

### 修复
- 投屏黑屏/无画面：内置 hosScrcpy 升级至 1.0.20-beta，修复部分设备系统环境更新后设备侧投屏服务崩溃（会话启动数秒即崩、无帧、反复重连）

### 新增
- 全新应用图标：HarmonyOS 风格投屏波纹图标，三平台安装包与运行时窗口图标全部替换（替换 Java 默认图标）
- 诊断日志导出：按设备记录网络/hdc/解码/卡顿等异常事件，设备列表一键分设备导出 zip（结构化报告+日志+环境信息）；设备行尾未读异常徽标；MDC 按设备打标日志
- 打开应用不再自动投屏，双击设备才连接（CLI --serial 仍自动连接）

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
- 静默断流看门狗：每 10s 请求 IDR 心跳，连续两轮无新帧判死重连（设备侧退出/被抢占时 gRPC 可能无任何异常回调）
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
