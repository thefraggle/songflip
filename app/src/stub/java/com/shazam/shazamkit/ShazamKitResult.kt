package com.shazam.shazamkit

sealed class ShazamKitResult<out T> {
    data class Success<out T>(val data: T) : ShazamKitResult<T>()
    data class Failure(val error: Throwable) : ShazamKitResult<Nothing>()
}
