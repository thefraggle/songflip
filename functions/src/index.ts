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
 * Verifies PRO entitlement via RevenueCat REST API.
 */
async function verifyProStatus(userId: string): Promise<boolean> {
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
    
    // Check both 'songflip_pro' and 'pro' entitlements
    const proEntitlement = entitlements["songflip_pro"] || entitlements["pro"];
    const isPro = !!(proEntitlement && (
      proEntitlement.expires_date === null || // Lifetime
      new Date(proEntitlement.expires_date).getTime() > Date.now()
    ));

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

/**
 * Resolves song metadata & cross-platform links via SongLink API.
 */
async function resolveSongLive(url: string): Promise<SongMetadata | null> {
  try {
    const res = await axios.get("https://api.song.link/v1-alpha.1/links", {
      params: { url: url, userCountry: "DE" },
      headers: { "User-Agent": "SongFlip-L2-Cache/1.0" },
      timeout: 6000,
    });

    const data = res.data;
    const entityUniqueId = data?.entityUniqueId;
    const entity = data?.entitiesByUniqueId?.[entityUniqueId];

    const title = entity?.title || "Unknown Title";
    const artist = entity?.artistName || "Unknown Artist";
    const thumbnailUrl = entity?.thumbnailUrl;
    const isAlbum = entity?.type === "album";

    const linksByPlatform = data?.linksByPlatform || {};
    const links: PlatformLinks = {
      spotify: linksByPlatform.spotify?.url,
      youtubeMusic: linksByPlatform.youtubeMusic?.url || linksByPlatform.youtube?.url,
      appleMusic: linksByPlatform.appleMusic?.url || linksByPlatform.itunes?.url,
      deezer: linksByPlatform.deezer?.url,
      tidal: linksByPlatform.tidal?.url,
      amazonMusic: linksByPlatform.amazonMusic?.url || linksByPlatform.amazon?.url,
    };

    const now = Date.now();
    const ninetyDaysMs = 90 * 24 * 60 * 60 * 1000;
    const expiresAt = admin.firestore.Timestamp.fromMillis(now + ninetyDaysMs);

    return {
      title,
      artist,
      thumbnailUrl,
      isAlbum,
      links,
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
 * Health check endpoint
 */
export const health = onRequest(
  { region: "europe-west3", memory: "128MiB", cors: true, invoker: "public" },
  async (_req, res) => {
    res.status(200).json({ status: "ok", service: "SongFlip L2 Cache Engine", timestamp: Date.now() });
  }
);
