# Shared placement relay

Small in-memory HTTP relay used by the private shared-schematic feature.

## Run

```bash
docker build -t litematica-shared ./shared-server
docker run --rm -p 8787:8787 -e SHARED_TOKEN=change-me litematica-shared
```

Configure both Minecraft clients with the same relay URL and token:

```text
-Dlitematica.shared.url=http://YOUR_SERVER:8787
-Dlitematica.shared.token=change-me
```

Then give the corresponding placement the same name on both clients, prefixed with `[shared] `, for example:

```text
[shared] main-base
```

The clients derive the same stable shared ID from `main-base` and synchronize placement origin, rotation, mirror and enabled state. Local changes are detected twice per second; the HTTP relay is polled every 500 ms.

The relay stores only the latest placement state in memory. Restarting the relay clears its state, but the next connected client republishes its current local state automatically.

## Endpoints

- `GET /health`
- `GET /api/states`
- `POST /api/states`

If `SHARED_TOKEN` is set, all requests require `Authorization: Bearer <token>`.
