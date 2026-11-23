package dev.gnacho.netgrip.core.network

sealed class CertificateValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UnknownCertificateException(
    val host: String,
    val fingerprint: String,
    cause: Throwable? = null,
) : CertificateValidationException("Unknown certificate for $host", cause)

class CertificateChangedException(
    val host: String,
    val expectedFingerprint: String,
    val actualFingerprint: String,
    cause: Throwable? = null,
) : CertificateValidationException("Certificate changed for $host", cause)

class SelfSignedCertificatesUnsupportedException :
    CertificateValidationException(SELF_SIGNED_UNSUPPORTED_MESSAGE)

fun Throwable.certificateValidationException(): CertificateValidationException? {
    var current: Throwable? = this
    repeat(MAX_CAUSE_DEPTH) {
        when (val error = current) {
            is CertificateValidationException -> return error
            else -> current = error?.cause
        }
    }
    return null
}

const val SELF_SIGNED_UNSUPPORTED_MESSAGE =
    "Los certificados autofirmados no están soportados en iOS. Usa HTTP o un certificado válido."

private const val MAX_CAUSE_DEPTH = 16
