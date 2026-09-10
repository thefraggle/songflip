package de.goork.songflip

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class LocalizationTest {

    @Test
    fun testAllLocalizedFormatStringsAreValid() {
        val resDir = File("src/main/res")
        if (!resDir.exists()) return

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()

        resDir.listFiles()?.filter { it.name.startsWith("values") && it.isDirectory }?.forEach { dir ->
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
        val resDir = File("src/main/res")
        if (!resDir.exists()) return

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()

        val requiredStringKeys = listOf(
            "settings_tip_multiple_apps_title",
            "settings_tip_multiple_apps_body",
            "pro_debug_reset_status",
            "pro_debug_reset_button"
        )
        val requiredPluralKeys = listOf(
            "history_cached_count",
            "pro_nudge_title",
            "history_capacity_free",
            "history_capacity_pro"
        )

        resDir.listFiles()?.filter { it.name.startsWith("values") && it.isDirectory }?.forEach { dir ->
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
}
