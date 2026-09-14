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

if not os.path.exists(FONT_BOLD_PATH):
    FONT_BOLD_PATH = "/System/Library/Fonts/HelveticaNeue.ttc"
    FONT_REG_PATH = "/System/Library/Fonts/HelveticaNeue.ttc"

def get_fonts(bold_size=74, reg_size=40, lang="en"):
    """Load appropriate system fonts with fallback."""
    try:
        if lang in ["zh", "zh-CN", "zh-TW", "ja", "ko", "hi", "mr", "bn", "ar"]:
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

def wrap_text(text, font, max_width, draw):
    """Wrap text to fit within max_width."""
    lines = []
    paragraphs = text.split("\n")
    for para in paragraphs:
        words = para.split(" ")
        current_line = []
        for word in words:
            test_line = " ".join(current_line + [word])
            bbox = draw.textbbox((0, 0), test_line, font=font)
            w = bbox[2] - bbox[0]
            if w <= max_width:
                current_line.append(word)
            else:
                if current_line:
                    lines.append(" ".join(current_line))
                    current_line = [word]
                else:
                    lines.append(word)
                    current_line = []
        if current_line:
            lines.append(" ".join(current_line))
    return lines

def render_screenshot(headline, subline, screen_num, lang_code="en", is_template=False, mockup_path=None):
    """Render a complete store screenshot with localized text and mockup area."""
    img = create_background_gradient()
    draw = ImageDraw.Draw(img)
    font_headline, font_subline = get_fonts(bold_size=76, reg_size=38, lang=lang_code)

    # 1. Header Text Layout
    max_text_width = WIDTH - 160  # 80px side margins
    start_y = 150

    headline_lines = wrap_text(headline, font_headline, max_text_width, draw)
    subline_lines = wrap_text(subline, font_subline, max_text_width, draw)

    current_y = start_y
    for line in headline_lines:
        bbox = draw.textbbox((0, 0), line, font=font_headline)
        line_w = bbox[2] - bbox[0]
        line_h = bbox[3] - bbox[1]
        draw.text(((WIDTH - line_w) // 2, current_y), line, font=font_headline, fill=TEXT_PRIMARY)
        current_y += line_h + 16

    current_y += 18
    for line in subline_lines:
        bbox = draw.textbbox((0, 0), line, font=font_subline)
        line_w = bbox[2] - bbox[0]
        line_h = bbox[3] - bbox[1]
        draw.text(((WIDTH - line_w) // 2, current_y), line, font=font_subline, fill=TEXT_MUTED)
        current_y += line_h + 14

    # 2. Mockup / Graphic Area
    mockup_box_top = max(current_y + 40, 560)
    mockup_box_left = 90
    mockup_box_right = WIDTH - 90
    mockup_box_bottom = HEIGHT - 60
    mockup_box_w = mockup_box_right - mockup_box_left
    mockup_box_h = mockup_box_bottom - mockup_box_top

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

# Storyboard Data (Reduced to 4 punchy, high-converting core screens)
STORYBOARD = {
    "de-DE": [
        (1, "0 Klicks. Direkt Musik.", "Öffnet empfangene Musik-Links sofort in deiner Wunsch-App."),
        (2, "Alle Dienste. Shazam inklusive.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & mehr."),
        (3, "Ein Link für alle Freunde.", "Erstelle universelle Web-Links, die überall funktionieren."),
        (4, "100 % Privat. 0 Werbung.", "Kein Konto, kein Login, keine Analyse deines Musikgeschmacks.")
    ],
    "en-US": [
        (1, "0 Clicks. Direct Playback.", "Instantly opens incoming music links in your preferred player."),
        (2, "All Platforms. Plus Shazam.", "Spotify, YouTube Music, Apple Music, Tidal, Deezer & more."),
        (3, "One Link for Everyone.", "Share smart links that work seamlessly across any platform."),
        (4, "100% Private. Zero Ads.", "No accounts, no logins, no listening habits collected.")
    ]
}

def main():
    print("=== Generating Store Screenshot Templates ===")
    
    # 1. Pure Template Guide
    template_guide = render_screenshot(
        headline="Zeile Eins: Große Headline",
        subline="Zeile Zwei: Beschreibende Subline für die wichtigste Funktion.",
        screen_num=1,
        lang_code="de",
        is_template=True
    )
    guide_path = os.path.join(TEMPLATES_DIR, "template_preview_guide.png")
    template_guide.save(guide_path, "PNG")
    print(f"✓ Created template guide: {guide_path}")

    # 2. Render all 5 screens for German (de-DE)
    de_dir = os.path.join(SCREENSHOTS_DIR, "de-DE")
    os.makedirs(de_dir, exist_ok=True)
    for num, h, s in STORYBOARD["de-DE"]:
        mock_file = os.path.join(MOCKUPS_DIR, f"screen_{num}.png")
        img = render_screenshot(h, s, num, lang_code="de", is_template=not os.path.exists(mock_file), mockup_path=mock_file)
        out_p = os.path.join(de_dir, f"screen_{num}.png")
        img.save(out_p, "PNG")
        print(f"✓ Created de-DE Screen {num}: {out_p}")

    # 3. Render all 5 screens for English (en-US)
    en_dir = os.path.join(SCREENSHOTS_DIR, "en-US")
    os.makedirs(en_dir, exist_ok=True)
    for num, h, s in STORYBOARD["en-US"]:
        mock_file = os.path.join(MOCKUPS_DIR, f"screen_{num}.png")
        img = render_screenshot(h, s, num, lang_code="en", is_template=not os.path.exists(mock_file), mockup_path=mock_file)
        out_p = os.path.join(en_dir, f"screen_{num}.png")
        img.save(out_p, "PNG")
        print(f"✓ Created en-US Screen {num}: {out_p}")

    print("\nAll templates ready in distribution/screenshots/!")

if __name__ == "__main__":
    main()
