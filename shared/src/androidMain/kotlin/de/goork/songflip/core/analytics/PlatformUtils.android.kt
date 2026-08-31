package de.goork.songflip.core.analytics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

actual fun getIso8601Timestamp(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date())
}

actual fun generateRandomSessionId(): String {
    return UUID.randomUUID().toString()
}
