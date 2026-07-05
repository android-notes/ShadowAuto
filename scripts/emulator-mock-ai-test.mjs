import { execFileSync } from 'node:child_process';
import http from 'node:http';
import net from 'node:net';

const serial = process.env.ADB_SERIAL || 'emulator-5554';
const port = 43110;
const mockPort = 18180;
const apk = 'android-shell/build/outputs/apk/debug/android-shell-debug.apk';
const pidPath = '/data/local/tmp/silent-auto.pid';
const deviceApk = '/data/local/tmp/silent-shell.apk';
const taskId = 'test-1';

function run(command, args, options = {}) {
  const output = execFileSync(command, args, { encoding: 'utf8', stdio: options.stdio || 'pipe' });
  return output == null ? '' : output.trim();
}

function adb(...args) {
  return run('adb', ['-s', serial, ...args]);
}

function sse(res, payloads) {
  res.writeHead(200, { 'Content-Type': 'text/event-stream' });
  for (const payload of payloads) {
    res.write(`data: ${JSON.stringify(payload)}\n\n`);
  }
  res.write('data: [DONE]\n\n');
  res.end();
}

function content(text) {
  return { choices: [{ delta: { content: text } }] };
}

function tool(index, name, args) {
  return {
    choices: [{
      delta: {
        tool_calls: [{
          index,
          id: `call_${index}`,
          type: 'function',
          function: { name, arguments: args }
        }]
      }
    }]
  };
}

let toolStep = 0;
let sawTypedTextInUi = false;
let sawImeOnVirtualDisplay = false;
let sawWindowDumpMeta = false;
let sawScrollTool = false;
let sawExtraTools = false;
const server = http.createServer((req, res) => {
  let body = '';
  req.on('data', chunk => body += chunk);
  req.on('end', () => {
    if (!body.includes('"tools"')) {
      sse(res, [content('{"packageName":"com.android.settings","reason":"mock"}')]);
      return;
    }
    toolStep += 1;
    if (toolStep >= 3 && (body.includes('\\"text\\":\\"wifi\\"') || body.includes('"text":"wifi"'))) {
      sawTypedTextInUi = true;
    }
    if (body.includes('com.google.android.inputmethod')) {
      sawImeOnVirtualDisplay = true;
    }
    if (body.includes('windowSource') && body.includes('availableDisplays')) {
      sawWindowDumpMeta = true;
    }
    if (body.includes('scroll_ui')) {
      sawScrollTool = true;
    }
    if (body.includes('press_key') && body.includes('long_press') && body.includes('drag') && body.includes('clear_text')) {
      sawExtraTools = true;
    }
    if (toolStep === 1) {
      sse(res, [
        tool(0, 'focus_input', '{"inputIndex":0,'),
        tool(0, '', '"query":"search",'),
        tool(0, '', '"reason":"mock focus search input"}')
      ]);
    } else if (toolStep === 2) {
      sse(res, [
        tool(0, 'wait', '{"ms":1000,'),
        tool(0, '', '"reason":"mock model incorrectly waits after focusing search"}')
      ]);
    } else if (toolStep === 3) {
      sse(res, [
        tool(0, 'get_ui_layout', '{"mode":"simple",'),
        tool(0, '', '"reason":"verify typed text"}')
      ]);
    } else {
      sse(res, [
        content('done by mock'),
        tool(0, 'finish', '{"reason":"mock finish"}')
      ]);
    }
  });
});

function rpc(request, timeoutMs = 45000) {
  return new Promise((resolve, reject) => {
    const client = net.createConnection(port, '127.0.0.1');
    let data = '';
    let failed = null;
    const timer = setTimeout(() => {
      client.destroy();
      reject(new Error(`rpc timeout\n${data}`));
    }, timeoutMs);
    client.on('data', chunk => {
      data += chunk.toString();
      if (data.includes('automation finished') || data.includes('automation failed')) {
        client.end();
      }
    });
    client.on('connect', () => client.write(`${JSON.stringify(request)}\n`));
    client.on('close', () => {
      clearTimeout(timer);
      if (failed) {
        reject(failed);
        return;
      }
      resolve(data);
    });
    client.on('error', error => {
      failed = error;
    });
  });
}

