package de.goork.songflip.core.engine

import de.goork.songflip.core.cache.LinkCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackendConfigIsolationTest {

    @BeforeTest
    @AfterTest
    fun resetBackendConfig() {
        EngineConfig.configureBackend(cacheBaseUrl = "", webBaseUrl = "")
    }

    @Test
    fun buildBackendEndpointsIsEmptyByDefaultAndNormalizesConfiguredUrls() {
        assertEquals(emptyList(), EngineConfig.buildBackendEndpoints("/resolve?url=test"))
        assertEquals("", EngineConfig.promoBannerEndpoint())

        EngineConfig.configureBackend(
            cacheBaseUrl = "https://cache.example.test/",
            webBaseUrl = "https://web.example.test/"
        )

        assertEquals(
            listOf(
                "https://cache.example.test/resolve?url=test",
                "https://web.example.test/resolve?url=test"
            ),
            EngineConfig.buildBackendEndpoints("/resolve?url=test")
        )
        assertEquals("https://web.example.test/api/promo-banner", EngineConfig.promoBannerEndpoint())
    }

    @Test
    fun clientsDoNotHitNetworkWhenBackendUrlsAreUnconfigured() = runTest {
        var requestsMade = 0
        val requestedHosts = mutableListOf<String>()
        val mockClient = HttpClient(MockEngine { request ->
            requestsMade++
            requestedHosts += request.url.host
            respond("{}", HttpStatusCode.OK)
        })

        val l2Client = L2CacheClient(mockClient, Json { ignoreUnknownKeys = true }, this)
        val cached = l2Client.queryL2ServerCache(
            canonicalUrl = "https://open.spotify.com/track/123",
            targetPlatformKey = "appleMusic",
            authToken = "pro_user_123"
        )
        assertNull(cached)

        l2Client.pingCacheIngestAsync(
            originalUrl = "https://open.spotify.com/track/123",
            targetUrl = "https://music.apple.com/us/album/123",
            targetPlatform = "appleMusic",
            title = "Song",
            artist = "Artist",
            isAlbum = false,
            links = mapOf(
                "spotify" to "https://open.spotify.com/track/123",
                "appleMusic" to "https://music.apple.com/us/album/123",
                "deezer" to "https://www.deezer.com/track/123"
            )
        )

        val playlistEngine = PlaylistConverterEngine(mockClient)
        val playlistResult = playlistEngine.convertPlaylist(
            url = "https://open.spotify.com/playlist/123",
            targetPlatformKey = "youtubeMusic",
            isPro = false,
            maxTracks = 5
        )
        assertTrue(playlistResult.isFailure)

        val couponService = CouponService(mockClient)
        val redeemOutcome = couponService.redeemCoupon("FLIP-ABCD-EFGH", "install-12345678")
        assertEquals(CouponRedeemStatus.NETWORK_ERROR, redeemOutcome.status)

        val refreshOutcome = couponService.refreshCouponToken("sct_v2_abc.def", "install-12345678")
        assertEquals(CouponRefreshStatus.KEEP, refreshOutcome.status)

        val bannerRaw = PromoBannerManager.fetchPromoBannerRaw(forceRefresh = true)
        assertNull(bannerRaw)

        assertEquals(0, requestsMade, "Unconfigured open-source build must not call proprietary backend endpoints")

        val podcastResolver = PodcastResolver(mockClient, Json { ignoreUnknownKeys = true }, LinkCache())
        podcastResolver.resolvePodcast(
            canonicalUrl = "https://open.spotify.com/episode/123",
            targetPlatformKey = "applePodcasts"
        )
        assertTrue(
            requestedHosts.none { it.contains("songflip.link") || it.contains("songflip-web.web.app") },
            "PodcastResolver must skip proprietary backend /resolve when unconfigured, saw hosts: $requestedHosts"
        )
    }
}
