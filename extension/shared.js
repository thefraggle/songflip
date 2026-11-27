/**
 * SongFlip Browser Extension – Shared Platform & Resolution Core (MV3 ES Module)
 * Zero runtime dependencies. Mirrors SongFlip Web & KMP Core URL rules.
 */

export const SONGFLIP_WEB_BASE = 'https://songflip.link';
export const SONGFLIP_CACHE_BASE = 'https://cache.songflip.link';
export const PLAY_STORE_URL = 'https://play.google.com/store/apps/details?id=de.goork.songflip';
export const TESTFLIGHT_URL = 'https://testflight.apple.com/join/c2ceF8ZK';
export const GITHUB_REPO_URL = 'https://github.com/thefraggle/songflip';

export const DEFAULT_SETTINGS = {
  targetPlatform: 'spotify',
  preferDesktopApp: false,
  autoRedirect: false,
  recentHistory: []
};

export const TARGET_PLATFORMS = {
  spotify: { key: 'spotify', name: 'Spotify', linkKey: 'spotify', color: '#1DB954' },
  appleMusic: { key: 'appleMusic', name: 'Apple Music', linkKey: 'appleMusic', color: '#FA243C' },
  youtubeMusic: { key: 'youtubeMusic', name: 'YouTube Music', linkKey: 'youtubeMusic', color: '#FF0000' },
  tidal: { key: 'tidal', name: 'Tidal', linkKey: 'tidal', color: '#00FFFF' },
  deezer: { key: 'deezer', name: 'Deezer', linkKey: 'deezer', color: '#A238FF' },
  qobuz: { key: 'qobuz', name: 'Qobuz', linkKey: 'qobuz', color: '#0073D1' },
  amazonMusic: { key: 'amazonMusic', name: 'Amazon Music', linkKey: 'amazonMusic', color: '#25D1DA' },
  soundcloud: { key: 'soundcloud', name: 'SoundCloud', linkKey: 'soundcloud', color: '#FF5500' },
  audiomack: { key: 'audiomack', name: 'Audiomack', linkKey: 'audiomack', color: '#FFA200' },
  idagio: { key: 'idagio', name: 'Idagio', linkKey: 'idagio', color: '#4B82C4' },
  anghami: { key: 'anghami', name: 'Anghami', linkKey: 'anghami', color: '#9429D4' }
};

export const OPTIONAL_MUSIC_ORIGINS = [
  '*://open.spotify.com/*',
  '*://music.apple.com/*',
  '*://music.youtube.com/*',
  '*://www.youtube.com/*',
  '*://listen.tidal.com/*',
  '*://tidal.com/*',
  '*://www.deezer.com/*',
  '*://open.qobuz.com/*',
  '*://play.qobuz.com/*',
  '*://music.amazon.com/*',
  '*://music.amazon.de/*',
  '*://music.amazon.co.uk/*',
  '*://soundcloud.com/*',
  '*://*.bandcamp.com/*',
  '*://www.beatport.com/*',
  '*://audiomack.com/*',
  '*://app.idagio.com/*',
  '*://play.anghami.com/*',
  '*://www.last.fm/*'
];

const SUPPORTED_DOMAIN_PATTERNS = [
  /(^|\.)spotify\.com$/i,
  /^spotify\.link$/i,
  /^spoti\.fi$/i,
  /^music\.apple\.com$/i,
  /^podcasts\.apple\.com$/i,
  /^itunes\.apple\.com$/i,
  /^music\.youtube\.com$/i,
  /(^|\.)youtube\.com$/i,
  /^youtu\.be$/i,
  /(^|\.)tidal\.com$/i,
  /(^|\.)deezer\.com$/i,
  /^dzr\.page\.link$/i,
  /^music\.amazon\.[a-z.]+$/i,
  /^amazon\.[a-z.]+\/music/i,
  /(^|\.)soundcloud\.com$/i,
  /^on\.soundcloud\.com$/i,
  /(^|\.)bandcamp\.com$/i,
  /(^|\.)qobuz\.com$/i,
  /(^|\.)beatport\.com$/i,
  /(^|\.)audiomack\.com$/i,
  /(^|\.)idagio\.com$/i,
  /(^|\.)anghami\.com$/i,
  /(^|\.)last\.fm$/i,
  /^pca\.st$/i,
  /(^|\.)pocketcasts\.com$/i,
  /(^|\.)shazam\.com$/i,
  /^song\.link$/i,
  /^album\.link$/i,
  /^pods\.link$/i,
  /^artist\.link$/i,
  /^odesli\.co$/i
];

