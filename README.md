# SongFlip 🎵

**SongFlip** is a transparent, open-source Android link redirector for music links (Spotify → YouTube Music / Apple Music / Tidal / Deezer).

Inspired by [MapFlip](https://github.com/thefraggle/mapflip), SongFlip acts completely in the background: set it up once, and whenever a friend shares a Spotify link in WhatsApp, Telegram, or your browser, SongFlip instantly intercepts and converts it to open directly in your preferred music player without any visible app UI delay!

---

## ✨ Features

- **⚡ Instant & Invisible Redirect**: Intercepts `open.spotify.com` and `spotify.link` URLs transparently in the background.
- **🌐 Powered by Odesli / Song.link**: Resolves track metadata and converts incoming links to exact streaming targets via the Odesli API.
- **🎯 Configurable Target Player**: Support for YouTube Music (Google Music), Apple Music, Spotify, Tidal, and Deezer.
- **🌍 Dual Language Support**: English and German UI with an interactive in-app language switcher.
- **🧪 Interactive Test Bench**: Paste any Spotify link inside the app to test instant conversion and copy or open target links.
- **🛡️ Offline Fallback**: If network is unavailable or resolution fails, SongFlip gracefully falls back to opening the original link.

---

## 📸 Overview & Architecture

- **`RedirectActivity`**: Transparent activity (`Theme.Translucent.NoTitleBar`) configured with Android `IntentFilter`s for instant background handling.
- **`OdesliRepository`**: Asynchronous HTTP client (OkHttp + Coroutines) for querying `https://api.song.link/v1-alpha.1/links`.
- **`MainActivity`**: Modern dark glassmorphic UI built with Jetpack Compose & Material 3.

---

## 🛠️ Building & Installation

### Prerequisites
- JDK 17+
- Android SDK (API 26 or higher)

### Build Debug APK
```bash
./gradlew assembleDebug
```
The output APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 👤 Author & License

Developed with ❤️ by **Daniel Notthoff ([TheFraggle](https://github.com/thefraggle))**.

Brought to you by the creators of **[FamWake](https://famwake.de)**.

Licensed under the [MIT License](LICENSE).
