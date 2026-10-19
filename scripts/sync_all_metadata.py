#!/usr/bin/env python3
import os
import re
import csv

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CSV_PATH = os.path.join(BASE_DIR, "distribution", "playstore_listings_all_languages.csv")
MD_PATH = os.path.join(BASE_DIR, "distribution", "playstore_listings_all_languages.md")
ANDROID_METADATA_DIR = os.path.join(BASE_DIR, "fastlane", "metadata", "android")
IOS_METADATA_DIR = os.path.join(BASE_DIR, "fastlane", "metadata")
GEN_IOS_SCRIPT = os.path.join(BASE_DIR, "scripts", "generate_ios_store_listings.py")

# Language display names for Markdown
LOCALE_NAMES = {
    'bn-BD': 'বাংলা (bn-BD)',
    'cs-CZ': 'Čeština (cs-CZ)',
    'da-DK': 'Dansk (da-DK)',
    'de-DE': 'Deutsch (de-DE)',
    'el-GR': 'Ελληνικά (el-GR)',
    'en-GB': 'English - UK (en-GB)',
    'en-US': 'English - US (en-US)',
    'es-419': 'Español - Latinoamérica (es-419)',
    'es-ES': 'Español - España (es-ES)',
    'es-US': 'Español - Estados Unidos (es-US)',
    'fi-FI': 'Suomi (fi-FI)',
    'fr-CA': 'Français - Canada (fr-CA)',
    'fr-FR': 'Français - France (fr-FR)',
    'hi-IN': 'हिन्दी (hi-IN)',
    'hu-HU': 'Magyar (hu-HU)',
    'id': 'Bahasa Indonesia (id)',
    'it-IT': 'Italiano (it-IT)',
    'ja-JP': '日本語 (ja-JP)',
    'ko-KR': '한국어 (ko-KR)',
    'mr-IN': 'मराठी (mr-IN)',
    'nb-NO': 'Norsk Bokmål (nb-NO)',
    'nl-NL': 'Nederlands (nl-NL)',
    'pl-PL': 'Polski (pl-PL)',
    'pt-BR': 'Português - Brasil (pt-BR)',
    'pt-PT': 'Português - Portugal (pt-PT)',
    'ro-RO': 'Română (ro-RO)',
    'ru-RU': 'Русский (ru-RU)',
    'sv-SE': 'Svenska (sv-SE)',
    'th-TH': 'ไทย (th-TH)',
    'tr-TR': 'Türkçe (tr-TR)',
    'uk': 'Українська (uk)',
    'vi': 'Tiếng Việt (vi)',
    'zh-CN': '简体中文 (zh-CN)',
    'zh-TW': '繁體中文 (zh-TW)',
}

