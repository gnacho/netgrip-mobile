# Notifications and diagnostics

Closes #23: the router can send alerts through Telegram or ntfy, and the app can
ask it for a self test and one-shot network probes. Everything is executed by
the router; the app only sends the settings and prints what comes back.

## Notifications

| Setting | Read | Write | Test |
|---|---|---|---|
| Telegram | `GET /api/telegram` | `POST /api/telegram` | `POST /api/telegram/test` |
| ntfy | `GET /api/ntfy` | `POST /api/ntfy` | `POST /api/ntfy/test` |

- **Secrets are one-way**: the Telegram token arrives masked (`123456...WXYZ`)
  and the ntfy token is only reported as `tokenSet`. An empty field keeps the
  stored value, so changing only the chat or the topic does not force the user
  to paste the token again.
- **Telegram validates on save**: the server checks the bot and the chat against
  Telegram and returns the bot and chat names, which the app shows. A wrong chat
  leaves its message in the card, not a generic error.
- **The test button reports the router's answer**. It is only enabled when the
  channel is enabled and configured; the app never claims a message was
  delivered on its own.

## Diagnostics

| Action | Endpoint |
|---|---|
| Self test (gateway, WAN, DNS, NTP + available tools) | `GET /api/diagnostics/selftest` |
| Ping, traceroute, DNS, TCP port | `POST /api/diagnostics` |

- The self test shows each check with its verdict (correct, not proven, fails)
  and the router's own reason, plus which tools exist on the device.
- One-shot probes are chosen with a segmented control. Ping sends four
  packets; DNS takes an optional resolver; the TCP test takes host and port.
- The result is printed as the router measured it (samples, min/average/max,
  hops, answers) in a selectable monospace block, so it can be copied. The app
  does not summarise or reinterpret it.
- Probes use a long timeout (ping up to 15 s, traceroute up to 30 s) so the
  request is not cut while the router is still measuring.

## Gates

```
./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug
```

Host tests cover the Telegram and ntfy bodies (including an omitted token when
the field is empty), their test buttons, a rejected Telegram chat, the self test
with its reasons and tools, and a ping rendered as text.

## Limitations

- Notification history, retries and delivery receipts are not exposed by the
  panel, so the app cannot show whether an alert arrived.
- A probe can fail because the tool is missing on the router; the answer says
  which one, and the app prints it instead of a generic failure.
