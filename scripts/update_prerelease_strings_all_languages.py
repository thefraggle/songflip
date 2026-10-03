#!/usr/bin/env python3
"""
Updates Pre-Release notice strings across all 31 Android localized values directories.
"""

import os
import re

RES_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res")

TRANSLATIONS = {
    'en': {
        'title': 'Unreleased Album',
        'desc': 'This album is not yet released on streaming platforms.',
        'presave': 'Pre-save on Spotify',
        'search': 'Search singles in %1$s',
        'cancel': 'Cancel'
    },
    'de': {
        'title': 'Noch nicht erschienen',
        'desc': 'Dieses Album ist noch nicht auf den Streaming-Plattformen veröffentlicht.',
        'presave': 'In Spotify vorab speichern',
        'search': 'Vorab-Singles in %1$s suchen',
        'cancel': 'Abbrechen'
    },
    'es': {
        'title': 'Álbum no publicado',
        'desc': 'Este álbum aún no está disponible en las plataformas de streaming.',
        'presave': 'Guardar por adelantado en Spotify',
        'search': 'Buscar singles en %1$s',
        'cancel': 'Cancelar'
    },
    'fr': {
        'title': 'Album pas encore sorti',
        'desc': r"Cet album n\'est pas encore disponible sur les plateformes de streaming.",
        'presave': 'Pré-enregistrer sur Spotify',
        'search': 'Chercher les singles sur %1$s',
        'cancel': 'Annuler'
    },
    'it': {
        'title': 'Album non ancora pubblicato',
        'desc': r"Questo album non è ancora disponibile sulle piattaforme di streaming.",
        'presave': 'Salva in anteprima su Spotify',
        'search': 'Cerca singoli su %1$s',
        'cancel': 'Annulla'
    },
    'pt': {
        'title': 'Álbum ainda não lançado',
        'desc': 'Este álbum ainda não está disponível nas plataformas de streaming.',
        'presave': 'Pré-salvar no Spotify',
        'search': 'Buscar singles no %1$s',
        'cancel': 'Cancelar'
    },
    'nl': {
        'title': 'Nog niet verschenen',
        'desc': 'Dit album is nog niet uitgebracht op streamingplatforms.',
        'presave': 'Vooraf opslaan op Spotify',
        'search': 'Singles zoeken in %1$s',
        'cancel': 'Annuleren'
    },
    'pl': {
        'title': 'Album jeszcze niewydany',
        'desc': 'Ten album nie jest jeszcze dostępny na platformach streamingowych.',
        'presave': 'Zapisz wcześniej na Spotify',
        'search': 'Szukaj singli w %1$s',
        'cancel': 'Anuluj'
    },
    'da': {
        'title': 'Endnu ikke udgivet',
        'desc': 'Dette album er endnu ikke udgivet på streamingtjenesterne.',
        'presave': 'Gem på forhånd på Spotify',
        'search': 'Søg efter singler i %1$s',
        'cancel': 'Annuller'
    },
    'nb': {
        'title': 'Ikke utgitt ennå',
        'desc': 'Dette albumet er ennå ikke tilgjengelig på strømmetjenestene.',
        'presave': 'Forhåndslagre på Spotify',
        'search': 'Søk etter singler i %1$s',
        'cancel': 'Avbryt'
    },
    'no': {
        'title': 'Ikke utgitt ennå',
        'desc': 'Dette albumet er ennå ikke tilgjengelig på strømmetjenestene.',
        'presave': 'Forhåndslagre på Spotify',
        'search': 'Søk etter singler i %1$s',
        'cancel': 'Avbryt'
    },
    'sv': {
        'title': 'Inte släppt än',
        'desc': 'Detta album har inte släppts på streamingtjänster än.',
        'presave': 'Förhandsspara på Spotify',
        'search': 'Sök efter singlar i %1$s',
        'cancel': 'Avbryt'
    },
    'fi': {
        'title': 'Ei vielä julkaistu',
        'desc': 'Tätä albumia ei ole vielä julkaistu suoratoistopalveluissa.',
        'presave': 'Ennakkotallenna Spotifyssa',
        'search': 'Etsi singlejä palvelussa %1$s',
        'cancel': 'Peruuta'
    },
    'cs': {
        'title': 'Dosud nevydané album',
        'desc': 'Toto album zatím nebylo vydáno na streamovacích platformách.',
        'presave': 'Uložit předem na Spotify',
        'search': 'Hledat singly v %1$s',
        'cancel': 'Zrušit'
    },
    'hu': {
        'title': 'Még nem jelent meg',
        'desc': 'Ez az album még nem jelent meg a streaming felületeken.',
        'presave': 'Előzetes mentés a Spotify-on',
        'search': 'Kislemez keresése itt: %1$s',
        'cancel': 'Mégse'
    },
    'ro': {
        'title': 'Album nelansat încă',
        'desc': 'Acest album nu este încă disponibil pe platformele de streaming.',
        'presave': 'Pre-salvează pe Spotify',
        'search': 'Caută single-uri pe %1$s',
        'cancel': 'Anulează'
    },
    'el': {
        'title': 'Άλμπουμ που δεν έχει κυκλοφορήσει',
        'desc': 'Αυτό το άλμπουμ δεν έχει κυκλοφορήσει ακόμη στις πλατφόρμες streaming.',
        'presave': 'Προ-αποθήκευση στο Spotify',
        'search': 'Αναζήτηση singles στο %1$s',
        'cancel': 'Ακύρωση'
    },
    'tr': {
        'title': 'Henüz Yayınlanmadı',
        'desc': 'Bu albüm henüz yayın akışı platformlarında yayınlanmadı.',
        'presave': r"Spotify\'da önceden kaydet",
        'search': '%1$s içinde teklileri ara',
        'cancel': 'İptal'
    },
    'ru': {
        'title': 'Ещё не вышел',
        'desc': 'Этот альбом ещё не доступен на стриминговых платформах.',
        'presave': 'Добавить предзаказ в Spotify',
        'search': 'Искать синглы в %1$s',
        'cancel': 'Отмена'
    },
    'uk': {
        'title': 'Ще не вийшов',
        'desc': 'Цей альбом ще не опублікований на стримінгових платформах.',
        'presave': 'Зберегти заздалегідь у Spotify',
        'search': 'Шукати сингли в %1$s',
        'cancel': 'Скасувати'
    },
    'ja': {
        'title': '未リリースのアルバム',
        'desc': 'このアルバムはまだストリーミング配信されていません。',
        'presave': 'Spotifyで事前保存',
        'search': '%1$sでシングルを検索',
        'cancel': 'キャンセル'
    },
    'ko': {
        'title': '아직 발매되지 않음',
        'desc': '이 앨범은 아직 스트리밍 서비스에 발매되지 않았습니다.',
        'presave': 'Spotify에서 사전 저장',
        'search': '%1$s에서 싱글 검색',
        'cancel': '취소'
    },
    'zh-rCN': {
        'title': '尚未发行',
        'desc': '该专辑尚未在流媒体平台上架。',
        'presave': '在 Spotify 上预存',
        'search': '在 %1$s 中搜索单曲',
        'cancel': '取消'
    },
    'zh-rTW': {
        'title': '尚未發行',
        'desc': '該專輯尚未在串流平台上架。',
        'presave': '在 Spotify 上預存',
        'search': '在 %1$s 中搜尋單曲',
        'cancel': '取消'
    },
    'id': {
        'title': 'Belum Dirilis',
        'desc': 'Album ini belum dirilis di platform streaming.',
        'presave': 'Pra-simpan di Spotify',
        'search': 'Cari single di %1$s',
        'cancel': 'Batal'
    },
    'in': {
        'title': 'Belum Dirilis',
        'desc': 'Album ini belum dirilis di platform streaming.',
        'presave': 'Pra-simpan di Spotify',
        'search': 'Cari single di %1$s',
        'cancel': 'Batal'
    },
    'vi': {
        'title': 'Chưa phát hành',
        'desc': 'Album này chưa được phát hành trên các nền tảng phát trực tuyến.',
        'presave': 'Lưu trước trên Spotify',
        'search': 'Tìm đĩa đơn trên %1$s',
        'cancel': 'Hủy'
    },
    'th': {
        'title': 'ยังไม่วางจำหน่าย',
        'desc': 'อัลบั้มนี้ยังไม่เปิดให้ฟังบนแพลตฟอร์มสตรีมมิง',
        'presave': 'บันทึกล่วงหน้าบน Spotify',
        'search': 'ค้นหาซิงเกิลใน %1$s',
        'cancel': 'ยกเลิก'
    },
    'hi': {
        'title': 'अभी रिलीज़ नहीं हुआ',
        'desc': 'यह एल्बम अभी स्ट्रीमिंग प्लेटफ़ॉर्म पर उपलब्ध नहीं है।',
        'presave': 'Spotify पर प्री-सेव करें',
        'search': '%1$s में सिंगल्स खोजें',
        'cancel': 'रद्द करें'
    },
    'bn': {
        'title': 'এখনও মুক্তি পায়নি',
        'desc': 'এই অ্যালবামটি এখনও স্ট্রিমিং প্ল্যাটফর্মে প্রকাশিত হয়নি।',
        'presave': 'Spotify-এ প্রি-সেভ করুন',
        'search': '%1$s-এ একক গান খুঁজুন',
        'cancel': 'বাতিল'
    },
    'mr': {
        'title': 'अद्याप रिलीज झाले नाही',
        'desc': 'हा अल्बम अद्याप स्ट्रीमिंग प्लॅटफॉर्मवर उपलब्ध नाही.',
        'presave': 'Spotify वर प्री-सेव्ह करा',
        'search': '%1$s मध्ये सिंगल्स शोधा',
        'cancel': 'रद्द करा'
    },
}

