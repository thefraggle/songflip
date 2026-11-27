#!/usr/bin/env node
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = path.resolve(SCRIPT_DIR, '..');
const METADATA_DIR = path.join(ROOT_DIR, 'fastlane', 'metadata');
const PBXPROJ_PATH = path.join(ROOT_DIR, 'iosApp', 'SongFlip', 'SongFlip.xcodeproj', 'project.pbxproj');

const APP_BUNDLE_ID = process.env.APP_BUNDLE_ID || 'de.goork.SongFlip';
const COPYRIGHT_TEXT = '© 2026 Daniel Notthoff';

export const STORE_LIMITS = {
  name: 30,
  subtitle: 30,
  promotionalText: 170,
  keywords: 100,
  description: 4000,
  whatsNew: 4000,
  notes: 4000,
};

function utf16Len(str) {
  return Buffer.byteLength(str, 'utf16le') / 2;
}

function readOptionalFile(filePath) {
  if (!fs.existsSync(filePath)) return '';
  return fs.readFileSync(filePath, 'utf8').trim();
}

export function resolveTargetVersion() {
  if (process.env.APP_VERSION && process.env.APP_VERSION.trim()) {
    return process.env.APP_VERSION.trim().replace(/^v/, '');
  }
  if (fs.existsSync(PBXPROJ_PATH)) {
    const pbx = fs.readFileSync(PBXPROJ_PATH, 'utf8');
    const match = pbx.match(/MARKETING_VERSION\s*=\s*([0-9]+(?:\.[0-9]+)+)\s*;/);
    if (match) return match[1];
  }
  throw new Error(`Could not determine target MARKETING_VERSION from ${PBXPROJ_PATH} or APP_VERSION`);
}

export function loadLocalAppStoreData() {
  const locales = ['de-DE', 'en-US'];
  const listings = {};

  for (const locale of locales) {
    const dir = path.join(METADATA_DIR, locale);
    const listing = {
      name: readOptionalFile(path.join(dir, 'name.txt')),
      subtitle: readOptionalFile(path.join(dir, 'subtitle.txt')),
      promotionalText: readOptionalFile(path.join(dir, 'promotional_text.txt')),
      keywords: readOptionalFile(path.join(dir, 'keywords.txt')),
      description: readOptionalFile(path.join(dir, 'description.txt')),
      whatsNew: readOptionalFile(path.join(dir, 'release_notes.txt')),
      supportUrl: readOptionalFile(path.join(dir, 'support_url.txt')),
      marketingUrl: readOptionalFile(path.join(dir, 'marketing_url.txt')),
      privacyPolicyUrl: readOptionalFile(path.join(dir, 'privacy_url.txt')),
    };

    for (const [field, maxLen] of Object.entries(STORE_LIMITS)) {
      if (field === 'notes') continue;
      const value = listing[field];
      if (!value) {
        throw new Error(`Missing ${field} for locale ${locale} in ${dir}`);
      }
      const len = utf16Len(value);
      if (len > maxLen) {
        throw new Error(`${locale}.${field} exceeds ${maxLen} UTF-16 units (${len})`);
      }
    }

    if (!listing.supportUrl || !listing.marketingUrl || !listing.privacyPolicyUrl) {
      throw new Error(`Missing support/marketing/privacy URL for locale ${locale}`);
    }

    listings[locale] = listing;
  }

  const reviewDir = path.join(METADATA_DIR, 'review_information');
  const reviewDetails = {
    contactFirstName: readOptionalFile(path.join(reviewDir, 'first_name.txt')) || 'Daniel',
    contactLastName: readOptionalFile(path.join(reviewDir, 'last_name.txt')) || 'Notthoff',
    contactPhone: readOptionalFile(path.join(reviewDir, 'phone_number.txt')) || '+49 2335 8873531',
    contactEmail: readOptionalFile(path.join(reviewDir, 'email_address.txt')) || 'hey@goork.de',
    demoAccountRequired: false,
    notes: readOptionalFile(path.join(reviewDir, 'notes.txt')),
  };

  if (!reviewDetails.notes) {
    throw new Error(`Missing review_information/notes.txt in ${reviewDir}`);
  }
  const notesLen = utf16Len(reviewDetails.notes);
  if (notesLen > STORE_LIMITS.notes) {
    throw new Error(`review_information/notes.txt exceeds ${STORE_LIMITS.notes} UTF-16 units (${notesLen})`);
  }

  return { listings, reviewDetails };
}

