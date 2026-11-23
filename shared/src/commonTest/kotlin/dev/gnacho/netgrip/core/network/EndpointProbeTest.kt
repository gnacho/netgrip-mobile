package dev.gnacho.netgrip.core.network

import dev.gnacho.netgrip.core.storage.SecureStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private class ProbeStorage : SecureStorage {
    val data = mutableMapOf<String, String>()
    override suspend fun save(key: String, value: String) { data[key] = value }
    override suspend fun read(key: String): String? = data[key]
    override suspend fun delete(key: String) { data.remove(key) }
}

private data class ProbeCall(val baseUrl: String, val pin: CertificatePin?)

private class FakeProbeTransport(
    private val handler: (String) -> TransportProbeResult,
) : EndpointProbeTransport {
    val calls = mutableListOf<ProbeCall>()

    override suspend fun probe(baseUrl: String, certificatePin: CertificatePin?): TransportProbeResult {
        calls += ProbeCall(baseUrl, certificatePin)
        return handler(baseUrl)
    }
}

private val fingerprintA = "00".repeat(32)
private val fingerprintB = "11".repeat(32)

class EndpointProbeTest {

    @Test
    fun systemTrustedHttpsWinsWithoutPromptOrHttpProbe() = runTest {
        val transport = FakeProbeTransport { TransportProbeResult.Responded }
        val result = probe(transport)

        val resolved = assertIs<EndpointProbeResult.Resolved>(result)
        assertEquals("https://router.local:8080", resolved.baseUrl)
        assertNull(resolved.certificatePin)
        assertEquals(listOf("https://router.local:8080"), transport.calls.map { it.baseUrl })
    }

    @Test
    fun httpIsOnlyTriedWhenHttpsDoesNotRespond() = runTest {
        val transport = FakeProbeTransport { url ->
            if (url.startsWith("https://")) TransportProbeResult.Unreachable else TransportProbeResult.Responded
        }
        val result = probe(transport)

        val resolved = assertIs<EndpointProbeResult.Resolved>(result)
        assertEquals("http://router.local:8080", resolved.baseUrl)
        assertEquals(
            listOf("https://router.local:8080", "http://router.local:8080"),
            transport.calls.map { it.baseUrl },
        )
    }

    @Test
    fun unknownHttpsCertificateNeverFallsBackToHttp() = runTest {
        val transport = FakeProbeTransport { TransportProbeResult.UnknownCertificate(fingerprintA) }
        val result = probe(transport)

        val confirmation = assertIs<EndpointProbeResult.CertificateConfirmationRequired>(result)
        assertEquals("router.local", confirmation.host)
        assertEquals(fingerprintA, confirmation.fingerprint)
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun changedPinnedCertificateIsRejectedWithoutHttpProbe() = runTest {
        val storage = ProbeStorage()
        CertificateFingerprintStore(storage).save("router.local", fingerprintA)
        val transport = FakeProbeTransport { TransportProbeResult.UnknownCertificate(fingerprintB) }

        val result = probe(transport, storage)

        val changed = assertIs<EndpointProbeResult.CertificateChanged>(result)
        assertEquals(fingerprintA, changed.expectedFingerprint)
        assertEquals(fingerprintB, changed.actualFingerprint)
        assertEquals(1, transport.calls.size)
    }

    @Test
    fun acceptedPinnedCertificateIsReusedForHttps() = runTest {
        val storage = ProbeStorage()
        val pin = CertificateFingerprintStore(storage).save("router.local", fingerprintA)
        val transport = FakeProbeTransport { TransportProbeResult.Responded }

        val result = probe(transport, storage)

        val resolved = assertIs<EndpointProbeResult.Resolved>(result)
        assertEquals(pin, resolved.certificatePin)
        assertEquals(pin, transport.calls.single().pin)
    }

    @Test
    fun storedPinPreventsHttpDowngrade() = runTest {
        val storage = ProbeStorage()
        CertificateFingerprintStore(storage).save("router.local", fingerprintA)
        val transport = FakeProbeTransport { TransportProbeResult.Unreachable }

        assertEquals(EndpointProbeResult.Unreachable, probe(transport, storage))
        assertEquals(listOf("https://router.local:8080"), transport.calls.map { it.baseUrl })
    }

    @Test
    fun explicitHttpsNeverDowngradesToHttp() = runTest {
        val transport = FakeProbeTransport { TransportProbeResult.Unreachable }
        val result = probe(transport, baseUrl = "https://router.local:8080")

        assertEquals(EndpointProbeResult.Unreachable, result)
        assertEquals(listOf("https://router.local:8080"), transport.calls.map { it.baseUrl })
    }

    @Test
    fun realNetworkFailureIsReportedAfterBothSchemes() = runTest {
        val transport = FakeProbeTransport { TransportProbeResult.Unreachable }

        assertEquals(EndpointProbeResult.Unreachable, probe(transport))
        assertEquals(2, transport.calls.size)
    }

    private suspend fun probe(
        transport: FakeProbeTransport,
        storage: ProbeStorage = ProbeStorage(),
        baseUrl: String = "http://router.local:8080",
    ): EndpointProbeResult = DefaultEndpointProbe(storage, transport = transport).probe(baseUrl)
}
