#!/usr/bin/env python3
import os
import math
from PIL import Image, ImageDraw, ImageFont

# Canvas Configuration for Google Play Store (Standard 1080 x 2400 Portrait, 20:9)
WIDTH = 1080
HEIGHT = 2400

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCREENSHOTS_DIR = os.path.join(BASE_DIR, "distribution", "screenshots")
TEMPLATES_DIR = os.path.join(SCREENSHOTS_DIR, "templates")
MOCKUPS_DIR = os.path.join(SCREENSHOTS_DIR, "mockups")

os.makedirs(TEMPLATES_DIR, exist_ok=True)
os.makedirs(MOCKUPS_DIR, exist_ok=True)

# Colors
BG_DARK = (10, 12, 16)         # Deep AMOLED #0A0C10
BG_MID = (18, 22, 30)          # Slate #12161E
EMERALD = (16, 185, 129)       # Accent #10B981
TEXT_PRIMARY = (255, 255, 255) # White
TEXT_MUTED = (148, 163, 184)   # Slate 400
GUIDE_BOX = (51, 65, 85)       # Slate 700
GUIDE_ACCENT = (16, 185, 129)  # Emerald guide line

# Available System Fonts
FONT_BOLD_PATH = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT_REG_PATH = "/System/Library/Fonts/Supplemental/Arial.ttf"
FONT_UNICODE_PATH = "/Library/Fonts/Arial Unicode.ttf"
if not os.path.exists(FONT_UNICODE_PATH):
    FONT_UNICODE_PATH = "/System/Library/Fonts/Supplemental/Arial Unicode.ttf"

if not os.path.exists(FONT_BOLD_PATH):
    FONT_BOLD_PATH = "/System/Library/Fonts/HelveticaNeue.ttc"
    FONT_REG_PATH = "/System/Library/Fonts/HelveticaNeue.ttc"

def get_fonts(bold_size=76, reg_size=48, lang="en"):
    """Load appropriate system fonts with fallback for international glyphs."""
    try:
        if any(code in lang for code in ["zh", "ja", "ko", "hi", "mr", "bn", "ar", "el", "ru", "uk", "th", "vi"]):
            if os.path.exists(FONT_UNICODE_PATH):
                return ImageFont.truetype(FONT_UNICODE_PATH, bold_size), ImageFont.truetype(FONT_UNICODE_PATH, reg_size)
        return ImageFont.truetype(FONT_BOLD_PATH, bold_size), ImageFont.truetype(FONT_REG_PATH, reg_size)
    except Exception:
        return ImageFont.load_default(), ImageFont.load_default()

def create_background_gradient():
    """Render high-resolution vertical gradient with subtle top glow."""
    base = Image.new("RGB", (WIDTH, HEIGHT), BG_DARK)
    draw = ImageDraw.Draw(base)

    # Vertical linear gradient
    for y in range(HEIGHT):
        ratio = y / float(HEIGHT)
        r = int(BG_DARK[0] * (1 - ratio * 0.4) + BG_MID[0] * (ratio * 0.4))
        g = int(BG_DARK[1] * (1 - ratio * 0.4) + BG_MID[1] * (ratio * 0.4))
        b = int(BG_DARK[2] * (1 - ratio * 0.4) + BG_MID[2] * (ratio * 0.4))
        draw.line([(0, y), (WIDTH, y)], fill=(r, g, b))

    # Top subtle emerald glow
    glow = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    center_x, center_y = WIDTH // 2, 280
    max_radius = 550

    for r in range(max_radius, 0, -10):
        alpha = int(22 * (1 - r / max_radius))
        glow_draw.ellipse(
            [center_x - r, center_y - r * 0.6, center_x + r, center_y + r * 0.6],
            fill=(EMERALD[0], EMERALD[1], EMERALD[2], alpha)
        )

    return Image.alpha_composite(base.convert("RGBA"), glow).convert("RGB")

def tokenize(text):
    """Split text into words and individual CJK characters for proper multi-language wrapping."""
    tokens = []
    current_word = ""
    for char in text:
        if char == " ":
            if current_word:
                tokens.append(current_word)
                current_word = ""
            tokens.append(" ")
        elif "\u4e00" <= char <= "\u9fff" or "\u3040" <= char <= "\u30ff" or "\uac00" <= char <= "\ud7af":
            if current_word:
                tokens.append(current_word)
                current_word = ""
            tokens.append(char)
        else:
            current_word += char
    if current_word:
        tokens.append(current_word)
    return tokens

def wrap_text(text, font, max_width, draw):
    """Wrap text to fit within max_width across all alphabets and scripts."""
    lines = []
    for para in text.split("\n"):
        tokens = tokenize(para)
        current_line = ""
        for token in tokens:
            test_line = current_line + token
            bbox = draw.textbbox((0, 0), test_line, font=font)
            if bbox[2] - bbox[0] <= max_width:
                current_line = test_line
            else:
                if current_line.strip():
                    lines.append(current_line.strip())
                    current_line = token.lstrip()
                else:
                    lines.append(token.strip())
                    current_line = ""
        if current_line.strip():
            lines.append(current_line.strip())
    return lines

