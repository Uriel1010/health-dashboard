# API Manual

This is the maintained API reference for the private Health Dashboard MVP.

Base URL for API calls from outside the LAN:

```text
https://health.example.com
```

Base URL on the host machine:

```text
http://localhost:8015
```

Base URL from another LAN device:

```text
http://<linux-lan-ip>:8015
```

The FastAPI interactive docs are available at:

```text
http://localhost:8015/docs
```

## Authentication

### Browser sessions

Dashboard and read APIs use a per-user HTTP-only session cookie. Log in with JSON credentials:

```bash
curl -sS -c dashboard.cookies -X POST http://localhost:8015/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"your-password"}'
```

Use `-b dashboard.cookies` for authenticated read requests. There is no public registration. Only the sole administrator can create regular users.

### Ingest API

Snapshot and meal writes use each user's active ingest bearer token. Users generate or rotate it from **Account & ingestion**. The pre-upgrade `INGEST_API_TOKEN` is assigned to the bootstrap administrator.

### Infrastructure maintenance API

`/api/admin/*` remains separate and requires the optional environment `ADMIN_TOKEN`.

See [MULTI_USER_UPGRADE.md](MULTI_USER_UPGRADE.md) for migration, bootstrap, and rollback instructions.

## Service Health

### `GET /health`

Checks API and database availability.

```bash
curl -sS http://localhost:8015/health
```

Response:

```json
{
  "status": "ok",
  "database": "ok",
  "timestamp": "2026-06-28T19:01:19.146154Z"
}
```

## Ingest

### `POST /api/v1/ingest/health-snapshot`

Stores a health snapshot for a device. Requires `INGEST_API_TOKEN`.

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
    },
    "permissions": {
      "steps": "granted"
    },
    "errors": []
  }'
```

Success response:

```json
{
  "ok": true,
  "snapshotId": 123,
  "receivedAt": "2026-06-28T19:01:19.146154Z"
}
```

Common errors:

- `401 Missing bearer token`
- `401 Invalid bearer token`
- `422 deviceId is required`
- `422 capturedAt must be an ISO datetime`

## Devices

### `GET /api/v1/devices`

Lists known devices, newest seen first.

```bash
curl -sS http://localhost:8015/api/v1/devices \
  -b dashboard.cookies
```

Response:

```json
[
  {
    "deviceId": "uriel-phone",
    "deviceName": "SM-S916B",
    "firstSeenAt": "2026-05-18T22:21:26.100898Z",
    "lastSeenAt": "2026-06-28T18:53:29.140746Z"
  }
]
```

## Health Snapshots

### `GET /api/v1/health/latest`

Gets the latest snapshot. If `deviceId` is omitted, the most recently seen device is used.

```bash
curl -sS 'http://localhost:8015/api/v1/health/latest?deviceId=uriel-phone' \
  -b dashboard.cookies
```

### `GET /api/v1/health/recent`

Gets recent snapshots for a device.

Query parameters:

- `deviceId`, required
- `limit`, optional, default `20`, max `100`
- `from`, optional ISO datetime
- `to`, optional ISO datetime

```bash
curl -sS 'http://localhost:8015/api/v1/health/recent?deviceId=uriel-phone&limit=5' \
  -b dashboard.cookies
```

```bash
curl -sS 'http://localhost:8015/api/v1/health/recent?deviceId=uriel-phone&from=2026-05-19T00:00:00Z&to=2026-05-20T00:00:00Z' \
  -b dashboard.cookies
```

### `GET /api/v1/health/history`

Gets history points for one metric.

Query parameters:

- `deviceId`, required
- `metric`, required
- `from`, optional ISO datetime
- `to`, optional ISO datetime

Supported metrics:

- `steps_today`
- `distance_km_today`
- `active_calories_kcal_today`
- `total_calories_kcal_today`
- `latest_heart_rate_bpm`
- `resting_heart_rate_bpm`
- `sleep_minutes_last_night`
- `latest_weight_kg`

```bash
curl -sS 'http://localhost:8015/api/v1/health/history?deviceId=uriel-phone&metric=steps_today' \
  -b dashboard.cookies
```

### `GET /api/v1/health/snapshot/{snapshotId}`

Gets one snapshot with its raw payload. This may expose sensitive health data.

```bash
curl -sS http://localhost:8015/api/v1/health/snapshot/37 \
  -b dashboard.cookies
