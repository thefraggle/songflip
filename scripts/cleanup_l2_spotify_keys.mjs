#!/usr/bin/env node
/**
 * One-off / repeatable Firestore hygiene script for #293:
 * Normalizes legacy capitalized `links.Spotify` keys in `l2_song_cache` to `links.spotify`
 * and removes the legacy `links.Spotify` key.
 *
 * Usage:
 *   node scripts/cleanup_l2_spotify_keys.mjs [--dry-run | --execute]
 */

import { execSync } from "node:child_process";

const PROJECT_ID = "songflip-web";
const COLLECTION = "l2_song_cache";
const PAGE_SIZE = 300;
const BATCH_SIZE = 200;
const FIRESTORE_BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

const execute = process.argv.includes("--execute");

function getAccessToken() {
  if (process.env.GOOGLE_ACCESS_TOKEN) {
    return process.env.GOOGLE_ACCESS_TOKEN.trim();
  }
  return execSync("gcloud auth print-access-token", { encoding: "utf8" }).trim();
}

async function fetchBatch(token, lastCursor) {
  const query = {
    structuredQuery: {
      from: [{ collectionId: COLLECTION }],
      where: {
        fieldFilter: {
          field: { fieldPath: "links.Spotify" },
          op: "GREATER_THAN",
          value: { stringValue: "" },
        },
      },
      orderBy: [
        { field: { fieldPath: "links.Spotify" }, direction: "ASCENDING" },
        { field: { fieldPath: "__name__" }, direction: "ASCENDING" },
      ],
      limit: PAGE_SIZE,
    },
  };

  if (lastCursor) {
    query.structuredQuery.startAt = {
      values: [
        { stringValue: lastCursor.spotifyValue },
        { referenceValue: lastCursor.name },
      ],
      before: false,
    };
  }

  const resp = await fetch(`${FIRESTORE_BASE}:runQuery`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(query),
  });

  if (!resp.ok) {
    throw new Error(`runQuery failed (${resp.status}): ${await resp.text()}`);
  }

  const rows = await resp.json();
  return rows.filter((r) => r.document).map((r) => r.document);
}

function buildMigrationWrite(doc) {
  const linksFields = doc.fields?.links?.mapValue?.fields || {};
  const legacySpotify = linksFields.Spotify?.stringValue;
  if (!legacySpotify) return null;

  const existingLower = linksFields.spotify?.stringValue;
  const targetValue = existingLower && existingLower.trim() ? existingLower : legacySpotify;

  return {
    update: {
      name: doc.name,
      fields: {
        links: {
          mapValue: {
            fields: {
              spotify: { stringValue: targetValue },
            },
          },
        },
      },
    },
    updateMask: {
      fieldPaths: ["links.spotify", "links.Spotify"],
    },
  };
}

async function commitWrites(token, writes) {
  const resp = await fetch(`${FIRESTORE_BASE}:commit`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ writes }),
  });

  if (!resp.ok) {
    throw new Error(`commit failed (${resp.status}): ${await resp.text()}`);
  }
  return resp.json();
}

async function main() {
  let token = getAccessToken();
  let lastCursor = null;
  let scanned = 0;
  let migrated = 0;

  console.log(`[l2_song_cache cleanup] Mode: ${execute ? "EXECUTE" : "DRY-RUN"}`);

  while (true) {
    const docs = await fetchBatch(token, lastCursor);
    if (docs.length === 0) break;

    scanned += docs.length;
    if (!execute) {
      const lastDoc = docs[docs.length - 1];
      lastCursor = {
        spotifyValue: lastDoc.fields?.links?.mapValue?.fields?.Spotify?.stringValue || "",
        name: lastDoc.name,
      };
    }

    const writes = docs.map(buildMigrationWrite).filter(Boolean);
    if (execute && writes.length > 0) {
      for (let i = 0; i < writes.length; i += BATCH_SIZE) {
        const chunk = writes.slice(i, i + BATCH_SIZE);
        await commitWrites(token, chunk);
        migrated += chunk.length;
      }
      // Refresh token periodically in case of longer runs
      if (migrated % 2000 === 0) {
        token = getAccessToken();
      }
    } else {
      migrated += writes.length;
    }

    console.log(`Progress: scanned=${scanned}, ${execute ? "migrated" : "would_migrate"}=${migrated}`);

    if (docs.length < PAGE_SIZE) break;
  }

  console.log(`Done. Total scanned=${scanned}, ${execute ? "migrated" : "would_migrate"}=${migrated}`);
}

main().catch((err) => {
  console.error("Cleanup failed:", err);
  process.exit(1);
});
