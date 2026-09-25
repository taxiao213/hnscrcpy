# hnscrcpy — 任务规划

> 前置阅读：[PRD.md](./PRD.md)、[ARCHITECTURE.md](./ARCHITECTURE.md)
> 估时为净开发人日，总计约 **30 人日**。依赖关系用 ← 标注。

## 里程碑总览

| 里程碑 | 内容 | 产出 | 估时 |
|--------|------|------|------|
| M0 | 调研与协议文档化 | 协议文档、调研结论、spike 决策依据 | 3d ✅ |
| M1 | 项目骨架 + HDC 层 | 可运行的设备发现 demo | 4d ✅ |
| M2 | 视频流打通（核心） | 出画面的最小投屏窗口 | 7d ✅ |
| M3 | 远控 | 可操作的投屏窗口 | 4d ✅ |
| M4 | UI/UX 完善 | 产品级体验 | 5d ✅ |
| M5 | 打包 + CI 三平台 | 三平台安装包 | 4d ✅ |
| M6 | 测试加固 + 发布 | 覆盖达标、文档、v1.0 | 3d ✅ |

> 全部里程碑已于 2026-09-24 完成。关键实现偏差（均已在文档中更新）：
> - **JDK 17**（非 21）：本地工具链最高 17.0.19，JavaFX 21 兼容 17+，jpackage 用默认运行时
> - **解码走 avcodec 直解**（非 FFmpegFrameGrabber）：grabber 的 find_stream_info 在裸 h264 直播管道上阻塞到 EOF（fps 估算需读完整流），无法实时；每条 gRPC 消息即完整 AU，无需 demuxer
> - **jpackage 默认运行时**（非 jlink）：JavaFX 以 classpath 方式加载，入口为 `Launcher`（主类不能继承 Application）
> - 控制通道与视频共用同一 HosRemoteDevice 桥（会话级共享），按键走 uinput

---

## M0 调研与协议文档化（3d）—— ✅ 已完成（2026-09-24）

| # | 任务 | 产出 | 状态 |
|---|------|------|------|
| 0.1 | 解包 hosScrcpy jar，梳理设备侧机制 | `docs/HOS_SCRCPY_PROTOCOL.md` | ✅ 全链路逆向完成 |
| 0.2 | 真机抓取 onData 样本帧 | `src/test/resources/sample1.h264`（48 帧 golden 样本，M0 抓取工具已退役） | ✅ Annex B / SPS+PPS 内嵌 / 每消息一帧 / 按变化推流 |
| 0.3 | uinput 最近任务键与唤醒验证 | 最近任务=**2720**（实测，2210/187 无效）；音量 16/17 | ✅ |
| 0.4 | 许可证评估 | 华为 DevEco Testing 插件组件，**不随包分发**，运行时自动发现 | ✅ 结论见协议文档 §9 |
| 0.5 | JavaCV 裸流解码 spike | M0 spike 工具已退役：47 帧 368ms 解完并导出 PNG 目检通过 | ✅ **go** |

**M0 关键发现（影响后续设计）**：
1. hosScrcpy jar 自包含 grpc/netty/protobuf（2023-10），叠加外部 grpc 1.64 会 UNKNOWN（NoSuchMethodError: filterTransport）——classpath 铁律：禁止外部 grpc/netty/protobuf 依赖
2. 触摸/鼠标/滚轮有 uitest JSON-RPC 通道（onTouch*/onMouse*），比 uinput 子进程更优；按键仍走 uinput
3. 视频按屏幕变化推送，静态无帧≠断流
4. 建流自动执行 power-shell wakeup 且 server 禁止息屏，无需 keep-awake 命令
5. hosScrcpy 的 protobuf 消息被 ProGuard 混淆，日志禁止拼接消息对象（toString 崩溃）

## M1 项目骨架 + HDC 层（4d）

