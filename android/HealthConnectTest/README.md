# HealthConnectApp

Native Android app for viewing Health Connect metrics and syncing a health snapshot to a configured server.

## Current Features

- Jetpack Compose dashboard for Health Connect metrics.
- Runtime Health Connect permission requests.
- Manual refresh of local health metrics.
- Manual server sync.
- Manual meal logging to the private server.
- Manual pull of server meals into Health Connect.
- Periodic background sync with WorkManager.
- Configurable server URL, device ID, device name, ingest/read tokens, and LAN HTTP allowance.

## Health Connect Metrics

The app currently reads:

- Today's steps
- Today's distance
- Today's active calories
- Today's total calories
- Latest heart rate
- Latest resting heart rate
- Last night's sleep duration
- Recent exercise sessions
- Latest weight

The app writes manually logged meals to Health Connect as `NutritionRecord` when nutrition write permission is granted.

## Server Sync

The app sends a summarized health snapshot to:

```text
POST /api/v1/ingest/health-snapshot
```

Manual meals are sent to:

```text
POST /api/v1/nutrition/meals
```

Server meals are pulled from:

```text
GET /api/v1/nutrition/meals?from=YYYY-MM-DD&to=YYYY-MM-DD
```

It also tests connectivity with:

```text
GET /health
```

Snapshot and meal upload requests use ingest bearer token authentication:

```text
Authorization: Bearer <INGEST_API_TOKEN>
```

Server meal pulls use read bearer token authentication:

```text
Authorization: Bearer <READ_TOKEN>
```

The `GET /health` connectivity check is treated as a public health endpoint and does not send the ingest token.

The ingest and read tokens are entered in the app's Server sync settings and stored locally in the app's private DataStore preferences. They are not hardcoded in source code.

The app does not call dashboard, cleanup, or admin APIs.

## Configure Server Sync

In the app, open the Server sync section and set:

- Server URL: the base URL of the private server, such as `https://example.com`
- Device ID: stable ID for this phone in server records
- Device name: display name for this phone
- New ingest API token: the server-side `INGEST_API_TOKEN`
- New read token: the server-side `READ_TOKEN` used only for pulling server meals

Remote/public server URLs should use HTTPS. LAN HTTP can be enabled with the Allow LAN HTTP switch for private-network testing, but the server should be protected before being exposed outside the LAN.

Token separation:

- `INGEST_API_TOKEN`: used by this Android app to upload health snapshots and manual meal entries.
- `READ_TOKEN`: used by this Android app only for `GET /api/v1/nutrition/meals` server meal pulls.
- `DASHBOARD_TOKEN`: used by server dashboard/read APIs only. Do not paste this into the Android app unless the app later adds dashboard API calls.
- `ADMIN_TOKEN`: server-only maintenance token. Do not paste this into the Android app.

For health snapshot upload and app-created meal upload, the Android app should only need `INGEST_API_TOKEN`. Server-to-Health-Connect meal sync requires `READ_TOKEN`. If `GET /health` is accidentally protected by read/dashboard auth, treat that as a server-side design issue instead of sending a token to that endpoint.

## Authentication And Error Handling

The app shows sync diagnostics in the Server sync section and stores the last sync status.

- `401 Unauthorized`: token is missing, expired, or invalid.
- `403 Forbidden`: token is valid enough to identify the request, but is not allowed for the endpoint.
- `404 Not Found`: server URL or server version may not expose the expected endpoint.
- `503 Service Unavailable`: server endpoint is unavailable or server auth/API configuration is incomplete.
- `500 Server Error`: server failed; background sync can retry later.
- Network timeout or unreachable server: shown as a network/server reachability error.

Authentication and server configuration failures are treated as non-retryable so the background worker does not keep retrying a bad token or bad server setup noisily.

## Add A Meal

In the Food diary section:

1. Select the meal type.
2. Keep the default date/time or edit it as an ISO offset timestamp.
3. Enter the meal name.
4. Enter calories, protein grams, carbs grams, and fat grams.
5. Optionally add notes.
6. Tap Add meal.

The app uploads immediately using `INGEST_API_TOKEN`. A stable `clientMealId` is generated for each new form entry and reused if an upload fails, which lets the server handle duplicate/idempotent retries. After a successful upload, the form clears and a new `clientMealId` is generated.

This first version does not include a local offline queue. If the server is offline or a temporary network/server error happens, the app keeps the entered form values and shows a failure message so the user can retry manually.

When Health Connect is available, the app can also write the meal locally as a `NutritionRecord`. The only additional Health Connect permission requested for this is `WRITE_NUTRITION`. If nutrition write permission is missing or denied, server meal upload still works and the app shows that Health Connect nutrition write was skipped.

