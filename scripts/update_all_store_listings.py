#!/usr/bin/env python3
import os
import re
import csv

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CSV_PATH = os.path.join(BASE_DIR, "distribution", "playstore_listings_all_languages.csv")
WHATSNEW_DIR = os.path.join(BASE_DIR, "distribution", "whatsnew")

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

DE_FULL_DESC = """Ein Freund schickt dir einen Song-Link oder Podcast auf Spotify, du hörst aber über YouTube Music, Apple Podcasts oder Deezer?

SongFlip ist dein smarter 0-Klick Musik-Link Redirect sowie Song- und Podcast-Link Converter für Android. Einmal eingerichtet, lassen sich empfangene Musik-Links und Podcasts automatisch konvertieren und direkt im Wunsch-Player öffnen – ohne Zwischenseiten, Wartezeit oder manuelle Suche.

Egal ob Spotify zu YouTube Music, Apple Podcasts zu Spotify, Spotify zu Apple Music oder geteilte Shazam-Tracks: SongFlip erkennt jeden Song-Link und Episoden blitzschnell als dein universeller Music Converter und Link Redirect.

🚀 DIE WICHTIGSTEN FUNKTIONEN:

• 12 Streaming- & Podcast-Plattformen:
Songs und Podcasts automatisch konvertieren und weiterleiten zwischen Spotify, YouTube Music, Apple Music, Apple Podcasts, Pocket Casts, Deezer, TIDAL, Amazon Music, Qobuz, Beatport, SoundCloud und Bandcamp. Erkennt auch geteilte Shazam-Tracks sofort!

• Listen & Flip – Musikerkennung (auch offline):
Erkenne Songs in Bars, Clubs oder im Radio per Mikrofon, speichere sie im Verlauf und öffne sie direkt im Wunsch-Player. Dank Offline-Modus werden Audio-Fingerabdrücke auch ohne Empfang gesichert und automatisch aufgelöst, sobald du wieder online bist.

• Direkte Titelsuche & Sofort-Wiedergabe:
Suche Songs & Interpreten direkt in SongFlip und starte die Wiedergabe per Fast Flip sofort im Ziel-Player – ganz ohne bestehende Web-URL oder leere Suchseiten.

• Musik per QR-Code teilen & scannen:
Generiere und scanne universelle Musik-QR-Codes. Teile Songs & Alben vor Ort – egal welcher Streamingdienst genutzt wird.

• Universal Playlist Converter & Playlist-Transfer:
Playlists konvertieren und Playlists übertragen zwischen Spotify, Apple Music, YouTube Music, TIDAL und Deezer – inklusive Duplikat-Bereinigung, bis zu 200 Tracks und schnellem Cache.

• Podcast Converter & Podcast-Weiterleitung:
Teile Podcast-Shows und Episoden plattformübergreifend. SongFlip löst Episodentitel für die Podcast-Umleitung automatisch auf und startet sie direkt im bevorzugten Podcast-Player.

• Treffer verfeinern & 1-Tap Favoriten:
Wähle gezielt zwischen Live-, Remaster- oder Studio-Versionen. Pinne Lieblings-Songs im Verlauf mit 1-Tap an und filtere erkannte Listen & Flip-Tracks.

• Schnellauswahl (Ask Every Time):
Nutzt du mehrere Audio-Apps? Aktiviere die Schnellauswahl, um beim Öffnen von Songs oder Podcasts flexibel den Ziel-Player zu wählen.

• Smarter Redirect-Assistent (Android 12+):
Prüft deine Audio-Apps und hilft dir mit einem Fingertipp, Standard-Apps konfliktfrei für den automatischen Musik-Link Redirect einzurichten.

• Clipboard Smart-Banner & Share-Menü:
Spotify-URL kopiert? Direkt über das Smart-Banner konvertieren und öffnen oder URLs aus WhatsApp, Telegram, Instagram oder Reddit mit SongFlip teilen.

• Universelle Web-Links & Webplayer (Smart Links):
Erstelle neutrale Smart-Links (songflip.link) mit Webplayer, die Freunde auf jedem Gerät und Browser direkt anhören können.

• Schnelleinstellungen & App-Shortcuts:
Pausiere die automatische Umleitung flexibel für 15 Minuten, 1 Stunde oder bis morgen früh über die Quick-Settings-Kachel oder per Long-Press.

• Material You Design & Themed Icon:
An dein Android-Farbschema angepasst (Dynamic Color, Light- & AMOLED-Dark-Mode) inklusive Monochrome-Homescreen-Icon.

• Promo-Code & Voucher-Unterstützung:
Löse Aktionscodes direkt in den Einstellungen ein, um PRO-Funktionen und erweiterten Verlauf freizuschalten.

🔒 DATENSCHUTZ, TRANSPARENZ & OPEN SOURCE:
• 100 % Open Source (GPLv3 auf GitHub)
• Kein Account, kein Login, keine Registrierung
• Absolut werbefrei – für immer
• Kein Tracking, keine Analyse deines Hörverhaltens

Kompatibel mit universellen Streaming-Protokollen wie Songlink / Odesli. Hol dir SongFlip – deinen automatischen Musik-Link Redirect und Playlist-Converter – und genieße Songs & Podcasts frei über alle Plattform-Grenzen hinweg!"""

