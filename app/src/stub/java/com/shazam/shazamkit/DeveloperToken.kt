package com.shazam.shazamkit

class DeveloperToken(val token: String)

fun interface DeveloperTokenProvider {
    fun provideToken(): DeveloperToken
}