function makeJwt(keyId, issuerId, privateKeyPem) {
  const now = Math.floor(Date.now() / 1000);
  const b64u = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');
  const signingInput = `${b64u({ alg: 'ES256', kid: keyId, typ: 'JWT' })}.${b64u({
    iss: issuerId,
    iat: now,
    exp: now + 900,
    aud: 'appstoreconnect-v1',
  })}`;
  const sig = crypto.sign('sha256', Buffer.from(signingInput), {
    key: privateKeyPem,
    dsaEncoding: 'ieee-p1363',
  });
  return `${signingInput}.${sig.toString('base64url')}`;
}

async function ascRequest(auth, method, endpoint, body) {
  const res = await fetch(`https://api.appstoreconnect.apple.com/v1${endpoint}`, {
    method,
    headers: {
      Authorization: `Bearer ${makeJwt(auth.keyId, auth.issuerId, auth.privateKey)}`,
      'Content-Type': 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) {
    const err = new Error(`ASC API ${method} ${endpoint} failed (${res.status}): ${text}`);
    err.status = res.status;
    throw err;
  }
  return text ? JSON.parse(text) : {};
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function cancelActiveSubmissionsIfRequested(auth, appId) {
  if (process.env.CANCEL_ACTIVE_REVIEW !== 'true') {
    return;
  }
  const subsRes = await ascRequest(
    auth,
    'GET',
    `/apps/${appId}/reviewSubmissions?filter[platform]=IOS&filter[state]=WAITING_FOR_REVIEW,IN_REVIEW,UNRESOLVED_ISSUES`,
  );
  const activeSubs = subsRes.data || [];
  if (activeSubs.length === 0) {
    console.log('No active iOS review submissions found to cancel.');
    return;
  }

  let canceledAny = false;
  for (const sub of activeSubs) {
    const state = sub.attributes?.state;
    if (state === 'WAITING_FOR_REVIEW' || state === 'UNRESOLVED_ISSUES') {
      await ascRequest(auth, 'PATCH', `/reviewSubmissions/${sub.id}`, {
        data: {
          type: 'reviewSubmissions',
          id: sub.id,
          attributes: { canceled: true },
        },
      });
      console.log(`Canceled active iOS review submission ${sub.id} (previous state: ${state}).`);
      canceledAny = true;
    } else {
      console.log(`Notice: review submission ${sub.id} is in state ${state}; cannot auto-cancel.`);
    }
  }
  if (canceledAny) {
    await sleep(3000);
  }
}

async function syncAppReviewDetails(auth, versionId, reviewDetails) {
  let existingId = null;
  try {
    const existingRes = await ascRequest(auth, 'GET', `/appStoreVersions/${versionId}/appStoreReviewDetail`);
    existingId = existingRes?.data?.id || null;
  } catch (err) {
    if (err.status !== 404) {
      console.warn(`Notice: appStoreReviewDetail lookup returned ${err.message}`);
    }
  }

  if (existingId) {
    await ascRequest(auth, 'PATCH', `/appStoreReviewDetails/${existingId}`, {
      data: {
        type: 'appStoreReviewDetails',
        id: existingId,
        attributes: reviewDetails,
      },
    });
  } else {
    await ascRequest(auth, 'POST', '/appStoreReviewDetails', {
      data: {
        type: 'appStoreReviewDetails',
        attributes: reviewDetails,
        relationships: {
          appStoreVersion: {
            data: { type: 'appStoreVersions', id: versionId },
          },
        },
      },
    });
  }
  console.log(
    `Synced IOS appStoreReviewDetails (demoAccountRequired=false, notes=${utf16Len(reviewDetails.notes)} UTF-16 units)`,
  );
}

export async function runSync() {
  const dryRun = process.argv.includes('--dry-run');
  const targetVersion = resolveTargetVersion();
  const { listings, reviewDetails } = loadLocalAppStoreData();

  console.log(
    `Validated iOS metadata for ${Object.keys(listings).join(', ')} (targetVersion=${targetVersion}, notes=${utf16Len(reviewDetails.notes)}/${STORE_LIMITS.notes}).`,
  );
  if (dryRun) {
    console.log('Dry run complete; skipping App Store Connect API calls.');
    return;
  }

  const keyId = process.env.APP_STORE_CONNECT_KEY_ID || process.env.ASC_KEY_ID;
  const issuerId = process.env.APP_STORE_CONNECT_ISSUER_ID || process.env.ASC_ISSUER_ID;
  const privateKey = process.env.APP_STORE_CONNECT_API_KEY_P8 || process.env.ASC_API_KEY_P8;

  if (!keyId || !issuerId || !privateKey) {
    throw new Error('Missing APP_STORE_CONNECT_KEY_ID / ISSUER_ID / API_KEY_P8');
  }
  const auth = { keyId, issuerId, privateKey };

  const appsRes = await ascRequest(auth, 'GET', `/apps?filter[bundleId]=${encodeURIComponent(APP_BUNDLE_ID)}`);
  const app = (appsRes.data || []).find((a) => a.attributes?.bundleId === APP_BUNDLE_ID);
  if (!app) {
    throw new Error(`App ${APP_BUNDLE_ID} not found in App Store Connect`);
  }

  await cancelActiveSubmissionsIfRequested(auth, app.id);

  // 1. Locate iOS App Store Versions first so we never mutate an active review queue
  const versionsRes = await ascRequest(auth, 'GET', `/apps/${app.id}/appStoreVersions?filter[platform]=IOS`);
  const allVersions = versionsRes.data || [];
  const editableStates = new Set([
    'PREPARE_FOR_SUBMISSION',
    'DEVELOPER_REJECTED',
    'REJECTED',
    'METADATA_REJECTED',
    'INVALID_BINARY',
  ]);
  const activeReviewStates = new Set([
    'WAITING_FOR_REVIEW',
    'IN_REVIEW',
    'PENDING_APPLE_RELEASE',
    'PENDING_CONTRACT',
  ]);

  let versionObj = allVersions.find((v) => editableStates.has(v.attributes?.appStoreState));
  if (!versionObj) {
    const activeVer = allVersions.find((v) => activeReviewStates.has(v.attributes?.appStoreState));
    if (activeVer) {
      console.log(
        `ℹ️ iOS version ${activeVer.attributes?.versionString} is currently in review (${activeVer.attributes?.appStoreState}); skipping store metadata mutation to preserve review queue.`,
      );
      return;
    }

    const createdVer = await ascRequest(auth, 'POST', '/appStoreVersions', {
      data: {
        type: 'appStoreVersions',
        attributes: {
          platform: 'IOS',
          versionString: targetVersion,
          copyright: COPYRIGHT_TEXT,
        },
        relationships: { app: { data: { type: 'apps', id: app.id } } },
      },
    });
    versionObj = createdVer.data;
    console.log(`Created new editable IOS App Store version ${versionObj.id}: ${targetVersion}`);
  } else {
    if (
      versionObj.attributes?.versionString !== targetVersion ||
      versionObj.attributes?.copyright !== COPYRIGHT_TEXT
    ) {
      const updatedVer = await ascRequest(auth, 'PATCH', `/appStoreVersions/${versionObj.id}`, {
        data: {
          type: 'appStoreVersions',
          id: versionObj.id,
          attributes: {
            versionString: targetVersion,
            copyright: COPYRIGHT_TEXT,
          },
        },
      });
      versionObj = updatedVer.data;
    }
    console.log(`Editable IOS App Store version ${versionObj.id}: ${targetVersion} (${versionObj.attributes?.appStoreState})`);
  }

  // 2. Sync App Info Localizations (name, subtitle, privacyPolicyUrl) when editable
  const appInfosRes = await ascRequest(auth, 'GET', `/apps/${app.id}/appInfos`);
  const editableAppInfo =
    (appInfosRes.data || []).find((i) => editableStates.has(i.attributes?.appStoreState)) ||
    (appInfosRes.data || [])[0];

  if (editableAppInfo) {
    try {
      const infoLocsRes = await ascRequest(auth, 'GET', `/appInfos/${editableAppInfo.id}/appInfoLocalizations`);
      const existingInfoLocs = infoLocsRes.data || [];
      for (const [locale, listing] of Object.entries(listings)) {
        const existing = existingInfoLocs.find((l) => l.attributes?.locale === locale);
        if (existing) {
          await ascRequest(auth, 'PATCH', `/appInfoLocalizations/${existing.id}`, {
            data: {
              type: 'appInfoLocalizations',
              id: existing.id,
              attributes: {
                name: listing.name,
                subtitle: listing.subtitle,
                privacyPolicyUrl: listing.privacyPolicyUrl,
              },
            },
          });
        } else {
          await ascRequest(auth, 'POST', '/appInfoLocalizations', {
            data: {
              type: 'appInfoLocalizations',
              attributes: {
                locale,
                name: listing.name,
                subtitle: listing.subtitle,
                privacyPolicyUrl: listing.privacyPolicyUrl,
              },
              relationships: {
                appInfo: { data: { type: 'appInfos', id: editableAppInfo.id } },
              },
            },
          });
        }
        console.log(`Synced appInfoLocalization (${locale})`);
      }
    } catch (err) {
      console.warn(`Notice: appInfoLocalizations could not be modified (${err.message})`);
    }
  }

  // 3. Attach latest valid processed iOS build
  const buildsRes = await ascRequest(
    auth,
    'GET',
    `/builds?filter[app]=${app.id}&include=preReleaseVersion&sort=-uploadedDate&limit=20`,
  );
  const preReleaseById = new Map(
    (buildsRes.included || [])
      .filter((item) => item.type === 'preReleaseVersions')
      .map((item) => [item.id, item]),
  );
  const validBuild = (buildsRes.data || []).find((b) => {
    if (b.attributes?.processingState !== 'VALID') return false;
    const preId = b.relationships?.preReleaseVersion?.data?.id;
    const pre = preId ? preReleaseById.get(preId) : null;
    return !pre || pre.attributes?.platform === 'IOS';
  });
  if (validBuild) {
    try {
      await ascRequest(auth, 'PATCH', `/appStoreVersions/${versionObj.id}/relationships/build`, {
        data: { type: 'builds', id: validBuild.id },
      });
      console.log(
        `Attached valid IOS build ${validBuild.id} (${validBuild.attributes?.version}) to ${targetVersion}`,
      );
    } catch (err) {
      console.warn(`Notice: could not attach IOS build automatically (${err.message})`);
    }
  }

  // 4. Sync App Store Version Localizations (skip whatsNew on initial release)
  const verLocsRes = await ascRequest(
    auth,
    'GET',
    `/appStoreVersions/${versionObj.id}/appStoreVersionLocalizations`,
  );
  const existingVerLocs = verLocsRes.data || [];
  const includeWhatsNew = allVersions.length > 1;

  for (const [locale, listing] of Object.entries(listings)) {
    const attrs = {
      description: listing.description,
      keywords: listing.keywords,
      promotionalText: listing.promotionalText,
      supportUrl: listing.supportUrl,
      marketingUrl: listing.marketingUrl,
      ...(includeWhatsNew ? { whatsNew: listing.whatsNew } : {}),
    };
    const locObj = existingVerLocs.find((l) => l.attributes?.locale === locale);
    if (locObj) {
      await ascRequest(auth, 'PATCH', `/appStoreVersionLocalizations/${locObj.id}`, {
        data: { type: 'appStoreVersionLocalizations', id: locObj.id, attributes: attrs },
      });
    } else {
      await ascRequest(auth, 'POST', '/appStoreVersionLocalizations', {
        data: {
          type: 'appStoreVersionLocalizations',
          attributes: { locale, ...attrs },
          relationships: {
            appStoreVersion: { data: { type: 'appStoreVersions', id: versionObj.id } },
          },
        },
      });
    }
    console.log(`Synced IOS appStoreVersionLocalization (${locale})`);
  }

  // 5. Sync App Store Review Details (Contact, No-Login Flag & 6-Step Walkthrough)
  await syncAppReviewDetails(auth, versionObj.id, reviewDetails);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runSync().catch((err) => {
    console.error(`::error::${err.message}`);
    process.exit(1);
  });
}
