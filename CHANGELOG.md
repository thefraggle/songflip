# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
