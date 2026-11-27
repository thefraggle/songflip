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
        'subtitle': 'Playlist & Podcast Converter',
        'promotional_text': 'Friends send Spotify or Apple Podcasts links, but you use YouTube Music or Pocket Casts? SongFlip instantly converts and opens shared audio links in your favorite player.',
        'keywords': 'spotify,apple,youtube,transfer,deezer,qobuz,tidal,soundcloud,shazam,import,songs,amazon,anghami',
        'description': """Friends send you a song link or podcast link from Spotify, Tidal, or Apple Podcasts – but you listen on Apple Music, YouTube Music, or Pocket Casts?

SongFlip is your universal music link redirect and song link converter for iOS. Whether via the iOS Share Sheet, Listen & Flip audio recognition, direct song search, universal QR codes, Shortcuts, or the Action Button: SongFlip instantly converts shared music links and podcast links to open them directly in your favorite player – with zero manual searching, no copying, and no friction.

Supported Audio & Podcast Services (14 Platforms – Any-to-Any Link Redirect):
- Apple Music & Apple Podcasts
- YouTube Music & YouTube
- Spotify
- Pocket Casts
- TIDAL
- Deezer
- Amazon Music
- Qobuz & Beatport
- Audiomack & Idagio
- Anghami
- SoundCloud
- Bandcamp & Shazam

Key Features:
- Seamless iOS Integration: Convert music links and open Spotify links from WhatsApp, Telegram, iMessage, Instagram, or Safari directly via the native Share Sheet.
- Listen & Flip (Audio Recognition & Offline Mode): Identify songs playing around you in clubs, bars, or on the radio with real-time acoustic signal guidance, instant haptic confirmation, save them to your history, and open them directly in your favorite player. Captures audio fingerprints even offline without internet reception and resolves tracks automatically once you're back online.
- Direct Song Search: Search for tracks and artists directly inside SongFlip and launch playback instantly in your favorite player with 1-tap Fast Flip – no existing song link required.
- QR Code Music Sharing: Generate and scan universal QR codes for songs and albums – perfect for sharing music links in person across different streaming services.
- Universal Playlist Converter, File Import (M3U) & Playlist Transfer: Convert playlists, import local .m3u, .m3u8, .csv, and plain text files, and transfer playlists across Spotify, Apple Music, YouTube Music, TIDAL, and Deezer – featuring duplicate cleaning, AI ghost-track filtering, and up to 200 tracks.
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
        'name': 'SongFlip: Musik-Link Redirect',
        'subtitle': 'Playlist & Podcast Converter',
        'promotional_text': 'Freunde schicken Spotify- oder Apple Podcasts-Links, aber du nutzt YouTube Music oder Pocket Casts? SongFlip leitet Audio-Links blitzschnell in deinen Wunsch-Player um.',
        'keywords': 'spotify,apple,youtube,transfer,deezer,beatport,qobuz,tidal,soundcloud,shazam,lieder,erkennen,amazon',
        'description': """Freunde schicken dir einen Song-Link oder Podcast-Link von Spotify, Deezer oder Apple Podcasts – aber du nutzt Apple Music, YouTube Music oder Pocket Casts?

SongFlip ist dein smarter Musik-Link Redirect sowie dein universeller Song- und Podcast-Link Converter für iOS. Ob über das native Teilen-Menü (Share Sheet), die Musikerkennung Listen & Flip, die direkte Titelsuche, universelle QR-Codes, Kurzbefehle oder den Action Button: SongFlip lässt dich geteilte Musik-Links und Podcast-Links blitzschnell konvertieren und direkt im Wunsch-Player öffnen – ganz ohne manuelles Suchen oder Kopieren.

Unterstützte Musik- & Podcastdienste (14 Plattformen – Jeder-zu-Jeder Link Redirect):
- Apple Music & Apple Podcasts
- YouTube Music & YouTube
- Spotify
- Pocket Casts
- TIDAL
- Deezer
- Amazon Music
- Qobuz & Beatport
- Audiomack & Idagio
- Anghami
- SoundCloud
- Bandcamp & Shazam

