package dev.gnacho.netgrip.core.storage

/**
 * Almacenamiento cifrado de secretos (token de sesion, no la contrasena).
 * Android: Android Keystore. iOS: Keychain (pendiente de validar con Mac).
 * Ver docs/adr/0002-sesiones-y-secretos.md.
 */
interface SecureStorage {
    suspend fun save(key: String, value: String)
    suspend fun read(key: String): String?
    suspend fun delete(key: String)
}
