#!/usr/bin/env node
/**
 * Universal Multi-Language Store Screenshot Renderer for SongFlip.
 * Renders all 37 locales using Puppeteer and the master unified mockup templates.
 */

const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer');

const BASE_DIR = __dirname;
const TEMPLATES_DIR = path.join(BASE_DIR, 'assets', 'screenshots');
const OUTPUT_BASE = path.join(path.dirname(BASE_DIR), 'screenshots');
const RES_DIR = path.join(BASE_DIR, '..', '..', 'app', 'src', 'main', 'res');
const LOCALIZATIONS_PATH = path.join(BASE_DIR, 'localizations.json');
const rawLocData = JSON.parse(fs.readFileSync(LOCALIZATIONS_PATH, 'utf8'));
const { STRINGS: MASTER_STRINGS } = require('./render_localized');

// Curated locale data for extra UI items (Screen 1 chat, Screen 2 routing, Screen 5 privacy)
const EXTRA_TRANSLATIONS = {
  'de': {
    chat_text: 'Hey! Hör dir mal das Lied an,<br>das ich gerade gefunden habe:',
    routing_title: 'Automatische Weiterleitung aktiv',
    routing_desc: 'Musik-Links aus allen unterstützten Streaming-Diensten öffnen sich ab jetzt automatisch im Hintergrund.',
    s5_title: 'Deine Daten gehören dir.',
    s5_t1_title: 'Kein Benutzerkonto',
    s5_t1_desc: 'Keine Registrierung, keine Passwörter. Sofort einsatzbereit.',
    s5_t2_title: '100 % Werbefrei',
    s5_t2_desc: 'Keine Drittanbieter-Werbung, keine Banner, keine Werbe-Tracker.',
    s5_t3_title: 'Anonym & Sicher',
    s5_t3_desc: 'Song-Links werden ohne Personenbezug aufgelöst. Kein Profiling.',
    s5_badge: 'Finanziert durch optionale PRO-Features – nicht durch deine Daten'
  },
  'en': {
    chat_text: 'Hey! Listen to this song,<br>I just found it:',
    routing_title: 'Automatic Link Routing Active',
    routing_desc: 'Music links from all supported streaming services will open automatically in the background.',
    s5_title: 'Your Data Stays Yours.',
    s5_t1_title: 'No Registration',
    s5_t1_desc: 'No accounts, no passwords, no emails. Ready immediately.',
    s5_t2_title: '100% Ad-Free',
    s5_t2_desc: 'No third-party ads, zero banners, no advertising trackers.',
    s5_t3_title: 'Anonymous & Secure',
    s5_t3_desc: 'Links are resolved without personal identity. Zero profiling.',
    s5_badge: 'Funded by optional PRO features – never by your data'
  },
  'es': {
    chat_text: '¡Hola! Escucha esta canción<br>que acabo de encontrar:',
    routing_title: 'Redirección automática activa',
    routing_desc: 'Los enlaces de música de todos los servicios compatibles se abrirán automáticamente en segundo plano.',
    s5_title: 'Tus datos son tuyos.',
    s5_t1_title: 'Sin registro',
    s5_t1_desc: 'Sin cuentas, contraseñas ni correos. Listo al instante.',
    s5_t2_title: '100 % Libre de anuncios',
    s5_t2_desc: 'Sin anuncios de terceros, sin banners ni rastreadores.',
    s5_t3_title: 'Anónimo y seguro',
    s5_t3_desc: 'Los enlaces se resuelven sin identidad personal. Cero perfiles.',
    s5_badge: 'Financiado por funciones PRO opcionales – nunca por tus datos'
  },
  'fr': {
    chat_text: 'Salut ! Écoute ce morceau<br>que je viens de trouver :',
    routing_title: 'Redirection automatique active',
    routing_desc: 'Les liens de tous les services compatibles s’ouvrent automatiquement en arrière-plan.',
    s5_title: 'Vos données vous appartiennent.',
    s5_t1_title: 'Sans inscription',
    s5_t1_desc: 'Aucun compte, mot de passe ou email requis. Prêt immédiatement.',
    s5_t2_title: '100 % Sans publicité',
    s5_t2_desc: 'Aucune pub tierce, aucune bannière, aucun traqueur publicitaire.',
    s5_t3_title: 'Anonyme et sécurisé',
    s5_t3_desc: 'Les liens sont résolus sans identité personnelle. Aucun profilage.',
    s5_badge: 'Financé par des fonctions PRO facultatives – jamais par vos données'
  },
  'it': {
    chat_text: 'Ehi! Ascolta questa canzone<br>che ho appena trovato:',
    routing_title: 'Reindirizzamento automatico attivo',
    routing_desc: 'I link musicali da tutti i servizi supportati si apriranno automaticamente in background.',
    s5_title: 'I tuoi dati sono tuoi.',
    s5_t1_title: 'Nessuna registrazione',
    s5_t1_desc: 'Nessun account, password o email richiesta. Subito pronto.',
    s5_t2_title: '100% Senza pubblicità',
    s5_t2_desc: 'Nessuna pubblicità di terze parti, zero banner o tracker.',
    s5_t3_title: 'Anonimo e sicuro',
    s5_t3_desc: 'I link vengono risolti senza dati personali. Zero profilazione.',
    s5_badge: 'Finanziato da funzioni PRO opzionali – mai dai tuoi dati'
  },
  'pt': {
    chat_text: 'Oi! Ouve esta música<br>que acabei de encontrar:',
    routing_title: 'Redirecionamento automático ativo',
    routing_desc: 'Links de todos os serviços de streaming suportados abrirão automaticamente em segundo plano.',
    s5_title: 'Seus dados pertencem a você.',
    s5_t1_title: 'Sem cadastro',
    s5_t1_desc: 'Sem contas, senhas ou e-mails. Pronto para usar.',
    s5_t2_title: '100% Livre de anúncios',
    s5_t2_desc: 'Sem anúncios de terceiros, sem banners ou rastreadores.',
    s5_t3_title: 'Anônimo e seguro',
    s5_t3_desc: 'Links são resolvidos sem identidade pessoal. Zero perfis.',
    s5_badge: 'Financiado por recursos PRO opcionais – nunca por seus dados'
  },
  'nl': {
    chat_text: 'Hé! Luister naar dit nummer<br>dat ik net vond:',
    routing_title: 'Automatisch doorsturen actief',
    routing_desc: 'Muzieklinks van alle ondersteunde diensten worden automatisch op de achtergrond geopend.',
    s5_title: 'Jouw data blijft van jou.',
    s5_t1_title: 'Geen account nodig',
    s5_t1_desc: 'Geen accounts, wachtwoorden of e-mails nodig. Direct klaar.',
    s5_t2_title: '100% Reclamevrij',
    s5_t2_desc: 'Geen advertenties van derden, geen banners of trackers.',
    s5_t3_title: 'Anoniem en veilig',
    s5_t3_desc: 'Links worden anoniem opgelost. Geen profilering.',
    s5_badge: 'Gefinancierd door optionele PRO-functies – nooit door jouw data'
  },
  'pl': {
    chat_text: 'Hej! Posłuchaj tego utworu,<br>właśnie go znalazłem:',
    routing_title: 'Automatyczne przekierowanie aktywne',
    routing_desc: 'Linki muzyczne ze wszystkich obsługiwanych usług otwierają się automatycznie w tle.',
    s5_title: 'Twoje dane należą do Ciebie.',
    s5_t1_title: 'Bez rejestracji',
    s5_t1_desc: 'Bez kont, haseł i adresów e-mail. Gotowe natychmiast.',
    s5_t2_title: '100% Bez reklam',
    s5_t2_desc: 'Brak reklam zewnętrznych, banerów i modułów śledzących.',
    s5_t3_title: 'Anonimowe i bezpieczne',
    s5_t3_desc: 'Linki są rozwiązywane bez powiązania z tożsamością. Brak profilowania.',
    s5_badge: 'Finansowane z opcjonalnych funkcji PRO – nigdy z Twoich danych'
  },
  'ru': {
    chat_text: 'Привет! Послушай эту песню,<br>только что нашел:',
    routing_title: 'Автоперенаправление активно',
    routing_desc: 'Музыкальные ссылки из всех сервисов будут автоматически открываться в фоновом режиме.',
    s5_title: 'Ваши данные принадлежат вам.',
    s5_t1_title: 'Без регистрации',
    s5_t1_desc: 'Без аккаунтов, паролей и почты. Готово к использованию.',
    s5_t2_title: '100% Без рекламы',
    s5_t2_desc: 'Никакой сторонней рекламы, баннеров и рекламных трекеров.',
    s5_t3_title: 'Анонимно и безопасно',
    s5_t3_desc: 'Ссылки открываются без привязки к личности. Без профилирования.',
    s5_badge: 'Финансируется функциями PRO – а не продажей ваших данных'
  },
  'tr': {
    chat_text: 'Selam! Yeni bulduğum bu<br>şarkıyı dinle:',
    routing_title: 'Otomatik yönlendirme etkin',
    routing_desc: 'Desteklenen tüm servislerden gelen müzik bağlantıları arka planda otomatik açılır.',
    s5_title: 'Verileriniz size aittir.',
    s5_t1_title: 'Kayıt Gerekmez',
    s5_t1_desc: 'Hesap, şifre veya e-posta gerekmez. Anında hazır.',
    s5_t2_title: '100% Reklamsız',
    s5_t2_desc: 'Üçüncü taraf reklam, banner veya reklam takipçisi yok.',
    s5_t3_title: 'Anonim ve Güvenli',
    s5_t3_desc: 'Bağlantılar kişisel kimlik olmadan çözülür. Profilleme yok.',
    s5_badge: 'İsteğe bağlı PRO özellikleriyle finanse edilir – verilerinizle değil'
  },
  'ja': {
    chat_text: 'ねえ！さっき見つけた<br>この曲聴いてみて:',
    routing_title: '自動リダイレクト有効',
    routing_desc: '対応するすべての音楽サービスのリンクがバックグラウンドで自動的に開きます。',
    s5_title: 'あなたのデータはあなたのもの。',
    s5_t1_title: 'アカウント登録不要',
    s5_t1_desc: '登録やパスワードは一切不要。すぐに使えます。',
    s5_t2_title: '100% 広告なし',
    s5_t2_desc: 'サードパーティ広告、バナー、トラッカーは一切ありません。',
    s5_t3_title: '匿名で安全',
    s5_t3_desc: '個人を特定せずにリンクを解決。プロファイリングなし。',
    s5_badge: '任意のPRO機能で運営 – あなたのデータを売ることはありません'
  },
  'ko': {
    chat_text: '안녕! 방금 찾은<br>이 노래 한번 들어봐:',
    routing_title: '자동 리디렉션 활성화됨',
    routing_desc: '지원되는 모든 스트리밍 서비스의 음악 링크가 백그라운드에서 자동으로 열립니다.',
    s5_title: '데이터는 오직 당신의 것입니다.',
    s5_t1_title: '회원가입 없음',
    s5_t1_desc: '계정, 비밀번호, 이메일 불필요. 즉시 사용 가능.',
    s5_t2_title: '100% 광고 없음',
    s5_t2_desc: '서드파티 광고, 배너, 광고 트래커 일체 없음.',
    s5_t3_title: '익명 및 안전',
    s5_t3_desc: '개인 식별 없이 링크를 연결합니다. 프로파일링 없음.',
    s5_badge: '선택적 PRO 기능으로 운영 – 데이터 판매 없음'
  },
  'zh': {
    chat_text: '嗨！听听这首<br>我刚发现的歌：',
    routing_title: '自动重定向已激活',
    routing_desc: '来自所有受支持服务的音乐链接将在后台自动打开。',
    s5_title: '您的数据完全归您所有。',
    s5_t1_title: '无需注册',
    s5_t1_desc: '无需账号、密码或邮箱。即开即用。',
    s5_t2_title: '100% 无广告',
    s5_t2_desc: '绝无第三方广告、横幅或广告追踪器。',
    s5_t3_title: '匿名且安全',
    s5_t3_desc: '解析音乐链接完全不关联个人身份。绝无画像追踪。',
    s5_badge: '由可选的 PRO 功能资助 – 绝不出售您的个人数据'
  },
  'id': {
    chat_text: 'Hei! Dengerin lagu ini,<br>baru aja aku temuin:',
    routing_title: 'Pengalihan Otomatis Aktif',
    routing_desc: 'Tautan musik dari semua layanan yang didukung akan terbuka otomatis di latar belakang.',
    s5_title: 'Data Anda Tetap Milik Anda.',
    s5_t1_title: 'Tanpa Registrasi',
    s5_t1_desc: 'Tanpa akun, kata sandi, atau email. Langsung siap dipakai.',
    s5_t2_title: '100% Bebas Iklan',
    s5_t2_desc: 'Tanpa iklan pihak ketiga, tanpa banner, tanpa pelacak.',
    s5_t3_title: 'Anonim & Aman',
    s5_t3_desc: 'Tautan diproses tanpa identitas pribadi. Tanpa pembuatan profil.',
    s5_badge: 'Didanai oleh fitur PRO opsional – bukan data Anda'
  },
  'vi': {
    chat_text: 'Này! Nghe bài hát này đi,<br>mình vừa tìm thấy nè:',
    routing_title: 'Tự động chuyển hướng đang bật',
    routing_desc: 'Liên kết âm nhạc từ mọi dịch vụ được hỗ trợ sẽ tự động mở trong nền.',
    s5_title: 'Dữ liệu luôn thuộc về bạn.',
    s5_t1_title: 'Không cần đăng ký',
    s5_t1_desc: 'Không cần tài khoản, mật khẩu hay email. Dùng được ngay.',
    s5_t2_title: '100% Không quảng cáo',
    s5_t2_desc: 'Không quảng cáo bên thứ ba, không banner, không trình theo dõi.',
    s5_t3_title: 'Ẩn danh & An toàn',
    s5_t3_desc: 'Liên kết được xử lý mà không cần danh tính cá nhân.',
    s5_badge: 'Được tài trợ bởi các tính năng PRO tùy chọn – không phải dữ liệu của bạn'
  },
  'th': {
    chat_text: 'เฮ้! ลองฟังเพลงนี้สิ<br>ฉันเพิ่งเจอมาเลย:',
    routing_title: 'เปิดการเปลี่ยนเส้นทางอัตโนมัติ',
    routing_desc: 'ลิงก์เพลงจากบริการที่รองรับทั้งหมดจะเปิดขึ้นในพื้นหลังโดยอัตโนมัติ',
    s5_title: 'ข้อมูลของคุณเป็นของคุณเสมอ',
    s5_t1_title: 'ไม่ต้องลงทะเบียน',
    s5_t1_desc: 'ไม่ต้องมีบัญชี รหัสผ่าน หรืออีเมล พร้อมใช้งานทันที',
    s5_t2_title: 'ไม่มีโฆษณา 100%',
    s5_t2_desc: 'ไม่มีโฆษณาบุคคลที่สาม ไม่มีแบนเนอร์ ไม่มีตัวติดตาม',
    s5_t3_title: 'นิรนามและปลอดภัย',
    s5_t3_desc: 'ลิงก์ได้รับการประมวลผลโดยไม่มีการระบุตัวตน',
    s5_badge: 'สนับสนุนโดยฟีเจอร์ PRO เสริม – ไม่ใช่ข้อมูลของคุณ'
  },
  'hi': {
    chat_text: 'अरे! यह गाना सुनो,<br>मुझे अभी मिला है:',
    routing_title: 'ऑटोमैटिक रीडायरेक्शन सक्रिय',
    routing_desc: 'समर्थित सभी सेवाओं के म्यूज़िक लिंक बैकग्राउंड में अपने आप खुलेंगे।',
    s5_title: 'आपका डेटा सिर्फ आपका है।',
    s5_t1_title: 'कोई रजिस्ट्रेशन नहीं',
    s5_t1_desc: 'कोई खाता, पासवर्ड या ईमेल नहीं चाहिए। तुरंत इस्तेमाल करें।',
    s5_t2_title: '100% विज्ञापन मुक्त',
    s5_t2_desc: 'कोई तीसरे पक्ष का विज्ञापन, बैनर या ट्रैकर नहीं।',
    s5_t3_title: 'गुमनाम और सुरक्षित',
    s5_t3_desc: 'बिना व्यक्तिगत पहचान के लिंक प्रोसेस होते हैं।',
    s5_badge: 'वैकल्पिक PRO फीचर्स द्वारा समर्थित – आपके डेटा द्वारा नहीं'
  },
  'cs': {
    chat_text: 'Ahoj! Poslechni si tuto skladbu,<br>právě jsem ji našel:',
    routing_title: 'Automatické přesměrování aktivní',
    routing_desc: 'Hudební odkazy ze všech podporovaných služeb se automaticky otevřou na pozadí.',
    s5_title: 'Vaše data patří vám.',
    s5_t1_title: 'Bez registrace',
    s5_t1_desc: 'Žádné účty, hesla ani e-maily. Okamžitě připraveno k použití.',
    s5_t2_title: '100% Bez reklam',
    s5_t2_desc: 'Žádné reklamy třetích stran, žádné bannery ani sledovací prvky.',
    s5_t3_title: 'Anonymní a bezpečné',
    s5_t3_desc: 'Odkazy jsou řešeny bez osobní identity. Žádné profilování.',
    s5_badge: 'Financováno volitelnými funkcemi PRO – nikoli vašimi daty'
  },
  'hu': {
    chat_text: 'Szia! Hallgasd meg ezt a számot,<br>most találtam:',
    routing_title: 'Automatikus átirányítás aktív',
    routing_desc: 'A támogatott szolgáltatások zenei linkjei automatikusan megnyílnak a háttérben.',
    s5_title: 'Az adataid a tieid maradnak.',
    s5_t1_title: 'Regisztráció nélkül',
    s5_t1_desc: 'Nincs fiók, jelszó vagy e-mail. Azonnal használatra kész.',
    s5_t2_title: '100% Reklámmentes',
    s5_t2_desc: 'Nincsenek harmadik féltől származó hirdetések, bannerek vagy nyomkövetők.',
    s5_t3_title: 'Névtelen és biztonságos',
    s5_t3_desc: 'A linkek személyes azonosítás nélkül nyílnak meg. Nincs profilalkotás.',
    s5_badge: 'Opcionális PRO funkciókból finanszírozva – nem a te adataidból'
  },
  'ro': {
    chat_text: 'Bună! Ascultă piesa asta,<br>tocmai am găsit-o:',
    routing_title: 'Redirecționare automată activă',
    routing_desc: 'Linkurile muzicale din toate serviciile acceptate se vor deschide automat în fundal.',
    s5_title: 'Datele tale îți aparțin.',
    s5_t1_title: 'Fără înregistrare',
    s5_t1_desc: 'Fără conturi, parole sau e-mailuri. Gata instant de utilizare.',
    s5_t2_title: '100% Fără reclame',
    s5_t2_desc: 'Fără reclame terțe, fără bannere, fără trackere.',
    s5_t3_title: 'Anonim și sigur',
    s5_t3_desc: 'Linkurile sunt rezolvate fără identitate personală. Fără profilare.',
    s5_badge: 'Finanțat prin funcții PRO opționale – niciodată prin datele tale'
  },
  'da': {
    chat_text: 'Hej! Hør denne sang,<br>jeg lige har fundet:',
    routing_title: 'Automatisk omdirigering aktiv',
    routing_desc: 'Musiklinks fra alle understøttede tjenester åbnes automatisk i baggrunden.',
    s5_title: 'Dine data forbliver dine.',
    s5_t1_title: 'Ingen registrering',
    s5_t1_desc: 'Ingen konti, adgangskoder eller e-mails nødvendige. Klar med det samme.',
    s5_t2_title: '100% Reklamefri',
    s5_t2_desc: 'Ingen tredjepartsreklamer, ingen bannere, ingen trackers.',
    s5_t3_title: 'Anonymt & sikkert',
    s5_t3_desc: 'Links behandles uden personlig identitet. Ingen profilering.',
    s5_badge: 'Finansieret af valgfrie PRO-funktioner – ikke af dine data'
  },
  'sv': {
    chat_text: 'Hej! Lyssna på den här låten<br>som jag precis hittade:',
    routing_title: 'Automatisk omdirigering aktiv',
    routing_desc: 'Musiklänkar från alla tjänster som stöds öppnas automatiskt i bakgrunden.',
    s5_title: 'Dina data tillhör dig.',
    s5_t1_title: 'Ingen registrering',
    s5_t1_desc: 'Inga konton, lösenord eller e-postadresser krävs. Redo direkt.',
    s5_t2_title: '100% Reklamfritt',
    s5_t2_desc: 'Inga tredjepartsannonser, inga banners, inga spårare.',
    s5_t3_title: 'Anonymt & säkert',
    s5_t3_desc: 'Länkar hanteras utan personlig identitet. Ingen profilering.',
    s5_badge: 'Finansieras av valfria PRO-funktioner – inte av dina data'
  },
  'nb': {
    chat_text: 'Hei! Hør på denne sangen<br>som jeg nettopp fant:',
    routing_title: 'Automatisk omdirigering aktiv',
    routing_desc: 'Musikklenker fra alle støttede tjenester åpnes automatisk i bakgrunnen.',
    s5_title: 'Dine data tilhører deg.',
    s5_t1_title: 'Ingen registrering',
    s5_t1_desc: 'Ingen kontoer, passord eller e-poster trengs. Klar umiddelbart.',
    s5_t2_title: '100% Reklamefritt',
    s5_t2_desc: 'Ingen tredjepartsannonser, ingen bannere, ingen sporere.',
    s5_t3_title: 'Anonymt & sikkert',
    s5_t3_desc: 'Lenker behandles uten personlig identitet. Ingen profilering.',
    s5_badge: 'Finansiert av valgfrie PRO-funksjoner – ikke av dine data'
  },
  'fi': {
    chat_text: 'Hei! Kuuntele tämä biisi,<br>löysin sen juuri:',
    routing_title: 'Automaattinen uudelleenohjaus päällä',
    routing_desc: 'Kaikkien tuettujen palvelujen musiikkilinkit avautuvat taustalla automaattisesti.',
    s5_title: 'Tietosi kuuluvat sinulle.',
    s5_t1_title: 'Ei rekisteröitymistä',
    s5_t1_desc: 'Ei tilejä, salasanoja tai sähköposteja. Valmis heti käyttöön.',
    s5_t2_title: '100% Mainokseton',
    s5_t2_desc: 'Ei kolmansien osapuolien mainoksia, bannereita tai seurantatyökaluja.',
    s5_t3_title: 'Nimetön ja turvallinen',
    s5_t3_desc: 'Linkit avataan ilman henkilöllisyyden tunnistusta. Ei profilointia.',
    s5_badge: 'Rahoitetaan valinnaisilla PRO-ominaisuuksilla – ei tiedoillasi'
  },
  'el': {
    chat_text: 'Γεια! Άκουσε αυτό το τραγούδι<br>που μόλις βρήκα:',
    routing_title: 'Αυτόματη ανακατεύθυνση ενεργή',
    routing_desc: 'Οι σύνδεσμοι μουσικής από όλες τις υποστηριζόμενες υπηρεσίες ανοίγουν αυτόματα.',
    s5_title: 'Τα δεδομένα σας ανήκουν σε εσάς.',
    s5_t1_title: 'Χωρίς εγγραφή',
    s5_t1_desc: 'Χωρίς λογαριασμούς, κωδικούς ή email. Έτοιμο αμέσως.',
    s5_t2_title: '100% Χωρίς διαφημίσεις',
    s5_t2_desc: 'Χωρίς διαφημίσεις τρίτων, banner ή ιχνηλάτες.',
    s5_t3_title: 'Ανώνυμο και ασφαλές',
    s5_t3_desc: 'Οι σύνδεσμοι επιλύονται χωρίς προσωπικά στοιχεία.',
    s5_badge: 'Χρηματοδοτείται από προαιρετικές λειτουργίες PRO – όχι από τα δεδομένα σας'
  },
  'uk': {
    chat_text: 'Привіт! Послухай цю пісню,<br>я щойно знайшов її:',
    routing_title: 'Автоперенаправлення активне',
    routing_desc: 'Музичні посилання з усіх підтримуваних сервісів відкриватимуться автоматично у фоновому режимі.',
    s5_title: 'Ваші дані належать лише вам.',
    s5_t1_title: 'Без реєстрації',
    s5_t1_desc: 'Без облікових записів, паролів чи пошти. Готово до використання.',
    s5_t2_title: '100% Без реклами',
    s5_t2_desc: 'Жодної сторонньої реклами, банерів чи трекерів.',
    s5_t3_title: 'Анонімно та безпечно',
    s5_t3_desc: 'Посилання відкриваються без прив’язки до особистості.',
    s5_badge: 'Фінансується додатковими функціями PRO – а не вашими даними'
  },
  'ar': {
    chat_text: 'مرحباً! استمع إلى هذه الأغنية<br>التي وجدتها للتو:',
    routing_title: 'إعادة التوجيه التلقائي نشطة',
    routing_desc: 'سيتم فتح روابط الموسيقى من جميع الخدمات المدعومة تلقائيًا في الخلفية.',
    s5_title: 'بياناتك ملك لك دائمًا.',
    s5_t1_title: 'بدون تسجيل',
    s5_t1_desc: 'لا حاجة لحسابات أو كلمات مرور أو بريد إلكتروني. جاهز فورًا.',
    s5_t2_title: '100% بدون إعلانات',
    s5_t2_desc: 'لا توجد إعلانات لأطراف ثالثة أو لافتات أو متتبعات.',
    s5_t3_title: 'مجهول وآمن',
    s5_t3_desc: 'تتم معالجة الروابط دون تحديد الهوية الشخصية.',
    s5_badge: 'ممولة من خلال ميزات PRO الاختيارية – وليس من بياناتك'
  }
};

