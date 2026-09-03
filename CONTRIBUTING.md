# Contributing to SongFlip 🎵

Thank you for your interest in contributing to SongFlip! Whether you are reporting an issue, proposing new features, adding a translation, or fixing a bug, your help is warmly welcomed.

---

## 🏗️ Architecture Overview

SongFlip is built as a Kotlin Multiplatform (KMP) project:
- **`app/`**: Native Android app (Jetpack Compose, Material 3, Quick Settings Tile, Android 13+ Themed Icons).
- **`iosApp/`**: Native iOS app (SwiftUI, Share Extension, App Intents for Siri Shortcuts & Action Button).
- **`shared/`**: Shared KMP core engine (`SongLinkEngine`, `UrlUtils`, `LinkCache`, `AptabaseClient`).
- **`functions/`**: Firebase Cloud Functions backend (SSR web landing pages, L2 cache, YouTube / Deezer live resolvers).

---

## 🚀 Getting Started

### Prerequisites
- **JDK 17** (Temurin or OpenJDK)
- **Android Studio** (Koala / Ladybug or newer) with Android SDK 26–36
- **Xcode 16+** (for building the iOS target on macOS)
- **Node.js 22** & npm (for Firebase functions in `functions/`)

### Local Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/thefraggle/songflip.git
   cd songflip
   ```

2. Optional Configuration (`local.properties`):
   If you are building your own fork or testing custom analytics / revenue endpoints, you can configure:
   ```properties
   # Optional: Custom RevenueCat and Aptabase telemetry keys
   revenuecat.api.key=your_key_here
   aptabase.app.key=your_app_key_here
   aptabase.host=https://your-telemetry-instance.com
   ```
   If omitted, safe defaults are used automatically.

3. Run Tests:
   ```bash
   ./gradlew :shared:allTests :app:testDebugUnitTest
   ```

4. Build Debug APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```

5. Build iOS Framework:
   ```bash
   ./gradlew :shared:assembleSongFlipKitReleaseXCFramework
   ```

---

## 🌍 Adding or Updating Translations

SongFlip supports 22+ languages!
- **Android**: Add or update strings in `app/src/main/res/values-<lang>/strings.xml`.
- **iOS**: Add or update entries in `iosApp/SongFlip/SongFlip/LocalizationManager.swift`.

Please ensure all string keys present in `app/src/main/res/values/strings.xml` are maintained.

---

## 📜 Pull Request Guidelines

1. **Commit Messages**: Follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat(core): add tidal album resolver`
   - `fix(android): resolve orientation glitch on tablet`
   - `docs: update translation guide`
2. **Clean Commits**: Run `git diff` before submitting to ensure no temporary logs or keys are committed.
3. **Tests**: Ensure `./gradlew testDebugUnitTest` and `:shared:allTests` pass cleanly.

---

## ⚖️ License

By contributing to SongFlip, you agree that your contributions will be licensed under the **GNU General Public License v3.0 (GPLv3)**.
