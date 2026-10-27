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
Musik erkennen in Bars, Clubs oder im Radio per Mikrofon: Songs blitzschnell identifizieren, im Verlauf speichern und direkt im Wunsch-Player öffnen. Dank Offline-Modus werden Audio-Fingerabdrücke auch ohne Empfang gesichert und automatisch aufgelöst, sobald du wieder online bist.

• Direkte Titelsuche & Sofort-Wiedergabe:
Suche Songs & Interpreten direkt in SongFlip und starte die Wiedergabe per Fast Flip sofort im Ziel-Player – ganz ohne bestehende Web-URL oder leere Suchseiten.

• Musik per QR-Code teilen & scannen:
Generiere und scanne universelle Musik-QR-Codes. Teile Songs & Alben vor Ort – egal welcher Streamingdienst genutzt wird.

• Universal Playlist Converter & Playlist-Transfer:
Playlists konvertieren und Playlists übertragen zwischen Spotify, Apple Music, YouTube Music, TIDAL und Deezer – inklusive Duplikat-Bereinigung, bis zu 200 Tracks und schnellem Cache.

• Podcast Converter & Podcast-Weiterleitung:
Teile Podcast-Shows und Episoden plattformübergreifend. SongFlip löst Episodentitel für die Podcast-Umleitung automatisch auf und startet sie direkt im bevorzugten Podcast-Player.

• Audiophile Qualität (Hi-Res & Lossless) & Favoriten:
Bevorzuge Studio-Originale, 24-Bit Hi-Res, Lossless & Dolby Atmos statt Radio-Edits und Compilations. Wähle Live- oder Studio-Versionen und pinne Favoriten mit 1-Tap an.

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

• Material You Design, AMOLED & Promo-Codes:
An dein Android-Farbschema angepasst (Dynamic Color, AMOLED-Dark-Mode, Monochrome-Icon) inklusive Einlösung von Aktionscodes für PRO-Funktionen.

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

• Listen & Flip – Song Recognizer & Audio Recognition (Online & Offline):
Identify songs playing around you in clubs, bars, or on the radio with instant song recognizer matching, save them to your history, and open them in your favorite player. With Offline Mode, capture audio fingerprints even without reception and resolve tracks automatically once you're back online.

• Direct Track Search & Instant Playback:
Search for tracks and artists directly inside SongFlip and trigger playback instantly in your preferred streaming player with 1-tap Fast Flip—no existing URL or empty search pages.

• QR Code Track Sharing & Scanning:
Generate and scan universal QR codes for songs and albums. Effortlessly share songs with friends in person across any streaming service.

• Universal Playlist Converter & Playlist Transfer:
Convert playlists and transfer playlists track-by-track between Spotify, Apple Music, YouTube Music, TIDAL, and Deezer—with automatic duplicate cleaning, up to 200 tracks, and fast caching.

• Podcast Converter & Podcast Redirect Engine:
Share full podcast shows or episodes across platforms. SongFlip resolves episode titles automatically to convert and open podcast links right inside your favorite podcast player.

• Audiophile Quality (Hi-Res & Lossless) & Favorites:
Prioritize studio masters, 24-bit Hi-Res, Lossless, and Dolby Atmos over radio edits and compilations. Refine live or remaster matches and pin favorites with 1 tap.

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

• Material You, AMOLED Dark Mode & Promo Codes:
Follows your system accent colors with AMOLED dark mode, a monochrome home screen icon, and built-in promo code redemption for PRO perks.

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

