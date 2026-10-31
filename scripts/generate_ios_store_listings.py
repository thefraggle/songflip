#!/usr/bin/env python3
import os
import re
import sys

BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
IOS_METADATA_DIR = os.path.join(BASE_DIR, 'fastlane', 'metadata')

# Master iOS Store Listings (EN & DE, tailored for iOS: Share Sheet, Shortcuts, Action Button, Privacy, No PRO, NO EMOJIS)
IOS_LISTINGS = {
    'en-US': {
        'name': 'SongFlip: Music Link Redirect',
        'subtitle': 'Podcast & Playlist Converter',
        'promotional_text': 'Friends send Spotify or Apple Podcasts links, but you use YouTube Music or Pocket Casts? SongFlip instantly converts and opens shared audio links in your favorite player.',
        'keywords': 'spotify,apple,youtube,transfer,deezer,beatport,qobuz,tidal,soundcloud,shazam,bandcamp,amazon,song',
        'description': """Friends send you a song link or podcast link from Spotify, Tidal, or Apple Podcasts – but you listen on Apple Music, YouTube Music, or Pocket Casts?

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
- Listen & Flip (Audio Recognition & Offline Mode): Identify songs playing around you in clubs, bars, or on the radio with instant haptic confirmation, save them to your history, and open them directly in your favorite player. Captures audio fingerprints even offline without internet reception and resolves tracks automatically once you're back online.
- Direct Song Search: Search for tracks and artists directly inside SongFlip and launch playback instantly in your favorite player with 1-tap Fast Flip – no existing song link required.
- QR Code Music Sharing: Generate and scan universal QR codes for songs and albums – perfect for sharing music links in person across different streaming services.
- Universal Playlist Converter, File Import (M3U) & Playlist Transfer: Convert playlists, import local .m3u, .m3u8, .csv, and plain text files, and transfer playlists across Spotify, Apple Music, YouTube Music, TIDAL, and Deezer – featuring duplicate cleaning and up to 200 tracks.
- Audiophile Quality (Hi-Res & Lossless) & Favorites: Prioritize studio masters, 24-bit Hi-Res, Lossless, and Dolby Atmos over radio edits and compilations. Choose between live, remaster, or studio versions and pin favorite songs in history with a single tap.
- Podcast Converter & Podcast Redirect Engine: Cross-platform resolution to convert and open podcast links for full shows and episodes in your favorite podcast player.
- Direct Playback Engine: Instantly launches exact track, episode, and video IDs in your target player.
- Quick Player Chooser & History: Choose your target player on the fly when multiple apps are installed, and revisit your recent music converter history anytime.
- Shortcuts & Action Button Support: Integrate SongFlip into iOS Shortcuts or trigger instant music link redirect with the Action Button.
- 100% Privacy & Zero Tracking: No account, no login required. SongFlip collects no personal data, no ad tracking IDs, and no listening habits.

Compatible with universal music link protocols including Songlink / Odesli. Built for music and podcast lovers who want a fast song converter and link redirect to enjoy songs and podcasts seamlessly across platforms.

Terms of Use (EULA): https://www.apple.com/legal/internet-services/itunes/dev/stdeula/
Privacy Policy: https://songflip.link/privacy""",
        'support_url': 'https://songflip.link',
        'marketing_url': 'https://songflip.link',
        'privacy_url': 'https://songflip.link/privacy-policy.html',
    },
    'de-DE': {
        'name': 'SongFlip: Musik Link Umleitung',
        'subtitle': 'Podcast & Playlist Converter',
        'promotional_text': 'Freunde schicken Spotify- oder Apple Podcasts-Links, aber du nutzt YouTube Music oder Pocket Casts? SongFlip leitet Audio-Links blitzschnell in deinen Wunsch-Player um.',
        'keywords': 'spotify,apple,youtube,transfer,deezer,beatport,qobuz,tidal,soundcloud,shazam,redirect,amazon,song',
        'description': """Freunde schicken dir einen Song-Link oder Podcast-Link von Spotify, Deezer oder Apple Podcasts – aber du nutzt Apple Music, YouTube Music oder Pocket Casts?

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
- Listen & Flip (Musikerkennung & Offline-Modus): Erkenne laufende Songs in Bars, Clubs oder im Radio per Mikrofon mit haptischem Feedback, speichere sie im Verlauf und öffne sie direkt in deinem Lieblings-Player. Auch ohne Internetempfang werden Audio-Fingerabdrücke lokal gesichert und automatisch aufgelöst, sobald du wieder online bist.
- Direkte Titelsuche: Suche Songs und Künstler direkt in SongFlip und starte sie mit einem Fingertipp (Fast Flip) sofort in deinem Lieblings-Player – auch ohne bestehenden Song-Link.
- Musik per QR-Code teilen: Erstelle und scanne universelle QR-Codes für Songs und Alben – ideal zum Teilen von Musik-Links vor Ort über alle Streamingdienste hinweg.
- Universal Playlist Converter, Datei-Import (M3U) & Playlist-Transfer: Playlists konvertieren, lokale .m3u-, .m3u8-, .csv- und Textdateien importieren und Playlists zwischen Spotify, Apple Music, YouTube Music, TIDAL und Deezer umziehen – inklusive Duplikat-Bereinigung und bis zu 200 Titeln.
- Audiophile Qualität (Hi-Res & Lossless) & Favoriten: Bevorzuge Studio-Originale, 24-Bit Hi-Res, Lossless & Dolby Atmos statt Radio-Edits und Compilations. Wähle gezielt zwischen Live-, Remaster- oder Studio-Versionen und pinne Lieblings-Songs mit 1-Tap als Favorit an.
- Podcast Converter & Podcast-Weiterleitung: Plattformübergreifende Podcast-Umleitung für Shows und Einzelepisoden direkt in deinem bevorzugten Podcast-Player.
- Direkte Wiedergabe: Startet exakte Titel, Episoden und Videos sofort im gewünschten Player.
- Schnellauswahl & Verlauf: Wähle bei mehreren installierten Apps flexibel deinen Ziel-Player und greife jederzeit auf deinen Verlauf kürzlich geöffneter Links zu.
- Kurzbefehle & Action Button: Integriere SongFlip direkt in deine iOS-Kurzbefehle oder lege den Musik-Link Redirect auf den Action Button.
- 100 % Privatsphäre & Kein Tracking: Kein Account, kein Login. SongFlip sammelt keine Daten, keine Werbe-IDs und analysiert keine Hörgewohnheiten.

Kompatibel mit universellen Musik-Link-Protokollen wie Songlink / Odesli. Entwickelt für alle Musik- und Podcast-Liebhaber, die Songs und Podcasts mit einem schnellen Music Converter ohne Plattformgrenzen genießen möchten.

Nutzungsbedingungen (Terms of Use / EULA): https://www.apple.com/legal/internet-services/itunes/dev/stdeula/
Datenschutzrichtlinie (Privacy Policy): https://songflip.link/privacy""",
        'support_url': 'https://songflip.link',
        'marketing_url': 'https://songflip.link',
        'privacy_url': 'https://songflip.link/privacy-policy.html',
    }
}

