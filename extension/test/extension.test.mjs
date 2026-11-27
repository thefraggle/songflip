import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  TARGET_PLATFORMS,
  OPTIONAL_MUSIC_ORIGINS,
  extractMusicUrl,
  detectPlatformFromUrl,
  isSupportedMusicUrl,
  isPlaylistUrl,
  isPodcastUrl,
  isAudiobookUrl,
  toDesktopNativeUri,
  buildSearchFallbackUrl,
  isSearchFallbackUrl,
  resolveMusicUrl
} from '../shared.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const EXT_DIR = path.resolve(__dirname, '..');

test('TARGET_PLATFORMS includes strictly the 11 supported music targets', () => {
  assert.equal(Object.keys(TARGET_PLATFORMS).length, 11);
  assert.ok(TARGET_PLATFORMS.spotify);
  assert.ok(TARGET_PLATFORMS.appleMusic);
  assert.ok(TARGET_PLATFORMS.youtubeMusic);
  assert.ok(TARGET_PLATFORMS.tidal);
  assert.ok(TARGET_PLATFORMS.deezer);
  assert.ok(TARGET_PLATFORMS.qobuz);
  assert.ok(TARGET_PLATFORMS.amazonMusic);
  assert.ok(TARGET_PLATFORMS.soundcloud);
  assert.ok(TARGET_PLATFORMS.audiomack);
  assert.ok(TARGET_PLATFORMS.idagio);
  assert.ok(TARGET_PLATFORMS.anghami);
  assert.equal(TARGET_PLATFORMS.pocketCasts, undefined);
  assert.ok(OPTIONAL_MUSIC_ORIGINS.length >= 15);
});

test('extractMusicUrl extracts URLs from chat text and unwraps social redirects', () => {
  assert.equal(
    extractMusicUrl('Check this out: https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=abc123!'),
    'https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT'
  );
  assert.equal(
    extractMusicUrl('https://www.youtube.com/redirect?q=https%3A%2F%2Fopen.spotify.com%2Ftrack%2F12345&event=video_description'),
    'https://open.spotify.com/track/12345'
  );
  assert.equal(
    extractMusicUrl('https://l.facebook.com/l.php?u=https%3A%2F%2Fmusic.apple.com%2Fus%2Falbum%2Ftest%2F123'),
    'https://music.apple.com/us/album/test/123'
  );
  assert.equal(
    extractMusicUrl('https://www.youtube.com/watch?v=UHyKsfFpil8&list=RDUHyKsfFpil8&start_radio=1'),
    'https://www.youtube.com/watch?v=UHyKsfFpil8'
  );
  assert.equal(extractMusicUrl('just random text without link'), null);
});

test('detectPlatformFromUrl and isSupportedMusicUrl identify all supported services', () => {
  assert.equal(detectPlatformFromUrl('https://open.spotify.com/track/abc'), 'spotify');
  assert.equal(detectPlatformFromUrl('https://music.apple.com/de/album/test/123?i=456'), 'appleMusic');
  assert.equal(detectPlatformFromUrl('https://music.youtube.com/watch?v=dQw4w9WgXcQ'), 'youtubeMusic');
  assert.equal(detectPlatformFromUrl('https://www.youtube.com/shorts/abc123'), 'youtubeMusic');
  assert.equal(detectPlatformFromUrl('https://listen.tidal.com/track/12345'), 'tidal');
  assert.equal(detectPlatformFromUrl('https://www.deezer.com/track/12345'), 'deezer');
  assert.equal(detectPlatformFromUrl('https://open.qobuz.com/track/12345'), 'qobuz');
  assert.equal(detectPlatformFromUrl('https://www.last.fm/music/Daft+Punk/_/Get+Lucky'), 'lastfm');
  assert.equal(detectPlatformFromUrl('https://www.last.fm/music/+noredirect/Daft+Punk/_/Get+Lucky'), 'lastfm');
  assert.equal(detectPlatformFromUrl('https://artist.bandcamp.com/track/song'), 'bandcamp');
  assert.equal(detectPlatformFromUrl('https://www.beatport.com/track/one-more-time/123'), 'beatport');
  assert.equal(detectPlatformFromUrl('https://app.idagio.com/recordings/123'), 'idagio');
  assert.equal(detectPlatformFromUrl('https://play.anghami.com/song/123'), 'anghami');
  assert.equal(isSupportedMusicUrl('https://open.spotify.com/track/abc'), true);
  assert.equal(isSupportedMusicUrl('https://songflip.link/s/rickroll'), false);
  assert.equal(isSupportedMusicUrl('https://example.com/article'), false);
});

test('isPlaylistUrl distinguishes playlists from YouTube/BitChord albums', () => {
  assert.equal(isPlaylistUrl('https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M'), true);
  assert.equal(isPlaylistUrl('https://music.apple.com/us/playlist/favorites/pl.123'), true);
  assert.equal(isPlaylistUrl('https://music.youtube.com/playlist?list=PL123456'), true);
  assert.equal(isPlaylistUrl('https://www.youtube.com/playlist?list=PL123456'), true);
  assert.equal(isPlaylistUrl('https://www.youtube.com/watch?v=UHyKsfFpil8&list=RDUHyKsfFpil8&start_radio=1'), false);
  assert.equal(isPlaylistUrl('https://www.deezer.com/us/playlist/908622995'), true);
  assert.equal(isPlaylistUrl('https://tidal.com/browse/playlist/abc-def'), true);
  // Albums must NOT be treated as playlists
  assert.equal(isPlaylistUrl('https://music.youtube.com/playlist?list=OLAK5uy_abc123'), false);
  assert.equal(isPlaylistUrl('https://music.youtube.com/browse/MPREb_xyz123'), false);
  assert.equal(isPlaylistUrl('https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT'), false);
});

