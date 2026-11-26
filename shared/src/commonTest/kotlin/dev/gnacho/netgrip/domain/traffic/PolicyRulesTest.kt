package dev.gnacho.netgrip.domain.traffic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PolicyRulesTest {

    @Test
    fun amountsAcceptCommaAndDot() {
        assertEquals(1.5, PolicyRules.parseAmount("1,5"))
        assertEquals(1.5, PolicyRules.parseAmount(" 1.5 "))
        assertNull(PolicyRules.parseAmount(""))
        assertNull(PolicyRules.parseAmount("0"))
        assertNull(PolicyRules.parseAmount("-2"))
        assertNull(PolicyRules.parseAmount("dos"))
    }

    @Test
    fun limitsConvertToBytesInTheChosenUnit() {
        assertEquals(5L * 1024 * 1024 * 1024, PolicyRules.limitBytes("5", DataUnit.GIB))
        assertEquals(1_572_864L, PolicyRules.limitBytes("1,5", DataUnit.MIB))
        assertNull(PolicyRules.limitBytes("", DataUnit.GIB))
    }

    @Test
    fun bytesRoundTripToTheEditorWithoutNoise() {
        assertEquals("10", PolicyRules.amountText(10L * DataUnit.GIB.bytes, DataUnit.GIB))
        assertEquals("512", PolicyRules.amountText(512L * DataUnit.MIB.bytes, DataUnit.MIB))
        assertEquals("1,5", PolicyRules.amountText(1536L * DataUnit.MIB.bytes, DataUnit.GIB))
        assertEquals("", PolicyRules.amountText(0, DataUnit.GIB))
    }

    @Test
    fun quotaNeedsALimitAndAThrottleSpeedOnlyWhenThrottling() {
        assertNull(PolicyRules.validateQuota(1024, "notify", 0))
        assertNull(PolicyRules.validateQuota(1024, "throttle", 512))
        assertEquals("Escribe un tope mayor que cero", PolicyRules.validateQuota(null, "notify", 0))
        assertEquals("Al limitar hace falta una velocidad en kb/s", PolicyRules.validateQuota(1024, "throttle", 0))
        assertTrue(PolicyRules.validateQuota(1024, "throttle", 2_000_000)!!.contains("demasiado alta"))
    }

    @Test
    fun qosRejectsNegativeAndAbsurdSpeeds() {
        assertNull(PolicyRules.validateQos(0, 0))
        assertNull(PolicyRules.validateQos(100, 20))
        assertEquals("Las velocidades no pueden ser negativas", PolicyRules.validateQos(-1, 5))
        assertTrue(PolicyRules.validateQos(50_000, 0)!!.contains("Mb/s"))
    }

    @Test
    fun hoursAreParsedStrictly() {
        assertEquals(0, PolicyRules.parseHHMM("00:00"))
        assertEquals(18 * 60 + 30, PolicyRules.parseHHMM("18:30"))
        assertEquals(23 * 60 + 59, PolicyRules.parseHHMM("23:59"))
        assertNull(PolicyRules.parseHHMM("24:00"))
        assertNull(PolicyRules.parseHHMM("8:30"))
        assertNull(PolicyRules.parseHHMM("18-30"))
        assertNull(PolicyRules.parseHHMM(""))
    }

    @Test
    fun parentalNeedsDaysAndAWindow() {
        assertEquals("Elige al menos un día", PolicyRules.validateParental(emptySet(), "18:00", "20:00"))
        assertNull(PolicyRules.validateParental(setOf(1, 3), "18:00", "20:00"))
        assertEquals(
            "La hora de fin no puede ser la de inicio",
            PolicyRules.validateParental(setOf(1), "18:00", "18:00"),
        )
        assertTrue(PolicyRules.validateParental(setOf(9), "18:00", "20:00")!!.contains("Día no válido"))
        assertTrue(PolicyRules.validateParental(setOf(1), "25:00", "20:00")!!.contains("HH:MM"))
    }

    @Test
    fun daysAndSummaryReadLikeSpanish() {
        assertEquals("L, X", PolicyRules.daysLabel(listOf(1, 3)))
        assertEquals("Todos los días", PolicyRules.daysLabel(listOf(0, 1, 2, 3, 4, 5, 6)))
        assertEquals("Ningún día", PolicyRules.daysLabel(emptyList()))
        assertEquals("Pausado: bloqueado ahora", PolicyRules.parentalSummary(true, true, listOf(1), "18:00", "20:00"))
        assertEquals("Apagado", PolicyRules.parentalSummary(false, false, listOf(1), "18:00", "20:00"))
        assertEquals("18:00 a 20:00 · L", PolicyRules.parentalSummary(true, false, listOf(1), "18:00", "20:00"))
    }
}
