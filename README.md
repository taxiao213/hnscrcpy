# hnscrcpy

鸿蒙 NEXT(HarmonyOS NEXT) 投屏远控桌面工具 —— 类似 scrcpy 的体验：
执行一个二进制，即可将设备屏幕实时镜像到 Windows / macOS 窗口，并用键鼠远程操作。

- 视频源：hosScrcpy H.264 流（gRPC over hdc forward，按屏幕变化推流）
- 解码：FFmpeg/avcodec 直解（零 demuxer 延迟）
- 技术栈：Java 17 + JavaFX 21 + JavaCPP(FFmpeg) + HDC
- 平台：Windows x64、macOS arm64、macOS x86_64

## 功能

- 设备列表自动发现（USB / 模拟器），双击投屏
- 实时镜像（60fps 配置上限，H.264 硬编），窗口等比缩放
- 远控：左键触摸、右键/中键鼠标事件、滚轮、拖拽
- 快捷键（macOS ⌘ / Win·Linux Ctrl）：`⌘T` 窗口置顶、`⇧⌘S` 截图、`⌘R` 旋转、`⇧⌘=` 音量加、`⌘-` 音量减、`⌘⌫` 返回、`⇧⌘H` 主页、`⇧⌘O` 最近任务
- 右侧悬浮工具条：截图 / 旋转 / 电源 / 音量± / 返回·主页·最近任务；☰ 菜单含置顶与关于
- 截图保存到 `~/Pictures/hnscrcpy/`
- 设备旋转自适应（横竖屏切换自动跟随）
- 断流自动重连（指数退避，状态栏可见重连进度）
- CLI：`--serial / --bit-rate / --fps / --no-control`（`--help` 查看全部）

## 使用前准备（重要）

视频流依赖华为 DevEco Testing（Hypium）JetBrains 插件中的 **hosScrcpy jar**。
该组件无再分发授权，**不随本程序分发**，程序启动时自动发现：

1. 环境变量 `HOS_SCRCPY_JAR` 指向该 jar；或
2. jar 位于 `~/.hnscrcpy/lib/`（自动取最新版本）；或
3. 已安装 DevEco Testing 插件的 IDE（JetBrains 系，含 DevEco Studio），
   自动扫描插件目录。

找不到时会给出引导提示。hdc 无需单独安装：程序内置三平台 hdc，
运行时释放到 `~/.hnscrcpy/tools/`（已安装 DevEco Studio 或 PATH 中有 hdc 时优先使用）。

## 安装与运行

### 安装包（推荐）

从 Releases 下载对应平台安装包：

- macOS：`hnscrcpy-<ver>.dmg`（首次打开如提示"未受信任的开发者"，右键 → 打开）
- Windows：`hnscrcpy-<ver>.exe`

### 从源码运行

```bash
mvn javafx:run                 # 开发模式
mvn test                       # 单元测试（含 golden 样本解码）
./scripts/build-package.sh     # macOS/Linux 出包（target/dist/）
scripts\build-package.ps1      # Windows 出包
```

CLI 示例：

```bash
hnscrcpy --serial 5KRUT25421010222 --bit-rate 30 --fps 60
hnscrcpy --no-control          # 只看不控
```

## 文档

| 文档 | 内容 |
|------|------|
| [docs/PRD.md](docs/PRD.md) | 产品需求、功能清单、CLI 设计 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 架构设计、模块划分、数据流、风险 |
| [docs/TASKS.md](docs/TASKS.md) | 里程碑任务分解（M0–M6 全部完成） |
| [docs/HOS_SCRCPY_PROTOCOL.md](docs/HOS_SCRCPY_PROTOCOL.md) | hosScrcpy 协议逆向结论（M0） |

## 状态

**v1.0.0（2026-09-24）**：M0–M6 全部完成，真机（MRT-AL10 / HarmonyOS NEXT 6.1）验证通过。

- 视频管线：gRPC 流 → avcodec 直解 → PixelBuffer 零拷贝渲染，实测 60fps 无丢帧
- 远控：触摸/鼠标/滚轮/按键（uitest 通道 + uinput）真机验证
- 打包：jpackage（dmg/exe），GitHub Actions 三平台 CI
- 测试：82 个单元测试，行覆盖率 83%（UI/会话装配层由真机冒烟验证）

## 已知限制

- 一屏一设备（多会话未支持）
- `-m/--max-size` 为预留参数，视频恒为设备原始分辨率
- 模拟器（127.0.0.1:5555）兼容性未实测
