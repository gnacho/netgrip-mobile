"""Mock del panel NetGrip para validar netgrip-mobile en el emulador.

Sirve lo justo para entrar, ver la shell y probar las escrituras de WiFi,
invitados, IoT y horarios. Las escrituras modifican estado en memoria, arman
la ventana de reversion (MOCK_CONFIRM_SECS) y el rollback restaura la foto.

Uso:
    python3 tools/mock-api.py                  # 18090, router-demo
    MOCK_CONFIRM_SECS=6 python3 tools/mock-api.py
    adb reverse tcp:18090 tcp:18090
"""
import json
import os
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("MOCK_PORT", "18090"))
HOSTNAME = os.environ.get("MOCK_HOSTNAME", "router-demo")
CONFIRM_SECS = int(os.environ.get("MOCK_CONFIRM_SECS", "120"))

ESTADO = {
    "wifi": [
        {
            "section": "default_radio0",
            "radio": "radio0",
            "ifname": "phy0-ap0",
            "band": "2g",
            "ssid": "Casa",
            "encryption": "psk2",
            "has_key": True,
            "hidden": False,
            "mac": "",
            "bssid": "aa:bb:cc:00:00:01",
            "disabled": False,
            "clients": [{"mac": "aa:bb:cc:dd:ee:01", "signal": -58}],
        },
        {
            "section": "default_radio1",
            "radio": "radio1",
            "ifname": "phy1-ap0",
            "band": "5g",
            "ssid": "Casa",
            "encryption": "psk2",
            "has_key": True,
            "hidden": False,
            "mac": "",
            "bssid": "aa:bb:cc:00:00:02",
            "disabled": False,
            "clients": [{"mac": "aa:bb:cc:dd:ee:02", "signal": -61}],
        },
    ],
    "radios": [
        {"name": "radio0", "up": True, "band": "2g", "channel": "6",
         "htmode": "HE20", "txpower": 20, "interfaces": []},
        {"name": "radio1", "up": True, "band": "5g", "channel": "36",
         "htmode": "HE80", "txpower": 23, "interfaces": []},
    ],
    "confirm": None,
    "snapshot": None,
    "password_changed": False,
    "snapshots": [{"id": "20261126-120000", "timestamp": 1790000000, "configs": 9},
                  {"id": "20261125-090000", "timestamp": 1789900000, "configs": 8}],
    "snapshot_seq": 1,
    # Politicas por dispositivo: cuota, velocidad y horario.
    "quotas": {},
    "qos": {},
    "parental": {},
    "firmware_started": False,
    "schedule": {"config": {"enabled": False, "intervalHours": 168,
                            "windowStart": 3, "windowEnd": 5},
                 "state": {"lastCheck": 0, "lastResult": "", "lastApply": 0,
                           "lastVersion": ""}},
    "selfupdate_started": False,
    "guest": {"gateway": True, "active": True, "ssid": "Invitados",
              "subnet": "192.168.9.1", "isolate": True,
              "ifaces": ["phy0-ap1"], "clients": 1, "gl_conflict": False},
    "iot": {"active": False, "ssid": "Cosa", "band": "2g", "isolated": True,
            "ifaces": [], "clients": 0},
    "schedules": [
        {"section": "default_radio0", "ifname": "phy0-ap0", "ssid": "Casa",
         "disabled": False, "off_by_schedule": False, "schedule": None},
        {"section": "default_radio1", "ifname": "phy1-ap0", "ssid": "Casa",
         "disabled": False, "off_by_schedule": False, "schedule": None},
    ],
}

