package io.github.supermonster003.autojs6.plugin.threestoneai

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationAttachment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/** Why a picked file did not become an attachment (roadmap D70). */
internal enum class ChatAttachmentImportFailure {
    UNSUPPORTED_TYPE,
    TOO_LARGE,
    UNREADABLE,
    BINARY_TEXT,
    PDF_PROTECTED,
    PDF_TOO_MANY_PAGES,
}

internal sealed class ChatAttachmentImport {
    data class Imported(val attachment: ChatAttachment) : ChatAttachmentImport()
    data class Failed(val name: String, val failure: ChatAttachmentImportFailure) : ChatAttachmentImport()
}

/**
 * Attachment files under `filesDir/attachments` (roadmap P4, D71, D136): `.pending/` holds files
 * picked for the unsent message, `<conversation id>/` the files of sent messages. Every method does
 * file I/O; call them off the main thread.
 */
internal class ChatAttachmentStore(context: Context) {
    private val appContext = context.applicationContext
    val root = File(appContext.filesDir, DIRECTORY)
    private val pendingDirectory = File(root, PENDING_DIRECTORY)

    fun pendingFile(attachment: ChatAttachment) = File(pendingDirectory, attachment.fileName)

    fun file(conversationId: String, attachment: ChatAttachment) =
        File(conversationDirectory(conversationId), attachment.fileName)

    fun conversationDirectory(conversationId: String) = File(root, directoryName(conversationId))

