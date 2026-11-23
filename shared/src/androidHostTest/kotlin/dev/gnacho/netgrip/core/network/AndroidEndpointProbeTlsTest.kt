package dev.gnacho.netgrip.core.network

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import dev.gnacho.netgrip.core.storage.SecureStorage
import dev.gnacho.netgrip.data.api.NetGripApi
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class TlsStorage : SecureStorage {
    val data = mutableMapOf<String, String>()
    override suspend fun save(key: String, value: String) { data[key] = value }
    override suspend fun read(key: String): String? = data[key]
    override suspend fun delete(key: String) { data.remove(key) }
}

class AndroidEndpointProbeTlsTest {

    @Test
    fun unknownPinnedAndChangedCertificatesFollowTheSecurityGate() = runTest {
        val storage = TlsStorage()
        val store = CertificateFingerprintStore(storage)
        val first = temporaryHttpsServer()
        val second = temporaryHttpsServer()
        try {
            first.start()
            val unknown = DefaultEndpointProbe(storage).probe(first.baseUrl)

            val confirmation = assertIs<EndpointProbeResult.CertificateConfirmationRequired>(unknown)
            assertEquals(0, first.requests.get())
            store.save(confirmation.host, confirmation.fingerprint)

            val pinned = DefaultEndpointProbe(storage).probe(first.baseUrl)
            assertIs<EndpointProbeResult.Resolved>(pinned)
            assertEquals(1, first.requests.get())

            second.start()
            val changed = DefaultEndpointProbe(storage).probe(second.baseUrl)
            assertIs<EndpointProbeResult.CertificateChanged>(changed)
            assertEquals(0, second.requests.get())

            val pin = store.read("127.0.0.1")!!
            val login = NetGripApi(platformEngine(pin), second.baseUrl).login("root", "test-password")
            assertIs<ApiResult.CertificateChanged>(login)
            assertEquals(0, second.requests.get())
        } finally {
            first.close()
            second.close()
        }
    }
}

private class TemporaryHttpsServer(
    private val directory: Path,
    private val server: HttpsServer,
    val requests: AtomicInteger,
) {
    val baseUrl: String = "https://127.0.0.1:${server.address.port}"

    fun start() = server.start()

    fun close() {
        server.stop(0)
        directory.toFile().deleteRecursively()
    }
}

private fun temporaryHttpsServer(): TemporaryHttpsServer {
    val directory = Files.createTempDirectory("netgrip-mobile-tls-test")
    return try {
        val keyStorePath = directory.resolve("temporary.p12")
        generateTemporaryCertificate(directory, keyStorePath)
        val password = TEST_KEYSTORE_PASSWORD.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        Files.newInputStream(keyStorePath).use { keyStore.load(it, password) }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagers.init(keyStore, password)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagers.keyManagers, null, null)
        val requests = AtomicInteger()
        val server = HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.httpsConfigurator = object : HttpsConfigurator(sslContext) {
            override fun configure(params: HttpsParameters) {
                params.setSSLParameters(sslContext.defaultSSLParameters)
            }
        }
        server.createContext("/api/me") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
        }
        TemporaryHttpsServer(directory, server, requests)
    } catch (error: Throwable) {
        directory.toFile().deleteRecursively()
        throw error
    }
}

private fun generateTemporaryCertificate(directory: Path, keyStorePath: Path) {
    val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
    val process = ProcessBuilder(
        keytool,
        "-genkeypair",
        "-alias", "temporary",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "1",
        "-dname", "CN=temporary-router",
        "-keystore", keyStorePath.toString(),
        "-storetype", "PKCS12",
        "-storepass", TEST_KEYSTORE_PASSWORD,
        "-keypass", TEST_KEYSTORE_PASSWORD,
    ).directory(directory.toFile()).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    check(process.waitFor() == 0) { "keytool failed: $output" }
}

private const val TEST_KEYSTORE_PASSWORD = "temporary-test-only"