BASE = {
    "/api/board": {"model": "Mock One", "hostname": HOSTNAME, "firmware": "mock"},
    "/api/system": {"uptime": 3600, "load": [0.12, 0.10, 0.08],
                    "memory": {"total": 262144, "free": 163840, "available": 175000,
                               "cached": 30000, "buffered": 4000},
                    "root": {"total": 16384, "free": 8192}},
    "/api/wan": {"proto": "dhcp", "ip": "192.0.2.10", "gateway": "192.0.2.1",
                 "dns": ["192.0.2.1"], "up": True, "rx_bytes": 120000000,
                 "tx_bytes": 34000000},
    "/api/mode": {"gateway": True, "has_wifi": True, "has_switch": True},
    "/api/clients": {"bands": ["2g", "5g"], "clients": [
        {"mac": "aa:bb:cc:dd:ee:01", "name": "portatil", "ip": "192.168.1.50",
         "type": "wifi", "band": "5g", "signal": -58, "blocked": False,
         "bands": ["2g", "5g"], "reserved": False, "blockable": True, "self": False},
    ]},
    "/api/ethports": {"ports": []},
    "/api/history": {"entries": []},
    "/api/access": {"https": False, "port": 8080},
    "/api/mdns": {"enabled": False},
    "/api/selfupdate": {"available": True, "current": "0.79.99-mock",
                        "latest": "0.80.1", "notes": "Correcciones del panel"},
    "/api/logs": {"entries": [
        {"ts": "Thu Nov 26 18:00:00 2026", "facility": "daemon", "level": "info",
         "tag": "netgrip[1]", "message": "listening on http://0.0.0.0:8080"},
    ], "total": 1},
    "/api/leds": {"applicable": False, "leds": []},
    "/api/fleet": {"nodes": []},
    "/api/telegram": {"chatId": "12345", "enabled": True, "botToken": "123456...WXYZ"},
    "/api/ntfy": {"server": "https://ntfy.sh", "topic": "casa", "tokenSet": True, "enabled": True},
    "/api/diagnostics/selftest": {
        "gateway": True, "wan": True, "dns": True, "ntp": False, "all_ok": False,
        "checks": [
            {"key": "gateway", "ok": True, "detail": "192.168.1.1 responde"},
            {"key": "wan", "ok": True, "detail": "salida a Internet correcta"},
            {"key": "dns", "ok": True, "detail": "resuelve example.org"},
            {"key": "ntp", "ok": False, "detail": "sin respuesta del servidor de hora"}],
        "tools": {"ping": True, "traceroute": False, "nslookup": True, "dig": False}},
    "/api/tailscale": {"installed": True, "running": False, "state": "NeedsLogin",
                        "auth_url": "https://login.tailscale.com/a/mock", "ips": []},
    "/api/wireguard": {"installed": True, "active": True, "running": True, "port": "51820",
                       "address": "10.6.0.1/24", "public_key": "PUB", "peers": [
                           {"section": "peer1", "name": "movil", "allowed_ips": ["10.6.0.2/32"]}],
                       "endpoint": "casa.duckdns.org", "managed_by": ""},
    "/api/openvpn": {"installed": False, "has_pki": False, "active": False, "running": False},
    "/api/ddns": {"installed": True, "wan_ip": "203.0.113.9", "entries": [
        {"section": "casa", "enabled": True, "running": True, "service_name": "duckdns",
         "domain": "casa.duckdns.org", "registered_ip": "203.0.113.9", "last_update": "hace 5 min"}]},
    "/api/remoteaccess": {"applicable": True, "ping_wan": True,
                          "remote_https": False, "remote_ssh": False},
    "/api/clients/meta": {"meta": {}},
    # Contabilidad del router (nlbwmon) y DPI (netifyd), tal como los sirve
    # internal/server: writeJSON de ProbeNlbwmon, NlbwmonTopDevices y
    # NetifydApps. Los bytes son contados, no tasas.
    "/api/nlbwmon": {"installed": True, "running": True, "generations": 2,
                     "commit_interval": 30, "prealloc_days": 7,
                     "protocol_database": True},
    "/api/nlbwmon/top": {
        "devices": [
            {"key": "aa:bb:cc:dd:ee:01", "ip": "192.168.1.50", "conns": 412,
             "down_bytes": 8_214_364_160, "up_bytes": 512_000_000},
            {"key": "aa:bb:cc:dd:ee:02", "ip": "192.168.1.60", "conns": 88,
             "down_bytes": 2_147_483_648, "up_bytes": 96_468_992},
            {"key": "aa:bb:cc:dd:ee:03", "conns": 12,
             "down_bytes": 64_000_000, "up_bytes": 8_000_000},
        ],
        "apps": [
            {"key": "HTTPS", "conns": 240, "down_bytes": 6_442_450_944, "up_bytes": 268_435_456},
            {"key": "QUIC", "conns": 180, "down_bytes": 3_435_973_836, "up_bytes": 220_000_000},
            {"key": "DNS", "conns": 96, "down_bytes": 12_582_912, "up_bytes": 6_291_456},
            {"key": "other", "conns": 40, "down_bytes": 914_358_272, "up_bytes": 121_634_816},
        ],
    },
    "/api/dpi": {
        "applicable": True, "total_bytes": 9_663_676_416, "total_flows": 892,
        "protocols": [
            {"name": "HTTPS", "bytes": 6_442_450_944, "flows": 402, "category": "web"},
            {"name": "QUIC", "bytes": 3_221_225_472, "flows": 490, "category": "web"},
        ],
    },
    "/api/dpi/apps": {"apps": [
        {"name": "YouTube", "category": "streaming", "bytes": 4_294_967_296,
         "local_bytes": 1_073_741_824, "other_bytes": 3_221_225_472,
         "packets": 3_210_000, "flows": 210},
        {"name": "Spotify", "category": "streaming", "bytes": 805_306_368,
         "local_bytes": 268_435_456, "other_bytes": 536_870_912,
         "packets": 610_000, "flows": 88},
        {"name": "GitHub", "category": "business", "bytes": 134_217_728,
         "local_bytes": 33_554_432, "other_bytes": 100_663_296,
         "packets": 120_000, "flows": 34},
        {"name": "Algo-que-netifyd-no-sabe", "category": "", "bytes": 33_554_432,
         "local_bytes": 0, "other_bytes": 33_554_432, "packets": 40_000, "flows": 6},
    ]},
}


