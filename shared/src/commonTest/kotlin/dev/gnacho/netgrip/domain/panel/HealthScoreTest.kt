package dev.gnacho.netgrip.domain.panel

import dev.gnacho.netgrip.data.dto.MemoryDto
import dev.gnacho.netgrip.data.dto.ModeDto
import dev.gnacho.netgrip.data.dto.RootDto
import dev.gnacho.netgrip.data.dto.SystemDto
import dev.gnacho.netgrip.data.dto.WanDto
import dev.gnacho.netgrip.data.dto.WirelessRadioDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La salud se porta de useHealthScore del panel web: mismas penalizaciones y
 * mismos umbrales, sin drift ni puertos fisicos (el movil no los consulta).
 */
class HealthScoreTest {

    private var totalBytes = 400L * MIB
    private var availableBytes = 300L * MIB
    private var rootTotal = 128L * 1024
    private var rootFree = 96L * 1024

    private fun system(load: List<Double> = listOf(0.4, 0.3, 0.2)) = SystemDto(
        uptime = 3600,
        load = load,
        memory = MemoryDto(total = totalBytes, free = 10, available = availableBytes),
        root = RootDto(total = rootTotal, free = rootFree),
    )

    private val wanUp = WanDto(present = true, up = true, ipv4 = listOf("192.0.2.2"))

    @Test
    fun unRouterSanoPuntuaCien() {
        val health = HealthScoreCalculator.compute(system(), wanUp, ModeDto(mode = "router"))
        assertEquals(100, health.score)
        assertEquals(HealthTone.OK, health.tone)
        assertEquals(HealthLevel.EXCELLENT, health.level)
        assertTrue(health.reasons.isEmpty())
    }

    @Test
    fun laWanCaidaRestaCuarenta() {
        val health = HealthScoreCalculator.compute(
            system(),
            WanDto(present = true, up = false),
            ModeDto(mode = "router"),
        )
        assertEquals(60, health.score)
        assertEquals(HealthTone.WARN, health.tone)
        assertEquals(HealthLevel.ATTENTION, health.level)
        assertEquals(listOf(HealthReason.NoInternet), health.reasons)
    }

    @Test
    fun enModoApLaWanCaidaNoPenaliza() {
        val health = HealthScoreCalculator.compute(
            system(),
            WanDto(present = true, up = false),
            ModeDto(mode = "ap"),
        )
        assertEquals(100, health.score)
    }

    @Test
    fun memoriaAlSetentaYCincoRestaDiez() {
        availableBytes = 100L * MIB
        val health = HealthScoreCalculator.compute(system(), wanUp, ModeDto(mode = "router"))
        assertEquals(90, health.score)
        assertEquals(HealthReason.Memory(75), health.reasons.single())
    }

    @Test
    fun memoriaAlNoventaRestaVeinte() {
        availableBytes = 40L * MIB
        val health = HealthScoreCalculator.compute(system(), wanUp, ModeDto(mode = "router"))
        assertEquals(80, health.score)
        assertEquals(HealthReason.Memory(90), health.reasons.single())
    }

    @Test
    fun flashLibreBajoVeinteRestaDiez() {
        rootFree = 25L * 1024
        val health = HealthScoreCalculator.compute(system(), wanUp, ModeDto(mode = "router"))
        assertEquals(90, health.score)
        assertEquals(HealthReason.Flash(20), health.reasons.single())
    }

    @Test
    fun flashLibreAlDiezRestaVeinte() {
        rootFree = 12L * 1024
        val health = HealthScoreCalculator.compute(system(), wanUp, ModeDto(mode = "router"))
        assertEquals(80, health.score)
        assertEquals(HealthReason.Flash(9), health.reasons.single())
    }

    @Test
    fun cargaAltaRestaCincoODiez() {
        assertEquals(
            95,
            HealthScoreCalculator.compute(system(load = listOf(2.0)), wanUp, ModeDto(mode = "router")).score,
        )
        assertEquals(
            90,
            HealthScoreCalculator.compute(system(load = listOf(4.0)), wanUp, ModeDto(mode = "router")).score,
        )
    }

    @Test
    fun todasLasRadiosApagadasRestanDiez() {
        val radios = listOf(
            WirelessRadioDto(name = "radio0", up = false, band = "2g"),
            WirelessRadioDto(name = "radio1", up = false, band = "5g"),
        )
        val health = HealthScoreCalculator.compute(
            system(),
            wanUp,
            ModeDto(mode = "ap", hasWifi = true),
            radios,
        )
        assertEquals(90, health.score)
        assertEquals(HealthReason.WifiOff, health.reasons.single())
    }

    @Test
    fun laPuntuacionNuncaBajaDeCeroNiPasaDeCien() {
        availableBytes = 1
        rootFree = 1
        val health = HealthScoreCalculator.compute(
            system(load = listOf(9.0)),
            WanDto(present = true, up = false),
            ModeDto(mode = "router", hasWifi = true),
            listOf(WirelessRadioDto(name = "radio0", up = false, band = "2g")),
        )
        assertEquals(0, health.score)
        assertEquals(HealthTone.DANGER, health.tone)
        assertEquals(HealthLevel.CRITICAL, health.level)
    }

    @Test
    fun sinDatosNoInventaPenalizaciones() {
        val health = HealthScoreCalculator.compute()
        assertEquals(100, health.score)
        assertTrue(health.reasons.isEmpty())
    }

    private companion object {
        const val MIB = 1024L * 1024L
    }
}
