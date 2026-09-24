# hosScrcpy 协议与工作机制（M0 逆向结论）

> 来源：反编译 `hosScrcpy-1.0.15-beta.jar`（CFR 0.152）+ 真机（MRT-AL10，HarmonyOS NEXT，
> uitest 6.0.2.3，屏幕 1272x2860@60Hz）实测验证。
> 验证工具：`tools/sample-capture`（抓流）、`tools/decode-spike`（解码）。

## 1. 组件总览

设备侧有两个独立组件，客户端通过 HDC 端口转发与它们通信：

```
┌─ Mac/PC ─────────────────────────────────────────────┐
│ hosScrcpy.jar (HosRemoteDevice)                       │
│   │ gRPC(HTTP/2)          │ 裸 TCP(JSON RPC)           │
│   ▼                       ▼                           │
│ 127.0.0.1:D ──fport──┐   127.0.0.1:B/C ──fport──┐     │
└──────────────────────┼────────────────────────────┼───┘
                       ▼                            ▼
┌─ 设备 ───────────────────────────────────────────────┐
│ scrcpy server (libscreen_casting.z.so)  uitest daemon│
│  unix abstract: scrcpy_grpc_socket      (agent.so)   │
│  gRPC: ScrcpyService                    tcp:8012 /   │
│                                         unix:uitest_socket │
└──────────────────────────────────────────────────────┘
```

| 组件 | 设备侧形态 | 作用 | 通道 |
|------|-----------|------|------|
| scrcpy server | `/data/local/tmp/libscreen_casting.z.so`（由 jar 内 `libscrcpy/*.z.so` 推送） | 屏幕采集 + H.264 编码 + gRPC 流服务 | gRPC over unix abstract socket `scrcpy_grpc_socket`（新版）或 tcp:5000（旧版，已 deprecated） |
| uitest daemon + agent | `/system/bin/uitest`（系统自带）+ `/data/local/tmp/agent.so`（jar 内 `uitest_agent_*.so` 推送） | 触摸/鼠标/滚轮注入、旋转、布局抓取、JPEG 截图 | 裸 TCP JSON RPC，设备端口 8012 或 unix `uitest_socket` |

## 2. 启动流程（HosRemoteDevice.startCaptureScreen）

1. **在线检查**：`hdc -s <ip>:<hdcPort> -t <sn> list targets` 输出包含 sn（hdcPort 默认 8710，即本地 hdc server 端口；超时仅 5s，偶发误判 → M2 加重试）。
2. **选本地端口**：随机取 36000–36999 作为 gRPC 本地转发端口 D。
3. **版本门槛**：`shell /system/bin/uitest --version` ≥ 4.1.4.6，否则报 "system version is not supported"。
4. **视频 server 选择**（按 md5 匹配，避免重复推送）：
   - 设备已有 `/data/local/tmp/libscreen_casting.z.so` 的 md5 与 jar 内某变体一致 → 直接启动；
   - 否则按 uitest 版本选变体推送（`hdc file send`）：
     - uitest ≥ 6.0.2.1：`libscrcpy_server_unix_6.3.1/6.4/6.5-*.z.so`（本机 6.0.2.3 → 6.5）
     - 更老：`libscrcpy_server1/2/3.z.so`、`libscrcpy_server_5.10-*.z.so`
     - 模拟器/云手机（存在 `/system/lib64/libCPHMediaEngine.z.so`）：`libscrcpy_server_emulator.z.so`
5. **启动视频 server**（后台）：
   ```
   /system/bin/uitest start-daemon singleness --extension-name libscreen_casting.z.so \
       -scale 1 -frameRate 30 -bitRate 8388608 -p 5000 -iFrameInterval 2000 &
   ```
   参数由 `HosRemoteConfig.getParams()` 生成：`bitRate << 20`（Mbps→字节）。hilog 确认：
   6.5 变体**只监听 unix abstract socket `scrcpy_grpc_socket`**，`-p 5000` 已 deprecated。
6. **端口转发**（按 uitest 版本二选一）：
   - uitest ≥ 6.0.2.1：`hdc fport tcp:<D> localabstract:scrcpy_grpc_socket`
   - 更老：`hdc fport tcp:<D> tcp:5000`
7. **gRPC 建流**：`ManagedChannelBuilder.forTarget("dns:///127.0.0.1:<D>").usePlaintext()`，
   `maxInboundMessageSize=100MB`，调用 `ScrcpyService/onStart`（server-streaming，请求 Empty）。
