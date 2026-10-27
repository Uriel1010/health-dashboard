# Production Android distribution

Production uses a private RSA signing identity stored outside this repository. Never commit signing.properties, keystore.properties, keystores, key passwords, or R8 mappings.

Set `HEALTH_SIGNING_PROPERTIES` to an absolute path outside the project before building:

```sh
export HEALTH_SIGNING_PROPERTIES=/private/path/signing.properties
./gradlew assembleDebug assembleRelease bundleRelease testDebugUnitTest
```

The properties file contains `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Release packaging refuses to run without signing configuration. Preserve the signing identity securely: losing it prevents updates to existing production installations. `scripts/New-ProductionSigning.ps1` can create a new identity once on Windows; never regenerate it for later releases.

Production releases are non-debuggable and use R8/resource shrinking. Publish using `scripts/release_android.py --production --apk .../app-release.apk --metadata .../output-metadata.json --bundle .../app-release.aab --notes ...` from the canonical monorepo. Production tags use `android-production-vX.Y.Z`; debug builds continue to follow `android-vX.Y.Z`. Both channels verify the installed signing certificate before installation.

## Migrating from development

The production APK has the same application ID but a different signing certificate from development APKs. Android cannot update the debug-signed app in place. Do not uninstall it automatically: uninstalling removes its local settings and may remove app-owned Health Connect data. Server meals stay on the server. Plan a manual migration and sign in again after installing production.

The AAB is signed for potential Play Console submission; it has not been submitted or approved by Google Play. Health Connect apps require applicable Play Console declarations and permissions review. APK release signing is not a security audit or a claim of store approval.
