package dev.gnacho.netgrip.domain.storage

import dev.gnacho.netgrip.data.dto.StorageDeviceDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageRulesTest {

    @Test
    fun theSuggestionUsesTheDeviceNameInsideMnt() {
        assertEquals("/mnt/sda1", StorageRules.suggestTarget("/dev/sda1"))
        assertEquals("/mnt/sda", StorageRules.suggestTarget("/dev/sda"))
        assertEquals("/mnt/usb", StorageRules.suggestTarget(""))
    }

    @Test
    fun targetsAreValidatedLikeTheRouterDoes() {
        assertNull(StorageRules.validateTarget("/mnt/disco"))
        assertNull(StorageRules.validateTarget(" /mnt/disco-usb_2.0/ "))
        assertEquals("Escribe un punto de montaje", StorageRules.validateTarget("  "))
        assertTrue(StorageRules.validateTarget("/media/disco")!!.contains("/mnt"))
        assertTrue(StorageRules.validateTarget("/mnt/")!!.contains("/mnt"))
        assertTrue(StorageRules.validateTarget("/mnt/disco/nuevo")!!.contains("Solo letras"))
        assertTrue(StorageRules.validateTarget("/mnt/con espacio")!!.contains("Solo letras"))
    }

    @Test
    fun onlyPanelMountsCanBeUnmounted() {
        val managed = StorageDeviceDto(path = "/dev/sda1", mountPoint = "/mnt/sda1", managed = true)
        val manual = StorageDeviceDto(path = "/dev/sdb1", mountPoint = "/mnt/manual", managed = false)
        val loose = StorageDeviceDto(path = "/dev/sdc1")
        assertTrue(StorageRules.canUnmount(managed))
        assertFalse(StorageRules.canUnmount(manual))
        assertFalse(StorageRules.canUnmount(loose))
        assertTrue(StorageRules.mountedOutsidePanel(manual))
        assertFalse(StorageRules.mountedOutsidePanel(managed))
        assertFalse(StorageRules.mountedOutsidePanel(loose))
    }

    @Test
    fun devicePathsFollowTheServerPattern() {
        assertTrue(StorageRules.isDevicePath("/dev/sda1"))
        assertTrue(StorageRules.isDevicePath("/dev/sdz"))
        assertFalse(StorageRules.isDevicePath("/dev/nvme0n1"))
        assertFalse(StorageRules.isDevicePath("/dev/sda1p1"))
    }

    @Test
    fun usageIsAPercentageWithGuards() {
        assertEquals(50, StorageRules.usedPercent(StorageDeviceDto(sizeBytes = 100, usedBytes = 50)))
        assertEquals(0, StorageRules.usedPercent(StorageDeviceDto(sizeBytes = 0, usedBytes = 10)))
        assertEquals(100, StorageRules.usedPercent(StorageDeviceDto(sizeBytes = 10, usedBytes = 40)))
    }
}
