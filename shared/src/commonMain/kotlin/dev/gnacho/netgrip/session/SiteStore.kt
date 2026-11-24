package dev.gnacho.netgrip.session

import dev.gnacho.netgrip.core.storage.SecureStorage
import dev.gnacho.netgrip.domain.sites.SavedDevice
import dev.gnacho.netgrip.domain.sites.SavedNetwork
import dev.gnacho.netgrip.domain.sites.SavedSites
import dev.gnacho.netgrip.domain.sites.SiteRules
import kotlinx.serialization.json.Json

/**
 * Persistencia de la lista de redes y dispositivos. Vive en el almacenamiento
 * cifrado, igual que las cookies, para no estrenar otra dependencia.
 *
 * La primera vez migra la sesion unica de versiones anteriores: la ultima URL
 * pasa a ser un dispositivo de una red llamada "Mi red".
 */
class SiteStore(private val storage: SecureStorage) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(legacyUrl: String?): SavedSites {
        val raw = storage.read(KEY)
        val stored = raw?.let { runCatching { json.decodeFromString<SavedSites>(it) }.getOrNull() }
        if (stored != null) return stored
        return migrate(legacyUrl)
    }

    suspend fun save(sites: SavedSites) {
        storage.save(KEY, json.encodeToString(SavedSites.serializer(), sites))
    }

    private suspend fun migrate(legacyUrl: String?): SavedSites {
        val url = legacyUrl?.trim().orEmpty()
        if (url.isBlank()) return SavedSites()
        val device = SavedDevice(
            id = SiteRules.deviceId(url),
            url = SiteRules.normalizeUrl(url),
        )
        val network = SavedNetwork(
            id = SiteRules.networkId(SiteRules.DEFAULT_NETWORK_NAME, emptyList()),
            name = SiteRules.DEFAULT_NETWORK_NAME,
            devices = listOf(device),
        )
        val migrated = SavedSites(
            networks = listOf(network),
            currentNetworkId = network.id,
            currentDeviceId = device.id,
        )
        save(migrated)
        return migrated
    }

    private companion object {
        const val KEY = "sites.v1"
    }
}
