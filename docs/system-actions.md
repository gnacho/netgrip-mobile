# Router reboot and password

This covers #21: the actions that cut the connection (reboot, password), the
two updates (router firmware, NetGrip panel) and the panel update schedule.

## Scope

| Action | Endpoint | What the user sees |
|---|---|---|
| Reboot the router | `POST /api/reboot` | Confirmation, then a "rebooting" state until `/api/me` answers again |
| Change the admin password | `POST /api/password` | Confirmation with current, new and repeated password; afterwards the login with the new one |
| Install router firmware | `GET|POST /api/update` | Version from and to, owut warnings, and an explicit acknowledgement before starting |
| Update the NetGrip panel | `POST /api/selfupdate` | Confirmation naming the version; only the panel restarts |
| Schedule the panel update | `GET|PUT /api/selfupdate/schedule` | Daily or weekly, inside a two hour window |

## Contract

Checked against `internal/server/server.go` and `internal/modules`.

| Endpoint | Request body | Response |
|---|---|---|
| `POST /api/reboot` | `{}` | `{"started":true}` before the router goes down |
| `POST /api/password` | `{"current","next"}` | `204` and every panel session dies; `400 {"error"}` when the current password is wrong |
| `GET /api/update` | none | `owut check -v` parsed: versions, warnings, `safe_to_proceed`, missing packages |
| `POST /api/update` | `{"confirm":true}` | `{"started":true,"reboot_pending":true}` before the router downloads and flashes |
| `POST /api/selfupdate` | `{"confirm":true}` | The panel starts downloading and restarting |
| `GET /api/selfupdate/schedule` | none | Config, last run and current status |
| `PUT /api/selfupdate/schedule` | `{"enabled","intervalHours","windowStart","windowEnd"}` | Only the fields that change; the rest is kept |

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

## Firmware and panel updates

- **Firmware** is offered only when the router reports a newer version, a
  different version (`same_version` false) and `owut` present. If owut says it
  is not safe (`safe_to_proceed` and `safe_with_reinstall` false) the button is
  disabled and the missing packages are listed.
- Installing needs a second step: a checkbox acknowledging that the router will
  restart and may take several minutes, plus the warning that a firmware that
  does not boot may need physical access to recover. The version being
  installed is always named.
- After accepting, the app waits up to 12 minutes (every 5 s) for `/api/me`.
  A session that does not survive sends the user to the login; silence is
  reported as "still not answering", never as success.
- **NetGrip panel** has its own button, named with the target version, and says
  that only the panel restarts. The wait is shorter (every 2 s) because the
  process comes back in seconds.
- Warnings from owut are listed verbatim; the app does not summarise them into
  "safe".

## Panel update schedule

- Daily (24 h) or weekly (168 h), inside a two hour window. The window start
  moves in one hour steps and the end follows two hours later, wrapping past
  midnight.
- Every control saves immediately through a partial `PUT` and the result is
  confirmed with a `GET`. The last result and its time are shown when the
  router has run a check.
- The schedule only updates the NetGrip panel, never the router firmware: a
  firmware upgrade reboots the router and is not something to run unattended
  from this app.

## Gates

```
./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug
```

Host tests cover the schedule partial body and its re-read, the firmware and panel update bodies, their waits, the reboot body, the polling until the router returns, the
timeout message, the lost session, and the password success and rejection
paths.

## Limitations

- The app cannot know whether the reboot was requested from another panel; it
  only reports what it sees after its own request.
- A router that comes back with a different certificate cannot be detected from
  here: the first read after the reboot will report the certificate change.
