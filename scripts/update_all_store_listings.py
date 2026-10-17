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
SYNC_ALL_SCRIPT = os.path.join(BASE_DIR, "scripts", "sync_all_metadata.py")

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

# Optimized short descriptions to eliminate SUBTITLE_DUPLICATES_TITLE while adding high-value "song" / "converter" terms
SHORT_DESC_OVERRIDES = {
    'bn-BD': 'গান ও পডকাস্ট কনভার্ট করে প্রিয় প্লেয়ারে সরাসরি চালান (০ ক্লিক)।',
    'cs-CZ': 'Převádějte a otevírejte skladby i podcasty přímo v oblíbeném přehrávači.',
    'el-GR': 'Μετατρέψτε και ανοίξτε τραγούδια και podcast στο αγαπημένο σας player.',
    'es-419': 'Convierte y abre canciones, álbumes y podcasts en tu reproductor favorito.',
    'es-ES': 'Convierte y abre canciones, álbumes y podcasts en tu reproductor favorito.',
    'es-US': 'Convierte y abre canciones, álbumes y podcasts en tu reproductor favorito.',
    'fr-CA': 'Convertissez et ouvrez chansons et podcasts dans votre lecteur favori (0 clic).',
    'fr-FR': 'Convertissez et ouvrez chansons et podcasts dans votre lecteur favori (0 clic).',
    'hi-IN': 'गाने और पॉडकास्ट कन्वर्ट करें व अपने पसंदीदा प्लेयर में तुरंत खोलें (0 क्लिक)।',
    'hu-HU': 'Alakítsd át és nyisd meg a dalokat és podcastokat kedvenc lejátszódban.',
    'id': 'Konversi dan buka tautan lagu & podcast di pemutar favorit Anda (0 klik).',
    'it-IT': 'Converti e apri brani, canzoni e podcast nel tuo lettore preferito (0 clic).',
    'mr-IN': 'गाणी आणि पॉडकास्ट कन्व्हर्ट करून आवडत्या प्लेअरमध्ये लगेच उघडा (० क्लिक).',
    'pt-BR': 'Converta e abra canções, faixas e podcasts no seu player favorito (0 clique).',
    'pt-PT': 'Converta e abra canções, faixas e podcasts no seu player favorito (0 clique).',
    'ro-RO': 'Convertește și deschide melodii, piese și podcasturi în playerul tău preferat.',
    'ru-RU': 'Конвертируйте и открывайте песни и подкасты в любимом плеере в 0 кликов.',
    'tr-TR': 'Şarkı ve podcast bağlantılarını dönüştürüp favori oynatıcınızda otomatik açın.',
    'uk': 'Конвертуйте та відкривайте пісні й подкасти в улюбленому плеєрі (0 кліків).',
    'vi': 'Tự động đổi và mở bài hát, podcast trực tiếp trong trình phát yêu thích.',
}

