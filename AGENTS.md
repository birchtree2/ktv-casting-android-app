# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android project. Kotlin source lives under `app/src/main/java/zju/bangdream/ktv/casting`. UI code is in `ui/screens`, `ui/components`, and `ui/theme`; update code is in `update`. Resources are in `app/src/main/res`, with manifest/config XML in `app/src/main/AndroidManifest.xml` and `app/src/main/res/xml`.

Native Rust artifacts belong in `app/src/main/jniLibs/<abi>/libktv_casting_lib.so`. Unit tests live in `app/src/test`; instrumented tests live in `app/src/androidTest`.

## Dependent Rust Library

The upstream `ktv-casting` Rust library source lives at `../star/ktv-casting` (local clone of the `android-app` branch). The `.so` is not built locally here — it's downloaded from the Rust repo's GitHub release during CI. See the Rust repo's `CLAUDE.md` for JNI/build details and the version-linking rule (`rust_libs_version` in `gradle.properties` must match the Rust repo's release tag).

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

This repository is verified through GitHub Actions rather than local Gradle. Before tagging, ensure `gradle.properties` manually sets `rust_libs_version` to the intended Rust library version. Push commits, create and push a `v*` tag, then monitor with `gh run list --limit 5` and `gh run watch <run-id>`. Plain branch pushes may not start a run.

## Release & Tag Conventions

开发测试一律在 **fork 仓库**（如 `birchtree2/ktv-casting-android-app`）内进行，不直接在 `KARAOKE-MASTER-ZJU` 主仓库内开发。为避免开发 tag 与主仓库 tag 冲突，按以下规则打 tag：

- **fork / 开发仓库**：release tag 使用 `vA.B.C+dev(.name)(.description)` 形式（SemVer 构建元数据），例如 `v1.6.13+dev.roomid-string`。**dev tag 只推送 fork，不推送主仓库。**
- **主仓库 `KARAOKE-MASTER-ZJU/ktv-casting-android-app`**：正式发布使用**无元数据**的 SemVer 标准，`vA.B.C` 或 `vA.B.C-alpha`（如 `v1.4.2`、`v1.4.1-alpha.1`），不带 `+dev` 后缀。
- 严格按 Semantic Versioning 递增基础版本：兼容新增功能升 minor，兼容修复升 patch，破坏性变更升 major；`+dev.*` 不能代替基础版本递增。
- GitHub Release 必须通过推送 `v*` tag 触发。只推 `master` 仅做编译检查，不创建 Release。

The CI workflow (`.github/workflows/build-and-release.yml`) handles:
- Auto-build on `v*` tag push
- Manual trigger via `workflow_dispatch` (requires version input)
- Branch push to `master` builds but does not create a release
- Changelog generation from git history between tags
- `release.json` pushed to `gh-pages` for in-app update checks

CI secrets required: `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD`. Optional variable: `CUSTOM_RUST_REPO` to override the Rust `.so` download repo.

## Commit & Pull Request Guidelines

Commit message 必须使用中文，并保持主题简短、准确。可按需使用 `feat:`、`fix:`、`chore:` 等 Conventional Commit 前缀。

Pull requests should include a summary, testing performed, and any device/Android version used. Include screenshots for visible Compose UI changes. Mention Rust `.so`, ABI, update-check, or release packaging changes explicitly.

## Security & Configuration Tips

Do not commit signing keys, private tokens, or local Android Studio files. Verify downloaded native libraries before placing them in `jniLibs`, and keep ABI directories aligned with the Gradle split list: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
