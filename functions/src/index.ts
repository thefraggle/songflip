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
    let title = entityData.title || "";
    let artist = entityData.artistName || "";
    const thumbnailUrl = entityData.thumbnailUrl;

    const sections = pageData.sections || [];
    if ((!title || !artist) && sections.length > 0) {
      const first = sections[0];
      if (!title) title = first.title || "";
      if (!artist) artist = first.artistName || "";
    }

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

    // 2. Authenticate PRO User via RevenueCat
    const authHeader = req.headers.authorization || "";
    const tokenMatch = authHeader.match(/^Bearer\s+(.+)$/i);
    const userId = tokenMatch ? tokenMatch[1].trim() : (req.headers["x-user-id"] as string)?.trim();

    if (!userId) {
      res.status(401).json({ error: "MISSING_AUTH_TOKEN", message: "RevenueCat user ID required" });
      return;
    }

    const isPro = await verifyProStatus(userId);
    if (!isPro) {
      res.status(403).json({ error: "PRO_REQUIRED", message: "SongFlip PRO is required to use the L2 Server Cache." });
      return;
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
        res.setHeader("X-Cache", "HIT");
        res.setHeader("Cache-Control", "public, max-age=86400");
        res.status(200).json({
          status: "success",
          cached: true,
          item: cachedData,
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

    // Also index other platform URLs for future hits
    Object.values(resolvedItem.links).forEach((platformUrl) => {
      if (platformUrl) {
        const altNorm = normalizeMusicUrl(platformUrl);
        const altHash = hashUrl(altNorm);
        if (altHash !== primaryHash) {
          batch.set(db.collection("l2_song_cache").doc(altHash), resolvedItem);
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
      item: resolvedItem,
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
