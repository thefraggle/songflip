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

def truncate_to_bytes(text, max_limit=450, suffix="..."):
    text = text.strip()
    def is_safe(s):
        return len(s) <= max_limit and len(s.encode('utf-8')) <= max_limit
    if is_safe(text):
        return text

    lines = text.split('\n')
    result = []
    for line in lines:
        if not line.strip():
            continue
        test_text = '\n'.join(result + [line]).strip()
        if not is_safe(test_text):
            break
        result.append(line)
    if result:
        return '\n'.join(result).strip()

    suffix_bytes = suffix.encode('utf-8')
    raw_bytes = text.encode('utf-8')[:max_limit - len(suffix_bytes)]
    safe_str = raw_bytes.decode('utf-8', errors='ignore') + suffix
    return safe_str[:max_limit]

def extract_changelog_for_version(version="1.2.15"):
    changelog_path = os.path.join(BASE_DIR, "CHANGELOG.md")
    content = open(changelog_path, "r", encoding="utf-8").read()
    pattern = r'## \[' + re.escape(version) + r'\].*?\n(.*?)(?=\n## \[|\Z)'
    match = re.search(pattern, content, re.DOTALL)
    if match:
        body = match.group(1).strip()
        lines = [re.sub(r'(\*\*|\*|__|_)', '', l.strip()) for l in body.split('\n') if l.strip()]
        return '\n'.join([l if l.startswith('- ') else f"- {l}" for l in lines])
    return "- Conversion Milestones: Celebrate conversion milestones with upgrade perks.\n- History Capacity: Easily track your saved song capacity.\n- Improved Clipboard Detection: Faster detection of copied links."

def translate_with_retry(translator, text, max_retries=3):
    for attempt in range(max_retries):
        try:
            return translator.translate(text)
        except Exception as e:
            if attempt == max_retries - 1:
                raise e
            time.sleep(1.0 + attempt * 1.5)

def main():
    target_version = sys.argv[1] if len(sys.argv) > 1 else "1.2.15"
    en_notes = extract_changelog_for_version(target_version)
    print(f"Notes:\n{en_notes}\n")

    from deep_translator import GoogleTranslator

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
                translator = GoogleTranslator(source='en', target=lang)
                lines = []
                for line in en_notes.split('\n'):
                    if line.startswith('- '):
                        t = translate_with_retry(translator, line[2:].strip())
                        lines.append(f"- {t}")
                    elif line.strip():
                        t = translate_with_retry(translator, line.strip())
                        lines.append(t)
                content = '\n'.join(lines)
                cache[lang] = content
                time.sleep(0.3)
            except Exception as e:
                print(f"Failed {locale} ({lang}): {e}")
                content = en_notes

        content = truncate_to_bytes(content)
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content.strip() + "\n")
        print(f"✓ whatsnew-{locale} ({len(content.encode('utf-8'))} bytes)")

if __name__ == "__main__":
    main()
