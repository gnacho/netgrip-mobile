package dev.gnacho.netgrip.session

import dev.gnacho.netgrip.core.network.ApiResult
import dev.gnacho.netgrip.core.storage.SecureStorage
import dev.gnacho.netgrip.data.api.NetGripApi

/**
 * Ciclo de vida de la sesion contra un router: restaurar, validar y cerrar.
 * Guarda el token de sesion cifrado y la ultima URL usada, nunca la
 * contrasena (ADR 0002). La multiplataforma de perfiles llega en la fase 2;
 * la clave ya va indexada por nombre de perfil.
 */
class SessionManager(private val storage: SecureStorage) {

    /** Ultima URL usada con exito, o null si no hay sesion previa. */
    suspend fun lastUrl(): String? = storage.read(KEY_LAST_URL)

    /** Restaura la cookie guardada y la valida contra GET /api/me. */
    suspend fun restore(api: NetGripApi, profileName: String = DEFAULT_PROFILE): Boolean =
        restoreWithResult(api, profileName) is ApiResult.Ok

    suspend fun restoreWithResult(api: NetGripApi, profileName: String = DEFAULT_PROFILE): ApiResult<Unit>? {
        val cookie = storage.read(cookieKey(profileName)) ?: return null
        api.restoreSessionCookie(cookie)
        val result = api.me()
        if (result !is ApiResult.Ok) storage.delete(cookieKey(profileName))
        return result
    }

    suspend fun onLoginOk(api: NetGripApi, baseUrl: String, profileName: String = DEFAULT_PROFILE) {
        val cookie = api.sessionCookie ?: return
        storage.save(cookieKey(profileName), cookie)
        storage.save(KEY_LAST_URL, baseUrl)
    }

    suspend fun logout(api: NetGripApi, profileName: String = DEFAULT_PROFILE) {
        api.logout()
        storage.delete(cookieKey(profileName))
        storage.delete(KEY_LAST_URL)
    }

    /**
     * Olvida la cookie sin llamar al router: se usa cuando ya respondio 401 y
     * no hay sesion que revocar. La ultima URL se conserva para reconectar.
     */
    suspend fun forgetSession(profileName: String = DEFAULT_PROFILE) {
        storage.delete(cookieKey(profileName))
    }

    /** Cookie guardada de un dispositivo, sin validarla. */
    suspend fun cookie(profileName: String): String? = storage.read(cookieKey(profileName))

    /**
     * Migra la cookie del perfil unico antiguo al perfil de un dispositivo.
     * Si el destino ya tiene cookie, no se toca.
     */
    suspend fun moveCookie(from: String, to: String) {
        if (from == to) return
        if (storage.read(cookieKey(to)) != null) return
        val cookie = storage.read(cookieKey(from)) ?: return
        storage.save(cookieKey(to), cookie)
        storage.delete(cookieKey(from))
    }

    /**
     * El perfil unico antiguo conserva su clave; los dispositivos usan
     * "session.device.<id>" para no chocar con el ni entre ellos.
     */
    private fun cookieKey(profileName: String) =
        if (profileName == DEFAULT_PROFILE) "session.$profileName" else "session.device.$profileName"

    companion object {
        const val DEFAULT_PROFILE = "default"
        private const val KEY_LAST_URL = "last.url"
    }
}
