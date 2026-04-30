# Project Instructions: EtchDroid

This repository hosts **EtchDroid**, an Android application for writing OS images to USB drives.

## Project Structure
- `app/` – Android app module
  - `src/main/` – Kotlin sources and Compose UI
  - `src/foss/` – F-Droid flavor overrides
  - `src/gplay/` – Google Play flavor overrides (telemetry, in-app review)
  - `src/test/` – Unit tests
- `appium-tests/` – End-to-end tests written in Python with Appium.
- `misc/` – Helper scripts and `mock-google-services.json` used for GPlay builds
- `fastlane/` – Play Store text and images
- Top-level `build.gradle.kts` and `settings.gradle.kts` define the Gradle build.

## Development Workflow & Conventions

### 1. Linear History (No Merge Commits)
We use a **rebase-only workflow**. Your branch must be rebased onto the latest `main` branch.
- **Do:** Use `git rebase main`.
- **Don't:** Use `git merge main`.

### 2. Atomic & Concise Commits
- **One Thing Per Commit:** Each commit should perform one logical task.
- **Separate Cleanup from Logic:** Do not lump stylistic/formatting/cleanup changes into functional commits.
- **Commit Messages:** Be descriptive. Use [Conventional Commits](https://www.conventionalcommits.org/) format (e.g., `feat(ui): add progress bar`).

### 3. Build and Unit Test
- The first build and test run may take up to **10 minutes**.
- Run the build as a background process (`is_background: true`) and log to a file for long runs.
- Do not use `--scan` when invoking Gradle.

#### FOSS variant
1. Ensure `ETCHDROID_ENABLE_SENTRY` is **unset** or empty.
2. Build: `./gradlew assembleFossDebug`
3. Test: `./gradlew testFossDebugUnitTest`

#### GPlay variant
1. Set `ETCHDROID_ENABLE_SENTRY=true`.
2. Prepare: `cp misc/mock-google-services.json app/google-services.json`
3. Build: `./gradlew assembleGplayDebug`
4. Test: `./gradlew testGplayDebugUnitTest`

### 4. End-to-End (Appium) Tests
Agents should run Appium tests unless instructed otherwise.
- Requirements: Android SDK, `uv`, `7z`, `qemu-img`, `node`/`npm` installed.
- **Install Appium**:
  ```bash
  cd appium-tests
  npm ci
  ```
- Location: `appium-tests/`

#### Running Bliss OS VM locally:
1. Prepare the VM files: `./appium-tests/scripts/prepare-vm.sh` (downloads ISO, extracts files, creates USB image in `.appium-vm/`).
2. Run the VM (in a separate terminal): `./appium-tests/scripts/run-vm.sh` (shows GTK UI, serial console on stdio, ADB on 5556).
3. Wait for the VM to boot: `./appium-tests/scripts/wait-vm-startup.sh`
4. Build and install the app: `./gradlew installFossDebug`
5. Run the tests: `uv run pytest -sv` (inside `appium-tests/`)

- Run only generic tests (excluding QEMU): `uv run pytest -m "not qemu" -sv`
- *Note: QEMU tests require a specific setup (see `appium-tests/README.md`). If the environment supports KVM/QEMU, they should be run.*

### 5. Linting
Run `./gradlew lint` before submitting changes.
