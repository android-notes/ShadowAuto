# 隐控 ShadowAuto

[English](README.md) | 中文

隐控 ShadowAuto 是一个 Android 后台静默自动化原型。它通过 `adb shell` 和 `app_process` 启动 AI 驱动的 shell 进程，在独立 VirtualDisplay 中运行目标应用，获取 UI 状态，向该虚拟屏注入输入事件，并把执行进度和虚拟投屏实时回传到 Android 控制端。

## 工程结构

- `android-shell`：通过 `app_process` 启动的 shell 自动化进程。负责创建 VirtualDisplay、启动应用、获取 UiAutomation 布局、注入输入、操作剪贴板、执行 AI tool-call 循环、提供 JSON-RPC、输出日志和推送投屏帧。
- `controller-app`：Android 控制端 App。用于配置大模型、输入任务目标、查看虚拟投屏、查看进度日志、启动任务、停止当前任务或停止所有任务。
- `web-launcher`：Svelte + TangoADB WebUSB 启动器。页面只展示一个启动按钮，点击后选择设备、上传 shell APK、杀死旧 shell 进程并启动新的 `app_process`。
- `android-stubs`：shell 模块编译时使用的 Android hidden API stub，仅用于编译。
- `scripts`：本地测试脚本。

## 环境要求

- Android 10 及以上。
- 目标设备已开启 ADB 调试。
- 默认只需要 shell 权限，不需要 root。
- JDK 17。
- Web 启动器需要 Node.js 和 npm。
- `web-launcher` 需要 Chrome 或其他支持 WebUSB 的浏览器。
- 一个兼容 OpenAI chat-completions 的大模型接口，并支持 streaming 和 tool calls。

## 编译

编译 shell 和控制端 App：

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug
```

把最新 shell APK 复制到 Web 启动器静态目录：

```sh
cp android-shell/build/outputs/apk/debug/android-shell-debug.apk web-launcher/static/silent-shell.apk
```

安装控制端 App：

```sh
adb install -r controller-app/build/outputs/apk/debug/controller-app-debug.apk
```

## 启动 Shell 进程

### 方式一：浏览器启动器

```sh
cd web-launcher
npm install
npm run dev
```

用 Chrome 打开终端输出的本地地址，点击 **启动隐控Assistant**，选择 Android 设备，并按 Android 弹窗授权 ADB。

如果浏览器提示设备已被其他程序占用，请关闭 Android Studio 并杀死 adb 后再次重试：

```sh
adb kill-server
```

### 方式二：手动 ADB

```sh
adb push android-shell/build/outputs/apk/debug/android-shell-debug.apk /data/local/tmp/silent-shell.apk
adb shell "if [ -f /data/local/tmp/silent-auto.pid ]; then kill \$(cat /data/local/tmp/silent-auto.pid) 2>/dev/null; rm -f /data/local/tmp/silent-auto.pid; fi"
adb shell "CLASSPATH=/data/local/tmp/silent-shell.apk nohup setsid app_process /system/bin com.silentauto.shell.Main --port=43110 >/data/local/tmp/silent-auto.log 2>&1 </dev/null & echo \$! >/data/local/tmp/silent-auto.pid"
```

查看 shell 日志：

```sh
adb logcat -v time -s ShadowAutoShell
```

停止 shell 进程：

```sh
adb shell "kill \$(cat /data/local/tmp/silent-auto.pid) 2>/dev/null; rm -f /data/local/tmp/silent-auto.pid"
```

## 使用 Android App

1. 在手机上打开隐控。
2. 首次进入时填写 API Key、API URL 和模型。填写 API Key 和 URL 后，App 可以自动拉取可用模型。
3. 点击测试连接。模型正常响应后，配置会被保存。
4. 输入任务目标，例如：`用淘宝闪购给我买杯星巴克香草拿铁，大杯`。
5. 点击执行。任务输入框会隐藏，开始显示虚拟投屏、停止按钮和进度日志。
6. 点击虚拟投屏区域，可以在弹窗中放大查看。
7. 点击停止可以停止当前任务；也可以点击右上角停止按钮，一键停止所有任务。
8. 点击右下角加号可以创建并行任务。并行任务过多会导致手机发热或卡顿。

## 核心原理

隐控采用 shell 侧 ReAct 风格自动化循环：

1. shell 进程通过 `adb shell` + `app_process` 启动，因此可以在 shell 权限下访问 Android 系统服务和 hidden API。
2. 每个自动化任务启动时，shell 创建一个 VirtualDisplay，并把目标 App 启动到该虚拟屏中。
3. 控制端 App 通过 `127.0.0.1:43110` 向 shell 发送 JSON-RPC `startTask` 请求，请求中包含用户目标和大模型配置。
4. shell 请求大模型，让模型根据目标和当前 UI 状态进行推理，并通过 tool call 选择下一步动作。
5. tool call 会执行真实 UI 操作：获取布局、点击目标节点、点击坐标、聚焦输入框、输入文本、全选、删除、粘贴、滚动、拖拽、返回、回车/搜索、等待或完成任务。
6. UiAutomation 负责读取虚拟显示屏上的 UI 树。shell 可以返回简化后的可操作布局，也可以返回完整布局。
7. InputManager 把触摸和按键事件注入到目标 display，而不是手机主屏幕。
8. 虚拟屏画面和进度日志通过本机 JSON-RPC 事件实时推送到 Android App。
9. 这个循环会持续执行，直到模型调用 `finish`、用户停止任务，或发生错误。

这种架构可以让目标应用在虚拟屏里运行，用户则在控制端 App 中观察进度，不需要把自动化操作暴露在主屏幕上。

## JSON-RPC 概览

shell 只监听本机回环地址：

```text
127.0.0.1:43110
```

主要方法：

- `startTask`：启动一个自动化任务，参数包括 `taskId`、`goal`、`apiKey`、`apiBase`、`model`。
- `stopTask`：传入 `taskId` 时停止指定任务；不传时停止全部任务。
- `status`：返回当前 shell 状态。
- `logs`：返回最近日志。
- `ping`：健康检查。

当前 JSON-RPC 没有鉴权，因为它只绑定本机回环地址，并且设计上只给本机控制端 App 使用。不要把该端口暴露到外部网络。

## 注意事项和限制

- 这是自动化研究原型，不是生产级助手。
- 大模型服务商会收到任务目标和自动化所需的 UI 上下文。请不要在不可信模型服务中处理敏感内容。
- 受 Android 安全限制，部分受保护页面在投屏时可能黑屏，例如支付密码页面。
- 同时执行多个任务可能会修改剪贴板内容，导致输入失败或文本错乱。
- 自动化运行时，不要在实体屏幕点击正在自动化的 App 图标，也不要从最近任务打开该 App，否则 App 可能离开虚拟屏，导致任务失败。
- hidden API 和 shell 权限行为可能因 Android 版本、厂商系统而变化。
- 浏览器启动器需要独占 WebUSB 设备。Android Studio 或正在运行的 adb server 可能会占用设备。

## 开发测试

连接测试设备或模拟器后：

```sh
node scripts/emulator-mock-ai-test.mjs
```

## 开源协议

隐控 ShadowAuto 使用 [Apache License 2.0](LICENSE) 开源。
