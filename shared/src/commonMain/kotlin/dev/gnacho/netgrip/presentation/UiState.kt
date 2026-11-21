package dev.gnacho.netgrip.presentation

/** Estados de pantalla del spike de la fase 1. */
sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data object Ready : UiState
    data class Error(val message: String) : UiState
    data object SessionExpired : UiState
    data object RouterUnreachable : UiState
    data class RateLimited(val retryAfterSeconds: Long?) : UiState
}
