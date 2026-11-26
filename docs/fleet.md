# Fleet

Closes #26: see the routers the panel manages together, check them, adopt the
ones announcing themselves on the LAN and update them in a batch, one after
another, with a result per device.

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

## Updating a batch

`POST /api/fleet/update` with `{"id":...}` only tells the peer to update its own
panel; the peer restarts and the panel loses contact for a while. So the app
treats that 200 as "order sent", never as "updated":

1. The batch starts with `check-all`, because skipping or updating on a stale
   status would be guessing. Every node gets its reason: no answer, already on
   its version, or no identifier.
2. The candidates are updated **sequentially**, one request per device.
3. After each order, the app polls `POST /api/fleet/check` every 10 seconds for
   up to 5 minutes. A node counts as updated only when a check sees a different,
   non-empty `current_version` and it is reachable.
4. Anything else is reported as it is: `Sin confirmar` when it never came back
   with a new version, `Error` when the order itself failed, `Omitido` with the
   reason when it never entered the batch.
5. `Detener el lote` stops at the next poll. The device being updated stays
   `Sin confirmar` ("detenido antes de confirmar") and whatever had not started
   is `No ejecutado`. Nothing that had been ordered is rolled back: the app says
   so instead of pretending.

## Tests

`FleetRulesTest` covers the address composition, the header summary and the skip
reasons. `FleetDtoParsingTest` parses the panel bodies: a checked node, one with
only the essentials, the discovered peers with `seen_at`, and the adopt body
(the fields are sent even when empty, because the DTO has no defaults).
`FleetViewModelTest` checks the three reads, the single check replacing only its
node, the summary after checking all, the adopt body with the composed address
and the reload, the rejection from the panel, the discovery switch and the 401.
`FleetBatchTest` covers the batch: only the candidates get an order, the skips
carry their reason, a 200 followed by the same version ends as `Sin confirmar`,
a rejected order is `Error` without polling, cancelling leaves the current one
unconfirmed and the rest unexecuted, and a failed `check-all` starts nothing.
