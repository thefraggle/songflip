#!/usr/bin/env python3
"""
Validates both iOS LocalizationManager.swift copies:
- both copies are byte-identical (main app + share extension compile their own copy)
- no duplicate keys per language block (duplicate dictionary literal keys trap at runtime)
- every language block has the same key set as "en" (differences are reported)
- format placeholders per key match "en"
Exit code 1 on errors, 0 otherwise (key-set differences are reported as warnings).
"""

import sys

from sync_ios_localization import IOS_LOCALIZATION_PATHS, check_blocks, parse_swift


def main():
    contents = [open(p, encoding="utf-8").read() for p in IOS_LOCALIZATION_PATHS]
    errors = []
    if len(set(contents)) != 1:
        errors.append("LocalizationManager.swift copies differ – run scripts/sync_ios_localization.py")
    _, blocks, _ = parse_swift(contents[0])
    block_errors, warnings = check_blocks(blocks)
    errors.extend(block_errors)
    for w in warnings:
        print(f"WARNING: {w}")
    for e in errors:
        print(f"ERROR: {e}")
    sizes = {lang: len(entries) for lang, entries in blocks.items()}
    print(f"{len(blocks)} language blocks, key counts: {sorted(set(sizes.values()))}")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
