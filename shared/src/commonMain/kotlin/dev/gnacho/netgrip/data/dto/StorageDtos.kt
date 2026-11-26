package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /api/storage: discos del router, no los de la app.
 *
 * [applicable] dice si el equipo puede gestionar almacenamiento, [supported]
 * si tiene buses USB (aunque esté vacío) y [usbStorageLoaded] si el kernel
 * cargó el controlador: sin él un disco no aparece y la pantalla lo dice en
 * vez de fingir que no hay nada enchufado.
 */
@Serializable
data class StorageProbeDto(
    val applicable: Boolean = false,
    val supported: Boolean = false,
    @SerialName("usb_present") val usbPresent: Boolean = false,
    @SerialName("usb_storage_loaded") val usbStorageLoaded: Boolean = false,
    val devices: List<StorageDeviceDto> = emptyList(),
    val services: List<StorageServiceDto> = emptyList(),
)

@Serializable
data class StorageDeviceDto(
    val name: String = "",
    val path: String = "",
    @SerialName("fs_type") val fsType: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("used_bytes") val usedBytes: Long = 0,
    @SerialName("free_bytes") val freeBytes: Long = 0,
    /** Ausente cuando el disco no está montado. */
    @SerialName("mount_point") val mountPoint: String? = null,
    /**
     * true solo si la entrada de fstab la creó el panel. Un montaje hecho a
     * mano no se desmonta desde aquí.
     */
    val managed: Boolean = false,
)

@Serializable
data class StorageServiceDto(
    val name: String = "",
    val running: Boolean = false,
    val enabled: Boolean = false,
    val installed: Boolean = false,
    val available: Boolean = false,
    val paths: List<String> = emptyList(),
)

/** POST /api/storage/mount. [target] tiene que ser /mnt/<algo>. */
@Serializable
data class StorageMountRequest(
    val device: String,
    val target: String,
    @SerialName("read_only") val readOnly: Boolean,
)

/** POST /api/storage/unmount. */
@Serializable
data class StorageUnmountRequest(
    val target: String,
)
