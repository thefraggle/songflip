import { onRequest } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import axios from "axios";
import * as crypto from "crypto";
import { LRUCache } from "lru-cache";

admin.initializeApp();
const db = admin.firestore();

// 1 Hour in-memory cache for user PRO status (User ID -> boolean)
const userProCache = new LRUCache<string, boolean>({
  max: 10000,
  ttl: 1000 * 60 * 60, // 1 hour
});

// In-memory IP Rate Limiter (IP -> request count & reset timestamp)
const rateLimitCache = new LRUCache<string, { count: number; resetAt: number }>({
  max: 50000,
  ttl: 1000 * 60, // 1 minute window
});

function isRateLimited(key: string, maxRequests: number, windowMs = 60000): boolean {
  if (!key || key === "127.0.0.1" || key === "::1") return false;
  const now = Date.now();
  const entry = rateLimitCache.get(key);

  if (!entry || now > entry.resetAt) {
    rateLimitCache.set(key, { count: 1, resetAt: now + windowMs });
    return false;
  }

  if (entry.count >= maxRequests) {
    return true;
  }

  entry.count++;
  return false;
}

const REVENUECAT_SECRET_KEY = process.env.REVENUECAT_SECRET_KEY || "";

/**
 * Standard API Security Headers for API JSON endpoints.
 */
function applyApiSecurityHeaders(res: any) {
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
  res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
  res.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none';");
}

/**
 * WebShare Landing Page Security Headers (CSP + Anti-Clickjacking).
 */
function applyWebShareSecurityHeaders(res: any) {
  res.setHeader("X-Frame-Options", "DENY");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
  res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
  res.setHeader(
    "Content-Security-Policy",
    "default-src 'self'; script-src 'self' 'unsafe-inline' https://telemetry.goork.de; style-src 'self' 'unsafe-inline' https://songflip.link; font-src 'self'; img-src 'self' data: https://songflip.link https://telemetry.goork.de https://i.scdn.co https://*.scdn.co https://*.spotifycdn.com https://*.mzstatic.com https://i.ytimg.com https://*.ytimg.com https://lh3.googleusercontent.com https://*.dzcdn.net https://resources.tidal.com https://*.tidal.com https://m.media-amazon.com https://*.media-amazon.com https://images-na.ssl-images-amazon.com https://*.sndcdn.com https://f4.bcbits.com https://*.bcbits.com; connect-src 'self' https://telemetry.goork.de; frame-src 'none'; frame-ancestors 'none'; object-src 'none'; media-src 'none'; worker-src 'none'; manifest-src 'self'; base-uri 'self'; form-action 'self'; upgrade-insecure-requests;"
  );
}

/**
 * Normalizes music URLs into clean canonical identifiers.
 */
function cleanSearchQuery(query: string): string {
  let cleaned = query.trim();
  if (!cleaned) return cleaned;

  cleaned = cleaned.replace(/\s*-\s*\d{4}\s+remaster(?:ed)?/gi, "");
  cleaned = cleaned.replace(/\s*[\(\[]\s*\d{4}\s+remaster(?:ed)?\s*[\)\]]/gi, "");
  cleaned = cleaned.replace(/\s*[\(\[]\s*remaster(?:ed)?(?:\s+\d{4})?\s*[\)\]]/gi, "");
  cleaned = cleaned.replace(/\s*-\s*remaster(?:ed)?/gi, "");

  cleaned = cleaned.replace(/\s*[\(\[]\s*(?:radio|single|album|extended|club)\s+edit\s*[\)\]]/gi, "");
  cleaned = cleaned.replace(/\s*[\(\[]\s*(?:radio|single|album)\s+version\s*[\)\]]/gi, "");

  cleaned = cleaned.replace(/\s*[\(\[]\s*live(?:\s+at[^)\]]+)?\s*[\)\]]/gi, "");
  cleaned = cleaned.replace(/\s*-\s*live(?:\s+at[^-]+)?/gi, "");

  cleaned = cleaned.replace(/\s{2,}/g, " ").trim();
  return cleaned || query.trim();
}

function normalizeMusicUrl(rawUrl: string): string {
  try {
    const trimmed = rawUrl.trim();
    // 1. Spotify
    const spotifyTrack = trimmed.match(/open\.spotify\.com(?:\/intl-[a-z-]+)?\/track\/([a-zA-Z0-9]+)/i);
    if (spotifyTrack && spotifyTrack[1]) return `https://open.spotify.com/track/${spotifyTrack[1]}`;
    const spotifyAlbum = trimmed.match(/open\.spotify\.com(?:\/intl-[a-z-]+)?\/album\/([a-zA-Z0-9]+)/i);
    if (spotifyAlbum && spotifyAlbum[1]) return `https://open.spotify.com/album/${spotifyAlbum[1]}`;
    const spotifyArtist = trimmed.match(/open\.spotify\.com(?:\/intl-[a-z-]+)?\/artist\/([a-zA-Z0-9]+)/i);
    if (spotifyArtist && spotifyArtist[1]) return `https://open.spotify.com/artist/${spotifyArtist[1]}`;

    // 2. Apple Music
    if (trimmed.includes("apple.com") && trimmed.includes("i=")) {
      const base = trimmed.split("?")[0];
      const match = trimmed.match(/[?&]i=(\d+)/);
      if (match && match[1]) return `${base}?i=${match[1]}`;
    } else if (trimmed.includes("apple.com") && (trimmed.includes("/album/") || trimmed.includes("/song/"))) {
      return trimmed.split("?")[0];
    }

    // 3. Deezer
    const deezerMatch = trimmed.match(/deezer\.com(?:\/[a-z-]+)?\/(track|album|artist)\/(\d+)/i);
    if (deezerMatch && deezerMatch[1] && deezerMatch[2]) {
      return `https://www.deezer.com/${deezerMatch[1].toLowerCase()}/${deezerMatch[2]}`;
    }

    // 4. YouTube & YouTube Music
    const ytWatchMatch = trimmed.match(/(?:music\.)?youtube\.com\/watch\?.*[?&]v=([a-zA-Z0-9_-]{11})/i);
    if (ytWatchMatch && ytWatchMatch[1]) {
      const host = trimmed.includes("music.youtube.com") ? "https://music.youtube.com" : "https://www.youtube.com";
      return `${host}/watch?v=${ytWatchMatch[1]}`;
    }
    const ytShortMatch = trimmed.match(/youtu\.be\/([a-zA-Z0-9_-]{11})/i);
    if (ytShortMatch && ytShortMatch[1]) {
      return `https://youtu.be/${ytShortMatch[1]}`;
    }

    // 5. Amazon Music
    if (trimmed.includes("music.amazon.") && trimmed.includes("trackAsin=")) {
      const base = trimmed.split("?")[0];
      const asinMatch = trimmed.match(/[?&]trackAsin=([a-zA-Z0-9]+)/);
      if (asinMatch && asinMatch[1]) return `${base}?trackAsin=${asinMatch[1]}`;
    } else if (trimmed.includes("music.amazon.")) {
      return trimmed.split("?")[0];
    }

    // 6. Tidal
    const tidalMatch = trimmed.match(/(?:listen\.)?tidal\.com\/(?:browse\/)?(track|album)\/([a-zA-Z0-9-]+)/i);
    if (tidalMatch && tidalMatch[1] && tidalMatch[2]) {
      return `https://tidal.com/browse/${tidalMatch[1].toLowerCase()}/${tidalMatch[2]}`;
    }

    // 7. SoundCloud
    const soundcloudMatch = trimmed.match(/(?:https?:\/\/)?(?:www\.|m\.)?soundcloud\.com\/([a-zA-Z0-9_-]+)\/([a-zA-Z0-9_-]+)/i);
    if (soundcloudMatch && !["search", "discover", "upload", "you", "stream", "settings"].includes(soundcloudMatch[1].toLowerCase())) {
      return `https://soundcloud.com/${soundcloudMatch[1]}/${soundcloudMatch[2]}`;
    }

    // 8. Bandcamp
    const bandcampMatch = trimmed.match(/(?:https?:\/\/)?([a-zA-Z0-9_-]+)\.bandcamp\.com\/(track|album)\/([a-zA-Z0-9_-]+)/i);
    if (bandcampMatch) {
      return `https://${bandcampMatch[1]}.bandcamp.com/${bandcampMatch[2].toLowerCase()}/${bandcampMatch[3]}`;
    }

    const url = new URL(trimmed);
    const trackingKeys = [
      "si", "context", "rowid", "rowId", "feature", "src", "ref", "ref_", "tag",
      "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
      "gclid", "fbclid", "igshid", "msclkid", "uo", "at", "ct", "app", "ls"
    ];
    for (const key of trackingKeys) {
      url.searchParams.delete(key);
    }

    return url.toString();
  } catch {
    return rawUrl.trim();
  }
}

/**
 * Generates SHA-256 hash for document key.
 */
function hashUrl(url: string): string {
  return crypto.createHash("sha256").update(url.toLowerCase()).digest("hex");
}

/**
 * Verifies PRO entitlement via RevenueCat REST API or valid VIP Coupon.
 */
async function verifyProStatus(userId: string): Promise<boolean> {
  if (userId.startsWith("coupon:")) {
    return true;
  }

  const cached = userProCache.get(userId);
  if (cached !== undefined) {
    return cached;
  }

  if (!REVENUECAT_SECRET_KEY) {
    console.error("REVENUECAT_SECRET_KEY not configured");
    return false;
  }

  try {
    const response = await axios.get(
      `https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(userId)}`,
      {
        headers: {
          Authorization: `Bearer ${REVENUECAT_SECRET_KEY}`,
          "Content-Type": "application/json",
        },
        timeout: 4000,
      }
    );

    const subscriber = response.data?.subscriber;
    const entitlements = subscriber?.entitlements || {};
    const subscriptions = subscriber?.subscriptions || {};
    const nonSubscriptions = subscriber?.non_subscriptions || {};
    
    // Check both 'songflip_pro' and 'pro' entitlements, any active subscription or lifetime non-subscription
    const proEntitlement = entitlements["songflip_pro"] || entitlements["pro"] || Object.values(entitlements).find((e: any) => {
      return e.expires_date === null || new Date(e.expires_date).getTime() > Date.now();
    });
    const hasActiveSubscription = Object.values(subscriptions).some((s: any) => 
      s.expires_date === null || new Date(s.expires_date).getTime() > Date.now()
    );
    const hasNonSubscription = Object.keys(nonSubscriptions).length > 0;

    const isPro = !!(hasActiveSubscription || hasNonSubscription || (proEntitlement && (
      (proEntitlement as any).expires_date === null ||
      new Date((proEntitlement as any).expires_date).getTime() > Date.now()
    )));

    userProCache.set(userId, isPro);
    return isPro;
  } catch (error: any) {
    console.error(`RevenueCat verification error for user ${userId}:`, error?.response?.status || error.message);
    userProCache.set(userId, false, { ttl: 1000 * 60 * 5 }); // 5 min cache on error/inactive
    return false;
  }
}

interface PlatformLinks {
  spotify?: string;
  youtubeMusic?: string;
  appleMusic?: string;
  deezer?: string;
  tidal?: string;
  amazonMusic?: string;
  soundcloud?: string;
  bandcamp?: string;
}

interface SongMetadata {
  title: string;
  artist: string;
  thumbnailUrl?: string;
  isAlbum: boolean;
  isArtist?: boolean;
  isFallback?: boolean;
  links: PlatformLinks;
  updatedAt: number;
  expiresAt: admin.firestore.Timestamp;
}

