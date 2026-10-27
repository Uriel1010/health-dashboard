# Android updates

The Android app checks published releases in `Uriel1010/health-dashboard` when opened, and from Settings > App updates. Only `android-vX.Y.Z` releases with `update.json` are candidates; dashboard releases are ignored. Development prereleases are supported because the project currently distributes debug-signed APKs.

## Publish an update

1. Increase both `versionCode` and `versionName` in `android/HealthConnectTest/app/build.gradle.kts`.
2. Build with `gradlew assembleDebug testDebugUnitTest`, using the same signing identity as the installed version. Keep signing keys outside Git.
3. Commit and push the reviewed Android source from the canonical monorepo.
4. Write release notes to a local file, then run from the monorepo root:

```sh
python3 scripts/release_android.py \
  --apk android/HealthConnectTest/app/build/outputs/apk/debug/app-debug.apk \
  --metadata android/HealthConnectTest/app/build/outputs/apk/debug/output-metadata.json \
  --notes /path/to/release-notes.md
```

The script publishes the APK, SHA-256 checksum, and `update.json`. Do not upload signing keys or account settings. The current debug-signed distribution is intended for development, not Play Store production.

## Installation behavior

The app compares numeric version codes, checks device compatibility, downloads only assets from this repository, verifies SHA-256, and checks the APK package ID, version code and signing certificate against the installed app. Installation uses Android's package installer and requires the user's approval. If requested, allow app installs from Health Companion, return to the app, and tap Install update again. No silent installation is performed. Network and verification failures leave the existing app intact.
