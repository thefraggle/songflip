#!/usr/bin/env node
/**
 * Automated localized screenshot renderer for SongFlip using Puppeteer.
 * Reads HTML templates, replaces placeholders, and renders 1080x2400 Play Store screenshots.
 */

const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer');

const BASE_DIR = __dirname;
const TEMPLATES_DIR = path.join(BASE_DIR, 'assets', 'screenshots');
const OUTPUT_BASE = path.join(path.dirname(BASE_DIR), 'screenshots');

const STRINGS = {
  'de-DE': {
    // Screen 1: Instant Playback
    s1_prefix: '0 Klicks. ',
    s1_accent: 'Direkt Musik.',
    s1_suffix: '',
    s1_subline: 'Öffnet empfangene Links zu Songs & Podcasts sofort in deiner Wunsch-App.',
    s1_chat_sender: 'Lena',
    s1_chat_time: '11:55',
    s1_chat_text: 'Hey! Hör dir mal das Lied an,<br>das ich gerade gefunden habe:',
    s1_chat_link: 'https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT',

    // Screen 2: All Platforms + Podcasts
    s2_prefix: 'Alle Dienste. ',
    s2_accent: 'Songs & Podcasts.',
    s2_suffix: '',
    s2_subline: 'Spotify, YouTube Music, Apple Music, Tidal, Deezer & Amazon Music.',
    s2_app_title: 'SongFlip',
    s2_app_subtitle: 'Automatische Musik-Link-Weiterleitung',
    s2_status_active: 'SongFlip ist aktiv',
    s2_status_pause: 'SongFlip pausieren',
    s2_routing_title: 'Automatische Weiterleitung aktiv',
    s2_routing_desc: 'Musik-Links aus allen unterstützten Streaming-Diensten öffnen sich ab jetzt automatisch im Hintergrund.',
    s2_routing_domains: '45 von 46 Musik-Domains aktiviert',
    s2_target_title: 'Bevorzugter Ziel-Player',
    s2_target_subtitle: 'Wähle den Ziel-Dienst. Installierte Apps öffnen direkt, andere im Web-Browser.',
    s2_installed: 'App installiert',
    s2_browser: 'Web-Browser',

    // Screen 3: Smart Links
    s3_prefix: 'Ein Link für ',
    s3_accent: 'alle Freunde',
    s3_suffix: '.',
    s3_subline: 'Erstelle universelle Web-Links, die überall funktionieren.',

    // Screen 4: Playlists
    s4_prefix: 'Ganze ',
    s4_accent: 'Playlists',
    s4_suffix: ' umwandeln.',
    s4_subline: 'Spotify zu YouTube Music & mehr. Bis zu 50 Songs mit einem Klick.',
    s4_matched: '50 von 50 Titeln gefunden',
    s4_btn_open: 'In YouTube Music öffnen & speichern',
    s4_btn_share: 'Playlist-Link teilen',

    // Screen 5: Privacy
    s5_prefix: '100 % Privat. ',
    s5_accent: 'Zero Tracking.',
    s5_suffix: '',
    s5_subline: 'Kein Konto, keine Werbung und keine Analyse deines Musikgeschmacks.',
    s5_card_title: 'Deine Daten gehören dir.',
    s5_t1_title: 'Kein Benutzerkonto',
    s5_t1_desc: 'Keine Registrierung, keine Passwörter. Sofort einsatzbereit.',
    s5_t2_title: '100 % Werbefrei',
    s5_t2_desc: 'Keine Drittanbieter-Werbung, keine Banner, keine Werbe-Tracker.',
    s5_t3_title: 'Anonym & Sicher',
    s5_t3_desc: 'Song-Links werden ohne Personenbezug aufgelöst. Kein Profiling.',
    s5_card_badge: 'Finanziert durch optionale PRO-Features – nicht durch deine Daten'
  },
  'en-US': {
    // Screen 1: Instant Playback
    s1_prefix: '0 Clicks. ',
    s1_accent: 'Instant Playback.',
    s1_suffix: '',
    s1_subline: 'Opens incoming song & podcast links directly in your preferred player.',
    s1_chat_sender: 'Lena',
    s1_chat_time: '11:55',
    s1_chat_text: 'Hey! Listen to this song,<br>I just found it:',
    s1_chat_link: 'https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT',

    // Screen 2: All Platforms + Podcasts
    s2_prefix: 'All Platforms. ',
    s2_accent: 'Music & Podcasts.',
    s2_suffix: '',
    s2_subline: 'Spotify, YouTube Music, Apple Music, Tidal, Deezer & Amazon Music.',
    s2_app_title: 'SongFlip',
    s2_app_subtitle: 'Automatic Music Link Redirector',
    s2_status_active: 'SongFlip is active',
    s2_status_pause: 'Pause SongFlip',
    s2_routing_title: 'Automatic Link Routing Active',
    s2_routing_desc: 'Music links from all supported streaming services will open automatically in the background.',
    s2_routing_domains: '45 of 46 music domains active',
    s2_target_title: 'Preferred Target Player',
    s2_target_subtitle: 'Select your target service. Installed apps open directly, others in browser.',
    s2_installed: 'App installed',
    s2_browser: 'Web browser',

    // Screen 3: Smart Links
    s3_prefix: 'One Link for ',
    s3_accent: 'Everyone',
    s3_suffix: '.',
    s3_subline: 'Share smart links that work seamlessly across any platform.',

    // Screen 4: Playlists
    s4_prefix: 'Convert Whole ',
    s4_accent: 'Playlists',
    s4_suffix: '.',
    s4_subline: 'Spotify to YouTube Music & more. Up to 50 tracks with one click.',
    s4_matched: '50 of 50 tracks matched',
    s4_btn_open: 'Open & Save in YouTube Music',
    s4_btn_share: 'Share Playlist Link',

    // Screen 5: Privacy
    s5_prefix: '100% Private. ',
    s5_accent: 'Zero Tracking.',
    s5_suffix: '',
    s5_subline: 'No accounts, no third-party ads, no profiling of your listening habits.',
    s5_card_title: 'Your Data Stays Yours.',
    s5_t1_title: 'No Registration',
    s5_t1_desc: 'No accounts, no passwords, no emails. Ready immediately.',
    s5_t2_title: '100% Ad-Free',
    s5_t2_desc: 'No third-party ads, zero banners, no advertising trackers.',
    s5_t3_title: 'Anonymous & Secure',
    s5_t3_desc: 'Links are resolved without personal identity. Zero profiling.',
    s5_card_badge: 'Funded by optional PRO features – never by your data'
  }
};

