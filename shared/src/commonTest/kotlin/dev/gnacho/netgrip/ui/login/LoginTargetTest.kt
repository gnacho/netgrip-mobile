package dev.gnacho.netgrip.ui.login

import dev.gnacho.netgrip.discovery.DiscoveredRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoginTargetTest {

    @Test
    fun selectedRouterPrefillsUrlNameAndDefaultUserCanBeRoot() {
        val router = DiscoveredRouter("rt2", "Router 2", "v1", "192.168.1.2", 8090)
        assertEquals(
            LoginTarget("http://192.168.1.2:8090", "Router 2"),
            LoginTarget.fromRouter(router),
        )
    }

    @Test
    fun manualUrlAddsHttpAndRemovesTrailingSlash() {
        assertEquals("http://192.168.1.2:8080", normalizeManualRouterUrl("192.168.1.2:8080/"))
    }

    @Test
    fun manualUrlPreservesHttpsAndHostname() {
        assertEquals("https://router.local:8443", normalizeManualRouterUrl(" HTTPS://router.local:8443/ "))
    }

    @Test
    fun invalidManualUrlsAreRejected() {
        listOf("", "ftp://router.local", "http://", "http://user@host", "http://host:0", "http://host:", "http://host:70000", "http://::1", "http://host/path").forEach {
            assertNull(normalizeManualRouterUrl(it), it)
        }
    }
}
