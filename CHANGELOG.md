# Changelog

## [1.11.7] - 2026-10-28
- **Comprehensive Interface Localization**: Localized remaining playlist indicators and accessibility controls across all languages.
- **Centralized Version Management & Network Hygiene**: Dynamized request metadata via centralized configuration and introduced structured diagnostic logging within the data engine.
- **Consistent Timeout Handling**: Standardized timeout messaging across link conversions for greater stability.

## [1.11.6] - 2026-10-27
- **Refined Studio Track Prioritization**: Music search and link conversion now consistently favor original studio versions over remixes, live recordings, and acoustic variations.
- **Enhanced Song Recognition**: Acoustic detection features global catalog fallback for international releases and subtle audio feedback indicators.
- **Smoother History & Artwork Backfill**: Optimized cover art hydration for past conversions, reducing network activity and speeding up history browsing.
- **Reliable Voucher Activation & Interface Polish**: Visual inline validation for voucher codes with responsive keyboard handling, comprehensive localization, and background stability updates.

## [1.11.5] - 2026-10-26
- **Enhanced Regional Catalog Resolution**: Search queries for streaming catalogs now dynamically consider the local region, enhancing discovery and accuracy for regional releases and international titles.
- **Security & Component Hardening**: Tightened access permissions for internal background services to ensure system security.
- **Documentation & Setup Templates**: Updated repository documentation, reference configuration templates, and security guidelines.

## [1.11.4] - 2026-10-24
- **Quick Player Selection**: Easily switch the target player directly from the song recognition and match screen.
- **Playlist Re-conversion from History**: Convert saved playlists to a newly selected player directly from the history menu.
- **Accurate Multi-Part Playlist Sharing**: Share links for partitioned playlists now preserve the selected playlist part.
- **Enhanced Player Support & Interface Polish**: Extended support for alternative music players during in-app search, along with refined layouts and clarified status notices.

## [1.11.3] - 2026-10-23
- **More Reliable Music Recognition**: Increased match accuracy for acoustic song recognition through global catalog fallback, supporting international tracks and special releases.
- **Clearer Playlist Notices**: Clear and early notices for private or personalized playlists directly during detection and forwarding.

## [1.11.2] - 2026-10-22
- **Enhanced Streaming Link Resolution**: Improved reliability when sharing links directly from streaming apps, ensuring smooth multi-hop redirect handling and instant playback launch.
- **General Stability & Performance**: Refined link cleaning and internal error handling for a seamless background experience.

## [1.11.1] - 2026-10-21
- **Enhanced Song History & Deduplication**: Refined history synchronization and automatic deduplication across conversions, ensuring favorite songs stay pinned and entry timestamps remain preserved.
- **Improved Link Resolution & Timeouts**: Extended URL normalization for international characters, improved link cleaning, and optimized network timeout handling.
- **Clearer Playlist Experience & Accessibility**: Added informative notices when streaming services do not support external playlist imports, expanded touch targets, and improved contrast ratios for better readability.

## [1.11.0] - 2026-10-21
- **Resilient Offline Music Recognition**: Scans captured while offline are now reliably preserved during temporary connection drops and processed seamlessly once back online.
- **Security & Internationalization**: Modernized cryptographic code validation with seamless feature retention, synchronized all 34 language catalogs, and refined special character search.

## [1.10.5] - 2026-10-21
- **Broader Catalog Search Without Duplicates**: In-app search now returns significantly more unique tracks for artist and song queries while automatically filtering out duplicate entries from compilations, greatest hits, and remastered re-issues in favor of the original studio release.
- **Expanded Player Detection & Selection**: Improved automatic discovery of compatible music players and extended preferred player selection across all supported streaming services.

## [1.10.4] - 2026-10-20
- **Reliable Promotional Code Activation & UI Polish**: Fixed a cryptographic verification edge case where certain promotional codes could not be redeemed, and refined the feature overview layout with a pinned header and automatic scrolling when entering a code.
- **Improved Album & Deluxe Edition Matching**: Enhanced album lookup accuracy for Deluxe and Bonus Track editions across Apple Music, Deezer, Tidal, Qobuz, and YouTube Music, and improved direct playback routing for YouTube Music album links.

## [1.10.3] - 2026-10-20
- **Quick-Access Shortcuts for Listen & Flip**: Start music recognition directly from the system Quick Settings tile, by long-pressing the app icon on your home screen, or via system shortcuts without opening the full app first.
- **General Stability**: Minor UI refinements and internal reliability improvements.

## [1.10.2] - 2026-10-20
- **Improved Playback Routing & App Detection**: Refined direct link opening and fallback handling for tracks, albums, artists, and playlists on Tidal and YouTube Music.
- **General Stability**: Minor UI refinements and internal reliability improvements.

## [1.10.1] - 2026-10-20
- **Reliable Promotional Code Persistence**: Fixed an issue where promotional codes redeemed in earlier versions could become inactive after an update, and enabled seamless re-activation on the same device.