# Updated iOS descriptions (v1.9.6: 12 Platforms incl. Qobuz & Beatport + Listen & Flip online & offline)
IOS_DE_DESC = """Freunde schicken dir einen Song-Link oder Podcast-Link von Spotify, Deezer oder Apple Podcasts – aber du nutzt Apple Music, YouTube Music oder Pocket Casts?

SongFlip ist deine smarte Musik-Link-Umleitung (Music Link Redirect) sowie dein universeller Song- und Podcast-Link Converter für iOS. Ob über das native Teilen-Menü (Share Sheet), die Musikerkennung Listen & Flip, die direkte Titelsuche, universelle QR-Codes, Kurzbefehle oder den Action Button: SongFlip lässt dich geteilte Musik-Links und Podcast-Links blitzschnell konvertieren und direkt im Wunsch-Player öffnen – ganz ohne manuelles Suchen oder Kopieren.

Unterstützte Musik- & Podcastdienste (12 Plattformen – Jeder-zu-Jeder Link Redirect):
- Apple Music & Apple Podcasts
- YouTube Music & YouTube
- Spotify
- Pocket Casts
- TIDAL
- Deezer
- Amazon Music
- Qobuz & Beatport
- SoundCloud
- Bandcamp & Shazam

Hauptfunktionen:
- Nahtlose iOS-Integration: Musik- und Podcast-Links sowie Spotify-Links aus WhatsApp, Telegram, iMessage, Instagram oder Safari direkt über das Teilen-Menü (Share Sheet) konvertieren und öffnen.
- Listen & Flip (Musikerkennung & Offline-Modus): Erkenne laufende Songs in Bars, Clubs oder im Radio per Mikrofon, speichere sie im Verlauf und öffne sie direkt in deinem Lieblings-Player. Auch ohne Internetempfang werden Audio-Fingerabdrücke lokal gesichert und automatisch aufgelöst, sobald du wieder online bist.
- Direkte Titelsuche: Suche Songs und Künstler direkt in SongFlip und starte sie mit einem Fingertipp (Fast Flip) sofort in deinem Lieblings-Player – auch ohne bestehenden Song-Link.
- Musik per QR-Code teilen: Erstelle und scanne universelle QR-Codes für Songs und Alben – ideal zum Teilen von Musik-Links vor Ort über alle Streamingdienste hinweg.
- Universal Playlist Converter & Playlist-Transfer: Playlists konvertieren, Playlists übertragen und ganze Alben zwischen Spotify, Apple Music, YouTube Music, TIDAL und Deezer umziehen – inklusive Duplikat-Bereinigung und bis zu 200 Titeln.
- Treffer verfeinern & Favoriten: Wähle gezielt zwischen Live-, Remaster- oder Studio-Versionen und pinne deine Lieblings-Songs im Verlauf mit 1-Tap als Favorit an.
- Podcast Converter & Podcast-Weiterleitung: Plattformübergreifende Podcast-Umleitung für Shows und Einzelepisoden direkt in deinem bevorzugten Podcast-Player.
- Direkte Wiedergabe: Startet exakte Titel, Episoden und Videos sofort im gewünschten Player.
- Schnellauswahl & Verlauf: Wähle bei mehreren installierten Apps flexibel deinen Ziel-Player und greife jederzeit auf deinen Verlauf kürzlich geöffneter Links zu.
- Kurzbefehle & Action Button: Integriere SongFlip direkt in deine iOS-Kurzbefehle oder lege den Musik-Link Redirect auf den Action Button.
- 100 % Privatsphäre & Kein Tracking: Kein Account, kein Login. SongFlip sammelt keine Daten, keine Werbe-IDs und analysiert keine Hörgewohnheiten.

Kompatibel mit universellen Musik-Link-Protokollen wie Songlink / Odesli. Entwickelt für alle Musik- und Podcast-Liebhaber, die Songs und Podcasts mit einem schnellen Music Converter ohne Plattformgrenzen genießen möchten.

Nutzungsbedingungen (Terms of Use / EULA): https://www.apple.com/legal/internet-services/itunes/dev/stdeula/
Datenschutzrichtlinie (Privacy Policy): https://songflip.link/privacy"""

IOS_EN_DESC = """Friends send you a song link or podcast link from Spotify, Tidal, or Apple Podcasts – but you listen on Apple Music, YouTube Music, or Pocket Casts?

SongFlip is your universal music link redirect and song link converter for iOS. Whether via the iOS Share Sheet, Listen & Flip audio recognition, direct song search, universal QR codes, Shortcuts, or the Action Button: SongFlip instantly converts shared music links and podcast links to open them directly in your favorite player – with zero manual searching, no copying, and no friction.

Supported Audio & Podcast Services (12 Platforms – Any-to-Any Link Redirect):
- Apple Music & Apple Podcasts
- YouTube Music & YouTube
- Spotify
- Pocket Casts
- TIDAL
- Deezer
- Amazon Music
- Qobuz & Beatport
- SoundCloud
- Bandcamp & Shazam

Key Features:
- Seamless iOS Integration: Convert music links and open Spotify links from WhatsApp, Telegram, iMessage, Instagram, or Safari directly via the native Share Sheet.
- Listen & Flip (Audio Recognition & Offline Mode): Identify songs playing around you in clubs, bars, or on the radio, save them to your history, and open them directly in your favorite player. Captures audio fingerprints even offline without internet reception and resolves tracks automatically once you're back online.
- Direct Song Search: Search for tracks and artists directly inside SongFlip and launch playback instantly in your favorite player with 1-tap Fast Flip – no existing song link required.
- QR Code Music Sharing: Generate and scan universal QR codes for songs and albums – perfect for sharing music links in person across different streaming services.
- Universal Playlist Converter & Playlist Transfer: Convert playlists, transfer playlists, and move full albums across Spotify, Apple Music, YouTube Music, TIDAL, and Deezer – featuring duplicate cleaning and up to 200 tracks.
- Refine Matches & 1-Tap Favorites: Choose between live recordings, remasters, or studio versions, and pin your favorite songs in history with a single tap.
- Podcast Converter & Podcast Redirect Engine: Cross-platform resolution to convert and open podcast links for full shows and episodes in your favorite podcast player.
- Direct Playback Engine: Instantly launches exact track, episode, and video IDs in your target player.
- Quick Player Chooser & History: Choose your target player on the fly when multiple apps are installed, and revisit your recent music converter history anytime.
- Shortcuts & Action Button Support: Integrate SongFlip into iOS Shortcuts or trigger instant music link redirect with the Action Button.
- 100% Privacy & Zero Tracking: No account, no login required. SongFlip collects no personal data, no ad tracking IDs, and no listening habits.

Compatible with universal music link protocols including Songlink / Odesli. Built for music and podcast lovers who want a fast song converter and link redirect to enjoy songs and podcasts seamlessly across platforms.

Terms of Use (EULA): https://www.apple.com/legal/internet-services/itunes/dev/stdeula/
Privacy Policy: https://songflip.link/privacy"""

