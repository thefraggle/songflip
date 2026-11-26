#!/usr/bin/env node
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const PACKAGE_NAME = process.env.PLAY_PACKAGE_NAME || 'de.goork.songflip';
const METADATA_DIR = path.resolve(SCRIPT_DIR, '..', 'fastlane', 'metadata', 'android');
const GRAPHICS_DIR = path.resolve(SCRIPT_DIR, '..', 'distribution', 'screenshots');
const FEATURE_GRAPHICS_DIR = path.resolve(SCRIPT_DIR, '..', 'distribution', 'open-assets', 'exports', 'feature-graphics');

// Active Google Play Store listing experiments break immediately if graphics are modified.
const AB_TEST_ACTIVE_LOCALES = new Set(['es-419', 'en-US', 'es-ES', 'de-DE', 'pt-BR']);

// Directory alias fallbacks (local fastlane folder -> canonical Play Store language code)
const SKIP_DUPLICATE_ALIAS_DIRS = new Set(['bn-IN', 'id-ID', 'no-NO', 'ro', 'th', 'uk-UA', 'vi-VN']);

const LIMITS = {
  title: 30,
  shortDescription: 80,
  fullDescription: 4000,
};

function utf16Len(str) {
  return Buffer.byteLength(str, 'utf16le') / 2;
}

function readLocalPlayListings() {
  if (!fs.existsSync(METADATA_DIR)) {
    throw new Error(`Missing fastlane metadata directory: ${METADATA_DIR}`);
  }
  const locales = fs
    .readdirSync(METADATA_DIR, { withFileTypes: true })
    .filter((d) => d.isDirectory() && !SKIP_DUPLICATE_ALIAS_DIRS.has(d.name))
    .map((d) => d.name)
    .sort();

  const listings = {};
  for (const locale of locales) {
    const dir = path.join(METADATA_DIR, locale);
    const titlePath = path.join(dir, 'title.txt');
    const shortPath = path.join(dir, 'short_description.txt');
    const fullPath = path.join(dir, 'full_description.txt');

    if (!fs.existsSync(titlePath) || !fs.existsSync(shortPath) || !fs.existsSync(fullPath)) {
      continue;
    }

    const title = fs.readFileSync(titlePath, 'utf8').trim();
    const shortDescription = fs.readFileSync(shortPath, 'utf8').trim();
    const fullDescription = fs.readFileSync(fullPath, 'utf8').trim();

    if (utf16Len(title) > LIMITS.title) {
      throw new Error(`[${locale}] title exceeds ${LIMITS.title} UTF-16 units (${utf16Len(title)})`);
    }
    if (utf16Len(shortDescription) > LIMITS.shortDescription) {
      throw new Error(`[${locale}] short_description exceeds ${LIMITS.shortDescription} UTF-16 units (${utf16Len(shortDescription)})`);
    }
    if (utf16Len(fullDescription) > LIMITS.fullDescription) {
      throw new Error(`[${locale}] full_description exceeds ${LIMITS.fullDescription} UTF-16 units (${utf16Len(fullDescription)})`);
    }

    listings[locale] = { title, shortDescription, fullDescription };
  }
  return listings;
}

async function getGoogleAccessToken(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const b64u = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');
  const header = b64u({ alg: 'RS256', typ: 'JWT' });
  const claim = b64u({
    iss: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  });
  const signingInput = `${header}.${claim}`;
  const signature = crypto
    .sign('RSA-SHA256', Buffer.from(signingInput), serviceAccount.private_key)
    .toString('base64url');

  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: `${signingInput}.${signature}`,
    }),
  });
  const data = await res.json();
  if (!res.ok || !data.access_token) {
    throw new Error(`Google OAuth token exchange failed (${res.status}): ${JSON.stringify(data)}`);
  }
  return data.access_token;
}