## [1.10.0] - 2026-10-20
- **Audiophile Quality Prioritization**: New option in Settings that prioritizes 24-bit Hi-Res, Lossless, Dolby Atmos, and original studio album releases over radio edits, compilation tracks, and cover versions when matching songs on Tidal, Qobuz, Apple Music, and Deezer.
- **Complete Translations & Stability**: Added localized descriptions across all 34 supported languages and improved direct catalog search reliability on Tidal.

## [1.9.11] - 2026-10-20
- **Smarter Handling for Non-Music Links**: Sharing links from unsupported video or web platforms now opens the original link immediately without waiting for a lookup and provides a helpful tip to use Listen & Flip for recognizing music in videos.
- **Complete Translations & Stability**: Added localized guidance across all 34 supported languages and refined internal diagnostics.

## [1.9.10] - 2026-10-19
- **More Reliable Playlist Conversion & History Refresh**: Refreshing a saved playlist directly from your history now re-runs full track matching and updates the playlist entry in place, with improved cold-start synchronization when sharing playlists from other apps.
- **Clearer Playlist Smart Banner & Track Counts**: Copied playlists on the home screen now clearly show the target music player action, and converted playlists display the full source track count alongside matched songs.

## [1.9.9] - 2026-10-19
- **Smarter Recognition Quota & Clean History**: Recognizing a song that is already saved in your history now updates the existing entry without deducting an extra free recognition, and eliminates duplicate history entries.
- **Visual Polish**: Unified the waveform badge icon for recognized tracks in the song history and improved button layout for longer translations on the recognition result card.

## [1.9.8] - 2026-10-19
- **Enhanced Music Recognition & Level Adaptation**: Improved microphone signal amplification and dynamic volume feedback so songs playing nearby or at lower room volume are recognized much more reliably in Listen & Flip.
- **Clearer Feature Overview**: Streamlined the feature overview for a cleaner, more focused presentation across all supported languages.

## [1.9.7] - 2026-10-19
- **More Reliable Music Recognition**: Improved audio recording stability and ambient room sensitivity for faster and more accurate song recognition in Listen & Flip.
- **General Stability**: Internal synchronization refinements and performance improvements.

## [1.9.6] - 2026-10-19
- **Improved Offline Recognition Feedback**: Clearer status feedback when starting music recognition without an active internet connection, plus completed translations across all 34 supported languages.

## [1.9.5] - 2026-10-19
- **Listen & Flip (Music Recognition)**: Identify music playing around you directly inside SongFlip. Recognized tracks are automatically matched to your preferred streaming player and saved to your history without interrupting the moment.
- **Offline Club Mode**: No reception in a club or bar? Capture an audio fingerprint offline and let SongFlip automatically identify the song and add it to your history as soon as you're back online.
- **Identified Filter in History**: Easily find all songs you recognized around you using the new "Identified" badge and filter chip in your song history.

## [1.9.4] - 2026-10-19
- **Clearer Status Indicator**: Refined the header status badge with a subtle, unobtrusive design so your active feature state is immediately recognizable at a glance.
- **Faster SoundCloud & Bandcamp Resolution**: Direct metadata lookup for shared SoundCloud and Bandcamp links for even faster playback starts.
- **Improved Playlist Cache**: More consistent caching for converted playlists across all supported streaming services.

## [1.9.3] - 2026-10-18
- **Smarter Clipboard & Share Detection**: Copied and shared links from Qobuz, Beatport, and Shazam are now recognized immediately when opening the app or sharing from other apps.
- **Improved Song Selection & Search**: Choosing alternative song versions or sharing universal links from in-app search now works seamlessly across all supported music services.
- **Reliable History & Favorites**: Saved songs and history entries are preserved cleanly across updates and when refreshing links.
- **Complete Translations & Polish**: Completed missing translations across all supported languages and refined theme switching and playback reliability.

## [1.9.2] - 2026-10-16
- **Manual Match Correction in History**: Quickly access manual song match selection directly from the history menu whenever a converted track needs a different version.
- **Quick Header Navigation**: Open your account status and feature overview directly by tapping the header badge on the home screen.
- **Localization & Stability**: Fixed a crash when opening nested sheets in certain languages and improved formatting reliability across all supported translations.

## [1.9.1] - 2026-10-15
- **New Music Services**: Full support for shared Qobuz and Beatport links – open music from even more sources seamlessly.
- **Qobuz as Target Player**: Set Qobuz as your preferred music player to open incoming links and searches directly in high-res audio.
- **Faster Link Resolution**: Refined song matching and improved reliability across web previews and shared music pages.

