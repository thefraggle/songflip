# Chrome Web Store & Firefox AMO Listing — SongFlip

> Last Updated: 2026-11-27

## Store Listing

**Extension Name** [REQUIRED]
SongFlip: Music Link Redirector & Converter

**Short Description** [REQUIRED]
Open shared music, album, and podcast links in your preferred streaming player or copy a universal share link in one click.

**Detailed Description** [REQUIRED]
SongFlip converts shared music, album, and podcast links between streaming services in one click—free, open-source (GPLv3), and without user accounts or tracking.

Key Features:
- Active-Tab Detection: Convert the music link from your current browser tab automatically when opening the SongFlip popup.
- Right-Click Context Menu: Right-click any supported music or podcast link on social media, chat apps, or anywhere on the web to open it directly in your preferred streaming player or open its universal Flippage (songflip.link/s/...).
- Desktop App or Web Player: Optionally switch converted links to launch straight in your installed desktop music application instead of a browser tab.
- 0-Click Auto-Redirect: Optionally redirect newly opened music streaming links automatically to your chosen default player.
- Universal Share Links: Copy a clean Flippage URL (songflip.link) so friends can open the track or album on their own preferred service.
- Local Recent History: Access your last 5 converted tracks locally in the popup for quick re-opening or sharing.
- Mobile Playlist Handoff: Detect playlist URLs automatically and jump to the SongFlip mobile app via QR code for zero-login playlist transfers.

How to Use:
1. Click the SongFlip icon in your browser toolbar while viewing a supported music page, or paste any music link into the popup.
2. Choose your preferred target player (and optionally enable "Open in Desktop App" or "0-Click Auto-Redirect").
3. Click "Open" to play the release in your chosen service, or click "Copy Flippage" to share a universal link.

Privacy & Open Source:
SongFlip requires no account, contains no ads, and does not track your listening habits or browsing history.
Full source code is publicly available under GPLv3 at https://github.com/thefraggle/songflip

Support & Feedback:
https://songflip.link | https://github.com/thefraggle/songflip/issues | https://www.reddit.com/r/SongFlip

**Category** [REQUIRED]
Productivity

**Single Purpose** [REQUIRED]
Converts and redirects music, album, and podcast links between streaming platforms into the user's chosen music player or a universal SongFlip share page.

**Primary Language** [REQUIRED]
English

---

## Graphics & Assets

| Asset | Dimensions | Status | Filename |
|-------|-----------|--------|----------|
| Store Icon [REQUIRED] | 128×128 PNG | ✅ Ready | `extension/icons/icon-128.png` |
| Screenshot 1 [REQUIRED] | 1280×800 PNG (no alpha) | ✅ Ready | `extension/dist/screenshots/01-instant-track-conversion.png` |
| Screenshot 2 [RECOMMENDED] | 1280×800 PNG (no alpha) | ✅ Ready | `extension/dist/screenshots/02-11-target-players.png` |
| Screenshot 3 [RECOMMENDED] | 1280×800 PNG (no alpha) | ✅ Ready | `extension/dist/screenshots/03-context-menu-flip.png` |
| Screenshot 4 [RECOMMENDED] | 1280×800 PNG (no alpha) | ✅ Ready | `extension/dist/screenshots/04-mobile-app-playlist-qr.png` |
| Small Promo Tile [RECOMMENDED] | 440×280 PNG (no alpha) | ✅ Ready | `extension/dist/screenshots/promo-small-440x280.png` |
| Marquee Promo Tile | 1400×560 PNG (no alpha) | ✅ Ready | `extension/dist/screenshots/promo-marquee-1400x560.png` |

---

## Permissions Justification

| Permission | Type | Justification |
|------------|------|---------------|
| `storage` | permissions | Stores the user's preferred target music player, Desktop App / Auto-Redirect preferences, and the last 5 converted tracks locally on the device (`chrome.storage.local`). |
| `activeTab` | permissions | Reads the URL of the currently active tab only when the user explicitly clicks the SongFlip toolbar icon or uses a context menu action, so the user does not have to manually copy and paste the music URL. |
| `contextMenus` | permissions | Adds two right-click menu entries ("Open in [Player] (SongFlip)" and "Open Universal Flippage") for links, selected text, and music pages. |
| `alarms` | permissions | Clears the temporary toolbar badge indicator ("✓", "ERR", "APP") after ~3 seconds without keeping the background worker alive continuously. |
| `https://cache.songflip.link/*` | host_permissions | Sends the music URL selected or pasted by the user to SongFlip's public resolution API (`https://cache.songflip.link/resolve`) to look up matching track/album/podcast links across streaming platforms. |
| Streaming platform origins | optional_host_permissions | Requested only at runtime if the user explicitly enables the "Auto-Redirect Tabs" toggle, allowing automatic detection and redirection when opening music links from other streaming services. |

---

## Privacy & Data Use

### Data Collection

**Does the extension collect user data?** Yes (only user-submitted music URLs sent to `https://cache.songflip.link/resolve` on explicit user action or when Auto-Redirect is enabled; no PII, no browsing history collection, no analytics).

| Data Type | Collected? | Transmitted Off-Device? | Purpose | Shared with Third Parties? |
|-----------|-----------|------------------------|---------|---------------------------|
| Personally identifiable info | No | No | — | No |
| Health info | No | No | — | No |
| Financial info | No | No | — | No |
| Authentication info | No | No | — | No |
| Personal communications | No | No | — | No |
| Location | No | No | — | No |
| Web history | No | No | — | No |
| User activity | No | No | — | No |
| Website content (Music URLs only) | Yes (user-triggered) | Yes (to `cache.songflip.link/resolve`) | Resolves the requested music link to equivalent links on other streaming services. | No (only cached track metadata) |

### Data Use Certification
- [x] Data is NOT sold to third parties
- [x] Data is NOT used for purposes unrelated to the extension's core functionality
- [x] Data is NOT used for creditworthiness or lending purposes

---

## Privacy Policy

**Privacy Policy URL** [REQUIRED]
https://songflip.link/privacy-policy.html

---

## Distribution

**Visibility**: Public
**Regions**: All regions

## Developer Info

**Publisher Name**: Daniel Notthoff / Goork
**Contact Email**: (Use verified Chrome Web Store developer email)
**Support URL**: https://github.com/thefraggle/songflip/issues
**Homepage URL**: https://songflip.link

---

## Firefox Add-ons (AMO) Notes
- Build package: `python3 scripts/build_extension.py` -> `extension/dist/songflip-firefox-v1.0.1.zip`
- Add-on ID: `extension@songflip.link` (`strict_min_version: 140.0`, `data_collection_permissions: { required: ["none"] }`, supports `optional_host_permissions` in MV3).
- No minification or obfuscation is used; reviewers can inspect `background.js`, `popup.js`, and `shared.js` directly inside the XPI/ZIP package.

---

## Version History

| Version | Date | Changes | Status |
|---------|------|---------|--------|
| 1.0.1 | 2026-11-27 | Fix Chrome Web Store Yellow Argon keyword-spam rejection by removing third-party brand enumerations from manifest and store description. | Draft |
| 1.0.0 | 2026-11-27 | Initial release: active-tab conversion, context menu actions, optional Desktop App URIs, optional Auto-Redirect, Flippage copy, and mobile app QR funnel for playlists. | Rejected (`Yellow Argon`) |
