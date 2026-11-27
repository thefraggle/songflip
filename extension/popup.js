import {
  DEFAULT_SETTINGS,
  GITHUB_REPO_URL,
  PLAY_STORE_URL,
  SONGFLIP_WEB_BASE,
  TARGET_PLATFORMS,
  TESTFLIGHT_URL,
  extractMusicUrl,
  getTargetUrlFromResult,
  isAudiobookUrl,
  isPlaylistUrl,
  isSearchFallbackUrl,
  isSupportedMusicUrl,
  resolveMusicUrl
} from './shared.js';

const elements = {
  targetSelect: document.getElementById('target-select'),
  toggleDesktopApp: document.getElementById('toggle-desktop-app'),
  toggleAutoRedirect: document.getElementById('toggle-auto-redirect'),
  toggleAppsBtn: document.getElementById('btn-toggle-apps'),
  form: document.getElementById('convert-form'),
  urlInput: document.getElementById('url-input'),
  pasteBtn: document.getElementById('btn-paste'),
  convertBtn: document.getElementById('btn-convert'),
  statusBanner: document.getElementById('status-banner'),
  resultCard: document.getElementById('result-card'),
  resultCover: document.getElementById('result-cover'),
  resultSource: document.getElementById('result-source'),
  resultType: document.getElementById('result-type'),
  resultTitle: document.getElementById('result-title'),
  resultArtist: document.getElementById('result-artist'),
  fallbackNotice: document.getElementById('fallback-notice'),
  openTargetBtn: document.getElementById('btn-open-target'),
  copyFlippageBtn: document.getElementById('btn-copy-flippage'),
  playlistCard: document.getElementById('playlist-card'),
  funnelKicker: document.getElementById('funnel-kicker'),
  playStoreBtn: document.getElementById('btn-playstore'),
  testFlightBtn: document.getElementById('btn-testflight'),
  historySection: document.getElementById('history-section'),
  historyList: document.getElementById('history-list'),
  clearHistoryBtn: document.getElementById('btn-clear-history'),
  openWebBtn: document.getElementById('btn-open-web'),
  openGithubBtn: document.getElementById('btn-open-github')
};

let currentResolved = null;
let currentSettings = { ...DEFAULT_SETTINGS };

function showStatus(text, type = 'info') {
  elements.statusBanner.textContent = text;
  elements.statusBanner.className = 'status-banner';
  if (type === 'error') elements.statusBanner.classList.add('is-error');
  if (type === 'loading') elements.statusBanner.classList.add('is-loading');
  elements.statusBanner.classList.remove('hidden');
}

function hideStatus() {
  elements.statusBanner.classList.add('hidden');
}

function setFormBusy(busy) {
  elements.urlInput.disabled = busy;
  elements.pasteBtn.disabled = busy;
  elements.convertBtn.disabled = busy;
  elements.convertBtn.textContent = busy ? 'Resolving...' : 'Flip';
}

function formatSourceLabel(platformKey) {
  if (!platformKey || platformKey === 'unknown') return 'Music';
  if (platformKey === 'lastfm') return 'Last.fm';
  if (platformKey === 'bandcamp') return 'Bandcamp';
  if (platformKey === 'beatport') return 'Beatport';
  if (platformKey === 'shazam') return 'Shazam';
  return TARGET_PLATFORMS[platformKey]?.name || platformKey;
}

function showAppFunnel(kickerText = 'SongFlip Mobile App') {
  elements.resultCard.classList.add('hidden');
  elements.funnelKicker.textContent = kickerText;
  elements.playlistCard.classList.remove('hidden');
  elements.toggleAppsBtn.classList.add('active');
}

function hideAppFunnel() {
  elements.playlistCard.classList.add('hidden');
  elements.toggleAppsBtn.classList.remove('active');
}

function renderResolvedTrack(resolved) {
  currentResolved = resolved;
  hideAppFunnel();

  const targetMeta = TARGET_PLATFORMS[currentSettings.targetPlatform] || TARGET_PLATFORMS.spotify;
  const targetWebUrl = getTargetUrlFromResult(resolved, currentSettings.targetPlatform, false);
  const isFallback = isSearchFallbackUrl(targetWebUrl);

  elements.resultCover.src = resolved.coverUrl || 'icons/icon-128.png';
  elements.resultSource.textContent = formatSourceLabel(resolved.sourcePlatform);
  elements.resultTitle.textContent = resolved.title || 'Unknown Track';
  elements.resultArtist.textContent = resolved.artist || '';

  if (resolved.isPodcast) {
    elements.resultType.textContent = 'Podcast';
    elements.resultType.classList.remove('hidden');
  } else if (resolved.isAlbum) {
    elements.resultType.textContent = 'Album';
    elements.resultType.classList.remove('hidden');
  } else {
    elements.resultType.classList.add('hidden');
  }

  if (isFallback) {
    elements.fallbackNotice.classList.remove('hidden');
    elements.openTargetBtn.textContent = `Search on ${targetMeta.name} ↗`;
  } else {
    elements.fallbackNotice.classList.add('hidden');
    elements.openTargetBtn.textContent = `Open in ${targetMeta.name} ↗`;
  }

  elements.copyFlippageBtn.textContent = `Copy Flippage (${resolved.smartUrl.replace('https://', '')})`;
  elements.resultCard.classList.remove('hidden');
}

