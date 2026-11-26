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
    "ar": "• محوّل قوائم التشغيل واستيراد الملفات (M3U) مجاناً:\nحوّل وانقل قوائم التشغيل مقطعاً بمقطع بين Spotify وApple Music وYouTube Music وTIDAL وDeezer. استورد قوائم الأغاني من ملفات .m3u و.m3u8 و.csv والنصوص العادية مباشرة — مع إزالة التكرارات وحتى 200 أغنية.",
    "bn-BD": "• প্লেলিস্ট কনভার্টার, ফাইল ইমপোর্ট (M3U) এবং স্থানান্তর:\nস্পটিফাই, অ্যাপল মিউজিক, ইউটিউব মিউজিক, টিআইডিএল এবং ডিজারের মধ্যে প্লেলিস্ট রূপান্তর ও স্থানান্তর করুন। .m3u, .m3u8, .csv এবং টেক্সট ফাইল থেকে সরাসরি স্ট্রিমিং প্লেলিস্টে গান ইমপোর্ট করুন — স্বয়ংক্রিয় ডুপ্লিকেট পরিষ্কার, 200টি পর্যন্ত ট্র্যাক এবং দ্রুত ক্যাশিং সহ।",
    "cs-CZ": "• Převodník playlistů, import souborů (M3U) a přenos:\nPřevádějte a přenášejte playlisty mezi Spotify, Apple Music, YouTube Music, TIDAL a Deezer. Importujte lokální seznamy z .m3u, .m3u8, .csv a textových souborů přímo do streamovacích playlistů – s čištěním duplicit, až 200 stopami a rychlou mezipamětí.",
    "da-DK": "• Playlist Converter, filimport (M3U) og overførsel:\nKonverter og overfør afspilningslister mellem Spotify, Apple Music, YouTube Music, TIDAL og Deezer. Importer lokale sporlister fra .m3u-, .m3u8-, .csv- og tekstfiler direkte til streaming-afspilningslister – med duplikatrensning, op til 200 numre og hurtig caching.",
    "de-DE": "• Universal Playlist Converter, Datei-Import (M3U) & Musik Transfer:\nPlaylists konvertieren, Playlist übertragen kostenlos & Spotify zu Apple Playlist transferieren – zwischen Spotify, Apple Music, YouTube Music, TIDAL und Deezer. Importiere lokale Tracklisten (.m3u, .m3u8, .csv, Text) direkt in deine Playlists – inklusive Duplikat-Bereinigung, Ghost-Track-Filter, bis zu 200 Tracks und Cache.",
    "el-GR": "• Μετατροπέας playlist, εισαγωγή αρχείων (M3U) & μεταφορά:\nΜετατρέψτε και μεταφέρετε λίστες αναπαραγωγής μεταξύ Spotify, Apple Music, YouTube Music, TIDAL και Deezer. Εισαγάγετε τοπικές λίστες από αρχεία .m3u, .m3u8, .csv και κειμένου απευθείας σε λίστες streaming (έως 200 κομμάτια).",
    "en-GB": "• Universal Playlist Converter, File Import (M3U) & Playlist Transfer App (Free):\nConvert playlists, import liked songs, and transfer playlists track-by-track between Spotify, Apple Music, YouTube Music, TIDAL, Qobuz, and Deezer. Import local tracklists from .m3u, .m3u8, .csv, and plain text files directly into streaming queues—with duplicate cleaning, up to 200 tracks, and fast caching.",
    "en-IN": "• Universal Playlist Converter, File Import (M3U) & Playlist Transfer App (Free):\nConvert playlists, import liked songs, and transfer playlists track-by-track between Spotify, Apple Music, YouTube Music, TIDAL, Qobuz, and Deezer. Import local tracklists from .m3u, .m3u8, .csv, and plain text files directly into streaming queues—with duplicate cleaning, up to 200 tracks, and fast caching.",
    "en-US": "• Universal Playlist Converter, File Import (M3U) & Playlist Transfer App (Free):\nConvert playlists, import liked songs, and transfer playlists track-by-track between Spotify, Apple Music, YouTube Music, TIDAL, Qobuz, and Deezer. Import local tracklists from .m3u, .m3u8, .csv, and plain text files directly into streaming queues—with duplicate cleaning, up to 200 tracks, and fast caching.",
    "es-419": "• Convertidor de listas, importación (M3U) y transferencia:\nConvierte y pasa tu playlist de Spotify a YouTube Music, Apple Music, TIDAL, Qobuz o Deezer pista por pista (pasar playlist de Spotify a YouTube). Importa listas desde archivos .m3u, .m3u8, .csv y texto directamente a tus colas de streaming (con limpieza de duplicados y hasta 200 pistas).",
    "es-ES": "• Convertidor de listas, importación (M3U) y transferencia:\nConvierte y pasa tu playlist de Spotify a YouTube Music, Apple Music, TIDAL, Qobuz o Deezer pista por pista (pasar playlist de Spotify a YouTube). Importa listas desde archivos .m3u, .m3u8, .csv y texto directamente a tus colas de streaming (con limpieza de duplicados y hasta 200 pistas).",
    "es-US": "• Convertidor de listas, importación (M3U) y transferencia:\nConvierta y transfiera listas de reproducción entre Spotify, Apple Music, YouTube Music, TIDAL y Deezer. Importe listas desde archivos .m3u, .m3u8, .csv y texto directamente a sus listas de streaming (con limpieza de duplicados y hasta 200 pistas).",
    "fi-FI": "• Soittolistamuunnin, tiedostojen tuonti (M3U) ja siirto:\nMuunna ja siirrä soittolistoja Spotifyn, Apple Musicin, YouTube Musicin, TIDALin ja Deezerin välillä. Tuo paikallisia kappalelistoja .m3u-, .m3u8-, .csv- ja tekstitiedostoista suoraan suoratoistosoittolistoille – kaksoiskappaleiden puhdistuksella ja jopa 200 kappaleella.",
    "fr-CA": "• Convertisseur de playlist, import de fichiers (M3U) et transfert :\nConvertissez et transférez des listes de lecture entre Spotify, Apple Music, YouTube Music, TIDAL et Deezer. Importez des listes locales depuis des fichiers .m3u, .m3u8, .csv et texte directement dans vos playlists de streaming (jusqu'à 200 pistes).",
    "fr-FR": "• Convertisseur de playlist, import de fichiers (M3U) et transfert :\nConvertissez et transférez vos listes de lecture (transférer playlist Spotify vers Deezer, Qobuz, Apple Music, YouTube Music ou TIDAL) vers votre lecteur favori. Importez des listes locales depuis des fichiers .m3u, .m3u8, .csv et texte directement dans vos playlists de streaming (jusqu'à 200 titres).",
    "hi-IN": "• प्लेलिस्ट कनवर्टर, फ़ाइल इम्पोर्ट (M3U) और ट्रांसफर:\nSpotify, Apple Music, YouTube Music, TIDAL और Deezer के बीच प्लेलिस्ट कनवर्ट व ट्रांसफर करें। .m3u, .m3u8, .csv और टेक्स्ट फ़ाइलों से सीधे स्ट्रीमिंग प्लेलिस्ट में ट्रैक इम्पोर्ट करें — डुप्लिकेट क्लीनिंग, 200 ट्रैक तक और तेज़ कैशिंग के साथ।",
    "hu-HU": "• Lejátszási lista konvertáló, fájlimport (M3U) és átvitel:\nKonvertálj és vigyél át lejátszási listákat a Spotify, Apple Music, YouTube Music, TIDAL és Deezer között. Importálj helyi zenelistákat .m3u, .m3u8, .csv és szöveges fájlokból közvetlenül a streaming listákba (akár 200 számmal).",
    "id": "• Konverter Playlist, Impor Berkas (M3U) & Transfer:\nKonversi, pindah playlist Spotify ke YouTube Music, dan transfer daftar putar antara Spotify, Apple Music, YouTube Music, TIDAL, Qobuz, dan Deezer. Impor daftar lagu dari file .m3u, .m3u8, .csv, dan teks langsung ke playlist streaming — dengan pembersihan duplikat dan hingga 200 trek.",
    "it-IT": "• Convertitore playlist, importazione file (M3U) e trasferimento:\nConverti e trasferisci playlist di canzoni e brani da Spotify a YouTube Music, Apple Music, TIDAL, Qobuz e Deezer (trasferire playlist Spotify). Importa elenchi di tracce da file .m3u, .m3u8, .csv e di testo direttamente nelle playlist di streaming (fino a 200 brani con pulizia duplicati).",
    "ja-JP": "• プレイリスト変換・ファイルインポート (M3U) と転送：\nSpotify、Apple Music、YouTube Music、TIDAL、Deezer間でプレイリストを相互変換・転送。.m3u、.m3u8、.csv、テキストファイルからローカル曲リストをストリーミング再生キューへ直接インポート（重複自動整理・最大200曲・高速キャッシュ対応）。",
    "ko-KR": "• 플레이리스트 변환, 파일 가져오기 (M3U) 및 전송:\nSpotify, Apple Music, YouTube Music, TIDAL, Deezer 간에 재생목록을 변환하고 전송합니다. .m3u, .m3u8, .csv 및 텍스트 파일의 로컬 트랙 목록을 스트리밍 대기열로 직접 가져옵니다 (중복 정리, 최대 200곡, 빠른 캐싱 지원).",
    "mr-IN": "• प्लेलिस्ट कनव्हर्टर, फाइल इम्पोर्ट (M3U) आणि ट्रान्सफर:\nSpotify, Apple Music, YouTube Music, TIDAL आणि Deezer दरम्यान प्लेलिस्ट रूपांतरित आणि ट्रान्सफर करा. .m3u, .m3u8, .csv आणि टेक्स्ट फाइल्समधून ट्रॅक थेट स्ट्रीमिंग प्लेलिस्टमध्ये आणा — डुप्लिकेट क्लीनिंग आणि २०० ट्रॅकपर्यंत.",
    "nb-NO": "• Spillelistekonverter, filimport (M3U) og overføring:\nKonverter og overfør spillelister mellom Spotify, Apple Music, YouTube Music, TIDAL og Deezer. Importer lokale sporlister fra .m3u-, .m3u8-, .csv- og tekstfiler direkte til strømmespillelister – med automatisk duplikatrensing, opptil 200 spor og rask hurtigbufring.",
    "nl-NL": "• Afspeellijstconverter, bestandsimport (M3U) & overdracht:\nConverteer en draag afspeellijsten over tussen Spotify, Apple Music, YouTube Music, TIDAL en Deezer. Importeer lokale tracklijsten uit .m3u-, .m3u8-, .csv- en tekstbestanden direct naar je streaminglijsten — met duplicatenverwijdering, tot 200 nummers en snelle caching.",
    "pl-PL": "• Konwerter list, import plików (M3U) i przesyłanie:\nKonwertuj i przesyłaj playlisty między Spotify, Apple Music, YouTube Music, TIDAL i Deezer. Importuj lokalne listy utworów z plików .m3u, .m3u8, .csv i tekstowych bezpośrednio do playlist streamingowych (do 200 utworów z usuwaniem duplikatów).",
    "pt-BR": "• Conversor de playlists, importação de arquivos (M3U) e transferência:\nConverta e transfira playlists ou passe sua playlist do Spotify para YouTube Music, Apple Music, TIDAL, Qobuz e Deezer. Importe canções e listas locais de arquivos .m3u, .m3u8, .csv e texto diretamente para suas playlists de streaming (com limpeza de duplicatas e até 200 faixas).",
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

