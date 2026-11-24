# GoodCloud parity plan

Analysis of what GL.iNet GoodCloud offers, what NetGrip already has behind its API, and what the Android app still needs to reach the same management surface.

## Sources

- GL.iNet Router Docs 4, GoodCloud: <https://docs.gl-inet.com/router/en/4/interface_guide/cloud/>
- GoodCloud Site to Site: <https://docs.gl-inet.com/router/en/4/tutorials/goodcloud_site_to_site/>
- GoodCloud product page and plans: <https://www.gl-inet.com/en-us/pages/goodcloud>
- GL.iNet mobile app listing, for the phone-side feature set: <https://play.google.com/store/apps/details?id=xyz.goodcloud.glinet>

## What GoodCloud actually offers

| Area | Capability |
|---|---|
| Status | Online and offline state, RAM usage, load average, uptime, model, firmware, MAC, IP |
| Alerts | Email rules for online and offline changes, new clients and block events (one rule per individual user) |
| WiFi | Read and change SSID and password remotely |
| Clients | Connected device list, real time traffic, block a client |
| Remote access | Remote web admin panel and remote SSH through the cloud relay, no public IP needed |
| Sharing | Share a device with another account, with an expiry for support sessions |
| Batch | Reboot and firmware upgrade across many devices, configuration templates, bulk import by spreadsheet, device groups |
| Site to site | Encrypted tunnel between 2 to 10 routers, one of them with a public IP |
| Logs | Activity, device, upgrade, alert and settings logs |
| Business | Organizations, inventory for resellers, white label, cloud to cloud integration |
| Hardware specific | LTE signal (cellular models), BLE MQTT bridge |

The GL.iNet mobile app adds: first time setup and password change, mesh setup, VPN client configuration, pausing WiFi, LED control and firmware update.

## The difference that matters

GoodCloud is a cloud account plus a relay: the router opens an outbound tunnel to a GL.iNet server and the account reaches it there. NetGrip is a router local panel with an HTTP API. Two consequences for this plan:

1. **Almost every GoodCloud management feature already exists in NetGrip's API.** The server registers 248 API routes, including WiFi writes, clients, blocking, traffic accounting, DPI, quotas, parental control, firewall exposure, DDNS, WireGuard, OpenVPN, Tailscale, firmware update with progress, config snapshots with rollback, fleet operations and Telegram and ntfy notifications. The gap is mostly the Android UI, not the backend.
2. **Reaching a router from outside the LAN is transport, not a feature.** NetGrip already ships three: DDNS entries, WireGuard and OpenVPN servers, and Tailscale. The app should guide the user to one of them and open the router through it. Building a fourth, NetGrip branded relay, is a separate product decision with hosting, identity and privacy costs.

## Verified state

### NetGrip server (route table, `internal/server/server.go`)

| Block | Endpoints |
|---|---|
| System and identity | `GET /api/board`, `GET /api/system`, `GET /api/history` (traffic ring), `GET /api/mode`, `POST /api/reboot`, `POST /api/password` |
| Firmware | Router: `GET /api/update`, `POST /api/update` (sysupgrade), `GET /api/packages`, `POST /api/packages/upgrade`. NetGrip binary: `GET /api/selfupdate`, `POST /api/selfupdate`, `GET /api/selfupdate/status`, `GET|PUT /api/selfupdate/schedule` |
| WiFi | `GET|POST /api/wifi`, `POST /api/wifi/radio`, `POST /api/wifichan`, `GET|POST /api/guestwifi`, `GET|POST /api/iotwifi`, `GET|POST /api/wifischedule` |
| Clients | `GET /api/clients`, `GET|POST /api/clients/meta`, `POST /api/clients/block`, `POST /api/clients/reserve`, `POST /api/wol` |
| Traffic | `GET /api/nlbwmon`, `GET /api/nlbwmon/top`, `POST /api/nlbwmon`, `GET /api/dpi`, `GET /api/dpi/apps`, `GET /api/dpi/timeline` |
| Policy | `GET|POST /api/quotas`, `GET|POST /api/nftqos`, `GET|POST|DELETE /api/parental`, `GET|POST /api/mac-acl` |
| Remote access | `GET|POST /api/ddns`, `POST /api/ddns/force`, `GET /api/wireguard`, `GET /api/openvpn`, `GET /api/tailscale`, `GET|POST /api/remoteaccess` |
| Notifications | `GET|POST /api/telegram`, `POST /api/telegram/test`, `GET|POST /api/ntfy`, `POST /api/ntfy/test` |
| Config | `GET /api/config/snapshots`, `POST /api/config/snapshot`, `GET /api/config/snapshot/export`, `GET /api/config/diff`, `POST /api/config/rollback`, `GET|POST /api/push-config` |
| Fleet | `GET|POST|DELETE /api/fleet`, `POST /api/fleet/check`, `POST /api/fleet/check-all`, `POST /api/fleet/update`, `POST /api/fleet/adopt`, `GET /api/fleet/discovered` |
| Diagnostics | `GET /api/diagnostics/selftest`, `POST /api/diagnostics` |

Not present in the route table: device logs, LED control, email alerts, user accounts or sharing, SMS or modem status.

### Android app 0.3.0