After Add meal, the app shows separate status lines for server upload and Health Connect nutrition write. A Health Connect success message means `insertRecords()` completed successfully. The app does not request `READ_NUTRITION`, so it does not read the inserted record back for verification; verify writes in the Health Connect app.

Nutrition mapping:

- meal name -> `NutritionRecord.name`
- meal type -> Health Connect meal type
- eaten at -> `startTime`, with `endTime` one minute later
- calories -> `Energy.kilocalories`
- protein, carbs, and fat -> `Mass.grams`
- fiber and sugar, when present from the server -> `Mass.grams`
- sodium, when present from the server -> `Mass.milligrams`

Writing to Health Connect does not guarantee Samsung Health will immediately display the meal. First verify that the record appears in Health Connect > App data > HealthConnectTest or Health Connect > Data and access > Nutrition, then verify Samsung Health has permission to read Nutrition from Health Connect.

## Sync Server Meals To Health Connect

OpenClaw can send meals to the server. The Android app can manually pull today's server meals with `READ_TOKEN` and write unsynced meals to Health Connect using `WRITE_NUTRITION`.

In the Server meals section, tap Sync server meals to Health Connect. The app fetches:

```text
GET /api/v1/nutrition/meals?from=today&to=today
```

The app stores synced server meal identities in DataStore and skips them on future pulls. It prefers `serverMealId`, then `id`, then `clientMealId` for the local sync identity. The Health Connect metadata client record id is deterministic: `server-meal-<identity>`.

Samsung Health display is still not guaranteed even when Health Connect contains the records.

## Build

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Install On Connected Device

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Manual Sync Test Steps

Successful sync:

1. Enter a valid HTTPS server URL.
2. Enter the correct `INGEST_API_TOKEN` in New ingest API token.
3. Tap Test connection.
4. Tap Sync now and confirm the status says Sync succeeded.

Invalid token:

1. Replace the token with an incorrect value.
2. Tap Sync now.
3. Confirm the app reports `Authentication failed HTTP 401` or `Authorization failed HTTP 403`.

Server offline:

1. Stop the server or enter an unreachable private test URL.
2. Tap Test connection or Sync now.
3. Confirm the app reports a network timeout, unresolved host, or server unreachable message.

Valid meal upload:

1. Rebuild and install the latest debug APK: `.\gradlew.bat assembleDebug`, then `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
2. Enter a valid server URL and `INGEST_API_TOKEN`.
3. Tap Request nutrition write and grant the Health Connect Nutrition write permission.
4. Confirm the app shows Nutrition write: Granted.
5. Fill the Food diary form with a meal name and non-negative calories/macros.
6. Tap Add meal.
7. Confirm the app shows separate status lines: Server upload: Meal uploaded, and Health Connect nutrition: Write succeeded.
8. Open Health Connect > App data > HealthConnectTest and confirm nutrition data exists.
9. Open Health Connect > Data and access > Nutrition and confirm the record exists.
10. Open Samsung Health and check whether it imports/displays the nutrition entry.

Nutrition permission denied:

1. Revoke or deny nutrition write permission.
2. Add a meal.
3. Confirm server upload still works.
4. Confirm the app reports Health Connect nutrition: Skipped: Nutrition write permission is missing.

Meal validation error:

1. Leave the meal name blank or enter a negative macro value.
2. Tap Add meal.
3. Confirm the app shows a validation message and keeps the form values.

Admin token boundary:

1. Search the app source for `ADMIN_TOKEN`, `DASHBOARD_TOKEN`, or admin endpoint paths.
2. Confirm no admin/dashboard token is configured or used by the app.

OpenClaw server meal pull:

1. Create a meal using OpenClaw.
2. Confirm it appears in the server/dashboard.
3. Rebuild and install the latest debug APK.
4. Enter a valid server URL, `INGEST_API_TOKEN`, and `READ_TOKEN`.
5. Confirm the app shows Nutrition write: Granted.
6. Tap Sync server meals to Health Connect.
7. Confirm the result shows the OpenClaw meal was written.
8. Open Health Connect > App data > HealthConnectTest and confirm the meal appears.
9. Run sync again and confirm the same meal is skipped instead of duplicated.

## Project Structure

```text
app/src/main/java/com/uriel/healthconnecttest/
  data/    Server settings persistence with DataStore
  health/  Health Connect availability, permissions, and reads
  sync/    Snapshot models, API client, and WorkManager sync
  ui/      Compose dashboard and settings UI
```

## Notes

- Minimum SDK: 28
- UI: Jetpack Compose
- Health data source: Health Connect
- Background execution: WorkManager periodic work
- Snapshot sync direction: Health Connect to server
- Manual meal direction: Android app to server and Health Connect
- Server meal direction: server to Android app to Health Connect, manual only
- Manual meal Health Connect write: `NutritionRecord` with `WRITE_NUTRITION`
- Offline meal queue: not implemented yet
- Background server meal pull is not implemented yet
