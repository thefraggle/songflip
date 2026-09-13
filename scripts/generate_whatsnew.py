#!/usr/bin/env python3
import os
import re
import sys
import time

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WHATSNEW_DIR = os.path.join(BASE_DIR, "distribution", "whatsnew")

TARGET_LOCALES = {
    'de-DE': 'de',
    'en-US': 'en',
    'en-GB': 'en',
    'en-IN': 'en',
    'fr-FR': 'fr',
    'fr-CA': 'fr',
    'it-IT': 'it',
    'es-ES': 'es',
    'es-US': 'es',
    'es-419': 'es',
    'pt-PT': 'pt',
    'pt-BR': 'pt',
    'pl-PL': 'pl',
    'nl-NL': 'nl',
    'sv-SE': 'sv',
    'da-DK': 'da',
    'nb-NO': 'no',
    'tr-TR': 'tr',
    'ru-RU': 'ru',
    'uk': 'uk',
    'ja-JP': 'ja',
    'ko-KR': 'ko',
    'zh-CN': 'zh-CN',
    'zh-TW': 'zh-TW',
    'id': 'id',
    'vi': 'vi',
    'hi-IN': 'hi',
    'bn-BD': 'bn',
    'mr-IN': 'mr',
    'ar': 'ar',
    'cs-CZ': 'cs',
    'el-GR': 'el',
    'fi-FI': 'fi',
    'hu-HU': 'hu',
    'ro': 'ro',
}

LANG_CODE_MAP = {
    'nb': 'no',
    'zh': 'zh-CN',
    'es': 'es',
}

def truncate_to_bytes(text, max_bytes=500, suffix="..."):
    """Google Play restricts release notes to 500 UTF-8 bytes, not characters.
    Multibyte characters (e.g. umlauts, CJK) consume 2-4 bytes each.
    This cleanly keeps complete bullet lines without truncating mid-character."""
    text = text.strip()
    if len(text.encode('utf-8')) <= max_bytes:
        return text

    lines = text.split('\n')
    result = []
    for line in lines:
        if not line.strip():
            continue
        test_text = '\n'.join(result + [line]).strip()
        if len(test_text.encode('utf-8')) > max_bytes:
            break
        result.append(line)

    if result:
        return '\n'.join(result).strip()

    suffix_bytes = suffix.encode('utf-8')
    encoded = text.encode('utf-8')[:max_bytes - len(suffix_bytes)]
    return encoded.decode('utf-8', errors='ignore') + suffix

def extract_changelog_for_version(version=None, filepath="CHANGELOG.md"):
    full_path = os.path.join(BASE_DIR, filepath)
    if not os.path.exists(full_path):
        return None
    content = open(full_path, "r", encoding="utf-8").read()
    if version:
        clean_version = version.lstrip('v').strip()
        pattern = r'## \[' + re.escape(clean_version) + r'\].*?\n(.*?)(?=\n## \[|\Z)'
        match = re.search(pattern, content, re.DOTALL)
        if match:
            body = match.group(1).strip()
            lines = [re.sub(r'(\*\*|\*|__|_)', '', l.strip()) for l in body.split('\n') if l.strip()]
            return '\n'.join([l if l.startswith('- ') else f"- {l}" for l in lines])

    fallback_pattern = r'## \[(.*?)\](?: - .*?)?\n(.*?)(?=\n## \[|\Z)'
    fallback_match = re.search(fallback_pattern, content, re.DOTALL)
    if fallback_match:
        body = fallback_match.group(2).strip()
        lines = [re.sub(r'(\*\*|\*|__|_)', '', l.strip()) for l in body.split('\n') if l.strip()]
        return '\n'.join([l if l.startswith('- ') else f"- {l}" for l in lines])

    return None