def get_lang_code(folder_name):
    if folder_name == "values":
        return "en"
    return folder_name.replace("values-", "")

def update_strings_file(file_path, t):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Check if prerelease_sheet_title already exists
    if "prerelease_sheet_title" in content:
        # Update existing
        content = re.sub(
            r'<string name="prerelease_sheet_title">.*?</string>',
            f'<string name="prerelease_sheet_title">{t["title"]}</string>',
            content
        )
        content = re.sub(
            r'<string name="prerelease_sheet_desc">.*?</string>',
            f'<string name="prerelease_sheet_desc">{t["desc"]}</string>',
            content
        )
        content = re.sub(
            r'<string name="prerelease_btn_presave_spotify">.*?</string>',
            f'<string name="prerelease_btn_presave_spotify">{t["presave"]}</string>',
            content
        )
        content = re.sub(
            r'<string name="prerelease_btn_search_target">.*?</string>',
            f'<string name="prerelease_btn_search_target">{t["search"]}</string>',
            content
        )
        content = re.sub(
            r'<string name="prerelease_btn_cancel">.*?</string>',
            f'<string name="prerelease_btn_cancel">{t["cancel"]}</string>',
            content
        )
    else:
        # Append before </resources>
        block = (
            f"    <!-- Pre-Release Notice -->\n"
            f'    <string name="prerelease_sheet_title">{t["title"]}</string>\n'
            f'    <string name="prerelease_sheet_desc">{t["desc"]}</string>\n'
            f'    <string name="prerelease_btn_presave_spotify">{t["presave"]}</string>\n'
            f'    <string name="prerelease_btn_search_target">{t["search"]}</string>\n'
            f'    <string name="prerelease_btn_cancel">{t["cancel"]}</string>\n'
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
