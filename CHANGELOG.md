# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.6] - 2026-08-23

### English
- **Instant Visual Feedback**: Immediate confirmation toast on incoming music links.
- **Accurate Track Matching**: Updated metadata resolution for Spotify, Apple Music & Odesli.
- **Single Track Protection**: Links with track IDs (`?i=...`, `/track/`) always open as single songs, never albums.
- **Localized Release Notes**: Multi-language Play Store update notes.

### Deutsch
- **Sofortiges Feedback**: Sofortige Toast-Meldung beim Antippen geteilter Musik-Links.
- **Präzise Song-Erkennung**: Aktualisierte Metadaten für Spotify, Apple Music & Odesli.
- **Einzelsong-Garantie**: Links mit Track-ID (`?i=...`, `/track/`) öffnen garantiert als Song, nie als Album.
- **Lokalisierte Release Notes**: Mehrsprachige Update-Hinweise im Play Store.

## [1.1.5] - 2026-08-23

### Improvements
- **Rock-Solid App Dispatching**: 3-stage launching cascade ensures your target music player always opens reliably.
- **Fail-Safe Search Routing**: Guaranteed fallback search if a track lacks direct catalog mapping, completely preventing dropped intents.
- **Cache Self-Healing**: Automatically resets corrupt or legacy test cache entries on startup.
- **Deep Share-Sheet Compatibility**: Flawless link extraction across all apps, share sheets, and clipboard sources.

## [1.1.4] - 2026-08-23

### Improvements
- **Rock-Solid Link Redirection**: Eliminated redirect loops and resolved unexpected app freezes when opening music links.
- **Enhanced Song Recognition**: Added full support for modern song and album link formats.
- **Seamless App Launch**: Directly opens your music player without unnecessary web browser detours.
- **Reliable Network Compatibility**: Consistent link conversion across all internet connections, including VPNs and private DNS.

## [1.1.3] - 2026-08-23

### Improvements
- **Simplified Settings**: Streamlined settings menu with a clean, focused design and effortless navigation.
- **Symmetric Music Grid**: Balanced and perfectly aligned service selection for a smoother look and feel.
- **Direct Link Forwarding**: Intuitive and reliable music link redirection without unnecessary toggles.

## [1.1.2] - 2026-08-23

### Improvements
- **Music Provider Logos**: Crisp service logos for Spotify, Apple Music, YouTube Music, Deezer, Tidal, and Amazon Music for instant recognition.
- **Clear Selection Highlights**: Calmer, consistent design when choosing your preferred music player.
- **Accurate Language Detection**: Automatically matches your system language right from the start.

## [1.1.1] - 2026-08-23

### Improvements
- **Instant Offline Fallback**: Shared links open immediately in your browser when offline without waiting for network timeouts.
- **Direct Deezer & Tidal Playback**: Links to Deezer and Tidal now launch directly in their apps without extra steps.
- **Smoother & Faster Performance**: Enhanced background speed and improved overall app responsiveness.

## [1.1.0] - 2026-08-23

### Improvements
- **Instant Link Opening**: Previously opened links now open in a fraction of a second without waiting for network requests.
- **Fast Track Lookup**: Significantly faster song conversion, even on slower mobile connections.
- **Direct Spotify Playback**: Links to Spotify now launch immediately inside the Spotify app without web redirects.
- **Live Song Info**: Shows the song title and artist directly when flipping into your player.
- **Android 16 Ready**: Fully updated and compliant with Android 16 (API 36).

## [1.0.5] - 2026-08-22

### Improvements
- **Android 15 (Target SDK 35)**: Full compatibility with the latest Android 15 platform standards.
- **Edge-to-Edge Experience**: Refined transparent system status bar for dark and light modes.

## [1.0.4] - 2026-08-22

### Improvements
- **Full Album Overview**: Shared album links now open the entire album with all tracks instead of single songs.
- **Instant Quick Settings Sync**: Tapping the Quick Settings tile updates the active/pause state in the app in real time.
- **Streamlined Settings**: Focused options and improved stability.

## [1.0.3] - 2026-08-22

### Improvements
- **Theme Selection**: Switch between System, Light, and Dark appearance in settings.
- **Apple Music Albums**: Complete albums from Apple Music now start playing directly in YouTube Music.
- **Clear & Clean UI**: Simplified status display, refined settings header with close button, and polished native translations.

## [1.0.2] - 2026-08-22

### Improvements
- **Seamless Updates**: App updates can now be installed directly over existing versions.
- **Accurate Status Banner**: Clearly shows whether link redirection is active, paused, or requires setup.
- **Legal Links**: Quick access to Privacy Policy, Legal Notice, and Terms of Use in settings.

## [1.0.1] - 2026-08-22

### Changed
- **Harmonized Live Status Banner**: Synchronized top status card with system domain verification state so initial setup requirements are clearly and accurately highlighted without contradictions.
- **Legal Footer & Compliance**: Added direct links to Privacy Policy, Legal Notice (Impressum), and Terms of Service in the main UI and settings drawer.
- **Dynamic Version Display**: Integrated dynamic app version badge into all UI footers.
- **CI/CD Optimization**: Automated release notes parsing in GitHub Actions to extract and attach version-specific changelog notes to release tags.

## [1.0.0] - 2026-08-22

### Added
- **Universal 6-Platform Link Resolution**: Instant, zero-click redirection between Spotify, Apple Music, YouTube Music, Tidal, Deezer, and Amazon Music.
- **Direct Instant Playback Engine**: Real-time extraction of YouTube Video IDs (`watch?v=...`) for immediate playback on YouTube Music without intermediate search pages.
- **Album & Artist Support**: Full support for complete albums/EPs (preserves full release overview) and artist discography profile links across all platforms.
- **Quick Settings Tile & Smart Pause**: Quick Settings tile (`SongFlipTileService`) with customizable pause durations (15m, 1h, until tomorrow morning 06:00, indefinitely).
- **Share Target Intent (`ACTION_SEND`)**: Intercept and resolve music links shared directly from WhatsApp, Telegram, Instagram, and Reddit with automatic text sanitization.
- **Interactive Test Studio**: In-app test bench to inspect track metadata and simulate conversions.
- **22 Languages Supported**: Full native localization in 22 languages (English, German, Spanish, French, Italian, Portuguese, Japanese, Korean, Chinese, Ukrainian, Polish, Turkish, Dutch, Arabic, Hindi, and more).
- **Domain Verification Setup Card**: Dynamic detection of missing domain links on Android 12+ (DomainVerificationManager).
- **Modern Jetpack Compose UI**: Clean dark glassmorphic interface with WCAG AA compliant contrasts and barrier-free 48dp touch targets.
- **Loop Prevention & Explicit Package Launching**: Safe intent forwarding preventing infinite re-interception loops.
- **CI/CD Pipeline**: Automated APK builds and release asset attachments on tag pushes via Forgejo and GitHub Actions.
- **Licensing**: Licensed under the GNU General Public License v3.0 (GPLv3).
