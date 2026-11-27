#!/usr/bin/env python3
"""
Builds and validates Chrome (Chromium) and Firefox Manifest V3 extension ZIP packages.
"""

from __future__ import annotations

import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXT_DIR = ROOT / "extension"
DIST_DIR = EXT_DIR / "dist"

RUNTIME_FILES = [
    "background.js",
    "popup.html",
    "popup.css",
    "popup.js",
    "shared.js",
    "icons/icon-16.png",
    "icons/icon-32.png",
    "icons/icon-48.png",
    "icons/icon-128.png",
    "icons/qr-songflip.svg",
]


def validate_manifest(manifest_path: Path, is_firefox: bool) -> dict:
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert data.get("manifest_version") == 3, f"{manifest_path.name}: manifest_version must be 3"
    assert len(data.get("name", "")) <= 75, f"{manifest_path.name}: name exceeds 75 chars"
    assert len(data.get("description", "")) <= 132, f"{manifest_path.name}: description exceeds 132 chars"

    if is_firefox:
        assert "scripts" in data.get("background", {}), "Firefox manifest requires background.scripts"
        gecko = data.get("browser_specific_settings", {}).get("gecko", {})
        assert gecko.get("id"), "Firefox manifest requires gecko.id"
        assert gecko.get("data_collection_permissions", {}).get("required"), (
            "Firefox manifest requires gecko.data_collection_permissions.required"
        )
    else:
        assert data.get("background", {}).get("service_worker") == "background.js", "Chrome manifest requires service_worker"

    for size, rel_path in data.get("icons", {}).items():
        icon_file = EXT_DIR / rel_path
        assert icon_file.is_file(), f"Missing icon file for size {size}: {icon_file}"

    return data


def validate_csp_and_sources() -> None:
    html = (EXT_DIR / "popup.html").read_text(encoding="utf-8")
    assert not re.search(r"<script(?![^>]*\bsrc=)[^>]*>", html, re.IGNORECASE), "Inline <script> found in popup.html"
    assert not re.search(r"\bon(?:click|change|submit|input|load)=", html, re.IGNORECASE), "Inline event handler found in popup.html"

    for js_name in ("background.js", "popup.js", "shared.js"):
        src = (EXT_DIR / js_name).read_text(encoding="utf-8")
        assert "eval(" not in src and "new Function(" not in src, f"Unsafe eval/Function found in {js_name}"


def build_zip(manifest_path: Path, manifest_data: dict, output_zip: Path) -> None:
    DIST_DIR.mkdir(parents=True, exist_ok=True)
    if output_zip.exists():
        output_zip.unlink()

    with zipfile.ZipFile(output_zip, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("manifest.json", json.dumps(manifest_data, indent=2) + "\n")
        for rel_path in RUNTIME_FILES:
            src_file = EXT_DIR / rel_path
            assert src_file.is_file(), f"Missing runtime file: {src_file}"
            zf.write(src_file, arcname=rel_path)


def main() -> int:
    chrome_manifest = validate_manifest(EXT_DIR / "manifest.json", is_firefox=False)
    firefox_manifest = validate_manifest(EXT_DIR / "manifest.firefox.json", is_firefox=True)
    validate_csp_and_sources()

    version = chrome_manifest["version"]
    assert firefox_manifest["version"] == version, "Chrome and Firefox manifest versions must match"

    chrome_zip = DIST_DIR / f"songflip-chrome-v{version}.zip"
    firefox_zip = DIST_DIR / f"songflip-firefox-v{version}.zip"

    build_zip(EXT_DIR / "manifest.json", chrome_manifest, chrome_zip)
    build_zip(EXT_DIR / "manifest.firefox.json", firefox_manifest, firefox_zip)

    print(f"Built {chrome_zip.relative_to(ROOT)} ({chrome_zip.stat().st_size} bytes)")
    print(f"Built {firefox_zip.relative_to(ROOT)} ({firefox_zip.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
