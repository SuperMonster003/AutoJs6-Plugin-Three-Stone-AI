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

    @Test
    fun `the voice command entry is an exported unprotected trampoline`() {
        val activity = manifest.children("application").single().children("activity")
            .single { it.android("name") == ".voice.VoiceCommandActivity" }

        // Roadmap D38 and Macaw D50: no permission, the app's label and icon, gone after use.
        assertEquals("true", activity.android("exported"))
        assertEquals("", activity.android("permission"))
        assertEquals("@string/app_name", activity.android("label"))
        assertEquals("@mipmap/ic_icon_studio_application", activity.android("icon"))
        assertEquals("@android:style/Theme.NoDisplay", activity.android("theme"))
        assertEquals("true", activity.android("excludeFromRecents"))
        assertEquals("true", activity.android("noHistory"))
        assertEquals("", activity.getAttributeNS(ANDROID, "taskAffinity"))
        assertEquals(true, activity.hasAttributeNS(ANDROID, "taskAffinity"))
        val filter = activity.children("intent-filter").single()
        assertEquals(listOf("org.autojs.plugin.action.VOICE_COMMAND"), filter.children("action").map { it.android("name") })
        assertEquals(listOf("android.intent.category.DEFAULT"), filter.children("category").map { it.android("name") })
        assertEquals(emptyList<Element>(), filter.children("data"))
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
