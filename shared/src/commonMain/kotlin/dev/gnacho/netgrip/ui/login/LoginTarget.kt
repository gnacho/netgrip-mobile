package dev.gnacho.netgrip.ui.login

import dev.gnacho.netgrip.discovery.DiscoveredRouter

data class LoginTarget(val baseUrl: String, val routerName: String? = null) {
    companion object {
        fun fromRouter(router: DiscoveredRouter): LoginTarget =
            LoginTarget(baseUrl = router.baseUrl, routerName = router.name)
    }
}

fun normalizeManualRouterUrl(input: String): String? {
    var value = input.trim()
    if (value.isEmpty()) return null
    if (!value.contains("://")) value = "http://$value"
    if (value.any { it.isWhitespace() }) return null

    val schemeSeparator = value.indexOf("://")
    val scheme = value.substring(0, schemeSeparator).lowercase()
    if (scheme != "http" && scheme != "https") return null

    val remainder = value.substring(schemeSeparator + 3)
    val slash = remainder.indexOf('/')
    if (slash >= 0 && slash != remainder.lastIndex) return null
    val authority = remainder.substringBefore('/')
    if (authority.isBlank() || authority.contains('@') || '?' in authority || '#' in authority) return null

    if (!authority.startsWith('[') && authority.count { it == ':' } > 1) return null

    val port = when {
        authority.startsWith('[') -> {
            val closingBracket = authority.indexOf(']')
            if (closingBracket <= 1) return null
            authority.substring(closingBracket + 1).removePrefix(":").ifEmpty { null }
        }
        ':' in authority -> authority.substringAfterLast(':')
        else -> null
    }
    if (port != null && port.toIntOrNull() !in 1..65535) return null

    val host = when {
        authority.startsWith('[') -> authority.substring(1, authority.indexOf(']'))
        ':' in authority -> authority.substringBeforeLast(':')
        else -> authority
    }
    if (host.isBlank()) return null

    return "$scheme://${remainder.trimEnd('/')}"
}