/**
 * Unwraps YouTube/Facebook/Instagram outbound redirect URLs.
 */
export function unwrapSocialRedirectUrl(rawUrl) {
  if (!rawUrl || typeof rawUrl !== 'string') return rawUrl;
  try {
    const parsed = new URL(rawUrl);
    const host = parsed.hostname.toLowerCase();
    if (host.includes('youtube.com') && parsed.pathname === '/redirect') {
      const q = parsed.searchParams.get('q');
      if (q && /^https?:\/\//i.test(q)) return q;
    }
    if ((host === 'l.facebook.com' || host === 'lm.facebook.com' || host === 'l.instagram.com') && parsed.pathname === '/l.php') {
      const u = parsed.searchParams.get('u');
      if (u && /^https?:\/\//i.test(u)) return u;
    }
  } catch {
    // Ignore invalid URL
  }
  return rawUrl;
}

/**
 * Extracts and cleans the first music URL from raw text or a shared string.
 */
export function extractMusicUrl(rawText) {
  if (!rawText || typeof rawText !== 'string') return null;
  const match = rawText.match(/https?:\/\/[^\s<>"'{}|\\^`\[\]]+/i);
  if (!match) return null;

  let candidate = match[0].replace(/[),.!?;:]+$/, '');
  candidate = unwrapSocialRedirectUrl(candidate);

  try {
    const parsed = new URL(candidate);
    const host = parsed.hostname.toLowerCase();
    if (host.includes('spotify.com')) {
      parsed.searchParams.delete('si');
      parsed.searchParams.delete('context');
      parsed.searchParams.delete('nd');
    }
    if ((host.includes('youtube.com') || host === 'youtu.be') && parsed.searchParams.has('v')) {
      parsed.searchParams.delete('list');
      parsed.searchParams.delete('start_radio');
      parsed.searchParams.delete('index');
      parsed.searchParams.delete('pp');
      parsed.searchParams.delete('si');
      parsed.searchParams.delete('feature');
    }
    return parsed.toString();
  } catch {
    return candidate;
  }
}

export function detectPlatformFromUrl(url) {
  if (!url || typeof url !== 'string') return 'unknown';
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.toLowerCase();
    const path = parsed.pathname.toLowerCase();

    if (host.includes('spotify.com') || host === 'spotify.link' || host === 'spoti.fi') return 'spotify';
    if (host === 'podcasts.apple.com' || host === 'pca.st' || host.includes('pocketcasts.com')) return 'pocketCasts';
    if (host.includes('apple.com')) return 'appleMusic';
    if (host === 'music.youtube.com' || host.includes('youtube.com') || host === 'youtu.be') return 'youtubeMusic';
    if (host.includes('tidal.com')) return 'tidal';
    if (host.includes('deezer.com') || host === 'dzr.page.link') return 'deezer';
    if (host.includes('qobuz.com')) return 'qobuz';
    if (host.startsWith('music.amazon.') || (host.includes('amazon.') && path.includes('/music'))) return 'amazonMusic';
    if (host.includes('soundcloud.com')) return 'soundcloud';
    if (host.endsWith('bandcamp.com')) return 'bandcamp';
    if (host.includes('beatport.com')) return 'beatport';
    if (host.includes('audiomack.com')) return 'audiomack';
    if (host.includes('idagio.com')) return 'idagio';
    if (host.includes('anghami.com')) return 'anghami';
    if (host.includes('last.fm')) return 'lastfm';
    if (host.includes('shazam.com')) return 'shazam';
  } catch {
    return 'unknown';
  }
  return 'unknown';
}

export function isSupportedMusicUrl(url) {
  if (!url || typeof url !== 'string') return false;
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return false;
    const host = parsed.hostname.toLowerCase();
    if (host === 'songflip.link' || host.endsWith('.songflip.link') || host === 'songflip-web.web.app') {
      return false;
    }
    if (host.includes('youtube.com')) {
      const path = parsed.pathname.toLowerCase();
      return path.startsWith('/watch') || path.startsWith('/shorts/') || path.startsWith('/playlist') || path.startsWith('/browse/');
    }
    return SUPPORTED_DOMAIN_PATTERNS.some((pattern) => pattern.test(host));
  } catch {
    return false;
  }
}

export function isPlaylistUrl(url) {
  if (!url || typeof url !== 'string') return false;
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.toLowerCase();
    const path = parsed.pathname.toLowerCase();
    const listParam = parsed.searchParams.get('list') || '';

    // Exclude YouTube/BitChord albums (OLAK5uy_ and MPREb_)
    if (listParam.startsWith('OLAK5uy_') || path.includes('/browse/mpreb_')) {
      return false;
    }
    if (host.includes('youtube.com') || host === 'youtu.be') {
      // YouTube watch?v=... or youtu.be/<id> URLs are individual videos/songs even with &list= (e.g. Radio Mix)
      if ((path.startsWith('/watch') && parsed.searchParams.has('v')) || host === 'youtu.be') {
        return false;
      }
      return Boolean(listParam) || path.startsWith('/playlist') || path.startsWith('/browse/vl');
    }
    if (host.includes('spotify.com')) return path.includes('/playlist/');
    if (host.includes('apple.com')) return path.includes('/playlist/');
    if (host.includes('deezer.com')) return path.includes('/playlist/');
    if (host.includes('tidal.com')) return path.includes('/playlist/');
    if (host.includes('qobuz.com')) return path.includes('/playlist/');
    if (host.includes('amazon.')) return path.includes('/playlists/') || path.includes('/user-playlists/');
    if (host.includes('soundcloud.com')) return path.includes('/sets/');
    if (host.includes('anghami.com')) return path.includes('/playlist/');
    if (host.includes('audiomack.com')) return path.includes('/playlist/');
  } catch {
    return false;
  }
  return false;
}

