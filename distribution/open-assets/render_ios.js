#!/usr/bin/env node
/**
 * Localized iOS App Store Screenshot Renderer for SongFlip using Puppeteer.
 * Renders high-resolution 1290x2796 App Store screenshots with Dynamic Island mockups.
 */

const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer');

const BASE_DIR = __dirname;
const TEMPLATES_DIR = path.join(BASE_DIR, 'assets', 'screenshots-ios');
const OUTPUT_DIR = path.join(path.dirname(BASE_DIR), 'screenshots-ios', 'de-DE');

const LOCALES = {
  'de-DE': {
    // Screen 1: Hero Playback
    s1_prefix: '1 Klick. ',
    s1_accent: 'Direkt Musik.',
    s1_suffix: '',
    s1_subline: 'Öffnet empfangene Links zu Songs & Podcasts sofort in deiner Wunsch-App.',
    s1_chat_sender: 'Lena',
    s1_chat_time: '11:55',
    s1_chat_text: 'Hey! Hör dir mal das Lied an,<br>das ich gerade gefunden habe:',
    s1_chat_link: 'https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT',

    // Screen 2: All Platforms (iOS App UI)
    s2_prefix: 'Wähle deinen ',
    s2_accent: 'Wunsch-Player',
    s2_suffix: '.',
    s2_subline: 'Spotify, Apple Music, YouTube Music, Tidal, Deezer & Amazon Music.',
    s2_app_title: 'SongFlip',
    s2_app_subtitle: 'Automatische Musik-Link-Weiterleitung',
    s2_target_title: 'BEVORZUGTER ZIEL-PLAYER',
    s2_target_badge: '1-Tap Auswahl',
    s2_guide_title: 'SO FUNKTIONIERT ES AUF IOS',
    s2_g1_title: '1. Über das Teilen-Menü',
    s2_g1_desc: 'Tippe auf Teilen in Spotify, Apple Music oder YouTube → SongFlip für sofortige Wiedergabe.',
    s2_g2_title: '2. Action Button & Kurzbefehle',
    s2_g2_desc: 'Lege den SongFlip-Kurzbefehl auf deinen Action Button für 1-Klick-Weiterleitung.',
    s2_g3_title: '3. Automatische Zwischenablage',
    s2_g3_desc: 'Kopierte Musik-Links werden beim Öffnen der App automatisch erkannt.',
    s2_footer_version: 'SongFlip v1.7.1',
    s2_footer_copyright: '© 2026 Daniel Notthoff',
    s2_footer_links: 'Datenschutz  •  Impressum  •  AGB',

    // Screen 3: Smart Links
    s3_prefix: 'Ein Link für ',
    s3_accent: 'alle Freunde',
    s3_suffix: '.',
    s3_subline: 'Erstelle universelle Web-Links, die in jedem Player funktionieren.',

    // Screen 4: Privacy
    s4_prefix: '100 % Privat. ',
    s4_accent: 'Zero Tracking.',
    s4_suffix: '',
    s4_subline: 'Kein Konto, keine Werbung und keine Analyse deines Musikgeschmacks.',
    s4_card_title: 'Deine Daten gehören dir.',
    s4_t1_title: 'Kein Benutzerkonto',
    s4_t1_desc: 'Keine Registrierung, keine Passwörter. Sofort einsatzbereit.',
    s4_t2_title: '100 % Werbefrei',
    s4_t2_desc: 'Keine Drittanbieter-Werbung, keine Banner, keine Werbe-Tracker.',
    s4_t3_title: 'Anonym & Sicher',
    s4_t3_desc: 'Song-Links werden ohne Personenbezug aufgelöst. Kein Profiling.',
    s4_card_badge: 'Finanziert durch optionale PRO-Features – nicht durch deine Daten'
  },
  'en-US': {
    // Screen 1: Hero Playback
    s1_prefix: '1 Click. ',
    s1_accent: 'Instant Playback.',
    s1_suffix: '',
    s1_subline: 'Opens incoming song & podcast links instantly in your preferred player.',
    s1_chat_sender: 'Sarah',
    s1_chat_time: '11:55',
    s1_chat_text: 'Hey! Check out this song<br>I just discovered:',
    s1_chat_link: 'https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT',

    // Screen 2: All Platforms (iOS App UI)
    s2_prefix: 'Choose Your ',
    s2_accent: 'Preferred Player',
    s2_suffix: '.',
    s2_subline: 'Spotify, Apple Music, YouTube Music, Tidal, Deezer & Amazon Music.',
    s2_app_title: 'SongFlip',
    s2_app_subtitle: 'Automatic Music Link Redirect',
    s2_target_title: 'PREFERRED TARGET PLAYER',
    s2_target_badge: '1-Tap Select',
    s2_guide_title: 'HOW IT WORKS ON IOS',
    s2_g1_title: '1. Via Share Sheet',
    s2_g1_desc: 'Tap Share in Spotify, Apple Music, or YouTube → SongFlip for instant playback.',
    s2_g2_title: '2. Action Button & Shortcuts',
    s2_g2_desc: 'Assign the SongFlip shortcut to your Action Button for 1-click redirects.',
    s2_g3_title: '3. Automatic Clipboard Detection',
    s2_g3_desc: 'Copied music links are automatically detected when you open the app.',
    s2_footer_version: 'SongFlip v1.7.1',
    s2_footer_copyright: '© 2026 Daniel Notthoff',
    s2_footer_links: 'Privacy Policy  •  Legal Notice  •  Terms',

    // Screen 3: Smart Links
    s3_prefix: 'One Link for ',
    s3_accent: 'All Friends',
    s3_suffix: '.',
    s3_subline: 'Create universal smart links that open smoothly in any music player.',

    // Screen 4: Privacy
    s4_prefix: '100% Private. ',
    s4_accent: 'Zero Tracking.',
    s4_suffix: '',
    s4_subline: 'No account, no ads, and no profiling of your listening habits.',
    s4_card_title: 'Your data stays yours.',
    s4_t1_title: 'No User Account',
    s4_t1_desc: 'No registration, no passwords. Ready to use immediately.',
    s4_t2_title: '100% Ad-Free',
    s4_t2_desc: 'No third-party ads, no banners, no tracking pixels.',
    s4_t3_title: 'Anonymous & Secure',
    s4_t3_desc: 'Song links are resolved anonymously. Zero user tracking or profiling.',
    s4_card_badge: 'Funded by optional PRO features – not your personal data'
  }
};

