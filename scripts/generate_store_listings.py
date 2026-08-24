import os

BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'fastlane', 'metadata', 'android'))
os.makedirs(BASE_DIR, exist_ok=True)

# Master templates
LISTINGS = {
    'en-US': {
        'title': 'SongFlip: Music Link Redirect',
        'shortDescription': 'Auto-convert & redirect music links to Spotify, YouTube Music, Apple Music & more.',
        'fullDescription': """Friends send you Spotify, Apple Music, or Tidal links – but you use YouTube Music, Deezer, or Amazon Music?

SongFlip is the automatic, 0-click music link redirector for Android. Set it up once, and whenever someone shares a music link in WhatsApp, Telegram, Instagram, or your browser, SongFlip instantly intercepts and converts it to open directly in your preferred music app – with zero clicks, no copying, and no intermediate screens.

🎵 Supported Music Services (Any-to-Any Redirection):
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Key Features:
• ⚡ 0-Click Background Redirect: Intercepts links seamlessly and launches direct playback.
• 💿 Full Album & Artist Recognition: Converts individual songs, complete albums, EPs, and artist discographies.
• 🚀 Direct Playback Engine: Instantly launches exact video and track IDs without manual searching.
• 🛡️ 100% Privacy & Zero Tracking: No account or login required. No tracking IDs, no advertising trackers, and no listening habits collected.
• ⏸️ Quick Settings Tile & Smart Pause: Suspend link redirection anytime directly from your Android notification shade (for 15 min, 1 hour, or until tomorrow morning).
• 🧪 Built-in Test Studio: Test and convert music links manually directly inside the app.
• 💎 Optional SongFlip PRO: Extended history of up to 100 songs and ultra-fast anonymous L2 server caching (< 50ms) to support independent development.

Built with passion for music lovers who want to enjoy songs in their favorite player without friction."""
    },
    'de-DE': {
        'title': 'SongFlip: Musik-Link Umleitung',
        'shortDescription': 'Musik-Links automatisch in Spotify, YouTube Music, Apple Music & mehr öffnen.',
        'fullDescription': """Freunde schicken dir Musik-Links von Spotify, Apple Music oder Tidal – aber du nutzt YouTube Music, Deezer oder Amazon Music?

SongFlip ist die automatische 0-Click Musik-Link-Weiterleitung für Android. Einmal eingerichtet, leitet SongFlip geteilte Musik-Links aus WhatsApp, Telegram, Instagram oder dem Browser blitzschnell und vollautomatisch in deinen bevorzugten Musik-Player um – ganz ohne manuelle Zwischenschritte, Kopieren oder störende Menüs.

🎵 Unterstützte Musikdienste (Jeder-zu-Jeder-Umleitung):
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Hauptfunktionen:
• ⚡ 0-Click Hintergrund-Weiterleitung: Fängt Links transparent ab und startet direkt die Wiedergabe.
• 💿 Alben & Interpreten-Erkennung: Unterstützt Einzelsongs, vollständige Alben, EPs und Künstler-Diskografien.
• 🚀 Direkte Wiedergabe: Startet exakte Track- und Video-IDs sofort ohne Umweg über Suchergebnisse.
• 🛡️ 100 % Privatsphäre & Kein Tracking: Kein Account und kein Login erforderlich. Keine Werbe-IDs, keine Tracker und keine Speicherung von Hörgewohnheiten.
• ⏸️ Schnelleinstellungs-Kachel & Smarte Pause: Umleitung jederzeit direkt in der Android-Statusleiste pausieren (für 15 Min., 1 Std. oder bis morgen früh).
• 🧪 Integriertes Test-Studio: Links direkt in der App manuell testen und umwandeln.
• 💎 Optionales SongFlip PRO: Erweiterter 100-Song-Verlauf und ultraschneller anonymer L2-Server-Cache (< 50 ms) zur Unterstützung unabhängiger App-Entwicklung.

Entwickelt für alle Musikliebhaber, die Musik nahtlos in ihrer Lieblings-App genießen möchten."""
    },
    'da-DK': {
        'title': 'SongFlip: Musiklink Omdiriger',
        'shortDescription': 'Åbn automatisk musiklinks i Spotify, YouTube Music, Apple Music og mere.',
        'fullDescription': """Venner sender dig Spotify-, Apple Music- eller Tidal-links – men du bruger YouTube Music, Deezer eller Amazon Music?

SongFlip er den automatiske musiklink-omdirigering med 0 klik til Android. Sæt den op én gang, og hver gang nogen deler et musiklink i WhatsApp, Telegram, Instagram eller browseren, omdirigerer SongFlip det øjeblikkeligt til din foretrukne musikafspiller.

🎵 Understøttede musiktjenester:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Nøglefunktioner:
• ⚡ 0-klik baggrundsomdirigering: Starter afspilning direkte.
• 💿 Album- & kunstnergenkendelse: Enkeltsange, hele albums og diskografier.
• 🚀 Direkte afspilningsmotor: Finder præcise spor uden manuelle søgninger.
• 🛡️ 100% privatliv & ingen sporing: Ingen konti, ingen reklamesporing, ingen logning af vaner.
• ⏸️ Kvikmenu-knap & smart pause: Pause omdirigering når som helst fra notifikationspanelet.
• 🧪 Indbygget teststudie: Test og konverter links manuelt i appen.
• 💎 Valgfri SongFlip PRO: Udvidet historik med op til 100 sange og ultrahurtig anonym L2-servercache (< 50 ms)."""
    },
    'nb-NO': {
        'title': 'SongFlip: Musikklenke Omdiriger',
        'shortDescription': 'Åpne musikklenker automatisk i Spotify, YouTube Music, Apple Music og mer.',
        'fullDescription': """Venner sender deg Spotify-, Apple Music- eller Tidal-lenker – men du bruker YouTube Music, Deezer eller Amazon Music?

SongFlip er den automatiske musikklenke-omdirigeringen med 0 klikk for Android. Konfigurer den én gang, og hver gang noen deler en musikklenke i WhatsApp, Telegram, Instagram eller nettleseren, omdirigerer SongFlip den øyeblikkelig til din favorittspiller.

🎵 Støttede musikktjenester:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Hovedfunksjoner:
• ⚡ 0-klikk bakgrunnsomdirigering: Starter avspilling direkte.
• 💿 Album- og artistgjenkjenning: Enkeltlåter, hele album og diskografier.
• 🚀 Direkte avspilling: Finner eksakte spor uten manuelle søk.
• 🛡️ 100% personvern & null sporing: Ingen kontoer, ingen sporing, ingen logging av lyttevaner.
• ⏸️ Hurtiginnstillinger & smart pause: Pause omdirigering når som helst fra varslingspanelet.
• 🧪 Innebygd teststudio: Test og konverter lenker manuelt i appen.
• 💎 Valgfri SongFlip PRO: Utvidet historikk med opptil 100 låter og lynrask anonym L2-serverhurtigbuffer (< 50 ms)."""
    },
    'sv-SE': {
        'title': 'SongFlip: Omdirigera Musik',
        'shortDescription': 'Öppna musiklänkar automatiskt i Spotify, YouTube Music, Apple Music m.fl.',
        'fullDescription': """Vänner skickar Spotify-, Apple Music- eller Tidal-länkar – men du använder YouTube Music, Deezer eller Amazon Music?

SongFlip är den automatiska 0-klicks musiklänkomdirigeraren för Android. Ställ in den en gång, och när någon delar en musiklänk i WhatsApp, Telegram, Instagram eller webbläsaren öppnas den direkt i din favoritapp.

🎵 Musikappar som stöds:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Huvudfunktioner:
• ⚡ 0-klick bakgrundsomdirigering: Startar uppspelning direkt.
• 💿 Identifiering av album och artister: Enskilda låtar, hela album och diskografier.
• 🚀 Direkt uppspelningsmotor: Startar exakt spår utan manuell sökning.
• 🛡️ 100% integritet & ingen spårning: Inga konton, inga annonsspårare, inga sparade lyssningsvanor.
• ⏸️ Snabbinställningar & smart paus: Pausa omdirigering när som helst från aviseringsfältet.
• 🧪 Inbyggd teststudio: Testa och konvertera länkar manuellt i appen.
• 💎 Valfri SongFlip PRO: Utökad historik med upp till 100 låtar och supersnabb anonym L2-servercache (< 50 ms)."""
    },
    'nl-NL': {
        'title': 'SongFlip: Muzieklink Doorsturen',
        'shortDescription': 'Open muzieklinks automatisch in Spotify, YouTube Music, Apple Music & meer.',
        'fullDescription': """Vrienden sturen je Spotify-, Apple Music- of Tidal-links – maar jij gebruikt YouTube Music, Deezer of Amazon Music?

SongFlip is de automatische 0-klik muzieklink-omleider voor Android. Stel het eenmalig in, en elke muzieklink uit WhatsApp, Telegram, Instagram of de browser opent direct in je favoriete muziek-app.

🎵 Ondersteunde muziekdiensten:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Belangrijkste kenmerken:
• ⚡ 0-klik achtergrondomleiding: Start direct met afspelen.
• 💿 Album- & artiestenherkenning: Losse nummers, complete albums en discografieën.
• 🚀 Direct afspeelmechanisme: Opent het exacte nummer zonder handmatig zoeken.
• 🛡️ 100% Privacy & Geen tracking: Geen accounts, geen reclame-trackers, geen luistergedrag opgeslagen.
• ⏸️ Snelle instellingen & Slimme pauze: Pauzeer omleidingen eenvoudig via het meldingenpaneel.
• 🧪 Ingebouwde teststudio: Test en converteer links handmatig in de app.
• 💎 Optioneel SongFlip PRO: Uitgebreide geschiedenis tot 100 nummers en ultrasnelle anonieme L2-servercache (< 50 ms)."""
    },
    'es-ES': {
        'title': 'SongFlip: Redirección Música',
        'shortDescription': 'Convierte y abre enlaces de música en Spotify, YouTube Music, Apple Music y más.',
        'fullDescription': """¿Tus amigos te envían enlaces de Spotify, Apple Music o Tidal, pero usas YouTube Music, Deezer o Amazon Music?

SongFlip es el redireccionador automático de enlaces de música sin clics para Android. Configúralo una vez y, cada vez que alguien comparta un enlace de música en WhatsApp, Telegram, Instagram o el navegador, SongFlip lo interceptará y abrirá al instante en tu reproductor preferido.

🎵 Servicios de música compatibles:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Características principales:
• ⚡ Redirección en segundo plano sin clics: Inicia la reproducción directamente.
• 💿 Reconocimiento de álbumes y artistas: Canciones individuales, álbumes completos y discografías.
• 🚀 Motor de reproducción directa: Abre IDs exactos sin búsquedas manuales.
• 🛡️ 100% Privacidad y sin rastreo: Sin cuentas, sin anuncios y sin recopilación de hábitos de escucha.
• ⏸️ Modo pausa y botón de ajustes rápidos: Pausa la redirección cuando quieras desde el panel de notificaciones.
• 🧪 Estudio de pruebas integrado: Prueba y convierte enlaces directamente en la app.
• 💎 SongFlip PRO opcional: Historial ampliado de 100 canciones y caché de servidor L2 ultra rápido (< 50 ms)."""
    },
    'fr-FR': {
        'title': 'SongFlip: Redirection Musique',
        'shortDescription': 'Convertissez et ouvrez vos liens dans Spotify, YouTube Music, Apple Music & plus.',
        'fullDescription': """Vos amis vous envoient des liens Spotify, Apple Music ou Tidal – mais vous utilisez YouTube Music, Deezer ou Amazon Music ?

SongFlip est le redirecteur automatique de liens musicaux sans clic pour Android. Configurez-le en 30 secondes, et chaque lien partagé dans WhatsApp, Telegram, Instagram ou le navigateur s'ouvre instantanément dans votre application musicale préférée.

🎵 Services de musique pris en charge :
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Fonctionnalités clés :
• ⚡ Redirection transparente en arrière-plan : Lance directement la lecture sans intermédiaire.
• 💿 Détection des albums et artistes : Compatible pistes individuelles, albums complets et profils d'artistes.
• 🚀 Moteur de lecture instantanée : Ouvre directement les identifiants sans recherche manuelle.
• 🛡️ 100% Privé & Sans suivi : Aucun compte requis, aucun traqueur publicitaire, aucune donnée d'écoute collectée.
• ⏸️ Tuile Paramètres rapides & Mode Pause : Suspendez la redirection facilement depuis le volet des notifications.
• 🧪 Studio de test intégré : Testez et convertissez vos liens directement dans l'application.
• 💎 SongFlip PRO en option : Historique étendu à 100 morceaux et cache serveur L2 ultra-rapide (< 50 ms)."""
    },
    'it-IT': {
        'title': 'SongFlip: Reindirizza Musica',
        'shortDescription': 'Converti e apri link musicali in Spotify, YouTube Music, Apple Music e altri.',
        'fullDescription': """I tuoi amici ti inviano link di Spotify, Apple Music o Tidal, ma tu usi YouTube Music, Deezer o Amazon Music?

SongFlip è il reindirizzatore automatico di link musicali zero-click per Android. Configuralo una volta e ogni volta che ricevi un link musicale su WhatsApp, Telegram, Instagram o nel browser, SongFlip lo converte e lo apre all'istante nella tua app musicale preferita.

🎵 Servizi musicali supportati:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Caratteristiche principali:
• ⚡ Reindirizzamento zero-click in background: Avvia direttamente la riproduzione.
• 💿 Riconoscimento album e artisti: Singoli brani, album completi ed intere discografie.
• 🚀 Riproduzione istantanea: Risolve gli ID esatti senza ricerche manuali.
• 🛡️ 100% Privacy & Zero tracciamento: Nessun account richiesto, nessun tracker pubblicitario, nessuna cronologia salvata.
• ⏸️ Riquadro Impostazioni rapide & Pausa smart: Sospendi il reindirizzamento in qualsiasi momento dalla barra delle notifiche.
• 🧪 Test Studio integrato: Prova e converti i link direttamente nell'app.
• 💎 SongFlip PRO opzionale: Cronologia estesa a 100 brani e cache server L2 ultra-veloce (< 50 ms)."""
    },
    'pt-BR': {
        'title': 'SongFlip: Redirecionar Música',
        'shortDescription': 'Abra links de música no Spotify, YouTube Music, Apple Music e muito mais.',
        'fullDescription': """Seus amigos enviam links do Spotify, Apple Music ou Tidal – mas você usa o YouTube Music, Deezer ou Amazon Music?

SongFlip é o redirecionador automático de links de música com 0 cliques para Android. Configure uma única vez e qualquer link recebido no WhatsApp, Telegram, Instagram ou navegador abrirá direto no seu player favorito.

🎵 Serviços de música compatíveis:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Principais recursos:
• ⚡ Redirecionamento em segundo plano sem cliques: Reprodução instantânea direta.
• 💿 Reconhecimento de álbuns e artistas: Músicas avulsas, álbuns completos e discografias.
• 🚀 Motor de reprodução direta: Abre a faixa exata sem buscas manuais.
• 🛡️ 100% Privacidade e Zero rastreamento: Sem contas, sem anúncios e sem coleta de hábitos musicais.
• ⏸️ Bloco de Configurações Rápidas e Pausa inteligente: Pause o redirecionamento direto pela barra de notificações.
• 🧪 Estúdio de testes integrado: Converta e teste links direto no aplicativo.
• 💎 SongFlip PRO opcional: Histórico ampliado de até 100 músicas e cache de servidor L2 ultra rápido (< 50 ms)."""
    },
    'pl-PL': {
        'title': 'SongFlip: Przekieruj Muzykę',
        'shortDescription': 'Otwieraj linki muzyczne w Spotify, YouTube Music, Apple Music i innych.',
        'fullDescription': """Znajomi wysyłają linki ze Spotify, Apple Music lub Tidal – ale Ty używasz YouTube Music, Deezer lub Amazon Music?

SongFlip to automatyczne narzędzie do przekierowywania linków muzycznych bez klikania dla Androida. Skonfiguruj raz, a każdy link z WhatsApp, Telegrama, Instagrama lub przeglądarki otworzy się natychmiast w Twojej ulubionej aplikacji.

🎵 Obsługiwane serwisy muzyczne:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Główne funkcje:
• ⚡ Przekierowanie w tle: Natychmiastowe uruchamianie odtwarzania.
• 💿 Rozpoznawanie albumów i wykonawców: Pojedyncze utwory, pełne albumy i dyskografie.
• 🚀 Silnik bezpośredniego odtwarzania: Otwiera dokładne utwory bez wyszukiwania ręcznego.
• 🛡️ 100% prywatności i brak śledzenia: Bez kont, bez reklam, bez zbierania danych o nawykach.
• ⏸️ Kafel szybkich ustawień i pauza: Wstrzymaj przekierowania w dowolnym momencie z paska powiadomień.
• 🧪 Wbudowane studio testowe: Testuj i konwertuj linki bezpośrednio w aplikacji.
• 💎 Opcjonalny SongFlip PRO: Rozszerzona historia do 100 utworów i ultraszybka anonimowa pamięć podręczna serwera L2 (< 50 ms)."""
    },
    'ru-RU': {
        'title': 'SongFlip: Музыкальный Редирект',
        'shortDescription': 'Открывайте ссылки в Spotify, YouTube Music, Apple Music и других плеерах.',
        'fullDescription': """Друзья присылают ссылки на Spotify, Apple Music или Tidal, но вы слушаете музыку в YouTube Music, Deezer или Amazon Music?

SongFlip — это автоматический перенаправитель музыкальных ссылок в 0 кликов для Android. Настройте один раз, и любая ссылка из WhatsApp, Telegram, Instagram или браузера мгновенно откроется в вашем любимом плеере.

🎵 Поддерживаемые сервисы:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Ключевые возможности:
• ⚡ Фоновый редирект в 0 кликов: Мгновенный запуск воспроизведения.
• 💿 Распознавание альбомов и исполнителей: Треки, альбомы, EP и дискографии.
• 🚀 Прямое воспроизведение: Находит точные треки без ручного поиска.
• 🛡️ 100% конфиденциальность: Без аккаунтов, без рекламы и без сбора истории прослушиваний.
• ⏸️ Плитка быстрых настроек и умная пауза: Приостанавливайте работу в шторке уведомлений.
• 🧪 Встроенная тестовая студия: Конвертируйте ссылки прямо в приложении.
• 💎 SongFlip PRO: История на 100 треков и сверхбыстрый анонимный серверный L2-кэш (< 50 мс)."""
    },
    'tr-TR': {
        'title': 'SongFlip: Müzik Bağlantısı',
        'shortDescription': 'Müzik bağlantılarını Spotify, YouTube Music, Apple Music ve diğerlerinde açın.',
        'fullDescription': """Arkadaşlarınız Spotify, Apple Music veya Tidal bağlantıları gönderiyor ama siz YouTube Music, Deezer veya Amazon Music mi kullanıyorsunuz?

SongFlip, Android için 0 tıklamalı otomatik müzik bağlantısı yönlendiricisidir. Bir kez kurun; WhatsApp, Telegram, Instagram veya tarayıcıdan gelen bağlantılar doğrudan tercih ettiğiniz müzik uygulamasında açılsın.

🎵 Desteklenen Müzik Servisleri:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Temel Özellikler:
• ⚡ 0 Tıklamalı Arka Plan Yönlendirmesi: Şarkıyı doğrudan oynatır.
• 💿 Albüm ve Sanatçı Tanıma: Tekli şarkılar, tam albümler ve diskografiler.
• 🚀 Doğrudan Oynatma Motoru: Arama yapmadan parçayı anında açar.
• 🛡️ %100 Gizlilik ve Sıfır Takip: Hesap gerekmez, reklam takibi ve dinleme geçmişi kaydı yoktur.
• ⏸️ Hızlı Ayarlar Kutucuğu ve Akıllı Duraklatma: Bildirim panelinden yönlendirmeyi duraklatın.
• 🧪 Dahili Test Stüdyosu: Bağlantıları doğrudan uygulama içinden test edin ve dönüştürün.
• 💎 İsteğe Bağlı SongFlip PRO: 100 şarkılık genişletilmiş geçmiş ve ultra hızlı anonim L2 sunucu önbelleği (< 50 ms)."""
    },
    'uk-UA': {
        'title': 'SongFlip: Музичний Редірект',
        'shortDescription': 'Відкривайте музичні посилання у Spotify, YouTube Music, Apple Music та ін.',
        'fullDescription': """Друзі надсилають посилання зі Spotify, Apple Music чи Tidal, але ви слухаєте в YouTube Music, Deezer або Amazon Music?

SongFlip — це автоматичний перенаправник музичних посилань в 0 кліків для Android. Налаштуйте один раз, і будь-яке посилання з WhatsApp, Telegram, Instagram чи браузера миттєво відкриється у вашому улюбленому додатку.

🎵 Підтримувані сервіси:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ Головні можливості:
• ⚡ Фоновий редірект в 0 кліків: Миттєвий запуск відтворення.
• 💿 Розпізнавання альбомів та виконавців: Окремі треки, повні альбоми та дискографії.
• 🚀 Пряме відтворення: Знаходить точні треки без ручного пошуку.
• 🛡️ 100% Конфіденційність: Без акаунтів, без реклами та без збору історії прослуховувань.
• ⏸️ Плитка швидких налаштувань та розумна пауза: Призупиняйте роботу у шторці сповіщень.
• 🧪 Вбудована тестова студія: Конвертуйте посилання безпосередньо в додатку.
• 💎 SongFlip PRO: Розширена історія на 100 треків та надшвидкий анонімний серверний L2-кеш (< 50 мс)."""
    },
    'ja-JP': {
        'title': 'SongFlip: 音楽リンク転送',
        'shortDescription': '音楽リンクをSpotify、YouTube Music、Apple Music等で自動再生。',
        'fullDescription': """友だちからSpotifyやApple Musicのリンクが届いても、YouTube MusicやAmazon Musicを使っていませんか？

SongFlipはAndroid向けの0クリック音楽リンク自動リダイレクトアプリです。一度設定するだけで、WhatsAppやLINE、Instagram、ブラウザから共有された音楽リンクをお気に入りのプレイヤーで即座に開きます。

🎵 対応音楽サービス:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ 主な機能:
• ⚡ 0クリックのバックグラウンド転送: 直接再生を開始。
• 💿 アルバム・アーティスト認識: シングル、フルアルバム、ディスコグラフィ対応。
• 🚀 ダイレクト再生エンジン: 手動検索なしで楽曲をダイレクトに解決。
• 🛡️ 100%プライバシー＆トラッキングなし: アカウント不要、広告トラッカーなし、再生履歴の収集なし。
• ⏸️ クイック設定タイル＆スマート一時停止: 通知パネルからいつでも一時停止可能。
• 🧪 テストスタジオ内蔵: アプリ内で手動テスト＆変換。
• 💎 SongFlip PRO（オプション）: 最大100曲の拡張履歴＆超高速な匿名L2サーバーキャッシュ（50ms未満）。"""
    },
    'ko-KR': {
        'title': 'SongFlip: 음악 링크 리다이렉트',
        'shortDescription': '음악 링크를 Spotify, YouTube Music, Apple Music 등에서 자동으로 열기.',
        'fullDescription': """친구들이 Spotify나 Apple Music 링크를 보내는데, YouTube Music이나 Deezer를 사용하시나요?

SongFlip은 Android용 0클릭 자동 음악 링크 변환기입니다. 한 번만 설정하면 카카오톡, 텔레그램, 인스타그램 또는 브라우저에서 공유된 음악 링크가 선호하는 음악 플레이어로 즉시 열립니다.

🎵 지원되는 음악 서비스:
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ 주요 기능:
• ⚡ 0클릭 백그라운드 리다이렉트: 즉시 직접 재생.
• 💿 앨범 및 아티스트 인식: 싱글 트랙, 정규 앨범 및 디스코그래피 지원.
• 🚀 직접 재생 엔진: 수동 검색 없이 정확한 트랙 실행.
• 🛡️ 100% 프라이버시 및 무추적: 계정 불필요, 광고 추적기 없음, 청취 습관 수집 없음.
• ⏸️ 빠른 설정 타일 및 스마트 일시정지: 알림 창에서 손쉽게 일시정지.
• 🧪 내장 테스트 스튜디오: 앱 내에서 직접 링크 테스트 및 변환.
• 💎 선택적 SongFlip PRO: 최대 100곡의 확장된 기록 및 초고속 익명 L2 서버 캐시(50ms 미만)."""
    },
    'zh-CN': {
        'title': 'SongFlip: 音乐链接重定向',
        'shortDescription': '自动将音乐链接转换为Spotify、YouTube Music、Apple Music等播放。',
        'fullDescription': """朋友发送了Spotify或Apple Music链接，但你使用的是YouTube Music或Amazon Music？

SongFlip是适用于Android的0点击自动音乐链接重定向工具。只需设置一次，微信、Telegram、Instagram或浏览器中分享的音乐链接即可直接在你偏好的音乐播放器中打开。

🎵 支持的音乐服务：
• 🟢 Spotify
• 🔴 YouTube Music & YouTube
• 🍎 Apple Music
• 🌊 Tidal
• 🟣 Deezer
• 🔵 Amazon Music

✨ 核心功能：
• ⚡ 0点击后台重定向：无缝拦截并直接启动播放。
• 💿 专辑与艺人识别：支持单曲、完整专辑及艺人作品集。
• 🚀 直接播放引擎：无需手动搜索即可打开精确曲目。
• 🛡️ 100% 隐私安全与零追踪：无需账号登录，无广告追踪器，不收集听歌习惯。
• ⏸️ 快捷设置磁贴与智能暂停：可随时在通知栏中暂停重定向。
• 🧪 内置测试工作台：直接在应用内测试并转换音乐链接。
• 💎 可选 SongFlip PRO：扩展至100首歌曲的历史记录及超高速匿名L2服务器缓存（< 50ms）。"""
    }
}

# Regional / Alias mappings
LISTINGS['es-419'] = LISTINGS['es-ES']
LISTINGS['es-US'] = LISTINGS['es-ES']
LISTINGS['pt-PT'] = LISTINGS['pt-BR']
LISTINGS['zh-TW'] = LISTINGS['zh-CN']
LISTINGS['no-NO'] = LISTINGS['nb-NO']

for locale, data in LISTINGS.items():
    loc_dir = os.path.join(BASE_DIR, locale)
    os.makedirs(loc_dir, exist_ok=True)
    
    with open(os.path.join(loc_dir, 'title.txt'), 'w', encoding='utf-8') as f:
        f.write(data['title'].strip())
        
    with open(os.path.join(loc_dir, 'short_description.txt'), 'w', encoding='utf-8') as f:
        f.write(data['shortDescription'].strip())
        
    with open(os.path.join(loc_dir, 'full_description.txt'), 'w', encoding='utf-8') as f:
        f.write(data['fullDescription'].strip())

print(f"Generated Fastlane Store Listings for {len(LISTINGS)} locales successfully!")
