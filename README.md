# SongFlip 🎵

**SongFlip** is an automatic, zero-click music link redirector for Android (Spotify ⇄ Apple Music ⇄ YouTube Music ⇄ Tidal ⇄ Deezer ⇄ Amazon Music).

Official Website: [songflip.link](https://songflip.link)

Inspired by [MapFlip](https://github.com/thefraggle/mapflip), SongFlip runs completely in the background: set it up in 30 seconds, and whenever a friend shares a music link in WhatsApp, Telegram, Instagram, or your browser, SongFlip instantly intercepts and converts it to open directly in your preferred music player without any intermediate UI or manual searching.

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
- **💿 Full Album & Artist Recognition**: Supports single songs, complete albums/EPs, and artist discography profiles.
- **🚀 Direct Instant Playback Engine**: Extracts direct video/track IDs in the background (e.g. YouTube Music `watch?v=...`) for instant playback without search result delays.
- **🌍 22 Languages Supported**: Fully localized in 22 languages (English, German, Spanish, French, Italian, Portuguese, Japanese, Korean, Chinese, Ukrainian, Polish, Turkish, Dutch, Arabic, Hindi, and more).
- **⏸️ Quick Settings Status Tile & Smart Pause**: Pause redirection directly from Android's notification shade for 15 minutes, 1 hour, or until tomorrow morning (06:00).
- **📤 Share Sheet Target (`ACTION_SEND`)**: Supports shared text containing links from WhatsApp, Instagram, and Reddit with automatic URL sanitization.
- **🛡️ 100% Privacy & Zero Tracking**: No accounts, no logins, no advertising IDs, and no listening habits collected.
- **🧪 Interactive Test Studio**: In-app test bench to preview conversions and inspect resolved URLs.

---

## 🏗️ Architecture & Resolution Engine

SongFlip is built as a modern **Kotlin Multiplatform (KMP)** project with a modular architecture:
- **`app/`**: Native Android app (Jetpack Compose, Material 3, Quick Settings Tile, Overlay & Notification handling).
- **`iosApp/`**: Native iOS app (SwiftUI, Share Extension, App Intents for 0-click Siri Shortcuts).
- **`shared/`**: Shared KMP core engine (platform parsing, URL normalization, multi-tier resolution logic, Ktor HTTP client).
- **`functions/`**: Firebase Cloud Functions backend powering token verification and high-speed L2 link caching.

### Resolution Pipeline
1. **Tier 1 (Direct 0-Redirect SongLink Engine)**: Normalizes incoming URLs into direct internal IDs (`/s/`, `/i/`, `/d/`, `/y/`) for instant HTTP responses without redirect loops.
2. **Tier 2 (L2 Server-Side Cache)**: High-speed Firebase edge cache with 90-day TTL for instant sub-100ms conversions for Pro users.
3. **Tier 3 (Direct Playback Extractor)**: Background video ID regex extraction for YouTube Music instant play.
4. **Tier 4 (Local Metadata Fallback APIs)**: iTunes Search & Lookup API, Spotify oEmbed, Deezer Public API, and YouTube oEmbed.
5. **Tier 5 (Target Catalog Search & Custom API)**: Fallback search routing and optional user-supplied custom API key / webhook endpoint.

---

## 👑 SongFlip Pro & Open Source Philosophy

SongFlip is **100% open source (GPLv3)** and its core 0-click redirect functionality will **always remain completely free and ad-free**.

For users who want the fastest possible performance or wish to support indie development, **SongFlip Pro** (one-time lifetime purchase) provides:

- **⚡ L2 Server-Side Cache**: Lightning-fast resolution (~50–100 ms) via our dedicated server cache with zero rate-limiting.
- **🔑 Custom API Token Option**: Non-Pro power users can also bring their own free Odesli API key directly in settings.
- **👑 Lifetime Supporter Status**: Golden/Emerald PRO badge & direct support for independent open-source development.
- **🔄 Upcoming Power Features**:
  - **1:1 Playlist Transfer**: Transfer and sync full playlists across Spotify, Apple Music, and YouTube Music.
  - **Enhanced AI Matching**: High-accuracy fuzzy matching for rare live bootlegs, remixes, and acoustic versions.
  - **Universal Share Links (`songflip.link`)**: Generate multi-platform web links for friends.

---

## 🛠️ Building & Development

### Prerequisites
- JDK 17+
- Android SDK (API 26 to 36)

### Build Debug APK
```bash
./gradlew assembleDebug
```
The output APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Run Unit Tests
```bash
./gradlew test
```

---

## 📄 License

This project is licensed under the **[GNU General Public License v3.0 (GPLv3)](LICENSE)**.

- **Freedom & Copyleft**: You are free to run, study, share, and modify this software.
- **Copyleft Requirement**: Any distributed modifications or derivative works must also be licensed under the GNU GPLv3 with full source code made available.

© 2026 Daniel Notthoff ([notthoff.org](https://notthoff.org))
