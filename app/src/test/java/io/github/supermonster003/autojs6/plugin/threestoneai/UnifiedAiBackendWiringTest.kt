package io.github.supermonster003.autojs6.plugin.threestoneai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UnifiedAiBackendWiringTest {
    @Test
    fun launcherChatAndBinderProviderUseOnlyTheApplicationAiBackendBoundary() {
        val application = source("ThreeStoneAiApplication.kt")
        val chat = source("ChatActivity.kt")
        val service = source("provider/ThreeStoneAiProviderService.kt")
        val binderSession = source("provider/RemoteThreeStoneAiSession.kt")

        assertTrue(application.contains("CompositeAiBackend("))
        assertTrue(application.contains("internal val aiBackend: AiBackend"))
        assertTrue(chat.contains(".aiBackend.createSession("))
        assertTrue(service.contains("aiBackend = pluginApplication.aiBackend"))
        assertTrue(service.contains("aiBackend = aiBackend"))
        assertTrue(binderSession.contains("aiBackend.createSession("))
        assertTrue(chat.contains("cancelAndClose"))
        assertTrue(binderSession.contains("cancelAndClose"))

        listOf(chat, service, binderSession).forEach { entryPoint ->
            DIRECT_IMPLEMENTATION_TYPES.forEach { implementation ->
                assertFalse(
                    "AI entry points must not depend on $implementation directly",
                    entryPoint.contains(implementation),
                )
            }
        }
    }

    private fun source(relativePath: String): String =
        File(sourceRoot(), relativePath).readText()

    private fun sourceRoot(): File = listOf(
        File("src/main/java/io/github/supermonster003/autojs6/plugin/threestoneai"),
        File("app/src/main/java/io/github/supermonster003/autojs6/plugin/threestoneai"),
    ).firstOrNull(File::isDirectory)
        ?: error("Unable to locate the plugin source directory")

    private companion object {
        val DIRECT_IMPLEMENTATION_TYPES = listOf(
            "LiteRtLocalBackend",
            "LiteRtLocalSession",
            "OnlineAiBackend",
            "OnlineAiSession",
        )
    }
}