const SCREENS = [
  { file: '02-all-platforms.html', name: '01_all_platforms.png' },
  { file: '01-instant-playback.html', name: '02_hero_playback.png' },
  { file: '03-smart-links.html', name: '03_smart_links.png' },
  { file: '04-privacy.html', name: '04_privacy.png' }
];

async function render() {
  const targetLocales = process.argv.slice(2).length > 0 
    ? process.argv.slice(2) 
    : ['de-DE', 'en-US'];

  console.log(`🚀 Rendering iOS App Store Screenshots (1290x2796) for: ${targetLocales.join(', ')}...`);

  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  await page.setViewport({
    width: 1290,
    height: 2796,
    deviceScaleFactor: 1
  });

  for (const locale of targetLocales) {
    const strings = LOCALES[locale];
    if (!strings) {
      console.warn(`⚠️ Warning: Unknown locale "${locale}", skipping.`);
      continue;
    }

    const outputDir = path.join(path.dirname(BASE_DIR), 'screenshots-ios', locale);
    if (!fs.existsSync(outputDir)) {
      fs.mkdirSync(outputDir, { recursive: true });
    }

    console.log(`\n📸 Generating [${locale}]...`);

    for (const s of SCREENS) {
      const templatePath = path.join(TEMPLATES_DIR, s.file);
      let html = fs.readFileSync(templatePath, 'utf8');

      // Replace all placeholders
      for (const [key, val] of Object.entries(strings)) {
        html = html.replace(new RegExp(`\\{\\{${key}\\}\\}`, 'g'), val);
      }

      const tmpFile = path.join(TEMPLATES_DIR, `_tmp_${locale}_${s.name}.html`);
      fs.writeFileSync(tmpFile, html, 'utf8');

      try {
        await page.goto(`file://${tmpFile}`, { waitUntil: 'networkidle2', timeout: 25000 });
        await new Promise(r => setTimeout(r, 400));

        const dir67 = path.join(outputDir, '6.7-inch');
        const dir65 = path.join(outputDir, '6.5-inch');
        if (!fs.existsSync(dir67)) fs.mkdirSync(dir67, { recursive: true });
        if (!fs.existsSync(dir65)) fs.mkdirSync(dir65, { recursive: true });

        const path67 = path.join(dir67, s.name);
        await page.screenshot({
          path: path67,
          type: 'png',
          clip: { x: 0, y: 0, width: 1290, height: 2796 }
        });

        // 6.5-inch required by App Store Connect: 1284 x 2778 px
        const path65 = path.join(dir65, s.name);
        const rootPath = path.join(outputDir, s.name);
        const { execSync } = require('child_process');
        execSync(`sips -z 2778 1284 "${path67}" --out "${path65}"`, { stdio: 'pipe' });
        fs.copyFileSync(path65, rootPath);

        console.log(`  ✅ Saved: ${s.name} (6.7" & 6.5" for ${locale})`);
      } finally {
        if (fs.existsSync(tmpFile)) {
          fs.unlinkSync(tmpFile);
        }
      }
    }
  }

  await browser.close();
  console.log('\n🎉 Done! All iOS screenshots successfully generated.');
}

render().catch(err => {
  console.error('❌ Error rendering iOS screenshots:', err);
  process.exit(1);
});

