Server-triggered meal push notifications for Android 0.6.0.

- Durable, transactional delivery queue for newly created meals, including dashboard and API entries.
- Account/session-bound device registration with installation ownership checks, token rotation and queued-message invalidation on account changes.
- Authenticated per-meal lookup; Firebase payloads contain only opaque event/meal identifiers and an account binding.
- Retry handling, unregistered-token cleanup, expired/revoked-session protection and no notification backfill for old meals.
- Optional `docker-compose.push.yml` mounts the private Firebase credential read-only outside the image and repository. See `docs/MEAL_PUSH.md`.

The additive database migration requires a database backup before deployment. Existing scheduled synchronization remains available. Backend regression tests and FCM credential validation pass; live transport to a Google Play-enabled phone is not verified by the AOSP emulator test.
