package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Los cuerpos de ejemplo replican las respuestas reales del router (mismos
 * nombres y unidades) para fijar el contrato de los DTO tolerantes.
 */
class NetGripDtoParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun boardIgnoresExtraUbusKeys() {
        val board = json.decodeFromJsonElement<BoardDto>(
            json.parseToJsonElement(
                """
                {"kernel":"6.6.89","hostname":"casa","model":"Redmi AX6 (IPQ807x)",
                 "board_name":"redmi,ax6","system":"Qualcomm IPQ8071A",
                 "release":{"distribution":"OpenWrt","version":"25.12.5","revision":"r28417",
                            "target":"qualcommax/ipq807x","description":"OpenWrt 25.12.5"}}
                """.trimIndent(),
            ),
        )
        assertEquals("casa", board.hostname)
        assertEquals("Redmi AX6 (IPQ807x)", board.model)
        assertEquals("6.6.89", board.kernel)
        assertEquals("OpenWrt", board.release?.distribution)
        assertEquals("25.12.5", board.release?.version)
        assertEquals("r28417", board.release?.revision)
    }

    @Test
    fun boardToleraCamposAusentes() {
        val board = json.decodeFromJsonElement<BoardDto>(json.parseToJsonElement("""{"hostname":"rt3"}"""))
        assertEquals("rt3", board.hostname)
        assertNull(board.model)
        assertNull(board.release)
    }

    @Test
    fun systemLeeMemoriaEnBytesYRootEnKib() {
        val mib = 1024L * 1024L
        val system = json.decodeFromJsonElement<SystemDto>(
            json.parseToJsonElement(
                """
                {"uptime":1080000,"load":[0.42,0.38,0.31],
                 "memory":{"total":${395 * mib},"free":${96 * mib},"available":${247 * mib},
                           "cached":${88 * mib},"buffered":${12 * mib}},
                 "root":{"total":${128 * 1024},"free":${96 * 1024}}}
                """.trimIndent(),
            ),
        )
        assertEquals(1080000L, system.uptime)
        assertEquals(listOf(0.42, 0.38, 0.31), system.load)
        assertEquals(395 * mib, system.memory.total)
        assertEquals(247 * mib, system.memory.available)
        assertEquals(128L * 1024, system.root.total)
        assertEquals(96L * 1024, system.root.free)
    }

    @Test
    fun systemVacioUsaValoresPorDefecto() {
        val system = json.decodeFromJsonElement<SystemDto>(json.parseToJsonElement("{}"))
        assertEquals(0L, system.uptime)
        assertTrue(system.load.isEmpty())
        assertEquals(0L, system.memory.total)
        assertEquals(0L, system.root.total)
    }

    @Test
    fun wanConGatewayAusente() {
        val wan = json.decodeFromJsonElement<WanDto>(
            json.parseToJsonElement(
                """{"present":true,"up":false,"uptime":0,"ipv4":[],"dns":["1.1.1.1"]}""",
            ),
        )
        assertTrue(wan.present)
        assertFalse(wan.up)
        assertNull(wan.gateway)
        assertEquals(listOf("1.1.1.1"), wan.dns)
    }

    @Test
    fun modeDerivaRolApSiFaltaRole() {
        val mode = json.decodeFromJsonElement<ModeDto>(
            json.parseToJsonElement("""{"mode":"ap","has_wifi":true,"port_count":1,"dnsmasq_on":false}"""),
        )
        assertEquals("ap", mode.effectiveRole)
        assertEquals(false, mode.dnsmasqOn)
        assertNull(mode.firewallOn)
    }

    @Test
    fun modeRespetaRoleExplicitoYDerivaRouter() {
        val explicit = json.decodeFromJsonElement<ModeDto>(
            json.parseToJsonElement("""{"mode":"ap","role":"mesh","has_wifi":true,"port_count":3}"""),
        )
        assertEquals("mesh", explicit.effectiveRole)
        val router = json.decodeFromJsonElement<ModeDto>(
            json.parseToJsonElement("""{"mode":"router","has_wifi":false,"port_count":5}"""),
        )
        assertEquals("router", router.effectiveRole)
    }

    @Test
    fun clientsLeeContadoresFlagsYConcesion() {
        val body = """
            {"ts":1732300000,"bands":["2g","5g"],"clients":[
              {"name":"Pixel 8 Pro (Marta)","ip":"192.168.8.112","mac":"9C:B6:D0:12:AB:77",
               "type":"wifi5","device_type":"phone","iface":"phy1-ap0","signal":-52,
               "rx_bytes":4414504970,"tx_bytes":398458880,"self":false,"reserved":false,
               "reservable":true,"blocked":false,"blockable":true,"parental_blocked":true,
               "parental_next":"22:00","lease_expiry":1732307200,"lease_source":"gateway",
               "ip_source":"arp","quota_used":8912896000,"quota_limit":10485760000,
               "quota_remaining":1572864000,"quota_period":"daily","quota_throttled":false,
               "quota_exceeded":false,"campo_nuevo_del_futuro":"ignorado"}
            ]}
        """.trimIndent()
        val clients = json.decodeFromJsonElement<ClientsDto>(json.parseToJsonElement(body))
        assertEquals(listOf("2g", "5g"), clients.bands)
        assertEquals(1732300000L, clients.ts)
        val client = clients.clients.single()
        assertEquals("Pixel 8 Pro (Marta)", client.name)
        assertEquals(-52, client.signal)
        assertEquals(4414504970L, client.rxBytes)
        assertTrue(client.parentalBlocked)
        assertEquals("22:00", client.parentalNext)
        assertEquals(1732307200L, client.leaseExpiry)
        assertEquals("gateway", client.leaseSource)
        assertEquals(10485760000L, client.quotaLimit)
    }

    @Test
    fun clientsSinListaQuedaVacia() {
        val clients = json.decodeFromJsonElement<ClientsDto>(json.parseToJsonElement("""{"ts":1}"""))
        assertTrue(clients.clients.isEmpty())
        assertTrue(clients.bands.isEmpty())
    }

    @Test
    fun ethPortsLeeVelocidadYDispositivos() {
        val body = """
            {"ports":[
              {"name":"wan","wan":true,"up":true,"speed_mbps":1000,"devices":[]},
              {"name":"lan4","wan":false,"up":true,"speed_mbps":100,
               "devices":[{"mac":"C8:2B:96:33:77:19","name":"Impresora HP"}]}
            ]}
        """.trimIndent()
        val ports = json.decodeFromJsonElement<EthPortsDto>(json.parseToJsonElement(body)).ports
        assertEquals(2, ports.size)
        assertTrue(ports[0].wan)
        assertEquals(1000, ports[0].speedMbps)
        assertEquals("Impresora HP", ports[1].devices.single().name)
    }

    @Test
    fun wifiNuncaExponeLaClaveAunqueVenga() {
        val body = """
            {"interfaces":[
              {"section":"wifinet0","radio":"radio0","ifname":"phy0-ap0","band":"2g",
               "ssid":"CasaGarcia","encryption":"sae-mixed","has_key":true,"hidden":false,
               "mac":"","bssid":"8C:53:C3:11:22:30","disabled":false,
               "clients":[{"mac":"3A:7B:1C:44:20:11","signal":-58}],
               "key":"no-debe-leerse"}
            ]}
        """.trimIndent()
        val wifi = json.decodeFromJsonElement<WifiDto>(json.parseToJsonElement(body))
        val iface = wifi.interfaces.single()
        assertTrue(iface.hasKey)
        assertEquals(-58, iface.clients.single().signal)
    }

    @Test
    fun wirelessEsUnArrayDesnudo() {
        val body = """
            [{"name":"radio0","up":true,"band":"2g","channel":"6","htmode":"HE40","txpower":20,
              "interfaces":[{"ifname":"phy0-ap0","ssid":"CasaGarcia","encryption":"sae-mixed",
                             "disabled":false,"hidden":false,"bssid":"8C:53:C3:11:22:30",
                             "rname":"radio0","clients":[{"mac":"3A:7B:1C:44:20:11","signal":-58}]}]}]
        """.trimIndent()
        val radios = json.decodeFromJsonElement<List<WirelessRadioDto>>(json.parseToJsonElement(body))
        assertEquals("radio0", radios.single().name)
        assertEquals("6", radios.single().channel)
        assertEquals("radio0", radios.single().interfaces.single().rname)
    }

    @Test
    fun accessLeePanelLuciYSsh() {
        val body = """
            {"panel":{"http_port":8090,"https_enabled":true,"force_https":false,"session_ttl":"60m"},
             "luci":{"http_port":80,"https_port":443,"force_https":false,"enabled":true},
             "ssh":{"enabled":true,"port":"22"}}
        """.trimIndent()
        val access = json.decodeFromJsonElement<AccessDto>(json.parseToJsonElement(body))
        assertEquals(8090, access.panel.httpPort)
        assertTrue(access.panel.httpsEnabled)
        assertEquals("60m", access.panel.sessionTtl)
        assertEquals(443, access.luci.httpsPort)
        assertEquals("22", access.ssh.port)
    }

    @Test
    fun mdnsLeeEstadoDelServicio() {
        val mdns = json.decodeFromJsonElement<MdnsDto>(
            json.parseToJsonElement("""{"installed":true,"enabled":true,"running":true,"domain":"casa.local"}"""),
        )
        assertTrue(mdns.installed)
        assertTrue(mdns.running)
        assertEquals("casa.local", mdns.domain)
    }

    @Test
    fun selfUpdateConErrorDeComprobacion() {
        val body = """{"current":"v0.79.0","latest":"","available":false,"notes":"","check_error":"HTTP 403"}"""
        val update = json.decodeFromJsonElement<SelfUpdateDto>(json.parseToJsonElement(body))
        assertEquals("v0.79.0", update.current)
        assertEquals("HTTP 403", update.checkError)
        assertFalse(update.available)
    }

    @Test
    fun selfUpdateSinErrorOmiteElCampo() {
        val body = """{"current":"v0.79.0","latest":"v0.79.0","available":false,"notes":""}"""
        val update = json.decodeFromJsonElement<SelfUpdateDto>(json.parseToJsonElement(body))
        assertNull(update.checkError)
        assertEquals("v0.79.0", update.latest)
    }
}
