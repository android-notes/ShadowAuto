import { Adb, AdbDaemonTransport, LinuxFileType } from '@yume-chan/adb';
import AdbWebCredentialStore from '@yume-chan/adb-credential-web';
import { AdbDaemonWebUsbDevice, AdbDaemonWebUsbDeviceManager } from '@yume-chan/adb-daemon-webusb';
import type { MaybeConsumable, ReadableStream as AdbReadableStream } from '@yume-chan/stream-extra';

const credentialStore = new AdbWebCredentialStore('ShadowAuto');
const devicePath = '/data/local/tmp/silent-shell.apk';
const controllerPath = '/data/local/tmp/shadowauto-controller.apk';
const controllerComponent = 'com.silentauto.controller/.MainActivity';
const pidPath = '/data/local/tmp/silent-auto.pid';
const processMarker = 'com.silentauto.shell.Main';
const ocrRoot = '/data/local/tmp/shadowauto/ocr';
const ocrFiles = [
  'lib/arm64-v8a/libNative.so',
  'lib/arm64-v8a/libc++_shared.so',
  'lib/arm64-v8a/libomp.so',
  'lib/arm64-v8a/libhiai.so',
  'lib/arm64-v8a/libhiai_ir.so',
  'lib/arm64-v8a/libhiai_ir_build.so',
  'models/ch_PP-OCRv2/det_db.nb',
  'models/ch_PP-OCRv2/rec_crnn.nb',
  'models/ch_PP-OCRv2/cls.nb',
  'labels/ppocr_keys_v1.txt'
];

export type LaunchProgress =
  | { action: 'selectDevice' }
  | { action: 'connectDevice'; name: string }
  | { action: 'download'; name: string }
  | { action: 'upload'; name: string }
  | { action: 'skip'; name: string }
  | { action: 'startShell' }
  | { action: 'installController' }
  | { action: 'openController' };

export type LaunchProgressCallback = (progress: LaunchProgress) => void;

export function supported() {
  return Boolean(AdbDaemonWebUsbDeviceManager.BROWSER);
}

export async function launchShell(report: LaunchProgressCallback = () => {}) {
  const manager = AdbDaemonWebUsbDeviceManager.BROWSER;
  if (!manager) {
    throw new Error('WebUSB ADB is not supported');
  }
  report({ action: 'selectDevice' });
  const selected = await manager.requestDevice();
  if (!selected) {
    throw new Error('No device selected');
  }
  report({ action: 'connectDevice', name: selected.serial });
  const adb = await connect(selected);
  try {
    await push(adb, '/silent-shell.apk', devicePath, 'silent-shell.apk', report);
    await pushOcrRuntime(adb, report);
    report({ action: 'startShell' });
    await shell(adb, startCommand());
    await push(adb, '/controller-app.apk', controllerPath, 'controller-app.apk', report);
    report({ action: 'installController' });
    await shell(adb, installControllerCommand());
    report({ action: 'openController' });
    await shell(adb, openControllerCommand());
  } finally {
    await adb.close();
  }
}

async function connect(selected: AdbDaemonWebUsbDevice) {
  const connection = await selected.connect();
  const transport = await AdbDaemonTransport.authenticate({
    serial: selected.serial,
    connection,
    credentialStore
  });
  return new Adb(transport);
}

async function push(adb: Adb, url: string, path: string, name: string, report: LaunchProgressCallback) {
  report({ action: 'download', name });
  const response = await fetch(url);
  if (!response.ok || !response.body) {
    throw new Error(`download failed: ${response.status}`);
  }
  report({ action: 'upload', name });
  const sync = await adb.sync();
  try {
    await sync.write({
      filename: path,
      file: response.body as unknown as AdbReadableStream<MaybeConsumable<Uint8Array>>,
      type: LinuxFileType.File,
      permission: 0o644,
      mtime: Math.floor(Date.now() / 1000)
    });
  } finally {
    await sync.dispose();
  }
}

async function pushOcrRuntime(adb: Adb, report: LaunchProgressCallback) {
  await shell(adb, `mkdir -p ${quote(ocrRoot)}`);
  for (const file of ocrFiles) {
    await pushOptional(adb, `/ocr/${file}`, `${ocrRoot}/${file}`, `ocr/${file}`, report);
  }
}

async function pushOptional(adb: Adb, url: string, path: string, name: string, report: LaunchProgressCallback) {
  report({ action: 'download', name });
  const response = await fetch(url);
  if (response.status === 404) {
    report({ action: 'skip', name });
    return;
  }
  if (!response.ok || !response.body) {
    throw new Error(`download failed: ${response.status}`);
  }
  await shell(adb, `mkdir -p ${quote(parentPath(path))}`);
  report({ action: 'upload', name });
  const sync = await adb.sync();
  try {
    await sync.write({
      filename: path,
      file: response.body as unknown as AdbReadableStream<MaybeConsumable<Uint8Array>>,
      type: LinuxFileType.File,
      permission: 0o644,
      mtime: Math.floor(Date.now() / 1000)
    });
  } finally {
    await sync.dispose();
  }
}

async function shell(adb: Adb, command: string) {
  const protocol = adb.subprocess.shellProtocol;
  if (!protocol?.isSupported) {
    throw new Error('ADB shell protocol unsupported');
  }
  const result = await protocol.spawnWaitText(command);
  if (result.exitCode !== 0) {
    throw new Error(result.stderr || result.stdout || `exit ${result.exitCode}`);
  }
  return result.stdout;
}

function startCommand() {
  const killPrevious = [
    `if [ -f ${pidPath} ]; then kill $(cat ${pidPath}) 2>/dev/null; rm -f ${pidPath}; fi`,
    `self=$$; for p in /proc/[0-9]*; do pid=$(basename "$p"); if [ "$pid" = "$self" ]; then continue; fi; cmd=$(tr '\\0' ' ' < "$p/cmdline" 2>/dev/null || true); case "$cmd" in *${processMarker}*) kill "$pid" 2>/dev/null || true;; esac; done`
  ].join('; ');
  const env = `CLASSPATH=${quote(devicePath)}`;
  const main = 'app_process /system/bin com.silentauto.shell.Main --port=43110';
  return `${killPrevious}; ${env} nohup setsid ${main} >/data/local/tmp/silent-auto.log 2>&1 </dev/null & echo $! > ${pidPath}; echo started`;
}

function installControllerCommand() {
  return `pm install -r -t -d ${quote(controllerPath)}`;
}

function openControllerCommand() {
  return `am start -n ${quote(controllerComponent)}`;
}

function parentPath(path: string) {
  const index = path.lastIndexOf('/');
  if (index <= 0) {
    return '/';
  }
  return path.slice(0, index);
}

function quote(value: string) {
  return `'${(value || '').replace(/'/g, `'\\''`)}'`;
}
