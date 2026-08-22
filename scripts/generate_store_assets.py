#!/usr/bin/env python3
"""
SongFlip - Google Play Store Graphic & Screenshot Generator
Generates:
- Feature Graphic (1024 x 500 px) in DE & EN
- 4 High-Resolution Screenshots (1080 x 2400 px) in DE & EN
"""

import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FONT_BOLD = '/System/Library/Fonts/Supplemental/Arial Bold.ttf'
FONT_REGULAR = '/System/Library/Fonts/Helvetica.ttc'

COLOR_BG = (10, 15, 26, 255) # Deep Slate #0A0F1A
COLOR_CARD_BG = (15, 23, 42, 240) # Slate 900
COLOR_CARD_BORDER = (30, 41, 59, 255) # Slate 800
COLOR_EMERALD = (16, 185, 129, 255) # #10B981
COLOR_EMERALD_LIGHT = (52, 211, 153, 255)
COLOR_TEXT_WHITE = (255, 255, 255, 255)
COLOR_TEXT_MUTED = (148, 163, 184, 255) # Slate 400

BRAND_SPOTIFY = (29, 185, 84, 255)
BRAND_APPLE = (250, 45, 72, 255)
BRAND_YOUTUBE = (255, 0, 0, 255)
BRAND_TIDAL = (0, 255, 255, 255)
BRAND_DEEZER = (162, 56, 255, 255)
BRAND_AMAZON = (0, 168, 225, 255)

ICON_PATH = '/Users/daniel.notthoff/GIT_Repos/_privat/songflip-web/images/android-chrome-512x512.png'

def get_fonts():
    return {
        'hero_title': ImageFont.truetype(FONT_BOLD, 74),
        'hero_sub': ImageFont.truetype(FONT_BOLD, 36),
        'feature_title': ImageFont.truetype(FONT_BOLD, 54),
        'feature_sub': ImageFont.truetype(FONT_BOLD, 26),
        'body': ImageFont.truetype(FONT_REGULAR, 24),
        'badge': ImageFont.truetype(FONT_BOLD, 22),
        'chip': ImageFont.truetype(FONT_BOLD, 20),
        'card_title': ImageFont.truetype(FONT_BOLD, 32),
        'card_body': ImageFont.truetype(FONT_REGULAR, 22),
        'small': ImageFont.truetype(FONT_REGULAR, 18),
    }

def draw_ambient_glow(img, w, h, primary_color=(16, 185, 129), secondary_color=(59, 130, 246)):
    glow = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(glow)
    draw.ellipse([int(w * 0.45), int(-h * 0.2), int(w * 1.2), int(h * 0.6)], fill=(primary_color[0], primary_color[1], primary_color[2], 55))
    draw.ellipse([int(-w * 0.2), int(h * 0.5), int(w * 0.6), int(h * 1.1)], fill=(secondary_color[0], secondary_color[1], secondary_color[2], 45))
    glow = glow.filter(ImageFilter.GaussianBlur(100))
    return Image.alpha_composite(img, glow)