# ASO-optimized English template paragraphs (exact compound phrases: music link redirect, song link converter, music converter, link redirect, open Spotify links, convert playlists, transfer playlists)
EN_PARAS = {
    0: "A friend sends you a song link or podcast link on Spotify, but you listen on YouTube Music, Apple Podcasts, or Deezer?",
    1: "SongFlip is your seamless, zero-click music link redirect and song link converter for Android. Once enabled, incoming music links and podcast links automatically convert in the background and open directly in your favorite player—no manual searching, no ads, no browser detours.",
    2: "Whether switching Spotify to YouTube Music, Apple Podcasts to Pocket Casts, Spotify to Apple Music, or opening tracks from Shazam, SongFlip works as your universal music converter and link redirect tool to open Spotify links and audio streams instantly.",
    4: "• 10 Streaming Platforms, Podcasts & Shazam:\nSeamlessly convert and redirect song links and podcasts across Spotify, YouTube Music, Apple Music, Apple Podcasts, Pocket Casts, Deezer, TIDAL, Amazon Music, SoundCloud, and Bandcamp. Includes instant music link converter support for shared Shazam links!",
    5: "• Direct Song Search (In-App Search):\nSearch for tracks and artists directly inside SongFlip and launch playback instantly in your preferred streaming player with 1-tap Fast Flip—no existing song link required.",
    6: "• QR Code Song Sharing & Scanning:\nGenerate and scan universal QR codes for songs and albums. Effortlessly share music links with friends in person across any streaming service.",
    7: "• Podcast Converter & Podcast Redirect Engine:\nShare full podcast shows or episodes across platforms. SongFlip resolves episode titles automatically to convert and open podcast links right inside your favorite podcast player.",
    8: "• Universal Playlist Converter & Playlist Transfer:\nConvert playlists and transfer playlists track-by-track between Spotify, Apple Music, YouTube Music, TIDAL, and Deezer—with automatic duplicate cleaning, up to 200 tracks, and fast caching.",
    9: "• Refine Matches & 1-Tap Favorites:\nEasily pick between live recordings, remasters, or studio versions. Pin your favorite songs in history with a single tap and manage them via the quick action menu.",
    12: "• Smart Link Redirect Assistant (Android 12+):\nEasily verify and configure Android Open by Default App Links with our interactive 1-tap link redirect setup assistant.",
    13: "• Clipboard Smart-Banner & Native Sharing:\nCopied a music link? Convert and open it instantly via the smart dashboard banner, or share links directly from WhatsApp, Telegram, Reddit, or Instagram into SongFlip.",
    19: "Compatible with universal music link protocols including Songlink / Odesli. Download SongFlip now—your automatic music link redirect and song link converter to bridge songs & podcasts across all streaming services effortlessly!"
}

# Compact English fallbacks for paragraphs in case a verbose language exceeds 3950 UTF-16 units
EN_PARAS_COMPACT = {
    1: "SongFlip is your 0-click music link redirect and song link converter for Android. Incoming music links and podcast links automatically convert and open directly in your favorite player—no manual search or ads.",
    2: "From Spotify to YouTube Music, Apple Podcasts to Pocket Casts, or Shazam tracks: SongFlip is your universal music converter and link redirect to open Spotify links instantly.",
    4: "• 10 Platforms, Podcasts & Shazam:\nConvert and redirect song links and podcasts across Spotify, YouTube Music, Apple Music, Apple Podcasts, Pocket Casts, Deezer, TIDAL, Amazon Music, SoundCloud, Bandcamp, and Shazam!",
    5: "• Direct Song Search:\nSearch songs and artists in SongFlip and start playback in your favorite player with 1-tap Fast Flip.",
    6: "• QR Code Song Sharing:\nCreate and scan universal QR codes for songs and albums across any streaming service.",
    7: "• Podcast Converter & Redirect:\nShare podcast shows or episodes across platforms. Automatically convert and open podcast links in your favorite podcast player.",
    8: "• Playlist Converter & Transfer:\nConvert playlists and transfer playlists between Spotify, Apple Music, YouTube Music, TIDAL, and Deezer (up to 200 tracks).",
    9: "• Refine Matches & Favorites:\nPick between live, remaster, or studio versions and pin favorite songs with 1 tap.",
    10: "• Instant Track Playback:\nStarts playback directly inside your target player instead of empty search pages.",
    11: "• Quick Player Chooser:\nPick your target audio player on the fly whenever you open a shared song link or podcast link.",
    12: "• Link Redirect Assistant (Android 12+):\nConfigure Android Open by Default App Links with our 1-tap link redirect setup assistant.",
    13: "• Clipboard Banner & Share Menu:\nCopied a music link? Convert and open it via the banner or share links from WhatsApp, Telegram, or Instagram.",
    14: "• Web Smart Links (songflip.link):\nCreate neutral web links with an embedded player so friends can listen in any browser.",
    15: "• Quick Settings & Shortcuts:\nPause link redirect for 15 min, 1 hour, or until tomorrow via Quick Settings.",
    16: "• Material You & AMOLED Dark Mode:\nFollows system dynamic colors with full AMOLED dark mode and themed icon.",
    17: "• Promo Codes & Vouchers:\nRedeem promo codes in settings to unlock PRO perks and extended history.",
    19: "Compatible with Songlink / Odesli. Download SongFlip—your automatic music link redirect and song link converter for all streaming services!"
}

