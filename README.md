# status-service

Backend for [status.fynn-koch.de](https://status.fynn-koch.de). Polls the game
servers running in the homelab and exposes their live status (online/offline,
player counts, version, latency) as a small JSON API with a Server-Sent Events
stream.

Paired frontend: [status-ui](../status-ui).

## What it does

Every `status.refresh-interval` (default 30s) the service queries each
configured server in parallel (one virtual thread per query) and caches the
result. Clients read the cached snapshot, so the game servers are never hit more
often than the poll interval, no matter how many people watch the page.

### Query protocols

| Game       | Protocol                        | Reports                          |
|------------|---------------------------------|----------------------------------|
| Minecraft  | Server List Ping (TCP)          | online/offline, players, version, MOTD, latency |
| Palworld   | Steam A2S_INFO (UDP)            | online/offline, players, map, latency |
| Enshrouded | Steam A2S_INFO (UDP)            | online/offline, players, map, latency |
| TeamSpeak  | HTTP WebQuery (port 10080)      | online/offline, players, latency |

TeamSpeak is a special case: its WebQuery is an admin interface, so it is not
published to the host. Instead the TeamSpeak container joins the internal
`status-net` network and this service reaches it by container name. See
*TeamSpeak WebQuery* below.

## API

| Method | Path                  | Description                                        |
|--------|-----------------------|----------------------------------------------------|
| GET    | `/api/status`         | Current snapshot of every server as a JSON array.  |
| GET    | `/api/status/stream`  | SSE stream; pushes the snapshot on connect and after every poll (event name `status`). |
| GET    | `/actuator/health`    | Health check.                                      |

Example `ServerStatus`:

```json
{
  "id": "minecraft",
  "name": "Minecraft",
  "game": "MINECRAFT",
  "online": true,
  "players": 3,
  "maxPlayers": 10,
  "version": "1.21.4",
  "map": null,
  "motd": "MLTSF",
  "latencyMs": 24,
  "error": null,
  "lastChecked": "2026-08-11T10:15:30Z"
}
```

## Configuration

All settings live under `status.*` in
[`application.yml`](src/main/resources/application.yml) and can be overridden
with environment variables.

| Variable                      | Default              | Meaning                                   |
|-------------------------------|----------------------|-------------------------------------------|
| `STATUS_REFRESH_INTERVAL`     | `30s`                | Time between poll cycles.                 |
| `STATUS_TIMEOUT`              | `5s`                 | Socket timeout per query.                 |
| `STATUS_CORS_ALLOWED_ORIGINS` | *(empty)*            | Comma-separated origins; only for local dev. |
| `STATUS_<GAME>_HOST`          | per game             | Host to reach a given server on.          |
| `STATUS_<GAME>_PORT`          | per game             | Query port for a given server.            |
| `STATUS_TEAMSPEAK_QUERY_PASSWORD` | *(empty)*        | serveradmin WebQuery password (from a Jenkins credential). |

Minecraft, Palworld and Enshrouded publish their ports on the Docker host, so in
the container the service reaches them via `host.docker.internal` (mapped to the
host gateway in `docker-compose-status-service.yml`). TeamSpeak is reached over
the shared `status-net` network by container name (`teamspeak-server`).

## Local development

Requires JDK 25.

```bash
./gradlew bootRun
```

To let a locally running [status-ui](../status-ui) (Vite on port 5173) call the
API, allow its origin:

```bash
STATUS_CORS_ALLOWED_ORIGINS=http://localhost:5173 \
STATUS_TEAMSPEAK_HOST=localhost \
STATUS_MINECRAFT_HOST=localhost \
STATUS_PALWORLD_HOST=localhost \
STATUS_ENSHROUDED_HOST=localhost \
./gradlew bootRun
```

Run the tests:

```bash
./gradlew test
```

## Deployment

Jenkins builds the image and runs it via
`docker-compose-status-service.yml`. The container joins the `status-net`
network, where `status-ui` proxies `/api/` requests to it.

## Adding or changing a server

Add another entry under `status.servers` in `application.yml` (or override via
env vars) with an `id`, `name`, `game` (`minecraft`, `steam_a2s`,
`teamspeak_webquery`, `teamspeak` or `tcp`), `host` and `port`. Then add a
matching card key in the UI if needed.

## TeamSpeak WebQuery

TeamSpeak 6 disables all query interfaces by default and no longer offers the
old raw ServerQuery port (10011). Player counts are read through the HTTP
WebQuery API instead:

- The TeamSpeak container enables the HTTP WebQuery (`TSSERVER_QUERY_HTTP_ENABLED=true`,
  port 10080) and sets a fixed `serveradmin` password
  (`TSSERVER_QUERY_ADMIN_PASSWORD`), both configured in the `teamspeak-server`
  project.
- That port is **not** published to the host; the container joins `status-net`
  so this service can reach it internally at `teamspeak-server:10080`.
- The same password is passed to this service as `STATUS_TEAMSPEAK_QUERY_PASSWORD`
  (from the shared `TEAMSPEAK_QUERY_ADMIN_PASSWORD` Jenkins credential).

The `teamspeak_webquery` querier logs in as `serveradmin` to obtain a session
api key, then calls `serverinfo`. The reported player count is
`virtualserver_clientsonline` minus `virtualserver_queryclientsonline`, so query
connections (including this service's own) are not counted as voice users.

You can verify the WebQuery against a running server from a throwaway container
on the same network:

```bash
docker run --rm --network status-net curlimages/curl -s \
  "http://teamspeak-server:10080/login?client_login_name=serveradmin&client_login_password=YOUR_PASSWORD"
```

The response contains an `apikey`; use it to read `serverinfo`:

```bash
docker run --rm --network status-net curlimages/curl -s \
  -H "x-api-key: THE_APIKEY" \
  "http://teamspeak-server:10080/1/serverinfo"
```
