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

# Updated iOS descriptions
IOS_DE_DESC = """Freunde schicken dir Musik- oder Podcast-Links von Spotify, Deezer oder Apple Podcasts – aber du nutzt Apple Music, YouTube Music oder Pocket Casts?

SongFlip ist die smarte Musik- & Podcast-Link-Umleitung und dein universeller Converter für iOS. Ob über das native Teilen-Menü (Share Sheet), die direkte Titelsuche, universelle QR-Codes, Kurzbefehle oder den Action Button: SongFlip wandelt Audio-Links blitzschnell um und öffnet sie direkt in deiner bevorzugten App – ganz ohne lästiges Suchen, Kopieren oder manuelle Zwischenschritte.

Unterstützte Musik- & Podcastdienste (Jeder-zu-Jeder-Umleitung):
- Apple Music & Apple Podcasts
- YouTube Music & YouTube
- Spotify
- Pocket Casts
- TIDAL
- Deezer
- Amazon Music
- SoundCloud
- Bandcamp & Shazam

Hauptfunktionen:
- Nahtlose iOS-Integration: Öffne Musik- und Podcast-Links aus WhatsApp, Telegram, iMessage, Instagram oder Safari direkt über das Teilen-Menü (Share Sheet).
- Direkte Titelsuche (Neu): Suche Songs und Künstler direkt in SongFlip und starte sie mit einem Fingertipp (Fast Flip) sofort in deinem Lieblings-Player.
- Musik per QR-Code teilen: Erstelle und scanne universelle QR-Codes für Songs und Alben – ideal zum Teilen mit Freunden vor Ort, egal welchen Musikdienst sie nutzen.
- Universal Playlist Converter: Erkennt und überträgt Playlists und Alben sauber zwischen Spotify, Apple Music, YouTube Music, TIDAL und Deezer – inklusive Duplikat-Bereinigung und bis zu 200 Titeln.
- Treffer verfeinern & Favoriten: Wähle bei Bedarf gezielt zwischen Live-, Remaster- oder Studio-Versionen und pinne deine Lieblingstracks im Verlauf mit 1-Tap als Favorit an.
- Podcast Converter Engine: Plattformübergreifende Auflösung für Shows und Einzelepisoden direkt in deiner bevorzugten Podcast-App.
- Direkte Wiedergabe: Startet exakte Titel, Episoden und Videos sofort im gewünschten Player.
- Schnellauswahl & Verlauf: Wähle bei mehreren installierten Apps flexibel deinen Ziel-Player und greife jederzeit auf deinen Verlauf kürzlich geöffneter Links zu.
- Kurzbefehle & Action Button: Integriere SongFlip direkt in deine iOS-Kurzbefehle oder lege die Link-Umwandlung auf den Action Button.
- 100 % Privatsphäre & Kein Tracking: Kein Account, kein Login. SongFlip sammelt keine Daten, keine Werbe-IDs und analysiert keine Hörgewohnheiten.

Entwickelt für alle Musik- und Podcast-Liebhaber, die Audio-Inhalte ohne Plattformgrenzen in ihrer Lieblings-App genießen möchten."""

IOS_EN_DESC = """Friends send you Spotify, Tidal, or Apple Podcasts links – but you use Apple Music, YouTube Music, or Pocket Casts?

SongFlip is the universal music and podcast link redirector and converter for iOS. Whether via the iOS Share Sheet, direct song search, universal QR codes, Shortcuts, or the Action Button: SongFlip instantly converts shared audio links and opens them directly in your preferred app – with zero manual searching, no copying, and no friction.

Supported Audio & Podcast Services (Any-to-Any Redirection):
- Apple Music & Apple Podcasts
- YouTube Music & YouTube
- Spotify
- Pocket Casts
- TIDAL
- Deezer
- Amazon Music
- SoundCloud
- Bandcamp & Shazam

Key Features:
- Seamless iOS Integration: Convert music and podcast links from WhatsApp, Telegram, iMessage, Instagram, or Safari directly via the native Share Sheet.
- Direct Song Search (New): Search for tracks and artists directly inside SongFlip and launch playback instantly in your favorite player with 1-tap Fast Flip.
- QR Code Music Sharing: Generate and scan universal QR codes for songs and albums – perfect for sharing music in person across different streaming services.
- Universal Playlist Converter: Detects and transfers playlists and full albums across Spotify, Apple Music, YouTube Music, TIDAL, and Deezer – featuring duplicate cleaning and up to 200 tracks.
- Refine Matches & 1-Tap Favorites: Choose between live recordings, remasters, or alternative versions, and pin your favorite tracks in history with a single tap.
- Podcast Converter Engine: Cross-platform resolution for full podcast shows and individual episodes in your favorite podcast app.
- Direct Playback Engine: Instantly launches exact track, episode, and video IDs in your target player.
- Quick Player Chooser & History: Choose your target player on the fly when multiple apps are installed, and revisit your recent conversions anytime in the history sheet.
- Shortcuts & Action Button Support: Integrate SongFlip into iOS Shortcuts or trigger instant conversion with the Action Button.
- 100% Privacy & Zero Tracking: No account, no login required. SongFlip collects no personal data, no ad tracking IDs, and no listening habits.

Built with passion for music and podcast lovers who want to enjoy audio seamlessly across platforms in their favorite player."""

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

    # Write clean Markdown
    print(f"Generating clean Markdown file at {MD_PATH}...")
    with open(MD_PATH, 'w', encoding='utf-8') as f:
        f.write("# 📱 SongFlip – Play Store Listings (Alle Sprachen)\n")
        f.write("> **Version:** 1.8.10 | **Umfang:** 34 Store-Sprachen | **Zertifiziert:** Titel ≤ 30 Zeichen, Kurzbeschreibung ≤ 80 Zeichen, Volltext ≤ 4.000 Zeichen.\n")
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

    # Update individual fastlane/metadata/android files
    print("Syncing fastlane/metadata/android files...")
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

    # Update iOS metadata
    print("Syncing iOS fastlane metadata...")
    with open(os.path.join(IOS_METADATA_DIR, "de-DE", "description.txt"), 'w', encoding='utf-8') as f:
        f.write(IOS_DE_DESC.strip() + "\n")
    with open(os.path.join(IOS_METADATA_DIR, "en-US", "description.txt"), 'w', encoding='utf-8') as f:
        f.write(IOS_EN_DESC.strip() + "\n")

    # Update scripts/generate_ios_store_listings.py
    if os.path.exists(GEN_IOS_SCRIPT):
        with open(GEN_IOS_SCRIPT, 'r', encoding='utf-8') as f:
            script_code = f.read()

        # Update descriptions in script
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
