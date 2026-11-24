package dev.gnacho.netgrip.domain.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

actual fun formatClock(epochSeconds: Long): String =
    clock.format(Instant.ofEpochSecond(epochSeconds))

actual fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000
