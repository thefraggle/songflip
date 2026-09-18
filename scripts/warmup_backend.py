#!/usr/bin/env python3
import urllib.request
import time
import sys

TARGETS = [
    ("L2 Cache Health", "https://cache.songflip.link/health", {}),
    ("L2 Cache Resolver", "https://cache.songflip.link/resolve?url=https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", {"x-web-client": "songflip"}),
    ("Web Share SSR", "https://songflip.link/s/rickroll", {"User-Agent": "SongFlip-Warmup/1.0"}),
]

def warmup():
    print("🔥 Starting post-deploy backend warmup...")
    all_ok = True
    for name, url, headers in TARGETS:
        success = False
        last_error = ""
        for attempt in range(1, 4):
            start = time.time()
            try:
                req = urllib.request.Request(url, headers=headers)
                with urllib.request.urlopen(req, timeout=12) as resp:
                    elapsed = int((time.time() - start) * 1000)
                    if resp.status == 200:
                        print(f"  ✓ {name}: HTTP 200 in {elapsed}ms (Attempt {attempt})")
                        success = True
                        break
            except Exception as e:
                last_error = str(e)
                time.sleep(1.5)
        if not success:
            print(f"  ❌ {name} failed: {last_error}")
            all_ok = False

    if all_ok:
        print("🚀 All backend endpoints are warm and operational!")
    else:
        print("⚠️ Warmup finished with warnings.")
    return 0 if all_ok else 1

if __name__ == "__main__":
    sys.exit(warmup())
