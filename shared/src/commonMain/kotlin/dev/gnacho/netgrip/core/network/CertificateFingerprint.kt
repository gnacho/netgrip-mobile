package dev.gnacho.netgrip.core.network

import dev.gnacho.netgrip.core.storage.SecureStorage
import io.ktor.http.Url

data class CertificatePin(
    val host: String,
    val sha256: String,
)

class CertificateFingerprintStore(private val storage: SecureStorage) {

    suspend fun read(host: String): CertificatePin? {
        val normalizedHost = normalizeHost(host) ?: return null
        val fingerprint = storage.read("$KEY_PREFIX$normalizedHost") ?: return null
        val normalizedFingerprint = normalizeSha256Fingerprint(fingerprint) ?: return null
        return CertificatePin(normalizedHost, normalizedFingerprint)
    }

    suspend fun readForUrl(baseUrl: String): CertificatePin? =
        routerHost(baseUrl)?.let { read(it) }

    suspend fun save(host: String, fingerprint: String): CertificatePin {
        val normalizedHost = normalizeHost(host)
            ?: throw IllegalArgumentException("Router host is empty")
        val normalizedFingerprint = normalizeSha256Fingerprint(fingerprint)
            ?: throw IllegalArgumentException("Invalid SHA-256 fingerprint")
        storage.save("$KEY_PREFIX$normalizedHost", normalizedFingerprint)
        return CertificatePin(normalizedHost, normalizedFingerprint)
    }

    private companion object {
        const val KEY_PREFIX = "tls.fingerprint."
    }
}

fun routerHost(baseUrl: String): String? = runCatching {
    normalizeHost(Url(baseUrl).host)
}.getOrNull()

fun normalizeSha256Fingerprint(value: String): String? {
    val compact = value.filterNot { it == ':' }
    if (compact.length != SHA256_HEX_LENGTH || compact.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) {
        return null
    }
    return compact.lowercase()
}

fun formatSha256Fingerprint(value: String): String =
    normalizeSha256Fingerprint(value)
        ?.uppercase()
        ?.chunked(2)
        ?.joinToString(":")
        ?: value

fun fingerprintsMatch(first: String, second: String): Boolean {
    val left = normalizeSha256Fingerprint(first) ?: return false
    val right = normalizeSha256Fingerprint(second) ?: return false
    var difference = 0
    for (index in left.indices) {
        difference = difference or (left[index].code xor right[index].code)
    }
    return difference == 0
}

private fun normalizeHost(host: String): String? =
    host.trim().removeSurrounding("[", "]").lowercase().ifEmpty { null }

private const val SHA256_HEX_LENGTH = 64
