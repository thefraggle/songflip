# SongFlip 🎵

**SongFlip** is an automatic, zero-click music link redirector for Android & iOS (Spotify ⇄ Apple Music ⇄ YouTube Music ⇄ Tidal ⇄ Deezer ⇄ Amazon Music).

Official Website: [songflip.link](https://songflip.link)

SongFlip runs completely in the background: set it up in 30 seconds, and whenever a friend shares a music link in WhatsApp, Telegram, Instagram, or your browser, SongFlip instantly intercepts and converts it to open directly in your preferred music player without any intermediate UI or manual searching.

---

## ✨ Key Features

- **⚡ 0-Click Background Redirect**: Intercepts music links transparently and launches direct playback in your target player.
- **🎧 Universal 6-Platform Support**: Full any-to-any redirection between:
  - 🟢 **Spotify** (`open.spotify.com`, `spotify.link`)
  - 🔴 **YouTube Music** (`music.youtube.com`, `youtu.be`)
  - 🍎 **Apple Music** (`music.apple.com`, `apple.co`, `itunes.apple.com`)
  - 🌊 **Tidal** (`tidal.com`, `listen.tidal.com`)
  - 🟣 **Deezer** (`deezer.com`, `link.deezer.com`, `deezer.page.link`)
  - 🔵 **Amazon Music** (`music.amazon.com`, `music.amazon.de`, `music.amazon.co.uk`, `amzn.to`, `a.co`)
- **💿 Full Album & Artist Recognition**: Supports single tracks, full albums/EPs, and artist channel/discography profiles (including `@handles`).
- **📋 Clipboard Smart-Banner**: 1-tap player launch and universal link copying when music links are copied on Android & iOS.
- **🍎 iOS Deep Integration**: Native Share Extension, Action Button support, and Siri App Intents (`ConvertSongIntent`).
- **🎨 Material You Themed Icon (Android 13+)**: Dynamic system tinting matching the user's wallpaper palette.
- **🔗 Universal Smart Share Links (`songflip.link/s/...`) [PRO]**: Generate clean, lightning-fast multi-platform landing pages with rich cover art & OpenGraph preview cards for WhatsApp, Telegram, iMessage & Discord. *(Try the permanent live demo: [songflip.link/s/rickroll](https://songflip.link/s/rickroll))*.
- **📜 Conversion History & Quick Sharing**: Offline history log with 1-tap replay, search filter, and instant smart-link sharing.
- **🚀 Direct Instant Playback Engine**: Extracts direct video/track IDs in the background (e.g. YouTube Music `watch?v=...`) for instant playback without search result delays.
- **🌍 24 Languages Supported**: Fully localized across 24 languages on Android and 22 languages on iOS (English, German, Spanish, French, Italian, Portuguese, Japanese, Korean, Chinese, Ukrainian, Polish, Turkish, Dutch, Arabic, Hindi, and more).
- **⏸️ Quick Settings Status Tile & Smart Pause**: Pause redirection directly from Android's notification shade for 15 minutes, 1 hour, or until tomorrow morning (06:00).
- **📤 Share Sheet Target (`ACTION_SEND`)**: Supports shared text containing links from WhatsApp, Instagram, and Reddit with automatic URL sanitization.
- **🛡️ 100% Privacy & Zero Tracking**: No accounts, no logins, no advertising IDs, and no listening habits collected.
- **🧪 Interactive Test Studio**: In-app test bench to preview conversions and inspect resolved URLs.

---

## 🏗️ Architecture & Resolution Engine

SongFlip is built as a modern **Kotlin Multiplatform (KMP)** project with a modular architecture:
- **`app/`**: Native Android app (Jetpack Compose, Material 3, Quick Settings Tile, Overlay & Notification handling).
- **`iosApp/`**: Native iOS app (SwiftUI, Share Extension, App Intents for 0-click Siri Shortcuts & Action Button).
- **`shared/`**: Shared KMP core engine (platform parsing, universal URL sanitizing, multi-tier resolution logic, Ktor HTTP client).
- **`functions/`**: Firebase Cloud Functions backend powering token verification, SSR web-share landing pages, and high-speed L2 link caching.

### Resolution Pipeline
1. **Tier 1 (Local Device Memory & SQLite Cache)**: Instant sub-5ms lookup on device for previously converted songs.
2. **Tier 2 (L2 Server-Side Cloud Cache) [PRO]**: High-speed Firebase edge cache with 90-day TTL for zero-latency (<50ms) global conversions and web-share landing page generation.
3. **Tier 3 (Direct 0-Redirect SongLink Engine)**: Normalizes incoming URLs into direct internal IDs for instant HTTP responses without redirect loops.
4. **Tier 4 (Direct Playback Extractor)**: Background video ID regex extraction for YouTube Music instant play.
5. **Tier 5 (Local & Cloud Metadata Healing)**: iTunes Search & Lookup API, Deezer Catalog API, YouTube oEmbed, and video noise sanitization.
6. **Tier 6 (Target Catalog Deep-Search Fallback)**: Intelligent 2-tier search routing (remaster/edit noise stripped) for obscure releases and regional variants.

---

## 👑 SongFlip PRO & Open Source Philosophy

SongFlip is **100% open source (GPLv3)** and its core 0-click redirect functionality will **always remain completely free and ad-free**.

For users who want the fastest possible performance or wish to support indie development, **SongFlip PRO** provides:

- **⚡ L2 Server-Side Cache**: Lightning-fast resolution (~30–50 ms) via our dedicated server cache with zero rate-limiting.
- **🔗 Universal Smart Share Links**: Generate `songflip.link/s/...` landing pages directly from the app or share sheet.
- **👑 Supporter Status**: PRO badge & direct support for independent open-source development.

---

## 🛠️ Building & Development

### Prerequisites
- **JDK 17+** (Temurin or OpenJDK)
- **Android SDK** (API 26 to 36)
- **Xcode 16+** (for building iOS on macOS)

### Build Android Debug APK
```bash
./gradlew :app:assembleDebug
```
The output APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Build iOS Shared Framework
```bash
./gradlew :shared:assembleSongFlipKitReleaseXCFramework
```

### Run Unit Tests
```bash
./gradlew :shared:allTests :app:testDebugUnitTest
```

---

## 📄 License

This project is licensed under the **[GNU General Public License v3.0 (GPLv3)](LICENSE)**.

- **Freedom & Copyleft**: You are free to run, study, share, and modify this software.
- **Copyleft Requirement**: Any distributed modifications or derivative works must also be licensed under the GNU GPLv3 with full source code made available.

© 2026 Daniel Notthoff ([notthoff.org](https://notthoff.org))
