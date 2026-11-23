package dev.gnacho.netgrip.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.gnacho.netgrip.core.storage.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Preferencia de tema, persistida entre arranques. */
enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Oscuro"),
    ;

    companion object {
        fun parse(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * Estado del tema. La preferencia vive en el almacenamiento seguro que ya usa
 * la sesion, para no anadir otra dependencia de persistencia.
 */
class ThemeController(
    private val storage: SecureStorage,
    private val scope: CoroutineScope,
) {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun load() {
        scope.launch {
            mode = ThemeMode.parse(runCatching { storage.read(KEY) }.getOrNull())
        }
    }

    fun set(mode: ThemeMode) {
        this.mode = mode
        scope.launch { runCatching { storage.save(KEY, mode.name) } }
    }

    private companion object {
        const val KEY = "ui.theme.mode"
    }
}
