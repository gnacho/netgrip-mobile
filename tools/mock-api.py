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
    "/api/selfupdate": {"available": False, "current": "0.79.99-mock"},
    "/api/logs": {"entries": [
        {"ts": "Thu Nov 26 18:00:00 2026", "facility": "daemon", "level": "info",
         "tag": "netgrip[1]", "message": "listening on http://0.0.0.0:8080"},
    ], "total": 1},
    "/api/leds": {"applicable": False, "leds": []},
    "/api/fleet": {"nodes": []},
    "/api/clients/meta": {"meta": {}},
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

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/api/me":
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
        if path == "/api/config/confirm":
            pending = ESTADO["confirm"]
            if pending and pending["expires"] > now():
                self._send(200, {"pending": True, "id": pending["id"], "expires": pending["expires"]})
            else:
                ESTADO["confirm"] = None
                self._send(200, {"pending": False})
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
