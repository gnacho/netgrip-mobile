# Traffic

Closes #25: the tab shows what the router has already counted and lets each
device carry a data quota, a bandwidth limit and a parental schedule.

Every number in this tab comes from the router. The app does not estimate, does
not derive rates from the counters and does not fill gaps: if the router does
not measure something, the card says so.

## Readings

| Source | Endpoint | Shape |
|---|---|---|
| Accounting probe | `GET /api/nlbwmon` | `installed`, `running`, `generations`, `commit_interval`, `prealloc_days`, `protocol_database` |
| Top talkers | `GET /api/nlbwmon/top` | `devices[]` and `apps[]` with `key`, `ip`, `conns`, `down_bytes`, `up_bytes` |
| DPI protocols | `GET /api/dpi` | `applicable`, `total_bytes`, `total_flows`, `protocols[]` |
| DPI applications | `GET /api/dpi/apps` | `apps[]` with `name`, `category`, `bytes`, `local_bytes`, `other_bytes`, `packets`, `flows` |
| DPI timeline | `GET /api/dpi/timeline` | `buckets[]` of 5 minutes with `time` (RFC3339) and `apps`, plus `top[]` and `totals` |

Names are resolved with `GET /api/clients` and `GET /api/clients/meta`: the name
saved by hand wins, then the one the client announces, then the IP and finally
the MAC. If those two reads fail the tab still paints, with IPs and MACs.

## What the app does not do

- **No rates**. `nlbwmon` returns byte counters and `netifyd` returns bytes per
  5 minute bucket. Dividing them by the elapsed time would be a number the
  router never measured, so the chart is labelled in bytes per bucket.
- **No invented devices**. Devices without a key are dropped. `nlbwmon` reports
  traffic it cannot attribute with an all-zero MAC, which is not a device.
- **No empty DPI screen crash**. `/api/dpi` used to serialize `protocols: null`
  when the panel could not read conntrack (fixed server side in netgrip #661).
  The DTO keeps the field nullable and `TrafficRules.protocolRows` normalizes it,
  so the tab survives the old shape too.
- **No install button**. Installing or starting `nlbwmon` is a write and is not
  part of this tab; the card only explains why there is no data.

## Policies

| Policy | Read | Write | Remove |
|---|---|---|---|
| Data quota | `GET /api/quotas` | `POST /api/quotas` | `DELETE /api/quotas?mac=` |
| Bandwidth | `GET /api/nftqos` | `POST /api/nftqos` | `DELETE /api/nftqos?mac=` |
| Parental schedule | `GET /api/parental` | `POST /api/parental` | `DELETE /api/parental?mac=` |

- **Every write is confirmed in a dialog and re-read**: the app posts, then reads
  the three probes again. Nothing is painted optimistically, and if the re-read
  fails it says so instead of showing the old state as if it were new.
- **The whole body goes on every write**. The server rejects an empty `period`
  or `action`, so `QuotaDto` and `ParentalRuleDto` have no defaults: kotlinx would
  drop the fields that match them and the router would answer 400.
- **The MAC is normalized and the IP comes from the client**, not from the form:
  quota and QoS are applied per client IP and the router needs the current one.
- **Quota units**: the editor works in MiB or GiB and converts to bytes, which is
  what `/api/quotas` expects. `throttle_kbps` is only sent when the action is
  `throttle`; the server rejects it otherwise.
- **Parental semantics** (verified in `internal/modules/parental.go`): the window
  blocks outside it, `paused` blocks immediately even with the schedule off, and
  the days are Go weekdays, Sunday being 0.
- Both `quotas.applicable` and `nftqos.applicable` are false outside the gateway;
  the sheet explains it and disables the buttons instead of sending a write that
  can only fail.
- **The card lists only the devices that already have a policy**, with a count
  and a button to expand to the whole client list when a new one has to be
  created. A router can have fifty clients: listing them all pushed the DPI and
  the timeline cards off the screen.

## Partial failures

The seven reads are independent. One failing endpoint leaves its card empty and
the first error is shown at the bottom; a 401 anywhere sends the user back to the
login as in the rest of the shell. If `netifyd` is not applicable, the DPI card
explains it instead of showing an empty table.

## Tests

`TrafficRulesTest` covers the name resolution, the ordering, the `other` label,
the `protocols: null` normalization and the RFC3339 parser (UTC, offsets,
fractions and garbage). `TrafficDtoParsingTest` parses the bodies the panel
actually serves, including the null `protocols` and unknown keys.
`TrafficViewModelTest` checks the seven reads, the partial failure, the 401 and
the `ensure` guard.

`PolicyRulesTest` covers the amount parser (comma and dot, MiB and GiB), the
validations for quota, bandwidth and schedule, and the day labels.
`PoliciesViewModelTest` checks the three reads, that a save posts the full body
with the MAC normalized and the IP taken from the client, that the three probes
are re-read after writing, that a rejected write keeps the server message, the
DELETE query and the 401.

The mock (`tools/mock-api.py`) serves all five traffic endpoints, with the
timeline generated in 5 minute buckets around the current time, and the three
policy endpoints with an in-memory state so a write shows up on the next read.
