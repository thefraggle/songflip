#!/usr/bin/env python3
"""
Adds In-App Feedback dialog strings across all 31 Android localized values directories.
"""

import os
import re

RES_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res")

TRANSLATIONS = {
    'en': {
        'title': 'Feedback & Support',
        'cat_bug': '🐛 Bug',
        'cat_feature': '💡 Idea',
        'cat_general': '💬 General',
        'message_hint': 'Describe your issue, idea, or feedback...',
        'email_hint': 'Your email (optional, for reply)',
        'privacy_note': 'Zero-Tracking: Only app version and device model are included.',
        'btn_send': 'Send Feedback',
        'btn_mail_fallback': 'Write an email instead',
        'success': 'Thank you for your feedback!',
        'error_empty': 'Please enter at least 10 characters.',
        'error_network': 'Unable to send feedback. Please try email instead.'
    },
    'de': {
        'title': 'Feedback & Support',
        'cat_bug': '🐛 Bug',
        'cat_feature': '💡 Idee',
        'cat_general': '💬 Allgemein',
        'message_hint': 'Beschreibe dein Anliegen, deine Idee oder ein Problem...',
        'email_hint': 'Deine E-Mail (optional für Rückfragen)',
        'privacy_note': 'Zero-Tracking: Nur App-Version und Gerätetyp werden übertragen.',
        'btn_send': 'Feedback senden',
        'btn_mail_fallback': 'Lieber per E-Mail schreiben',
        'success': 'Vielen Dank für dein Feedback!',
        'error_empty': 'Bitte gib mindestens 10 Zeichen ein.',
        'error_network': 'Senden fehlgeschlagen. Bitte nutze stattdessen E-Mail.'
    },
    'es': {
        'title': 'Comentarios y Soporte',
        'cat_bug': '🐛 Error',
        'cat_feature': '💡 Idea',
        'cat_general': '💬 General',
        'message_hint': 'Describe tu problema, sugerencia o comentario...',
        'email_hint': 'Tu correo (opcional, para responder)',
        'privacy_note': 'Cero seguimiento: Solo se envían la versión de la app y el modelo de dispositivo.',
        'btn_send': 'Enviar comentarios',
        'btn_mail_fallback': 'Prefiero enviar un correo',
        'success': '¡Gracias por tus comentarios!',
        'error_empty': 'Por favor escribe al menos 10 caracteres.',
        'error_network': 'No se pudo enviar. Intenta por correo electrónico.'
    },
    'fr': {
        'title': 'Commentaires et Support',
        'cat_bug': '🐛 Bug',
        'cat_feature': '💡 Idée',
        'cat_general': '💬 Général',
        'message_hint': 'Décrivez votre problème, idée ou commentaire...',
        'email_hint': 'Votre e-mail (facultatif, pour réponse)',
        'privacy_note': "Zéro traçage : Seuls la version de l\\'application et le modèle d\\'appareil sont transmis.",
        'btn_send': 'Envoyer les commentaires',
        'btn_mail_fallback': 'Écrire par e-mail à la place',
        'success': 'Merci pour vos commentaires !',
        'error_empty': 'Veuillez saisir au moins 10 caractères.',
        'error_network': "Échec de l\\'envoi. Veuillez utiliser l\\'e-mail."
    },
    'it': {
        'title': 'Feedback e Supporto',
        'cat_bug': '🐛 Bug',
        'cat_feature': '💡 Idea',
        'cat_general': '💬 Generale',
        'message_hint': 'Descrivi il tuo problema, idea o feedback...',
        'email_hint': 'La tua email (opzionale, per risposta)',
        'privacy_note': "Zero tracciamento: Vengono inviati solo versione dell\\'app e modello dispositivo.",
        'btn_send': 'Invia feedback',
        'btn_mail_fallback': "Scrivi invece un\\'email",
        'success': 'Grazie per il tuo feedback!',
        'error_empty': 'Inserisci almeno 10 caratteri.',
        'error_network': 'Invio non riuscito. Prova via email.'
    },
    'pt': {
        'title': 'Feedback e Suporte',
        'cat_bug': '🐛 Bug',
        'cat_feature': '💡 Ideia',
        'cat_general': '💬 Geral',
        'message_hint': 'Descreva o problema, ideia ou sugestão...',
        'email_hint': 'Seu e-mail (opcional, para resposta)',
        'privacy_note': 'Zero rastreamento: Apenas versão do app e modelo do aparelho são enviados.',
        'btn_send': 'Enviar feedback',
        'btn_mail_fallback': 'Preferir escrever por e-mail',
        'success': 'Obrigado pelo seu feedback!',
        'error_empty': 'Digite pelo menos 10 caracteres.',
        'error_network': 'Não foi possível enviar. Tente por e-mail.'
    },
    'nl': {
        'title': 'Feedback & Ondersteuning',
        'cat_bug': '🐛 Fout',
        'cat_feature': '💡 Idee',
        'cat_general': '💬 Algemeen',
        'message_hint': 'Beschrijf je probleem, idee of feedback...',
        'email_hint': 'Je e-mailadres (optioneel, voor reactie)',
        'privacy_note': 'Nul tracking: Alleen appversie en apparaatmodel worden verzonden.',
        'btn_send': 'Feedback verzenden',
        'btn_mail_fallback': 'Liever via e-mail sturen',
        'success': 'Bedankt voor je feedback!',
        'error_empty': 'Voer minimaal 10 tekens in.',
        'error_network': 'Verzenden mislukt. Probeer het via e-mail.'
    },
    'pl': {
        'title': 'Opinie i Pomoc',
        'cat_bug': '🐛 Błąd',
        'cat_feature': '💡 Pomysł',
        'cat_general': '💬 Ogólne',
        'message_hint': 'Opisz problem, pomysł lub opinię...',
        'email_hint': 'Twój e-mail (opcjonalnie, do odpowiedzi)',
        'privacy_note': 'Zero śledzenia: Przesyłana jest tylko wersja aplikacji i model urządzenia.',
        'btn_send': 'Wyślij opinię',
        'btn_mail_fallback': 'Wolisz napisać e-mail?',
        'success': 'Dziękujemy za Twoją opinię!',
        'error_empty': 'Wpisz co najmniej 10 znaków.',
        'error_network': 'Nie udało się wysłać. Spróbuj przez e-mail.'
    },
    'ru': {
        'title': 'Отзывы и Поддержка',
        'cat_bug': '🐛 Ошибка',
        'cat_feature': '💡 Идея',
        'cat_general': '💬 Общее',
        'message_hint': 'Опишите проблему, идею или отзыв...',
        'email_hint': 'Ваш e-mail (необязательно, для ответа)',
        'privacy_note': 'Ноль слежки: Передаются только версия приложения и модель устройства.',
        'btn_send': 'Отправить отзыв',
        'btn_mail_fallback': 'Написать по эл. почте',
        'success': 'Спасибо за ваш отзыв!',
        'error_empty': 'Введите не менее 10 символов.',
        'error_network': 'Не удалось отправить. Попробуйте через e-mail.'
    },
    'uk': {
        'title': 'Відгуки та Підтримка',
        'cat_bug': '🐛 Помилка',
        'cat_feature': '💡 Ідея',
        'cat_general': '💬 Загальне',
        'message_hint': 'Опишіть проблему, ідею або відгук...',
        'email_hint': "Ваш e-mail (необов\\'язково, для відповіді)",
        'privacy_note': 'Нуль відстеження: Передаються лише версія додатка та модель пристрою.',
        'btn_send': 'Надіслати відгук',
        'btn_mail_fallback': 'Написати на e-mail',
        'success': 'Дякуємо за ваш відгук!',
        'error_empty': 'Введіть щонайменше 10 символів.',
        'error_network': 'Не вдалося надіслати. Спробуйте через e-mail.'
    },
    'tr': {
        'title': 'Geri Bildirim ve Destek',
        'cat_bug': '🐛 Hata',
        'cat_feature': '💡 Fikir',
        'cat_general': '💬 Genel',
        'message_hint': 'Sorununuzu, fikrinizi veya geri bildiriminizi açıklayın...',
        'email_hint': 'E-postanız (isteğe bağlı, yanıt için)',
        'privacy_note': 'Sıfır takip: Yalnızca uygulama sürümü ve cihaz modeli gönderilir.',
        'btn_send': 'Geri Bildirim Gönder',
        'btn_mail_fallback': 'E-posta ile yazmayı tercih et',
        'success': 'Geri bildiriminiz için teşekkürler!',
        'error_empty': 'Lütfen en az 10 karakter girin.',
        'error_network': 'Gönderilemedi. Lütfen e-posta ile deneyin.'
    },
    'sv': {
        'title': 'Feedback & Support',
        'cat_bug': '🐛 Bugg',
        'cat_feature': '💡 Idé',
        'cat_general': '💬 Allmänt',
        'message_hint': 'Beskriv ditt problem, din idé eller feedback...',
        'email_hint': 'Din e-post (valfritt, för svar)',
        'privacy_note': 'Noll spårning: Endast appversion och enhetsmodell skickas.',
        'btn_send': 'Skicka feedback',
        'btn_mail_fallback': 'Skriv hellre ett e-postmeddelande',
        'success': 'Tack för din feedback!',
        'error_empty': 'Ange minst 10 tecken.',
        'error_network': 'Kunde inte skicka. Försök via e-post istället.'
    },
    'da': {
        'title': 'Feedback & Support',
        'cat_bug': '🐛 Fejl',
        'cat_feature': '💡 Idé',
        'cat_general': '💬 Generelt',
        'message_hint': 'Beskriv dit problem, din idé eller feedback...',
        'email_hint': 'Din e-mail (valgfrit, for svar)',
        'privacy_note': 'Nul sporing: Kun appversion og enhedsmodel sendes.',
        'btn_send': 'Send feedback',
        'btn_mail_fallback': 'Skriv hellere en e-mail',
        'success': 'Tak for din feedback!',
        'error_empty': 'Indtast mindst 10 tegn.',
        'error_network': 'Kunne ikke sende. Prøv venligst via e-mail.'
    },
    'no': {
        'title': 'Tilbakemelding og Brukerstøtte',
        'cat_bug': '🐛 Feil',
        'cat_feature': '💡 Idé',
        'cat_general': '💬 Generelt',
        'message_hint': 'Beskriv problemet, ideen eller tilbakemeldingen...',
        'email_hint': 'Din e-post (valgfritt, for svar)',
        'privacy_note': 'Null sporing: Bare appversjon og enhetsmodell sendes.',
        'btn_send': 'Send tilbakemelding',
        'btn_mail_fallback': 'Skriv heller en e-post',
        'success': 'Takk for tilbakemeldingen!',
        'error_empty': 'Skriv inn minst 10 tegn.',
        'error_network': 'Kunne ikke sende. Prøv via e-post i stedet.'
    },
    'nb': {
        'title': 'Tilbakemelding og Brukerstøtte',
        'cat_bug': '🐛 Feil',
        'cat_feature': '💡 Idé',
        'cat_general': '💬 Generelt',
        'message_hint': 'Beskriv problemet, ideen eller tilbakemeldingen...',
        'email_hint': 'Din e-post (valgfritt, for svar)',
        'privacy_note': 'Null sporing: Bare appversjon og enhetsmodell sendes.',
        'btn_send': 'Send tilbakemelding',
        'btn_mail_fallback': 'Skriv heller en e-post',
        'success': 'Takk for tilbakemeldingen!',
        'error_empty': 'Skriv inn minst 10 tegn.',
        'error_network': 'Kunne ikke sende. Prøv via e-post i stedet.'
    },
    'fi': {
        'title': 'Palaute ja Tuki',
        'cat_bug': '🐛 Virhe',
        'cat_feature': '💡 Idea',
        'cat_general': '💬 Yleinen',
        'message_hint': 'Kuvaile ongelmasi, ideasi tai palautteesi...',
        'email_hint': 'Sähköpostisi (valinnainen, vastausta varten)',
        'privacy_note': 'Nolla seurantaa: Vain sovelluksen versio ja laitemalli lähetetään.',
        'btn_send': 'Lähetä palaute',
        'btn_mail_fallback': 'Kirjoita mieluummin sähköpostia',
        'success': 'Kiitos palautteestasi!',
        'error_empty': 'Kirjoita vähintään 10 merkkiä.',
        'error_network': 'Lähetys epäonnistui. Kokeile sähköpostilla.'
    },
    'cs': {
        'title': 'Zpětná vazba a Podpora',
        'cat_bug': '🐛 Chyba',
        'cat_feature': '💡 Nápad',
        'cat_general': '💬 Obecné',
        'message_hint': 'Popište svůj problém, nápad nebo připomínku...',
        'email_hint': 'Váš e-mail (volitelné, pro odpověď)',
        'privacy_note': 'Nulové sledování: Odesílá se pouze verze aplikace a model zařízení.',
        'btn_send': 'Odeslat zpětnou vazbu',
        'btn_mail_fallback': 'Raději napsat e-mail',
        'success': 'Děkujeme za vaši zpětnou vazbu!',
        'error_empty': 'Zadejte alespoň 10 znaků.',
        'error_network': 'Odeslání se nezdařilo. Zkuste to e-mailem.'
    },
    'ro': {
        'title': 'Feedback și Asistență',
        'cat_bug': '🐛 Problemă',
        'cat_feature': '💡 Idee',
        'cat_general': '💬 General',
        'message_hint': 'Descrieți problema, ideea sau sugestia dvs...',
        'email_hint': 'E-mailul dvs. (opțional, pentru răspuns)',
        'privacy_note': 'Fără urmărire: Se trimit doar versiunea aplicației și modelul dispozitivului.',
        'btn_send': 'Trimite feedback',
        'btn_mail_fallback': 'Prefer să scriu un e-mail',
        'success': 'Vă mulțumim pentru feedback!',
        'error_empty': 'Introduceți cel puțin 10 caractere.',
        'error_network': 'Trimiterea a eșuat. Încercați prin e-mail.'
    },
    'hu': {
        'title': 'Visszajelzés és Támogatás',
        'cat_bug': '🐛 Hiba',
        'cat_feature': '💡 Ötlet',
        'cat_general': '💬 Általános',
        'message_hint': 'Írja le a problémát, ötletet vagy észrevételt...',
        'email_hint': 'Az Ön e-mailje (opcionális, válaszhoz)',
        'privacy_note': 'Nulla követés: Csak az alkalmazásverzió és a készüléktípus kerül továbbításra.',
        'btn_send': 'Visszajelzés küldése',
        'btn_mail_fallback': 'Inkább e-mailt írok',
        'success': 'Köszönjük a visszajelzést!',
        'error_empty': 'Kérjük, írjon be legalább 10 karaktert.',
        'error_network': 'Nem sikerült elküldeni. Kérjük, próbálja e-mailben.'
    },
    'el': {
        'title': 'Σχόλια και Υποστήριξη',
        'cat_bug': '🐛 Σφάλμα',
        'cat_feature': '💡 Ιδέα',
        'cat_general': '💬 Γενικά',
        'message_hint': 'Περιγράψτε το πρόβλημα, την ιδέα ή τα σχόλιά σας...',
        'email_hint': 'Το email σας (προαιρετικό, για απάντηση)',
        'privacy_note': 'Μηδενική παρακολούθηση: Αποστέλλονται μόνο η έκδοση εφαρμογής και το μοντέλο συσκευής.',
        'btn_send': 'Αποστολή σχολίων',
        'btn_mail_fallback': 'Προτιμάτε να στείλετε email;',
        'success': 'Ευχαριστούμε για τα σχόλιά σας!',
        'error_empty': 'Παρακαλούμε εισάγετε τουλάχιστον 10 χαρακτήρες.',
        'error_network': 'Αποτυχία αποστολής. Δοκιμάστε μέσω email.'
    },
    'ja': {
        'title': 'フィードバックとサポート',
        'cat_bug': '🐛 不具合',
        'cat_feature': '💡 アイデア',
        'cat_general': '💬 その他',
        'message_hint': '問題、ご要望、ご意見をご記入ください...',
        'email_hint': 'メールアドレス（返信をご希望の場合）',
        'privacy_note': 'トラッキングゼロ：アプリのバージョンと端末モデルのみ送信されます。',
        'btn_send': 'フィードバックを送信',
        'btn_mail_fallback': 'メールで送信する',
        'success': 'フィードバックありがとうございます！',
        'error_empty': '10文字以上入力してください。',
        'error_network': '送信できませんでした。メールでお試しください。'
    },
    'ko': {
        'title': '피드백 및 지원',
        'cat_bug': '🐛 버그',
        'cat_feature': '💡 제안',
        'cat_general': '💬 일반',
        'message_hint': '문제, 제안 또는 의견을 입력해주세요...',
        'email_hint': '이메일 (답변 희망 시 입력)',
        'privacy_note': '제로 트래킹: 앱 버전과 기기 모델 정보만 전송됩니다.',
        'btn_send': '피드백 보내기',
        'btn_mail_fallback': '이메일로 보내기',
        'success': '소중한 피드백 감사합니다!',
        'error_empty': '최소 10자 이상 입력해주세요.',
        'error_network': '전송에 실패했습니다. 이메일로 문의해주세요.'
    },
    'zh-rCN': {
        'title': '反馈与支持',
        'cat_bug': '🐛 问题反馈',
        'cat_feature': '💡 功能建议',
        'cat_general': '💬 其他意见',
        'message_hint': '请描述您遇到的问题、建议或意见...',
        'email_hint': '您的邮箱（选填，用于接收回复）',
        'privacy_note': '零追踪：仅发送应用版本和设备型号以排查问题。',
        'btn_send': '提交反馈',
        'btn_mail_fallback': '通过邮件发送',
        'success': '感谢您的反馈！',
        'error_empty': '请输入至少10个字符。',
        'error_network': '发送失败，请尝试使用邮件发送。'
    },
    'zh-rTW': {
        'title': '回饋與支援',
        'cat_bug': '🐛 問題回報',
        'cat_feature': '💡 功能建議',
        'cat_general': '💬 其他意見',
        'message_hint': '請描述您遇到的問題、建議或意見...',
        'email_hint': '您的電子郵件（選填，便於回覆）',
        'privacy_note': '零追蹤：僅傳送應用程式版本和裝置型號以利問題排查。',
        'btn_send': '送出回饋',
        'btn_mail_fallback': '改用電子郵件傳送',
        'success': '感謝您的回饋！',
        'error_empty': '請輸入至少10個字元。',
        'error_network': '傳送失敗，請嘗試使用電子郵件。'
    },
    'vi': {
        'title': 'Phản hồi & Hỗ trợ',
        'cat_bug': '🐛 Lỗi',
        'cat_feature': '💡 Ý tưởng',
        'cat_general': '💬 Chung',
        'message_hint': 'Hãy mô tả vấn đề, ý tưởng hoặc phản hồi của bạn...',
        'email_hint': 'Email của bạn (không bắt buộc, để phản hồi)',
        'privacy_note': 'Không theo dõi: Chỉ gửi phiên bản ứng dụng và kiểu thiết bị.',
        'btn_send': 'Gửi phản hồi',
        'btn_mail_fallback': 'Viết qua email thay thế',
        'success': 'Cảm ơn bạn đã gửi phản hồi!',
        'error_empty': 'Vui lòng nhập ít nhất 10 ký tự.',
        'error_network': 'Không thể gửi phản hồi. Vui lòng thử qua email.'
    },
    'th': {
        'title': 'ข้อเสนอแนะและความช่วยเหลือ',
        'cat_bug': '🐛 ปัญหา',
        'cat_feature': '💡 ไอเดีย',
        'cat_general': '💬 ทั่วไป',
        'message_hint': 'อธิบายปัญหา ไอเดีย หรือข้อเสนอแนะของคุณ...',
        'email_hint': 'อีเมลของคุณ (ไม่บังคับ เพื่อรอการตอบกลับ)',
        'privacy_note': 'ไม่มีการติดตาม: ส่งเฉพาะเวอร์ชันแอปและรุ่นอุปกรณ์เท่านั้น',
        'btn_send': 'ส่งข้อเสนอแนะ',
        'btn_mail_fallback': 'ส่งทางอีเมลแทน',
        'success': 'ขอบคุณสำหรับข้อเสนอแนะของคุณ!',
        'error_empty': 'โปรดระบุอย่างน้อย 10 ตัวอักษร',
        'error_network': 'ส่งไม่สำเร็จ โปรดส่งทางอีเมลแทน'
    },
    'id': {
        'title': 'Masukan & Dukungan',
        'cat_bug': '🐛 Masalah',
        'cat_feature': '💡 Ide',
        'cat_general': '💬 Umum',
        'message_hint': 'Jelaskan masalah, ide, atau saran Anda...',
        'email_hint': 'Email Anda (opsional, untuk balasan)',
        'privacy_note': 'Nol pelacakan: Hanya versi aplikasi dan model perangkat yang dikirim.',
        'btn_send': 'Kirim Masukan',
        'btn_mail_fallback': 'Lebih suka kirim email',
        'success': 'Terima kasih atas masukan Anda!',
        'error_empty': 'Masukkan minimal 10 karakter.',
        'error_network': 'Gagal mengirim masukan. Silakan coba lewat email.'
    },
    'in': {
        'title': 'Masukan & Dukungan',
        'cat_bug': '🐛 Masalah',
        'cat_feature': '💡 Ide',
        'cat_general': '💬 Umum',
        'message_hint': 'Jelaskan masalah, ide, atau saran Anda...',
        'email_hint': 'Email Anda (opsional, untuk balasan)',
        'privacy_note': 'Nol pelacakan: Hanya versi aplikasi dan model perangkat yang dikirim.',
        'btn_send': 'Kirim Masukan',
        'btn_mail_fallback': 'Lebih suka kirim email',
        'success': 'Terima kasih atas masukan Anda!',
        'error_empty': 'Masukkan minimal 10 karakter.',
        'error_network': 'Gagal mengirim masukan. Silakan coba lewat email.'
    },
    'hi': {
        'title': 'प्रतिक्रिया और सहायता',
        'cat_bug': '🐛 बग',
        'cat_feature': '💡 विचार',
        'cat_general': '💬 सामान्य',
        'message_hint': 'अपनी समस्या, विचार या प्रतिक्रिया बताएं...',
        'email_hint': 'आपका ईमेल (वैकल्पिक, उत्तर के लिए)',
        'privacy_note': 'शून्य ट्रैकिंग: समस्या निवारण के लिए केवल ऐप संस्करण और डिवाइस मॉडल भेजा जाता है।',
        'btn_send': 'प्रतिक्रिया भेजें',
        'btn_mail_fallback': 'इसके बजाय ईमेल लिखें',
        'success': 'आपकी प्रतिक्रिया के लिए धन्यवाद!',
        'error_empty': 'कृपया कम से कम 10 वर्ण दर्ज करें।',
        'error_network': 'भेजने में असमर्थ। कृपया ईमेल का उपयोग करें।'
    },
    'bn': {
        'title': 'প্রতিক্রিয়া ও সহায়তা',
        'cat_bug': '🐛 বাগ',
        'cat_feature': '💡 ধারণা',
        'cat_general': '💬 সাধারণ',
        'message_hint': 'আপনার সমস্যা, ধারণা বা প্রতিক্রিয়া বর্ণনা করুন...',
        'email_hint': 'আপনার ইমেল (ঐচ্ছিক, উত্তরের জন্য)',
        'privacy_note': 'শূন্য ট্র্যাকিং: সমস্যা সমাধানের জন্য শুধুমাত্র অ্যাপ সংস্করণ ও ডিভাইস মডেল পাঠানো হয়।',
        'btn_send': 'প্রতিক্রিয়া পাঠান',
        'btn_mail_fallback': 'এর পরিবর্তে ইমেল লিখুন',
        'success': 'আপনার প্রতিক্রিয়ার জন্য ধন্যবাদ!',
        'error_empty': 'অনুগ্রহ করে কমপক্ষে ১০টি অক্ষর লিখুন।',
        'error_network': 'পাঠানো সম্ভব হয়নি। অনুগ্রহ করে ইমেলের মাধ্যমে চেষ্টা করুন।'
    },
    'mr': {
        'title': 'अभिप्राय आणि समर्थन',
        'cat_bug': '🐛 त्रुटी',
        'cat_feature': '💡 कल्पना',
        'cat_general': '💬 सामान्य',
        'message_hint': 'तुमची समस्या, कल्पना किंवा अभिप्राय सांगा...',
        'email_hint': 'तुमचा ईमेल (पर्यायी, उत्तरासाठी)',
        'privacy_note': 'शून्य ट्रॅकिंग: समस्या निवारणासाठी फक्त अॅप आवृत्ती आणि डिव्हाइस मॉडेल पाठवले जाते।',
        'btn_send': 'अभिप्राय पाठवा',
        'btn_mail_fallback': 'त्याऐवजी ईमेल लिहा',
        'success': 'तुमच्या अभिप्रायाबद्दल धन्यवाद!',
        'error_empty': 'कृपया किमान १० वर्ण प्रविष्ट करा.',
        'error_network': 'पाठवता आले नाही. कृपया ईमेलद्वारे प्रयत्न करा.'
    }
}