## [1.9.0] - 2026-10-14
- **Favorites Everywhere**: Pin favorite songs with one tap in your history and filter your saved favorites at any time.
- **Faster & More Reliable History**: Rebuilt local history and song storage for instant loading and rock-solid synchronization across share menus and shortcuts.
- **Unified Search & Playlists**: Smoother song search, match selection, and playlist conversion with stronger security for promotional codes.

## [1.8.18] - 2026-10-14
- **SoundCloud as Target Player**: Select SoundCloud as your preferred music player to open shared tracks and searches directly in the SoundCloud app.
- **Rich Link Previews**: Shared music and playlist links now display richer social previews with audio playback support in messaging apps and social feeds.
- **Reliability & Stability**: Improved settings persistence across app reinstalls and refined playlist conversion stability.

## [1.8.17] - 2026-10-13
- **Smarter Code Redemption**: Smarter recognition of shared promotional codes from links, with clearer status feedback and multi-server redundancy.
- **Enhanced Connection Reliability**: Direct diagnostics and automatic recovery when link redirection encounters connectivity dropouts.
- **Stability & Performance**: Cleaned up system logging and refined internal configuration management.

## [1.8.16] - 2026-10-13
- **Rock-Solid Offline Access**: Uninterrupted access to all features even during network dropouts or offline mode.
- **Reliable History Sync**: Seamless history updates when converting links from share menus, shortcuts, or within the app without losing entries.
- **Improved Performance & Stability**: Enhanced internal data synchronization and robust background processing.

## [1.8.15] - 2026-10-12
- **Reliable Link Resolution**: Faster and more consistent music link matching with enhanced caching.
- **Playlist Stability**: Improved transfer handling and robust connection management for shared playlists.
- **Security & Hardening**: Fortified license validation and strengthened internal data checks.

## [1.8.14] - 2026-10-12
- **Favorites Stay Put**: Saved favorites are now kept permanently and survive re-flipping the same link.
- **Permanent History**: Your flip history is now kept permanently without expiring after a week.
- **Stability & Security**: Smoother app resume, safer handling of saved data and general hardening.

## [1.8.13] - 2026-10-12
- **Large Playlist Reliability**: Significantly enhanced conversion and transfer stability for playlists with 50+ tracks, including resilient timeout handling and adaptive request pacing.
- **Graceful Conversion Recovery**: Playlists now preserve all matched tracks even under spotty network conditions, avoiding abrupt transfer aborts.
- **Fast Same-Platform Transfers**: Instantaneous track matching when source and target streaming services align.
- **Stability & Performance**: General bugfixes and connection optimizations across all supported music services.

## [1.8.12] - 2026-10-11
- **Reliable Deezer Redirection**: Shared Deezer music links now open directly as songs in your preferred music player.
- **Streamlined Search**: Simplified and cleaner actions menu when browsing song search results.
- **Clearer Playlist Guidance**: Informative tips and guidance when converting restricted or personal playlists.
- **Stability & Polish**: General bugfixes and improvements for smoother music playback.

## [1.8.11] - 2026-10-10
- **More Languages**: Added full language support for Czech, Finnish, Hungarian, Greek, Romanian, and Thai across the entire app.
- **Reliable Player Selection**: When set to ask every time, the quick player picker opens promptly and reliably before opening any music link.
- **Playback Stability**: Improved music link resolution and seamless playback fallbacks when music apps are busy.
- **Security & Reliability**: Enhanced internal settings verification and robust background error handling.

## [1.8.10] - 2026-10-10
- **Cleaner Search & History**: Song cards in search and history now feature a streamlined action menu for sharing, favorites, and quick link copying.
- **Instant Fast Flip**: Tapping any cover or track opens the song directly in your favorite music player without delay.
- **Smoother Navigation**: The search keyboard automatically dismisses when scrolling through search results.

## [1.8.9] - 2026-10-09
- **Direct Song Search**: Search for songs directly in the app and open them in your favourite player with one tap.
- **Refine Matches**: Easily choose alternative song versions or recordings whenever needed.
- **More Playlist Services**: Convert and transfer playlists with TIDAL and Deezer.

## [1.8.8] - 2026-10-09
- **QR Code Sharing**: QR code generation for sharing music links is now seamlessly aligned with advanced link sharing options.
- **Polish & Refinements**: Cleaner menu options and overall interface improvements.

## [1.8.7] - 2026-10-09
- **Quick Player Selection**: Easily switch between your installed music players right from the main player card.
- **1-Tap Favorites**: Pin your favorite songs and playlists with a single tap in your history and filter them instantly.
- **QR Code Sharing**: Generate and scan QR codes for universal music links to share songs across devices effortlessly.
- **Smart Playlist Cleaning**: Duplicate tracks are automatically detected and cleaned before playlist transfers.
- **Precision Matching**: Enhanced title and album resolution across music streaming services for more accurate song playback.

## [1.8.6] - 2026-10-09
- **Bugfixes & Polish**: Improved detection and launch reliability for compatible music player apps.
- **Stability Improvements**: Under-the-hood fixes for link handling and overall app reliability.

