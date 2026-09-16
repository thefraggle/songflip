#!/usr/bin/env python3
import os
import csv

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST_DIR = os.path.join(BASE_DIR, "distribution")
os.makedirs(DIST_DIR, exist_ok=True)

# Dictionary of localized listings:
# Key: locale_code
# Value: (Language Name, Title [<=30], Short Description [<=80], Full Description [<=4000])

LISTINGS = {}

# --- DEUTSCH (de-DE) ---
LISTINGS["de-DE"] = (
    "Deutsch",
    "SongFlip: Musik-Link Redirect",
    "Musik-Links automatisch konvertieren & in deiner Wunsch-App öffnen (0-Klick).",
    """Ein Freund schickt dir einen Song auf Spotify, du hörst Musik aber über YouTube Music, Apple Music oder Deezer?

SongFlip ist dein smarter 0-Klick Musik-Link Converter und Weiterleitung für Android. Einmal eingerichtet, werden empfangene Musik-Links automatisch im Hintergrund in deinen bevorzugten Streaming-Dienst umgewandelt und direkt dort gestartet – ohne lästige Zwischenseiten, ohne Wartezeit, ohne manuelle Suche.

Egal ob Spotify zu YouTube Music, Spotify zu Apple Music oder Links von Shazam: SongFlip erkennt den Song blitzschnell und fungiert als dein universeller Smart Link Converter für jeden Musik-Player.

🚀 DIE WICHTIGSTEN FUNKTIONEN:

• 8 Streaming-Plattformen & Shazam:
Konvertiert Musik-Links nahtlos zwischen Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music sowie SoundCloud und Bandcamp. Erkennt jetzt auch geteilte Shazam-Links blitzschnell!

• Sofortige Wiedergabe (Instant Playback):
Titel starten direkt im Ziel-Player, anstatt nur eine leere Suchergebnisseite zu öffnen.

• Volle Album- & Playlist-Erkennung:
Erkennt vollständige Alben und öffnet die komplette Trackliste. Playlist-Links werden präzise erkannt und nahtlos übergeben – dein intelligenter Playlist Converter für geteilte Musik.

• Schnellauswahl (Ask Every Time):
Nutzt du mehrere Musik-Apps? Aktiviere die optionale Schnellauswahl, um beim Öffnen eines Musik-Links jedes Mal blitzschnell den gewünschten Ziel-Player zu wählen.

• Smarter Einrichtungs-Assistent (Android 12+):
Der integrierte Assistent prüft deine installierten Musik-Apps und hilft dir mit nur einem Fingertipp, App-Links konfliktfrei für die automatische Weiterleitung einzurichten.

• Clipboard Smart-Banner & Share-Menü:
Kopiere einen Link oder teile ihn direkt aus WhatsApp, Telegram, Instagram oder Reddit mit SongFlip für eine sofortige 1-Klick-Konvertierung.

• Universelle Web-Links (Smart Links):
Erstelle mit einem Fingertipp neutrale Web-Links (songflip.link), die deine Freunde auf jedem beliebigen Gerät und Streaming-Dienst öffnen können.

• Schnelleinstellungen & App-Shortcuts:
Pausiere die automatische Umleitung flexibel für 15 Minuten, 1 Stunde oder bis zum nächsten Morgen über die praktische Quick-Settings-Kachel. Schneller Zugriff per Long-Press auf das App-Icon.

• Material You Design & Themed Icon:
Vollständig an dein Android-Systemfarbschema angepasst (Dynamic Color, Light- & AMOLED-Dark-Mode) inklusive sauberem Monochrome-Homescreen-Icon.

🔒 DATENSCHUTZ, TRANSPARENZ & OPEN SOURCE:
• 100 % Open Source (GPLv3 lizenziert auf GitHub)
• Keine Benutzerkonten, kein Login, keine Registrierung
• Absolut werbefrei – für immer
• Kein Tracking, keine Analyse deines Musikgeschmacks oder Hörverhaltens

Angetrieben von moderner Open-Source-Technologie und kompatibel mit Songlink / Odesli. Hol dir SongFlip und genieße Musik frei über alle Plattform-Grenzen hinweg!"""
)

# --- ENGLISH (en-US, en-GB) ---
EN_TUPLE = (
    "English",
    "SongFlip: Music Link Redirect",
    "Automatically convert and open music links in your favorite player. Zero-click.",
    """A friend sends you a song on Spotify, but you listen on YouTube Music, Apple Music, or Deezer?

SongFlip is your seamless, zero-click music link converter and redirector for Android. Once enabled, incoming music links automatically convert in the background and launch directly inside your preferred music player—no manual searching, no ads, no intermediate browser detours.

Whether switching Spotify to YouTube Music, Spotify to Apple Music, or opening tracks identified with Shazam, SongFlip handles song links instantly as your universal smart link converter.

🚀 KEY FEATURES:

• Universal 8-Platform Redirection & Shazam:
Seamlessly converts tracks and albums across Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud, and Bandcamp. Newly added: instant resolution for shared Shazam links!

• Instant Track Playback:
Directly triggers playback inside your target music player instead of landing on empty search result pages.

• Full Album & Playlist Support:
Recognizes entire albums and opens the full discography release. Shared playlists are cleanly detected—your go-to playlist converter for shared music.

• Quick Player Chooser (Ask Every Time):
Using multiple music apps? Enable the optional quick player chooser to pick your target player on the fly whenever you open a music link.

• Smart Link Assistant (Android 12+):
Easily verify and configure Android Open by Default App Links with our interactive 1-tap setup assistant.

• Clipboard Smart-Banner & Native Sharing:
Copied a music link? Launch it instantly via the smart dashboard banner, or share links directly from WhatsApp, Telegram, Reddit, or Instagram into SongFlip.

• Universal Web Smart Links:
Generate neutral, universal web-share links (songflip.link) so your friends can listen on whichever streaming platform they prefer.

• Quick Settings Tile & App Shortcuts:
Temporarily pause redirection for 15 minutes, 1 hour, or until tomorrow morning directly from your Android Quick Settings shade or home screen long-press.

• Material You Dynamic Color & Themed Icon:
Beautifully follows your system accent colors with full AMOLED dark mode, light mode, and a crisp monochrome Material You home screen icon.

🔒 PRIVACY & OPEN SOURCE FIRST:
• 100% Open Source (GPLv3 licensed on GitHub)
• No accounts, no sign-ups, no logins required
• Completely ad-free forever
• Zero tracking, no data harvesting, no listening habits logged

Compatible with universal music protocols including Songlink / Odesli. Download SongFlip now and bridge music links across all streaming services effortlessly!"""
)
LISTINGS["en-US"] = ("English (United States)", EN_TUPLE[1], EN_TUPLE[2], EN_TUPLE[3])
LISTINGS["en-GB"] = ("English (United Kingdom)", EN_TUPLE[1], EN_TUPLE[2], EN_TUPLE[3])

# --- ESPAÑOL (es-ES, es-419, es-US) ---
ES_TUPLE = (
    "Español",
    "SongFlip: Enlaces de Música",
    "Convierte y abre enlaces de música en tu app favorita. 0 clics, 100% privado.",
    """¿Un amigo te envía una canción en Spotify, pero tú usas YouTube Music, Apple Music o Deezer?

SongFlip es tu convertidor y redireccionador automático de enlaces de música para Android. Una vez configurado, los enlaces recibidos se transforman en segundo plano y se abren al instante en tu reproductor preferido: sin búsquedas manuales, sin publicidad y sin rodeos en el navegador.

Ya sea de Spotify a YouTube Music, de Spotify a Apple Music o enlaces de Shazam: SongFlip abre tus canciones al instante como tu convertidor inteligente universal.

🚀 FUNCIONES PRINCIPALES:

• 8 Plataformas de Streaming y Shazam:
Convierte sin problemas entre Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud y Bandcamp. ¡Ahora también con soporte instantáneo para enlaces compartidos de Shazam!

• Reproducción Inmediata (Instant Playback):
Las canciones se reproducen directamente en la app de destino en lugar de mostrar resultados de búsqueda vacíos.

• Soporte Completo para Álbumes y Playlists:
Detecta álbumes enteros y abre la lista completa de pistas. Las listas de reproducción compartidas se abren con un solo toque — tu convertidor inteligente de playlists para música compartida.

• Selector Rápido de Reproductor (Preguntar cada vez):
¿Usas varias apps de música? Activa el selector rápido para elegir al instante tu reproductor preferido cada vez que abras un enlace musical.

• Asistente de Configuración Inteligente (Android 12+):
Verifica y configura fácilmente los enlaces predeterminados de tus apps de música con un solo toque.

• Smart-Banner de Portapapeles y Menú Compartir:
¿Copiaste un enlace? Ábrelo al instante desde el banner inteligente o compártelo directamente desde WhatsApp, Telegram, Reddit o Instagram hacia SongFlip.

• Enlaces Web Universales (Smart Links):
Genera enlaces neutros (songflip.link) para que tus amigos escuchen en su servicio de streaming favorito sin importar su dispositivo.

• Ajustes Rápidos y Accesos Directos:
Pausa la redirección por 15 minutos, 1 hora o hasta mañana directamente desde el panel de ajustes rápidos de Android.

• Diseño Material You e Icono Temático:
Se adapta a los colores de tu sistema con modo oscuro AMOLED, modo claro e icono monocromático para tu pantalla de inicio.

🔒 PRIVACIDAD Y CÓDIGO ABIERTO:
• 100 % Código Abierto (licencia GPLv3 en GitHub)
• Sin cuentas, sin registros, sin inicios de sesión
• Totalmente libre de publicidad para siempre
• Cero rastreo, sin recopilación de hábitos de escucha

Compatible con Songlink / Odesli. ¡Descarga SongFlip ahora y disfruta de tu música sin fronteras entre plataformas!"""
)
LISTINGS["es-ES"] = ("Español (España)", ES_TUPLE[1], ES_TUPLE[2], ES_TUPLE[3])
LISTINGS["es-419"] = ("Español (Latinoamérica)", ES_TUPLE[1], ES_TUPLE[2], ES_TUPLE[3])
LISTINGS["es-US"] = ("Español (Estados Unidos)", ES_TUPLE[1], ES_TUPLE[2], ES_TUPLE[3])

# --- FRANÇAIS (fr-FR, fr-CA) ---
FR_TUPLE = (
    "Français",
    "SongFlip: Liens Musicaux",
    "Convertissez et ouvrez vos liens musicaux dans votre lecteur favori (0 clic).",
    """Un ami vous envoie un morceau sur Spotify, mais vous utilisez YouTube Music, Apple Music ou Deezer ?

SongFlip est votre convertisseur et redirecteur de liens musicaux intelligent pour Android. Une fois activé, les liens reçus sont automatiquement convertis en arrière-plan et s'ouvrent directement dans votre application musicale préférée, sans recherche manuelle ni pages intermédiaires.

Que ce soit de Spotify vers YouTube Music, de Spotify vers Apple Music ou des liens Shazam : SongFlip traite vos liens instantanément en tant que convertisseur intelligent universel.

🚀 FONCTIONNALITÉS CLÉS :

• 8 Plateformes de Streaming & Shazam :
Convertit en toute transparence entre Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud et Bandcamp. Intègre désormais la prise en charge rapide des liens Shazam !

• Lecture Immédiate (Instant Playback) :
Lance directement la lecture du titre dans l'application cible sans vous laisser sur une page de recherche.

• Prise en Charge Complète des Albums et Playlists :
Détecte les albums complets et ouvre l'intégralité des pistes. Les playlists partagées sont détectées avec précision — votre convertisseur de playlists idéal pour la musique partagée.

• Sélecteur Rapide de Lecteur (Demander à chaque fois) :
Vous utilisez plusieurs applications musicales ? Activez le sélecteur rapide optionnel pour choisir votre lecteur cible à la volée dès que vous ouvrez un lien.

• Assistant de Configuration Intelligent (Android 12+) :
Configurez facilement l'ouverture par défaut des liens d'application grâce à notre assistant interactif en 1 clic.

• Bannière Presse-papiers & Menu Partager :
Lien copié ? Lancez-le immédiatement depuis le tableau de bord ou partagez-le depuis WhatsApp, Telegram, Reddit ou Instagram vers SongFlip.

• Liens Web Universels (Smart Links) :
Générez des liens web universels (songflip.link) pour que vos proches puissent écouter sur la plateforme de leur choix.

• Tuile Réglages Rapides & Raccourcis :
Mettez la redirection en pause pendant 15 minutes, 1 heure ou jusqu'au lendemain matin depuis vos réglages rapides Android.

• Design Material You & Icône Thématique :
S'adapte parfaitement aux couleurs de votre système (mode sombre AMOLED, mode clair) avec icône monochrome Material You.

🔒 VIE PRIVÉE ET OPEN SOURCE :
• 100 % Open Source (licence GPLv3 sur GitHub)
• Aucun compte, aucune inscription requise
• Entièrement sans publicité, pour toujours
• Zéro suivi ni collecte de données

Compatible avec Songlink / Odesli. Téléchargez SongFlip dès maintenant et libérez votre musique de toutes les barrières !"""
)
LISTINGS["fr-FR"] = ("Français (France)", FR_TUPLE[1], FR_TUPLE[2], FR_TUPLE[3])
LISTINGS["fr-CA"] = ("Français (Canada)", FR_TUPLE[1], FR_TUPLE[2], FR_TUPLE[3])