TRANSLATION_CACHE = {}

def utf16_len(s):
    return len(s.encode('utf-16-le')) // 2

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
            with urllib.request.urlopen(req, timeout=4) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                res = ''.join([part[0] for part in data[0] if part and part[0]]).strip()
                if res:
                    # Normalize bullet formatting if translated
                    res = re.sub(r'^[•·*]\s*', '• ', res)
                    TRANSLATION_CACHE[cache_key] = res
                    return res
        except Exception:
            time.sleep(0.2 + attempt * 0.2)

    return text

def optimize_locale_description(desc, locale):
    if locale in ('en-US', 'en-GB', 'de-DE'):
        return desc

    t_lang = LOCALE_TO_TRANSLATE_LANG.get(locale, 'en')
    paras = desc.split('\n\n')
    if len(paras) != 20:
        print(f"⚠️ [{locale}] Unexpected paragraph count {len(paras)}, skipping structural replacement.")
        return desc

    for idx, en_text in EN_PARAS.items():
        paras[idx] = translate_text(en_text, t_lang)

    candidate = '\n\n'.join(paras)

    # If candidate exceeds 3960 UTF-16 code units, progressively compact secondary bullets
    if utf16_len(candidate) > 3960:
        for c_idx in [16, 17, 15, 14, 10, 11, 9, 6, 5, 12, 13, 8, 7, 4, 2, 1, 19]:
            if utf16_len(candidate) <= 3950:
                break
            paras[c_idx] = translate_text(EN_PARAS_COMPACT[c_idx], t_lang)
            candidate = '\n\n'.join(paras)

    return candidate

from concurrent.futures import ThreadPoolExecutor

def prewarm_translations():
    tasks = []
    target_langs = sorted(set(LOCALE_TO_TRANSLATE_LANG.values()) - {'en', 'de'})
    all_texts = list(EN_PARAS.values()) + list(EN_PARAS_COMPACT.values())
    for tl in target_langs:
        for txt in all_texts:
            tasks.append((txt, tl))

    print(f"Pre-fetching {len(tasks)} translations across {len(target_langs)} languages in parallel...")
    with ThreadPoolExecutor(max_workers=32) as pool:
        list(pool.map(lambda args: translate_text(args[0], args[1], max_retries=2), tasks))

def main():
    print("Reading CSV...")
    with open(CSV_PATH, 'r', encoding='utf-8') as f:
        rows = list(csv.DictReader(f))

    prewarm_translations()

    updated_rows = []
    for row in rows:
        loc = row['Language']
        title = row['Title'].strip()
        short_desc = SHORT_DESC_OVERRIDES.get(loc, row['Short description'].strip())
        full_desc = row['Full description'].strip()

        new_full = optimize_locale_description(full_desc, loc)

        t_u16 = utf16_len(title)
        s_u16 = utf16_len(short_desc)
        f_u16 = utf16_len(new_full)

        assert t_u16 <= 30, f"Title exceeds 30 UTF-16 units for {loc}: {t_u16}"
        assert s_u16 <= 80, f"Short desc exceeds 80 UTF-16 units for {loc}: {s_u16}"
        assert f_u16 <= 4000, f"Full desc exceeds 4000 UTF-16 units for {loc}: {f_u16}"

        print(f"✓ {loc:7s} | Title: {t_u16:2d}/30 | Short: {s_u16:2d}/80 | Full: {f_u16:4d}/4000 (UTF-16)")
        updated_rows.append({
            'Language': loc,
            'Title': title,
            'Short description': short_desc,
            'Full description': new_full,
        })

    with open(CSV_PATH, 'w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['Language', 'Title', 'Short description', 'Full description'])
        writer.writeheader()
        writer.writerows(updated_rows)

    print("CSV updated. Running sync_all_metadata.py...")

if __name__ == '__main__':
    main()