AUDIOPHILE_P10 = {
    "bn-BD": "• অডিওফাইল কোয়ালিটি (Hi-Res ও Lossless) ও फेভারিট:\nরেডিও এডিট ও সংকলনের বদলে স্টুডিও মাস্টার, 24-bit Hi-Res, Lossless ও Dolby Atmos প্রাধান্য দিন। ১ ট্যাপে প্রিয় গান পিন করুন।".replace("फेভারিট", "ফেভারিট"),
    "cs-CZ": "• Audiofilská kvalita (Hi-Res a Lossless) a oblíbené:\nUpřednostněte studiové mastery, 24-bit Hi-Res, Lossless a Dolby Atmos před rádiovými verzemi a kompilacemi. Připněte oblíbené skladby 1 klepnutím.",
    "da-DK": "• Audiofil kvalitet (Hi-Res & Lossless) & favoritter:\nForetræk studiemasters, 24-bit Hi-Res, Lossless & Dolby Atmos frem for radio-edits og opsamlinger. Fastgør yndlingssange i historikken med 1 tryk.",
    "de-DE": "• Audiophile Qualität (Hi-Res & Lossless) & Favoriten:\nBevorzuge Studio-Originale, 24-Bit Hi-Res, Lossless & Dolby Atmos statt Radio-Edits und Compilations. Wähle Live- oder Studio-Versionen und pinne Favoriten mit 1-Tap an.",
    "el-GR": "• Ποιότητα Audiophile (Hi-Res & Lossless) & Αγαπημένα:\nΠροτιμήστε αυθεντικά studio masters, 24-bit Hi-Res, Lossless & Dolby Atmos έναντι radio edits και συλλογών. Καρφιτσώστε αγαπημένα με 1 πάτημα.",
    "en-GB": "• Audiophile Quality (Hi-Res & Lossless) & Favorites:\nPrioritize studio masters, 24-bit Hi-Res, Lossless, and Dolby Atmos over radio edits and compilations. Refine live or remaster matches and pin favorites with 1 tap.",
    "en-US": "• Audiophile Quality (Hi-Res & Lossless) & Favorites:\nPrioritize studio masters, 24-bit Hi-Res, Lossless, and Dolby Atmos over radio edits and compilations. Refine live or remaster matches and pin favorites with 1 tap.",
    "es-419": "• Calidad audiófila (Hi-Res y Lossless) y favoritos:\nPrioriza masters de estudio, 24-bit Hi-Res, Lossless y Dolby Atmos frente a ediciones de radio y recopilatorios. Elige versiones y fija favoritos con 1 toque.",
    "es-ES": "• Calidad audiófila (Hi-Res y Lossless) y favoritos:\nPrioriza masters de estudio, 24-bit Hi-Res, Lossless y Dolby Atmos frente a ediciones de radio y recopilatorios. Elige versiones y fija favoritos con 1 toque.",
    "es-US": "• Calidad audiófila (Hi-Res y Lossless) y favoritos:\nPrioriza masters de estudio, 24-bit Hi-Res, Lossless y Dolby Atmos frente a ediciones de radio y recopilatorios. Elige versiones y fija favoritos con 1 toque.",
    "fi-FI": "• Audiofiilinen laatu (Hi-Res & Lossless) ja suosikit:\nSuosi studiomastereita, 24-bit Hi-Res-, Lossless- ja Dolby Atmos -versioita radioversioiden ja kokoelmien sijaan. Kiinnitä suosikkikappaleesi 1 napautuksella.",
    "fr-CA": "• Qualité audiophile (Hi-Res & Lossless) et favoris :\nPrivilégiez les masters studio, 24-bit Hi-Res, Lossless et Dolby Atmos aux versions radio et compilations. Épinglez vos titres favoris en 1 clic.",
    "fr-FR": "• Qualité audiophile (Hi-Res & Lossless) et favoris :\nPrivilégiez les masters studio, 24-bit Hi-Res, Lossless et Dolby Atmos aux versions radio et compilations. Épinglez vos titres favoris en 1 clic.",
    "hi-IN": "• ऑडियोफाइल क्वालिटी (Hi-Res व Lossless) और पसंदीदा:\nरेडियो एडिट और संकलन के बजाय स्टूडियो मास्टर, 24-bit Hi-Res, Lossless और Dolby Atmos को प्राथमिकता दें। 1-टैप में पसंदीदा गाने पिन करें।",
    "hu-HU": "• Audiofil minőség (Hi-Res & Lossless) és kedvencek:\nStúdió masterek, 24-bit Hi-Res, Lossless és Dolby Atmos előnyben részesítése a rádióverziókkal és válogatásokkal szemben. Kedvencek rögzítése 1 érintéssel.",
    "id": "• Kualitas Audiofil (Hi-Res & Lossless) & Favorit:\nUtamakan master studio, 24-bit Hi-Res, Lossless & Dolby Atmos dibanding radio edit dan kompilasi. Pilih versi lagu dan sematkan favorit dengan 1 ketukan.",
    "it-IT": "• Qualità audiofila (Hi-Res & Lossless) e preferiti:\nPreferisci master in studio, 24-bit Hi-Res, Lossless e Dolby Atmos rispetto a radio edit e compilation. Scegli tra versioni live o studio e fissa i preferiti con 1 tocco.",
    "ja-JP": "• オーディオファイル音質優先 (Hi-Res & Lossless) とお気に入り：\nラジオ編集版やコンピレーションよりも、スタジオマスター、24-bit Hi-Res、Lossless、Dolby Atmosを優先マッチング。ライブ版やリマスター版の選択やワンタップお気に入り登録にも対応。",
    "ko-KR": "• 오디오파일 음질 우선 (Hi-Res & Lossless) 및 즐겨찾기:\n라디오 에디트 및 컴필레이션 대신 스튜디오 마스터, 24-bit Hi-Res, Lossless, Dolby Atmos 버전을 우선 매칭합니다. 라이브·리마스터 버전 선택 및 원탭 즐겨찾기를 지원합니다.",
    "mr-IN": "• ऑडिओफाइल गुणवत्ता (Hi-Res व Lossless) आणि आवडते:\nरेडिओ एडिट आणि संकलनाऐवजी स्टुडिओ मास्टर, 24-bit Hi-Res, Lossless आणि Dolby Atmos ला प्राधान्य द्या. १-टॅपने आवडती गाणी पिन करा.",
    "nb-NO": "• Audiofil kvalitet (Hi-Res & Lossless) og favoritter:\nForetrekk studiomastere, 24-bit Hi-Res, Lossless & Dolby Atmos fremfor radio-edits og samlealbum. Fest favorittsanger i historikken med 1 trykk.",
    "nl-NL": "• Audiofiele kwaliteit (Hi-Res & Lossless) & favorieten:\nGeef voorkeur aan studiomasters, 24-bit Hi-Res, Lossless & Dolby Atmos boven radio-edits en compilaties. Zet je favoriete nummers met 1 tik vast.",
    "pl-PL": "• Jakość audiofilska (Hi-Res & Lossless) i ulubione:\nPreferuj wydania studyjne, 24-bit Hi-Res, Lossless i Dolby Atmos zamiast wersji radiowych i składanek. Przypinaj ulubione utwory 1 dotknięciem.",
    "pt-BR": "• Qualidade audiófila (Hi-Res e Lossless) e favoritos:\nPriorize masters de estúdio, 24-bit Hi-Res, Lossless e Dolby Atmos em vez de edições de rádio e coletâneas. Escolha versões e fixe favoritos com 1 toque.",
    "pt-PT": "• Qualidade audiófila (Hi-Res e Lossless) e favoritos:\nPriorize masters de estúdio, 24-bit Hi-Res, Lossless e Dolby Atmos em vez de edições de rádio e coletâneas. Escolha versões e fixe favoritos com 1 toque.",
    "ro-RO": "• Calitate audiofilă (Hi-Res & Lossless) și favorite:\nPreferă masterele de studio, 24-bit Hi-Res, Lossless și Dolby Atmos în locul edițiilor radio și compilațiilor. Fixează melodiile preferate cu o atingere.",
    "ru-RU": "• Аудиофильское качество (Hi-Res и Lossless) и избранное:\nПриоритет студийных оригиналов, 24-bit Hi-Res, Lossless и Dolby Atmos вместо радио-версий и сборников. Закрепляйте любимые треки в 1 касание.",
    "sv-SE": "• Audiofil kvalitet (Hi-Res & Lossless) och favoriter:\nFöredra studiomasters, 24-bit Hi-Res, Lossless & Dolby Atmos framför radio-edits och samlingar. Fäst dina favoritlåtar i historiken med 1 tryck.",
    "th-TH": "• คุณภาพเสียง Audiophile (Hi-Res & Lossless) และรายการโปรด:\nเลือกต้นฉบับสตูดิโอ, 24-bit Hi-Res, Lossless และ Dolby Atmos ก่อนเวอร์ชันวิทยุและอัลบั้มรวมเพลง พร้อมปักหมุดเพลงโปรดใน 1 แตะ",
    "tr-TR": "• Odyofil Kalitesi (Hi-Res & Lossless) ve Favoriler:\nRadyo düzenlemeleri ve derlemeler yerine stüdyo kayıtlarını, 24-bit Hi-Res, Lossless ve Dolby Atmos sürümlerini tercih edin. Favorileri tek dokunuşla sabitleyin.",
    "uk": "• Аудіофільська якість (Hi-Res і Lossless) та вибране:\nПріоритет студійних оригіналів, 24-bit Hi-Res, Lossless і Dolby Atmos замість радіо-версій та збірок. Закріплюйте улюблені пісні в 1 дотик.",
    "vi": "• Chất lượng Audiophile (Hi-Res & Lossless) & Yêu thích:\nƯu tiên bản thu phòng thu, 24-bit Hi-Res, Lossless & Dolby Atmos hơn bản radio edit và tuyển tập. Ghim bài hát yêu thích chỉ bằng 1 chạm.",
    "zh-CN": "• 发烧级音质优先 (Hi-Res & Lossless) 与一键收藏：\n优先匹配录音室母带、24-bit Hi-Res、无损 (Lossless) 与杜比全景声 (Dolby Atmos) 版本，自动避开电台剪辑与合辑。支持在现场版、重制版间自由切换并一键收藏喜爱的歌曲。",
    "zh-TW": "• 發燒級音質優先 (Hi-Res & Lossless) 與一鍵收藏：\n優先配對錄音室母帶、24-bit Hi-Res、無損 (Lossless) 與杜比全景聲 (Dolby Atmos) 版本，自動避開電台剪輯與合輯。支援在現場版、重製版間自由切換並一鍵收藏喜愛的歌曲。",
}