Hauptfunktionen:
- Nahtlose iOS-Integration: Musik- und Podcast-Links sowie Spotify-Links aus WhatsApp, Telegram, iMessage, Instagram oder Safari direkt über das Teilen-Menü (Share Sheet) konvertieren und öffnen.
- Listen & Flip (Musikerkennung & Offline-Modus): Erkenne laufende Songs in Bars, Clubs oder im Radio per Mikrofon mit Signalqualitäts-Feedback und haptischer Bestätigung, speichere sie im Verlauf und öffne sie direkt in deinem Lieblings-Player. Auch ohne Internetempfang werden Audio-Fingerabdrücke lokal gesichert und automatisch aufgelöst, sobald du wieder online bist.
- Direkte Titelsuche: Suche Songs und Künstler direkt in SongFlip und starte sie mit einem Fingertipp (Fast Flip) sofort in deinem Lieblings-Player – auch ohne bestehenden Song-Link.
- Musik per QR-Code teilen: Erstelle und scanne universelle QR-Codes für Songs und Alben – ideal zum Teilen von Musik-Links vor Ort über alle Streamingdienste hinweg.
- Universal Playlist Converter, Datei-Import (M3U) & Playlist-Transfer: Playlists konvertieren, lokale .m3u-, .m3u8-, .csv- und Textdateien importieren und Playlists zwischen Spotify, Apple Music, YouTube Music, TIDAL und Deezer umziehen – inklusive Duplikat-Bereinigung, Ghost-Track-Filter und bis zu 200 Titeln.
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

REVIEW_INFORMATION = {
    'first_name': 'Daniel',
    'last_name': 'Notthoff',
    'phone_number': '+49 2335 8873531',
    'email_address': 'hey@goork.de',
    'demo_user': '',
    'demo_password': '',
    'notes': """1. Live Physical Device Walkthrough (No Login Required):
SongFlip uses a Zero-Auth architecture with no account registration, no login wall, and no demo credentials required. 100% of free and PRO test flows are directly accessible from launch:
- Launch SongFlip -> under "Preferred Target Player", tap any target streaming service (e.g., Apple Music, YouTube Music, Spotify, TIDAL).
- Direct Song Search: Tap "Search song or artist..." -> search any track (e.g., "Daft Punk Get Lucky") -> tap a result to open it directly in the selected target player.
- Clipboard / Link Conversion: Copy any public streaming URL (e.g., https://open.spotify.com/track/6rqhFgbbKwnb9MLmUQDhG6) and return to SongFlip, or share any music/podcast link via the iOS Share Sheet ("SongFlip"). Tap the detected link banner to resolve and redirect immediately.
- Listen & Flip (Audio Recognition): Tap "Listen & Flip" next to the search bar to test microphone-based ShazamKit song recognition (requests microphone permission only on tap; works online and queues offline).
- Playlist File Import: Open Settings (gear icon, top right) -> "Import Playlist File (.m3u8, .csv)" to convert local tracklists (Free tier converts up to 5 tracks; PRO unlocks up to 200 tracks).

2. App Purpose & Target Audience:
SongFlip is a universal music & podcast link converter and redirector across 14 streaming and podcast platforms. It solves the friction of receiving shared links from friends who use a different streaming service.

3. In-App Purchases & Subscriptions (4 Attached Items):
All 4 items are attached to this submission: SongFlip Pro Lifetime (non-consumable), SongFlip Pro Subscriptions (subscription group), SongFlip Pro (Annual), and SongFlip Pro (Monthly).
- To view the native Paywall: Tap the "PRO" badge next to the "SongFlip" title on the Home Screen, OR open Settings (gear icon) -> "SongFlip PRO", OR toggle "Audiophile Quality Priority" in Settings.
- All prices and subscription billing periods are loaded dynamically from StoreKit / App Store Connect via RevenueCatUI, and a "Restore Purchases" option is included.
- Account Login / Demo Credentials: None required (demoAccountRequired = false).

4. External Services & Infrastructure Used:
- Resolution & Playlist API: https://cache.songflip.link / https://songflip.link (Firebase Cloud Functions v2 in europe-west3 + Firestore cache) and public catalog metadata endpoints (iTunes Search, Deezer, Qobuz, TIDAL, SoundCloud, Songlink/Odesli).
- Audio Recognition: Apple ShazamKit signatures matched via SongFlip's short-lived developer token endpoint (/getShazamToken).
- Billing: Apple StoreKit 2 via RevenueCat SDK.
- Anonymous Diagnostics: Self-hosted, cookie-free Aptabase instance at https://telemetry-apps.goork.de (anonymous OS/app version and aggregate feature counters only; no personal data, no ad SDKs, no IDFA).

5. Regional Differences:
The app functions identically across all App Store storefronts worldwide (localized in 28 languages).

6. Regulated Industry & Third-Party Material:
SongFlip does not operate in a regulated industry and does not host, stream, or redistribute copyrighted audio. It strictly resolves public links and metadata to open tracks in the user's chosen licensed streaming app.""",
}

