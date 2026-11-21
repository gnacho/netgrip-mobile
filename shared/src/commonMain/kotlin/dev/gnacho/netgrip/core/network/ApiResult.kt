package dev.gnacho.netgrip.core.network

/** Resultado de una llamada a la API de NetGrip. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data object Unauthorized : ApiResult<Nothing>
    data class RateLimited(val retryAfterSeconds: Long?) : ApiResult<Nothing>
    data class HttpError(val code: Int) : ApiResult<Nothing>
    data object Unreachable : ApiResult<Nothing>
    data class Unknown(val message: String?) : ApiResult<Nothing>
}