| # | 任务 | 产出 | 估时 |
|---|------|------|------|
| 1.1 | Maven 工程初始化：Java 21、JavaFX 21、JavaCV（仅 ffmpeg/openblas 本平台 classifier）、junit；复制三平台 hdc 二进制（来源：参考项目 resources）；.gitignore、git init。**hosScrcpy jar 不进仓库/不进依赖**（M0 结论），运行时经 HosScrcpyLocator 发现 | 可构建骨架 | 0.5d |
| 1.2 | `util/Platform` + `util/ProcessRunner`（含单测：超时、退出码、输出收集） | 基础设施 + 测试 | 1d |
| 1.3 | `device/HdcLocator`：HDC 环境变量 > PATH > 内嵌提取到 `~/.hnscrcpy/tools/<platform>/`，chmod +x | 定位逻辑 + 测试 | 0.5d |
| 1.4 | `device/HdcClient`：list targets / shell / fport / file send·recv；设备信息查询与解析（型号、版本、SP_daemon 屏幕信息） | 客户端 + 解析测试 | 1d |
| 1.5 | `device/DeviceMonitor`：2s 轮询 + 变化监听；`device/DeviceInfo`（真机/模拟器判定） | 监控器 + 测试 | 0.5d |
| 1.6 | `App` + `ui/MainView` 最小版：设备列表展示、刷新 | 可运行 demo | 0.5d |

**验收**：连接真机+模拟器，demo 正确显示设备列表与插拔刷新；`mvn test` 全绿，覆盖率 ≥60%（基建阶段）。

## M2 视频流打通（7d）← M0、M1

| # | 任务 | 产出 | 估时 |
|---|------|------|------|
| 2.1 | `stream/` 接口定义：StreamProvider / FrameSink / VideoConfig | 接口 + javadoc | 0.5d |
| 2.2 | `stream/HosScrcpyProvider`：包装 HosRemoteDevice；启动/停止/重试（pid 找不到 3 次重建重试）；onData → 有界队列；onVideoSize 回调 | provider + 设备联调 | 2d |
| 2.3 | `decode/H264Decoder`：自定义 IO 管道喂 Annex B（spike 已验证 h264 demuxer 兼容；正式实现避免 grabber 文件语义）；SPS/PPS 变更、分辨率变化处理；golden 样本单测（samples/sample1.h264） | 解码器 + golden 流单测 | 1.5d |
| 2.4 | `decode/DecoderPump`：独立线程、有界队列丢旧帧、背压 | pump + 压测记录 | 1d |
| 2.5 | `render/FrameRenderer` + `RenderScheduler`：PixelBuffer 零拷贝、AnimationTimer 取最新帧 | 渲染 + 上屏验证 | 1d |
| 2.6 | `session/MirrorSession` + 状态机 + 清理逻辑（关流/杀设备侧进程/关窗口） | 会话管理 | 1d |

**验收**：真机 USB 连接，双击设备出画面；交互场景实测帧率 ≥30fps（设备支持时）、延迟 <300ms；静态画面不误报断流；拔线后进程不崩溃、资源清理干净。

## M3 远控（4d）← M2

| # | 任务 | 产出 | 估时 |
|---|------|------|------|
| 3.1 | `control/DeviceController`：触摸/鼠标/滚轮 → HosRemoteDevice.onTouch*/onMouse*（uitest 通道，M0 实证优于 uinput）；按键（Home/Back/电源/最近任务2720/音量）→ executeShellCommand(uinput -K)；控制通道可用性自检 | 控制器 + 单测 | 1d |
| 3.2 | `control/CoordinateMapper`：窗口内容区 ↔ 设备坐标（缩放/黑边/旋转），双向换算 | 映射器 + 全分支单测 | 1d |
| 3.3 | `control/InputForwarder`：鼠标点击/滑动/拖拽手势判定，键盘快捷键（MOD 键）；**mouseMove 节流 ≤60Hz**（控制通道为同步 RPC） | 事件转发 | 1d |
| 3.4 | 联调：点击/滑动/拖拽/Home/Back/电源/最近任务全功能真机验证；`--no-control` 模式 | 验收记录 | 1d |

