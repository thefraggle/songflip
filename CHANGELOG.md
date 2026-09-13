# Changelog

## [1.2.30] - 2026-09-13
- **Smarter Voucher Input**: Pasted voucher codes are now recognized automatically even when copied alongside a full message, with no manual editing needed.
- **Reliable Music Links**: Improved link handling prevents connection errors and ensures your music opens smoothly every time.
- **Clearer Visual Feedback**: Interactive buttons now show helpful status indicators while actions are being prepared.

## [1.2.29] - 2026-09-12
- **Choose Your Music App**: Added an optional "Ask every time" setting for anyone using multiple music services, letting you pick where to play each song with a single tap.
- **Seamless Shazam Links**: Songs identified with Shazam now open instantly and reliably in your favorite music player without interruptions.
- **Global Polish**: Quick player selection is fully translated across all 24 supported languages.

## [1.2.28] - 2026-09-12
- **Native Player Share Fix**: Sharing a song from within your music player via the system share menu now generates a universal smart link to share with friends instead of reopening your player.
- **Accurate History Counting**: Resolved a count discrepancy where pre-cached links were counted in Settings before appearing in the history list.
- **Seamless Share Previews**: Shared tracks now seamlessly populate the in-app action banner with instant prefetching.

## [1.2.27] - 2026-09-12
- **Material You Dynamic Theming**: Fixed the monochrome app icon on Pixel and modern Android devices to adapt beautifully and crisply to your home screen theme.
- **Enhanced Link Interception**: Added support for Shazam links, allowing you to open identified songs directly in your preferred music player.
- **Smarter Promo Code Input**: Redeeming promo codes is now more flexible and forgiving with automatic formatting, cleaner error handling, and helpful rate-limit feedback.
- **Player Compatibility**: Improved launch detection and player compatibility across custom setups and community packages.

## [1.2.26] - 2026-09-10
- **Smart Link Assistant**: An interactive setup guide checks your installed music apps and helps you resolve link conflicts with a single tap.
- **Instant Playback**: Songs that already belong to your preferred music player now open directly without any waiting time.

## [1.2.25] - 2026-09-10
- **Smart Playlist Handling**: When a playlist link is shared or copied, the app clearly detects it and opens the original playlist in your music app with a single tap.
- **Link Reliability**: Improved cross-platform link detection ensures shared tracks and playlists are always handled cleanly without conversion errors.

## [1.2.24] - 2026-09-10
- **Instant Speed & History**: Smarter caching and accurate history tracking ensure converted songs open instantly and appear reliably.
- **Global Polish**: Localized shortcuts, setup tips, and share actions across all 24 supported languages with accurate plural counts.
- **Enhanced Privacy & Security**: Strengthened link validation and protection to keep your music sharing fast, private, and secure.

## [1.2.23] - 2026-09-10
- **Direct Playback**: Shared music links now open straight in your preferred player without detour.
- **Reliable Fallbacks**: If a music app is not installed, songs now open smoothly in your web browser instead.
- **Helpful Alerts**: Clearer messages when redeeming codes or handling connection issues.

## [1.2.22] - 2026-09-09
- **More Music Services**: Added support for SoundCloud and Bandcamp so you can open indie tracks and underground gems seamlessly in your favorite player.
- **Easy Link Sharing**: Shared music pages now include a one-tap share button to quickly send songs to friends.
- **Smarter Clipboard Detection**: Copied tracks from even more music apps are recognized immediately when you open the app.

## [1.2.21] - 2026-09-08
- **Effortless Setup**: A quick 3-step guide and clear status badge for your active music services.
- **Smart App Detection**: Your preferred music app is now recognized automatically on first launch.
- **Instant Alternatives**: Convert music links on the spot via share menu or clipboard without setup.

## [1.2.20] - 2026-09-07
- **Streamlined Preferences & Dashboard**: Removed the redundant manual link tester across Android and iOS in favor of the automatic Clipboard Smart-Banner.
- **Automated Store Release Notes**: Resolved tag parsing to ensure localized Play Store and TestFlight release notes always dynamically match the latest version changes.
- **Enhanced UX**: Refined dashboard layout on both platforms with a clean, distraction-free view focusing on prominent 1-tap playback and song history.

## [1.2.19] - 2026-09-07
- **Focused Clipboard Actions**: Streamlined the Clipboard Smart-Banner to prioritize prominent 1-tap playback, ensuring clean action button separation between free playback and PRO smart-link generation.
- **Web-Share Landing Page Resolution**: Fixed server-side preset routing to prevent cross-song metadata collisions and ensure 100% accurate track details on universal smart links.
- **Playback Reliability**: Enhanced touch target sizing and immediate visual responsiveness when launching target players directly from copied clipboard links.

