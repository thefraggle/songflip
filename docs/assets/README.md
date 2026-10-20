# Store & Marketing Assets

This directory contains curated public showcase graphics and mockups for SongFlip.

## Master Source & Rendering Engine
The full localization suite (37 locales for Google Play & iOS App Store, 6 screenshots each) is generated using the local Node/Puppeteer scripts located in `distribution/open-assets/`:
- `node render_all_locales.js` (batch renders all 37 Android locales, 1080×2400)
- `node render_ios.js --all` (batch renders all 37 iOS locales in 6.7" 1290×2796 & 6.5" 1284×2778)
- Outputs are saved to `distribution/screenshots/` (Android) and `distribution/screenshots-ios/` (iOS).

## Showcase Previews (DE & EN)
- **Feature & Social OG Graphic (1200x630)**:
  - `og-image-de.png` / `og-image-en.png`
- **Store Feature Graphic (1024x500)**:
  - `feature_graphic_de.png` / `feature_graphic_en.png`
- **Android Screenshots (1080x2400)**:
  - `android_screen_1_chat_*.png`: Screen 1 – Instant 0-click redirect with incoming chat link
  - `android_screen_2_platforms_*.png`: Screen 2 – All services & preferred target player (with Search & Listen & Flip bar)
  - `android_screen_3_listen_*.png`: Screen 3 – Listen & Flip audio recognition & Offline Club Mode
  - `android_screen_4_playlists_*.png`: Screen 4 – Universal Playlist Converter (up to 200 tracks)
  - `android_screen_3_smartlinks_*.png`: Screen 5 – Smart Universal Web Links (`songflip.link/s/...`)
  - `android_screen_5_privacy_*.png`: Screen 6 – 100% Privacy promise (no account, no ads, zero tracking)
- **iOS Screenshots (1284x2778 / 1290x2796)**:
  - `ios_screen_2_playback_*.png`: Screen 1 – Instant 1-click playback
  - `ios_screen_1_platforms_*.png`: Screen 2 – Platform overview, Search, Listen & Flip & Action Button / Share Sheet
  - `ios_screen_listen_*.png`: Screen 3 – Listen & Flip audio recognition & Offline Mode
  - `ios_screen_playlists_*.png`: Screen 4 – Universal Playlist Converter (up to 200 tracks)
  - `ios_screen_3_smartlinks_*.png`: Screen 5 – Universal Web Links
  - `ios_screen_4_privacy_*.png`: Screen 6 – 100% Privacy promise
