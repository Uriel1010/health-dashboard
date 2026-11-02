# Android 0.4.0 reference redesign

The implementation follows the supplied ten-screen Health Companion reference: purple actions and navigation, compact nutrition summaries, menu-based meal rows, a full-screen registration form, daily-average charts, compact health readings, Settings rows, explicit synchronization details, deletion confirmation and refresh failures.

## Appearance

App preferences supports Follow system, Light and Dark. The choice is stored locally and applied to screens, dialogs and system-bar icon contrast. Wallpaper-derived colors remain disabled by default so the reference palette is consistent.

| Role | Light | Dark |
| --- | --- | --- |
| Primary | `#6324B5` | `#C7A7FF` |
| Primary container | `#EEE7FF` | `#392451` |
| Background | `#FAF9FE` | `#141119` |
| Surface | `#FDFCFF` | `#1D1924` |
| Main text | `#17131E` | `#F1EDF7` |
| Border | `#E6E1ED` | `#393142` |

Nutrition accents retain distinct protein, carbohydrate, fat and fiber colors. Typography follows Android font scaling; navigation keeps accessible destination names when labels are omitted at large text sizes.

## Functional mapping

- Today preserves the current account nutrition day, totals, goals, recent meals, refresh and edit-goal shortcuts. Missing initial data is not fabricated as zero consumption. Successful refresh clears previous errors.
- Food orders meals chronologically while Today retains newest-first recent meals. Registration and deletion use the existing server and Health Connect workflows.
- The date control explains the loaded nutrition day and reset boundary. It does not falsely suggest that data has been filtered to a different date.
- Nutrition statistics use the existing authenticated 7/14/30-day API and retain coverage, unknown-data handling and exclusion of today.
- Activity and Weight read completed phone-calendar days from Health Connect. Activity uses calendar-period aggregation to account for daylight-saving boundaries. Weight uses the last measurement for each day and follows pagination.
- Settings presents Updates, Account & server, Health Connect, Daily nutrition goals, App preferences and About. Synchronization scheduling, status and permissions are under Health Connect; widgets, notifications and appearance are under App preferences.
- Update availability is real release state, not the sample version number from the reference.
- Meal records do not contain photo URLs. Small code-drawn food illustrations occupy the thumbnail position without claiming to be photos of actual meals.

No server API or schema change is needed. Existing signing identity and the WorkManager constructor-retention rule remain intact.

## Verification

`ReferenceScreensTest` exercises both themes, registration/cancel, deletion confirmation/cancel, average-period controls, history categories, Health, Settings, expanded updates, sync details, simulated refresh failure and a synthetic register/delete round trip. It saves screenshots of the real rendered app.

The three interaction tests passed on the isolated Android 16 emulator at normal and 150% system text size. Light, dark, registration, confirmation, synchronization and error screenshots were visually inspected. Screenshots in the README use synthetic nutrition data, never production account records.

The tests refuse physical devices and require an explicit opt-in plus a positively identified localhost fixture. They never use real accounts or food records.

On an isolated emulator, start the fixture in a separate terminal:

```powershell
python scripts/reference_fixture_server.py
adb -s emulator-5580 reverse tcp:18016 tcp:18016
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w -e referenceFixture true -e class com.uriel.healthconnecttest.ReferenceScreensTest com.uriel.healthconnecttest.test/androidx.test.runner.AndroidJUnitRunner
```

Use the existing `scripts/Test-ProductionStartup.ps1` against the signed release on an isolated emulator to check three cold launches and all five navigation destinations. It deliberately clears only the emulator's app data and refuses physical-device serials.
