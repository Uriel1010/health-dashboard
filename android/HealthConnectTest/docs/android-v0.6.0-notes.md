Immediate server-triggered meal notifications through Firebase Cloud Messaging.

- Meal notifications are independent of the health synchronization interval.
- Account-bound phone registration, token refresh and logout/account-switch protection.
- One notification for each new meal, shared duplicate suppression with scheduled sync, and a silent initial baseline.
- Authenticated meal fetch after a push; titles and nutrition are not included in Firebase payloads.
- A push requests a refresh of meals, totals and widgets. Tapping opens Food.
- Existing scheduled synchronization remains a recovery path. Offline, disabled permissions, expired sessions and force-stopped apps can delay or prevent delivery.

Requires the updated dashboard backend and configured Firebase sender. Open the app after updating and enable New meal notifications under App preferences. No changes to existing meals or Health Connect permissions are required.

Verified: backend regression tests, FCM credential validation, Android worker/notification integration tests on a synthetic localhost account, and production build/startup checks. Live Google Play services delivery is not verified on the AOSP test emulator.
