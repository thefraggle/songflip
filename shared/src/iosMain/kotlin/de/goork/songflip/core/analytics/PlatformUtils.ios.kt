package de.goork.songflip.core.analytics

import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSUUID

actual fun getIso8601Timestamp(): String {
    val formatter = NSISO8601DateFormatter()
    return formatter.stringFromDate(NSDate())
}

actual fun generateRandomSessionId(): String {
    return NSUUID().UUIDString()
}
