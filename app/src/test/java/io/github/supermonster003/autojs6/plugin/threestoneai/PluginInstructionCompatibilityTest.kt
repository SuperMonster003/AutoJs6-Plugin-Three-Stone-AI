package io.github.supermonster003.autojs6.plugin.threestoneai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PluginInstructionCompatibilityTest {

    @Test
    fun rawBinderExamplesUseTheProtocol20GenerationOptionsSignature() {
        val resourceRoot = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
        ).firstOrNull(File::isDirectory)
            ?: error("Unable to locate the Android resource directory")

        val instructionFiles = resourceRoot.listFiles()
            .orEmpty()
            .filter { directory ->
                directory.isDirectory &&
                    (directory.name == "raw" || directory.name.startsWith("raw-"))
            }
            .map { directory -> File(directory, "plugin_instruction.md") }
            .filter(File::isFile)
            .sortedBy(File::getPath)

        assertEquals("Unexpected plugin-instruction resource count", 11, instructionFiles.size)

        instructionFiles.forEach { instructionFile ->
            val source = instructionFile.readText()
            val label = instructionFile.relativeTo(resourceRoot).path

            assertEquals(
                "$label must contain exactly one raw AiGenerationOptions example",
                1,
                GENERATION_OPTIONS_MARKER.toRegex(RegexOption.LITERAL).findAll(source).count(),
            )
            assertEquals(
                "$label must pass every argument required by the current API",
                GENERATION_OPTIONS_PARAMETER_COUNT,
                countConstructorArguments(source, GENERATION_OPTIONS_MARKER),
            )
            assertTrue(
                "$label must use protocol 2.0",
                source.contains("new CommonApi.AiProtocolVersion(2, 0)"),
            )
            assertTrue(
                "$label must document the persistent-session argument",
                source.contains("false,  // persistent session"),
            )
            assertTrue(
                "$label must document the backend-profile argument",
                source.contains("\"cpu\"   // explicit backend profile"),
            )
            assertTrue(
                "$label runnable examples must not impose a token limit by default",
                source.lineSequence().none { line ->
                    line.trimStart().startsWith("maxTokens:")
                },
            )
            assertTrue(
                "$label must retain maxTokens as a documented opt-in option",
                source.contains("// maxTokens: 1024,"),
            )
            assertFalse(
                "$label raw example must not retain the old 256-token limit",
                source.contains("java.lang.Long.valueOf(\"256\")"),
            )
            assertTrue(
                "$label raw example must use the provider's full output-byte allowance",
                source.contains("65536,  // 插件输出安全上限 64 KiB"),
            )
            assertTrue(
                "$label raw example must delegate its token limit to the model or engine",
                source.contains("null,   // 不额外限制 token, 使用模型/引擎默认值"),
            )
        }
    }

    private fun countConstructorArguments(source: String, marker: String): Int {
        val argumentsStart = source.indexOf(marker)
            .takeIf { it >= 0 }
            ?.plus(marker.length)
            ?: error("Constructor marker not found")
        var parenthesesDepth = 0
        var bracketsDepth = 0
        var bracesDepth = 0
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        var argumentCount = 1
        var index = argumentsStart

        while (index < source.length) {
            val character = source[index]
            val next = source.getOrNull(index + 1)

            if (lineComment) {
                if (character == '\n') lineComment = false
                index += 1
                continue
            }
            if (blockComment) {
                if (character == '*' && next == '/') {
                    blockComment = false
                    index += 2
                } else {
                    index += 1
                }
                continue
            }
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == quote -> quote = null
                }
                index += 1
                continue
            }

            when {
                character == '/' && next == '/' -> {
                    lineComment = true
                    index += 2
                    continue
                }
                character == '/' && next == '*' -> {
                    blockComment = true
                    index += 2
                    continue
                }
                character == '\'' || character == '"' -> quote = character
                character == '(' -> parenthesesDepth += 1
                character == ')' && parenthesesDepth > 0 -> parenthesesDepth -= 1
                character == ')' -> return argumentCount
                character == '[' -> bracketsDepth += 1
                character == ']' -> bracketsDepth -= 1
                character == '{' -> bracesDepth += 1
                character == '}' -> bracesDepth -= 1
                character == ',' &&
                    parenthesesDepth == 0 &&
                    bracketsDepth == 0 &&
                    bracesDepth == 0 -> argumentCount += 1
            }
            index += 1
        }

        error("AiGenerationOptions constructor call is not closed")
    }

    private companion object {
        const val GENERATION_OPTIONS_MARKER = "new TextApi.AiGenerationOptions("
        const val GENERATION_OPTIONS_PARAMETER_COUNT = 16
    }
}