LIMITS = {
    'name': 30,
    'subtitle': 30,
    'promotional_text': 170,
    'keywords': 100,
    'description': 4000,
    'release_notes': 4000,
}

def extract_changelog_for_version(version=None):
    changelog_path = os.path.join(BASE_DIR, "CHANGELOG.md")
    if not os.path.exists(changelog_path):
        return "Performance improvements and bug fixes."

    content = open(changelog_path, "r", encoding="utf-8").read()

    if version:
        clean_v = version.lstrip('v').strip()
        pattern = r'## \[' + re.escape(clean_v) + r'\].*?\n(.*?)(?=\n## \[|\Z)'
        match = re.search(pattern, content, re.DOTALL)
        if match:
            return clean_markdown_notes(match.group(1).strip())

    match = re.search(r"## \[.*?\][^\n]*\n(.*?)(?=\n## \[|\Z)", content, re.DOTALL)
    if match:
        return clean_markdown_notes(match.group(1).strip())

    return "Performance improvements and bug fixes."

def clean_markdown_notes(body):
    lines = body.split('\n')
    cleaned = []
    for line in lines:
        line = line.strip()
        if not line or line.startswith('###'):
            continue
        line = re.sub(r'(\*\*|\*|__|_)', '', line)
        # Strip (PRO) / [PRO] for iOS release notes
        line = re.sub(r'\s*[\(\[]PRO[\)\]]', '', line, flags=re.IGNORECASE)
        if line.startswith('- '):
            cleaned.append(line)
        else:
            cleaned.append(f"- {line}")
    return '\n'.join(cleaned)

def main():
    target_version = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("GITHUB_REF_NAME", "")
    print(f"Generating iOS App Store metadata (Version: '{target_version or 'latest'}')...\n")

    release_notes_en = extract_changelog_for_version(target_version)

    try:
        from deep_translator import MyMemoryTranslator
        translator_cls = MyMemoryTranslator
    except Exception:
        translator_cls = None

    for locale, data in IOS_LISTINGS.items():
        locale_dir = os.path.join(IOS_METADATA_DIR, locale)
        os.makedirs(locale_dir, exist_ok=True)

        current_data = dict(data)
        
        # Localize release notes if DE
        if locale.startswith('de') and translator_cls:
            try:
                translated_lines = []
                for line in release_notes_en.split('\n'):
                    if line.startswith('- '):
                        raw_text = line[2:].strip()
                        trans = translator_cls(source='en-GB', target='de-DE').translate(raw_text)
                        translated_lines.append(f"- {trans}")
                    elif line.strip():
                        trans = translator_cls(source='en-GB', target='de-DE').translate(line.strip())
                        translated_lines.append(trans)
                current_data['release_notes'] = '\n'.join(translated_lines)
            except Exception as e:
                print(f"Translation failed for {locale}: {e}")
                current_data['release_notes'] = release_notes_en
        else:
            current_data['release_notes'] = release_notes_en

        print(f"--- Locale: {locale} ---")
        for field, text in current_data.items():
            text_clean = text.strip()
            max_limit = LIMITS.get(field)
            char_count = len(text_clean)

            if max_limit and char_count > max_limit:
                print(f"❌ ERROR: [{field}] exceeds limit! ({char_count}/{max_limit} chars)")
                sys.exit(1)
            elif max_limit:
                print(f"✓ [{field}]: {char_count}/{max_limit} chars")

            filename = f"{field}.txt"
            filepath = os.path.join(locale_dir, filename)
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(text_clean + "\n")

        print(f"Saved metadata files to: {locale_dir}\n")

    print("🎉 All iOS App Store metadata successfully generated!")

if __name__ == '__main__':
    main()
