# SongFlip Store Assets (Open Assets)

Automatisierte Generierung von Google Play & iOS App Store Screenshots mittels [Open Assets](https://github.com/Parra-Inc/open-assets).

## Struktur

- `assets.json`: Zentrale Konfiguration (Auflösungen, Templates, Sprachen).
- `localizations.json`: Übersetzungen aller 37 Sprachen.
- `assets/styles.css`: SongFlip Dark/AMOLED & Emerald Theme Styling.
- `assets/screenshots/`: HTML-Templates für die 5 Screens.
- `public/mockups/`: Quell-Mockups (`screen_1.png` bis `screen_5.png`).

## Befehle

Im Ordner `distribution/open-assets`:

```bash
# 1. Live-Preview im Browser starten (Storybook-Modus mit Hot-Reload)
npx open-assets dev

# 2. Alle Screenshots für alle Sprachen & Größen rendern
npx open-assets render

# 3. Nur Deutsch rendern
npx open-assets render --locale de-DE

# 4. Nur Englisch & Play Store rendern
npx open-assets render --locale en-US --size playstore

# 5. Konfiguration & Templates validieren
npx open-assets validate
```