## [1.8.5] - 2026-10-08
- **Playlist Details in History**: Tapping a converted playlist in your history now re-opens the complete track view with all parts and file export options, while the play icon on the cover starts instant playback.
- **Playlist Artwork**: Restored cover artwork displays for playlists in your history and added the artwork directly into the conversion header.
- **Sharing & Polish**: Added quick access to playlist details and updated options across all supported languages.

## [1.8.4] - 2026-10-08
- **Transparent Playlist Guidance**: When transferring extensive playlists, helpful guidance clarifies platform-specific public boundaries.
- **Clearer Status Messages**: Improved explanations for connection timeouts and long tracklists.
- **Fixes & Polish**: Updated informational messages and status descriptions across all supported languages.

## [1.8.3] - 2026-10-08
- **Flexible Player Selection**: When multiple compatible music apps are installed on your device, you can now choose your preferred default player directly in Settings.
- **Broader App Compatibility**: Music links now open even more seamlessly in your favorite installed media players.

## [1.8.2] - 2026-10-08
- **Stability Hotfix**: Resolved an unexpected startup issue to ensure smooth and reliable app opening.

## [1.8.1] - 2026-10-08
- **Instant Full Loading**: Unlocked playlists now refresh automatically without needing to re-open or convert again.
- **Enhanced Playlist Reach**: Large playlists reliably retrieve the full available tracklist with up to 200 songs.
- **Smoother Playback**: Faster and more consistent loading for long tracklists.

## [1.8.0] - 2026-10-08
- **Large Playlist Support**: Playlists with up to 200 songs can now be converted smoothly and navigated in convenient parts.
- **Playlist File Export**: Save and share entire playlists directly as M3U8 or CSV files to open them in your favorite media player or desktop app.
- **Smoother Playback Launch**: Start listening to each part with a single tap, with full control over all converted tracks.

## [1.7.6] - 2026-10-08
- **Persistent Playlist Conversions**: Converted playlists now remain available as a dashboard card until explicitly dismissed — preventing accidental loss if you close the details sheet.
- **Direct History Access**: A dedicated history icon in the header lets you jump straight to your recent tracks and playlists in a single tap.
- **Background Conversion Reliability**: Playlist transfers continue smoothly even if you close the sheet, change orientation, or switch app settings.
- **Bug Fix**: Resolved an issue where changing the app language could re-trigger the playlist sheet unexpectedly.

## [1.7.5] - 2026-10-07
- **Clear Playlist Guidance**: Transparent notice for large playlists explaining the current 50-track limit without account requirements.
- **Enhanced Connection Reliability**: Increased timeout tolerances for comprehensive playlist conversions to prevent connection drops.
- **Polish & Stability**: Improved error explanations and updated translations across all supported languages.

## [1.7.4] - 2026-10-07
- **Flexible Player Choice**: With "Ask every time" enabled, you can now pick your preferred music service on the fly whenever opening or sharing songs.
- **Polish & Stability**: Smoother sharing workflow and improved link redirection reliability.

## [1.7.3] - 2026-10-07
- **Smarter Service Selection**: Installed music apps are now prioritized at the top of the main screen for quicker 1-tap switching.
- **Alternative Player Support**: Seamless support for privacy-friendly community players like RiMusic, ViMusic, and InnerTune.
- **Clearer Pricing**: Annual plans now show the computed monthly price and your actual savings upfront.
- **Milestone Celebrations**: Milestone cards celebrate your music sharing with direct access to special discounts.
- **Thoughtful Feedback**: Review prompts now appear at more natural moments without interrupting your listening flow.
- **Polish & Stability**: Improved translations and smoother transitions.

## [1.7.2] - 2026-10-05
- **Faster Resolution**: Optimized caching opens songs faster and more reliably.
- **Smarter Playlists**: Playlists from your active service open directly without conversion detours.
- **Connection Recovery**: Temporary network timeouts now offer an instant retry option.
- **Keyboard Navigation**: All dialogs and sheets can now be closed via Escape key (ESC).
- **Stability & Polish**: Refined URL parsing and background performance improvements.

## [1.7.1] - 2026-10-04
- **Enhanced Keyboard Navigation**: Dialogs and sheets can now be closed immediately using the Escape key (ESC) for more comfortable navigation and ergonomics.
- **Hardened Link & Domain Validation**: Strict domain verification prevents spoofed or malicious music links from interfering with redirect resolution.
- **Transparent Offer Details**: Clearer and more consistent pricing and deal highlights across all regions.
- **Everyday Polish & Stability**: Performance improvements, smoother transitions, and enriched diagnostic reliability.

