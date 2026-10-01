#!/usr/bin/env python3
"""
Update Android strings for v1.6.3 across all 31 localized values directories:
1. app_links_step2_title: Clarify that user is selecting links to redirect.
2. app_links_step2_desc: Clearly explain that ticking available services is right, and installed target player is greyed out by Android intentionally.
3. playlist_error_private_title & playlist_error_private_desc: Provide crystal-clear explanation why personal mixes (Daily Mix) aren't supported due to strict no-login privacy + actionable tip.
"""

import os
import re

RES_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res")

TRANSLATIONS = {
    'en': {
        'step2_title': 'Select Links to Redirect',
        'step2_desc': 'Tick available streaming services. (Your installed music player is greyed out by Android — that is normal and optimal!)',
        'private_title': 'Private or Personalized Playlist',
        'private_desc': 'SongFlip operates 100%% private without user accounts or login. Therefore, personal algorithmic mixes (such as Spotify Daily Mix or Discover Weekly) and private playlists cannot be accessed. Tip: Copy the tracks to a public playlist or share an album/public playlist.'
    },
    'de': {
        'step2_title': 'Umzuleitende Links auswählen',
        'step2_desc': 'Hake verfügbare Streaming-Dienste an. (Dein installierter Musik-Player ist von Android ausgegraut — das ist völlig normal und gewollt!)',
        'private_title': 'Private oder personalisierte Playlist',
        'private_desc': 'SongFlip arbeitet 100%% privat ohne Benutzerkonto oder Login. Daher können persönliche algorithmische Mixe (wie Spotify Daily Mix oder Discover Weekly) und private Playlists nicht geladen werden. Tipp: Kopiere die Songs in eine öffentliche Playlist oder teile ein Album/eine öffentliche Playlist.'
    },
    'da': {
        'step2_title': 'Vælg links, der skal omdirigeres',
        'step2_desc': 'Markér tilgængelige musiktjenester. (Din installerede musikafspiller er nedtonet af Android – det er helt normalt og optimalt!)',
        'private_title': 'Privat eller personlig playliste',
        'private_desc': 'SongFlip fungerer 100%% privat uden brugerkonto eller login. Personlige algoritmiske mix (som Spotify Daily Mix eller Discover Weekly) og private playlister kan derfor ikke indlæses. Tip: Kopiér numrene til en offentlig playliste eller del et album/en offentlig playliste.'
    },
    'no': {
        'step2_title': 'Velg lenker som skal omdirigeres',
        'step2_desc': 'Huk av for tilgjengelige musikktjenester. (Din installerte musikkspiller er grået ut av Android – dette er helt normalt og optimalt!)',
        'private_title': 'Privat eller personlig spilleliste',
        'private_desc': 'SongFlip fungerer 100%% privat uten brukerkonto eller pålogging. Personlige algoritmiske mikser (som Spotify Daily Mix eller Discover Weekly) og private spillelister kan derfor ikke leses. Tips: Kopier sangene til en offentlig spilleliste eller del et album/en offentlig spilleliste.'
    },
    'sv': {
        'step2_title': 'Välj länkar att omdirigera',
        'step2_desc': 'Markera tillgängliga musiktjänster. (Din installerade musikspelare är gråmarkerad av Android – det är helt normalt och optimalt!)',
        'private_title': 'Privat eller anpassad spellista',
        'private_desc': 'SongFlip fungerar 100%% privat utan användarkonton eller inloggning. Personliga algoritmiska mixar (som Spotify Daily Mix eller Discover Weekly) och privata spellistor kan därför inte hämtas. Tips: Kopiera låtarna till en offentlig spellista eller dela ett album/en offentlig spellista.'
    },
    'nl': {
        'step2_title': 'Links omleiden selecteren',
        'step2_desc': 'Vink beschikbare muziekdiensten aan. (Je geïnstalleerde muziekspeler is grijs weergegeven door Android — dat is volkomen normaal en optimaal!)',
        'private_title': 'Privé of gepersonaliseerde afspeellijst',
        'private_desc': 'SongFlip werkt 100%% privé zonder gebruikersaccounts of login. Persoonlijke algoritmische mixen (zoals Spotify Daily Mix of Discover Weekly) en privé-afspeellijsten kunnen daarom niet worden geopend. Tip: Kopieer de nummers naar een openbare afspeellijst of deel een album/openbare afspeellijst.'
    },
    'fr': {
        'step2_title': 'Sélectionner les liens à rediriger',
        'step2_desc': r"Cochez les services disponibles. (Votre lecteur installé est grisé par Android — c\'est tout à fait normal et optimal !)",
        'private_title': 'Playlist privée ou personnalisée',
        'private_desc': 'SongFlip fonctionne à 100%% privé sans compte ni connexion. Les mix algorithmiques personnels (comme Daily Mix de Spotify ou Discover Weekly) et les playlists privées ne peuvent donc pas être chargés. Astuce : copiez les morceaux dans une playlist publique ou partagez un album/une playlist publique.'
    },
    'es': {
        'step2_title': 'Seleccionar enlaces para redirigir',
        'step2_desc': 'Marca los servicios disponibles. (Tu reproductor instalado aparece atenuado por Android: ¡es totalmente normal y óptimo!)',
        'private_title': 'Playlist privada o personalizada',
        'private_desc': 'SongFlip funciona 100%% privado sin cuenta ni inicio de sesión. Por tanto, los mixes algorítmicos personales (como Spotify Daily Mix o Discover Weekly) y las playlists privadas no se pueden cargar. Consejo: copia las canciones a una playlist pública o comparte un álbum/playlist pública.'
    },
    'it': {
        'step2_title': 'Seleziona i link da reindirizzare',
        'step2_desc': 'Seleziona i servizi musicali disponibili. (Il tuo lettore installato è disattivato da Android: è assolutamente normale e corretto!)',
        'private_title': 'Playlist privata o personalizzata',
        'private_desc': 'SongFlip funziona al 100%% privato senza account né accesso. Pertanto, i mix algoritmici personali (come Spotify Daily Mix o Discover Weekly) e le playlist private non possono essere aperti. Consiglio: copia i brani in una playlist pubblica o condividi un album/playlist pubblica.'
    },
    'pt': {
        'step2_title': 'Selecionar links para redirecionar',
        'step2_desc': 'Marque os serviços disponíveis. (O seu reprodutor instalado aparece desativado pelo Android — isso é totalmente normal e ideal!)',
        'private_title': 'Playlist privada ou personalizada',
        'private_desc': 'O SongFlip funciona 100%% privado sem contas nem login. Portanto, mixes algorítmicos pessoais (como Spotify Daily Mix ou Discover Weekly) e playlists privadas não podem ser acessados. Dica: copie as músicas para uma playlist pública ou compartilhe um álbum/playlist pública.'
    },
    'pl': {
        'step2_title': 'Wybierz linki do przekierowania',
        'step2_desc': 'Zaznacz dostępne serwisy. (Twój zainstalowany odtwarzacz jest wyszarzony przez system Android — to całkowicie normalne i prawidłowe!)',
        'private_title': 'Playlista prywatna lub spersonalizowana',
        'private_desc': 'SongFlip działa w 100%% prywatnie bez kont i logowania. Osobiste składanki algorytmiczne (np. Spotify Daily Mix czy Odkryj w tym tygodniu) i prywatne playlisty nie mogą zostać pobrane. Wskazówka: skopiuj utwory do publicznej playlisty lub udostępnij album/publiczną playlistę.'
    },
    'ru': {
        'step2_title': 'Выберите ссылки для перенаправления',
        'step2_desc': 'Отметьте доступные сервисы. (Ваш установленный плеер выделен серым в Android — это абсолютно нормально и правильно!)',
        'private_title': 'Приватный или персонализированный плейлист',
        'private_desc': 'SongFlip работает на 100%% конфиденциально без аккаунтов и входа. Персональные алгоритмические миксы (например, Spotify Daily Mix) и приватные плейлисты недоступны. Совет: скопируйте треки в публичный плейлист или поделитесь альбомом/публичным плейлистом.'
    },
    'uk': {
        'step2_title': 'Виберіть посилання для перенаправлення',
        'step2_desc': 'Позначте доступні сервіси. (Ваш встановлений плеєр виділений сірим в Android — це абсолютно нормально й правильно!)',
        'private_title': 'Приватний або персоналізований плейліст',
        'private_desc': 'SongFlip працює на 100%% конфіденційно без акаунтів та входу. Персональні алгоритмічні мікси (наприклад, Spotify Daily Mix) та приватні плейлісти недоступні. Порада: скопіюйте треки до публічного плейліста або поділіться альбомом/публічним плейлістом.'
    },
    'tr': {
        'step2_title': 'Yönlendirilecek Bağlantıları Seçin',
        'step2_desc': 'Kullanılabilir servisleri işaretleyin. (Yüklü müzik çalarınız Android tarafından gri gösterilir — bu tamamen normaldir ve istenen durumdur!)',
        'private_title': 'Özel veya Kişiselleştirilmiş Çalma Listesi',
        'private_desc': 'SongFlip hesap veya giriş gerektirmeden %%100 gizli çalışır. Bu nedenle kişisel algoritmik miksler (Spotify Daily Mix veya Haftalık Keşif gibi) ve gizli çalma listeleri yüklenemez. İpucu: Şarkıları herkese açık bir listeye kopyalayın veya bir albüm/açık liste paylaşın.'
    },
    'cs': {
        'step2_title': 'Vyberte odkazy k přesměrování',
        'step2_desc': 'Zaškrtněte dostupné služby. (Váš nainstalovaný přehrávač je v Androidu zašedlý — to je zcela normální a žádoucí!)',
        'private_title': 'Soukromý nebo personalizovaný playlist',
        'private_desc': 'SongFlip funguje 100%% soukromě bez uživatelských účtů nebo přihlašování. Osobní algoritmické mixy (jako Spotify Daily Mix) a soukromé playlisty proto nelze načíst. Tip: Zkopírujte skladby do veřejného playlistu nebo sdílejte album/veřejný playlist.'
    },
    'el': {
        'step2_title': 'Επιλέξτε συνδέσμους για ανακατεύθυνση',
        'step2_desc': 'Επιλέξτε τις διαθέσιμες υπηρεσίες. (Η εγκατεστημένη εφαρμογή σας είναι απενεργοποιημένη από το Android — αυτό είναι απολύτως φυσιολογικό!)',
        'private_title': 'Ιδιωτική ή εξατομικευμένη λίστα αναπαραγωγής',
        'private_desc': 'Το SongFlip λειτουργεί 100%% ιδιωτικά χωρίς λογαριασμούς ή σύνδεση. Προσωπικά αλγοριθμικά μείγματα (όπως το Spotify Daily Mix) και ιδιωτικές λίστες δεν μπορούν να φορτωθούν. Συμβουλή: Αντιγράψτε τα κομμάτια σε μια δημόσια λίστα ή μοιραστείτε ένα άλμπουμ/δημόσια λίστα.'
    },
    'fi': {
        'step2_title': 'Valitse uudelleenohjattavat linkit',
        'step2_desc': 'Valitse saatavilla olevat palvelut. (Asennettu musiikkisoittimesi on Androidissa harmaana — se on täysin normaalia ja toivottua!)',
        'private_title': 'Yksityinen tai henkilökohtainen soittolista',
        'private_desc': 'SongFlip toimii 100%% yksityisesti ilman käyttäjätilejä tai kirjautumista. Algoritmisia henkilökohtaisia miksauksia (kuten Spotify Daily Mix) tai yksityisiä soittolistoja ei siksi voi avata. Vinkki: Kopioi kappaleet julkiselle soittolistalle tai jaa albumi/julkinen lista.'
    },
    'hu': {
        'step2_title': 'Átirányítandó linkek kiválasztása',
        'step2_desc': 'Jelölje be az elérhető szolgáltatásokat. (A telepített lejátszója szürkén jelenik meg az Androidban — ez teljesen normális és így a helyes!)',
        'private_title': 'Privát vagy személyre szabott lejátszási lista',
        'private_desc': 'A SongFlip 100%%-ban privát módon működik, fiókok vagy bejelentkezés nélkül. A személyes algoritmikus mixek (mint a Spotify Daily Mix) és a privát listák ezért nem tölthetők be. Tipp: Másolja a számokat egy nyilvános lejátszási listába, vagy osszon meg egy albumot/nyilvános listát.'
    },
    'ro': {
        'step2_title': 'Selectați linkurile de redirecționat',
        'step2_desc': 'Bifați serviciile disponibile. (Playerul dvs. instalat este estompat de Android — este absolut normal și corect!)',
        'private_title': 'Playlist privat sau personalizat',
        'private_desc': 'SongFlip funcționează 100%% privat, fără conturi sau autentificare. Mixurile algoritmice personale (cum ar fi Spotify Daily Mix) și playlisturile private nu pot fi accesate. Sfat: Copiați piesele într-un playlist public sau partajați un album/playlist public.'
    },
    'id': {
        'step2_title': 'Pilih Tautan untuk Dialihkan',
        'step2_desc': 'Centang layanan yang tersedia. (Pemutar musik Anda yang terinstal berwarna abu-abu oleh Android — itu normal dan memang semestinya!)',
        'private_title': 'Daftar Putar Pribadi atau Dipersonalisasi',
        'private_desc': 'SongFlip beroperasi 100%% privat tanpa akun atau login. Oleh karena itu, mix algoritmik pribadi (seperti Spotify Daily Mix) dan playlist privat tidak dapat diakses. Tips: Salin lagu ke playlist publik atau bagikan album/playlist publik.'
    },
    'vi': {
        'step2_title': 'Chọn liên kết để chuyển hướng',
        'step2_desc': 'Đánh dấu các dịch vụ khả dụng. (Trình phát đã cài đặt của bạn bị làm mờ bởi Android — điều đó hoàn toàn bình thường và tối ưu!)',
        'private_title': 'Danh sách phát riêng tư hoặc cá nhân hóa',
        'private_desc': 'SongFlip hoạt động riêng tư 100%% không cần tài khoản hay đăng nhập. Do đó, các danh sách kết hợp thuật toán cá nhân (như Spotify Daily Mix) và playlist riêng tư không thể truy cập. Mẹo: Sao chép bài hát vào danh sách phát công khai hoặc chia sẻ album/playlist công khai.'
    },
    'th': {
        'step2_title': 'เลือกลิงก์ที่จะเปลี่ยนเส้นทาง',
        'step2_desc': 'เลือกบริการที่มีให้ใช้งาน (เครื่องเล่นเพลงที่คุณติดตั้งไว้จะแสดงเป็นสีเทาใน Android ซึ่งเป็นเรื่องปกติและถูกต้องแล้ว!)',
        'private_title': 'เพลย์ลิสต์ส่วนตัวหรือเพลย์ลิสต์เฉพาะบุคคล',
        'private_desc': 'SongFlip ทำงานอย่างเป็นส่วนตัว 100%% โดยไม่ต้องมีบัญชีหรือเข้าสู่ระบบ ดังนั้นมิกซ์อัลกอริทึมส่วนบุคคล (เช่น Spotify Daily Mix) และเพลย์ลิสต์ส่วนตัวจึงไม่สามารถเข้าถึงได้ คำแนะนำ: คัดลอกแทร็กไปยังเพลย์ลิสต์สาธารณะหรือแชร์อัลบั้ม/เพลย์ลิสต์สาธารณะ'
    },
    'ja': {
        'step2_title': '転送するリンクを選択',
        'step2_desc': '利用可能なサービスにチェックを入れます（インストール済みの再生アプリはAndroidによりグレーアウトされますが、正常な動作です）。',
        'private_title': '非公開またはパーソナライズされたプレイリスト',
        'private_desc': 'SongFlipはログイン不要・アカウント不要で100%%プライベートに動作します。そのため、個人用ミックス（Spotify Daily Mixなど）や非公開プレイリストは読み込めません。ヒント：曲を公開プレイリストにコピーするか、アルバムや公開プレイリストを共有してください。'
    },
    'ko': {
        'step2_title': '전환할 링크 선택',
        'step2_desc': '사용 가능한 서비스를 선택하세요. (설치된 음악 플레이어는 Android에서 비활성화(회색)로 표시되며 이는 정상입니다!)',
        'private_title': '비공개 또는 맞춤형 재생목록',
        'private_desc': 'SongFlip은 로그인이나 계정 없이 100%% 비공개로 작동합니다. 따라서 개인 맞춤 믹스(Spotify Daily Mix 등)나 비공개 재생목록은 불러올 수 없습니다. 팁: 곡을 공개 재생목록에 복사하거나 앨범/공개 재생목록을 공유하세요.'
    },
    'zh-rCN': {
        'step2_title': '选择要重定向的链接',
        'step2_desc': '勾选可用的音乐平台。（您已安装的播放器会被 Android 设为灰色，这是完全正常的且符合预期！）',
        'private_title': '私人或个性化歌单',
        'private_desc': 'SongFlip 100%% 保护隐私，无需账号或登录。因此无法访问个性化推荐歌单（例如 Spotify Daily Mix）及私人歌单。提示：可将歌曲复制到公开歌单，或直接分享专辑/公开歌单。'
    },
    'zh-rTW': {
        'step2_title': '選擇要重定向的連結',
        'step2_desc': '勾選可用的音樂服務。（您已安裝的播放器會被 Android 顯示為灰色，這完全正常且符合預期！）',
        'private_title': '私人或個人化播放清單',
        'private_desc': 'SongFlip 100%% 保障隱私，無須帳號或登入。因此無法讀取個人演算法推薦（如 Spotify Daily Mix）及私人播放清單。提示：可將曲目複製到公開播放清單，或直接分享專輯/公開播放清單。'
    },
    'hi': {
        'step2_title': 'रीडायरेक्ट करने के लिए लिंक चुनें',
        'step2_desc': 'उपलब्ध सेवाओं को टिक करें। (आपका इंस्टॉल किया गया प्लेयर Android द्वारा धूसर (greyed out) है — यह बिल्कुल सामान्य और सही है!)',
        'private_title': 'निजी या व्यक्तिगत प्लेलिस्ट',
        'private_desc': 'SongFlip बिना किसी खाते या लॉगिन के 100%% निजी रूप से काम करता है। इसलिए व्यक्तिगत एल्गोरिथम मिक्स (जैसे Spotify Daily Mix) और निजी प्लेलिस्ट लोड नहीं हो सकती हैं। सुझाव: गानों को किसी सार्वजनिक प्लेलिस्ट में कॉपी करें या एल्बम/सार्वजनिक प्लेलिस्ट साझा करें।'
    },
    'bn': {
        'step2_title': 'রিডাইরেক্ট করার জন্য লিঙ্ক নির্বাচন করুন',
        'step2_desc': 'উপলব্ধ পরিষেবাগুলিতে টিক দিন। (আপনার ইনস্টল করা প্লেয়ারটি Android দ্বারা ধূসর দেখাবে — এটি সম্পূর্ণ স্বাভাবিক এবং সঠিক!)',
        'private_title': 'ব্যক্তিগত বা নিজস্ব প্লেলিস্ট',
        'private_desc': 'SongFlip কোনও অ্যাকাউন্ট বা লগইন ছাড়াই 100%% ব্যক্তিগতভাবে কাজ করে। তাই ব্যক্তিগত অ্যালগরিদম মিক্স (যেমন Spotify Daily Mix) এবং ব্যক্তিগত প্লেলিস্ট লোড করা যায় না। পরামর্শ: গানগুলি একটি সর্বজনীন প্লেলিস্টে অনুলিপি করুন বা অ্যালবাম/সর্বজনীন প্লেলিস্ট শেয়ার করুন। '
    },
    'mr': {
        'step2_title': 'रीडायरेक्ट करण्यासाठी लिंक निवडा',
        'step2_desc': 'उपलब्ध सेवा निवडा. (तुमचा इन्स्टॉल केलेला म्युझिक प्लेअर Android द्वारे ग्रे (निष्क्रिय) दिसेल — हे पूर्णपणे सामान्य आहे!)',
        'private_title': 'खाजगी किंवा वैयक्तिकृत प्लेलिस्ट',
        'private_desc': 'SongFlip कोणत्याही खात्याशिवाय किंवा लॉगिनशिवाय 100%% खाजगीरित्या कार्य करते. त्यामुळे वैयक्तिक अल्गोरिदम मिक्स (जसे की Spotify Daily Mix) आणि खाजगी प्लेलिस्ट उघडता येत नाहीत. टीप: गाणी सार्वजनिक प्लेलिस्टमध्ये कॉपी करा किंवा अल्बम/सार्वजनिक प्लेलिस्ट शेअर करा.'
    }
}

