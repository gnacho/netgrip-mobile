package dev.gnacho.netgrip.presentation

/** Pestanas del shell de solo lectura. */
enum class ShellTab(val label: String) {
    PANEL("Panel"),
    CLIENTS("Clientes"),
    WIFI("WiFi"),
    REMOTE("Acceso remoto"),
    PORTS("Puertos"),
    DIAGNOSTICS("Diagnóstico"),
    SETTINGS("Ajustes"),
}