const SCREENS = [
  { template: '01-instant-playback.html', outName: 'screen_1.png' },
  { template: '02-all-platforms.html', outName: 'screen_2.png' },
  { template: '03-smart-links.html', outName: 'screen_3.png' },
  { template: '04-playlist-conversion.html', outName: 'screen_4.png' },
  { template: '05-privacy.html', outName: 'screen_5.png' }
];

async function renderLocale(locale = 'de-DE') {
  const strings = STRINGS[locale] || STRINGS['de-DE'];
  console.log(`\n=== Rendering 5 Store Screenshots for [${locale}] (1080x2400) ===`);

  const outDir = path.join(OUTPUT_BASE, locale);
  fs.mkdirSync(outDir, { recursive: true });

  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1080, height: 2400, deviceScaleFactor: 1 });

  for (let i = 0; i < SCREENS.length; i++) {
    const item = SCREENS[i];
    const templatePath = path.join(TEMPLATES_DIR, item.template);
    if (!fs.existsSync(templatePath)) {
      console.error(`Template not found: ${templatePath}`);
      continue;
    }

    let html = fs.readFileSync(templatePath, 'utf8');

    // Substitute all {{key}}
    for (const [key, val] of Object.entries(strings)) {
      const regex = new RegExp(`\\{\\{${key}\\}\\}`, 'g');
      html = html.replace(regex, val);
    }

    // Write substituted HTML to temporary file so relative CSS/images resolve perfectly
    const tmpFile = path.join(TEMPLATES_DIR, `_tmp_${item.outName}.html`);
    fs.writeFileSync(tmpFile, html, 'utf8');

    try {
      await page.goto(`file://${tmpFile}`, {
        waitUntil: 'networkidle2',
        timeout: 20000
      });

      // Short wait for local web fonts
      await new Promise((r) => setTimeout(r, 600));

      const outPath = path.join(outDir, item.outName);
      await page.screenshot({
        path: outPath,
        type: 'png',
        clip: { x: 0, y: 0, width: 1080, height: 2400 }
      });

      console.log(`✓ Rendered ${item.outName} -> ${outPath}`);
    } finally {
      if (fs.existsSync(tmpFile)) {
        fs.unlinkSync(tmpFile);
      }
    }
  }

}

module.exports = { STRINGS };

if (require.main === module) {
  const targetLocale = process.argv[2] || 'de-DE';
  renderLocale(targetLocale).catch((err) => {
    console.error('Fatal rendering error:', err);
    process.exit(1);
  });
}
