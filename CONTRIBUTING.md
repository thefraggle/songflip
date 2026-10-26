# Contributing to SongFlip 🎵

Thank you for your interest in contributing to SongFlip! Whether you are reporting an issue, proposing new features, adding a translation, or fixing a bug, your help is warmly welcomed.

---

## 🏗️ Architecture Overview

SongFlip is built as a Kotlin Multiplatform (KMP) project:
- **`app/`**: Native Android app (Jetpack Compose, MVVM architecture, Material 3, Quick Settings Tiles, Android 13+ Themed Icons).
- **`iosApp/`**: Native iOS app (SwiftUI, Share Extension, App Intents for Siri Shortcuts & Action Button).
- **`shared/`**: Shared KMP core engine (`SongLinkEngine`, platform resolvers, `UrlUtils`, SQLDelight `LinkCache`, `AptabaseClient`).
- **`functions/`** *(Private / Closed Source)*: Proprietary hosted cloud backend (Firebase Cloud Functions by Daniel Notthoff) powering token verification, SSR web-share landing pages, playlist batch scrapers, and global L2 link caching. The client applications (Android, iOS & shared KMP core) are 100% autonomous and compile/run completely without this backend submodule.

---

## 🚀 Getting Started

### Prerequisites
- **JDK 17** (Temurin or OpenJDK)
- **Android Studio** (Koala / Ladybug or newer) with Android SDK 26–36
- **Xcode 16+** (for building the iOS target on macOS)

> 💡 **Submodule Notice**: The `functions/` folder points to a private, closed-source backend submodule. You do **not** need this submodule to build, test, or contribute to SongFlip. A standard clone (`git clone https://github.com/thefraggle/songflip.git` without `--recursive`) builds both Android and iOS targets out of the box.

### Local Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/thefraggle/songflip.git
   cd songflip
   ```

2. Optional Configuration (`local.properties`):
   Copy `local.properties.example` to `local.properties` if you wish to configure custom test keys:
   ```bash
   cp local.properties.example local.properties
   ```
   If omitted, safe defaults are used automatically.

3. Run Tests:
   ```bash
   ./gradlew test
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

SongFlip supports 34+ languages!
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
3. **Tests**: Ensure `./gradlew test` passes cleanly.

---

## ⚖️ License

By contributing to SongFlip, you agree that your contributions will be licensed under the **GNU General Public License v3.0 (GPLv3)**.
