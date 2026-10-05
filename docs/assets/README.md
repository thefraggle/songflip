# Store & Marketing Assets

This directory contains curated public showcase graphics and mockups for SongFlip.

## Master Source & Rendering Engine
The full localization suite (34 languages for Google Play & iOS App Store) is generated using the local Node/Puppeteer server located in `distribution/open-assets/`:
- `npm run dev` (starts the interactive asset editor preview server)
- `npm run render:all` (batch renders screenshots across all 34 locales)
- Outputs are saved to `distribution/screenshots/` (Android) and `distribution/screenshots-ios/` (iOS).

## Showcase Previews (DE & EN)
- **Feature Graphic (1024x500)**:
  - `feature_graphic_de.png` / `feature_graphic_en.png`
- **Android Screenshots (1080x2400)**:
  - `android_screen_1_chat_*.png`: Screen 1 – Instant 0-click redirect with incoming chat link
  - `android_screen_2_platforms_*.png`: Screen 2 – Multi-service support (all platforms, podcasts & songs)
  - `android_screen_3_smartlinks_*.png`: Screen 3 – Smart Universal Web Links (`songflip.link/s/...`)
  - `android_screen_4_playlists_*.png`: Screen 4 – Universal Playlist Converter
  - `android_screen_5_privacy_*.png`: Screen 5 – 100% Privacy promise (no account, no ads, zero tracking)
- **iOS Screenshots**:
  - `ios_screen_1_platforms_*.png`: Screen 1 – Platform overview & Action Button / Share Sheet
  - `ios_screen_2_playback_*.png`: Screen 2 – Instant zero-click playback
  - `ios_screen_3_smartlinks_*.png`: Screen 3 – Universal Web Links
  - `ios_screen_4_privacy_*.png`: Screen 4 – 100% Privacy promise
