package dev.gnacho.netgrip.domain.history

import dev.gnacho.netgrip.data.dto.HistoryEntryDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrafficSeriesTest {

    private fun entry(ts: Long, rx: Long, tx: Long, clients: Int = 3, load: Double = 0.5) =
        HistoryEntryDto(ts = ts, rx = rx, tx = tx, load = load, clients = clients)

    @Test
    fun laPrimeraMuestraNoTieneTasa() {
        val samples = TrafficSeries.fromHistory(listOf(entry(1000, 500, 800)))
        assertEquals(1, samples.size)
        assertEquals(0.0, samples.single().rxRate)
        assertEquals(0.0, samples.single().txRate)
    }

    @Test
    fun laTasaSaleDelDeltaEntreMuestras() {
        val samples = TrafficSeries.fromHistory(
            listOf(
                entry(1000, 1_000, 2_000),
                entry(1300, 61_000, 32_000),
            ),
        )
        // 60000 bytes en 300 s = 200 B/s; 30000 en 300 s = 100 B/s
        assertEquals(200.0, samples[1].rxRate)
        assertEquals(100.0, samples[1].txRate)
    }

    @Test
    fun unContadorQueBajaSeDibujaComoCero() {
        val samples = TrafficSeries.fromHistory(
            listOf(
                entry(1000, 90_000, 90_000),
                entry(1300, 10, 10),
            ),
        )
        assertEquals(0.0, samples[1].rxRate)
        assertEquals(0.0, samples[1].txRate)
    }

    @Test
    fun dosMuestrasConElMismoInstanteNoDividenPorCero() {
        val samples = TrafficSeries.fromHistory(
            listOf(
                entry(1000, 10, 10),
                entry(1000, 90, 90),
            ),
        )
        assertEquals(0.0, samples[1].rxRate)
    }

    @Test
    fun laVentanaRecortaAlRangoElegido() {
        val now = 1_000_000L
        val entries = listOf(
            entry(now - 7200, 0, 0),
            entry(now - 3600, 10, 10),
            entry(now - 1800, 20, 20),
            entry(now, 30, 30),
        )
        val samples = TrafficSeries.fromHistory(entries)
        assertEquals(4, TrafficSeries.window(samples, TrafficRange.DAY).size)
        assertEquals(3, TrafficSeries.window(samples, TrafficRange.HOUR).size)
    }

    @Test
    fun elTransferidoUsaLosAcumuladosDeLaVentana() {
        val samples = TrafficSeries.fromHistory(
            listOf(
                entry(1000, 1_000, 5_000),
                entry(1300, 3_000, 6_000),
                entry(1600, 10_000, 9_000),
            ),
        )
        assertEquals(9_000L, TrafficSeries.transferred(samples, rx = true))
        assertEquals(4_000L, TrafficSeries.transferred(samples, rx = false))
    }

    @Test
    fun picoYMediaUsanLasTasasDeLasDosSeries() {
        val samples = TrafficSeries.fromHistory(
            listOf(
                entry(0, 0, 0),
                entry(100, 1_000, 200),
                entry(200, 2_000, 2_000),
            ),
        )
        assertEquals(18.0, TrafficSeries.peakBytesPerSecond(samples))
        assertTrue(TrafficSeries.averageBytesPerSecond(samples, rx = true) > 0)
    }

    @Test
    fun unaListaVaciaNoRompeNada() {
        val samples = TrafficSeries.fromHistory(emptyList())
        assertTrue(samples.isEmpty())
        assertEquals(0.0, TrafficSeries.peakBytesPerSecond(samples))
        assertEquals(0L, TrafficSeries.transferred(samples, rx = true))
        assertEquals(emptyList(), TrafficSeries.window(samples, TrafficRange.DAY))
    }

    @Test
    fun elOrdenDelServidorNoImporta() {
        val samples = TrafficSeries.fromHistory(
            listOf(
                entry(1300, 2_000, 2_000),
                entry(1000, 1_000, 1_000),
            ),
        )
        assertEquals(1000L, samples.first().timestamp)
        assertEquals(1300L, samples.last().timestamp)
    }
}
