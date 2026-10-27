# Admin API

The admin API is for maintenance cleanup only. It is protected by `Authorization: Bearer <ADMIN_TOKEN>` and is disabled when `ADMIN_TOKEN` is not configured.

Set a token in `.env`:

```bash
ADMIN_TOKEN=replace-with-a-long-random-admin-token
```

Apply it to the running API:

```bash
docker compose up -d --build api
```

Use a shell variable for examples:

```bash
export ADMIN_TOKEN='replace-with-a-long-random-admin-token'
```

## Stats

```bash
curl -sS http://localhost:8015/api/admin/stats \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

The response includes total device count, total snapshot count, oldest/newest snapshot timestamps, and per-device snapshot counts with first/last snapshot timestamps.

## Delete Test Data

Dry run first:

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/test-data?deviceId=pixel-test&dryRun=true' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

Actual delete:

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/test-data?deviceId=pixel-test&dryRun=false' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

This matches devices where `device_id` or `device_name` exactly equals the supplied value.

## Delete Snapshots By Device

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/devices/uriel-phone/snapshots?dryRun=true' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

Set `dryRun=false` after checking the preview response.

## Delete Snapshots By Date Range

Dates must be strict `YYYY-MM-DD`. The `from` date starts at `00:00:00 UTC`; the `to` date includes the full day through `23:59:59.999999 UTC`.

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/snapshots?from=2026-05-01&to=2026-05-31&dryRun=true' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

## Delete Snapshots By Device And Date Range

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/snapshots?deviceId=uriel-phone&from=2026-05-01&to=2026-05-31&dryRun=true' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

## Full Snapshot Cleanup Guard

`DELETE /api/admin/snapshots` refuses full-table cleanup unless this confirmation is present:

```bash
curl -sS -X DELETE 'http://localhost:8015/api/admin/snapshots?dryRun=true&confirm=DELETE_ALL_SNAPSHOTS' \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

Keep `dryRun=true` until you are certain the matched counts are correct.

## Error Responses

- Missing admin token header: `401 Missing admin bearer token`
- Invalid admin token: `401 Invalid admin bearer token`
- `ADMIN_TOKEN` not configured: `503 Admin API is disabled`
- Invalid date or unsafe parameters: `422`
- No matching cleanup records: `404`

Admin logs include action names, filters, and counts only. They do not log raw health payloads.

The dashboard and read APIs require authenticated per-user sessions. Keep VPN-only access, Cloudflare Access, reverse proxy basic auth, or another trusted access layer before exposing the server beyond a trusted network.

Nutrition meal write/read APIs are normal app APIs: Android writes use per-user ingest tokens, dashboard reads use per-user sessions, and `ADMIN_TOKEN` is not used for normal nutrition operations.
