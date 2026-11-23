package dev.gnacho.netgrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import dev.gnacho.netgrip.core.storage.AndroidSecureStorage
import dev.gnacho.netgrip.discovery.AndroidRouterDiscovery
import dev.gnacho.netgrip.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = AndroidSecureStorage(applicationContext)
        setContent {
            App(
                secureStorage = storage,
                routerDiscovery = AndroidRouterDiscovery(applicationContext),
                platformBackHandler = { enabled, onBack ->
                    BackHandler(enabled = enabled, onBack = onBack)
                },
            )
        }
    }
}