LISTEN_AND_FLIP_PARAS = {
    "ar": "• Listen & Flip – التعرّف على الأغاني (متصل وبدون إنترنت):\nتعرّف على الأغاني التي تُعزف حولك في المقاهي أو الراديو خلال ثوانٍ، واحفظها في السجل وافتحها في مشغلك المفضل. مع الوضع غير المتصل، التقط البصمة الصوتية بدون إنترنت وسيتم التعرّف عليها تلقائياً عند عودة الاتصال.",
    "de-DE": "• Listen & Flip – Lieder & Musik erkennen kostenlos (auch offline):\nLieder erkennen, Musik erkennen & Tracks identifizieren in Bars, Clubs oder im Radio: Songs im Verlauf speichern und direkt im Wunsch-Player öffnen. Dank Signalqualitäts-Feedback und Offline-Modus werden Audio-Fingerabdrücke ohne Empfang gesichert und automatisch erkannt, sobald du wieder online bist.",
    "en-US": "• Listen & Flip – Song Recognizer & Song Identifier (Online & Offline):\nIdentify tracks playing around you in clubs, bars, or on the radio with instant audio recognition, save them to your history, and open them in your favorite player. With Offline Mode, capture audio fingerprints even without reception and resolve tracks automatically once you're back online.",
    "en-GB": "• Listen & Flip – Song Recognizer & Song Identifier (Online & Offline):\nIdentify tracks playing around you in clubs, bars, or on the radio with instant audio recognition, save them to your history, and open them in your favorite player. With Offline Mode, capture audio fingerprints even without reception and resolve tracks automatically once you're back online.",
    "en-IN": "• Listen & Flip – Song Recognizer & Song Identifier (Online & Offline):\nIdentify tracks playing around you in clubs, bars, or on the radio with instant audio recognition, save them to your history, and open them in your favorite player. With Offline Mode, capture audio fingerprints even without reception and resolve tracks automatically once you're back online.",
    "es-419": "• Listen & Flip (Reconocimiento musical e identificador de canciones):\nIdentifica canciones y música a tu alrededor en SongFlip. Las canciones se adaptan a tu reproductor favorito y se guardan en el historial. ¿Sin señal? Guarda la huella de audio sin conexión y SongFlip identificará la canción automáticamente al volver a estar en línea.",
    "es-ES": "• Listen & Flip (Reconocimiento musical e identificador de canciones):\nIdentifica canciones y música a tu alrededor en SongFlip. Las canciones se adaptan a tu reproductor favorito y se guardan en el historial. ¿Sin cobertura? Guarda la huella de audio sin conexión y SongFlip identificará la canción automáticamente al volver a conectarte.",
    "es-US": "• Listen & Flip (Reconocimiento musical e identificador de canciones):\nIdentifica canciones y música a tu alrededor en SongFlip. Las canciones se adaptan a tu reproductor favorito y se guardan en el historial. ¿Sin señal? Guarda la huella de audio sin conexión y SongFlip identificará la canción automáticamente al volver a estar en línea.",
    "pt-BR": "• Listen & Flip (Reconhecimento musical e identificador de músicas):\nIdentifique músicas ao seu redor no SongFlip. As faixas são convertidas para o seu player favorito e salvas no histórico. Sem sinal? Salve a assinatura de áudio offline e o SongFlip identificará a música automaticamente quando você voltar a ficar online.",
    "pt-PT": "• Listen & Flip (Reconhecimento musical e identificador de músicas):\nIdentifique músicas ao seu redor no SongFlip. As faixas são convertidas para o seu leitor favorito e guardadas no histórico. Sem rede? Guarde a assinatura de áudio offline e o SongFlip identificará a música automaticamente quando voltar a estar online.",
    "nl-NL": "• Listen & Flip (Muziekherkenning & liedjes herkennen):\nHerken muziek en liedjes om je heen direct in SongFlip. Nummers worden gekoppeld aan je favoriete speler en opgeslagen in je geschiedenis. Geen bereik in de club? Sla de audio-afdruk offline op en laat het nummer automatisch herkennen zodra je weer online bent.",
}

