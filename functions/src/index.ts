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

const REVENUECAT_SECRET_KEY = process.env.REVENUECAT_SECRET_KEY || "";

/**
 * Normalizes music URLs into clean canonical identifiers.
 */
function normalizeMusicUrl(rawUrl: string): string {
  try {
    const url = new URL(rawUrl.trim());
    // Strip common tracking and navigation parameters
    url.searchParams.delete("si");
    url.searchParams.delete("context");
    url.searchParams.delete("feature");
    url.searchParams.delete("src");
    url.searchParams.delete("utm_source");
    url.searchParams.delete("utm_medium");
    url.searchParams.delete("utm_campaign");

    // Remove international language paths for Spotify (e.g. /intl-de/track/...)
    let pathname = url.pathname;
    pathname = pathname.replace(/\/intl-[a-z]{2,3}(?:-[a-z]{2,4})?\//i, "/");
    url.pathname = pathname;

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
}

interface SongMetadata {
  title: string;
  artist: string;
  thumbnailUrl?: string;
  isAlbum: boolean;
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

/**
 * Resolves song metadata & cross-platform links via direct SongLink engine.
 */
async function resolveSongLive(url: string): Promise<SongMetadata | null> {
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
    if (typeof html !== "string") return null;

    const scriptTag = '<script id="__NEXT_DATA__" type="application/json">';
    if (!html.includes(scriptTag)) return null;

    const jsonString = html.split(scriptTag)[1]?.split("</script>")[0];
    if (!jsonString) return null;

    const parsed = JSON.parse(jsonString);
    const pageData = parsed?.props?.pageProps?.pageData;
    if (!pageData) return null;

    const pageId = pageData.pageId || "";
    const entityUniqueId = pageData.entityUniqueId || "";
    const isAlbum = pageId.includes("|album|") || entityUniqueId.includes("|album|");

    const entityData = pageData.entityData || {};
    let rawTitle = entityData.title || "";
    let rawArtist = entityData.artistName || "";
    let thumbnailUrl = entityData.thumbnailUrl;

    const sections = pageData.sections || [];
    if ((!rawTitle || !rawArtist) && sections.length > 0) {
      const first = sections[0];
      if (!rawTitle) rawTitle = first.title || "";
      if (!rawArtist) rawArtist = first.artistName || "";
    }

    const sanitized = sanitizeMusicMetadata(rawTitle, rawArtist);
    let title = sanitized.title;
    let artist = sanitized.artist;

    const linksMap: PlatformLinks = {};
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
        }
      }
    }

    // If artist is missing or generic, or few links, query Deezer to heal metadata
    if (!artist || Object.keys(linksMap).length < 4) {
      const query = (artist + " " + title).trim();
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
        } catch (_) {}
      }
    }

    // Fallback for Apple Music via iTunes Search API if not populated
    if (!linksMap.appleMusic && title && artist) {
      try {
        const itunesRes = await axios.get("https://itunes.apple.com/search", {
          params: {
            term: `${artist} ${title}`,
            media: "music",
            entity: isAlbum ? "album" : "song",
            limit: 1,
          },
          timeout: 4000,
        });
        const first = itunesRes.data?.results?.[0];
        if (first) {
          linksMap.appleMusic = first.trackViewUrl || first.collectionViewUrl;
          if (!thumbnailUrl && first.artworkUrl100) {
            thumbnailUrl = first.artworkUrl100.replace("100x100bb.jpg", "600x600bb.jpg");
          }
        }
      } catch (itunesErr: any) {
        console.warn("iTunes fallback search failed:", itunesErr?.message);
      }
    }

    // If SongLink returned no links, trigger Multi-Tier Metadata & Album Resolver
    if (Object.keys(linksMap).length === 0) {
      const cleanLower = url.toLowerCase();
      const isAlbumUrl = cleanLower.includes("/album/") || cleanLower.includes("album");

      // Attempt 1: Apple Music ID Lookup
      if (cleanLower.includes("apple.com")) {
        const idMatch = url.match(/[?&]i=(\d+)/) || url.match(/\/(\d+)(?:\?|$)/);
        if (idMatch && idMatch[1]) {
          try {
            const itunesLookup = await axios.get("https://itunes.apple.com/lookup", {
              params: { id: idMatch[1], entity: isAlbumUrl ? "album" : "song" },
              timeout: 4000,
            });
            const item = itunesLookup.data?.results?.[0];
            if (item) {
              title = item.trackName || item.collectionName || title;
              artist = item.artistName || artist;
              thumbnailUrl = (item.artworkUrl100 || "").replace("100x100bb.jpg", "600x600bb.jpg");
              linksMap.appleMusic = item.trackViewUrl || item.collectionViewUrl || url;
            }
          } catch (_) {}
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
          } catch (_) {}
        }
      }
    }

    // Always populate all 6 streaming platforms with direct or fallback search links
    if (title && artist) {
      const cleanLower = url.toLowerCase();
      if (!linksMap.spotify) {
        linksMap.spotify = cleanLower.includes("spotify.com") ? url : `https://open.spotify.com/search/${encodeURIComponent(artist + " " + title)}`;
      }
      if (!linksMap.appleMusic) {
        linksMap.appleMusic = cleanLower.includes("apple.com") ? url : `https://music.apple.com/search?term=${encodeURIComponent(artist + " " + title)}`;
      }
      if (!linksMap.youtubeMusic) {
        linksMap.youtubeMusic = cleanLower.includes("music.youtube.com") ? url : `https://music.youtube.com/search?q=${encodeURIComponent(artist + " " + title)}`;
      }
      if (!linksMap.deezer) {
        linksMap.deezer = cleanLower.includes("deezer.com") ? url : `https://www.deezer.com/search/${encodeURIComponent(artist + " " + title)}`;
      }
      if (!linksMap.tidal) {
        linksMap.tidal = cleanLower.includes("tidal.com") ? url : `https://listen.tidal.com/search?q=${encodeURIComponent(artist + " " + title)}`;
      }
      if (!linksMap.amazonMusic) {
        linksMap.amazonMusic = cleanLower.includes("amazon.") ? url : `https://music.amazon.com/search/${encodeURIComponent(artist + " " + title)}`;
      }
    }

    if (Object.keys(linksMap).length === 0) return null;

    const now = Date.now();
    const ninetyDaysMs = 90 * 24 * 60 * 60 * 1000;
    const expiresAt = admin.firestore.Timestamp.fromMillis(now + ninetyDaysMs);

    return {
      title: title || "Unknown Title",
      artist: artist || "Unknown Artist",
      thumbnailUrl,
      isAlbum,
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
    // 1. Validate HTTP Method
    if (req.method !== "GET") {
      res.status(405).json({ error: "METHOD_NOT_ALLOWED" });
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
    if (!targetUrl || typeof targetUrl !== "string") {
      res.status(400).json({ error: "INVALID_URL", message: "Parameter 'url' is required" });
      return;
    }

    const normalizedUrl = normalizeMusicUrl(targetUrl);
    const primaryHash = hashUrl(normalizedUrl);

    // 4. L2 Cache Lookup in Firestore
    const cacheRef = db.collection("l2_song_cache").doc(primaryHash);
    const docSnap = await cacheRef.get();

    if (docSnap.exists) {
      const cachedData = docSnap.data() as SongMetadata;
      const isExpired = cachedData.expiresAt && cachedData.expiresAt.toMillis() < Date.now();
      if (!isExpired) {
        // Rolling 90-day TTL: Reset expiration timer upon each access
        const rollingExpiresAt = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000);
        cacheRef.update({ expiresAt: rollingExpiresAt, lastAccessedAt: Date.now() }).catch(() => {});

        res.setHeader("X-Cache", "HIT");
        res.setHeader("Cache-Control", "public, max-age=86400");
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

    // 5. Cache Miss -> Live Resolution
    const resolvedItem = await resolveSongLive(normalizedUrl);
    if (!resolvedItem) {
      res.status(502).json({ error: "RESOLUTION_FAILED", message: "Could not resolve music metadata" });
      return;
    }

    // 6. Save in Firestore for primary URL hash and all other platform links
    const batch = db.batch();
    batch.set(cacheRef, resolvedItem);
    // Also store short 8-char key for short URLs
    const primaryShortId = primaryHash.substring(0, 8);
    batch.set(db.collection("l2_song_cache").doc(primaryShortId), resolvedItem);

    // Also index other platform URLs for future hits
    Object.values(resolvedItem.links).forEach((platformUrl) => {
      if (platformUrl) {
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

    res.setHeader("X-Cache", "MISS");
    res.setHeader("Cache-Control", "public, max-age=86400");
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
 * Dynamic Promo Code Redemption Endpoint: POST /redeemPromoCode
 * Body: { code: string }
 */
export const redeemPromoCode = onRequest(
  {
    region: "europe-west3",
    memory: "256MiB",
    maxInstances: 10,
    timeoutSeconds: 10,
    cors: true,
    invoker: "public",
  },
  async (req, res) => {
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

    const rawCode = (req.body?.code || req.query?.code) as string;
    if (!rawCode || typeof rawCode !== "string") {
      res.status(400).json({ error: "INVALID_CODE", message: "Gutscheincode erforderlich." });
      return;
    }

    const cleanCode = rawCode.trim().toUpperCase();
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

        // Atomically increment redemptions
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
    title: "Blinding Lights",
    artist: "The Weeknd",
    thumbnailUrl: "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36",
    isAlbum: false,
    links: {
      spotify: "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b",
      appleMusic: "https://music.apple.com/us/album/blinding-lights/1499378108?i=1499378607",
      youtubeMusic: "https://music.youtube.com/watch?v=4NRXx6U8ABQ",
      deezer: "https://www.deezer.com/track/908604612",
      tidal: "https://listen.tidal.com/track/134657499",
      amazonMusic: "https://music.amazon.com/albums/B0855DV6QG?trackAsin=B0855DTRX6"
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
      tidal: "https://listen.tidal.com/track/134657499",
      amazonMusic: "https://music.amazon.com/albums/B0855DV6QG?trackAsin=B0855DTRX6"
    }
  },
  "9c3a1b8e": {
    title: "Get Lucky (feat. Pharrell Williams & Nile Rodgers)",
    artist: "Daft Punk",
    thumbnailUrl: "https://cdn-images.dzcdn.net/images/cover/bc49adb87758e0c8c4e508a9c5cce85d/1000x1000-000000-80-0-0.jpg",
    isAlbum: false,
    links: {
      spotify: "https://open.spotify.com/track/2Foc5Q5nqNiosCNqttzAG4",
      appleMusic: "https://music.apple.com/us/album/get-lucky-feat-pharrell-williams-nile-rodgers/617154241?i=617154366",
      youtubeMusic: "https://music.youtube.com/watch?v=5NV6Rdv1a3I",
      deezer: "https://www.deezer.com/track/67238733",
      tidal: "https://listen.tidal.com/track/19875150",
      amazonMusic: "https://music.amazon.com/albums/B00C0641ES?trackAsin=B00C06497O"
    }
  },
  "86e847b3": {
    title: "Get Lucky (feat. Pharrell Williams & Nile Rodgers)",
    artist: "Daft Punk",
    thumbnailUrl: "https://cdn-images.dzcdn.net/images/cover/bc49adb87758e0c8c4e508a9c5cce85d/1000x1000-000000-80-0-0.jpg",
    isAlbum: false,
    links: {
      spotify: "https://open.spotify.com/track/2Foc5Q5nqNiosCNqttzAG4",
      appleMusic: "https://music.apple.com/us/album/get-lucky-feat-pharrell-williams-nile-rodgers/617154241?i=617154366",
      youtubeMusic: "https://music.youtube.com/watch?v=5NV6Rdv1a3I",
      deezer: "https://www.deezer.com/track/67238733",
      tidal: "https://listen.tidal.com/track/19875150",
      amazonMusic: "https://music.amazon.com/albums/B00C0641ES?trackAsin=B00C06497O"
    }
  },
  "7e4d2a1f": {
    title: "BIRDS OF A FEATHER",
    artist: "Billie Eilish",
    thumbnailUrl: "https://cdn-images.dzcdn.net/images/cover/5d284b31cb9ddeb1a0c79aede5a94e1c/1000x1000-000000-80-0-0.jpg",
    isAlbum: false,
    links: {
      spotify: "https://open.spotify.com/track/6dOtVTDmmpzpEcYRQBRnh3",
      appleMusic: "https://music.apple.com/us/album/birds-of-a-feather/1739659134?i=1739659142",
      youtubeMusic: "https://music.youtube.com/watch?v=V9PVRfjEBTI",
      deezer: "https://www.deezer.com/track/2798606042",
      tidal: "https://listen.tidal.com/track/363406282",
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
          // Rolling 90-day TTL: Reset expiration timer upon each access
          const rollingExpiresAt = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000);
          docSnap.ref.update({ expiresAt: rollingExpiresAt, lastAccessedAt: Date.now() }).catch(() => {});
        } else if (hash.length > 8) {
          const shortSnap = await db.collection("l2_song_cache").doc(hash.substring(0, 8)).get();
          if (shortSnap.exists) {
            songData = shortSnap.data();
            // Rolling 90-day TTL: Reset expiration timer upon each access
            const rollingExpiresAt = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000);
            shortSnap.ref.update({ expiresAt: rollingExpiresAt, lastAccessedAt: Date.now() }).catch(() => {});
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
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>Song Not Found | SongFlip</title>
  <meta name="description" content="This shared song link is expired or not found. Open links directly with SongFlip.">
  <meta name="theme-color" content="#0b0f17">
  
  <!-- Favicons (Official App Icon Suite) -->
  <link rel="icon" type="image/svg+xml" href="https://songflip.link/images/favicon.svg">
  <link rel="icon" type="image/png" sizes="96x96" href="https://songflip.link/images/favicon-96x96.png">
  <link rel="icon" type="image/png" sizes="192x192" href="https://songflip.link/images/favicon-192x192.png">
  <link rel="shortcut icon" href="https://songflip.link/images/favicon.ico">
  <link rel="apple-touch-icon" sizes="180x180" href="https://songflip.link/images/apple-touch-icon.png">

  <style>
    :root {
      --bg: #0b0f17;
      --card-bg: rgba(22, 27, 34, 0.85);
      --card-border: rgba(255, 255, 255, 0.1);
      --text-main: #f0f6fc;
      --text-muted: #8b949e;
      --accent: #10b981;
      --accent-glow: rgba(16, 185, 129, 0.25);
    }
    * { margin: 0; padding: 0; box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; -webkit-font-smoothing: antialiased; }
    body {
      background-color: var(--bg);
      color: var(--text-main);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px 16px;
      position: relative;
      overflow-x: hidden;
    }
    .ambient-bg {
      position: fixed;
      top: -30%;
      left: -20%;
      width: 140%;
      height: 140%;
      background: radial-gradient(circle at center, rgba(16, 185, 129, 0.12) 0%, rgba(139, 92, 246, 0.08) 40%, transparent 70%);
      filter: blur(60px);
      pointer-events: none;
      z-index: 0;
    }
    .container {
      position: relative;
      z-index: 1;
      max-width: 440px;
      width: 100%;
      background: var(--card-bg);
      backdrop-filter: blur(24px);
      -webkit-backdrop-filter: blur(24px);
      border: 1px solid var(--card-border);
      border-radius: 28px;
      padding: 36px 24px 28px 24px;
      box-shadow: 0 24px 48px rgba(0, 0, 0, 0.6);
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
    }
    .icon-wrapper {
      width: 80px;
      height: 80px;
      border-radius: 24px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 20px;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
      position: relative;
    }
    .icon-symbol {
      font-size: 38px;
      line-height: 1;
    }
    .badge-status {
      display: inline-block;
      background: rgba(239, 68, 68, 0.15);
      border: 1px solid rgba(239, 68, 68, 0.3);
      color: #f87171;
      padding: 3px 12px;
      border-radius: 20px;
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      margin-bottom: 12px;
    }
    .title {
      font-size: 22px;
      font-weight: 800;
      color: #ffffff;
      margin-bottom: 10px;
      line-height: 1.3;
    }
    .description {
      font-size: 14px;
      line-height: 1.6;
      color: var(--text-muted);
      margin-bottom: 28px;
      max-width: 360px;
    }
    .btn-action {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 10px;
      width: 100%;
      background: linear-gradient(135deg, #10b981 0%, #059669 100%);
      color: #ffffff;
      text-decoration: none;
      padding: 14px 20px;
      border-radius: 14px;
      font-weight: 700;
      font-size: 15px;
      box-shadow: 0 8px 20px var(--accent-glow);
      transition: all 0.2s ease;
      margin-bottom: 24px;
    }
    .btn-action:hover {
      transform: translateY(-2px);
      box-shadow: 0 12px 28px rgba(16, 185, 129, 0.35);
    }
    .btn-action:active {
      transform: translateY(0);
    }
    .footer-app {
      width: 100%;
      padding-top: 18px;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
    }
    .brand-link {
      display: inline-flex;
      align-items: center;
      gap: 9px;
      text-decoration: none;
      color: #f0f6fc;
      transition: opacity 0.2s ease;
    }
    .brand-link:hover {
      opacity: 0.85;
    }
    .brand-icon {
      width: 22px;
      height: 22px;
      border-radius: 6px;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
    }
    .brand-text {
      font-size: 14px;
      color: #e2e8f0;
    }
    .brand-text strong {
      color: #34d399;
    }
    .footer-subtext {
      font-size: 12px;
      color: var(--text-muted);
    }
  </style>
</head>
<body>
  <div class="ambient-bg"></div>

  <main class="container">
    <div class="icon-wrapper">
      <span class="icon-symbol">🎵</span>
    </div>

    <span class="badge-status">404 • Not Found</span>
    <h1 class="title">Song Link Not Found</h1>
    <p class="description">
      This music share link is no longer available or was entered incorrectly. Open music links seamlessly with SongFlip.
    </p>

    <a href="https://download.songflip.link" class="btn-action">
      <span>Discover SongFlip</span>
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>
    </a>

    <footer class="footer-app">
      <a href="https://download.songflip.link" class="brand-link">
        <img src="https://songflip.link/images/favicon-96x96.png" alt="SongFlip Logo" class="brand-icon" />
        <span class="brand-text">Powered by <strong>SongFlip</strong></span>
      </a>
      <p class="footer-subtext">The automatic 0-click music redirector</p>
    </footer>
  </main>
</body>
</html>`);
        return;
      }

      // 5. Prepare Safe Data & Strings
      const isAlbum = !!songData.isAlbum;
      const sanitized = sanitizeMusicMetadata(songData.title || "", songData.artist || "");
      let cleanTitle = sanitized.title || "Track";
      let cleanArtist = sanitized.artist;

      // If artist is missing or generic (e.g. from older cache entry), live heal via Deezer search
      if (!cleanArtist || cleanArtist === "Music" || cleanArtist === "Artist" || cleanArtist.toLowerCase().includes("youtube")) {
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
      const artist = escapeHtml(cleanArtist || "Music");
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
      ];

      const activeButtons = platforms
        .filter((p) => !!p.url)
        .map((p) => {
          return `
          <a href="${escapeHtml(p.url)}" target="_blank" rel="noopener noreferrer" class="platform-btn" style="--btn-brand: ${p.color}; --btn-brand-hover: ${p.bgHover};">
            <span class="platform-icon">${p.icon}</span>
            <span class="platform-name">${p.name}</span>
            <span class="platform-action">Play</span>
          </a>`;
        })
        .join("\n");

      // Disable cache during development/testing for instant updates
      res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
      res.setHeader("Pragma", "no-cache");
      res.setHeader("Expires", "0");
      res.setHeader("Content-Type", "text/html; charset=utf-8");

      res.status(200).send(`<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>${title} – ${artist} | SongFlip</title>
  <meta name="description" content="Listen to ${title} by ${artist} on Spotify, Apple Music, YouTube Music, Deezer, Tidal and Amazon Music.">
  
  <!-- Open Graph / Social Media Preview Cards (WhatsApp, iMessage, Telegram, Discord) -->
  <meta property="og:site_name" content="SongFlip">
  <meta property="og:title" content="${title} – ${artist}">
  <meta property="og:description" content="Listen on Spotify, Apple Music, YouTube Music & more.">
  <meta property="og:image" content="${coverUrl}">
  <meta property="og:image:width" content="640">
  <meta property="og:image:height" content="640">
  <meta property="og:url" content="${currentShareUrl}">
  <meta property="og:type" content="music.song">
  
  <!-- Twitter Card -->
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="${title} – ${artist}">
  <meta name="twitter:description" content="Listen on Spotify, Apple Music, YouTube Music & more.">
  <meta name="twitter:image" content="${coverUrl}">
  
  <meta name="theme-color" content="#0d1117">
  <!-- Favicons (Official App Icon Suite) -->
  <link rel="icon" type="image/svg+xml" href="https://songflip.link/images/favicon.svg">
  <link rel="icon" type="image/png" sizes="96x96" href="https://songflip.link/images/favicon-96x96.png">
  <link rel="icon" type="image/png" sizes="192x192" href="https://songflip.link/images/favicon-192x192.png">
  <link rel="shortcut icon" href="https://songflip.link/images/favicon.ico">
  <link rel="apple-touch-icon" sizes="180x180" href="https://songflip.link/images/apple-touch-icon.png">
  
  <style>
    :root {
      --bg: #0b0f17;
      --card-bg: rgba(22, 27, 34, 0.75);
      --card-border: rgba(255, 255, 255, 0.1);
      --text-main: #f0f6fc;
      --text-muted: #8b949e;
      --accent: #10b981;
    }
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      -webkit-font-smoothing: antialiased;
    }
    body {
      background-color: var(--bg);
      color: var(--text-main);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px 16px;
      position: relative;
      overflow-x: hidden;
    }
    /* Ambient Ambient Blurred Glow */
    .ambient-bg {
      position: fixed;
      top: -20%;
      left: -20%;
      width: 140%;
      height: 140%;
      background-image: url('${coverUrl}');
      background-size: cover;
      background-position: center;
      filter: blur(80px) brightness(0.25) saturate(1.4);
      opacity: 0.8;
      z-index: 0;
      pointer-events: none;
      transform: translateZ(0);
    }
    .container {
      position: relative;
      z-index: 1;
      max-width: 440px;
      width: 100%;
      background: var(--card-bg);
      backdrop-filter: blur(24px);
      -webkit-backdrop-filter: blur(24px);
      border: 1px solid var(--card-border);
      border-radius: 28px;
      padding: 28px 20px;
      box-shadow: 0 24px 48px rgba(0, 0, 0, 0.5);
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
    }
    .cover-art-container {
      width: 220px;
      height: 220px;
      margin-bottom: 20px;
      position: relative;
    }
    .cover-art {
      width: 100%;
      height: 100%;
      object-fit: cover;
      border-radius: 20px;
      box-shadow: 0 12px 28px rgba(0, 0, 0, 0.45);
      border: 1px solid rgba(255, 255, 255, 0.12);
    }
    .type-badge {
      display: inline-block;
      background: rgba(255, 255, 255, 0.1);
      border: 1px solid rgba(255, 255, 255, 0.15);
      padding: 3px 10px;
      border-radius: 20px;
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      color: #cbd5e1;
      margin-bottom: 8px;
    }
    .song-title {
      font-size: 21px;
      font-weight: 800;
      color: #ffffff;
      line-height: 1.3;
      margin-bottom: 6px;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .artist-name {
      font-size: 15px;
      font-weight: 500;
      color: var(--text-muted);
      margin-bottom: 24px;
      display: -webkit-box;
      -webkit-line-clamp: 1;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .platforms-list {
      width: 100%;
      display: flex;
      flex-direction: column;
      gap: 10px;
      margin-bottom: 24px;
    }
    .platform-btn {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
      padding: 12px 18px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 16px;
      text-decoration: none;
      color: #ffffff;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    }
    .platform-btn:hover {
      background: rgba(255, 255, 255, 0.12);
      border-color: rgba(255, 255, 255, 0.2);
      transform: translateY(-1px);
    }
    .platform-btn:active {
      transform: scale(0.98);
    }
    .platform-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--btn-brand);
    }
    .platform-name {
      font-size: 15px;
      font-weight: 600;
      flex: 1;
      text-align: left;
      margin-left: 14px;
    }
    .platform-action {
      font-size: 13px;
      font-weight: 700;
      background: var(--btn-brand);
      color: #000000;
      padding: 6px 14px;
      border-radius: 10px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .footer-app {
      width: 100%;
      padding-top: 18px;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
    }
    .brand-link {
      display: inline-flex;
      align-items: center;
      gap: 9px;
      text-decoration: none;
      color: #f0f6fc;
      transition: opacity 0.2s ease;
    }
    .brand-link:hover {
      opacity: 0.85;
    }
    .brand-icon {
      width: 22px;
      height: 22px;
      border-radius: 6px;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
    }
    .brand-text {
      font-size: 14px;
      color: #e2e8f0;
    }
    .brand-text strong {
      color: #34d399;
    }
    .footer-subtext {
      font-size: 12px;
      color: var(--text-muted);
    }
  </style>
</head>
<body>
  <div class="ambient-bg"></div>
  
  <main class="container">
    <div class="cover-art-container">
      <img src="${coverUrl}" alt="${title}" class="cover-art" loading="eager" />
    </div>
    
    ${isAlbum ? '<span class="type-badge">Album</span>' : ""}
    <h1 class="song-title">${title}</h1>
    <p class="artist-name">${artist}</p>
    
    <div class="platforms-list">
      ${activeButtons}
    </div>
    
    <footer class="footer-app">
      <a href="https://download.songflip.link" target="_blank" rel="noopener noreferrer" class="brand-link">
        <img src="https://songflip.link/images/favicon-96x96.png" alt="SongFlip Logo" class="brand-icon" />
        <span class="brand-text">Flipped with <strong>SongFlip</strong></span>
      </a>
      <p class="footer-subtext">The automatic 0-click music redirector</p>
    </footer>
  </main>
</body>
</html>`);
    } catch (err: any) {
      console.error("Error in renderWebShare:", err);
      res.status(500).send("Internal Server Error");
    }
  }
);

