import {
  DEFAULT_SETTINGS,
  OPTIONAL_MUSIC_ORIGINS,
  SONGFLIP_WEB_BASE,
  TARGET_PLATFORMS,
  detectPlatformFromUrl,
  extractMusicUrl,
  getTargetUrlFromResult,
  isPlaylistUrl,
  isSupportedMusicUrl,
  resolveMusicUrl
} from './shared.js';

const MENU_OPEN_TARGET = 'songflip-open-target';
const MENU_OPEN_FLIPPAGE = 'songflip-open-flippage';
const ALARM_CLEAR_BADGE = 'songflip-clear-badge';
const MAX_HISTORY_ITEMS = 5;

async function getSettings() {
  const stored = await chrome.storage.local.get(DEFAULT_SETTINGS);
  return {
    targetPlatform: stored.targetPlatform || DEFAULT_SETTINGS.targetPlatform,
    preferDesktopApp: Boolean(stored.preferDesktopApp),
    autoRedirect: Boolean(stored.autoRedirect),
    recentHistory: Array.isArray(stored.recentHistory) ? stored.recentHistory : []
  };
}

async function saveHistoryEntry(entry) {
  const { recentHistory } = await getSettings();
  const deduped = recentHistory.filter((item) => item.smartUrl !== entry.smartUrl);
  const updated = [entry, ...deduped].slice(0, MAX_HISTORY_ITEMS);
  await chrome.storage.local.set({ recentHistory: updated });
}

async function flashBadge(text, color = '#10B981') {
  try {
    await chrome.action.setBadgeBackgroundColor({ color });
    await chrome.action.setBadgeText({ text });
    await chrome.alarms.create(ALARM_CLEAR_BADGE, { when: Date.now() + 2800 });
  } catch {
    // Ignore badge errors on unsupported contexts
  }
}

async function setupContextMenus() {
  const { targetPlatform } = await getSettings();
  const targetMeta = TARGET_PLATFORMS[targetPlatform] || TARGET_PLATFORMS.spotify;

  try {
    await chrome.contextMenus.removeAll();
  } catch {
    // Ignore if empty
  }

  chrome.contextMenus.create({
    id: MENU_OPEN_TARGET,
    title: `Open in ${targetMeta.name} (SongFlip)`,
    contexts: ['link', 'selection', 'page']
  });

  chrome.contextMenus.create({
    id: MENU_OPEN_FLIPPAGE,
    title: 'Open Universal Flippage (songflip.link)',
    contexts: ['link', 'selection', 'page']
  });
}

chrome.runtime.onInstalled.addListener(async () => {
  await setupContextMenus();
});

chrome.storage.onChanged.addListener(async (changes, areaName) => {
  if (areaName === 'local' && changes.targetPlatform) {
    await setupContextMenus();
  }
});

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === ALARM_CLEAR_BADGE) {
    try {
      await chrome.action.setBadgeText({ text: '' });
    } catch {
      // Ignore
    }
  }
});

// Rule #20: Call chrome.permissions.request synchronously before any await
chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === 'REQUEST_AUTO_REDIRECT_PERMISSION') {
    (async () => {
      try {
        const granted = await chrome.permissions.request({ origins: OPTIONAL_MUSIC_ORIGINS });
        sendResponse({ granted: Boolean(granted) });
      } catch (err) {
        sendResponse({ granted: false, error: err?.message || 'Permission request failed' });
      }
    })();
    return true;
  }

  if (message?.type === 'SAVE_HISTORY_ENTRY' && message.entry) {
    (async () => {
      await saveHistoryEntry(message.entry);
      sendResponse({ ok: true });
    })();
    return true;
  }

  return false;
});

chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  const rawCandidate = info.linkUrl || info.selectionText || info.pageUrl || tab?.url || '';
  const extractedUrl = extractMusicUrl(rawCandidate);

  if (!extractedUrl || !isSupportedMusicUrl(extractedUrl)) {
    await flashBadge('!', '#F59E0B');
    return;
  }

  if (isPlaylistUrl(extractedUrl)) {
    await chrome.tabs.create({ url: `${SONGFLIP_WEB_BASE}/#apps` });
    await flashBadge('APP', '#3B82F6');
    return;
  }

  try {
    await flashBadge('...', '#334155');
    const settings = await getSettings();
    const resolved = await resolveMusicUrl(extractedUrl);

    if (resolved.isPlaylist) {
      await chrome.tabs.create({ url: `${SONGFLIP_WEB_BASE}/#apps` });
      await flashBadge('APP', '#3B82F6');
      return;
    }

    await saveHistoryEntry({
      title: resolved.title,
      artist: resolved.artist,
      coverUrl: resolved.coverUrl,
      sourcePlatform: resolved.sourcePlatform,
      smartUrl: resolved.smartUrl,
      links: resolved.links,
      timestamp: Date.now()
    });

    if (info.menuItemId === MENU_OPEN_FLIPPAGE) {
      await chrome.tabs.create({ url: resolved.smartUrl });
      await flashBadge('✓', '#10B981');
      return;
    }

    const destinationUrl = getTargetUrlFromResult(
      resolved,
      settings.targetPlatform,
      settings.preferDesktopApp
    );
    await chrome.tabs.create({ url: destinationUrl });
    await flashBadge('✓', '#10B981');
  } catch {
    await flashBadge('ERR', '#EF4444');
  }
});

async function wasRecentlyRedirected(url) {
  try {
    if (!chrome.storage.session) return false;
    const { recentRedirects = {} } = await chrome.storage.session.get('recentRedirects');
    const ts = recentRedirects[url];
    return typeof ts === 'number' && Date.now() - ts < 15000;
  } catch {
    return false;
  }
}

async function markRecentlyRedirected(url) {
  try {
    if (!chrome.storage.session) return;
    const { recentRedirects = {} } = await chrome.storage.session.get('recentRedirects');
    const now = Date.now();
    const cleaned = {};
    for (const [k, v] of Object.entries(recentRedirects)) {
      if (now - v < 30000) cleaned[k] = v;
    }
    cleaned[url] = now;
    await chrome.storage.session.set({ recentRedirects: cleaned });
  } catch {
    // Ignore session storage errors
  }
}

chrome.tabs.onUpdated.addListener(async (tabId, changeInfo, tab) => {
  const candidateUrl = changeInfo.url || (changeInfo.status === 'loading' ? tab?.url : null);
  if (!candidateUrl || !isSupportedMusicUrl(candidateUrl) || isPlaylistUrl(candidateUrl)) {
    return;
  }

  const settings = await getSettings();
  if (!settings.autoRedirect) return;

  const sourcePlatform = detectPlatformFromUrl(candidateUrl);
  if (sourcePlatform === 'unknown' || sourcePlatform === settings.targetPlatform) {
    return;
  }

  if (await wasRecentlyRedirected(candidateUrl)) {
    return;
  }

  try {
    await markRecentlyRedirected(candidateUrl);
    const resolved = await resolveMusicUrl(candidateUrl);
    if (resolved.isPlaylist || resolved.isAudiobook) return;

    const destinationUrl = getTargetUrlFromResult(
      resolved,
      settings.targetPlatform,
      settings.preferDesktopApp
    );
    if (!destinationUrl || destinationUrl === candidateUrl) return;

    await markRecentlyRedirected(destinationUrl);
    await saveHistoryEntry({
      title: resolved.title,
      artist: resolved.artist,
      coverUrl: resolved.coverUrl,
      sourcePlatform: resolved.sourcePlatform,
      smartUrl: resolved.smartUrl,
      links: resolved.links,
      timestamp: Date.now()
    });
    await chrome.tabs.update(tabId, { url: destinationUrl });
    await flashBadge('✓', '#10B981');
  } catch {
    // Keep original tab untouched on transient failure
  }
});
