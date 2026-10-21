#!/usr/bin/env python3
"""
Syncs translations from Android strings.xml into both iOS LocalizationManager.swift copies
(main app + share extension) for all iOS supported languages.

Default mode: adds keys that exist in Android (values/strings.xml) but are missing in an iOS
language block. Existing iOS values are never touched.

--fix-english-fallbacks: additionally replaces iOS values that are still identical to the iOS
English value when Android has a real translation for that key. Only applied when the iOS
English text equals the Android English text, so iOS-specific wording is never overwritten
with an Android translation that means something else.

--check: only validates (no write). See check_ios_localization.py.

The script never falls back to English silently: if an Android source folder or a translation
is missing, a WARNING is printed and the key is left out (iOS falls back to "en" at runtime).
"""

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter, OrderedDict

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID_RES = os.path.join(BASE_DIR, "app", "src", "main", "res")
IOS_LOCALIZATION_PATHS = [
    os.path.join(BASE_DIR, "iosApp", "SongFlip", "SongFlip", "LocalizationManager.swift"),
    os.path.join(BASE_DIR, "iosApp", "SongFlip", "SongFlipShareExtension", "LocalizationManager.swift"),
]

# iOS language code -> Android resource folder.
# zh: Android only ships values-zh-rCN (Simplified); a non-existent values-zh made the old
#     script fall back to English for 200+ keys.
# in: iOS uses the legacy "in" code (see LocalizationManager.currentLanguage); values-in and
#     values-id are kept identical on Android.
# nb: iOS maps no/nn/nb -> "nb"; values-nb and values-no are kept identical on Android.
LANG_MAP = OrderedDict([
    ("en", "values"),
    ("de", "values-de"),
    ("da", "values-da"),
    ("nb", "values-nb"),
    ("sv", "values-sv"),
    ("nl", "values-nl"),
    ("fr", "values-fr"),
    ("es", "values-es"),
    ("it", "values-it"),
    ("pt", "values-pt"),
    ("pl", "values-pl"),
    ("ru", "values-ru"),
    ("tr", "values-tr"),
    ("uk", "values-uk"),
    ("ja", "values-ja"),
    ("ko", "values-ko"),
    ("zh", "values-zh-rCN"),
    ("in", "values-in"),
    ("vi", "values-vi"),
    ("bn", "values-bn"),
    ("hi", "values-hi"),
    ("mr", "values-mr"),
    ("cs", "values-cs"),
    ("el", "values-el"),
    ("fi", "values-fi"),
    ("hu", "values-hu"),
    ("ro", "values-ro"),
    ("th", "values-th"),
])

# iOS-only keys without Android equivalent that must exist in every language block.
EXTRA_KEYS = {
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
        "mr": "विस्तारित इतिहास आणि प्लेलिस्टसाठी Pro वर अपग्रेड करा",
    }
}

TRANSLATIONS_HEADER = "translations: [String: [String: String]] = ["
BLOCK_RE = re.compile(r'^\s{8}"([A-Za-z-]+)": \[\s*$')
ENTRY_RE = re.compile(r'^\s{12}"([^"\\]+)": "((?:[^"\\]|\\.)*)",?\s*$')
BLOCK_END_RE = re.compile(r'^\s{8}\],?\s*$')
# printf-style specifiers as used on iOS (%@, %1$@, %d, %1$d, %ld, ...); "%%" is a literal.
PLACEHOLDER_RE = re.compile(r'%(?:%|(?:\d+\$)?l{0,2}[@dsfu])')


def warn(msg):
    print(f"WARNING: {msg}", file=sys.stderr)


def placeholders(swift_value):
    """Sorted placeholder list of a Swift-escaped value, ignoring literal %%."""
    return sorted(p for p in PLACEHOLDER_RE.findall(swift_value) if p != "%%")


