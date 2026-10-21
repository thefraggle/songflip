package de.goork.songflip

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class LocalizationTest {

    @Test
    fun testAllLocalizedFormatStringsAreValid() {
        val resDir = getResDir()
        val valuesDirs = resDir.listFiles()?.filter { it.name.startsWith("values") && it.isDirectory } ?: emptyList()
        assertTrue("Expected at least 24 values directories, found ${valuesDirs.size}", valuesDirs.size >= 24)

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()

        valuesDirs.forEach { dir ->
            val stringsFile = File(dir, "strings.xml")
            if (stringsFile.exists()) {
                val doc = dBuilder.parse(stringsFile)
                val stringNodes = doc.getElementsByTagName("string")
                for (i in 0 until stringNodes.length) {
                    val node = stringNodes.item(i)
                    val text = node.textContent
                    val formattedAttr = node.attributes?.getNamedItem("formatted")?.nodeValue
                    if (formattedAttr == "false") continue

                    validateFormat(text, "${dir.name}/strings.xml <string>")
                }

                val pluralsNodes = doc.getElementsByTagName("plurals")
                for (i in 0 until pluralsNodes.length) {
                    val pNode = pluralsNodes.item(i) as Element
                    val itemNodes = pNode.getElementsByTagName("item")
                    for (j in 0 until itemNodes.length) {
                        val node = itemNodes.item(j)
                        val text = node.textContent
                        val formattedAttr = node.attributes?.getNamedItem("formatted")?.nodeValue
                        if (formattedAttr == "false") continue

                        validateFormat(text, "${dir.name}/strings.xml <plurals name='${pNode.getAttribute("name")}'>")
                    }
                }
            }
        }
        assertTrue(true)
    }

    private fun validateFormat(text: String, location: String) {
        if (text.contains("%")) {
            try {
                val regex = Regex("%([0-9]+\\$)?([a-zA-Z])")
                val matches = regex.findAll(text).toList()
                val args = matches.map { match ->
                    when (match.groupValues[2]) {
                        "d" -> 10
                        else -> "test"
                    }
                }.toTypedArray()
                String.format(text, *args)
            } catch (e: Exception) {
                throw AssertionError("Invalid format in $location: '$text' (${e.message})", e)
            }
        }
    }

    @Test
    fun testRequiredKeysAndPluralsExistInAllLanguages() {
        val resDir = getResDir()
        val valuesDirs = resDir.listFiles()?.filter { it.name.startsWith("values") && it.isDirectory } ?: emptyList()
        assertTrue("Expected at least 24 values directories, found ${valuesDirs.size}", valuesDirs.size >= 24)

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()

        val requiredStringKeys = listOf(
            "settings_tip_multiple_apps_title",
            "settings_tip_multiple_apps_body",
            "pro_debug_reset_status",
            "pro_debug_reset_button",
            "pro_save_badge",
            "pro_price_annual_vs_monthly",
            "pro_price_annual_vs_monthly_intro",
            "pro_lifetime_onetime_badge",
            "pro_subscription_terms",
            "legal_terms",
            "legal_privacy",
            // Were English-only until 1.6.6 (lint MissingTranslation).
            "history_copy_target",
            "history_copy_source",
            "action_more_options",
            "playlist_error_unsupported_title",
            "playlist_error_unsupported_desc",
            "playlist_error_timeout_title",
            "playlist_error_timeout_desc",
            "playlist_error_empty_title",
            "playlist_error_empty_desc",
            "platform_label_unknown",
            "platform_label_universal",
            "history_copy_source_generic",
            "pro_feature_playlists_title",
            "pro_feature_playlists_desc",
            "pro_feature_history_title",
            "pro_feature_history_desc",
            "pro_feature_cache_title",
            "pro_feature_cache_desc",
            "pro_feature_links_title",
            "pro_feature_links_desc",
            "pro_feature_review_title",
            "pro_feature_review_desc",
            "search_title",
            "search_hint",
            "search_initial_prompt",
            "search_empty",
            "search_error",
            "disambiguate_action",
            "disambiguate_title",
            "disambiguate_subtitle",
            "disambiguate_current",
            "disambiguate_success",
            "disambiguate_empty",
            "disambiguate_error",
            "pro_btn_lifetime",
            "pro_btn_annual",
            "pro_trial_badge",
            "pro_price_annual_trial",
            "pro_price_annual_trial_intro",
            "pro_price_annual_trial_simple",
            "pro_btn_annual_trial",
            "pro_btn_monthly",
            "feedback_title",
            "feedback_cat_bug",
            "feedback_cat_feature",
            "feedback_cat_general",
            "feedback_message_hint",
            "feedback_email_hint",
            "feedback_privacy_note",
            "feedback_btn_send",
            "feedback_btn_mail_fallback",
            "feedback_success",
            "feedback_error_empty",
            "feedback_error_network",
            "playlist_same_platform_title",
            "playlist_same_platform_desc",
            "qr_code_title",
            "qr_code_hint",
            "action_close",
            "favorite_add",
            "favorite_remove",
            "history_filter_all",
            "history_filter_favorites",
            "target_player_chip",
            "listen_and_flip_title",
            "listen_and_flip_subtitle",
            "badge_identified",
            "listen_free_scans_remaining",
            "listen_status_listening",
            "listen_status_hint",
            "listen_status_resolving",
            "listen_saved_to_history",
            "listen_open_in_player",
            "listen_share_action",
            "listen_scan_again",
            "listen_offline_saved_title",
            "listen_offline_saved_desc",
            "listen_offline_resolved_toast",
            "listen_no_match_title",
            "listen_no_match_subtitle",
            "listen_try_again",
            "listen_error_offline",
            "listen_permission_title",
            "listen_permission_desc",
            "listen_permission_grant",
            "listen_quota_reached_title",
            "listen_quota_reached_desc",
            "listen_unlock_pro",
            "pro_feature_listen_title",
            "pro_feature_listen_desc",
            "unsupported_domain_toast",
            // Replaced the hardcoded English "x/y Songs" history subtitle (#142).
            "history_playlist_tracks_summary"
        )
        val requiredPluralKeys = listOf(
            "history_cached_count",
            "pro_nudge_title",
            "history_capacity_free",
            "history_capacity_pro"
        )

        valuesDirs.forEach { dir ->
            val stringsFile = File(dir, "strings.xml")
            if (stringsFile.exists()) {
                val doc = dBuilder.parse(stringsFile)
                val stringNodes = doc.getElementsByTagName("string")
                val stringKeys = mutableSetOf<String>()
                for (i in 0 until stringNodes.length) {
                    val elem = stringNodes.item(i) as Element
                    stringKeys.add(elem.getAttribute("name"))
                }

                val pluralsNodes = doc.getElementsByTagName("plurals")
                val pluralKeys = mutableSetOf<String>()
                for (i in 0 until pluralsNodes.length) {
                    val elem = pluralsNodes.item(i) as Element
                    pluralKeys.add(elem.getAttribute("name"))
                }

                for (key in requiredStringKeys) {
                    assertTrue("Missing string key '$key' in ${dir.name}/strings.xml", stringKeys.contains(key))
                }
                for (key in requiredPluralKeys) {
                    assertTrue("Missing plural key '$key' in ${dir.name}/strings.xml", pluralKeys.contains(key))
                }
            }
        }
    }

    private fun getResDir(): File {
        return listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull { it.exists() }
            ?: throw AssertionError("Could not find Android res directory from working dir: ${File(".").absolutePath}")
    }
}
