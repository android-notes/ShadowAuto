import { Adb, AdbDaemonTransport, LinuxFileType } from '@yume-chan/adb';
import AdbWebCredentialStore from '@yume-chan/adb-credential-web';
import { AdbDaemonWebUsbDevice, AdbDaemonWebUsbDeviceManager } from '@yume-chan/adb-daemon-webusb';
import type { MaybeConsumable, ReadableStream as AdbReadableStream } from '@yume-chan/stream-extra';

const credentialStore = new AdbWebCredentialStore('ShadowAuto');
const devicePath = '/data/local/tmp/silent-shell.apk';
const pidPath = '/data/local/tmp/silent-auto.pid';
const processMarker = 'com.silentauto.shell.Main';

export function supported() {
  return Boolean(AdbDaemonWebUsbDeviceManager.BROWSER);
}

export async function launchShell() {
  const manager = AdbDaemonWebUsbDeviceManager.BROWSER;
  if (!manager) {
    throw new Error('WebUSB ADB is not supported');
  }
  const selected = await manager.requestDevice();
  if (!selected) {
    throw new Error('No device selected');
  }
  const adb = await connect(selected);
  try {
    await push(adb, '/silent-shell.apk', devicePath);
    await shell(adb, startCommand());
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

async function push(adb: Adb, url: string, path: string) {
  const response = await fetch(url);
  if (!response.ok || !response.body) {
    throw new Error(`download failed: ${response.status}`);
  }
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

function quote(value: string) {
  return `'${(value || '').replace(/'/g, `'\\''`)}'`;
}
