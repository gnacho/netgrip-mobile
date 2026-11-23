# Safe client writes

Third deliverable of netgrip-mobile: the client tab stops being read-only and
gains four reversible actions, each with confirmation, busy state, server error
reporting and a re-read of the router state. Nothing else is written.

## Scope

| Action | Endpoint | Inverse |
|---|---|---|
| Rename and set the device type | `POST /api/clients/meta` | Save empty name and type, which deletes the override |
| Block or unblock | `POST /api/clients/block` | Unblock in the same band or in all bands |
| Pin or remove the current IP | `POST /api/clients/reserve` | Remove the reservation |
| Wake on LAN | `POST /api/wol` | None, it does not persist anything |

Reboot, password, updates, WAN, LAN, access, HTTPS, parental, quota and firewall
settings stay out of the app on purpose.

## Contract

Checked against `internal/server/server.go`, `internal/modules/clients.go`,
`internal/modules/clients_meta.go` and the web client (`app/src/api.ts`,
`app/src/pages/Clients.tsx`).

| Endpoint | Request body | Response |
|---|---|---|
| `POST /api/clients/meta` | `{"mac","name","device_type"}` | `{"meta":{"<mac>":{"name","device_type"}}}` |
| `POST /api/clients/block` | `{"mac","type","band","blocked"}` | `{"rolled_back":bool,"status":"applied"}` |
| `POST /api/clients/reserve` | `{"mac","ip","reserved"}` | `{"rolled_back":bool,"status":"applied"}` |
| `POST /api/wol` | `{"mac"}` | `{"status":"sent"}` |
| `GET /api/clients/meta` | none | `{"meta":{"<mac>":{"name","device_type"}}}` |

Any non-2xx can carry `{"error":"..."}`. The app keeps that text in
`ApiResult.HttpError.message` and shows it instead of a generic HTTP code. A 401
still closes the session once.

## Rules

- **Meta**: the POST replaces the whole entry, so both fields always travel.
  The name is trimmed and cut at 40 characters. Empty name and empty type delete
  the override and restore the router-provided name. The type picker uses the
  21 keys of the web catalog plus "Sin tipo".
- **Block**: `type` is `"wifi"` or `"cable"`, never `wifi24`/`wifi5`. The band
  always comes from the `bands` array of `/api/clients` (the payload), filtered
  to `2g`, `5g` and `6g`. `band=""` means all bands. The app refuses to block the
  device marked `self` or a client with `blockable=false`, and the button is not
  shown in that case. WiFi block only disconnects that client and reloads the
  radio access list. Cable block adds a firewall REJECT rule and reloads the
  firewall, so it only exists on a gateway.
- **Reserve**: there is no free IP field. The app only pins the IP the router is
  already serving, and only when `reservable=true`, the IP is non-empty and the
  client is not reserved yet. Removing a reservation is allowed whenever
  `reserved=true`. Both keep connectivity.
- **Wake on LAN**: offered as a secondary action in the detail sheet. It sends
  the magic packet and shows a snackbar. The router cannot confirm the device
  woke up, so the app does not claim it.
- Unblocking offers one button per band in `blocked_on` plus "todas las bandas"
  when several bands are blocked.

## Verification after a write

The app never paints the change optimistically. After a 200 it re-reads
`/api/clients` and `/api/clients/meta` and compares:

- Block: `blocked` for all bands, or the band in `blocked_on`.
- Reserve: `reserved` equals the requested value.
- Meta: the returned map already contains the change, so it is checked on the
  POST response and the list is re-read for names.

`/api/clients` is cached for 2 s on the server, so an unchanged first read is
retried once after 2.1 s. If the state still does not match, the snackbar says
so and points to the web panel, especially for reservations that may already
exist by hand. If the client disappears from the list (the normal case when a
WiFi client is blocked and disconnected), the app reports that it applied the
change but cannot show the client any more.

## Gates

```
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug
```

New host tests cover MAC validation, name trimming to 40 characters, the device
type catalog, band selection from `bands` and never from `Client.type`, self and
non-blockable refusal, request bodies byte by byte through MockEngine, 200 and
`{"error"}` responses, 401, rolled-back responses, reservation limited to the
current IP, re-read confirmation with the 2.1 s retry, honest "state unchanged"
messages, and no re-read after Wake on LAN.

## Limitations

- A WiFi client blocked in all bands disappears from `/api/clients`, so the
  phone can only unblock partial blocks (one band while still associated). A
  fully blocked WiFi client is unblocked from the web panel.
- Cable block and reserve on an AP depend on the gateway: the server needs the
  firewall (cable) or the read-only SSH key (reserve). The app shows the server
  error when that is missing.
- The re-read retry adds up to 2.1 s to the busy state, by design.
- No local undo history: every action is reversible from the same sheet while
  the router keeps reporting the client.
