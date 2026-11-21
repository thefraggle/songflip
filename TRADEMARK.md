# SongFlip Trademark & Brand Assets Policy

**Last Updated:** November 2026  
**Owner:** Daniel Notthoff ([songflip.link](https://songflip.link) / [goork.de](https://goork.de))

While the **SongFlip** client source code is publicly available under the [GNU General Public License v3.0 (GPLv3)](LICENSE), **the GPLv3 does not grant any rights to use the SongFlip trademarks, service marks, trade names, logos, app icons, or promotional store graphics** (see GNU GPLv3, Section 7(e)).

This policy explains how you may and may not use the SongFlip brand and visual assets when forking, modifying, or redistributing the source code.

---

## 1. Protected Trademarks & Visual Assets ("Brand Assets")

The following assets are the exclusive property of **Daniel Notthoff** (*All Rights Reserved*) and are **excluded** from the GNU GPLv3 open-source license:

1. **Word Marks & Names:**
   - **"SongFlip"** and **"SongFlip PRO"**
   - The package and bundle identifiers **`de.goork.songflip`** (Android `applicationId` and iOS Bundle IDs)
   - Official domain names including **`songflip.link`**, **`songflip.de`**, and all associated subdomains (`cache.songflip.link`, `download.songflip.link`, etc.)

2. **Logos, App Icons & Promotional Graphics:**
   - The official SongFlip app icons and vector drawables (`ic_launcher*`, `AppIcon.appiconset`, `/images/favicon*`, `/images/apple-touch-icon.png`)
   - Promotional graphics, feature graphics, and store screenshots located in `docs/assets/store/`

---

## 2. Requirements for Forks, Modified Versions & Redistribution

If you fork, modify, compile, or redistribute this software (whether gratis or commercially, in source or binary form):

1. **Mandatory Rebranding:**
   - You **must remove and replace** the name **"SongFlip"** (and any confusingly similar variation such as *"SongFlip Free"*, *"SongFlip Mod"*, *"OpenSongFlip"*, or *"FlipSong"*), the official app icons, and all promotional store graphics before publishing or distributing binaries (e.g., on Google Play, Apple App Store, F-Droid, GitHub Releases, or third-party APK sites).
   - You **must change** the Android `applicationId` and iOS Bundle Identifier from `de.goork.songflip` to your own namespace.

2. **No Implied Endorsement:**
   - You may not state or imply that your fork or modified build is an official SongFlip release, or that it is endorsed, sponsored, or supported by Daniel Notthoff or Goork.

3. **No Unauthorized Use of Hosted Cloud Infrastructure:**
   - The open-source license covers the client source code only. It does **not** grant permission to use the proprietary hosted backend infrastructure (`songflip.link`, `cache.songflip.link`, `songflip-web.web.app`) for third-party forks, redistributed apps, or automated scraping. Forks distributed to end users must disable or replace calls to official SongFlip cloud endpoints with their own infrastructure.

---

## 3. Permitted Nominative Use

You may make truthful, descriptive textual references to **"SongFlip"** without prior written permission solely in the following contexts:

- Stating in your repository documentation or "About" screen that your project is *"a fork of the open-source SongFlip project"* or *"based on source code from SongFlip"*, provided that it is accompanied by a clear disclaimer that your project is unofficial and not affiliated with SongFlip.
- Editorial, journalistic, or community coverage (reviews, blog posts, tutorials) discussing the official SongFlip app.

---

## 4. Reporting & Questions

For press inquiries, permission requests, or trademark questions, please contact:  
**Daniel Notthoff** – [hey@goork.de](mailto:hey@goork.de)