async function playApi(token, method, url, { body, contentType, isBinary } = {}) {
  const headers = { Authorization: `Bearer ${token}` };
  if (contentType) headers['Content-Type'] = contentType;
  else if (body && !isBinary) headers['Content-Type'] = 'application/json';

  const res = await fetch(url, {
    method,
    headers,
    body: isBinary ? body : body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) {
    const err = new Error(`Google Play API ${method} ${url} failed (${res.status}): ${text}`);
    err.status = res.status;
    err.responseText = text;
    throw err;
  }
  return text ? JSON.parse(text) : {};
}

async function uploadImageGroup(token, baseUrl, editId, locale, imageType, filePaths) {
  const existingUrl = `${baseUrl}/edits/${editId}/listings/${locale}/${imageType}`;
  try {
    await playApi(token, 'DELETE', existingUrl);
  } catch (err) {
    if (err.status !== 404) {
      console.warn(`Notice: clearing ${locale}/${imageType} returned ${err.message}`);
    }
  }

  for (const filePath of filePaths) {
    const buf = fs.readFileSync(filePath);
    await playApi(
      token,
      'POST',
      `https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${encodeURIComponent(PACKAGE_NAME)}/edits/${editId}/listings/${locale}/${imageType}?uploadType=media`,
      { body: buf, contentType: 'image/png', isBinary: true },
    );
    console.log(`Uploaded ${locale} ${imageType}: ${path.basename(filePath)}`);
  }
}

async function commitEdit(token, baseUrl, editId) {
  try {
    await playApi(token, 'POST', `${baseUrl}/edits/${editId}:commit`);
    console.log(`Committed Google Play edit ${editId} cleanly.`);
  } catch (err) {
    if (err.status === 400 && String(err.responseText || '').includes('changesNotSentForReview')) {
      await playApi(token, 'POST', `${baseUrl}/edits/${editId}:commit?changesNotSentForReview=true`);
      console.log(`Committed Google Play edit ${editId} (changesNotSentForReview=true).`);
    } else {
      throw err;
    }
  }
}

export async function runSync() {
  const dryRun = process.argv.includes('--dry-run');
  const uploadGraphics = process.env.UPLOAD_GRAPHICS === 'true';
  const allowAbGraphics = process.env.ALLOW_AB_TEST_LOCALE_GRAPHICS === 'true';
  const listings = readLocalPlayListings();
  const localeKeys = Object.keys(listings);

  console.log(`Validated ${localeKeys.length} local Play Store listings (UTF-16 limits OK).`);
  if (dryRun) {
    console.log('Dry run complete; skipping Google Play API calls.');
    return;
  }

  const saRaw = process.env.SERVICE_ACCOUNT_JSON || process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
  if (!saRaw) {
    throw new Error('Missing SERVICE_ACCOUNT_JSON (or GOOGLE_PLAY_SERVICE_ACCOUNT_JSON)');
  }
  const serviceAccount = JSON.parse(saRaw);
  const token = await getGoogleAccessToken(serviceAccount);
  const baseUrl = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(PACKAGE_NAME)}`;

  const edit = await playApi(token, 'POST', `${baseUrl}/edits`, { body: {} });
  const editId = edit.id;
  console.log(`Opened Google Play edit ${editId} for ${PACKAGE_NAME}`);

  let activePlayLocales = new Set();
  try {
    const existing = await playApi(token, 'GET', `${baseUrl}/edits/${editId}/listings`);
    for (const item of existing.listings || []) {
      if (item.language) activePlayLocales.add(item.language);
    }
    console.log(`Active Play Console listings: ${activePlayLocales.size} locales`);
  } catch (err) {
    console.warn(`Could not list existing Play Store locales (${err.message}); attempting all local folders.`);
  }

  let updatedCount = 0;
  for (const locale of localeKeys) {
    const listing = listings[locale];
    if (activePlayLocales.size > 0 && !activePlayLocales.has(locale)) {
      // Only update locales currently enabled in Play Console unless it's a core locale
      console.log(`Skipping ${locale} (not active in Play Console listings)`);
      continue;
    }

    try {
      await playApi(token, 'PUT', `${baseUrl}/edits/${editId}/listings/${locale}`, {
        body: {
          language: locale,
          title: listing.title,
          shortDescription: listing.shortDescription,
          fullDescription: listing.fullDescription,
        },
      });
      updatedCount += 1;
      console.log(`✓ Updated listing texts (${locale})`);
    } catch (err) {
      if (err.status === 400 || err.status === 404) {
        console.warn(`⚠️ Skipping ${locale}: ${err.message}`);
        continue;
      }
      throw err;
    }

    if (uploadGraphics) {
      if (AB_TEST_ACTIVE_LOCALES.has(locale) && !allowAbGraphics) {
        console.log(`ℹ️ Skipping graphics upload for ${locale} (active Play Store A/B experiment freeze)`);
        continue;
      }
      const localeShotsDir = path.join(GRAPHICS_DIR, locale);
      if (fs.existsSync(localeShotsDir)) {
        const shots = fs
          .readdirSync(localeShotsDir)
          .filter((f) => f.endsWith('.png'))
          .sort()
          .map((f) => path.join(localeShotsDir, f));
        if (shots.length > 0) {
          await uploadImageGroup(token, baseUrl, editId, locale, 'phoneScreenshots', shots);
        }
      }
      const fgPath = path.join(FEATURE_GRAPHICS_DIR, locale, 'feature_graphic.png');
      if (fs.existsSync(fgPath)) {
        await uploadImageGroup(token, baseUrl, editId, locale, 'featureGraphic', [fgPath]);
      }
    }
  }

  console.log(`Prepared ${updatedCount} listing updates in edit ${editId}.`);
  await commitEdit(token, baseUrl, editId);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runSync().catch((err) => {
    console.error(`::error::${err.message}`);
    process.exit(1);
  });
}
