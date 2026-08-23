#!/usr/bin/env python3
import os
import re
import sys

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WHATSNEW_DIR = os.path.join(BASE_DIR, "distribution", "whatsnew")

LOCALES = [
    "en-US", "en-GB", "de-DE", "da-DK", "no-NO", "sv-SE", "nl-NL", "fr-FR",
    "es-ES", "es-US", "it-IT", "pt-PT", "pt-BR", "pl-PL", "ru-RU", "tr-TR",
    "uk", "ja-JP", "ko-KR", "zh-CN", "id", "vi", "bn-BD", "hi-IN", "mr-IN"
]

def extract_latest_notes():
    changelog_path = os.path.join(BASE_DIR, "CHANGELOG.md")
    if not os.path.exists(changelog_path):
        return "Performance improvements and bug fixes.", "Fehlerbehebungen und Performance-Verbesserungen."

    content = open(changelog_path, "r", encoding="utf-8").read()
    match = re.search(r"## \[.*?\][^\n]*\n(.*?)(?=\n## \[|\Z)", content, re.DOTALL)
    if not match:
        return "Performance improvements and bug fixes.", "Fehlerbehebungen und Performance-Verbesserungen."

    raw_body = match.group(1).strip()
    
    de_match = re.search(r"### (?:Deutsch|DE)\n(.*?)(?=\n### |\Z)", raw_body, re.DOTALL)
    en_match = re.search(r"### (?:English|EN)\n(.*?)(?=\n### |\Z)", raw_body, re.DOTALL)

    if de_match and en_match:
        en_notes = en_match.group(1).strip()
        de_notes = de_match.group(1).strip()
    elif de_match:
        de_notes = de_match.group(1).strip()
        en_notes = raw_body
    else:
        en_notes = raw_body
        de_notes = raw_body

    return en_notes[:480], de_notes[:480]

def main():
    os.makedirs(WHATSNEW_DIR, exist_ok=True)
    en_notes, de_notes = extract_latest_notes()

    for locale in LOCALES:
        filepath = os.path.join(WHATSNEW_DIR, f"whatsnew-{locale}")
        text = de_notes if locale.startswith("de") else en_notes
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(text.strip() + "\n")
    print(f"Generated whatsnew files in {WHATSNEW_DIR} for {len(LOCALES)} locales.")

if __name__ == "__main__":
    main()