def render_screenshot(headline, subline, screen_num, mockup_filename=None, lang_code="en", is_template=False):
    """Render a complete store screenshot with localized text and mockup area."""
    img = create_background_gradient()
    draw = ImageDraw.Draw(img)
    font_headline, font_subline = get_fonts(bold_size=76, reg_size=48, lang=lang_code)

    # 1. Header Text Layout
    max_text_width = WIDTH - 160  # 80px side margins
    start_y = 130

    headline_lines = wrap_text(headline, font_headline, max_text_width, draw)
    subline_lines = wrap_text(subline, font_subline, max_text_width, draw)

    current_y = start_y
    for line in headline_lines:
        bbox = draw.textbbox((0, 0), line, font=font_headline)
        line_w = bbox[2] - bbox[0]
        line_h = bbox[3] - bbox[1]
        draw.text(((WIDTH - line_w) // 2, current_y), line, font=font_headline, fill=TEXT_PRIMARY)
        current_y += line_h + 14

    current_y += 26
    for line in subline_lines:
        bbox = draw.textbbox((0, 0), line, font=font_subline)
        line_w = bbox[2] - bbox[0]
        line_h = bbox[3] - bbox[1]
        draw.text(((WIDTH - line_w) // 2, current_y), line, font=font_subline, fill=TEXT_MUTED)
        current_y += line_h + 12

    # 2. Mockup / Graphic Area - Adjusted for tighter spacing to text
    mockup_box_top = max(current_y + 40, 480)
    mockup_box_left = 70
    mockup_box_right = WIDTH - 70
    mockup_box_bottom = HEIGHT - 40
    mockup_box_w = mockup_box_right - mockup_box_left
    mockup_box_h = mockup_box_bottom - mockup_box_top

    mockup_path = os.path.join(MOCKUPS_DIR, mockup_filename) if mockup_filename else None

    if is_template or not (mockup_path and os.path.exists(mockup_path)):
        # Render clean guide box with dimensions
        draw.rounded_rectangle(
            [mockup_box_left, mockup_box_top, mockup_box_right, mockup_box_bottom],
            radius=48,
            outline=GUIDE_BOX,
            width=3
        )
        # Inner hint text
        hint_font, _ = get_fonts(bold_size=36, reg_size=28, lang="en")
        t1 = f"Mockup / Screenshot Bereich (Screen {screen_num})"
        t2 = f"{mockup_box_w} × {mockup_box_h} px"
        t3 = "Hier dein Phone-Mockup oder App-Screenshot platzieren"

        bbox1 = draw.textbbox((0, 0), t1, font=hint_font)
        bbox2 = draw.textbbox((0, 0), t2, font=hint_font)
        bbox3 = draw.textbbox((0, 0), t3, font=hint_font)

        center_y = mockup_box_top + (mockup_box_h // 2)
        draw.text(((WIDTH - (bbox1[2] - bbox1[0])) // 2, center_y - 60), t1, font=hint_font, fill=GUIDE_ACCENT)
        draw.text(((WIDTH - (bbox2[2] - bbox2[0])) // 2, center_y), t2, font=hint_font, fill=TEXT_PRIMARY)
        draw.text(((WIDTH - (bbox3[2] - bbox3[0])) // 2, center_y + 60), t3, font=hint_font, fill=TEXT_MUTED)
    else:
        # Composite actual graphic mockup with high-quality proportional scaling
        mock_img = Image.open(mockup_path).convert("RGBA")
        scale = min(mockup_box_w / mock_img.width, mockup_box_h / mock_img.height)
        new_w = int(mock_img.width * scale)
        new_h = int(mock_img.height * scale)
        mock_resized = mock_img.resize((new_w, new_h), Image.Resampling.LANCZOS)
        paste_x = mockup_box_left + (mockup_box_w - new_w) // 2
        paste_y = mockup_box_top + (mockup_box_h - new_h) // 2
        img.paste(mock_resized, (paste_x, paste_y), mock_resized)

    return img

## Storyboard Data across all 35 Google Play Store Locales (Order: 2 -> 1 -> 3 -> 4 -> 5)
# Tuple structure: (screen_position, headline, subline, mockup_source_file)
STORYBOARD = {
    "ar": [
        (1, "كل المنصات. مع Shazam.", "Spotify، YouTube Music، Apple Music، Tidal والمزيد.", "screen_2.png"),
        (2, "0 نقرات. تشغيل فوري.", "يفتح روابط الموسيقى المستلمة فوراً في مشغلك المفضل.", "screen_1.png"),
        (3, "رابط واحد لجميع أصدقائك.", "أنشئ روابط ذكية تعمل بسلاسة على أي منصة.", "screen_3.png"),
        (4, "تحويل قوائم التشغيل كاملة.", "من Spotify إلى YouTube Music والمزيد بنقرة واحدة.", "screen_4.png"),
        (5, "خصوصية 100%. بدون إعلانات.", "بدون حسابات، بدون تسجيل، وبدون تتبع لسجل استماعك.", "screen_5.png")
    ],
    "bn-BD": [
        (1, "সব প্ল্যাটফর্ম। সাথে Shazam।", "Spotify, YouTube Music, Apple Music, Tidal ও আরও অনেক।", "screen_2.png"),
        (2, "০ ক্লিক। সরাসরি গান।", "যেকোনো মিউজিক লিঙ্ক স্বয়ংক্রিয়ভাবে প্রিয় অ্যাপে চালু হয়।", "screen_1.png"),
        (3, "সবার জন্য একটি স্মার্ট লিঙ্ক।", "ইউনিভার্সাল লিঙ্ক তৈরি করুন যা সব ডিভাইসে কাজ করে।", "screen_3.png"),
        (4, "সম্পূর্ণ প্লেলিস্ট রূপান্তর।", "Spotify থেকে YouTube Music ও আরও অনেক, ১-ট্যাপে सीधे।", "screen_4.png"),
        (5, "১০০% প্রাইভেট। বিজ্ঞাপনহীন।", "কোনো অ্যাকাউন্ট বা লগইন নেই, আপনার ডেটা সুরক্ষিত।", "screen_5.png")
    ],
    "cs-CZ": [
        (1, "Všechny platformy. Včetně Shazam.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer a další.", "screen_2.png"),
        (2, "0 kliknutí. Přímo hudba.", "Otevírá přijaté hudební odkazy ihned v oblíbené aplikaci.", "screen_1.png"),
        (3, "Jeden odkaz pro všechny přátele.", "Vytvářej univerzální chytré odkazy funkční na každém zařízení.", "screen_3.png"),
        (4, "Převod celých playlistů.", "Spotify do YouTube Music a další. Až 50 skladeb na jedno kliknutí.", "screen_4.png"),
        (5, "100% soukromí. Žádné reklamy.", "Bez účtů, bez přihlášení a bez sledování hudebního vkusu.", "screen_5.png")
    ],
    "da-DK": [
        (1, "Alle platforme. Inklusiv Shazam.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & mere.", "screen_2.png"),
        (2, "0 klik. Direkte musik.", "Åbner automatisk modtagne musiklinks i din yndlingsapp.", "screen_1.png"),
        (3, "Ét link til alle venner.", "Opret universelle links, der fungerer på alle platforme.", "screen_3.png"),
        (4, "Konverter hele playlister.", "Spotify til YouTube Music & mere. Op til 50 sange med 1 klik.", "screen_4.png"),
        (5, "100 % privat. Ingen reklamer.", "Ingen konto, intet login, ingen sporing af dine musikvaner.", "screen_5.png")
    ],
    "de-DE": [
        (1, "Alle Dienste. Shazam inklusive.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & mehr.", "screen_2.png"),
        (2, "0 Klicks. Direkt Musik.", "Öffnet empfangene Musik-Links sofort in deiner Wunsch-App.", "screen_1.png"),
        (3, "Ein Link für alle Freunde.", "Erstelle universelle Web-Links, die überall funktionieren.", "screen_3.png"),
        (4, "Ganze Playlists umwandeln.", "Spotify zu YouTube Music & mehr. Bis zu 50 Songs mit 1 Klick.", "screen_4.png"),
        (5, "100 % Privat. 0 Werbung.", "Kein Konto, kein Login, keine Analyse deines Musikgeschmacks.", "screen_5.png")
    ],
    "el-GR": [
        (1, "Όλες οι πλατφόρμες. Και Shazam.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & άλλα.", "screen_2.png"),
        (2, "0 κλικ. Άμεση αναπαραγωγή.", "Ανοίγει αυτόματα συνδέσμους μουσικής στην αγαπημένη σας εφαρμογή.", "screen_1.png"),
        (3, "Ένας σύνδεσμος για όλους.", "Δημιουργήστε έξυπνους συνδέσμους που λειτουργούν παντού.", "screen_3.png"),
        (4, "Μετατροπή ολόκληρων playlist.", "Spotify σε YouTube Music & άλλα με 1 πάτημα.", "screen_4.png"),
        (5, "100% απόρρητο. Χωρίς διαφημίσεις.", "Χωρίς λογαριασμούς, χωρίς σύνδεση, χωρίς καταγραφή δεδομένων.", "screen_5.png")
    ],
    "en-GB": [
        (1, "All Platforms. Plus Shazam.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & more.", "screen_2.png"),
        (2, "0 Clicks. Direct Playback.", "Instantly opens incoming music links in your preferred player.", "screen_1.png"),
        (3, "One Link for Everyone.", "Share smart links that work seamlessly across any platform.", "screen_3.png"),
        (4, "Convert Entire Playlists.", "Spotify to YouTube Music & more. Up to 50 tracks with 1-tap queue.", "screen_4.png"),
        (5, "100% Private. Zero Ads.", "No accounts, no logins, no listening habits collected.", "screen_5.png")
    ],
    "en-IN": [
        (1, "All Platforms. Plus Shazam.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & more.", "screen_2.png"),
        (2, "0 Clicks. Direct Playback.", "Instantly opens incoming music links in your preferred player.", "screen_1.png"),
        (3, "One Link for Everyone.", "Share smart links that work seamlessly across any platform.", "screen_3.png"),
        (4, "Convert Entire Playlists.", "Spotify to YouTube Music & more. Up to 50 tracks with 1-tap queue.", "screen_4.png"),
        (5, "100% Private. Zero Ads.", "No accounts, no logins, no listening habits collected.", "screen_5.png")
    ],
    "en-US": [
        (1, "All Platforms. Plus Shazam.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & more.", "screen_2.png"),
        (2, "0 Clicks. Direct Playback.", "Instantly opens incoming music links in your preferred player.", "screen_1.png"),
        (3, "One Link for Everyone.", "Share smart links that work seamlessly across any platform.", "screen_3.png"),
        (4, "Convert Entire Playlists.", "Spotify to YouTube Music & more. Up to 50 tracks with 1-tap queue.", "screen_4.png"),
        (5, "100% Private. Zero Ads.", "No accounts, no logins, no listening habits collected.", "screen_5.png")
    ],
    "es-419": [
        (1, "Todas las plataformas. Shazam incluido.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer y más.", "screen_2.png"),
        (2, "0 clics. Música al instante.", "Abre enlaces de música al instante en tu app favorita.", "screen_1.png"),
        (3, "Un enlace para todos tus amigos.", "Crea enlaces universales que funcionan en cualquier plataforma.", "screen_3.png"),
        (4, "Convierte playlists enteras.", "Spotify a YouTube Music y más. Hasta 50 canciones con 1 toque.", "screen_4.png"),
        (5, "100% privado. Sin anuncios.", "Sin cuentas, sin registros, sin recopilación de hábitos de escucha.", "screen_5.png")
    ],
    "es-ES": [
        (1, "Todas las plataformas. Shazam incluido.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer y más.", "screen_2.png"),
        (2, "0 clics. Música al instante.", "Abre enlaces de música al instante en tu app favorita.", "screen_1.png"),
        (3, "Un enlace para todos tus amigos.", "Crea enlaces universales que funcionan en cualquier plataforma.", "screen_3.png"),
        (4, "Convierte playlists enteras.", "Spotify a YouTube Music y más. Hasta 50 canciones con 1 toque.", "screen_4.png"),
        (5, "100% privado. Sin anuncios.", "Sin cuentas, sin registros, sin recopilación de hábitos de escucha.", "screen_5.png")
    ],
    "es-US": [
        (1, "Todas las plataformas. Shazam incluido.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer y más.", "screen_2.png"),
        (2, "0 clics. Música al instante.", "Abre enlaces de música al instante en tu app favorita.", "screen_1.png"),
        (3, "Un enlace para todos tus amigos.", "Crea enlaces universales que funcionan en cualquier plataforma.", "screen_3.png"),
        (4, "Convierte playlists enteras.", "Spotify a YouTube Music y más. Hasta 50 canciones con 1 toque.", "screen_4.png"),
        (5, "100% privado. Sin anuncios.", "Sin cuentas, sin registros, sin recopilación de hábitos de escucha.", "screen_4.png")
    ],
    "fi-FI": [
        (1, "Kaikki alustat. Myös Shazam.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & muut.", "screen_2.png"),
        (2, "0 klikkausta. Heti musiikkia.", "Avaa vastaanotetut musiikkilinkit heti suosikkisovelluksessasi.", "screen_1.png"),
        (3, "Yksi linkki kaikille ystäville.", "Luo älylinkkejä, jotka toimivat saumattomasti kaikkialla.", "screen_3.png"),
        (4, "Muunna kokonaisia soittolistoja.", "Spotifysta YouTube Musiciin & muut yhdellä napautuksella.", "screen_4.png"),
        (5, "100 % yksityinen. Ei mainoksia.", "Ei tilejä, ei kirjautumista, ei kuuntelutottumusten seurantaa.", "screen_5.png")
    ],
    "fr-CA": [
        (1, "Toutes les plateformes. Shazam inclus.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer et plus.", "screen_2.png"),
        (2, "0 clic. Musique instantanée.", "Ouvre directement les liens reçus dans votre lecteur préféré.", "screen_1.png"),
        (3, "Un lien unique pour tous vos amis.", "Partagez des liens universels compatibles avec chaque service.", "screen_3.png"),
        (4, "Convertissez des playlists entières.", "Spotify vers YouTube Music et plus. Jusqu'à 50 titres en 1 clic.", "screen_4.png"),
        (5, "100 % privé. Zéro publicité.", "Aucun compte, aucune connexion, aucun historique collecté.", "screen_5.png")
    ],
    "fr-FR": [
        (1, "Toutes les plateformes. Shazam inclus.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer et plus.", "screen_2.png"),
        (2, "0 clic. Musique instantanée.", "Ouvre directement les liens reçus dans votre lecteur préféré.", "screen_1.png"),
        (3, "Un lien unique pour tous vos amis.", "Partagez des liens universels compatibles avec chaque service.", "screen_3.png"),
        (4, "Convertissez des playlists entières.", "Spotify vers YouTube Music et plus. Jusqu'à 50 titres en 1 clic.", "screen_4.png"),
        (5, "100 % privé. Zéro publicité.", "Aucun compte, aucune connexion, aucun historique collecté.", "screen_5.png")
    ],
    "hi-IN": [
        (1, "सभी प्लेटफॉर्म। Shazam भी शामिल।", "Spotify, YouTube Music, Apple Music, Tidal, Deezer और अन्य।", "screen_2.png"),
        (2, "0 क्लिक। सीधा संगीत।", "प्राप्त संगीत लिंक तुरंत आपके पसंदीदा प्लेयर में खुलते हैं।", "screen_1.png"),
        (3, "सभी दोस्तों के लिए एक लिंक।", "यूनिवर्सल स्मार्ट लिंक बनाएं जो हर प्लेटफॉर्म पर काम करें।", "screen_3.png"),
        (4, "पूरी प्लेलिस्ट कन्वर्ट करें।", "Spotify से YouTube Music और अन्य। 1-टैप में आसान ट्रांसफर।", "screen_4.png"),
        (5, "100% निजी। विज्ञापन मुक्त।", "कोई खाता नहीं, कोई लॉगिन नहीं, कोई ट्रैकिंग नहीं।", "screen_5.png")
    ],
    "hu-HU": [
        (1, "Minden platform. Shazam is.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer és mások.", "screen_2.png"),
        (2, "0 kattintás. Azonnali zene.", "A kapott zenei linkeket azonnal a kedvenc alkalmazásodban nyitja meg.", "screen_1.png"),
        (3, "Egyetlen link minden barátodnak.", "Hozz létre univerzális linkeket, amelyek bárhol működnek.", "screen_3.png"),
        (4, "Teljes lejátszási listák konvertálása.", "Spotify-ról YouTube Music-ra és mások 1 koppintással.", "screen_4.png"),
        (5, "100% privát. Reklámmentes.", "Nincs fiók, nincs bejelentkezés, nem gyűjtünk adatokat.", "screen_5.png")
    ],
    "id": [
        (1, "Semua Platform. Termasuk Shazam.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & lainnya.", "screen_2.png"),
        (2, "0 Klik. Musik Langsung Putar.", "Buka tautan musik langsung di pemutar musik favorit Anda.", "screen_1.png"),
        (3, "Satu Tautan untuk Semua Teman.", "Buat tautan pintar universal yang bekerja di platform mana pun.", "screen_3.png"),
        (4, "Konversi Seluruh Playlist.", "Spotify ke YouTube Music & lainnya hingga 50 lagu.", "screen_4.png"),
        (5, "100% Pribadi. Bebas Iklan.", "Tanpa akun, tanpa login, tanpa pelacakan kebiasaan mendengar.", "screen_5.png")
    ],
    "it-IT": [
        (1, "Tutte le piattaforme. Shazam incluso.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer e altri.", "screen_2.png"),
        (2, "0 clic. Musica istantanea.", "Apre automaticamente i link musicali nella tua app preferita.", "screen_1.png"),
        (3, "Un link per tutti i tuoi amici.", "Crea smart link universali che funzionano ovunque.", "screen_3.png"),
        (4, "Converti intere playlist.", "Spotify su YouTube Music e altri. Fino a 50 brani con 1 tocco.", "screen_4.png"),
        (5, "100% privato. Zero pubblicità.", "Nessun account, nessun login, nessun tracciamento dei tuoi ascolti.", "screen_5.png")
    ],
    "ja-JP": [
        (1, "全配信サービス対応。Shazamも。", "Spotify、YouTube Music、Apple Music、Tidalなどに対応。", "screen_2.png"),
        (2, "0クリック。即座に再生。", "受信した音楽リンクをお気に入りのプレイヤーで直接開きます。", "screen_1.png"),
        (3, "みんなで共有できる1つのリンク。", "どの端末でも開けるユニバーサル共有リンクを作成。", "screen_3.png"),
        (4, "プレイリストを一括変換。", "SpotifyからYouTube Musicなどへ最大50曲を1タップで転送。", "screen_4.png"),
        (5, "完全プライベート。広告ゼロ。", "アカウント不要・ログイン不要・データ収集なし。", "screen_5.png")
    ],
    "ko-KR": [
        (1, "모든 플랫폼 지원. Shazam 포함.", "Spotify, YouTube Music, Apple Music, Tidal 등 완벽 지원.", "screen_2.png"),
        (2, "0클릭. 즉시 음악 재생.", "공유받은 음악 링크를 선호하는 앱에서 바로 재생합니다.", "screen_1.png"),
        (3, "모두를 위한 하나의 스마트 링크.", "모든 기기와 플랫폼에서 작동하는 범용 링크 생성.", "screen_3.png"),
        (4, "플레이리스트 전체 변환.", "Spotify에서 YouTube Music 등 최대 50곡을 원탭으로 변환.", "screen_4.png"),
        (5, "100% 개인정보 보호. 광고 없음.", "계정 및 로그인 불필요, 청취 기록 수집 없음.", "screen_5.png")
    ],
    "mr-IN": [
        (1, "सर्व प्लॅटफॉर्म्स. Shazam सह.", "Spotify, YouTube Music, Apple Music, Tidal आणि इतर बरेच.", "screen_2.png"),
        (2, "० क्लिक. थेट संगीत.", "मिळालेले म्युझिक लिंक्स थेट तुमच्या आवडत्या प्लेअरमध्ये उघडा.", "screen_1.png"),
        (3, "सर्व मित्रांसाठी एकच स्मार्ट लिंक.", "सर्व उपकरणांवर चालणाऱ्या युनिव्हर्सल लिंक्स तयार करा.", "screen_3.png"),
        (4, "संपूर्ण प्लेलिस्ट रूपांतरित करा.", "Spotify ते YouTube Music आणि इतर. १-टॅपमध्ये सोपे ट्रान्सफर.", "screen_4.png"),
        (5, "१००% सुरक्षित. जाहिरातमुक्त.", "कोणतेही खाते नाही, लॉगिन नाही, कोणताही डेटा ट्रॅकिंग नाही.", "screen_5.png")
    ],
    "nb-NO": [
        (1, "Alle plattformer. Shazam inkludert.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & mer.", "screen_2.png"),
        (2, "0 klikk. Direkte musikk.", "Åpner mottatte musikklenker direkte i favorittspilleren din.", "screen_1.png"),
        (3, "Én lenke for alle venner.", "Del universelle smartlenker som fungerer på tvers av plattformer.", "screen_3.png"),
        (4, "Konverter hele spillelister.", "Spotify til YouTube Music & mer. Opptil 50 sanger med 1 trykk.", "screen_4.png"),
        (5, "100 % privat. Ingen reklame.", "Ingen konto, ingen innlogging, ingen sporing av musikkvaner.", "screen_5.png")
    ],
    "nl-NL": [
        (1, "Alle platformen. Shazam inbegrepen.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & meer.", "screen_2.png"),
        (2, "0 klikken. Direct muziek.", "Opent ontvangen muzieklinks direct in jouw favoriete app.", "screen_1.png"),
        (3, "Eén link voor al je vrienden.", "Maak universele smart links die overal naadloos werken.", "screen_3.png"),
        (4, "Zet hele afspeellijsten om.", "Spotify naar YouTube Music & meer. Tot 50 nummers in 1 klik.", "screen_4.png"),
        (5, "100% privé. Geen advertenties.", "Geen accounts, geen logins, geen analyse van luistergedrag.", "screen_5.png")
    ],
    "pl-PL": [
        (1, "Wszystkie platformy. W tym Shazam.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer i inne.", "screen_2.png"),
        (2, "0 kliknięć. Muzyka od razu.", "Automatycznie otwiera linki muzyczne w Twojej ulubionej aplikacji.", "screen_1.png"),
        (3, "Jeden link dla wszystkich znajomych.", "Twórz uniwersalne smart linki działające na każdej platformie.", "screen_3.png"),
        (4, "Konwertuj całe playlisty.", "Spotify do YouTube Music i inne. Do 50 utworów jednym kliknięciem.", "screen_4.png"),
        (5, "100% prywatności. Zero reklam.", "Bez konta, bez logowania, bez śledzenia Twoich nawyków.", "screen_5.png")
    ],
    "pt-BR": [
        (1, "Todas as plataformas. Shazam incluso.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer e mais.", "screen_2.png"),
        (2, "0 cliques. Música instantânea.", "Abre links recebidos direto no seu player favorito.", "screen_1.png"),
        (3, "Um link para todos os amigos.", "Crie smart links universais que funcionam em qualquer serviço.", "screen_3.png"),
        (4, "Converta playlists inteiras.", "Spotify para YouTube Music e mais. Até 50 músicas com 1 toque.", "screen_4.png"),
        (5, "100% privado. Sem anúncios.", "Sem contas, sem login e sem rastreamento de hábitos musicais.", "screen_5.png")
    ],
    "pt-PT": [
        (1, "Todas as plataformas. Shazam incluso.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer e mais.", "screen_2.png"),
        (2, "0 cliques. Música instantânea.", "Abre links recebidos direto no seu player favorito.", "screen_1.png"),
        (3, "Um link para todos os amigos.", "Crie smart links universais que funcionam em qualquer serviço.", "screen_3.png"),
        (4, "Converta playlists inteiras.", "Spotify para YouTube Music e mais. Até 50 músicas com 1 toque.", "screen_4.png"),
        (5, "100% privado. Sem anúncios.", "Sem contas, sem login e sem rastreamento de hábitos musicais.", "screen_5.png")
    ],
    "ro-RO": [
        (1, "Toate platformele. Shazam inclus.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer și altele.", "screen_2.png"),
        (2, "0 clicuri. Muzică instant.", "Deschide linkurile primite direct în playerul tău preferat.", "screen_1.png"),
        (3, "Un singur link pentru toți prietenii.", "Creează smart linkuri universale care funcționează oriunde.", "screen_3.png"),
        (4, "Convertește playlisturi întregi.", "Spotify în YouTube Music și altele. Până la 50 de piese cu 1 tap.", "screen_4.png"),
        (5, "100% privat. Fără reclame.", "Fără conturi, fără autentificare, fără colectare de date.", "screen_5.png")
    ],
    "ro": [
        (1, "Toate platformele. Shazam inclus.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer și altele.", "screen_2.png"),
        (2, "0 clicuri. Muzică instant.", "Deschide linkurile primite direct în playerul tău preferat.", "screen_1.png"),
        (3, "Un singur link pentru toți prietenii.", "Creează smart linkuri universale care funcționează oriunde.", "screen_3.png"),
        (4, "Convertește playlisturi întregi.", "Spotify în YouTube Music și altele. Până la 50 de piese cu 1 tap.", "screen_4.png"),
        (5, "100% privat. Fără reclame.", "Fără conturi, fără autentificare, fără colectare de date.", "screen_5.png")
    ],
    "ru-RU": [
        (1, "Все платформы. Shazam включен.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer и другие.", "screen_2.png"),
        (2, "0 кликов. Музыка сразу.", "Мгновенно открывает полученные ссылки в любимом плеере.", "screen_1.png"),
        (3, "Одна ссылка для всех друзей.", "Создавайте универсальные смарт-ссылки для любой платформы.", "screen_3.png"),
        (4, "Конвертируйте целые плейлисты.", "Spotify в YouTube Music и другие. До 50 треков в 1 касание.", "screen_4.png"),
        (5, "100% приватность. Без рекламы.", "Без аккаунтов, без регистрации, без отслеживания истории.", "screen_5.png")
    ],
    "sv-SE": [
        (1, "Alla plattformar. Shazam inkluderat.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & mer.", "screen_2.png"),
        (2, "0 klick. Direkt musik.", "Öppnar mottagna musiklänkar direkt i din favoritspelare.", "screen_1.png"),
        (3, "En länk för alla vänner.", "Skapa universella smartlänkar som fungerar överallt.", "screen_3.png"),
        (4, "Konvertera hela spellistor.", "Spotify till YouTube Music & mer. Upp till 50 låtar med 1 tryck.", "screen_4.png"),
        (5, "100 % privat. Inga annonser.", "Inget konto, ingen inloggning, ingen spårning av musikvanor.", "screen_5.png")
    ],
    "tr-TR": [
        (1, "Tüm platformlar. Shazam dahil.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer ve daha fazlası.", "screen_2.png"),
        (2, "0 tıklama. Anında müzik.", "Gelen müzik bağlantılarını anında favori uygulamanızda açar.", "screen_1.png"),
        (3, "Tüm arkadaşlar için tek bağlantı.", "Her platformda sorunsuz çalışan akıllı bağlantılar oluşturun.", "screen_3.png"),
        (4, "Tüm çalma listelerini dönüştürün.", "Spotify'dan YouTube Music'e ve daha fazlası. Tek dokunuşla 50 şarkı.", "screen_4.png"),
        (5, "%100 gizli. Sıfır reklam.", "Hesap yok, giriş yok, müzik zevkiniz asla takip edilmez.", "screen_5.png")
    ],
    "uk": [
        (1, "Усі платформи. Shazam включно.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer та інші.", "screen_2.png"),
        (2, "0 кліків. Музика миттєво.", "Миттєво відкриває отримані посилання у вашому плеєрі.", "screen_1.png"),
        (3, "Одне посилання для всіх друзів.", "Створюйте універсальні смарт-посилання для будь-якої платформи.", "screen_3.png"),
        (4, "Конвертуйте цілі плейлисти.", "Spotify в YouTube Music та інші. До 50 треків в 1 дотик.", "screen_4.png"),
        (5, "100% приватність. Без реклами.", "Без акаунтів, без входу, без збору історії прослуховувань.", "screen_5.png")
    ],
    "vi": [
        (1, "Mọi nền tảng. Bao gồm Shazam.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer và hơn thế.", "screen_2.png"),
        (2, "0 lần chạm. Phát nhạc ngay.", "Tự động mở liên kết nhạc trong ứng dụng nghe nhạc yêu thích.", "screen_1.png"),
        (3, "Một liên kết cho tất cả bạn bè.", "Tạo smart link thông minh hoạt động mượt mà ở mọi nơi.", "screen_3.png"),
        (4, "Chuyển đổi toàn bộ danh sách phát.", "Spotify sang YouTube Music & hơn thế. Tối đa 50 bài hát với 1 chạm.", "screen_4.png"),
        (5, "100% riêng tư. Không quảng cáo.", "Không tài khoản, không đăng nhập, không thu thập dữ liệu.", "screen_5.png")
    ],
    "zh-CN": [
        (1, "全音乐平台支持。包含 Shazam。", "无缝支持 Spotify、YouTube Music、Apple Music、Tidal 等。", "screen_2.png"),
        (2, "0 次点击。直接播放音乐。", "自动识别接收的音乐链接并在您喜爱的播放器中打开。", "screen_1.png"),
        (3, "一个通用链接，分享给所有人。", "生成可在任何设备和平台上无缝打开的智能音乐链接。", "screen_3.png"),
        (4, "一键转换整份歌单。", "Spotify 转 YouTube Music 等平台，支持最多 50 首单曲一键导入。", "screen_4.png"),
        (5, "100% 隐私保护。零广告。", "无需注册账号，无需登录，绝不收集您的听歌偏好。", "screen_5.png")
    ],
    "th-TH": [
        (1, "รองรับทุกแพลตฟอร์มเพลง รวมถึง Shazam", "Spotify, YouTube Music, Apple Music, Tidal, Deezer และอื่นๆ", "screen_2.png"),
        (2, "0 คลิก เล่นเพลงทันที", "เปิดลิงก์เพลงที่ได้รับในแอปโปรดของคุณโดยอัตโนมัติ", "screen_1.png"),
        (3, "ลิงก์เดียวสำหรับเพื่อนทุกคน", "สร้างสมาร์ทลิงก์สากลที่ใช้งานได้บนทุกแพลตฟอร์ม", "screen_3.png"),
        (4, "แปลงเพลย์ลิสต์ทั้งชุด", "Spotify ไปยัง YouTube Music และอื่นๆ สูงสุด 50 เพลงใน 1 แตะ", "screen_4.png"),
        (5, "เป็นส่วนตัว 100% ไม่มีโฆษณา", "ไม่ต้องมีบัญชี ไม่ต้องเข้าสู่ระบบ ไม่มีการติดตามข้อมูล", "screen_5.png")
    ],
    "zh-TW": [
        (1, "支援各大音樂平台。包含 Shazam。", "無縫支援 Spotify、YouTube Music、Apple Music、Tidal 等。", "screen_2.png"),
        (2, "0 次點擊。立即播放音樂。", "自動識別收到的音樂連結並在偏好的播放器中開啟。", "screen_1.png"),
        (3, "單一智慧連結，分享給所有好友。", "建立可在任何裝置與平台上順暢開啟的通用音樂連結。", "screen_3.png"),
        (4, "一鍵轉換整份播放清單。", "Spotify 轉 YouTube Music 等平台，支援最多 50 首歌曲一鍵導入。", "screen_4.png"),
        (5, "100% 隱私保護。零廣告。", "無須註冊帳號，無須登入，絕不收集您的聽歌紀錄。", "screen_5.png")
    ]
}

def main():
    import sys
    locales_to_run = sys.argv[1:] if len(sys.argv) > 1 else list(STORYBOARD.keys())
    print(f"=== Generating Store Screenshot Templates for {len(locales_to_run)} Languages (Order: 1, 2, 3, 4, 5) ===")
    
    # 1. Pure Template Guide
    template_guide = render_screenshot(
        headline="Zeile Eins: Große Headline",
        subline="Zeile Zwei: Beschreibende Subline für die wichtigste Funktion.",
        screen_num=1,
        mockup_filename=None,
        lang_code="de",
        is_template=True
    )
    guide_path = os.path.join(TEMPLATES_DIR, "template_preview_guide.png")
    template_guide.save(guide_path, "PNG")
    print(f"✓ Created template guide: {guide_path}")

    # 2. Render all 5 screens for selected languages
    for lang_code in locales_to_run:
        if lang_code not in STORYBOARD:
            print(f"⚠️ Warning: Locale '{lang_code}' not in STORYBOARD, skipping.")
            continue
        screens = STORYBOARD[lang_code]
        lang_dir = os.path.join(SCREENSHOTS_DIR, lang_code)
        os.makedirs(lang_dir, exist_ok=True)
        for num, h, s, mock_file in screens:
            img = render_screenshot(h, s, num, mockup_filename=mock_file, lang_code=lang_code)
            out_p = os.path.join(lang_dir, f"screen_{num}.png")
            img.save(out_p, "PNG")
        print(f"✓ Created {len(screens)} screens for [{lang_code}]: {lang_dir}")

    print(f"\nAll requested screenshots ready in distribution/screenshots/!")

if __name__ == "__main__":
    main()