# --- ITALIANO (it-IT) ---
LISTINGS["it-IT"] = (
    "Italiano",
    "SongFlip: Link Musicali",
    "Converti e apri i link musicali nella tua app preferita (0 clic, privato).",
    """Un amico ti invia un brano su Spotify, ma tu usi YouTube Music, Apple Music o Deezer?

SongFlip è il tuo convertitore e reindirizzatore intelligente di link musicali per Android. Una volta attivato, i link ricevuti vengono convertiti automaticamente in background e aperti all'istante nel tuo lettore musicale preferito, senza ricerche manuali o fastidiose pagine intermedie.

Da Spotify a YouTube Music, da Spotify ad Apple Music o link condivisi da Shazam: SongFlip gestisce i link all'istante come tuo convertitore smart universale.

🚀 CARATTERISTICHE PRINCIPALI:

• 8 Piattaforme di Streaming & Shazam:
Converte senza problemi tra Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud e Bandcamp. Ora supporta anche i link condivisi da Shazam!

• Riproduzione Immediata (Instant Playback):
Avvia direttamente il brano nel lettore di destinazione anziché mostrare pagine di ricerca vuote.

• Supporto Completo per Album e Playlist:
Riconosce album interi e apre tutte le tracce. Le playlist condivise vengono rilevate con precisione — il tuo convertitore di playlist ideale per la musica condivisa.

• Selettore Rapido del Lettore (Chiedi ogni volta):
Usi più app musicali? Attiva il selettore rapido opzionale per scegliere al volo il tuo lettore preferito ogni volta che apri un link.

• Assistente di Configurazione Intelligente (Android 12+):
Configura facilmente i link predefiniti delle app musicali con il nostro assistente interattivo in un tocco.

• Smart-Banner Appunti & Condivisione Diretta:
Hai copiato un link? Avvialo subito dal banner intelligente o condividi brani da WhatsApp, Telegram, Reddit o Instagram su SongFlip.

• Smart Link Web Universali:
Crea link web neutri (songflip.link) affinché i tuoi amici possano ascoltare sulla loro piattaforma preferita.

• Riquadro Impostazioni Rapide & Scorciatoie:
Metti in pausa il reindirizzamento per 15 minuti, 1 ora o fino a domani direttamente dal pannello delle impostazioni rapide.

• Design Material You & Icona a Tema:
Segue i colori del tuo sistema con modalità scura AMOLED, tema chiaro e icona monocromatica per la schermata home.

🔒 PRIVACY E OPEN SOURCE:
• 100% Open Source (licenza GPLv3 su GitHub)
• Nessun account, nessuna registrazione richiesta
• Completamente senza pubblicità per sempre
• Zero tracciamento, nessun dato raccolto

Compatibile con Songlink / Odesli. Scarica SongFlip ora e ascolta musica senza barriere tra servizi di streaming!"""
)

# --- PORTUGUÊS (pt-BR, pt-PT) ---
PT_TUPLE = (
    "Português",
    "SongFlip: Links de Música",
    "Converta e abra links de música no seu player favorito com zero cliques.",
    """Um amigo te manda uma música no Spotify, mas você usa YouTube Music, Apple Music ou Deezer?

O SongFlip é o seu conversor e redirecionador inteligente de links de música para Android. Uma vez ativado, os links recebidos são convertidos automaticamente em segundo plano e abertos direto no seu reprodutor preferido: sem buscas manuais, sem anúncios e sem páginas intermediárias.

Seja do Spotify para o YouTube Music, do Spotify para o Apple Music ou links do Shazam: o SongFlip resolve na hora como seu conversor inteligente universal de links.

🚀 PRINCIPAIS RECURSOS:

• 8 Plataformas de Streaming e Shazam:
Converte perfeitamente entre Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud e Bandcamp. Agora também com suporte rápido a links do Shazam!

• Reprodução Instantânea (Instant Playback):
Toca a faixa direto no player de destino em vez de exibir páginas de busca vazias.

• Suporte Completo a Álbuns e Playlists:
Reconhece álbuns completos e abre todas as faixas. Playlists compartilhadas são detectadas com precisão — seu conversor de playlists definitivo para músicas compartilhadas.

• Seletor Rápido de Player (Perguntar sempre):
Usa vários apps de música? Ative o seletor rápido para escolher instantaneamente seu reprodutor preferido ao abrir qualquer link musical.

• Assistente de Configuração Inteligente (Android 12+):
Configure os links padrão de aplicativos com facilidade com nosso assistente em um toque.

• Banner de Área de Transferência e Compartilhamento:
Copiou um link de música? Abra direto pelo banner inteligente ou compartilhe pelo WhatsApp, Telegram, Reddit ou Instagram para o SongFlip.

• Links Universais da Web (Smart Links):
Gere links neutros (songflip.link) para que seus amigos escutem em qualquer serviço de streaming favorito.

• Botão de Configurações Rápidas e Atalhos:
Pause o redirecionamento por 15 minutos, 1 hora ou até amanhã de manhã direto pelo painel de ajustes rápidos do Android.

• Design Material You e Ícone Temático:
Adapta-se às cores do seu sistema com modo escuro AMOLED, modo claro e ícone monocromático para a tela inicial.

🔒 PRIVACIDADE E CÓDIGO ABERTO:
• 100% Código Aberto (licença GPLv3 no GitHub)
• Sem contas, sem login, sem cadastros
• Totalmente livre de anúncios para sempre
• Zero rastreamento de dados ou hábitos de escuta

Compatível com Songlink / Odesli. Baixe o SongFlip agora e ouça música sem limites entre plataformas!"""
)
LISTINGS["pt-BR"] = ("Português (Brasil)", PT_TUPLE[1], PT_TUPLE[2], PT_TUPLE[3])
LISTINGS["pt-PT"] = ("Português (Portugal)", PT_TUPLE[1], PT_TUPLE[2], PT_TUPLE[3])

# --- NEDERLANDS (nl-NL) ---
LISTINGS["nl-NL"] = (
    "Nederlands",
    "SongFlip: Muzieklink Redirect",
    "Converteer en open muzieklinks in je favoriete app. 0 klikken, 100% privé.",
    """Stuurt een vriend je een nummer op Spotify, maar luister jij via YouTube Music, Apple Music of Deezer?

SongFlip is jouw slimme 0-klik muzieklink converter en redirector voor Android. Eenmaal ingesteld worden ontvangen muzieklinks automatisch op de achtergrond geconverteerd en direct geopend in jouw favoriete muziek-app — zonder handmatig zoeken of tussenliggende pagina's.

Of het nu gaat om Spotify naar YouTube Music, Spotify naar Apple Music of Shazam-links: SongFlip verwerkt links direct als jouw universele smart link converter.

🚀 BELANGRIJKSTE FUNCTIES:

• 8 Streamingplatforms & Shazam:
Converteert naadloos tussen Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud en Bandcamp. Nu ook met ondersteuning voor Shazam-links!

• Directe Weergave (Instant Playback):
Start nummers direct in de doel-app in plaats van een lege zoekpagina te tonen.

• Volledige Album- & Afspeellijstondersteuning:
Herkent complete albums en opent de volledige tracklijst. Gedeelde afspeellijsten openen soepel — jouw ideale playlist-converter voor gedeelde muziek.

• Snelle Spelerkiezer (Elke keer vragen):
Gebruik je meerdere muziek-apps? Activeer de optionele snelle kiezer om direct je doelspeler te kiezen wanneer je een muzieklink opent.

• Slimme Installatiewizard (Android 12+):
Controleer en stel eenvoudig standaard app-koppelingen in met onze interactieve wizard in 1 tik.

• Klembord Smart-Banner & Deelmenu:
Muzieklink gekopieerd? Start direct via de slimme banner of deel links vanuit WhatsApp, Telegram, Reddit of Instagram naar SongFlip.

• Universele Web Smart Links:
Maak neutrale weblinks (songflip.link) zodat je vrienden kunnen luisteren op hun favoriete streamingdienst.

• Snelle Instellingen & Snelkoppelingen:
Pauzeer de omleiding tijdelijk voor 15 minuten, 1 uur of tot morgenochtend via de Android Quick Settings.

• Material You Design & Thema-icoon:
Past naadloos bij je systeemkleuren met AMOLED-donkere modus, lichte modus en monochroom startschermpictogram.

🔒 PRIVACY & OPEN SOURCE:
• 100% Open Source (GPLv3-licentie op GitHub)
• Geen accounts, geen registratie nodig
• Altijd volledig advertentievrij
• Geen tracking of gegevensverzameling

Compatibel met Songlink / Odesli. Download SongFlip nu en verbind al je muziekdiensten moeiteloos!"""
)

# --- POLSKI (pl-PL) ---
LISTINGS["pl-PL"] = (
    "Polski",
    "SongFlip: Konwerter Muzyki",
    "Konwertuj i otwieraj linki muzyczne w ulubionej aplikacji (0 klik, prywatnie).",
    """Znajomy wysyła Ci utwór ze Spotify, ale Ty używasz YouTube Music, Apple Music lub Deezer?

SongFlip to Twój inteligentny konwerter i przekierowujący linki muzyczne dla Androida. Po jednorazowej konfiguracji otrzymane linki są automatycznie konwertowane w tle i otwierane w Twojej ulubionej aplikacji muzycznej — bez ręcznego wyszukiwania i bez zbędnych stron pośrednich.

Niezależnie od tego, czy chodzi o Spotify na YouTube Music, Spotify na Apple Music czy utwory z Shazam: SongFlip obsługuje linki natychmiast jako Twój uniwersalny konwerter smart linków.

🚀 NAJWAŻNIEJSZE FUNKCJE:

• 8 Platform Streamingowych i Shazam:
Płynnie konwertuje utwory i albumy między Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud oraz Bandcamp. Nowość: obsługa udostępnionych linków Shazam!

• Natychmiastowe Odtwarzanie (Instant Playback):
Utwory uruchamiają się bezpośrednio w aplikacji docelowej zamiast na pustej stronie wyników wyszukiwania.

• Pełna Obsługa Albumów i Playlist:
Rozpoznaje całe albumy i otwiera pełną listę utworów. Udostępnione playlisty są precyzyjnie wykrywane — Twój niezastąpiony konwerter playlist dla udostępnianej muzyki.

• Szybki Wybór Odtwarzacza (Pytaj za każdym razem):
Korzystasz z kilku aplikacji muzycznych? Włącz szybki selektor, aby błyskawicznie wybrać docelowy odtwarzacz przy każdym otwarciu linku.

• Inteligentny Kreator Konfiguracji (Android 12+):
Łatwo skonfiguruj domyślne linki aplikacji za pomocą interaktywnego asystenta.

• Inteligentny Baner Schowka i Menu Udostępniania:
Skopiowałeś link? Uruchom go błyskawicznie z poziomu banera lub udostępnij link bezpośrednio z WhatsApp, Telegrama, Reddita lub Instagrama do SongFlip.

• Uniwersalne Linki Internetowe (Smart Links):
Generuj neutralne linki (songflip.link), aby Twoi znajomi mogli słuchać muzyki w wybranej przez siebie usłudze.

• Kafelek Szybkich Ustawień i Skróty:
Wstrzymaj przekierowywanie na 15 minut, 1 godzinę lub do rana bezpośrednio z panelu szybkich ustawień Androida.

• Wygląd Material You i Ikona Motywu:
Dopasowuje się do kolorów systemu (tryb ciemny AMOLED, tryb jasny) wraz z monochromatyczną ikoną na ekranie głównym.

🔒 PRYWATNOŚĆ I OPEN SOURCE:
• 100% Open Source (licencja GPLv3 na GitHubie)
• Bez kont, bez rejestracji i logowania
• Całkowicie bez reklam na zawsze
• Brak śledzenia i zbierania danych o gustach muzycznych

Kompatybilny z Songlink / Odesli. Pobierz SongFlip i ciesz się muzyką bez barier między platformami!"""
)