function normalizeToSongLinkDirectUrl(rawUrl: string): string {
  const clean = rawUrl.includes("?") ? rawUrl.substring(0, rawUrl.indexOf("?")) : rawUrl;

  // Spotify
  if (clean.includes("spotify.com") && clean.includes("/track/")) {
    const id = clean.split("/track/")[1]?.split("/")[0]?.trim();
    if (id) return `https://song.link/s/${id}`;
  }
  if (clean.includes("spotify.com") && clean.includes("/album/")) {
    const id = clean.split("/album/")[1]?.split("/")[0]?.trim();
    if (id) return `https://album.link/s/${id}`;
  }

  // Apple Music
  if (rawUrl.includes("apple.com") && rawUrl.includes("i=")) {
    const id = rawUrl.split("i=")[1]?.split("&")[0]?.split("?")[0]?.trim();
    if (id) return `https://song.link/i/${id}`;
  }
  if (clean.includes("apple.com") && clean.includes("/song/")) {
    const parts = clean.split("/song/")[1]?.split("/");
    const id = parts?.[parts.length - 1]?.trim();
    if (id && /^\d+$/.test(id)) return `https://song.link/i/${id}`;
  }
  if (clean.includes("apple.com") && clean.includes("/album/")) {
    const parts = clean.split("/album/")[1]?.split("/");
    const id = parts?.[parts.length - 1]?.trim();
    if (id && /^\d+$/.test(id)) return `https://album.link/i/${id}`;
  }

  // Deezer
  if (clean.includes("deezer.com") && clean.includes("/track/")) {
    const id = clean.split("/track/")[1]?.split("/")[0]?.trim();
    if (id) return `https://song.link/d/${id}`;
  }
  if (clean.includes("deezer.com") && clean.includes("/album/")) {
    const id = clean.split("/album/")[1]?.split("/")[0]?.trim();
    if (id) return `https://album.link/d/${id}`;
  }

  // Tidal
  if (clean.includes("tidal.com") && clean.includes("/track/")) {
    const id = clean.split("/track/")[1]?.split("/")[0]?.trim();
    if (id) return `https://song.link/t/${id}`;
  }

  // YouTube
  if (clean.includes("youtu.be/")) {
    const id = clean.split("youtu.be/")[1]?.split("/")[0]?.trim();
    if (id) return `https://song.link/y/${id}`;
  }
  if (rawUrl.includes("youtube.com/watch") && rawUrl.includes("v=")) {
    const id = rawUrl.split("v=")[1]?.split("&")[0]?.split("?")[0]?.trim();
    if (id) return `https://song.link/y/${id}`;
  }

  return clean.includes("/album/") ? `https://album.link/${rawUrl}` : `https://song.link/${rawUrl}`;
}

function sanitizeMusicMetadata(rawTitle: string, rawArtist: string): { title: string; artist: string; isGenericArtist: boolean } {
  let title = (rawTitle || "").trim();
  let artist = (rawArtist || "").trim();

  // Strip generic video & album noise
  title = title
    .replace(/\(Official\s+(?:Video|Audio|Music\s+Video|Lyric\s+Video|HD|4K)\)/gi, "")
    .replace(/\[(?:Official\s+Video|Official\s+Audio|HD|4K|HQ|Lyrics)\]/gi, "")
    .replace(/\b(?:Full\s+Album|Official\s+Audio|Official\s+Video)\b/gi, "")
    .trim();

  if (/^album\s*-\s*/i.test(title)) {
    title = title.replace(/^album\s*-\s*/i, "").trim();
  }
  if (/^track\s*-\s*/i.test(title)) {
    title = title.replace(/^track\s*-\s*/i, "").trim();
  }

  const isGenericArtist = !artist || 
    /^(?:YouTube(?:\s+Music)?|Various\s+Artists|Topic|Auto-generated\s+by\s+YouTube|Unknown\s+Artist)$/i.test(artist);

  if (isGenericArtist && title.includes(" - ")) {
    const parts = title.split(" - ");
    if (parts.length >= 2) {
      artist = parts[0].trim();
      title = parts.slice(1).join(" - ").trim();
    }
  } else if (isGenericArtist) {
    artist = "";
  }

  return { title, artist, isGenericArtist };
}

function isArtistUrl(url: string): boolean {
  const clean = url.toLowerCase();
  if (clean.includes("/artist/")) return true;
  if ((clean.includes("youtube.com") || clean.includes("youtu.be")) && (clean.includes("/@") || clean.includes("/channel/") || clean.includes("/user/"))) {
    return true;
  }
  return false;
}

/**
 * Resolves direct YouTube Music Channel for artist pages.
 */
