<script lang="ts">
  import { onMount, tick } from 'svelte';
  import { launchShell, supported, type LaunchProgress } from './lib/tango-adb';

  type Locale = 'zh' | 'en';
  type Copy = {
    title: string;
    start: string;
    starting: string;
    deviceBusy: string;
    unsupported: string;
    ready: string;
    repository: string;
    setupTitle: string;
    setupNotes: string[];
    developerGuideTitle: string;
    progress(progress: LaunchProgress): string;
    failed(message: string): string;
  };

  type GuideLink = {
    name: string;
    url: string;
  };

  type ProgressItem = {
    time: string;
    text: string;
  };

  const developerGuides: GuideLink[] = [
    { name: 'Xiaomi', url: 'https://jingyan.baidu.com/article/ce436649ca6c877773afd3e2.html' },
    { name: 'Huawei', url: 'https://jingyan.baidu.com/article/a378c960e87118b3282830bc.html' },
    { name: 'OPPO', url: 'https://jingyan.baidu.com/article/cb5d6105b0936a005d2fe052.html' },
    { name: 'VIVO', url: 'https://jingyan.baidu.com/article/335530da406f4358cb41c3b4.html' }
  ];
  const repositoryUrl = 'https://github.com/android-notes/ShadowAuto';

  const copies: Record<Locale, Copy> = {
    zh: {
      title: 'ShadowAuto 启动器',
      start: '启动隐控Assistant',
      starting: '启动中...',
      deviceBusy: '设备已被其他程序占用，请关闭 Android Studio 并杀死 adb 后再次重试。',
      unsupported: '当前浏览器不支持 WebUSB ADB，请使用 Chrome 或 Edge，并通过 HTTPS 或 localhost 打开。',
      ready: '现在隐控 App 已打开。你需要先配置 API KEY 才能使用手机自动化；配置完成后输入自动化任务并点击执行。',
      repository: 'GitHub 仓库主页',
      setupTitle: '连接前准备',
      setupNotes: [
        '请先打开手机开发者选项，并开启 USB 调试。',
        '部分小米手机还需要开启 USB 调试（安全设置）。',
        '首次连接时，手机上可能会弹出允许调试提示，请点击允许。',
        '安装控制端 App 时，如果手机弹出允许安装提示，请点击允许安装。'
      ],
      developerGuideTitle: '开发者选项参考',
      progress: progressTextZh,
      failed: (message: string) => `启动失败：${message}`
    },
    en: {
      title: 'ShadowAuto Launcher',
      start: 'Start ShadowAuto Assistant',
      starting: 'Starting...',
      deviceBusy: 'The device is already in use. Please close Android Studio and kill adb, then retry.',
      unsupported: 'This browser does not support WebUSB ADB. Please use Chrome or Edge over HTTPS or localhost.',
      ready: 'The ShadowAuto app is now open. Configure an API key before using phone automation, then enter a task and tap Run.',
      repository: 'GitHub Repository',
      setupTitle: 'Before Connecting',
      setupNotes: [
        'Enable Developer options and USB debugging on the phone first.',
        'Some Xiaomi phones also require USB debugging (Security settings).',
        'On first connection, tap Allow on the phone when the USB debugging prompt appears.',
        'When installing the controller app, tap Allow install if the phone shows an install prompt.'
      ],
      developerGuideTitle: 'Developer option guides',
      progress: progressTextEn,
      failed: (message: string) => `Failed to start: ${message}`
    }
  };

  let busy = false;
  let errorText = '';
  let readyText = '';
  let progressElement: HTMLOListElement | null = null;
  let progressItems: ProgressItem[] = [];
  let locale: Locale = 'en';

  $: copy = copies[locale];

  onMount(() => {
    locale = detectLocale();
    document.documentElement.lang = locale === 'zh' ? 'zh-CN' : 'en';
  });

  async function start() {
    if (!supported()) {
      errorText = copy.unsupported;
      readyText = '';
      progressItems = [];
      return;
    }
    busy = true;
    errorText = '';
    readyText = '';
    progressItems = [];
    try {
      await launchShell(addProgress);
      readyText = copy.ready;
    } catch (error) {
      errorText = friendlyError(error);
    } finally {
      busy = false;
    }
  }

  async function addProgress(progress: LaunchProgress) {
    progressItems = [...progressItems, { time: formatTime(new Date()), text: copy.progress(progress) }];
    await tick();
    scrollProgressToBottom();
  }

  function scrollProgressToBottom() {
    if (progressElement) {
      progressElement.scrollTop = progressElement.scrollHeight;
    }
  }

  function formatTime(date: Date) {
    const hours = padTime(date.getHours());
    const minutes = padTime(date.getMinutes());
    const seconds = padTime(date.getSeconds());
    return `${hours}:${minutes}:${seconds}`;
  }

  function padTime(value: number) {
    return String(value).padStart(2, '0');
  }

  function detectLocale(): Locale {
    const languages = navigator.languages?.length ? navigator.languages : [navigator.language];
    for (const language of languages) {
      const normalized = language.toLowerCase();
      if (normalized.startsWith('zh')) {
        return 'zh';
      }
      if (normalized.startsWith('en')) {
        return 'en';
      }
    }
    return 'en';
  }

  function friendlyError(error: unknown) {
    const message = error instanceof Error ? error.message : String(error);
    const normalized = message.toLowerCase();
    if (normalized.includes('already in use') || normalized.includes('already in used') || normalized.includes('claim interface') || normalized.includes('busy')) {
      return copy.deviceBusy;
    }
    return copy.failed(message);
  }

  function progressTextZh(progress: LaunchProgress) {
    switch (progress.action) {
      case 'selectDevice':
        return '请选择要启动隐控的手机';
      case 'connectDevice':
        return `正在连接设备 ${progress.name}`;
      case 'download':
        return `正在下载 ${progress.name}`;
      case 'upload':
        return `正在把 ${progress.name} 发送到手机上`;
      case 'skip':
        return `跳过缺失文件 ${progress.name}`;
      case 'startShell':
        return '正在启动后台 shell 进程';
      case 'allowInstall':
        return '请点击手机上的允许安装按钮';
      case 'installController':
        return '正在重装隐控 App';
      case 'configureApiKey':
        return '你需要先配置 API KEY 才能使用手机自动化';
      case 'openController':
        return '正在打开隐控 App';
    }
  }

  function progressTextEn(progress: LaunchProgress) {
    switch (progress.action) {
      case 'selectDevice':
        return 'Select the phone to launch ShadowAuto';
      case 'connectDevice':
        return `Connecting to ${progress.name}`;
      case 'download':
        return `Downloading ${progress.name}`;
      case 'upload':
        return `Sending ${progress.name} to the phone`;
      case 'skip':
        return `Skipping missing file ${progress.name}`;
      case 'startShell':
        return 'Starting the background shell process';
      case 'allowInstall':
        return 'Tap Allow on the phone when the install prompt appears';
      case 'installController':
        return 'Reinstalling the ShadowAuto app';
      case 'configureApiKey':
        return 'Configure an API key before using phone automation';
      case 'openController':
        return 'Opening the ShadowAuto app';
    }
  }