function getXmlVal(xmlContent, key) {
  const match = xmlContent.match(new RegExp(`<string name="${key}">(.*?)</string>`, 's'));
  if (!match) return null;
  return match[1].replace(/&amp;/g, '&').replace(/\\'/g, "'").replace(/\\"/g, '"').trim();
}

function getAndroidStrings(locale) {
  const lang = locale.split('-')[0];
  const candidates = [
    `values-${locale.replace('-', '-r')}`,
    `values-${locale}`,
    `values-${lang}`,
    'values'
  ];
  const defaultXml = fs.readFileSync(path.join(RES_DIR, 'values', 'strings.xml'), 'utf8');
  let chosenXml = defaultXml;
  for (const cand of candidates) {
    const p = path.join(RES_DIR, cand, 'strings.xml');
    if (fs.existsSync(p)) {
      chosenXml = fs.readFileSync(p, 'utf8');
      break;
    }
  }

  function resolve(key) {
    return getXmlVal(chosenXml, key) || getXmlVal(defaultXml, key) || '';
  }

  return {
    app_name: 'SongFlip',
    app_subtitle: resolve('status_title') || 'Music Link Redirector',
    status_active: resolve('status_active') || 'SongFlip is active',
    btn_pause: resolve('btn_pause') || 'Pause SongFlip',
    status_partial_active: (resolve('status_partial_active') || '%1$d of %2$d music domains enabled')
      .replace('%1$d', '45')
      .replace('%2$d', '46'),
    target_service_label: resolve('target_service_label') || 'Preferred Target Player',
    target_service_subtitle: resolve('target_service_subtitle') || 'Select where links should open.',
    status_installed: resolve('status_installed') || 'App installed',
    status_browser: resolve('status_browser') || 'Web browser',
    playlist_matched_count: (resolve('playlist_matched_count') || '%1$d of %2$d tracks matched')
      .replace('%1$d', '50')
      .replace('%2$d', '50'),
    playlist_open_import: (resolve('playlist_open_import') || 'Open & Save in %1$s')
      .replace('%1$s', 'YouTube Music'),
    playlist_share_link: resolve('playlist_share_link') || 'Share Playlist Link'
  };
}