# --- РУССКИЙ (ru-RU) ---
LISTINGS["ru-RU"] = (
    "Русский",
    "SongFlip: Музыкальные Ссылки",
    "Автоматически конвертируйте и открывайте ссылки на музыку в любимом плеере.",
    """Друг прислал трек со Spotify, а вы слушаете музыку в YouTube Music, Apple Music или Deezer?

SongFlip — это умный конвертер музыкальных ссылок для Android. После простой настройки входящие ссылки автоматически преобразуются в фоновом режиме и открываются в вашем любимом плеере без ручного поиска, рекламы и лишних страниц.

Переход со Spotify в YouTube Music, со Spotify в Apple Music или ссылки из Shazam — SongFlip обрабатывает ссылки мгновенно как ваш универсальный смарт-конвертер.

🚀 ОСНОВНЫЕ ВОЗМОЖНОСТИ:

• 8 Стриминговых Сервисов и Shazam:
Конвертация между Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud и Bandcamp. Теперь с мгновенной поддержкой ссылок Shazam!

• Мгновенное Воспроизведение (Instant Playback):
Трек сразу начинает играть в целевом приложении вместо показа пустых страниц поиска.

• Полная Поддержка Альбомов и Плейлистов:
Распознает полные альбомы и дискографии. Плейлисты открываются точно — ваш надежный конвертер плейлистов для совместной музыки.

• Быстрый Выбор Плеера (Спрашивать каждый раз):
Используете несколько музыкальных приложений? Включите быстрый выбор, чтобы мгновенно выбирать целевой плеер при открытии ссылки.

• Умный Помощник Настройки (Android 12+):
Быстрая проверка и настройка открытия ссылок по умолчанию в один клик.

• Умный Баннер Буфера Обмена и Меню «Поделиться»:
Скопировали ссылку? Откройте ее через смарт-баннер или отправьте из WhatsApp, Telegram, Reddit или Instagram прямо в SongFlip.

• Универсальные Смарт-Ссылки (songflip.link):
Создавайте нейтральные веб-ссылки, чтобы друзья могли слушать музыку на любой удобной им платформе.

• Плитка Быстрых Настроек и Ярлыки:
Приостанавливайте перенаправление на 15 минут, 1 час или до утра прямо из шторки быстрых настроек Android.

• Дизайн Material You и Тематическая Иконка:
Идеальная адаптация под системные цвета, темная тема AMOLED и монохромная иконка на рабочем столе.

🔒 КОНФИДЕНЦИАЛЬНОСТЬ И OPEN SOURCE:
• 100% Открытый Исходный Код (лицензия GPLv3 на GitHub)
• Никаких учетных записей и регистраций
• Без рекламы навсегда
• Никакого отслеживания и сбора данных

Совместимо с Songlink / Odesli. Установите SongFlip и слушайте музыку без границ между сервисами!"""
)

# --- УКРАЇНСЬКА (uk) ---
LISTINGS["uk"] = (
    "Українська",
    "SongFlip: Музичні Посилання",
    "Автоматично конвертуйте та відкривайте музичні посилання в улюбленому плеєрі.",
    """Друг надіслав пісню зі Spotify, а ви слухаєте музику в YouTube Music, Apple Music чи Deezer?

SongFlip — це розумний конвертер музичних посилань для Android. Після легкого налаштування вхідні посилання автоматично конвертуються у фоновому режимі та відкриваються у вашому улюбленому додатку без ручного пошуку, реклами та зайвих веб-сторінок.

Зі Spotify в YouTube Music, зі Spotify в Apple Music або посилання з Shazam — SongFlip відкриває треки миттєво як ваш універсальний смарт-конвертер.

🚀 ГОЛОВНІ ФУНКЦІЇ:

• 8 Стрімінгових Сервісів та Shazam:
Швидка конвертація між Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud та Bandcamp. Тепер із підтримкою посилань Shazam!

• Миттєве Відтворення (Instant Playback):
Пісня починає грати безпосередньо в обраному плеєрі без показу порожніх результатів пошуку.

• Підтримка Альбомів та Плейлистів:
Розпізнає цілі альбоми та відкриває всі треки. Спільні плейлисти відкриваються точно — ваш надійний конвертер плейлистів для спільної музики.

• Швидкий Вибір Плеєра (Запитувати щоразу):
Користуєтеся кількома музичними додатками? Увімкніть швидкий вибір, щоб миттєво обирати цільовий плеєр під час відкриття посилання.

• Розумний Помічник Налаштування (Android 12+):
Швидко налаштуйте посилання за замовчуванням за допомогою інтерактивного помічника.

• Розумний Банер Буфера Обміну та Меню «Поділитися»:
Скопіювали посилання? Відкрийте його через банер або надішліть із WhatsApp, Telegram, Reddit чи Instagram у SongFlip.

• Універсальні Веб-Посилання (Smart Links):
Створюйте нейтральні посилання (songflip.link), щоб друзі могли слухати музику на будь-якому зручному сервісі.

• Швидкі Налаштування та Ярлики:
Призупиняйте перенаправлення на 15 хвилин, 1 годину або до ранку зі шторки швидких налаштувань Android.

• Дизайн Material You та Тематична Іконка:
Адаптація до кольорів системи, темний режим AMOLED та монохромна іконка для головного екрана.

🔒 ПРИВАТНІСТЬ ТА ВІДКРИТИЙ КОД:
• 100% Відкритий Код (GPLv3 на GitHub)
• Жодних облікових записів та реєстрацій
• Без реклами назавжди
• Жодного відстеження та збору даних

Сумісно з Songlink / Odesli. Завантажуйте SongFlip та слухайте музику вільно на будь-якій платформі!"""
)

# --- TÜRKÇE (tr-TR) ---
LISTINGS["tr-TR"] = (
    "Türkçe",
    "SongFlip: Müzik Link Çevirici",
    "Müzik linklerini otomatik dönüştürün ve favori oynatıcınızda 0 tıkla açın.",
    """Bir arkadaşınız Spotify'dan bir şarkı gönderdi ama siz YouTube Music, Apple Music veya Deezer mı kullanıyorsunuz?

SongFlip, Android için akıllı ve sıfır tıklamalı müzik linki dönüştürücünüzdür. Kurulduktan sonra gelen müzik linkleri arka planda otomatik olarak dönüştürülür ve doğrudan tercih ettiğiniz müzik uygulamasında başlatılır.

İster Spotify'dan YouTube Music'e, ister Spotify'dan Apple Music'e veya Shazam linkleri olsun: SongFlip evrensel akıllı link dönüştürücünüz olarak şarkıyı anında açar.

🚀 ÖNE ÇIKAN ÖZELLİKLER:

• 8 Müzik Platformu & Shazam:
Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud ve Bandcamp arasında sorunsuz dönüşüm. Şimdi paylaşılan Shazam linkleri desteğiyle!

• Anında Oynatma (Instant Playback):
Boş arama sayfaları göstermek yerine parçayı doğrudan hedef oynatıcıda çalmaya başlar.

• Tam Albüm ve Çalma Listesi Desteği:
Tüm albümleri tanır ve tam parça listesini açar. Çalma listeleri kusursuzca aktarılır — paylaşılan müzikler için akıllı çalma listesi dönüştürücünüz.

• Hızlı Oynatıcı Seçici (Her Seferinde Sor):
Birden fazla müzik uygulaması mı kullanıyorsunuz? Müzik linki açtığınızda hedef oynatıcıyı anında seçmek için hızlı seçiciyi etkinleştirin.

• Akıllı Kurulum Asistanı (Android 12+):
Varsayılan bağlantı ayarlarını tek dokunuşla kolayca yapılandırın.

• Akıllı Pano Başlığı & Paylaşım Menüsü:
Bir link mi kopyaladınız? Doğrudan akıllı panodan açın veya WhatsApp, Telegram, Reddit ya da Instagram'dan SongFlip'e paylaşın.

• Evrensel Akıllı Web Bağlantıları:
Arkadaşlarınızın istedikleri platformda dinleyebilmesi için tarafsız bağlantılar (songflip.link) oluşturun.

• Hızlı Ayarlar Karosu & Kısayollar:
Yönlendirmeyi 15 dakika, 1 saat veya yarına kadar Android hızlı ayarlarından kolayca duraklatın.

• Material You Tasarım & Temalı Simge:
AMOLED karanlık mod, açık mod ve tek renkli ana ekran simgesiyle sistem renklerinize tam uyum sağlar.

🔒 GİZLİLİK VE AÇIK KAYNAK:
• %100 Açık Kaynak (GitHub'da GPLv3 lisanslı)
• Hesap, kayıt veya giriş gerekmez
• Sonsuza kadar tamamen reklamsız
• Sıfır izleme, dinleme alışkanlıkları kaydedilmez

Songlink / Odesli uyumludur. SongFlip'i indirin ve platformlar arası müzik özgürlüğünün tadını çıkarın!"""
)

# --- 日本語 (ja-JP) ---
LISTINGS["ja-JP"] = (
    "日本語",
    "SongFlip: 音楽リンク変換",
    "音楽リンクをお気に入りのプレーヤーで自動変換して開く（0クリック・無料）。",
    """友達からSpotifyのリンクが送られてきたのに、あなたが使っているのはYouTube MusicやApple Musicですか？

SongFlipは、Android向けのシームレスな自動音楽リンク変換リダイレクターです。一度設定すれば、受信した音楽リンクがバックグラウンドで自動的に変換され、お気に入りの音楽アプリですぐに再生されます。手動検索やブラウザの中継ページは一切不要です。

SpotifyからYouTube Music、SpotifyからApple Music、Shazamの共有リンクまで、SongFlipなら万能なスマートリンク変換ツールとして瞬時に開きます。

🚀 主な機能：

• 8つの主要ストリーミングサービス＆Shazam対応：
Spotify、YouTube Music、Apple Music、Deezer、TIDAL、Amazon Music、SoundCloud、Bandcamp間でスムーズに変換。Shazamの共有曲リンクにも新対応！

• インスタント再生（Instant Playback）：
検索結果画面にとどまらず、目的のプレーヤー内で直接トラックを再生します。

• アルバム＆プレイリストの完全サポート：
アルバム全体を認識し、すべての収録曲を開きます。プレイリストも正確に検出 — 共有された音楽のためのスマートなプレイリスト変換機能。

• クイックプレーヤー選択（毎回確認）：
複数の音楽アプリをお使いですか？リンクを開くたびに目的のプレーヤーを即座に選べるクイック選択機能を搭載。

• スマートセットアップアシスタント（Android 12+）：
デフォルトアプリリンクの設定を、インタラクティブなアシスタントで簡単に解決できます。

• クリップボードスマートバナー＆共有メニュー：
リンクをコピーするだけでダッシュボードからワンタップ再生。WhatsApp、LINE、Telegram、Instagramから直接共有も可能です。

• ユニバーサルWebスマートリンク：
どのサービスを使っている友達にも送れる中立的な共有リンク（songflip.link）をワンタップで生成。

• クイック設定タイル＆アプリアイコンショートカット：
自動リダイレクトを15分、1時間、または翌朝までクイック設定から手軽に一時停止できます。

• Material You＆テーマアイコン：
AMOLEDダークモード、ライトモード、洗練されたモノクロホーム画面アイコンでAndroidのシステムカラーに完全調和。

🔒 プライバシー重視＆完全オープンソース：
• 100%オープンソース（GitHubにてGPLv3ライセンス公開）
• アカウント登録・ログイン一切不要
• 永久に完全広告なし
• トラッキングや聴取履歴の収集なし

Songlink / Odesli互換。今すぐSongFlipをダウンロードして、音楽サービスの壁を越えましょう！"""
)

