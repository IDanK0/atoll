package dev.atoll.island.domain.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class CardStringsTest {

    private fun strings(folder: String): Map<String, String> {
        val file = listOf("src/main/res/$folder/strings.xml", "app/src/main/res/$folder/strings.xml")
            .map(::File).first { it.isFile }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private fun isEmoji(cp: Int) = cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF ||
        cp in 0x2B00..0x2BFF || cp == 0xFE0F || cp == 0x200D

    @Test
    fun noEmojiInAnyString() {
        listOf("values", "values-it").forEach { folder ->
            strings(folder).forEach { (name, text) ->
                val bad = text.codePoints().toArray().filter(::isEmoji)
                assertTrue("$folder/$name has emoji: ${bad.joinToString { Integer.toHexString(it) }}", bad.isEmpty())
            }
        }
    }

    @Test
    fun italianHasEveryCardString() {
        val en = strings("values").keys.filter { it.startsWith("card_") }.toSet()
        val it = strings("values-it").keys.filter { it.startsWith("card_") }.toSet()
        assertTrue(en.isNotEmpty())
        assertEquals(en, it)
    }

    @Test
    fun noBrokenEncoding() {
        listOf("values", "values-it").forEach { folder ->
            strings(folder).forEach { (name, text) ->
                assertTrue("$folder/$name", '\uFFFD' !in text && "Ã" !in text && "â€" !in text)
            }
        }
    }
}
