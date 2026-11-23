package dev.gnacho.netgrip.domain.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatsTest {

    @Test
    fun losBytesSeMuestranEnMib() {
        assertEquals("395,0 MiB", Formats.bytes(395L * 1024 * 1024))
        assertEquals("512 B", Formats.bytes(512))
    }

    @Test
    fun losKibDeRootPasanAMib() {
        assertEquals("1,0 MiB", Formats.kib(1024))
        assertEquals("128,0 MiB", Formats.kib(128L * 1024))
    }

    @Test
    fun elUptimeSeLeeEnDiasHorasYMinutos() {
        assertEquals("12 d 4 h 0 min", Formats.uptime(12L * 86400 + 4 * 3600))
        assertEquals("2 h 7 min", Formats.uptime(2L * 3600 + 7 * 60))
        assertEquals("42 min", Formats.uptime(42L * 60))
        assertEquals("30 s", Formats.uptime(30))
        assertEquals("0 s", Formats.uptime(0))
    }

    @Test
    fun laVelocidadDistingueGigaYMedia() {
        assertEquals("1 Gb/s", Formats.speed(1000))
        assertEquals("2,5 Gb/s", Formats.speed(2500))
        assertEquals("100 Mb/s", Formats.speed(100))
        assertEquals("Sin enlace", Formats.speed(0))
    }

    @Test
    fun laSenalSoloApareceSiHayDato() {
        assertNull(Formats.signal(null))
        assertNull(Formats.signal(0))
        assertEquals("-62 dBm", Formats.signal(-62))
    }

    @Test
    fun elPorcentajeRedondeaYEvitaDividirPorCero() {
        assertEquals(37, Formats.percent(148, 395))
        assertEquals(0, Formats.percent(10, 0))
    }

    @Test
    fun elSeparadorDecimalEsUnaComa() {
        assertEquals("0,42", Formats.decimal(0.42, 2))
        assertEquals("1,0", Formats.decimal(1.0, 1))
        assertEquals("0,38 / 0,31", Formats.load(listOf(0.42, 0.38, 0.31)).split(" / ").drop(1).joinToString(" / "))
    }
}
