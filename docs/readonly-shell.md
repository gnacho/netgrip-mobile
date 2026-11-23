# Read-only mobile shell

Second deliverable of netgrip-mobile: the phase 1 spike dashboard is replaced by a
five-tab shell that reads the real NetGrip API. Nothing in this document writes
to the router. The only non-GET calls of the shell are the existing login and
logout. The reversible client actions added later are described in
[client-writes.md](client-writes.md).

## Contract

The contract below was checked against `app/src/types.ts` and the Go handlers in
`internal/server/server.go` and `internal/modules`. Every DTO lives in
`shared/src/commonMain/kotlin/dev/gnacho/netgrip/data/dto` and is tolerant:
unknown keys are ignored and optional router fields have defaults.

| Endpoint | Shape used by the app |
|---|---|
| `GET /api/board` | `hostname`, `model`, `kernel`, `release.distribution/version/revision`. Extra ubus keys ignored |
| `GET /api/system` | `uptime` (s), `load` (1/5/15), `memory.*` in bytes, `root.*` in KiB |
| `GET /api/wan` | `present`, `up`, `uptime` (s), `ipv4[]`, `gateway?`, `dns[]` |
| `GET /api/mode` | `role?`, `mode`, `has_wifi`, `port_count`, `dnsmasq_on?`, `firewall_on?` |
| `GET /api/clients` | `clients[]`, `bands[]`, `ts`. Mac, ip, type, signal, counters, block/reserve/parental/quota flags |
| `GET /api/ethports` | `ports[]` with `name`, `wan`, `up`, `speed_mbps`, `devices[]` |
| `GET /api/wifi` | `interfaces[]` with section, radio, band, ssid, encryption, `has_key`, hidden, disabled, clients |
| `GET /api/wireless` | bare array of radios with `name`, `up`, `band`, `channel`, `htmode`, `txpower` |
| `GET /api/access` | `panel`, `luci`, `ssh` port and exposure state |
| `GET /api/mdns` | `installed`, `enabled`, `running`, `domain` |
| `GET /api/selfupdate` | `current`, `latest`, `available`, `notes`, `check_error?` |
| `GET /api/history` | `entries[]` con `ts` (s), `rx` y `tx` acumulados del puente LAN, `load` y `clients`. Anillo de 288 muestras cada 5 min |

`role` is optional in older firmware. The app falls back to `ap` when `mode` is
`ap`, and to `router` otherwise, the same rule the web panel uses.

Not used: `/api/update`, `/api/wifichan`, `/api/wifi/key`, and any POST other
than login and logout.

Error mapping: `401` becomes `Unauthorized`, `429` keeps `Retry-After`, any other
non-200 status becomes `HttpError(code)` (502 when ubus fails). A malformed body
is reported as an error instead of crashing.

## Tabs

- **Panel**: board, health score, WAN, RAM, flash, load, chassis ports and client
  summary. Health is a port of `useHealthScore` limited to the data this app
  reads: WAN down in router mode (-40), RAM used 75/90 % (-10/-20), flash free
  20/10 % (-10/-20), load 2/4 (-5/-10), all radios down (-10).
- **Clientes**: real list with search by name, IP and MAC, connection type,
  signal and block/reserve/parental/quota badges. Tapping opens the detail:
  IP, MAC, interface, lease source, IP source, quota and traffic counters.
- **WiFi**: networks grouped by SSID with bands, encryption, hidden and disabled
  state, client count, and radio data (channel, htmode, txpower) crossed from
  `/api/wireless`. Keys are never requested or shown. `has_wifi=false` shows an
  explicit empty state.
- **Puertos**: port state, negotiated speed and devices. A link up below
  1000 Mb/s is flagged as degraded.
- **Ajustes**: board, mode, access, mDNS, self-update check, current base URL and
  logout, with a note that changes are made from the web panel.

Each tab loads its reads the first time it opens and on demand with the refresh
action. There is no polling. Any `401` closes the session once through
`SessionViewModel.onSessionExpired()` and returns to login without retrying.

## Visual system

The Compose theme mirrors the web panel instead of defining a separate identity:
the light and dark palettes, the 16 dp cards with a 1 dp border and a soft
shadow, the 12 dp icon tiles, the pills with a status dot, the segmented
control, the 22/28 page title and the 11/16 eyebrow come from
`app/src/index.css` and `design-rev2.md`.

Material 3 has no success, warning or faint roles, so they live in
`NetGripPalette`, provided through `LocalNetGripPalette` and read as
`MaterialTheme.netGrip`.

Icons are the Lucide set the web panel uses. `ui/common/NetGripIcons.kt` holds 52
`ImageVector`s generated from the original SVG path data with
`PathParser`, so the silhouette and the 2 px stroke match without pulling a
30 MB icon pack. `deviceIcon()` maps the 21 device types to the same icons as
`app/src/components/clients/catalog.ts`.

Navigation follows the panel: a grouped side menu (`Resumen`, `Tu red`,
`Sistema`) that is permanent at 1000 dp or more, a 76 dp rail from 720 dp and a
modal drawer on phones. The drawer footer carries the health pill, the theme
selector (`Auto`, `Claro`, `Oscuro`) and logout. The theme mode is stored in the
same encrypted storage as the session and applied to the system bars.

## Charts

The Panel draws the LAN bridge traffic from `/api/history`. The router stores
accumulated counters every five minutes, so `TrafficSeries` derives rates from
consecutive deltas, ignores negative deltas (counter reset) and recalculates the
totals for the selected window. The chart is a Compose `Canvas`: 25/50/75/100 %
grid, midpoint-smoothed curves, 12 % area fill, series colours from the theme
and a touch crosshair with the values of the selected sample. Windows are 1, 6
and 24 hours.

Client detail has no time series available, so it shows the traffic of the
current connection as a split bar between upload and download.

## Gates

```
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug
```

Host tests cover DTO parsing against demo-shaped JSON, typed GET error mapping,
health score, client search and labels, WiFi grouping, port insight, formatting
and the shell view model (loading, partial failure, 401, no reload).
