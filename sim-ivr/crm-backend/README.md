# SIM IVR CRM Backend

A production-grade backend for the SIM IVR Android app: receives every call result and
callback/opt-out event the app pushes, and serves campaign contact lists back to it. Node.js +
TypeScript + Express + PostgreSQL (Prisma). Includes a small admin dashboard for managing
campaigns and working leads.

This is the "real" backend — `../crm-stub` (also in this repo) is a tiny reference logger kept
around for quick contract testing; this service is what you'd actually run.

## Build status — verified working

Unlike the Android app in this repo (blocked by this sandbox's network policy on the Android
SDK), **this backend has no such blocker** — `npm`/Node packages are unaffected — so it was built,
tested, and run for real:

- `npm run build` compiles clean (TypeScript → `dist/`).
- `npm test` — **29/29 tests pass** (vitest + supertest against a real local Postgres, not mocked):
  auth (missing/wrong API key and admin token both rejected), malformed-body validation, event
  dedup on retry, distinct events for different `startedAt`, Lead auto-creation on
  `callback_request` (and not duplicated on retry, and not created for `call_result`), opt-out
  propagation from digit "9" (only when `campaignId` is set) to both the `OptOut` table and the
  matching `Contact`, `GET .../contacts` excluding opted-out numbers, CSV import skipping
  opted-out/invalid rows, campaign CRUD, and lead listing/filtering/updates.
- The compiled server was actually started against a real Postgres database and driven end to end
  with curl: created a campaign, imported a CSV, called `GET /api/ivr/campaigns/:id/contacts`,
  posted a `call_result` and a `callback_request` (confirmed the Lead appeared in `GET
  /api/leads`), then posted digit `9` on a campaign call and confirmed that number silently
  disappeared from the next `GET .../contacts` call. All of that is captured in the test suite too.
- The dashboard's static files were confirmed served and its login endpoint confirmed working.
- **Not verified in this sandbox**: `docker compose up` itself — this environment has the Docker
  CLI but no running daemon, so the Dockerfile/compose file follow standard, well-tested patterns
  but weren't executed here. Test it once you're on your actual server (see below).

## Data model

- **Campaign** — id, name, status, createdAt
- **Contact** — belongs to a campaign; name, phone, `vars` (jsonb, arbitrary CSV columns),
  status (PENDING/DIALED/OPTED_OUT)
- **CallEvent** — one row per event the app posts, deduped on `(number, campaignId, startedAt)`
  so the app's offline-outbox retries never create duplicates
- **Lead** — auto-created 1:1 from any CallEvent with `event == "callback_request"`
- **OptOut** — phone numbers that pressed 9 on a campaign call; filters both
  `GET /api/ivr/campaigns/:id/contacts` and CSV import

## API

### Contract endpoints (called by the SIM IVR Android app)

Both require the app's API key, sent as `Authorization: Bearer <key>` **or** `X-Api-Key: <key>`
(the app's Settings screen sends both).

**`POST /api/ivr/events`**
```json
{
  "event": "call_result",
  "direction": "outgoing",
  "number": "+919000000001",
  "sim": 0,
  "flow": "sample-outbound",
  "digits": ["1"],
  "durationMs": 15000,
  "startedAt": 1732600000000,
  "endedAt": 1732600015000,
  "campaignId": "uuid-or-null",
  "vars": { "name": "Asha" }
}
```
→ `200 { "ok": true, "callEventId": "...", "wasDuplicate": false, "leadCreated": false, "optedOut": false }`

**`GET /api/ivr/campaigns/:id/contacts`** → `200 [{ "name": "...", "phone": "...", "vars": {...} }, ...]`
(opted-out numbers are silently excluded)

### Admin endpoints (dashboard + you)

All require `Authorization: Bearer <ADMIN_TOKEN>` — a **separate** token from the app's API key.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/admin/login` | Validates a token (used by the dashboard's login form) |
| GET | `/api/campaigns` | List campaigns with contact/lead counts |
| POST | `/api/campaigns` | `{ "name": "..." }` → create a campaign |
| GET | `/api/campaigns/:id` | Campaign detail + contact status breakdown |
| POST | `/api/campaigns/:id/contacts/csv` | Multipart `file` field, CSV with `name,phone,...custom columns` header |
| GET | `/api/leads?status=NEW\|CONTACTED\|CLOSED` | List leads, optionally filtered |
| PATCH | `/api/leads/:id` | `{ "status": "CONTACTED" }` |
| GET | `/api/events?campaignId=&limit=` | Raw event feed, newest first |
| GET | `/health` | No auth — `{ ok, db }` |

### Dashboard

`GET /` (or any static path) serves a single-page dashboard (plain HTML/JS, no build step) at the
server root: log in with `ADMIN_TOKEN`, create campaigns, upload CSVs, browse/update leads, and
watch a live (5s-polled) event feed. The token is kept in the browser's `localStorage` for
convenience — treat this as an internal tool on a network you control, not a public login page.

## Local development

```bash
cd crm-backend
npm install
cp .env.example .env        # fill in DATABASE_URL / API_KEY / ADMIN_TOKEN
npx prisma migrate dev      # creates tables (and a shadow db — the DB user needs CREATEDB)
npm run dev                 # tsx watch, http://localhost:8080
```

Run the tests (needs a second Postgres database — see `.env.test`):
```bash
npm test
```

## Deploying on your server

**Option A — Docker (recommended):**
```bash
cd crm-backend
cp .env.example .env
# edit .env: set API_KEY, ADMIN_TOKEN, and POSTGRES_PASSWORD if you want a non-default one
API_KEY=... ADMIN_TOKEN=... docker compose up -d --build
```
This starts Postgres + the app, runs `prisma migrate deploy` automatically on container start, and
exposes the app on port 8080 (override with `HOST_PORT`). Put it behind a reverse proxy
(nginx/Caddy) for HTTPS — the Android app's Settings screen expects an `https://` base URL for
anything beyond same-LAN testing.

**Option B — bare Node + your own Postgres:**
```bash
cd crm-backend
npm ci
npm run build
DATABASE_URL=postgresql://user:pass@localhost:5432/crm_backend \
  API_KEY=... ADMIN_TOKEN=... PORT=8080 \
  npx prisma migrate deploy
DATABASE_URL=... API_KEY=... ADMIN_TOKEN=... PORT=8080 node dist/server.js
```
Keep it alive with `pm2` or a systemd unit, same as `crm-stub`.

Once it's up, point the SIM IVR app's **Settings → CRM base URL** at
`https://your-domain.example` and **CRM API key** at the value of `API_KEY` (not `ADMIN_TOKEN`).

## Security notes

- `API_KEY` (the app) and `ADMIN_TOKEN` (humans/dashboard) are deliberately separate — rotate
  either independently, and never put `ADMIN_TOKEN` in the Android app.
- Full phone numbers are only logged at `debug` level; `info`-level logs mask to the last 4 digits
  (`src/lib/logger.ts`).
- Rate limiting is applied separately to the public `/api/ivr/*` routes and the admin routes.
- All request bodies are validated with zod; malformed input gets a 400, never a crash.
- Postgres in `docker-compose.yml` is not exposed outside the compose network — only the app
  container talks to it.
