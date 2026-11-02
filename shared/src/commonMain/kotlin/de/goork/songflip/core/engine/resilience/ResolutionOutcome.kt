package de.goork.songflip.core.engine.resilience

sealed interface ResolutionOutcome<out T> {
    data class Success<out T>(val data: T) : ResolutionOutcome<T>
    object NotFound : ResolutionOutcome<Nothing>
    data class RateLimited(
        val host: String? = null,
        val retryAfterSeconds: Long? = 300L
    ) : ResolutionOutcome<Nothing>
    data class Error(val message: String, val throwable: Throwable? = null) : ResolutionOutcome<Nothing>

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    val isRateLimited: Boolean
        get() = this is RateLimited
}