def unescape_android(text):
    """Resolves Android resource escapes (\\', \\", \\n, \\uXXXX, ...) to plain text."""
    text = text.strip()
    if len(text) >= 2 and text.startswith('"') and text.endswith('"'):
        text = text[1:-1]
    out = []
    i = 0
    while i < len(text):
        c = text[i]
        if c == "\\" and i + 1 < len(text):
            n = text[i + 1]
            if n == "u" and re.fullmatch(r"[0-9a-fA-F]{4}", text[i + 2:i + 6] or ""):
                out.append(chr(int(text[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append({"n": "\n", "t": "\t"}.get(n, n))
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def android_to_swift(text):
    """Android string -> Swift string literal content with iOS format specifiers."""
    if not text:
        return ""
    text = unescape_android(text)
    text = re.sub(r"%(\d+)\$s", r"%\1$@", text)
    text = re.sub(r"%s", r"%@", text)
    # "%%" is only a literal-percent escape for strings passed through String(format:).
    # Plain strings (no other specifier) are displayed verbatim on iOS, so collapse it there.
    if not [p for p in PLACEHOLDER_RE.findall(text) if p != "%%"]:
        text = text.replace("%%", "%")
    return plain_to_swift(text)


def plain_to_swift(text):
    return (text.replace("\\", "\\\\").replace('"', '\\"')
            .replace("\n", "\\n").replace("\t", "\\t"))


def load_android_strings(values_dir):
    """Returns {key: swift_value} or None if the folder/file does not exist."""
    strings_file = os.path.join(ANDROID_RES, values_dir, "strings.xml")
    if not os.path.exists(strings_file):
        return None
    res = {}
    root = ET.parse(strings_file).getroot()
    for s in root.findall("string"):
        name = s.get("name")
        if name and s.get("translatable") != "false":
            res[name] = android_to_swift("".join(s.itertext()))
    for p in root.findall("plurals"):
        name = p.get("name")
        other = p.find("item[@quantity='other']")
        if name and other is not None:
            res[name] = android_to_swift("".join(other.itertext()))
    return res


def parse_swift(content):
    """
    Parses the translations dictionary literal.
    Returns (lines_before, OrderedDict[lang -> list[(key, value)]], lines_after).
    Raises ValueError on any unexpected line so we never rewrite a file we did not understand.
    """
    lines = content.split("\n")
    try:
        start = next(i for i, l in enumerate(lines) if TRANSLATIONS_HEADER in l)
        end = next(i for i in range(start + 1, len(lines)) if lines[i].rstrip() == "    ]")
    except StopIteration:
        raise ValueError("translations dictionary not found")
    blocks = OrderedDict()
    current = None
    for idx in range(start + 1, end):
        line = lines[idx]
        if not line.strip():
            continue
        m = BLOCK_RE.match(line)
        if m:
            current = m.group(1)
            if current in blocks:
                raise ValueError(f"duplicate language block '{current}' (line {idx + 1})")
            blocks[current] = []
            continue
        m = ENTRY_RE.match(line)
        if m and current is not None:
            blocks[current].append((m.group(1), m.group(2)))
            continue
        if BLOCK_END_RE.match(line):
            current = None
            continue
        raise ValueError(f"unexpected line {idx + 1}: {line[:100]!r}")
    return lines[:start + 1], blocks, lines[end:]


def render_swift(before, blocks, after):
    out = list(before)
    for lang, entries in blocks.items():
        out.append(f'        "{lang}": [')
        for i, (k, v) in enumerate(entries):
            comma = "," if i < len(entries) - 1 else ""
            out.append(f'            "{k}": "{v}"{comma}')
        out.append("        ],")
    out.extend(after)
    return "\n".join(out)


def check_blocks(blocks):
    """Returns (errors, warnings) lists. Errors = would crash or break formatting at runtime."""
    errors, warnings = [], []
    if "en" not in blocks:
        return ["no 'en' block"], warnings
    en = OrderedDict(blocks["en"])
    for lang, entries in blocks.items():
        dups = [k for k, c in Counter(k for k, _ in entries).items() if c > 1]
        if dups:
            # Duplicate keys in a Swift dictionary literal trap at runtime.
            errors.append(f"[{lang}] duplicate keys: {dups}")
        d = dict(entries)
        missing = sorted(set(en) - set(d))
        extra = sorted(set(d) - set(en))
        if missing:
            warnings.append(f"[{lang}] missing vs en ({len(missing)}): {missing}")
        if extra:
            warnings.append(f"[{lang}] extra vs en ({len(extra)}): {extra}")
        for k, v in d.items():
            if k in en and placeholders(v) != placeholders(en[k]):
                errors.append(f"[{lang}] placeholder mismatch '{k}': {placeholders(v)} vs en {placeholders(en[k])}")
    return errors, warnings


def sync(blocks, fix_english_fallbacks):
    android_en = load_android_strings("values")
    if android_en is None:
        raise SystemExit("ERROR: Android values/strings.xml not found")
    ios_en = dict(blocks.get("en", []))
    problems = 0

    for lang, val_dir in LANG_MAP.items():
        if lang not in blocks:
            warn(f"language block '{lang}' not found in LocalizationManager.swift – skipped")
            problems += 1
            continue
        android = load_android_strings(val_dir)
        if android is None:
            warn(f"Android folder '{val_dir}' for '{lang}' does not exist – no keys added/fixed")
            problems += 1
            continue

        entries = blocks[lang]
        existing = dict(entries)
        index = {k: i for i, (k, _) in enumerate(entries)}

        added = []
        for k in sorted(android_en.keys()):
            if k in existing:
                continue
            if k in android:
                added.append((k, android[k]))
            else:
                warn(f"[{lang}] '{k}' has no translation in {val_dir} – not added")
                problems += 1
        for ek, trans in EXTRA_KEYS.items():
            if ek in existing or any(k == ek for k, _ in added):
                continue
            if lang in trans:
                added.append((ek, plain_to_swift(trans[lang])))
            else:
                warn(f"[{lang}] iOS-only key '{ek}' has no translation – not added")
                problems += 1
        entries.extend(added)
        if added:
            print(f"[{lang}] added {len(added)} keys: {[k for k, _ in added]}")

        if fix_english_fallbacks and lang != "en":
            fixed, unresolved = [], []
            for k, v in list(entries):
                if k not in ios_en or v != ios_en[k]:
                    continue
                tr = android.get(k)
                if tr is None or tr == android_en.get(k):
                    continue  # no real translation (or intentionally identical, e.g. brand names)
                if android_en.get(k) != ios_en[k]:
                    unresolved.append(k)  # iOS wording differs from Android – needs a human
                    continue
                if placeholders(tr) != placeholders(ios_en[k]):
                    warn(f"[{lang}] '{k}' Android translation has different placeholders – skipped")
                    problems += 1
                    continue
                entries[index[k]] = (k, tr)
                fixed.append(k)
            if fixed:
                print(f"[{lang}] replaced {len(fixed)} English fallbacks")
            if unresolved:
                warn(f"[{lang}] {len(unresolved)} values still English (iOS text differs from Android en): {unresolved}")

    normalize_single_placeholders(blocks)
    return problems


def normalize_single_placeholders(blocks):
    """
    "%1$@" and "%@" are equivalent when a string has exactly one argument, but mixed styles per
    key make the placeholder check noisy and hide real mismatches. Align such values to "en".
    """
    en = dict(blocks.get("en", []))
    for lang, entries in blocks.items():
        if lang == "en":
            continue
        for i, (k, v) in enumerate(entries):
            if k not in en:
                continue
            ph_v, ph_en = placeholders(v), placeholders(en[k])
            if ph_v == ph_en or len(ph_v) != 1 or len(ph_en) != 1:
                continue
            if re.sub(r"\d+\$", "", ph_v[0]) == re.sub(r"\d+\$", "", ph_en[0]):
                entries[i] = (k, v.replace(ph_v[0], ph_en[0]))
                print(f"[{lang}] normalized placeholder in '{k}': {ph_v[0]} -> {ph_en[0]}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--fix-english-fallbacks", action="store_true",
                    help="replace iOS values identical to English when Android has a translation")
    ap.add_argument("--check", action="store_true", help="validate only, do not write")
    args = ap.parse_args()

    primary = IOS_LOCALIZATION_PATHS[0]
    with open(primary, "r", encoding="utf-8") as f:
        content = f.read()
    before, blocks, after = parse_swift(content)

    if args.check:
        errors, warnings = check_blocks(blocks)
        for w in warnings:
            warn(w)
        for e in errors:
            print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1 if errors else 0)

    problems = sync(blocks, args.fix_english_fallbacks)
    errors, warnings = check_blocks(blocks)
    for w in warnings:
        warn(w)
    if errors:
        for e in errors:
            print(f"ERROR: {e}", file=sys.stderr)
        raise SystemExit("Aborting – nothing written.")

    new_content = render_swift(before, blocks, after)
    # Both targets compile their own copy; they must stay byte-identical. The share extension
    # copy is currently a symlink to the app copy – write each real file only once so the
    # symlink is preserved, but still handle a future switch to a real file.
    written = set()
    for path in IOS_LOCALIZATION_PATHS:
        real = os.path.realpath(path)
        if real in written:
            print(f"Skipped {os.path.relpath(path, BASE_DIR)} (symlink to an already written file)")
            continue
        with open(real, "w", encoding="utf-8") as f:
            f.write(new_content)
        written.add(real)
        print(f"Wrote {os.path.relpath(path, BASE_DIR)}")
    if problems:
        print(f"Done with {problems} warning(s) – see above.")
    else:
        print("Successfully synchronized iOS localization!")


if __name__ == "__main__":
    main()