DIR_MAP = {
    'values': 'en',
    'values-bn': 'bn',
    'values-cs': 'cs',
    'values-da': 'da',
    'values-de': 'de',
    'values-el': 'el',
    'values-es': 'es',
    'values-fi': 'fi',
    'values-fr': 'fr',
    'values-hi': 'hi',
    'values-hu': 'hu',
    'values-id': 'id',
    'values-in': 'id',
    'values-it': 'it',
    'values-ja': 'ja',
    'values-ko': 'ko',
    'values-mr': 'mr',
    'values-nb': 'no',
    'values-nl': 'nl',
    'values-no': 'no',
    'values-pl': 'pl',
    'values-pt': 'pt',
    'values-ro': 'ro',
    'values-ru': 'ru',
    'values-sv': 'sv',
    'values-th': 'th',
    'values-tr': 'tr',
    'values-uk': 'uk',
    'values-vi': 'vi',
    'values-zh-rCN': 'zh-rCN',
    'values-zh-rTW': 'zh-rTW'
}

def update_file(dir_name):
    lang_key = DIR_MAP.get(dir_name, 'en')
    t = TRANSLATIONS.get(lang_key, TRANSLATIONS['en'])
    file_path = os.path.join(RES_DIR, dir_name, "strings.xml")
    if not os.path.exists(file_path):
        print(f"Skipping {file_path}, does not exist")
        return

    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Update app_links_step2_title
    pattern_title = r'(<string\s+name="app_links_step2_title"[^>]*>)(.*?)(</string>)'
    if re.search(pattern_title, content):
        content = re.sub(pattern_title, lambda m: m.group(1) + t["step2_title"] + m.group(3), content)
    else:
        # insert before app_links_step2_desc
        pattern_insert = r'(<string\s+name="app_links_step2_desc")'
        content = re.sub(pattern_insert, lambda m: f'<string name="app_links_step2_title">{t["step2_title"]}</string>\n    ' + m.group(1), content)

    # 2. Update app_links_step2_desc
    pattern_desc = r'(<string\s+name="app_links_step2_desc"[^>]*>)(.*?)(</string>)'
    if re.search(pattern_desc, content):
        content = re.sub(pattern_desc, lambda m: m.group(1) + t["step2_desc"] + m.group(3), content)

    # 3. Update or Insert playlist_error_private_title & playlist_error_private_desc
    pattern_priv_title = r'(<string\s+name="playlist_error_private_title"[^>]*>)(.*?)(</string>)'
    pattern_priv_desc = r'(<string\s+name="playlist_error_private_desc"[^>]*>)(.*?)(</string>)'

    if re.search(pattern_priv_title, content):
        content = re.sub(pattern_priv_title, lambda m: m.group(1) + t["private_title"] + m.group(3), content)
    if re.search(pattern_priv_desc, content):
        content = re.sub(pattern_priv_desc, lambda m: m.group(1) + t["private_desc"] + m.group(3), content)

    if not re.search(pattern_priv_title, content):
        # Insert after playlist_error_desc or before playlist_btn_retry or before </resources>
        insert_block = f'    <string name="playlist_error_private_title">{t["private_title"]}</string>\n    <string name="playlist_error_private_desc">{t["private_desc"]}</string>\n'
        if '<string name="playlist_error_desc">' in content or '<string name="playlist_error_desc"' in content:
            content = re.sub(r'(<string\s+name="playlist_error_desc"[^>]*>.*?</string>\n)', r'\1' + insert_block, content, count=1)
        elif '<string name="playlist_btn_retry"' in content:
            content = re.sub(r'(<string\s+name="playlist_btn_retry")', insert_block + r'    \1', content, count=1)
        else:
            content = content.replace('</resources>', insert_block + '</resources>')

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Updated {dir_name} ({lang_key})")

def main():
    for dir_name in sorted(DIR_MAP.keys()):
        update_file(dir_name)
    print("All 31 directories updated successfully.")

if __name__ == "__main__":
    main()
