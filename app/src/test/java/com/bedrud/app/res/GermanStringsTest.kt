package com.bedrud.app.res

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The German strings, which address the reader informally as *du* throughout. A formal *Sie* in a
 * single string reads as a different app speaking, so a new string written in the other register
 * fails here rather than reaching a reader.
 */
class GermanStringsTest {

    private val germanStrings = File("src/main/res/values-de/strings.xml")

    /** Every translatable text in the file, keyed by its resource name; plural items share it. */
    private fun textsByName(): List<Pair<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(germanStrings)
        val strings = document.getElementsByTagName("string")
        val items = document.getElementsByTagName("item")
        val stringTexts = (0 until strings.length).map { index ->
            val element = strings.item(index) as Element
            element.getAttribute("name") to element.textContent
        }
        val pluralTexts = (0 until items.length).map { index ->
            val element = items.item(index) as Element
            val plural = element.parentNode as Element
            plural.getAttribute("name") to element.textContent
        }
        return stringTexts + pluralTexts
    }

    @Test
    fun `should address the reader as du in every German string`() {
        val formal = textsByName()
            .filter { (_, text) -> FormalAddress.containsMatchIn(text) }
            .map { (name, _) -> name }
            .distinct()

        assertEquals("Strings addressing the reader as Sie", emptyList<String>(), formal)
    }

    @Test
    fun `should find a formal address the way a reader would`() {
        assertEquals(true, FormalAddress.containsMatchIn("Geben Sie Ihre E-Mail-Adresse ein"))
        assertEquals(true, FormalAddress.containsMatchIn("Wir senden Ihnen einen Link"))
        assertEquals(false, FormalAddress.containsMatchIn("Gib deine E-Mail-Adresse ein"))
    }

    private companion object {
        /**
         * The formal pronoun in each of its forms. Matched case-sensitively: the formal forms are
         * always capitalised, while lowercase *sie* and *ihr* are "she", "they" and "her".
         */
        val FormalAddress = Regex("""\b(Sie|Ihnen|Ihr|Ihre|Ihrem|Ihren|Ihrer|Ihres)\b""")
    }
}
