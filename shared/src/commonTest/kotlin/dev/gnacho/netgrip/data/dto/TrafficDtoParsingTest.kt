package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Los cuerpos de estos DTO son los que sirve el panel verificado en
 * netgrip-git (internal/server: writeJSON de ProbeNlbwmon, NlbwmonTopDevices y
 * NetifydTimeline). Se prueban las dos trampas reales: claves de mas y
 * `protocols: null`.
 */
class TrafficDtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun nlbwmonProbeMapsSnakeCaseFields() {
        val probe = json.decodeFromString<NlbwmonProbeDto>(
            """{"installed":true,"running":true,"generations":2,"commit_interval":30,
               "prealloc_days":7,"protocol_database":true,"columna_nueva":"ignorada"}""",
        )
        assertTrue(probe.installed)
        assertTrue(probe.running)
        assertEquals(2, probe.generations)
        assertEquals(30, probe.commitInterval)
        assertEquals(7, probe.preallocDays)
        assertTrue(probe.protocolDatabase)
    }

    @Test
    fun nlbwmonProbeWithoutFieldsIsNotInstalled() {
        val probe = json.decodeFromString<NlbwmonProbeDto>("""{"installed":false}""")
        assertEquals(false, probe.installed)
        assertEquals(0, probe.commitInterval)
    }

    @Test
    fun nlbwmonTopCarriesDevicesAndProtocols() {
        val top = json.decodeFromString<NlbwmonTopDto>(
            """{"devices":[{"key":"aa:bb:cc:dd:ee:01","ip":"192.168.1.20","conns":12,
                 "down_bytes":1024,"up_bytes":256}],"apps":[{"key":"HTTPS","conns":8,
                 "down_bytes":900,"up_bytes":100}]}""",
        )
        assertEquals(1, top.devices.size)
        assertEquals("192.168.1.20", top.devices.first().ip)
        assertEquals(1024, top.devices.first().downBytes)
        assertEquals(256, top.devices.first().upBytes)
        assertEquals("HTTPS", top.apps.first().key)
    }

    @Test
    fun dpiProbeAcceptsNullProtocols() {
        val probe = json.decodeFromString<DpiProbeDto>(
            """{"applicable":false,"total_bytes":0,"total_flows":0,"protocols":null}""",
        )
        assertEquals(false, probe.applicable)
        assertNull(probe.protocols)
    }

    @Test
    fun dpiProbeWithoutProtocolsKeyAlsoParses() {
        val probe = json.decodeFromString<DpiProbeDto>("""{"applicable":true}""")
        assertNull(probe.protocols)
    }

    @Test
    fun dpiAppsAndTimelineParseTheRealShape() {
        val apps = json.decodeFromString<DpiAppsDto>(
            """{"apps":[{"name":"Spotify","category":"streaming","bytes":2048,
                 "local_bytes":1024,"other_bytes":1024,"packets":40,"flows":3}]}""",
        )
        assertEquals("Spotify", apps.apps.single().name)
        assertEquals(3, apps.apps.single().flows)

        val timeline = json.decodeFromString<DpiTimelineDto>(
            """{"buckets":[{"time":"2026-11-26T17:30:00Z","apps":{"Spotify":
                 {"local":1,"other":2,"total":3}}}],
               "top":[{"name":"Spotify","category":"streaming","bytes":3}],
               "totals":{"local":1,"other":2,"total":3}}""",
        )
        assertEquals(1, timeline.buckets.size)
        assertEquals("2026-11-26T17:30:00Z", timeline.buckets.single().time)
        assertEquals(3, timeline.buckets.single().apps.getValue("Spotify").total)
        assertEquals(3, timeline.totals.total)
        assertEquals("Spotify", timeline.top.single().name)
    }

    @Test
    fun emptyTimelineIsNotAnError() {
        val timeline = json.decodeFromString<DpiTimelineDto>("""{"buckets":[],"top":[],"totals":{}}""")
        assertTrue(timeline.buckets.isEmpty())
        assertEquals(0, timeline.totals.total)
    }
}
