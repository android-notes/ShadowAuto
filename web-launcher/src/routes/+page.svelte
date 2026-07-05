<script lang="ts">
  import { onMount } from 'svelte';
  import { launchShell, supported } from '$lib/tango-adb';

  type Locale = 'zh' | 'en';
  type Copy = {
    title: string;
    start: string;
    starting: string;
    deviceBusy: string;
    failed(message: string): string;
  };

  const copies: Record<Locale, Copy> = {
    zh: {
      title: 'ShadowAuto 启动器',
      start: '启动隐控Assistant',
      starting: '启动中...',
      deviceBusy: '设备已被其他程序占用，请关闭 Android Studio 并杀死 adb 后再次重试。',
      failed: (message: string) => `启动失败：${message}`
    },
    en: {
      title: 'ShadowAuto Launcher',
      start: 'Start ShadowAuto Assistant',
      starting: 'Starting...',
      deviceBusy: 'The device is already in use. Please close Android Studio and kill adb, then retry.',
      failed: (message: string) => `Failed to start: ${message}`
    }
  };

  let busy = false;
  let errorText = '';
  let locale: Locale = 'en';

  $: copy = copies[locale];

  onMount(() => {
    locale = detectLocale();
    document.documentElement.lang = locale === 'zh' ? 'zh-CN' : 'en';
  });

  async function start() {
    busy = true;
    errorText = '';
    try {
      await launchShell();
    } catch (error) {
      errorText = friendlyError(error);
    } finally {
      busy = false;
    }
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
    <button disabled={!supported() || busy} on:click={start}>
      <span>{busy ? copy.starting : copy.start}</span>
    </button>
    {#if errorText}
      <p class="error" aria-live="polite">{errorText}</p>
    {/if}
  </section>
</main>

<style>
  :global(body) {
    margin: 0;
    font-family: Inter, system-ui, sans-serif;
    background: #06070b;
    color: #f8fafc;
    overflow: hidden;
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
