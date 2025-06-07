# AGENT INSTRUCTIONS

This repository hosts **EtchDroid**, an Android application for writing OS images to USB drives. This file provides guidance for Codex agents working in the project.

## Project structure
- `app/` – Android app module
  - `src/main/` – Kotlin sources and Compose UI
  - `src/foss/` – F-Droid flavor overrides
  - `src/gplay/` – Google Play flavor overrides (telemetry, in-app review)
  - `src/test/` – unit tests
- `appium-tests/` – end-to-end tests written in Python with Appium. Some tests require QEMU; skip those.
- `misc/` – helper scripts and `mock-google-services.json` used for GPlay builds
- `fastlane/` – Play Store text and images
- Top-level `build.gradle.kts` and `settings.gradle.kts` define the Gradle build.

## Build and Test
- The first build and test run may take up to **10 minutes**. Allow enough time.
- It's best to run the build as a background process by daemonizing it and make it log to a file, so you can periodically check whether it's done or not, and so you don't accidentally risk interrupting it when checking.
- Run unit tests with Gradle for the appropriate flavor.
- You should not use `--scan` when invoking Gradle, since that would attempt to contact scans.gradle.com.

### FOSS variant
1. Ensure `ETCHDROID_ENABLE_SENTRY` is **unset** or empty.
2. Run `./gradlew assembleFossDebug`.
3. Run `./gradlew testFossDebugUnitTest`.

### GPlay variant
1. Set `ETCHDROID_ENABLE_SENTRY=true`.
2. Copy the mock Google services file before running Gradle:
   ```bash
   cp misc/mock-google-services.json app/google-services.json
   ```
3. Run `./gradlew assembleGplayDebug`.
4. Run `./gradlew testGplayDebugUnitTest`.

## End-to-end Tests
- **Do not** run the QEMU-based tests in `appium-tests` because the environment lacks KVM support.

## Linting
- You may run `./gradlew lint` before submitting changes.