## [1.2.18] - 2026-09-07
- **Header Subtitle Display**: Fixed an issue where the app tagline in the dashboard header was abruptly truncated after the first word across localized languages.
- **Text Layout & Wrapping**: Enhanced header banner layout to smoothly accommodate localized descriptions on screens of all sizes with multi-line wrapping.
- **Streamlined Settings**: Removed third-party cross-promotional cards and purged unused localization assets for a cleaner, distraction-free preferences view.

## [1.2.17] - 2026-09-07
- **Optimized Playback Routing**: Enhanced task stack flags and intent category handling ensure smooth, immediate playback in target music apps.
- **Cache Auto-Purge**: Migrated link cache to v5 to automatically purge outdated or stale entries across all tester devices.
- **Multiple Music Apps Tip**: Added a quick guidance tip in Settings explaining how to use native sharing when competing music apps claim links.
- **Enhanced Reliability**: Strengthened link resolution pipeline and dual-hash invalidation consistency.

## [1.2.16] - 2026-09-05
- **Complete 24-Language Parity**: Full native translations across all 24 supported languages, ensuring complete coverage for song history and features.
- **Seamless Language Switching**: Instant language updates upon selection with reliable preference persistence across app launches.
- **Refined Phrasing**: Polished localized terminology and removed residual technical jargon for a smoother, native reading experience.
- **Updated Legal Information**: Direct access to dedicated privacy policy, imprint, and terms of service pages.

## [1.2.15] - 2026-09-05
- **Conversion Milestones**: Celebrate your song conversion milestones with discreet upgrade perks and voucher redemption.
- **History Capacity Overview**: Easily track your saved song capacity and unlock extended history with a single tap.
- **Improved Clipboard Detection**: Faster and more reliable detection of copied music links upon opening the app.

## [1.2.14] - 2026-09-04
- **Direct Music Playback**: Fixed an issue where converted songs opened the web browser with an error instead of playing directly in your music app.
- **Automatic Link Healing**: Saved songs are automatically refreshed and repaired to ensure smooth, immediate playback.

## [1.2.13] - 2026-09-04
- **Dynamic App Shortcuts**: Long-press the app icon to immediately play your last converted song or pause link redirects for 1 hour.
- **One-Tap Cache Refresh**: Instantly refresh and re-resolve any song from your history to clear outdated links and repair playback destinations.
- **Instant Clipboard Prefetching**: Detected music links are now pre-resolved in the background for zero-latency instant playback.
- **Enhanced Tactile Feedback**: Added subtle haptic responses across link actions, clipboard banners, and preferences for a more responsive feel.
- **Dead-Link Detection**: Real-time pre-validation filters out removed or restricted videos to keep resolution caches completely clean.

## [1.2.12] - 2026-09-03
- **Universal Link Sanitizing**: Automatically strips tracking and referral parameters from links shared via WhatsApp, Telegram, and social media across all supported platforms.
- **Intelligent Search Matching**: Cleans metadata noise such as remaster, live, and version tags to ensure reliable matching across music services.
- **Complete Translations**: Finalized full translations across all 24 supported languages with complete parity.
- **Enhanced Reliability**: Extended network tolerances and improved fallback resolution for obscure and regional releases.

## [1.2.11] - 2026-09-03
- **Broader Link Detection**: The clipboard banner now instantly recognizes compact share links from all supported music apps, including short links shared via messaging apps.
- **Enhanced Reliability**: Refined background verification and startup checks for an even faster, smoother experience.

## [1.2.10] - 2026-09-02
- **Clipboard Smart-Banner**: When you copy a music link, opening the app now displays a quick action banner to immediately play the track in your favorite player or share a universal link with a single tap.
- **Streamlined Settings**: Reorganized settings put your most important preferences — including song history, language, and appearance — right at your fingertips.
- **Customizable Link Detection**: You can now conveniently enable or disable automatic clipboard link detection at any time in settings.
- **Adaptive App Icon**: The app icon now automatically matches your chosen system theme and wallpaper colors on supported devices.
- **Polished Experience**: All new actions and options are fully translated across all 24 supported languages with smoother transitions.

## [1.2.9] - 2026-09-01
- **Fair Promo Code Protection**: Enforced anonymous per-device redemption limits to prevent unauthorized duplicate activations.
- **Enhanced Error Feedback**: Localized duplicate redemption warnings across all 24 supported languages.
- **Backend Optimization**: Deployed atomic Firestore transaction checks for all promo code redemptions.