| Screen | State |
|---|---|
| Panel | Health, WAN, resources, chassis, client summary, 24 hour traffic chart |
| Clientes | List, search, detail, rename, device type, block and unblock, reserve, Wake on LAN |
| WiFi | Read only: SSIDs, bands, encryption, radios |
| Puertos | Read only: link state, speed, devices, degraded link |
| Ajustes | Read only: board, mode, access, mDNS, update state, theme, logout |
| Shell | Multiple networks and devices, per device session, fleet import |

## Gap table

| GoodCloud capability | NetGrip API | App today | Work | Where |
|---|---|---|---|---|
| Status, RAM, load, uptime | Yes | Done | Nothing | App |
| Clients and blocking | Yes | Done | Nothing | App |
| WiFi read | Yes | Done | Nothing | App |
| WiFi change (SSID, key, radio, channel) | Yes | Missing | Write UI with confirmation | App |
| Guest and IoT WiFi | Yes | Missing | Screens plus writes | App |
| WiFi schedule | Yes | Missing | Schedule editor | App |
| Reboot | Yes | Missing | Confirmation, disconnect handling | App |
| Router firmware update (sysupgrade) | Yes | Missing | Update flow with confirmation and reconnect | App |
| NetGrip binary update and schedule | Yes | Read only | Update flow with progress and schedule | App |
| Administrator password change | Yes | Missing | Change with re-login and warning | App |
| Real time per client traffic | Yes (nlbwmon, DPI) | Missing | Traffic screen and per client detail | App |
| Quotas, QoS, parental | Yes | Missing | Editors per client | App |
| Remote access status and setup | Yes (DDNS, WireGuard, OpenVPN, Tailscale, WAN exposure) | Missing | Remote access screen | App |
| Alerts (Telegram, ntfy) | Yes | Missing | Configuration and test | App |
| Logs | No | Missing | New endpoint | Server |
| LED control | No | Missing | New endpoint | Server |
| Email alerts | No | Missing | SMTP channel or documented alternative | Server |
| Config snapshots and rollback | Yes | Missing | Snapshot list, export, rollback | App |
| Batch reboot, update, push config | Yes (fleet) | Partial (import only) | Multi device actions from the app | App |
| Sharing with other people | No | Missing | Identity and ACL | Server, product |
| Site to site | Partial (WireGuard server) | Missing | Orchestration, not a phone screen | Server |
| Organizations, VAR, white label | No | Not applicable | Out of scope | None |
| LTE signal, SMS | No | Not applicable | No cellular hardware | None |
| BLE MQTT bridge, mesh | No | Not applicable | GL specific, NetGrip uses usteer | None |

## What not to port, and why

- **Organizations, inventory, white label, bulk import by spreadsheet.** They exist so a reseller can manage customers. A self hosted panel with one administrator per device does not need a second layer of tenancy, and adding it would put an account system in front of the router credentials.
- **Cloud relay for remote access.** It is the one GoodCloud feature that cannot be copied as a UI. It needs servers, an identity provider and a privacy model. NetGrip already has three self hosted transports, and the phone can reach them without new infrastructure.
- **LTE, SMS, BLE, mesh.** Tied to hardware and firmware that NetGrip does not target.
- **Site to site as a phone feature.** The tunnel is configured on the routers; the app can show its state, not build it.

## Phases

### Phase 1: app only, API already exists

1. WiFi writes: SSID and key, radio on or off, channel, guest network, IoT network, schedule.
2. Reboot, router firmware update, NetGrip binary update and administrator password change, with confirmation, progress and reconnect handling.
3. Remote access screen: DDNS entries, Tailscale state and login link, WireGuard and OpenVPN status, WAN exposure toggles for ping, HTTPS and SSH.
4. Notifications: Telegram and ntfy configuration with a test button.
5. Diagnostics and tools: self test, ping, traceroute.
6. Config snapshots: list, create, export, diff and rollback.
7. Traffic and policy: per client traffic from nlbwmon, per app from DPI, quota, QoS and parental editors.
8. Fleet: check, update, reboot and push config across the saved devices.

### Phase 2: server work

- Logs endpoint and a log viewer in the app.
- LED control endpoint.
- Email channel for alerts, or an explicit decision to keep Telegram and ntfy as the only channels.
- Per client traffic history, because today only counters since connection are available.

### Phase 3: product decisions

- Which transport the app recommends during onboarding: Tailscale, WireGuard or DDNS.
- Whether sharing a device with another person belongs in a self hosted tool, and with what identity model.

## Tracking

| Work | Issue |
|---|---|
| WiFi configuration writes | #20 |
| Reboot and firmware update flows | #21 |
| Remote access screen | #22 |
| Notifications and diagnostics | #23 |
| Config snapshots and rollback | #24 |
| Traffic and policy per client | #25 |
| Fleet actions across saved devices | #26 |
| Recommended remote access transport | #27 |
| Server: logs and LED endpoints | gnacho/netgrip#642 |

## Risks and rules

- Writes over a remote link keep the same rules as the existing client writes: confirmation, re read after the write, and never an action that can lock the administrator out.
- A firmware update started from a phone can outlive the app. The status endpoint already reports progress, so the app must resume the view after a restart instead of tracking the request itself.
- A reboot drops the connection. The app must show it explicitly and retry the session instead of showing a generic network error.
- Snapshot rollback is destructive. It needs a diff preview and an explicit confirmation.
