# ClaimOps over iroh

**English** · [ภาษาไทย](README.th.md)

A complete app on iroh-bridge: an APAC motor and property claims platform (Angular 22 frontend, Spring Boot backend,
PostgreSQL, Kafka) whose backend **publishes no port**. The browser reaches it only through an
[iroh-gateway](../../README.md#backend-iroh-gateway), so the backend could just as well sit behind NAT on another
machine. Claimants submit and track claims; staff pick them up, request information, and settle or reject them.

The app comes from [claimops-platform](https://github.com/konrawitAEK/claimops-platform). Its design notes are in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). The [changes for iroh](#what-changed-for-iroh) are small: one new file
in the frontend, and no change to the backend code.

```
 browser (any host)                                         docker compose (could be behind NAT)
┌──────────────────────┐            ┌───────┐              ┌──────────────┐  HTTP   ┌─────────┐   ┌──────────┐
│ Angular + iroh-fetch │ ─────────▶ │ relay │ ◀─────────── │ iroh-gateway │ ──────▶ │ backend │ ─▶│ Postgres │
│ HttpClient → /api/** │  WebSocket └───────┘   outbound   │  /api only   │  :8080  │ Spring  │   │ Kafka    │
└──────────────────────┘                                   └──────────────┘         └─────────┘   └──────────┘
        ▲ static files (index.html, JS, iroh.json, .wasm) from the frontend container on :4200
```

## Run

Requires Docker. From this folder:

```bash
docker compose up --build -d
```

Open <http://localhost:4200>. Use the top bar to switch between Claimant and Staff view, user and market.

| Service | Address |
|---|---|
| App (Angular) | <http://localhost:4200> |
| Backend | not published; reachable only through the gateway (see [Call the API from your machine](#call-the-api-from-your-machine)) |
| Gateway | `docker compose logs gateway` shows its `IROH_ENDPOINT_ID=...` |
| PostgreSQL | `localhost:5432`, database, user and password all `claimops` |
| Kafka | `localhost:9092` from the host |

On start, the frontend container asks the gateway for its endpoint id and relay (the gateway's admin `/status`,
which is only reachable on the compose network) and writes them to `/iroh.json`. The page reads that file, connects
to the gateway, and only then starts Angular, so the first API call already goes over iroh. Connecting takes a
second or two; the page shows "Connecting to ClaimOps…" meanwhile.

To check that API calls really go over iroh: the browser's network tab shows no requests to `/api`, and the gateway
counts them:

```bash
docker compose exec frontend wget -qO- http://gateway:9090/metrics | grep requests_total
```

| Task | Command |
|---|---|
| Status | `docker compose ps` |
| Follow backend logs | `docker compose logs -f backend` |
| Rebuild one service after a code change | `docker compose up -d --build backend` (or `frontend`) |
| Stop, keeping data | `docker compose down` |
| Full reset (also a new gateway endpoint id) | `docker compose down -v && docker compose up --build -d` |

The gateway's secret key is in the `gateway-data` volume. Keep the volume to keep the endpoint id; `down -v` deletes
it, and the gateway then gets a new id (the frontend picks it up on its next start).

### Reset only the database

Claim numbers restart at 000001. Kafka keeps its old events, but the consumer has already passed them, so they are
not replayed.

```bash
docker compose exec postgres psql -U claimops -c "TRUNCATE claims, info_requests, liability_exposures, claim_audit_log, processed_events; ALTER SEQUENCE claim_number_seq RESTART;"
```

## Call the API from your machine

The backend has no published port, so use [`iroh-proxy`](../../README.md#local-port-iroh-proxy), which exposes the
gateway on a local port for curl, Postman or any HTTP client:

```bash
npx -y -p iroh-fetch iroh-proxy <endpoint id> --port 8787

curl -H 'X-User-Role: STAFF' -H 'X-User-Name: bob' http://127.0.0.1:8787/api/staff/dashboard
```

The gateway only lets `/api` through (`IROH_ALLOW_PATHS` in [docker-compose.yml](docker-compose.yml)), so Swagger UI
answers 403. To use it, add `/swagger-ui,/v3/api-docs` to `IROH_ALLOW_PATHS` and open
<http://127.0.0.1:8787/swagger-ui.html>, or publish the backend's port again while developing.

## Serve the frontend somewhere else

The frontend container only serves static files and needs no route to the backend, so it can run on another host
than the backend, or the build (`npm run build`, output in `dist/browser`) can go to any static host. It needs
`/iroh.json` next to `index.html`:

```json
{ "endpointId": "<the gateway's endpoint id>", "relayUrl": "https://euc1-1.relay.n0.iroh.link" }
```

| Field | |
|---|---|
| `endpointId` | required: the gateway's endpoint id |
| `relayUrl` | optional: the relay the gateway is on, a hint that makes the first connection faster |
| `clientRelayUrl` | optional: your own relay for the browser, instead of n0's public relays |
| `relayToken` | optional: token for that relay. It is public in this file; see [Relay token from your backend](../../README.md#relay-token-from-your-backend) for a better way |

With the Docker image, set these as environment variables instead (`IROH_ENDPOINT_ID`, `IROH_GATEWAY_RELAY_URL`,
`IROH_RELAY_URL`, `IROH_RELAY_TOKEN`); see
[docker-entrypoint.d/40-iroh-config.sh](frontend/docker-entrypoint.d/40-iroh-config.sh).

On the backend machine, run everything in [docker-compose.yml](docker-compose.yml) except `frontend`. For production,
use [your own relay](../../README.md#run-your-own-relay): n0's public relays have no SLA.

## Develop

Start the backend side in Docker (no frontend container):

```bash
docker compose up -d postgres kafka backend gateway
```

**Frontend dev server** on <http://localhost:4200>. Needs Node 22.22.3 or later. If the frontend container is running,
stop it first with `docker compose stop frontend`.

```bash
cd frontend && npm install && npm start
```

Without `public/iroh.json`, API calls go straight to `/api`, which `ng serve` proxies to `localhost:8080`
([proxy.conf.json](frontend/proxy.conf.json)). That only works with the backend's port published or the backend run
from the host (`cd backend && ./mvnw spring-boot:run`, JDK 21). To go over iroh instead, create
`frontend/public/iroh.json` as [above](#serve-the-frontend-somewhere-else) (it is git-ignored).

## Test

```bash
cd backend && ./mvnw test           # JDK 21; no Docker, database or Kafka needed
cd frontend && npm run build        # type check and production build
```

See the original project's README for what the backend tests cover.

## Security

- **The backend trusts `X-User-Role` / `X-User-Name` as sent.** That was a deliberate shortcut in the original app,
  where the API was only on localhost. Over iroh, anyone who knows the gateway's endpoint id (it is public, in
  `/iroh.json`) can call the API as any staff user, exactly as with a public URL. Before real use, replace
  [HeaderAuthInterceptor](backend/src/main/java/com/chubb/claimops/common/auth/HeaderAuthInterceptor.java) with real
  authentication (e.g. OAuth2/JWT, which iroh-fetch passes through unchanged). The backend also receives
  `X-Iroh-Remote-Id`, the caller's verified iroh endpoint id, for rate limits or audit logs.
- The gateway only forwards `/api` (`IROH_ALLOW_PATHS`); Swagger UI and actuator stay internal.
- Keep the `gateway-data` volume private: whoever has the key can impersonate the backend.

## What changed for iroh

Compared with [claimops-platform](https://github.com/konrawitAEK/claimops-platform):

| File | Change |
|---|---|
| [frontend/src/app/core/iroh.ts](frontend/src/app/core/iroh.ts) | **New.** Reads `/iroh.json`, connects with `createIrohFetch`, and `install()`s it for `/api/**` on the page's origin. HttpClient already uses `fetch` (`withFetch()`), so services and pages are unchanged. |
| [frontend/src/main.ts](frontend/src/main.ts) | Connects before bootstrapping Angular; shows an error in the page if the gateway can't be reached. |
| [frontend/angular.json](frontend/angular.json) | Copies iroh-fetch's `.wasm` to `/wasm/`. |
| [frontend/package.json](frontend/package.json) | Adds `iroh-fetch`. |
| [frontend/nginx.conf](frontend/nginx.conf) | No more `/api` proxy to the backend; serves `/iroh.json` (no cache) and the `.wasm`. |
| [frontend/Dockerfile](frontend/Dockerfile), [40-iroh-config.sh](frontend/docker-entrypoint.d/40-iroh-config.sh) | Writes `/iroh.json` at container start, from the environment or the gateway's `/status`. |
| [frontend/src/index.html](frontend/src/index.html), [styles.scss](frontend/src/styles.scss) | "Connecting…" text and error style while connecting. |
| [docker-compose.yml](docker-compose.yml) | Adds the `gateway` service; the backend no longer publishes port 8080; the frontend depends on the gateway. |
| backend | Unchanged. |
