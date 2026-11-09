#!/usr/bin/env python3
"""
CLI helper to enable or disable the SongFlip promo banner in Firestore.
Usage:
    python3 scripts/set_promo_banner.py --enable
    python3 scripts/set_promo_banner.py --disable
    python3 scripts/set_promo_banner.py --status
"""

import argparse
import json
import os
import sys
import urllib.request


def get_firebase_token() -> str:
    path = os.path.expanduser("~/.config/configstore/firebase-tools.json")
    if not os.path.exists(path):
        print("Error: firebase-tools.json not found. Run 'npx firebase-tools login' first.")
        sys.exit(1)
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    token = data.get("tokens", {}).get("access_token")
    if not token:
        print("Error: access_token not found in firebase-tools.json.")
        sys.exit(1)
    return token


def main():
    parser = argparse.ArgumentParser(description="Toggle SongFlip Promo Banner in Firestore.")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--enable", action="store_true", help="Enable the promo banner")
    group.add_argument("--disable", action="store_true", help="Disable the promo banner")
    group.add_argument("--status", action="store_true", help="Check current banner status")
    args = parser.parse_args()

    token = get_firebase_token()
    url = "https://firestore.googleapis.com/v1/projects/songflip-web/databases/(default)/documents/config/promo_banner"

    if args.status:
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
        try:
            with urllib.request.urlopen(req) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                fields = data.get("fields", {})
                enabled = fields.get("enabled", {}).get("booleanValue", False)
                campaign = fields.get("campaign_id", {}).get("stringValue", "none")
                print(f"Promo Banner Status: {'ENABLED' if enabled else 'DISABLED'} (Campaign: {campaign})")
        except Exception as e:
            print(f"Error fetching status: {e}")
        return

    new_state = True if args.enable else False
    patch_url = f"{url}?updateMask.fieldPaths=enabled"
    body = json.dumps({"fields": {"enabled": {"booleanValue": new_state}}}).encode("utf-8")

    req = urllib.request.Request(patch_url, data=body, headers={
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    })
    req.get_method = lambda: "PATCH"

    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print(f"Promo Banner successfully set to: {'ENABLED' if new_state else 'DISABLED'}")
    except Exception as e:
        print(f"Error updating banner: {e}")
        if hasattr(e, "read"):
            print(e.read().decode("utf-8"))
        sys.exit(1)


if __name__ == "__main__":
    main()
