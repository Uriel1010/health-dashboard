Fix production app crashing immediately at startup.

- Preserve the no-argument constructor of WorkManager's Room database during release optimization.
- Fix `NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init>` during AndroidX Startup initialization, before the first screen can open.
- Keep production signing and code/resource optimization enabled.

Reproduced with the published 0.3.0 production APK in an isolated Android 16 emulator. This failure does not depend on account settings or cached app data.

The fixed signed production APK passed three cold launches and navigation through Today, Food, Trends, Health and Settings on the same emulator. The regression smoke test detects the original 0.3.0 crash. Samsung-specific validation remains to be done on the affected phone.
