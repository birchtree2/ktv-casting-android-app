# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android project. The app module lives in `app/`, with Kotlin source under `app/src/main/java/zju/bangdream/ktv/casting`. UI code is organized into `ui/screens`, `ui/components`, and `ui/theme`; update-related code is in `update`. Android resources are in `app/src/main/res`, and manifest/config XML files are in `app/src/main/AndroidManifest.xml` and `app/src/main/res/xml`.

Native Rust artifacts belong in `app/src/main/jniLibs/<abi>/libktv_casting_lib.so`. Unit tests live in `app/src/test`; instrumented tests live in `app/src/androidTest`.

## Build, Test, and Development Commands

- `./gradlew assembleDebug`: build a debug APK for local installation.
- `./gradlew installDebug`: install the debug build on a connected device or emulator.
- `./gradlew test`: run local JVM unit tests.
- `./gradlew connectedAndroidTest`: run instrumented tests on a connected device.
- `./gradlew packRelease`: assemble release APKs and copy ABI-specific outputs into `apks/` as `KTV-Casting-v<version>-<abi>.apk`.

Open the repository in Android Studio for Compose development and device debugging.

## Coding Style & Naming Conventions

Use Kotlin and Jetpack Compose idioms. Keep composables in PascalCase, for example `SettingsScreen` or `VolumeControl`; keep functions and properties in lower camelCase. Preserve the package root `zju.bangdream.ktv.casting`. Prefer small screen/component files over adding unrelated UI to `MainActivity.kt`.

Use 4-space indentation for Kotlin and Gradle Kotlin DSL files. Keep dependency versions in `gradle/libs.versions.toml` unless the project already declares a one-off dependency inline.

## Testing Guidelines

Use JUnit 4 in `app/src/test/java`. Use AndroidX Test, Espresso, and Compose UI tests in `app/src/androidTest/java`. Name tests after behavior, such as `queueEmpty_disablesNextButton`.

This repository is normally verified through GitHub Actions rather than local Gradle runs. After committing, push to GitHub, then check workflow progress with `gh run list --limit 5` and `gh run watch <run-id>`. The release workflow currently triggers on `v*` tag pushes and `repository_dispatch` events, so a plain branch push may not start a run.

## Commit & Pull Request Guidelines

Commit history uses Conventional Commit-style prefixes: `feat:`, `fix:`, and `chore:`. Keep subjects concise; Chinese or English is acceptable.

Pull requests should include a short summary, testing performed, and any device/Android version used. Include screenshots or screen recordings for visible Compose UI changes. Mention changes to Rust `.so` artifacts, ABI coverage, update-check behavior, or release packaging explicitly.

## Security & Configuration Tips

Do not commit signing keys, private tokens, or local Android Studio files. Verify downloaded native libraries before placing them in `jniLibs`, and keep ABI directories aligned with the Gradle split list: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
