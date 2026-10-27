# Health Dashboard API — Regular User Guide

This guide documents the API available to regular Health Dashboard users. Every health record, device, meal, and token is isolated to the authenticated account.

Administrator, user-management, server-maintenance, database, and deployment operations are intentionally excluded.

## Base URL

For calls from outside the LAN, use the public HTTPS API address:

```bash
export API_BASE_URL="https://health.example.com"
```

For calls inside the private LAN, the administrator may also provide a local address such as `http://192.168.1.10:8015`.

## Authentication overview

The API uses two separate credentials:

| Credential | Used for | Sent as |
| --- | --- | --- |
| Account session | Dashboard reads, manual meals, and account settings | HTTP-only cookie |
| Ingest token | Android snapshot and meal writes | `Authorization: Bearer …` |

Never place a password, session cookie, or ingest token in source control, screenshots, public issue reports, or published examples.

There is no public registration. An administrator creates the account and provides a one-time password.

## Account session

### Log in

`POST /api/v1/auth/login`

```bash
curl -sS -c session.cookies \
  -X POST "$API_BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "your-username",
    "password": "your-password"
  }'
```

A successful response sets an HTTP-only session cookie and returns the account:

```json
{
  "id": 12,
  "username": "alex",
  "displayName": "Alex",
  "isAdmin": false,
  "isActive": true,
  "mustChangePassword": false,
  "nutritionResetHour": 4,
  "serverTimezone": "Asia/Jerusalem",
  "currentNutritionDate": "2026-08-20"
}
```

Keep `session.cookies` private. Add `-b session.cookies` to authenticated curl requests.

### First login and password changes

If `mustChangePassword` is `true`, change the temporary password before using health-data endpoints.

`POST /api/v1/auth/change-password`

```bash
curl -sS -b session.cookies \
  -X POST "$API_BASE_URL/api/v1/auth/change-password" \
  -H "Content-Type: application/json" \
  -d '{
    "currentPassword": "temporary-password",
    "newPassword": "a-new-password-of-at-least-12-characters"
  }'
```

Changing the password revokes the account's other browser sessions. The current session remains active.

### Read the current account

`GET /api/v1/auth/me`

```bash
curl -sS -b session.cookies "$API_BASE_URL/api/v1/auth/me"
```

### Log out

`POST /api/v1/auth/logout`

```bash
curl -i -b session.cookies \
  -X POST "$API_BASE_URL/api/v1/auth/logout"
```

A successful logout returns `204 No Content`.

## Nutrition-day preference

Each user chooses an hourly nutrition reset boundary from `0` through `23`.

`PATCH /api/v1/account/preferences`

```bash
curl -sS -b session.cookies \
  -X PATCH "$API_BASE_URL/api/v1/account/preferences" \
  -H "Content-Type: application/json" \
  -d '{"nutritionResetHour": 4}'
```

The server interprets the hour in `serverTimezone`. With a reset hour of `4`, the nutrition day runs from 04:00 through 03:59 the next calendar day. A meal at 02:00 belongs to the preceding nutrition date.

Changing the reset hour immediately changes how historical meals are grouped; original timestamps are not rewritten.

## Android ingest token

A user has at most one active ingest token. The full token is returned only when generated or rotated.

### Check token status

`GET /api/v1/account/ingest-token`

```bash
curl -sS -b session.cookies "$API_BASE_URL/api/v1/account/ingest-token"
```

```json
{
  "active": true,
  "prefix": "hd_ing_example",
  "createdAt": "2026-08-20T12:00:00Z"
}
```

### Generate or rotate

`POST /api/v1/account/ingest-token/rotate`

```bash
curl -sS -b session.cookies \
  -X POST "$API_BASE_URL/api/v1/account/ingest-token/rotate"
```

```json
{
  "active": true,
  "prefix": "hd_ing_example",
  "createdAt": "2026-08-20T12:00:00Z",
  "token": "hd_ing_copy-this-value-once"
}
```

Copy the `token` immediately and configure it in the user's Android sender. Rotation revokes the previous token immediately.

For command-line examples:

```bash
export INGEST_TOKEN="hd_ing_copy-this-value-once"
```

### Revoke

`DELETE /api/v1/account/ingest-token`

```bash
curl -i -b session.cookies \
  -X DELETE "$API_BASE_URL/api/v1/account/ingest-token"
```

Revocation returns `204 No Content` and stops future Android writes.

## Devices and health snapshots

All read endpoints in this section require `-b session.cookies`.

### List devices

`GET /api/v1/devices`

```bash
curl -sS -b session.cookies "$API_BASE_URL/api/v1/devices"
```

Only devices owned by the current user are returned.

### Latest snapshot

`GET /api/v1/health/latest?deviceId=DEVICE_ID`

```bash
curl -sS -b session.cookies \
  "$API_BASE_URL/api/v1/health/latest?deviceId=my-phone"
```

If `deviceId` is omitted, the user's most recently seen device is selected.

### Recent snapshots

`GET /api/v1/health/recent`

Query parameters:

- `deviceId`: required
- `limit`: optional, from 1 through 100; default 20
- `from`: optional ISO 8601 datetime
- `to`: optional ISO 8601 datetime

```bash
curl -sS -b session.cookies \
  "$API_BASE_URL/api/v1/health/recent?deviceId=my-phone&limit=20"
```

### Metric history

`GET /api/v1/health/history`

