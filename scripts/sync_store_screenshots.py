#!/usr/bin/env python3
"""
Synchronize rendered Open Assets screenshots into distribution/screenshots/ for store deployment.
"""

import os
import shutil

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXPORTS_DIR = os.path.join(BASE_DIR, "distribution", "open-assets", "exports", "screenshots")
TARGET_DIR = os.path.join(BASE_DIR, "distribution", "screenshots")

def sync():
    if not os.path.exists(EXPORTS_DIR):
        print(f"Error: {EXPORTS_DIR} does not exist. Run open-assets render first.")
        return

    count = 0
    for locale in sorted(os.listdir(EXPORTS_DIR)):
        src_playstore = os.path.join(EXPORTS_DIR, locale, "playstore")
        if os.path.isdir(src_playstore):
            dst_locale = os.path.join(TARGET_DIR, locale)
            os.makedirs(dst_locale, exist_ok=True)
            for i in range(1, 6):
                src_file = os.path.join(src_playstore, f"screen_{i}.png")
                dst_file = os.path.join(dst_locale, f"screen_{i}.png")
                if os.path.exists(src_file):
                    shutil.copy2(src_file, dst_file)
                    count += 1

    print(f"✓ Synchronized {count} final store screenshots across {count // 5} locales.")

if __name__ == "__main__":
    sync()
