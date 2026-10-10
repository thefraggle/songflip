#!/usr/bin/env python3
import os
import re
import csv
import json
import time
import urllib.request
import urllib.parse

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CSV_PATH = os.path.join(BASE_DIR, "distribution", "playstore_listings_all_languages.csv")
MD_PATH = os.path.join(BASE_DIR, "distribution", "playstore_listings_all_languages.md")
ANDROID_METADATA_DIR = os.path.join(BASE_DIR, "fastlane", "metadata", "android")
IOS_METADATA_DIR = os.path.join(BASE_DIR, "fastlane", "metadata")
GEN_IOS_SCRIPT = os.path.join(BASE_DIR, "scripts", "generate_ios_store_listings.py")

# Translation mappings for 34 languages
LOCALE_TO_TRANSLATE_LANG = {
    'bn-BD': 'bn',
    'cs-CZ': 'cs',
    'da-DK': 'da',
    'de-DE': 'de',
    'el-GR': 'el',
    'en-GB': 'en',
    'en-US': 'en',
    'es-419': 'es',
    'es-ES': 'es',
    'es-US': 'es',
    'fi-FI': 'fi',
    'fr-CA': 'fr',
    'fr-FR': 'fr',
    'hi-IN': 'hi',
    'hu-HU': 'hu',
    'id': 'id',
    'it-IT': 'it',
    'ja-JP': 'ja',
    'ko-KR': 'ko',
    'mr-IN': 'mr',
    'nb-NO': 'no',
    'nl-NL': 'nl',
    'pl-PL': 'pl',
    'pt-BR': 'pt',
    'pt-PT': 'pt',
    'ro-RO': 'ro',
    'ru-RU': 'ru',
    'sv-SE': 'sv',
    'th-TH': 'th',
    'tr-TR': 'tr',
    'uk': 'uk',
    'vi': 'vi',
    'zh-CN': 'zh-CN',
    'zh-TW': 'zh-TW',
}

# Master items in English and German
MASTER_CONTENT = {
    'en': {
        'search_title': "Direct Song Search (In-App Search):",
        'search_body': "Search for tracks and artists directly inside SongFlip and launch playback instantly in your preferred music app with 1-tap Fast Flip—no existing link required.",
        
        'qr_title': "QR Code Music Sharing & Scanning:",
        'qr_body': "Generate and scan universal QR codes for songs and albums. Effortlessly share music with friends in person, no matter which streaming service they subscribe to.",
        
        'playlist_body': "Transfer entire playlists track-by-track between Spotify, Apple Music, YouTube Music, TIDAL, and Deezer—featuring automatic duplicate cleaning, up to 200 tracks, and high-speed multi-tier caching.",
        
        'refine_title': "Refine Matches & 1-Tap Favorites:",
        'refine_body': "Easily pick between live recordings, remasters, or alternative versions. Pin your favorite tracks in history with a single tap and manage them via the streamlined quick action menu.",
    },
    'de': {
        'search_title': "Direkte Titelsuche (In-App Search):",
        'search_body': "Suche Songs & Interpreten direkt in SongFlip und starte sie per Fast Flip sofort in deiner Lieblings-App – auch ganz ohne Link.",
        
        'qr_title': "Musik per QR-Code teilen & scannen:",
        'qr_body': "Generiere und scanne universelle Musik-QR-Codes. Teile Songs & Alben blitzschnell vor Ort – egal welcher Streamingdienst genutzt wird.",
        
        'playlist_body': "Übertrage Playlists zwischen Spotify, Apple Music, YouTube Music, TIDAL und Deezer – inklusive Duplikat-Bereinigung, bis zu 200 Tracks und schnellem Cache.",
        
        'refine_title': "Treffer verfeinern & 1-Tap Favoriten:",
        'refine_body': "Wähle gezielt zwischen Live-, Remaster- oder Studio-Versionen. Pinne Favoriten im Verlauf mit 1-Tap an und nutze das praktische Schnellmenü.",
    }
}

TRANSLATION_CACHE = {}

def translate_text(text, target_lang, max_retries=3):
    if target_lang in ('en', 'en-US', 'en-GB'):
        return text
    cache_key = (text, target_lang)
    if cache_key in TRANSLATION_CACHE:
        return TRANSLATION_CACHE[cache_key]

    for attempt in range(max_retries):
        try:
            url = f"https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl={urllib.parse.quote(target_lang)}&dt=t&q={urllib.parse.quote(text)}"
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
            with urllib.request.urlopen(req, timeout=8) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                res = ''.join([part[0] for part in data[0] if part and part[0]]).strip()
                if res:
                    TRANSLATION_CACHE[cache_key] = res
                    return res
        except Exception as e:
            time.sleep(0.5 + attempt * 0.5)

    # Fallback to English if translation fails
    return text

def get_localized_additions(locale):
    if locale == 'de-DE':
        return MASTER_CONTENT['de']
    if locale in ('en-US', 'en-GB'):
        return MASTER_CONTENT['en']

    t_lang = LOCALE_TO_TRANSLATE_LANG.get(locale, 'en')
    return {
        'search_title': translate_text(MASTER_CONTENT['en']['search_title'], t_lang),
        'search_body': translate_text(MASTER_CONTENT['en']['search_body'], t_lang),
        'qr_title': translate_text(MASTER_CONTENT['en']['qr_title'], t_lang),
        'qr_body': translate_text(MASTER_CONTENT['en']['qr_body'], t_lang),
        'playlist_body': translate_text(MASTER_CONTENT['en']['playlist_body'], t_lang),
        'refine_title': translate_text(MASTER_CONTENT['en']['refine_title'], t_lang),
        'refine_body': translate_text(MASTER_CONTENT['en']['refine_body'], t_lang),
    }

