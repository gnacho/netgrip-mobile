package dev.gnacho.netgrip.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CancellationException

actual fun platformEngine(certificatePin: CertificatePin?): HttpClientEngine =
    if (certificatePin == null) OkHttp.create() else androidEngine(certificatePin)

actual fun platformEndpointProbeTransport(): EndpointProbeTransport = AndroidEndpointProbeTransport()

private class AndroidEndpointProbeTransport : EndpointProbeTransport {
    override suspend fun probe(
        baseUrl: String,
        certificatePin: CertificatePin?,
    ): TransportProbeResult {
        val isHttps = baseUrl.startsWith("https://", ignoreCase = true)
        val host = certificatePin?.host ?: routerHost(baseUrl) ?: return TransportProbeResult.Unreachable
        val engine = if (isHttps) {
            androidEngine(certificatePin, host)
        } else {
            OkHttp.create()
        }
        return try {
            HttpClient(engine) {
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
            when (val certificateError = error.certificateValidationException()) {
                is UnknownCertificateException -> TransportProbeResult.UnknownCertificate(
                    certificateError.fingerprint,
                )
                is CertificateChangedException -> TransportProbeResult.UnknownCertificate(
                    certificateError.actualFingerprint,
                )
                is SelfSignedCertificatesUnsupportedException -> TransportProbeResult.Unsupported(
                    SELF_SIGNED_UNSUPPORTED_MESSAGE,
                )
                null -> TransportProbeResult.Unreachable
            }
        }
    }
}

private fun androidEngine(
    certificatePin: CertificatePin?,
    probeHost: String = certificatePin?.host ?: "",
): HttpClientEngine {
    val trustManager = PinningTrustManager(
        host = certificatePin?.host ?: probeHost,
        expectedFingerprint = certificatePin?.sha256,
    )
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(trustManager), null)
    return OkHttp.create {
        config {
            sslSocketFactory(sslContext.socketFactory, trustManager)
            hostnameVerifier(PinningHostnameVerifier(certificatePin))
        }
    }
}

private class PinningTrustManager(
    private val host: String,
    private val expectedFingerprint: String?,
) : X509TrustManager {
    private val systemTrustManager = systemTrustManager()

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        systemTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val certificate = chain?.firstOrNull()
        val actualFingerprint = certificate?.sha256Fingerprint()
        if (expectedFingerprint != null && actualFingerprint != null &&
            !fingerprintsMatch(expectedFingerprint, actualFingerprint)
        ) {
            throw CertificateChangedException(
                host = host,
                expectedFingerprint = expectedFingerprint,
                actualFingerprint = actualFingerprint,
            )
        }
        try {
            systemTrustManager.checkServerTrusted(chain, authType)
            return
        } catch (systemFailure: CertificateException) {
            if (certificate == null || actualFingerprint == null) throw systemFailure
            if (expectedFingerprint == null) {
                throw UnknownCertificateException(
                    host = host,
                    fingerprint = actualFingerprint,
                    cause = systemFailure,
                )
            }
            return
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = systemTrustManager.acceptedIssuers
}

private class PinningHostnameVerifier(private val certificatePin: CertificatePin?) : HostnameVerifier {
    private val systemVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

    override fun verify(hostname: String?, session: SSLSession?): Boolean {
        if (hostname == null || session == null) return false
        if (systemVerifier.verify(hostname, session)) return true
        val pin = certificatePin ?: return false
        if (!hostname.trim().removeSurrounding("[", "]").equals(pin.host, ignoreCase = true)) {
            return false
        }
        val certificate = runCatching {
            session.peerCertificates?.firstOrNull() as? X509Certificate
        }.getOrNull() ?: return false
        return fingerprintsMatch(pin.sha256, certificate.sha256Fingerprint())
    }
}

private fun systemTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        ?: throw CertificateException("No system X509 trust manager")
}

private fun X509Certificate.sha256Fingerprint(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(encoded)
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

private const val PROBE_CONNECT_TIMEOUT_MILLIS = 3_000L
private const val PROBE_SOCKET_TIMEOUT_MILLIS = 3_000L
private const val PROBE_REQUEST_TIMEOUT_MILLIS = 5_000L
