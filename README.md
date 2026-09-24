# hnscrcpy

鸿蒙 NEXT(HarmonyOS NEXT) 投屏远控桌面工具 —— 类似 scrcpy 的体验：
执行一个二进制，即可将设备屏幕实时镜像到 Windows / macOS 窗口，并用键鼠远程操作。

- 视频源：hosScrcpy H.264 流（复用已验证方案）
- 技术栈：Java 21 + JavaFX + JavaCV(FFmpeg) + HDC
- 平台：Windows x64、macOS arm64、macOS x86_64

## 文档

| 文档 | 内容 |
|------|------|
| [docs/PRD.md](docs/PRD.md) | 产品需求、功能清单、CLI 设计 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 架构设计、模块划分、数据流、风险 |
| [docs/TASKS.md](docs/TASKS.md) | 里程碑任务分解（M0–M6，约 30 人日） |
| [docs/HOS_SCRCPY_PROTOCOL.md](docs/HOS_SCRCPY_PROTOCOL.md) | hosScrcpy 协议逆向结论（M0） |

## 状态

M0 已完成（2026-09-24）：协议全链路逆向 + 真机抓流验证 + JavaCV 解码 spike 通过。
关键结论：hosScrcpy 自包含 grpc（禁止外部 grpc 依赖）；触摸走 uitest 通道；
视频按屏幕变化推送；最近任务键 2720；hosScrcpy jar 不随包分发、运行时自动发现。
下一步：M1 项目骨架 + HDC 层。
