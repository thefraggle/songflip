package de.goork.songflip

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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
                            throw AssertionError("Invalid format in ${dir.name}/strings.xml: '$text' (${e.message})", e)
                        }
                    }
                }
            }
        }
        assertTrue(true)
    }
}
