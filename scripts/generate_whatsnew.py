#!/usr/bin/env python3
import os
import re
import sys
import time

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WHATSNEW_DIR = os.path.join(BASE_DIR, "distribution", "whatsnew")

TARGET_LOCALES = {
    'en-US': 'en-GB',
    'en-GB': 'en-GB',
    'de-DE': 'de-DE',
    'fr-FR': 'fr-FR',
    'fr-CA': 'fr-CA',
    'it-IT': 'it-IT',
    'es-ES': 'es-ES',
    'es-US': 'es-ES',
    'es-419': 'es-ES',
    'pt-PT': 'pt-PT',
    'pt-BR': 'pt-BR',
    'pl-PL': 'pl-PL',
    'nl-NL': 'nl-NL',
    'sv-SE': 'sv-SE',
    'da-DK': 'da-DK',
    'nb-NO': 'nb-NO',
    'tr-TR': 'tr-TR',
    'ru-RU': 'ru-RU',
    'uk': 'uk-UA',
    'ja-JP': 'ja-JP',
    'ko-KR': 'ko-KR',
    'zh-CN': 'zh-CN',
    'zh-TW': 'zh-TW',
    'id': 'id-ID',
    'vi': 'vi-VN',
    'hi-IN': 'hi-IN',
    'bn-BD': 'bn-IN',
    'mr-IN': 'mr-IN',
    'ar': 'ar-SA',
    'cs-CZ': 'cs-CZ',
    'el-GR': 'el-GR',
    'fi-FI': 'fi-FI',
    'hu-HU': 'hu-HU',
    'ro': 'ro-RO',
}

def truncate_to_bytes(text, max_bytes=500, suffix="..."):
    """Google Play counts UTF-8 bytes, not characters. Max limit is 500 bytes."""
    encoded = text.encode('utf-8')
    if len(encoded) <= max_bytes:
        return text
    suffix_bytes = suffix.encode('utf-8')
    truncated = encoded[:max_bytes - len(suffix_bytes)]
    return truncated.decode('utf-8', errors='ignore') + suffix

def extract_changelog_for_version(version=None):
    changelog_path = os.path.join(BASE_DIR, "CHANGELOG.md")
    if not os.path.exists(changelog_path):
        return "Performance improvements and bug fixes."

    content = open(changelog_path, "r", encoding="utf-8").read()

    if version:
        clean_v = version.lstrip('v').strip()
        pattern = r'## \[' + re.escape(clean_v) + r'\].*?\n(.*?)(?=\n## \[|\Z)'
        match = re.search(pattern, content, re.DOTALL)
        if match:
            raw_body = match.group(1).strip()
            return clean_markdown_notes(raw_body)

    # Fallback to the top release
    match = re.search(r"## \[.*?\][^\n]*\n(.*?)(?=\n## \[|\Z)", content, re.DOTALL)
    if match:
        return clean_markdown_notes(match.group(1).strip())

    return "Performance improvements and bug fixes."

def clean_markdown_notes(body):
    lines = body.split('\n')
    cleaned = []
    for line in lines:
        line = line.strip()
        if not line or line.startswith('###'):
            continue
        line = re.sub(r'(\*\*|\*|__|_)', '', line)
        if line.startswith('- '):
            cleaned.append(line)
        else:
            cleaned.append(f"- {line}")
    return '\n'.join(cleaned)

def main():
    target_version = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("GITHUB_REF_NAME", "")
    print(f"Extracting changelog for version: '{target_version or 'latest'}'")

    en_notes = extract_changelog_for_version(target_version)
    print(f"Changelog content:\n{en_notes}\n")

    try:
        from deep_translator import MyMemoryTranslator
        translator_cls = MyMemoryTranslator
    except Exception as e:
        print(f"deep-translator not available: {e}")
        translator_cls = None

    import shutil
    if os.path.exists(WHATSNEW_DIR):
        shutil.rmtree(WHATSNEW_DIR)
    os.makedirs(WHATSNEW_DIR, exist_ok=True)

    for locale, target_lang in TARGET_LOCALES.items():
        filepath = os.path.join(WHATSNEW_DIR, f"whatsnew-{locale}")

        if locale.startswith('en'):
            content = en_notes
        elif translator_cls:
            try:
                translated_lines = []
                for line in en_notes.split('\n'):
                    if line.startswith('- '):
                        raw_text = line[2:].strip()
                        trans = translator_cls(source='en-GB', target=target_lang).translate(raw_text)
                        translated_lines.append(f"- {trans}")
                    elif line.strip():
                        trans = translator_cls(source='en-GB', target=target_lang).translate(line.strip())
                        translated_lines.append(trans)
                content = '\n'.join(translated_lines)
                time.sleep(0.05)
            except Exception as e:
                print(f"Translation failed for {locale} ({target_lang}): {e}")
                content = en_notes
        else:
            content = en_notes

        content = truncate_to_bytes(content)
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content.strip() + "\n")

        byte_len = len(content.encode('utf-8'))
        print(f"✓ whatsnew-{locale} ({byte_len} bytes)")

    print(f"\nSuccessfully generated {len(TARGET_LOCALES)} localized whatsnew files in {WHATSNEW_DIR}.")

if __name__ == "__main__":
    main()