def optimize_locale_description(desc, locale):
    if locale == 'de-DE':
        return DE_FULL_DESC
    if locale in ('en-US', 'en-GB'):
        return EN_FULL_DESC

    paras = desc.split('\n\n')
    if len(paras) not in (19, 20):
        raise ValueError(f"[{locale}] Unexpected paragraph count {len(paras)}")

    paras[4] = update_platforms_para(paras[4])
    paras[10] = AUDIOPHILE_P10[locale]

    candidate = '\n\n'.join(paras)

    # Trim second sentence in secondary bullets if > 3985 UTF-16 units
    if utf16_len(candidate) > 3985:
        for idx in (13, 12, 11, 14, 15, 7, 6, 9, 8):
            if idx >= len(paras):
                continue
            if utf16_len(candidate) <= 3975:
                break
            lines = paras[idx].split('\n')
            if len(lines) > 1:
                sentences = re.split(r'(?<=[.!।?？])\s+', lines[1].strip())
                if len(sentences) > 1:
                    # Keep the longer / more descriptive sentence (usually sentences[1] if sentences[0] is a short question)
                    kept = sentences[1].strip() if lines[1].strip().startswith(('¿', 'Copied', 'Zkopírovali', 'Αντιγράψατε', 'Скопировали', 'Скопіювали', 'Sao chép')) or sentences[0].endswith('?') else sentences[0].strip()
                    lines[1] = kept
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
