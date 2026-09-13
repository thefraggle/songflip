#!/usr/bin/env python3
import os
import re
import sys
import time

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WHATSNEW_DIR = os.path.join(BASE_DIR, "distribution", "whatsnew")

TARGET_LOCALES = {
    'en-US': 'en',
    'en-GB': 'en',
    'de-DE': 'de',
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

def truncate_to_limits(text, max_chars=480, suffix="..."):
    text = text.strip()
    if len(text) <= max_chars:
        return text

    lines = text.split('\n')
    result = []
    for line in lines:
        if not line.strip():
            continue
        test_text = '\n'.join(result + [line]).strip()
        if len(test_text) > max_chars:
            break
        result.append(line)
    if result:
        return '\n'.join(result).strip()

    return text[:max_chars - len(suffix)] + suffix

def extract_changelog_for_version(version=None):
    changelog_path = os.path.join(BASE_DIR, "CHANGELOG.md")
    content = open(changelog_path, "r", encoding="utf-8").read()
    if version:
        clean_version = version.lstrip('v').strip()
        pattern = r'## \[' + re.escape(clean_version) + r'\].*?\n(.*?)(?=\n## \[|\Z)'
        match = re.search(pattern, content, re.DOTALL)
        if match:
            body = match.group(1).strip()
            lines = [re.sub(r'(\*\*|\*|__|_)', '', l.strip()) for l in body.split('\n') if l.strip()]
            return '\n'.join([l if l.startswith('- ') else f"- {l}" for l in lines])

    # Dynamic fallback to the latest version entry in CHANGELOG.md
    fallback_pattern = r'## \[(.*?)\](?: - .*?)?\n(.*?)(?=\n## \[|\Z)'
    fallback_match = re.search(fallback_pattern, content, re.DOTALL)
    if fallback_match:
        body = fallback_match.group(2).strip()
        lines = [re.sub(r'(\*\*|\*|__|_)', '', l.strip()) for l in body.split('\n') if l.strip()]
        return '\n'.join([l if l.startswith('- ') else f"- {l}" for l in lines])

    return "- Bug fixes and performance improvements."

def translate_text(text, target_lang, max_retries=3):
    if target_lang == 'en':
        return text
    import urllib.request
    import urllib.parse
    import json
    url = f"https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl={urllib.parse.quote(target_lang)}&dt=t&q={urllib.parse.quote(text)}"
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    for attempt in range(max_retries):
        try:
            with urllib.request.urlopen(req, timeout=8) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                translated = ''.join([part[0] for part in data[0] if part and part[0]]).strip()
                if translated:
                    return translated
        except Exception as e:
            if attempt == max_retries - 1:
                try:
                    from deep_translator import GoogleTranslator
                    return GoogleTranslator(source='en', target=target_lang).translate(text)
                except Exception:
                    raise e
            time.sleep(1.0 + attempt * 1.5)
    return text

def main():
    target_version = sys.argv[1] if len(sys.argv) > 1 else None
    en_notes = extract_changelog_for_version(target_version)
    print(f"Notes:\n{en_notes}\n")

    os.makedirs(WHATSNEW_DIR, exist_ok=True)
    cache = {}

    for locale, lang in TARGET_LOCALES.items():
        filepath = os.path.join(WHATSNEW_DIR, f"whatsnew-{locale}")
        if lang in cache:
            content = cache[lang]
        elif lang == 'en':
            content = en_notes
            cache['en'] = content
        else:
            try:
                lines = []
                for line in en_notes.split('\n'):
                    if line.startswith('- '):
                        t = translate_text(line[2:].strip(), lang)
                        lines.append(f"- {t}")
                    elif line.strip():
                        t = translate_text(line.strip(), lang)
                        lines.append(t)
                content = '\n'.join(lines)
                cache[lang] = content
                time.sleep(0.15)
            except Exception as e:
                print(f"Failed {locale} ({lang}): {e}")
                content = en_notes

        content = truncate_to_limits(content)
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content.strip() + "\n")
        print(f"✓ whatsnew-{locale} ({len(content.encode('utf-8'))} bytes)")

    # Synchronize iOS TestFlight WhatToTest files if directory exists
    ios_testflight_dir = os.path.join(BASE_DIR, "iosApp", "SongFlip", "TestFlight")
    if os.path.isdir(ios_testflight_dir):
        en_ios_file = os.path.join(ios_testflight_dir, "WhatToTest.en-US.txt")
        de_ios_file = os.path.join(ios_testflight_dir, "WhatToTest.de-DE.txt")
        if 'en' in cache:
            with open(en_ios_file, "w", encoding="utf-8") as f:
                f.write(cache['en'].strip() + "\n")
            print("✓ Synced iosApp/SongFlip/TestFlight/WhatToTest.en-US.txt")
        if 'de' in cache:
            with open(de_ios_file, "w", encoding="utf-8") as f:
                f.write(cache['de'].strip() + "\n")
            print("✓ Synced iosApp/SongFlip/TestFlight/WhatToTest.de-DE.txt")

if __name__ == "__main__":
    main()
