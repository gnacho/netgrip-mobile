# Fleet

Part of #26, the first half: see the routers the panel manages together, check
them and adopt the ones announcing themselves on the LAN. The update batch
lands in the second half.

The panel is the one that logs into each router: it has their passwords, the
phone does not. Everything on this tab is a request to the panel or the last
state it recorded.

## Readings

| Action | Endpoint | Notes |
|---|---|---|
| List the fleet | `GET /api/fleet` | Last known status, the one left by the previous check |
| Check one | `POST /api/fleet/check` with `{"id":...}` | The panel enters that router and returns a fresh status |
| Check all | `POST /api/fleet/check-all` | The panel runs them in parallel and returns the same shape |
| Discovered peers | `GET /api/fleet/discovered` | Announced peers that are not in the fleet yet |
| Discovery switch | `GET|POST /api/fleet/discovery-config` | `{"enabled":bool}` |

`FleetNodeDto` is the same shape for the list and for a single check: `id`,
`name`, `address`, `reachable`, `current_version`, `latest_version`,
`update_available` and `error`. A node the panel has never checked arrives with
`reachable: false` and no `error`, which the app paints as "Sin datos", not as
"no responde": an unchecked router is not a broken one.

## Adopting a peer

`POST /api/fleet/adopt` takes `id`, `name`, `address` and `password`. The panel
verifies it can log in before saving anything, so a wrong password leaves its
message in the card and the peer stays in the discovered list.

- The address is composed from the beacon: `address` alone when the port is 0,
  `host:port` otherwise, with brackets for IPv6. `FleetRules.hostPort` does it
  and is tested, including the case of an address that already carries a port.
- The password is the admin password of that router. It is typed in the dialog,
  sent once and never stored by the app.
- The name is prefilled from the beacon and can be changed before adopting.

## What the app does not do

- **No probing from the phone**. Checking means asking the panel, which is the
  only one with credentials. Coming back empty-handed from that request is an
  error to show, never a "no update" conclusion.
- **No fleet edits**. Adding by hand, removing or renaming nodes stays in the
  panel; the app reads and adopts what the LAN announced.
- **No reboot**. The panel has no per-node reboot endpoint in the fleet API, so
  the issue line about rebooting selected devices is not something the app can
  do today. It would need a server endpoint first.

## Tests

`FleetRulesTest` covers the address composition, the header summary and the skip
reasons. `FleetDtoParsingTest` parses the panel bodies: a checked node, one with
only the essentials, the discovered peers with `seen_at`, and the adopt body
(the fields are sent even when empty, because the DTO has no defaults).
`FleetViewModelTest` checks the three reads, the single check replacing only its
node, the summary after checking all, the adopt body with the composed address
and the reload, the rejection from the panel, the discovery switch and the 401.