# --- 한국어 (ko-KR) ---
LISTINGS["ko-KR"] = (
    "한국어",
    "SongFlip: 음악 링크 변환기",
    "음악 링크를 원하는 스트리밍 앱에서 바로 열어보세요 (0클릭, 100% 비공개).",
    """친구가 Spotify 링크를 보냈는데, 나는 YouTube Music이나 Apple Music을 사용하고 계신가요?

SongFlip은 Android를 위한 원클릭 자동 음악 링크 변환 앱입니다. 한 번 설정해 두면 수신된 음악 링크가 백그라운드에서 자동으로 변환되어 원하는 음악 플레이어에서 즉시 열립니다. 번거로운 검색이나 웹페이지 우회 과정이 전혀 없습니다.

Spotify에서 YouTube Music으로, Spotify에서 Apple Music으로, Shazam 공유 링크까지 SongFlip이 범용 스마트 링크 변환기로 즉시 해결합니다.

🚀 주요 기능:

• 8대 스트리밍 플랫폼 및 Shazam 지원:
Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud, Bandcamp 간 완벽 변환. Shazam 공유 링크 지원 추가!

• 즉시 재생 (Instant Playback):
빈 검색 결과 화면 대신 대상 플레이어에서 곡을 즉시 재생합니다.

• 앨범 및 재생목록 완벽 지원:
전체 앨범을 인식하여 전체 트랙 목록을 열고, 공유된 재생목록도 완벽 감지 — 공유된 음악을 위한 스마트 플레이리스트 변환기。

• 빠른 플레이어 선택 (매번 묻기):
여러 음악 앱을 함께 사용하시나요? 음악 링크를 열 때마다 원하는 플레이어를 즉시 선택할 수 있는 빠른 선택기를 지원합니다.

• 스마트 설정 도우미 (Android 12+):
인터랙티브 도우미를 통해 기본 앱 링크 설정을 한 번의 터치로 간편하게 구성할 수 있습니다.

• 클립보드 스마트 배너 및 공유 메뉴 연동:
음악 링크를 복사하면 대시보드에서 바로 실행하거나, 카카오톡, 텔레그램, 인스타그램 등에서 바로 SongFlip으로 공유하세요.

• 범용 웹 스마트 링크:
친구가 어떤 음악 앱을 사용하든 자유롭게 들을 수 있는 스마트 링크(songflip.link)를 생성합니다.

• 빠른 설정 타일 및 앱 바로가기:
Android 퀵 패널에서 15분, 1시간 또는 다음 날 아침까지 자동 리다이렉트를 간편하게 일시중지할 수 있습니다.

• Material You 디자인 및 테마 아이콘:
AMOLED 다크 모드, 라이트 모드 및 모노크롬 홈 화면 아이콘으로 시스템 테마에 완벽하게 어우러집니다.

🔒 개인정보 보호 및 오픈 소스:
• 100% 오픈 소스 (GitHub GPLv3 라이선스)
• 계정 생성, 로그인 불필요
• 광고 영구 무료
• 사용자 데이터 및 청취 습관 수집 없음

Songlink / Odesli 호환. 지금 SongFlip을 다운로드하고 플랫폼 장벽 없이 음악을 즐기세요!"""
)

# --- 中文 (zh-CN: 简体, zh-TW: 繁體) ---
ZH_CN_TUPLE = (
    "中文（简体）",
    "SongFlip: 音乐链接自动跳转",
    "无缝转换音乐链接并在您喜爱的播放器中直接打开（0次点击，安全无广告）。",
    """朋友在 Spotify 上给您分享了一首歌，但您平时使用的是 YouTube Music、Apple Music 或 Deezer？

SongFlip 是专为 Android 打造的无感音乐链接转换神器。只需一次设置，接收到的音乐链接将在后台自动转换，并直接在您偏好的音乐播放器中开启播放，无需手动搜索，告别中间跳转网页。

无论是 Spotify 转 YouTube Music、Spotify 转 Apple Music，还是 Shazam 听歌识曲链接，SongFlip 都能作为您的通用智能链接转换器秒级处理。

🚀 核心功能：

• 8大音乐平台与 Shazam 联动：
在 Spotify、YouTube Music、Apple Music、Deezer、TIDAL、Amazon Music、SoundCloud 和 Bandcamp 之间无缝切换。现已全新支持 Shazam 识别链接！

• 即刻播放 (Instant Playback)：
直接触发目标播放器开始播放，避免停留在无意义的搜索结果页面。

• 完整专辑与歌单支持：
精准识别整张专辑并打开完整曲目列表，智能识别共享歌单 — 专为分享音乐打造的智能歌单转换助手。

• 快速播放器选择器（每次询问）：
使用多个音乐应用？开启快速选择器，每次打开音乐链接时均可随心挑选目标播放器。

• 智能设置向导 (Android 12+)：
内置交互式设置助手，一键解决默认应用链接冲突，确保平滑跳转。

• 剪贴板智能横幅与系统分享菜单：
复制音乐链接即可在仪表盘一键开启，或从微信、Telegram、Reddit、Instagram 直接分享至 SongFlip 转换。

• 通用网页智能链接 (Smart Links)：
一键生成中立的网页分享链接 (songflip.link)，让您的好友在任何设备或平台都能轻松收听。

• 快捷设置磁贴与桌面快捷方式：
可通过下拉快捷设置磁贴，灵活暂停自动跳转 15 分钟、1 小时或直至明早。

• Material You 动态主题与单色图标：
完美适配系统取色（支持 AMOLED 深色模式、浅色模式）及 Android 桌面自适应单色图标。

🔒 隐私至上与开源透明：
• 100% 开源项目（基于 GitHub GPLv3 协议）
• 无需注册账号，无需登录
• 永远无任何商业广告
• 零跟踪，绝不收集或分析您的听歌习惯

兼容 Songlink / Odesli 协议。立即下载 SongFlip，打破流媒体平台壁垒，畅享自由音乐体验！"""
)
LISTINGS["zh-CN"] = ("中文（简体）", ZH_CN_TUPLE[1], ZH_CN_TUPLE[2], ZH_CN_TUPLE[3])

ZH_TW_TUPLE = (
    "中文（繁體）",
    "SongFlip: 音樂連結自動跳轉",
    "無縫轉換音樂連結並在您偏好的播放器中直接打開（0次點擊，安全無廣告）。",
    """朋友在 Spotify 上與您分享了一首歌，但您平時使用的是 YouTube Music、Apple Music 或 Deezer？

SongFlip 是專為 Android 打造的無感音樂連結轉換神器。只需一次設定，接收到的音樂連結將在後台自動轉換，並直接在您偏好的音樂播放器中開啟播放，無需手動搜尋，告別繁瑣的中轉網頁。

無論是 Spotify 轉 YouTube Music、Spotify 轉 Apple Music，還是 Shazam 辨識連結，SongFlip 都能作為您的通用智慧連結轉換器秒級處理。

🚀 核心功能：

• 8大音樂平台與 Shazam 支援：
在 Spotify、YouTube Music、Apple Music、Deezer、TIDAL、Amazon Music、SoundCloud 和 Bandcamp 之間無縫轉換。現已全新支援 Shazam 歌曲連結！

• 即刻播放 (Instant Playback)：
直接在目標播放器中開始播放，避免停留在空白的搜尋結果頁面。

• 完整專輯與播放清單支援：
精準辨識整張專輯並開啟完整曲目清單，智慧辨識共享播放清單 — 專為分享音樂打造的智慧歌單轉換工具。

• 快速播放器選擇器（每次詢問）：
使用多個音樂應用程式？開啟快速選擇器，每次開啟音樂連結時均可隨心挑選目標播放器。

• 智慧設定精靈 (Android 12+)：
內建互動式設定助手，一鍵解決預設應用程式連結衝突，確保順暢跳轉。

• 剪貼簿智慧橫幅與系統分享選單：
複製音樂連結即可在儀表板一鍵開啟，或從通訊軟體、Instagram 直接分享至 SongFlip 轉換。

• 通用網頁智慧連結 (Smart Links)：
一鍵產生中立的網頁分享連結 (songflip.link)，讓您的好友在任何設備或平台都能輕鬆收聽。

• 快捷設定磁貼與桌面捷徑：
可透過下拉快捷設定磁貼，靈活暫停自動跳轉 15 分鐘、1 小時或直至明晨。

• Material You 動態主題與單色圖示：
完美適配系統色彩（支援 AMOLED 深色模式、淺色模式）及 Android 桌面單色圖示。

🔒 隱私至上與開源透明：
• 100% 開源專案（基於 GitHub GPLv3 協議）
• 無需註冊帳號，無需登入
• 永遠完全無廣告
• 零追蹤，絕不收集或記錄您的聆聽習慣

相容於 Songlink / Odesli 協議。立即下載 SongFlip，打破串流平台壁壘，自由享受音樂！"""
)
LISTINGS["zh-TW"] = ("中文（繁體）", ZH_TW_TUPLE[1], ZH_TW_TUPLE[2], ZH_TW_TUPLE[3])

# --- HINDI (hi-IN) ---
LISTINGS["hi-IN"] = (
    "हिन्दी",
    "SongFlip: संगीत लिंक कनवर्टर",
    "म्यूजिक लिंक को अपने पसंदीदा प्लेयर में अपने आप खोलें (0-क्लिक, पूरी तरह निजी)।",
    """कोई दोस्त आपको Spotify पर गाना भेजता है, लेकिन आप YouTube Music, Apple Music या Deezer का उपयोग करते हैं?

SongFlip Android के लिए आपका स्मार्ट और स्वचालित म्यूजिक लिंक कनवर्टर है। एक बार सेट करने के बाद, प्राप्त संगीत लिंक बैकग्राउंड में अपने आप बदल जाते हैं और सीधे आपके पसंदीदा म्यूजिक प्लेयर में खुलते हैं — बिना किसी मैन्युअल खोज या विज्ञापनों के।

Spotify से YouTube Music हो, Spotify से Apple Music या Shazam के लिंक: SongFlip गानों को तुरंत आपके सार्वभौमिक स्मार्ट लिंक कनवर्टर के रूप में खोलता है।

🚀 मुख्य विशेषताएं:

• 8 स्ट्रीमिंग प्लेटफॉर्म और Shazam:
Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud और Bandcamp के बीच सहज बदलाव। अब Shazam लिंक के लिए भी उपलब्ध!

• तत्काल प्लेबैक (Instant Playback):
खाली खोज पृष्ठों के बजाय सीधे आपके पसंदीदा म्यूजिक प्लेयर में गाना बजाना शुरू करता है।

• संपूर्ण एल्बम और प्लेलिस्ट समर्थन:
पूरे एल्बम को पहचानता है और सभी गानों की सूची खोलता है। साझा की गई प्लेलिस्ट को आसानी से पहचानता है — साझा किए गए संगीत के लिए आपका विश्वसनीय प्लेलिस्ट कनवर्टर।

• त्वरित प्लेयर चयनकर्ता (हर बार पूछें):
क्या आप कई संगीत ऐप्स इस्तेमाल करते हैं? कोई भी संगीत लिंक खोलते समय तुरंत अपना पसंदीदा प्लेयर चुनने के लिए इस सुविधा को सक्षम करें।

• स्मार्ट सेटअप सहायक (Android 12+):
डिफ़ॉल्ट ऐप लिंक को आसानी से सेट करने के लिए हमारा इंटरैक्टिव 1-टैप सहायक।

• क्लिपबोर्ड स्मार्ट-बैनर और शेयर मेनू:
कॉपी किया गया लिंक स्मार्ट डैशबोर्ड से तुरंत खोलें या सीधे WhatsApp, Telegram, Instagram से SongFlip में शेयर करें।

• सार्वभौमिक वेब स्मार्ट लिंक (songflip.link):
तटस्थ वेब लिंक बनाएं ताकि आपके दोस्त अपनी पसंद के किसी भी ऐप में गाना सुन सकें।

• त्वरित सेटिंग्स टाइल और शॉर्टकट:
क्विक सेटिंग्स शेड से 15 मिनट, 1 घंटे या अगली सुबह तक ऑटो-रीडायरेक्ट को आसानी से रोकें।

• Material You डिज़ाइन और थीम्ड आइकन:
AMOLED डार्क मोड, लाइट मोड और मोनोक्रोम होम स्क्रीन आइकन के साथ सिस्टम रंगों का सुंदर समर्थन।

🔒 गोपनीयता और ओपन सोर्स:
• 100% ओपन सोर्स (GitHub पर GPLv3 लाइसेंस)
• कोई खाता या लॉगिन आवश्यक नहीं
• हमेशा के लिए पूरी तरह विज्ञापन-मुक्त
• कोई डेटा ट्रैकिंग नहीं

Songlink / Odesli के अनुकूल। SongFlip अभी डाउनलोड करें और बिना रुकावट संगीत का आनंद लें!"""
)

