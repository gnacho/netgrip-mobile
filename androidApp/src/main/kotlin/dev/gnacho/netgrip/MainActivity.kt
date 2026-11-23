package dev.gnacho.netgrip

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import dev.gnacho.netgrip.core.storage.AndroidSecureStorage
import dev.gnacho.netgrip.discovery.AndroidRouterDiscovery
import dev.gnacho.netgrip.ui.App

class MainActivity : ComponentActivity() {
    private var permissionCallback: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = AndroidSecureStorage(applicationContext)
        setContent {
            App(
                secureStorage = storage,
                routerDiscovery = AndroidRouterDiscovery(applicationContext),
                isLocalNetworkGranted = ::isLocalNetworkGranted,
                requestLocalNetworkPermission = ::requestLocalNetworkPermission,
                platformBackHandler = { enabled, onBack ->
                    BackHandler(enabled = enabled, onBack = onBack)
                },
            )
        }
    }

    private fun localNetworkPermission(): String =
        if (Build.VERSION.SDK_INT >= 37) {
            Manifest.permission.ACCESS_LOCAL_NETWORK
        } else {
            Manifest.permission.NEARBY_WIFI_DEVICES
        }

    private fun isLocalNetworkGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(localNetworkPermission()) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun requestLocalNetworkPermission(callback: (Boolean) -> Unit) {
        if (isLocalNetworkGranted()) {
            callback(true)
            return
        }
        permissionCallback = callback
        requestPermissions(arrayOf(localNetworkPermission()), LOCAL_NETWORK_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        if (requestCode != LOCAL_NETWORK_REQUEST) return
        permissionCallback?.invoke(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        permissionCallback = null
    }

    private companion object {
        const val LOCAL_NETWORK_REQUEST = 5141
    }
}
