#!/usr/bin/env python3
"""
Automated Android Store Screenshot Capture for SongFlip.
Captures live screens from the Android Emulator / Device, formats them,
and triggers open-assets to render marketing store screenshots.
"""

import os
import subprocess
import time
import sys
import xml.etree.ElementTree as ET

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOCKUPS_DIR = os.path.join(BASE_DIR, "distribution", "open-assets", "public", "mockups")
OPEN_ASSETS_DIR = os.path.join(BASE_DIR, "distribution", "open-assets")

def run_cmd(cmd, check=True):
    print(f"[$] {cmd}")
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if check and res.returncode != 0:
        print(f"Error running command: {res.stderr}")
    return res

def set_demo_mode(enable=True):
    if enable:
        print("Enabling Android SystemUI Clean Demo Mode (12:00, 100% Battery)...")
        run_cmd("adb shell settings put global sysui_demo_allowed 1")
        run_cmd("adb shell am broadcast -a com.android.systemui.demo -e command enter")
        run_cmd("adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200")
        run_cmd("adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false")
        run_cmd("adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4")
        run_cmd("adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false")
    else:
        print("Disabling Demo Mode...")
        run_cmd("adb shell am broadcast -a com.android.systemui.demo -e command exit")

def capture_screen(output_name):
    target_path = os.path.join(MOCKUPS_DIR, output_name)
    os.makedirs(MOCKUPS_DIR, exist_ok=True)
    tmp_device_path = f"/sdcard/{output_name}"
    run_cmd(f"adb shell screencap -p {tmp_device_path}")
    run_cmd(f"adb pull {tmp_device_path} {target_path}")
    run_cmd(f"adb shell rm {tmp_device_path}")
    print(f"✓ Captured {output_name} -> {target_path}")

def find_node_bounds(content_desc_target=None, text_target=None):
    run_cmd("adb shell uiautomator dump /sdcard/ui_dump.xml")
    run_cmd("adb pull /sdcard/ui_dump.xml /tmp/ui_dump.xml")
    try:
        tree = ET.parse("/tmp/ui_dump.xml")
        root = tree.getroot()
        for node in root.iter("node"):
            desc = node.attrib.get("content-desc", "")
            text = node.attrib.get("text", "")
            if (content_desc_target and content_desc_target in desc) or (text_target and text_target in text):
                bounds = node.attrib.get("bounds", "")
                # Format: [left,top][right,bottom]
                import re
                m = re.findall(r"\d+", bounds)
                if len(m) == 4:
                    x = (int(m[0]) + int(m[2])) // 2
                    y = (int(m[1]) + int(m[3])) // 2
                    return x, y
    except Exception as e:
        print(f"Error parsing UI dump: {e}")
    return None

def click_element(content_desc_target=None, text_target=None, fallback_x=None, fallback_y=None):
    coords = find_node_bounds(content_desc_target, text_target)
    if coords:
        x, y = coords
        print(f"Clicking '{content_desc_target or text_target}' at ({x}, {y})")
        run_cmd(f"adb shell input tap {x} {y}")
    elif fallback_x and fallback_y:
        print(f"Fallback click at ({fallback_x}, {fallback_y})")
        run_cmd(f"adb shell input tap {fallback_x} {fallback_y}")
    else:
        print(f"Could not find element: {content_desc_target or text_target}")

def main():
    print("=== SongFlip Automated Android Screenshot Capture ===")
    
    # 1. Clean Demo Mode
    set_demo_mode(True)
    time.sleep(1)

    # 2. Capture Screen 1: Main Activity / Target Player
    print("\n[1/5] Capturing Screen 1 (Main Activity)...")
    run_cmd("adb shell am force-stop de.goork.songflip")
    run_cmd("adb shell am start -n de.goork.songflip/.ui.MainActivity")
    time.sleep(2)
    capture_screen("screen_1.png")

    # 3. Capture Screen 5: Settings Screen
    print("\n[2/5] Capturing Screen 5 (Settings)...")
    click_element(content_desc_target="Settings", fallback_x=980, fallback_y=160)
    time.sleep(2)
    capture_screen("screen_5.png")

    # Back to Main
    run_cmd("adb shell input keyevent 4") # KEYCODE_BACK
    time.sleep(1)

    # 4. Trigger Playlist Convert Sheet (Screen 4)
    print("\n[3/5] Capturing Screen 4 (Playlist Converter)...")
    # Click Playlist Convert button if visible or open intent
    click_element(text_target="Playlists", content_desc_target="Playlists", fallback_x=540, fallback_y=1900)
    time.sleep(2)
    capture_screen("screen_4.png")

    # 5. Disable Demo Mode
    set_demo_mode(False)

    # 6. Render with Open Assets
    print("\n=== Rendering Store Screenshots with Open Assets ===")
    run_cmd(f"cd {OPEN_ASSETS_DIR} && npx @open-assets/open-assets render --force --locale de-DE --size playstore")
    print("\n✓ Done! Automated screenshot pipeline finished successfully.")

if __name__ == "__main__":
    main()