def generate_feature_graphic(lang="de", out_dir="store_assets"):
    W, H = 1024, 500
    img = Image.new('RGBA', (W, H), COLOR_BG)
    img = draw_ambient_glow(img, W, H, (16, 185, 129), (162, 56, 255))
    draw = ImageDraw.Draw(img)
    fonts = get_fonts()

    # App Icon (Left)
    if os.path.exists(ICON_PATH):
        icon = Image.open(ICON_PATH).convert('RGBA').resize((240, 240), Image.Resampling.LANCZOS)
        card = Image.new('RGBA', (280, 280), (0, 0, 0, 0))
        cd = ImageDraw.Draw(card)
        cd.rounded_rectangle([0, 0, 280, 280], radius=48, fill=(15, 23, 42, 230), outline=COLOR_EMERALD, width=3)
        card.paste(icon, (20, 20), icon)
        
        shadow = Image.new('RGBA', (320, 320), (0, 0, 0, 0))
        sd = ImageDraw.Draw(shadow)
        sd.rounded_rectangle([20, 20, 300, 300], radius=48, fill=(0, 0, 0, 160))
        shadow = shadow.filter(ImageFilter.GaussianBlur(25))
        
        img.paste(shadow, (50, 90), shadow)
        img.paste(card, (70, 110), card)

    # Content (Right)
    tx = 390
    badge_text = "ANDROID • 0-KLICK REDIRECTOR" if lang == "de" else "ANDROID • 0-CLICK REDIRECTOR"
    title_text = "SongFlip"
    sub_text = "Jeden Musik-Link im eigenen Player öffnen." if lang == "de" else "Open any music link in your preferred player."
    chips = ["0 Klicks", "6 Plattformen", "100% Privat", "Kostenlos"] if lang == "de" else ["0 Clicks", "6 Platforms", "100% Private", "Free"]

    # Badge Pill
    bbox = draw.textbbox((0, 0), badge_text, font=fonts['badge'])
    bw, bh = bbox[2] - bbox[0] + 40, 38
    draw.rounded_rectangle([tx, 65, tx + bw, 65 + bh], radius=19, fill=(15, 23, 42, 220), outline=COLOR_EMERALD, width=2)
    draw.ellipse([tx + 14, 65 + 13, tx + 24, 65 + 23], fill=COLOR_EMERALD)
    draw.text((tx + 32, 65 + 8), badge_text, font=fonts['badge'], fill=COLOR_TEXT_WHITE)

    # Title
    draw.text((tx, 115), title_text, font=fonts['hero_title'], fill=COLOR_TEXT_WHITE)

    # Subtitle
    draw.text((tx, 205), sub_text, font=fonts['feature_sub'], fill=COLOR_EMERALD_LIGHT)

    # Service dots row
    sy = 265
    services = [
        ("Spotify", BRAND_SPOTIFY),
        ("Apple Music", BRAND_APPLE),
        ("YouTube Music", BRAND_YOUTUBE),
        ("Tidal", BRAND_TIDAL),
        ("Deezer", BRAND_DEEZER),
        ("Amazon Music", BRAND_AMAZON),
    ]
    cur_x = tx
    for sname, scolor in services:
        draw.ellipse([cur_x, sy + 6, cur_x + 14, sy + 20], fill=scolor)
        draw.text((cur_x + 20, sy), sname, font=fonts['small'], fill=COLOR_TEXT_WHITE)
        cur_x += draw.textbbox((0, 0), sname, font=fonts['small'])[2] + 36

    # Chips Bottom
    cy = 340
    cx = tx
    for chip in chips:
        c_bbox = draw.textbbox((0, 0), chip, font=fonts['chip'])
        cw, ch = c_bbox[2] - c_bbox[0] + 34, 42
        draw.rounded_rectangle([cx, cy, cx + cw, cy + ch], radius=12, fill=(30, 41, 59, 220), outline=(51, 65, 85, 255), width=1)
        draw.ellipse([cx + 12, cy + 17, cx + 20, cy + 25], fill=COLOR_EMERALD)
        draw.text((cx + 26, cy + 9), chip, font=fonts['chip'], fill=(226, 232, 240, 255))
        cx += cw + 12

    out_file = os.path.join(out_dir, lang, "feature_graphic_1024x500.png")
    img.convert('RGB').save(out_file, quality=95)
    print(f"✓ Generated Feature Graphic ({lang}): {out_file}")


