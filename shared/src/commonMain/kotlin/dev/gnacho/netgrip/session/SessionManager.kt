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
    suspend fun restore(api: NetGripApi, profileName: String = DEFAULT_PROFILE): Boolean {
        val cookie = storage.read(cookieKey(profileName)) ?: return false
        api.restoreSessionCookie(cookie)
        return when (api.me()) {
            is ApiResult.Ok -> true
            else -> {
                storage.delete(cookieKey(profileName))
                false
            }
        }
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

    private fun cookieKey(profileName: String) = "session.$profileName"

    companion object {
        const val DEFAULT_PROFILE = "default"
        private const val KEY_LAST_URL = "last.url"
    }
}
