package dev.gnacho.netgrip.domain.storage

import dev.gnacho.netgrip.data.dto.StorageDeviceDto

/**
 * Reglas de los discos del router. El servidor valida lo mismo
 * (internal/modules/storage_mount.go: reStorageTarget), pero la app comprueba
 * antes para no mandar una escritura que solo puede fallar y para explicar el
 * motivo en el propio formulario.
 */
object StorageRules {

    private val TARGET = Regex("^/mnt/[A-Za-z0-9._-]+$")
    private val DEVICE = Regex("^/dev/sd[a-z][0-9]*$")

    /** Punto de montaje propuesto para un dispositivo: /dev/sda1 -> /mnt/sda1. */
    fun suggestTarget(device: String): String {
        val name = device.substringAfterLast('/').ifBlank { "usb" }
        return "/mnt/$name"
    }

    /** Recorta espacios y barras finales, sin inventar el prefijo. */
    fun normalizeTarget(raw: String): String = raw.trim().trimEnd('/')

    /** Motivo por el que el punto de montaje no vale, o null si vale. */
    fun validateTarget(raw: String): String? {
        val target = normalizeTarget(raw)
        return when {
            target.isBlank() -> "Escribe un punto de montaje"
            !target.startsWith("/mnt/") -> "Tiene que colgar de /mnt, por ejemplo /mnt/disco"
            !TARGET.matches(target) -> "Solo letras, números, punto, guion y guion bajo"
            else -> null
        }
    }

    fun isDevicePath(path: String): Boolean = DEVICE.matches(path.trim())

    /** Montado por el panel: es lo unico que se puede desmontar desde aqui. */
    fun canUnmount(device: StorageDeviceDto): Boolean =
        !device.mountPoint.isNullOrBlank() && device.managed

    /** Montado a mano: se ve, pero no se toca. */
    fun mountedOutsidePanel(device: StorageDeviceDto): Boolean =
        !device.mountPoint.isNullOrBlank() && !device.managed

    /** Porcentaje usado del disco, 0 si el tamano no es valido. */
    fun usedPercent(device: StorageDeviceDto): Int {
        if (device.sizeBytes <= 0) return 0
        return ((device.usedBytes * 100.0) / device.sizeBytes).toInt().coerceIn(0, 100)
    }

    /**
     * El router informa del uso solo cuando lo mide. Un disco montado a mano
     * llega con tamano y con used/free a cero: pintar "0 B" seria afirmar algo
     * que no midio.
     */
    fun hasUsage(device: StorageDeviceDto): Boolean =
        device.usedBytes > 0 || device.freeBytes > 0
}