# --- BAHASA INDONESIA (id) ---
LISTINGS["id"] = (
    "Bahasa Indonesia",
    "SongFlip: Tautan Musik",
    "Buka tautan musik di pemutar favoritmu otomatis (0-klik, tanpa iklan).",
    """Temanmu mengirim lagu dari Spotify, tapi kamu mendengarkan di YouTube Music, Apple Music, atau Deezer?

SongFlip adalah pengalih dan konverter tautan musik otomatis untuk Android. Sekali diatur, tautan musik yang diterima akan otomatis dikonversi di latar belakang dan langsung terbuka di pemutar musik pilihanmu—tanpa pencarian manual atau halaman perantara yang mengganggu.

Dari Spotify ke YouTube Music, Spotify ke Apple Music, hingga tautan Shazam: SongFlip menyelesaikannya seketika sebagai konverter tautan pintar universal Anda.

🚀 FITUR UTAMA:

• 8 Platform Streaming & Shazam:
Konversi mulus antara Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud, dan Bandcamp. Sekarang mendukung tautan Shazam!

• Pemutaran Instan (Instant Playback):
Memulai pemutaran lagu langsung di pemutar tujuan tanpa membuka halaman pencarian kosong.

• Dukungan Penuh Album & Playlist:
Mendeteksi seluruh album dan daftar lagu lengkap. Playlist bersama terdeteksi akurat — pengonversi playlist andalan Anda untuk musik bersama.

• Pemilih Pemutar Cepat (Tanya setiap kali):
Menggunakan beberapa aplikasi musik? Aktifkan pemilih cepat untuk memilih pemutar tujuan secara instan setiap kali membuka tautan musik.

• Asisten Pengaturan Pintar (Android 12+):
Konfigurasikan tautan aplikasi bawaan dengan mudah melalui asisten interaktif 1-ketukan.

• Spanduk Pintar Papan Klip & Menu Berbagi:
Menyalin tautan musik? Buka langsung dari spanduk pintar atau bagikan dari WhatsApp, Telegram, Reddit, atau Instagram ke SongFlip.

• Tautan Pintar Web Universal (songflip.link):
Buat tautan netral agar teman-temanmu dapat mendengarkan di layanan streaming favorit mereka.

• Ubin Pengaturan Cepat & Pintasan:
Jeda pengalihan sementara selama 15 menit, 1 jam, atau hingga besok pagi langsung dari panel setelan cepat Android.

• Desain Material You & Ikon Bertema:
Menyesuaikan warna sistem ponselmu dengan mode gelap AMOLED, mode terang, dan ikon monokrom layar utama.

🔒 PRIVASI & SUMBER TERBUKA:
• 100% Open Source (lisensi GPLv3 di GitHub)
• Tanpa akun, tanpa registrasi, tanpa login
• Bebas iklan selamanya
• Tanpa pelacakan data atau kebiasaan mendengarkan

Kompatibel dengan Songlink / Odesli. Unduh SongFlip sekarang dan nikmati musik tanpa batas antar-platform!"""
)

# --- TIẾNG VIỆT (vi) ---
LISTINGS["vi"] = (
    "Tiếng Việt",
    "SongFlip: Chuyển Link Nhạc",
    "Tự động chuyển đổi và mở liên kết nhạc trong ứng dụng yêu thích (0 lần nhấp).",
    """Bạn bè gửi cho bạn một bài hát trên Spotify, nhưng bạn lại dùng YouTube Music, Apple Music hoặc Deezer?

SongFlip là ứng dụng chuyển đổi liên kết âm nhạc tự động dành cho Android. Chỉ cần thiết lập một lần, các liên kết nhận được sẽ tự động chuyển đổi trong nền và mở ngay trong trình phát nhạc ưa thích của bạn mà không cần tìm kiếm thủ công hay qua trang web trung gian.

Dù là Spotify sang YouTube Music, Spotify sang Apple Music hay liên kết từ Shazam: SongFlip mở bài hát ngay tức thì như một trình chuyển đổi liên kết thông minh vạn năng.

🚀 TÍNH NĂNG CHÍNH:

• 8 Nền tảng Âm nhạc & Shazam:
Chuyển đổi liền mạch giữa Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud và Bandcamp. Mới: Hỗ trợ nhận diện liên kết Shazam!

• Phát nhạc tức thì (Instant Playback):
Phát trực tiếp bài hát trong ứng dụng đích thay vì chỉ hiển thị trang kết quả tìm kiếm trống.

• Hỗ trợ toàn diện Album & Danh sách phát:
Nhận diện toàn bộ album và mở danh sách bài hát đầy đủ. Danh sách phát mở nhanh — trình chuyển đổi danh sách phát thông minh cho âm nhạc được chia sẻ.

• Bộ Chọn Trình Phát Nhanh (Hỏi mọi lần):
Sử dụng nhiều ứng dụng âm nhạc? Kích hoạt bộ chọn nhanh để chọn ngay trình phát mục tiêu mỗi khi bạn mở một liên kết âm nhạc.

• Trợ lý Cài đặt Thông minh (Android 12+):
Dễ dàng kiểm tra và thiết lập liên kết ứng dụng mặc định chỉ với một chạm.

• Biểu ngữ Khay nhớ tạm & Menu Chia sẻ:
Đã sao chép liên kết? Mở ngay từ biểu ngữ thông minh hoặc chia sẻ trực tiếp từ WhatsApp, Telegram, Instagram vào SongFlip.

• Liên kết Thông minh Đa năng (songflip.link):
Tạo liên kết web trung lập để bạn bè có thể nghe trên bất kỳ nền tảng nào họ thích.

• Ô Cài đặt Nhanh & Lối tắt:
Tạm dừng chuyển hướng linh hoạt trong 15 phút, 1 giờ hoặc đến sáng hôm sau từ bảng cài đặt nhanh.

• Giao diện Material You & Biểu tượng Đơn sắc:
Tương thích hoàn hảo với màu hệ thống Android (chế độ tối AMOLED, chế độ sáng) cùng biểu tượng màn hình chính đẹp mắt.

🔒 BẢO MẬT & MÃ NGUỒN MỞ:
• 100% Mã nguồn mở (Giấy phép GPLv3 trên GitHub)
• Không cần tài khoản, không cần đăng nhập
• Hoàn toàn không chứa quảng cáo
• Không theo dõi hoặc thu thập dữ liệu nghe nhạc

Tương thích với Songlink / Odesli. Tải ngay SongFlip và tận hưởng âm nhạc không giới hạn nền tảng!"""
)

# --- SVENSKA (sv-SE) ---
LISTINGS["sv-SE"] = (
    "Svenska",
    "SongFlip: Musiklänkar",
    "Konvertera och öppna musiklänkar automatiskt i din favoritapp (0 klick).",
    """Skickar en vän en låt på Spotify, men du lyssnar på YouTube Music, Apple Music eller Deezer?

SongFlip är din smarta musiklänkkonverterare och omdirigerare för Android. Efter en enkel inställning konverteras mottagna musiklänkar automatiskt i bakgrunden och öppnas direkt i din favoritapp – utan manuell sökning eller webbomvägar.

Från Spotify till YouTube Music, Spotify till Apple Music eller Shazam-länkar: SongFlip hanterar länkar direkt som din universella smartlänkkonverterare.

🚀 NYCKELFUNKTIONER:

• 8 Streamingplattformar & Shazam:
Konvertera sömlöst mellan Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud och Bandcamp. Nu även med stöd för Shazam-länkar!

• Direkt Uppspelning (Instant Playback):
Startar låten direkt i målspelaren istället för att visa tomma söksidor.

• Stöd för Album & Spellistor:
Känner igen hela album och öppnar låtlistan. Delade spellistor upptäcks sömlöst — din smarta spelliste-konverterare för delad musik.

• Snabbspelare-väljare (Fråga varje gång):
Använder du flera musikappar? Aktivera snabbväljaren för att direkt välja önskad spelare när du öppnar en musiklänk.

• Smart Konfigurationsassistent (Android 12+):
Ställ enkelt in standardapplänkar med vår interaktiva 1-trycksguide.

• Urklippsbanner & Delningsmeny:
Kopierat en länk? Öppna den direkt via instrumentpanelen eller dela från WhatsApp, Telegram eller Instagram till SongFlip.

• Universella Smarta Webblänkar (songflip.link):
Skapa neutrala länkar så att dina vänner kan lyssna på vilken musiktjänst de vill.

• Snabbinställningar & Genvägar:
Pausa omdirigeringen i 15 minuter, 1 timme eller till imorgon direkt från Androids snabbinställningar.

• Material You Design & Temad Ikon:
Anpassar sig till dina systemfärger med AMOLED-mörkt läge, ljust läge och monokrom hemskärmsikon.

🔒 INTEGRITET & ÖPPEN KÄLLKOD:
• 100% Open Source (GPLv3 på GitHub)
• Inga konton eller inloggningar krävs
• Helt reklamfritt för alltid
• Noll datainsamling eller spårning

Kompatibel med Songlink / Odesli. Ladda ner SongFlip nu och dela musik utan gränser!"""
)

# --- DANSK (da-DK) ---
LISTINGS["da-DK"] = (
    "Dansk",
    "SongFlip: Musiklinks",
    "Konverter og åbn musiklinks automatisk i din foretrukne afspiller (0 klik).",
    """Sender en ven dig et nummer på Spotify, men du bruger YouTube Music, Apple Music eller Deezer?

SongFlip er din automatiske musiklink-konverter til Android. Når appen er sat op, konverteres modtagne musiklinks automatisk i baggrunden og åbnes direkte i din foretrukne musik-app – uden manuel søgning eller irriterende mellemsider.

Uanset om det er Spotify til YouTube Music, Spotify til Apple Music eller Shazam-links: SongFlip håndterer links øjeblikkeligt som din universelle smartlink-konverter.

🚀 VIGTIGSTE FUNKTIONER:

• 8 Streamingplatforme & Shazam:
Konverterer gnidningsfrit mellem Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud og Bandcamp. Nu med Shazam-understøttelse!

• Øjeblikkelig Afspilning (Instant Playback):
Starter afspilningen direkte i mål-appen i stedet for at vise tomme søgeresultater.

• Fuld Album- & Playliste-understøttelse:
Genkender hele albums og åbner den fulde trackliste. Delte playlister genkendes præcist — din smarte playliste-konverter til delt musik.

• Hurtig Afspillervælger (Spørg hver gang):
Bruger du flere musikapps? Aktivér hurtigvælgeren for at vælge mål-afspiller med det samme, når du åbner et musiklink.

• Smart Konfigurationsguide (Android 12+):
Bekræft og konfigurer nemt standardlinks med vores interaktive 1-tryks guide.

• Udklipsholder-banner & Delingsmenu:
Kopieret et link? Åbn det direkte via den smarte banner eller del fra WhatsApp, Telegram eller Instagram til SongFlip.

• Universelle Web Smart-links (songflip.link):
Opret neutrale links, så dine venner kan lytte på deres foretrukne streamingtjeneste.

• Hurtigindstillinger & Genveje:
Sæt omdirigering på pause i 15 minutter, 1 time eller til i morgen direkte fra Android hurtigindstillinger.

• Material You Design & Tematiseret Ikon:
Følger dine systemfarver med AMOLED mørk tilstand, lys tilstand og monokromt ikon på startskærmen.

🔒 PRIVATLIV & OPEN SOURCE:
• 100% Open Source (GPLv3-licens på GitHub)
• Ingen konti, intet login nødvendigt
• Fuldstændig reklamefri for altid
• Ingen sporing eller dataindsamling

Kompatibel med Songlink / Odesli. Hent SongFlip nu og lyt til musik uden barrierer!"""
)

