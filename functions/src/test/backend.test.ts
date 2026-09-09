import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { cleanSearchQuery, normalizeMusicUrl, isRateLimited, getWebShareI18n } from "../index";

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
      assert.equal(i18n.linkCopied, "Link in Zwischenablage kopiert!");
      assert.equal(i18n.play, "Abspielen");
      assert.equal(i18n.flippedWith, "Geflippt mit");
    });

    it("should return French translations for fr-FR", () => {
      const i18n = getWebShareI18n("fr-FR,fr;q=0.9");
      assert.equal(i18n.lang, "fr");
      assert.equal(i18n.shareLink, "Partager le lien");
      assert.equal(i18n.linkCopied, "Lien copié dans le presse-papiers !");
      assert.equal(i18n.play, "Écouter");
    });

    it("should return Japanese translations for ja", () => {
      const i18n = getWebShareI18n("ja-JP");
      assert.equal(i18n.lang, "ja");
      assert.equal(i18n.shareLink, "リンクを共有");
      assert.equal(i18n.play, "再生");
    });

    it("should fallback to English for unknown languages or missing header", () => {
      const i18nEmpty = getWebShareI18n(undefined);
      assert.equal(i18nEmpty.lang, "en");
      assert.equal(i18nEmpty.shareLink, "Share Link");
      assert.equal(i18nEmpty.play, "Play");

      const i18nUnknown = getWebShareI18n("xx-YY");
      assert.equal(i18nUnknown.lang, "en");
      assert.equal(i18nUnknown.shareLink, "Share Link");
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
        assert.ok(i18n.linkCopied && i18n.linkCopied.length > 0, `linkCopied missing for ${lang}`);
        assert.ok(i18n.play && i18n.play.length > 0, `play missing for ${lang}`);
        assert.ok(i18n.notFoundTitle && i18n.notFoundTitle.length > 0, `notFoundTitle missing for ${lang}`);
        assert.ok(i18n.discoverSongFlip && i18n.discoverSongFlip.length > 0, `discoverSongFlip missing for ${lang}`);
      });
    });
  });
});
