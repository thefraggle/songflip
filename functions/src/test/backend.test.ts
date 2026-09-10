import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  cleanSearchQuery,
  normalizeMusicUrl,
  isRateLimited,
  recordFailedAttempt,
  isBlockedDueToFailures,
  getWebShareI18n,
  hashUrl,
  isValidBandcampUrl,
  isSafePublicHttpsUrl,
  createSignedCouponToken,
  verifySignedCouponToken,
  isSecureMatch,
  isArtistUrl,
  isPlaylistUrl,
  detectPlatformFromUrl,
} from "../index";

describe("Backend Helper Tests", () => {

  describe("cleanSearchQuery", () => {
    it("should remove remastered tags", () => {
      assert.equal(cleanSearchQuery("Bohemian Rhapsody - 2011 Remaster"), "Bohemian Rhapsody");
      assert.equal(cleanSearchQuery("Hotel California (Remastered 2013)"), "Hotel California");
      assert.equal(cleanSearchQuery("In The End (Remastered)"), "In The End");
    });

    it("should remove version and edit tags", () => {
      assert.equal(cleanSearchQuery("Billie Jean [Single Version]"), "Billie Jean");
      assert.equal(cleanSearchQuery("Numb (Album Version)"), "Numb");
      assert.equal(cleanSearchQuery("Du Hast (Radio Edit)"), "Du Hast");
    });

    it("should remove live concert suffixes", () => {
      assert.equal(cleanSearchQuery("Letzter Tanz (Live at Rock am Ring)"), "Letzter Tanz");
      assert.equal(cleanSearchQuery("Master of Puppets - Live"), "Master of Puppets");
    });

    it("should preserve clean titles unchanged", () => {
      assert.equal(cleanSearchQuery("Wenn du dumm bist"), "Wenn du dumm bist");
      assert.equal(cleanSearchQuery("Stairway to Heaven"), "Stairway to Heaven");
    });
  });

  describe("normalizeMusicUrl", () => {
    it("should normalize Spotify URLs and strip tracking", () => {
      const dirty = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv?si=abc12345&utm_source=whatsapp";
      assert.equal(normalizeMusicUrl(dirty), "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv");

      const intl = "https://open.spotify.com/intl-de/album/12345?si=abc";
      assert.equal(normalizeMusicUrl(intl), "https://open.spotify.com/album/12345");
    });

    it("should preserve Apple Music track ID (?i=)", () => {
      const dirty = "https://music.apple.com/de/album/letzter-tanz/1761769878?i=1761770183&uo=4";
      assert.equal(normalizeMusicUrl(dirty), "https://music.apple.com/de/album/letzter-tanz/1761769878?i=1761770183");

      const album = "https://music.apple.com/de/album/bohemian-rhapsody/1440650428?uo=4";
      assert.equal(normalizeMusicUrl(album), "https://music.apple.com/de/album/bohemian-rhapsody/1440650428");
    });

    it("should normalize Deezer URLs", () => {
      const deezer = "https://www.deezer.com/de/track/3506388961?utm_source=test";
      assert.equal(normalizeMusicUrl(deezer), "https://www.deezer.com/track/3506388961");
    });

    it("should normalize SoundCloud URLs", () => {
      const soundcloud = "https://soundcloud.com/octobersveryown/drake-gods-plan?si=12345&utm_source=clipboard";
      assert.equal(normalizeMusicUrl(soundcloud), "https://soundcloud.com/octobersveryown/drake-gods-plan");
    });

    it("should normalize Bandcamp URLs", () => {
      const bandcamp = "https://radiohead.bandcamp.com/track/reckoner?from=search";
      assert.equal(normalizeMusicUrl(bandcamp), "https://radiohead.bandcamp.com/track/reckoner");

      const album = "https://artist.bandcamp.com/album/greatest-hits?from=share";
      assert.equal(normalizeMusicUrl(album), "https://artist.bandcamp.com/album/greatest-hits");
    });
  });

  describe("isRateLimited", () => {
    it("should exempt localhost IPs", () => {
      assert.equal(isRateLimited("127.0.0.1", 1), false);
      assert.equal(isRateLimited("127.0.0.1", 1), false);
      assert.equal(isRateLimited("::1", 1), false);
    });

    it("should block requests when limit is exceeded", () => {
      const ip = "192.168.1.100";
      assert.equal(isRateLimited(ip, 2, 60000), false); // 1st request
      assert.equal(isRateLimited(ip, 2, 60000), false); // 2nd request
      assert.equal(isRateLimited(ip, 2, 60000), true);  // 3rd request blocked
    });
  });

  describe("getWebShareI18n", () => {
    it("should return German translations for de-DE", () => {
      const i18n = getWebShareI18n("de-DE,de;q=0.9,en;q=0.8");
      assert.equal(i18n.lang, "de");
      assert.equal(i18n.shareLink, "Link teilen");
      assert.equal(i18n.copyLink, "Link kopieren");
      assert.equal(i18n.linkCopied, "Link in Zwischenablage kopiert!");
      assert.equal(i18n.play, "Abspielen");
      assert.equal(i18n.flippedWith, "Geflippt mit");
    });

    it("should return French translations for fr-FR", () => {
      const i18n = getWebShareI18n("fr-FR,fr;q=0.9");
      assert.equal(i18n.lang, "fr");
      assert.equal(i18n.shareLink, "Partager le lien");
      assert.equal(i18n.copyLink, "Copier le lien");
      assert.equal(i18n.linkCopied, "Lien copié dans le presse-papiers !");
      assert.equal(i18n.play, "Écouter");
    });

    it("should return Japanese translations for ja", () => {
      const i18n = getWebShareI18n("ja-JP");
      assert.equal(i18n.lang, "ja");
      assert.equal(i18n.shareLink, "リンクを共有");
      assert.equal(i18n.copyLink, "リンクをコピー");
      assert.equal(i18n.play, "再生");
    });

    it("should fallback to English for unknown languages or missing header", () => {
      const i18nEmpty = getWebShareI18n(undefined);
      assert.equal(i18nEmpty.lang, "en");
      assert.equal(i18nEmpty.shareLink, "Share Link");
      assert.equal(i18nEmpty.copyLink, "Copy Link");
      assert.equal(i18nEmpty.play, "Play");

      const i18nUnknown = getWebShareI18n("xx-YY");
      assert.equal(i18nUnknown.lang, "en");
      assert.equal(i18nUnknown.shareLink, "Share Link");
      assert.equal(i18nUnknown.copyLink, "Copy Link");
    });

    it("should cover all 24 supported languages with complete translations", () => {
      const languages = [
        "de", "en", "es", "fr", "it", "pt", "nl", "pl", "da", "nb",
        "no", "sv", "ru", "uk", "tr", "ja", "ko", "zh", "id", "in",
        "vi", "hi", "bn", "mr"
      ];
      languages.forEach((lang) => {
        const i18n = getWebShareI18n(lang);
        assert.ok(i18n.shareLink && i18n.shareLink.length > 0, `shareLink missing for ${lang}`);
        assert.ok(i18n.copyLink && i18n.copyLink.length > 0, `copyLink missing for ${lang}`);
        assert.ok(i18n.linkCopied && i18n.linkCopied.length > 0, `linkCopied missing for ${lang}`);
        assert.ok(i18n.play && i18n.play.length > 0, `play missing for ${lang}`);
        assert.ok(i18n.notFoundTitle && i18n.notFoundTitle.length > 0, `notFoundTitle missing for ${lang}`);
        assert.ok(i18n.discoverSongFlip && i18n.discoverSongFlip.length > 0, `discoverSongFlip missing for ${lang}`);
      });
    });
  });

  describe("Short Hash Generation (12 Hex Characters)", () => {
    it("should produce a 12-character short hash with 48 bits entropy", () => {
      const url = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv";
      const fullHash = hashUrl(url);
      const shortHash = fullHash.substring(0, 12);

      assert.equal(fullHash.length, 64);
      assert.equal(shortHash.length, 12);
      assert.match(shortHash, /^[0-9a-f]{12}$/);
    });

    it("should prevent collisions across distinct songs", () => {
      const hashes = new Set<string>();
      for (let i = 0; i < 1000; i++) {
        const url = `https://music.apple.com/album/song-${i}/123456?i=789${i}`;
        const short = hashUrl(url).substring(0, 12);
        assert.equal(hashes.has(short), false, `Collision detected on item ${i}: ${short}`);
        hashes.add(short);
      }
      assert.equal(hashes.size, 1000);
    });
  });

  describe("SSRF & URL Validation", () => {
    it("should accept valid Bandcamp HTTPS URLs", () => {
      assert.equal(isValidBandcampUrl("https://radiohead.bandcamp.com/track/reckoner"), true);
      assert.equal(isValidBandcampUrl("https://artist-name.bandcamp.com/album/greatest-hits"), true);
      assert.equal(isValidBandcampUrl("https://bandcamp.com/track/hello"), true);
      assert.equal(isValidBandcampUrl("https://sub.label.bandcamp.com/album/lp1"), true);
    });

    it("should reject non-HTTPS Bandcamp URLs", () => {
      assert.equal(isValidBandcampUrl("http://radiohead.bandcamp.com/track/reckoner"), false);
      assert.equal(isValidBandcampUrl("ftp://radiohead.bandcamp.com/track/reckoner"), false);
      assert.equal(isValidBandcampUrl("file:///etc/passwd"), false);
    });

    it("should reject SSRF attacks targeting Cloud Metadata and Private IPs", () => {
      assert.equal(isValidBandcampUrl("https://169.254.169.254/latest/meta-data/"), false);
      assert.equal(isValidBandcampUrl("https://127.0.0.1/admin"), false);
      assert.equal(isValidBandcampUrl("https://10.0.0.1/secrets"), false);
      assert.equal(isValidBandcampUrl("https://192.168.1.1/internal"), false);
      assert.equal(isValidBandcampUrl("https://172.16.0.1/config"), false);
      assert.equal(isValidBandcampUrl("https://localhost/bandcamp.com"), false);
    });

    it("should reject domain spoofing and tricky hosts", () => {
      assert.equal(isValidBandcampUrl("https://evil-bandcamp.com/track/123"), false);
      assert.equal(isValidBandcampUrl("https://bandcamp.com.attacker.com/track/123"), false);
      assert.equal(isValidBandcampUrl("https://attacker.com/bandcamp.com"), false);
      assert.equal(isValidBandcampUrl("https://notbandcamp.com"), false);
    });

    it("should reject embedded credentials and non-standard ports", () => {
      assert.equal(isValidBandcampUrl("https://user:pass@artist.bandcamp.com/track/1"), false);
      assert.equal(isValidBandcampUrl("https://artist.bandcamp.com:8080/track/1"), false);
    });

    it("should validate general safe public HTTPS URLs", () => {
      assert.equal(isSafePublicHttpsUrl("https://open.spotify.com/track/123"), true);
      assert.equal(isSafePublicHttpsUrl("https://music.apple.com/album/1"), true);
      assert.equal(isSafePublicHttpsUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"), true);
      assert.equal(isSafePublicHttpsUrl("https://music.youtube.com/watch?v=123"), true);
      assert.equal(isSafePublicHttpsUrl("https://www.deezer.com/track/123"), true);
      assert.equal(isSafePublicHttpsUrl("https://listen.tidal.com/track/123"), true);
      assert.equal(isSafePublicHttpsUrl("https://music.amazon.de/albums/B0123"), true);
      assert.equal(isSafePublicHttpsUrl("https://soundcloud.com/artist/track"), true);
      assert.equal(isSafePublicHttpsUrl("https://song.link/s/123"), true);

      // Rejections
      assert.equal(isSafePublicHttpsUrl("http://open.spotify.com/track/123"), false);
      assert.equal(isSafePublicHttpsUrl("https://169.254.169.254/"), false);
      assert.equal(isSafePublicHttpsUrl("https://127.0.0.1/"), false);
      assert.equal(isSafePublicHttpsUrl("https://localhost/test"), false);
      assert.equal(isSafePublicHttpsUrl("https://admin:pass@example.com/"), false);
      assert.equal(isSafePublicHttpsUrl("https://metadata.google.internal/computeMetadata/v1/"), false);
      assert.equal(isSafePublicHttpsUrl("https://metadata/"), false);
      assert.equal(isSafePublicHttpsUrl("https://server.local/"), false);
      assert.equal(isSafePublicHttpsUrl("https://service.internal/"), false);
      assert.equal(isSafePublicHttpsUrl("https://attacker.com/song"), false);
      assert.equal(isSafePublicHttpsUrl("https://evil-spoof.org/track/1"), false);
    });
  });

  describe("Artist URL Host Spoofing Guard", () => {
    it("should accept authentic YouTube and Spotify artist URLs", () => {
      assert.equal(isArtistUrl("https://www.youtube.com/@Radiohead"), true);
      assert.equal(isArtistUrl("https://music.youtube.com/channel/UC1234567890123456789012"), true);
      assert.equal(isArtistUrl("https://open.spotify.com/artist/4Z8W4fKeB5YxbusRsdQVPb"), true);
    });

    it("should reject SSRF spoofing targeting external hosts disguised as YouTube", () => {
      assert.equal(isArtistUrl("https://attacker.com/@Radiohead?query=youtube.com"), false);
      assert.equal(isArtistUrl("https://attacker.com/channel/UC123?youtube.com"), false);
      assert.equal(isArtistUrl("https://evil.org/user/test?youtu.be"), false);
    });
  });

  describe("Timing-Safe Secret Matching", () => {
    it("should match identical secrets securely", () => {
      assert.equal(isSecureMatch("super-secret-key-1234", "super-secret-key-1234"), true);
    });

    it("should reject non-matching secrets", () => {
      assert.equal(isSecureMatch("super-secret-key-1234", "wrong-secret-key-5678"), false);
      assert.equal(isSecureMatch("short", "longer-secret-key"), false);
      assert.equal(isSecureMatch("", "secret"), false);
      assert.equal(isSecureMatch(undefined, "secret"), false);
      assert.equal(isSecureMatch(null as any, "secret"), false);
    });
  });

  describe("Input Format Validation (Promo Codes & Install IDs)", () => {
    const installIdRegex = /^[a-zA-Z0-9_.-]{8,128}$/;
    const promoCodeRegex = /^[A-Z0-9_-]{3,64}$/i;

    it("should validate safe installIds", () => {
      assert.ok(installIdRegex.test("install_12345678"));
      assert.ok(installIdRegex.test("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));
      assert.ok(installIdRegex.test("device.id.12345678"));
    });

    it("should reject invalid/dangerous installIds", () => {
      assert.equal(installIdRegex.test("short"), false); // < 8 chars
      assert.equal(installIdRegex.test("user/12345678"), false); // path traversal slash
      assert.equal(installIdRegex.test("user 12345678"), false); // spaces
      assert.equal(installIdRegex.test("../../../etc/passwd"), false);
      assert.equal(installIdRegex.test(""), false);
    });

    it("should validate safe promo codes", () => {
      assert.ok(promoCodeRegex.test("SONGFLIP_BETA_2026"));
      assert.ok(promoCodeRegex.test("VIP-2026"));
      assert.ok(promoCodeRegex.test("CODE123"));
    });

    it("should reject invalid/dangerous promo codes", () => {
      assert.equal(promoCodeRegex.test("NO"), false); // < 3 chars
      assert.equal(promoCodeRegex.test("CODE/SLASH"), false); // slash
      assert.equal(promoCodeRegex.test("CODE WITH SPACES"), false);
      assert.equal(promoCodeRegex.test("CODE$#@!"), false);
    });
  });

  describe("HMAC Signed Coupon Tokens", () => {
    it("should generate and verify valid signed coupon tokens", () => {
      const exp = Date.now() + 1000 * 60 * 60 * 24 * 30; // 30 days
      const token = createSignedCouponToken("TEST_CODE_2026", "install_12345678", exp, "1month");
      assert.ok(token.startsWith("sct_"), "Token should have sct_ prefix");

      const result = verifySignedCouponToken(token);
      assert.equal(result.valid, true);
      assert.equal(result.code, "TEST_CODE_2026");
      assert.equal(result.exp, exp);
    });

    it("should reject expired coupon tokens", () => {
      const pastExp = Date.now() - 1000; // expired 1s ago
      const expiredToken = createSignedCouponToken("OLD_CODE", "install_12345678", pastExp, "1month");
      const result = verifySignedCouponToken(expiredToken);
      assert.equal(result.valid, false);
    });

    it("should reject tampered tokens", () => {
      const token = createSignedCouponToken("CODE", "install_12345678", null, "lifetime");
      const parts = token.substring(4).split(".");
      const tampered = `sct_${parts[0]}.wrongsignature1234567890abcdef`;
      assert.equal(verifySignedCouponToken(tampered).valid, false);
    });

    it("should reject arbitrary or malformed strings", () => {
      assert.equal(verifySignedCouponToken("").valid, false);
      assert.equal(verifySignedCouponToken("coupon:fake").valid, false);
      assert.equal(verifySignedCouponToken("sct_invalidpayload.invalidsig").valid, false);
      assert.equal(verifySignedCouponToken("sct_notenoughparts").valid, false);
    });
  });

  describe("Failed Attempts Brute-Force Throttling", () => {
    it("should block after reaching maxFailures threshold", () => {
      const key = "test_promo_throttle_ip_1";
      assert.equal(isBlockedDueToFailures(key, 3), false);

      assert.equal(recordFailedAttempt(key, 3, 60000), false); // 1st
      assert.equal(isBlockedDueToFailures(key, 3), false);

      assert.equal(recordFailedAttempt(key, 3, 60000), false); // 2nd
      assert.equal(isBlockedDueToFailures(key, 3), false);

      assert.equal(recordFailedAttempt(key, 3, 60000), true);  // 3rd (reaches maxFailures)
      assert.equal(isBlockedDueToFailures(key, 3), true);      // Now blocked
    });
  });

  describe("Playlist URL & Platform Detection", () => {
    it("should accurately detect playlist URLs across major platforms", () => {
      assert.equal(isPlaylistUrl("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"), true);
      assert.equal(isPlaylistUrl("https://open.spotify.com/intl-de/playlist/37i9dQZF1DXcBWIGoYBM5M"), true);
      assert.equal(isPlaylistUrl("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"), true);
      assert.equal(isPlaylistUrl("https://music.apple.com/us/playlist/today-hits/pl.f4d106fed2bd41149aaacabb233eb5eb"), true);
      assert.equal(isPlaylistUrl("https://www.youtube.com/playlist?list=PL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i"), true);
      assert.equal(isPlaylistUrl("https://music.youtube.com/playlist?list=RDCLAK5uy_kfdjh"), true);
      assert.equal(isPlaylistUrl("https://www.deezer.com/en/playlist/908622995"), true);
      assert.equal(isPlaylistUrl("https://tidal.com/browse/playlist/5c868037-4bf6-4b2a-b605-7790b9687a70"), true);
      assert.equal(isPlaylistUrl("https://music.amazon.com/playlists/B073HDFD2G"), true);
      assert.equal(isPlaylistUrl("https://soundcloud.com/user-12345/sets/summer-vibes"), true);
    });

    it("should NOT classify single tracks or watch links as playlists", () => {
      assert.equal(isPlaylistUrl("https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"), false);
      assert.equal(isPlaylistUrl("https://music.apple.com/us/album/song-name/123456?i=654321"), false);
      assert.equal(isPlaylistUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"), false);
      // YouTube watch URLs with &list= param are single-track watches inside a playlist, NOT pure playlists
      assert.equal(isPlaylistUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i"), false);
      assert.equal(isPlaylistUrl("https://www.deezer.com/track/1234567"), false);
      assert.equal(isPlaylistUrl("https://tidal.com/browse/track/1234567"), false);
      assert.equal(isPlaylistUrl("https://soundcloud.com/octobersveryown/drake-gods-plan"), false);
    });

    it("should detect platform names correctly from URLs", () => {
      assert.equal(detectPlatformFromUrl("https://open.spotify.com/playlist/123"), "Spotify");
      assert.equal(detectPlatformFromUrl("spotify:playlist:123"), "Spotify");
      assert.equal(detectPlatformFromUrl("https://music.apple.com/us/playlist/123"), "Apple Music");
      assert.equal(detectPlatformFromUrl("https://youtube.com/playlist?list=123"), "YouTube");
      assert.equal(detectPlatformFromUrl("https://music.youtube.com/playlist?list=123"), "YouTube");
      assert.equal(detectPlatformFromUrl("https://www.deezer.com/playlist/123"), "Deezer");
      assert.equal(detectPlatformFromUrl("https://tidal.com/browse/playlist/123"), "Tidal");
      assert.equal(detectPlatformFromUrl("https://music.amazon.com/playlists/123"), "Amazon Music");
      assert.equal(detectPlatformFromUrl("https://soundcloud.com/user/sets/123"), "SoundCloud");
      assert.equal(detectPlatformFromUrl("https://artist.bandcamp.com/album/test"), "Bandcamp");
    });
  });
});