def translate_block(text, target_lang, max_retries=2):
    """Translate an entire text block in one single request to prevent 429 rate-limiting."""
    # 1. Primary: deep-translator
    try:
        from deep_translator import GoogleTranslator
        translated = GoogleTranslator(source='en', target=target_lang).translate(text)
        if translated and translated.strip():
            return translated.strip()
    except Exception as e:
        print(f"  [deep-translator error for {target_lang}]: {e}")

    # 2. Fallback: urllib gtx endpoint
    import urllib.request
    import urllib.parse
    import json
    for attempt in range(max_retries):
        try:
            url = f"https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl={urllib.parse.quote(target_lang)}&dt=t&q={urllib.parse.quote(text)}"
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=6) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                translated = ''.join([part[0] for part in data[0] if part and part[0]]).strip()
                if translated:
                    return translated
        except Exception:
            time.sleep(0.5 + attempt * 0.5)

    # 3. Last resort fallback
    return text

def main():
    target_version = sys.argv[1] if len(sys.argv) > 1 else None

    # 1. Direct native German notes from docs/CHANGELOG.md (never machine translated)
    de_notes = extract_changelog_for_version(target_version, "docs/CHANGELOG.md")
    # 2. English notes from docs/CHANGELOG.en.md or root CHANGELOG.md
    en_notes = extract_changelog_for_version(target_version, "docs/CHANGELOG.en.md") or extract_changelog_for_version(target_version, "CHANGELOG.md")

    if not en_notes and not de_notes:
        en_notes = "- Bug fixes and performance improvements."
        de_notes = "- Fehlerbehebungen und Leistungsverbesserungen."
    elif not en_notes:
        en_notes = de_notes
    elif not de_notes:
        de_notes = en_notes

    print(f"Target Version: {target_version or 'Latest'}")
    print(f"German Notes ({len(de_notes.encode('utf-8'))} bytes):\n{de_notes}\n")
    print(f"English Notes ({len(en_notes.encode('utf-8'))} bytes):\n{en_notes}\n")

    os.makedirs(WHATSNEW_DIR, exist_ok=True)
    cache = {
        'de': de_notes,
        'en': en_notes,
    }

    for locale, lang in TARGET_LOCALES.items():
        filepath = os.path.join(WHATSNEW_DIR, f"whatsnew-{locale}")

        if locale == 'de-DE':
            content = de_notes
        elif lang == 'en':
            content = en_notes
        elif lang in cache:
            content = cache[lang]
        else:
            resolved_lang = LANG_CODE_MAP.get(lang, lang)
            print(f"Translating for {locale} ({resolved_lang})...")
            content = translate_block(en_notes, resolved_lang)
            cache[lang] = content
            time.sleep(0.1)

        # Truncate to Google Play hard limit (500 bytes)
        content = truncate_to_bytes(content, max_bytes=500)

        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content.strip() + "\n")

        byte_len = len(content.encode('utf-8'))
        print(f"✓ whatsnew-{locale} ({byte_len} bytes)")

    # Synchronize iOS TestFlight WhatToTest files if directory exists
    ios_testflight_dir = os.path.join(BASE_DIR, "iosApp", "SongFlip", "TestFlight")
    if os.path.isdir(ios_testflight_dir):
        en_ios_file = os.path.join(ios_testflight_dir, "WhatToTest.en-US.txt")
        de_ios_file = os.path.join(ios_testflight_dir, "WhatToTest.de-DE.txt")
        with open(en_ios_file, "w", encoding="utf-8") as f:
            f.write(truncate_to_bytes(en_notes, max_bytes=500).strip() + "\n")
        print("✓ Synced iosApp/SongFlip/TestFlight/WhatToTest.en-US.txt")
        with open(de_ios_file, "w", encoding="utf-8") as f:
            f.write(truncate_to_bytes(de_notes, max_bytes=500).strip() + "\n")
        print("✓ Synced iosApp/SongFlip/TestFlight/WhatToTest.de-DE.txt")

if __name__ == "__main__":
    main()
