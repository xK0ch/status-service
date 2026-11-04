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
| Project Zomboid | Steam A2S_INFO (UDP, port 16261) | online/offline, players, map, latency |
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
| GET    | `/swagger-ui/index.html` | Swagger UI for the API.                         |
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
| `STATUS_<GAME>_EXPECTED_ONLINE` | per game           | Whether the server is meant to be running (see below). |
| `STATUS_TEAMSPEAK_QUERY_APIKEY` | *(empty)*          | TeamSpeak WebQuery api key (from a Jenkins credential). |

Minecraft, Palworld, Enshrouded and Project Zomboid publish their ports on the Docker host, so in
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

## Expected-online

Each server has an `expected-online` flag (default `true`). It distinguishes a
real outage from a server that is intentionally off:

- `expected-online: true` and offline is treated as **down** (a problem): the UI
  shows it in red with the query error, and the browser tab favicon turns red.
- `expected-online: false` and offline is shown as **idle** (intentionally off):
  a calm "not running" note, no error, and no effect on the favicon.

TeamSpeak defaults to `true`; Minecraft, Palworld and Enshrouded default to
`false`. To bring one back into service, flip its flag, e.g.
`STATUS_MINECRAFT_EXPECTED_ONLINE=true`.

## TeamSpeak WebQuery

TeamSpeak 6 disables all query interfaces by default and no longer offers the
old raw ServerQuery port (10011). Every WebQuery request requires an
`x-api-key`; there is no unauthenticated login. Player counts are read like this:

- The `teamspeak-server` project enables the HTTP WebQuery
  (`TSSERVER_QUERY_HTTP_ENABLED=true`, port 10080) and the SSH query
  (`TSSERVER_QUERY_SSH_ENABLED=true`, port 10022), and sets a fixed `serveradmin`
  password (`TSSERVER_QUERY_ADMIN_PASSWORD`).
- Neither port is published to the host; the container joins `status-net` so
  this service can reach the WebQuery internally at `teamspeak-server:10080`.
- A persistent api key is created once over the SSH query (see below) and passed
  to this service as `STATUS_TEAMSPEAK_QUERY_APIKEY` (from the
  `TEAMSPEAK_WEBQUERY_APIKEY` Jenkins credential).

The `teamspeak_webquery` querier makes a single `GET /{sid}/serverinfo` request
with the `x-api-key` header. The reported player count is
`virtualserver_clientsonline` minus `virtualserver_queryclientsonline`, so query
connections (including this service's own) are not counted as voice users.

### Creating the api key (one-time)

TeamSpeak 6 can only mint api keys over an authenticated ServerQuery session, so
connect once via the SSH query and run `apikeyadd`. `serverinfo` needs the
`manage` scope:

```bash
docker run --rm -it --network status-net alpine sh -c \
  "apk add -q openssh-client && ssh -p 10022 -o StrictHostKeyChecking=no serveradmin@teamspeak-server"
```

At the ServerQuery prompt (log in with the serveradmin password):

```
use 1
apikeyadd scope=manage lifetime=0
quit
```

Store the returned `apikey` as the `TEAMSPEAK_WEBQUERY_APIKEY` Jenkins
credential. Verify it against the WebQuery from a throwaway container:

```bash
docker run --rm --network status-net curlimages/curl -s \
  -H 'x-api-key: THE_APIKEY' \
  'http://teamspeak-server:10080/1/serverinfo'
```
