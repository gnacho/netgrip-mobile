package dev.gnacho.netgrip.presentation

/** Pestanas del shell de solo lectura. */
enum class ShellTab(val label: String) {
    PANEL("Panel"),
    CLIENTS("Clientes"),
    TRAFFIC("Tráfico"),
    WIFI("WiFi"),
    REMOTE("Acceso remoto"),
    SNAPSHOTS("Copias"),
    PORTS("Puertos"),
    DIAGNOSTICS("Diagnóstico"),
    SETTINGS("Ajustes"),
}
