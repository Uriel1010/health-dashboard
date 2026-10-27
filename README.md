# Health Dashboard + Android Companion

A self-hosted health and nutrition dashboard with a native Kotlin / Jetpack Compose Android companion. Meals are registered on the server; the phone shows nutrition progress, syncs health metrics, and provides home-screen widgets and optional new-meal notifications.

## Project layout

- `backend/`: FastAPI, PostgreSQL, account authentication and nutrition APIs.
- `frontend/`: React / TypeScript dashboard.
- `android/HealthConnectTest/`: Android app, minimum API 28.
- `docs/`: API, account administration, backup and development notes.

## Run the dashboard

1. Copy `.env.example` to `.env` and replace the database password and token placeholders. Keep the database password in `DATABASE_URL` consistent.
2. Run `docker compose up -d --build`.
3. Open `http://localhost:3016`; the API is available on port 8015.
4. Follow `docs/MULTI_USER_UPGRADE.md` for initial administrator/account setup.

Use HTTPS, secure cookies and explicit allowed origins for remote deployment. Example credentials are placeholders, not deployment credentials. The optional tunnel profile requires your own Cloudflare token.

## Android

Open `android/HealthConnectTest` in Android Studio, configure your Android SDK locally, and run `./gradlew assembleDebug testDebugUnitTest` (Windows: `gradlew.bat`). Configure your server URL and account in the app; grant Health Connect and notification permissions as needed. Local SDK paths, account sessions and tokens are not included.

Production Android releases provide an optimized, non-debuggable APK signed with the project's private production identity, plus an AAB for possible future store submission. See [production signing](docs/PRODUCTION_SIGNING.md). Development prereleases remain debug-signed and use a separate update channel. Android cannot update between these signing identities in place; plan a manual migration before uninstalling an existing app. No Google Play submission or approval is implied.

Notifications use scheduled polling, not server push. Nutrition totals and goals follow the server timezone and account reset hour. Health data availability depends on source apps and Health Connect permissions.

## Verify

```sh
cd frontend && npm ci && npm run build
cd ../backend && python -m unittest discover -s tests -v
cd ../android/HealthConnectTest && ./gradlew assembleDebug testDebugUnitTest
```

## Releases

Dashboard, Android development, and Android production use separate tags: `dashboard-vX.Y.Z`, `android-vX.Y.Z`, and `android-production-vX.Y.Z`. Android releases include update metadata and checksums. Deployment configuration, private signing material and user health data are never release assets.

See `SECURITY.md` before deployment. This project is not a medical device.