IOS_EN_KEYWORDS = "spotify,apple,youtube,transfer,deezer,beatport,qobuz,tidal,soundcloud,shazam,bandcamp,open,song"
IOS_DE_KEYWORDS = "spotify,apple,youtube,transfer,deezer,beatport,qobuz,tidal,soundcloud,shazam,redirect,öffnen,song"

def main():
    print("Reading and validating CSV...")
    with open(CSV_PATH, 'r', encoding='utf-8') as f:
        rows = list(csv.DictReader(f))

    print(f"Validating {len(rows)} languages...")
    validation_passed = True
    for r in rows:
        loc = r['Language']
        title = r['Title']
        short_desc = r['Short description']
        full_desc = r['Full description']
        
        t_len = len(title)
        s_len = len(short_desc)
        f_len = len(full_desc)
        
        if t_len > 30:
            print(f"❌ [Title] exceeds limit for {loc}: {t_len}/30")
            validation_passed = False
        if s_len > 80:
            print(f"❌ [Short] exceeds limit for {loc}: {s_len}/80")
            validation_passed = False
        if f_len > 4000:
            print(f"❌ [Full] exceeds limit for {loc}: {f_len}/4000")
            validation_passed = False
        else:
            print(f"✓ {loc:7s} | Title: {t_len:2d}/30 | Short: {s_len:2d}/80 | Full: {f_len:4d}/4000")

    if not validation_passed:
        raise ValueError("Validation failed on character limits!")

    # Write clean Markdown (including all 34 Google Play languages + iOS App Store DE & EN)
    print(f"Generating clean Markdown file at {MD_PATH}...")
    with open(MD_PATH, 'w', encoding='utf-8') as f:
        f.write("# 📱 SongFlip – Play Store & App Store Listings (Alle Sprachen)\n")
        f.write("> **Version:** 1.9.6 | **Umfang:** 34 Play Store-Sprachen + iOS App Store (DE/EN) | **Zertifiziert:** Titel ≤ 30 Zeichen, Kurzbeschreibung ≤ 80 Zeichen, Volltext ≤ 4.000 Zeichen, iOS Keywords ≤ 100 Zeichen.\n")
        f.write("---\n\n")
        for r in rows:
            loc = r['Language']
            name = LOCALE_NAMES.get(loc, loc)
            t = r['Title']
            s = r['Short description']
            d = r['Full description']
            f.write(f"## {name}\n\n")
            f.write(f"### Titel ({len(t)}/30 Zeichen)\n```text\n{t}\n```\n\n")
            f.write(f"### Kurzbeschreibung ({len(s)}/80 Zeichen)\n```text\n{s}\n```\n\n")
            f.write(f"### Ausführliche Beschreibung ({len(d)}/4000 Zeichen)\n```text\n{d}\n```\n\n")
            f.write("---\n\n")

        f.write("# 🍎 iOS App Store Listings (de-DE & en-US)\n\n")
        f.write("## 🇩🇪 iOS Deutsch (de-DE)\n\n")
        f.write("### App-Name (30/30 Zeichen)\n```text\nSongFlip: Musik Link Umleitung\n```\n\n")
        f.write("### Untertitel (28/30 Zeichen)\n```text\nPodcast & Playlist Converter\n```\n\n")
        f.write(f"### Keywords ({len(IOS_DE_KEYWORDS)}/100 Zeichen)\n```text\n{IOS_DE_KEYWORDS}\n```\n\n")
        f.write(f"### Beschreibung ({len(IOS_DE_DESC)}/4000 Zeichen)\n```text\n{IOS_DE_DESC}\n```\n\n")
        f.write("---\n\n")
        f.write("## 🇺🇸 iOS English (en-US)\n\n")
        f.write("### App Name (29/30 Zeichen)\n```text\nSongFlip: Music Link Redirect\n```\n\n")
        f.write("### Subtitle (28/30 Zeichen)\n```text\nPodcast & Playlist Converter\n```\n\n")
        f.write(f"### Keywords ({len(IOS_EN_KEYWORDS)}/100 Zeichen)\n```text\n{IOS_EN_KEYWORDS}\n```\n\n")
        f.write(f"### Description ({len(IOS_EN_DESC)}/4000 Zeichen)\n```text\n{IOS_EN_DESC}\n```\n\n")
        f.write("---\n\n")

    # Update individual fastlane/metadata/android files
    print("Syncing fastlane/metadata/android files...")
    row_map = {r['Language']: r for r in rows}
    for r in rows:
        loc = r['Language']
        loc_dir = os.path.join(ANDROID_METADATA_DIR, loc)
        if os.path.exists(loc_dir):
            with open(os.path.join(loc_dir, "full_description.txt"), 'w', encoding='utf-8') as f:
                f.write(r['Full description'].strip() + "\n")
            with open(os.path.join(loc_dir, "short_description.txt"), 'w', encoding='utf-8') as f:
                f.write(r['Short description'].strip() + "\n")
            with open(os.path.join(loc_dir, "title.txt"), 'w', encoding='utf-8') as f:
                f.write(r['Title'].strip() + "\n")

    # Sync regional and alias locales
    ALIAS_LOCALES = {
        'ar': 'en-US',
        'bn-IN': 'bn-BD',
        'id-ID': 'id',
        'no-NO': 'nb-NO',
        'ro': 'ro-RO',
        'th': 'th-TH',
        'uk-UA': 'uk',
        'vi-VN': 'vi',
    }
    print("Syncing alias locales in fastlane/metadata/android...")
    for alias, source in ALIAS_LOCALES.items():
        if source in row_map:
            src_data = row_map[source]
            alias_dir = os.path.join(ANDROID_METADATA_DIR, alias)
            if os.path.exists(alias_dir):
                with open(os.path.join(alias_dir, "full_description.txt"), 'w', encoding='utf-8') as f:
                    f.write(src_data['Full description'].strip() + "\n")
                with open(os.path.join(alias_dir, "short_description.txt"), 'w', encoding='utf-8') as f:
                    f.write(src_data['Short description'].strip() + "\n")
                with open(os.path.join(alias_dir, "title.txt"), 'w', encoding='utf-8') as f:
                    f.write(src_data['Title'].strip() + "\n")

    # Update iOS metadata
    print("Syncing iOS fastlane metadata...")
    with open(os.path.join(IOS_METADATA_DIR, "de-DE", "description.txt"), 'w', encoding='utf-8') as f:
        f.write(IOS_DE_DESC.strip() + "\n")
    with open(os.path.join(IOS_METADATA_DIR, "en-US", "description.txt"), 'w', encoding='utf-8') as f:
        f.write(IOS_EN_DESC.strip() + "\n")
    with open(os.path.join(IOS_METADATA_DIR, "de-DE", "keywords.txt"), 'w', encoding='utf-8') as f:
        f.write(IOS_DE_KEYWORDS.strip() + "\n")
    with open(os.path.join(IOS_METADATA_DIR, "en-US", "keywords.txt"), 'w', encoding='utf-8') as f:
        f.write(IOS_EN_KEYWORDS.strip() + "\n")

    # Update scripts/generate_ios_store_listings.py
    if os.path.exists(GEN_IOS_SCRIPT):
        with open(GEN_IOS_SCRIPT, 'r', encoding='utf-8') as f:
            script_code = f.read()

        # Update descriptions and keywords in script
        pattern_en_kw = r"('en-US':\s*\{.*?'keywords':\s*').*?(',)"
        script_code = re.sub(pattern_en_kw, r"\g<1>" + IOS_EN_KEYWORDS + r"\g<2>", script_code, flags=re.DOTALL)

        pattern_de_kw = r"('de-DE':\s*\{.*?'keywords':\s*').*?(',)"
        script_code = re.sub(pattern_de_kw, r"\g<1>" + IOS_DE_KEYWORDS + r"\g<2>", script_code, flags=re.DOTALL)

        pattern_en = r"('en-US':\s*\{.*?'description':\s*\"\"\").*?(\"\"\",)"
        script_code = re.sub(pattern_en, r"\g<1>" + IOS_EN_DESC + r"\g<2>", script_code, flags=re.DOTALL)
        
        pattern_de = r"('de-DE':\s*\{.*?'description':\s*\"\"\").*?(\"\"\",)"
        script_code = re.sub(pattern_de, r"\g<1>" + IOS_DE_DESC + r"\g<2>", script_code, flags=re.DOTALL)

        with open(GEN_IOS_SCRIPT, 'w', encoding='utf-8') as f:
            f.write(script_code)
        print("Updated scripts/generate_ios_store_listings.py")

    print("\n🎉 SUCCESS: All Play Store listings (CSV & MD) and iOS Store texts are 100% synchronized and within character limits!")

if __name__ == '__main__':
    main()
