package io.github.supermonster003.autojs6.plugin.ondeviceai.provider

import android.os.ParcelFileDescriptor
import java.io.Closeable

internal class OwnedParcelFileDescriptors private constructor(
    private val descriptors: Array<ParcelFileDescriptor?>,
) : Closeable {
    private val ledger = DescriptorOwnershipLedger(descriptors.size)

    val count: Int
        get() = ledger.count

    fun readDeclaredBytes(index: Int, declaredLengthBytes: Long): ByteArray {
        ledger.take(index)
        var descriptor: ParcelFileDescriptor? = null
        return try {
            val activeDescriptor = synchronized(descriptors) {
                descriptors[index] ?: error("Descriptor owner was closed")
            }
            descriptor = activeDescriptor
            ParcelFileDescriptor.AutoCloseInputStream(activeDescriptor).use { input ->
                DeclaredPayloadReader.readExactly(input, declaredLengthBytes) { activeDescriptor.checkError() }
            }
        } finally {
            ledger.release(index)
            synchronized(descriptors) {
                if (descriptors[index] === descriptor) descriptors[index] = null
            }
            closeQuietly(descriptor)
        }
    }

    override fun close() {
        ledger.closeOwned().forEach { index ->
            val descriptor = synchronized(descriptors) {
                descriptors[index].also { descriptors[index] = null }
            }
            closeQuietly(descriptor)
        }
    }

    companion object {
        fun duplicateBeforeAsync(incoming: Array<out ParcelFileDescriptor>): OwnedParcelFileDescriptors {
            val copies = arrayOfNulls<ParcelFileDescriptor>(incoming.size)
            try {
                incoming.forEachIndexed { index, descriptor ->
                    copies[index] = ParcelFileDescriptor.dup(descriptor.fileDescriptor)
                }
                return OwnedParcelFileDescriptors(copies)
            } catch (error: Throwable) {
                copies.forEach(::closeQuietly)
                throw error
            } finally {
                incoming.forEach(::closeQuietly)
            }
        }

        fun closeIncoming(incoming: Array<out ParcelFileDescriptor>?) {
            incoming?.forEach(::closeQuietly)
        }

        private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
            runCatching { descriptor?.close() }
        }
    }
}
