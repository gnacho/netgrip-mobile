package dev.gnacho.netgrip.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Iconos Lucide vectorizados a partir de los SVG originales (ISC).
 * Trazo de 2 sobre lienzo de 24, igual que el panel web. Se tintan con
 * [androidx.compose.material3.Icon]. El parseo de los paths ocurre una sola
 * vez por icono, la primera vez que se dibuja.
 */
private fun lucide(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    paths.forEach { d ->
        val nodes = PathParser().parsePathString(d).toNodes()
        builder.addPath(
            pathData = nodes,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

/** Iconos del panel NetGrip. */
object NetGripIcons {
    val Panel: ImageVector by lazy { lucide("Panel", "M4 3h5a1 1 0 0 1 1 1v7a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-7a1 1 0 0 1 1 -1z", "M15 3h5a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z", "M15 12h5a1 1 0 0 1 1 1v7a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-7a1 1 0 0 1 1 -1z", "M4 16h5a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z") }
    val Clients: ImageVector by lazy { lucide("Clients", "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z", "M12 18h.01") }
    val Wifi: ImageVector by lazy { lucide("Wifi", "M12 20h.01", "M2 8.82a15 15 0 0 1 20 0", "M5 12.859a10 10 0 0 1 14 0", "M8.5 16.429a5 5 0 0 1 7 0") }
    val Ports: ImageVector by lazy { lucide("Ports", "M8 3 4 7l4 4", "M4 7h16", "m16 21 4-4-4-4", "M20 17H4") }
    val Settings: ImageVector by lazy { lucide("Settings", "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0") }
    val Menu: ImageVector by lazy { lucide("Menu", "M4 5h16", "M4 12h16", "M4 19h16") }
    val Logout: ImageVector by lazy { lucide("Logout", "m16 17 5-5-5-5", "M21 12H9", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4") }
    val Refresh: ImageVector by lazy { lucide("Refresh", "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5") }
    val Search: ImageVector by lazy { lucide("Search", "m21 21-4.34-4.34", "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0") }
    val Sun: ImageVector by lazy { lucide("Sun", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41") }
    val Moon: ImageVector by lazy { lucide("Moon", "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401") }
    val Close: ImageVector by lazy { lucide("Close", "M18 6 6 18", "m6 6 12 12") }
    val Check: ImageVector by lazy { lucide("Check", "M20 6 9 17l-5-5") }
    val Warning: ImageVector by lazy { lucide("Warning", "M21.73 18 13.73 4a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01") }
    val Info: ImageVector by lazy { lucide("Info", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "M12 16v-4", "M12 8h.01") }
    val Activity: ImageVector by lazy { lucide("Activity", "M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2") }
    val CloudOff: ImageVector by lazy { lucide("CloudOff", "M10.94 5.274A7 7 0 0 1 15.71 10h1.79a4.5 4.5 0 0 1 4.222 6.057", "M18.796 18.81A4.5 4.5 0 0 1 17.5 19H9A7 7 0 0 1 5.79 5.78", "m2 2 20 20") }
    val Pencil: ImageVector by lazy { lucide("Pencil", "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "m15 5 4 4") }
    val Ban: ImageVector by lazy { lucide("Ban", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "M4.929 4.929 19.07 19.071") }
    val Pin: ImageVector by lazy { lucide("Pin", "M12 17v5", "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z") }
    val Zap: ImageVector by lazy { lucide("Zap", "M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z") }
    val Cable: ImageVector by lazy { lucide("Cable", "M17 19a1 1 0 0 1-1-1v-2a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2a1 1 0 0 1-1 1z", "M17 21v-2", "M19 14V6.5a1 1 0 0 0-7 0v11a1 1 0 0 1-7 0V10", "M21 21v-2", "M3 5V3", "M4 10a2 2 0 0 1-2-2V6a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2a2 2 0 0 1-2 2z", "M7 5V3") }
    val Router: ImageVector by lazy { lucide("Router", "M4 14h16a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2z", "M6.01 18H6", "M10.01 18H10", "M15 10v4", "M17.84 7.17a4 4 0 0 0-5.66 0", "M20.66 4.34a8 8 0 0 0-11.31 0") }
    val ChevronRight: ImageVector by lazy { lucide("ChevronRight", "m9 18 6-6-6-6") }
    val ChevronDown: ImageVector by lazy { lucide("ChevronDown", "m6 9 6 6 6-6") }
    val CircleCheck: ImageVector by lazy { lucide("CircleCheck", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "m16 9-5.5 5.5L8 12") }
    val Expand: ImageVector by lazy { lucide("Expand", "M15 3h6v6", "m21 3-7 7", "m3 21 7-7", "M9 21H3v-6") }
    val Eye: ImageVector by lazy { lucide("Eye", "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0") }
    val EyeOff: ImageVector by lazy { lucide("EyeOff", "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49", "M14.084 14.158a3 3 0 0 1-4.242-4.242", "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143", "m2 2 20 20") }
    val ArrowUp: ImageVector by lazy { lucide("ArrowUp", "m5 12 7-7 7 7", "M12 19V5") }
    val ArrowDown: ImageVector by lazy { lucide("ArrowDown", "M12 5v14", "m19 12-7 7-7-7") }
    val Gauge: ImageVector by lazy { lucide("Gauge", "m12 14 4-4", "M3.34 19a10 10 0 1 1 17.32 0") }
    val EthernetPort: ImageVector by lazy { lucide("EthernetPort", "M10 8v1", "M14 8v1", "M18 8v1", "M19 17a2 2 0 00-1.765 1.059l-.47.882A2 2 0 0115 20H9a2 2 0 01-1.765-1.059l-.47-.882A2 2 0 005 17H4a2 2 0 01-2-2V6a2 2 0 012-2h16a2 2 0 012 2v9a2 2 0 01-2 2z", "M6 8v1") }
    val Network: ImageVector by lazy { lucide("Network", "M17 16h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z", "M3 16h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z", "M10 2h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z", "M5 16v-3a1 1 0 0 1 1-1h12a1 1 0 0 1 1 1v3", "M12 12V8") }
    val Plus: ImageVector by lazy { lucide("Plus", "M5 12h14", "M12 5v14") }
    val Trash: ImageVector by lazy { lucide("Trash", "M10 11v6", "M14 11v6", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M3 6h18", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2") }
    val Layers: ImageVector by lazy { lucide("Layers", "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z", "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12", "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17") }
    val Unplug: ImageVector by lazy { lucide("Unplug", "m19 5 3-3", "m2 22 3-3", "M6.3 20.3a2.4 2.4 0 0 0 3.4 0L12 18l-6-6-2.3 2.3a2.4 2.4 0 0 0 0 3.4Z", "M7.5 13.5 10 11", "M10.5 16.5 13 14", "m12 6 6 6 2.3-2.3a2.4 2.4 0 0 0 0-3.4l-2.6-2.6a2.4 2.4 0 0 0-3.4 0Z") }
    val DevicePc: ImageVector by lazy { lucide("DevicePc", "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z", "M8 21L16 21", "M12 17L12 21") }
    val DeviceLaptop: ImageVector by lazy { lucide("DeviceLaptop", "M18 5a2 2 0 0 1 2 2v8.526a2 2 0 0 0 .212.897l1.068 2.127a1 1 0 0 1-.9 1.45H3.62a1 1 0 0 1-.9-1.45l1.068-2.127A2 2 0 0 0 4 15.526V7a2 2 0 0 1 2-2z", "M20.054 15.987H3.946") }
    val DevicePhone: ImageVector by lazy { lucide("DevicePhone", "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z", "M12 18h.01") }
    val DeviceTablet: ImageVector by lazy { lucide("DeviceTablet", "M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z", "M12 18L12.01 18") }
    val DeviceCamera: ImageVector by lazy { lucide("DeviceCamera", "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z", "M9 13a3 3 0 1 0 6 0a3 3 0 1 0 -6 0") }
    val DeviceWatch: ImageVector by lazy { lucide("DeviceWatch", "M12 10v2.2l1.6 1", "m16.13 7.66-.81-4.05a2 2 0 0 0-2-1.61h-2.68a2 2 0 0 0-2 1.61l-.78 4.05", "m7.88 16.36.8 4a2 2 0 0 0 2 1.61h2.72a2 2 0 0 0 2-1.61l.81-4.05", "M6 12a6 6 0 1 0 12 0a6 6 0 1 0 -12 0") }
    val DevicePrinter: ImageVector by lazy { lucide("DevicePrinter", "M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2", "M6 9V3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v6", "M7 14h10a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-10a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1z") }
    val DeviceAudio: ImageVector by lazy { lucide("DeviceAudio", "M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3") }
    val DeviceSpeaker: ImageVector by lazy { lucide("DeviceSpeaker", "M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z", "M12 6h.01", "M8 14a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M12 14h.01") }
    val DeviceTv: ImageVector by lazy { lucide("DeviceTv", "m17 2-5 5-5-5", "M4 7h16a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2z") }
    val DeviceGaming: ImageVector by lazy { lucide("DeviceGaming", "M6 11L10 11", "M8 9L8 13", "M15 12L15.01 12", "M18 10L18.01 10", "M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z") }
    val DeviceIot: ImageVector by lazy { lucide("DeviceIot", "M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5", "M9 18h6", "M10 22h4") }
    val DeviceNas: ImageVector by lazy { lucide("DeviceNas", "M10 16h.01", "M2.212 11.577a2 2 0 0 0-.212.896V18a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-5.527a2 2 0 0 0-.212-.896L18.55 5.11A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z", "M21.946 12.013H2.054", "M6 16h.01") }
    val DeviceServer: ImageVector by lazy { lucide("DeviceServer", "M4 2h16a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2z", "M4 14h16a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2z", "M6 6L6.01 6", "M6 18L6.01 18") }
    val DeviceAppliance: ImageVector by lazy { lucide("DeviceAppliance", "M5 6a4 4 0 0 1 4-4h6a4 4 0 0 1 4 4v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6Z", "M5 10h14", "M15 7v6") }
    val DeviceAc: ImageVector by lazy { lucide("DeviceAc", "M18 17.5a2.5 2.5 0 1 1-4 2.03V12", "M6 12H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2", "M6 8h12", "M6.6 15.572A2 2 0 1 0 10 17v-5") }
    val DeviceVacuum: ImageVector by lazy { lucide("DeviceVacuum", "M12 8V4H8", "M6 8h12a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z", "M2 14h2", "M20 14h2", "M15 13v2", "M9 13v2") }
    val DevicePool: ImageVector by lazy { lucide("DevicePool", "M19 5a2 2 0 0 0-2 2v11", "M2 18c.6.5 1.2 1 2.5 1 2.5 0 2.5-2 5-2 2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1", "M7 13h10", "M7 9h10", "M9 5a2 2 0 0 0-2 2v11") }
    val DeviceMower: ImageVector by lazy { lucide("DeviceMower", "M11 20a10 10 0 0010-10 25.9 25.9 0 00-1.04-7.281 1 1 0 00-1.755-.325C15.833 5.5 13 5.5 9.8 6.1A7 7 0 0011 20", "M2 21a5 5 0 012.911-4.544C7.613 15.212 8.351 15.24 11 13") }
    val DeviceOther: ImageVector by lazy { lucide("DeviceOther", "M12 20v2", "M12 2v2", "M17 20v2", "M17 2v2", "M2 12h2", "M2 17h2", "M2 7h2", "M20 12h2", "M20 17h2", "M20 7h2", "M7 20v2", "M7 2v2", "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2z", "M9 8h6a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1z") }
}

/** Icono de un tipo de dispositivo, con el mismo catalogo que la web. */
fun deviceIcon(deviceType: String?): ImageVector = when (deviceType) {
    "pc" -> NetGripIcons.DevicePc
    "laptop" -> NetGripIcons.DeviceLaptop
    "phone" -> NetGripIcons.DevicePhone
    "tablet" -> NetGripIcons.DeviceTablet
    "camera" -> NetGripIcons.DeviceCamera
    "wearable" -> NetGripIcons.DeviceWatch
    "printer" -> NetGripIcons.DevicePrinter
    "audio" -> NetGripIcons.DeviceAudio
    "speaker" -> NetGripIcons.DeviceSpeaker
    "tv" -> NetGripIcons.DeviceTv
    "gaming" -> NetGripIcons.DeviceGaming
    "iot" -> NetGripIcons.DeviceIot
    "gateway" -> NetGripIcons.Router
    "nas" -> NetGripIcons.DeviceNas
    "server" -> NetGripIcons.DeviceServer
    "appliance" -> NetGripIcons.DeviceAppliance
    "ac" -> NetGripIcons.DeviceAc
    "vacuum" -> NetGripIcons.DeviceVacuum
    "pool" -> NetGripIcons.DevicePool
    "mower" -> NetGripIcons.DeviceMower
    else -> NetGripIcons.DeviceOther
}
