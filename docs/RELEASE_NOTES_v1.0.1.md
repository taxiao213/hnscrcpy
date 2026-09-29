# hnscrcpy v1.0.1

鸿蒙 NEXT（HarmonyOS NEXT）投屏远控桌面工具，类似 scrcpy 的体验：**打开即用，零外部环境依赖**——JRE、hdc、投屏组件全部内置，无需安装 Java 或任何开发环境。

## 🔧 本版修复

- **修复投屏黑屏/无画面**：部分设备系统环境更新后，hosScrcpy 1.0.15-beta 的设备侧投屏服务会在会话启动数秒后崩溃（画面无帧、反复重连）。内置组件升级至 **hosScrcpy 1.0.20-beta** 后实测恢复正常

## ✨ 新增

- **诊断日志导出**（设备列表底部「📤 导出诊断日志」）：
  - 自动记录每台设备的异常事件：网络/流错误、重连过程、hdc 命令失败、解码异常（花屏代理信号）、卡顿看门狗、设备上下线
  - 分设备导出 zip：结构化异常时间线报告 + 运行日志 + 环境信息，一键反馈问题
  - 设备行尾红色徽标实时提示未读异常数，点击直接导出该设备
  - 「📁 打开日志文件夹」快速访问原始日志
- 连接方式调整：打开应用不再自动连上次的设备，**双击设备列表中的设备**才会投屏（CLI `--serial` 显式指定时仍自动连接）

## 📦 下载

| 平台 | 文件 |
|------|------|
| macOS Apple Silicon | `hnscrcpy-v1.0.1-macOS-arm64.dmg` |
| macOS Intel | `hnscrcpy-v1.0.1-macOS-x86_64.dmg` |
| Windows x64 | `hnscrcpy-v1.0.1-windows-x64-setup.exe` |
| Linux x86_64 | `hnscrcpy-v1.0.1-linux-x64.deb` / `.tar.gz` |

## ⚠️ 已知不足

- **不支持鸿蒙模拟器**（仅支持 USB 真机连接）
- **不支持音频转发**（投屏组件无音频通道，仅画面+远控）
- macOS 未公证：首次打开如提示"未受信任的开发者"，请 **右键 → 打开**

---

本项目代码以 [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) 授权；内置的 hosScrcpy / hdc 组件版权归华为所有，仅供个人学习研究、禁止商用，详见 THIRD-PARTY-NOTICES.md。