EN_FULL_DESC = """A friend sends you a song link or podcast on Spotify, but you listen on Apple Podcasts, TIDAL, or Deezer?

SongFlip is your seamless, zero-click music link redirect and song link converter for Android. Once enabled, incoming song links and podcast links automatically convert in the background and open directly in your favorite player—no manual searching, no ads, no browser detours.

Whether switching Spotify to YouTube Music, Apple Podcasts to Pocket Casts, Spotify to Apple Music, or opening tracks from Shazam, SongFlip works as your universal song converter and link redirect tool to open Spotify links and audio streams instantly.

🚀 KEY FEATURES:

• 12 Streaming Platforms, Podcasts & Shazam:
Seamlessly convert and redirect song links and podcasts across Spotify, YouTube Music, Apple Music, Apple Podcasts, Pocket Casts, Deezer, TIDAL, Amazon, Qobuz, Beatport, SoundCloud, and Bandcamp. Includes instant converter support for shared Shazam tracks!

• Listen & Flip – Audio Recognition (Online & Offline):
Identify songs playing around you in clubs, bars, or on the radio, save them to your history, and open them in your favorite player. With Offline Mode, capture audio fingerprints even without reception and resolve tracks automatically once you're back online.

• Direct Track Search & Instant Playback:
Search for tracks and artists directly inside SongFlip and trigger playback instantly in your preferred streaming player with 1-tap Fast Flip—no existing URL or empty search pages.

• QR Code Track Sharing & Scanning:
Generate and scan universal QR codes for songs and albums. Effortlessly share songs with friends in person across any streaming service.

• Universal Playlist Converter & Playlist Transfer:
Convert playlists and transfer playlists track-by-track between Spotify, Apple Music, YouTube Music, TIDAL, and Deezer—with automatic duplicate cleaning, up to 200 tracks, and fast caching.

• Podcast Converter & Podcast Redirect Engine:
Share full podcast shows or episodes across platforms. SongFlip resolves episode titles automatically to convert and open podcast links right inside your favorite podcast player.

• Refine Matches & 1-Tap Favorites:
Easily pick between live recordings, remasters, or studio versions. Pin your favorite songs in history with 1 tap and filter recognized Listen & Flip tracks.

• Quick Player Chooser (Ask Every Time):
Using multiple audio apps? Enable the quick player chooser to pick your target player on the fly whenever you open a shared track or podcast episode.

• Smart Redirect Assistant (Android 12+):
Easily verify and configure Android Open by Default App Links with our interactive 1-tap setup assistant.

• Clipboard Smart-Banner & Native Sharing:
Copied a track URL? Convert and open it instantly via the smart dashboard banner, or share URLs directly from WhatsApp, Telegram, Reddit, or Instagram into SongFlip.

• Universal Web Smart Links & HTML5 Player:
Generate neutral web-share URLs (songflip.link) with an embedded webplayer so friends can listen on any device or browser.

• Quick Settings Tile & App Shortcuts:
Temporarily pause automatic redirection for 15 minutes, 1 hour, or until tomorrow morning right from your Quick Settings shade or home screen long-press.

• Material You Dynamic Color & Themed Icon:
Follows your system accent colors with full AMOLED dark mode, light mode, and a monochrome Material You home screen icon.

• Promo Code & Voucher Redemption:
Redeem promo codes and vouchers directly in settings to unlock PRO perks and extended history.

🔒 PRIVACY & OPEN SOURCE FIRST:
• 100% Open Source (GPLv3 licensed on GitHub)
• No accounts, no sign-ups, no logins required
• Completely ad-free forever
• Zero tracking, no data harvesting, no listening habits logged

Compatible with universal protocols including Songlink / Odesli. Download SongFlip now—your automatic music link redirect and song link converter to bridge songs & podcasts across all streaming services effortlessly!"""

