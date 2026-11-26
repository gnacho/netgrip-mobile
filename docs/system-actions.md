# Router reboot and password

First half of #21: the two system actions that cut the connection. Firmware and
the NetGrip self update stay open.

## Scope

| Action | Endpoint | What the user sees |
|---|---|---|
| Reboot the router | `POST /api/reboot` | Confirmation, then a "rebooting" state until `/api/me` answers again |
| Change the admin password | `POST /api/password` | Confirmation with current, new and repeated password; afterwards the login with the new one |

## Contract

Checked against `internal/server/server.go` and `internal/modules`.

| Endpoint | Request body | Response |
|---|---|---|
| `POST /api/reboot` | `{}` | `{"started":true}` before the router goes down |
| `POST /api/password` | `{"current","next"}` | `204` and every panel session dies; `400 {"error"}` when the current password is wrong |

## Rules

- **Both ask first**. The reboot dialog says the router will stop answering for
  one or two minutes and that the internet goes down with it. The password
  dialog says every panel session is closed, including this one.
- **A reboot is not success until the router answers**. After the `200` the app
  polls `/api/me` every 3 seconds for up to 3 minutes:
  - `204`: the router is back, the settings are reloaded and a message says so.
  - `401`: the session did not survive, so the app returns to the login.
  - Nothing: the app says the router has not answered yet instead of claiming a
    reboot that may not have finished.
- **Password rules checked locally**: the new one needs at least 8 characters
  and both fields must match. The current password is never stored, and a wrong
  one keeps the dialog open with the server message inside it.
- **After a password change** the router invalidates every session, so the app
  forgets the stored cookie, shows the login for that router and explains why.
  It does not keep a dead cookie for the next launch.
- Errors are shown where they can be fixed (card or dialog), not lost in a
  snackbar.

## Gates

```
./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug
```

Host tests cover the reboot body, the polling until the router returns, the
timeout message, the lost session, and the password success and rejection
paths.

## Limitations

- The app cannot know whether the reboot was requested from another panel; it
  only reports what it sees after its own request.
- A router that comes back with a different certificate cannot be detected from
  here: the first read after the reboot will report the certificate change.
