package dev.gnacho.netgrip.domain.clients

import dev.gnacho.netgrip.data.dto.ClientDto
import dev.gnacho.netgrip.data.dto.ClientMetaEntryDto

/**
 * Reglas puras de las acciones de escritura sobre un cliente. Todo lo que
 * decide si una accion es valida vive aqui para poder probarlo en la JVM sin
 * emulador ni router.
 */

/** Catalogo de tipos de dispositivo del panel web (app/src/components/clients/catalog.ts). */
object DeviceTypes {

    const val OTHER = "other"

    /** Claves validas en el mismo orden que el catalogo web. */
    val KEYS: List<String> = listOf(
        "pc",
        "laptop",
        "phone",
        "tablet",
        "camera",
        "wearable",
        "printer",
        "audio",
        "speaker",
        "tv",
        "gaming",
        "iot",
        "gateway",
        "nas",
        "server",
        "appliance",
        "ac",
        "vacuum",
        "pool",
        "mower",
        "other",
    )

    fun label(key: String?): String = when (key) {
        "pc" -> "Ordenador"
        "laptop" -> "Portátil"
        "phone" -> "Móvil"
        "tablet" -> "Tableta"
        "camera" -> "Cámara"
        "wearable" -> "Pulsera o reloj"
        "printer" -> "Impresora"
        "audio" -> "Audio"
        "speaker" -> "Altavoz"
        "tv" -> "Televisión"
        "gaming" -> "Consola"
        "iot" -> "Dispositivo IoT"
        "gateway" -> "Gateway"
        "nas" -> "NAS"
        "server" -> "Servidor"
        "appliance" -> "Electrodoméstico"
        "ac" -> "Aire acondicionado"
        "vacuum" -> "Aspiradora"
        "pool" -> "Piscina"
        "mower" -> "Cortacésped"
        OTHER -> "Otro"
        else -> if (key.isNullOrBlank()) "Sin tipo" else key
    }
}

/** Forma de una MAC y normalizacion a minusculas, como hace el servidor. */
object MacAddress {

    private val pattern = Regex("^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")

    /** Devuelve la MAC normalizada o null si no tiene forma valida. */
    fun normalize(raw: String): String? {
        val candidate = raw.trim().lowercase()
        return candidate.takeIf { pattern.matches(it) }
    }

    fun isValid(raw: String): Boolean = normalize(raw) != null
}

/** Validacion de la ficha editable (nombre y tipo de dispositivo). */
object ClientMetaRules {

    const val MAX_NAME = 40

    /** Recorta a 40 caracteres despues de quitar espacios sobrantes. */
    fun normalizeName(raw: String): String = raw.trim().take(MAX_NAME)

    /** Clave de tipo valida, o "" cuando el usuario elige "Sin tipo". */
    fun normalizeDeviceType(raw: String): String {
        val key = raw.trim().lowercase()
        return if (key in DeviceTypes.KEYS) key else if (key.isEmpty()) "" else DeviceTypes.OTHER
    }

    fun hasOverride(meta: Map<String, ClientMetaEntryDto>, mac: String): Boolean {
        val key = MacAddress.normalize(mac) ?: return false
        val entry = meta[key] ?: return false
        return entry.name.isNotBlank() || entry.deviceType.isNotBlank()
    }

    /** El tipo que muestra el formulario: la ficha guardada o "" si no hay. */
    fun currentDeviceType(meta: Map<String, ClientMetaEntryDto>, mac: String): String {
        val key = MacAddress.normalize(mac) ?: return ""
        return meta[key]?.deviceType.orEmpty()
    }

    fun currentName(meta: Map<String, ClientMetaEntryDto>, mac: String): String {
        val key = MacAddress.normalize(mac) ?: return ""
        return meta[key]?.name.orEmpty()
    }

    /**
     * Nombre que debe precargar el formulario. Si no hay override se parte del
     * que anuncia el cliente: abrir el dialogo y guardar solo el tipo no debe
     * borrar el nombre.
     */
    fun effectiveName(meta: Map<String, ClientMetaEntryDto>, client: ClientDto): String =
        currentName(meta, client.mac).ifBlank { client.name }

    /** Tipo que debe precargar el formulario, con el mismo respaldo. */
    fun effectiveDeviceType(meta: Map<String, ClientMetaEntryDto>, client: ClientDto): String =
        currentDeviceType(meta, client.mac).ifBlank { client.deviceType.orEmpty() }
}

/** Reglas de bloqueo y desbloqueo por bandas. */
object ClientBlockRules {

    /** Bandas que entiende el router, en el orden canonico. */
    val KNOWN_BANDS: List<String> = listOf("2g", "5g", "6g")

    /** Bandas del payload /api/clients que el servidor reconoce. */
    fun availableBands(bands: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        bands.forEach { raw ->
            val key = raw.trim().lowercase()
            if (key in KNOWN_BANDS) seen += key
        }
        return seen.toList()
    }

    /**
     * Tipo que espera POST /api/clients/block. Nunca se usa Client.type tal
     * cual: el servidor solo distingue "cable" del resto, y "wifi24"/"wifi5"
     * no son bandas.
     */
    fun requestType(client: ClientDto): String = if (client.type == "cable") "cable" else "wifi"

    fun isCable(client: ClientDto): Boolean = client.type == "cable"

    /** Bloquear exige blockable y nunca aplica al propio dispositivo. */
    fun canBlock(client: ClientDto): Boolean = client.blockable && !client.self

    /**
     * Opciones de banda para el dialogo de bloqueo: "" (todas) y una entrada
     * por banda presente en el payload. En cable solo hay una opcion, "".
     */
    fun bandOptions(client: ClientDto, bands: List<String>): List<String> =
        if (isCable(client)) listOf("") else listOf("") + availableBands(bands)

