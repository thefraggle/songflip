# SongFlip Browser Extension (Manifest V3)

Official open-source browser extension (GPLv3) for **Chrome, Brave, Edge, Vivaldi, Opera, Arc**, and **Firefox** — live on the [Chrome Web Store](https://chromewebstore.google.com/detail/nloohflhncnpknknfnmehflaeoihpkmg).

## Features
- **Instant Active-Tab Conversion:** Click the toolbar icon on any Spotify, Apple Music, YouTube Music, Tidal, Deezer, Qobuz, SoundCloud, Bandcamp, Beatport, Audiomack, Idagio, Anghami, Last.fm, or Podcast page to open it in your preferred player.
- **Right-Click Context Menu:** Right-click any music link on Reddit, Discord Web, or anywhere on the web to open it in your target player or open a universal Flippage (`https://songflip.link/s/...`).
- **Desktop App or Webplayer:** Optionally convert resolved links to native desktop URIs (`spotify:`, `deezer://`, `tidal://`, `music://`, `qobuzapp://`, `amznmp3://`).
- **Optional Auto-Redirect:** Automatically redirect incoming music links to your chosen player (requests streaming host permissions only when toggled on).
- **Playlist Handoff:** Detects playlist links and provides a QR code + store links to convert playlists in the SongFlip mobile app.

## Load Locally (Development)
- **Chrome / Brave / Edge:** Open `chrome://extensions`, enable **Developer mode**, click **Load unpacked**, and select the `extension/` folder.
- **Firefox:** Open `about:debugging#/runtime/this-firefox`, click **Load Temporary Add-on...**, and select `extension/manifest.firefox.json` (or build the packaged ZIP below).

## Build & Test
```bash
node --test extension/test/extension.test.mjs
python3 scripts/build_extension.py
```
Packaged ZIPs are written to `extension/dist/` (gitignored):
- `extension/dist/songflip-chrome-v1.0.1.zip`
- `extension/dist/songflip-firefox-v1.0.1.zip`
