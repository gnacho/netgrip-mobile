package dev.gnacho.netgrip.core.network

import dev.gnacho.netgrip.core.storage.SecureStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FingerprintStorage : SecureStorage {
    val data = mutableMapOf<String, String>()
    override suspend fun save(key: String, value: String) { data[key] = value }
    override suspend fun read(key: String): String? = data[key]
    override suspend fun delete(key: String) { data.remove(key) }
}

class CertificateFingerprintTest {

    @Test
    fun fingerprintIsFormattedAsUppercaseColonSeparatedBytes() {
        val raw = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
        assertEquals(
            "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF",
            formatSha256Fingerprint(raw),
        )
    }

    @Test
    fun fingerprintComparisonIgnoresCaseAndColons() {
        val raw = "ab".repeat(32)
        assertTrue(fingerprintsMatch(raw, formatSha256Fingerprint(raw).lowercase()))
        assertFalse(fingerprintsMatch(raw, "ac".repeat(32)))
    }

    @Test
    fun fingerprintsPersistPerNormalizedHost() = runTest {
        val storage = FingerprintStorage()
        val store = CertificateFingerprintStore(storage)
        val saved = store.save("ROUTER.LOCAL", formatSha256Fingerprint("cd".repeat(32)))

        val restored = CertificateFingerprintStore(storage).read("router.local")
        assertEquals(saved, restored)
        assertEquals("cd".repeat(32), restored?.sha256)
        assertEquals(saved, store.readForUrl("https://router.local:8080"))
        assertNull(store.read("other.local"))
        assertTrue(storage.data.values.all { ':' !in it })
    }

    @Test
    fun malformedPersistedFingerprintIsNotTrusted() = runTest {
        val storage = FingerprintStorage()
        storage.save("tls.fingerprint.router.local", "not-a-sha256")

        assertNull(CertificateFingerprintStore(storage).read("router.local"))
    }
}