def utf16_len(s):
    return len(s.encode('utf-16-le')) // 2

def build_listen_and_flip_para(locale, compact=False):
    wn_name = "whatsnew-ro" if locale == "ro-RO" else f"whatsnew-{locale}"
    wn_path = os.path.join(WHATSNEW_DIR, wn_name)
    with open(wn_path, 'r', encoding='utf-8') as f:
        lines = [l.strip().lstrip('- ').strip() for l in f.read().strip().split('\n') if l.strip()]

    # Line 0: "Listen & Flip (Header): Body1"
    # Line 1: "Offline Mode Header: Body2"
    l0 = lines[0]
    l1 = lines[1]

    # Split header and body on colon (ASCII ':' or full-width '：')
    m0 = re.match(r'^([^:：]+[:：])\s*(.+)$', l0)
    m1 = re.match(r'^([^:：]+[:：])\s*(.+)$', l1)

    if m0 and m1:
        header0 = m0.group(1)
        body0 = m0.group(2)
        body1 = m1.group(2)
        if compact:
            return f"• {header0}\n{body0}"
        return f"• {header0}\n{body0} {body1}"
    return f"• {l0}"

def update_platforms_para(para4):
    # Update 10 -> 12 and insert Qobuz, Beatport next to Amazon Music
    p = re.sub(r'\b10\b', '12', para4)
    p = p.replace('১০', '১২').replace('१०', '१२')
    if 'Qobuz' not in p:
        if 'Amazon Music,' in p:
            p = p.replace('Amazon Music,', 'Amazon Music, Qobuz, Beatport,')
        elif 'Amazon Music、' in p:
            p = p.replace('Amazon Music、', 'Amazon Music、Qobuz、Beatport、')
    return p

def optimize_locale_description(desc, locale):
    if locale == 'de-DE':
        return DE_FULL_DESC
    if locale in ('en-US', 'en-GB'):
        return EN_FULL_DESC

    paras = desc.split('\n\n')
    if len(paras) != 20:
        raise ValueError(f"[{locale}] Unexpected paragraph count {len(paras)}")

    # Check if already updated with Listen & Flip at paras[5]
    already_has_lf = 'Listen & Flip' in paras[5]

    paras[4] = update_platforms_para(paras[4])
    lf_full = build_listen_and_flip_para(locale, compact=False)
    lf_compact = build_listen_and_flip_para(locale, compact=True)

    if not already_has_lf:
        # Shift [5..9] -> [6..10] (replacing the 1-line redundant Instant Playback at [10])
        paras = paras[:5] + [lf_full] + paras[5:10] + paras[11:]
    else:
        paras[5] = lf_full

    assert len(paras) == 20, f"[{locale}] Expected 20 paragraphs after update, got {len(paras)}"
    candidate = '\n\n'.join(paras)

    # Keep full Listen & Flip (online + offline) and trim second sentence in secondary bullets if > 3985 UTF-16 units
    if utf16_len(candidate) > 3985:
        for idx in (17, 16, 15, 14, 13, 12, 11, 10, 7, 6, 9, 8, 4):
            if utf16_len(candidate) <= 3975:
                break
            lines = paras[idx].split('\n')
            if len(lines) > 1:
                # If body has 2 sentences, keep the first sentence
                sentences = re.split(r'(?<=[.!।])\s+', lines[1].strip())
                if len(sentences) > 1:
                    lines[1] = sentences[0].strip()
                    paras[idx] = '\n'.join(lines)
                    candidate = '\n\n'.join(paras)
                elif '—' in lines[1] or '–' in lines[1]:
                    lines[1] = re.split(r'\s*[—–]\s*', lines[1])[0].strip() + '.'
                    paras[idx] = '\n'.join(lines)
                    candidate = '\n\n'.join(paras)

    return candidate

def main():
    print(f"EN_FULL_DESC UTF-16 len: {utf16_len(EN_FULL_DESC)}/4000")
    print(f"DE_FULL_DESC UTF-16 len: {utf16_len(DE_FULL_DESC)}/4000")
    print("Reading CSV...")
    with open(CSV_PATH, 'r', encoding='utf-8') as f:
        rows = list(csv.DictReader(f))

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

    print("CSV updated successfully.")

if __name__ == '__main__':
    main()
