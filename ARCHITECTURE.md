# SongFlip Architecture & Internals 🏛️

This document outlines the technical design, architectural patterns, and resolution pipeline powering **SongFlip**.

SongFlip is designed to solve a simple problem with high engineering rigor: **How to intercept, resolve, and redirect cross-platform music links with sub-50ms latency, zero intermediate UI, and zero user tracking.**

---

## 1. High-Level System Overview

SongFlip operates across three client layers (Android, iOS, Web) backed by a shared Kotlin Multiplatform engine and a serverless edge cache:

```mermaid
graph TD
    A["Incoming Music Link (WhatsApp, Telegram, Browser)"] --> B["Native OS Interception Layer"]
    B -->|"Android: App Links / Intent Filter"| C["Regex Extraction & Parameter Sanitization"]
    B -->|"iOS: Share Extension / Action Button"| C
    B -->|"Web: songflip.link / SSR"| C

    C --> D["KMP Shared Core (Resolution Pipeline)"]

    D --> E{"L1 Local Cache (Memory / SQLite)"}
    E -->|"Hit (<5ms)"| H["Direct Playback Launch Intent"]
    E -->|"Miss"| F{"L2 Edge Cache (Firestore Short-Hash / Pro Tier)"}
    
    F -->|"Hit (~30-50ms)"| H
    F -->|"Miss"| G["L3 Multi-Source Upstream Engine"]
    
    G -->|"Odesli / Shazam / Deezer / YouTube Scraper"| I["Entity Matching & Normalization"]
    I --> J["Write to L1 & L2 Edge Cache"]
    J --> H
```

---

## 2. The Resolution Pipeline (Step-by-Step)

### Step 1: Deterministic URL Sanitization
Music streaming services append aggressive tracking clutters to share links (e.g. `?si=...`, `?context=...`, `?utm_source=...`, `intl-*` subdomains).
Before hashing or resolving, the URL is strictly canonicalized:
- Tracking query parameters are stripped.
- Canonical entity types (`track`, `album`, `artist`, `playlist`) and platform IDs are extracted via regex guards.
- Eliminating tracking parameters maximizes cache hit rates across different social networks.

### Step 2: Tiered Caching Architecture

| Tier | Technology | Latency | Scope & Invalidation |
|---|---|---|---|
| **L1 (Local)** | In-Memory / SQLite Room | `< 5 ms` | Device-only. Sub-millisecond playback launch for repeated tracks. Always active on all devices. |
| **L2 (Edge Cache)** | Serverless Firestore Edge *(Pro Tier)* | `~30–50 ms` | Global, indexed via 12-char SHA-256 short hashes (`/resolve`). Server infrastructure funded via SongFlip PRO to prevent rate-limiting for viral links & power web share pages. |
| **L3 (Upstream)** | Multi-Provider Aggregator | `~200–500 ms` | Cold fallback only. Queries Odesli, iTunes Search API, Deezer Catalog API, and Shazam REST directly from the client. |

> **Architectural Note on Tiering:** SongFlip's client engine is completely autonomous and operates with 100% functionality on device using L1 + L3 alone. The L2 Edge Cache is an optional, serverless performance tier that eliminates client-side network roundtrips for popular music entities.

### Step 3: Self-Healing & Edge Cases
- **Self-Titled Albums:** Search APIs frequently map an album name (matching the artist's name) to a single track video instead of the album playlist. SongFlip enforces strict entity type validation (`music.youtube.com/playlist?list=OLAK5uy_...` for albums) to prevent single-video downgrades.
- **Shazam Links:** Apple's Shazam CDN blocks standard user agents with HTTP 405. SongFlip leverages the internal discovery REST endpoint (`amp.shazam.com/discovery/v5/...`) with native headers to retrieve clean Apple Music and ISRC identifiers without auth.
- **Regional Domains & Morphe/ReVanced:** On Android, custom modded packages (e.g. `app.revanced.android.apps.youtube.music`) and regional Amazon Music domains (`music.amazon.de`, `music.amazon.co.uk`) are dynamically supported.

---

## 3. Kotlin Multiplatform (KMP) Architecture

The codebase separates platform-specific UI from deterministic platform-agnostic business logic:

```text
├── app/                  # Native Android App (Jetpack Compose, Material 3, Quick Settings Tile)
├── iosApp/               # Native iOS App (SwiftUI, Share Extension, App Intents)
├── shared/               # Kotlin Multiplatform Core (Shared by Android & iOS)
│   ├── commonMain/       # Shared URL sanitizers, cache manager, HTTP engine (Ktor), models
│   ├── androidMain/      # Android PackageUtils & Intent resolution helpers
│   └── iosMain/          # iOS Framework export & Swift bridging
└── functions/            # Firebase Cloud Functions (TypeScript edge proxy, SSR landing pages, HMAC vouchers)
```

### Shared Logic Benefits:
- **Consistent Resolver Behavior:** Both platforms share identical regex rules and parsing priority. A song resolved on Android resolves identically on iOS.
- **Zero Drift:** Bug fixes in the resolution engine only need to be written and unit-tested once in `shared/commonMain`.

---

## 4. Privacy & Zero-Tracking Principles

SongFlip is built around strict data minimalism:
1. **No User Accounts:** Users never log in or provide emails.
2. **No Listening Habit Profiling:** Resolved links are never tied to user identities. Cache lookups use anonymous SHA-256 hashes of the music entity URL.
3. **No Third-Party Advertising SDKs:** Zero tracking cookies, zero advertising networks (Google Ads, Meta Pixel, Adjust, AppsFlyer are strictly absent).
4. **Self-Hosted Privacy Telemetry:** Anonymous diagnostic metrics (Aptabase / Umami) with zero cookies, no IP retention, and complete opt-out support.

---

## 5. Security & Verification

- **HMAC Signed Vouchers:** Promo codes are verified server-side with constant-time cryptographic signatures to prevent brute-force enumeration.
- **SSRF Hardening:** Incoming URL inputs on cloud endpoints are validated against strict whitelist regexes before executing any upstream network call.