</script>

<svelte:head>
  <title>{copy.title}</title>
</svelte:head>

<main>
  <div class="ambient" aria-hidden="true">
    <span></span>
    <span></span>
    <span></span>
    <span></span>
    <span></span>
    <span></span>
    <span></span>
    <span></span>
    <span></span>
    <span></span>
    <span></span>
    <span></span>
  </div>
  <section class="launcher">
    <button disabled={busy} on:click={start}>
      <span>{busy ? copy.starting : copy.start}</span>
    </button>
    <div class="setup">
      <strong>{copy.setupTitle}</strong>
      <ul>
        {#each copy.setupNotes as note}
          <li>{note}</li>
        {/each}
      </ul>
      <div class="guide-links" aria-label={copy.developerGuideTitle}>
        <span>{copy.developerGuideTitle}</span>
        {#each developerGuides as guide}
          <a href={guide.url} target="_blank" rel="noreferrer">{guide.name}</a>
        {/each}
      </div>
    </div>
    <a class="repo-link" href={repositoryUrl} target="_blank" rel="noreferrer">
      <span>GitHub</span>
      <strong>{copy.repository}</strong>
    </a>
    {#if errorText}
      <p class="error" aria-live="polite">{errorText}</p>
    {/if}
    {#if progressItems.length}
      <ol bind:this={progressElement} class="progress" aria-live="polite">
        {#each progressItems as item}
          <li>
            <time>{item.time}</time>
            <span>{item.text}</span>
          </li>
        {/each}
      </ol>
    {/if}
    {#if readyText}
      <p class="ready" aria-live="polite">{readyText}</p>
    {/if}
  </section>
</main>

<style>
  :global(body) {
    margin: 0;
    font-family: Inter, system-ui, sans-serif;
    background: #06070b;
    color: #f8fafc;
    overflow: auto;
  }

  main {
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: 24px;
    position: relative;
    isolation: isolate;
    background:
      linear-gradient(115deg, rgba(33, 41, 58, 0.94), rgba(6, 7, 11, 0.98) 48%, rgba(18, 16, 26, 0.96)),
      repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.05) 0 1px, transparent 1px 80px),
      repeating-linear-gradient(0deg, rgba(255, 255, 255, 0.035) 0 1px, transparent 1px 80px);
    background-size: auto, 80px 80px, 80px 80px;
    animation: grid-drift 18s linear infinite;
  }

  main::before,
  main::after {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
  }

  main::before {
    z-index: -2;
    background:
      linear-gradient(145deg, transparent 0 34%, rgba(45, 212, 191, 0.24) 34.4%, transparent 35.1%),
      linear-gradient(32deg, transparent 0 58%, rgba(251, 191, 36, 0.18) 58.3%, transparent 59%),
      linear-gradient(168deg, transparent 0 70%, rgba(244, 63, 94, 0.15) 70.3%, transparent 71%);
    filter: blur(0.2px);
  }

  main::after {
    z-index: -1;
    background:
      repeating-linear-gradient(0deg, rgba(255, 255, 255, 0.05) 0 1px, transparent 1px 7px),
      linear-gradient(90deg, transparent, rgba(45, 212, 191, 0.08), transparent);
    mix-blend-mode: screen;
    opacity: 0.38;
    animation: scan 7s linear infinite;
  }

  .ambient {
    position: absolute;
    inset: 0;
    z-index: 0;
    overflow: hidden;
    pointer-events: none;
  }

  .ambient span {
    position: absolute;
    display: block;
    width: 9px;
    height: 9px;
    border: 1px solid rgba(125, 211, 252, 0.55);
    background: rgba(45, 212, 191, 0.16);
    clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
    box-shadow:
      0 0 18px rgba(45, 212, 191, 0.36),
      0 0 1px rgba(255, 255, 255, 0.72) inset;
    opacity: 0.64;
    animation: float-a 13s ease-in-out infinite;
  }

  .ambient span:nth-child(1) {
    left: 9%;
    top: 19%;
  }

  .ambient span:nth-child(2) {
    left: 77%;
    top: 13%;
    width: 62px;
    height: 2px;
    border: 0;
    background: linear-gradient(90deg, transparent, rgba(125, 211, 252, 0.82), transparent);
    clip-path: none;
    animation: float-b 15s ease-in-out infinite;
  }

  .ambient span:nth-child(3) {
    left: 68%;
    top: 73%;
    width: 14px;
    height: 14px;
    background: rgba(251, 191, 36, 0.12);
    border-color: rgba(251, 191, 36, 0.58);
    animation: float-c 16s ease-in-out infinite;
  }

  .ambient span:nth-child(4) {
    left: 21%;
    top: 70%;
    width: 86px;
    height: 2px;
    border: 0;
    background: linear-gradient(90deg, transparent, rgba(45, 212, 191, 0.78), transparent);
    clip-path: none;
    animation: float-b 19s ease-in-out infinite reverse;
  }

  .ambient span:nth-child(5) {
    left: 43%;
    top: 11%;
    width: 6px;
    height: 6px;
    animation: float-c 12s ease-in-out infinite reverse;
  }

  .ambient span:nth-child(6) {
    left: 86%;
    top: 46%;
    width: 18px;
    height: 18px;
    background: rgba(244, 63, 94, 0.08);
    border-color: rgba(244, 63, 94, 0.45);
    animation: float-a 17s ease-in-out infinite;
  }

  .ambient span:nth-child(7) {
    left: 7%;
    top: 53%;
    width: 48px;
    height: 2px;
    border: 0;
    background: linear-gradient(90deg, transparent, rgba(251, 191, 36, 0.65), transparent);
    clip-path: none;
    animation: float-b 14s ease-in-out infinite;
  }

  .ambient span:nth-child(8) {
    left: 33%;
    top: 86%;
    width: 12px;
    height: 12px;
    animation: float-c 18s ease-in-out infinite;
  }

  .ambient span:nth-child(9) {
    left: 91%;
    top: 82%;
    width: 7px;
    height: 7px;
    background: rgba(125, 211, 252, 0.14);
    animation: float-a 11s ease-in-out infinite reverse;
  }

  .ambient span:nth-child(10) {
    left: 15%;
    top: 8%;
    width: 16px;
    height: 16px;
    background: transparent;
    border-color: rgba(203, 213, 225, 0.36);
    animation: float-c 21s ease-in-out infinite reverse;
  }

  .ambient span:nth-child(11) {
    left: 55%;
    top: 51%;
    width: 110px;
    height: 1px;
    border: 0;
    background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.44), transparent);
    clip-path: none;
    animation: float-b 17s ease-in-out infinite;
  }

  .ambient span:nth-child(12) {
    left: 51%;
    top: 27%;
    width: 8px;
    height: 8px;
    background: rgba(45, 212, 191, 0.1);
    animation: float-a 15s ease-in-out infinite;
  }

  button {
    min-width: min(300px, calc(100vw - 48px));
    max-width: calc(100vw - 48px);
    min-height: 62px;
    border: 1px solid rgba(148, 163, 184, 0.36);
    border-radius: 999px;
    padding: 0 34px;
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    background:
      linear-gradient(135deg, rgba(45, 212, 191, 0.95), rgba(79, 70, 229, 0.95) 48%, rgba(251, 191, 36, 0.94)),
      #0f172a;
    color: #ffffff;
    box-shadow:
      0 0 0 1px rgba(255, 255, 255, 0.12) inset,
      0 20px 55px rgba(45, 212, 191, 0.22),
      0 12px 28px rgba(0, 0, 0, 0.42);
    font-weight: 700;
    font-size: 17px;
    line-height: 1.18;
    text-align: center;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.36);
    cursor: pointer;
    transform: translateY(0);
    transition:
      transform 180ms ease,
      box-shadow 180ms ease,
      filter 180ms ease;
  }

  button span {
    position: relative;
    z-index: 1;
  }

  button::before,
  button::after {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
  }

  button::before {
    background: linear-gradient(110deg, transparent 0 24%, rgba(255, 255, 255, 0.42) 42%, transparent 58% 100%);
    transform: translateX(-92%);
    animation: shine 2.8s ease-in-out infinite;
  }

  button::after {
    border-radius: inherit;
    border: 1px solid rgba(255, 255, 255, 0.48);
    opacity: 0.46;
  }

  button:hover:not(:disabled) {
    transform: translateY(-2px);
    filter: saturate(1.16);
    box-shadow:
      0 0 0 1px rgba(255, 255, 255, 0.14) inset,
      0 26px 70px rgba(45, 212, 191, 0.26),
      0 16px 38px rgba(0, 0, 0, 0.5);
  }

  button:active:not(:disabled) {
    transform: translateY(1px) scale(0.99);
  }

  button:disabled {
    cursor: default;
    background:
      linear-gradient(135deg, rgba(71, 85, 105, 0.86), rgba(30, 41, 59, 0.9)),
      #334155;
    color: rgba(248, 250, 252, 0.72);
    box-shadow:
      0 0 0 1px rgba(255, 255, 255, 0.08) inset,
      0 12px 30px rgba(0, 0, 0, 0.32);
  }

  button:disabled::before {
    animation: none;
  }

  .launcher {
    width: min(520px, 100%);
    display: grid;
    justify-items: center;
    gap: 18px;
    position: relative;
    z-index: 1;
  }

  .setup {
    width: min(520px, calc(100vw - 48px));
    margin: 0;
    padding: 16px 18px;
    border: 1px solid rgba(148, 163, 184, 0.24);
    border-radius: 12px;
    background: rgba(15, 23, 42, 0.62);
    box-shadow:
      0 0 0 1px rgba(255, 255, 255, 0.05) inset,
      0 18px 50px rgba(0, 0, 0, 0.22);
    color: #dbeafe;
    font-size: 13px;
    line-height: 1.55;
    backdrop-filter: blur(12px);
  }

  .setup strong {
    display: block;
    margin-bottom: 8px;
    color: #f8fafc;
    font-size: 14px;
  }

  .setup ul {
    display: grid;
    gap: 5px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .setup li {
    position: relative;
    padding-left: 14px;
  }

  .setup li::before {
    content: '';
    position: absolute;
    left: 0;
    top: 9px;
    width: 5px;
    height: 5px;
    border-radius: 999px;
    background: #2dd4bf;
    box-shadow: 0 0 12px rgba(45, 212, 191, 0.58);
  }

  .guide-links {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    margin-top: 12px;
  }

  .guide-links span {
    color: rgba(226, 232, 240, 0.74);
  }

  .guide-links a {
    padding: 4px 10px;
    border: 1px solid rgba(125, 211, 252, 0.3);
    border-radius: 999px;
    background: rgba(14, 165, 233, 0.12);
    color: #bae6fd;
    text-decoration: none;
  }

  .guide-links a:hover {
    border-color: rgba(45, 212, 191, 0.52);
    color: #ccfbf1;
  }

  .repo-link {
    width: min(520px, calc(100vw - 48px));
    min-height: 48px;
    padding: 0 18px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    border: 1px solid rgba(125, 211, 252, 0.28);
    border-radius: 12px;
    background:
      linear-gradient(135deg, rgba(15, 23, 42, 0.72), rgba(30, 41, 59, 0.54)),
      rgba(15, 23, 42, 0.62);
    color: #e0f2fe;
    text-decoration: none;
    box-shadow:
      0 0 0 1px rgba(255, 255, 255, 0.05) inset,
      0 18px 50px rgba(0, 0, 0, 0.22);
    backdrop-filter: blur(12px);
    transition:
      transform 180ms ease,
      border-color 180ms ease,
      background 180ms ease;
  }

  .repo-link span {
    color: #67e8f9;
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0;
    text-transform: uppercase;
  }

  .repo-link strong {
    min-width: 0;
    color: #f8fafc;
    font-size: 14px;
    text-align: right;
  }

  .repo-link:hover {
    transform: translateY(-1px);
    border-color: rgba(45, 212, 191, 0.56);
    background:
      linear-gradient(135deg, rgba(20, 184, 166, 0.16), rgba(59, 130, 246, 0.12)),
      rgba(15, 23, 42, 0.72);
  }

  .error {
    width: min(520px, calc(100vw - 48px));
    margin: 0;
    padding: 14px 16px;
    border: 1px solid rgba(251, 191, 36, 0.38);
    border-radius: 12px;
    background: rgba(15, 23, 42, 0.72);
    color: #fde68a;
    box-shadow:
      0 0 0 1px rgba(255, 255, 255, 0.06) inset,
      0 18px 50px rgba(0, 0, 0, 0.28);
    font-size: 14px;
    line-height: 1.55;
    text-align: center;
    white-space: pre-line;
    backdrop-filter: blur(12px);
  }

  .progress {
    width: min(520px, calc(100vw - 48px));
    max-height: min(42vh, 360px);
    margin: 0;
    padding: 14px 18px;
    overflow: auto;
    border: 1px solid rgba(148, 163, 184, 0.24);
    border-radius: 12px;
    background: rgba(15, 23, 42, 0.7);
    box-shadow:
      0 0 0 1px rgba(255, 255, 255, 0.06) inset,
      0 18px 50px rgba(0, 0, 0, 0.28);
    color: #dbeafe;
    font-size: 13px;
    line-height: 1.55;
    list-style: none;
    backdrop-filter: blur(12px);
  }

  .progress li {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 12px;
    align-items: baseline;
    padding: 4px 0;
  }

  .progress time {
    color: #67e8f9;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  .progress span {
    min-width: 0;
  }

  .ready {
    width: min(520px, calc(100vw - 48px));
    margin: 0;
    padding: 16px 18px;
    border: 1px solid rgba(45, 212, 191, 0.42);
    border-radius: 12px;
    background: rgba(13, 148, 136, 0.18);
    color: #ccfbf1;
    box-shadow:
      0 0 0 1px rgba(255, 255, 255, 0.06) inset,
      0 18px 50px rgba(0, 0, 0, 0.28);
    font-size: 15px;
    line-height: 1.55;
    text-align: center;
    backdrop-filter: blur(12px);
  }

  @media (prefers-reduced-motion: reduce) {
    main,
    main::after,
    button::before,
    .ambient span {
      animation: none;
    }
  }

  @keyframes grid-drift {
    0% {
      background-position: 0 0, 0 0, 0 0;
    }

    100% {
      background-position: 0 0, 80px 40px, 40px 80px;
    }
  }

  @keyframes float-a {
    0%,
    100% {
      transform: translate3d(0, 0, 0) rotate(0deg);
    }

    50% {
      transform: translate3d(34px, -26px, 0) rotate(58deg);
    }
  }

  @keyframes float-b {
    0%,
    100% {
      transform: translate3d(0, 0, 0) rotate(-8deg) scaleX(1);
      opacity: 0.28;
    }

    50% {
      transform: translate3d(-42px, 18px, 0) rotate(5deg) scaleX(1.28);
      opacity: 0.74;
    }
  }

  @keyframes float-c {
    0%,
    100% {
      transform: translate3d(0, 0, 0) rotate(45deg) scale(1);
      opacity: 0.48;
    }

    50% {
      transform: translate3d(-24px, -32px, 0) rotate(135deg) scale(1.16);
      opacity: 0.82;
    }
  }

  @keyframes shine {
    0% {
      transform: translateX(-96%);
    }

    46%,
    100% {
      transform: translateX(96%);
    }
  }

  @keyframes scan {
    0% {
      transform: translateY(-18px);
    }

    100% {
      transform: translateY(18px);
    }
  }
</style>
