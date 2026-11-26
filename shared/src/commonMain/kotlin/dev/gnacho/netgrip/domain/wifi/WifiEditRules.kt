package dev.gnacho.netgrip.domain.wifi

/**
 * Reglas del editor WiFi: que se puede enviar y con que limites. El router
 * sigue validando y devolviendo su error; esto solo evita peticiones que la
 * app ya sabe que van a fallar y mantiene la clave fuera de la vista.
 */
object WifiEditRules {

    /** Seguridad seleccionable; el valor es el que entiende UCI. */
    data class Encryption(val value: String, val label: String)

    val WPA2 = Encryption("psk2", "WPA2")
    val WPA3 = Encryption("sae", "WPA3")
    val MIXED = Encryption("sae-mixed", "WPA2/WPA3")

    /**
     * Opciones del selector. "Abierta" solo aparece si la red ya lo estaba:
     * quitar el cifrado a una red existente no se ofrece desde la app.
     */
    fun encryptions(current: String): List<Encryption> {
        val base = listOf(WPA2, WPA3, MIXED)
        return if (isOpen(current)) base + Encryption("none", "Abierta") else base
    }

    /** Valor del selector para el cifrado actual; psk y variantes antiguas van a WPA2. */
    fun selectedEncryption(current: String): Encryption = when (current) {
        "sae" -> WPA3
        "sae-mixed" -> MIXED
        "none" -> Encryption("none", "Abierta")
        else -> WPA2
    }

    /** Etiqueta legible del valor UCI que sirve el router. */
    fun encryptionLabel(encryption: String): String = when (encryption) {
        "" -> ""
        "none" -> "Abierta"
        "sae" -> "WPA3"
        "sae-mixed" -> "WPA2/WPA3"
        "psk2", "psk", "psk-mixed", "psk2+ccmp", "psk-mixed+ccmp" -> "WPA2"
        else -> encryption
    }

    fun isOpen(encryption: String): Boolean =
        encryption.isBlank() || encryption.equals("none", ignoreCase = true)

    /** Un SSID util: 1 a 32 bytes, sin espacios alrededor. */
    fun normalizeSsid(raw: String): String = raw.trim()

    fun ssidError(raw: String): String? = when {
        raw.trim().isEmpty() -> "El nombre de la red no puede quedar vacío"
        raw.trim().encodeToByteArray().size > 32 -> "El nombre no puede pasar de 32 caracteres"
        else -> null
    }

    /**
     * La clave es de escritura: vacia significa "dejar la actual". Para una
     * red abierta no se envia. WPA2 y WPA3 aceptan entre 8 y 63 caracteres.
     */
    fun keyError(encryption: String, key: String): String? = when {
        key.isEmpty() -> null
        isOpen(encryption) -> "Una red abierta no lleva clave"
        key.length < 8 -> "La clave necesita al menos 8 caracteres"
        key.length > 63 -> "La clave no puede pasar de 63 caracteres"
        else -> null
    }

    /** Campos que cambian respecto al estado actual, para el resumen previo. */
    fun pendingChanges(
        currentSsid: String,
        currentEncryption: String,
        currentHidden: Boolean,
        currentDisabled: Boolean,
        ssid: String,
        encryption: String,
        hidden: Boolean,
        disabled: Boolean,
        keyProvided: Boolean,
    ): List<String> = buildList {
        if (normalizeSsid(ssid) != currentSsid) add("Nombre")
        if (!isOpen(encryption) && keyProvided) add("Clave")
        if (!isOpen(currentEncryption) && isOpen(encryption)) add("Quitar cifrado")
        else if (!isOpen(currentEncryption) && encryption != currentEncryption) add("Seguridad")
        if (hidden != currentHidden) add(if (hidden) "Ocultar la red" else "Dejar de ocultarla")
        if (disabled != currentDisabled) add(if (disabled) "Apagar la red" else "Encender la red")
    }

    /** Canales que acepta el panel por banda ("auto" va aparte). */
    fun channelsFor(band: String): List<String> = when (band.lowercase()) {
        "2g" -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13")
        "5g" -> listOf(
            "36", "40", "44", "48", "52", "56", "60", "64",
            "100", "104", "108", "112", "116", "120", "124", "128",
            "132", "136", "140", "144", "149", "153", "157", "161", "165",
        )
        "6g" -> (1..233 step 4).map { it.toString() }
        else -> emptyList()
    }

    /** Anchos de canal por banda; el prefijo (HT/VHT/HE) depende del estandar. */
    fun widthsFor(band: String): List<String> = when (band.lowercase()) {
        "2g" -> listOf("HT20", "HT40", "HE20", "HE40")
        "5g" -> listOf("VHT20", "VHT40", "VHT80", "VHT160", "HE20", "HE40", "HE80", "HE160")
        "6g" -> listOf("HE20", "HE40", "HE80", "HE160")
        else -> emptyList()
    }

    fun bandLabel(band: String): String = when (band.lowercase()) {
        "2g" -> "2,4 GHz"
        "5g" -> "5 GHz"
        "6g" -> "6 GHz"
        else -> band
    }
}
