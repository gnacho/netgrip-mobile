package dev.gnacho.netgrip.domain.traffic

import dev.gnacho.netgrip.data.dto.DpiAppDto
import dev.gnacho.netgrip.data.dto.DpiBucketDto
import dev.gnacho.netgrip.data.dto.DpiCountersDto
import dev.gnacho.netgrip.data.dto.DpiProtocolDto
import dev.gnacho.netgrip.data.dto.NlbwUsageDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrafficRulesTest {

    @Test
    fun devicesUseTheStoredNameAndKeepTheIpInTheDetail() {
        val rows = TrafficRules.deviceRows(
            usage = listOf(
                NlbwUsageDto(key = "aa:bb:cc:dd:ee:01", ip = "192.168.1.20", conns = 4, downBytes = 100, upBytes = 20),
            ),
            names = mapOf("aa:bb:cc:dd:ee:01" to "Portatil"),
        )
        assertEquals("Portatil", rows.single().label)
        assertEquals("192.168.1.20", rows.single().detail)
        assertEquals(120, rows.single().total)
    }

    @Test
    fun devicesFallBackToTheIpAndThenToTheMac() {
        val rows = TrafficRules.deviceRows(
            usage = listOf(
                NlbwUsageDto(key = "aa:bb:cc:dd:ee:01", ip = "192.168.1.20", downBytes = 1),
                NlbwUsageDto(key = "aa:bb:cc:dd:ee:02", ip = "", downBytes = 1),
            ),
            names = mapOf("aa:bb:cc:dd:ee:99" to "Otro"),
        )
        assertEquals("192.168.1.20", rows.first().label)
        assertEquals("", rows.first().detail)
        assertEquals("aa:bb:cc:dd:ee:02", rows.last().label)
    }

    @Test
    fun rowsComeSortedByCountedBytes() {
        val rows = TrafficRules.deviceRows(
            listOf(
                NlbwUsageDto(key = "a", downBytes = 10, upBytes = 0),
                NlbwUsageDto(key = "b", downBytes = 5, upBytes = 100),
                NlbwUsageDto(key = "c", downBytes = 1, upBytes = 1),
            ),
        )
        assertEquals(listOf("b", "a", "c"), rows.map { it.key })
    }

    @Test
    fun rowsWithoutAKeyAreDropped() {
        val rows = TrafficRules.deviceRows(
            listOf(
                NlbwUsageDto(key = "", ip = "192.168.1.2", downBytes = 5),
                NlbwUsageDto(key = "aa", downBytes = 1),
            ),
        )
        assertEquals(listOf("aa"), rows.map { it.key })
    }

    @Test
    fun unnamedProtocolsReadAsSinIdentificar() {
        val rows = TrafficRules.appRows(
            listOf(
                NlbwUsageDto(key = "other", downBytes = 10),
                NlbwUsageDto(key = "HTTPS", downBytes = 90),
            ),
        )
        assertEquals(listOf("HTTPS", "Sin identificar"), rows.map { it.label })
    }

    @Test
    fun nullProtocolsAreNormalisedInsteadOfCrashing() {
        // El panel llego a serializar protocols: null cuando no podia leer
        // conntrack (#661); la app no debe romper por eso.
        assertTrue(TrafficRules.protocolRows(null).isEmpty())
        val rows = TrafficRules.protocolRows(
            listOf(
                DpiProtocolDto(name = "", bytes = 10),
                DpiProtocolDto(name = "HTTPS", bytes = 5),
                DpiProtocolDto(name = "DNS", bytes = 50),
            ),
        )
        assertEquals(listOf("DNS", "HTTPS"), rows.map { it.name })
    }

    @Test
    fun applicationsWithoutBytesAreDropped() {
        val rows = TrafficRules.dpiAppRows(
            listOf(
                DpiAppDto(name = "Spotify", category = "streaming", bytes = 2048, flows = 3),
                DpiAppDto(name = "Ruido", bytes = 0),
                DpiAppDto(name = "", bytes = 10),
            ),
        )
        assertEquals(listOf("Spotify"), rows.map { it.name })
    }

    @Test
    fun timelineAddsTheApplicationsOfEachBucket() {
        val buckets = listOf(
            DpiBucketDto(
                time = "2026-11-26T17:30:00Z",
                apps = mapOf(
                    "Spotify" to DpiCountersDto(local = 1, other = 2, total = 3),
                    "YouTube" to DpiCountersDto(local = 4, other = 0, total = 4),
                ),
            ),
            DpiBucketDto(time = "no es una fecha", apps = mapOf("X" to DpiCountersDto(total = 9))),
            DpiBucketDto(time = "2026-11-26T17:35:00Z", apps = mapOf("DNS" to DpiCountersDto(total = 5))),
        )
        val points = TrafficRules.points(buckets) { "t$it" }
        assertEquals(2, points.size)
        assertEquals("t1795714200", points.first().label)
        assertEquals(7, points.first().bytes)
        assertEquals(5, points.last().bytes)
    }

    @Test
    fun rfc3339ParsesUtcOffsetsAndFractions() {
        assertEquals(0L, TrafficRules.rfc3339Seconds("1970-01-01T00:00:00Z"))
        assertEquals(1_795_714_500L, TrafficRules.rfc3339Seconds("2026-11-26T17:35:00Z"))
        assertEquals(1_795_707_300L, TrafficRules.rfc3339Seconds("2026-11-26T17:35:00+02:00"))
        assertEquals(1_795_714_500L, TrafficRules.rfc3339Seconds("2026-11-26T17:35:00.250Z"))
        assertEquals(1_709_164_800L, TrafficRules.rfc3339Seconds("2024-02-29T00:00:00Z"))
    }

    @Test
    fun rfc3339RejectsAnythingThatIsNotADate() {
        assertNull(TrafficRules.rfc3339Seconds(""))
        assertNull(TrafficRules.rfc3339Seconds("ayer"))
        assertNull(TrafficRules.rfc3339Seconds("2026-13-01T00:00:00Z"))
        assertNull(TrafficRules.rfc3339Seconds("2026-11-26 17:35:00Z"))
        assertNull(TrafficRules.rfc3339Seconds("2026-11-26T25:00:00Z"))
    }

    @Test
    fun categoriesAreReadableAndUnknownOnesStayRaw() {
        assertEquals("Streaming", TrafficRules.category("streaming"))
        assertEquals("Redes sociales", TrafficRules.category("SOCIAL"))
        assertEquals("Sin categoria", TrafficRules.category(""))
        assertEquals("voip-ish", TrafficRules.category("voip-ish"))
    }
}