export function isPodcastUrl(url) {
  if (!url || typeof url !== 'string') return false;
  const lower = url.toLowerCase();
  return (
    lower.includes('podcasts.apple.com') ||
    lower.includes('pca.st') ||
    lower.includes('pocketcasts.com') ||
    lower.includes('open.spotify.com/episode/') ||
    lower.includes('open.spotify.com/show/')
  );
}

export function isAudiobookUrl(url) {
  if (!url || typeof url !== 'string') return false;
  const lower = url.toLowerCase();
  return (
    lower.includes('audible.') ||
    lower.includes('open.spotify.com/audiobook/') ||
    lower.includes('audiobooks.apple.com')
  );
}

export function isSearchFallbackUrl(url) {
  if (!url || typeof url !== 'string') return true;
  const lower = url.toLowerCase();
  return (
    lower.includes('/search') ||
    lower.includes('results?search_query=') ||
    lower.includes('search?term=') ||
    lower.includes('search?q=') ||
    lower.includes('search.html')
  );
}

export function buildSearchFallbackUrl(targetKey, title = '', artist = '') {
  const q = encodeURIComponent(`${title || ''} ${artist || ''}`.trim() || 'Music');
  switch (targetKey) {
    case 'spotify':
      return `https://open.spotify.com/search/${q}`;
    case 'appleMusic':
      return `https://music.apple.com/search?term=${q}`;
    case 'youtubeMusic':
      return `https://music.youtube.com/search?q=${q}`;
    case 'tidal':
      return `https://listen.tidal.com/search?q=${q}`;
    case 'deezer':
      return `https://www.deezer.com/search/${q}`;
    case 'qobuz':
      return `https://play.qobuz.com/search?q=${q}`;
    case 'amazonMusic':
      return `https://music.amazon.com/search/${q}`;
    case 'soundcloud':
      return `https://soundcloud.com/search?q=${q}`;
    case 'audiomack':
      return `https://audiomack.com/search?q=${q}`;
    case 'idagio':
      return `https://app.idagio.com/search/${q}`;
    case 'anghami':
      return `https://play.anghami.com/search/${q}`;
    case 'pocketCasts':
      return `https://play.pocketcasts.com/search?q=${q}`;
    default:
      return `https://open.spotify.com/search/${q}`;
  }
}

/**
 * Converts a resolved HTTPS player URL into a desktop protocol URI when preferDesktopApp is enabled.
 * Search fallbacks and unsupported schemes stay HTTPS.
 */
export function toDesktopNativeUri(targetKey, webUrl) {
  if (!webUrl || typeof webUrl !== 'string' || isSearchFallbackUrl(webUrl)) {
    return webUrl;
  }
  try {
    const parsed = new URL(webUrl);
    const path = parsed.pathname;

    if (targetKey === 'spotify') {
      const m = path.match(/\/(track|album|artist|episode|show)\/([a-zA-Z0-9]+)/i);
      if (m) return `spotify:${m[1].toLowerCase()}:${m[2]}`;
    }
    if (targetKey === 'deezer') {
      return `deezer://${parsed.host}${parsed.pathname}${parsed.search}`;
    }
    if (targetKey === 'tidal') {
      const m = path.match(/\/(track|album|artist)\/([0-9]+)/i);
      if (m) return `tidal://${m[1].toLowerCase()}/${m[2]}`;
    }
    if (targetKey === 'appleMusic') {
      return `music://${parsed.host}${parsed.pathname}${parsed.search}`;
    }
    if (targetKey === 'qobuz') {
      const m = path.match(/\/(track|album|artist)\/([a-zA-Z0-9_-]+)/i);
      if (m) return `qobuzapp://${m[1].toLowerCase()}/${m[2]}`;
    }
    if (targetKey === 'amazonMusic') {
      return `amznmp3://${parsed.host}${parsed.pathname}${parsed.search}`;
    }
  } catch {
    return webUrl;
  }
  return webUrl;
}

