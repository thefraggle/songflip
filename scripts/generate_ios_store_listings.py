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
        'subtitle': 'Open links in your music app',
        'promotional_text': 'Friends send Spotify links, but you use Apple Music or YouTube Music? SongFlip instantly converts and opens shared music links in your favorite player.',
        'keywords': 'music,link,spotify,apple music,youtube music,tidal,deezer,songlink,odesli,redirect,convert,share',
        'description': """Friends send you Spotify, Tidal, or Deezer links – but you use Apple Music, YouTube Music, or Amazon Music?

SongFlip is the music link redirector for iOS. Whether via the iOS Share Sheet, Shortcuts, or the Action Button: SongFlip instantly converts shared music links and opens them directly in your preferred music app – with zero manual searching, no copying, and no friction.

Supported Music Services (Any-to-Any Redirection):
- Apple Music
- YouTube Music & YouTube
- Spotify
- Tidal
- Deezer
- Amazon Music

Key Features:
- Seamless iOS Integration: Convert links from WhatsApp, Telegram, iMessage, Instagram, or Safari directly via the native Share Sheet.
- Direct Playback Engine: Instantly launches exact track and video IDs in your target player.
- Albums, Playlists & Artists: Recognizes individual songs, full albums, EPs, artist profiles, and search queries.
- Shortcuts & Action Button Support: Integrate SongFlip into iOS Shortcuts or trigger instant conversion with the Action Button.
- 100% Privacy & Zero Tracking: No account, no login required. SongFlip collects no personal data, no ad tracking IDs, and no listening habits.
- Built-in Test Studio & History: Test music links manually and easily revisit your recent conversions in the history sheet.

Built with passion for music lovers who want to enjoy songs seamlessly across platforms in their favorite player.""",
        'support_url': 'https://songflip.link',
        'marketing_url': 'https://songflip.link',
        'privacy_url': 'https://songflip.link/privacy',
    },
    'de-DE': {
        'name': 'SongFlip: Musik Link Umleitung',
        'subtitle': 'Links im Wunschplayer öffnen',
        'promotional_text': 'Freunde schicken Spotify-Links, aber du nutzt Apple Music oder YouTube Music? SongFlip leitet geteilte Musiklinks blitzschnell in deinen Lieblingsplayer um.',
        'keywords': 'musik,link,spotify,apple music,youtube music,tidal,deezer,songlink,odesli,redirect,teilen,player',
        'description': """Freunde schicken dir Musik-Links von Spotify, Deezer oder Tidal – aber du nutzt Apple Music, YouTube Music oder Amazon Music?

SongFlip ist die Musik-Link-Umleitung für iOS. Ob über das iOS-Teilen-Menü (Share Sheet), Kurzbefehle oder den Action Button: SongFlip wandelt geteilte Musik-Links blitzschnell um und öffnet sie direkt in deiner bevorzugten Musik-App – ganz ohne lästiges Suchen, Kopieren oder manuelle Zwischenschritte.

Unterstützte Musikdienste (Jeder-zu-Jeder-Umleitung):
- Apple Music
- YouTube Music & YouTube
- Spotify
- Tidal
- Deezer
- Amazon Music

Hauptfunktionen:
- Nahtlose iOS-Integration: Öffne Links aus WhatsApp, Telegram, iMessage, Instagram oder Safari direkt über das Teilen-Menü (Share Sheet).
- Direkte Wiedergabe: Startet exakte Titel und Videos direkt im gewünschten Player.
- Alben, Playlists & Interpreten: Erkennt Einzelsongs, vollständige Alben, EPs, Künstlerprofile und Suchlinks.
- Kurzbefehle & Action Button: Integriere SongFlip direkt in deine iOS-Kurzbefehle oder lege die Link-Umwandlung auf den Action Button.
- 100 % Privatsphäre & Kein Tracking: Kein Account, kein Login. SongFlip sammelt keine Daten, keine Werbe-IDs und analysiert keine Hörgewohnheiten.
- Integriertes Test-Studio & Verlauf: Probiere Musik-Links direkt in der App aus und greife jederzeit auf deinen Verlauf kürzlich geöffneter Songs zu.

Entwickelt für alle Musikliebhaber, die Musik ohne Plattformgrenzen in ihrer Lieblings-App genießen möchten.""",
        'support_url': 'https://songflip.link',
        'marketing_url': 'https://songflip.link',
        'privacy_url': 'https://songflip.link/privacy',
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