function renderHistory(items) {
  elements.historyList.textContent = '';
  if (!Array.isArray(items) || items.length === 0) {
    elements.historySection.classList.add('hidden');
    return;
  }

  for (const item of items) {
    const li = document.createElement('li');
    li.className = 'history-item';

    const info = document.createElement('div');
    info.className = 'history-info';

    const title = document.createElement('span');
    title.className = 'history-title';
    title.textContent = item.title || 'Track';

    const artist = document.createElement('span');
    artist.className = 'history-artist';
    artist.textContent = item.artist || formatSourceLabel(item.sourcePlatform);

    info.append(title, artist);

    const actions = document.createElement('div');
    actions.className = 'history-actions';

    const openBtn = document.createElement('button');
    openBtn.type = 'button';
    openBtn.className = 'history-btn';
    openBtn.textContent = 'Open';
    openBtn.addEventListener('click', async () => {
      const dest = getTargetUrlFromResult(item, currentSettings.targetPlatform, currentSettings.preferDesktopApp);
      await chrome.tabs.create({ url: dest });
    });

    const copyBtn = document.createElement('button');
    copyBtn.type = 'button';
    copyBtn.className = 'history-btn';
    copyBtn.textContent = 'Link';
    copyBtn.addEventListener('click', async () => {
      await navigator.clipboard.writeText(item.smartUrl);
      copyBtn.textContent = '✓';
      setTimeout(() => {
        copyBtn.textContent = 'Link';
      }, 1500);
    });

    actions.append(openBtn, copyBtn);
    li.append(info, actions);
    elements.historyList.append(li);
  }

  elements.historySection.classList.remove('hidden');
}

async function resolveAndDisplay(rawUrl) {
  const cleanUrl = extractMusicUrl(rawUrl) || rawUrl.trim();
  if (!cleanUrl || !isSupportedMusicUrl(cleanUrl)) {
    showStatus('Enter a valid Spotify, Apple Music, YouTube Music, Tidal, Deezer, Qobuz, or Last.fm link.', 'error');
    return;
  }

  if (isPlaylistUrl(cleanUrl)) {
    hideStatus();
    showAppFunnel('Playlist Detected');
    return;
  }

  if (isAudiobookUrl(cleanUrl)) {
    hideStatus();
    showAppFunnel('Audiobook Detected');
    return;
  }

  setFormBusy(true);
  showStatus('Resolving link across streaming catalogs...', 'loading');

  try {
    const resolved = await resolveMusicUrl(cleanUrl);
    hideStatus();

    if (resolved.isPlaylist) {
      showAppFunnel('Playlist Detected');
      return;
    }
    if (resolved.isAudiobook) {
      showAppFunnel('Audiobook Detected');
      return;
    }

    renderResolvedTrack(resolved);

    const entry = {
      title: resolved.title,
      artist: resolved.artist,
      coverUrl: resolved.coverUrl,
      sourcePlatform: resolved.sourcePlatform,
      smartUrl: resolved.smartUrl,
      links: resolved.links,
      timestamp: Date.now()
    };
    await chrome.runtime.sendMessage({ type: 'SAVE_HISTORY_ENTRY', entry });
    const stored = await chrome.storage.local.get({ recentHistory: [] });
    currentSettings.recentHistory = stored.recentHistory;
    renderHistory(currentSettings.recentHistory);
  } catch (err) {
    showStatus(err?.message || 'Could not resolve this music link.', 'error');
  } finally {
    setFormBusy(false);
  }
}