def now():
    return int(time.time())


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def _send(self, code, payload=None, headers=None):
        body = b"" if payload is None else json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        if body:
            self.wfile.write(body)

    def _json(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0:
            return {}
        raw = self.rfile.read(length)
        try:
            return json.loads(raw.decode())
        except ValueError:
            return {}

    def _quotas_probe(self):
        """GET /api/quotas: intencion guardada + consumo de ejemplo."""
        usage = {}
        for mac, quota in ESTADO["quotas"].items():
            used = 2 * 1024 ** 3
            usage[mac] = {
                "used": min(used, quota["limit"]),
                "limit": quota["limit"],
                "remaining": max(quota["limit"] - used, 0),
                "period": "2026-11-26",
                "throttled": quota.get("action") == "throttle",
                "exceeded": used >= quota["limit"],
            }
        return {
            "applicable": True,
            "quotas": ESTADO["quotas"],
            "usage": usage,
            "ts": now() * 1000,
        }

    def _traffic_timeline(self):
        """Cubos de 5 minutos de las ultimas horas, como los agrega netifyd."""
        bucket = now() // 300 * 300
        names = ["YouTube", "Spotify", "GitHub"]
        buckets = []
        for index in range(12):
            start = bucket - (11 - index) * 300
            stamp = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(start))
            weights = [index % 5 + 1, (index * 3) % 4, index % 3]
            entries = {}
            for name, weight in zip(names, weights):
                size = weight * 12_000_000
                entries[name] = {
                    "local": size // 3,
                    "other": size - size // 3,
                    "total": size,
                }
            buckets.append({"time": stamp, "apps": entries})
        total = sum(entry["total"] for b in buckets for entry in b["apps"].values())
        return {
            "buckets": buckets,
            "top": BASE["/api/dpi/apps"]["apps"],
            "totals": {"local": total // 4, "other": total - total // 4, "total": total},
        }

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/api/me":
            if ESTADO["password_changed"]:
                self._send(401, {"error": "unauthorized"})
            else:
                self._send(204)
            return
        if path == "/api/wifi":
            self._wipe_expired()
            self._send(200, {"interfaces": ESTADO["wifi"]})
            return
        if path == "/api/wireless":
            self._wipe_expired()
            self._send(200, ESTADO["radios"])
            return
        if path == "/api/wifischedule":
            self._send(200, {"schedules": ESTADO["schedules"]})
            return
        if path == "/api/guestwifi":
            self._send(200, ESTADO["guest"])
            return
        if path == "/api/iotwifi":
            self._send(200, ESTADO["iot"])
            return
        if path == "/api/config/snapshots":
            self._send(200, {"snapshots": ESTADO["snapshots"]})
            return
        if path == "/api/config/diff":
            self._send(200, {"diffs": [{"config": "network",
                "before": "config interface 'lan'\n\toption ipaddr '192.168.1.1'",
                "after": "config interface 'lan'\n\toption ipaddr '192.168.1.2'"}]})
            return
        if path == "/api/config/confirm":
            pending = ESTADO["confirm"]
            if pending and pending["expires"] > now():
                self._send(200, {"pending": True, "id": pending["id"], "expires": pending["expires"]})
            else:
                ESTADO["confirm"] = None
                self._send(200, {"pending": False})
            return
        if path in ("/api/telegram", "/api/ntfy", "/api/diagnostics/selftest"):
            self._send(200, BASE[path])
            return
        if path == "/api/tailscale" or path == "/api/wireguard" or path == "/api/openvpn" \
                or path == "/api/ddns" or path == "/api/remoteaccess":
            self._send(200, BASE[path])
            return
        if path == "/api/update":
            self._send(200, {
                "available": True, "same_version": False, "owut_present": True,
                "version_from": "24.10.0-rc1", "version_to": "24.10.1",
                "out_of_date_packages": 3,
                "warnings": ["3 packages are out-of-date"],
                "safe_to_proceed": True, "missing_packages": [],
                "safe_with_reinstall": False,
                "upgrade_started": ESTADO["firmware_started"],
            })
            return
        if path == "/api/selfupdate/schedule":
            self._send(200, ESTADO["schedule"])
            return
        if path == "/api/selfupdate/status":
            self._send(200, {"phase": "idle", "progress": 0})
            return
        if path == "/api/dpi/timeline":
            self._send(200, self._traffic_timeline())
            return
        if path == "/api/quotas":
            self._send(200, self._quotas_probe())
            return
        if path == "/api/nftqos":
            self._send(200, {"applicable": True, "limits": ESTADO["qos"]})
            return
        if path == "/api/parental":
            self._send(200, {"rules": ESTADO["parental"], "ts": now() * 1000})
            return
        if path in BASE:
            self._send(200, BASE[path])
            return
        self._send(404, {"error": "not found"})

    def do_POST(self):
        path = self.path.split("?", 1)[0]
        body = self._json()
        print(f"POST {path} {body}", flush=True)
        if path == "/api/login":
            self._send(204, headers={"Set-Cookie": "netgrip_http_session=mock; Path=/"})
            return
        if path == "/api/wifi":
            self._wipe_expired()
            ESTADO["snapshot"] = json.loads(json.dumps(ESTADO["wifi"]))
            sections = body.get("sections") or [body.get("section")]
            for iface in ESTADO["wifi"]:
                if iface["section"] not in sections:
                    continue
                if "ssid" in body:
                    iface["ssid"] = body["ssid"]
                if "encryption" in body:
                    iface["encryption"] = body["encryption"]
                    iface["has_key"] = body["encryption"] != "none"
                if "key" in body:
                    iface["has_key"] = bool(body["key"])
                if "hidden" in body:
                    iface["hidden"] = bool(body["hidden"])
                if "disabled" in body:
                    iface["disabled"] = bool(body["disabled"])
            self._risky({"status": "applied", "rolled_back": False, "state": self._first(sections)})
            return
        if path == "/api/wifi/radio":
            self._wipe_expired()
            ESTADO["snapshot"] = json.loads(json.dumps(ESTADO["radios"]))
            for radio in ESTADO["radios"]:
                if radio["name"] != body.get("radio"):
                    continue
                if "channel" in body:
                    radio["channel"] = str(body["channel"])
                if "htmode" in body:
                    radio["htmode"] = body["htmode"]
                if "txpower" in body:
                    radio["txpower"] = int(body["txpower"])
            self._risky({"status": "applied", "rolled_back": False, "state": ESTADO["radios"][0]})
            return
        if path == "/api/update":
            ESTADO["firmware_started"] = True
            self._send(200, {"started": True, "reboot_pending": True})
            return
        if path == "/api/selfupdate/schedule":
            cfg = ESTADO["schedule"]["config"]
            for key in ("enabled", "intervalHours", "windowStart", "windowEnd"):
                if key in body:
                    cfg[key] = body[key]
            cfg["lastResult"] = "guardado"
            self._send(200, ESTADO["schedule"])
            return
        if path == "/api/selfupdate":
            ESTADO["selfupdate_started"] = True
            self._send(200, {"phase": "restarting", "progress": 100})
            return
        if path == "/api/telegram":
            base = dict(BASE["/api/telegram"])
            base["chatId"] = body.get("chatId", base["chatId"])
            base["enabled"] = bool(body.get("enabled", base["enabled"]))
            if body.get("botToken"):
                base["botToken"] = "123456...nuevo"
            BASE["/api/telegram"] = base
            self._send(200, {"ok": True, "botName": "bot-demo", "chatName": "Casa"})
            return
        if path == "/api/telegram/test":
            self._send(200, {"ok": True})
            return
        if path == "/api/ntfy":
            base = dict(BASE["/api/ntfy"])
            for key in ("server", "topic"):
                if body.get(key):
                    base[key] = body[key]
            base["enabled"] = bool(body.get("enabled", base["enabled"]))
            if body.get("token"):
                base["tokenSet"] = True
            BASE["/api/ntfy"] = base
            self._send(200, {"ok": True})
            return
        if path == "/api/ntfy/test":
            self._send(200, {"ok": True})
            return
        if path == "/api/diagnostics":
            test = body.get("test", "ping")
            if test == "ping":
                self._send(200, {
                    "host": body.get("host", ""), "count": 4, "sent": 4, "received": 4,
                    "loss_pct": 0, "min_ms": 10.1, "avg_ms": 11.2, "max_ms": 12.3,
                    "samples": [{"seq": i, "time_ms": 10.0 + i} for i in range(1, 5)]})
            elif test == "traceroute":
                self._send(200, {"host": body.get("host", ""), "hops": [
                    {"hop": 1, "host": "192.168.1.1", "rtts": [1.2], "parsed": True}]})
            elif test == "dns":
                self._send(200, {"query": body.get("query", ""), "parsed": True,
                                 "answers": [{"name": body.get("query", ""), "type": "A",
                                              "value": "93.184.216.34"}]})
            else:
                self._send(200, {"host": body.get("host", ""), "port": body.get("port", 0), "open": True})
            return
        if path == "/api/tailscale":
            base = dict(BASE["/api/tailscale"])
            base["running"] = bool(body.get("enabled"))
            base["state"] = "Running" if base["running"] else "Stopped"
            base["auth_url"] = "" if base["running"] else base["auth_url"]
            BASE["/api/tailscale"] = base
            self._send(200, {"status": "applied", "rolled_back": False, "state": base})
            return
        if path == "/api/remoteaccess":
            base = dict(BASE["/api/remoteaccess"])
            for key in ("ping_wan", "remote_https", "remote_ssh"):
                if key in body:
                    base[key] = bool(body[key])
            BASE["/api/remoteaccess"] = base
            self._send(200, {"status": "applied", "rolled_back": False, "state": base})
            return
        if path == "/api/ddns/force":
            self._send(200, {"status": "applied", "rolled_back": False,
                             "state": BASE["/api/ddns"]})
            return
        if path == "/api/reboot":
            self._send(200, {"started": True})
            return
        if path == "/api/password":
            current = body.get("current", "")
            new = body.get("next", "")
            if current != "demo":
                self._send(400, {"error": "current password is wrong"})
                return
            if len(new) < 8:
                self._send(400, {"error": "password must be at least 8 characters"})
                return
            ESTADO["password_changed"] = True
            self._send(204)
            return
        if path == "/api/wifischedule":
            for entry in ESTADO["schedules"]:
                if entry["section"] != body.get("section"):
                    continue
                entry["schedule"] = {
                    "section": body.get("section"),
                    "enabled": bool(body.get("enabled")),
                    "days": body.get("days") or [],
                    "start": body.get("start", "00:00"),
                    "end": body.get("end", "00:00"),
                    "paused": bool(body.get("paused")),
                }
                entry["off_by_schedule"] = False
            self._send(200, {"status": "applied", "rolled_back": False,
                             "state": {"schedules": ESTADO["schedules"]}})
            return
        if path == "/api/guestwifi":
            g = ESTADO["guest"]
            g["active"] = bool(body.get("enabled", g["active"]))
            g["ssid"] = body.get("ssid", g["ssid"])
            if body.get("subnet"):
                g["subnet"] = body["subnet"]
            g["isolate"] = bool(body.get("isolate", g["isolate"]))
            g["clients"] = 0 if not g["active"] else max(1, g["clients"])
            self._send(200, {"status": "applied", "rolled_back": False, "state": g})
            return
        if path == "/api/iotwifi":
            i = ESTADO["iot"]
            i["active"] = bool(body.get("enabled", i["active"]))
            i["ssid"] = body.get("ssid", i["ssid"])
            i["band"] = body.get("band", i["band"])
            self._send(200, {"status": "applied", "rolled_back": False, "state": i})
            return
        if path == "/api/config/snapshot":
            ESTADO["snapshot_seq"] += 1
            snap = {"id": f"20261126-13000{ESTADO['snapshot_seq']}",
                    "timestamp": int(time.time()), "configs": 9}
            ESTADO["snapshots"].insert(0, snap)
            self._send(200, snap)
            return
        if path == "/api/config/rollback":
            self._send(200, {"status": "applied"})
            return
        if path == "/api/config/snapshot" and self.command == "DELETE":
            self._send(204)
            return
        if path == "/api/config/confirm":
            pending = ESTADO["confirm"]
            if not pending or pending["id"] != body.get("id"):
                self._send(404, {"error": "no pending confirm"})
                return
            ESTADO["confirm"] = None
            ESTADO["snapshot"] = None
            self._send(200, {"status": "kept"})
            return
        if path == "/api/config/confirm/rollback":
            pending = ESTADO["confirm"]
            if not pending or pending["id"] != body.get("id"):
                self._send(404, {"error": "no pending confirm"})
                return
            ESTADO["confirm"] = None
            snap = ESTADO["snapshot"]
            if isinstance(snap, list) and snap and "section" in snap[0]:
                ESTADO["wifi"] = snap
            elif isinstance(snap, list) and snap and "name" in snap[0]:
                ESTADO["radios"] = snap
            ESTADO["snapshot"] = None
            self._send(200, {"status": "reverted"})
            return
        if path == "/api/quotas":
            mac = str(body.get("mac", "")).lower()
            if not mac or not body.get("period") or int(body.get("limit", 0)) <= 0:
                self._send(400, {"error": "quota needs mac, period and a positive limit"})
                return
            ESTADO["quotas"][mac] = {
                "mac": mac,
                "ip": body.get("ip", ""),
                "period": body["period"],
                "limit": int(body["limit"]),
                "action": body.get("action", "notify"),
                "throttle_kbps": int(body.get("throttle_kbps", 0)),
            }
            self._send(200, self._quotas_probe())
            return
        if path == "/api/nftqos":
            mac = str(body.get("mac", "")).lower()
            if not mac or not body.get("ip"):
                self._send(400, {"error": "mac and ip are required"})
                return
            down = int(body.get("download", 0))
            up = int(body.get("upload", 0))
            if down <= 0 and up <= 0:
                ESTADO["qos"].pop(mac, None)
            else:
                ESTADO["qos"][mac] = {
                    "mac": mac,
                    "ip": body["ip"],
                    "download": down,
                    "upload": up,
                }
            self._send(200, {"applicable": True, "limits": ESTADO["qos"]})
            return
        if path == "/api/parental":
            mac = str(body.get("mac", "")).lower()
            days = body.get("days") or []
            if not mac or not days:
                self._send(400, {"error": "mac and at least one day are required"})
                return
            ESTADO["parental"][mac] = {
                "mac": mac,
                "enabled": bool(body.get("enabled")),
                "days": sorted(int(day) for day in days),
                "start": body.get("start", "00:00"),
                "end": body.get("end", "00:00"),
                "paused": bool(body.get("paused")),
            }
            self._send(200, {"rules": ESTADO["parental"], "ts": now() * 1000})
            return
        self._send(404, {"error": "not found"})

    def do_DELETE(self):
        path = self.path.split("?", 1)[0]
        query = self.path.split("?", 1)[1] if "?" in self.path else ""
        print(f"DELETE {path} {query}", flush=True)
        if path == "/api/config/snapshot":
            sid = ""
            for part in query.split("&"):
                if part.startswith("id="):
                    sid = part[3:]
            ESTADO["snapshots"] = [x for x in ESTADO["snapshots"] if x["id"] != sid]
            self._send(204)
            return
        if path in ("/api/quotas", "/api/nftqos", "/api/parental"):
            mac = ""
            for part in query.split("&"):
                if part.startswith("mac="):
                    mac = urllib.parse.unquote(part[4:]).lower()
            table = {"/api/quotas": "quotas", "/api/nftqos": "qos", "/api/parental": "parental"}[path]
            ESTADO[table].pop(mac, None)
            if path == "/api/quotas":
                self._send(200, self._quotas_probe())
            elif path == "/api/nftqos":
                self._send(200, {"applicable": True, "limits": ESTADO["qos"]})
            else:
                self._send(200, {"rules": ESTADO["parental"], "ts": now() * 1000})
            return
        self._send(404, {"error": "not found"})

    def do_PUT(self):
        path = self.path.split("?", 1)[0]
        body = self._json()
        print(f"PUT {path} {body}", flush=True)
        if path == "/api/selfupdate/schedule":
            cfg = ESTADO["schedule"]["config"]
            for key in ("enabled", "intervalHours", "windowStart", "windowEnd"):
                if key in body:
                    cfg[key] = body[key]
            self._send(200, ESTADO["schedule"])
            return
        self._send(404, {"error": "not found"})

    def _first(self, sections):
        for iface in ESTADO["wifi"]:
            if iface["section"] in sections:
                return iface
        return ESTADO["wifi"][0]

    def _wipe_expired(self):
        pending = ESTADO["confirm"]
        if pending and pending["expires"] <= now():
            snap = ESTADO["snapshot"]
            if isinstance(snap, list) and snap and "section" in snap[0]:
                ESTADO["wifi"] = snap
            elif isinstance(snap, list) and snap and "name" in snap[0]:
                ESTADO["radios"] = snap
            ESTADO["confirm"] = None
            ESTADO["snapshot"] = None

    def _risky(self, payload):
        expires = now() + CONFIRM_SECS
        ESTADO["confirm"] = {"id": "mock", "expires": expires}
        payload["confirm"] = {"id": "mock", "expires": expires}
        self._send(200, payload)


if __name__ == "__main__":
    print(f"mock netgrip en http://0.0.0.0:{PORT} ({HOSTNAME})", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
