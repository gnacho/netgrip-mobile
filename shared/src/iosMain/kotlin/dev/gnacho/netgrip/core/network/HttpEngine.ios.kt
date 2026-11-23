package dev.gnacho.netgrip.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorServerCertificateHasBadDate
import platform.Foundation.NSURLErrorServerCertificateHasUnknownRoot
import platform.Foundation.NSURLErrorServerCertificateNotYetValid
import platform.Foundation.NSURLErrorServerCertificateUntrusted

actual fun platformEngine(certificatePin: CertificatePin?): HttpClientEngine {
    if (certificatePin != null) throw SelfSignedCertificatesUnsupportedException()
    return Darwin.create()
}

actual fun platformEndpointProbeTransport(): EndpointProbeTransport = IosEndpointProbeTransport()

private class IosEndpointProbeTransport : EndpointProbeTransport {
    override suspend fun probe(
        baseUrl: String,
        certificatePin: CertificatePin?,
    ): TransportProbeResult {
        if (certificatePin != null) {
            return TransportProbeResult.Unsupported(SELF_SIGNED_UNSUPPORTED_MESSAGE)
        }
        return try {
            HttpClient(Darwin.create()) {
                expectSuccess = false
                followRedirects = false
                install(HttpTimeout) {
                    connectTimeoutMillis = PROBE_CONNECT_TIMEOUT_MILLIS
                    socketTimeoutMillis = PROBE_SOCKET_TIMEOUT_MILLIS
                    requestTimeoutMillis = PROBE_REQUEST_TIMEOUT_MILLIS
                }
            }.use { client ->
                client.get("$baseUrl/api/me")
            }
            TransportProbeResult.Responded
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (error.isDarwinCertificateFailure()) {
                TransportProbeResult.Unsupported(SELF_SIGNED_UNSUPPORTED_MESSAGE)
            } else {
                TransportProbeResult.Unreachable
            }
        }
    }
}

private fun Throwable.isDarwinCertificateFailure(): Boolean {
    var current: Throwable? = this
    repeat(MAX_CAUSE_DEPTH) {
        val error = current as? DarwinHttpRequestException
        if (error != null && error.origin.domain == NSURLErrorDomain && error.origin.code in CERTIFICATE_ERRORS) {
            return true
        }
        current = current?.cause
    }
    return false
}

private val CERTIFICATE_ERRORS = setOf(
    NSURLErrorServerCertificateHasBadDate,
    NSURLErrorServerCertificateUntrusted,
    NSURLErrorServerCertificateHasUnknownRoot,
    NSURLErrorServerCertificateNotYetValid,
)

private const val PROBE_CONNECT_TIMEOUT_MILLIS = 3_000L
private const val PROBE_SOCKET_TIMEOUT_MILLIS = 3_000L
private const val PROBE_REQUEST_TIMEOUT_MILLIS = 5_000L
private const val MAX_CAUSE_DEPTH = 16
