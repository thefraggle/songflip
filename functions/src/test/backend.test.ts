import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { cleanSearchQuery, normalizeMusicUrl, isRateLimited } from "../index";

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
});
