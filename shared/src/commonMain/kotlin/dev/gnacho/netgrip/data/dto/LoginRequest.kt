package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.Serializable

/**
 * Body verificado de POST /api/login (internal/server/server.go de NetGrip).
 * Si el username va vacio, el backend usa root.
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)
