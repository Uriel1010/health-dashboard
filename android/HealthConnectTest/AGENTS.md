# Repository Instructions

This repository contains a native Android app built with Kotlin and Jetpack Compose.

## Project Direction

- Use Kotlin only for app code.
- Use Jetpack Compose for UI.
- Prefer a simple MVVM structure as the app grows.
- Keep changes small, focused, and easy to review.
- Do not add heavy dependencies unless they are necessary.
- Health Connect integration will be added step by step.
- Minimum SDK should remain 28 or higher.
- The first MVP is reading today's step count from Health Connect.

## Build And Verification

- On Windows PowerShell, run:

```powershell
.\gradlew.bat assembleDebug
```

- Always run the build after code changes.