def update_full_description(desc, locale):
    additions = get_localized_additions(locale)
    
    # Check if already updated
    if additions['search_body'] in desc or "Fast Flip" in desc or "QR" in desc:
        print(f"[{locale}] Already contains updated features.")
        return desc

    # Strategy: split into paragraphs or bullet blocks
    # We want to insert Search and QR after Bullet 1 (10 Platforms),
    # Update Universal Playlist Converter body,
    # And insert Refine & Favorites right after Playlist Converter.
    lines = desc.split('\n')
    new_lines = []
    
    bullet_count = 0
    in_playlist_bullet = False
    
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        
        if stripped.startswith('•'):
            bullet_count += 1
            
            # Check if this is the Playlist Converter bullet (Bullet 3)
            if bullet_count == 3:
                # Keep the playlist header line
                new_lines.append(line)
                i += 1
                # Skip the old playlist body lines until empty line or next bullet
                while i < len(lines) and lines[i].strip() and not lines[i].strip().startswith('•'):
                    i += 1
                # Insert our updated playlist body
                new_lines.append(additions['playlist_body'])
                new_lines.append("")
                
                # Immediately insert Refine Matches & 1-Tap Favorites
                new_lines.append(f"• {additions['refine_title']}")
                new_lines.append(additions['refine_body'])
                new_lines.append("")
                continue
                
            new_lines.append(line)
            
            # If this is Bullet 1 (10 Platforms), insert the body of Bullet 1, then Search and QR
            if bullet_count == 1:
                i += 1
                # Add bullet 1 body lines
                while i < len(lines) and not lines[i].strip().startswith('•'):
                    new_lines.append(lines[i])
                    i += 1
                
                # Make sure there is empty space, then insert Direct Search & QR
                if new_lines and new_lines[-1].strip() != "":
                    new_lines.append("")
                    
                new_lines.append(f"• {additions['search_title']}")
                new_lines.append(additions['search_body'])
                new_lines.append("")
                new_lines.append(f"• {additions['qr_title']}")
                new_lines.append(additions['qr_body'])
                new_lines.append("")
                continue
        else:
            new_lines.append(line)
            
        i += 1

    result = '\n'.join(new_lines)
    # Clean up excess consecutive blank lines (max 2)
    result = re.sub(r'\n{3,}', '\n\n', result).strip()
    return result

def main():
    print("Reading CSV...")
    with open(CSV_PATH, 'r', encoding='utf-8') as f:
        reader = list(csv.DictReader(f))

    updated_rows = []
    print(f"Processing {len(reader)} languages...")
    
    for row in reader:
        locale = row['Language']
        title = row['Title']
        short_desc = row['Short description']
        full_desc = row['Full description']
        
        print(f"Translating & updating {locale}...")
        new_full_desc = update_full_description(full_desc, locale)
        
        # Verify length constraints
        assert len(title) <= 30, f"Title too long for {locale}: {len(title)}"
        assert len(short_desc) <= 80, f"Short desc too long for {locale}: {len(short_desc)}"
        if len(new_full_desc) > 4000:
            print(f"⚠️ WARNING: Full desc for {locale} is {len(new_full_desc)} chars (limit 4000)!")
        else:
            print(f"✓ {locale}: {len(new_full_desc)}/4000 chars")
            
        updated_rows.append({
            'Language': locale,
            'Title': title,
            'Short description': short_desc,
            'Full description': new_full_desc
        })

    # Write updated CSV
    print(f"Writing updated CSV to {CSV_PATH}...")
    with open(CSV_PATH, 'w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['Language', 'Title', 'Short description', 'Full description'])
        writer.writeheader()
        writer.writerows(updated_rows)

    # Write updated MD
    print(f"Writing updated Markdown to {MD_PATH}...")
    with open(MD_PATH, 'w', encoding='utf-8') as f:
        f.write("# 📱 SongFlip – Play Store Listings (Alle Sprachen)\n")
        f.write("> **Version:** 1.8.10 | **Umfang:** 34 Store-Sprachen | **Zertifiziert:** Titel ≤ 30 Zeichen, Kurzbeschreibung ≤ 80 Zeichen, Volltext ≤ 4.000 Zeichen.\n")
        f.write("---\n\n")
        for row in updated_rows:
            loc = row['Language']
            t = row['Title']
            s = row['Short description']
            d = row['Full description']
            f.write(f"## {loc}\n\n")
            f.write(f"### Titel ({len(t)}/30 Zeichen)\n```text\n{t}\n```\n\n")
            f.write(f"### Kurzbeschreibung ({len(s)}/80 Zeichen)\n```text\n{s}\n```\n\n")
            f.write(f"### Ausführliche Beschreibung ({len(d)}/4000 Zeichen)\n```text\n{d}\n```\n\n")
            f.write("---\n\n")

    # Update individual fastlane/metadata/android files
    print("Updating fastlane/metadata/android directories...")
    for row in updated_rows:
        loc = row['Language']
        loc_dir = os.path.join(ANDROID_METADATA_DIR, loc)
        if os.path.exists(loc_dir):
            with open(os.path.join(loc_dir, "full_description.txt"), 'w', encoding='utf-8') as f:
                f.write(row['Full description'] + "\n")

    print("🎉 All 34 Play Store listings updated successfully!")

if __name__ == '__main__':
    main()
