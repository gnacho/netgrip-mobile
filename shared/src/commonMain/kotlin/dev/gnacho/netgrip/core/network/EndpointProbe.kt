package dev.gnacho.netgrip.core.network

import dev.gnacho.netgrip.core.storage.SecureStorage
import io.ktor.http.Url
import io.ktor.http.URLProtocol

interface EndpointProbe {
    suspend fun probe(baseUrl: String): EndpointProbeResult
}

interface EndpointProbeTransport {
    suspend fun probe(baseUrl: String, certificatePin: CertificatePin?): TransportProbeResult
}

sealed interface EndpointProbeResult {
    data class Resolved(
        val baseUrl: String,
        val certificatePin: CertificatePin? = null,
    ) : EndpointProbeResult

    data class CertificateConfirmationRequired(
        val baseUrl: String,
        val host: String,
        val fingerprint: String,
    ) : EndpointProbeResult

    data class CertificateChanged(
        val host: String,
        val expectedFingerprint: String,
        val actualFingerprint: String,
    ) : EndpointProbeResult

    data class Unsupported(val message: String) : EndpointProbeResult
    data object Unreachable : EndpointProbeResult
    data object InvalidUrl : EndpointProbeResult
}

sealed interface TransportProbeResult {
    data object Responded : TransportProbeResult
    data class UnknownCertificate(val fingerprint: String) : TransportProbeResult
    data class Unsupported(val message: String) : TransportProbeResult
    data object Unreachable : TransportProbeResult
}

class DefaultEndpointProbe(
    storage: SecureStorage,
    private val fingerprints: CertificateFingerprintStore = CertificateFingerprintStore(storage),
    private val transport: EndpointProbeTransport = platformEndpointProbeTransport(),
) : EndpointProbe {

    override suspend fun probe(baseUrl: String): EndpointProbeResult {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val parsed = runCatching { Url(cleanUrl) }.getOrNull() ?: return EndpointProbeResult.InvalidUrl
        if (parsed.protocol != URLProtocol.HTTP && parsed.protocol != URLProtocol.HTTPS) {
            return EndpointProbeResult.InvalidUrl
        }
        val host = routerHost(cleanUrl) ?: return EndpointProbeResult.InvalidUrl
        val pin = fingerprints.read(host)
        val httpsUrl = if (parsed.protocol == URLProtocol.HTTPS) {
            cleanUrl
        } else {
            "https://${cleanUrl.substringAfter("://")}"
        }
        val candidates = if (parsed.protocol == URLProtocol.HTTPS || pin != null) {
            listOf(httpsUrl)
        } else {
            listOf(httpsUrl, cleanUrl)
        }

        for (candidate in candidates) {
            val isHttps = candidate.startsWith("https://", ignoreCase = true)
            when (
                val result = transport.probe(
                    baseUrl = candidate,
                    certificatePin = pin.takeIf { isHttps },
                )
            ) {
                TransportProbeResult.Responded -> {
                    return EndpointProbeResult.Resolved(candidate, pin.takeIf { isHttps })
                }
                is TransportProbeResult.UnknownCertificate -> {
                    val actual = normalizeSha256Fingerprint(result.fingerprint)
                        ?: return EndpointProbeResult.Unreachable
                    return if (pin == null) {
                        EndpointProbeResult.CertificateConfirmationRequired(candidate, host, actual)
                    } else if (!fingerprintsMatch(pin.sha256, actual)) {
                        EndpointProbeResult.CertificateChanged(host, pin.sha256, actual)
                    } else {
                        EndpointProbeResult.Resolved(candidate, pin)
                    }
                }
                is TransportProbeResult.Unsupported -> return EndpointProbeResult.Unsupported(result.message)
                TransportProbeResult.Unreachable -> Unit
            }
        }
        return EndpointProbeResult.Unreachable
    }
}