function getStringsForLocale(locale) {
  if (MASTER_STRINGS && MASTER_STRINGS[locale]) {
    return MASTER_STRINGS[locale];
  }

  const lang = locale.split('-')[0];
  const extra = EXTRA_TRANSLATIONS[lang] || EXTRA_TRANSLATIONS['en'];
  const android = getAndroidStrings(locale);

  // Retrieve headlines & sublines from localizations.json
  const getLoc = (key) => {
    const strObj = rawLocData.strings[key];
    if (strObj && strObj.localizations) {
      if (strObj.localizations[locale] && strObj.localizations[locale].value !== undefined) {
        return strObj.localizations[locale].value;
      }
      if (strObj.localizations[lang] && strObj.localizations[lang].value !== undefined) {
        return strObj.localizations[lang].value;
      }
      if (strObj.localizations['en-US'] && strObj.localizations['en-US'].value !== undefined) {
        return strObj.localizations['en-US'].value;
      }
    }
    return '';
  };

  return {
    // Screen 1: Instant Playback
    s1_prefix: getLoc('s1_prefix') || '0 Clicks. ',
    s1_accent: getLoc('s1_accent') || 'Instant Playback.',
    s1_suffix: '',
    s1_subline: getLoc('s1_subline') || 'Opens incoming music links instantly in your preferred player.',
    s1_chat_sender: 'Lena',
    s1_chat_time: '11:55',
    s1_chat_text: extra.chat_text,
    s1_chat_link: 'https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT',

    // Screen 2: All Platforms
    s2_prefix: getLoc('s2_prefix') || 'All Platforms. ',
    s2_accent: getLoc('s2_accent') || 'Music & Podcasts.',
    s2_suffix: '',
    s2_subline: 'Spotify, YouTube Music, Apple Music, Tidal, Deezer & Amazon Music.',
    s2_app_title: android.app_name,
    s2_app_subtitle: android.app_subtitle,
    s2_status_active: android.status_active,
    s2_status_pause: android.btn_pause,
    s2_routing_title: extra.routing_title,
    s2_routing_desc: extra.routing_desc,
    s2_routing_domains: android.status_partial_active,
    s2_target_title: android.target_service_label,
    s2_target_subtitle: android.target_service_subtitle,
    s2_installed: android.status_installed,
    s2_browser: android.status_browser,

    // Screen 3: Smart Links
    s3_prefix: getLoc('s3_prefix'),
    s3_accent: getLoc('s3_accent'),
    s3_suffix: getLoc('s3_suffix'),
    s3_subline: getLoc('s3_subline'),

    // Screen 4: Playlists
    s4_prefix: getLoc('s4_prefix'),
    s4_accent: getLoc('s4_accent'),
    s4_suffix: getLoc('s4_suffix'),
    s4_subline: (getLoc('s4_subline') || '')
      .replace('1 Klick', 'einem Klick')
      .replace('1 tap', 'one click')
      .replace('1-tap queue', 'one click')
      .replace('1-tap', 'one-tap')
      .replace('1 toque', 'un toque')
      .replace('1 clic', 'un clic'),
    s4_matched: android.playlist_matched_count,
    s4_btn_open: android.playlist_open_import,
    s4_btn_share: android.playlist_share_link,

    // Screen 5: Privacy
    s5_prefix: getLoc('s5_prefix') || '100% Private. ',
    s5_accent: 'Zero Tracking.',
    s5_suffix: '',
    s5_subline: getLoc('s5_subline'),
    s5_card_title: extra.s5_title,
    s5_t1_title: extra.s5_t1_title,
    s5_t1_desc: extra.s5_t1_desc,
    s5_t2_title: extra.s5_t2_title,
    s5_t2_desc: extra.s5_t2_desc,
    s5_t3_title: extra.s5_t3_title,
    s5_t3_desc: extra.s5_t3_desc,
    s5_card_badge: extra.s5_badge
  };
}

