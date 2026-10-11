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
    'th-TH': 'th',
}

LANG_CODE_MAP = {
    'nb': 'no',
    'zh': 'zh-CN',
    'es': 'es',
}

def truncate_release_notes(text, max_limit=495):
    """Google Play restricts release notes to at most 500 characters and 500 UTF-8 bytes.
    Enforces a strict upper bound (default 495) on BOTH characters and bytes to prevent overflow.
    Keeps complete bullet lines when possible.
    """
    text = text.strip()
    if len(text) <= max_limit and len(text.encode('utf-8')) <= max_limit:
        return text

    lines = [l.strip() for l in text.split('\n') if l.strip()]
    result = []
    for line in lines:
        test_text = '\n'.join(result + [line]).strip()
        if len(test_text) > max_limit or len(test_text.encode('utf-8')) > max_limit:
            break
        result.append(line)

    if result:
        return '\n'.join(result).strip()

    suffix = "..."
    candidate = text[:max_limit - len(suffix)]
    while candidate and (len(candidate + suffix) > max_limit or len((candidate + suffix).encode('utf-8')) > max_limit):
        candidate = candidate[:-1]
    return (candidate.strip() + suffix).strip()

def truncate_to_bytes(text, max_bytes=495, suffix="..."):
    return truncate_release_notes(text, max_limit=max_bytes)

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
        return None

    fallback_pattern = r'## \[(.*?)\](?: - .*?)?\n(.*?)(?=\n## \[|\Z)'
    fallback_match = re.search(fallback_pattern, content, re.DOTALL)
    if fallback_match:
        body = fallback_match.group(2).strip()
        lines = [re.sub(r'(\*\*|\*|__|_)', '', l.strip()) for l in body.split('\n') if l.strip()]
        return '\n'.join([l if l.startswith('- ') else f"- {l}" for l in lines])

    return None

def is_valid_translation(text):
    if not text or not text.strip():
        return False
    lower = text.lower()
    if "query length limit" in lower or "mymemory warning" in lower or "server error" in lower or "too many requests" in lower:
        return False
    return True

def translate_single_line(text, target_lang, max_retries=2):
    # 1. Fallback: urllib gtx endpoint (fastest, high limit)
    import urllib.request
    import urllib.parse
    import json
    for attempt in range(max_retries):
        try:
            url = f"https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl={urllib.parse.quote(target_lang)}&dt=t&q={urllib.parse.quote(text)}"
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
            with urllib.request.urlopen(req, timeout=6) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                translated = ''.join([part[0] for part in data[0] if part and part[0]]).strip()
                if is_valid_translation(translated):
                    return translated
        except Exception:
            time.sleep(0.3 + attempt * 0.3)

    # 2. deep-translator
    try:
        from deep_translator import GoogleTranslator
        translated = GoogleTranslator(source='en', target=target_lang).translate(text)
        if is_valid_translation(translated):
            return translated.strip()
    except Exception:
        pass

    # 3. Fallback: MyMemory API (short segments)
    if len(text) < 400:
        try:
            url = f"https://api.mymemory.translated.net/get?q={urllib.parse.quote(text)}&langpair=en|{urllib.parse.quote(target_lang)}"
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=6) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                translated = data.get("responseData", {}).get("translatedText")
                if is_valid_translation(translated):
                    return translated.strip()
        except Exception:
            pass

    return text

def translate_block(text, target_lang, max_retries=2):
    lines = [l.strip() for l in text.split('\n') if l.strip()]
    translated_lines = []
    for line in lines:
        prefix = "- " if line.startswith("- ") else ""
        content = line[2:] if line.startswith("- ") else line
        trans = translate_single_line(content, target_lang, max_retries=max_retries)
        translated_lines.append(f"{prefix}{trans}")
    return '\n'.join(translated_lines)

def main():
    target_version = sys.argv[1] if len(sys.argv) > 1 else None

    # 1. English notes from root CHANGELOG.md
    en_notes = extract_changelog_for_version(target_version, "CHANGELOG.md")

    # 2. Native German notes from root CHANGELOG.de.md (if present), else translate from en_notes
    de_notes = extract_changelog_for_version(target_version, "CHANGELOG.de.md")

    if not en_notes and not de_notes:
        en_notes = "- Bug fixes and performance improvements."
        de_notes = "- Fehlerbehebungen und Leistungsverbesserungen."
    elif not en_notes and de_notes:
        en_notes = de_notes
    elif not de_notes and en_notes:
        de_notes = translate_block(en_notes, 'de')

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

        # Truncate to Google Play hard limit (strictly <= 495 chars and bytes)
        content = truncate_release_notes(content, max_limit=495)

        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content.strip())

        char_len = len(content.strip())
        byte_len = len(content.strip().encode('utf-8'))
        print(f"✓ whatsnew-{locale} ({char_len} chars, {byte_len} bytes)")

    # Strict validation gate for Google Play requirements
    for loc in TARGET_LOCALES:
        fpath = os.path.join(WHATSNEW_DIR, f"whatsnew-{loc}")
        if not os.path.exists(fpath):
            raise FileNotFoundError(f"Missing release notes file for locale {loc}: {fpath}")
        with open(fpath, "r", encoding="utf-8") as f:
            val = f.read()
        c_len = len(val)
        b_len = len(val.encode("utf-8"))
        if c_len > 500 or b_len > 500:
            raise ValueError(f"CRITICAL: {fpath} exceeds Google Play limit (chars={c_len}, bytes={b_len}, max=500)")
    print("✓ All whatsnew files strictly verified (<= 500 chars & bytes)")

    # Synchronize iOS TestFlight WhatToTest files if directory exists
    ios_testflight_dir = os.path.join(BASE_DIR, "iosApp", "SongFlip", "TestFlight")
    if os.path.isdir(ios_testflight_dir):
        en_ios_file = os.path.join(ios_testflight_dir, "WhatToTest.en-US.txt")
        de_ios_file = os.path.join(ios_testflight_dir, "WhatToTest.de-DE.txt")
        with open(en_ios_file, "w", encoding="utf-8") as f:
            f.write(truncate_release_notes(en_notes, max_limit=495).strip())
        print("✓ Synced iosApp/SongFlip/TestFlight/WhatToTest.en-US.txt")
        with open(de_ios_file, "w", encoding="utf-8") as f:
            f.write(truncate_release_notes(de_notes, max_limit=495).strip())
        print("✓ Synced iosApp/SongFlip/TestFlight/WhatToTest.de-DE.txt")

if __name__ == "__main__":
    main()