## [1.2.8] - 2026-09-01
- **Zero-Delay Offline Handling**: Instantly detects offline state and forwards to original links without waiting for network timeouts.
- **R8 Code & Resource Optimization**: Enabled R8 shrinking and resource optimization for smaller download size and faster launch times.
- **Bot & Pre-Launch Test Isolation**: Automated test lab and emulator traffic are now cleanly routed to debug streams for precise analytics.
- **Comprehensive Telemetry**: Added anonymous diagnostics for purchase flows, quick settings tile toggles, and review interactions.
- **Lifecycle Stability**: Refined activity lifecycle handling to ensure accurate launch detection across configuration changes.

## [1.2.7] - 2026-08-31
- **Performance & Reliability**: Optimized background processing for faster and smoother song redirection.
- **Stability Improvements**: General connectivity and stability enhancements.

## [1.2.6] - 2026-08-31
- **In-App Feedback & Rating**: Conveniently rate SongFlip and share feedback directly from settings.
- **Complete Translations**: Fully localized all new setup actions and buttons across all 24 supported languages.
- **Refined Share Pages**: Polished high-resolution artwork and player buttons on universal flip link pages.
- **Smoother Experience**: Faster background redirections and improved stability.

## [1.2.5] - 2026-08-29
- **Live Status Indicator**: The top status banner now dynamically reflects link configuration states alongside pause controls.
- **One-Tap System Setup**: Added an immediate configuration action directly in the status banner when setup is required.
- **Search Link Resolution**: Improved resolution and direct catalog playback for shared music search queries.
- **Performance & Reliability**: Enhanced link matching and faster fallback handling across all supported streaming services.

## [1.2.4] - 2026-08-25
- **Universal Album Sharing (PRO)**: Full support for sharing full music albums with high-resolution artwork and direct player buttons.
- **Instant Cloud Sync**: Shared songs and albums automatically sync to the high-speed global cache upon sharing.
- **Rolling Cache Renewal**: Shared links now automatically extend their 90-day active window every time someone opens them.
- **Accurate URL Matching**: Enhanced normalization and link hashing across all streaming platforms.
- **Branded Link Experience**: Refined dark-mode landing page and informative status pages for shared music links.

## [1.2.3] - 2026-08-25
- **Universal Web Links (PRO)**: Generate universal share links so friends on any music player can listen instantly.
- **Direct System Sharing**: Share music links from any app to instantly copy a universal link.
- **History Sharing**: Share universal links directly from your song history with a single tap.
- **Fast Track Detection**: Enhanced recognition and faster matching across all music services.

## [1.2.2] - 2026-08-25
- **Flexible Promo Codes**: Added support for new gift codes with instant activation.
- **Accurate Expiry Display**: Precise renewal and access dates now shown across all menus.
- **High-Speed Cache Engine**: Faster and more reliable track conversion across all music platforms.
- **Localization Refinements**: Polished wording and status labels in all 24 languages.

## [1.2.1] - 2026-08-25
- **Enhanced Song History**: Clearer artist labels, playlists, and album tags in your history.
- **Stability & Crash Fixes**: Resolved layout and language display issues across multiple languages.
- **Improved Purchase Reliability**: Smoother activation and instant restoration for subscriptions.

## [1.2.0] - 2026-08-24
- **Ultra-Fast Level 2 Server Cache**: Songs resolve in milliseconds for PRO users using our new anonymous high-speed cache.
- **Smoother Subscription Experience**: Improved purchase flow and instant restore capabilities.
- **Optimized Link Handling**: Enhanced reliability across all supported streaming services.
- **Self-Refreshing Cache**: Intelligent 90-day automatic metadata refresh for always-accurate links.

## [1.1.9] - 2026-08-24
- **Faster Link Resolution**: Improved track detection for international and regional music links.
- **Cleaner Header Layout**: Streamlined main screen with more space and a cleaner top bar.
- **Manual Link Converter in Settings**: Easily test and convert music links directly from the settings menu.
- **High-Speed Cache Support**: Optimized background resolution engine for instant playback.

## [1.1.8] - 2026-08-24
- **Enhanced Settings**: Clearer layout with direct access to all app preferences.
- **Extended History Support**: Smarter storage handling for larger collections of converted songs.
- **Promo Code Support**: Directly redeem voucher codes in settings to unlock perks.
- **Performance & Stability**: Smoother animations and robust link handling across all streaming services.

## [1.1.7] - 2026-08-24
- **Song History**: View and replay your recently flipped songs anytime directly in settings.
- **Manage History**: Copy links or remove songs from your history with a single tap.
- **Clear All**: Reset your local song history and cache whenever you want.

## [1.1.6] - 2026-08-23
- **Instant Feedback**: Immediate confirmation notification on incoming music links.
- **Accurate Track Matching**: Updated metadata resolution for Spotify, Apple Music, and other platforms.
- **Single Track Guarantee**: Links with track IDs always open as single songs, never albums.
- **Localized Release Notes**: Multi-language update descriptions.

