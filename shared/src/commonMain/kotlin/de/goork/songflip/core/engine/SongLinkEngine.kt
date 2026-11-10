package de.goork.songflip.core.engine

import de.goork.songflip.core.analytics.getSystemCountryCode
import de.goork.songflip.core.cache.LinkCache
import de.goork.songflip.core.cache.createDefaultCacheStorage
import de.goork.songflip.core.engine.resolvers.AppleMusicResolver
import de.goork.songflip.core.engine.resolvers.AudiomackResolver
import de.goork.songflip.core.engine.resolvers.BeatportResolver
import de.goork.songflip.core.engine.resolvers.DeezerResolver
import de.goork.songflip.core.engine.resolvers.IdagioResolver
import de.goork.songflip.core.engine.resolvers.PlatformResolver
import de.goork.songflip.core.engine.resolvers.QobuzResolver
import de.goork.songflip.core.engine.resolvers.SongLinkApiResolver
import de.goork.songflip.core.engine.resolvers.SpotifyResolver
import de.goork.songflip.core.engine.resolvers.TidalResolver
import de.goork.songflip.core.engine.resolvers.YouTubeMusicResolver
import de.goork.songflip.core.model.MusicPlatform
import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.core.util.SongFlipLogger
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import de.goork.songflip.core.util.PlatformLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
        private const val TIDAL_CLIENT_APP_TOKEN = "REPLACED_SECRET"
        private val AUDIOPHILE_PLATFORMS = setOf("tidal", "qobuz", "appleMusic", "deezer")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Platform Resolver Strategies
    val appleMusicResolver = AppleMusicResolver(client, json)
    val deezerResolver = DeezerResolver(client, json)
    val tidalResolver = TidalResolver(client, json, TIDAL_CLIENT_APP_TOKEN, countryCodeProvider = { getSystemCountryCode() })
    val youTubeMusicResolver = YouTubeMusicResolver(client)
    val spotifyResolver = SpotifyResolver(client, json)
    val songLinkApiResolver = SongLinkApiResolver(client, json)
    val qobuzResolver = QobuzResolver(client, json)
    val beatportResolver = BeatportResolver(client)
    val audiomackResolver = AudiomackResolver(client)
    val idagioResolver = IdagioResolver(client)
    val anghamiResolver = de.goork.songflip.core.engine.resolvers.AnghamiResolver(client)
    val soundCloudResolver = de.goork.songflip.core.engine.resolvers.SoundCloudResolver(client)

    val metadataExtractor = MetadataExtractor(
        client = client,
        json = json,
        appleMusicResolver = appleMusicResolver,
        deezerResolver = deezerResolver,
        youTubeMusicResolver = youTubeMusicResolver,
        spotifyResolver = spotifyResolver,
        qobuzResolver = qobuzResolver,
        beatportResolver = beatportResolver,
        audiomackResolver = audiomackResolver,
        idagioResolver = idagioResolver,
        anghamiResolver = anghamiResolver
    )

    val shortLinkResolver = ShortLinkResolver(client) { metadataExtractor.resolveShazamToAppleMusic(it) }

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val l2CacheClient = L2CacheClient(client, json, backgroundScope)

    val podcastResolver = PodcastResolver(client, json, cache)

    private fun cacheResolvedShortLink(sourceUrl: String, resolvedUrl: String): String =
        shortLinkResolver.cacheResolvedShortLink(sourceUrl, resolvedUrl)

    private fun pingCacheIngestAsync(
        originalUrl: String,
        targetUrl: String,
        targetPlatform: String,
        title: String?,
        artist: String?,
        isAlbum: Boolean,
        links: Map<String, String>? = null
    ) = l2CacheClient.pingCacheIngestAsync(
        originalUrl = originalUrl,
        targetUrl = targetUrl,
        targetPlatform = targetPlatform,
        title = title,
        artist = artist,
        isAlbum = isAlbum,
        links = links
    )

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
        authToken: String = "",
        audiophileMode: Boolean = false
    ): ResolutionResult {
        return resolveTargetUrl(
            inputUrl = inputUrl,
            targetPlatformKey = targetPlatformKey,
            customApiUrl = customApiUrl,
            customApiToken = customApiToken,
            isPro = isPro,
            authToken = authToken,
            isPrefetch = false,
            forceRefresh = true,
            audiophileMode = audiophileMode
        )
    }

    suspend fun resolveTargetUrl(
        inputUrl: String,
        targetPlatformKey: String,
        customApiUrl: String,
        customApiToken: String,
        isPro: Boolean,
        authToken: String,
        isPrefetch: Boolean,
        forceRefresh: Boolean
    ): ResolutionResult {
        return resolveTargetUrl(
            inputUrl = inputUrl,
            targetPlatformKey = targetPlatformKey,
            customApiUrl = customApiUrl,
            customApiToken = customApiToken,
            isPro = isPro,
            authToken = authToken,
            isPrefetch = isPrefetch,
            forceRefresh = forceRefresh,
            audiophileMode = false
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
    ): ResolutionResult.Success? = l2CacheClient.queryL2ServerCache(
        canonicalUrl = canonicalUrl,
        targetPlatformKey = targetPlatformKey,
        authToken = authToken,
        forceRefresh = forceRefresh
    ) { query, platform, isAlbum ->
        resolveDirectPlatformUrl(query, platform, isAlbum)
    }

    suspend fun resolveTargetUrl(
        inputUrl: String,
        targetPlatformKey: String,
        customApiUrl: String = "",
        customApiToken: String = "",
        isPro: Boolean = false,
        authToken: String = "",
        isPrefetch: Boolean = false,
        forceRefresh: Boolean = false,
        audiophileMode: Boolean = false
    ): ResolutionResult {
        val effectiveAudiophile = isPro && audiophileMode && targetPlatformKey in AUDIOPHILE_PLATFORMS
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

            // Fast-fail for foreign non-music domains (e.g. TikTok, Instagram Reels, generic websites) (#132)
            if (!UrlUtils.isSupportedMusicOrAggregatorUrl(canonicalUrl)) {
                return ResolutionResult.UnsupportedEntity(
                    originalUrl = canonicalUrl,
                    platform = "unknown",
                    entityType = "unsupported_domain",
                    message = "UNSUPPORTED_DOMAIN"
                )
            }

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

            // Spotify Pre-Releases (Unreleased albums/singles with countdown)
            if (UrlUtils.isPreReleaseUrl(canonicalUrl)) {
                if (targetPlatformKey == "spotify") {
                    val nativeUri = UrlUtils.toNativeSpotifyUri(canonicalUrl)
                    return ResolutionResult.Success(
                        targetUrl = canonicalUrl,
                        platform = "spotify",
                        isAlbum = true,
                        nativeAppUri = nativeUri
                    )
                }
                val info = spotifyResolver.extractPreReleaseInfo(canonicalUrl)
                val title = info.title
                val artist = info.artist
                val query = listOfNotNull(artist, title).joinToString(" ").ifBlank { "music" }
                val targetSearchUrl = UrlUtils.buildSearchUrl(query, targetPlatformKey)
                val nativeSpotifyUri = UrlUtils.toNativeSpotifyUri(canonicalUrl)
                return ResolutionResult.PreRelease(
                    originalUrl = canonicalUrl,
                    platform = targetPlatformKey,
                    title = title,
                    artist = artist,
                    targetSearchUrl = targetSearchUrl,
                    nativeSpotifyUri = nativeSpotifyUri,
                    message = "PRE_RELEASE_NOT_YET_AVAILABLE"
                )
            }

            val isExplicitTrackUrl = UrlUtils.hasAppleMusicTrackId(canonicalUrl) || canonicalUrl.contains("/song/") || canonicalUrl.contains("/track/")
            val isExplicitAlbumUrl = !isExplicitTrackUrl && UrlUtils.isAlbumUrl(canonicalUrl)
            val now = getCurrentTimeMillis()

            if (!forceRefresh) {
                // 4. L1 Cache Lookup (< 5ms). On forceRefresh we skip the cache read to resolve freshly
                // from upstream, while preserving existing history and favorite flags (#171).
                val cached = cache.get(canonicalUrl, targetPlatformKey, now)
                if (cached != null) {
                    if (!isPrefetch) {
                        cache.markAsHistory(canonicalUrl, targetPlatformKey, now)
                    }
                    return cached.copy(isCacheHit = true)
                }
            }

            // 4.2. Search URL Resolution (Spotify, Apple Music, YouTube, Deezer, Tidal search links)
            val searchQuery = UrlUtils.extractSearchQuery(canonicalUrl)
            if (searchQuery != null) {
                val directUrl = resolveDirectPlatformUrl(searchQuery, targetPlatformKey, isAlbum = false, audiophileMode = effectiveAudiophile)
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
                return result
            }

            // 4.5. L2 Server Cache (PRO Feature - < 30ms; bypassed when audiophile prioritization is active so catalog scoring runs)
            if (isPro && authToken.isNotBlank() && !effectiveAudiophile) {
                val l2Result = queryL2ServerCache(canonicalUrl, targetPlatformKey, authToken, forceRefresh)
                if (l2Result != null) {
                    cache.put(canonicalUrl, targetPlatformKey, l2Result, now, isHistory = !isPrefetch)
                    return l2Result
                }
            }

            // 5. Parallel Multi-Source Resolution with global safety timeout (#196)
            val (songLinkData, trackInfo) = withContext(Dispatchers.Default) {
                withTimeoutOrNull(9_000L) {
                    supervisorScope {
                        val songLinkDeferred = async { fetchSongLinkData(canonicalUrl) }
                        val fallbackTrackDeferred = async { extractTrackInfo(canonicalUrl) }
                        val sld = try { songLinkDeferred.await() } catch (e: Exception) { if (e is CancellationException) throw e; null }
                        val ti = try { fallbackTrackDeferred.await() } catch (e: Exception) { if (e is CancellationException) throw e; null }
                        Pair(sld, ti)
                    }
                }
            } ?: Pair(null, null)

            if (songLinkData != null) {
                // If the user requested an explicit track (e.g. Apple Music with ?i=...), but song.link mapped it to the whole album:
                if (isExplicitTrackUrl && songLinkData.isAlbum) {
                    val resolvedQuery = trackInfo ?: if (songLinkData.artist.isNotEmpty() && !songLinkData.title.contains(songLinkData.artist, ignoreCase = true)) {
                        "${songLinkData.artist} ${songLinkData.title}"
                    } else {
                        songLinkData.title
                    }
                    val directTrackUrl = resolveDirectPlatformUrl(resolvedQuery, targetPlatformKey, isAlbum = false, audiophileMode = effectiveAudiophile)
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

                // Audiophile Quality Matcher (#85): When enabled for Pro users on Tidal/Qobuz/Apple Music/Deezer,
                // evaluate target catalog candidates to prefer Hi-Res / Lossless / Atmos studio masters over Radio Edits & Compilations.
                val audiophileUpgradedUrl = if (effectiveAudiophile && songLinkData.title.isNotEmpty()) {
                    val rawQuery = if (songLinkData.artist.isNotEmpty() && !songLinkData.title.contains(songLinkData.artist, ignoreCase = true)) {
                        "${songLinkData.artist} ${songLinkData.title}"
                    } else {
                        songLinkData.title
                    }
                    val cleanQuery = UrlUtils.cleanSearchQuery(rawQuery)
                    resolveDirectPlatformUrl(
                        query = cleanQuery,
                        targetPlatformKey = targetPlatformKey,
                        isAlbum = isAlbum,
                        audiophileMode = true
                    )
                } else null

                val effectiveDirectUrl = audiophileUpgradedUrl ?: directUrl

                if (!effectiveDirectUrl.isNullOrEmpty()) {
                    val isAlbumPlaylist = effectiveDirectUrl.contains("playlist?list=") || effectiveDirectUrl.contains("/playlist/") || effectiveDirectUrl.contains("/album/") || effectiveDirectUrl.contains("/albums/")
                    if (isExplicitTrackUrl && isAlbumPlaylist) {
                        val trackQuery = if (songLinkData.artist.isNotEmpty() && !songLinkData.title.contains(songLinkData.artist, ignoreCase = true)) {
                            "${songLinkData.artist} ${songLinkData.title}"
                        } else {
                            songLinkData.title
                        }
                        val resolvedDirectUrl = resolveDirectPlatformUrl(trackQuery, targetPlatformKey, isAlbum = false, audiophileMode = effectiveAudiophile)
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

                    val formatted = UrlUtils.formatTargetUrl(effectiveDirectUrl, targetPlatformKey)
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
                        isAlbum = isAlbum,
                        audiophileMode = effectiveAudiophile
                    ) ?: if (cleanQuery != rawQuery) {
                        resolveDirectPlatformUrl(
                            query = rawQuery,
                            targetPlatformKey = targetPlatformKey,
                            isAlbum = isAlbum,
                            audiophileMode = effectiveAudiophile
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
                        targetPlatform = result.platform,
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
                    isAlbum = isExplicitAlbumUrl,
                    audiophileMode = effectiveAudiophile
                ) ?: if (cleanTrack != trackInfo) {
                    resolveDirectPlatformUrl(
                        query = trackInfo,
                        targetPlatformKey = targetPlatformKey,
                        isAlbum = isExplicitAlbumUrl,
                        audiophileMode = effectiveAudiophile
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
                return result
            }

            // 7. Fallback: Artist Page Detection
            val artistInfo = metadataExtractor.extractArtistInfo(canonicalUrl)
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
                return result
            }

            // 8. Fallback: Playlist Search Routing
            val playlistInfo = metadataExtractor.extractPlaylistInfo(canonicalUrl)
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
            try {
                val cleanUrl = UrlUtils.extractCleanUrl(inputUrl) ?: inputUrl
                val isExplicitAlbumUrl = UrlUtils.isAlbumUrl(cleanUrl)

                val trackInfo = extractTrackInfo(cleanUrl)
                if (trackInfo != null) {
                    val resolved = resolveDirectPlatformUrl(trackInfo, targetPlatformKey, isExplicitAlbumUrl, audiophileMode = effectiveAudiophile)
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
            } catch (fallbackEx: Throwable) {
                if (fallbackEx is CancellationException) throw fallbackEx
                // Log and continue to Error result below
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

    private suspend fun resolveDirectPlatformUrl(
        query: String,
        targetPlatformKey: String,
        isAlbum: Boolean = false,
        audiophileMode: Boolean = false
    ): String? {
        val cleanQuery = UrlUtils.cleanSearchQuery(query)
        val resolver: PlatformResolver? = when (targetPlatformKey) {
            "youtubeMusic" -> youTubeMusicResolver
            "appleMusic" -> appleMusicResolver
            "deezer" -> deezerResolver
            "tidal" -> tidalResolver
            "spotify" -> spotifyResolver
            "qobuz" -> qobuzResolver
            "soundcloud" -> soundCloudResolver
            else -> null
        }
        val directMatch = if (isAlbum) {
            resolver?.resolveAlbum(cleanQuery, audiophileMode)
        } else {
            resolver?.resolveTrack(cleanQuery, audiophileMode)
        }
        return directMatch?.takeIf { !UrlUtils.isSearchUrl(it) }
    }

    private suspend fun resolveDirectArtistUrl(artistName: String, targetPlatformKey: String): String? {
        return when (targetPlatformKey) {
            "deezer" -> deezerResolver.resolveArtist(artistName)
            "appleMusic" -> appleMusicResolver.resolveArtist(artistName)
            "youtubeMusic" -> youTubeMusicResolver.resolveArtist(artistName)
            "tidal" -> tidalResolver.resolveArtist(artistName)
            "qobuz" -> qobuzResolver.resolveArtist(artistName)
            else -> null
        }
    }


    suspend fun extractTrackInfo(url: String): String? = metadataExtractor.extractTrackInfo(url)

    suspend fun resolveCanonicalUrl(url: String): String = shortLinkResolver.resolveCanonicalUrl(url)

    private suspend fun queryCustomApi(apiUrl: String, token: String, url: String, targetPlatform: String): String? {
        return try {
            val payload = buildJsonObject {
                put("url", url)
                put("targetPlatform", targetPlatform)
            }.toString()
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
    ): ResolutionResult.Podcast = podcastResolver.resolvePodcast(
        canonicalUrl = canonicalUrl,
        targetPlatformKey = targetPlatformKey,
        isPrefetch = isPrefetch,
        forceRefresh = forceRefresh
    )
}
