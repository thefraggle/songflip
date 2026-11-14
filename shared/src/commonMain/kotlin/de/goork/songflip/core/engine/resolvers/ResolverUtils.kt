package de.goork.songflip.core.engine.resolvers

object ResolverUtils {
    private val STOP_WORDS = setOf("the", "a", "an", "and", "und", "feat", "ft", "featuring", "with")

    fun normalizeTokens(text: String): List<String> {
        val cleaned = text.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace("é", "e").replace("è", "e").replace("ê", "e").replace("ë", "e")
            .replace("á", "a").replace("à", "a").replace("â", "a").replace("ã", "a").replace("å", "a").replace("æ", "ae")
            .replace("ó", "o").replace("ò", "o").replace("ô", "o").replace("õ", "o").replace("ø", "oe")
            .replace("í", "i").replace("ì", "i").replace("î", "i").replace("ï", "i")
            .replace("ú", "u").replace("ù", "u").replace("û", "u")
            .replace("ñ", "n").replace("ç", "c")
            .replace("p!nk", "pink").replace("ke\$ha", "kesha")
            .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
        return cleaned.split("\\s+".toRegex())
            .map { it.trim() }
            .filter { (it.length > 1 || it.any { c -> c.code > 127 }) && it !in STOP_WORDS }
    }