def get_lang_code(folder_name):
    if folder_name == "values":
        return "en"
    return folder_name.replace("values-", "")

def update_strings_file(file_path, t):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Check if feedback_title already exists
    if "feedback_title" in content:
        content = re.sub(r'<string name="feedback_title">.*?</string>', f'<string name="feedback_title">{t["title"]}</string>', content)
        content = re.sub(r'<string name="feedback_cat_bug">.*?</string>', f'<string name="feedback_cat_bug">{t["cat_bug"]}</string>', content)
        content = re.sub(r'<string name="feedback_cat_feature">.*?</string>', f'<string name="feedback_cat_feature">{t["cat_feature"]}</string>', content)
        content = re.sub(r'<string name="feedback_cat_general">.*?</string>', f'<string name="feedback_cat_general">{t["cat_general"]}</string>', content)
        content = re.sub(r'<string name="feedback_message_hint">.*?</string>', f'<string name="feedback_message_hint">{t["message_hint"]}</string>', content)
        content = re.sub(r'<string name="feedback_email_hint">.*?</string>', f'<string name="feedback_email_hint">{t["email_hint"]}</string>', content)
        content = re.sub(r'<string name="feedback_privacy_note">.*?</string>', f'<string name="feedback_privacy_note">{t["privacy_note"]}</string>', content)
        content = re.sub(r'<string name="feedback_btn_send">.*?</string>', f'<string name="feedback_btn_send">{t["btn_send"]}</string>', content)
        content = re.sub(r'<string name="feedback_btn_mail_fallback">.*?</string>', f'<string name="feedback_btn_mail_fallback">{t["btn_mail_fallback"]}</string>', content)
        content = re.sub(r'<string name="feedback_success">.*?</string>', f'<string name="feedback_success">{t["success"]}</string>', content)
        content = re.sub(r'<string name="feedback_error_empty">.*?</string>', f'<string name="feedback_error_empty">{t["error_empty"]}</string>', content)
        content = re.sub(r'<string name="feedback_error_network">.*?</string>', f'<string name="feedback_error_network">{t["error_network"]}</string>', content)
    else:
        # Append before </resources>
        block = (
            f"    <!-- In-App Feedback Dialog -->\n"
            f'    <string name="feedback_title">{t["title"]}</string>\n'
            f'    <string name="feedback_cat_bug">{t["cat_bug"]}</string>\n'
            f'    <string name="feedback_cat_feature">{t["cat_feature"]}</string>\n'
            f'    <string name="feedback_cat_general">{t["cat_general"]}</string>\n'
            f'    <string name="feedback_message_hint">{t["message_hint"]}</string>\n'
            f'    <string name="feedback_email_hint">{t["email_hint"]}</string>\n'
            f'    <string name="feedback_privacy_note">{t["privacy_note"]}</string>\n'
            f'    <string name="feedback_btn_send">{t["btn_send"]}</string>\n'
            f'    <string name="feedback_btn_mail_fallback">{t["btn_mail_fallback"]}</string>\n'
            f'    <string name="feedback_success">{t["success"]}</string>\n'
            f'    <string name="feedback_error_empty">{t["error_empty"]}</string>\n'
            f'    <string name="feedback_error_network">{t["error_network"]}</string>\n'
            f"</resources>"
        )
        content = re.sub(r'</resources>\s*$', block, content)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)

def main():
    updated = 0
    for root, dirs, files in os.walk(RES_DIR):
        folder = os.path.basename(root)
        if folder == "values" or folder.startswith("values-"):
            if "strings.xml" in files:
                lang = get_lang_code(folder)
                if lang in TRANSLATIONS:
                    update_strings_file(os.path.join(root, "strings.xml"), TRANSLATIONS[lang])
                    updated += 1
                else:
                    print(f"Warning: Missing translation for {lang}")
    print(f"Successfully updated {updated} language files.")

if __name__ == "__main__":
    main()