**验收**：鼠标点击误差 ≤2 设备像素；滑动跟手；快捷键全部生效；控制指令端到端延迟 <150ms。

## M4 UI/UX 完善（5d）← M3

| # | 任务 | 产出 | 估时 |
|---|------|------|------|
| 4.1 | 主窗口完善：设备卡片（型号/SN/系统版本）、空态、错误态、连接中状态 | 主窗口 | 1d |
| 4.2 | 投屏窗口：工具栏（返回/Home/多任务/电源/旋转/全屏/置顶/截图）、状态栏（分辨率/帧率/码率/延迟）、工具栏自动隐藏 | 投屏窗口 | 1.5d |
| 4.3 | 设备旋转自适应：分辨率变化 → 解码器重建 + 窗口尺寸/方向跟随 | 旋转支持 | 1d |
| 4.4 | 截图保存（PNG/JPEG 到 ~/Pictures/hnscrcpy 或配置目录） | 截图功能 | 0.5d |
| 4.5 | CLI 参数全量实现（`cli/Arguments` + 直通投屏模式）；设置持久化（config.properties） | CLI + 设置 | 1d |

**验收**：PRD §3.1/§3.2 的 P0+P1 功能清单逐项走查通过（除 F13 多会话如受 R3 限制则降级）。

## M5 打包 + CI 三平台（4d）← M4

| # | 任务 | 产出 | 估时 |
|---|------|------|------|
| 5.1 | 构建脚本：依赖拷贝（本平台 classifier 的 javacpp/ffmpeg/openblas）+ jlink 运行时 + jpackage | `build/` 脚本 + 本地 macOS arm 出包 | 1.5d |
| 5.2 | GitHub Actions matrix 三平台 CI：构建 + artifact 上传 + tag 触发 Release | `.github/workflows/release.yml` | 1.5d |
| 5.3 | macOS 签名/公证方案（自签名 + 文档说明；预留证书签名接入） | 签名文档 + dmg 安装说明 | 0.5d |
| 5.4 | 三平台真机安装验证（干净机器/虚拟机：Windows 10+、macOS Intel、macOS Apple Silicon） | 验证记录 | 0.5d |

**验收**：三平台 Release artifact 下载安装后可正常投屏远控；安装包 ≤250MB。

## M6 测试加固 + 发布（3d）← M5

| # | 任务 | 产出 | 估时 |
|---|------|------|------|
| 6.1 | 覆盖率补足：CoordinateMapper、命令构建、CLI 解析、配置、会话状态机到 80% 行覆盖（设备依赖代码走手工清单） | 覆盖率报告 | 1d |
| 6.2 | 稳定性 soak：连续投屏 2h（内存、句柄、帧率曲线）；异常注入（拔线、关 hdc、设备重启） | 测试报告 + 问题修复 | 1d |
| 6.3 | README（安装/驱动/使用/快捷键/FAQ）、CHANGELOG、v1.0.0 打 tag 发布 | 发布 | 1d |

**验收**：`mvn verify` 全绿；soak 无内存泄漏；三平台 README 走查。

---

## 依赖图

```
M0 ──→ M1 ──→ M2 ──→ M3 ──→ M4 ──→ M5 ──→ M6
        ▲       ▲
        └───────┘（M0 spike 结论直接决定 M2 解码实现）
```

## 并行机会

- M0 的 0.1–0.5 可拆两人并行（协议分析 / 设备实验 / spike）
- M1 的 1.2–1.5 与 M0 部分可并行（不依赖调研结论）
- M4 的 4.1/4.4/4.5 与 M3 可并行
- M5 的 5.2 CI 骨架可在 M4 期间提前搭建

## 测试设备需求（需提前准备）

- HarmonyOS NEXT 真机 ≥1 台（USB 调试开启）—— hosScrcpy 兼容性以此为准
- 官方模拟器（127.0.0.1:5555）—— 回归测试
- Windows 10+ 机器/虚拟机 + 华为 USB 驱动
- macOS Intel 机器/虚拟机（CI macos-13 可部分替代）