# --- NORSK (nb-NO) ---
LISTINGS["nb-NO"] = (
    "Norsk",
    "SongFlip: Musikklenker",
    "Konverter og åpne musikklenker automatisk i favorittappen din (0 klikk).",
    """Sender en venn en sang på Spotify, men du bruker YouTube Music, Apple Music eller Deezer?

SongFlip er din smarte musikklenkekonverterer for Android. Når den er aktivert, blir mottatte musikklenker automatisk konvertert i bakgrunnen og åpnet direkte i din foretrukne musikkspiller – uten manuell søking eller omveier i nettleseren.

Fra Spotify til YouTube Music, Spotify til Apple Music eller Shazam-lenker: SongFlip ordner lenker umiddelbart som din universelle smartlenkekonverterer.

🚀 NØKKELFUNKSJONER:

• 8 Strømmeplattformer & Shazam:
Konverterer sømløst mellom Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud og Bandcamp. Nå også med støtte for Shazam-lenker!

• Umiddelbar Avspilling (Instant Playback):
Starter avspillingen direkte i målappen i stedet for å vise tomme søkeresultatsider.

• Støtte for Album & Spillelister:
Gjenkjenner hele album og åpner hele sporlisten. Delte spillelister gjenkjennes sømløst — din smarte spilleliste-konverter for delt musikk.

• Hurtigspillervelger (Spør hver gang):
Bruker du flere musikkapper? Aktiver hurtigvelgeren for å velge målspiller umiddelbart når du åpner en musikklenke.

• Smart Konfigurasjonsassistent (Android 12+):
Konfigurer standardapp-lenker enkelt med vår interaktive veiviser.

• Utklippstavle-banner & Delemeny:
Kopiert en lenke? Åpne den direkte via dashbordet eller del fra WhatsApp, Telegram eller Instagram til SongFlip.

• Universelle Smarte Nettlenker (songflip.link):
Opprett nøytrale lenker slik at vennene dine kan lytte på hvilken som helst strømmetjeneste de foretrekker.

• Hurtiginnstillinger & Snarveier:
Sett viderekobling på pause i 15 minutter, 1 time eller til i morgen direkte fra Androids hurtiginnstillinger.

• Material You Design & Tematisk Ikon:
Følger systemfargene dine med AMOLED mørk modus, lys modus og monokromt startskjermikon.

🔒 PERSONVERN & ÅPEN KILDEKODE:
• 100% Open Source (GPLv3-lisens på GitHub)
• Ingen kontoer eller pålogging nødvendig
• Helt uten reklame for alltid
• Null sporing eller innsamling av lyttevaner

Kompatibel med Songlink / Odesli. Last ned SongFlip nå og del musikk på tvers av alle plattformer!"""
)

# Bengali (bn-BD)
LISTINGS["bn-BD"] = (
    "বাংলা",
    "SongFlip: মিউজিক লিঙ্ক",
    "মিউজিক লিঙ্ক স্বয়ংক্রিয়ভাবে প্রিয় প্লেয়ারে চালান (০ ক্লিক)।",
    """কোনো বন্ধু আপনাকে Spotify-এ একটি গান পাঠিয়েছে, কিন্তু আপনি YouTube Music, Apple Music বা Deezer ব্যবহার করেন?

SongFlip হলো Android-এর জন্য স্বয়ংক্রিয় মিউজিক লিঙ্ক কনভার্টার। সেট আপ করার পর, যেকোনো মিউজিক লিঙ্ক ব্যাকগ্রাউন্ডে কনভার্ট হয়ে সরাসরি আপনার প্রিয় প্লেয়ারে চালু হয় — কোনো ম্যানুয়াল সার্চ বা বিজ্ঞাপন ছাড়াই।

Spotify থেকে YouTube Music, Apple Music কিংবা Shazam-এর গান: SongFlip সার্বজনীন স্মার্ট লিঙ্ক রূপান্তরকারী হিসেবে মুহূর্তেই সমাধান করে।

🚀 মূল বৈশিষ্ট্য:

• ৮টি মিউজিক প্ল্যাটফর্ম ও Shazam:
Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud ও Bandcamp-এর মধ্যে লিঙ্ক রূপান্তর করুন। এখন Shazam লিঙ্কও সমর্থিত!

• ইনস্ট্যান্ট প্লেব্যাক (Instant Playback):
খালি সার্চ পেজের বদলে সরাসরি প্লেয়ারে গান শুরু হয়।

• অ্যালবাম ও প্লেলিস্ট সাপোর্ট:
সম্পূর্ণ অ্যালবাম এবং প্লেলিস্ট এক ট্যাপে সরাসরি চালু হয় — শেয়ার করা সঙ্গীতের জন্য আপনার স্মার্ট প্লেলিস্ট কনভার্টার।

• দ্রুত প্লেয়ার নির্বাচক (প্রতিবার জিজ্ঞাসা করুন):
একাধিক মিউজিক অ্যাপ ব্যবহার করেন? একটি লিঙ্ক খোলার সময় অবিলম্বে আপনার লক্ষ্য প্লেয়ার বেছে নিতে দ্রুত নির্বাচক সক্রিয় করুন।

• স্মার্ট সেটআপ অ্যাসিস্ট্যান্ট (Android 12+):
এক ক্লিকেই অ্যাপ লিঙ্ক অটোমেটিক ওপেন করার সহজ সেটআপ গাইড।

• ক্লিপবোর্ড স্মার্ট-ব্যানার ও শেয়ার মেনু:
লিঙ্ক কপি করে বা WhatsApp, Telegram, Instagram থেকে সরাসরি SongFlip-এ শেয়ার করে গান শুনুন।

• ইউনিভার্সাল স্মার্ট লিঙ্ক (songflip.link):
বন্ধুদের জন্য নিরপেক্ষ লিঙ্ক তৈরি করুন যাতে তারা যেকোনো অ্যাপে শুনতে পারে।

• কুইক সেটিংস ও শর্টকাট:
নোটিফিকেশন প্যানেল থেকে ১৫ মিনিট বা ১ ঘণ্টার জন্য রিডাইরেক্ট সাময়িক বন্ধ রাখুন।

• Material You ডিজাইন ও থিমড আইকন:
AMOLED ডার্ক মোড এবং সুন্দর হোমস্ক্রিন আইকন।

🔒 গোপনীয়তা ও ওপেন সোর্স:
• ১০০% ওপেন সোর্স (GitHub GPLv3)
• কোনো অ্যাকাউন্ট বা লগইন নেই
• সম্পূর্ণ বিজ্ঞাপনমুক্ত
• কোনো ট্র্যাকিং বা ডেটা সংগ্রহ নেই

SongFlip এখনই ডাউনলোড করুন এবং যেকোনো প্ল্যাটফর্মে গান শুনুন সহজে!"""
)

# Marathi (mr-IN)
LISTINGS["mr-IN"] = (
    "मराठी",
    "SongFlip: म्युझिक लिंक्स",
    "म्युझिक लिंक्स आपोआप तुमच्या आवडत्या प्लेयरमध्ये उघडा (शून्य क्लिक, खाजगी).",
    """एखाद्या मित्राने Spotify लिंक पाठवली, पण तुम्ही YouTube Music किंवा Apple Music वापरता?

SongFlip हे Android साठी स्वयंचलित म्युझिक लिंक कन्व्हर्टर आहे. लिंक मिळताच ती आपोआप तुमच्या आवडत्या ॲपमध्ये सुरू होते — मॅन्युअल शोध किंवा जाहिरातींशिवाय.

Spotify ते YouTube Music, Apple Music किंवा Shazam: SongFlip सर्व गाणी युनिव्हर्सल स्मार्ट लिंक कन्व्हर्टर म्हणून लगेच सुरू करते.

🚀 मुख्य वैशिष्ट्ये:

• ८ प्लॅटफॉर्म्स आणि Shazam:
Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud आणि Bandcamp सपोर्ट. आता Shazam लिंक्स देखील सपोर्टेड!

• इन्स्टंट प्लेबॅक:
सर्च निकालांऐवजी गाणे थेट प्लेयरमध्ये प्ले होते.

• अल्बम आणि प्लेलिस्ट सपोर्ट:
संपूर्ण अल्बम आणि प्लेलिस्ट एका टॅपमध्ये उघडा — शेअर केलेल्या संगीतासाठी तुमचे स्मार्ट प्लेलिस्ट कन्व्हर्टर.

• जलद प्लेअर निवडक (प्रत्येक वेळी विचारा):
अनेक संगीत ॲप्स वापरता? कोणताही संगीत दुवा उघडताना त्वरित तुमचे आवडते प्लेअर निवडण्यासाठी ही सुविधा सक्षम करा.

• स्मार्ट सेटअप असिस्टंट (Android 12+):
अॅप लिंक्स सहजपणे सेट करण्यासाठी १-टॅप मार्गदर्शक.

• क्लिपबोर्ड स्मार्ट-बॅनर आणि शेअर मेनू:
लिंक कॉपी करा किंवा WhatsApp, Telegram वरून थेट SongFlip वर शेअर करा.

• युनिव्हर्सल वेब लिंक्स (songflip.link):
मित्रांसाठी तटस्थ लिंक्स तयार करा.

• क्विक सेटिंग्स आणि शॉर्टकट:
१५ मिनिटे किंवा १ तासासाठी रीडायरेक्ट सहज थांबवा.

• Material You डिझाइन आणि थीम असलेला आयकॉन:
सिस्टम रंगांशी सुसंगत, AMOLED डार्क मोड.

🔒 गोपनीयता आणि ओपन सोर्स:
• १००% ओपन सोर्स (GitHub GPLv3)
• लॉगिन किंवा खात्याची गरज नाही
• कायमस्वरूपी जाहिरातमुक्त

आता SongFlip डाउनलोड करा आणि प्लॅटफॉर्मच्या बंधनांशिवाय संगीत ऐका!"""
)


