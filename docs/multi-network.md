# Networks and devices

The app keeps any number of networks, each with any number of routers, and switches between them without losing the sessions.

## Model

`SavedSites` (in `domain/sites/SavedSites.kt`) is the persisted state:

- `SavedNetwork(id, name, devices)`.
- `SavedDevice(id, url, name, model, role, lastSeenAt)`.
- `currentNetworkId` and `currentDeviceId`.

Device ids are derived from the URL (`SiteRules.deviceId`), so adding the same router twice updates it instead of duplicating it. Network ids come from the name plus a numeric suffix when it already exists. The whole list lives under `sites.v1` in the same encrypted storage the session uses. Session cookies are not inside it: each device keeps its cookie under `session.device.<deviceId>`, so switching devices restores its own session.

## Migration

On the first run after upgrading, `SiteStore.load()` reads the previous single `last.url` and converts it into one network called "Mi red" with one device. The old cookie (`session.default`) is moved to the device profile by `SessionManager.moveCookie`, and the previous key stops being used.

## Flows

- **Networks list** (home): every saved network with its device count and the current one marked. Create, rename and delete from here.
- **Devices list**: the routers of the selected network with their URL, role and connection state. Tap to connect, add a device by URL, import the fleet or remove a device.
- **Adopting from the Fleet tab**: a peer adopted in Flota is added to the network chosen in its dialog (the current one by default). It does not switch the current device, so the session stays where it was.
- **Device configuration**: the tabs of the selected device. System back returns to the device list, and again to the networks list.
- **Header**: two chips, the current network and the current device. Tapping one opens its sheet, as a shortcut to the same lists.
- **Networks sheet**: lists every network with its device count, marks the current one, and allows renaming, deleting and creating. Selecting an empty network closes the session and shows the login screen, because there is nothing to connect to.
- **Devices sheet**: lists the routers of the current network with their URL, role and connection state. Tapping a device restores its cookie silently. If there is no valid session it opens a credentials dialog, including the self-signed certificate confirmation when the fingerprint is new.
- **Add device**: URL, user and password in one dialog, without leaving the app. The password is never stored, only the resulting cookie. A device added from a network is stored in that network, not in whichever one happened to be connected.
- **Import fleet**: reads `GET /api/fleet` from the connected router and adds its nodes as devices of the current network. Only name and address are used; the fleet passwords are never read or stored.
- **Login screen**: networks and devices already saved appear under "Tus redes". Tapping one restores the session or opens the login prefilled with its URL.
- **Logout**: revokes and deletes only the current device cookie, keeps the device in the list and tries another saved device before asking for credentials again.

## Rules

- A device always belongs to the network that contains it. Connecting from the login screen selects that network, not the one that happened to be active.
- The first network is called "Mi red" and is not renamed automatically. The device chip shows the router hostname, so the two labels stay distinct.
- Deleting a network removes its devices from the app only. The routers are untouched.
- A device with a changed certificate is never contacted again automatically; the error is shown and the credential dialog asks for confirmation of the new fingerprint.

## Limits

- Switching devices reloads the app data for the selected router; the previous screen state is not kept in memory.
- The fleet import does not probe the nodes, so an unreachable or wrong address only fails when the device is selected.
- Discovery (mDNS and fleet beacons) still feeds the login screen. Saved networks do not update themselves when a device changes its IP.
