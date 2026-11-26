# Traffic

Part of #25, readings only. The app shows what the router has already counted;
writes (quotas, `nftqos`, parental schedules) are the other half of the issue and
are not in this tab yet.

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
- **No install button**. Installing or starting `nlbwmon` is a write and belongs
  to the second half of the issue.

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

The mock (`tools/mock-api.py`) serves all five traffic endpoints, with the
timeline generated in 5 minute buckets around the current time.
