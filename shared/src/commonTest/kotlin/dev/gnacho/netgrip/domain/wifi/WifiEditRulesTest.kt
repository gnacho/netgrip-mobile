package dev.gnacho.netgrip.domain.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reglas del editor WiFi: limites, opciones y resumen de cambios. */
class WifiEditRulesTest {

    @Test
    fun unaRedAbiertaNoOfreceQuitarElCifradoNiAlReves() {
        val cerrada = WifiEditRules.encryptions("psk2").map { it.value }
        assertEquals(listOf("psk2", "sae", "sae-mixed"), cerrada)
        assertFalse(cerrada.contains("none"))

        val abierta = WifiEditRules.encryptions("none").map { it.value }
        assertTrue(abierta.contains("none"))
    }

    @Test
    fun lasVariantesAntiguasSeMuestranComoWpa2() {
        assertEquals("psk2", WifiEditRules.selectedEncryption("psk").value)
        assertEquals("psk2", WifiEditRules.selectedEncryption("psk-mixed").value)
        assertEquals("psk2", WifiEditRules.selectedEncryption("").value)
        assertEquals("sae", WifiEditRules.selectedEncryption("sae").value)
        assertEquals("sae-mixed", WifiEditRules.selectedEncryption("sae-mixed").value)
    }

    @Test
    fun elSsidNoPuedeQuedarVacioNiPasarDe32Bytes() {
        assertEquals("casa", WifiEditRules.normalizeSsid("  casa "))
        assertEquals("El nombre de la red no puede quedar vacío", WifiEditRules.ssidError("   "))
        assertNull(WifiEditRules.ssidError("x".repeat(32)))
        assertEquals(
            "El nombre no puede pasar de 32 caracteres",
            WifiEditRules.ssidError("x".repeat(33)),
        )
    }

    @Test
    fun laClaveVaciaSignificaNoCambiarla() {
        assertNull(WifiEditRules.keyError("psk2", ""))
        assertNull(WifiEditRules.keyError("psk2", "12345678"))
        assertEquals(
            "La clave necesita al menos 8 caracteres",
            WifiEditRules.keyError("sae", "1234567"),
        )
        assertEquals(
            "La clave no puede pasar de 63 caracteres",
            WifiEditRules.keyError("sae-mixed", "x".repeat(64)),
        )
        assertEquals(
            "Una red abierta no lleva clave",
            WifiEditRules.keyError("none", "12345678"),
        )
    }

    @Test
    fun elResumenNombraCadaCambioYNoInventaClave() {
        val sinCambios = WifiEditRules.pendingChanges(
            currentSsid = "casa",
            currentEncryption = "psk2",
            currentHidden = false,
            currentDisabled = false,
            ssid = "casa ",
            encryption = "psk2",
            hidden = false,
            disabled = false,
            keyProvided = false,
        )
        assertTrue(sinCambios.isEmpty())

        val cambios = WifiEditRules.pendingChanges(
            currentSsid = "casa",
            currentEncryption = "psk2",
            currentHidden = false,
            currentDisabled = false,
            ssid = "casa-nueva",
            encryption = "sae",
            hidden = true,
            disabled = true,
            keyProvided = true,
        )
        assertEquals(listOf("Nombre", "Clave", "Seguridad", "Ocultar la red", "Apagar la red"), cambios)
    }

    @Test
    fun quitarElCifradoSeNombraAparteDeCambiarDeSeguridad() {
        val cambios = WifiEditRules.pendingChanges(
            currentSsid = "casa",
            currentEncryption = "psk2",
            currentHidden = false,
            currentDisabled = false,
            ssid = "casa",
            encryption = "none",
            hidden = false,
            disabled = false,
            keyProvided = false,
        )
        assertEquals(listOf("Quitar cifrado"), cambios)
    }

    @Test
    fun cerrarUnaRedAbiertaCuentaLaClaveComoSeguridadYClave() {
        val cambios = WifiEditRules.pendingChanges(
            currentSsid = "invitados",
            currentEncryption = "none",
            currentHidden = false,
            currentDisabled = false,
            ssid = "invitados",
            encryption = "psk2",
            hidden = false,
            disabled = false,
            keyProvided = true,
        )
        assertEquals(listOf("Clave"), cambios)
    }

    @Test
    fun losCanalesYAnchosSalenPorBanda() {
        assertTrue(WifiEditRules.channelsFor("2g").first() == "1")
        assertTrue(WifiEditRules.channelsFor("2g").contains("13"))
        assertFalse(WifiEditRules.channelsFor("2g").contains("14"))
        assertTrue(WifiEditRules.channelsFor("5g").contains("149"))
        assertFalse(WifiEditRules.channelsFor("5g").contains("200"))
        assertTrue(WifiEditRules.channelsFor("6g").contains("1"))
        assertTrue(WifiEditRules.channelsFor("").isEmpty())

        assertEquals(listOf("HT20", "HT40", "HE20", "HE40"), WifiEditRules.widthsFor("2g"))
        assertTrue(WifiEditRules.widthsFor("5g").contains("VHT160"))
        assertTrue(WifiEditRules.widthsFor("6g").contains("HE160"))
    }

    @Test
    fun lasBandasSeEtiquetanEnCastellano() {
        assertEquals("2,4 GHz", WifiEditRules.bandLabel("2g"))
        assertEquals("5 GHz", WifiEditRules.bandLabel("5g"))
        assertEquals("6 GHz", WifiEditRules.bandLabel("6g"))
    }
}