    private val EDITION_PAREN_REGEX = Regex(
        """\s*[\(\[]\s*(?:super\s+deluxe|deluxe|bonus\s+tracks?|expanded|special|collector'?s|standard|explicit|clean|digital|international|tour|anniversary|\d+(?:st|nd|rd|th)\s+anniversary)(?:\s+(?:edition|version|release|remaster(?:ed)?|album|ver\.?))?\s*[\)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val EDITION_DASH_REGEX = Regex(
        """\s*[-–—]\s*(?:super\s+deluxe|deluxe|bonus\s+tracks?|expanded|special|collector'?s|anniversary|\d+(?:st|nd|rd|th)\s+anniversary)(?:\s+(?:edition|version|release))?$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Strips album/track edition suffixes (e.g. "(Deluxe)", "(Bonus Track Version)", "(Expanded Edition)")
     * for cross-catalog fallback matching when streaming services use differing edition labels.
     */
    fun stripEditionSuffix(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        val stripped = trimmed
            .replace(EDITION_PAREN_REGEX, "")
            .replace(EDITION_DASH_REGEX, "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return stripped.ifEmpty { trimmed }
    }

    /**
     * Validates whether candidate metadata matches the search query to prevent blind redirects
     * to completely unrelated tracks or albums when the streaming catalog lacks the item (#194).
     */
    fun isMatch(query: String, candidateArtist: String?, candidateTitle: String?): Boolean {
        val queryTokens = normalizeTokens(query)
        if (queryTokens.isEmpty()) return true

        val candidateArtistTokens = candidateArtist?.let { normalizeTokens(it) } ?: emptyList()
        val candidateTitleTokens = candidateTitle?.let { normalizeTokens(it) } ?: emptyList()
        val candidateTokens = (candidateArtistTokens + candidateTitleTokens).toSet()

        // If candidate lacks descriptive metadata (e.g. minimal mock or id-only payload), cannot disqualify
        if (candidateTokens.isEmpty()) {
            return true
        }

        // 1. If candidate has a title, require at least one token match with the query
        // to prevent matching an unrelated track from the same artist (#194)
        if (candidateTitleTokens.isNotEmpty()) {
            val hasTitleMatch = candidateTitleTokens.any { cTok ->
                queryTokens.any { qTok ->
                    cTok == qTok || (qTok.length >= 4 && (cTok.startsWith(qTok) || qTok.startsWith(cTok)))
                }
            }
            if (!hasTitleMatch) return false
        }

        // 2. Token overlap: Ensure at least one query token matches candidate tokens
        return queryTokens.any { qTok ->
            candidateTokens.any { cTok ->
                cTok == qTok || (qTok.length >= 4 && (cTok.startsWith(qTok) || qTok.startsWith(cTok)))
            }
        }
    }

    val VARIANT_INDICATORS = listOf(
        "remix", "rework", "bootleg", "sped up", "slowed", "instrumental",
        "acapella", "drumless", "club mix", "extended mix", "vip mix", "dub mix", "dance mix", "flip",
        "cover", "tribute", "lofi", "lo-fi", "reverb"
    )

    /**
     * Selects the best candidate from a list, context-sensitively protecting original tracks
     * from unrequested remixes/variants (#208) while ensuring that explicit remix/live queries
     * still prefer their corresponding version.
     */
    fun <T> findBestCandidate(
        candidates: List<T>,
        query: String,
        isAlbum: Boolean = false,
        getArtist: (T) -> String?,
        getTitle: (T) -> String?,
        getVersion: ((T) -> String?)? = null,
        getAlbumTitle: ((T) -> String?)? = null
    ): T? {
        if (candidates.isEmpty()) return null

        val validMatches = candidates.filter { item ->
            val title = getTitle(item)
            val artist = getArtist(item)
            val version = getVersion?.invoke(item)
            val fullTitle = if (version.isNullOrBlank()) title else "$title ($version)"
            isMatch(query, artist, title) || (fullTitle != title && isMatch(query, artist, fullTitle))
        }

        if (validMatches.isEmpty()) return null
        if (validMatches.size == 1) return validMatches.first()

        val lowerQuery = query.lowercase()
        val wantsRemix = Regex("""\b(?:remix|mix|rework|bootleg|flip)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerQuery)
        val wantsLive = Regex("""\b(?:live|concert|tour)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerQuery)
        val wantsAcoustic = Regex("""\b(?:acoustic|unplugged)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerQuery)
        val wantsInstrumental = Regex("""\b(?:instrumental|karaoke)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerQuery)
        val wantsSpedUpOrSlowed = Regex("""\b(?:sped up|slowed|reverb)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerQuery)
        val wantsCoverOrTribute = Regex("""\b(?:cover|tribute)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerQuery)
        val wantsLofi = Regex("""\b(?:lofi|lo-fi)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerQuery)

        return validMatches.maxByOrNull { item ->
            var s = 0
            val title = getTitle(item) ?: ""
            val version = getVersion?.invoke(item) ?: ""
            val combined = "$title $version".lowercase()

            val isCandRemix = Regex("""\b(?:remix|mix|rework|bootleg|flip)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combined)
            val isCandLive = Regex("""\b(?:live)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combined)
            val isCandAcoustic = Regex("""\b(?:acoustic|unplugged)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combined)
            val isCandInstrumental = Regex("""\b(?:instrumental|karaoke)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combined)
            val isCandSpedUpOrSlowed = Regex("""\b(?:sped up|slowed|reverb)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combined)
            val isCandCoverOrTribute = Regex("""\b(?:cover|tribute)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combined)
            val isCandLofi = Regex("""\b(?:lofi|lo-fi)\b""", RegexOption.IGNORE_CASE).containsMatchIn(combined)

            if (!wantsRemix && isCandRemix) s -= 120
            if (wantsRemix && !isCandRemix) s -= 80

            if (!wantsLive && isCandLive) s -= 100
            if (wantsLive && !isCandLive) s -= 60

            if (!wantsAcoustic && isCandAcoustic) s -= 80
            if (wantsAcoustic && !isCandAcoustic) s -= 50

            if (!wantsInstrumental && isCandInstrumental) s -= 100
            if (wantsInstrumental && !isCandInstrumental) s -= 60

            if (!wantsSpedUpOrSlowed && isCandSpedUpOrSlowed) s -= 120

            if (!wantsCoverOrTribute && isCandCoverOrTribute) s -= 120
            if (wantsCoverOrTribute && !isCandCoverOrTribute) s -= 80

            if (!wantsLofi && isCandLofi) s -= 100
            if (wantsLofi && !isCandLofi) s -= 60

            // Reward exact title matches
            val normTitle = normalizeTokens(title).joinToString(" ")
            val normQuery = normalizeTokens(query).joinToString(" ")
            if (normQuery.contains(normTitle) || normTitle == normQuery) {
                s += 40
            }

            // Prefer original studio releases over "Best of" / compilation albums when not explicitly requested
            val albumTitle = getAlbumTitle?.invoke(item)?.lowercase().orEmpty()
            if (!isAlbum && albumTitle.isNotEmpty()) {
                val compilationIndicators = listOf(
                    "best of", "greatest hits", "anthology", "essential",
                    "complete collection", "ultimate collection", "very best",
                    "hits collection", "gold collection"
                )
                if (compilationIndicators.any { albumTitle.contains(it) && !lowerQuery.contains(it) }) {
                    s -= 45
                }
            }

            s
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
        val wantsVariant = VARIANT_INDICATORS.any { lowerQuery.contains(it) }
        for (indicator in VARIANT_INDICATORS) {
            if (combinedMeta.contains(indicator) && !wantsVariant) {
                score -= 100
            }
        }
        if ((lowerTitle.contains(" live") || lowerVersion.contains("live")) && !lowerQuery.contains("live")) {
            score -= 50
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

    private val NON_ALPHANUMERIC_UNICODE = Regex("[^\\p{L}\\p{N}]")

    fun isArtistNameMatch(candidate: String?, target: String?): Boolean {
        fun normalize(str: String): String {
            val s = str.lowercase().trim()
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            val stripped = s.replace(NON_ALPHANUMERIC_UNICODE, "")
            return stripped.ifEmpty { s }
        }
        val normCand = normalize(candidate ?: return false)
        val normTarget = normalize(target ?: return false)
        if (normCand.isEmpty() || normTarget.isEmpty()) return false
        if (normCand == normTarget) return true
        if (normCand == normTarget + "thema" || normCand == normTarget + "topic") return true
        if (normTarget == normCand + "thema" || normTarget == normCand + "topic") return true

        // Multi-artist / collaboration match (e.g. "Gang Starr" vs "Gang Starr, Big Shug, Freddie Foxxx")
        val splitRegex = Regex("""[,/&]|\s+(?:feat\.?|ft\.?|with)\s+""", RegexOption.IGNORE_CASE)
        val candParts = candidate.split(splitRegex).map { normalize(it) }.filter { it.length >= 3 }
        val targetParts = target.split(splitRegex).map { normalize(it) }.filter { it.length >= 3 }
        if (candParts.any { c -> targetParts.any { t -> c == t || (c.length >= 5 && (t.startsWith(c) || c.startsWith(t))) } }) {
            return true
        }

        return false
    }
}
