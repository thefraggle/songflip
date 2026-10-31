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

M3U_PLAYLIST_PARAS = {
    "bn-BD": "• প্লেলিস্ট কনভার্টার, ফাইল ইমপোর্ট (M3U) এবং স্থানান্তর:\nস্পটিফাই, অ্যাপল মিউজিক, ইউটিউব মিউজিক, টিআইডিএল এবং ডিজারের মধ্যে প্লেলিস্ট রূপান্তর ও স্থানান্তর করুন। .m3u, .m3u8, .csv এবং টেক্সট ফাইল থেকে সরাসরি স্ট্রিমিং প্লেলিস্টে গান ইমপোর্ট করুন — স্বয়ংক্রিয় ডুপ্লিকেট পরিষ্কার, 200টি পর্যন্ত ট্র্যাক এবং দ্রুত ক্যাশিং সহ।",
    "cs-CZ": "• Převodník playlistů, import souborů (M3U) a přenos:\nPřevádějte a přenášejte playlisty mezi Spotify, Apple Music, YouTube Music, TIDAL a Deezer. Importujte lokální seznamy z .m3u, .m3u8, .csv a textových souborů přímo do streamovacích playlistů – s čištěním duplicit, až 200 stopami a rychlou mezipamětí.",
    "da-DK": "• Playlist Converter, filimport (M3U) og overførsel:\nKonverter og overfør afspilningslister mellem Spotify, Apple Music, YouTube Music, TIDAL og Deezer. Importer lokale sporlister fra .m3u-, .m3u8-, .csv- og tekstfiler direkte til streaming-afspilningslister – med duplikatrensning, op til 200 numre og hurtig caching.",
    "de-DE": "• Playlist Converter, Datei-Import (M3U) & Transfer:\nPlaylists konvertieren und Playlists übertragen zwischen Spotify, Apple Music, YouTube Music, TIDAL und Deezer. Importiere lokale Tracklisten aus .m3u-, .m3u8-, .csv- und Textdateien direkt in deine Streaming-Wiedergabelisten – inklusive Duplikat-Bereinigung, bis zu 200 Tracks und schnellem Cache.",
    "el-GR": "• Μετατροπέας playlist, εισαγωγή αρχείων (M3U) & μεταφορά:\nΜετατρέψτε και μεταφέρετε λίστες αναπαραγωγής μεταξύ Spotify, Apple Music, YouTube Music, TIDAL και Deezer. Εισαγάγετε τοπικές λίστες από αρχεία .m3u, .m3u8, .csv και κειμένου απευθείας σε λίστες streaming (έως 200 κομμάτια).",
    "en-GB": "• Playlist Converter, File Import (M3U) & Transfer:\nConvert playlists and transfer playlists track-by-track between Spotify, Apple Music, YouTube Music, TIDAL, and Deezer. Import local tracklists from .m3u, .m3u8, .csv, and plain text files directly into streaming queues—with duplicate cleaning, up to 200 tracks, and fast caching.",
    "en-US": "• Playlist Converter, File Import (M3U) & Transfer:\nConvert playlists and transfer playlists track-by-track between Spotify, Apple Music, YouTube Music, TIDAL, and Deezer. Import local tracklists from .m3u, .m3u8, .csv, and plain text files directly into streaming queues—with duplicate cleaning, up to 200 tracks, and fast caching.",
    "es-419": "• Convertidor de listas, importación (M3U) y transferencia:\nConvierta y transfiera listas de reproducción entre Spotify, Apple Music, YouTube Music, TIDAL y Deezer. Importe listas desde archivos .m3u, .m3u8, .csv y texto directamente a sus listas de streaming (con limpieza de duplicados y hasta 200 pistas).",
    "es-ES": "• Convertidor de listas, importación (M3U) y transferencia:\nConvierta y transfiera listas de reproducción entre Spotify, Apple Music, YouTube Music, TIDAL y Deezer. Importe listas desde archivos .m3u, .m3u8, .csv y texto directamente a sus listas de streaming (con limpieza de duplicados y hasta 200 pistas).",
    "es-US": "• Convertidor de listas, importación (M3U) y transferencia:\nConvierta y transfiera listas de reproducción entre Spotify, Apple Music, YouTube Music, TIDAL y Deezer. Importe listas desde archivos .m3u, .m3u8, .csv y texto directamente a sus listas de streaming (con limpieza de duplicados y hasta 200 pistas).",
    "fi-FI": "• Soittolistamuunnin, tiedostojen tuonti (M3U) ja siirto:\nMuunna ja siirrä soittolistoja Spotifyn, Apple Musicin, YouTube Musicin, TIDALin ja Deezerin välillä. Tuo paikallisia kappalelistoja .m3u-, .m3u8-, .csv- ja tekstitiedostoista suoraan suoratoistosoittolistoille – kaksoiskappaleiden puhdistuksella ja jopa 200 kappaleella.",
    "fr-CA": "• Convertisseur de playlist, import de fichiers (M3U) et transfert :\nConvertissez et transférez des listes de lecture entre Spotify, Apple Music, YouTube Music, TIDAL et Deezer. Importez des listes locales depuis des fichiers .m3u, .m3u8, .csv et texte directement dans vos playlists de streaming (jusqu'à 200 pistes).",
    "fr-FR": "• Convertisseur de playlist, import de fichiers (M3U) et transfert :\nConvertissez et transférez des listes de lecture entre Spotify, Apple Music, YouTube Music, TIDAL et Deezer. Importez des listes locales depuis des fichiers .m3u, .m3u8, .csv et texte directement dans vos playlists de streaming (jusqu'à 200 pistes).",
    "hi-IN": "• प्लेलिस्ट कनवर्टर, फ़ाइल इम्पोर्ट (M3U) और ट्रांसफर:\nSpotify, Apple Music, YouTube Music, TIDAL और Deezer के बीच प्लेलिस्ट कनवर्ट व ट्रांसफर करें। .m3u, .m3u8, .csv और टेक्स्ट फ़ाइलों से सीधे स्ट्रीमिंग प्लेलिस्ट में ट्रैक इम्पोर्ट करें — डुप्लिकेट क्लीनिंग, 200 ट्रैक तक और तेज़ कैशिंग के साथ।",
    "hu-HU": "• Lejátszási lista konvertáló, fájlimport (M3U) és átvitel:\nKonvertálj és vigyél át lejátszási listákat a Spotify, Apple Music, YouTube Music, TIDAL és Deezer között. Importálj helyi zenelistákat .m3u, .m3u8, .csv és szöveges fájlokból közvetlenül a streaming listákba (akár 200 számmal).",
    "id": "• Konverter Playlist, Impor Berkas (M3U) & Transfer:\nKonversi dan transfer daftar putar antara Spotify, Apple Music, YouTube Music, TIDAL, dan Deezer. Impor daftar lagu dari file .m3u, .m3u8, .csv, dan teks langsung ke playlist streaming — dengan pembersihan duplikat dan hingga 200 trek.",
    "it-IT": "• Convertitore playlist, importazione file (M3U) e trasferimento:\nConverti e trasferisci playlist tra Spotify, Apple Music, YouTube Music, TIDAL e Deezer. Importa elenchi di tracce da file .m3u, .m3u8, .csv e di testo direttamente nelle playlist di streaming (fino a 200 brani con pulizia duplicati).",
    "ja-JP": "• プレイリスト変換・ファイルインポート (M3U) と転送：\nSpotify、Apple Music、YouTube Music、TIDAL、Deezer間でプレイリストを相互変換・転送。.m3u、.m3u8、.csv、テキストファイルからローカル曲リストをストリーミング再生キューへ直接インポート（重複自動整理・最大200曲・高速キャッシュ対応）。",
    "ko-KR": "• 플레이리스트 변환, 파일 가져오기 (M3U) 및 전송:\nSpotify, Apple Music, YouTube Music, TIDAL, Deezer 간에 재생목록을 변환하고 전송합니다. .m3u, .m3u8, .csv 및 텍스트 파일의 로컬 트랙 목록을 스트리밍 대기열로 직접 가져옵니다 (중복 정리, 최대 200곡, 빠른 캐싱 지원).",
    "mr-IN": "• प्लेलिस्ट कनव्हर्टर, फाइल इम्पोर्ट (M3U) आणि ट्रान्सफर:\nSpotify, Apple Music, YouTube Music, TIDAL आणि Deezer दरम्यान प्लेलिस्ट रूपांतरित आणि ट्रान्सफर करा. .m3u, .m3u8, .csv आणि टेक्स्ट फाइल्समधून ट्रॅक थेट स्ट्रीमिंग प्लेलिस्टमध्ये आणा — डुप्लिकेट क्लीनिंग आणि २०० ट्रॅकपर्यंत.",
    "nb-NO": "• Spillelistekonverter, filimport (M3U) og overføring:\nKonverter og overfør spillelister mellom Spotify, Apple Music, YouTube Music, TIDAL og Deezer. Importer lokale sporlister fra .m3u-, .m3u8-, .csv- og tekstfiler direkte til strømmespillelister – med automatisk duplikatrensing, opptil 200 spor og rask hurtigbufring.",
    "nl-NL": "• Afspeellijstconverter, bestandsimport (M3U) & overdracht:\nConverteer en draag afspeellijsten over tussen Spotify, Apple Music, YouTube Music, TIDAL en Deezer. Importeer lokale tracklijsten uit .m3u-, .m3u8-, .csv- en tekstbestanden direct naar je streaminglijsten — met duplicatenverwijdering, tot 200 nummers en snelle caching.",
    "pl-PL": "• Konwerter list, import plików (M3U) i przesyłanie:\nKonwertuj i przesyłaj playlisty między Spotify, Apple Music, YouTube Music, TIDAL i Deezer. Importuj lokalne listy utworów z plików .m3u, .m3u8, .csv i tekstowych bezpośrednio do playlist streamingowych (do 200 utworów z usuwaniem duplikatów).",
    "pt-BR": "• Conversor de playlists, importação de arquivos (M3U) e transferência:\nConverta e transfira playlists entre Spotify, Apple Music, YouTube Music, TIDAL e Deezer. Importe listas locais de arquivos .m3u, .m3u8, .csv e texto diretamente para suas playlists de streaming (com limpeza de duplicatas e até 200 faixas).",
    "pt-PT": "• Conversor de playlists, importação de arquivos (M3U) e transferência:\nConverta e transfira playlists entre Spotify, Apple Music, YouTube Music, TIDAL e Deezer. Importe listas locais de arquivos .m3u, .m3u8, .csv e texto diretamente para suas playlists de streaming (com limpeza de duplicatas e até 200 faixas).",
    "ro-RO": "• Convertor de playlisturi, import fișiere (M3U) și transfer:\nConvertiți și transferați liste de redare între Spotify, Apple Music, YouTube Music, TIDAL și Deezer. Importați liste locale din fișiere .m3u, .m3u8, .csv și text direct în playlisturile de streaming (până la 200 de piese).",
    "ru-RU": "• Конвертер плейлистов, импорт файлов (M3U) и перенос:\nКонвертируйте и переносите плейлисты между Spotify, Apple Music, YouTube Music, TIDAL и Deezer. Импортируйте списки треков из файлов .m3u, .m3u8, .csv и текстовых файлов прямо в стриминговые плейлисты (до 200 треков с очисткой дубликатов).",
    "sv-SE": "• Spellistkonverterare, filimport (M3U) & överföring:\nKonvertera och överför spellistor mellan Spotify, Apple Music, YouTube Music, TIDAL och Deezer. Importera lokala spårlistor från .m3u-, .m3u8-, .csv- och textfiler direkt till strömningsspellistor – med dubblettrensning, upp till 200 spår och snabb cachelagring.",
    "th-TH": "• ตัวแปลงเพลย์ลิสต์, การนำเข้าไฟล์ (M3U) และการโอนย้าย:\nแปลงและถ่ายโอนเพลย์ลิสต์ระหว่าง Spotify, Apple Music, YouTube Music, TIDAL และ Deezer นำเข้ารายชื่อเพลงจากไฟล์ .m3u, .m3u8, .csv และข้อความโดยตรงไปยังเพลย์ลิสต์สตรีมมิ่ง (ลบเพลงซ้ำอัตโนมัติ สูงสุด 200 เพลง).",
    "tr-TR": "• Çalma Listesi Dönüştürücü, Dosya İçe Aktarma (M3U) ve Aktarım:\nÇalma listelerini Spotify, Apple Music, YouTube Music, TIDAL ve Deezer arasında dönüştürün ve aktarın. .m3u, .m3u8, .csv ve metin dosyalarından parça listelerini doğrudan akış çalma listelerine aktarın (çift temizleme ve 200 parçaya kadar).",
    "uk": "• Конвертер плейлистів, імпорт файлів (M3U) та перенесення:\nКонвертуйте та переносьте плейлисти між Spotify, Apple Music, YouTube Music, TIDAL і Deezer. Імпортуйте трек-листи з файлів .m3u, .m3u8, .csv та текстових файлів безпосередньо у стримінгові плейлисти (до 200 треків з очищенням дублікатів).",
    "vi": "• Bộ chuyển đổi danh sách phát, nhập tệp (M3U) & chuyển tiếp:\nChuyển đổi và chuyển danh sách phát giữa Spotify, Apple Music, YouTube Music, TIDAL và Deezer. Nhập danh sách bài hát từ tệp .m3u, .m3u8, .csv và văn bản trực tiếp vào danh sách phát phát trực tuyến (tự động xóa bài trùng, tối đa 200 bài).",
    "zh-CN": "• 播放列表转换、文件导入 (M3U) 与跨平台迁移：\n在 Spotify、Apple Music、YouTube Music、TIDAL 和 Deezer 之间自由转换和迁移播放列表。支持从 .m3u、.m3u8、.csv 和纯文本文件中直接导入本地歌单至流媒体队列（自动清理重复曲目、支持多达 200 首曲目并具备高速缓存）。",
    "zh-TW": "• 播放清單轉換、檔案匯入 (M3U) 與跨平台轉移：\n在 Spotify、Apple Music、YouTube Music、TIDAL 和 Deezer 之間自由轉換與轉移播放清單。支援從 .m3u、.m3u8、.csv 及純文字檔案直接匯入本機歌單至串流佇列（自動清理重複曲目、支援多達 200 首曲目並具備高速快取）。",
}

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
Privacy Policy: https://songflip.link/privacy"""

