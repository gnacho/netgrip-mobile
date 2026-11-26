# Storage

Closes #47: the router can mount a detected USB disk and the app can do the
same from a phone.

## Readings

`GET /api/storage` returns `StorageProbe`:

- `applicable`, `supported` (the board has USB buses, even with nothing plugged),
  `usb_present` and `usb_storage_loaded`.
- `devices[]`: `name`, `path`, `fs_type`, `size_bytes`, `used_bytes`,
  `free_bytes`, `mount_point` (absent when not mounted) and `managed`.
- `services[]`: read here and not shown; sharing services and their folders stay
  in the panel for now.

The three flags answer different questions and the app keeps them apart: no USB
ports, nothing plugged in, and a kernel without `usb-storage` are three different
sentences. The last one cannot be fixed from the app and says so.

## Mounting

`POST /api/storage/mount` takes `device`, `target` and `read_only`.

- The dialog suggests `/mnt/<device>` and validates the same pattern the router
  uses (`internal/modules/storage_mount.go`: `^/mnt/[A-Za-z0-9._-]+$`), so a bad
  mount point is explained before sending anything.
- The read-only switch sends `read_only: true`, which mounts with `MS_RDONLY`.
- The router leaves an fstab entry and, if the mount fails, removes it again. The
  dialog says that instead of promising a clean failure.
- The response is the re-read probe: the app paints that, not what it expected.

## Unmounting

`POST /api/storage/unmount` takes `target`, and the router only accepts mounts it
created (`managed: true`). A disk mounted by hand is shown as
"Montado en /mnt/x · a mano" with no button: the app does not offer an action the
panel would reject.

## What the app does not do

- **No service switches**. Starting Samba or minidlna, installing them and
  editing their shared folders (`POST /api/storage`, `/install`, `/paths`) stays
  in the panel.
- **No formatting, no fstab editing** by hand. Mount and unmount only.
- **No assumed success**. The device card shows the state that came back in the
  probe; a rejected mount keeps the old state and the router message.

## Tests

`StorageRulesTest` covers the suggested target, the validation (out of `/mnt`,
nested paths, spaces), the managed versus manual distinction and the usage
percentage. `StorageDtoParsingTest` parses the probe with and without
`mount_point`, the services and the mount body. `StorageViewModelTest` checks the
read, that a mount posts the body and paints the probe the router returned, that
a rejected mount keeps the previous state with the message, the unmount target,
the 401 and the `ensure` guard.