    fun bandLabel(band: String): String = when (band) {
        "" -> "Todas las bandas"
        "2g" -> "2,4 GHz"
        "5g" -> "5 GHz"
        "6g" -> "6 GHz"
        else -> band
    }

    /** Bandas donde consta un bloqueo manual actual. */
    fun blockedBands(client: ClientDto): List<String> =
        client.blockedOn.map { it.trim().lowercase() }.filter { it in KNOWN_BANDS }.distinct()

    /** Opciones de desbloqueo: cada banda bloqueada y, si hay varias, todas. */
    fun unblockOptions(client: ClientDto): List<String> {
        val bands = blockedBands(client)
        return when {
            bands.size > 1 -> bands + ""
            bands.size == 1 -> bands
            client.blocked -> listOf("")
            else -> emptyList()
        }
    }

    fun canUnblock(client: ClientDto): Boolean = !client.self && unblockOptions(client).isNotEmpty()

    /** Estados posibles del bloqueo, para las etiquetas del detalle. */
    fun stateLabel(client: ClientDto): String {
        val bands = blockedBands(client)
        return when {
            bands.isNotEmpty() -> "Bloqueado en " + bands.joinToString(" + ") { bandLabel(it) }
            client.blocked -> "Bloqueado"
            else -> "Sin bloquear"
        }
    }
}

/** Reserva DHCP limitada a la IP actual del cliente. */
sealed interface ReservationAction {
    data class SetCurrentIp(val ip: String) : ReservationAction
    data object Remove : ReservationAction
    data object Unavailable : ReservationAction
}

object ClientReservationRules {

    /**
     * No hay campo libre de IP a proposito: solo se fija la IP que el router
     * ya esta sirviendo al cliente, y solo si la marca como reservable.
     */
    fun actionFor(client: ClientDto): ReservationAction {
        if (client.self) return ReservationAction.Unavailable
        if (client.reserved) return ReservationAction.Remove
        val ip = client.ip?.trim().orEmpty()
        return if (client.reservable && ip.isNotEmpty()) {
            ReservationAction.SetCurrentIp(ip)
        } else {
            ReservationAction.Unavailable
        }
    }

    fun canReserve(client: ClientDto): Boolean = actionFor(client) != ReservationAction.Unavailable
}

/** Comprobaciones de que la relectura refleja el cambio pedido. */
object ClientWriteCheck {

    /**
     * true si el cliente refleja el bloqueo pedido, false si sigue igual,
     * null si el cliente ya no aparece en /api/clients (al bloquear un WiFi
     * se desconecta) y no se puede comprobar.
     */
    fun blockApplied(clients: List<ClientDto>, mac: String, blocked: Boolean, band: String): Boolean? {
        val normalized = MacAddress.normalize(mac) ?: mac.trim().lowercase()
        val client = clients.firstOrNull { it.mac.trim().lowercase() == normalized } ?: return null
        val requestedBand = band.trim().lowercase()
        return if (blocked) {
            if (requestedBand.isEmpty()) {
                client.blocked
            } else {
                requestedBand in ClientBlockRules.blockedBands(client)
            }
        } else {
            if (requestedBand.isEmpty()) {
                !client.blocked && ClientBlockRules.blockedBands(client).isEmpty()
            } else {
                requestedBand !in ClientBlockRules.blockedBands(client)
            }
        }
    }

    fun reservationApplied(clients: List<ClientDto>, mac: String, reserved: Boolean): Boolean? {
        val normalized = MacAddress.normalize(mac) ?: mac.trim().lowercase()
        val client = clients.firstOrNull { it.mac.trim().lowercase() == normalized } ?: return null
        return client.reserved == reserved
    }

    /** La respuesta del POST ya trae el mapa completo: no hace falta releer. */
    fun metaApplied(
        meta: Map<String, ClientMetaEntryDto>,
        mac: String,
        name: String,
        deviceType: String,
    ): Boolean {
        val key = MacAddress.normalize(mac) ?: mac.trim().lowercase()
        val entry = meta[key]
        return if (name.isEmpty() && deviceType.isEmpty()) {
            entry == null || (entry.name.isEmpty() && entry.deviceType.isEmpty())
        } else {
            entry != null && entry.name == name && entry.deviceType == deviceType
        }
    }
}

/** Textos de resultado de las acciones, en castellano y sin promesas falsas. */
object ClientActionMessages {

    fun metaSaved(): String = "Nombre y tipo guardados"

    fun metaCleared(): String = "Nombre y tipo restablecidos"

    fun blocked(band: String): String =
        if (band.isEmpty()) "Cliente bloqueado en todas las bandas" else "Cliente bloqueado en ${ClientBlockRules.bandLabel(band)}"

    fun unblocked(band: String): String =
        if (band.isEmpty()) "Cliente desbloqueado en todas las bandas" else "Cliente desbloqueado en ${ClientBlockRules.bandLabel(band)}"

    fun reserved(ip: String): String = "IP $ip fijada para este cliente"

    fun reservationRemoved(): String = "Reserva DHCP eliminada"

    fun wolSent(): String = "Paquete de despertar enviado"

    fun rolledBack(): String = "El router revirtió el cambio y dejó el estado anterior"

    fun stateUnchanged(): String =
        "El router aceptó la petición, pero el estado del cliente no ha cambiado. Revisa el panel web."

    fun reservationUnchanged(): String =
        "El router aceptó la petición, pero la reserva no aparece aplicada. Puede existir una reserva manual previa."

    fun clientAbsent(): String =
        "El router aplicó el cambio. El cliente no aparece ahora mismo en la lista de conectados."

    fun readFailed(reason: String): String =
        "El cambio se envió, pero no se pudo releer la lista: $reason"
}
