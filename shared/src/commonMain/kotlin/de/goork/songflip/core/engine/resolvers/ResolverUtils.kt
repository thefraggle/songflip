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