const SCREENS = [
  { template: '01-instant-playback.html', outName: 'screen_1.png' },
  { template: '02-all-platforms.html', outName: 'screen_2.png' },
  { template: '03-smart-links.html', outName: 'screen_3.png' },
  { template: '04-playlist-conversion.html', outName: 'screen_4.png' },
  { template: '05-privacy.html', outName: 'screen_5.png' }
];

async function renderLocale(browser, locale) {
  const strings = getStringsForLocale(locale);
  const outDir = path.join(OUTPUT_BASE, locale);
  fs.mkdirSync(outDir, { recursive: true });

  const page = await browser.newPage();
  await page.setViewport({ width: 1080, height: 2400, deviceScaleFactor: 1 });

  for (const item of SCREENS) {
    const templatePath = path.join(TEMPLATES_DIR, item.template);
    if (!fs.existsSync(templatePath)) continue;

    let html = fs.readFileSync(templatePath, 'utf8');
    for (const [key, val] of Object.entries(strings)) {
      html = html.replace(new RegExp(`\\{\\{${key}\\}\\}`, 'g'), val);
    }

    const tmpFile = path.join(TEMPLATES_DIR, `_tmp_${locale}_${item.outName}.html`);
    fs.writeFileSync(tmpFile, html, 'utf8');

    try {
      await page.goto(`file://${tmpFile}`, { waitUntil: 'networkidle2', timeout: 20000 });
      await new Promise((r) => setTimeout(r, 400));

      const outPath = path.join(outDir, item.outName);
      await page.screenshot({ path: outPath, type: 'png', clip: { x: 0, y: 0, width: 1080, height: 2400 } });
    } finally {
      if (fs.existsSync(tmpFile)) fs.unlinkSync(tmpFile);
    }
  }

  await page.close();
  console.log(`✓ Completed [${locale}] (5 screens)`);
}

async function main() {
  const allLocales = Object.keys(rawLocData.strings.s1_accent.localizations);
  const specifiedLocale = process.argv[2];
  const targetLocales = specifiedLocale ? [specifiedLocale] : allLocales;

  console.log(`Starting render for ${targetLocales.length} locales...`);
  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const startTime = Date.now();
  for (let i = 0; i < targetLocales.length; i++) {
    const loc = targetLocales[i];
    console.log(`[${i + 1}/${targetLocales.length}] Rendering ${loc}...`);
    try {
      await renderLocale(browser, loc);
    } catch (err) {
      console.error(`Error rendering ${loc}:`, err.message);
    }
  }

  await browser.close();
  const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
  console.log(`\n🎉 Finished rendering ${targetLocales.length} locales in ${elapsed}s!`);
}

main().catch((err) => {
  console.error('Fatal execution error:', err);
  process.exit(1);
});
