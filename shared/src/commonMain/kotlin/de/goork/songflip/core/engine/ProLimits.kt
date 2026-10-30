package de.goork.songflip.core.engine

/**
 * Centralized business limits and thresholds for SongFlip.
 *
 * Consumed across Android and iOS clients to avoid magic numbers and maintain
 * consistent business invariants across platforms.
 */
object ProLimits {
    /**
     * Maximum number of tracks converted for non-PRO (free) users per playlist conversion.
     */
    const val MAX_FREE_PLAYLIST_TRACKS: Int = 5

    /**
     * Maximum number of tracks converted for PRO users per playlist conversion.
     */
    const val MAX_PRO_PLAYLIST_TRACKS: Int = 200

    /**
     * Maximum number of favorite links a non-PRO user can store.
     */
    const val MAX_FREE_FAVORITES: Int = 10

    /**
     * Free acoustic music recognition (Listen & Flip) scans per calendar month.
     */
    const val FREE_SCAN_QUOTA_PER_MONTH: Int = 3
}
