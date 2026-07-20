# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android project. Kotlin source lives under `app/src/main/java/zju/bangdream/ktv/casting`. UI code is in `ui/screens`, `ui/components`, and `ui/theme`; update code is in `update`. Resources are in `app/src/main/res`, with manifest/config XML in `app/src/main/AndroidManifest.xml` and `app/src/main/res/xml`.

Native Rust artifacts belong in `app/src/main/jniLibs/<abi>/libktv_casting_lib.so`. Unit tests live in `app/src/test`; instrumented tests live in `app/src/androidTest`.

## Fork Configuration

Repository identity (GitHub owner/name) is read at build time from `app/local.properties`, which is gitignored. Forks should copy `app/local.properties.example` to `app/local.properties` and set their own values:

```properties
repo_owner=your_user_name
repo_name=your_repo_name
```

These values are injected as `BuildConfig.GITHUB_REPO_OWNER` / `GITHUB_REPO_NAME` and used by the in-app update checker and repository links. If `local.properties` is absent, the build falls back to `KARAOKE-MASTER-ZJU` / `ktv-casting-android-app`.

## Build, Test, and Development Commands

- `./gradlew assembleDebug`: build a debug APK for local installation.
- `./gradlew installDebug`: install the debug build on a connected device or emulator.
- `./gradlew test`: run local JVM unit tests.
- `./gradlew connectedAndroidTest`: run instrumented tests on a connected device.
- `./gradlew packRelease`: assemble release APKs and copy ABI-specific outputs into `apks/` as `KTV-Casting-v<version>-<abi>.apk`.

Open the repository in Android Studio for Compose development and device debugging.

## Coding Style & Naming Conventions

Use Kotlin and Jetpack Compose idioms. Keep composables in PascalCase, for example `SettingsScreen`; keep functions and properties in lower camelCase. Preserve `zju.bangdream.ktv.casting`. Prefer small screen/component files over adding unrelated UI to `MainActivity.kt`.

Use 4-space indentation for Kotlin and Gradle Kotlin DSL files. Keep dependency versions in `gradle/libs.versions.toml` unless the project already declares a one-off dependency inline.

## Testing Guidelines

Use JUnit 4 in `app/src/test/java`. Use AndroidX Test, Espresso, and Compose UI tests in `app/src/androidTest/java`. Name tests after behavior, such as `queueEmpty_disablesNextButton`.

This repository is verified through GitHub Actions rather than local Gradle. Before tagging, ensure `gradle.properties` manually sets `rust_libs_version` to the latest Rust release. Release tags must follow Semantic Versioning in `vMAJOR.MINOR.PATCH` form, for example `v1.6.9`. Push commits, create and push a `v*` tag, then monitor with `gh run list --limit 5` and `gh run watch <run-id>`. Plain branch pushes may not start a run.

The CI workflow (`.github/workflows/build-and-release.yml`) handles:
- Auto-build on `v*` tag push
- Manual trigger via `workflow_dispatch` (requires version input)
- Branch push to `master` builds but does not create a release
- Changelog generation from git history between tags
- `release.json` pushed to `gh-pages` for in-app update checks

CI secrets required: `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD`. Optional variable: `CUSTOM_RUST_REPO` to override the Rust `.so` download repo.

## Commit & Pull Request Guidelines

Commit history uses Conventional Commit-style prefixes: `feat:`, `fix:`, and `chore:`. Keep subjects concise; Chinese or English is acceptable.

Pull requests should include a summary, testing performed, and any device/Android version used. Include screenshots for visible Compose UI changes. Mention Rust `.so`, ABI, update-check, or release packaging changes explicitly.

## Security & Configuration Tips

Do not commit signing keys, private tokens, or local Android Studio files. Verify downloaded native libraries before placing them in `jniLibs`, and keep ABI directories aligned with the Gradle split list: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