    /** Copies and normalizes one picked file into `.pending/` (D132). */
    fun import(uri: Uri): ChatAttachmentImport {
        val resolver = appContext.contentResolver
        val (displayName, declaredSize) = queryNameAndSize(resolver, uri)
        val name = boundedName(displayName ?: uri.lastPathSegment ?: DEFAULT_NAME)
        val mimeType = resolver.getType(uri)?.lowercase() ?: mimeFromName(name)
        val kind = when {
            mimeType != null && mimeType.startsWith("image/") -> ChatAttachmentKind.IMAGE
            mimeType == ChatAttachmentLimits.MIME_PDF -> ChatAttachmentKind.PDF
            else -> ChatAttachmentKind.TEXT
        }
        return try {
            when (kind) {
                ChatAttachmentKind.IMAGE -> importImage(resolver, uri, name)
                ChatAttachmentKind.PDF -> importPdf(resolver, uri, name, declaredSize)
                ChatAttachmentKind.TEXT -> importText(resolver, uri, name, mimeType, declaredSize)
            }
        } catch (_: TooLarge) {
            ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.TOO_LARGE)
        } catch (_: IOException) {
            ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.UNREADABLE)
        } catch (_: SecurityException) {
            ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.UNREADABLE)
        } catch (_: IllegalArgumentException) {
            ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.UNREADABLE)
        } catch (_: OutOfMemoryError) {
            ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.TOO_LARGE)
        }
    }

    /**
     * Moves pending files into the conversation directory when the message is sent; false when a
     * file is missing afterwards. A file already moved by an earlier attempt counts as present.
     */
    fun commit(conversationId: String, attachments: List<ChatAttachment>): Boolean {
        if (attachments.isEmpty()) return true
        val directory = conversationDirectory(conversationId)
        directory.mkdirs()
        attachments.forEach { attachment ->
            val source = pendingFile(attachment)
            val target = File(directory, attachment.fileName)
            if (!source.isFile) return@forEach
            if (!source.renameTo(target)) {
                runCatching { source.copyTo(target, overwrite = true) }
                    .onSuccess { source.delete() }
                    .onFailure { target.delete() }
            }
            // The sweep spares recently committed files whose message is not in the history yet.
            target.setLastModified(System.currentTimeMillis())
        }
        return attachments.all { attachment -> file(conversationId, attachment).length() == attachment.sizeBytes }
    }

    /** Deletes the files of messages removed from a conversation (edit, delete from here). */
    fun deleteFiles(conversationId: String, attachments: List<ChatAttachment>) {
        attachments.forEach { attachment -> file(conversationId, attachment).delete() }
        conversationDirectory(conversationId).takeIf { directory -> directory.list()?.isEmpty() == true }?.delete()
    }

    fun discardPending(attachments: List<ChatAttachment>) {
        attachments.forEach { attachment -> pendingFile(attachment).delete() }
    }

    fun deleteConversation(conversationId: String) {
        conversationDirectory(conversationId).deleteRecursively()
    }

    /** Removes every conversation directory; pending files of the composer stay. */
    fun deleteAllConversations() {
        root.listFiles()?.forEach { entry -> if (entry.name != PENDING_DIRECTORY) entry.deleteRecursively() }
    }

    /**
     * Deletes files no stored message refers to (D136): directories of conversations that no longer
     * exist, files not in [referenced] (directory name to file names) and pending files older than a
     * day that are not in [keepPending]. Files committed in the last few minutes stay, because their
     * message may still be on its way into the history. Returns the count and bytes removed.
     */
    fun sweep(referenced: Map<String, Set<String>>, keepPending: Set<String>, nowMillis: Long): SweepResult {
        var files = 0
        var bytes = 0L
        fun remove(file: File) {
            val size = file.length()
            if (file.delete()) {
                files += 1
                bytes += size
            }
        }
        root.listFiles()?.forEach { entry ->
            if (entry.name == PENDING_DIRECTORY) {
                entry.listFiles()?.forEach { file ->
                    if (file.name !in keepPending && nowMillis - file.lastModified() > PENDING_RETENTION_MILLIS) remove(file)
                }
                return@forEach
            }
            val kept = referenced[entry.name]
            if (!entry.isDirectory) {
                remove(entry)
                return@forEach
            }
            entry.listFiles()?.forEach { file ->
                val recent = nowMillis - file.lastModified() < COMMITTED_GRACE_MILLIS
                if ((kept == null || file.name !in kept) && !recent) {
                    if (file.isDirectory) {
                        bytes += file.walkBottomUp().filter(File::isFile).sumOf(File::length)
                        files += file.walkBottomUp().count(File::isFile)
                        file.deleteRecursively()
                    } else {
                        remove(file)
                    }
                }
            }
            if (entry.listFiles().isNullOrEmpty()) entry.delete()
        }
        return SweepResult(files, bytes)
    }

    /**
     * Map of directory name to file names for [sweep]; a file that only a version not shown refers to
     * is still referenced (roadmap D148).
     */
    fun references(conversations: List<StoredConversation>): Map<String, Set<String>> =
        conversations.associate { conversation ->
            directoryName(conversation.id) to ChatBranchPolicy.attachmentFileNames(conversation.messages)
        }

    /** A request attachment backed by the stored file; null when the file is gone or changed. */
    fun generationAttachment(conversationId: String, attachment: ChatAttachment): GenerationAttachment? {
        val file = file(conversationId, attachment)
        if (!file.isFile || file.length() != attachment.sizeBytes) return null
        return attachment.toGeneration { file.readBytes() }
    }

    data class SweepResult(val files: Int, val bytes: Long)

    private fun importImage(resolver: ContentResolver, uri: Uri, name: String): ChatAttachmentImport {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openStream(uri).use { input -> BitmapFactory.decodeStream(input, null, bounds) }
        val sourceMime = bounds.outMimeType?.lowercase()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || sourceMime == null) {
            return ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.UNREADABLE)
        }
        val rotation = runCatching {
            resolver.openStream(uri).use { input -> exifRotation(ExifInterface(input)) }
        }.getOrDefault(0)
        val id = newId()
        val keepAsIs = sourceMime in KEPT_IMAGE_TYPES && rotation == 0 &&
            max(bounds.outWidth, bounds.outHeight) <= ChatAttachmentLimits.MAXIMUM_IMAGE_EDGE
        if (keepAsIs) {
            val bytes = resolver.openStream(uri).use { input -> input.readBounded(ChatAttachmentLimits.MAXIMUM_IMAGE_BYTES) }
            if (bytes != null) {
                val attachment = ChatAttachment(id, ChatAttachmentKind.IMAGE, name, sourceMime, bytes.size.toLong(), bounds.outWidth, bounds.outHeight)
                writePending(attachment, bytes)
                return ChatAttachmentImport.Imported(attachment)
            }
        }
        // D132: scale down to the long-edge limit, apply the EXIF rotation, keep only the first GIF
        // frame, then JPEG (PNG when the image has transparency).
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longEdge / (sample * 2) >= ChatAttachmentLimits.MAXIMUM_IMAGE_EDGE) sample *= 2
        val decoded = resolver.openStream(uri).use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.UNREADABLE)
        var bitmap = transformed(decoded, rotation, ChatAttachmentLimits.MAXIMUM_IMAGE_EDGE)
        try {
            var quality = JPEG_QUALITY
            repeat(ENCODE_ATTEMPTS) {
                val png = bitmap.hasAlpha()
                val bytes = ByteArrayOutputStream().use { output ->
                    bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, output)
                    output.toByteArray()
                }
                if (bytes.size <= ChatAttachmentLimits.MAXIMUM_IMAGE_BYTES) {
                    val mime = if (png) ChatAttachmentLimits.MIME_PNG else ChatAttachmentLimits.MIME_JPEG
                    val attachment = ChatAttachment(id, ChatAttachmentKind.IMAGE, name, mime, bytes.size.toLong(), bitmap.width, bitmap.height)
                    writePending(attachment, bytes)
                    return ChatAttachmentImport.Imported(attachment)
                }
                val smaller = (max(bitmap.width, bitmap.height) * SHRINK_FACTOR).roundToInt()
                val next = transformed(bitmap, 0, smaller)
                if (next !== bitmap) bitmap.recycle()
                bitmap = next
                quality = (quality - QUALITY_STEP).coerceAtLeast(MINIMUM_JPEG_QUALITY)
            }
        } finally {
            bitmap.recycle()
        }
        return ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.TOO_LARGE)
    }

    private fun importPdf(resolver: ContentResolver, uri: Uri, name: String, declaredSize: Long?): ChatAttachmentImport {
        if (declaredSize != null && declaredSize > ChatAttachmentLimits.MAXIMUM_PDF_BYTES) throw TooLarge()
        val bytes = resolver.openStream(uri).use { input -> input.readBounded(ChatAttachmentLimits.MAXIMUM_PDF_BYTES) }
            ?: throw TooLarge()
        if (bytes.size < PDF_MAGIC.size || !bytes.copyOfRange(0, PDF_MAGIC.size).contentEquals(PDF_MAGIC)) {
            return ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.UNSUPPORTED_TYPE)
        }
        val id = newId()
        val probe = ChatAttachment(id, ChatAttachmentKind.PDF, name, ChatAttachmentLimits.MIME_PDF, bytes.size.toLong(), pageCount = 1)
        writePending(probe, bytes)
        val file = pendingFile(probe)
        val pages = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
            }
        } catch (_: SecurityException) {
            file.delete()
            return ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.PDF_PROTECTED)
        } catch (_: IOException) {
            file.delete()
            return ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.UNREADABLE)
        }
        if (pages > ChatAttachmentLimits.MAXIMUM_PDF_PAGES) {
            file.delete()
            return ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.PDF_TOO_MANY_PAGES)
        }
        if (pages <= 0) {
            file.delete()
            return ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.UNREADABLE)
        }
        return ChatAttachmentImport.Imported(probe.copy(pageCount = pages))
    }

    private fun importText(
        resolver: ContentResolver,
        uri: Uri,
        name: String,
        mimeType: String?,
        declaredSize: Long?,
    ): ChatAttachmentImport {
        if (mimeType != null && !ChatAttachmentTextPolicy.mayBeText(mimeType, name)) {
            return ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.UNSUPPORTED_TYPE)
        }
        if (declaredSize != null && declaredSize > ChatAttachmentLimits.MAXIMUM_TEXT_BYTES) throw TooLarge()
        val bytes = resolver.openStream(uri).use { input -> input.readBounded(ChatAttachmentLimits.MAXIMUM_TEXT_BYTES) }
            ?: throw TooLarge()
        val text = ChatAttachmentTextPolicy.decode(bytes)
            ?: return ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.BINARY_TEXT)
        val stored = text.toByteArray(Charsets.UTF_8)
        if (stored.isEmpty()) return ChatAttachmentImport.Failed(name, ChatAttachmentImportFailure.UNREADABLE)
        if (stored.size > ChatAttachmentLimits.MAXIMUM_TEXT_BYTES) throw TooLarge()
        val attachment = ChatAttachment(newId(), ChatAttachmentKind.TEXT, name, ChatAttachmentLimits.MIME_TEXT, stored.size.toLong())
        writePending(attachment, stored)
        return ChatAttachmentImport.Imported(attachment)
    }

    private fun writePending(attachment: ChatAttachment, bytes: ByteArray) {
        pendingDirectory.mkdirs()
        val target = pendingFile(attachment)
        val temporary = File(pendingDirectory, "${attachment.fileName}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Cannot store attachment")
        }
    }

    private fun queryNameAndSize(resolver: ContentResolver, uri: Uri): Pair<String?, Long?> = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null to null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val displayName = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
            displayName to size?.takeIf { value -> value >= 0L }
        }
    }.getOrNull() ?: (null to null)

    private fun ContentResolver.openStream(uri: Uri): InputStream =
        openInputStream(uri) ?: throw IOException("Cannot open attachment")

    private fun transformed(bitmap: Bitmap, rotation: Int, maximumEdge: Int): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        val scale = if (longEdge > maximumEdge) maximumEdge.toFloat() / longEdge else 1f
        if (rotation == 0 && scale == 1f) return bitmap
        val matrix = Matrix().apply {
            if (scale != 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (result !== bitmap) bitmap.recycle()
        return result
    }

    private fun exifRotation(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private class TooLarge : RuntimeException()

    companion object {
        const val DIRECTORY = "attachments"
        const val PENDING_DIRECTORY = ".pending"
        private const val DEFAULT_NAME = "file"
        private const val PENDING_RETENTION_MILLIS = 24L * 60L * 60L * 1_000L
        private const val COMMITTED_GRACE_MILLIS = 10L * 60L * 1_000L
        private const val JPEG_QUALITY = 90
        private const val MINIMUM_JPEG_QUALITY = 70
        private const val QUALITY_STEP = 5
        private const val ENCODE_ATTEMPTS = 6
        private const val SHRINK_FACTOR = 0.8
        private val KEPT_IMAGE_TYPES = setOf(
            ChatAttachmentLimits.MIME_JPEG,
            ChatAttachmentLimits.MIME_PNG,
            ChatAttachmentLimits.MIME_WEBP,
        )
        private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
        private val SAFE_DIRECTORY = Regex("^[0-9A-Za-z-]{1,64}$")

        fun newId(): String = UUID.randomUUID().toString().replace("-", "")

        /** Conversation IDs from imported files may contain anything; such IDs map to a hash. */
        fun directoryName(conversationId: String): String =
            if (SAFE_DIRECTORY.matches(conversationId) && conversationId != PENDING_DIRECTORY) {
                conversationId
            } else {
                "h" + MessageDigest.getInstance("SHA-256")
                    .digest(conversationId.toByteArray(Charsets.UTF_8))
                    .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
                    .take(32)
            }

        fun boundedName(name: String): String {
            val cleaned = name.replace(CONTROL_CHARACTERS, " ").trim().ifEmpty { DEFAULT_NAME }
            if (cleaned.length <= ChatAttachmentLimits.MAXIMUM_NAME_CHARACTERS) return cleaned
            val dot = cleaned.lastIndexOf('.')
            val extension = if (dot > 0 && cleaned.length - dot <= 10) cleaned.substring(dot) else ""
            return cleaned.take(ChatAttachmentLimits.MAXIMUM_NAME_CHARACTERS - extension.length) + extension
        }

        fun mimeFromName(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> ChatAttachmentLimits.MIME_JPEG
            "png" -> ChatAttachmentLimits.MIME_PNG
            "webp" -> ChatAttachmentLimits.MIME_WEBP
            "gif" -> "image/gif"
            "pdf" -> ChatAttachmentLimits.MIME_PDF
            else -> null
        }

        private val CONTROL_CHARACTERS = Regex("[\\p{Cntrl}]+")

        /** Reads at most [maximum] bytes; null when the stream is longer. */
        private fun InputStream.readBounded(maximum: Long): ByteArray? {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0L
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                total += read
                if (total > maximum) return null
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }

        private const val BUFFER_BYTES = 64 * 1024
    }
}

/** Which picked files count as text and how they are read (roadmap D70, D133). */
internal object ChatAttachmentTextPolicy {
    private val TEXT_MIME_TYPES = setOf(
        "application/json",
        "application/xml",
        "application/javascript",
        "application/x-javascript",
        "application/x-sh",
        "application/x-yaml",
        "application/yaml",
        "application/toml",
        "application/x-subrip",
        "application/sql",
        "application/x-kotlin",
        "application/csv",
    )
    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "jsonl", "xml", "html", "htm", "css", "js", "mjs",
        "cjs", "ts", "tsx", "jsx", "kt", "kts", "java", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp",
        "cs", "swift", "sh", "bat", "ps1", "yml", "yaml", "toml", "ini", "cfg", "conf", "properties",
        "gradle", "sql", "log", "srt", "vtt", "tex", "lua", "php", "pl", "r", "dart", "vue", "svelte",
    )

    /** False for MIME types that are certainly not text; unknown types are probed by content. */
    fun mayBeText(mimeType: String, name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return mimeType.startsWith("text/") || mimeType in TEXT_MIME_TYPES ||
            mimeType == "application/octet-stream" || extension in TEXT_EXTENSIONS
    }

    /** Strict UTF-8 without NUL; a leading BOM is dropped. Null for binary content. */
    fun decode(bytes: ByteArray): String? {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            return null
        }
        if (text.indexOf('\u0000') >= 0) return null
        val controls = text.count { character -> character < ' ' && character != '\n' && character != '\r' && character != '\t' && character != '\u000c' }
        if (controls > text.length / CONTROL_RATIO_DIVISOR + 1) return null
        return if (text.firstOrNull()?.code == BYTE_ORDER_MARK) text.substring(1) else text
    }

    private const val CONTROL_RATIO_DIVISOR = 100
    private const val BYTE_ORDER_MARK = 0xFEFF
}

