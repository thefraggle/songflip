package de.goork.songflip.core.engine

import de.goork.songflip.core.cache.LinkCache
import de.goork.songflip.core.cache.createDefaultCacheStorage
import de.goork.songflip.core.engine.resolvers.AppleMusicResolver
import de.goork.songflip.core.engine.resolvers.DeezerResolver
import de.goork.songflip.core.engine.resolvers.PlatformResolver
import de.goork.songflip.core.engine.resolvers.SongLinkApiResolver
import de.goork.songflip.core.engine.resolvers.SpotifyResolver
import de.goork.songflip.core.engine.resolvers.TidalResolver
import de.goork.songflip.core.engine.resolvers.YouTubeMusicResolver
import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.core.util.UrlUtils
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class SongLinkEngine(
    private val client: HttpClient = createPlatformHttpClient(),
    val cache: LinkCache = LinkCache(storage = createDefaultCacheStorage())
) {
    constructor() : this(createPlatformHttpClient(), LinkCache(storage = createDefaultCacheStorage()))

    companion object {
        val shared: SongLinkEngine by lazy { SongLinkEngine() }
        // Public Tidal Web Client Application ID used for public catalog search
        private const val TIDAL_CLIENT_APP_TOKEN = "CzET4vdadNUFQ5JU"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Platform Resolver Strategies
    val appleMusicResolver = AppleMusicResolver(client, json)
    val deezerResolver = DeezerResolver(client, json)
    val tidalResolver = TidalResolver(client, json, TIDAL_CLIENT_APP_TOKEN)
    val youTubeMusicResolver = YouTubeMusicResolver(client)
    val spotifyResolver = SpotifyResolver(client, json)
    val songLinkApiResolver = SongLinkApiResolver(client, json)

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun pingCacheIngestAsync(
        originalUrl: String,
        targetUrl: String,
        targetPlatform: String,
        title: String?,
        artist: String?,
        isAlbum: Boolean,
        links: Map<String, String>? = null
    ) {
        if (originalUrl.isBlank() || targetUrl.isBlank()) return

        backgroundScope.launch {
            try {
                val jsonPayload = buildJsonObject {
                    put("originalUrl", originalUrl)
                    put("targetUrl", targetUrl)
                    put("platform", targetPlatform)
                    if (!title.isNullOrBlank()) put("title", title)
                    if (!artist.isNullOrBlank()) put("artist", artist)
                    put("isAlbum", isAlbum)
                    if (!links.isNullOrEmpty()) {
                        putJsonObject("links") {
                            for ((k, v) in links) {
                                if (k.isNotBlank() && v.isNotBlank()) {
                                    put(k, v)
                                }
                            }
                        }
                    }
                }.toString()

                client.post("https://cache.songflip.link/ingest") {
                    header("Content-Type", "application/json")
                    header("x-web-client", "songflip-app")
                    setBody(jsonPayload)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                // Background ingestion is completely non-blocking and best-effort
            }
        }
    }

    suspend fun resolveTargetUrl(
        inputUrl: String,
        targetPlatformKey: String = "youtubeMusic",
        customApiUrl: String = "",
        customApiToken: String = ""
    ): ResolutionResult {
        return resolveTargetUrl(
            inputUrl = inputUrl,
            targetPlatformKey = targetPlatformKey,
            customApiUrl = customApiUrl,
            customApiToken = customApiToken,
            isPro = false,
            authToken = "",
            isPrefetch = false,
            forceRefresh = false
        )
    }

    suspend fun resolveTargetUrl(
        inputUrl: String,
        targetPlatformKey: String,
        customApiUrl: String,
        customApiToken: String,
        forceRefresh: Boolean
    ): ResolutionResult {
        return resolveTargetUrl(
            inputUrl = inputUrl,
            targetPlatformKey = targetPlatformKey,
            customApiUrl = customApiUrl,
            customApiToken = customApiToken,
            isPro = false,
            authToken = "",
            isPrefetch = false,
            forceRefresh = forceRefresh
        )
    }

    suspend fun forceRefreshTargetUrl(
        inputUrl: String,
        targetPlatformKey: String = "youtubeMusic",
        customApiUrl: String = "",
        customApiToken: String = "",
        isPro: Boolean = false,
        authToken: String = ""
    ): ResolutionResult {
        return resolveTargetUrl(
            inputUrl = inputUrl,
            targetPlatformKey = targetPlatformKey,
            customApiUrl = customApiUrl,
            customApiToken = customApiToken,
            isPro = isPro,
            authToken = authToken,
            isPrefetch = false,
            forceRefresh = true
        )
    }

    suspend fun prefetch(
        inputUrl: String,
        targetPlatformKey: String = "youtubeMusic",
        isPro: Boolean = false,
        authToken: String = ""
    ) {
        val cleanUrl = UrlUtils.extractCleanUrl(inputUrl) ?: return
        val resolvedUrl = if (UrlUtils.isShortLinkDomain(cleanUrl)) {
            resolveCanonicalUrl(cleanUrl)
        } else {
            cleanUrl
        }
        val canonicalUrl = UrlUtils.normalizeUrl(resolvedUrl)
        val now = getCurrentTimeMillis()
        if (cache.get(canonicalUrl, targetPlatformKey, now) != null) return
        try {
            resolveTargetUrl(
                inputUrl = canonicalUrl,
                targetPlatformKey = targetPlatformKey,
                isPro = isPro,
                authToken = authToken,
                isPrefetch = true
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    suspend fun queryL2ServerCache(
        canonicalUrl: String,
        targetPlatformKey: String,
        authToken: String,
        forceRefresh: Boolean = false
    ): ResolutionResult.Success? {
        if (authToken.isBlank()) return null
        val encodedUrl = canonicalUrl.encodeURLParameter()
        val refreshParam = if (forceRefresh) "&force_refresh=true" else ""
        val endpoints = listOf(
            "https://cache.songflip.link/resolve?url=$encodedUrl$refreshParam",
            "https://songflip-web.web.app/resolve?url=$encodedUrl$refreshParam"
        )

        for (endpoint in endpoints) {
            try {
                val resp = client.get(endpoint) {
                    header("Authorization", "Bearer $authToken")
                    header("Accept", "application/json")
                }
                if (resp.status.isSuccess()) {
                    val body = resp.bodyAsText()
                    val root = json.parseToJsonElement(body).jsonObject
                    if (root["status"]?.jsonPrimitive?.content == "success") {
                        val item = root["item"]?.jsonObject
                        if (item != null) {
                            val title = item["title"]?.jsonPrimitive?.content?.ifBlank { null }
                            val artist = item["artist"]?.jsonPrimitive?.content?.ifBlank { null }
                            val isAlbum = item["isAlbum"]?.jsonPrimitive?.booleanOrNull ?: false
                            val links = item["links"]?.jsonObject
                            val thumbnailUrl = item["thumbnailUrl"]?.jsonPrimitive?.content?.ifBlank { null }
                                ?: root["thumbnailUrl"]?.jsonPrimitive?.content?.ifBlank { null }

                            val rawTarget = when (targetPlatformKey) {
                                "spotify" -> links?.get("spotify")?.jsonPrimitive?.content
                                "appleMusic" -> links?.get("appleMusic")?.jsonPrimitive?.content
                                "youtubeMusic" -> links?.get("youtubeMusic")?.jsonPrimitive?.content ?: links?.get("youtube")?.jsonPrimitive?.content
                                "deezer" -> links?.get("deezer")?.jsonPrimitive?.content
                                "tidal" -> links?.get("tidal")?.jsonPrimitive?.content
                                "amazonMusic" -> links?.get("amazonMusic")?.jsonPrimitive?.content
                                else -> links?.get(targetPlatformKey)?.jsonPrimitive?.content
                            }

                            if (!rawTarget.isNullOrBlank()) {
                                val formatted = UrlUtils.formatTargetUrl(rawTarget, targetPlatformKey)
                                val nativeUri = UrlUtils.toNativeAppUri(formatted, targetPlatformKey)
                                return ResolutionResult.Success(
                                    targetUrl = formatted,
                                    platform = targetPlatformKey,
                                    title = title,
                                    artist = artist,
                                    isAlbum = isAlbum,
                                    nativeAppUri = nativeUri,
                                    thumbnailUrl = thumbnailUrl
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Endpoint unreachable or timed out; continue to next fallback
            }
        }
        return null
    }

    suspend fun resolveTargetUrl(
        inputUrl: String,
        targetPlatformKey: String,
        customApiUrl: String = "",
        customApiToken: String = "",
        isPro: Boolean = false,
        authToken: String = "",
        isPrefetch: Boolean = false,
        forceRefresh: Boolean = false
    ): ResolutionResult {
        try {
            // 1. Extract clean URL
            val cleanUrl = UrlUtils.extractCleanUrl(inputUrl)
                ?: return ResolutionResult.Error("No valid URL found in input", errorReason = "invalid_url")

            // 2. Custom API / Webhook (if configured)
            if (customApiUrl.isNotBlank()) {
                val customResult = queryCustomApi(customApiUrl, customApiToken, cleanUrl, targetPlatformKey)
                if (customResult != null) {
                    val formatted = UrlUtils.formatTargetUrl(customResult, targetPlatformKey)
                    val nativeUri = UrlUtils.toNativeAppUri(formatted, targetPlatformKey)
                    return ResolutionResult.Success(
                        targetUrl = formatted,
                        platform = "custom_api",
                        nativeAppUri = nativeUri
                    )
                }
            }

            // 3. Resolve short links
            val resolvedUrl = if (UrlUtils.isShortLinkDomain(cleanUrl)) {
                resolveCanonicalUrl(cleanUrl)
            } else {
                cleanUrl
            }
            val canonicalUrl = UrlUtils.normalizeUrl(resolvedUrl)

            // Playlist links cannot be converted 1:1 in background
            if (UrlUtils.isPlaylistUrl(canonicalUrl)) {
                val platformKey = UrlUtils.detectPlatform(canonicalUrl)?.key ?: "unknown"
                return ResolutionResult.Playlist(
                    originalUrl = canonicalUrl,
                    platform = platformKey,
                    message = "PLAYLIST_NOT_SUPPORTED"
                )
            }

            // Audiobook links cannot be converted due to DRM and platform barriers
            if (UrlUtils.isAudiobookUrl(canonicalUrl)) {
                val platformKey = UrlUtils.detectPlatform(canonicalUrl)?.key ?: "unknown"
                return ResolutionResult.PodcastOrAudiobook(
                    originalUrl = canonicalUrl,
                    platform = platformKey,
                    isAudiobook = true,
                    message = "AUDIOBOOK_NOT_SUPPORTED"
                )
            }

            // Dynamic Podcast Resolution (Issue #31)
            if (UrlUtils.isPodcastUrl(canonicalUrl)) {
                return resolvePodcast(canonicalUrl, targetPlatformKey, isPrefetch, forceRefresh)
            }

            // Social & Session links (Spotify Blend, Jam, Live, User profile) cannot be converted 1:1
            if (UrlUtils.isSocialOrSessionUrl(canonicalUrl)) {
                val platformKey = UrlUtils.detectPlatform(canonicalUrl)?.key ?: "unknown"
                return ResolutionResult.UnsupportedEntity(
                    originalUrl = canonicalUrl,
                    platform = platformKey,
                    entityType = "social_session",
                    message = "SOCIAL_SESSION_NOT_SUPPORTED"
                )
            }

            val isExplicitTrackUrl = canonicalUrl.contains("i=") || canonicalUrl.contains("/song/") || canonicalUrl.contains("/track/")
            val isExplicitAlbumUrl = !isExplicitTrackUrl && UrlUtils.isAlbumUrl(canonicalUrl)
            val now = getCurrentTimeMillis()

            if (forceRefresh) {
                cache.remove(canonicalUrl, targetPlatformKey)
            } else {
                // 4. L1 Cache Lookup (< 5ms)
                val cached = cache.get(canonicalUrl, targetPlatformKey, now)
                if (cached != null) {
                    if (!isPrefetch) {
                        cache.markAsHistory(canonicalUrl, targetPlatformKey, now)
                    }
                    return cached
                }
            }

            // 4.2. Search URL Resolution (Spotify, Apple Music, YouTube, Deezer, Tidal search links)
            val searchQuery = UrlUtils.extractSearchQuery(canonicalUrl)
            if (searchQuery != null) {
                val directUrl = resolveDirectPlatformUrl(searchQuery, targetPlatformKey, isAlbum = false)
                val finalTargetUrl = directUrl ?: UrlUtils.buildSearchUrl(searchQuery, targetPlatformKey)
                val nativeUri = UrlUtils.toNativeAppUri(finalTargetUrl, targetPlatformKey)
                val result = ResolutionResult.Success(
                    targetUrl = finalTargetUrl,
                    platform = if (directUrl != null) targetPlatformKey else "${targetPlatformKey}_search",
                    title = searchQuery,
                    artist = null,
                    isAlbum = false,
                    nativeAppUri = nativeUri
                )
                cache.put(canonicalUrl, targetPlatformKey, result, now, isHistory = !isPrefetch)
                pingCacheIngestAsync(
                    originalUrl = canonicalUrl,
                    targetUrl = finalTargetUrl,
                    targetPlatform = result.platform,
                    title = searchQuery,
                    artist = null,
                    isAlbum = false
                )
                return result
            }

            // 4.5. L2 Server Cache (PRO Feature - < 30ms)
            if (isPro && authToken.isNotBlank()) {
                val l2Result = queryL2ServerCache(canonicalUrl, targetPlatformKey, authToken, forceRefresh)
                if (l2Result != null) {
                    cache.put(canonicalUrl, targetPlatformKey, l2Result, now, isHistory = !isPrefetch)
                    return l2Result
                }
            }

            // 5. Parallel Multi-Source Resolution
            val (songLinkData, trackInfo) = supervisorScope {
                val songLinkDeferred = async { fetchSongLinkData(canonicalUrl) }
                val fallbackTrackDeferred = async { extractTrackInfo(canonicalUrl) }
                val sld = try { songLinkDeferred.await() } catch (e: Exception) { if (e is CancellationException) throw e; null }
                val ti = try { fallbackTrackDeferred.await() } catch (e: Exception) { if (e is CancellationException) throw e; null }
                Pair(sld, ti)
            }

            if (songLinkData != null) {
                // If the user requested an explicit track (e.g. Apple Music with ?i=...), but song.link mapped it to the whole album:
                if (isExplicitTrackUrl && songLinkData.isAlbum) {
                    val resolvedQuery = trackInfo ?: if (songLinkData.artist.isNotEmpty() && !songLinkData.title.contains(songLinkData.artist, ignoreCase = true)) {
                        "${songLinkData.artist} ${songLinkData.title}"
                    } else {
                        songLinkData.title
                    }
                    val directTrackUrl = resolveDirectPlatformUrl(resolvedQuery, targetPlatformKey, isAlbum = false)
                    val finalTargetUrl = directTrackUrl ?: UrlUtils.buildSearchUrl(resolvedQuery, targetPlatformKey)
                    val nativeUri = UrlUtils.toNativeAppUri(finalTargetUrl, targetPlatformKey)
                    val result = ResolutionResult.Success(
                        targetUrl = finalTargetUrl,
                        platform = if (directTrackUrl != null) targetPlatformKey else "${targetPlatformKey}_search",
                        title = trackInfo ?: songLinkData.title.ifEmpty { null },
                        artist = songLinkData.artist.ifEmpty { null },
                        isAlbum = false,
                        nativeAppUri = nativeUri,
                        thumbnailUrl = songLinkData.thumbnailUrl
                    )
                    cache.put(canonicalUrl, targetPlatformKey, result, now, isHistory = !isPrefetch)
                    pingCacheIngestAsync(
                        originalUrl = canonicalUrl,
                        targetUrl = finalTargetUrl,
                        targetPlatform = result.platform,
                        title = result.title,
                        artist = result.artist,
                        isAlbum = false,
                        links = songLinkData.links
                    )
                    return result
                }

                val directUrl = songLinkData.links[targetPlatformKey]
                    ?: if (targetPlatformKey == "youtubeMusic") songLinkData.links["youtube"] else null

                val isAlbum = if (isExplicitTrackUrl) false else (songLinkData.isAlbum || isExplicitAlbumUrl)

                if (!directUrl.isNullOrEmpty()) {
                    val isAlbumPlaylist = directUrl.contains("playlist?list=") || directUrl.contains("/playlist/") || directUrl.contains("/album/") || directUrl.contains("/albums/")
                    if (isExplicitTrackUrl && isAlbumPlaylist) {
                        val trackQuery = if (songLinkData.artist.isNotEmpty() && !songLinkData.title.contains(songLinkData.artist, ignoreCase = true)) {
                            "${songLinkData.artist} ${songLinkData.title}"
                        } else {
                            songLinkData.title
                        }
                        val resolvedDirectUrl = resolveDirectPlatformUrl(trackQuery, targetPlatformKey, isAlbum = false)
                        if (resolvedDirectUrl != null) {
                            val nativeUri = UrlUtils.toNativeAppUri(resolvedDirectUrl, targetPlatformKey)
                            val result = ResolutionResult.Success(
                                targetUrl = resolvedDirectUrl,
                                platform = targetPlatformKey,
                                title = songLinkData.title.ifEmpty { null },
                                artist = songLinkData.artist.ifEmpty { null },
                                isAlbum = false,
                                nativeAppUri = nativeUri,
                                thumbnailUrl = songLinkData.thumbnailUrl
                            )
                            cache.put(canonicalUrl, targetPlatformKey, result, now, isHistory = !isPrefetch)
                            pingCacheIngestAsync(
                                originalUrl = canonicalUrl,
                                targetUrl = resolvedDirectUrl,
                                targetPlatform = targetPlatformKey,
                                title = songLinkData.title.ifEmpty { null },
                                artist = songLinkData.artist.ifEmpty { null },
                                isAlbum = false,
                                links = songLinkData.links
                            )
                            return result
                        }
                    }

                    val formatted = UrlUtils.formatTargetUrl(directUrl, targetPlatformKey)
                    val nativeUri = UrlUtils.toNativeAppUri(formatted, targetPlatformKey)
                    val result = ResolutionResult.Success(
                        targetUrl = formatted,
                        platform = targetPlatformKey,
                        title = songLinkData.title.ifEmpty { null },
                        artist = songLinkData.artist.ifEmpty { null },
                        isAlbum = isAlbum,
                        nativeAppUri = nativeUri,
                        thumbnailUrl = songLinkData.thumbnailUrl
                    )
                    cache.put(canonicalUrl, targetPlatformKey, result, now, isHistory = !isPrefetch)
                    pingCacheIngestAsync(
                        originalUrl = canonicalUrl,
                        targetUrl = formatted,
                        targetPlatform = targetPlatformKey,
                        title = songLinkData.title.ifEmpty { null },
                        artist = songLinkData.artist.ifEmpty { null },
                        isAlbum = isAlbum,
                        links = songLinkData.links
                    )
                    return result
                }

                if (songLinkData.title.isNotEmpty()) {
                    val rawQuery = if (songLinkData.artist.isNotEmpty() && !songLinkData.title.contains(songLinkData.artist, ignoreCase = true)) {
                        "${songLinkData.artist} ${songLinkData.title}"
                    } else {
                        songLinkData.title
                    }
                    val cleanQuery = UrlUtils.cleanSearchQuery(rawQuery)

                    val resolvedDirectUrl = resolveDirectPlatformUrl(
                        query = cleanQuery,
                        targetPlatformKey = targetPlatformKey,
                        isAlbum = isAlbum
                    ) ?: if (cleanQuery != rawQuery) {
                        resolveDirectPlatformUrl(
                            query = rawQuery,
                            targetPlatformKey = targetPlatformKey,
                            isAlbum = isAlbum
                        )
                    } else null

                    val finalTargetUrl = resolvedDirectUrl ?: UrlUtils.buildSearchUrl(cleanQuery, targetPlatformKey)
                    val nativeUri = UrlUtils.toNativeAppUri(finalTargetUrl, targetPlatformKey)
                    val result = ResolutionResult.Success(
                        targetUrl = finalTargetUrl,
                        platform = if (resolvedDirectUrl != null) targetPlatformKey else "${targetPlatformKey}_search",
                        title = songLinkData.title.ifEmpty { null },
                        artist = songLinkData.artist.ifEmpty { null },
                        isAlbum = isAlbum,
                        nativeAppUri = nativeUri,
                        thumbnailUrl = songLinkData.thumbnailUrl
                    )
                    cache.put(canonicalUrl, targetPlatformKey, result, now, isHistory = !isPrefetch)
                    pingCacheIngestAsync(
                        originalUrl = canonicalUrl,
                        targetUrl = finalTargetUrl,
                        targetPlatform = targetPlatformKey,
                        title = songLinkData.title.ifEmpty { null },
                        artist = songLinkData.artist.ifEmpty { null },
                        isAlbum = isAlbum,
                        links = songLinkData.links
                    )
                    return result
                }
            }

            // 6. Fallback Metadata Extraction via OEmbed / Public APIs
            if (trackInfo != null && trackInfo.isNotBlank()) {
                val cleanTrack = UrlUtils.cleanSearchQuery(trackInfo)
                val resolvedDirectUrl = resolveDirectPlatformUrl(
                    query = cleanTrack,
                    targetPlatformKey = targetPlatformKey,
                    isAlbum = isExplicitAlbumUrl
                ) ?: if (cleanTrack != trackInfo) {
                    resolveDirectPlatformUrl(
                        query = trackInfo,
                        targetPlatformKey = targetPlatformKey,
                        isAlbum = isExplicitAlbumUrl
                    )
                } else null

                val targetUrl = resolvedDirectUrl ?: UrlUtils.buildSearchUrl(cleanTrack, targetPlatformKey)
                val platform = if (resolvedDirectUrl != null) targetPlatformKey else "${targetPlatformKey}_search"
                val nativeUri = UrlUtils.toNativeAppUri(targetUrl, targetPlatformKey)
                val result = ResolutionResult.Success(
                    targetUrl = targetUrl,
                    platform = platform,
                    title = trackInfo,
                    artist = null,
                    isAlbum = isExplicitAlbumUrl,
                    nativeAppUri = nativeUri
                )
                cache.put(canonicalUrl, targetPlatformKey, result, now, isHistory = !isPrefetch)
                pingCacheIngestAsync(
                    originalUrl = canonicalUrl,
                    targetUrl = targetUrl,
                    targetPlatform = platform,
                    title = trackInfo,
                    artist = null,
                    isAlbum = isExplicitAlbumUrl
                )
                return result
            }

            // 7. Fallback: Artist Page Detection
            val artistInfo = extractArtistInfo(canonicalUrl)
            if (artistInfo != null && artistInfo.isNotBlank()) {
                val directArtistUrl = resolveDirectArtistUrl(artistInfo, targetPlatformKey)
                val finalTargetUrl = directArtistUrl ?: UrlUtils.buildSearchUrl(artistInfo, targetPlatformKey)
                val nativeUri = UrlUtils.toNativeAppUri(finalTargetUrl, targetPlatformKey)
                val result = ResolutionResult.Success(
                    targetUrl = finalTargetUrl,
                    platform = if (directArtistUrl != null) targetPlatformKey else "${targetPlatformKey}_artist",
                    title = null,
                    artist = artistInfo,
                    isAlbum = false,
                    nativeAppUri = nativeUri
                )
                cache.put(canonicalUrl, targetPlatformKey, result, now, isHistory = !isPrefetch)
                pingCacheIngestAsync(
                    originalUrl = canonicalUrl,
                    targetUrl = finalTargetUrl,
                    targetPlatform = result.platform,
                    title = null,
                    artist = artistInfo,
                    isAlbum = false
                )
                return result
            }

            // 8. Fallback: Playlist Search Routing
            val playlistInfo = extractPlaylistInfo(canonicalUrl)
            if (playlistInfo != null && playlistInfo.isNotBlank()) {
                val searchUrl = UrlUtils.buildSearchUrl(playlistInfo, targetPlatformKey)
                val nativeUri = UrlUtils.toNativeAppUri(searchUrl, targetPlatformKey)
                return ResolutionResult.Success(
                    targetUrl = searchUrl,
                    platform = "${targetPlatformKey}_playlist",
                    title = playlistInfo,
                    artist = null,
                    isAlbum = false,
                    nativeAppUri = nativeUri
                )
            }

            return ResolutionResult.Error("Could not resolve music link", errorReason = "no_match_found")
        } catch (e: Exception) {
            if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) {
                throw e
            }
            val cleanUrl = UrlUtils.extractCleanUrl(inputUrl) ?: inputUrl
            val isExplicitAlbumUrl = UrlUtils.isAlbumUrl(cleanUrl)

            val trackInfo = extractTrackInfo(cleanUrl)
            if (trackInfo != null) {
                val resolved = resolveDirectPlatformUrl(trackInfo, targetPlatformKey, isExplicitAlbumUrl)
                    ?: UrlUtils.buildSearchUrl(trackInfo, targetPlatformKey)
                val nativeUri = UrlUtils.toNativeAppUri(resolved, targetPlatformKey)
                return ResolutionResult.Success(
                    targetUrl = resolved,
                    platform = "${targetPlatformKey}_fallback",
                    title = trackInfo,
                    artist = null,
                    isAlbum = isExplicitAlbumUrl,
                    nativeAppUri = nativeUri
                )
            }

            val errorReason = if (e is io.ktor.client.plugins.HttpRequestTimeoutException || e is kotlinx.coroutines.TimeoutCancellationException) {
                "timeout"
            } else {
                "network_error"
            }
            return ResolutionResult.Error(e.message ?: "Unknown network error", errorReason = errorReason)
        }
    }

    private data class SongLinkData(
        val title: String,
        val artist: String,
        val type: String,
        val links: Map<String, String>,
        val thumbnailUrl: String? = null
    ) {
        val isAlbum: Boolean
            get() = type.equals("album", ignoreCase = true) || type.equals("ep", ignoreCase = true)
    }

    private suspend fun fetchSongLinkData(url: String): SongLinkData? {
        return try {
            val targetSongLink = UrlUtils.normalizeToSongLinkDirectUrl(url)
            val resp = client.get(targetSongLink) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }
            if (!resp.status.isSuccess()) return null

            val html = resp.bodyAsText()
            val scriptTag = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
            if (!html.contains(scriptTag)) return null

            val jsonString = html.substringAfter(scriptTag).substringBefore("</script>")
            val rootObj = json.parseToJsonElement(jsonString).jsonObject
            val pageProps = rootObj["props"]?.jsonObject?.get("pageProps")?.jsonObject ?: return null
            val pageData = pageProps["pageData"]?.jsonObject ?: return null

            val pageId = pageData["pageId"]?.jsonPrimitive?.content ?: ""
            val entityUniqueId = pageData["entityUniqueId"]?.jsonPrimitive?.content ?: ""
            val isAlbumEntity = pageId.contains("|album|") || entityUniqueId.contains("|album|")

            val entityData = pageData["entityData"]?.jsonObject
            var title = entityData?.get("title")?.jsonPrimitive?.content ?: ""
            var artist = entityData?.get("artistName")?.jsonPrimitive?.content ?: ""
            val entityType = entityData?.get("type")?.jsonPrimitive?.content ?: (if (isAlbumEntity) "album" else "")
            var thumbnailUrl = entityData?.get("thumbnailUrl")?.jsonPrimitive?.content

            val sections = pageData["sections"]?.jsonArray
            if (sections != null && sections.isNotEmpty()) {
                val firstSection = sections[0].jsonObject
                if (title.isEmpty()) {
                    title = firstSection["title"]?.jsonPrimitive?.content ?: ""
                }
                if (artist.isEmpty()) {
                    artist = firstSection["artistName"]?.jsonPrimitive?.content ?: ""
                }
                if (thumbnailUrl.isNullOrBlank()) {
                    thumbnailUrl = firstSection["thumbnailUrl"]?.jsonPrimitive?.content
                }
            }

            val linksMap = mutableMapOf<String, String>()
            if (sections != null) {
                for (sectionElem in sections) {
                    val section = sectionElem.jsonObject
                    val links = section["links"]?.jsonArray ?: continue
                    for (linkElem in links) {
                        val linkObj = linkElem.jsonObject
                        val platform = linkObj["platform"]?.jsonPrimitive?.content ?: ""
                        val linkUrl = linkObj["url"]?.jsonPrimitive?.content ?: ""
                        if (platform.isNotEmpty() && linkUrl.isNotEmpty()) {
                            linksMap[platform] = linkUrl
                        }
                    }
                }
            }

            SongLinkData(title = title, artist = artist, type = entityType, links = linksMap, thumbnailUrl = thumbnailUrl)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun resolveDirectPlatformUrl(query: String, targetPlatformKey: String, isAlbum: Boolean = false): String? {
        val cleanQuery = UrlUtils.cleanSearchQuery(query)
        val resolver: PlatformResolver? = when (targetPlatformKey) {
            "youtubeMusic" -> youTubeMusicResolver
            "appleMusic" -> appleMusicResolver
            "deezer" -> deezerResolver
            "tidal" -> tidalResolver
            "spotify" -> spotifyResolver
            else -> null
        }
        val directMatch = if (isAlbum) {
            resolver?.resolveAlbum(cleanQuery)
        } else {
            resolver?.resolveTrack(cleanQuery)
        }
        return directMatch ?: UrlUtils.buildSearchUrl(cleanQuery, targetPlatformKey)
    }

    private suspend fun resolveDirectArtistUrl(artistName: String, targetPlatformKey: String): String? {
        return when (targetPlatformKey) {
            "deezer" -> deezerResolver.resolveArtist(artistName)
            "appleMusic" -> appleMusicResolver.resolveArtist(artistName)
            "youtubeMusic" -> youTubeMusicResolver.resolveArtist(artistName)
            "tidal" -> tidalResolver.resolveArtist(artistName)
            else -> null
        }
    }

    suspend fun extractTrackInfo(url: String): String? {
        return try {
            if (url.contains("spotify.com")) {
                spotifyResolver.extractMetadata(url)
            } else if (url.contains("apple.com")) {
                appleMusicResolver.extractMetadata(url)
            } else if (url.contains("youtube.com") || url.contains("youtu.be")) {
                val encoded = url.encodeURLParameter()
                val resp = client.get("https://www.youtube.com/oembed?url=$encoded&format=json")
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content ?: ""
                    val author = root["author_name"]?.jsonPrimitive?.content ?: ""
                    if (title.isNotEmpty()) {
                        return if (author.isNotEmpty() && !title.contains(author, ignoreCase = true)) "$author $title" else title
                    }
                }
                null
            } else if (url.contains("deezer.com")) {
                val encoded = url.encodeURLParameter()
                val resp = client.get("https://api.deezer.com/oembed?url=$encoded")
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content
                    if (!title.isNullOrEmpty()) return title
                }
                null
            } else if (url.contains("soundcloud.com")) {
                val encoded = url.encodeURLParameter()
                val resp = client.get("https://soundcloud.com/oembed?url=$encoded&format=json")
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content ?: ""
                    val author = root["author_name"]?.jsonPrimitive?.content ?: ""
                    val cleanTitle = if (author.isNotEmpty() && title.endsWith(" by $author", ignoreCase = true)) {
                        title.substring(0, title.length - " by $author".length).trim()
                    } else {
                        title
                    }
                    if (cleanTitle.isNotEmpty()) {
                        return if (author.isNotEmpty() && !cleanTitle.contains(author, ignoreCase = true)) "$author $cleanTitle" else cleanTitle
                    }
                }
                null
            } else if (url.contains("bandcamp.com")) {
                val resp = client.get(url)
                if (resp.status.isSuccess()) {
                    val html = resp.bodyAsText()
                    val ogTitleMatch = Regex("<meta\\s+property=[\"']og:title[\"']\\s+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("<meta\\s+content=[\"']([^\"']+)[\"']\\s+property=[\"']og:title[\"']", RegexOption.IGNORE_CASE).find(html)
                    if (ogTitleMatch != null) {
                        val ogTitle = ogTitleMatch.groupValues[1]
                        if (ogTitle.contains(", by ")) {
                            val track = ogTitle.substringBefore(", by ").trim()
                            val artist = ogTitle.substringAfter(", by ").trim()
                            return "$artist $track"
                        }
                        return ogTitle
                    }
                }
                null
            } else if (url.contains("shazam.com")) {
                val trackMatch = Regex("shazam\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?track/([0-9]+)", RegexOption.IGNORE_CASE).find(url)
                val trackId = trackMatch?.groupValues?.get(1) ?: url.substringAfter("/track/").substringBefore("/").substringBefore("?").trim().takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }
                if (!trackId.isNullOrEmpty()) {
                    val resp = client.get("https://amp.shazam.com/discovery/v5/en-US/US/web/-/track/$trackId") {
                        header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1")
                    }
                    if (resp.status.isSuccess()) {
                        val body = resp.bodyAsText()
                        val root = json.parseToJsonElement(body).jsonObject
                        val title = root["title"]?.jsonPrimitive?.content?.trim()
                        val artist = root["subtitle"]?.jsonPrimitive?.content?.trim()
                        if (!title.isNullOrEmpty() && !artist.isNullOrEmpty()) {
                            return "$artist $title"
                        }
                    }
                }
                null
            } else {
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun extractArtistInfo(url: String): String? {
        return try {
            if (url.contains("deezer.com") && url.contains("/artist/")) {
                val artistId = url.substringAfter("/artist/").substringBefore("?").substringBefore("/").trim()
                if (artistId.isNotEmpty()) {
                    val resp = client.get("https://api.deezer.com/artist/$artistId")
                    if (resp.status.isSuccess()) {
                        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                        val name = root["name"]?.jsonPrimitive?.content
                        if (!name.isNullOrEmpty()) return name
                    }
                }
            } else if (url.contains("spotify.com") && url.contains("/artist/")) {
                val encoded = url.encodeURLParameter()
                val resp = client.get("https://open.spotify.com/oembed?url=$encoded")
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content
                    if (!title.isNullOrEmpty()) return title
                }
            } else if (url.contains("apple.com") && url.contains("/artist/")) {
                val artistId = url.substringAfterLast("/").substringBefore("?").substringBefore("&").trim()
                if (artistId.isNotEmpty() && artistId.all { it.isDigit() }) {
                    val resp = client.get("https://itunes.apple.com/lookup?id=$artistId")
                    if (resp.status.isSuccess()) {
                        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                        val results = root["results"]?.jsonArray
                        if (results != null && results.isNotEmpty()) {
                            val artistObj = results[0].jsonObject
                            val artistName = artistObj["artistName"]?.jsonPrimitive?.content
                            if (!artistName.isNullOrEmpty()) return artistName
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun extractPlaylistInfo(url: String): String? {
        return try {
            if (url.contains("deezer.com") && url.contains("/playlist/")) {
                val playlistId = url.substringAfter("/playlist/").substringBefore("?").substringBefore("/").trim()
                if (playlistId.isNotEmpty()) {
                    val resp = client.get("https://api.deezer.com/playlist/$playlistId")
                    if (resp.status.isSuccess()) {
                        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                        val title = root["title"]?.jsonPrimitive?.content
                        if (!title.isNullOrEmpty()) return title
                    }
                }
            } else if (url.contains("spotify.com") && url.contains("/playlist/")) {
                val encoded = url.encodeURLParameter()
                val resp = client.get("https://open.spotify.com/oembed?url=$encoded")
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content
                    if (!title.isNullOrEmpty()) return title
                }
            }
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun resolveShazamToAppleMusic(url: String): String? {
        return try {
            val trackMatch = Regex("shazam\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?track/([0-9]+)", RegexOption.IGNORE_CASE).find(url)
            val trackId = trackMatch?.groupValues?.get(1) ?: url.substringAfter("/track/").substringBefore("/").substringBefore("?").trim().takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }
            if (trackId.isNullOrEmpty()) return null

            val resp = client.get("https://amp.shazam.com/discovery/v5/en-US/US/web/-/track/$trackId") {
                header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1")
            }
            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val root = json.parseToJsonElement(body).jsonObject
                val hub = root["hub"]?.jsonObject
                val actions = hub?.get("actions")?.jsonArray
                if (actions != null) {
                    for (actEl in actions) {
                        val act = actEl.jsonObject
                        if (act["name"]?.jsonPrimitive?.content == "apple" && act["type"]?.jsonPrimitive?.content == "applemusicplay") {
                            val appleId = act["id"]?.jsonPrimitive?.content
                            if (!appleId.isNullOrBlank()) {
                                return "https://music.apple.com/song/$appleId"
                            }
                        }
                    }
                }
                val fallbackId = root["trackadamid"]?.jsonPrimitive?.content
                if (!fallbackId.isNullOrBlank()) {
                    return "https://music.apple.com/song/$fallbackId"
                }
            }
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun resolveCanonicalUrl(url: String): String {
        if (url.contains("shazam.com")) {
            val appleUrl = resolveShazamToAppleMusic(url)
            if (appleUrl != null) return appleUrl
        }

        return try {
            val resp = client.head(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
            val location = resp.headers["Location"]
            if (!location.isNullOrBlank()) {
                location
            } else {
                val getResp = client.get(url) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                getResp.request.url.toString()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            try {
                val getResp = client.get(url) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                getResp.request.url.toString()
            } catch (inner: Exception) {
                if (inner is CancellationException) throw inner
                url
            }
        }
    }

    private suspend fun queryCustomApi(apiUrl: String, token: String, url: String, targetPlatform: String): String? {
        return try {
            val payload = "{\"url\":\"$url\",\"targetPlatform\":\"$targetPlatform\"}"
            val resp = client.post(apiUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
                if (token.isNotBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            if (resp.status.isSuccess()) {
                val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                val targetUrl = root["targetUrl"]?.jsonPrimitive?.content
                    ?: root["url"]?.jsonPrimitive?.content
                if (!targetUrl.isNullOrEmpty()) {
                    return targetUrl
                }
            }
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun resolvePodcast(
        canonicalUrl: String,
        targetPlatformKey: String,
        isPrefetch: Boolean = false,
        forceRefresh: Boolean = false
    ): ResolutionResult.Podcast {
        val now = getCurrentTimeMillis()
        if (!forceRefresh) {
            val cached = cache.get(canonicalUrl, targetPlatformKey, now)
            if (cached != null) {
                if (!isPrefetch) {
                    cache.markAsHistory(canonicalUrl, targetPlatformKey, now)
                }
                return ResolutionResult.Podcast(
                    originalUrl = canonicalUrl,
                    targetUrl = cached.targetUrl,
                    platform = cached.platform,
                    showTitle = cached.artist ?: cached.title ?: "Podcast",
                    episodeTitle = if (cached.artist != null) cached.title else null,
                    nativeAppUri = cached.nativeAppUri,
                    thumbnailUrl = cached.thumbnailUrl,
                    isEpisode = cached.artist != null,
                    isDeepSearch = true
                )
            }
        }

        // 1. Try Backend /resolve for high-accuracy metadata & direct links
        val encodedUrl = canonicalUrl.encodeURLParameter()
        val refreshParam = if (forceRefresh) "&force_refresh=true" else ""
        val endpoints = listOf(
            "https://cache.songflip.link/resolve?url=$encodedUrl&platform=$targetPlatformKey$refreshParam",
            "https://songflip-web.web.app/resolve?url=$encodedUrl&platform=$targetPlatformKey$refreshParam"
        )

        for (endpoint in endpoints) {
            try {
                val resp = client.get(endpoint) {
                    header("Accept", "application/json")
                }
                if (resp.status.isSuccess()) {
                    val body = resp.bodyAsText()
                    val root = json.parseToJsonElement(body).jsonObject
                    if (root["status"]?.jsonPrimitive?.content == "success" && (root["isPodcast"]?.jsonPrimitive?.booleanOrNull == true || root["entityType"]?.jsonPrimitive?.content == "podcast")) {
                        val item = root["item"]?.jsonObject
                        if (item != null) {
                            val showTitle = item["showTitle"]?.jsonPrimitive?.content?.ifBlank { null } ?: "Podcast"
                            val episodeTitle = item["episodeTitle"]?.jsonPrimitive?.content?.ifBlank { null }
                            val thumbnailUrl = item["thumbnailUrl"]?.jsonPrimitive?.content?.ifBlank { null }
                            val isEpisode = item["isEpisode"]?.jsonPrimitive?.booleanOrNull ?: (episodeTitle != null)
                            val queryText = if (episodeTitle != null) {
                                if (showTitle.equals("Podcast", ignoreCase = true) || episodeTitle.contains(showTitle, ignoreCase = true)) {
                                    episodeTitle
                                } else {
                                    "$showTitle $episodeTitle"
                                }
                            } else {
                                showTitle
                            }
                            val targetUrl = item["targetUrl"]?.jsonPrimitive?.content
                                ?: UrlUtils.buildPodcastSearchUrl(queryText, targetPlatformKey)
                            val nativeAppUri = item["nativeAppUri"]?.jsonPrimitive?.content
                                ?: UrlUtils.toPodcastNativeAppUri(targetPlatformKey, queryText)

                            val result = ResolutionResult.Podcast(
                                originalUrl = canonicalUrl,
                                targetUrl = targetUrl,
                                platform = targetPlatformKey,
                                showTitle = showTitle,
                                episodeTitle = episodeTitle,
                                nativeAppUri = nativeAppUri,
                                thumbnailUrl = thumbnailUrl,
                                isEpisode = isEpisode,
                                isDeepSearch = true
                            )
                            cache.putPodcast(canonicalUrl, targetPlatformKey, result, now, isHistory = !isPrefetch)
                            return result
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }

        // 2. Direct Client-Side Spotify Scraping (Bot UA + oEmbed Fallback)
        if (canonicalUrl.contains("spotify.com") || canonicalUrl.startsWith("spotify:")) {
            try {
                val sResp = client.get(canonicalUrl) {
                    header("User-Agent", "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")
                }
                if (sResp.status.isSuccess()) {
                    val html = sResp.bodyAsText()
                    val titleMatch = Regex("<meta\\s+(?:property|name)=[\"'](?:og:title|twitter:title)[\"']\\s+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("<meta\\s+content=[\"']([^\"']+)[\"']\\s+(?:property|name)=[\"'](?:og:title|twitter:title)[\"']", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("<title>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)
                    val rawTitle = titleMatch?.groupValues?.get(1)
                        ?.replace(Regex("\\s*\\|\\s*Podcast on Spotify", RegexOption.IGNORE_CASE), "")
                        ?.replace(Regex("\\s*\\|\\s*Spotify", RegexOption.IGNORE_CASE), "")
                        ?.trim()
                    if (!rawTitle.isNullOrBlank() && !rawTitle.contains("Spotify – Web Player")) {
                        val isEp = canonicalUrl.contains("/episode/") || canonicalUrl.startsWith("spotify:episode:")
                        val targetUrl = UrlUtils.buildPodcastSearchUrl(rawTitle, targetPlatformKey)
                        val nativeUri = UrlUtils.toPodcastNativeAppUri(targetPlatformKey, rawTitle)
                        val sResult = ResolutionResult.Podcast(
                            originalUrl = canonicalUrl,
                            targetUrl = targetUrl,
                            platform = targetPlatformKey,
                            showTitle = rawTitle,
                            episodeTitle = if (isEp) rawTitle else null,
                            nativeAppUri = nativeUri,
                            thumbnailUrl = null,
                            isEpisode = isEp,
                            isDeepSearch = true
                        )
                        cache.putPodcast(canonicalUrl, targetPlatformKey, sResult, now, isHistory = !isPrefetch)
                        return sResult
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }

            try {
                val oembedUrl = "https://open.spotify.com/oembed?url=${canonicalUrl.encodeURLParameter()}"
                val oResp = client.get(oembedUrl)
                if (oResp.status.isSuccess()) {
                    val oBody = oResp.bodyAsText()
                    val oRoot = json.parseToJsonElement(oBody).jsonObject
                    val title = oRoot["title"]?.jsonPrimitive?.content?.ifBlank { null }
                    val thumb = oRoot["thumbnail_url"]?.jsonPrimitive?.content?.ifBlank { null }
                    if (title != null) {
                        val isEp = canonicalUrl.contains("/episode/") || canonicalUrl.startsWith("spotify:episode:")
                        val targetUrl = UrlUtils.buildPodcastSearchUrl(title, targetPlatformKey)
                        val nativeUri = UrlUtils.toPodcastNativeAppUri(targetPlatformKey, title)
                        val oResult = ResolutionResult.Podcast(
                            originalUrl = canonicalUrl,
                            targetUrl = targetUrl,
                            platform = targetPlatformKey,
                            showTitle = title,
                            episodeTitle = if (isEp) title else null,
                            nativeAppUri = nativeUri,
                            thumbnailUrl = thumb,
                            isEpisode = isEp,
                            isDeepSearch = true
                        )
                        cache.putPodcast(canonicalUrl, targetPlatformKey, oResult, now, isHistory = !isPrefetch)
                        return oResult
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }

        // 3. Offline Fallback: Extract Show / Episode slug and generate Deep-Search Intent
        val fallbackQuery = extractPodcastFallbackQuery(canonicalUrl)
        val targetUrl = UrlUtils.buildPodcastSearchUrl(fallbackQuery, targetPlatformKey)
        val nativeUri = UrlUtils.toPodcastNativeAppUri(targetPlatformKey, fallbackQuery)
        val fallbackResult = ResolutionResult.Podcast(
            originalUrl = canonicalUrl,
            targetUrl = targetUrl,
            platform = targetPlatformKey,
            showTitle = fallbackQuery,
            episodeTitle = null,
            nativeAppUri = nativeUri,
            thumbnailUrl = null,
            isEpisode = false,
            isDeepSearch = true
        )
        cache.putPodcast(canonicalUrl, targetPlatformKey, fallbackResult, now, isHistory = !isPrefetch)
        return fallbackResult
    }

    private fun extractPodcastFallbackQuery(url: String): String {
        return try {
            val clean = url.substringBefore("?").substringBefore("#").trimEnd('/')
            val rawSlug = clean.substringAfterLast("/")
            // If slug looks like a raw alphanumeric Base62 ID (e.g. 22 chars without hyphens/spaces like 7BTOsF2boKmlYr76BelijW)
            if (rawSlug.length in 18..30 && rawSlug.matches(Regex("""^[a-zA-Z0-9]+$"""))) {
                return "Podcast"
            }
            val slug = rawSlug
                .replace("-", " ")
                .replace("_", " ")
                .replace(Regex("""\bid\d+\b""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (slug.isNotBlank() && slug.length > 2 && !slug.all { it.isDigit() }) {
                slug
            } else {
                "Podcast"
            }
        } catch (_: Exception) {
            "Podcast"
        }
    }
}