```

## Nutrition / Meals
Meal writes require the user's ingest token. Dashboard reads require the user's session. `ADMIN_TOKEN` is not used for nutrition APIs.

### `POST /api/v1/nutrition/meals`
Creates a meal. If `clientMealId` and `deviceId` match an existing meal, the server updates and returns that meal so Android retries do not create duplicates.

```bash
curl -sS -X POST http://localhost:8015/api/v1/nutrition/meals \
  -H "Authorization: Bearer ${INGEST_API_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"clientMealId":"android-20260701-132000-abc12345","deviceId":"uriel-phone","eatenAt":"2026-07-01T13:20:00+03:00","mealType":"lunch","title":"Tuna pasta","caloriesKcal":720,"proteinG":42,"carbsG":85,"fatG":20,"notes":"Manual entry"}'
```

Missing or wrong ingest token returns `401`.

### `GET /api/v1/nutrition/meals`
Reads meals by local date or date range.

```bash
curl -sS 'http://localhost:8015/api/v1/nutrition/meals?date=2026-07-01' \
  -b dashboard.cookies

curl -sS 'http://localhost:8015/api/v1/nutrition/meals?from=2026-07-01&to=2026-07-07' \
  -b dashboard.cookies
```

Missing or expired user sessions return `401`; cross-account resources return `404`.

### `GET /api/v1/nutrition/summary/daily`

```bash
curl -sS 'http://localhost:8015/api/v1/nutrition/summary/daily?date=2026-07-01' \
  -b dashboard.cookies
```

Example response includes `caloriesKcal`, `proteinG`, `carbsG`, `fatG`, optional fiber/sugar/sodium totals, `mealCount`, and `byMealType`.

### `GET /api/v1/nutrition/summary/range`

```bash
curl -sS 'http://localhost:8015/api/v1/nutrition/summary/range?from=2026-07-01&to=2026-07-07' \
  -b dashboard.cookies
```

Current limitations: meal-level totals are stored. Individual meal items, food database lookup, barcode scanning, AI recognition, and dashboard meal creation are not part of this MVP.

## Admin API

See [ADMIN_API.md](ADMIN_API.md) for the full maintenance API. Short examples are included here for daily use.

### `GET /api/admin/stats`

```bash
curl -sS http://localhost:8015/api/admin/stats \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

Returns device count, snapshot count, oldest/newest snapshot timestamps, and per-device snapshot counts.

### `DELETE /api/admin/test-data`

Dry-run delete of test data, usually `pixel-test`:

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/test-data?deviceId=pixel-test&dryRun=true' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

Actual delete:

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/test-data?deviceId=pixel-test&dryRun=false' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

### `DELETE /api/admin/devices/{deviceId}/snapshots`

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/devices/uriel-phone/snapshots?dryRun=true' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

### `DELETE /api/admin/snapshots`

Delete snapshots by date range:

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/snapshots?from=2026-05-01&to=2026-05-31&dryRun=true' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

Delete snapshots by device and date range:

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/snapshots?deviceId=uriel-phone&from=2026-05-01&to=2026-05-31&dryRun=true' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

Full snapshot cleanup requires an explicit confirmation parameter:

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/snapshots?dryRun=true&confirm=DELETE_ALL_SNAPSHOTS' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

## Backup And Restore

Back up the Postgres database:

```bash
./scripts/backup-db.sh
```

Restore a backup, destructively:

```bash
CONFIRM_RESTORE=RESTORE_HEALTH_DASHBOARD ./scripts/restore-db.sh backups/health_dashboard_YYYYMMDDTHHMMSSZ.sql
```

See [BACKUP_RESTORE.md](BACKUP_RESTORE.md) for the complete flow.

## Manual Smoke Checks

```bash
curl -sS http://localhost:8015/health
curl -sS http://localhost:8015/api/v1/devices \
  -b dashboard.cookies
curl -sS 'http://localhost:8015/api/v1/health/latest?deviceId=uriel-phone' \
  -b dashboard.cookies
```

Admin disabled check when `ADMIN_TOKEN` is not set:

```bash
curl -i http://localhost:8015/api/admin/stats
```

Expected: `503 Admin API is disabled`.
