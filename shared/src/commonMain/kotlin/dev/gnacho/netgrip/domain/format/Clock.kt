package dev.gnacho.netgrip.domain.format

/** Hora local "14:35" de un instante en segundos. */
expect fun formatClock(epochSeconds: Long): String

/** Instante actual en segundos desde la epoca. */
expect fun nowEpochSeconds(): Long
