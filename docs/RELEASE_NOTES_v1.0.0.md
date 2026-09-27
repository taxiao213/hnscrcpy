# hnscrcpy v1.0.0 — 首个正式版

鸿蒙 NEXT（HarmonyOS NEXT）投屏远控桌面工具，类似 scrcpy 的体验：**打开即用，零外部环境依赖**——JRE、hdc、投屏组件全部内置，无需安装 Java 或任何开发环境。

> 本文件由 CI 的 release job 按 tag 读取（`body_path: docs/RELEASE_NOTES_<tag>.md`），作为 GitHub Release 正文。

## ✨ 功能一览

**投屏**
- 设备自动发现（USB 真机），双击即投屏，支持多台设备同时投屏
- H.264 硬编实时镜像，最高 60fps 可配（码率可调）
- avcodec 多线程直解 + 零拷贝渲染，高负载实测 50–57fps **零丢帧**
- 窗口等比缩放；设备横竖屏旋转时窗口与触控坐标自动跟随
- 一键截图，保存至 `~/Pictures/hnscrcpy/`
- 断流自动重连（指数退避 + 看门狗心跳，状态栏可见进度）

**远控**
- 鼠标：左键触摸 / 右键 / 中键 / 滚轮 / 拖拽
- 侧边悬浮工具栏：截图、旋转、电源、音量±、返回、主页、最近任务
- 快捷键齐全（macOS ⌘ / Win·Linux Ctrl）：置顶 `⌘T`、截图 `⇧⌘S`、旋转 `⌘R` 等
- 支持「只看不控」模式（`--no-control`）

**平台与分发**
- macOS（Apple Silicon / Intel）、Windows x64、Linux x86_64 四平台安装包
- macOS 免安装 `.app`、Windows 安装程序（开始菜单+桌面快捷方式）、Linux deb / 免安装 tar.gz
- CLI 参数：`--serial / --bit-rate / --fps / --no-control`

## 🔧 本版修复

- **修复投屏卡顿风暴**：关闭投屏窗口后残留僵尸会话，与活跃会话互踢设备侧投屏服务，导致画面卡顿、延迟升高、反复重连；现所有关闭路径均彻底清理会话
- **修复降采样缩放器逐帧重建**：窗口亚像素抖动导致每帧重建缩放缓冲（约 18MB/次），解码吞吐被 GC 拖垮；量化 + 迟滞后仅在窗口尺寸真实变化时重建
- 同一设备重复双击只聚焦已有投屏窗口，不再开出第二个互踢会话

## ⚠️ 已知不足

- **不支持鸿蒙模拟器**（仅支持 USB 真机连接）
- **不支持音频转发**（投屏组件无音频通道，仅画面+远控）
- 视频恒为设备原始分辨率（窗口缩放由客户端渲染层处理）
- macOS 未公证：首次打开如提示"未受信任的开发者"，请 **右键 → 打开**

## 📦 下载

| 平台 | 文件 |
|------|------|
| macOS Apple Silicon | `hnscrcpy-v1.0.0-macOS-arm64.dmg` |
| macOS Intel | `hnscrcpy-v1.0.0-macOS-x86_64.dmg` |
| Windows x64 | `hnscrcpy-v1.0.0-windows-x64-setup.exe` |
| Linux x86_64 | `hnscrcpy-v1.0.0-linux-x64.deb` / `.tar.gz` |