## [1.7.0] - 2026-10-04
- **Podcasts Support**: Shared podcast episodes and shows now open directly in your preferred player with accurate show matching.
- **Instant & Responsive Link Resolution**: Immediate, latency-free clipboard detection right when launching the app or returning to the home screen.
- **Accurate Search & Album Matching**: Improved matching for special characters, emojis, Spotify pre-release countdowns, and official albums.
- **Direct In-App Feedback**: Share feedback, report bugs, or suggest features directly inside the app with zero tracking.
- **Bug Fixes & Stability**: Comprehensive stability improvements, reliable cancellation handling, and smoother screen transitions.

## [1.6.9] - 2026-10-04
- **Instant Link Detection**: Immediate, latency-free clipboard detection right when launching the app or returning to the home screen (#60).
- **Direct In-App Feedback**: Share feedback, report bugs, or suggest features directly inside the app with zero tracking (#61).
- **Display & Regional Price Fixes**: Resolved edge cases where promotional pricing and offer terms were displayed inconsistently in certain regions.
- **Bug Fixes & Stability**: General stability improvements and smoother screen transitions.

## [1.6.8] - 2026-10-03
- **Enhanced Link Detection**: Self-referential app links are now recognized and filtered with a helpful hint to share a music link instead.
- **Improved Synchronization**: Automatic background recovery for existing account entitlements and status synchronization.
- **Bug Fixes & Stability**: General stability improvements and more dependable error recovery.

## [1.6.7] - 2026-10-03
- **Spotify Pre-Release Handling**: Spotify countdown pre-release links are now intelligently recognized. You can pre-save the album directly in Spotify or search for already released singles in your target player.
- **Accurate Album Matching**: Fixed YouTube Music matching for newly released official albums to avoid user-generated video playlists.
- **Polish & Stability**: Improved link normalization and faster background resolution.

## [1.6.6] - 2026-10-03
- **Clearer Descriptions**: Features now explain what they do for you – in all 31 languages.
- **Accurate Display in Every Region**: Fixed some details not showing correctly in certain countries.
- **Easier Access to Legal Info**: Terms and privacy policy are now linked in more places.
- **Polish & Stability**: Minor bug fixes.

## [1.6.5] - 2026-10-02
- **Streamlined Feature Overview**: Cleaner layout and more concise descriptions across core features.
- **Bug Fixes & Stability**: Hardened background diagnostics and more reliable action handling.
- **Everyday Polish**: Updated localizations across all 31 languages and smoother daily use.

## [1.6.4] - 2026-10-01
- **Instant & Responsive Link Resolution**: Cancellation and interruption handling has been hardened so switching apps or tapping away never hangs or delays your music redirects.
- **Enhanced Reliability & Diagnostics**: Stricter background error handling and diagnostics keep redirects smooth and dependable.
- **Accessibility & Polished Touch Targets**: UI controls and interactive sheets now feature clearer states, accessible feedback, and effortless touch targets.
- **Everyday Polish & Stability**: Visual adjustments and stability enhancements for everyday use.

## [1.6.3] - 2026-10-01
- **Crystal-Clear Link Setup**: Clarified Android setup instructions for link routing — explained why installed players (like Spotify) are intentionally greyed out in system settings.
- **Transparent Playlist Guidance**: Added clear explanations for why personalized mixes (e.g. Daily Mix) require a public playlist due to SongFlip's strict no-login privacy model.
- **Everyday Polish & Stability**: Visual adjustments and stability enhancements across all 31 languages.

## [1.6.2] - 2026-09-30
- **Robust Search Matching**: Special characters (`+`, `&`, `#`), colons, quotes, and emojis in podcast and song titles are cleanly filtered for accurate search results across all platforms (Pocket Casts, Apple, Spotify, etc.).
- **Reliable Fallbacks**: Enhanced search URL builders prevent empty results when opening complex titles.
- **Everyday Polish & Stability**: Visual adjustments and stability enhancements for everyday use.

## [1.6.1] - 2026-09-30
- **Clipboard Podcasts**: Copied podcast links can now be directly converted and opened in your preferred player with a single tap.
- **Enhanced Podcast Matching**: More accurate recognition of podcast shows and episode titles.
- **Everyday Polish & Stability**: Faster loading and smooth playback for daily use.

## [1.6.0] - 2026-09-30
- **Podcasts Support**: Shared podcast episodes and shows now open directly in your preferred player.
- **Faster Link Handling**: Smoother and quicker redirects across your favorite apps.
- **Performance & Polish**: Visual refinements and everyday reliability updates.

## [1.5.4] - 2026-09-30
- **Refined Campaign Banners**: Polished visual design with warm adaptive accents for special events.
- **Reliable Link Conversions**: Faster, uninterrupted redirects across all music services.
- **Everyday Polish & Stability**: Visual adjustments and stability enhancements for everyday use.

## [1.5.3] - 2026-09-30
- **Seasonal Specials & Announcements**: Support for timely community events and special offers in your language.
- **Enhanced Link Handling**: Smoother and faster redirect experience across all supported music services.
- **Performance & Polish**: Subtle visual refinements and reliability updates for daily use.

## [1.5.2] - 2026-09-30
- **Accurate Player Detection**: Improved detection of installed music apps so your selected player is always matched correctly.
- **Seamless Music Playback**: Shared links open directly to the song without unwanted subscription prompts or landing pages.
- **Smoother Music Redirects**: General refinements for instant and reliable song playback.

## [1.5.1] - 2026-09-28
- **Cover Artwork in History**: Your history now displays song and album artwork with an instant play button.
- **Streamlined History**: Redesigned entries with easy one-tap sharing and quick link copying.
- **Instant Loading**: Cover artwork and track details load smoothly and without delay.

## [1.5.0] - 2026-09-26
- **Playlist Conversion & Sharing**: Convert entire playlists, open them directly in your preferred music player, or share them via web links.
- **Faster & More Reliable**: Lightning-fast link resolution, instant redirects, and automatic retry on unstable network connections.
- **Smarter Music Matching**: Even more accurate matching for songs, albums, shorts, and links across all supported services.
- **Complete Localization**: All conversion steps and dialogs fully translated across 31 languages.

## [1.4.18] - 2026-09-25
- **Direct Playlist Playback**: Tapping the main play button on shared playlists now opens directly in your favorite music app without any errors or verification screens.
- **Faster Shared Link Loading**: Shared song and playlist pages now load significantly faster and more reliably when sharing music with friends.
- **Smarter Music Matching**: Improved accuracy and reliability when finding matching tracks and artists across all supported streaming services.
- **Everyday Polish & Reliability**: General performance and stability enhancements for seamless, zero-delay redirects.

## [1.4.17] - 2026-09-24
- **Differentiated Playlist Diagnostics**: Automatic detection for private or personalized playlists (e.g. Daily Mix), unsupported platforms, and connection timeouts with clear guidance and 1-tap original playlist launch.
- **Reliable Link Resolution**: Optimized handling and routing for shared music links across supported music services.
- **Background & Resource Optimizations**: Streamlined event processing and smarter state checks when selecting target music players for reduced background activity.

## [1.4.16] - 2026-09-23
- **Full Multi-Language Support for Playlist Converter**: Completely localized all interactive playlist conversion steps, progress statuses, and sharing dialogs across all 31 supported languages.
- **Enhanced Conversion Status Feedback**: Fixed missing translations during track searching and queue building, ensuring a consistent native experience worldwide.
- **Everyday Polish & Reliability**: Minor visual and interface refinements across the app.

## [1.4.15] - 2026-09-22
- **Offline & Low-Connectivity Resilience**: Introduced automatic buffering and live retry for link resolution during network interruptions, keeping the workflow seamless when moving through dead zones.
- **Automated Reconnection Handling**: Seamlessly resumes link conversions the moment connectivity is restored, accompanied by a clean waiting indicator and manual retry options.
- **Comprehensive Quality & Data Model Verification**: Expanded automated test coverage for settings persistence, historical conversion tracking, and resilient timestamp recovery across all supported languages.

## [1.4.14] - 2026-09-22
- **Accessibility & Screen Reader Enhancements (WCAG AA)**: Added comprehensive descriptions across all interactive icons, upgraded PRO tier selector cards with clear selection semantics, and enlarged touch targets for effortless navigation.
- **Build & Dependency Modernization**: Updated core libraries and internal toolchains with hardened version verification and build guards.
- **Architecture & Developer Documentation**: Documented pipeline latency guarantees, timeout budgets, and expanded developer setup guidance in repository documentation.

## [1.4.13] - 2026-09-22
- **App Detection & Launch Hotfix**: Fixed a critical crash on devices where certain music players are not installed.
- **Reliability Enhancements**: Hardened cache and system inspection routines for seamless performance across all environments.

## [1.4.12] - 2026-09-22
- **Startup & Stability Hotfix**: Resolved an issue that could cause unexpected crashes during app launch.
- **Enhanced Performance**: Streamlined state management for a faster, more reliable user experience.

## [1.4.11] - 2026-09-22
- **Clearer Pricing & Promo Display**: Improved visibility for special promotions and limited-time deals on SongFlip PRO.
- **Refined Upgrade Experience**: Smarter calculation and display of regional currencies and regular comparison prices.
- **General Polish & Improvements**: Minor visual and stability enhancements across the app.

## [1.4.10] - 2026-09-22
- **Core Architecture & God-Class Decoupling**: Refactored `SongLinkEngine` to a modular strategy pattern with dedicated platform resolvers for Apple Music, Deezer, Tidal, YouTube Music, Spotify, and SongLink API.
- **Pure Multiplatform URL Utilities**: Pure KMP multi-byte UTF-8 percent decoding and hardened regex-based album detection across iOS and Android without platform dependencies.
- **Android Architecture & ViewModel Layer**: Introduced `MainViewModel` and `MainUiState` with unidirectional data flow, debounced clipboard inspection, thread-safe package cache, and asynchronous link diagnosis.
- **Backend Modularization**: Extracted Firebase Cloud Functions into focused domain modules (`security.ts`, `utils.ts`, `resolvers.ts`, `webshare.ts`, `proVerification.ts`) maintaining full backward compatibility.

- **Backend Security & Entitlement Hardening**: Strict server-side verification of PRO entitlements, secure HMAC coupon generation, and hardened Firestore rules.
- **Paywall & Purchase Flow Polish**: Instant automatic sheet dismissal upon coupon redemption, clear error and retry states when loading subscription options, localized error handling across all 31 languages, and synchronized purchase callback handling.
- **App Lifecycle & Privacy**: Smoother intent routing for incoming shared links without UI restarts, and privacy-preserving GDPR-compliant telemetry hashing for promo codes.
- **Stability & Performance**: Cleaned up release logging, structured warning diagnostics, and optimized client engine token configurations.

## [1.4.8] - 2026-09-21
- **Enhanced Conversion Reliability**: Improved service routing and smart fallback handling between all supported music platforms.
- **Playlist Converter Refinements**: Smoother progress tracking and enhanced completion handling for multi-track conversions.
- **Under-the-Hood Improvements**: Stability optimizations and diagnostic refinements to keep link handling fast and dependable.

## [1.4.7] - 2026-09-21
- **Unified Multiplatform Engine**: Migrated Android link conversion and caching to the shared Kotlin Multiplatform core for identical, lightning-fast resolving on Android and iOS.
- **Lifetime Offer Pricing Clarity**: Fixed Lifetime PRO pricing display to always show exact introductory discounts and localized pricing clearly.
- **Under-the-Hood Optimizations**: Streamlined cache storage and removed legacy networking layers for higher stability and smaller app footprint.

## [1.4.6] - 2026-09-21
- **Enhanced YouTube Shorts Support**: Convert shared YouTube Shorts directly into full music tracks in your target player.
- **Smarter Social Link Handling**: Seamlessly opens Spotify Blend, Jam, Live, and Profile links in their original app without interruption.
- **Reliable Mobile Conversions**: Smoother and faster link resolving, even on weak or unstable internet connections.

## [1.4.5] - 2026-09-21
- **Annual Intro Offer & Pricing Highlights**: Refined the upgrade experience to dynamically showcase first-year introductory savings and localized currency rates.
- **Enhanced Reliability**: Smoother pricing phase resolution and everyday stability improvements.

## [1.4.4] - 2026-09-20
- **Smoother Upgrades & Checkout**: Purchase buttons now reset instantly and reliably if you dismiss the upgrade sheet.
- **Faster App Startup**: Optimized background history loading for an instant, lag-free startup experience.
- **Enhanced Reliability & Comfort**: Refined button touch targets and strengthened playlist conversions for everyday listening.

## [1.4.3] - 2026-09-20
- **Modern Kotlin 2.1 Engine**: Upgraded app toolchain to Kotlin 2.1, official Compose Compiler Plugin, and modern build tools for smoother animations and faster startup.
- **Multiplatform Refinements**: Updated network, coroutines, and serialization components for faster multi-platform handling.
- **Seamless Edge-to-Edge Experience**: Modernized system bar insets and layout handling for modern device screens.

## [1.4.2] - 2026-09-20
- **Playlist History & 1-Tap Replay**: Converted playlists are now automatically saved to your history with track count badges and instant 1-tap queue playback.
- **Global Swarm Cache Acceleration**: Playlist conversions automatically seed the cloud cache, enabling sub-30ms instant resolutions for all users.
- **Enhanced Player Routing**: Smoother package detection and intent handling for alternative and modded music players.

## [1.4.1] - 2026-09-19
- **Lightning-Fast Playlist Conversion**: Optimized 50-track processing with smart instant caching for previously converted playlists.
- **Interactive Track Play Buttons**: Play individual songs directly on shared playlist web pages in the target platform's branded style.
- **Animated Progress Feedback**: Real-time visual progress and step indicators during playlist conversion in the app.
- **Reliability & Refinements**: Enhanced 1-tap queue launching for YouTube Music and Spotify.

## [1.4.0] - 2026-09-19
- **Universal Playlist Converter**: Convert entire playlists from your friends and open them directly in your preferred music player.
- **Playlist Web Sharing**: Share converted playlists via web links that anyone can view and import in 1 tap.
- **Smoother Experience**: Stability improvements and background performance optimizations.

## [1.3.14] - 2026-09-19
- **Unified Fast Link Matching**: Enhanced shared resolution engine for faster music switching and precise track-versus-album detection.
- **Reliable Link History**: Instant, smooth access to previously converted songs with improved background synchronization.

## [1.3.13] - 2026-09-19
- **Localized Pricing Consistency**: Dynamic and accurate currency display for subscription pricing across all regions and currencies.
- **Stability & Performance**: General refinements and background optimizations for everyday use.

## [1.3.12] - 2026-09-19
- **Fail-Safe Opening**: If a song cannot be switched, your original music link opens seamlessly so you never lose your music.
- **Smoother Experience**: Stability improvements and enhanced reliability for everyday listening.

## [1.3.11] - 2026-09-18
- **Lighter & Faster**: Streamlined app structure for faster loading times and a smaller storage footprint on your device.
- **Smoother Experience**: Stability improvements and subtle performance enhancements for everyday music switching.

## [1.3.10] - 2026-09-18
- **Helpful Link Guidance**: Links to podcasts and audiobooks are now clearly identified as unsupported, with direct shortcuts to open them in their original app.

## [1.3.9] - 2026-09-18
- **Expanded Global Music Links**: Comprehensive support for international streaming links across Spotify, Tidal, Deezer, and Amazon Music.
- **Smarter Album Detection**: Improved album and track routing with seamless multi-platform conversion.




## [1.3.8] - 2026-09-17
- **Expanded Player Compatibility**: Reliable detection and priority routing for custom and alternative YouTube Music players (including RVX and Anddea).
- **Dynamic Intent Visibility**: Optimized package visibility queries for seamless 1-click redirects to modified music apps.

## [1.3.5] - 2026-09-16
- **Universal Song Sharing**: Share music links that friends can open seamlessly on any streaming service.
- **Faster Song Lookups**: Popular tracks open even faster and more reliably with instant cloud matching.
- **Polish & Performance**: Smoother navigation and enhanced link recognition across all 31 supported languages.

## [1.3.4] - 2026-09-16
- **Expanded Language Support**: Added full in-app and store localization for Traditional Chinese (Taiwan/Hong Kong), Czech, Hungarian, Finnish, Greek, Romanian, and Thai.
- **Enhanced Global Experience**: Synchronized language selection and store assets across all platforms.
- **Store Listings & Visuals**: Fully updated and certified multi-language listings and screenshot assets for 37 international markets.

## [1.3.3] - 2026-09-15
- **Enhanced Player Compatibility**: Improved launch detection and priority for custom and alternative YouTube Music installations.
- **Smarter App Routing**: Automatically recognizes your preferred player setup so music opens without delay.
- **General Polish**: Refined package visibility and background link handling.

## [1.3.2] - 2026-09-15
- **Share SongFlip**: Easily share SongFlip with friends and music lovers directly from settings.
- **Smart Link Routing**: Recipients are automatically routed to their device's native app store via universal smart links.
- **Full Localization**: Completely translated across all 24 supported languages.

## [1.3.1] - 2026-09-14
- **Clearer Setup**: Your selected music player is now immediately confirmed as ready in the overview.
- **Polish**: Improved status indicators and an even smoother experience.

## [1.3.0] - 2026-09-13
- **SoundCloud & Bandcamp**: Full support for more music services alongside your favorite streaming players.
- **Instant Shazam Links**: Identified songs open directly and reliably in your preferred music player.
- **Choose Your Music App**: Pick your player on the fly with the optional "Ask every time" setting.
- **Smarter Sharing**: Sharing a song creates a universal smart link for friends instead of looping back.
- **Effortless Voucher Input**: Pasted codes are recognized automatically even from chat messages.
- **Smoother & Faster Playback**: Optimized link handling and enhanced background performance.

## [1.2.30] - 2026-09-13
- **Smarter Voucher Input**: Pasted voucher codes are recognized automatically even when copied within a message, no editing needed.
- **Reliable Music Links**: Improved link handling prevents connection errors and ensures your music opens smoothly.
- **Clearer Visual Feedback**: Interactive buttons now show status indicators while actions are being prepared.

## [1.2.29] - 2026-09-12
- **Choose Your Music App**: Added an optional "Ask every time" setting for anyone using multiple music services, letting you pick where to play each song with a single tap.
- **Seamless Shazam Links**: Songs identified with Shazam now open instantly and reliably in your favorite music player without interruptions.
- **Global Polish**: Quick player selection is fully translated across all 24 supported languages.

## [1.2.28] - 2026-09-12
- **Native Player Share Fix**: Sharing a song from within your music player via the system share menu now generates a universal smart link to share with friends instead of reopening your player.
- **Accurate History Counting**: Resolved a count discrepancy where pre-cached links were counted in Settings before appearing in the history list.
- **Seamless Share Previews**: Shared tracks now seamlessly populate the in-app action banner with instant prefetching.

## [1.2.27] - 2026-09-12
- **Dynamic Theming**: Fixed the monochrome app icon on modern devices to adapt beautifully and crisply to your home screen theme.
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
- **Streamlined Preferences & Dashboard**: Removed the redundant manual link tester in favor of the automatic Clipboard Smart-Banner.
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
