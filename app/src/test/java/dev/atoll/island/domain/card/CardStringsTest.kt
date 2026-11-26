package dev.atoll.island.domain.card

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
        val plurals = doc.getElementsByTagName("plurals")
        val map = linkedMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            map[node.attributes.getNamedItem("name").nodeValue] = node.textContent
        }
        for (i in 0 until plurals.length) {
            val plural = plurals.item(i)
            val base = plural.attributes.getNamedItem("name").nodeValue
            val children = plural.childNodes
            for (j in 0 until children.length) {
                val item = children.item(j)
                if (item.nodeName == "item") {
                    val qty = item.attributes.getNamedItem("quantity").nodeValue
                    map["$base[$qty]"] = item.textContent
                }
            }
        }
        return map
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
        assertTrue("missing in values-it: ${en - it}", it.containsAll(en))
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
