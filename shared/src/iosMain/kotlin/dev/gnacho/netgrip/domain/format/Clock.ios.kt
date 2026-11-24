package dev.gnacho.netgrip.domain.format

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.time

actual fun formatClock(epochSeconds: Long): String {
    val formatter = NSDateFormatter().apply { dateFormat = "HH:mm" }
    val date = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())
    return formatter.stringFromDate(date)
}

@OptIn(ExperimentalForeignApi::class)
actual fun nowEpochSeconds(): Long = time(null)