def utf16_len(s):
    return len(s.encode('utf-16-le')) // 2

def extract_changelog_for_version(version=None, filepath="CHANGELOG.md"):
    changelog_path = os.path.join(BASE_DIR, filepath)
    if not os.path.exists(changelog_path):
        return None

    content = open(changelog_path, "r", encoding="utf-8").read()

    if version and version != "latest":
        clean_v = version.lstrip('v').strip()
        pattern = r'## \[' + re.escape(clean_v) + r'\].*?\n(.*?)(?=\n## \[|\Z)'
        match = re.search(pattern, content, re.DOTALL)
        if match:
            return clean_markdown_notes(match.group(1).strip())
        return None

    match = re.search(r"## \[.*?\][^\n]*\n(.*?)(?=\n## \[|\Z)", content, re.DOTALL)
    if match:
        return clean_markdown_notes(match.group(1).strip())

    return None

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

    release_notes_en = extract_changelog_for_version(target_version, "CHANGELOG.md") or "Performance improvements and bug fixes."
    release_notes_de = extract_changelog_for_version(target_version, "CHANGELOG.de.md")

    if not release_notes_de:
        try:
            from deep_translator import MyMemoryTranslator
            translated_lines = []
            for line in release_notes_en.split('\n'):
                if line.startswith('- '):
                    raw_text = line[2:].strip()
                    trans = MyMemoryTranslator(source='en-GB', target='de-DE').translate(raw_text)
                    translated_lines.append(f"- {trans}")
                elif line.strip():
                    trans = MyMemoryTranslator(source='en-GB', target='de-DE').translate(line.strip())
                    translated_lines.append(trans)
            release_notes_de = '\n'.join(translated_lines)
        except Exception as e:
            print(f"German translation fallback failed: {e}")
            release_notes_de = release_notes_en

    # Update TestFlight WhatToTest notes on the fly from CHANGELOGs
    testflight_dir = os.path.join(BASE_DIR, "iosApp", "SongFlip", "TestFlight")
    os.makedirs(testflight_dir, exist_ok=True)
    with open(os.path.join(testflight_dir, "WhatToTest.en-US.txt"), 'w', encoding='utf-8') as f:
        f.write(release_notes_en[:4000].strip() + "\n")
    with open(os.path.join(testflight_dir, "WhatToTest.de-DE.txt"), 'w', encoding='utf-8') as f:
        f.write(release_notes_de[:4000].strip() + "\n")
    print(f"✓ Updated TestFlight WhatToTest notes in {testflight_dir}\n")

    for locale, data in IOS_LISTINGS.items():
        locale_dir = os.path.join(IOS_METADATA_DIR, locale)
        os.makedirs(locale_dir, exist_ok=True)

        current_data = dict(data)
        
        if locale.startswith('de'):
            current_data['release_notes'] = release_notes_de
        else:
            current_data['release_notes'] = release_notes_en

        print(f"--- Locale: {locale} ---")
        for field, text in current_data.items():
            text_clean = text.strip()
            max_limit = LIMITS.get(field)
            char_count = utf16_len(text_clean)

            if max_limit and char_count > max_limit:
                print(f"❌ ERROR: [{field}] exceeds limit! ({char_count}/{max_limit} UTF-16 units)")
                sys.exit(1)
            elif max_limit:
                print(f"✓ [{field}]: {char_count}/{max_limit} chars")

            filename = f"{field}.txt"
            filepath = os.path.join(locale_dir, filename)
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(text_clean + "\n")

        print(f"Saved metadata files to: {locale_dir}\n")

    review_dir = os.path.join(IOS_METADATA_DIR, 'review_information')
    os.makedirs(review_dir, exist_ok=True)
    notes_len = utf16_len(REVIEW_INFORMATION['notes'].strip())
    if notes_len > 4000:
        print(f"❌ ERROR: [review_information.notes] exceeds limit! ({notes_len}/4000 UTF-16 units)")
        sys.exit(1)
    print(f"--- App Review Information ---")
    print(f"✓ [notes]: {notes_len}/4000 UTF-16 units")
    for field, text in REVIEW_INFORMATION.items():
        filepath = os.path.join(review_dir, f"{field}.txt")
        with open(filepath, 'w', encoding='utf-8') as f:
            clean = text.strip()
            f.write((clean + "\n") if clean else "")
    print(f"Saved review information files to: {review_dir}\n")

    print("🎉 All iOS App Store metadata successfully generated!")

if __name__ == '__main__':
    main()