# --- CZECH (cs-CZ) ---
LISTINGS["cs-CZ"] = (
    "Čeština",
    "SongFlip: Převodník odkazů",
    "Automaticky převádějte a otevírejte hudební odkazy v oblíbené aplikaci.",
    """Poslal vám kamarád odkaz na skladbu ze Spotify, ale vy posloucháte na YouTube Music, Apple Music nebo Deezeru?

SongFlip je váš chytrý převodník hudebních odkazů pro Android. Po jednorázovém nastavení se příchozí hudební odkazy automaticky převedou na pozadí a otevřou přímo ve vašem preferovaném přehrávači – bez ručního vyhledávání, bez reklam a bez zbytečných meziwebů.

Ať už jde o převod ze Spotify do YouTube Music, Spotify do Apple Music nebo sdílené odkazy ze Shazam: SongFlip okamžitě rozpozná skladbu a poslouží jako univerzální inteligentní převodník pro jakýkoli přehrávač.

🚀 KLÍČOVÉ FUNKCE:

• 8 streamovacích platforem a Shazam:
Plynule převádí skladby a alba mezi Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud a Bandcamp. Nově s okamžitou podporou pro sdílené odkazy ze Shazam!

• Okamžité přehrávání (Instant Playback):
Skladby se spustí přímo v cílovém přehrávači, namísto otevření prázdné stránky s výsledky vyhledávání.

• Plná podpora alb a playlistů:
Rozpozná celá alba a otevře kompletní diskografii. Odkazy na playlisty jsou přesně detekovány – váš chytrý převodník playlistů pro sdílenou hudbu.

• Rychlý výběr přehrávače (Vždy se zeptat):
Používáte více hudebních aplikací? Povolte volitelný rychlý výběr a zvolte cílový přehrávač při každém otevření odkazu.

• Průvodce nastavením (Android 12+):
Integrovaný průvodce zkontroluje nainstalované aplikace a pomůže vám jedním klepnutím nastavit výchozí odkazy bez konfliktů.

• Chytrý banner schránky a nabídka sdílení:
Zkopírujte odkaz nebo jej sdílejte přímo z aplikací WhatsApp, Telegram, Instagram či Reddit do SongFlip pro okamžitý převod na 1 kliknutí.

• Univerzální webové odkazy (Smart Links):
Vytvořte jedním klepnutím neutrální webové odkazy (songflip.link), které vaši přátelé mohou otevřít na libovolném zařízení a ve své oblíbené streamovací službě.

• Rychlá nastavení a zkratky aplikace:
Pozastavte automatické přesměrování flexibilně na 15 minut, 1 hodinu nebo do zítřejšího rána pomocí dlaždice Rychlého nastavení.

• Material You design a tematická ikona:
Plně přizpůsobeno barevnému schématu vašeho systému Android (Dynamic Color, světlý a AMOLED tmavý režim) včetně čisté monochromatické ikony.

🔒 SOUKROMÍ, TRANSPARENTNOST A OPEN SOURCE:
• 100% Open Source (licence GPLv3 na GitHubu)
• Žádné uživatelské účty, registrace ani přihlašování
• Zcela bez reklam – navždy
• Žádné sledování, žádná analýza vašeho hudebního vkusu

Kompatibilní se Songlink / Odesli. Stáhněte si SongFlip a užijte si hudební svobodu napříč všemi platformami!"""
)

# --- GREEK (el-GR) ---
LISTINGS["el-GR"] = (
    "Ελληνικά",
    "SongFlip: Σύνδεσμοι Μουσικής",
    "Αυτόματη μετατροπή και άνοιγμα συνδέσμων στην αγαπημένη σας εφαρμογή (0 κλικ).",
    """Ένας φίλος σάς στέλνει τραγούδι στο Spotify, αλλά εσείς ακούτε μουσική στο YouTube Music, στο Apple Music ή στο Deezer;

Το SongFlip είναι ο έξυπνος μετατροπέας συνδέσμων μουσικής για Android με 0 κλικ. Μόλις ρυθμιστεί, οι σύνδεσμοι μουσικής μετατρέπονται αυτόματα στο παρασκήνιο και ανοίγουν απευθείας στην εφαρμογή που προτιμάτε – χωρίς χειροκίνητη αναζήτηση, χωρίς διαφημίσεις και χωρίς ενδιάμεσες ιστοσελίδες.

Είτε μετατρέπετε από Spotify σε YouTube Music, από Spotify σε Apple Music είτε ανοίγετε συνδέσμους από το Shazam: Το SongFlip διαχειρίζεται τα τραγούδια άμεσα ως ο καθολικός σας έξυπνος μετατροπέας.

🚀 ΚΥΡΙΑ ΧΑΡΑΚΤΗΡΙΣΤΙΚΑ:

• 8 Πλατφόρμες Streaming & Shazam:
Ομαλή μετατροπή κομματιών και άλμπουμ σε Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud και Bandcamp. Νέα υποστήριξη για κοινόχρηστους συνδέσμους Shazam!

• Άμεση αναπαραγωγή (Instant Playback):
Ξεκινά απευθείας την αναπαραγωγή στο πρόγραμμα της επιλογής σας αντί να προσγειώνεται σε κενές σελίδες αναζήτησης.

• Πλήρης υποστήριξη άλμπουμ & λιστών:
Αναγνωρίζει ολόκληρα άλμπουμ και ανοίγει την πλήρη κυκλοφορία. Οι κοινόχρηστες λίστες αναπαραγωγής εντοπίζονται καθαρά – ο ιδανικός μετατροπέας λιστών μουσικής.

• Γρήγορη επιλογή προγράμματος (Ερώτηση κάθε φορά):
Χρησιμοποιείτε πολλές εφαρμογές μουσικής; Ενεργοποιήστε τη γρήγορη επιλογή για να διαλέγετε τον επιθυμητό προορισμό κατά το άνοιγμα συνδέσμων.

• Έξυπνος οδηγός ρύθμισης (Android 12+):
Ρυθμίστε εύκολα τους συνδέσμους εφαρμογών με ένα πάτημα χωρίς διενέξεις.

• Έξυπνο banner προχείρου & Μενού κοινής χρήσης:
Αντιγράψτε έναν σύνδεσμο ή κοινοποιήστε τον απευθείας από WhatsApp, Telegram, Instagram ή Reddit στο SongFlip για άμεση μετατροπή με 1 κλικ.

• Καθολικοί σύνδεσμοι Web (Smart Links):
Δημιουργήστε με ένα πάτημα ουδέτερους συνδέσμους ιστού (songflip.link) που οι φίλοι σας μπορούν να ανοίξουν σε οποιαδήποτε συσκευή.

• Πλακίδιο γρήγορων ρυθμίσεων & Συντομεύσεις:
Κάντε παύση της ανακατεύθυνσης για 15 λεπτά, 1 ώρα ή μέχρι το επόμενο πρωί από τις Γρήγορες ρυθμίσεις.

• Σχεδίαση Material You & Θεματικό εικονίδιο:
Πλήρης προσαρμογή στα χρώματα του συστήματός σας (Dynamic Color, Light & AMOLED Dark mode).

🔒 ΑΠΟΡΡΗΤΟ & OPEN SOURCE:
• 100% Open Source (άδεια GPLv3 στο GitHub)
• Χωρίς λογαριασμούς, συνδέσεις ή εγγραφές
• Εντελώς χωρίς διαφημίσεις – για πάντα
• Μηδενική παρακολούθηση δεδομένων

Συμβατό με Songlink / Odesli. Κατεβάστε το SongFlip τώρα!"""
)

# --- FINNISH (fi-FI) ---
LISTINGS["fi-FI"] = (
    "Suomi",
    "SongFlip: Musiikkilinkit",
    "Muunna ja avaa musiikkilinkit automaattisesti suosikkisoittimessasi.",
    """Ystävä lähettää sinulle kappaleen Spotifyssa, mutta kuuntelet musiikkia YouTube Musicissa, Apple Musicissa tai Deezerissä?

SongFlip on saumaton ja automaattinen musiikkilinkkien muunnin ja uudelleenohjaus Androidille. Kun asetus on tehty, vastaanotetut linkit muunnetaan automaattisesti taustalla ja avataan suoraan haluamassasi soittimessa – ilman manuaalista hakua, mainoksia tai selaimen välisivuja.

Olipa kyseessä Spotify YouTube Musiciin, Spotify Apple Musiciin tai Shazam-linkkien avaaminen: SongFlip käsittelee musiikkilinkit heti universaalina älykkäänä muuntimena.

🚀 TÄRKEIMMÄT OMINAISUUDET:

• 8 suoratoistopalvelua & Shazam:
Muuntaa kappaleet ja albumit saumattomasti Spotifyn, YouTube Musicin, Apple Musicin, Deezerin, TIDALin, Amazon Musicin, SoundCloudin ja Bandcampin välillä. Mukana uusi välitön tuki jaetuille Shazam-linkeille!

• Välitön toisto (Instant Playback):
Käynnistää toiston suoraan kohdesoittimessa tyhjien hakutulossivujen sijaan.

• Täysi albumi- ja soittolistatuki:
Tunnistaa kokonaiset albumit ja avaa koko julkaisun. Jaetut soittolistat havaitaan tarkasti – kätevä soittolistojen muunnin.

• Nopea soittimen valitsin (Kysy joka kerta):
Käytätkö useita musiikkisovelluksia? Ota käyttöön pikavalitsin valitaksesi kohdesoittimen lennossa aina kun avaat musiikkilinkin.

• Älykäs asennusapuri (Android 12+):
Ohjattu asennus tarkistaa asennetut sovellukset ja auttaa määrittämään oletuslinkit yhdellä napautuksella ilman ristiriitoja.

• Leikepöydän älypalkki & Jaa-valikko:
Kopioi linkki tai jaa se suoraan WhatsAppista, Telegramista, Instagramista tai Redditistä SongFlipiin välitöntä 1 klikkauksen muunnosta varten.

• Universaalit verkkolinkit (Smart Links):
Luo yhdellä napautuksella neutraaleja verkkolinkkejä (songflip.link), jotka ystäväsi voivat avata millä tahansa laitteella ja musiikkipalvelulla.

• Pika-asetuskuvake & Sovelluksen pikakuvakkeet:
Keskeytä automaattinen uudelleenohjaus kätevästi 15 minuutiksi, 1 tunniksi tai seuraavaan aamuun pika-asetuspaneelista.

• Material You -muotoilu & Teemoitettu kuvake:
Täysin Android-järjestelmäsi väreihin mukautuva ulkoasu (Dynamic Color, vaalea ja AMOLED-tumma tila).

🔒 TIETOSUOJA & AVOIN LÄHDEKOODI:
• 100 % avointa lähdekoodia (GPLv3-lisensoitu GitHubissa)
• Ei käyttäjätilejä, rekisteröitymistä tai kirjautumista
• Täysin mainokseton – ikuisesti
• Ei seurantaa tai kuuntelutottumusten tallentamista

Yhteensopiva Songlink / Odesli -palvelun kanssa. Lataa SongFlip nyt!"""
)

# --- HUNGARIAN (hu-HU) ---
LISTINGS["hu-HU"] = (
    "Magyar",
    "SongFlip: Zenei Linkek",
    "Zenei linkek automatikus átalakítása és megnyitása a kedvenc lejátszódban.",
    """Egy barátod megosztott veled egy dalt a Spotifyról, de te YouTube Musicot, Apple Musicot vagy Deezert használsz?

A SongFlip a te zökkenőmentes, 0-kattintásos zenei link-átalakítód és átirányítód Androidra. A beállítás után a beérkező zenei linkek automatikusan átalakulnak a háttérben, és közvetlenül a kívánt zenelejátszóban indulnak el – kézi keresés, reklámok és felesleges böngészős átirányítások nélkül.

Legyen szó Spotify-ról YouTube Musicra, Spotify-ról Apple Musicra vagy megosztott Shazam-linkekről: A SongFlip azonnal felismeri a dalt univerzális intelligens link-konvertálóként.

🚀 FŐBB JELLEMZŐK:

• 8 zenei platform és Shazam támogatása:
Zökkenőmentes konvertálás a Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud és Bandcamp között. Mostantól a megosztott Shazam-linkek azonnali feloldásával!

• Azonnali lejátszás (Instant Playback):
A zeneszám közvetlenül a céllejátszóban indul el, nem pedig egy üres keresési oldalon.

• Teljes album- és lejátszási lista támogatás:
Felismeri a teljes albumokat és megnyitja a teljes kiadványt. A megosztott lejátszási listákat is tisztán kezeli.

• Gyors lejátszóválasztó (Kérdezzen rá minden alkalommal):
Több zenei alkalmazást is használsz? Engedélyezd a gyorsválasztót a céllejátszó azonnali kiválasztásához a link megnyitásakor.

• Intelligens beállítási varázsló (Android 12+):
Segít egyetlen érintéssel konfliktusmentesen beállítani az alapértelmezett hivatkozásokat.

• Vágólap intelligens sáv & Megosztás menü:
Csak másolj ki egy linket, vagy oszd meg közvetlenül a WhatsApp, Telegram, Instagram vagy Reddit alkalmazásból a SongFlipbe az 1-kattintásos azonnali konvertáláshoz.

• Univerzális webes okoslinkek (Smart Links):
Hozz létre egyetlen érintéssel semleges webes linkeket (songflip.link), amelyeket a barátaid bármilyen eszközön és streaming szolgáltatásban megnyithatnak.

• Gyorsbeállítások csempe & Ikon parancsikonok:
Szüneteltesd az átirányítást rugalmasan 15 percre, 1 órára vagy másnap reggelig a Gyorsbeállítások panelen.

• Material You dizájn & Tematikus ikon:
Teljesen illeszkedik az Android rendszerszíneihez (Dynamic Color, világos és AMOLED sötét mód).

🔒 ADATVÉDELEM ÉS NYÍLT FORRÁSKÓD:
• 100% nyílt forráskód (GPLv3 licenc a GitHubon)
• Nincsenek felhasználói fiókok, nincs regisztráció
• Teljesen reklámmentes – örökre
• Nulla nyomon követés, nincsenek mentett adatok

Kompatibilis a Songlink / Odesli platformmal. Töltsd le a SongFlipet most!"""
)

