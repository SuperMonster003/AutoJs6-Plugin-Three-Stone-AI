package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import android.os.ParcelFileDescriptor
import android.os.Parcel
import android.os.Parcelable
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.Closeable
import java.io.InterruptedIOException
import java.util.concurrent.atomic.AtomicBoolean

internal class OwnedParcelFileDescriptors private constructor(
    private val descriptors: Array<ParcelFileDescriptor?>,
) : Closeable {
    private val ledger = DescriptorOwnershipLedger(descriptors.size)
    private val closed = AtomicBoolean(false)

    val count: Int
        get() = ledger.count

    fun readDeclaredBytes(index: Int, declaredLengthBytes: Long): ByteArray {
        ledger.take(index)
        var descriptor: ParcelFileDescriptor? = null
        return try {
            require(declaredLengthBytes in 0L..org.autojs.plugin.ai.common.api.AiCommonLimits.MAX_PAYLOAD_BYTES)
            // A private nonblocking fd avoids changing the sender's shared file flags. It also
            // stays owned by this worker until it exits, so cancellation cannot recycle it
            // under a poll/read operation. Preserve the sender's current regular-file offset.
            val fd = synchronized(descriptors) {
                check(!closed.get()) { "Descriptor owner was closed" }
                val active = descriptors[index] ?: error("Descriptor owner was closed")
                descriptor = active
                val offset = try { Os.lseek(active.fileDescriptor, 0, OsConstants.SEEK_CUR) }
                catch (failure: ErrnoException) { if (failure.errno == OsConstants.ESPIPE) null else throw failure }
                Os.open("/proc/self/fd/${active.fd}", OsConstants.O_RDONLY or OsConstants.O_NONBLOCK or O_CLOEXEC, 0).also { copy ->
                    try { if (offset != null) Os.lseek(copy, offset, OsConstants.SEEK_SET) }
                    catch (failure: Throwable) { Os.close(copy); throw failure }
                }
            }
            try {
                val bytes = ByteArray(declaredLengthBytes.toInt())
                val extra = ByteArray(1)
                var offset = 0
                val poll = StructPollfd().apply { this.fd = fd; events = OsConstants.POLLIN.toShort() }
                while (true) {
                    if (closed.get() || Thread.currentThread().isInterrupted) throw InterruptedIOException("Descriptor read cancelled")
                    val count = try {
                        if (Os.poll(arrayOf(poll), 100) == 0) continue
                        if (offset < bytes.size) Os.read(fd, bytes, offset, bytes.size - offset)
                        else Os.read(fd, extra, 0, 1)
                    } catch (failure: ErrnoException) {
                        if (failure.errno == OsConstants.EAGAIN || failure.errno == OsConstants.EINTR) continue
                        throw failure
                    }
                    if (count == 0) break
                    require(offset < bytes.size) { "Descriptor payload exceeds its declared length" }
                    offset += count
                }
                require(offset == bytes.size) { "Descriptor payload ended before its declared length" }
                checkNotNull(descriptor).checkError()
                bytes
            } finally { Os.close(fd) }
        } finally {
            ledger.release(index)
            synchronized(descriptors) {
                if (descriptors[index] === descriptor) descriptors[index] = null
            }
            closeQuietly(descriptor)
        }
    }

    override fun close() {
        closed.set(true)
        ledger.closeOwned().forEach { index ->
            val descriptor = synchronized(descriptors) {
                descriptors[index].also { descriptors[index] = null }
            }
            closeQuietly(descriptor)
        }
    }

    companion object {
        // Linux value; OsConstants.O_CLOEXEC is not public on every supported Android API.
        private const val O_CLOEXEC = 0x80000
        fun duplicateBeforeAsync(incoming: Array<out ParcelFileDescriptor>): OwnedParcelFileDescriptors {
            val copies = arrayOfNulls<ParcelFileDescriptor>(incoming.size)
            try {
                incoming.forEachIndexed { index, descriptor ->
                    // Preserve both reliable-pipe descriptors and transfer the old owner
                    // silently. A normal close would consume the producer's queued status.
                    val parcel = Parcel.obtain()
                    try {
                        descriptor.writeToParcel(parcel, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
                        parcel.setDataPosition(0)
                        copies[index] = ParcelFileDescriptor.CREATOR.createFromParcel(parcel)
                    } finally { parcel.recycle() }
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