test('isPodcastUrl and isAudiobookUrl detect podcasts and audiobooks accurately', () => {
  assert.equal(isPodcastUrl('https://open.spotify.com/episode/abc123'), true);
  assert.equal(isPodcastUrl('https://open.spotify.com/show/abc123'), true);
  assert.equal(isPodcastUrl('https://podcasts.apple.com/us/podcast/show/id123'), true);
  assert.equal(isPodcastUrl('https://pca.st/abc123'), true);
  assert.equal(isAudiobookUrl('https://www.audible.com/pd/Test/B00123'), true);
  assert.equal(isAudiobookUrl('https://open.spotify.com/audiobook/abc123'), true);
  assert.equal(isPodcastUrl('https://open.spotify.com/track/abc123'), false);
});

test('toDesktopNativeUri converts web URLs to native desktop protocol URIs', () => {
  assert.equal(
    toDesktopNativeUri('spotify', 'https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT'),
    'spotify:track:4cOdK2wGLETKBW3PvgPWqT'
  );
  assert.equal(
    toDesktopNativeUri('spotify', 'https://open.spotify.com/album/12345'),
    'spotify:album:12345'
  );
  assert.equal(
    toDesktopNativeUri('deezer', 'https://www.deezer.com/track/3135556'),
    'deezer://www.deezer.com/track/3135556'
  );
  assert.equal(
    toDesktopNativeUri('tidal', 'https://listen.tidal.com/track/77610756'),
    'tidal://track/77610756'
  );
  assert.equal(
    toDesktopNativeUri('appleMusic', 'https://music.apple.com/us/album/test/123?i=456'),
    'music://music.apple.com/us/album/test/123?i=456'
  );
  assert.equal(
    toDesktopNativeUri('qobuz', 'https://open.qobuz.com/track/123456'),
    'qobuzapp://track/123456'
  );
  // Search fallbacks should remain https:// URLs
  assert.equal(
    toDesktopNativeUri('deezer', 'https://www.deezer.com/search/rick%20astley'),
    'https://www.deezer.com/search/rick%20astley'
  );
});

test('buildSearchFallbackUrl and isSearchFallbackUrl match backend conventions', () => {
  const qobuzSearch = buildSearchFallbackUrl('qobuz', 'Never Gonna Give You Up', 'Rick Astley');
  assert.equal(qobuzSearch.startsWith('https://play.qobuz.com/search?q='), true);
  assert.equal(isSearchFallbackUrl(qobuzSearch), true);
  assert.equal(isSearchFallbackUrl('https://open.qobuz.com/track/12345'), false);
});

test('resolveMusicUrl queries cache.songflip.link/resolve directly', async () => {
  const calls = [];
  const mockFetch = async (url) => {
    calls.push(url);
    return {
      ok: true,
      status: 200,
      headers: { get: () => 'application/json' },
      json: async () => ({
        status: 'success',
        hash: 'rick1234',
        item: {
          id: 'rick1234',
          title: 'Never Gonna Give You Up',
          artist: 'Rick Astley',
          album: 'Whenever You Need Somebody',
          thumbnailUrl: 'https://example.com/cover.jpg',
          links: {
            spotify: 'https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT',
            deezer: 'https://www.deezer.com/track/3135556'
          }
        }
      })
    };
  };

  const result = await resolveMusicUrl('https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT', mockFetch);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].startsWith('https://cache.songflip.link/resolve?url='), true);
  assert.equal(result.status, 'success');
  assert.equal(result.title, 'Never Gonna Give You Up');
  assert.equal(result.artist, 'Rick Astley');
  assert.equal(result.smartUrl, 'https://songflip.link/s/rick1234');
  assert.equal(result.links.deezer, 'https://www.deezer.com/track/3135556');
});

test('manifest and store listing descriptions avoid CWS Yellow Argon brand keyword lists', () => {
  const bannedBrandPattern = /spotify|apple music|youtube|tidal|deezer|qobuz|last\.fm|soundcloud|bandcamp|amazon music/i;

  for (const file of ['manifest.json', 'manifest.firefox.json']) {
    const manifest = JSON.parse(fs.readFileSync(path.join(EXT_DIR, file), 'utf8'));
    assert.equal(manifest.version, '1.0.1');
    assert.ok(manifest.description.length > 20 && manifest.description.length <= 132);
    assert.equal(bannedBrandPattern.test(manifest.description), false, `${file} description contains brand keyword spam`);
  }

  const cwsDoc = fs.readFileSync(path.join(EXT_DIR, 'CHROMEWEBSTORE.md'), 'utf8');
  const storeListingSection = cwsDoc.split('---')[0];
  assert.equal(
    bannedBrandPattern.test(storeListingSection),
    false,
    'CHROMEWEBSTORE.md Store Listing section contains third-party brand names that trigger Yellow Argon'
  );
});
