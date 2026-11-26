# Configuration snapshots

Closes #24: the router can store copies of its UCI configuration, and the app
can create them, see what restoring one would change, restore it or delete it.

## Contract

| Action | Endpoint | Response |
|---|---|---|
| List | `GET /api/config/snapshots` | `{"snapshots":[{"id","timestamp","configs"}]}` |
| Create | `POST /api/config/snapshot` | The new snapshot |
| Compare two | `GET /api/config/diff?from&to` | `{"diffs":[{"config","before","after"}]}` |
| Restore | `POST /api/config/rollback` `{"id"}` | `{"status":"applied"}` |
| Delete | `DELETE /api/config/snapshot?id` | `204` |

## Diff against what is live now

The panel compares two snapshots, so the app:

1. creates a temporary snapshot (`POST /api/config/snapshot`),
2. compares the chosen one with it (`from` = chosen, `to` = temporary),
3. deletes the temporary one (`DELETE`).

The temporary copy is deleted even when the comparison fails, so the user's
list does not fill with leftovers. Everything shown is measured by the router:
the sheet prints "now" (`after`) and "with the copy" (`before`) per file, in
selectable monospace text.

## Restore is destructive

- The dialog names the snapshot, how many files it restores, that later changes
  are lost and that the network is reloaded, so the connection may drop.
- The result is the router's answer; a failure such as "no configs restored"
  is shown verbatim.
- The list is re-read after restoring, deleting or creating.

## Gates

```
./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug
```

Host tests cover the temporary-snapshot diff (including deleting the temporary
copy when the comparison fails), restoring with its body, creating with its id
in the message, and a server error on restore.

## Limitations

- Exporting a snapshot (`GET /api/config/snapshot/export`, a gzipped tar) is not
  in the app: it returns a binary file and the shared module has no file or
  share layer. It stays in the web panel.
- The diff is presented as before/after blocks per file, not as a line-by-line
  red/green diff.
