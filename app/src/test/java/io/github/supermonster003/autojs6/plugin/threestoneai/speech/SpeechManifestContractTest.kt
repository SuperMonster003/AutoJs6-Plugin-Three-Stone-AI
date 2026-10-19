package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

/** Source-manifest checks for the speech entry points (roadmap D6, D16, D22, 10.6 (c)). */
class SpeechManifestContractTest {
    private val manifest: Element by lazy {
        val path = projectRoot().resolve("app/src/main/AndroidManifest.xml")
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(path.toFile())
            .documentElement
    }

    @Test
    fun `queries name only the host package and two speech actions`() {
        val queries = manifest.children("queries").single()
        val packages = queries.children("package").map { it.android("name") }
        val intents = queries.children("intent")

        assertEquals(listOf("org.autojs.autojs6"), packages)
        assertEquals(
            listOf("android.speech.action.RECOGNIZE_SPEECH", "android.intent.action.TTS_SERVICE"),
            intents.map { intent -> intent.children("action").single().android("name") },
        )
        // Action only, like 3-Stove Agent D59: no category, data or package narrowing.
        intents.forEach { intent ->
            assertEquals(listOf("action"), intent.childElements().map(Element::getLocalName))
        }
    }

    @Test
    fun `speech adds no permission and never records audio`() {
        val permissions = manifest.children("uses-permission").map { it.android("name") }

        assertEquals(
            listOf(
                "org.autojs.permission.PLUGIN",
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
            ),
            permissions,
        )
    }

    private fun Element.childElements(): List<Element> = buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) (nodes.item(index) as? Element)?.let(::add)
    }

    private fun Element.children(name: String): List<Element> =
        childElements().filter { element -> element.localName == name }

    private fun Element.android(name: String): String = getAttributeNS(ANDROID, name)

    private fun projectRoot(): Path = generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
        .first { path -> Files.isDirectory(path.resolve("app/src/main")) }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
