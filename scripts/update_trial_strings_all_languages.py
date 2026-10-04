#!/usr/bin/env python3
"""
Adds dynamic Free Trial strings across all 31 Android localized values directories.
"""

import os
import re

RES_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res")

TRANSLATIONS = {
    'en': {
        'badge': '%1$d DAYS FREE',
        'price_trial': '%1$d days free, then %2$s / month (instead of %3$s)',
        'price_trial_intro': '%1$d days free, then %2$s / month in year 1 (instead of %3$s)',
        'price_trial_simple': '%1$d days free, then %2$s / year',
        'btn_trial': 'Try %1$d days free'
    },
    'de': {
        'badge': '%1$d TAGE GRATIS',
        'price_trial': '%1$d Tage kostenlos, danach %2$s / Monat (statt %3$s)',
        'price_trial_intro': '%1$d Tage kostenlos, danach %2$s / Monat im 1. Jahr (statt %3$s)',
        'price_trial_simple': '%1$d Tage kostenlos, danach %2$s / Jahr',
        'btn_trial': '%1$d Tage kostenlos testen'
    },
    'es': {
        'badge': '%1$d DÍAS GRATIS',
        'price_trial': '%1$d días gratis, luego %2$s / mes (en vez de %3$s)',
        'price_trial_intro': '%1$d días gratis, luego %2$s / mes el 1.er año (en vez de %3$s)',
        'price_trial_simple': '%1$d días gratis, luego %2$s / año',
        'btn_trial': 'Probar %1$d días gratis'
    },
    'fr': {
        'badge': '%1$d JOURS GRATUITS',
        'price_trial': '%1$d jours gratuits, puis %2$s / mois (au lieu de %3$s)',
        'price_trial_intro': '%1$d jours gratuits, puis %2$s / mois la 1re année (au lieu de %3$s)',
        'price_trial_simple': '%1$d jours gratuits, puis %2$s / an',
        'btn_trial': 'Essayer %1$d jours gratuits'
    },
    'it': {
        'badge': '%1$d GIORNI GRATIS',
        'price_trial': '%1$d giorni gratis, poi %2$s / mese (invece di %3$s)',
        'price_trial_intro': '%1$d giorni gratis, poi %2$s / mese il 1° anno (invece di %3$s)',
        'price_trial_simple': '%1$d giorni gratis, poi %2$s / anno',
        'btn_trial': 'Prova gratis per %1$d giorni'
    },
    'pt': {
        'badge': '%1$d DIAS GRÁTIS',
        'price_trial': '%1$d dias grátis, depois %2$s / mês (em vez de %3$s)',
        'price_trial_intro': '%1$d dias grátis, depois %2$s / mês no 1.º ano (em vez de %3$s)',
        'price_trial_simple': '%1$d dias grátis, depois %2$s / ano',
        'btn_trial': 'Testar por %1$d dias grátis'
    },
    'nl': {
        'badge': '%1$d DAGEN GRATIS',
        'price_trial': '%1$d dagen gratis, daarna %2$s / maand (in plaats van %3$s)',
        'price_trial_intro': '%1$d dagen gratis, daarna %2$s / maand in het 1e jaar (in plaats van %3$s)',
        'price_trial_simple': '%1$d dagen gratis, daarna %2$s / jaar',
        'btn_trial': '%1$d dagen gratis proberen'
    },
    'pl': {
        'badge': '%1$d DNI ZA DARMO',
        'price_trial': '%1$d dni za darmo, potem %2$s / mies. (zamiast %3$s)',
        'price_trial_intro': '%1$d dni za darmo, potem %2$s / mies. w 1. roku (zamiast %3$s)',
        'price_trial_simple': '%1$d dni za darmo, potem %2$s / rok',
        'btn_trial': 'Wypróbuj %1$d dni za darmo'
    },
    'ru': {
        'badge': '%1$d ДНЕЙ БЕСПЛАТНО',
        'price_trial': '%1$d дней бесплатно, затем %2$s / мес. (вместо %3$s)',
        'price_trial_intro': '%1$d дней бесплатно, затем %2$s / мес. в первый год (вместо %3$s)',
        'price_trial_simple': '%1$d дней бесплатно, затем %2$s / год',
        'btn_trial': 'Попробовать %1$d дней бесплатно'
    },
    'uk': {
        'badge': '%1$d ДНІВ БЕЗКОШТОВНО',
        'price_trial': '%1$d днів безкоштовно, потім %2$s / міс. (замість %3$s)',
        'price_trial_intro': '%1$d днів безкоштовно, потім %2$s / міс. у 1-й рік (замість %3$s)',
        'price_trial_simple': '%1$d днів безкоштовно, потім %2$s / рік',
        'btn_trial': 'Спробувати %1$d днів безкоштовно'
    },
    'tr': {
        'badge': '%1$d GÜN ÜCRETSİZ',
        'price_trial': '%1$d gün ücretsiz, sonra %2$s / ay (%3$s yerine)',
        'price_trial_intro': '%1$d gün ücretsiz, sonra 1. yıl %2$s / ay (%3$s yerine)',
        'price_trial_simple': '%1$d gün ücretsiz, sonra %2$s / yıl',
        'btn_trial': '%1$d gün ücretsiz dene'
    },
    'sv': {
        'badge': '%1$d DAGAR GRATIS',
        'price_trial': '%1$d dagar gratis, sedan %2$s / månad (istället för %3$s)',
        'price_trial_intro': '%1$d dagar gratis, sedan %2$s / månad under år 1 (istället för %3$s)',
        'price_trial_simple': '%1$d dagar gratis, sedan %2$s / år',
        'btn_trial': 'Prova %1$d dagar gratis'
    },
    'da': {
        'badge': '%1$d DAGE GRATIS',
        'price_trial': '%1$d dage gratis, derefter %2$s / måned (i stedet for %3$s)',
        'price_trial_intro': '%1$d dage gratis, derefter %2$s / måned i 1. år (i stedet for %3$s)',
        'price_trial_simple': '%1$d dage gratis, derefter %2$s / år',
        'btn_trial': 'Prøv %1$d dage gratis'
    },
    'no': {
        'badge': '%1$d DAGER GRATIS',
        'price_trial': '%1$d dager gratis, deretter %2$s / måned (i stedet for %3$s)',
        'price_trial_intro': '%1$d dager gratis, deretter %2$s / måned i år 1 (i stedet for %3$s)',
        'price_trial_simple': '%1$d dager gratis, deretter %2$s / år',
        'btn_trial': 'Prøv %1$d dager gratis'
    },
    'nb': {
        'badge': '%1$d DAGER GRATIS',
        'price_trial': '%1$d dager gratis, deretter %2$s / måned (i stedet for %3$s)',
        'price_trial_intro': '%1$d dager gratis, deretter %2$s / måned i år 1 (i stedet for %3$s)',
        'price_trial_simple': '%1$d dager gratis, deretter %2$s / år',
        'btn_trial': 'Prøv %1$d dager gratis'
    },
    'fi': {
        'badge': '%1$d PÄIVÄÄ ILMAISEKSI',
        'price_trial': '%1$d päivää ilmaiseksi, sitten %2$s / kk (norm. %3$s)',
        'price_trial_intro': '%1$d päivää ilmaiseksi, sitten %2$s / kk 1. vuonna (norm. %3$s)',
        'price_trial_simple': '%1$d päivää ilmaiseksi, sitten %2$s / vuosi',
        'btn_trial': 'Kokeile %1$d päivää ilmaiseksi'
    },
    'cs': {
        'badge': '%1$d DNÍ ZDARMA',
        'price_trial': '%1$d dní zdarma, poté %2$s / měsíc (místo %3$s)',
        'price_trial_intro': '%1$d dní zdarma, poté %2$s / měsíc v 1. roce (místo %3$s)',
        'price_trial_simple': '%1$d dní zdarma, poté %2$s / rok',
        'btn_trial': 'Vyzkoušet na %1$d dní zdarma'
    },
    'ro': {
        'badge': '%1$d ZILE GRATIS',
        'price_trial': '%1$d zile gratuit, apoi %2$s / lună (în loc de %3$s)',
        'price_trial_intro': '%1$d zile gratuit, apoi %2$s / lună în primul an (în loc de %3$s)',
        'price_trial_simple': '%1$d zile gratuit, apoi %2$s / an',
        'btn_trial': 'Încearcă gratuit %1$d zile'
    },
    'hu': {
        'badge': '%1$d NAP INGYEN',
        'price_trial': '%1$d nap ingyen, utána %2$s / hó (%3$s helyett)',
        'price_trial_intro': '%1$d nap ingyen, utána %2$s / hó az 1. évben (%3$s helyett)',
        'price_trial_simple': '%1$d nap ingyen, utána %2$s / év',
        'btn_trial': 'Próbáld ki %1$d napig ingyen'
    },
    'el': {
        'badge': '%1$d ΗΜΕΡΕΣ ΔΩΡΕΑΝ',
        'price_trial': '%1$d ημέρες δωρεάν, μετά %2$s / μήνα (αντί για %3$s)',
        'price_trial_intro': '%1$d ημέρες δωρεάν, μετά %2$s / μήνα για τον 1ο χρόνο (αντί για %3$s)',
        'price_trial_simple': '%1$d ημέρες δωρεάν, μετά %2$s / έτος',
        'btn_trial': 'Δοκιμάστε δωρεάν για %1$d ημέρες'
    },
    'ja': {
        'badge': '%1$d日間無料',
        'price_trial': '%1$d日間無料、その後 %2$s / 月（通常 %3$s）',
        'price_trial_intro': '%1$d日間無料、その後初年度 %2$s / 月（通常 %3$s）',
        'price_trial_simple': '%1$d日間無料、その後 %2$s / 年',
        'btn_trial': '%1$d日間無料で試す'
    },
    'ko': {
        'badge': '%1$d일 무료 체험',
        'price_trial': '%1$d일 무료 체험 후 %2$s / 월 (정가 %3$s)',
        'price_trial_intro': '%1$d일 무료 체험 후 1년차 %2$s / 월 (정가 %3$s)',
        'price_trial_simple': '%1$d일 무료 체험 후 %2$s / 년',
        'btn_trial': '%1$d일 무료 체험 시작'
    },
    'zh-rCN': {
        'badge': '免费试用 %1$d 天',
        'price_trial': '免费试用 %1$d 天，之后 %2$s / 月（原价 %3$s）',
        'price_trial_intro': '免费试用 %1$d 天，首年 %2$s / 月（原价 %3$s）',
        'price_trial_simple': '免费试用 %1$d 天，之后 %2$s / 年',
        'btn_trial': '免费试用 %1$d 天'
    },
    'zh-rTW': {
        'badge': '免費試用 %1$d 天',
        'price_trial': '免費試用 %1$d 天，之後 %2$s / 月（原價 %3$s）',
        'price_trial_intro': '免費試用 %1$d 天，首年 %2$s / 月（原價 %3$s）',
        'price_trial_simple': '免費試用 %1$d 天，之後 %2$s / 年',
        'btn_trial': '免費試用 %1$d 天'
    },
    'vi': {
        'badge': '%1$d NGÀY MIỄN PHÍ',
        'price_trial': '%1$d ngày miễn phí, sau đó %2$s / tháng (thay vì %3$s)',
        'price_trial_intro': '%1$d ngày miễn phí, sau đó %2$s / tháng trong năm đầu (thay vì %3$s)',
        'price_trial_simple': '%1$d ngày miễn phí, sau đó %2$s / năm',
        'btn_trial': 'Dùng thử %1$d ngày miễn phí'
    },
    'th': {
        'badge': 'ทดลองฟรี %1$d วัน',
        'price_trial': 'ทดลองฟรี %1$d วัน หลังจากนั้น %2$s / เดือน (จากปกติ %3$s)',
        'price_trial_intro': 'ทดลองฟรี %1$d วัน หลังจากนั้น %2$s / เดือน ในปีแรก (จากปกติ %3$s)',
        'price_trial_simple': 'ทดลองฟรี %1$d วัน หลังจากนั้น %2$s / ปี',
        'btn_trial': 'ทดลองใช้ฟรี %1$d วัน'
    },
    'id': {
        'badge': '%1$d HARI GRATIS',
        'price_trial': 'Gratis %1$d hari, lalu %2$s / bulan (bukan %3$s)',
        'price_trial_intro': 'Gratis %1$d hari, lalu %2$s / bulan di tahun ke-1 (bukan %3$s)',
        'price_trial_simple': 'Gratis %1$d hari, lalu %2$s / tahun',
        'btn_trial': 'Coba gratis %1$d hari'
    },
    'in': {
        'badge': '%1$d HARI GRATIS',
        'price_trial': 'Gratis %1$d hari, lalu %2$s / bulan (bukan %3$s)',
        'price_trial_intro': 'Gratis %1$d hari, lalu %2$s / bulan di tahun ke-1 (bukan %3$s)',
        'price_trial_simple': 'Gratis %1$d hari, lalu %2$s / tahun',
        'btn_trial': 'Coba gratis %1$d hari'
    },
    'hi': {
        'badge': '%1$d दिन निःशुल्क',
        'price_trial': '%1$d दिन निःशुल्क, फिर %2$s / माह (%3$s के बजाय)',
        'price_trial_intro': '%1$d दिन निःशुल्क, फिर पहले वर्ष %2$s / माह (%3$s के बजाय)',
        'price_trial_simple': '%1$d दिन निःशुल्क, फिर %2$s / वर्ष',
        'btn_trial': '%1$d दिन का निःशुल्क परीक्षण'
    },
    'bn': {
        'badge': '%1$d দিন বিনামূল্যে',
        'price_trial': '%1$d দিন বিনামূল্যে, তারপর %2$s / মাস (%3$s-এর পরিবর্তে)',
        'price_trial_intro': '%1$d দিন বিনামূল্যে, তারপর ১ম বছরে %2$s / মাস (%3$s-এর পরিবর্তে)',
        'price_trial_simple': '%1$d দিন বিনামূল্যে, তারপর %2$s / বছর',
        'btn_trial': '%1$d দিন বিনামূল্যে ট্রায়াল করুন'
    },
    'mr': {
        'badge': '%1$d दिवस विनामूल्य',
        'price_trial': '%1$d दिवस विनामूल्य, नंतर %2$s / महिना (%3$s ऐवजी)',
        'price_trial_intro': '%1$d दिवस विनामूल्य, नंतर पहिल्या वर्षी %2$s / महिना (%3$s ऐवजी)',
        'price_trial_simple': '%1$d दिवस विनामूल्य, नंतर %2$s / वर्ष',
        'btn_trial': '%1$d दिवस विनामूल्य वापरा'
    }
}

