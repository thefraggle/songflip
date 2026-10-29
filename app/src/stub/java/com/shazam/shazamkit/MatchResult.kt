package com.shazam.shazamkit

import java.net.URL

sealed class MatchResult {
    data class Match(val matchedMediaItems: List<MatchedMediaItem>) : MatchResult()
    object NoMatch : MatchResult()
    data class Error(val exception: Throwable) : MatchResult()
}

class MatchedMediaItem(
    val title: String? = null,
    val artist: String? = null,
    val subtitle: String? = null,
    val artworkURL: URL? = null,
    val appleMusicID: String? = null,
    val appleMusicURL: URL? = null,
    val webURL: URL? = null
)
