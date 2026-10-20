package de.goork.songflip.core.engine.resolvers

object ResolverUtils {
    private val STOP_WORDS = setOf("the", "a", "an", "and", "und", "feat", "ft", "featuring", "with")

    fun normalizeTokens(text: String): List<String> {
        val cleaned = text.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace("[^a-z0-9\\s]".toRegex(), " ")
        return cleaned.split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.length > 1 && it !in STOP_WORDS }
    }

    /**
     * Validates whether candidate metadata matches the search query to prevent blind redirects
     * to completely unrelated tracks or albums when the streaming catalog lacks the item.
     */
    fun isMatch(query: String, candidateArtist: String?, candidateTitle: String?): Boolean {
        val queryTokens = normalizeTokens(query)
        if (queryTokens.isEmpty()) return true

        val candidateArtistTokens = candidateArtist?.let { normalizeTokens(it) } ?: emptyList()
        val candidateTitleTokens = candidateTitle?.let { normalizeTokens(it) } ?: emptyList()
        val candidateTokens = (candidateArtistTokens + candidateTitleTokens).toSet()

        if (candidateTokens.isEmpty()) {
            return true
        }

        return queryTokens.any { qTok ->
            candidateTokens.any { cTok ->
                cTok == qTok || (qTok.length >= 4 && (cTok.startsWith(qTok) || qTok.startsWith(cTok)))
            }
        }
    }

    /**
     * Scores a catalog candidate for Audiophile Quality Prioritization (#85).
     * Rewards studio masters, Hi-Res/24-bit/Atmos flags, and exact artist/title alignment,
     * while penalizing unrequested Radio Edits, Various Artists compilations, remixes, and tribute/cover versions.
     */
    fun scoreAudiophileCandidate(
        query: String,
        candidateArtist: String?,
        candidateTitle: String?,
        candidateVersion: String? = null,
        albumTitle: String? = null,
        albumArtist: String? = null,
        isHiRes: Boolean = false,
        isLossless: Boolean = false,
        isSpatialOrAtmos: Boolean = false,
        bitDepth: Int? = null,
        samplingRate: Double? = null,
        albumTrackCount: Int? = null
    ): Int {
        if (!isMatch(query, candidateArtist, candidateTitle)) return Int.MIN_VALUE

        var score = 100
        val lowerQuery = query.lowercase()
        val lowerTitle = (candidateTitle ?: "").lowercase()
        val lowerVersion = (candidateVersion ?: "").lowercase()
        val lowerAlbum = (albumTitle ?: "").lowercase()
        val lowerArtist = (candidateArtist ?: "").lowercase()
        val lowerAlbumArtist = (albumArtist ?: "").lowercase()
        val combinedMeta = "$lowerTitle $lowerVersion $lowerAlbum"

        // 1. Token overlap base quality
        val queryTokens = normalizeTokens(query)
        val artistTokens = candidateArtist?.let { normalizeTokens(it) } ?: emptyList()
        val titleTokens = candidateTitle?.let { normalizeTokens(it) } ?: emptyList()
        val matchedTitleTokens = queryTokens.count { q -> titleTokens.any { it == q } }
        val matchedArtistTokens = queryTokens.count { q -> artistTokens.any { it == q } }
        score += (matchedTitleTokens * 12) + (matchedArtistTokens * 15)

        // Penalize candidates where artist has zero overlap with query tokens (likely wrong artist or cover band)
        if (artistTokens.isNotEmpty() && matchedArtistTokens == 0) {
            score -= 60
        }

        // 2. Penalize tribute / cover / karaoke traps unless explicitly requested
        val coverIndicators = listOf(
            "originally performed by", "tribute", "karaoke", "in the style of",
            "8-bit", "chiptune", "lullaby", "made famous by", "cover"
        )
        for (indicator in coverIndicators) {
            if ((combinedMeta.contains(indicator) || lowerArtist.contains(indicator)) && !lowerQuery.contains(indicator)) {
                score -= 120
            }
        }

        // 3. Penalize Radio Edits & Short Edits unless requested in query
        val radioIndicators = listOf("radio edit", "short edit", "single edit", "clean edit", "video edit")
        for (indicator in radioIndicators) {
            if (combinedMeta.contains(indicator) && !lowerQuery.contains("radio") && !lowerQuery.contains("edit")) {
                score -= 40
            }
        }

        // 4. Penalize unrequested remixes / live / instrumental / sped up versions
        val variantIndicators = listOf("remix", "rework", "bootleg", "sped up", "slowed", "instrumental", "acapella", "drumless")
        for (indicator in variantIndicators) {
            if (combinedMeta.contains(indicator) && !lowerQuery.contains(indicator)) {
                score -= 45
            }
        }
        if ((lowerTitle.contains(" live") || lowerVersion.contains("live")) && !lowerQuery.contains("live")) {
            score -= 30
        }

        // 5. Penalize "Various Artists" & Compilation albums vs. Original Studio Albums
        val compilationIndicators = listOf(
            "various artists", "greatest hits", "best of", "top 100", "bravo hits",
            "now that's what i call", "mega hits", "summer hits", "party hits", "workout"
        )
        val isVariousAlbum = lowerAlbumArtist.contains("various")
        val isCompilationAlbum = compilationIndicators.any { lowerAlbum.contains(it) && !lowerQuery.contains(it) }
        if (isVariousAlbum || isCompilationAlbum) {
            score -= 55
        } else if (albumTrackCount != null) {
            if (albumTrackCount >= 4) {
                // Full studio album (most likely to carry the canonical Hi-Res / Atmos master)
                score += 25
            } else if (albumTrackCount == 1 && lowerAlbum == lowerTitle) {
                // Standalone 1-track single
                score -= 5
            }
        }

        // 6. Reward verified Audiophile Audio Quality metadata
        if (isHiRes) score += 45
        if (isSpatialOrAtmos) score += 35
        if (isLossless) score += 20
        if (bitDepth != null && bitDepth >= 24) score += 25
        if (samplingRate != null) {
            when {
                samplingRate >= 96.0 -> score += 25
                samplingRate >= 88.2 -> score += 20
                samplingRate > 44.1 -> score += 10
            }
        }

        return score
    }

    fun isArtistNameMatch(candidate: String?, target: String?): Boolean {
        fun normalize(str: String): String {
            return str.lowercase().trim()
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replace("[^a-z0-9]".toRegex(), "")
        }
        val normCand = normalize(candidate ?: return false)
        val normTarget = normalize(target ?: return false)
        if (normCand.isEmpty() || normTarget.isEmpty()) return false
        if (normCand == normTarget) return true
        if (normCand == normTarget + "thema" || normCand == normTarget + "topic") return true
        if (normTarget == normCand + "thema" || normTarget == normCand + "topic") return true
        return false
    }
}
