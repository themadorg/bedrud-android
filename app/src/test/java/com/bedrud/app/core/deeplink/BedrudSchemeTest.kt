package com.bedrud.app.core.deeplink

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val AndroidNamespace = "http://schemas.android.com/apk/res/android"

/** The host of the sign-in callback, `bedrud://oauth`, which `OAuthLoginHandler` matches. */
private const val OAuthCallbackHost = "oauth"

/**
 * The app's own URI scheme, which the manifest has to name too: an intent filter cannot read a
 * Kotlin constant, so the two are kept in step by hand, and a callback the manifest does not claim
 * never reaches the app at all. Read straight from the manifest the app ships.
 */
class BedrudSchemeTest {

    private val manifest = File("src/main/AndroidManifest.xml")

    /** The scheme of every `<data>` entry in the manifest whose host is [host]. */
    private fun schemesForHost(host: String): List<String> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val entries = factory.newDocumentBuilder().parse(manifest).getElementsByTagName("data")
        return (0 until entries.length)
            .map { entries.item(it) as Element }
            .filter { it.getAttributeNS(AndroidNamespace, "host") == host }
            .map { it.getAttributeNS(AndroidNamespace, "scheme") }
    }

    @Test
    fun `should be the scheme the manifest claims the sign-in callback with`() {
        assertEquals(listOf(BedrudScheme.SCHEME), schemesForHost(OAuthCallbackHost))
    }
}