Required parameters are `deviceId` and `metric`. Optional `from` and `to` values are ISO 8601 datetimes.

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
curl -sS -b session.cookies \
  "$API_BASE_URL/api/v1/health/history?deviceId=my-phone&metric=steps_today"
```

### Snapshot detail

`GET /api/v1/health/snapshot/{snapshotId}`

```bash
curl -sS -b session.cookies \
  "$API_BASE_URL/api/v1/health/snapshot/123"
```

This response includes the original raw health payload. Treat it as sensitive health information.

## Upload health snapshots

`POST /api/v1/ingest/health-snapshot`

This endpoint uses the ingest token, not the browser cookie.

```bash
curl -sS \
  -X POST "$API_BASE_URL/api/v1/ingest/health-snapshot" \
  -H "Authorization: Bearer $INGEST_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "my-phone",
    "deviceName": "My phone",
    "capturedAt": "2026-08-20T12:30:00+03:00",
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

The device is automatically associated with the user who owns the ingest token. The request does not accept a user ID.

## Nutrition reads

Nutrition `date`, `from`, and `to` values are nutrition-day labels, using the current user's reset hour.

### List meals

`GET /api/v1/nutrition/meals`

Use either `date` or a `from`/`to` range.

```bash
curl -sS -b session.cookies \
  "$API_BASE_URL/api/v1/nutrition/meals?date=2026-08-20"
```

Each meal includes both:

- `localDate`: its original submitted local calendar date
- `nutritionDate`: the date calculated using the user's reset hour

### Daily summary

`GET /api/v1/nutrition/summary/daily?date=YYYY-MM-DD`

```bash
curl -sS -b session.cookies \
  "$API_BASE_URL/api/v1/nutrition/summary/daily?date=2026-08-20"
```

The response contains meal count, calories, protein, carbohydrates, fat, fiber, sugar, sodium, and totals grouped by meal type.

### Range summary

`GET /api/v1/nutrition/summary/range?from=YYYY-MM-DD&to=YYYY-MM-DD`

```bash
curl -sS -b session.cookies \
  "$API_BASE_URL/api/v1/nutrition/summary/range?from=2026-08-01&to=2026-08-20"
```

### Daily and rolling trends

`GET /api/v1/nutrition/trends?to=YYYY-MM-DD`

```bash
curl -sS -b session.cookies \
  "$API_BASE_URL/api/v1/nutrition/trends?to=2026-08-20"
```

The endpoint returns 30 visible nutrition days. Each point includes:

- Daily calories, protein, carbohydrates, and fat
- Trailing 7-day average
- Trailing 14-day average
- Trailing 30-day average
- `hasMeals`
- `isIncomplete`

Missing days count as zero and every average uses its complete divisor of 7, 14, or 30.

## Create and manage meals

Meal writes accept either the signed-in dashboard session or the user's ingest token. External apps and automations should use the ingest token.

### Create or idempotently update a meal

`POST /api/v1/nutrition/meals`

```bash
curl -sS \
  -X POST "$API_BASE_URL/api/v1/nutrition/meals" \
  -H "Authorization: Bearer $INGEST_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientMealId": "android-20260820-123000-abc123",
    "deviceId": "my-phone",
    "eatenAt": "2026-08-20T12:30:00+03:00",
    "timezone": "Asia/Jerusalem",
    "mealType": "lunch",
    "title": "Chicken and rice",
    "notes": "Homemade",
    "caloriesKcal": 720,
    "proteinG": 48,
    "carbsG": 82,
    "fatG": 20,
    "fiberG": 7,
    "sugarG": 5,
    "sodiumMg": 640
  }'
```

Supported meal types are `breakfast`, `lunch`, `dinner`, `snack`, and `other`.

Reusing the same non-empty `clientMealId` with the same `deviceId` and ingest-token owner updates that meal instead of creating a duplicate.

### Update a meal

`PATCH /api/v1/nutrition/meals/{mealId}`

Send only fields that should change:

```bash
curl -sS \
  -X PATCH "$API_BASE_URL/api/v1/nutrition/meals/456" \
  -H "Authorization: Bearer $INGEST_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Updated meal title","caloriesKcal":680}'
```

### Delete a meal

`DELETE /api/v1/nutrition/meals/{mealId}`

```bash
curl -sS \
  -X DELETE "$API_BASE_URL/api/v1/nutrition/meals/456" \
  -H "Authorization: Bearer $INGEST_TOKEN"
```

Meals are soft-deleted and disappear from lists, summaries, and trends.

## Errors

The API returns JSON errors in this form:

```json
{"detail": "Human-readable error"}
```

Common statuses:

| Status | Meaning |
| --- | --- |
| `401` | Missing, expired, or invalid session/ingest token |
| `403` | Password change required or operation not allowed |
| `404` | Resource does not exist for the current user |
| `409` | Conflicting unique value |
| `422` | Invalid field, date range, metric, timezone, or payload |

For privacy, another user's resource is not exposed and behaves as not found.

## Publishing checklist

Before publishing this guide:

- Replace the example base URL with the public API hostname, if appropriate.
- Keep all usernames, passwords, cookies, ingest tokens, device IDs, snapshot IDs, and raw payloads fictional.
- Require HTTPS for any API exposed outside a trusted LAN.
- Consider VPN or an additional trusted access layer for sensitive health data.
- Do not publish administrator or infrastructure-maintenance documentation with this user guide.