function statusRequest(timeoutMs = 3000) {
  return new Promise((resolve, reject) => {
    const client = net.createConnection(port, '127.0.0.1');
    let data = '';
    let failed = null;
    const timer = setTimeout(() => {
      client.destroy();
      reject(new Error('status timeout'));
    }, timeoutMs);
    client.on('data', chunk => {
      data += chunk.toString();
      if (data.includes('"id":2')) {
        client.end();
      }
    });
    client.on('connect', () => client.write('{"jsonrpc":"2.0","id":2,"method":"status","params":{}}\n'));
    client.on('close', () => {
      clearTimeout(timer);
      if (failed) {
        reject(failed);
      } else {
        resolve(data);
      }
    });
    client.on('error', error => {
      failed = error;
    });
  });
}

async function waitForShell() {
  const started = Date.now();
  let lastError = null;
  while (Date.now() - started < 10000) {
    try {
      const status = await statusRequest(1000);
      if (status.includes('"state"')) {
        return status;
      }
    } catch (error) {
      lastError = error;
      await new Promise(resolve => setTimeout(resolve, 300));
    }
  }
  throw lastError || new Error('shell did not become ready');
}

async function main() {
  run('./gradlew', [':android-shell:assembleDebug'], { stdio: 'inherit' });
  adb('push', apk, deviceApk);
  adb('shell', `if [ -f ${pidPath} ]; then kill $(cat ${pidPath}) 2>/dev/null; rm -f ${pidPath}; fi; CLASSPATH=${deviceApk} nohup setsid app_process /system/bin com.silentauto.shell.Main --port=${port} >/data/local/tmp/silent-auto.log 2>&1 </dev/null & echo $! >${pidPath}`);
  adb('forward', `tcp:${port}`, `tcp:${port}`);
  await waitForShell();

  await new Promise(resolve => server.listen(mockPort, '127.0.0.1', resolve));
  adb('reverse', `tcp:${mockPort}`, `tcp:${mockPort}`);

  const output = await rpc({
    jsonrpc: '2.0',
    id: 1,
    method: 'startTask',
    params: {
      taskId,
      goal: '打开系统设置，搜索wifi，然后结束。',
      apiKey: 'mock',
      apiBase: `http://127.0.0.1:${mockPort}/v1`,
      model: 'mock'
    }
  });

  const status = await statusRequest();

  server.close();

  const checks = [
    ['startTask taskId result', output.includes(`"taskId":"${taskId}"`)],
    ['taskId event routing', output.includes(`"taskId":"${taskId}"`)],
    ['frame event', output.includes('"type":"frame"')],
    ['virtual display local IME enabled', output.includes('virtual display local IME enabled')],
    ['window dump display metadata', sawWindowDumpMeta],
    ['scroll tool available', sawScrollTool],
    ['extra interaction tools available', sawExtraTools],
    ['tool_call focus_input', output.includes('tool_call focus_input')],
    ['auto input after search focus', output.includes('auto input after search focus: wifi')],
    ['text set or pasted', output.includes('text set by accessibility') || output.includes('text pasted from clipboard')],
    ['typed text visible in UI', sawTypedTextInUi],
    ['tool_call finish', output.includes('tool_call finish')],
    ['automation finished', output.includes('automation finished')],
    ['idle status', status.includes('"state":"idle"') && status.includes('"displayId":-1')]
  ];
  const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
  if (failed.length) {
    console.error(output);
    console.error(status);
    throw new Error(`failed checks: ${failed.join(', ')}`);
  }
  console.log('mock AI tool-call emulator test passed');
}

main().catch(error => {
  server.close();
  console.error(error.stack || error.message);
  process.exit(1);
});
