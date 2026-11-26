# WiFi writes

Fourth deliverable of netgrip-mobile: the WiFi tab stops being read-only. It
can rename and resecure a network, hide it, stop emitting it, and change the
channel, width and power of a radio. Every write is confirmed first, arms the
router's rollback window, re-reads the state and never shows the key.

## Scope

| Action | Endpoint | Consequence |
|---|---|---|
| SSID, key, security, hidden, emitting | `POST /api/wifi` | The radio reloads; clients of that network may disconnect |
| Channel, width, power | `POST /api/wifi/radio` | The radio bounces; its clients reconnect |
| Keep the applied change | `POST /api/config/confirm` | None |
| Undo the applied change | `POST /api/config/confirm/rollback` | Restores the snapshot taken by the server |
| Guest network | `GET|POST /api/guestwifi` | The router snapshots network, DHCP, firewall and wireless and reloads them |
| IoT network | `GET|POST /api/iotwifi` | The router snapshots wireless and reloads the radio |

Guest network and IoT network are included. WiFi schedules are still open,
the last part of #20.

## Contract

Checked against `internal/server/server.go`, `internal/server/confirm.go`,
`internal/modules/wifi.go` and `app/src/components/WifiEditModal.tsx`.

| Endpoint | Request body | Response |
|---|---|---|
| `POST /api/wifi` | `{"section","sections","ssid","key","encryption","hidden","disabled","confirm"}` | `{"status","rolled_back","state","confirm":{"id","expires"}}` |
| `POST /api/wifi/radio` | `{"radio","channel","htmode","txpower","confirm"}` | Same envelope |
| `GET /api/config/confirm` | none | `{"pending","id","expires"}` |
| `POST /api/config/confirm` | `{"id"}` | `{"status":"kept"}` |
| `POST /api/config/confirm/rollback` | `{"id"}` | `{"status":"reverted"}` |

Null fields are omitted, so only what changes travels. `confirm: true` is not
the user confirmation (the app asks for that in a dialog): it tells the server
to keep a snapshot and arm a 120 s auto-revert window.

## Rules

- **One network, all its bands**. The editor applies the change to every
  interface that shares the SSID (`sections`), which is the web panel default.
  Separating bands into different names is not offered here.
- **The key is write-only**. The app never calls `GET /api/wifi/key`, never
  keeps the key in state, and clears the field after applying. An empty field
  means "leave the current key".
- **Security choices** are WPA2 (`psk2`), WPA3 (`sae`) and WPA2/WPA3
  (`sae-mixed`). "Open" (`none`) is only offered when the network is already
  open: the app does not strip encryption from an existing network. Old values
  (`psk`, `psk-mixed`) are shown as WPA2.
- **Limits checked locally**: SSID 1 to 32 bytes after trimming, key 8 to 63
  characters when provided. The router still validates and its message is
  shown verbatim.
- **Radio**: channel must be `auto` or a number for that band, width one of
  `HT/VHT/HE` + `20/40/80/160`, power 1 to 40 dBm. Only the fields that differ
  from the current state are sent; saving nothing is not allowed.
- **Confirmation dialog** names what changes (name, key, security, hidden,
  emitting) and warns that clients may disconnect.

## Guest and IoT

- **Guest** exists only on a gateway. On a dumb AP the card explains why it
  cannot be configured instead of offering a button.
- **Key**: the router only requires it when it has to create the network. On an
  existing guest network an empty field keeps the current key; the app checks
  this locally and does not block edits that do not touch the key. The IoT
  endpoint always rebuilds its sections, so it always asks for a key.
- **Subnet** is optional and validated as IPv4, rejecting the network and
  broadcast addresses. Empty keeps the current one.
- **Isolation** is sent as `isolate` for guest and is fixed to on by the server
  for IoT.
- Neither endpoint arms the 120 s rollback window; they take a snapshot, apply,
  run a healthcheck of up to 75 seconds and roll back on failure. The POSTs
  therefore use a 100 s timeout instead of the app default, and the result is
  read from the response and confirmed with a re-read.

## Verification after a write

- On `status: applied` with a `confirm` window, the app opens the window, shows
  a countdown with Keep and Undo, and re-reads `/api/wifi` and `/api/wireless`.
- On `rolled_back` or `status: failed` it re-reads and reports the server
  message; it never keeps the change on screen.
- When the countdown reaches zero the app re-reads and says the router undid
  the change. It does not assume the change survived.
- A pending window is re-hydrated from `GET /api/config/confirm` when the tab
  loads, so closing the app does not orphan it.
- Nothing is painted optimistically: the list only shows what the router
  returned.

## Gates

```
./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug
```

Host tests cover guest and IoT request bodies, the "key only when creating"
rule, subnet validation, the rollback paths, the encryption catalog and its "open only if already open"
rule, SSID and key limits, the change summary, channels and widths per band,
the exact POST bodies (including omitted nulls), the confirm/keep/rollback
calls, the rollback and server-error paths, the countdown expiry, and
re-hydrating a pending window.

## Limitations

- No key generator: Kotlin's `Random` is not cryptographic and the shared
  module has no secure random, so the user types the key.
- Band separation (one SSID per band) stays in the web panel.
- `htmode` prefixes depend on the standard of each radio; the app offers the
  per-band list the panel uses and the router rejects what it cannot apply.
- The app cannot tell whether the phone itself is connected to the network it
  is editing, so the warning is always shown.
