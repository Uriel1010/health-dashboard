First production-signed distribution of Health Companion.

- Private production signing identity, non-debuggable release build.
- R8 code optimization and resource shrinking.
- Separate production update channel: android-production-vX.Y.Z.
- Signed APK for direct installation and signed AAB for future store submission.

Important migration note: previous GitHub APKs were debug-signed. This production APK cannot update them in place because Android requires the same signing certificate. Back up your configuration and plan a manual migration; do not uninstall your existing app without considering local settings and Health Connect data. Server meals are unaffected.

This release is not a Google Play publication or approval. Future production updates must use the same private signing identity. APK signatures and non-debuggable flags are verified before release.
