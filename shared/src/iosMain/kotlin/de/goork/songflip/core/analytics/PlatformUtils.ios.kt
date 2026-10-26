package de.goork.songflip.core.analytics

import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSUUID
import platform.Foundation.countryCode
import platform.Foundation.currentLocale

actual fun getIso8601Timestamp(): String {
    val formatter = NSISO8601DateFormatter()
    return formatter.stringFromDate(NSDate())
}

actual fun generateRandomSessionId(): String {
    return NSUUID().UUIDString()
}

actual fun getSystemCountryCode(): String {
    val code = NSLocale.currentLocale.countryCode
    return if (!code.isNullOrBlank()) code.uppercase() else "DE"
}

