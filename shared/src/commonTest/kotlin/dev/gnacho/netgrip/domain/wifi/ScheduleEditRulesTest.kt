package dev.gnacho.netgrip.domain.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reglas del horario WiFi: horas, dias, ventana y resumen. */
class ScheduleEditRulesTest {

    @Test
    fun lasHorasSeValidanEnFormatoDe24Horas() {
        assertTrue(ScheduleEditRules.isTime("00:00"))
        assertTrue(ScheduleEditRules.isTime("07:30"))
        assertTrue(ScheduleEditRules.isTime("23:59"))
        assertFalse(ScheduleEditRules.isTime("24:00"))
        assertFalse(ScheduleEditRules.isTime("7:30"))
        assertFalse(ScheduleEditRules.isTime("07:60"))
        assertFalse(ScheduleEditRules.isTime(""))
        assertEquals("Escribe las horas como 07:30", ScheduleEditRules.timeError("7:30", "23:00"))
    }

    @Test
    fun unHorarioActivoNecesitaAlMenosUnDiaYHorasValidas() {
        assertEquals(
            "Elige al menos un día",
            ScheduleEditRules.configError(true, emptySet(), "07:00", "23:00"),
        )
        assertNull(ScheduleEditRules.configError(true, setOf(1, 2, 3), "07:00", "23:00"))
        // Desactivado no se valida: se guarda lo que hubiera.
        assertNull(ScheduleEditRules.configError(false, emptySet(), "", ""))
    }

    @Test
    fun laVentanaAvisaCuandoCruzaMedianoche() {
        assertEquals("07:00 a 23:00", ScheduleEditRules.windowLabel("07:00", "23:00"))
        assertEquals(
            "22:00 a 06:00 (cruza medianoche)",
            ScheduleEditRules.windowLabel("22:00", "06:00"),
        )
        assertEquals("", ScheduleEditRules.windowLabel("", "06:00"))
    }

    @Test
    fun losDiasSeResumenEnUnaLinea() {
        assertEquals("sin días", ScheduleEditRules.daysSummary(emptySet()))
        assertEquals("todos los días", ScheduleEditRules.daysSummary(setOf(0, 1, 2, 3, 4, 5, 6)))
        assertEquals("L M X J V", ScheduleEditRules.daysSummary(setOf(1, 2, 3, 4, 5)))
        assertEquals("D S", ScheduleEditRules.daysSummary(setOf(0, 6)))
    }

    @Test
    fun elEstadoExplicaPorQueEstaApagada() {
        assertEquals(
            "Sin horario",
            ScheduleEditRules.stateLabel(false, false, false, false, false),
        )
        assertEquals(
            "Apagada a mano",
            ScheduleEditRules.stateLabel(true, true, true, false, true),
        )
        assertEquals(
            "Apagada por el horario ahora",
            ScheduleEditRules.stateLabel(true, true, false, true, true),
        )
        assertEquals(
            "Horario desactivado",
            ScheduleEditRules.stateLabel(true, false, false, false, false),
        )
        assertEquals(
            "Dentro de la ventana",
            ScheduleEditRules.stateLabel(true, true, false, false, false),
        )
    }
}