def get_lang_code(folder_name):
    if folder_name == "values":
        return "en"
    return folder_name.replace("values-", "")

def update_strings_file(file_path, t):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Check if pro_trial_badge already exists
    if "pro_trial_badge" in content:
        content = re.sub(
            r'<string name="pro_trial_badge">.*?</string>',
            f'<string name="pro_trial_badge">{t["badge"]}</string>',
            content
        )
        content = re.sub(
            r'<string name="pro_price_annual_trial">.*?</string>',
            f'<string name="pro_price_annual_trial">{t["price_trial"]}</string>',
            content
        )
        content = re.sub(
            r'<string name="pro_price_annual_trial_intro">.*?</string>',
            f'<string name="pro_price_annual_trial_intro">{t["price_trial_intro"]}</string>',
            content
        )
        content = re.sub(
            r'<string name="pro_price_annual_trial_simple">.*?</string>',
            f'<string name="pro_price_annual_trial_simple">{t["price_trial_simple"]}</string>',
            content
        )
        content = re.sub(
            r'<string name="pro_btn_annual_trial">.*?</string>',
            f'<string name="pro_btn_annual_trial">{t["btn_trial"]}</string>',
            content
        )
    else:
        # Append before </resources>
        block = (
            f"    <!-- Free Trial Offer -->\n"
            f'    <string name="pro_trial_badge">{t["badge"]}</string>\n'
            f'    <string name="pro_price_annual_trial">{t["price_trial"]}</string>\n'
            f'    <string name="pro_price_annual_trial_intro">{t["price_trial_intro"]}</string>\n'
            f'    <string name="pro_price_annual_trial_simple">{t["price_trial_simple"]}</string>\n'
            f'    <string name="pro_btn_annual_trial">{t["btn_trial"]}</string>\n'
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
