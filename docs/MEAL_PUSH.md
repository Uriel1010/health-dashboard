# Immediate meal notifications

Android 0.6.0 registers an installation with the authenticated dashboard account when meal notifications are enabled. The backend records a delivery for every enabled installation in the same transaction as each newly inserted meal. Updates and idempotent re-submissions do not create another event. Existing meals are not backfilled into the push queue.

FCM carries only an event ID, meal ID, and random account binding. Meal titles and nutrition stay on the dashboard server until the app fetches them over the authenticated API. The phone checks its current account and notification preference before displaying. Push and scheduled synchronization share meal deduplication; the initial registration silently establishes a baseline. Delivery is normally prompt when connected, but Android, connectivity and force-stop restrictions can delay it.

## Server configuration

1. Create a Firebase project and register the Android package `com.uriel.healthconnecttest`. Enable the FCM HTTP v1 API.
2. Keep the server service-account JSON in a protected directory outside the checkout (directory mode 700, file mode 600). Do not put it in an image, APK, source archive or GitHub release.
3. Set `FCM_PROJECT_ID` and `FCM_CREDENTIALS_FILE` in your private deployment environment. The latter is the absolute host path of the credential JSON.
4. Include `docker-compose.push.yml` alongside your existing Compose files and rebuild the API. Its normal startup applies the additive Alembic migration. Back up the database first. For example: `docker compose -f docker-compose.yml -f docker-compose.push.yml up -d --build api`.
5. Mounts are read-only. With no FCM project configured the sender remains inactive. No Firebase Analytics dependency is used.

## Android builds

Download the Android `google-services.json` from your Firebase project into `android/HealthConnectTest/app/google-services.json`. It is excluded from Git. Production packaging requires this configuration; public source can build a debug app without it, using periodic notifications only. Configure your own Firebase project when building a fork.

## API additions

- `POST /api/v1/push/installations`: authenticated dashboard session; registers or disables a phone's installation, verifies its installation secret, and returns its current binding. Account/session changes rotate that binding. Tokens cannot be reassigned to a different installation without ownership.
- `GET /api/v1/push/meals/{meal_id}`: authenticated, account-scoped meal details for a push. Deleted and foreign-account meals return 404.

The sender checks account/session validity and binding again before sending. Revoked/expired sessions stop delivery. Transient failures retry with bounded exponential delay for up to one day; unregistered tokens disable their installation. Each event has a separate delivery record and message, without a collapse key. The app rejects messages for a previous account and excludes push installation secrets from backup/device transfer.

## Verification

Backend unit tests cover transaction rollback, updates, account boundaries, old meals, revoked sessions, changed bindings, retries, invalid tokens and deleted meals. Android's emulator-only `MealPushTest` uses `scripts/reference_fixture_server.py` on localhost port 18016, `adb reverse tcp:18016 tcp:18016`, and notification permission. It injects the same opaque event data consumed by the Firebase service and exercises actual WorkManager, authenticated fetch and notification posting. It verifies one notification per meal, duplicate suppression, stale bindings and disabled notifications.

An AOSP emulator cannot validate the Google Play services transport. A Play-enabled device is required for a full live FCM delivery/Doze test; FCM `validate_only` can independently check server credentials without delivering a message.
