# ShadowAuto

English | [中文](README.zh-CN.md)

ShadowAuto is an open-source Android prototype similar to Doubao Phone. It supports any Android 10 or later device and can likewise implement background silent automation on Android phones. It starts an AI-driven shell process through `adb shell` and `app_process`, launches real Android apps inside an isolated VirtualDisplay, and lets automation run quietly in the background while the physical screen remains usable.

ShadowAuto is not just a click script. It gives the AI a phone-assistant-style loop for understanding and operating the screen: the shell process can read the UiAutomation UI node tree for the virtual display, fall back to offline OCR when accessibility nodes are missing or incomplete, and inject touch, key, clipboard, and text input events into the target display.

The Chinese product name is **隐控**.

## Key Capabilities

- Background silent automation: target apps run inside a VirtualDisplay without occupying the physical screen.
- AI tool-call control: the model repeatedly chooses tools such as tap, input, scroll, back, search, wait, and finish.
- UI node reading: UiAutomation returns windows, node trees, editable fields, and actionable targets for the target display.
- OCR visual fallback: Paddle Lite OCR reads visible text and bounding boxes on self-rendered or accessibility-opaque pages.
- Display-targeted input injection: touch, key, clipboard, and IME operations are injected into the virtual display, not the main display.
- Real-time virtual screen streaming: the VirtualDisplay is streamed to the controller app as H.264 video.
- Parallel tasks: the controller app can create multiple independent task pages, each with its own goal, screen stream, and progress log.

## Demo Video

<video src="shadowauto.mp4" controls="controls" style="max-width: 100%;"></video>

If the current page cannot play the embedded video, open [shadowauto.mp4](shadowauto.mp4).

## Technical Deep Dive

- [Android Virtual Display and AI Background Automation](docs/android-virtual-display-ai-automation.en.md)
- [Android 虚拟投屏与 AI 后台静默自动化实践](docs/android-virtual-display-ai-automation.md) (Chinese)

## Project Layout

- `android-shell`: shell-side automation process started by `app_process`. It owns VirtualDisplay creation, app launching, UI node reading, OCR fallback, input injection, clipboard operations, AI tool-call loop, JSON-RPC, logs, and H.264 virtual screen streaming.
- `controller-app`: Android app used to configure the model, enter automation goals, watch the virtual screen, read progress logs, start tasks, and stop one or all tasks.
- `web-launcher`: Svelte + TangoADB WebUSB launcher. It shows one launch button, lets the user select a device in the browser, uploads the shell APK, OCR files, and controller APK, then starts `app_process`.
- `android-stubs`: compile-only Android hidden API stubs used by the shell module.
- `scripts`: local test helpers.

## Requirements

- Any Android 10 or later device.
- ADB debugging enabled on the target device.
- Shell permission is enough; root is not required.
- JDK 17.
- Node.js and npm for the web launcher.
- Chrome or another WebUSB-capable browser for `web-launcher`.
- An OpenAI-compatible chat-completions endpoint that supports streaming and tool calls.

## Build

Build the shell and controller app:

```sh
./gradlew :android-shell:assembleDebug :controller-app:assembleDebug
```

Sync the latest shell APK and OCR runtime files into the web launcher static directory:

```sh
./gradlew :android-shell:syncWebLauncherArtifacts
```

Install the controller app:

```sh
adb install -r controller-app/build/outputs/apk/debug/controller-app-debug.apk
```

## Start The Shell Process

### Option 1: Browser Launcher

