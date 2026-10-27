# Health Dashboard

Self-hosted Health Connect snapshot receiver and dashboard for a Linux machine on your LAN.

## Stack
- Docker Compose
- FastAPI backend
- PostgreSQL database
- React + Vite + TypeScript frontend
- Tailwind CSS

## Setup
```bash
cp .env.example .env
```

Edit `.env` and replace:
- `POSTGRES_PASSWORD`
- `INGEST_API_TOKEN`
- `ADMIN_TOKEN` to enable protected maintenance endpoints under `/api/admin`
- `APP_TIMEZONE` for per-user nutrition-day boundaries
- account authentication replaces the old `READ_TOKEN` dashboard access
- `VITE_API_BASE_URL` only if you want to override the browser-derived API URL

By default, leave `VITE_API_BASE_URL` empty. The browser will call `protocol + "//" + window.location.hostname + ":8015"`, so opening `http://127.0.0.1:3016` makes the dashboard call `http://127.0.0.1:8015`.

## Multi-user upgrade
Follow [docs/MULTI_USER_UPGRADE.md](docs/MULTI_USER_UPGRADE.md) before starting the new image against an existing database. The one-time bootstrap creates the sole administrator and preserves the existing Android ingest token.

## Run
```bash
docker compose up -d --build
docker compose ps
docker compose logs -f api
```

Open:
- API manual: docs/API_MANUAL.md
- Regular-user API guide: docs/USER_API.md
- Public API for external calls: https://health.example.com
- Frontend on this machine: http://localhost:3016
- Frontend from LAN: http://<linux-lan-ip>:3016, for example http://127.0.0.1:3016
- API docs: http://localhost:8015/docs
- Health check: http://localhost:8015/health


## Verify Latest Data
Check that the backend is healthy and returning the latest Android snapshot:

```bash
curl -sS http://localhost:8015/health
curl -sS http://localhost:8015/api/v1/devices \
  -b dashboard.cookies
curl -sS http://localhost:8015/api/v1/health/latest \
  -b dashboard.cookies
curl -sS 'http://localhost:8015/api/v1/health/recent?deviceId=uriel-phone&limit=5' \
  -b dashboard.cookies
```

From another LAN device, open the dashboard with the Linux machine IP, for example:

```text
http://127.0.0.1:3016
```

The browser derives the API URL from the dashboard hostname, so that LAN page calls `http://127.0.0.1:8015`.

## Ingest Test
```bash
curl -sS -X POST http://localhost:8015/api/v1/ingest/health-snapshot \
  -H "Authorization: Bearer ${INGEST_API_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "pixel-test",
    "deviceName": "Pixel Test",
    "capturedAt": "2026-05-19T01:15:00+03:00",
    "timezone": "Asia/Jerusalem",
    "metrics": {
      "stepsToday": 8421,
      "distanceKmToday": 6.2,
      "activeCaloriesKcalToday": 540,
      "totalCaloriesKcalToday": 2200,
      "latestHeartRateBpm": 78,
      "restingHeartRateBpm": 62,
      "sleepMinutesLastNight": 438,
      "latestWeightKg": 78.4
    }
  }'
```

## API Manual
See [docs/API_MANUAL.md](docs/API_MANUAL.md) for the maintained endpoint reference, request examples, auth notes, and smoke checks.

## API Examples
```bash
curl -sS "http://localhost:8015/api/v1/health/latest?deviceId=pixel-test" \
  -b dashboard.cookies
curl -sS "http://localhost:8015/api/v1/health/history?deviceId=pixel-test&metric=steps_today" \
  -b dashboard.cookies
curl -sS "http://localhost:8015/api/v1/devices" \
  -b dashboard.cookies
```

Unauthorized ingest check:
```bash
curl -i -X POST http://localhost:8015/api/v1/ingest/health-snapshot \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"pixel-test"}'
```

Expected result: `401`.



## Dashboard Read APIs
These endpoints require an authenticated browser session. Use `POST /api/v1/auth/login` and send the returned HTTP-only cookie. Snapshot detail includes raw health JSON, so keep an additional trusted access layer for public exposure when possible.

Recent snapshots for a device:
```bash
curl -sS 'http://localhost:8015/api/v1/health/recent?deviceId=uriel-phone&limit=5' \
  -b dashboard.cookies
```

