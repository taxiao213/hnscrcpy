# hnscrcpy v1.0.2

鸿蒙 NEXT（HarmonyOS NEXT）投屏远控桌面工具，类似 scrcpy 的体验：**打开即用，零外部环境依赖**——JRE、hdc、投屏组件全部内置，无需安装 Java 或任何开发环境。

## ✨ 新增

- **电脑键盘输入**：投屏窗口聚焦后直接打字，英文/数字实时注入手机当前输入框（最后点击处）；Enter / 退格 / Tab / 方向键走 uinput 注入
- **中文输入（「输入文本…」对话框，⌘I）**：呼出独立文本框，完整支持中文输入法组词/选字与 ⌘V 粘贴，支持多行编辑预览，回车整段发送。投屏画面控件的 IME 直打受 JavaFX 非文本控件管线限制，中文/长文本请走此对话框
- **剪贴板粘贴到手机（⌘V）**：复制电脑上的文本 → 点击投屏画面 → ⌘V 或工具条「粘贴到输入框」按钮，整段注入设备输入框；Win/Linux 为 Ctrl+V
- 新增文本注入基础设施 `TextInjector`（150ms 攒批、换行拆段段间回车、单引号转义、超长分片）与键盘转发器 `KeyInputForwarder`（⌘/Ctrl 组合键不拦截，既有快捷键不受影响）
- 诊断日志新增文本注入失败事件（TEXT_INJECT_FAILED），便于反馈问题

## 🔧 修复

- **投屏零帧/反复重连（设备固件兼容性）**：部分设备固件更新后启用 XPM 代码签名管控，hosScrcpy 推送的设备侧投屏服务（含模拟器误判路径推送的变体）被内核拒绝加载，导致会话建立后零帧、看门狗反复重连。排查结论与设备侧解法（使用 `libscrcpy_server_unix_6.8-20260801` 变体）已记录在 `docs/HOS_SCRCPY_PROTOCOL.md` §10

## 📦 下载

| 平台 | 文件 |
|------|------|
| macOS Apple Silicon | `hnscrcpy-v1.0.2-macOS-arm64.dmg` |
| macOS Intel | `hnscrcpy-v1.0.2-macOS-x86_64.dmg` |
| Windows x64 | `hnscrcpy-v1.0.2-windows-x64-setup.exe` |
| Linux x86_64 | `hnscrcpy-v1.0.2-linux-x64.deb` / `.tar.gz` |

## ⚠️ 已知不足

- **不支持鸿蒙模拟器**（仅支持 USB 真机连接）
- **不支持音频转发**（投屏组件无音频通道，仅画面+远控）
- **手机 → 电脑剪贴板暂不支持**：鸿蒙对剪贴板文本读取做了隐私限制，shell 层无公开通道（写入方向已通过文本注入实现）
- ESCAPE / 方向键的 uinput 键值为文档值未真机标定，异常时请运行 `scripts/spike-text-inject.sh` 标定
- macOS 未公证：首次打开如提示"未受信任的开发者"，请 **右键 → 打开**

---

本项目代码以 [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) 授权；内置的 hosScrcpy / hdc 组件版权归华为所有，仅供个人学习研究、禁止商用，详见 THIRD-PARTY-NOTICES.md。
