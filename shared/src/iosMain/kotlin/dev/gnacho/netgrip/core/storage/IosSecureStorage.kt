package dev.gnacho.netgrip.core.storage

/**
 * Stub de compilacion para iOS.
 *
 * La implementacion real usara Keychain (Security framework) y se escribira
 * cuando haya un Mac disponible para compilar y validarla. Hasta entonces no
 * se ejecuta: la app iOS no forma parte del primer entregable.
 * Ver docs/adr/0002-sesiones-y-secretos.md.
 */
class IosSecureStorage : SecureStorage {
    override suspend fun save(key: String, value: String) {
        TODO("Keychain pendiente de implementar y validar en Mac")
    }

    override suspend fun read(key: String): String? = null

    override suspend fun delete(key: String) = Unit
}
