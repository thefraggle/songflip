package de.goork.songflip.core.engine

/**
 * Parsed song item extracted from a playlist file (.m3u8, .csv, plain text).
 */
data class ParsedImportTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val isrc: String? = null,
    val originalUrl: String? = null
)

/**
 * Universal parser for playlist files.
 * Supports:
 * 1. M3U / M3U8 (Extended M3U with #EXTINF directives)
 * 2. RFC 4180 CSV with dynamic header resolution (SongFlip, Exportify, Soundiiz, MusConv)
 * 3. Plain-text tracklists ("Artist - Title", "1. Artist - Title")
 */
object PlaylistImportParser {

    /**
     * Parses raw playlist file content based on content or optional file name extension.
     */
    fun parse(content: String, fileNameHint: String? = null): List<ParsedImportTrack> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()

        val ext = fileNameHint?.substringAfterLast('.', "")?.lowercase() ?: ""

        return when {
            ext == "m3u" || ext == "m3u8" || trimmed.startsWith("#EXTM3U") -> parseM3u(trimmed)
            ext == "csv" || isLikelyCsv(trimmed) -> parseCsv(trimmed)
            else -> parsePlainText(trimmed)
        }
    }

    /**
     * Parses Extended M3U and M3U8 content.
     * Looks for #EXTINF:<duration>,[Artist - ]Title
     */
    fun parseM3u(content: String): List<ParsedImportTrack> {
        val tracks = mutableListOf<ParsedImportTrack>()
        val lines = content.lines()
        var pendingExtInf: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                // Example: #EXTINF:180,Daft Punk - Get Lucky
                // Example: #EXTINF:-1,Get Lucky
                val commaIndex = line.indexOf(',')
                if (commaIndex != -1 && commaIndex < line.length - 1) {
                    pendingExtInf = line.substring(commaIndex + 1).trim()
                }
            } else if (!line.startsWith("#")) {
                // This is either a media file path or a streaming URL
                val titleInfo = pendingExtInf ?: cleanFileName(line)
                pendingExtInf = null

                val track = splitArtistAndTitle(titleInfo, originalUrl = if (line.startsWith("http://") || line.startsWith("https://")) line else null)
                if (track.title.isNotBlank()) {
                    tracks.add(track)
                }
            }
        }

        // If there was an EXTINF at the end without a URL line, add it too
        pendingExtInf?.let {
            val track = splitArtistAndTitle(it)
            if (track.title.isNotBlank()) {
                tracks.add(track)
            }
        }

        return tracks
    }

    /**
     * Parses CSV files with flexible column headers.
     */
    fun parseCsv(content: String): List<ParsedImportTrack> {
        val lines = parseCsvLines(content)
        if (lines.isEmpty()) return emptyList()

        val header = lines.first().map { it.trim().lowercase().replace("\"", "").replace("[^a-z0-9]".toRegex(), "") }
        val titleIdx = header.indexOfFirst { it in listOf("title", "track", "trackname", "song", "songtitle", "name") }
        val artistIdx = header.indexOfFirst { it in listOf("artist", "artistname", "artists", "artistnames", "creator") }
        val albumIdx = header.indexOfFirst { it in listOf("album", "albumname", "collection") }
        val isrcIdx = header.indexOfFirst { it in listOf("isrc", "isrccode") }
        val urlIdx = header.indexOfFirst { it in listOf("url", "targeturl", "sourceurl", "spotifyuri", "link") }

        // If no explicit title or artist header is found, try line-by-line fallback
        if (titleIdx == -1 && artistIdx == -1) {
            return parsePlainText(content)
        }

        val tracks = mutableListOf<ParsedImportTrack>()
        for (i in 1 until lines.size) {
            val row = lines[i]
            if (row.all { it.isBlank() }) continue

            val title = if (titleIdx in row.indices) row[titleIdx].trim() else ""
            val artist = if (artistIdx in row.indices) row[artistIdx].trim() else ""
            val album = if (albumIdx in row.indices) row[albumIdx].trim().ifBlank { null } else null
            val isrc = if (isrcIdx in row.indices) row[isrcIdx].trim().ifBlank { null } else null
            val url = if (urlIdx in row.indices) row[urlIdx].trim().ifBlank { null } else null

            if (title.isNotBlank() && artist.isNotBlank()) {
                tracks.add(ParsedImportTrack(title = title, artist = artist, album = album, isrc = isrc, originalUrl = url))
            } else if (title.isNotBlank()) {
                tracks.add(splitArtistAndTitle(title, album = album, isrc = isrc, originalUrl = url))
            }
        }

        return tracks
    }

    /**
     * Parses plain text tracklists.
     * Patterns handled:
     * - "Artist - Title"
     * - "1. Artist - Title"
     * - "Artist: Title"
     * - "Title (by Artist)"
     */
    fun parsePlainText(content: String): List<ParsedImportTrack> {
        val tracks = mutableListOf<ParsedImportTrack>()
        val lines = content.lines()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue

            // Strip leading list numbering (e.g. "01.", "1)", "[1]")
            val cleaned = line.replace("""^(\[\d+\]|\d+[\.\)\-\:]\s*)""".toRegex(), "").trim()
            if (cleaned.isNotBlank()) {
                val track = splitArtistAndTitle(cleaned)
                if (track.title.isNotBlank()) {
                    tracks.add(track)
                }
            }
        }

        return tracks
    }

    private fun isLikelyCsv(content: String): Boolean {
        val firstLine = content.lines().firstOrNull()?.lowercase() ?: return false
        val hasDelimiter = firstLine.contains(',') || firstLine.contains(';') || firstLine.contains('\t')
        val hasKeywords = firstLine.contains("track") || firstLine.contains("title") || firstLine.contains("artist")
        return hasDelimiter && hasKeywords
    }

    private fun splitArtistAndTitle(
        text: String,
        album: String? = null,
        isrc: String? = null,
        originalUrl: String? = null
    ): ParsedImportTrack {
        // Strip file extensions if present
        val clean = text.replace("""\.(mp3|m4a|flac|wav|ogg|aac|alac)$""".toRegex(RegexOption.IGNORE_CASE), "").trim()

        // 1. Try "Artist - Title" or "Artist – Title" or "Artist — Title"
        val dashSeparators = listOf(" - ", " – ", " — ", " | ", " : ")
        for (sep in dashSeparators) {
            if (clean.contains(sep)) {
                val parts = clean.split(sep, limit = 2)
                val artistPart = parts[0].trim()
                val titlePart = parts[1].trim()
                if (artistPart.isNotBlank() && titlePart.isNotBlank()) {
                    return ParsedImportTrack(
                        title = titlePart,
                        artist = artistPart,
                        album = album,
                        isrc = isrc,
                        originalUrl = originalUrl
                    )
                }
            }
        }

        // 2. Try "Title (by Artist)" or "Title by Artist"
        val byRegex = """^(.*?)\s+(?:by|\(by\))\s+(.*?)\)?$""".toRegex(RegexOption.IGNORE_CASE)
        val byMatch = byRegex.find(clean)
        if (byMatch != null) {
            val titlePart = byMatch.groupValues[1].trim()
            val artistPart = byMatch.groupValues[2].trim()
            if (titlePart.isNotBlank() && artistPart.isNotBlank()) {
                return ParsedImportTrack(
                    title = titlePart,
                    artist = artistPart,
                    album = album,
                    isrc = isrc,
                    originalUrl = originalUrl
                )
            }
        }

        // Fallback: entire text is treated as title with empty artist
        return ParsedImportTrack(
            title = clean,
            artist = "",
            album = album,
            isrc = isrc,
            originalUrl = originalUrl
        )
    }

    private fun cleanFileName(path: String): String {
        val fileName = path.substringAfterLast('/').substringAfterLast('\\')
        return fileName.replace("""\.(mp3|m4a|flac|wav|ogg|aac|alac)$""".toRegex(RegexOption.IGNORE_CASE), "").trim()
    }

    /**
     * Robust RFC 4180 CSV line tokenizer handling quotes, commas, semicolons, and tabs.
     */
    private fun parseCsvLines(content: String): List<List<String>> {
        val lines = mutableListOf<List<String>>()
        val firstLine = content.lines().firstOrNull() ?: return emptyList()
        val delimiter = when {
            firstLine.count { it == '\t' } > firstLine.count { it == ',' } -> '\t'
            firstLine.count { it == ';' } > firstLine.count { it == ',' } -> ';'
            else -> ','
        }

        var inQuotes = false
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()

        var i = 0
        while (i < content.length) {
            val c = content[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < content.length && content[i + 1] == '"') {
                        currentField.append('"')
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                }
                (c == '\r' || c == '\n') && !inQuotes -> {
                    if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') {
                        i++ // CRLF
                    }
                    currentRow.add(currentField.toString())
                    currentField.clear()
                    if (currentRow.any { it.isNotBlank() }) {
                        lines.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.any { it.isNotBlank() }) {
                lines.add(currentRow.toList())
            }
        }

        return lines
    }
}
