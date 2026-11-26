package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.Serializable

/** GET /api/config/snapshots. */
@Serializable
data class SnapshotsDto(val snapshots: List<ConfigSnapshotDto> = emptyList())

@Serializable
data class ConfigSnapshotDto(
    val id: String = "",
    val timestamp: Long = 0,
    val configs: Int = 0,
)

/** GET /api/config/diff: un fichero UCI que cambia entre dos copias. */
@Serializable
data class DiffsDto(val diffs: List<ConfigDiffDto> = emptyList())

@Serializable
data class ConfigDiffDto(
    val config: String = "",
    val before: String = "",
    val after: String = "",
)

/** POST /api/config/rollback. */
@Serializable
data class RollbackRequest(val id: String)

@Serializable
data class RollbackResponse(val status: String = "")