8. **保活**：连接建立后执行 `shell power-shell wakeup` 点亮屏幕；hilog 显示 server 端
   "disable power off render control success"（采集期间禁止息屏，无需额外 keep-awake 命令）。

## 3. 视频流协议

**proto（`scrcpy.proto`，无 package）**：
```proto
service ScrcpyService {
  rpc onStart (Empty) returns (stream ReplyMessage);   // 视频流
  rpc onEnd (Empty) returns (ReplyEndMessage);          // 停止
  rpc onRequestIDRFrame (Empty) returns (ReplyEndMessage); // 请求关键帧
}
message Empty {}
message ParamValue { bytes valBytes = ?; ... }        // 实际含 valBytes 等字段
message ReplyMessage { map<string, ParamValue> payload = 1; }  // keys: data/pts/flags/len
```

**帧载荷**：`ReplyMessage.payload["data"].valBytes` =  Annex B H.264。实测（samples/sample1.h264）：

- 4 字节起始码 `00 00 00 01`；**每个 gRPC 消息 = 一帧**（首消息例外：SPS+PPS 合包 33 字节）
- 首帧序列：SPS(7)+PPS(8) → IDR(5) → P(1)×N；SPS/PPS 内嵌，无需额外 extradata
- **按屏幕变化推流**：静态画面不发帧（实测静置 7s 无帧属正常，非断流）；交互时 30fps 满帧
- 实测吞吐：滑动场景 ~0.3 Mbps（8Mbps 上限内），延迟低

**已知坑（关键！）**：hosScrcpy jar **自包含全套 grpc/netty/protobuf/fastjson/guava（2023-10，netty 4.1.100）**。
classpath 上再叠加外部 grpc 1.64 等依赖会版本错配，报：
```
io.grpc.StatusRuntimeException: UNKNOWN
  cause: NoSuchMethodError: io.grpc.internal.ManagedClientTransport$Listener.filterTransport
```
→ **运行期只允许 hosScrcpy jar 自带的 grpc 类，禁止再引外部 grpc/netty/protobuf 依赖。**
（参考项目 harmony-screencap 的 pom 恰好叠加了 grpc 1.64 —— 其 phone 模式在本机必然UNKNOWN，
"can not find scrcpy pid" 的锅很可能部分源于此。）

**protobuf 反射坑**：hosScrcpy 的 protobuf 生成类被 ProGuard 混淆，消息 `toString()`（TextFormat）
会抛 `IllegalStateException: missing method setResult` —— 不影响序列化，**日志里不要拼接消息对象**。

## 4. 控制协议（uitest agent 通道）

初始化（`startCaptureScreen` 时自动完成，executor C 异步执行）：
1. 选 agent 变体：`file /system/bin/uitest` 含 x86_64 → `uitest_agent_x86_1.1.9.so`；
   否则按 uitest 版本：≥5.1.1.2 → `1.1.3`、=5.1.1.3 → `1.1.5`、≥6.0.2.1 → `1.1.12`、其他 → `1.2.3`
2. 比对设备端 agent.so 内嵌版本号（`cat agent.so | grep -a UITEST_AGENT_LIBRARY`），旧则强杀 uitest 重推
3. 启动 `uitest start-daemon singleness &`；建立 3 条转发（随机本地端口 B/C/D）：
   - agent < 1.2.0：`fport tcp:<port> tcp:8012`（本机 1.1.12 走此路径）
   - agent ≥ 1.2.0：`fport tcp:<port> localabstract:uitest_socket`
   - B=控制 socket，C=layout socket，D=JPEG 截图 socket

**控制消息**（B socket，裸 JSON，无帧头；每发一条同步等一条响应）：
```json
{"module":"com.ohos.devicetest.hypiumApiHelper","method":"Gestures",
 "params":{"api":"touchDown","args":{"x":600,"y":2000}}}
```
- 触摸：`api = touchDown / touchMove / touchUp`
- 鼠标：`ButtonLeftDown/Up/Move`、`ButtonRightDown/Up/Move`、`ButtonMiddleDown/Up/Move`、`MouseMove`
- 滚轮：`AxisUp / AxisDown / AxisStop`
- 旋转：`api = "Driver.setDisplayRotation"`，`args = [1|0]`（1=横屏 0=竖屏）
- 布局抓取（C socket，带帧头 `_uitestkit_rpc_message_head_` + magic 1145141919 + len + JSON + `_tail_`）：
  `api = "captureLayout"`
