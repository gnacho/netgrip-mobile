package dev.gnacho.netgrip.domain.model

/**
 * Perfil de router. En la fase 1 se usa uno solo introducido en el login;
 * el almacenamiento de perfiles multiples con URL alternativa llega en la
 * fase 2 (ver docs/adr/0002-sesiones-y-secretos.md).
 */
data class RouterProfile(
    val name: String,
    val primaryUrl: String,
    val alternateUrl: String? = null,
)