## [1.1.5] - 2026-08-23
- **Reliable Player Launching**: Seamless cascade ensures your target music player opens reliably.
- **Fail-Safe Search Routing**: Guaranteed fallback search if a track lacks direct catalog mapping.
- **Cache Self-Healing**: Automatically resets corrupt or outdated cache entries on startup.
- **Deep Share Compatibility**: Flawless link extraction across all apps, share sheets, and clipboard sources.

## [1.1.4] - 2026-08-23
- **Smooth Link Redirection**: Eliminated redirect loops and resolved unexpected app freezes when opening music links.
- **Enhanced Song Recognition**: Added full support for modern song and album link formats.
- **Seamless App Launch**: Directly opens your music player without unnecessary web browser detours.
- **Reliable Network Compatibility**: Consistent link conversion across all internet connections, including VPNs.

## [1.1.3] - 2026-08-23
- **Simplified Settings**: Streamlined settings menu with a clean, focused design and effortless navigation.
- **Symmetric Music Grid**: Balanced and perfectly aligned service selection for a smoother look and feel.
- **Direct Link Forwarding**: Intuitive and reliable music link redirection without unnecessary toggles.

## [1.1.2] - 2026-08-23
- **Music Provider Logos**: Crisp service logos for Spotify, Apple Music, YouTube Music, Deezer, Tidal, and Amazon Music.
- **Clear Selection Highlights**: Calmer, consistent design when choosing your preferred music player.
- **Accurate Language Detection**: Automatically matches your system language right from the start.

## [1.1.1] - 2026-08-23
- **Instant Offline Fallback**: Shared links open immediately in your browser when offline without waiting for network timeouts.
- **Direct Deezer & Tidal Playback**: Links to Deezer and Tidal now launch directly in their apps without extra steps.
- **Smoother & Faster Performance**: Enhanced background speed and improved overall app responsiveness.

## [1.1.0] - 2026-08-23
- **Instant Link Opening**: Previously opened links now open in a fraction of a second without waiting for network requests.
- **Fast Track Lookup**: Significantly faster song conversion, even on slower mobile connections.
- **Direct Spotify Playback**: Links to Spotify now launch immediately inside the Spotify app without web redirects.
- **Live Song Info**: Shows the song title and artist directly when flipping into your player.

## [1.0.5] - 2026-08-22
- **Compatibility Update**: Full compatibility with the latest system standards.
- **Edge-to-Edge Experience**: Refined transparent system bars for dark and light modes.

## [1.0.4] - 2026-08-22
- **Full Album Overview**: Shared album links now open the entire album with all tracks instead of single songs.
- **Instant Quick Settings Sync**: Tapping the Quick Settings tile updates the active/pause state in real time.
- **Streamlined Settings**: Focused options and improved stability.

## [1.0.3] - 2026-08-22
- **Theme Selection**: Switch between System, Light, and Dark appearance in settings.
- **Apple Music Albums**: Complete albums from Apple Music now start playing directly in YouTube Music.
- **Clear & Clean UI**: Simplified status display, refined settings header with close button, and polished native translations.

## [1.0.2] - 2026-08-22
- **Seamless Updates**: App updates can now be installed directly over existing versions.
- **Accurate Status Banner**: Clearly shows whether link redirection is active, paused, or requires setup.
- **Legal Links**: Quick access to Privacy Policy, Legal Notice, and Terms of Use in settings.

## [1.0.1] - 2026-08-22
- **Harmonized Live Status Banner**: Synchronized top status card so initial setup requirements are clearly highlighted.
- **Legal Footer & Compliance**: Added direct links to Privacy Policy, Legal Notice, and Terms of Service.
- **Dynamic Version Display**: Integrated dynamic app version badge into all footers.

## [1.0.0] - 2026-08-22
- **Universal 6-Platform Link Resolution**: Instant redirection between Spotify, Apple Music, YouTube Music, Tidal, Deezer, and Amazon Music.
- **Direct Instant Playback Engine**: Immediate playback on YouTube Music without intermediate search pages.
- **Album & Artist Support**: Full support for complete albums/EPs and artist discography profile links.
- **Quick Settings Tile & Smart Pause**: Quick Settings tile with customizable pause durations (15m, 1h, tomorrow morning, indefinitely).
- **Share Target Integration**: Resolve music links shared directly from messaging and social apps.
- **Interactive Test Studio**: In-app test bench to inspect track metadata and simulate conversions.
- **22 Languages Supported**: Full native localization across 22 languages.
- **Modern Interface**: Clean glassmorphic design with barrier-free touch targets.