def render_phone_mockup_frame(draw, x, y, w, h):
    # Outer device bezel
    draw.rounded_rectangle([x, y, x + w, y + h], radius=56, fill=(15, 23, 42, 255), outline=(51, 65, 85, 255), width=4)
    # Inner display area
    draw.rounded_rectangle([x + 12, y + 12, x + w - 12, y + h - 12], radius=46, fill=(7, 11, 20, 255))
    # Camera pill notch
    pw = 140
    draw.rounded_rectangle([x + (w - pw)//2, y + 22, x + (w + pw)//2, y + 54], radius=16, fill=(0, 0, 0, 255))


def generate_screenshot_1(lang="de", out_dir="store_assets"):
    W, H = 1080, 2400
    img = Image.new('RGBA', (W, H), COLOR_BG)
    img = draw_ambient_glow(img, W, H, (16, 185, 129), (59, 130, 246))
    draw = ImageDraw.Draw(img)
    fonts = get_fonts()

    # Top Header Copy
    tag = "0-KLICK MUSIK-REDIRECTOR" if lang == "de" else "0-CLICK MUSIC REDIRECTOR"
    head = "Jeder Musik-Link.\nDein Wunsch-Player." if lang == "de" else "Any Music Link.\nYour Preferred Player."
    sub = "Geteilte Links öffnen sich sofort im eigenen Player." if lang == "de" else "Shared links open instantly in your chosen player."

    draw.text((70, 120), tag, font=fonts['badge'], fill=COLOR_EMERALD)
    draw.text((70, 170), head, font=fonts['hero_title'], fill=COLOR_TEXT_WHITE, spacing=16)
    draw.text((70, 360), sub, font=fonts['hero_sub'], fill=COLOR_TEXT_MUTED)

    # Phone Mockup
    px, py, pw, ph = 100, 480, 880, 1860
    render_phone_mockup_frame(draw, px, py, pw, ph)

    # App UI Inside Mockup
    ix = px + 40
    iy = py + 90
    
    # 1. App Header inside phone
    draw.rounded_rectangle([ix, iy, ix + pw - 80, iy + 140], radius=24, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=2)
    draw.ellipse([ix + 24, iy + 25, ix + 114, iy + 115], fill=(16, 185, 129, 40), outline=COLOR_EMERALD, width=2)
    if os.path.exists(ICON_PATH):
        mini_icon = Image.open(ICON_PATH).convert('RGBA').resize((70, 70), Image.Resampling.LANCZOS)
        img.paste(mini_icon, (ix + 34, iy + 35), mini_icon)
    draw.text((ix + 130, iy + 34), "SongFlip", font=fonts['card_title'], fill=COLOR_TEXT_WHITE)
    draw.text((ix + 130, iy + 78), "Automatischer Redirector" if lang == "de" else "Automatic Redirector", font=fonts['small'], fill=COLOR_TEXT_MUTED)

    # 2. Live Status Banner
    sy = iy + 170
    draw.rounded_rectangle([ix, sy, ix + pw - 80, sy + 110], radius=20, fill=(16, 185, 129, 35), outline=COLOR_EMERALD, width=2)
    draw.ellipse([ix + 30, sy + 43, ix + 54, sy + 67], fill=COLOR_EMERALD)
    status_t = "Links sind aktiviert & einsatzbereit" if lang == "de" else "Links are active & ready"
    draw.text((ix + 70, sy + 38), status_t, font=fonts['card_title'], fill=COLOR_EMERALD_LIGHT)

    # 3. Target Services List
    ty = sy + 140
    target_heading = "Bevorzugter Ziel-Player" if lang == "de" else "Preferred Target Player"
    draw.text((ix + 10, ty), target_heading, font=fonts['card_title'], fill=COLOR_TEXT_WHITE)

    services_ui = [
        ("YouTube Music", BRAND_YOUTUBE, True),
        ("Spotify", BRAND_SPOTIFY, False),
        ("Apple Music", BRAND_APPLE, False),
        ("Tidal", BRAND_TIDAL, False),
        ("Deezer", BRAND_DEEZER, False),
        ("Amazon Music", BRAND_AMAZON, False),
    ]
    cur_row_y = ty + 60
    for sname, scolor, is_sel in services_ui:
        border_c = scolor if is_sel else COLOR_CARD_BORDER
        bg_c = (scolor[0], scolor[1], scolor[2], 30) if is_sel else COLOR_CARD_BG
        draw.rounded_rectangle([ix, cur_row_y, ix + pw - 80, cur_row_y + 95], radius=18, fill=bg_c, outline=border_c, width=2 if is_sel else 1)
        draw.ellipse([ix + 26, cur_row_y + 36, ix + 50, cur_row_y + 60], fill=scolor)
        draw.text((ix + 68, cur_row_y + 30), sname, font=fonts['card_title'], fill=COLOR_TEXT_WHITE)
        
        # Checkmark for selected
        if is_sel:
            draw.text((ix + pw - 140, cur_row_y + 30), "✓", font=fonts['card_title'], fill=scolor)
        cur_row_y += 112

    out_file = os.path.join(out_dir, lang, "screenshot_1_hero.png")
    img.convert('RGB').save(out_file, quality=95)
    print(f"✓ Generated Screenshot 1 ({lang}): {out_file}")


def generate_screenshot_2(lang="de", out_dir="store_assets"):
    W, H = 1080, 2400
    img = Image.new('RGBA', (W, H), COLOR_BG)
    img = draw_ambient_glow(img, W, H, (29, 185, 84), (255, 0, 0))
    draw = ImageDraw.Draw(img)
    fonts = get_fonts()

    tag = "NAHTLOSER HINTERGRUND-ABLAUF" if lang == "de" else "SEAMLESS BACKGROUND FLOW"
    head = "0 Klicks. Keine Wartezeit.\nVolle Magie." if lang == "de" else "0 Clicks. Zero Delay.\nPure Magic."
    sub = "WhatsApp, Telegram oder Browser – Link antippen & hören." if lang == "de" else "WhatsApp, Telegram, or Browser – tap link & listen."

    draw.text((70, 120), tag, font=fonts['badge'], fill=COLOR_EMERALD)
    draw.text((70, 170), head, font=fonts['hero_title'], fill=COLOR_TEXT_WHITE, spacing=16)
    draw.text((70, 360), sub, font=fonts['hero_sub'], fill=COLOR_TEXT_MUTED)

    # Visual Flow Cards
    fy = 520
    
    # Step 1: Received WhatsApp Link
    draw.rounded_rectangle([100, fy, 980, fy + 260], radius=28, fill=(15, 23, 42, 240), outline=COLOR_CARD_BORDER, width=2)
    draw.ellipse([140, fy + 35, 200, fy + 95], fill=(37, 211, 102, 255))
    draw.text((220, fy + 40), "WhatsApp Nachricht empfangen" if lang == "de" else "WhatsApp message received", font=fonts['card_title'], fill=COLOR_TEXT_WHITE)
    draw.rounded_rectangle([140, fy + 120, 940, fy + 220], radius=16, fill=(30, 41, 59, 200))
    draw.text((165, fy + 145), "https://open.spotify.com/track/4u7EnebtmKWz...", font=fonts['body'], fill=COLOR_EMERALD_LIGHT)

    # Arrow Down
    draw.text((520, fy + 285), "↓", font=fonts['hero_title'], fill=COLOR_EMERALD)

    # Step 2: SongFlip 0-Click Interceptor
    sy2 = fy + 380
    draw.rounded_rectangle([100, sy2, 980, sy2 + 300], radius=28, fill=(16, 185, 129, 25), outline=COLOR_EMERALD, width=3)
    draw.ellipse([140, sy2 + 35, 210, sy2 + 105], fill=COLOR_EMERALD)
    draw.text((230, sy2 + 45), "SongFlip Link-Auflösung (~150 ms)" if lang == "de" else "SongFlip Link Resolution (~150 ms)", font=fonts['card_title'], fill=COLOR_TEXT_WHITE)
    draw.text((140, sy2 + 130), "• 0 Zwischenschritte, kein sichtbares App-Flackern" if lang == "de" else "• Zero intermediate steps, zero app flickering", font=fonts['card_body'], fill=COLOR_TEXT_MUTED)
    draw.text((140, sy2 + 180), "• Direct Video ID Extraction für Sofort-Wiedergabe" if lang == "de" else "• Direct Video ID extraction for instant playback", font=fonts['card_body'], fill=COLOR_TEXT_MUTED)
    draw.text((140, sy2 + 230), "• 100% Privat: Keine Benutzerdaten, keine Tracker" if lang == "de" else "• 100% Private: No user data, no trackers", font=fonts['card_body'], fill=COLOR_EMERALD_LIGHT)

    # Arrow Down
    draw.text((520, sy2 + 325), "↓", font=fonts['hero_title'], fill=COLOR_EMERALD)

    # Step 3: Direct Target Player Playback
    sy3 = sy2 + 420
    draw.rounded_rectangle([100, sy3, 980, sy3 + 360], radius=28, fill=(15, 23, 42, 240), outline=BRAND_YOUTUBE, width=2)
    draw.ellipse([140, sy3 + 35, 210, sy3 + 105], fill=BRAND_YOUTUBE)
    draw.text((230, sy3 + 45), "Direkte Wiedergabe in YouTube Music" if lang == "de" else "Direct Playback in YouTube Music", font=fonts['card_title'], fill=COLOR_TEXT_WHITE)
    
    # Music Player Preview Card inside
    draw.rounded_rectangle([140, sy3 + 130, 940, sy3 + 310], radius=20, fill=(30, 41, 59, 220))
    draw.rounded_rectangle([170, sy3 + 155, 300, sy3 + 285], radius=16, fill=(255, 0, 0, 60), outline=BRAND_YOUTUBE, width=1)
    draw.text((215, sy3 + 195), "▶", font=fonts['hero_sub'], fill=COLOR_TEXT_WHITE)
    draw.text((330, sy3 + 170), "Bohemian Rhapsody", font=fonts['card_title'], fill=COLOR_TEXT_WHITE)
    draw.text((330, sy3 + 215), "Queen • A Night at the Opera", font=fonts['card_body'], fill=COLOR_TEXT_MUTED)
    draw.text((330, sy3 + 255), "Playing instantly in your player...", font=fonts['small'], fill=COLOR_EMERALD_LIGHT)

    out_file = os.path.join(out_dir, lang, "screenshot_2_background.png")
    img.convert('RGB').save(out_file, quality=95)
    print(f"✓ Generated Screenshot 2 ({lang}): {out_file}")


def generate_screenshot_3(lang="de", out_dir="store_assets"):
    W, H = 1080, 2400
    img = Image.new('RGBA', (W, H), COLOR_BG)
    img = draw_ambient_glow(img, W, H, (250, 45, 72), (0, 255, 255))
    draw = ImageDraw.Draw(img)
    fonts = get_fonts()

    tag = "UNIVERSAL & FLEXIBEL" if lang == "de" else "UNIVERSAL & FLEXIBLE"
    head = "Songs, komplette Alben\n& Künstler-Profile." if lang == "de" else "Songs, Full Albums\n& Artist Profiles."
    sub = "Unterstützt alle 6 großen Musik-Streamingdienste." if lang == "de" else "Supports all 6 major music streaming platforms."

    draw.text((70, 120), tag, font=fonts['badge'], fill=COLOR_EMERALD)
    draw.text((70, 170), head, font=fonts['hero_title'], fill=COLOR_TEXT_WHITE, spacing=16)
    draw.text((70, 360), sub, font=fonts['hero_sub'], fill=COLOR_TEXT_MUTED)

    # 6 Service Grid Cards
    services_grid = [
        ("Spotify", "open.spotify.com", BRAND_SPOTIFY, "Tracks, Alben & Künstler" if lang == "de" else "Tracks, Albums & Artists"),
        ("Apple Music", "music.apple.com", BRAND_APPLE, "Tracks, Alben & Künstler" if lang == "de" else "Tracks, Albums & Artists"),
        ("YouTube Music", "music.youtube.com", BRAND_YOUTUBE, "Direct Instant Playback" if lang == "de" else "Direct Instant Playback"),
        ("Tidal", "listen.tidal.com", BRAND_TIDAL, "High-Res Master Matches" if lang == "de" else "High-Res Master Matches"),
        ("Deezer", "deezer.com", BRAND_DEEZER, "Kurzlinks & Kataloge" if lang == "de" else "Shortlinks & Catalog"),
        ("Amazon Music", "music.amazon.com", BRAND_AMAZON, "Alle Länder & Domains" if lang == "de" else "All Countries & Domains"),
    ]

    gy = 490
    for sname, sdomain, scolor, sfeature in services_grid:
        draw.rounded_rectangle([90, gy, 990, gy + 175], radius=24, fill=COLOR_CARD_BG, outline=(scolor[0], scolor[1], scolor[2], 120), width=2)
        draw.ellipse([130, gy + 45, 210, gy + 125], fill=(scolor[0], scolor[1], scolor[2], 50), outline=scolor, width=2)
        draw.text((155, gy + 65), "♫", font=fonts['card_title'], fill=scolor)
        
        draw.text((240, gy + 38), sname, font=fonts['card_title'], fill=COLOR_TEXT_WHITE)
        draw.text((240, gy + 80), sdomain, font=fonts['card_body'], fill=scolor)
        draw.text((240, gy + 118), f"✓ {sfeature}", font=fonts['small'], fill=COLOR_TEXT_MUTED)
        gy += 205

    # Bottom Privacy Pill
    draw.rounded_rectangle([90, gy + 30, 990, gy + 130], radius=20, fill=(16, 185, 129, 30), outline=COLOR_EMERALD, width=2)
    draw.text((140, gy + 62), "🛡️ 100% Datenschutz: Keine Benutzerkonten & werbefrei" if lang == "de" else "🛡️ 100% Privacy: No accounts, zero tracking & ad-free", font=fonts['card_body'], fill=COLOR_EMERALD_LIGHT)

    out_file = os.path.join(out_dir, lang, "screenshot_3_services.png")
    img.convert('RGB').save(out_file, quality=95)
    print(f"✓ Generated Screenshot 3 ({lang}): {out_file}")


def generate_screenshot_4(lang="de", out_dir="store_assets"):
    W, H = 1080, 2400
    img = Image.new('RGBA', (W, H), COLOR_BG)
    img = draw_ambient_glow(img, W, H, (16, 185, 129), (245, 158, 11))
    draw = ImageDraw.Draw(img)
    fonts = get_fonts()

    tag = "KONTROLLE & LOKALISIERUNG" if lang == "de" else "CONTROL & LOCALIZATION"
    head = "Quick Settings Tile\n& 22 Sprachen." if lang == "de" else "Quick Settings Tile\n& 22 Languages."
    sub = "Pausieren direkt aus der Android-Leiste." if lang == "de" else "Pause directly from Android quick shade."

    draw.text((70, 120), tag, font=fonts['badge'], fill=COLOR_EMERALD)
    draw.text((70, 170), head, font=fonts['hero_title'], fill=COLOR_TEXT_WHITE, spacing=16)
    draw.text((70, 360), sub, font=fonts['hero_sub'], fill=COLOR_TEXT_MUTED)

    # 1. Quick Settings Tile Simulation
    qy = 490
    draw.rounded_rectangle([90, qy, 990, qy + 360], radius=28, fill=(15, 23, 42, 240), outline=COLOR_CARD_BORDER, width=2)
    draw.text((130, qy + 35), "Android Quick Settings Leiste" if lang == "de" else "Android Quick Settings Shade", font=fonts['card_title'], fill=COLOR_TEXT_WHITE)
    
    # Active Tile
    draw.rounded_rectangle([130, qy + 95, 520, qy + 300], radius=22, fill=(16, 185, 129, 50), outline=COLOR_EMERALD, width=2)
    draw.ellipse([160, qy + 125, 220, qy + 185], fill=COLOR_EMERALD)
    draw.text((240, qy + 135), "SongFlip", font=fonts['card_title'], fill=COLOR_TEXT_WHITE)
    draw.text((240, qy + 185), "● Aktiv & Bereit" if lang == "de" else "● Active & Ready", font=fonts['small'], fill=COLOR_EMERALD_LIGHT)
    draw.text((160, qy + 235), "Tippen für 15m, 1h Pause" if lang == "de" else "Tap for 15m, 1h pause", font=fonts['small'], fill=COLOR_TEXT_MUTED)

    # Paused Tile Preview
    draw.rounded_rectangle([560, qy + 95, 950, qy + 300], radius=22, fill=(245, 158, 11, 30), outline=(245, 158, 11, 200), width=1)
    draw.ellipse([590, qy + 125, 650, qy + 185], fill=(245, 158, 11, 255))
    draw.text((670, qy + 135), "SongFlip", font=fonts['card_title'], fill=COLOR_TEXT_WHITE)
    draw.text((670, qy + 185), "⏸ Bis 06:00 Uhr" if lang == "de" else "⏸ Until 06:00 AM", font=fonts['small'], fill=(245, 158, 11, 255))
    draw.text((590, qy + 235), "Intelligente Pause" if lang == "de" else "Smart Pause timer", font=fonts['small'], fill=COLOR_TEXT_MUTED)

    # 2. Languages Card
    ly = qy + 400
    draw.rounded_rectangle([90, ly, 990, ly + 880], radius=28, fill=(15, 23, 42, 240), outline=COLOR_CARD_BORDER, width=2)
    draw.text((130, ly + 40), "22 Nativ unterstützte Sprachen" if lang == "de" else "22 Natively Supported Languages", font=fonts['card_title'], fill=COLOR_TEXT_WHITE)
    draw.text((130, ly + 90), "Vollständig lokalisiert für weltweite Musik-Freunde:" if lang == "de" else "Fully localized for music fans worldwide:", font=fonts['card_body'], fill=COLOR_TEXT_MUTED)

    languages = [
        ("🇩🇪 Deutsch", "🇬🇧 English"),
        ("🇫🇷 Français", "🇪🇸 Español"),
        ("🇮🇹 Italiano", "🇵🇹 Português"),
        ("🇳🇱 Nederlands", "🇵🇱 Polski"),
        ("🇩🇰 Dansk", "🇸🇪 Svenska"),
        ("🇳🇴 Norsk", "🇺🇦 Українська"),
        ("🇯🇵 日本語", "🇰🇷 한국어"),
        ("🇨🇳 简体中文", "🇹🇷 Türkçe"),
        ("🇮🇳 हिन्दी", "🇮🇩 Bahasa Indon."),
    ]
    cur_lang_y = ly + 150
    for l1, l2 in languages:
        draw.rounded_rectangle([130, cur_lang_y, 520, cur_lang_y + 60], radius=14, fill=(30, 41, 59, 180))
        draw.text((160, cur_lang_y + 16), l1, font=fonts['card_body'], fill=COLOR_TEXT_WHITE)

        draw.rounded_rectangle([560, cur_lang_y, 950, cur_lang_y + 60], radius=14, fill=(30, 41, 59, 180))
        draw.text((590, cur_lang_y + 16), l2, font=fonts['card_body'], fill=COLOR_TEXT_WHITE)
        cur_lang_y += 76

    out_file = os.path.join(out_dir, lang, "screenshot_4_settings.png")
    img.convert('RGB').save(out_file, quality=95)
    print(f"✓ Generated Screenshot 4 ({lang}): {out_file}")


def main():
    print("🚀 Generating SongFlip Google Play Store Assets...")
    out_dir = "/Users/daniel.notthoff/GIT_Repos/_privat/songflip/store_assets"
    
    for lang in ["de", "en"]:
        generate_feature_graphic(lang, out_dir)
        generate_screenshot_1(lang, out_dir)
        generate_screenshot_2(lang, out_dir)
        generate_screenshot_3(lang, out_dir)
        generate_screenshot_4(lang, out_dir)

    print("\n🎉 ALL STORE ASSETS GENERATED SUCCESSFULLY!")

if __name__ == '__main__':
    main()
