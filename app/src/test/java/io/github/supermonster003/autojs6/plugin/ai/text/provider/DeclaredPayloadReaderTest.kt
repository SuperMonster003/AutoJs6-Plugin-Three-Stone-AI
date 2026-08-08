package io.github.supermonster003.autojs6.plugin.ai.text.provider

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class DeclaredPayloadReaderTest {
    @Test
    fun requiresExactDeclaredLength() {
        assertArrayEquals(
            byteArrayOf(1, 2, 3),
            DeclaredPayloadReader.readExactly(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3),
        )
        assertThrows(IllegalArgumentException::class.java) {
            DeclaredPayloadReader.readExactly(ByteArrayInputStream(byteArrayOf(1, 2)), 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeclaredPayloadReader.readExactly(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3)
        }
    }
}
