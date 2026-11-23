package dev.gnacho.netgrip.ui.theme

import androidx.compose.runtime.Composable

/**
 * Ajusta el color de los iconos de la barra de estado y de navegacion al tema
 * en uso. Sin esto, en tema claro los iconos del sistema quedan blancos sobre
 * fondo blanco.
 */
@Composable
expect fun SystemBars(darkTheme: Boolean)
