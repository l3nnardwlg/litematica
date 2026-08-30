# Litematica Shared Relay

This is the server-side relay for shared schematic placements.

It uses Minecraft's existing plugin-message connection, so there is no extra HTTP/WebSocket service and no additional open port.

## Build

```bash
cd server-plugin
mvn clean package
```

The plugin jar is written to `server-plugin/target/litematica-shared-relay-1.0.0.jar`.

## Install

1. Copy the jar into the Paper/Spigot server's `plugins/` directory.
2. Restart the Minecraft server.
3. Install the matching Litematica fork build on both clients.
4. Load the same schematic on both clients.
5. Give the corresponding placement the same name on both clients, prefixed with `[shared] `, for example:

```text
[shared] main-base
```

Position, rotation, mirror and enabled state are then relayed over the normal Minecraft connection.

## Protocol

Legacy 1.12.2 plugin channel: `LitematicaShared`

The relay keeps the latest state for every shared placement ID in memory and sends the current snapshot to newly connected clients. Revisions are assigned by the server so the last server-accepted update wins consistently on all clients.