Recent snapshots also accepts optional ISO datetime filters:
```text
from=2026-05-19T00:00:00Z
to=2026-05-20T00:00:00Z
```

Snapshot detail, including raw payload:
```bash
curl -sS http://localhost:8015/api/v1/health/snapshot/37 \
  -b dashboard.cookies
```

Warning: `/api/v1/health/snapshot/{id}` and the dashboard raw snapshot viewer expose stored raw health payload data. Keep this dashboard private/LAN-only and use authenticated user sessions plus a trusted access layer before public exposure.

## Food Diary / Meals
Manual meal logging is available for the Android app and dashboard. Android writes meals with the user's ingest token; the signed-in dashboard can create, read, and delete meals with the user's session. `ADMIN_TOKEN` is not used for normal nutrition APIs.

Create a meal:
```bash
curl -sS -X POST http://localhost:8015/api/v1/nutrition/meals \
  -H "Authorization: Bearer ${INGEST_API_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"clientMealId":"android-20260701-132000-abc12345","deviceId":"uriel-phone","eatenAt":"2026-07-01T13:20:00+03:00","mealType":"lunch","title":"Tuna pasta","caloriesKcal":720,"proteinG":42,"carbsG":85,"fatG":20,"notes":"Manual entry"}'
```

Read meals and daily summary:
```bash
curl -sS 'http://localhost:8015/api/v1/nutrition/meals?date=2026-07-01' \
  -b dashboard.cookies

curl -sS 'http://localhost:8015/api/v1/nutrition/summary/daily?date=2026-07-01' \
  -b dashboard.cookies
```

Current limitations: meal items are not modeled yet, and there is no external food database or barcode scanning.

## Remove Smoke-Test Data
Prefer the protected admin API so you can preview counts before deleting. Set `ADMIN_TOKEN`, restart the API, then run a dry run first:

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/test-data?deviceId=pixel-test&dryRun=true' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

If the counts look correct, run the same command with `dryRun=false`.

## Android Sender URL
Use your Linux machine LAN IP:
```text
POST http://<linux-lan-ip>:8015/api/v1/ingest/health-snapshot
Authorization: Bearer <INGEST_API_TOKEN>
```

## Admin Maintenance
The admin API is disabled unless `ADMIN_TOKEN` is set. Admin endpoints require `Authorization: Bearer <ADMIN_TOKEN>` and support dry-run cleanup previews before deletion.

See [docs/ADMIN_API.md](docs/ADMIN_API.md) for stats and cleanup commands, including deleting `pixel-test`, deleting snapshots by device, and deleting snapshots by date range.

## Backup And Restore
Postgres data is stored in the Docker Compose database volume. Create timestamped SQL backups with:

```bash
./scripts/backup-db.sh
```

Restore is destructive and requires explicit confirmation:

```bash
CONFIRM_RESTORE=RESTORE_HEALTH_DASHBOARD ./scripts/restore-db.sh backups/health_dashboard_YYYYMMDDTHHMMSSZ.sql
```

See [docs/BACKUP_RESTORE.md](docs/BACKUP_RESTORE.md) for verification and isolated restore testing.

## Security Notes
- The ingest endpoint requires `Authorization: Bearer <INGEST_API_TOKEN>`.
- The admin API requires `Authorization: Bearer <ADMIN_TOKEN>` and is disabled when `ADMIN_TOKEN` is empty.
- The dashboard and read APIs require a valid per-user HTTP-only session cookie.
- User sessions protect health-data reads and isolate every account's records.
- Keep ports `3016` and `8015` limited to trusted networks. Do not expose the dashboard publicly without authentication or Cloudflare Access.
- CORS defaults to the local frontend origins and can be changed with `CORS_ALLOWED_ORIGINS`.
- Tokens are read from `.env`; do not commit real tokens. The frontend does not need or receive the ingest token.
- Good exposure options are VPN-only access, Cloudflare Access, reverse proxy basic auth, or another trusted access layer in addition to account authentication.

## Cloudflare Tunnel Later
Local development does not require Cloudflare Tunnel.

Recommended public URLs:
- `https://health.example.com` for the dashboard
- `https://health-api.example.com` for the API

Keep the API protected with bearer token even when tunneled.

Optional Compose profile:
```bash
docker compose --profile tunnel up -d
```

Set `CLOUDFLARED_TOKEN` in `.env` first. Do not commit the real tunnel token.