Online launcher: [https://android-notes.github.io/ShadowAuto/](https://android-notes.github.io/ShadowAuto/)

Before connecting, prepare the phone:

- Enable Developer options and USB debugging.
- Some Xiaomi phones also require USB debugging (Security settings).
- On first connection, the phone may show an Allow USB debugging prompt; tap Allow on the phone.
- When installing the controller app, tap Allow install if the phone shows an install prompt.
- After installation, configure an API key in the ShadowAuto app before using phone automation.
- Developer option references: [Xiaomi](https://jingyan.baidu.com/article/ce436649ca6c877773afd3e2.html), [Huawei](https://jingyan.baidu.com/article/a378c960e87118b3282830bc.html), [OPPO](https://jingyan.baidu.com/article/cb5d6105b0936a005d2fe052.html), [VIVO](https://jingyan.baidu.com/article/335530da406f4358cb41c3b4.html).

You can also run it locally:

```sh
cd web-launcher
npm install
npm run dev
```

Open the printed local URL in Chrome, click **Start ShadowAuto Assistant**, select the Android device, and authorize ADB if Android asks.

If the browser reports that the device is already in use, close Android Studio and kill adb, then retry:

```sh
adb kill-server
```

### Option 2: Manual ADB

```sh
./gradlew :android-shell:syncWebLauncherArtifacts
adb push web-launcher/static/silent-shell.apk /data/local/tmp/silent-shell.apk
adb shell "rm -rf /data/local/tmp/shadowauto/ocr && mkdir -p /data/local/tmp/shadowauto/ocr"
adb push web-launcher/static/ocr/. /data/local/tmp/shadowauto/ocr/
adb shell "if [ -f /data/local/tmp/silent-auto.pid ]; then kill \$(cat /data/local/tmp/silent-auto.pid) 2>/dev/null; rm -f /data/local/tmp/silent-auto.pid; fi"
adb shell "CLASSPATH=/data/local/tmp/silent-shell.apk nohup sh -c 'exec app_process /system/bin com.silentauto.shell.Main --port=43110' >/data/local/tmp/silent-auto.log 2>&1 </dev/null & echo \$! >/data/local/tmp/silent-auto.pid"
```

The OCR files are required for the `get_screen_ocr` tool. If you only push `silent-shell.apk`, normal automation can start, but OCR fallback will fail when the target page has no accessibility nodes.

If logcat shows `ClassNotFoundException: com.silentauto.shell.Main` after startup, `silent-shell.apk` was usually not pushed to the device. Check it first:

```sh
adb shell "ls -lh /data/local/tmp/silent-shell.apk"
```

When multiple devices are connected, add `-s <serial>` to every command, for example `adb -s emulator-5554 push ...`.

View shell logs:

```sh
adb logcat -v time -s ShadowAutoShell
```

Stop the shell process:

```sh
adb shell "kill \$(cat /data/local/tmp/silent-auto.pid) 2>/dev/null; rm -f /data/local/tmp/silent-auto.pid"
```

## Use The Android App

1. Launch ShadowAuto on the phone.
2. On first launch, enter API Key, API URL, and model. The app can load available models after API Key and URL are entered.
3. Tap the test button. If the model responds normally, the config is saved.
4. Enter a goal, for example: `Use Meituan Waimai to order me a grande Starbucks vanilla latte`.
5. Tap Run. The task input hides, and the virtual screen, Stop button, and progress logs appear.
6. Tap the virtual screen preview to open a larger preview dialog.
7. Tap Stop to stop the current task, or use the top-right stop button in the controller app to stop all tasks.
8. Tap the floating plus button to create another parallel task. Too many parallel tasks may heat up or slow down the phone.

## Core Principle

ShadowAuto uses a shell-owned ReAct-style automation loop:

1. The shell process starts under `adb shell` by `app_process`, enabling access to shell-level Android services and hidden APIs.
2. For each automation task, the shell creates a VirtualDisplay and launches the target Android app on that display.
3. The controller app sends a JSON-RPC `startTask` request to `127.0.0.1:43110` with the user goal and model config.
4. The shell asks the AI model to reason about the goal and use tools.
5. Tool calls perform real UI operations: dump UI layout, tap targets, tap coordinates, focus inputs, input text, select all, delete, paste clipboard, scroll, drag, press Back, press Enter/Search, wait, or finish.
6. UiAutomation observes the UI node tree for the virtual display. The shell can return a compact actionable layout or a full layout dump.
7. When UI nodes are empty, wrong, or insufficient, the AI can call `get_screen_ocr`; the shell uses Paddle Lite OCR to return visible text, confidence, and bounding boxes.
8. InputManager injects touch and key events into the target display instead of the main phone display.
9. The VirtualDisplay renders into a MediaCodec input surface. The shell streams H.264 config and samples to the Android app, which decodes them into a TextureView; progress logs are sent through the same local JSON-RPC event channel.
10. The loop repeats until the model calls `finish`, the user stops the task, or an error occurs.

This architecture keeps the automated app off the main display while still allowing the user to monitor progress from the controller app.

## Paddle Lite Offline OCR

The `paddler-ocr` module contains the PaddleOCR Android integration for visual fallback on self-rendered or accessibility-opaque pages. The shell process calls this module directly; the controller app does not contain OCR code. OCR is enabled by default; build or copy its runtime files with:

```sh
./gradlew :paddler-ocr:preparePaddleLiteOcr
./gradlew :android-shell:copyPaddleLiteOcrRuntime
```

The prepare task downloads PaddleLite v2.10, OpenCV Android SDK, the PP-OCRv2 Chinese mobile models, and labels. Generated files live under `paddler-ocr/PaddleLite`, `paddler-ocr/OpenCV`, and `paddler-ocr/PaddleOcrAssets`; they are ignored by git.

The Java entry point is `com.silentauto.paddlerocr.PaddleOcrEngine`. After `init()`, call `recognize(Bitmap)` to get text, confidence, and bounding boxes. Runtime files are copied to `android-shell/build/ocr-runtime` and uploaded to `/data/local/tmp/shadowauto/ocr` by the web launcher.

## JSON-RPC Overview

The shell listens on loopback only:

```text
127.0.0.1:43110
```

Main methods:

- `startTask`: starts one automation task with `taskId`, `goal`, `apiKey`, `apiBase`, and `model`.
- `stopTask`: stops a specific task when `taskId` is provided, or all tasks when it is omitted.
- `status`: returns current shell status.
- `logs`: returns recent logs.
- `ping`: health check.

The server is intentionally unauthenticated because it binds to local loopback and is designed for a local controller app. Do not expose this port externally.

## Notes And Limitations

- The AI provider receives the task goal and UI context needed for automation. Do not use it with sensitive content unless you trust the configured provider.
- Some protected pages may appear black in screen streaming because of Android security restrictions, such as payment password screens.
- Running multiple tasks can change clipboard contents and may cause input failure or text mix-ups.
- Do not tap or open the app being automated from the physical display or Recents while the task is running; doing so can move the app away from the virtual display and break automation.
- Hidden APIs and shell permissions can vary between Android versions and vendors.
- The browser launcher needs exclusive WebUSB access to the device. Android Studio or a running adb server may block it.

## Development Test

With a test device or emulator connected:

```sh
node scripts/emulator-mock-ai-test.mjs
```

## License

ShadowAuto is licensed under the [Apache License 2.0](LICENSE).