IOS_EN_KEYWORDS = "spotify,apple,youtube,transfer,deezer,beatport,qobuz,tidal,soundcloud,shazam,bandcamp,amazon,song"
IOS_DE_KEYWORDS = "spotify,apple,youtube,transfer,deezer,beatport,qobuz,tidal,soundcloud,shazam,redirect,amazon,song"

QUOTE_CHARS = {'"', "'", "“", "”", "‘", "’", "«", "»"}

def strip_framing_quotes(text):
    t = text.strip()
    changed = True
    while changed and len(t) > 1:
        changed = False
        if t[0] in QUOTE_CHARS and t[-1] in QUOTE_CHARS:
            t = t[1:-1].strip()
            changed = True
        elif t[0] in QUOTE_CHARS:
            t = t[1:].strip()
            changed = True
        elif t[-1] in QUOTE_CHARS:
            t = t[:-1].strip()
            changed = True
    return t

def utf16_len(s):
    return len(s.encode('utf-16-le')) // 2

def trim_description_if_needed(candidate, paras):
    if utf16_len(candidate) <= 3980:
        return candidate
    # Trim secondary sentences from bullets to keep strictly under 3980
    for idx in (13, 12, 11, 14, 15, 7, 6, 9, 8):
        if idx >= len(paras):
            continue
        if utf16_len(candidate) <= 3975:
            break
        lines = paras[idx].split('\n')
        if len(lines) > 1:
            sentences = re.split(r'(?<=[.!।?？])\s+', lines[1].strip())
            if len(sentences) > 1:
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
    print("Reading and updating play store listings...")
    with open(CSV_PATH, 'r', encoding='utf-8') as f:
        rows = list(csv.DictReader(f))

    updated_rows = []
    for r in rows:
        loc = r['Language']
        title = strip_framing_quotes(r['Title'])
        short_desc = strip_framing_quotes(r['Short description'])
        full_desc = strip_framing_quotes(r['Full description'])

        paras = full_desc.split('\n\n')
        idx = 8 if loc in ('de-DE', 'en-US', 'en-GB') else 9

        if loc in M3U_PLAYLIST_PARAS:
            paras[idx] = M3U_PLAYLIST_PARAS[loc]

        new_full = '\n\n'.join(paras)
        new_full = trim_description_if_needed(new_full, paras)
        new_full = strip_framing_quotes(new_full)

        # Final assertion against framing quotes
        assert new_full[0] not in QUOTE_CHARS, f"[{loc}] Full description still starts with quote: {new_full[:10]}"
        assert new_full[-1] not in QUOTE_CHARS, f"[{loc}] Full description still ends with quote: {new_full[-10:]}"
        assert title[0] not in QUOTE_CHARS and title[-1] not in QUOTE_CHARS, f"[{loc}] Title framed by quote: {title}"
        assert short_desc[0] not in QUOTE_CHARS and short_desc[-1] not in QUOTE_CHARS, f"[{loc}] Short desc framed by quote: {short_desc}"

        t_len = utf16_len(title)
        s_len = utf16_len(short_desc)
        f_len = utf16_len(new_full)

        assert t_len <= 30, f"[{loc}] Title exceeds 30: {t_len}"
        assert s_len <= 80, f"[{loc}] Short desc exceeds 80: {s_len}"
        assert f_len <= 4000, f"[{loc}] Full desc exceeds 4000: {f_len}"

        print(f"✓ {loc:7s} | Title: {t_len:2d}/30 | Short: {s_len:2d}/80 | Full: {f_len:4d}/4000")
        updated_rows.append({
            'Language': loc,
            'Title': title,
            'Short description': short_desc,
            'Full description': new_full
        })

    # 1. Write clean CSV
    print(f"Writing updated CSV to {CSV_PATH}...")
    with open(CSV_PATH, 'w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['Language', 'Title', 'Short description', 'Full description'])
        writer.writeheader()
        writer.writerows(updated_rows)

    # 2. Write clean Markdown
    print(f"Writing clean Markdown to {MD_PATH}...")
    with open(MD_PATH, 'w', encoding='utf-8') as f:
        f.write("# 📱 SongFlip – Play Store & App Store Listings (Alle Sprachen)\n")
        f.write("> **Version:** 1.12.3 | **Umfang:** 34 Play Store-Sprachen + iOS App Store (DE/EN) | **Zertifiziert:** Titel ≤ 30 Zeichen, Kurzbeschreibung ≤ 80 Zeichen, Volltext ≤ 4.000 Zeichen (UTF-16), iOS Keywords ≤ 100 Zeichen. Ohne umschließende Anführungszeichen.\n")
        f.write("---\n\n")
        for r in updated_rows:
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

    # 3. Update fastlane/metadata/android files
    print("Syncing fastlane/metadata/android files...")
    row_map = {r['Language']: r for r in updated_rows}
    for r in updated_rows:
        loc = r['Language']
        loc_dir = os.path.join(ANDROID_METADATA_DIR, loc)
        if os.path.exists(loc_dir):
            with open(os.path.join(loc_dir, "full_description.txt"), 'w', encoding='utf-8') as f:
                f.write(r['Full description'].strip() + "\n")
            with open(os.path.join(loc_dir, "short_description.txt"), 'w', encoding='utf-8') as f:
                f.write(r['Short description'].strip() + "\n")
            with open(os.path.join(loc_dir, "title.txt"), 'w', encoding='utf-8') as f:
                f.write(r['Title'].strip() + "\n")

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

    # 4. Update iOS metadata
    print("Syncing iOS fastlane metadata...")
    with open(os.path.join(IOS_METADATA_DIR, "de-DE", "description.txt"), 'w', encoding='utf-8') as f:
        f.write(IOS_DE_DESC.strip() + "\n")
    with open(os.path.join(IOS_METADATA_DIR, "en-US", "description.txt"), 'w', encoding='utf-8') as f:
        f.write(IOS_EN_DESC.strip() + "\n")
    with open(os.path.join(IOS_METADATA_DIR, "de-DE", "keywords.txt"), 'w', encoding='utf-8') as f:
        f.write(IOS_DE_KEYWORDS.strip() + "\n")
    with open(os.path.join(IOS_METADATA_DIR, "en-US", "keywords.txt"), 'w', encoding='utf-8') as f:
        f.write(IOS_EN_KEYWORDS.strip() + "\n")

    # 5. Update scripts/generate_ios_store_listings.py
    if os.path.exists(GEN_IOS_SCRIPT):
        with open(GEN_IOS_SCRIPT, 'r', encoding='utf-8') as f:
            script_code = f.read()

        pattern_en = r"('en-US':\s*\{.*?'description':\s*\"\"\").*?(\"\"\",)"
        script_code = re.sub(pattern_en, r"\g<1>" + IOS_EN_DESC + r"\g<2>", script_code, flags=re.DOTALL)
        
        pattern_de = r"('de-DE':\s*\{.*?'description':\s*\"\"\").*?(\"\"\",)"
        script_code = re.sub(pattern_de, r"\g<1>" + IOS_DE_DESC + r"\g<2>", script_code, flags=re.DOTALL)

        with open(GEN_IOS_SCRIPT, 'w', encoding='utf-8') as f:
            f.write(script_code)

    print("\n🎉 SUCCESS: All Play Store listings (CSV & MD) and iOS metadata are 100% updated, feature-complete (incl. M3U import) and free of framing quotes!")

if __name__ == '__main__':
    main()
