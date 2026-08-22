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

SongFlip uses a robust 5-tier resolution pipeline:

1. **Tier 1 (Direct 0-Redirect SongLink Engine)**: Normalizes incoming URLs into direct internal IDs (`/s/`, `/i/`, `/d/`, `/y/`) for instant HTTP 200 responses (~150 ms) without redirect loops.
2. **Tier 2 (Direct Playback Extractor)**: Background video ID regex extraction for YouTube Music instant play.
3. **Tier 3 (Local Metadata Fallback APIs)**: iTunes Search & Lookup API, Spotify oEmbed, Deezer Public API, and YouTube oEmbed.
4. **Tier 4 (Target Catalog Search Fallback)**: Fallback search routing for obscure releases and regional variants.
5. **Tier 5 (Custom AI / Webhook API)**: User-configurable endpoint (e.g. n8n, self-hosted webhook) for custom resolution.

---

## 🔮 Roadmap (SongFlip Pro)

While the core 0-click redirect functionality will **always remain 100% free and ad-free**, advanced power features are planned as a one-time lifetime purchase (4.99 € Lifetime):

- **👑 Lifetime Pro Supporter Status**: Golden/Emerald PRO badge & support indie development.
- **🔄 1:1 Cross-Platform Playlist Transfer**: Transfer and sync full playlists between Spotify, Apple Music, and YouTube Music.
- **✨ Enhanced AI Fuzzy Matching**: Powered by Gemini 2.0 Flash for rare live bootlegs, remixes, and acoustic versions.
- **🔗 Universal Share Links (`songflip.link/s/...`)**: Generate custom shareable multi-platform links for friends.
- **📜 Flip History & Library**: Local history of all flipped songs and albums.

---

## 🛠️ Building & Development

### Prerequisites
- JDK 17+
- Android SDK (API 26 to 35)

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
