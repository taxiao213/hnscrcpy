# THIRD-PARTY NOTICES — 第三方组件声明

本文件列出 hnscrcpy 发行包中包含的第三方组件及其许可信息。

**重要**：hnscrcpy 自有代码以 [Apache License 2.0](LICENSE) 授权；下述第三方组件的许可条款独立于本项目授权，以其自身许可为准。

---

## 第一节 华为闭源组件（不属于 Apache-2.0 授权范围）

以下组件版权归 **华为技术有限公司（Huawei）** 所有，随 hnscrcpy 发行包分发**未获得华为的再分发授权**：

| 组件 | 来源 |
|------|------|
| `hosScrcpy-1.0.15-beta.jar`（含内置的华为设备侧原生库 `libscrcpy_server*.z.so`、`uitest_agent*.so`） | 华为 DevEco Testing (Hypium) JetBrains 插件组件 |
| `hdc` 命令行工具（linux-64 / mac-arm64 / mac-x64 / windows-x64 四平台二进制，含 libusb） | HarmonyOS 设备连接器 |

特此声明：

1. 上述组件的著作权与一切权利归华为所有，其使用条款由华为保留；
2. 它们**不在**本项目 Apache-2.0 授权范围内，本项目的开源许可不构成对它们的任何授权；
3. 本工具仅供**个人学习与研究**使用，**禁止商用**；如需商业用途，请自行向华为获取相应授权；
4. 若权利人认为本项目的分发方式侵犯其权益，请联系作者（yin13753884368@163.com），将立即移除相关组件。

`hosScrcpy-1.0.15-beta.jar` 为 shaded（重打包）jar，其中嵌入了以下第三方库，均以各自许可授权：

| 库 | 许可证 |
|----|--------|
| gRPC、Netty、Guava、Gson、fastjson / fastjson2、perfmark、error-prone / j2objc / JSR-305 注解、Google API stubs | Apache-2.0 |
| Protocol Buffers | BSD-3-Clause |
| Checker Framework | MIT（jar 内 `META-INF/LICENSE.txt` 全文见附录 A） |

---

## 第二节 FFmpeg（LGPL-3.0）

发行包含有 bytedeco 构建的 **FFmpeg 7.1** 共享库（libav* dylib/dll 及 ffmpeg/ffprobe 命令行工具），以 **GNU Lesser General Public License v3.0（LGPL-3.0）** 授权（构建配置：`--enable-shared --enable-version3`，未启用 GPL 组件）。

- 源码：https://github.com/bytedeco/javacpp-presets （tag `ffmpeg-7.1-1.5.11`，上游 http://ffmpeg.org）
- 本项目以动态链接方式使用 FFmpeg；你可以在 LGPL-3.0 条款下替换或重新链接这些库
- LGPL-3.0 全文见附录 B

FFmpeg 构建中随带的编解码库按其上游许可授权：OpenSSL（Apache-2.0）、OpenH264（BSD-2）、libvpx / libaom / SVT-AV1 / libwebp / Opus / Speex（BSD）、libxml2（MIT）、libmp3lame（LGPL）、Opencore-AMR / vo-AMRWBENC（Apache-2.0）、SRT（MPL-2.0）。

---

## 第三节 Maven 运行时依赖

| 组件 | 版本 | 许可证 |
|------|------|--------|
| JavaFX（base / graphics / controls） | 21.0.4 | GPLv2 + Classpath Exception（https://openjdk.java.net/legal/gplv2+ce.html） |
| JavaCPP（经 FFmpeg preset 传递引入） | 1.5.11 | Apache-2.0 |
| slf4j-api | 2.0.13 | MIT |
| logback-classic / logback-core | 1.5.6 | EPL-1.0 或 LGPL-2.1（此处依 EPL-1.0，https://www.eclipse.org/legal/epl-v10.html） |

测试范围依赖（不随包分发）：JUnit Jupiter 5.10.2（EPL-2.0）、AssertJ 3.25.3（Apache-2.0）。

---

## 附录 A — Checker Framework MIT 许可（hosScrcpy jar 内 LICENSE.txt 原文）

```
Checker Framework qualifiers
Copyright 2004-present by the Checker Framework developers

Licensed under the MIT License. https://opensource.org/licenses/MIT
```

## 附录 B — GNU Lesser General Public License v3.0

本程序随附分发 FFmpeg 时须提供 LGPL-3.0 许可文本。全文见：
https://www.gnu.org/licenses/lgpl-3.0.html

（LGPL-3.0 基于 GPL-3.0 附加补充条款构成，GPL-3.0 全文见 https://www.gnu.org/licenses/gpl-3.0.html）