async function resolveYouTubeArtistChannelLive(artistName: string): Promise<string | null> {
  try {
    const encoded = encodeURIComponent(`${artistName} artist`);
    const ytUrl = `https://www.youtube.com/results?search_query=${encoded}&sp=EgIQAg%253D%253D`;
    const res = await axios.get(ytUrl, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language": "en-US,en;q=0.9",
      },
      timeout: 4000,
    });
    const html = typeof res.data === "string" ? res.data : "";
    const channelMatch = html.match(/"channelId":"(UC[a-zA-Z0-9_-]{22})"/);
    if (channelMatch && channelMatch[1]) {
      return `https://music.youtube.com/channel/${channelMatch[1]}`;
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * Resolves direct Apple Music Artist URL via iTunes Search API.
 */
async function resolveAppleMusicArtistLive(artistName: string): Promise<string | null> {
  try {
    const itunesRes = await axios.get("https://itunes.apple.com/search", {
      params: {
        term: artistName,
        entity: "musicArtist",
        limit: 1,
      },
      timeout: 4000,
    });
    const first = itunesRes.data?.results?.[0];
    if (first && (first.artistLinkUrl || first.artistViewUrl)) {
      return first.artistLinkUrl || first.artistViewUrl;
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * Resolves artist metadata & cross-platform direct links for artist profile URLs.
 */
async function resolveArtistLive(url: string): Promise<SongMetadata | null> {
  try {
    const clean = url.toLowerCase();
    let artistName = "";
    let thumbnailUrl = "";
    let deezerLink = clean.includes("deezer.com") ? url : "";
    let appleMusicLink = clean.includes("apple.com") ? url : "";
    let youtubeMusicLink = (clean.includes("music.youtube.com") || clean.includes("youtube.com")) ? url : "";

    // 0. Spotify Artist
    if (clean.includes("spotify.com") && clean.includes("/artist/")) {
      const match = url.match(/\/artist\/([a-zA-Z0-9]+)/);
      if (match && match[1]) {
        const spotifyId = match[1];
        try {
          const oembedRes = await axios.get(`https://open.spotify.com/oembed?url=https://open.spotify.com/artist/${spotifyId}`, { timeout: 4000 });
          if (oembedRes.data && oembedRes.data.title) {
            artistName = oembedRes.data.title;
            thumbnailUrl = oembedRes.data.thumbnail_url || "";
          }
        } catch (_) {}
      }
    }

    // 1. Apple Music Artist
    else if (clean.includes("apple.com") && clean.includes("/artist/")) {
      const match = clean.match(/\/artist\/([^/]+)\/(\d+)/);
      if (match && match[1]) {
        artistName = decodeURIComponent(match[1]).replace(/-/g, " ").trim();
        artistName = artistName.replace(/\b\w/g, (c) => c.toUpperCase());
        appleMusicLink = url;
      }
    }

    // 2. Deezer Artist
    else if (clean.includes("deezer.com") && clean.includes("/artist/")) {
      const match = clean.match(/\/artist\/(\d+)/);
      if (match && match[1]) {
        try {
          const res = await axios.get(`https://api.deezer.com/artist/${match[1]}`, { timeout: 4000 });
          if (res.data && !res.data.error) {
            artistName = res.data.name || "";
            thumbnailUrl = res.data.picture_xl || "";
            deezerLink = res.data.link || url;
          }
        } catch (_) {}
      }
    }

    // 3. YouTube / YouTube Music Channel / Handle
    else if (clean.includes("youtube.com") || clean.includes("youtu.be")) {
      try {
        const res = await axios.get(url, {
          headers: {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          },
          timeout: 5000,
        });
        const html = res.data;
        if (typeof html === "string") {
          const ogTitleMatch = html.match(/<meta property="og:title" content="(.*?)">/);
          const ogDescMatch = html.match(/<meta property="og:description" content="(.*?)">/);
          const ogImageMatch = html.match(/<meta property="og:image" content="(.*?)">/);

          const title = ogTitleMatch ? ogTitleMatch[1] : "";
          const desc = ogDescMatch ? ogDescMatch[1] : "";
          thumbnailUrl = ogImageMatch ? ogImageMatch[1] : "";

          const vonMatch = desc.match(/(?:von|of)\s+([a-zA-Z0-9äöüÄÖÜß\s\-]+?)(?:\.|\,|$)/i);
          if (vonMatch && vonMatch[1] && vonMatch[1].trim().length > 2) {
            artistName = vonMatch[1].trim();
          } else {
            artistName = title.replace(/\s*-\s*YouTube/gi, "").replace(/TV/gi, "").replace(/Official/gi, "").trim();
          }
        }
      } catch (_) {}
    }

    // Enrich via Deezer Artist API for direct link, HD artwork, and exact spelling
    if (artistName) {
      try {
        const deezerRes = await axios.get(`https://api.deezer.com/search/artist?q=${encodeURIComponent(artistName)}`, { timeout: 4000 });
        const first = deezerRes.data?.data?.[0];
        if (first) {
          artistName = first.name || artistName;
          if (!deezerLink) {
            deezerLink = first.link || `https://www.deezer.com/artist/${first.id}`;
          }
          if (!thumbnailUrl || thumbnailUrl.includes("icon.png")) {
            thumbnailUrl = first.picture_xl || thumbnailUrl;
          }
        }
      } catch (_) {}
    }

    if (!artistName) return null;

    // Resolve direct Apple Music & YouTube Music links if not already present
    if (!appleMusicLink) {
      appleMusicLink = (await resolveAppleMusicArtistLive(artistName)) || "";
    }
    if (!youtubeMusicLink) {
      youtubeMusicLink = (await resolveYouTubeArtistChannelLive(artistName)) || "";
    }

    const q = encodeURIComponent(artistName);
    const linksMap: PlatformLinks = {
      spotify: clean.includes("spotify.com") ? url : `https://open.spotify.com/search/${q}`,
      appleMusic: appleMusicLink || `https://music.apple.com/search?term=${q}`,
      youtubeMusic: youtubeMusicLink || `https://music.youtube.com/search?q=${q}`,
      deezer: deezerLink || `https://www.deezer.com/search/${q}`,
      tidal: clean.includes("tidal.com") ? url : `https://listen.tidal.com/search?q=${q}`,
      amazonMusic: clean.includes("amazon.") ? url : `https://music.amazon.com/search/${q}`,
    };

    const now = Date.now();
    const ninetyDaysMs = 90 * 24 * 60 * 60 * 1000;
    const expiresAt = admin.firestore.Timestamp.fromMillis(now + ninetyDaysMs);

    return {
      title: artistName,
      artist: "Artist",
      thumbnailUrl: thumbnailUrl || "https://songflip.link/icon.png",
      isAlbum: false,
      isArtist: true,
      links: linksMap,
      updatedAt: now,
      expiresAt,
    };
  } catch (err: any) {
    console.error("Artist resolution failed:", url, err?.message);
    return null;
  }
}

/**
 * Dead-Link Detection & oEmbed Guard:
 * Fast pre-validation check against YouTube oEmbed endpoint to reject deleted, private, or blocked videos.
 */
async function isYoutubeVideoPlayable(videoId: string): Promise<boolean> {
  if (!videoId || videoId.length !== 11) return false;
  try {
    const oembedUrl = `https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=${videoId}&format=json`;
    const res = await axios.get(oembedUrl, {
      timeout: 2500,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
      },
    });
    return res.status === 200;
  } catch (err: any) {
    if (err.response && (err.response.status === 404 || err.response.status === 401 || err.response.status === 403)) {
      return false;
    }
    // Network timeout or temporary glitch: don't falsely discard
    return true;
  }
}

/**
 * Resolves direct YouTube video ID or Album playlist ID for instant playback.
 */
async function resolveYouTubeDirectPlayLive(query: string, isAlbum = false): Promise<string | null> {
  try {
    const encoded = encodeURIComponent(query);
    const ytUrl = isAlbum
      ? `https://www.youtube.com/results?search_query=${encoded}&sp=EgIQAw%253D%253D`
      : `https://www.youtube.com/results?search_query=${encoded}&sp=EgIQAQ%253D%253D`;

    const res = await axios.get(ytUrl, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language": "en-US,en;q=0.9",
      },
      timeout: 4000,
    });

    const html = typeof res.data === "string" ? res.data : "";
    if (isAlbum) {
      const albumMatch = html.match(/"playlistId":"(OLAK5uy_[a-zA-Z0-9_-]+)"/) || html.match(/"playlistId":"([a-zA-Z0-9_-]{18,})"/);
      if (albumMatch && albumMatch[1]) {
        return `https://music.youtube.com/playlist?list=${albumMatch[1]}`;
      }
    }

    // 1. Prioritize official videoRenderer (filters out Shorts, reels, fan clips) with Dead-Link Guard
    const vrMatches = [...html.matchAll(/"videoRenderer":\{"videoId":"([a-zA-Z0-9_-]{11})"/g)];
    for (const match of vrMatches.slice(0, 3)) {
      const candidateId = match[1];
      if (await isYoutubeVideoPlayable(candidateId)) {
        return `https://music.youtube.com/watch?v=${candidateId}`;
      }
    }

    const videoMatches = [...html.matchAll(/"videoId":"([a-zA-Z0-9_-]{11})"/g)];
    for (const match of videoMatches.slice(0, 3)) {
      const candidateId = match[1];
      if (await isYoutubeVideoPlayable(candidateId)) {
        return `https://music.youtube.com/watch?v=${candidateId}`;
      }
    }

    return null;
  } catch {
    return null;
  }
}

function isSearchUrl(url: string): boolean {
  const clean = url.toLowerCase();
  return clean.includes("spotify.com/search") ||
    (clean.includes("music.apple.com") && clean.includes("/search")) ||
    clean.includes("music.youtube.com/search") ||
    clean.includes("youtube.com/results") ||
    (clean.includes("deezer.com") && clean.includes("/search")) ||
    (clean.includes("tidal.com") && clean.includes("/search")) ||
    (clean.includes("music.amazon.") && clean.includes("/search"));
}

function extractSearchQuery(url: string): string | null {
  const clean = url.trim();
  const lower = clean.toLowerCase();
  let rawQuery: string | null = null;

  if (lower.includes("spotify.com") && lower.includes("/search")) {
    const after = clean.split("/search/")[1] || clean.split("/search?")[1] || "";
    if (after.startsWith("q=")) {
      rawQuery = after.substring(2).split("&")[0].split("?")[0];
    } else {
      rawQuery = after.split("?")[0].split("&")[0];
    }
  } else if (lower.includes("apple.com") && lower.includes("/search")) {
    if (clean.includes("term=")) {
      rawQuery = clean.split("term=")[1].split("&")[0];
    } else if (clean.includes("q=")) {
      rawQuery = clean.split("q=")[1].split("&")[0];
    } else {
      rawQuery = clean.split("/search/")[1]?.split("?")[0]?.split("&")[0] || null;
    }
  } else if (lower.includes("music.youtube.com/search")) {
    rawQuery = clean.split("q=")[1]?.split("&")[0] || null;
  } else if (lower.includes("youtube.com/results")) {
    rawQuery = clean.split("search_query=")[1]?.split("&")[0] || null;
  } else if (lower.includes("deezer.com") && lower.includes("/search")) {
    const after = clean.split("/search/")[1] || clean.split("/search?")[1] || "";
    if (after.startsWith("q=")) {
      rawQuery = after.substring(2).split("&")[0];
    } else {
      rawQuery = after.split("?")[0].split("&")[0];
    }
  } else if (lower.includes("tidal.com") && lower.includes("/search")) {
    if (clean.includes("q=")) {
      rawQuery = clean.split("q=")[1].split("&")[0];
    } else {
      rawQuery = clean.split("/search/")[1]?.split("?")[0]?.split("&")[0] || null;
    }
  } else if (lower.includes("music.amazon.") && lower.includes("/search")) {
    if (clean.includes("k=")) {
      rawQuery = clean.split("k=")[1].split("&")[0];
    } else if (clean.includes("keywords=")) {
      rawQuery = clean.split("keywords=")[1].split("&")[0];
    } else {
      rawQuery = clean.split("/search/")[1]?.split("?")[0]?.split("&")[0] || null;
    }
  }

  if (!rawQuery) return null;

  try {
    return decodeURIComponent(rawQuery.replace(/\+/g, " "));
  } catch {
    return rawQuery.replace(/\+/g, " ").replace(/%20/g, " ");
  }
}

/**
 * Resolves search metadata & cross-platform links for query/search URLs.
 */
async function resolveSearchLive(url: string, query: string): Promise<SongMetadata | null> {
  try {
    let title = query;
    let artist = "";
    let thumbnailUrl = "";
    let deezerLink = "";
    let appleMusicLink = "";

    // 1. Try Deezer Search API for exact track/artist match & cover
    try {
      const deezerRes = await axios.get(`https://api.deezer.com/search?q=${encodeURIComponent(query)}&limit=1`, { timeout: 4000 });
      const first = deezerRes.data?.data?.[0];
      if (first) {
        title = first.title || title;
        artist = first.artist?.name || "";
        thumbnailUrl = first.cover_xl || first.album?.cover_xl || "";
        deezerLink = first.link || `https://www.deezer.com/track/${first.id}`;
      }
    } catch (_) {}

    // 2. Try iTunes Search API
    try {
      const itunesRes = await axios.get("https://itunes.apple.com/search", {
        params: { term: query, media: "music", entity: "song", limit: 1 },
        timeout: 4000,
      });
      const first = itunesRes.data?.results?.[0];
      if (first) {
        if (!artist && first.artistName) artist = first.artistName;
        if (title === query && first.trackName) title = first.trackName;
        appleMusicLink = first.trackViewUrl || "";
        if (!thumbnailUrl && first.artworkUrl100) {
          thumbnailUrl = first.artworkUrl100.replace("100x100bb.jpg", "600x600bb.jpg");
        }
      }
    } catch (_) {}

    // 3. Try YouTube Direct Watch resolution
    let youtubeMusicLink = "";
    try {
      const ytDirect = await resolveYouTubeDirectPlayLive(artist ? `${artist} ${title}` : query, false);
      if (ytDirect) {
        youtubeMusicLink = ytDirect;
      }
    } catch (_) {}

    const qEnc = encodeURIComponent(artist ? `${artist} ${title}` : query);
    const cleanLower = url.toLowerCase();

    const linksMap: PlatformLinks = {
      spotify: cleanLower.includes("spotify.com") ? url : `https://open.spotify.com/search/${qEnc}`,
      appleMusic: appleMusicLink || (cleanLower.includes("apple.com") ? url : `https://music.apple.com/search?term=${qEnc}`),
      youtubeMusic: youtubeMusicLink || (cleanLower.includes("music.youtube.com") ? url : `https://music.youtube.com/search?q=${qEnc}`),
      deezer: deezerLink || (cleanLower.includes("deezer.com") ? url : `https://www.deezer.com/search/${qEnc}`),
      tidal: cleanLower.includes("tidal.com") ? url : `https://listen.tidal.com/search?q=${qEnc}`,
      amazonMusic: cleanLower.includes("amazon.") ? url : `https://music.amazon.com/search/${qEnc}`,
    };

    const now = Date.now();
    const fourteenDaysMs = 14 * 24 * 60 * 60 * 1000;
    const expiresAt = admin.firestore.Timestamp.fromMillis(now + fourteenDaysMs);

    return {
      title,
      artist: artist || "Track",
      thumbnailUrl: thumbnailUrl || "https://songflip.link/icon.png",
      isAlbum: false,
      isFallback: true,
      links: linksMap,
      updatedAt: now,
      expiresAt,
    };
  } catch (err: any) {
    console.error("Search resolution failed:", url, err?.message);
    return null;
  }
}

/**
 * Resolves song metadata & cross-platform links via direct SongLink engine.
 */
async function resolveSongLive(url: string): Promise<SongMetadata | null> {
  if (isArtistUrl(url)) {
    return resolveArtistLive(url);
  }

  if (isSearchUrl(url)) {
    const searchQuery = extractSearchQuery(url);
    if (searchQuery) {
      return resolveSearchLive(url, searchQuery);
    }
  }

  let title = "";
  let artist = "";
  let thumbnailUrl: string | undefined;
  let isAlbum = false;
  let isFallback = false;
  const linksMap: PlatformLinks = {};

  try {
    try {
      const targetSongLink = normalizeToSongLinkDirectUrl(url);
      const res = await axios.get(targetSongLink, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
          "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        },
        timeout: 7000,
      });

      const html = res.data;
      if (typeof html === "string" && html.includes('<script id="__NEXT_DATA__" type="application/json">')) {
        const jsonString = html.split('<script id="__NEXT_DATA__" type="application/json">')[1]?.split("</script>")[0];
        if (jsonString) {
          const parsed = JSON.parse(jsonString);
          const pageData = parsed?.props?.pageProps?.pageData;
          if (pageData) {
            const pageId = pageData.pageId || "";
            const entityUniqueId = pageData.entityUniqueId || "";
            isAlbum = pageId.includes("|album|") || entityUniqueId.includes("|album|");

            const entityData = pageData.entityData || {};
            let rawTitle = entityData.title || "";
            let rawArtist = entityData.artistName || "";
            thumbnailUrl = entityData.thumbnailUrl;

            const sections = pageData.sections || [];
            if ((!rawTitle || !rawArtist) && sections.length > 0) {
              const first = sections[0];
              if (!rawTitle) rawTitle = first.title || "";
              if (!rawArtist) rawArtist = first.artistName || "";
            }

            const sanitized = sanitizeMusicMetadata(rawTitle, rawArtist);
            title = sanitized.title;
            artist = sanitized.artist;

            const linksByPlatform = pageData.linksByPlatform || {};
            Object.keys(linksByPlatform).forEach((key) => {
              const u = linksByPlatform[key]?.url;
              if (u) {
                if (key === "spotify") linksMap.spotify = u;
                else if (key === "youtubeMusic" || key === "youtube") linksMap.youtubeMusic = linksMap.youtubeMusic || u;
                else if (key === "appleMusic" || key === "itunes") linksMap.appleMusic = linksMap.appleMusic || u;
                else if (key === "deezer") linksMap.deezer = u;
                else if (key === "tidal") linksMap.tidal = u;
                else if (key === "amazonMusic" || key === "amazon") linksMap.amazonMusic = linksMap.amazonMusic || u;
                else if (key === "soundcloud") linksMap.soundcloud = u;
                else if (key === "bandcamp") linksMap.bandcamp = u;
              }
            });

            for (const section of sections) {
              const items = section.links || section.items || [];
              for (const item of items) {
                const p = item.platform;
                const u = item.url;
                if (p && u) {
                  if (p === "spotify" && !linksMap.spotify) linksMap.spotify = u;
                  else if ((p === "youtubeMusic" || p === "youtube") && !linksMap.youtubeMusic) linksMap.youtubeMusic = u;
                  else if ((p === "appleMusic" || p === "itunes") && !linksMap.appleMusic) linksMap.appleMusic = u;
                  else if (p === "deezer" && !linksMap.deezer) linksMap.deezer = u;
                  else if (p === "tidal" && !linksMap.tidal) linksMap.tidal = u;
                  else if ((p === "amazonMusic" || p === "amazon") && !linksMap.amazonMusic) linksMap.amazonMusic = u;
                  else if (p === "soundcloud" && !linksMap.soundcloud) linksMap.soundcloud = u;
                  else if (p === "bandcamp" && !linksMap.bandcamp) linksMap.bandcamp = u;
                }
              }
            }
          }
        }
      }
    } catch (songLinkErr: any) {
      console.debug("[SongLink] Scrape failed or not listed on song.link:", songLinkErr?.message);
    }

    // If artist is missing or generic, or few links, query Deezer to heal metadata
    if (!artist || Object.keys(linksMap).length < 4) {
      const cleanTitle = cleanSearchQuery(title);
      const query = (artist + " " + cleanTitle).trim();
      if (query) {
        try {
          const deezerType = isAlbum ? "album" : "track";
          const deezerRes = await axios.get(`https://api.deezer.com/search/${deezerType}?q=${encodeURIComponent(query)}`, { timeout: 4000 });
          const match = deezerRes.data?.data?.[0];
          if (match) {
            if (!artist) artist = match.artist?.name || artist;
            if (!title) title = match.title || title;
            if (!thumbnailUrl || thumbnailUrl.includes("icon.png")) {
              thumbnailUrl = match.cover_xl || match.album?.cover_xl || thumbnailUrl;
            }
            if (!linksMap.deezer) {
              linksMap.deezer = match.link || (isAlbum ? `https://www.deezer.com/album/${match.id}` : `https://www.deezer.com/track/${match.id}`);
            }
          }
        } catch (err: any) {
          console.debug("[Fallback/Deezer]", err?.message);
        }
      }
    }

    // Fallback for Apple Music via iTunes Search API if not populated
    if (!linksMap.appleMusic && title && artist) {
      try {
        const cleanTitle = cleanSearchQuery(title);
        const itunesRes = await axios.get("https://itunes.apple.com/search", {
          params: {
            term: `${artist} ${cleanTitle}`,
            media: "music",
            entity: isAlbum ? "album" : "song",
            limit: 1,
          },
          timeout: 4000,
        });
        const first = itunesRes.data?.results?.[0];
        if (first) {
          linksMap.appleMusic = first.trackViewUrl || first.collectionViewUrl;
        }
      } catch (err: any) {
        console.debug("[Fallback/iTunes]", err?.message);
      }
    }

    // If SongLink returned no links, trigger Multi-Tier Metadata & Fallback Resolvers
    if (Object.keys(linksMap).length === 0) {
      isFallback = true;
      const cleanLower = url.toLowerCase();

      // Attempt 1: Apple Music ID Lookup
      if (cleanLower.includes("apple.com") && cleanLower.includes("i=")) {
        const idMatch = url.match(/[?&]i=(\d+)/);
        if (idMatch && idMatch[1]) {
          try {
            const itunesLookup = await axios.get("https://itunes.apple.com/lookup", {
              params: { id: idMatch[1], entity: "song" },
              timeout: 4000,
            });
            const item = itunesLookup.data?.results?.[0];
            if (item) {
              title = item.trackName || title;
              artist = item.artistName || artist;
              thumbnailUrl = (item.artworkUrl100 || "").replace("100x100bb.jpg", "600x600bb.jpg");
              linksMap.appleMusic = item.trackViewUrl || item.collectionViewUrl || url;
            }
          } catch (err: any) {
            console.debug("[Fallback/AppleLookup]", err?.message);
          }
        }
      }

      // Attempt 2: Deezer Album / Track Lookup
      if (cleanLower.includes("deezer.com")) {
        const idMatch = url.match(/\/(album|track)\/(\d+)/);
        if (idMatch && idMatch[2]) {
          try {
            const deezerType = idMatch[1];
            const deezerRes = await axios.get(`https://api.deezer.com/${deezerType}/${idMatch[2]}`, { timeout: 4000 });
            if (deezerRes.data && !deezerRes.data.error) {
              title = deezerRes.data.title || title;
              artist = deezerRes.data.artist?.name || artist;
              thumbnailUrl = deezerRes.data.cover_xl || deezerRes.data.album?.cover_xl || thumbnailUrl;
              linksMap.deezer = deezerRes.data.link || url;
            }
          } catch (err: any) {
            console.debug("[Fallback/DeezerLookup]", err?.message);
          }
        }
      }

      // Attempt 3: SoundCloud oEmbed
      if (cleanLower.includes("soundcloud.com")) {
        try {
          const scRes = await axios.get(`https://soundcloud.com/oembed?url=${encodeURIComponent(url)}&format=json`, { timeout: 4000 });
          if (scRes.data && scRes.data.title) {
            title = scRes.data.title || title;
            artist = scRes.data.author_name || artist;
            thumbnailUrl = scRes.data.thumbnail_url || thumbnailUrl;
            linksMap.soundcloud = url;
          }
        } catch (err: any) {
          console.debug("[Fallback/SoundCloudOembed]", err?.message);
        }
      }

      // Attempt 4: Bandcamp OpenGraph
      if (cleanLower.includes("bandcamp.com")) {
        try {
          const bcRes = await axios.get(url, {
            headers: {
              "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            },
            timeout: 4000,
          });
          const html = bcRes.data;
          if (typeof html === "string") {
            const ogTitleMatch = html.match(/<meta\s+property=["']og:title["']\s+content=["'](.*?)["']/i);
            const ogSiteNameMatch = html.match(/<meta\s+property=["']og:site_name["']\s+content=["'](.*?)["']/i);
            const ogImageMatch = html.match(/<meta\s+property=["']og:image["']\s+content=["'](.*?)["']/i);
            if (ogTitleMatch && ogTitleMatch[1]) {
              const fullTitle = ogTitleMatch[1];
              if (fullTitle.includes(", by ")) {
                const parts = fullTitle.split(", by ");
                title = parts[0].trim();
                artist = parts[1].trim();
              } else {
                title = fullTitle.trim();
              }
            }
            if (ogSiteNameMatch && ogSiteNameMatch[1] && (!artist || artist === "Music")) {
              artist = ogSiteNameMatch[1].trim();
            }
            if (ogImageMatch && ogImageMatch[1]) {
              thumbnailUrl = ogImageMatch[1];
            }
            if (url.includes("/album/")) {
              isAlbum = true;
            }
            linksMap.bandcamp = url;
          }
        } catch (err: any) {
          console.debug("[Fallback/BandcampOG]", err?.message);
        }
      }
    }

    // If YouTube Music direct watch link is missing or is search-only, resolve exact video ID
    if ((!linksMap.youtubeMusic || linksMap.youtubeMusic.includes("/search")) && title && artist) {
      try {
        const cleanTitle = cleanSearchQuery(title);
        const directYt = await resolveYouTubeDirectPlayLive(`${artist} ${cleanTitle}`.trim(), isAlbum);
        if (directYt) {
          linksMap.youtubeMusic = directYt;
          isFallback = true;
        }
      } catch (err: any) {
        console.debug("[Fallback/YouTubeDirect]", err?.message);
      }
    }

    // Always populate all 6 streaming platforms with direct or fallback search links
    if (title && artist) {
      const cleanLower = url.toLowerCase();
      const cleanTitle = cleanSearchQuery(title);
      const searchEncoded = encodeURIComponent(artist + " " + cleanTitle);
      if (!linksMap.spotify) {
        linksMap.spotify = cleanLower.includes("spotify.com") ? url : `https://open.spotify.com/search/${searchEncoded}`;
      }
      if (!linksMap.appleMusic) {
        linksMap.appleMusic = cleanLower.includes("apple.com") ? url : `https://music.apple.com/search?term=${searchEncoded}`;
      }
      if (!linksMap.youtubeMusic) {
        linksMap.youtubeMusic = cleanLower.includes("music.youtube.com") ? url : `https://music.youtube.com/search?q=${searchEncoded}`;
      }
      if (!linksMap.deezer) {
        linksMap.deezer = cleanLower.includes("deezer.com") ? url : `https://www.deezer.com/search/${searchEncoded}`;
      }
      if (!linksMap.tidal) {
        linksMap.tidal = cleanLower.includes("tidal.com") ? url : `https://listen.tidal.com/search/${searchEncoded}`;
      }
      if (!linksMap.amazonMusic) {
        linksMap.amazonMusic = cleanLower.includes("amazon.") ? url : `https://music.amazon.com/search/${searchEncoded}`;
      }
    }

    if (Object.keys(linksMap).length === 0) return null;

    const now = Date.now();
    const ttlMs = isFallback ? 14 * 24 * 60 * 60 * 1000 : 90 * 24 * 60 * 60 * 1000;
    const expiresAt = admin.firestore.Timestamp.fromMillis(now + ttlMs);

    return {
      title: title || "Unknown Title",
      artist: artist || "Unknown Artist",
      thumbnailUrl,
      isAlbum,
      isFallback,
      links: linksMap,
      updatedAt: now,
      expiresAt,
    };
  } catch (error: any) {
    console.error("Live resolution failed for:", url, error?.message);
    return null;
  }
}

/**
 * High-Speed L2 Cache Endpoint: /resolve?url=<encoded_music_url>
 */
export const resolve = onRequest(
  {
    region: "europe-west3",
    memory: "256MiB",
    maxInstances: 20,
    timeoutSeconds: 15,
    cors: true,
    invoker: "public",
  },
  async (req, res) => {
    applyApiSecurityHeaders(res);

    // 1. Validate HTTP Method & Rate Limits
    if (req.method !== "GET") {
      res.status(405).json({ error: "METHOD_NOT_ALLOWED" });
      return;
    }

    const clientIp = (req.headers["x-forwarded-for"] as string)?.split(",")[0]?.trim() || req.ip || "unknown";
    if (isRateLimited(`resolve:${clientIp}`, 60, 60000)) {
      res.setHeader("Retry-After", "60");
      res.status(429).json({ error: "TOO_MANY_REQUESTS", message: "Rate limit exceeded. Please wait a moment." });
      return;
    }

    // 2. Authenticate User (PRO User via RevenueCat OR official SongFlip Web Showcase)
    const origin = (req.headers.origin as string) || "";
    const referer = (req.headers.referer as string) || "";
    const isWebShowcase = 
      origin === "https://songflip.link" || 
      origin.endsWith(".songflip.link") || 
      referer.includes("songflip.link") || 
      origin.includes("localhost") || 
      referer.includes("localhost") ||
      req.headers["x-web-client"] === "songflip";

    const authHeader = req.headers.authorization || "";
    const tokenMatch = authHeader.match(/^Bearer\s+(.+)$/i);
    const userId = tokenMatch ? tokenMatch[1].trim() : (req.headers["x-user-id"] as string)?.trim();

    if (!isWebShowcase) {
      if (!userId) {
        res.status(401).json({ error: "MISSING_AUTH_TOKEN", message: "RevenueCat user ID required" });
        return;
      }

      if (userId !== "web_showcase_2026") {
        const isPro = await verifyProStatus(userId);
        if (!isPro) {
          res.status(403).json({ error: "PRO_REQUIRED", message: "SongFlip PRO is required to use the L2 Server Cache." });
          return;
        }
      }
    }

    // 3. Validate Target URL
    const targetUrl = req.query.url as string;
    const forceRefresh = req.query.force_refresh === "true" || req.query.forceRefresh === "true";
    if (!targetUrl || typeof targetUrl !== "string") {
      res.status(400).json({ error: "INVALID_URL", message: "Parameter 'url' is required" });
      return;
    }

    const normalizedUrl = normalizeMusicUrl(targetUrl);
    const primaryHash = hashUrl(normalizedUrl);

    // 4. L2 Cache Lookup in Firestore
    const cacheRef = db.collection("l2_song_cache").doc(primaryHash);
    if (forceRefresh) {
      await cacheRef.delete().catch(() => {});
      await db.collection("l2_song_cache").doc(primaryHash.substring(0, 8)).delete().catch(() => {});
    } else {
      const docSnap = await cacheRef.get();

      if (docSnap.exists) {
        const cachedData = docSnap.data() as SongMetadata;
        const isExpired = cachedData.expiresAt && cachedData.expiresAt.toMillis() < Date.now();
        if (!isExpired) {
          // Rolling 90-day TTL ONLY for verified/official API results, NEVER for heuristic fallbacks
          if (!cachedData.isFallback) {
            const rollingExpiresAt = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000);
            cacheRef.update({ expiresAt: rollingExpiresAt, lastAccessedAt: Date.now() }).catch(() => {});
          } else {
            cacheRef.update({ lastAccessedAt: Date.now() }).catch(() => {});
          }

          res.setHeader("X-Cache", "HIT");
          res.setHeader("Cache-Control", "private, no-cache, no-store, must-revalidate");
          res.status(200).json({
            status: "success",
            cached: true,
            item: {
              ...cachedData,
              hash: primaryHash.substring(0, 8),
            },
          });
          return;
        }
      }
    }

    // 5. Cache Miss -> Live Resolution
    const resolvedItem = await resolveSongLive(normalizedUrl);
    if (!resolvedItem) {
      res.status(502).json({ error: "RESOLUTION_FAILED", message: "Could not resolve music metadata" });
      return;
    }

    // 6. Save in Firestore for primary URL hash and all other platform links
    const batch = db.batch();
    batch.set(cacheRef, resolvedItem);
    const primaryShortId = primaryHash.substring(0, 8);
    batch.set(db.collection("l2_song_cache").doc(primaryShortId), resolvedItem);

    // Also index other platform URLs for future hits
    Object.values(resolvedItem.links).forEach((platformUrl) => {
      if (typeof platformUrl === "string" && platformUrl.length > 0) {
        const altNorm = normalizeMusicUrl(platformUrl);
        const altHash = hashUrl(altNorm);
        if (altHash !== primaryHash) {
          batch.set(db.collection("l2_song_cache").doc(altHash), resolvedItem);
          batch.set(db.collection("l2_song_cache").doc(altHash.substring(0, 8)), resolvedItem);
        }
      }
    });

    // Commit batch asynchronously (non-blocking for ultra-fast response)
    batch.commit().catch((err) => console.error("Error committing L2 cache batch:", err));

    res.setHeader("X-Cache", forceRefresh ? "REFRESHED" : "MISS");
    res.setHeader("Cache-Control", "private, no-cache, no-store, must-revalidate");
    res.status(200).json({
      status: "success",
      cached: false,
      item: {
        ...resolvedItem,
        hash: primaryShortId,
      },
    });
  }
);

/**
 * Dynamic L2 Cache Invalidation Endpoint: POST/GET /invalidate
 * Query/Body: { url?: string, hash?: string }
 */
export const invalidate = onRequest(
  {
    region: "europe-west3",
    memory: "256MiB",
    maxInstances: 10,
    timeoutSeconds: 10,
    cors: true,
    invoker: "public",
  },
  async (req, res) => {
    applyApiSecurityHeaders(res);
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type, Authorization");

    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }

    if (req.method !== "POST" && req.method !== "GET") {
      res.status(405).json({ error: "METHOD_NOT_ALLOWED" });
      return;
    }

    const target = (req.body?.url || req.query?.url || req.body?.hash || req.query?.hash) as string;
    if (!target || typeof target !== "string") {
      res.status(400).json({ error: "INVALID_URL", message: "Parameter 'url' or 'hash' is required" });
      return;
    }

    if (target.length === 64 || target.length === 8) {
      await db.collection("l2_song_cache").doc(target).delete().catch(() => {});
    }

    const normalizedUrl = normalizeMusicUrl(target);
    const primaryHash = hashUrl(normalizedUrl);
    await db.collection("l2_song_cache").doc(primaryHash).delete().catch(() => {});
    await db.collection("l2_song_cache").doc(primaryHash.substring(0, 8)).delete().catch(() => {});

    res.status(200).json({
      status: "success",
      invalidated: true,
      hash: primaryHash.substring(0, 8),
    });
  }
);

/**
 * Dynamic Promo Code Redemption Endpoint: POST /redeemPromoCode
 * Body: { code: string }
 */
export const redeemPromoCode = onRequest(
  {
    region: "europe-west3",
    memory: "256MiB",
    maxInstances: 10,
    timeoutSeconds: 15,
    cors: true,
    invoker: "public",
  },
  async (req, res) => {
    applyApiSecurityHeaders(res);

    // Enable CORS manually if needed
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type, Authorization");

    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }

    if (req.method !== "POST") {
      res.status(405).json({ error: "METHOD_NOT_ALLOWED" });
      return;
    }

    const clientIp = (req.headers["x-forwarded-for"] as string)?.split(",")[0]?.trim() || req.ip || "unknown";
    if (isRateLimited(`promo:${clientIp}`, 10, 60000)) {
      res.setHeader("Retry-After", "60");
      res.status(429).json({ error: "TOO_MANY_REQUESTS", message: "Zu viele Versuche. Bitte warte eine Minute." });
      return;
    }

    const rawCode = (req.body?.code || req.query?.code) as string;
    const rawInstallId = (req.body?.installId || req.body?.install_id || req.query?.installId || req.query?.install_id) as string;

    if (!rawCode || typeof rawCode !== "string") {
      res.status(400).json({ error: "INVALID_CODE", message: "Gutscheincode erforderlich." });
      return;
    }

    const cleanCode = rawCode.trim().toUpperCase();
    const cleanInstallId = rawInstallId && typeof rawInstallId === "string" ? rawInstallId.trim() : null;
    const promoRef = db.collection("promo_codes").doc(cleanCode);

    try {
      const result = await db.runTransaction(async (transaction) => {
        const docSnap = await transaction.get(promoRef);
        if (!docSnap.exists) {
          return { error: "INVALID_CODE", message: "Dieser Gutscheincode ist ungültig." };
        }

        const data = docSnap.data() || {};
        const isActive = data.isActive !== false;
        if (!isActive) {
          return { error: "CODE_INACTIVE", message: "Dieser Gutscheincode ist nicht mehr aktiv." };
        }

        const validUntil = data.validUntil ? (data.validUntil as admin.firestore.Timestamp).toMillis() : null;
        if (validUntil && validUntil < Date.now()) {
          return { error: "CODE_EXPIRED", message: "Dieser Gutscheincode ist abgelaufen." };
        }

        const maxRedemptions = typeof data.maxRedemptions === "number" ? data.maxRedemptions : null;
        const currentRedemptions = typeof data.currentRedemptions === "number" ? data.currentRedemptions : 0;

        if (maxRedemptions !== null && currentRedemptions >= maxRedemptions) {
          return { error: "MAX_REDEMPTIONS_REACHED", message: "Das Einlöselimit für diesen Code wurde erreicht." };
        }

        // Per-device single redemption check
        let redemptionRef: admin.firestore.DocumentReference | null = null;
        if (cleanInstallId) {
          redemptionRef = promoRef.collection("redemptions").doc(cleanInstallId);
          const redemptionSnap = await transaction.get(redemptionRef);
          if (redemptionSnap.exists) {
            return {
              error: "ALREADY_REDEEMED_ON_DEVICE",
              message: "Dieser Gutscheincode wurde auf diesem Gerät bereits eingelöst."
            };
          }
        }

        // Record redemption per device
        if (redemptionRef && cleanInstallId) {
          transaction.set(redemptionRef, {
            installId: cleanInstallId,
            redeemedAt: admin.firestore.FieldValue.serverTimestamp(),
            ip: clientIp,
          });
        }

        // Atomically increment global redemptions
        transaction.update(promoRef, {
          currentRedemptions: currentRedemptions + 1,
          lastRedeemedAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        const type = (data.type || "1month").toLowerCase(); // "1month", "3months", "1year", "lifetime"
        const durationDays = type === "lifetime" ? null : (data.durationDays || (type === "1year" ? 365 : type === "3months" ? 90 : 30));

        return {
          status: "success",
          type: type,
          durationDays: durationDays,
          expirationTimestamp: durationDays ? Date.now() + durationDays * 24 * 60 * 60 * 1000 : null,
        };
      });

      if (result.error) {
        res.status(400).json(result);
        return;
      }

      res.status(200).json(result);
    } catch (err: any) {
      console.error("Error redeeming promo code:", err);
      res.status(500).json({ error: "INTERNAL_ERROR", message: "Fehler beim Einlösen des Codes." });
    }
  }
);

/**
 * Health check & auto-seeder endpoint
 */
export const health = onRequest(
  { region: "europe-west3", memory: "128MiB", cors: true, invoker: "public" },
  async (_req, res) => {
    applyApiSecurityHeaders(res);

    // Ensure initial promo codes exist in Firestore
    const initialCodes = [
      { code: "SONGFLIP_BETA_2026", type: "1month", durationDays: 30, maxRedemptions: 100 },
      { code: "SONGFLIP_LAUNCH_2026", type: "3months", durationDays: 90, maxRedemptions: 50 },
      { code: "SONGFLIP_VIP_2026", type: "1year", durationDays: 365, maxRedemptions: 25 },
      { code: "SONGFLIP_FOUNDER_2026", type: "lifetime", durationDays: null, maxRedemptions: 10 },
    ];

    for (const item of initialCodes) {
      const docRef = db.collection("promo_codes").doc(item.code);
      const snap = await docRef.get();
      if (!snap.exists) {
        await docRef.set({
          code: item.code,
          type: item.type,
          durationDays: item.durationDays,
          maxRedemptions: item.maxRedemptions,
          currentRedemptions: 0,
          isActive: true,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        });
      }
    }

    res.status(200).json({ status: "ok", service: "SongFlip L2 Cache Engine", timestamp: Date.now() });
  }
);

/**
 * Escapes HTML characters to prevent XSS.
 */
function escapeHtml(str: string): string {
  if (!str) return "";
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

const DEMO_PRESETS: Record<string, any> = {
  "f1f28ddc": {
    title: "Never Gonna Give You Up",
    artist: "Rick Astley",
    thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
    isAlbum: false,
    links: {
      spotify: "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
      appleMusic: "https://music.apple.com/us/album/never-gonna-give-you-up/1559885420?i=1559885421",
      youtubeMusic: "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
      deezer: "https://www.deezer.com/track/14408104",
      tidal: "https://listen.tidal.com/track/105707768",
      amazonMusic: "https://music.amazon.com/albums/B07PGF8R4G?trackAsin=B07P8N5Z6H"
    }
  },
  "838fa830": {
    title: "Blinding Lights",
    artist: "The Weeknd",
    thumbnailUrl: "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36",
    isAlbum: false,
    links: {
      spotify: "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b",
      appleMusic: "https://music.apple.com/us/album/blinding-lights/1499378108?i=1499378607",
      youtubeMusic: "https://music.youtube.com/watch?v=4NRXx6U8ABQ",
      deezer: "https://www.deezer.com/track/908604612",
      tidal: "https://listen.tidal.com/track/134858527",
      amazonMusic: "https://music.amazon.com/albums/B0855DV6QG?trackAsin=B0855DTRX6"
    }
  },
  "9c3a1b8e": {
    title: "Get Lucky (feat. Pharrell Williams & Nile Rodgers)",
    artist: "Daft Punk",
    thumbnailUrl: "https://cdn-images.dzcdn.net/images/cover/bc49adb87758e0c8c4e508a9c5cce85d/1000x1000-000000-80-0-0.jpg",
    isAlbum: false,
    links: {
      spotify: "https://open.spotify.com/track/2Foc5Q5nqNiosCNqttzHof",
      appleMusic: "https://music.apple.com/us/album/get-lucky-feat-pharrell-williams-nile-rodgers/617154241?i=617154366",
      youtubeMusic: "https://music.youtube.com/watch?v=5NV6Rdv1a3I",
      deezer: "https://www.deezer.com/track/67238735",
      tidal: "https://listen.tidal.com/track/20115564",
      amazonMusic: "https://music.amazon.com/albums/B00C0641ES?trackAsin=B00C06497O"
    }
  },
  "86e847b3": {
    title: "Get Lucky (feat. Pharrell Williams & Nile Rodgers)",
    artist: "Daft Punk",
    thumbnailUrl: "https://cdn-images.dzcdn.net/images/cover/bc49adb87758e0c8c4e508a9c5cce85d/1000x1000-000000-80-0-0.jpg",
    isAlbum: false,
    links: {
      spotify: "https://open.spotify.com/track/2Foc5Q5nqNiosCNqttzHof",
      appleMusic: "https://music.apple.com/us/album/get-lucky-feat-pharrell-williams-nile-rodgers/617154241?i=617154366",
      youtubeMusic: "https://music.youtube.com/watch?v=5NV6Rdv1a3I",
      deezer: "https://www.deezer.com/track/67238735",
      tidal: "https://listen.tidal.com/track/20115564",
      amazonMusic: "https://music.amazon.com/albums/B00C0641ES?trackAsin=B00C06497O"
    }
  },
  "7e4d2a1f": {
    title: "BIRDS OF A FEATHER",
    artist: "Billie Eilish",
    thumbnailUrl: "https://i.scdn.co/image/ab67616d0000b27371d62ea7ea8a5be92d3c1f62",
    isAlbum: false,
    links: {
      spotify: "https://open.spotify.com/track/6dOtVTDdiauQNBQEDOtlAB",
      appleMusic: "https://music.apple.com/us/album/birds-of-a-feather/1739659134?i=1739659142",
      youtubeMusic: "https://music.youtube.com/watch?v=V9PVRfjEBTI",
      deezer: "https://www.deezer.com/track/2801558052",
      tidal: "https://listen.tidal.com/track/363236466",
      amazonMusic: "https://music.amazon.com/albums/B0D18PFR2V?trackAsin=B0D18P2VFF"
    }
  },
  "rickroll": {
    title: "Never Gonna Give You Up",
    artist: "Rick Astley",
    thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
    isAlbum: false,
    links: {
      spotify: "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
      appleMusic: "https://music.apple.com/us/album/never-gonna-give-you-up/1559885420?i=1559885421",
      youtubeMusic: "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
      deezer: "https://www.deezer.com/track/14408104",
      tidal: "https://listen.tidal.com/track/105707768",
      amazonMusic: "https://music.amazon.com/albums/B07PGF8R4G?trackAsin=B07P8N5Z6H"
    }
  },
  "2619c5f7": {
    title: "Never Gonna Give You Up",
    artist: "Rick Astley",
    thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
    isAlbum: false,
    links: {
      spotify: "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
      appleMusic: "https://music.apple.com/us/album/never-gonna-give-you-up/1559885420?i=1559885421",
      youtubeMusic: "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
      deezer: "https://www.deezer.com/track/14408104",
      tidal: "https://listen.tidal.com/track/105707768",
      amazonMusic: "https://music.amazon.com/albums/B07PGF8R4G?trackAsin=B07P8N5Z6H"
    }
  }
};

interface WebShareI18n {
  lang: string;
  shareLink: string;
  copyLink: string;
  linkCopied: string;
  copyFailed: string;
  play: string;
  album: string;
  artist: string;
  flippedWith: string;
  tagline: string;
  listenOn: string;
  notFoundTitle: string;
  notFoundDesc: string;
  discoverSongFlip: string;
}

function getWebShareI18n(header?: string): WebShareI18n {
  const code = (header || "").split(",")[0]?.split(";")[0]?.trim().toLowerCase().split("-")[0] || "en";
  const dict: Record<string, Partial<WebShareI18n>> = {
    de: { lang: "de", shareLink: "Link teilen", copyLink: "Link kopieren", linkCopied: "Link in Zwischenablage kopiert!", copyFailed: "Kopieren fehlgeschlagen", play: "Abspielen", album: "Album", artist: "Künstler", flippedWith: "Geflippt mit", tagline: "Der automatische 0-Klick Musik-Redirector", listenOn: "Auf SongFlip anhören", notFoundTitle: "Song-Link nicht gefunden", notFoundDesc: "Dieser Musik-Share-Link ist nicht mehr verfügbar oder wurde fehlerhaft aufgerufen.", discoverSongFlip: "SongFlip entdecken" },
    es: { lang: "es", shareLink: "Compartir enlace", copyLink: "Copiar enlace", linkCopied: "¡Enlace copiado al portapapeles!", copyFailed: "Error al copiar", play: "Reproducir", album: "Álbum", artist: "Artista", flippedWith: "Flipped con", tagline: "El redireccionador automático de música sin clics", listenOn: "Escuchar en SongFlip", notFoundTitle: "Enlace no encontrado", notFoundDesc: "Este enlace de música ya no está disponible o se introdujo incorrectamente.", discoverSongFlip: "Descubrir SongFlip" },
    fr: { lang: "fr", shareLink: "Partager le lien", copyLink: "Copier le lien", linkCopied: "Lien copié dans le presse-papiers !", copyFailed: "Échec de la copie", play: "Écouter", album: "Album", artist: "Artiste", flippedWith: "Flipped avec", tagline: "Le redirecteur de musique automatique 0-clic", listenOn: "Écouter sur SongFlip", notFoundTitle: "Lien de morceau introuvable", notFoundDesc: "Ce lien de partage de musique n'est plus disponible ou a été mal saisi.", discoverSongFlip: "Découvrir SongFlip" },
    it: { lang: "it", shareLink: "Condividi link", copyLink: "Copia link", linkCopied: "Link copiato negli appunti!", copyFailed: "Copia non riuscita", play: "Riproduci", album: "Album", artist: "Artista", flippedWith: "Flipped con", tagline: "Il redirector musicale automatico zero-clic", listenOn: "Ascolta su SongFlip", notFoundTitle: "Link non trovato", notFoundDesc: "Questo link musicale non è più disponibile o non è stato inserito correttamente.", discoverSongFlip: "Scopri SongFlip" },
    pt: { lang: "pt", shareLink: "Compartilhar link", copyLink: "Copiar link", linkCopied: "Link copiado para a área de transferência!", copyFailed: "Falha ao copiar", play: "Tocar", album: "Álbum", artist: "Artista", flippedWith: "Flipped com", tagline: "O redirecionador de música automático de 0 cliques", listenOn: "Ouvir no SongFlip", notFoundTitle: "Link não encontrado", notFoundDesc: "Este link de música não está mais disponível ou foi digitado incorretamente.", discoverSongFlip: "Descubra o SongFlip" },
    nl: { lang: "nl", shareLink: "Link delen", copyLink: "Link kopiëren", linkCopied: "Link gekopieerd naar klembord!", copyFailed: "Kopiëren mislukt", play: "Afspelen", album: "Album", artist: "Artiest", flippedWith: "Geflipt met", tagline: "De automatische 0-klik muziek-redirector", listenOn: "Luister op SongFlip", notFoundTitle: "Muzieklink niet gevonden", notFoundDesc: "Deze muzieklink is niet meer beschikbaar of onjuist ingevoerd.", discoverSongFlip: "Ontdek SongFlip" },
    pl: { lang: "pl", shareLink: "Udostępnij link", copyLink: "Kopiuj link", linkCopied: "Skopiowano link do schowka!", copyFailed: "Kopiowanie nie powiodło się", play: "Odtwórz", album: "Album", artist: "Wykonawca", flippedWith: "Flipped z", tagline: "Automatyczny przekierowywacz muzyki 0-kliknięć", listenOn: "Słuchaj w SongFlip", notFoundTitle: "Nie znaleziono utworu", notFoundDesc: "Ten link muzyczny jest już niedostępny lub został nieprawidłowo wprowadzony.", discoverSongFlip: "Odkryj SongFlip" },
    da: { lang: "da", shareLink: "Del link", copyLink: "Kopier link", linkCopied: "Link kopieret til udklipsholder!", copyFailed: "Kopiering mislykkedes", play: "Afspil", album: "Album", artist: "Kunstner", flippedWith: "Flippet med", tagline: "Den automatiske 0-klik musik-omdirigering", listenOn: "Lyt på SongFlip", notFoundTitle: "Musiklink ikke fundet", notFoundDesc: "Dette musiklink er ikke længere tilgængeligt eller blev indtastet forkert.", discoverSongFlip: "Opdag SongFlip" },
    nb: { lang: "nb", shareLink: "Del lenke", copyLink: "Kopier lenke", linkCopied: "Lenke kopiert til utklippstavlen!", copyFailed: "Kopiering mislyktes", play: "Spill av", album: "Album", artist: "Artist", flippedWith: "Flippet med", tagline: "Den automatiske 0-klikk musikk-omdirigeringen", listenOn: "Lytt på SongFlip", notFoundTitle: "Musikklenke ikke funnet", notFoundDesc: "Denne musikklenken er ikke lenger tilgjengelig eller ble skrevet inn feil.", discoverSongFlip: "Oppdag SongFlip" },
    no: { lang: "no", shareLink: "Del lenke", copyLink: "Kopier lenke", linkCopied: "Lenke kopiert til utklippstavlen!", copyFailed: "Kopiering mislyktes", play: "Spill av", album: "Album", artist: "Artist", flippedWith: "Flippet med", tagline: "Den automatiske 0-klikk musikk-omdirigeringen", listenOn: "Lytt på SongFlip", notFoundTitle: "Musikklenke ikke funnet", notFoundDesc: "Denne musikklenken er ikke lenger tilgjengelig eller ble skrevet inn feil.", discoverSongFlip: "Oppdag SongFlip" },
    sv: { lang: "sv", shareLink: "Dela länk", copyLink: "Kopiera länk", linkCopied: "Länk kopierad till urklipp!", copyFailed: "Kopiering misslyckades", play: "Spela", album: "Album", artist: "Artist", flippedWith: "Flippad med", tagline: "Den automatiska 0-klick musik-omdirigeringen", listenOn: "Lyssna på SongFlip", notFoundTitle: "Musik-länk hittades inte", notFoundDesc: "Den här musiklänken är inte längre tillgänglig eller angavs felaktigt.", discoverSongFlip: "Upptäck SongFlip" },
    ru: { lang: "ru", shareLink: "Поделиться ссылкой", copyLink: "Скопировать ссылку", linkCopied: "Ссылка скопирована в буфер обмена!", copyFailed: "Не удалось скопировать", play: "Слушать", album: "Альбом", artist: "Исполнитель", flippedWith: "Flipped с", tagline: "Автоматический перенаправитель музыки в 0 кликов", listenOn: "Слушать на SongFlip", notFoundTitle: "Ссылка не найдена", notFoundDesc: "Эта музыкальная ссылка больше недоступна или введена неверно.", discoverSongFlip: "Узнать о SongFlip" },
    uk: { lang: "uk", shareLink: "Поділитися посиланням", copyLink: "Скопіювати посилання", linkCopied: "Посилання скопійовано в буфер обміну!", copyFailed: "Помилка копіювання", play: "Слухати", album: "Альбом", artist: "Виконавець", flippedWith: "Flipped з", tagline: "Автоматичний перенаправлювач музики в 0 кліків", listenOn: "Слухати на SongFlip", notFoundTitle: "Посилання не знайдено", notFoundDesc: "Це музичне посилання більше недоступне або введено неправильно.", discoverSongFlip: "Дізнатися про SongFlip" },
    tr: { lang: "tr", shareLink: "Bağlantıyı Paylaş", copyLink: "Bağlantıyı Kopyala", linkCopied: "Bağlantı panoya kopyalandı!", copyFailed: "Kopyalama başarısız", play: "Çal", album: "Albüm", artist: "Sanatçı", flippedWith: "SongFlip ile", tagline: "Otomatik 0 tık müzik yönlendirici", listenOn: "SongFlip'te Dinle", notFoundTitle: "Bağlantı Bulunamadı", notFoundDesc: "Bu müzik paylaşım bağlantısı artık mevcut değil veya yanlış girildi.", discoverSongFlip: "SongFlip'i Keşfet" },
    ja: { lang: "ja", shareLink: "リンクを共有", copyLink: "リンクをコピー", linkCopied: "クリップボードにコピーしました！", copyFailed: "コピーに失敗しました", play: "再生", album: "アルバム", artist: "アーティスト", flippedWith: "SongFlipで変換", tagline: "完全自動0クリック音楽リダイレクター", listenOn: "SongFlipで聴く", notFoundTitle: "曲が見つかりません", notFoundDesc: "このリンクは利用できないか、正しく入力されていません。", discoverSongFlip: "SongFlipを見る" },
    ko: { lang: "ko", shareLink: "링크 공유", copyLink: "링크 복사", linkCopied: "클립보드에 복사되었습니다!", copyFailed: "복사 실패", play: "재생", album: "앨범", artist: "아티스트", flippedWith: "SongFlip으로 전환", tagline: "0클릭 자동 음악 리디렉터", listenOn: "SongFlip에서 듣기", notFoundTitle: "음악 링크를 찾을 수 없음", notFoundDesc: "이 음악 공유 링크를 더 이상 사용할 수 없거나 잘못 입력되었습니다.", discoverSongFlip: "SongFlip 알아보기" },
    zh: { lang: "zh", shareLink: "分享链接", copyLink: "复制链接", linkCopied: "链接已复制到剪贴板！", copyFailed: "复制失败", play: "播放", album: "专辑", artist: "艺人", flippedWith: "使用 SongFlip 转换", tagline: "零点击自动音乐重定向器", listenOn: "在 SongFlip 上收听", notFoundTitle: "未找到歌曲链接", notFoundDesc: "该音乐分享链接已失效或输入有误。", discoverSongFlip: "探索 SongFlip" },
    id: { lang: "id", shareLink: "Bagikan Tautan", copyLink: "Salin Tautan", linkCopied: "Tautan disalin ke papan klip!", copyFailed: "Gagal menyalin", play: "Putar", album: "Album", artist: "Artis", flippedWith: "Flipped dengan", tagline: "Pengalih musik otomatis 0-klik", listenOn: "Dengarkan di SongFlip", notFoundTitle: "Tautan Lagu Tidak Ditemukan", notFoundDesc: "Tautan berbagi musik ini tidak lagi tersedia atau salah dimasukkan.", discoverSongFlip: "Jelajahi SongFlip" },
    in: { lang: "id", shareLink: "Bagikan Tautan", copyLink: "Salin Tautan", linkCopied: "Tautan disalin ke papan klip!", copyFailed: "Gagal menyalin", play: "Putar", album: "Album", artist: "Artis", flippedWith: "Flipped dengan", tagline: "Pengalih musik otomatis 0-klik", listenOn: "Dengarkan di SongFlip", notFoundTitle: "Tautan Lagu Tidak Ditemukan", notFoundDesc: "Tautan berbagi musik ini tidak lagi tersedia atau salah dimasukkan.", discoverSongFlip: "Jelajahi SongFlip" },
    vi: { lang: "vi", shareLink: "Chia sẻ liên kết", copyLink: "Sao chép liên kết", linkCopied: "Đã sao chép liên kết vào khay nhớ tạm!", copyFailed: "Sao chép thất bại", play: "Phát", album: "Album", artist: "Nghệ sĩ", flippedWith: "Flipped với", tagline: "Trình chuyển hướng âm nhạc 0 cú nhấp tự động", listenOn: "Nghe trên SongFlip", notFoundTitle: "Không tìm thấy liên kết", notFoundDesc: "Liên kết chia sẻ nhạc này không còn khả dụng hoặc được nhập không chính xác.", discoverSongFlip: "Khám phá SongFlip" },
    hi: { lang: "hi", shareLink: "लिंक साझा करें", copyLink: "लिंक कॉपी करें", linkCopied: "लिंक क्लिपबोर्ड पर कॉपी हो गया!", copyFailed: "कॉपी विफल", play: "बजाएं", album: "एल्बम", artist: "कलाकार", flippedWith: "SongFlip द्वारा", tagline: "स्वचालित 0-क्लिक संगीत रीडायरेक्टर", listenOn: "SongFlip पर सुनें", notFoundTitle: "संगीत लिंक नहीं मिला", notFoundDesc: "यह संगीत शेयर लिंक अब उपलब्ध नहीं है या गलत दर्ज किया गया था।", discoverSongFlip: "SongFlip जानें" },
    bn: { lang: "bn", shareLink: "লিঙ্ক শেয়ার করুন", copyLink: "লিঙ্ক অনুলিপি করুন", linkCopied: "ক্লিপবোর্ডে লিঙ্ক অনুলিপি করা হয়েছে!", copyFailed: "অনুলিপি ব্যর্থ", play: "চালান", album: "অ্যালবাম", artist: "শিল্পী", flippedWith: "SongFlip দিয়ে", tagline: "স্বয়ংক্রিয় ০-ক্লিক সঙ্গীত पुनর্নির্দেশক", listenOn: "SongFlip-এ শুনুন", notFoundTitle: "গানের লিঙ্ক পাওয়া যায়নি", notFoundDesc: "এই সঙ্গীত লিঙ্কটি আর উপলব্ধ নেই বা ভুল প্রবেশ করা হয়েছে।", discoverSongFlip: "SongFlip আবিষ্কার করুন" },
    mr: { lang: "mr", shareLink: "दुवा शेअर करा", copyLink: "दुवा कॉपी करा", linkCopied: "क्लिपबोर्डवर दुवा कॉपी केला!", copyFailed: "कॉपी अयशस्वी", play: "प्ले करा", album: "अल्बम", artist: "कलाकार", flippedWith: "SongFlip द्वारे", tagline: "स्वयंचलित ०-क्लिक संगीत पुनर्निर्देशन", listenOn: "SongFlip वर ऐका", notFoundTitle: "गाण्याचा दुवा सापडला नाही", notFoundDesc: "हा संगीत शेअर दुवा आता उपलब्ध नाही किंवा चुकीचा प्रविष्ट केला गेला आहे।", discoverSongFlip: "SongFlip शोधा" }
  };

  const defaults: WebShareI18n = {
    lang: "en",
    shareLink: "Share Link",
    copyLink: "Copy Link",
    linkCopied: "Link copied to clipboard!",
    copyFailed: "Copy failed",
    play: "Play",
    album: "Album",
    artist: "Artist",
    flippedWith: "Flipped with",
    tagline: "The automatic 0-click music redirector",
    listenOn: "Listen on SongFlip",
    notFoundTitle: "Song Link Not Found",
    notFoundDesc: "This music share link is no longer available or was entered incorrectly. Open music links seamlessly with SongFlip.",
    discoverSongFlip: "Discover SongFlip"
  };

  return { ...defaults, ...(dict[code] || {}) };
}

/**
 * Web-Share Landing Page: /s/:hash or /s?id=:hash
 * Delivers instant Universal Music Link page with Open Graph preview cards for WhatsApp, Telegram, iMessage & Discord.
 */
export const renderWebShare = onRequest(
  {
    region: "europe-west3",
    memory: "256MiB",
    timeoutSeconds: 15,
    cors: true,
    invoker: "public",
  },
  async (req, res) => {
    try {
      applyWebShareSecurityHeaders(res);
      const userAgent = (req.headers["user-agent"] as string) || "";
      const isMobileUA = /Android|iPhone|iPad|iPod/i.test(userAgent);
      const i18n = getWebShareI18n(req.headers["accept-language"]);

      // 1. Extract hash from path or query parameter
      const rawPath = req.path || "";
      const pathParts = rawPath.split("/").filter(Boolean);
      let hash = "";
      if (pathParts.length > 0) {
        hash = pathParts[pathParts.length - 1];
      }
      if (!hash || hash === "s") {
        hash = (req.query.id as string) || (req.query.h as string) || "";
      }
      hash = hash.trim();

      let songData: any = null;

      // 1.5 Built-in Demo Presets for Website Showcase
      if (hash && DEMO_PRESETS[hash.toLowerCase()]) {
        songData = DEMO_PRESETS[hash.toLowerCase()];
      }

      // 2. Fetch from Firestore Cache if not a built-in demo preset
      if (!songData && hash) {
        const docSnap = await db.collection("l2_song_cache").doc(hash).get();
        if (docSnap.exists) {
          songData = docSnap.data();
          // Rolling 90-day TTL ONLY for verified/official API results, NEVER for heuristic fallbacks
          if (!songData.isFallback) {
            const rollingExpiresAt = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000);
            docSnap.ref.update({ expiresAt: rollingExpiresAt, lastAccessedAt: Date.now() }).catch(() => {});
          } else {
            docSnap.ref.update({ lastAccessedAt: Date.now() }).catch(() => {});
          }
        } else if (hash.length > 8) {
          const shortSnap = await db.collection("l2_song_cache").doc(hash.substring(0, 8)).get();
          if (shortSnap.exists) {
            songData = shortSnap.data();
            // Rolling 90-day TTL ONLY for verified/official API results, NEVER for heuristic fallbacks
            if (!songData.isFallback) {
              const rollingExpiresAt = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000);
              shortSnap.ref.update({ expiresAt: rollingExpiresAt, lastAccessedAt: Date.now() }).catch(() => {});
            } else {
              shortSnap.ref.update({ lastAccessedAt: Date.now() }).catch(() => {});
            }
          }
        }
      }

      // 3. Fallback: If not found in cache and query parameter ?url= is passed, resolve live
      if (!songData && req.query.url && typeof req.query.url === "string") {
        const resolved = await resolveSongLive(req.query.url as string);
        if (resolved) {
          songData = resolved;
          const newHash = hashUrl(req.query.url as string);
          await db.collection("l2_song_cache").doc(newHash).set(resolved);
          await db.collection("l2_song_cache").doc(newHash.substring(0, 8)).set(resolved);
        }
      }

      // 4. Render 404 / Not Found Page if song is missing
      if (!songData || !songData.links || Object.keys(songData.links).length === 0) {
        res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        res.setHeader("Pragma", "no-cache");
        res.setHeader("Expires", "0");
        res.setHeader("Content-Type", "text/html; charset=utf-8");

        res.status(404).send(`<!DOCTYPE html>
<html lang="${i18n.lang}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>${i18n.notFoundTitle} | SongFlip</title>
  <meta name="description" content="${i18n.notFoundDesc}">
  <meta name="theme-color" content="#0b0f17">
  
  <!-- Favicons (Official App Icon Suite) -->
  <link rel="icon" type="image/svg+xml" href="https://songflip.link/images/favicon.svg">
  <link rel="icon" type="image/png" sizes="96x96" href="https://songflip.link/images/favicon-96x96.png">
  <link rel="icon" type="image/png" sizes="192x192" href="https://songflip.link/images/favicon-192x192.png">
  <link rel="shortcut icon" href="https://songflip.link/images/favicon.ico">
  <link rel="apple-touch-icon" sizes="180x180" href="https://songflip.link/images/apple-touch-icon.png">

  <link rel="stylesheet" href="https://songflip.link/share.css">
</head>
<body>
  <div class="ambient-bg-404"></div>

  <main class="container container-404">
    <div class="icon-wrapper">
      <span class="icon-symbol">🎵</span>
    </div>

    <span class="badge-status">404 • Not Found</span>
    <h1 class="title">${i18n.notFoundTitle}</h1>
    <p class="description">
      ${i18n.notFoundDesc}
    </p>

    <a href="https://download.songflip.link" class="btn-action">
      <span>${i18n.discoverSongFlip}</span>
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>
    </a>

    <footer class="footer-app">
      <a href="https://download.songflip.link" class="brand-link">
        <img src="https://songflip.link/images/favicon-96x96.png" alt="SongFlip Logo" class="brand-icon" />
        <span class="brand-text">${i18n.flippedWith} <strong>SongFlip</strong></span>
      </a>
      <p class="footer-subtext">${i18n.tagline}</p>
    </footer>
  </main>
</body>
</html>`);
        return;
      }

      // 5. Prepare Safe Data & Strings
      const isAlbum = !!songData.isAlbum;
      const isArtist = !!songData.isArtist;
      const sanitized = sanitizeMusicMetadata(songData.title || "", songData.artist || "");
      let cleanTitle = sanitized.title || "Track";
      let cleanArtist = isArtist ? "" : sanitized.artist;

      // If not isArtist and artist is missing or generic (e.g. from older cache entry), live heal via Deezer search
      if (!isArtist && (!cleanArtist || cleanArtist === "Music" || cleanArtist === "Artist" || cleanArtist.toLowerCase().includes("youtube"))) {
        try {
          const deezerType = isAlbum ? "album" : "track";
          const dRes = await axios.get(`https://api.deezer.com/search/${deezerType}?q=${encodeURIComponent(cleanTitle)}`, { timeout: 3000 });
          const match = dRes.data?.data?.[0];
          if (match) {
            cleanArtist = match.artist?.name || cleanArtist;
            cleanTitle = match.title || cleanTitle;
            if (!songData.thumbnailUrl || songData.thumbnailUrl.includes("icon.png")) {
              songData.thumbnailUrl = match.cover_xl || match.album?.cover_xl || songData.thumbnailUrl;
            }
            if (hash) {
              db.collection("l2_song_cache").doc(hash).set({
                artist: cleanArtist,
                title: cleanTitle,
                thumbnailUrl: songData.thumbnailUrl,
              }, { merge: true }).catch(() => {});
            }
          }
        } catch (_) {}
      }

      const title = escapeHtml(cleanTitle);
      const artist = escapeHtml(isArtist ? (i18n.artist || "Artist Profile") : (cleanArtist || "Music"));
      const coverUrl = songData.thumbnailUrl ? escapeHtml(songData.thumbnailUrl) : "https://songflip.link/icon.png";
      const links = { ...(songData.links || {}) };

      // Invalidate any search links containing generic "YouTube" or "Album - " noise
      Object.keys(links).forEach((key) => {
        const u = links[key];
        if (typeof u === "string" && (u.includes("/search/") || u.includes("/search?"))) {
          if (u.includes("YouTube") || u.includes("Album%20-%20") || u.includes("Album%20%E2%80%93%20")) {
            delete links[key];
          }
        }
      });

      // If isArtist, heal Deezer, Apple Music, and YouTube Music with direct native links instead of generic search URLs
      if (isArtist && cleanTitle) {
        let healed = false;
        // 1. Heal Deezer
        if (!links.deezer || links.deezer.includes("/search/") || links.deezer.includes("/search?")) {
          try {
            const deezerRes = await axios.get(`https://api.deezer.com/search/artist?q=${encodeURIComponent(cleanTitle)}`, { timeout: 3000 });
            const first = deezerRes.data?.data?.[0];
            if (first && (first.link || first.id)) {
              links.deezer = first.link || `https://www.deezer.com/artist/${first.id}`;
              healed = true;
            }
          } catch (_) {}
        }
        // 2. Heal Apple Music
        if (!links.appleMusic || links.appleMusic.includes("/search/") || links.appleMusic.includes("/search?") || links.appleMusic.includes("search?term=")) {
          try {
            const appleDirect = await resolveAppleMusicArtistLive(cleanTitle);
            if (appleDirect) {
              links.appleMusic = appleDirect;
              healed = true;
            }
          } catch (_) {}
        }
        // 3. Heal YouTube Music
        if (!links.youtubeMusic || links.youtubeMusic.includes("/search/") || links.youtubeMusic.includes("/search?") || links.youtubeMusic.includes("search?q=")) {
          try {
            const ytDirect = await resolveYouTubeArtistChannelLive(cleanTitle);
            if (ytDirect) {
              links.youtubeMusic = ytDirect;
              healed = true;
            }
          } catch (_) {}
        }
        // Persist healed links to Firestore asynchronously
        if (healed && hash) {
          db.collection("l2_song_cache").doc(hash).set({ links }, { merge: true }).catch(() => {});
        }
      }

      const query = encodeURIComponent((cleanArtist ? `${cleanArtist} ${cleanTitle}` : cleanTitle).trim());
      if (!links.spotify) links.spotify = `https://open.spotify.com/search/${query}`;
      if (!links.appleMusic) links.appleMusic = `https://music.apple.com/search?term=${query}`;
      if (!links.youtubeMusic) links.youtubeMusic = `https://music.youtube.com/search?q=${query}`;
      if (!links.deezer) links.deezer = `https://www.deezer.com/search/${query}`;
      if (!links.tidal) links.tidal = `https://listen.tidal.com/search?q=${query}`;
      if (!links.amazonMusic) links.amazonMusic = `https://music.amazon.com/search/${query}`;

      const shortId = hash ? (hash.length > 8 ? hash.substring(0, 8) : hash) : "";
      const currentShareUrl = `https://songflip.link/s/${encodeURIComponent(shortId)}`;

      // Streaming Platform Definitions
      const platforms = [
        {
          id: "spotify",
          name: "Spotify",
          color: "#1DB954",
          bgHover: "#1aa34a",
          url: links.spotify,
          icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0C5.4 0 0 5.4 0 12s5.4 12 12 12 12-5.4 12-12S18.66 0 12 0zm5.521 17.34c-.24.359-.66.48-1.021.24-2.82-1.74-6.36-2.101-10.561-1.141-.418.122-.779-.179-.899-.539-.12-.421.18-.78.54-.9 4.56-1.021 8.52-.6 11.64 1.32.42.18.479.659.301 1.02zm1.44-3.3c-.301.42-.841.6-1.262.3-3.239-1.98-8.159-2.58-11.939-1.38-.479.12-1.02-.12-1.14-.6-.12-.48.12-1.021.6-1.141C9.6 9.9 15 10.561 18.72 12.84c.361.181.54.78.241 1.2zm.12-3.36C15.24 8.4 8.82 8.16 5.16 9.301c-.6.179-1.2-.181-1.38-.721-.18-.601.18-1.2.72-1.381 4.26-1.26 11.28-1.02 15.721 1.621.539.3.719 1.02.419 1.56-.299.421-1.02.599-1.559.3z"/></svg>`,
        },
        {
          id: "appleMusic",
          name: "Apple Music",
          color: "#FC3C44",
          bgHover: "#e0333b",
          url: links.appleMusic,
          icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M15.97 6.37c.61-.75 1.04-1.8 0.92-2.87-.93.04-2.02.63-2.66 1.38-.56.65-1.06 1.71-.93 2.74 1.05.08 2.08-.55 2.67-1.25z"/></svg>`,
        },
        {
          id: "youtubeMusic",
          name: "YouTube Music",
          color: "#FF0000",
          bgHover: "#e60000",
          url: links.youtubeMusic,
          icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0C5.376 0 0 5.376 0 12s5.376 12 12 12 12-5.376 12-12S18.624 0 12 0zm0 19.104c-3.924 0-7.104-3.18-7.104-7.104S8.076 4.896 12 4.896s7.104 3.18 7.104 7.104-3.18 7.104-7.104 7.104zm0-11.44c-2.392 0-4.336 1.944-4.336 4.336S9.608 16.336 12 16.336s4.336-1.944 4.336-4.336S14.392 7.664 12 7.664zm-1.44 6.168V10.168l3.6 1.832-3.6 1.832z"/></svg>`,
        },
        {
          id: "deezer",
          name: "Deezer",
          color: "#A238FF",
          bgHover: "#8e2fe0",
          url: links.deezer,
          icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M18.8 3.8v3.4h4.4V3.8h-4.4zm0 4.6v3.4h4.4V8.4h-4.4zm0 4.6v3.4h4.4V13h-4.4zm0 4.6V21h4.4v-3.4h-4.4zm-6.2-4.6v3.4h4.4V13h-4.4zm0 4.6V21h4.4v-3.4h-4.4zm-6.4 0V21h4.4v-3.4H6.2zm-5.4 0V21h4.4v-3.4H.8z"/></svg>`,
        },
        {
          id: "tidal",
          name: "Tidal",
          color: "#00FFFF",
          bgHover: "#00d6d6",
          url: links.tidal,
          icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M12.012 3.842l-3.996 4.004 4.004 3.996 3.996-4.004-4.004-3.996zm-8.016 8.008L0 7.846l4.004-4.004 3.996 4.004-4.004 4.004zm8.016 0L8.016 7.846l4.004-4.004 3.996 4.004-4.004 4.004zm8.008 0l-3.996-4.004 4.004-4.004 3.996 4.004-4.004 4.004zm-8.008 8.008l-3.996-4.004 4.004-3.996 3.996 4.004-4.004 3.996z"/></svg>`,
        },
        {
          id: "amazonMusic",
          name: "Amazon Music",
          color: "#25D1DA",
          bgHover: "#1fbac2",
          url: links.amazonMusic,
          icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M13.882 12.802c0 .914-.528 1.405-1.583 1.405-.88 0-1.391-.491-1.391-1.405 0-.915.511-1.406 1.391-1.406 1.055 0 1.583.491 1.583 1.406zm8.877 7.027c-.334.457-1.127.67-1.742.67-2.604 0-5.698-2.076-7.898-3.908-.317-.264-.07-.633.282-.44 2.833 1.565 6.474 2.972 9.074 1.495.335-.194.617-.035.284.42v.001l-.001.002-.001.001-.001.001-.001.001-.001.002zm-8.913-9.524c-2.482 0-4.085 1.495-4.085 3.872 0 2.395 1.567 3.89 4.085 3.89 2.5 0 4.103-1.495 4.103-3.89 0-2.377-1.603-3.872-4.103-3.872z"/></svg>`,
        },
        {
          id: "soundcloud",
          name: "SoundCloud",
          color: "#FF5500",
          bgHover: "#e64d00",
          url: links.soundcloud,
          icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M11.56 8.87V17h9.01c1.89 0 3.43-1.54 3.43-3.43 0-1.85-1.48-3.36-3.31-3.42-.32-2.73-2.62-4.86-5.44-4.86-.98 0-1.9.27-2.69.75-.38.23-.7.52-.98.86-.02-.02-.02-.03-.02-.03zm-1.5 1.74v6.39h.82v-6.6c-.29.06-.56.14-.82.21zm-1.63.81v5.58h.82v-5.87c-.29.08-.56.18-.82.29zm-1.64.44v5.14h.82v-5.38c-.28.07-.56.15-.82.24zm-1.63.15v4.99h.82v-5.18c-.28.05-.56.11-.82.19zm-1.64.42v4.57h.82v-4.73c-.28.04-.55.1-.82.16zm-1.63.48v4.09h.82v-4.22c-.28.03-.55.08-.82.13zm-1.64.67v3.42h.82v-3.51c-.28.02-.55.05-.82.09zm-1.63.78v2.64h.82v-2.71c-.28.01-.55.04-.82.07z"/></svg>`,
        },
        {
          id: "bandcamp",
          name: "Bandcamp",
          color: "#1DA0C3",
          bgHover: "#1789a7",
          url: links.bandcamp,
          icon: `<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M0 18.75l7.437-13.5h16.563l-7.438 13.5z"/></svg>`,
        },
      ];

      const activeButtons = platforms
        .filter((p) => !!p.url)
        .map((p) => {
          return `
          <a href="${escapeHtml(p.url)}" target="_blank" rel="noopener noreferrer" class="platform-btn platform-${p.id}">
            <span class="platform-icon">${p.icon}</span>
            <span class="platform-name">${p.name}</span>
            <span class="platform-action">${i18n.play}</span>
          </a>`;
        })
        .join("\n");

      // Disable cache during development/testing for instant updates
      res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
      res.setHeader("Pragma", "no-cache");
      res.setHeader("Expires", "0");
      res.setHeader("Content-Type", "text/html; charset=utf-8");

      res.status(200).send(`<!DOCTYPE html>
<html lang="${i18n.lang}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>${title} – ${artist} | SongFlip</title>
  <meta name="description" content="${i18n.listenOn}: ${title} – ${artist}">
  
  <!-- Open Graph / Social Media Preview Cards (WhatsApp, iMessage, Telegram, Discord) -->
  <meta property="og:site_name" content="SongFlip">
  <meta property="og:title" content="${title} – ${artist}">
  <meta property="og:description" content="${i18n.listenOn}">
  <meta property="og:image" content="${coverUrl}">
  <meta property="og:image:width" content="640">
  <meta property="og:image:height" content="640">
  <meta property="og:url" content="${currentShareUrl}">
  <meta property="og:type" content="music.song">
  
  <!-- Twitter Card -->
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="${title} – ${artist}">
  <meta name="twitter:description" content="${i18n.listenOn}">
  <meta name="twitter:image" content="${coverUrl}">
  
  <meta name="theme-color" content="#0d1117">
  <!-- Favicons (Official App Icon Suite) -->
  <link rel="icon" type="image/svg+xml" href="https://songflip.link/images/favicon.svg">
  <link rel="icon" type="image/png" sizes="96x96" href="https://songflip.link/images/favicon-96x96.png">
  <link rel="icon" type="image/png" sizes="192x192" href="https://songflip.link/images/favicon-192x192.png">
  <link rel="shortcut icon" href="https://songflip.link/images/favicon.ico">
  <link rel="apple-touch-icon" sizes="180x180" href="https://songflip.link/images/apple-touch-icon.png">
  
  <link rel="stylesheet" href="https://songflip.link/share.css">
  <style>
    .platform-soundcloud .platform-icon { color: #FF5500; }
    .platform-soundcloud .platform-action { background: #FF5500; }
    .platform-soundcloud:hover .platform-action { background: #e64d00; }

    .platform-bandcamp .platform-icon { color: #1DA0C3; }
    .platform-bandcamp .platform-action { background: #1DA0C3; }
    .platform-bandcamp:hover .platform-action { background: #1789a7; }

    .share-action-bar {
      width: 100%;
      margin-bottom: 22px;
    }
    .btn-share {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 9px;
      width: 100%;
      padding: 13px 18px;
      background: rgba(255, 255, 255, 0.07);
      border: 1px solid rgba(255, 255, 255, 0.14);
      border-radius: 16px;
      color: #f0f6fc;
      font-size: 14px;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      font-family: inherit;
    }
    .btn-share:hover {
      background: rgba(255, 255, 255, 0.13);
      border-color: rgba(255, 255, 255, 0.24);
      transform: translateY(-1px);
    }
    .btn-share:active {
      transform: scale(0.98);
    }
    .share-toast {
      position: fixed;
      bottom: 28px;
      left: 50%;
      transform: translateX(-50%) translateY(30px);
      background: #10b981;
      color: #0b0f17;
      padding: 11px 22px;
      border-radius: 30px;
      font-size: 13px;
      font-weight: 800;
      box-shadow: 0 10px 28px rgba(16, 185, 129, 0.4);
      opacity: 0;
      pointer-events: none;
      transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
      z-index: 9999;
    }
    .share-toast.show {
      transform: translateX(-50%) translateY(0);
      opacity: 1;
    }
  </style>
</head>
<body>
  <img src="${coverUrl}" alt="" class="ambient-bg-cover" aria-hidden="true" />
  
  <main class="container">
    <div class="cover-art-container">
      <img src="${coverUrl}" alt="${title}" class="cover-art" loading="eager" />
    </div>
    
    ${isAlbum ? `<span class="type-badge">${i18n.album}</span>` : (isArtist ? `<span class="type-badge">${i18n.artist}</span>` : "")}
    <h1 class="song-title">${title}</h1>
    <p class="artist-name">${artist}</p>
    
    <div class="platforms-list">
      ${activeButtons}
    </div>

    <div class="share-action-bar">
      <button id="shareBtn" class="btn-share" type="button" aria-label="${isMobileUA ? i18n.shareLink : i18n.copyLink}">
        <svg id="shareIcon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="${isMobileUA ? "" : "display:none;"}">
          <circle cx="18" cy="5" r="3"></circle>
          <circle cx="6" cy="12" r="3"></circle>
          <circle cx="18" cy="19" r="3"></circle>
          <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line>
          <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line>
        </svg>
        <svg id="copyIcon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="${isMobileUA ? "display:none;" : ""}">
          <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
        </svg>
        <span id="shareBtnText">${isMobileUA ? i18n.shareLink : i18n.copyLink}</span>
      </button>
    </div>
    
    <footer class="footer-app">
      <a href="https://download.songflip.link" target="_blank" rel="noopener noreferrer" class="brand-link">
        <img src="https://songflip.link/images/favicon-96x96.png" alt="SongFlip Logo" class="brand-icon" />
        <span class="brand-text">${i18n.flippedWith} <strong>SongFlip</strong></span>
      </a>
      <p class="footer-subtext">${i18n.tagline}</p>
    </footer>
  </main>

  <div id="shareToast" class="share-toast" role="status" aria-live="polite">${i18n.linkCopied}</div>

  <script>
    (function() {
      var shareBtn = document.getElementById("shareBtn");
      var shareBtnText = document.getElementById("shareBtnText");
      var shareIcon = document.getElementById("shareIcon");
      var copyIcon = document.getElementById("copyIcon");
      var shareToast = document.getElementById("shareToast");
      var textCopied = ${JSON.stringify(i18n.linkCopied)};
      var textFailed = ${JSON.stringify(i18n.copyFailed)};
      var textShareLink = ${JSON.stringify(i18n.shareLink)};
      var textCopyLink = ${JSON.stringify(i18n.copyLink)};
      if (!shareBtn) return;

      var isMobile = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent) ||
                     (navigator.maxTouchPoints > 1 && /Macintosh|MacIntel/i.test(navigator.userAgent));

      if (isMobile) {
        if (shareBtnText) shareBtnText.textContent = textShareLink;
        if (shareBtn) shareBtn.setAttribute("aria-label", textShareLink);
        if (shareIcon) shareIcon.style.display = "inline-block";
        if (copyIcon) copyIcon.style.display = "none";
      } else {
        if (shareBtnText) shareBtnText.textContent = textCopyLink;
        if (shareBtn) shareBtn.setAttribute("aria-label", textCopyLink);
        if (shareIcon) shareIcon.style.display = "none";
        if (copyIcon) copyIcon.style.display = "inline-block";
      }

      function showToast(msg) {
        if (!shareToast) return;
        shareToast.textContent = msg || textCopied;
        shareToast.classList.add("show");
        setTimeout(function() {
          shareToast.classList.remove("show");
        }, 2200);
      }

      function copyFallback(url) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(url).then(function() {
            showToast(textCopied);
          }).catch(function() {
            legacyCopy(url);
          });
        } else {
          legacyCopy(url);
        }
      }

      function legacyCopy(url) {
        var input = document.createElement("input");
        input.value = url;
        document.body.appendChild(input);
        input.select();
        try {
          document.execCommand("copy");
          showToast(textCopied);
        } catch (e) {
          showToast(textFailed);
        }
        document.body.removeChild(input);
      }

      shareBtn.addEventListener("click", function() {
        var url = window.location.href;
        var title = document.title;
        if (isMobile && navigator.share) {
          navigator.share({
            title: title,
            url: url
          }).catch(function(err) {
            if (err.name !== "AbortError") {
              copyFallback(url);
            }
          });
        } else {
          copyFallback(url);
        }
      });
    })();
  </script>
</body>
</html>`);
    } catch (err: any) {
      console.error("Error in renderWebShare:", err);
      res.status(500).send("Internal Server Error");
    }
  }
);

export { cleanSearchQuery, normalizeMusicUrl, isRateLimited, getWebShareI18n };