# --- ROMANIAN (ro-RO) ---
LISTINGS["ro-RO"] = (
    "Română",
    "SongFlip: Redirecționare Link",
    "Convertește și deschide automat linkurile muzicale în playerul favorit.",
    """Un prieten îți trimite o melodie pe Spotify, dar tu asculți pe YouTube Music, Apple Music sau Deezer?

SongFlip este convertorul și redirecționerul tău inteligent de linkuri muzicale pentru Android, cu zero clicuri. Odată configurat, linkurile muzicale primite sunt convertite automat în fundal și se deschid direct în playerul tău preferat – fără căutare manuală, fără reclame și fără pagini intermediare în browser.

Fie că treci de la Spotify la YouTube Music, de la Spotify la Apple Music sau deschizi piese din Shazam: SongFlip rezolvă linkurile instantaneu ca convertor universal.

🚀 FUNCȚII PRINCIPALE:

• 8 platforme de streaming și Shazam:
Convertește fără efort piese și albume între Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud și Bandcamp. Nou adăugat: suport instantaneu pentru linkurile Shazam!

• Redare instantanee (Instant Playback):
Pornește redarea direct în playerul țintă, în loc să afișeze pagini goale de căutare.

• Suport complet pentru albume și playlisturi:
Recunoaște albume întregi și deschide întreaga discografie. Playlisturile partajate sunt detectate precis.

• Selector rapid de player (Întreabă de fiecare dată):
Folosești mai multe aplicații de muzică? Activează selectorul rapid pentru a alege playerul țintă din mers.

• Asistent inteligent de configurare (Android 12+):
Te ajută să configurezi linkurile implicite cu o singură atingere, fără conflicte.

• Banner inteligent pentru clipboard & Meniu de partajare:
Copiază un link sau partajează-l direct din WhatsApp, Telegram, Instagram sau Reddit în SongFlip pentru conversie instantanee cu 1 clic.

• Linkuri web universale (Smart Links):
Creează cu o singură atingere linkuri web neutre (songflip.link) pe care prietenii tăi le pot deschide pe orice dispozitiv și serviciu.

• Buton în Setări Rapide & Comenzi rapide:
Pune pe pauză redirecționarea pentru 15 minute, 1 oră sau până a doua zi dimineață din panoul de Setări Rapide.

• Design Material You & Pictogramă tematică:
Adaptat complet la culorile sistemului tău Android (Dynamic Color, mod luminos și mod întunecat AMOLED).

🔒 CONFIDENȚIALITATE & OPEN SOURCE:
• 100% Open Source (licențiat GPLv3 pe GitHub)
• Fără conturi, fără înregistrare, fără autentificare
• Complet fără reclame – pentru totdeauna
• Fără urmărire sau analiză a preferințelor muzicale

Compatibil cu Songlink / Odesli. Descarcă SongFlip acum!"""
)

# --- THAI (th-TH) ---
LISTINGS["th-TH"] = (
    "ไทย",
    "SongFlip: ตัวแปลงลิงก์เพลง",
    "แปลงและเปิดลิงก์เพลงในเครื่องเล่นโปรดของคุณโดยอัตโนมัติ (0 คลิก ไม่มีโฆษณา)",
    """เพื่อนส่งลิงก์เพลงบน Spotify ให้คุณ แต่คุณฟังเพลงบน YouTube Music, Apple Music หรือ Deezer?

SongFlip คือตัวแปลงและเปลี่ยนเส้นทางลิงก์เพลงอัตโนมัติแบบ 0 คลิกสำหรับ Android เมื่อตั้งค่าแล้ว ลิงก์เพลงที่ได้รับจะแปลงในเบื้องหลังโดยอัตโนมัติและเปิดเล่นในแอปเพลงที่คุณต้องการทันที – ไม่ต้องค้นหาด้วยตนเอง ไม่มีโฆษณา และไม่มีหน้าเว็บคั่นให้รำคาญใจ

ไม่ว่าจะเป็นการสลับจาก Spotify ไปยัง YouTube Music, Spotify ไปยัง Apple Music หรือลิงก์ที่แชร์จาก Shazam: SongFlip จะเปิดเพลงให้ทันทีในฐานะตัวแปลงสมาร์ทลิงก์สากล

🚀 ฟีเจอร์หลัก:

• รองรับ 8 แพลตฟอร์มเพลงชั้นนำและ Shazam:
แปลงแทร็กและอัลบั้มระหว่าง Spotify, YouTube Music, Apple Music, Deezer, TIDAL, Amazon Music, SoundCloud และ Bandcamp ได้อย่างราบรื่น รองรับลิงก์เพลงที่แชร์จาก Shazam ได้ทันที!

• เล่นเพลงทันที (Instant Playback):
เปิดเล่นเพลงในเครื่องเล่นเป้าหมายโดยตรง แทนที่จะค้างอยู่ที่หน้าผลการค้นหาว่างเปล่า

• รองรับทั้งอัลบั้มและเพลย์ลิสต์:
ตรวจจับอัลบั้มเต็มและเปิดผลงานเพลงทั้งหมด ลิงก์เพลย์ลิสต์ที่แชร์จะได้รับการตรวจจับอย่างแม่นยำ

• หน้าต่างเลือกเครื่องเล่นด่วน (ถามทุกครั้ง):
ใช้แอปเพลงหลายแอปใช่ไหม? เปิดใช้งานหน้าต่างเลือกด่วนเพื่อเลือกเครื่องเล่นเป้าหมายได้ทันทีเมื่อเปิดลิงก์เพลง

• ตัวช่วยตั้งค่าอัจฉริยะ (Android 12+):
ตั้งค่าลิงก์แอปเริ่มต้นได้อย่างง่ายดายด้วยการแตะเพียงครั้งเดียว

• แถบตรวจจับคลิปบอร์ด & เมนูแชร์:
คัดลอกลิงก์หรือแชร์โดยตรงจาก WhatsApp, Telegram, Instagram, LINE หรือ Reddit ไปยัง SongFlip เพื่อแปลงได้ทันทีใน 1 คลิก

• ลิงก์เว็บสากล (Smart Links):
สร้างลิงก์เว็บที่เป็นกลาง (songflip.link) ได้ด้วยการแตะเพียงครั้งเดียว เพื่อให้เพื่อนๆ เปิดบนอุปกรณ์และแอปเพลงใดก็ได้

• ไทล์การตั้งค่าด่วน & ทางลัดแอป:
หยุดการเปลี่ยนเส้นทางชั่วคราวได้อย่างยืดหยุ่นเป็นเวลา 15 นาที 1 ชั่วโมง หรือจนถึงเช้าวันถัดไป

• ดีไซน์ Material You & ไอคอนตามธีม:
ปรับแต่งตามโทนสีของระบบ Android อย่างสมบูรณ์แบบ (Dynamic Color, โหมดสว่าง และโหมดมืด AMOLED)

🔒 ความเป็นส่วนตัว & โอเพ่นซอร์ส:
• โอเพ่นซอร์ส 100% (ลิขสิทธิ์ GPLv3 บน GitHub)
• ไม่ต้องมีบัญชี ไม่ต้องลงทะเบียน ไม่ต้องเข้าสู่ระบบ
• ไม่มีโฆษณาตลอดไป
• ไม่มีการติดตามพฤติกรรมการฟังเพลง

รองรับ Songlink / Odesli ดาวน์โหลด SongFlip เลยวันนี้!"""
)

print(f"Total locales mapped: {len(LISTINGS)}")

# Validation check
for code, data in LISTINGS.items():
    lang, title, short, full = data
    if len(title) > 30:
        print(f"WARNING: {code} Title too long ({len(title)}): {title}")
    if len(short) > 80:
        print(f"WARNING: {code} Short too long ({len(short)}): {short}")
    if len(full) > 4000:
        print(f"WARNING: {code} Full too long ({len(full)})")

# 1. Generate clean markdown file
md_file = os.path.join(DIST_DIR, "playstore_listings_all_languages.md")
with open(md_file, "w", encoding="utf-8") as f:
    f.write("# 📱 SongFlip – Play Store Listings (Alle Sprachen)\n\n")
    f.write("> **Version:** 1.3.1 | **Umfang:** 34 Store-Sprachen | **Zertifiziert:** Titel ≤ 30 Zeichen, Kurzbeschreibung ≤ 80 Zeichen, Volltext ≤ 4.000 Zeichen.\n\n")
    f.write("---\n\n")
    for code, data in sorted(LISTINGS.items()):
        lang, title, short, full = data
        f.write(f"## {code} – {lang}\n\n")
        f.write(f"### Titel ({len(title)}/30 Zeichen)\n```text\n{title}\n```\n\n")
        f.write(f"### Kurzbeschreibung ({len(short)}/80 Zeichen)\n```text\n{short}\n```\n\n")
        f.write(f"### Ausführliche Beschreibung ({len(full)}/4000 Zeichen)\n```text\n{full}\n```\n\n")
        f.write("---\n\n")

print(f"✓ Saved Markdown to {md_file}")

# 2. Generate Google Play Console CSV Import File
csv_file = os.path.join(DIST_DIR, "playstore_listings_all_languages.csv")
with open(csv_file, "w", encoding="utf-8", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["Language", "Title", "Short description", "Full description"])
    for code, data in sorted(LISTINGS.items()):
        lang, title, short, full = data
        writer.writerow([code, title, short, full])

print(f"✓ Saved CSV to {csv_file}")


# 3. Synchronize individual fastlane metadata folders for Android
FASTLANE_ANDROID_DIR = os.path.join(BASE_DIR, "fastlane", "metadata", "android")
if os.path.exists(FASTLANE_ANDROID_DIR):
    FASTLANE_DIR_MAP = {
        "bn-BD": ["bn-IN"],
        "id": ["id-ID"],
        "uk": ["uk-UA"],
        "vi": ["vi-VN"],
        "nb-NO": ["nb-NO", "no-NO"],
        "ro-RO": ["ro-RO", "ro"],
        "cs-CZ": ["cs-CZ"],
        "el-GR": ["el-GR"],
        "fi-FI": ["fi-FI"],
        "hu-HU": ["hu-HU"],
        "th-TH": ["th-TH"],
    }
    synced_dirs = set()
    for code, data in LISTINGS.items():
        lang, title, short, full = data
        target_dirs = FASTLANE_DIR_MAP.get(code, [code])
        for target_name in target_dirs:
            target_path = os.path.join(FASTLANE_ANDROID_DIR, target_name)
            if os.path.exists(target_path):
                synced_dirs.add(target_name)
                with open(os.path.join(target_path, "title.txt"), "w", encoding="utf-8") as f:
                    f.write(title + "\n")
                with open(os.path.join(target_path, "short_description.txt"), "w", encoding="utf-8") as f:
                    f.write(short + "\n")
                with open(os.path.join(target_path, "full_description.txt"), "w", encoding="utf-8") as f:
                    f.write(full + "\n")

    # Update remaining directories with clean English text
    en_data = LISTINGS["en-US"]
    for d in os.listdir(FASTLANE_ANDROID_DIR):
        full_p = os.path.join(FASTLANE_ANDROID_DIR, d)
        if os.path.isdir(full_p) and d not in synced_dirs:
            with open(os.path.join(full_p, "title.txt"), "w", encoding="utf-8") as f:
                f.write(en_data[1] + "\n")
            with open(os.path.join(full_p, "short_description.txt"), "w", encoding="utf-8") as f:
                f.write(en_data[2] + "\n")
            with open(os.path.join(full_p, "full_description.txt"), "w", encoding="utf-8") as f:
                f.write(en_data[3] + "\n")

    print(f"✓ Synchronized fastlane metadata in {FASTLANE_ANDROID_DIR}")