LOCALE_NAMES = {
    'ar': 'العربية (ar)',
    'bn-BD': 'বাংলা (bn-BD)',
    'cs-CZ': 'Čeština (cs-CZ)',
    'da-DK': 'Dansk (da-DK)',
    'de-DE': 'Deutsch (de-DE)',
    'el-GR': 'Ελληνικά (el-GR)',
    'en-GB': 'English - UK (en-GB)',
    'en-IN': 'English - India (en-IN)',
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
Datenschutzrichtlinie (Privacy Policy): https://songflip.link/privacy"""

IOS_EN_DESC = """Friends send you a song link or podcast link from Spotify, Tidal, or Apple Podcasts – but you listen on Apple Music, YouTube Music, or Pocket Casts?

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
Privacy Policy: https://songflip.link/privacy"""

IOS_EN_KEYWORDS = "spotify,apple,youtube,transfer,deezer,qobuz,tidal,soundcloud,shazam,import,songs,amazon,anghami"
IOS_DE_KEYWORDS = "spotify,apple,youtube,transfer,deezer,beatport,qobuz,tidal,soundcloud,shazam,lieder,erkennen,amazon"

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

def update_p4_platforms(p4, loc):
    if loc in ('en-US', 'en-GB'):
        return '• 14 Streaming Platforms, Podcasts & Shazam:\nSeamlessly convert and redirect song links and podcasts across Spotify, YouTube Music, Apple Music, Apple Podcasts, Pocket Casts, Deezer, TIDAL, Amazon Music, Qobuz, Beatport, Audiomack, Idagio, SoundCloud, Bandcamp, and Anghami. Instant converter support for shared Shazam links included!'
    if 'Anghami' in p4 or 'anghami' in p4:
        return p4
    if loc == 'de-DE':
        return '• 14 Streaming- & Podcast-Plattformen:\nSongs und Podcasts automatisch konvertieren und weiterleiten zwischen Spotify, YouTube Music, Apple Music, Apple Podcasts, Pocket Casts, Deezer, TIDAL, Amazon Music, Qobuz, Beatport, Audiomack, Idagio, SoundCloud, Bandcamp und Anghami. Erkennt auch Shazam sofort!'
    if loc == 'fi-FI':
        return '• 14 suoratoistoalustaa, podcastia ja Shazam:\nMuunna ja ohjaa saumattomasti kappalelinkkejä ja podcasteja Spotifyssa, YouTube Musicissa, Apple Musicissa, Apple Podcasteissa, Pocket Castsissa, Deezerissä, TIDALissa, Amazon Musicissa, Qobuzissa, Beatportissa, Audiomackissa, Idagiossa, SoundCloudissa, Bandcampissa ja Anghamissa. Sisältää välittömän musiikkilinkkimuuntimen tuen jaetuille Shazam-linkeille!'
    if loc == 'ja-JP':
        return p4.replace('12', '14').replace('SoundCloud、Bandcamp', 'SoundCloud、Bandcamp、Anghami')
    if loc == 'ko-KR':
        return p4.replace('10가지', '14가지').replace('SoundCloud 및 Bandcamp에서', 'SoundCloud, Bandcamp 및 Anghami에서')
    if loc == 'zh-CN':
        return p4.replace('12 个', '14 个').replace('SoundCloud 和 Bandcamp', 'SoundCloud、Bandcamp 和 Anghami')
    if loc == 'zh-TW':
        return p4.replace('12 個', '14 個').replace('SoundCloud 和 Bandcamp', 'SoundCloud、Bandcamp 與 Anghami')
    if loc == 'hu-HU':
        return p4.replace('12', '14').replace('a Bandcamp', 'a Bandcamp és az Anghami')
    if loc == 'tr-TR':
        return p4.replace('12', '14').replace("ve Bandcamp'ta", "Bandcamp ve Anghami'de")
    if loc == 'bn-BD':
        return p4.replace('10টি', '14টি').replace('এবং Bandcamp', 'Bandcamp এবং Anghami')

    res = re.sub(r'•\s*12\b', '• 14', p4)

    connectors = {
        'cs-CZ': ('a Bandcamp', 'Bandcamp a Anghami'),
        'da-DK': ('og Bandcamp', 'Bandcamp og Anghami'),
        'el-GR': ('και Bandcamp', 'Bandcamp και Anghami'),
        'es-419': ('y Bandcamp', 'Bandcamp y Anghami'),
        'es-ES': ('y Bandcamp', 'Bandcamp y Anghami'),
        'es-US': ('y Bandcamp', 'Bandcamp y Anghami'),
        'fr-CA': ('et Bandcamp', 'Bandcamp et Anghami'),
        'fr-FR': ('et Bandcamp', 'Bandcamp et Anghami'),
        'hi-IN': ('और Bandcamp', 'Bandcamp और Anghami'),
        'id': ('dan Bandcamp', 'Bandcamp, dan Anghami'),
        'it-IT': ('e Bandcamp', 'Bandcamp e Anghami'),
        'mr-IN': ('आणि Bandcamp', 'Bandcamp आणि Anghami'),
        'nb-NO': ('og Bandcamp', 'Bandcamp og Anghami'),
        'nl-NL': ('en Bandcamp', 'Bandcamp en Anghami'),
        'pl-PL': ('i Bandcamp', 'Bandcamp i Anghami'),
        'pt-BR': ('e Bandcamp', 'Bandcamp e Anghami'),
        'pt-PT': ('e Bandcamp', 'Bandcamp e Anghami'),
        'ro-RO': ('și Bandcamp', 'Bandcamp și Anghami'),
        'ru-RU': ('и Bandcamp', 'Bandcamp и Anghami'),
        'sv-SE': ('och Bandcamp', 'Bandcamp och Anghami'),
        'th-TH': ('และ Bandcamp', 'Bandcamp และ Anghami'),
        'uk': ('і Bandcamp', 'Bandcamp і Anghami'),
        'vi': ('và Bandcamp', 'Bandcamp và Anghami'),
    }

    if loc in connectors:
        old_c, new_c = connectors[loc]
        if old_c in res:
            res = res.replace(old_c, new_c)

    return res

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
    rows = []
    if os.path.exists(CSV_PATH):
        with open(CSV_PATH, 'r', encoding='utf-8') as f:
            rows = list(csv.DictReader(f))
    else:
        for loc in sorted(LOCALE_NAMES.keys()):
            loc_dir = os.path.join(ANDROID_METADATA_DIR, loc)
            t_file = os.path.join(loc_dir, "title.txt")
            s_file = os.path.join(loc_dir, "short_description.txt")
            f_file = os.path.join(loc_dir, "full_description.txt")
            if os.path.exists(t_file) and os.path.exists(s_file) and os.path.exists(f_file):
                with open(t_file, 'r', encoding='utf-8') as tf:
                    t_val = tf.read().strip()
                with open(s_file, 'r', encoding='utf-8') as sf:
                    s_val = sf.read().strip()
                with open(f_file, 'r', encoding='utf-8') as ff:
                    f_val = ff.read().strip()
                rows.append({
                    'Language': loc,
                    'Title': t_val,
                    'Short description': s_val,
                    'Full description': f_val
                })

    updated_rows = []
    for r in rows:
        loc = r['Language']
        title = strip_framing_quotes(r['Title'])
        short_desc = strip_framing_quotes(r['Short description'])
        full_desc = strip_framing_quotes(r['Full description'])

        paras = full_desc.split('\n\n')
        idx = 8 if loc in ('ar', 'de-DE', 'en-US', 'en-GB', 'en-IN') else 9

        if loc in M3U_PLAYLIST_PARAS:
            paras[idx] = M3U_PLAYLIST_PARAS[loc]

        if loc in LISTEN_AND_FLIP_PARAS:
            paras[5] = LISTEN_AND_FLIP_PARAS[loc]

        # Update platforms paragraph (Paragraph 4)
        paras[4] = update_p4_platforms(paras[4], loc)

        if loc == 'de-DE':
            paras[0] = "Ein Freund schickt dir einen Song- oder Podcast-Link auf Spotify, du nutzt aber YouTube Music, Apple Podcasts oder Deezer?"
            paras[5] = LISTEN_AND_FLIP_PARAS["de-DE"]
            if len(paras) > 7:
                paras[7] = "• QR-Codes für Audio & Songs teilen & scannen:\nGeneriere und scanne universelle QR-Codes für Audio & Songs. Teile Tracks & Alben vor Ort – egal welcher Streamingdienst beim Gegenüber installiert ist."
            paras[8] = M3U_PLAYLIST_PARAS["de-DE"]
            if len(paras) > 18:
                paras[18] = "Kompatibel mit universellen Streaming-Protokollen wie Songlink / Odesli. Hol dir SongFlip – deinen autonomen Link Converter und Musik-Link Redirect für Android!"

        if loc in ('en-US', 'en-GB', 'en-IN'):
            paras[1] = paras[1].replace("incoming song links and podcast links", "incoming music and podcast links")
            paras[4] = update_p4_platforms(paras[4], loc)
            if len(paras) > 13:
                paras[13] = "• Clipboard Smart-Banner & Native Sharing:\nConvert and open links instantly via the smart dashboard banner, or share universal music URLs directly from WhatsApp, Telegram, Reddit, or your browser."
            if len(paras) > 14:
                paras[14] = "• Universal Web Smart Links & HTML5 Player:\nGenerate neutral web-share URLs (songflip.link) with an embedded webplayer to share tracks across platforms on any device or browser."
            if len(paras) > 15:
                paras[15] = "• Quick Settings Tile & App Shortcuts:\nPause redirection for 15 minutes, 1 hour, or until tomorrow morning via Quick Settings tile or app shortcuts."
            if len(paras) > 16:
                paras[16] = "• Material You, AMOLED Dark Mode & Promo Codes:\nMatches system Dynamic Color with AMOLED dark mode, monochrome themed icon, and promo code redemption."
            if len(paras) > 17:
                paras[-1] = paras[-1].replace("automatic music link redirect", "automatic audio link redirect")

        if loc in ('es-419', 'es-ES', 'es-US'):
            paras[1] = "SongFlip es tu redireccionamiento de enlaces de música y conversor de canciones sin clics para Android. Una vez activado, convierte y abre canciones, álbumes y podcasts entrantes directamente en tu reproductor favorito, sin búsquedas manuales, sin anuncios ni desvíos del navegador."
            paras[2] = "Ya sea para pasar playlist de Spotify a YouTube Music, cambiar de Apple Podcasts a Pocket Casts o de Spotify a Apple Music, SongFlip funciona como tu conversor de música universal para abrir enlaces de Spotify y streams de audio al instante."
            paras[4] = "• 14 plataformas de streaming, podcasts y Shazam:\nConvierte, abre y redirige sin problemas canciones, álbumes y podcasts en Spotify, YouTube Music, Apple Music, Apple Podcasts, Pocket Casts, Deezer, TIDAL, Amazon Music, Qobuz, Beatport, Audiomack, Idagio, SoundCloud, Bandcamp y Anghami. ¡Incluye conversión instantánea para pistas compartidas de Shazam y Last.fm!"
            paras[11] = "• Selector rápido de reproductores:\nElige tu reproductor de audio de destino cada vez que abras una canción o podcast compartido."
            paras[12] = "• Asistente de redirección (Android 12+):\nConfigura la apertura predeterminada en Android con nuestro asistente interactivo de 1 toque."
            paras[13] = "• Banner del portapapeles y menú Compartir:\nConvierte y abre cualquier pista desde el banner inteligente o comparte directamente desde WhatsApp, Telegram o Instagram."
            paras[14] = "• Páginas Web Inteligentes (songflip.link):\nCrea páginas web universales con reproductor integrado para que tus amigos escuchen en cualquier navegador."
            paras[-1] = "Compatible con protocolos universales de música, incluidos Songlink/Odesli. Descarga SongFlip ahora: tu herramienta automática que convierte y abre enlaces de música, canciones y podcasts en todos los servicios de streaming sin esfuerzo."

        if loc == 'pt-BR':
            paras[1] = "SongFlip é o seu redirecionamento de links de música e conversor de canções sem clique para Android. Uma vez ativado, converte e abre canções, faixas e podcasts recebidos diretamente no seu player favorito – sem pesquisa manual, sem anúncios, sem desvios do navegador."
            paras[2] = "Seja mudando do Spotify para YouTube Music, do Apple Podcasts para Pocket Casts, do Spotify para Apple Music ou abrindo faixas do Shazam, o SongFlip funciona como seu conversor universal de música para abrir links do Spotify e fluxos de áudio instantaneamente."
            paras[11] = "• Seletor rápido de reprodutores:\nEscolha seu reprodutor de áudio de destino instantaneamente sempre que abrir uma música, canção ou podcast compartilhado."
            paras[12] = "• Assistente de redirecionamento inteligente (Android 12+):\nVerifique e configure facilmente a abertura padrão do Android com nosso assistente interativo de 1 toque."
            paras[15] = "• Configurações rápidas e atalhos:\nPause o redirecionamento por 15 minutos, 1 hora ou até amanhã por meio das Configurações rápidas."
            paras[-1] = "Compatível com protocolos universais de música, incluindo Songlink/Odesli. Baixe o SongFlip agora – seu redirecionamento automático e conversor de música para conectar canções, faixas e podcasts em todos os serviços de streaming sem esforço!"

        if loc == 'fr-FR':
            paras[1] = "SongFlip est votre convertisseur et outil de redirection de lien musical et de chanson sans clic pour Android. Une fois activé, il convertit et ouvre vos chansons et podcasts entrants directement dans votre lecteur favori : pas de recherche manuelle, pas de publicité, pas de détours de navigateur."
            paras[4] = "• 14 plateformes de streaming, podcasts et Shazam :\nConvertissez et redirigez en toute transparence vos chansons, albums et podcasts sur Spotify, YouTube Music, Apple Music, Apple Podcasts, Pocket Casts, Deezer, TIDAL, Amazon Music, Qobuz, Beatport, Audiomack, Idagio, SoundCloud, Bandcamp et Anghami. Inclut la prise en charge instantanée pour les titres Shazam & Last.fm partagés !"
            paras[12] = "• Assistant de redirection (Android 12+) :\nConfigurez l'ouverture par défaut sur Android avec notre assistant interactif en 1 clic."
            paras[13] = "• Bannière du Presse-papiers et menu de partage :\nConvertissez et ouvrez vos titres via la bannière ou partagez depuis WhatsApp, Telegram ou Instagram."
            paras[14] = "• Pages Web intelligentes (songflip.link) :\nCréez des pages universelles neutres avec un lecteur intégré pour que vos amis puissent écouter dans n'importe quel navigateur."
            paras[-1] = "Compatible avec les protocoles universels de partage musical, notamment Songlink/Odesli. Téléchargez SongFlip maintenant : votre redirection automatique de liens musicaux et convertisseur de chansons pour relier musique et podcasts dans votre lecteur favori sans effort !"

        if loc == 'it-IT':
            paras[1] = "SongFlip è il tuo strumento di reindirizzamento di link musicali e convertitore di canzoni e brani con zero clic per Android. Una volta attivato, converte e apre brani, canzoni e podcast in entrata direttamente nel tuo lettore preferito: senza ricerche manuali, senza pubblicità, senza deviazioni dal browser."
            paras[2] = "Che si tratti di passare da Spotify a YouTube Music, da Apple Podcasts a Pocket Casts, da Spotify a Apple Music o aprire canzoni da Shazam, SongFlip funziona come convertitore di musica universale per aprire link Spotify e flussi audio all'istante."
            paras[4] = "• 14 piattaforme di streaming, podcast e Shazam:\nConverti e reindirizza senza problemi link di canzoni, brani e podcast su Spotify, YouTube Music, Apple Music, Apple Podcasts, Pocket Casts, Deezer, TIDAL, Amazon Music, Qobuz, Beatport, Audiomack, Idagio, SoundCloud, Bandcamp e Anghami. Include il supporto istantaneo per i brani Shazam & Last.fm condivisi!"
            paras[11] = "• Scelta rapida del lettore:\nScegli al volo il tuo lettore audio di destinazione ogni volta che apri una canzone, un brano o un podcast condiviso."
            paras[12] = "• Assistente di reindirizzamento (Android 12+):\nConfigura l'apertura predefinita su Android con il nostro assistente di configurazione in un solo tocco."
            paras[13] = "• Banner negli appunti e menu Condividi:\nConverti e apri qualsiasi brano tramite il banner o condividi link da WhatsApp, Telegram o Instagram."
            paras[14] = "• Pagine Web Smart (songflip.link):\nCrea pagine Web universali con un player incorporato in modo che gli amici possano ascoltare canzoni e playlist in qualsiasi browser."
            paras[-1] = "Compatibile con i protocolli di link musicali universali tra cui Songlink/Odesli. Scarica subito SongFlip: il reindirizzamento automatico e convertitore per aprire brani, canzoni e podcast su tutti i servizi di streaming senza sforzo!"

        if loc == 'id':
            paras[0] = "Seorang teman mengirimi Anda link musik, tautan lagu, atau podcast di Spotify, tetapi Anda mendengarkannya di YouTube Music, Apple Podcasts, atau Deezer?"
            paras[1] = "SongFlip adalah pengalih musik dan pengonversi tautan lagu tanpa klik yang mulus untuk Android. Setelah diaktifkan, link musik dan podcast yang masuk secara otomatis dikonversi di latar belakang dan terbuka langsung di pemutar favorit Anda—tanpa pencarian manual, tanpa iklan, tanpa memutar browser."
            paras[2] = "Baik mengalihkan Spotify ke YouTube Music, Apple Podcasts ke Pocket Casts, Spotify ke Apple Music, atau membuka lagu dari Shazam, SongFlip berfungsi sebagai pengonversi musik universal untuk buka link Spotify dan streaming audio secara instan."
            paras[4] = "• 14 Platform Streaming, Podcast & Shazam:\nKonversikan dan alihkan lagu, album, dan podcast dengan lancar di Spotify, YouTube Music, Apple Music, Apple Podcasts, Pocket Casts, Deezer, TIDAL, Amazon Music, Qobuz, Beatport, Audiomack, Idagio, SoundCloud, Bandcamp, dan Anghami. Termasuk dukungan instan untuk lagu Shazam & Last.fm yang dibagikan!"
            paras[8] = "• Konverter Podcast & Mesin Pengalih Podcast:\nBagikan acara atau episode podcast lengkap di seluruh platform. SongFlip mencocokkan judul episode secara otomatis untuk mengonversi dan membuka podcast langsung di pemutar favorit Anda."
            paras[11] = "• Pemilih Pemutar Cepat (Tanya Setiap Saat):\nAktifkan pemilih cepat untuk memilih pemutar tujuan secara instan setiap kali membuka lagu atau podcast."
            paras[12] = "• Asisten Pengalihan Cerdas (Android 12+):\nVerifikasi dan konfigurasikan Android Open by Default App Links dengan mudah menggunakan asisten penyiapan 1 ketukan interaktif kami."
            paras[13] = "• Spanduk Cerdas Papan Klip & Berbagi Asli:\nKonversikan dan buka secara instan melalui spanduk dasbor pintar, atau bagikan link langsung dari WhatsApp, Telegram, Reddit, atau Instagram ke SongFlip."
            paras[14] = "• Smart Link Web Universal & Pemutar Web (songflip.link):\nBuat halaman web universal dengan pemutar bawaan agar teman dapat mendengarkan di perangkat apa pun."
            paras[-1] = "Kompatibel dengan protokol musik universal termasuk Songlink / Odesli. Unduh SongFlip sekarang—pengalih tautan musik otomatis dan konverter lagu untuk menjembatani lagu & podcast di semua layanan streaming dengan mudah!"

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

    # 1. Write clean Markdown (Single Source of Truth, 0 quotes)
    version_match = re.search(r'val\s+appVersionName\s*=\s*"([^"]+)"', open(os.path.join(os.path.dirname(__file__), '../app/build.gradle.kts'), encoding='utf-8').read()) if os.path.exists(os.path.join(os.path.dirname(__file__), '../app/build.gradle.kts')) else None
    app_version = version_match.group(1) if version_match else "1.14.18"
    print(f"Writing clean Markdown to {MD_PATH}...")
    with open(MD_PATH, 'w', encoding='utf-8') as f:
        f.write("# 📱 SongFlip – Play Store & App Store Listings (Alle Sprachen)\n")
        f.write(f"> **Version:** {app_version} | **Umfang:** 34 Play Store-Sprachen + iOS App Store (DE/EN) | **Zertifiziert:** Titel ≤ 30 Zeichen, Kurzbeschreibung ≤ 80 Zeichen, Volltext ≤ 4.000 Zeichen (UTF-16), iOS Keywords ≤ 100 Zeichen. Ohne umschließende Anführungszeichen.\n")
        f.write("---\n\n")
        for r in updated_rows:
            loc = r['Language']
            name = LOCALE_NAMES.get(loc, loc)
            t = r['Title']
            s = r['Short description']
            d = r['Full description']
            f.write(f"## {name}\n\n")
            f.write(f"### Titel ({utf16_len(t)}/30 Zeichen)\n```text\n{t}\n```\n\n")
            f.write(f"### Kurzbeschreibung ({utf16_len(s)}/80 Zeichen)\n```text\n{s}\n```\n\n")
            f.write(f"### Ausführliche Beschreibung ({utf16_len(d)}/4000 Zeichen)\n```text\n{d}\n```\n\n")
            f.write("---\n\n")

        f.write("# 🍎 iOS App Store Listings (de-DE & en-US)\n\n")
        f.write("## 🇩🇪 iOS Deutsch (de-DE)\n\n")
        f.write("### App-Name (29/30 Zeichen)\n```text\nSongFlip: Musik-Link Redirect\n```\n\n")
        f.write("### Untertitel (28/30 Zeichen)\n```text\nPlaylist & Podcast Converter\n```\n\n")
        f.write(f"### Keywords ({len(IOS_DE_KEYWORDS)}/100 Zeichen)\n```text\n{IOS_DE_KEYWORDS}\n```\n\n")
        f.write(f"### Beschreibung ({len(IOS_DE_DESC)}/4000 Zeichen)\n```text\n{IOS_DE_DESC}\n```\n\n")
        f.write("---\n\n")
        f.write("## 🇺🇸 iOS English (en-US)\n\n")
        f.write("### App Name (29/30 Zeichen)\n```text\nSongFlip: Music Link Redirect\n```\n\n")
        f.write("### Subtitle (28/30 Zeichen)\n```text\nPlaylist & Podcast Converter\n```\n\n")
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
    with open(os.path.join(IOS_METADATA_DIR, "de-DE", "name.txt"), 'w', encoding='utf-8') as f:
        f.write("SongFlip: Musik-Link Redirect\n")
    with open(os.path.join(IOS_METADATA_DIR, "en-US", "name.txt"), 'w', encoding='utf-8') as f:
        f.write("SongFlip: Music Link Redirect\n")
    with open(os.path.join(IOS_METADATA_DIR, "de-DE", "subtitle.txt"), 'w', encoding='utf-8') as f:
        f.write("Playlist & Podcast Converter\n")
    with open(os.path.join(IOS_METADATA_DIR, "en-US", "subtitle.txt"), 'w', encoding='utf-8') as f:
        f.write("Playlist & Podcast Converter\n")
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

    print("\n🎉 SUCCESS: All Play Store listings (Markdown) and iOS metadata are 100% updated, feature-complete (incl. M3U import) and free of framing quotes!")

if __name__ == '__main__':
    main()