export function getTargetUrlFromResult(resolved, targetKey, preferDesktopApp = false) {
  const meta = TARGET_PLATFORMS[targetKey] || TARGET_PLATFORMS.spotify;
  const links = resolved?.links || {};
  const direct = links[meta.linkKey] || links[targetKey];
  const webUrl = direct || buildSearchFallbackUrl(targetKey, resolved?.title, resolved?.artist);
  if (preferDesktopApp) {
    return toDesktopNativeUri(targetKey, webUrl);
  }
  return webUrl;
}

/**
 * Resolves a music, album, or podcast URL directly via SongFlip's public
 * cache.songflip.link/resolve endpoint.
 */
export async function resolveMusicUrl(rawUrl, fetchImpl = globalThis.fetch) {
  const cleanUrl = extractMusicUrl(rawUrl) || rawUrl?.trim();
  if (!cleanUrl) {
    throw new Error('No valid music URL provided.');
  }

  const headers = {
    'X-Web-Client': 'songflip-extension',
    'Accept': 'application/json'
  };

  const res = await fetchImpl(`${SONGFLIP_CACHE_BASE}/resolve?url=${encodeURIComponent(cleanUrl)}`, {
    method: 'GET',
    headers
  }).catch(() => null);

  let data = null;
  if (res) {
    const ct = res.headers?.get?.('content-type') || '';
    if (ct.includes('application/json')) {
      data = await res.json().catch(() => null);
    }
  }

  if (!data) {
    throw new Error('SongFlip service is currently unreachable.');
  }

  if (data.error === 'UNSUPPORTED_MUSIC_SERVICE') {
    throw new Error(data.message || 'This music service is not supported yet.');
  }

  if (data.isPlaylist === true || data.status === 'playlist_detected') {
    return {
      status: 'playlist_detected',
      isPlaylist: true,
      isAudiobook: false,
      isPodcast: false,
      platform: data.platform || detectPlatformFromUrl(cleanUrl),
      originalUrl: data.originalUrl || cleanUrl
    };
  }

  if (data.isAudiobook === true) {
    return {
      status: 'audiobook_detected',
      isPlaylist: false,
      isAudiobook: true,
      isPodcast: false,
      platform: data.platform || detectPlatformFromUrl(cleanUrl),
      originalUrl: data.originalUrl || cleanUrl
    };
  }

  if ((data.isPodcast === true || data.entityType === 'podcast') && data.item) {
    const pItem = data.item;
    const hash = pItem.id || data.hash || 'podcast';
    return {
      status: 'success',
      isPlaylist: false,
      isAudiobook: false,
      isPodcast: true,
      sourcePlatform: pItem.sourcePlatform || detectPlatformFromUrl(cleanUrl),
      title: pItem.episodeTitle || pItem.showTitle || 'Podcast Episode',
      artist: pItem.showTitle || 'Podcast',
      album: '',
      coverUrl: pItem.thumbnailUrl || '',
      smartHash: hash,
      smartUrl: `${SONGFLIP_WEB_BASE}/s/${hash}`,
      links: pItem.links || {}
    };
  }

  if (data.status === 'success' && data.item) {
    const item = data.item;
    const hash = item.hash || data.hash || item.id || 'track';
    return {
      status: 'success',
      isPlaylist: false,
      isAudiobook: false,
      isPodcast: false,
      isAlbum: Boolean(item.isAlbum),
      sourcePlatform: item.sourcePlatform || detectPlatformFromUrl(cleanUrl),
      title: item.title || 'Unknown Track',
      artist: (item.artist || '').replace(/ - Topic$/i, ''),
      album: item.isAlbum ? '' : (item.album || ''),
      coverUrl: item.thumbnailUrl || '',
      smartHash: hash,
      smartUrl: `${SONGFLIP_WEB_BASE}/s/${hash}`,
      links: item.links || {}
    };
  }

  throw new Error(data.message || data.error || 'Could not resolve this music link.');
}
