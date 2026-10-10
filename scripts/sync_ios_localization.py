#!/usr/bin/env python3
"""
Syncs missing translations (pro_*, playlist_*, feedback_*, history_*) from Android strings.xml
into iosApp/SongFlip/SongFlip/LocalizationManager.swift for all 22 iOS supported languages.
"""

import os
import re
import xml.etree.ElementTree as ET

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID_RES = os.path.join(BASE_DIR, "app", "src", "main", "res")
IOS_LOCALIZATION_PATH = os.path.join(BASE_DIR, "iosApp", "SongFlip", "SongFlip", "LocalizationManager.swift")

LANG_MAP = {
    "en": "values",
    "de": "values-de",
    "da": "values-da",
    "nb": "values-nb",
    "sv": "values-sv",
    "nl": "values-nl",
    "fr": "values-fr",
    "es": "values-es",
    "it": "values-it",
    "pt": "values-pt",
    "pl": "values-pl",
    "ru": "values-ru",
    "tr": "values-tr",
    "uk": "values-uk",
    "ja": "values-ja",
    "ko": "values-ko",
    "zh": "values-zh",
    "in": "values-in",
    "vi": "values-vi",
    "bn": "values-bn",
    "hi": "values-hi",
    "mr": "values-mr",
    "cs": "values-cs",
    "el": "values-el",
    "fi": "values-fi",
    "hu": "values-hu",
    "ro": "values-ro",
    "th": "values-th",
}

def clean_android_text(text):
    if not text:
        return ""
    # Unescape escaped quotes and apos
    text = text.replace(r"\'", "'").replace(r'\"', '"')
    # Convert %% to %
    text = text.replace("%%", "%")
    # Convert Android string format specifiers (%1$s, %s) to iOS (%1$@, %@)
    text = re.sub(r'%(\d+)\$s', r'%\1$@', text)
    text = re.sub(r'%s', r'%@', text)
    # Escape quotes for Swift string literal
    text = text.replace('\\', '\\\\').replace('"', r'\"')
    return text

def load_android_strings(values_dir):
    strings_file = os.path.join(ANDROID_RES, values_dir, "strings.xml")
    if not os.path.exists(strings_file):
        return {}
    res = {}
    try:
        tree = ET.parse(strings_file)
        root = tree.getroot()
        for s in root.findall("string"):
            name = s.get("name")
            text = s.text or ""
            if name:
                res[name] = clean_android_text(text)
        for p in root.findall("plurals"):
            name = p.get("name")
            other = p.find("item[@quantity='other']")
            if other is not None and other.text:
                res[name] = clean_android_text(other.text)
    except Exception as e:
        print(f"Error parsing {strings_file}: {e}")
    return res

def main():
    en_strings = load_android_strings("values")
    
    # Read LocalizationManager.swift
    with open(IOS_LOCALIZATION_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    # Extra iOS-specific keys if needed
    extra_keys = {
        "pro_upgrade_card_subtitle": {
            "en": "Upgrade for extended history & playlists",
            "de": "Upgraden für erweiterte History & Playlists",
            "da": "Opgrader til udvidet historik & playlister",
            "nb": "Oppgrader for utvidet historikk og spillelister",
            "sv": "Uppgradera för utökad historik och spellistor",
            "nl": "Upgrade voor uitgebreide geschiedenis en afspeellijsten",
            "fr": "Passez à la version Pro pour un historique et des playlists étendus",
            "es": "Mejora a Pro para historial y listas de reproducción ampliadas",
            "it": "Passa a Pro per cronologia e playlist ampliate",
            "pt": "Atualize para Pro para histórico e playlists estendidos",
            "pl": "Uaktualnij do wersji Pro, aby rozszerzyć historię i playlisty",
            "ru": "Перейдите на Pro для расширенной истории и плейлистов",
            "tr": "Genişletilmiş geçmiş ve çalma listeleri için Pro'ya yükseltin",
            "uk": "Оновіть до Pro для розширеної історії та списків відтворення",
            "ja": "拡張履歴とプレイリストのためにProにアップグレード",
            "ko": "더 긴 기록 및 재생목록을 위해 Pro로 업그레이드",
            "zh": "升级以获取扩展历史记录和播放列表",
            "in": "Tingkatkan ke Pro untuk riwayat & daftar putar diperluas",
            "vi": "Nâng cấp lên Pro để có lịch sử & danh sách phát mở rộng",
            "bn": "বর্ধিত ইতিহাস এবং প্লেলিস্টের জন্য Pro-তে আপগ্রেড করুন",
            "hi": "विस्तारित इतिहास और प्लेलिस्ट के लिए Pro में अपग्रेड करें",
            "mr": "विस्तारित इतिहास आणि प्लेलिस्टसाठी Pro वर अपग्रेड करा"
        }
    }

    # For each language, find its dictionary block in LocalizationManager.swift
    for lang, val_dir in LANG_MAP.items():
        android_dict = load_android_strings(val_dir)
        # merge with en as fallback
        merged = dict(en_strings)
        merged.update(android_dict)
        
        # Add extra keys
        for ek, trans in extra_keys.items():
            if lang in trans:
                merged[ek] = clean_android_text(trans[lang])
            elif "en" in trans:
                merged[ek] = clean_android_text(trans["en"])

        # Match the language block: `"<lang>": [` ... `]`
        pattern = re.compile(rf'(\s+"{lang}":\s*\[)(.*?)(\n\s*\])', re.DOTALL)
        match = pattern.search(content)
        if not match:
            print(f"Warning: Language block '{lang}' not found in LocalizationManager.swift")
            continue

        prefix, body, suffix = match.groups()
        
        # Find existing keys in body
        existing_keys = set(re.findall(r'"([^"]+)":', body))
        
        # Determine new entries to append
        new_entries = []
        for k in sorted(merged.keys()):
            if k not in existing_keys:
                v = merged[k]
                new_entries.append(f'            "{k}": "{v}",')
        
        if new_entries:
            # Body ends with either a comma or newline, ensure clean insertion
            clean_body = body.rstrip()
            if not clean_body.endswith(","):
                clean_body += ","
            clean_body += "\n" + "\n".join(new_entries)
            content = content[:match.start()] + prefix + clean_body + suffix + content[match.end():]
            print(f"Added {len(new_entries)} keys to '{lang}'")

    with open(IOS_LOCALIZATION_PATH, "w", encoding="utf-8") as f:
        f.write(content)

    print("Successfully synchronized iOS localization!")

if __name__ == "__main__":
    main()
