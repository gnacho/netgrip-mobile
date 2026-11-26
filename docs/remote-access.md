# Remote access

The app can now explain and, where the router allows it, set up how to reach the
panel from outside the home. Closes #22 and applies the transport decision from
#27.

## What it shows

| Transport | Read | Write |
|---|---|---|
| Tailscale | `GET /api/tailscale` | `POST /api/tailscale` (`{"enabled":bool}`) |
| WireGuard | `GET /api/wireguard` | none |
| OpenVPN | `GET /api/openvpn` | none |
| DDNS | `GET /api/ddns` | `POST /api/ddns/force` |
| WAN exposure | `GET /api/remoteaccess` | `POST /api/remoteaccess` (one field at a time) |

Every read is independent: a router that lacks a package answers 502 for that
transport and the rest of the screen still works.

## The recommendation

Tailscale is presented first, as the recommended path: a private network
between the user's devices and the router, no port opened, nothing published.
WireGuard is described as the alternative for those who already run it and have
a fixed address or port forwarding. Exposing the panel or SSH to the internet is
labelled as the last resort.

When Tailscale is installed but waiting for authorisation, the card shows the
`auth_url` and opens it in the browser. No credentials or account data are
stored by the app.

## Writes

- **Tailscale**: start or stop the node. Confirmed with a re-read.
- **DDNS**: forces an update of every entry (`{"section":""}`, which the server
  reads as all). Uses a long timeout because the router talks to the provider.
  If the registered IP differs from the WAN IP, the card says so.
- **WAN exposure**: each toggle opens its own dialog that states what it
  exposes, and the app sends only that field. Turning something on is painted as
  a destructive action; nothing is enabled without that confirmation.

## Gates

```
./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug
```

Host tests cover loading the five transports with one of them missing, the
Tailscale body, one-field exposure changes, forcing DDNS, and a rolled-back
exposure.

## Limitations

- Creating WireGuard peers, OpenVPN clients or new DDNS entries stays in the web
  panel: this screen is for reaching the router, not for provisioning accounts.
- The app cannot verify from outside whether the port is actually reachable;
  it reports the router configuration only.
