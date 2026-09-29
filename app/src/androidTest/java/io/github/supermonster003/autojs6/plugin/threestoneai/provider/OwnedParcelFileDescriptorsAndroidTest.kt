package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * A host hands over image descriptors that are regular files inside its own private cache. The
 * Provider process may hold such a descriptor yet be refused when it re-opens the file by path, so
 * the read must fall back to a duplicate of the descriptor instead of failing the tool turn.
 */
class OwnedParcelFileDescriptorsAndroidTest {
    private val bytes = Random(7).nextBytes(70_000)

    @Test
    fun aRegularFileThatCannotBeReopenedByPathIsReadThroughADuplicate() {
        // Mode 000 makes the /proc/self/fd re-open fail with EACCES for this uid as well, while the
        // descriptor that is already open keeps its read permission, exactly like a file in another
        // app's private directory.
        val descriptor = file(bytes) { Os.fchmod(it.fileDescriptor, 0) }
        val owned = OwnedParcelFileDescriptors.duplicateBeforeAsync(arrayOf(descriptor))
        try {
            assertArrayEquals(bytes, owned.readDeclaredBytes(0, bytes.size.toLong()))
        } finally { owned.close() }
    }

    @Test
    fun aRegularFileThatCanBeReopenedStillHonoursItsOffsetAndDeclaredLength() {
        val descriptor = file(bytes) { Os.lseek(it.fileDescriptor, 1_000, android.system.OsConstants.SEEK_SET) }
        val owned = OwnedParcelFileDescriptors.duplicateBeforeAsync(arrayOf(descriptor))
        try {
            assertArrayEquals(bytes.copyOfRange(1_000, bytes.size), owned.readDeclaredBytes(0, (bytes.size - 1_000).toLong()))
        } finally { owned.close() }
        val shorter = OwnedParcelFileDescriptors.duplicateBeforeAsync(arrayOf(file(bytes)))
        try {
            assertThrows(IllegalArgumentException::class.java) { shorter.readDeclaredBytes(0, (bytes.size - 1).toLong()) }
        } finally { shorter.close() }
    }

    @Test
    fun aPipeIsStillReadThroughItsOwnNonBlockingDescriptor() {
        val (read, write) = ParcelFileDescriptor.createPipe().let { it[0] to it[1] }
        val writer = Thread { ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(bytes) } }.apply { start() }
        val owned = OwnedParcelFileDescriptors.duplicateBeforeAsync(arrayOf(read))
        try {
            assertArrayEquals(bytes, owned.readDeclaredBytes(0, bytes.size.toLong()))
            assertEquals(1, owned.count)
        } finally { owned.close(); writer.join(5_000) }
    }

    private fun file(content: ByteArray, prepare: (ParcelFileDescriptor) -> Unit = {}): ParcelFileDescriptor {
        val file = File.createTempFile("owned-fd-", ".bin", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        try {
            file.writeBytes(content)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).also(prepare)
        } finally { file.delete() }
    }
}
