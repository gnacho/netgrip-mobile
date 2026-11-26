package dev.gnacho.netgrip.domain.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reglas de invitados e IoT: nombre, clave, banda y subred. */
class NetworkEditRulesTest {

    @Test
    fun unaRedApagadaNoSeValidaPorqueGuardaLaConfiguracion() {
        assertNull(NetworkEditRules.configError(enabled = false, ssid = "", key = ""))
        assertEquals(
            "El nombre de la red no puede quedar vacío",
            NetworkEditRules.configError(enabled = true, ssid = " ", key = "12345678"),
        )
    }

    @Test
    fun laClaveEsObligatoriaAlActivarYVaDe8A63() {
        assertEquals(
            "La clave no puede quedar vacía",
            NetworkEditRules.configError(enabled = true, ssid = "Invitados", key = ""),
        )
        assertEquals(
            "La clave necesita al menos 8 caracteres",
            NetworkEditRules.configError(enabled = true, ssid = "Invitados", key = "1234567"),
        )
        assertEquals(
            "La clave no puede pasar de 63 caracteres",
            NetworkEditRules.configError(enabled = true, ssid = "Invitados", key = "x".repeat(64)),
        )
        assertNull(NetworkEditRules.configError(enabled = true, ssid = "Invitados", key = "12345678"))
    }

    @Test
    fun editarUnaRedExistenteNoExigeReescribirLaClave() {
        assertTrue(NetworkEditRules.guestNeedsKey(""))
        assertFalse(NetworkEditRules.guestNeedsKey("Invitados"))
        assertNull(
            NetworkEditRules.configError(
                enabled = true,
                ssid = "Invitados",
                key = "",
                keyRequired = NetworkEditRules.guestNeedsKey("Invitados"),
            ),
        )
        assertEquals(
            "La clave no puede quedar vacía",
            NetworkEditRules.configError(
                enabled = true,
                ssid = "Invitados",
                key = "",
                keyRequired = NetworkEditRules.guestNeedsKey(""),
            ),
        )
    }

    @Test
    fun laSubredVaciaEsValidaYUnaIpv4MalEscritaNo() {
        assertNull(NetworkEditRules.subnetError(""))
        assertNull(NetworkEditRules.subnetError("192.168.9.1"))
        assertEquals(
            "Escribe una IPv4, por ejemplo 192.168.9.1",
            NetworkEditRules.subnetError("192.168.9"),
        )
        assertEquals(
            "Cada número de la IPv4 va de 0 a 255",
            NetworkEditRules.subnetError("192.168.300.1"),
        )
        assertEquals(
            "Esa dirección es de red o de broadcast, usa otra",
            NetworkEditRules.subnetError("192.168.9.0"),
        )
        assertEquals(
            "Esa dirección es de red o de broadcast, usa otra",
            NetworkEditRules.subnetError("192.168.9.255"),
        )
    }

    @Test
    fun lasBandasSeEtiquetanEnCastellano() {
        assertEquals(listOf("both", "2g", "5g"), NetworkEditRules.bands)
        assertEquals("2,4 y 5 GHz", NetworkEditRules.bandLabel("both"))
        assertEquals("2,4 GHz", NetworkEditRules.bandLabel("2g"))
        assertEquals("5 GHz", NetworkEditRules.bandLabel("5g"))
    }

    @Test
    fun elResumenNoInventaClientesCuandoEstaApagada() {
        assertEquals("Desactivada", NetworkEditRules.summary(false, "Invitados", 3))
        assertEquals("Sin nombre configurado", NetworkEditRules.summary(true, "", 0))
        assertEquals("Invitados · 1 cliente", NetworkEditRules.summary(true, "Invitados", 1))
        assertEquals("Invitados · 4 clientes", NetworkEditRules.summary(true, "Invitados", 4))
    }
}
