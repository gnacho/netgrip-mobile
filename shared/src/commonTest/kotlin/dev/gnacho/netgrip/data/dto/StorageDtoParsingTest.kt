package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Los cuerpos son los que sirve el panel (internal/server handleStorageGet y
 * los structs de internal/modules/storage.go y storage_mount.go).
 */
class StorageDtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun theProbeCarriesDisksAndTheirMountState() {
        val probe = json.decodeFromString<StorageProbeDto>(
            """{"applicable":true,"supported":true,"usb_present":true,"usb_storage_loaded":true,
               "devices":[
                 {"name":"sda1","path":"/dev/sda1","fs_type":"ext4","size_bytes":1000,
                  "used_bytes":250,"free_bytes":750,"mount_point":"/mnt/sda1","managed":true},
                 {"name":"sdb1","path":"/dev/sdb1","fs_type":"vfat","size_bytes":500,
                  "used_bytes":0,"free_bytes":500}
               ],
               "services":[{"name":"samba4","running":true,"enabled":true,
                            "installed":true,"available":true,"paths":["/mnt/sda1"]}]}""",
        )
        assertEquals(2, probe.devices.size)
        assertEquals("/mnt/sda1", probe.devices.first().mountPoint)
        assertTrue(probe.devices.first().managed)
        // Sin montar, mount_point no viene y managed es false.
        assertNull(probe.devices[1].mountPoint)
        assertEquals(false, probe.devices[1].managed)
        assertEquals("samba4", probe.services.single().name)
        assertEquals(listOf("/mnt/sda1"), probe.services.single().paths)
    }

    @Test
    fun aRouterWithoutUsbIsNotAnError() {
        val probe = json.decodeFromString<StorageProbeDto>(
            """{"applicable":false,"supported":false,"usb_present":false,
               "usb_storage_loaded":false,"devices":[],"services":[]}""",
        )
        assertTrue(!probe.supported)
        assertTrue(probe.devices.isEmpty())
    }

    @Test
    fun theMountBodyCarriesTheReadOnlyFlag() {
        val body = Json.encodeToString(
            StorageMountRequest.serializer(),
            StorageMountRequest(device = "/dev/sda1", target = "/mnt/sda1", readOnly = true),
        )
        assertEquals(
            """{"device":"/dev/sda1","target":"/mnt/sda1","read_only":true}""",
            body,
        )
    }
}