/**
 * Reads picked files one at a time off the main thread (roadmap P4.1). The queue belongs to the
 * process, so a chat screen recreated while a file is read (rotation) still receives it: results
 * go to the attached screen, or wait until one attaches. Main thread only, except the reads.
 */
internal object ChatAttachmentImportQueue {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ThreeStoneAiAttachmentImport").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val unclaimed = ArrayList<ChatAttachmentImport>()
    private var receiver: ((ChatAttachmentImport) -> Unit)? = null

    /** Files still being read. */
    var running = 0
        private set

    fun submit(store: ChatAttachmentStore, uris: List<Uri>) {
        uris.forEach { uri ->
            running += 1
            executor.execute {
                // import() maps the expected failures; anything else must still reach the screen.
                val result = try {
                    store.import(uri)
                } catch (error: RuntimeException) {
                    ChatAttachmentImport.Failed(uri.lastPathSegment.orEmpty(), ChatAttachmentImportFailure.UNREADABLE)
                }
                mainHandler.post { deliver(result) }
            }
        }
    }

    fun attach(target: (ChatAttachmentImport) -> Unit) {
        receiver = target
        val waiting = unclaimed.toList()
        unclaimed.clear()
        waiting.forEach(target)
    }

    fun detach(target: (ChatAttachmentImport) -> Unit) {
        if (receiver === target) receiver = null
    }

    private fun deliver(result: ChatAttachmentImport) {
        running -= 1
        val target = receiver
        if (target == null) unclaimed += result else target(result)
    }
}