- JPEG 截图（D socket，同帧头协议）：`api = "startCaptureScreen"`（本工具不用）

**注意**：控制是**同步 RPC**（发→阻塞等响应），高频 mouseMove 需在客户端节流（M3 设计）。

**按键注入不在此通道**（无 home/back/power API）→ 仍走 `hdc shell uinput -K` 子进程，低频可接受。

## 5. uinput 按键表（本机实测）

| 功能 | 命令 | 验证 |
|------|------|------|
| Home | `uinput -K -d 1 -u 1` | 参考项目沿用 |
| Back | `uinput -K -d 2 -u 2` | 参考项目沿用 |
| 电源 | `uinput -K -d 18 -u 18` | 参考项目沿用 |
| **最近任务** | **`uinput -K -d 2720 -u 2720`**（KEYCODE_APPSELECT） | ✅ 本机实测打开最近任务；2210(VIRTUAL_MULTITASK)/187 无效 |
| 音量+/- | `uinput -K -d 16/17 -u 16/17` | ✅ 本机接受 |
| 点击 | `uinput -T -c <x> <y> 10` | 参考项目沿用 |
| 滑动 | `uinput -T -m <x1> <y1> <x2> <y2> 50` | 参考项目沿用 |
| 亮屏/解锁 | `aa start -a com.ohos.sceneboard -b com.ohos.sceneboard` | 参考项目沿用 |
| 唤醒 | `power-shell wakeup` | ✅ hosScrcpy 内置 |

## 6. 屏幕信息

- `shell SP_daemon -screen` → `activeMode: 1272x2860, refreshRate=60`（注意字段名大小写两种写法都见过：
  `refreshrate`/`refreshRate`，解析需兼容）
- `getScreenSize()` 内部用 `snapshot_display -f /data/local/tmp/screen.jpeg` 输出里的
  `width: N, height: N`（或 `width N, height N`），兜底 1344x2776

## 7. 清理（stopCaptureScreen）

1. `fport rm` 视频与控制全部转发
2. `ps -ef | grep singleness` 找到 `--extension-name` 进程，`kill -9`
3. 关 gRPC channel、uitest socket；参考实现 stop 后 sleep 2s 等设备侧退出，防新旧实例冲突

## 8. hosScrcpy 公开 API 摘要（hnscrcpy 将使用的）

| API | 用途 |
|-----|------|
| `startCaptureScreen(ScreenCapCallback)` | 启动视频流（onReady/onData/onException） |
| `stopCaptureScreen()` | 停止并清理 |
| `onTouchDown/Up/Move(x,y)` | 触摸注入（uitest 通道） |
| `onMouseDown/Up/Move(btn,x,y)` | 鼠标注入（btn=mouseLeft/mouseRight/mouseMiddle） |
| `onMouseWheelUp/Down/Stop(x,y)` | 滚轮 |
| `setRotationHorizontal/Vertical()` | 旋转 |
| `requestIDRFrame()` | 请求关键帧（解码器丢帧恢复用） |
| `getScreenSize(boolean)` | 屏幕尺寸 |
| `executeShellCommand(cmd, timeout)` | 任意 shell（uinput 按键走这里） |
| `isOnline()` | 在线检查（5s 超时，偶发误判需重试） |

`HosRemoteConfig` 可调：`scale / frameRate / bitRate(Mbps, 内部<<20) / port / iFrameInterval /
imageScaleSize / extensionName / ip / hdcPort / hdcPath`。

## 9. 分发合规结论（R6）

- hosScrcpy-1.0.15-beta.jar 是华为 **DevEco Testing (Hypium) JetBrains 插件**的组件
  （实测来源：`~/Library/Application Support/JetBrains/<IDE>/plugins/DevecoTesting-Hypium/lib/`），
  jar 内 LICENSE.txt 仅含 Checker Framework MIT 声明，**无华为再分发授权**。
- 结论：**hnscrcpy 不内置该 jar**。运行时自动发现（扫描 JetBrains 插件目录），
  未发现则引导用户安装 DevEco Testing 插件或手动放置。设备侧 .so（libscrcpy_server*、uitest_agent*）
  同属华为组件，随 jar 提取推送，不单独分发。