async function init() {
  const stored = await chrome.storage.local.get(DEFAULT_SETTINGS);
  currentSettings = {
    targetPlatform: stored.targetPlatform || DEFAULT_SETTINGS.targetPlatform,
    preferDesktopApp: Boolean(stored.preferDesktopApp),
    autoRedirect: Boolean(stored.autoRedirect),
    recentHistory: Array.isArray(stored.recentHistory) ? stored.recentHistory : []
  };

  elements.targetSelect.value = currentSettings.targetPlatform;
  elements.toggleDesktopApp.checked = currentSettings.preferDesktopApp;
  elements.toggleAutoRedirect.checked = currentSettings.autoRedirect;
  renderHistory(currentSettings.recentHistory);

  elements.targetSelect.addEventListener('change', async () => {
    currentSettings.targetPlatform = elements.targetSelect.value;
    await chrome.storage.local.set({ targetPlatform: currentSettings.targetPlatform });
    if (currentResolved) {
      renderResolvedTrack(currentResolved);
    }
  });

  elements.toggleDesktopApp.addEventListener('change', async () => {
    currentSettings.preferDesktopApp = elements.toggleDesktopApp.checked;
    await chrome.storage.local.set({ preferDesktopApp: currentSettings.preferDesktopApp });
  });

  elements.toggleAutoRedirect.addEventListener('change', async () => {
    const wantsEnable = elements.toggleAutoRedirect.checked;
    if (!wantsEnable) {
      currentSettings.autoRedirect = false;
      await chrome.storage.local.set({ autoRedirect: false });
      hideStatus();
      return;
    }

    const res = await chrome.runtime.sendMessage({ type: 'REQUEST_AUTO_REDIRECT_PERMISSION' });
    if (res?.granted) {
      currentSettings.autoRedirect = true;
      await chrome.storage.local.set({ autoRedirect: true });
      showStatus('Auto-redirect enabled for supported music links.', 'info');
    } else {
      elements.toggleAutoRedirect.checked = false;
      currentSettings.autoRedirect = false;
      await chrome.storage.local.set({ autoRedirect: false });
      showStatus('Host permission is required for automatic tab redirects.', 'error');
    }
  });

  elements.toggleAppsBtn.addEventListener('click', () => {
    if (elements.playlistCard.classList.contains('hidden')) {
      showAppFunnel('SongFlip Mobile App');
    } else {
      hideAppFunnel();
      if (currentResolved) {
        elements.resultCard.classList.remove('hidden');
      }
    }
  });

  elements.form.addEventListener('submit', async (event) => {
    event.preventDefault();
    await resolveAndDisplay(elements.urlInput.value);
  });

  elements.pasteBtn.addEventListener('click', async () => {
    try {
      const clipText = await navigator.clipboard.readText();
      if (clipText) {
        elements.urlInput.value = clipText.trim();
        await resolveAndDisplay(elements.urlInput.value);
      }
    } catch {
      elements.urlInput.focus();
    }
  });

  elements.openTargetBtn.addEventListener('click', async () => {
    if (!currentResolved) return;
    const dest = getTargetUrlFromResult(
      currentResolved,
      currentSettings.targetPlatform,
      currentSettings.preferDesktopApp
    );
    await chrome.tabs.create({ url: dest });
  });

  elements.copyFlippageBtn.addEventListener('click', async () => {
    if (!currentResolved?.smartUrl) return;
    await navigator.clipboard.writeText(currentResolved.smartUrl);
    const original = elements.copyFlippageBtn.textContent;
    elements.copyFlippageBtn.textContent = 'Copied! ✓';
    setTimeout(() => {
      elements.copyFlippageBtn.textContent = original;
    }, 1800);
  });

  elements.playStoreBtn.addEventListener('click', async () => {
    await chrome.tabs.create({ url: PLAY_STORE_URL });
  });

  elements.testFlightBtn.addEventListener('click', async () => {
    await chrome.tabs.create({ url: TESTFLIGHT_URL });
  });

  elements.clearHistoryBtn.addEventListener('click', async () => {
    currentSettings.recentHistory = [];
    await chrome.storage.local.set({ recentHistory: [] });
    renderHistory([]);
  });

  elements.openWebBtn.addEventListener('click', async () => {
    await chrome.tabs.create({ url: SONGFLIP_WEB_BASE });
  });

  elements.openGithubBtn.addEventListener('click', async () => {
    await chrome.tabs.create({ url: GITHUB_REPO_URL });
  });

  // Inspect active tab on popup open using activeTab permission
  try {
    const [activeTab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (activeTab?.url) {
      const extracted = extractMusicUrl(activeTab.url);
      if (extracted && isSupportedMusicUrl(extracted)) {
        elements.urlInput.value = extracted;
        await resolveAndDisplay(extracted);
      }
    }
  } catch {
    // Ignore restricted tabs (chrome://, about:blank)
  }
}

init();
