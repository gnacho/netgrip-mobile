package dev.gnacho.netgrip.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class DashboardDataParserTest {

    @Test
    fun `parses mock system with readable Spanish fields`() {
        val result = DashboardDataParser.parseSystem(
            json(
                """
                {
                  "hostname": "router-demo",
                  "firmware": "OpenWrt 25.12",
                  "uptime": "12 days, 4 hours",
                  "cpu_load": "23 %",
                  "memory": "412 MB / 1024 MB"
                }
                """,
            ),
        )

        assertEquals(
            listOf(
                DashboardField("Nombre del equipo", "router-demo"),
                DashboardField("Firmware", "OpenWrt 25.12"),
                DashboardField("Tiempo de actividad", "12 días, 4 horas"),
                DashboardField("Carga de CPU", "23 %"),
                DashboardField("Memoria", "412 MB / 1024 MB"),
            ),
            result,
        )
    }

    @Test
    fun `humanizes unknown system keys and formats primitive values`() {
        val result = DashboardDataParser.parseSystem(
            json("""{"wireguard_status":"active","available":true,"dns":["1.1.1.1","8.8.8.8"]}"""),
        )

        assertEquals(
            listOf(
                DashboardField("Wireguard status", "active"),
                DashboardField("Available", "Sí"),
                DashboardField("Dns", "1.1.1.1, 8.8.8.8"),
            ),
            result,
        )
    }

    @Test
    fun `returns no system fields for empty or unsupported content`() {
        assertTrue(DashboardDataParser.parseSystem(null).isEmpty())
        assertTrue(DashboardDataParser.parseSystem(json("null")).isEmpty())
        assertTrue(DashboardDataParser.parseSystem(json("[]")).isEmpty())
        assertTrue(
            DashboardDataParser.parseSystem(
                json("""{"hostname":" ","nested":{"value":1},"nothing":null}"""),
            ).isEmpty(),
        )
    }

    @Test
    fun `parses mock clients with name address and connection`() {
        val result = DashboardDataParser.parseClients(
            json(
                """
                {
                  "clients": [
                    {"name":"laptop","address":"192.0.2.50","connection":"WiFi"},
                    {"name":"nas","address":"192.0.2.60","connection":"Ethernet"},
                    {"name":"phone","address":"192.0.2.70","connection":"WiFi"}
                  ]
                }
                """,
            ),
        )

        assertEquals(
            listOf(
                DashboardClient("laptop", "192.0.2.50", "WiFi"),
                DashboardClient("nas", "192.0.2.60", "Ethernet"),
                DashboardClient("phone", "192.0.2.70", "WiFi"),
            ),
            result,
        )
    }

    @Test
    fun `accepts a client array and tolerates missing fields`() {
        val result = DashboardDataParser.parseClients(
            json("""[{"address":"192.0.2.10"},"laptop",{"name":" ","connection":"WiFi"}]"""),
        )

        assertEquals(
            listOf(
                DashboardClient("Cliente 1", address = "192.0.2.10"),
                DashboardClient("laptop"),
                DashboardClient("Cliente 3", connection = "WiFi"),
            ),
            result,
        )
    }

    @Test
    fun `returns no clients for empty or unknown shapes`() {
        assertTrue(DashboardDataParser.parseClients(null).isEmpty())
        assertTrue(DashboardDataParser.parseClients(json("{}")).isEmpty())
        assertTrue(DashboardDataParser.parseClients(json("""{"clients":{}}""")).isEmpty())
        assertTrue(DashboardDataParser.parseClients(json("[]")).isEmpty())
    }

    private fun json(value: String) = Json.parseToJsonElement(value)
}
